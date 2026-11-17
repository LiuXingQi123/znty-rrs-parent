package com.znty.rrs.service;

import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustSubmitReq;
import com.znty.rrs.mapper.BatchCompanyAdjustSubmitMapper;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class BatchCompanyAdjustSubmitServiceTest {

    /** 十个主体的手工项和联动项共用流程，步骤与池状态分别关联实际生成的日志 */
    @Test
    public void submitShouldBatchDirectLogsStepsAndPoolStates() throws Exception {
        try (SqlSession session = openSession("direct"); Statement sql = session.getConnection().createStatement()) {
            createTables(sql);
            sql.executeUpdate("INSERT INTO ip_pool_status (security_code, target_pool_id, audit_status, is_deleted) "
                    + "VALUES ('C1', 23, '20', 0), ('C1', 15, '20', 0), ('OTHER', 17, '20', 0)");
            BatchCompanyAdjustSubmitMapper mapper = mock(BatchCompanyAdjustSubmitMapper.class,
                    delegatesTo(session.getMapper(BatchCompanyAdjustSubmitMapper.class)));
            FlowMapper flows = mock(FlowMapper.class);
            BatchCompanyAdjustSubmitService service = buildService(mapper, flows, false);
            List<ForbiddenPoolAdjustSubmitReq> requests = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                String code = "C" + i;
                sql.executeUpdate("INSERT INTO ais_inv_ods.wind_compintroduction VALUES ('" + code + "', '全称', '简称')");
                requests.add(request(code, "Excel导入", item(23L, "调入", "manual", "G"),
                        item(17L, "调入", "linkage", "G")));
            }

            List<Long> ids = service.submit(requests);

            assertEquals(20, ids.size());
            assertEquals(10, count(sql, "SELECT COUNT(DISTINCT adjust_batch_no) FROM ip_adjust_log"));
            assertEquals(20, count(sql, "SELECT COUNT(*) FROM ip_adjust_log WHERE audit_status = '20'"));
            assertEquals(30, count(sql, "SELECT COUNT(*) FROM ip_adjust_step s JOIN ip_adjust_log l"
                    + " ON s.adjust_log_id = l.id AND s.adjust_batch_no = l.adjust_batch_no"
                    + " WHERE l.target_pool_id = 23 AND l.adjust_type = 'Excel导入'"));
            assertEquals(20, count(sql, "SELECT COUNT(*) FROM ip_pool_status p JOIN ip_adjust_log l"
                    + " ON p.adjust_log_id = l.id AND p.target_pool_id = l.target_pool_id"
                    + " AND p.security_code = l.security_code WHERE p.is_deleted = 0"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM ip_pool_status WHERE security_code = 'C1'"
                    + " AND target_pool_id = 23 AND is_deleted = 1"));
            assertEquals(2, count(sql, "SELECT COUNT(*) FROM ip_pool_status WHERE adjust_log_id IS NULL AND is_deleted = 0"));
            try (ResultSet logs = sql.executeQuery("SELECT id, security_code, target_pool_id FROM ip_adjust_log ORDER BY id")) {
                for (int i = 0; i < ids.size(); i++) {
                    assertTrue(logs.next());
                    assertEquals(ids.get(i).longValue(), logs.getLong("id"));
                    assertEquals("C" + (i / 2 + 1), logs.getString("security_code"));
                    assertEquals(i % 2 == 0 ? 23L : 17L, logs.getLong("target_pool_id"));
                }
            }
            verify(mapper).queryCompanyList(anyList());
            verify(mapper).addAdjustLogs(anyList());
            verify(mapper).addAdjustSteps(anyList());
            verify(mapper).deletePoolStatusesSoft(anyList());
            verify(mapper).addPoolStatuses(anyList());
            verifyNoMoreInteractions(mapper);
            verify(flows).queryFlowById(112L);
            verify(flows).queryFlowVersionByFlowIdList(112L, null);
            verify(flows).queryFlowNodeListByVersionId(113L);
            verify(flows).queryFlowEdgeListByVersionId(113L);
            verify(flows).queryApprovalConfigListByVersionId(113L);
            verify(flows).queryApprovalHandlerListByVersionId(113L);
            verifyNoMoreInteractions(flows);
        }
    }

    /** 非直通的主项和互斥项留在流程中，只有主项生成待审批步骤 */
    @Test
    public void submitShouldKeepPendingItemsOutOfPoolWrites() throws Exception {
        try (SqlSession session = openSession("pending"); Statement sql = session.getConnection().createStatement()) {
            createTables(sql);
            sql.executeUpdate("INSERT INTO ais_inv_ods.wind_compintroduction VALUES ('C1', '全称', '简称')");
            BatchCompanyAdjustSubmitMapper mapper = mock(BatchCompanyAdjustSubmitMapper.class,
                    delegatesTo(session.getMapper(BatchCompanyAdjustSubmitMapper.class)));
            BatchCompanyAdjustSubmitService service = buildService(mapper, mock(FlowMapper.class), true);

            List<Long> ids = service.submit(Collections.singletonList(request("C1", "Excel导入",
                    item(23L, "调入", "manual", "G"), item(17L, "调出", "mutex", "G"))));

            assertEquals(2, ids.size());
            assertEquals(2, count(sql, "SELECT COUNT(*) FROM ip_adjust_log WHERE audit_status = '00'"));
            assertEquals(3, count(sql, "SELECT COUNT(*) FROM ip_adjust_step WHERE adjust_log_id = " + ids.get(0)));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM ip_adjust_step WHERE step_status = 'pending' AND handler_id = '2'"));
            verify(mapper, never()).deletePoolStatusesSoft(anyList());
            verify(mapper, never()).addPoolStatuses(anyList());
        }
    }

    /** 同池先入后出按原顺序生效，出库缺少旧状态时仍完成 */
    @Test
    public void submitShouldKeepOriginalOrderForRepeatedPoolKey() throws Exception {
        try (SqlSession session = openSession("repeat_key"); Statement sql = session.getConnection().createStatement()) {
            createTables(sql);
            sql.executeUpdate("INSERT INTO ais_inv_ods.wind_compintroduction VALUES ('C1', '全称', '简称')");
            BatchCompanyAdjustSubmitMapper mapper = session.getMapper(BatchCompanyAdjustSubmitMapper.class);
            BatchCompanyAdjustSubmitService service = buildService(mapper, mock(FlowMapper.class), false);

            List<Long> ids = service.submit(Collections.singletonList(request("C1", "Excel清空",
                    item(23L, "调出", "manual", "OUT"), item(23L, "调入", "manual", "IN"),
                    item(17L, "调出", "linkage", "OUT"))));

            assertEquals(3, ids.size());
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted = 1 AND adjust_log_id = " + ids.get(0)));
            assertEquals(0, count(sql, "SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted = 0"));
            session.rollback(true);
            assertEquals(0, count(sql, "SELECT COUNT(*) FROM ip_adjust_log"));
            assertEquals(0, count(sql, "SELECT COUNT(*) FROM ip_adjust_step"));
            assertEquals(0, count(sql, "SELECT COUNT(*) FROM ip_pool_status"));
        }
    }

    private BatchCompanyAdjustSubmitService buildService(BatchCompanyAdjustSubmitMapper mapper, FlowMapper flows,
                                                         boolean pending) {
        FlowVersionBo version = new FlowVersionBo();
        version.setId(113L);
        version.setStatus("active");
        when(flows.queryFlowById(112L)).thenReturn(new FlowDefinitionBo());
        when(flows.queryFlowVersionByFlowIdList(112L, null)).thenReturn(Collections.singletonList(version));
        when(flows.queryFlowNodeListByVersionId(113L)).thenReturn(Arrays.asList(
                node(1L, "start"), node(2L, "approval"), node(3L, pending ? "approval" : "end")));
        when(flows.queryFlowEdgeListByVersionId(113L)).thenReturn(Arrays.asList(
                edge(1L, 2L, "auto"), edge(2L, 3L, "submit")));
        NodeApprovalConfigBo initiator = config(2L, "initiator");
        if (pending) {
            when(flows.queryApprovalConfigListByVersionId(113L)).thenReturn(Arrays.asList(initiator, config(3L, "preempt")));
            NodeApprovalHandlerBo handler = new NodeApprovalHandlerBo();
            handler.setApprovalConfigId(3L);
            handler.setHandlerType("user");
            handler.setHandlerId(2L);
            when(flows.queryApprovalHandlerListByVersionId(113L)).thenReturn(Collections.singletonList(handler));
        } else {
            when(flows.queryApprovalConfigListByVersionId(113L)).thenReturn(Collections.singletonList(initiator));
        }

        InvestmentPoolMapper pools = mock(InvestmentPoolMapper.class);
        when(pools.queryPoolList()).thenReturn(Arrays.asList(17L, 23L).stream().map(id -> {
            InvestmentPoolBo pool = new InvestmentPoolBo();
            pool.setId(id);
            return pool;
        }).collect(Collectors.toList()));
        ForbiddenPoolAdjustService planner = new ForbiddenPoolAdjustService();
        ReflectionTestUtils.setField(planner, "flowMapper", flows);
        BatchCompanyAdjustSubmitService service = new BatchCompanyAdjustSubmitService();
        ReflectionTestUtils.setField(service, "forbiddenPoolAdjustService", planner);
        ReflectionTestUtils.setField(service, "batchCompanyAdjustSubmitMapper", mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", pools);
        return service;
    }

    private FlowNodeBo node(Long id, String type) {
        FlowNodeBo node = new FlowNodeBo();
        node.setId(id);
        node.setNodeType(type);
        return node;
    }

    private FlowEdgeBo edge(Long from, Long to, String action) {
        FlowEdgeBo edge = new FlowEdgeBo();
        edge.setFromNodeId(from);
        edge.setToNodeId(to);
        edge.setRouteAction(action);
        return edge;
    }

    private NodeApprovalConfigBo config(Long node, String strategy) {
        NodeApprovalConfigBo config = new NodeApprovalConfigBo();
        config.setId(node);
        config.setNodeId(node);
        config.setApprovalStrategy(strategy);
        return config;
    }

    private ForbiddenPoolAdjustSubmitReq request(String code, String adjustType, ForbiddenPoolAdjustSubmitReq.AdjustItem... items) {
        ForbiddenPoolAdjustSubmitReq request = new ForbiddenPoolAdjustSubmitReq();
        request.setCompanyCode(code);
        request.setSecurityType("company");
        request.setAdjustType(adjustType);
        request.setAdjusterId("1");
        request.setAdjusterName("测试用户");
        request.setItems(Arrays.asList(items));
        return request;
    }

    private ForbiddenPoolAdjustSubmitReq.AdjustItem item(Long pool, String mode, String tag, String group) {
        ForbiddenPoolAdjustSubmitReq.AdjustItem item = new ForbiddenPoolAdjustSubmitReq.AdjustItem();
        item.setTargetPoolId(pool);
        item.setAdjustMode(mode);
        item.setItemTag(tag);
        item.setAdjustGroupKey(group);
        item.setFlowId(112L);
        return item;
    }

    private SqlSession openSession(String name) throws Exception {
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + name + ";MODE=MySQL", "sa", "");
        Configuration config = new Configuration(new Environment(name, new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/BatchCompanyAdjustSubmitMapper.xml";
        try (InputStream xml = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(xml, config, resource, config.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(config).openSession(false);
    }

    private void createTables(Statement sql) throws Exception {
        sql.execute("CREATE SCHEMA ais_inv_ods");
        sql.execute("CREATE TABLE ais_inv_ods.wind_compintroduction (COMP_ID VARCHAR(32), COMP_NAME VARCHAR(128), COMP_SNAME VARCHAR(128))");
        String schema = new String(Files.readAllBytes(Paths.get("sql/rrs_security_pool_adjust_schema.sql")), StandardCharsets.UTF_8);
        for (String table : Arrays.asList("ip_adjust_log", "ip_adjust_step", "ip_pool_status")) {
            int start = schema.indexOf("CREATE TABLE `" + table + "`");
            sql.execute(schema.substring(start, schema.indexOf(" ENGINE", start)));
        }
    }

    private int count(Statement sql, String query) throws Exception {
        try (ResultSet result = sql.executeQuery(query)) {
            result.next();
            return result.getInt(1);
        }
    }
}
