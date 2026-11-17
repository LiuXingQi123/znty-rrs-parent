package com.znty.rrs.service;

import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.securitypooladjust.AdjustSubmitDto;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData;
import com.znty.rrs.entity.securitypooladjust.RatingCompanySelectionReq;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.mapper.BatchSecurityCheckMapper;
import com.znty.rrs.mapper.BatchSecurityAdjustSubmitMapper;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BatchSecurityAdjustSubmitServiceTest {

    /** 同一批普通债、关联码及 ABS 的日志、快照和步骤都有各自正确的关联主键 */
    @Test
    public void directSubmitShouldBatchWritesAndPreserveAbsOrder() throws Exception {
        try (SqlSession session = openSession("security_direct"); Statement sql = session.getConnection().createStatement()) {
            createTables(sql);
            sql.executeUpdate("INSERT INTO ip_pool_status (security_code,target_pool_id,audit_status,is_deleted)"
                    + " VALUES ('B1.SH', 2, '20', 0), ('B1.SH', 3, '20', 0)");
            BatchSecurityAdjustSubmitMapper writes = mock(BatchSecurityAdjustSubmitMapper.class,
                    delegatesTo(session.getMapper(BatchSecurityAdjustSubmitMapper.class)));
            Fixture fixture = new Fixture(writes);
            BatchSecurityCheckData data = new BatchSecurityCheckData();
            for (String code : Arrays.asList("B1.SH", "B1.IB", "B2.IB", "ABS.IB", "B3.SH")) {
                SecurityInfoBo master = new SecurityInfoBo();
                master.setWindCode(code);
                master.setSecurityType(code.startsWith("ABS") ? "abs" : "mtn");
                master.setIssuerCode("OLD");
                data.securities.put(code, master);
                data.excelSecurityCodes.put(code, code);
            }
            when(fixture.legacy.querySecurityBoByCode("ABS.IB")).thenReturn(data.securities.get("ABS.IB"));
            SecurityPoolAdjustSubmitReq first = request("B1.SH", "调入", item("B1.SH", "调入", "manual"),
                    item("B1.IB", "调入", "related"));
            SecurityPoolAdjustSubmitReq second = request("B2.IB", "调入", item("B2.IB", "调入", "manual"));
            SecurityPoolAdjustSubmitReq abs = request("ABS.IB", "调入", item("ABS.IB", "调入", "manual"));
            abs.getSelectedRatingCompany().setCompanyCode("RIGHTS");
            abs.getSelectedRatingCompany().setCompanyName("权益人");
            abs.getSelectedRatingCompany().setRightsHolderName("权益人");
            SecurityPoolAdjustSubmitReq last = request("B3.SH", "调入", item("B3.SH", "调入", "manual"));

            List<AdjustSubmitDto> result = fixture.service.submit(Arrays.asList(first, second, abs, last), data, false);

            assertThat(result).extracting(AdjustSubmitDto::getSubmitCount).containsExactly(2, 1, 1, 1);
            assertEquals(5, count(sql, "SELECT COUNT(*) FROM ip_adjust_log WHERE audit_status = '20'"));
            assertEquals(12, count(sql, "SELECT COUNT(*) FROM ip_adjust_step s JOIN ip_adjust_log l"
                    + " ON s.adjust_log_id=l.id AND s.adjust_batch_no=l.adjust_batch_no WHERE l.security_code <> 'B1.IB'"));
            assertEquals(5, count(sql, "SELECT COUNT(*) FROM ip_adjust_security_snapshot s JOIN ip_adjust_log l"
                    + " ON s.adjust_log_id=l.id AND s.wind_code=l.security_code"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM ip_adjust_security_snapshot"
                    + " WHERE wind_code='ABS.IB' AND issuer_code='RIGHTS' AND abs_originator_name='权益人'"));
            assertEquals(5, count(sql, "SELECT COUNT(*) FROM ip_pool_status p JOIN ip_adjust_log l"
                    + " ON p.adjust_log_id=l.id AND p.security_code=l.security_code AND p.target_pool_id=l.target_pool_id"
                    + " WHERE p.is_deleted=0"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM ip_pool_status WHERE security_code='B1.SH' AND target_pool_id=3 AND is_deleted=0"));
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM ip_pool_status WHERE security_code='B1.SH' AND target_pool_id=2 AND is_deleted=1"));
            InOrder writesAndAbs = inOrder(writes, fixture.absIdentity, fixture.sync);
            writesAndAbs.verify(writes).addAdjustLogs(anyList());
            writesAndAbs.verify(writes).addAdjustSteps(anyList());
            writesAndAbs.verify(writes).addSecuritySnapshots(anyList());
            writesAndAbs.verify(writes).addPoolStatuses(anyList());
            writesAndAbs.verify(fixture.absIdentity).validateApprovalIdentities(anyList());
            writesAndAbs.verify(fixture.absIdentity).approveIdentities(anyList());
            writesAndAbs.verify(writes).addPoolStatuses(anyList());
            writesAndAbs.verify(fixture.sync).recordManualDecisions(anyList());
            writesAndAbs.verify(writes).addPoolStatuses(anyList());
            verify(writes, times(3)).deletePoolStatusesSoft(anyList());
            verify(fixture.legacy, never()).querySecurityCurrentPoolIdList(anyString());
            verify(fixture.legacy, never()).addAdjustLog(any());
            verify(fixture.reads, never()).querySecurities(anyList());
            verify(fixture.flows).queryFlowById(112L);
            session.rollback(true);
            assertEquals(0, count(sql, "SELECT COUNT(*) FROM ip_adjust_log"));
            assertEquals(0, count(sql, "SELECT COUNT(*) FROM ip_adjust_security_snapshot"));
        }
    }

    /** 清空出库只结束状态，普通证券清空沿用原无快照口径 */
    @Test
    public void directClearShouldEndPoolStatesWithoutInsertingInboundRows() throws Exception {
        try (SqlSession session = openSession("security_clear"); Statement sql = session.getConnection().createStatement()) {
            createTables(sql);
            sql.executeUpdate("INSERT INTO ip_pool_status (security_code,target_pool_id,audit_status,is_deleted)"
                    + " VALUES ('OLD.IB', 2, '20', 0)");
            BatchSecurityAdjustSubmitMapper writes = mock(BatchSecurityAdjustSubmitMapper.class,
                    delegatesTo(session.getMapper(BatchSecurityAdjustSubmitMapper.class)));
            Fixture fixture = new Fixture(writes);
            BatchSecurityCheckData data = new BatchSecurityCheckData();
            SecurityInfoBo master = new SecurityInfoBo();
            master.setWindCode("OLD.IB");
            master.setSecurityType("mtn");
            data.securities.put(master.getWindCode(), master);
            SecurityPoolAdjustSubmitReq req = request("OLD.IB", "调出", item("OLD.IB", "调出", "manual"));
            List<AdjustSubmitDto> result = fixture.service.submit(Collections.singletonList(req), data, true);
            assertThat(result).extracting(AdjustSubmitDto::getSubmitCount).containsExactly(1);
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM ip_pool_status WHERE is_deleted=1"));
            verify(writes, never()).addPoolStatuses(anyList());
            verify(writes, never()).addSecuritySnapshots(anyList());
            verify(fixture.absIdentity, never()).approveIdentities(anyList());
        }
    }

    /** 直通组批量落池，无法读取已选流程快照的待审批组使用原路径保存 */
    @Test
    public void pendingGroupShouldUseOriginalSubmitPathWithoutWritingPoolState() throws Exception {
        try (SqlSession session = openSession("security_pending"); Statement sql = session.getConnection().createStatement()) {
            createTables(sql);
            BatchSecurityAdjustSubmitMapper writes = mock(BatchSecurityAdjustSubmitMapper.class,
                    delegatesTo(session.getMapper(BatchSecurityAdjustSubmitMapper.class)));
            Fixture fixture = new Fixture(writes);
            BatchSecurityCheckData data = new BatchSecurityCheckData();
            for (String code : Arrays.asList("DIRECT", "PENDING")) {
                SecurityInfoBo master = new SecurityInfoBo();
                master.setWindCode(code);
                master.setSecurityType("mtn");
                data.securities.put(code, master);
            }
            when(fixture.legacy.addAdjustLog(any())).thenAnswer(invocation -> {
                IpAdjustLogBo log = invocation.getArgument(0);
                assertEquals("PENDING", log.getSecurityCode());
                assertEquals("00", log.getAuditStatus());
                log.setId(999L);
                return 1;
            });
            SecurityPoolAdjustSubmitReq.AdjustItem pending = item("PENDING", "调入", "manual");
            pending.setFlowId(999L);
            List<AdjustSubmitDto> result = fixture.service.submit(Arrays.asList(
                    request("DIRECT", "调入", item("DIRECT", "调入", "manual")),
                    request("PENDING", "调入", pending)), data, false);

            assertThat(result).extracting(AdjustSubmitDto::getSubmitCount).containsExactly(1, 1);
            assertThat(result.get(1).getLogIds()).containsExactly(999L);
            assertEquals(1, count(sql, "SELECT COUNT(*) FROM ip_pool_status WHERE security_code='DIRECT'"));
            assertEquals(0, count(sql, "SELECT COUNT(*) FROM ip_pool_status WHERE security_code='PENDING'"));
            verify(fixture.legacy).addAdjustLog(any());
        }
    }

    private final class Fixture {
        final BatchSecurityAdjustSubmitService service = new BatchSecurityAdjustSubmitService();
        final AbsIdentityService absIdentity = spy(new AbsIdentityService());
        final AbsCompanySyncService sync = mock(AbsCompanySyncService.class);
        final SecurityPoolAdjustMapper legacy = mock(SecurityPoolAdjustMapper.class);
        final BatchSecurityCheckMapper reads = mock(BatchSecurityCheckMapper.class);
        final FlowMapper flows = mock(FlowMapper.class);

        Fixture(BatchSecurityAdjustSubmitMapper writes) {
            doNothing().when(absIdentity).validateIdentity(any(), any());
            doNothing().when(absIdentity).validateApprovalIdentities(anyList());
            doNothing().when(absIdentity).approveIdentities(anyList());
            doNothing().when(absIdentity).restoreSnapshotSelection(any(), any(), anyBoolean());
            FlowVersionBo version = new FlowVersionBo();
            version.setId(113L);
            version.setStatus("active");
            when(flows.queryFlowById(112L)).thenReturn(new FlowDefinitionBo());
            when(flows.queryFlowVersionByFlowIdList(112L, null)).thenReturn(Collections.singletonList(version));
            when(flows.queryFlowNodeListByVersionId(113L)).thenReturn(Arrays.asList(
                    node(1L, "start"), node(2L, "approval"), node(3L, "end")));
            when(flows.queryFlowEdgeListByVersionId(113L)).thenReturn(Arrays.asList(
                    edge(1L, 2L, "auto"), edge(2L, 3L, "submit")));
            when(flows.queryApprovalConfigListByVersionId(113L)).thenReturn(Collections.singletonList(config(2L, "initiator")));
            InvestmentPoolMapper pools = mock(InvestmentPoolMapper.class);
            InvestmentPoolBo pool = new InvestmentPoolBo();
            pool.setId(2L);
            pool.setPoolName("一级库");
            when(pools.queryPoolList()).thenReturn(Collections.singletonList(pool));
            SecurityPoolAdjustService planner = new SecurityPoolAdjustService();
            ReflectionTestUtils.setField(planner, "securityPoolAdjustMapper", legacy);
            ReflectionTestUtils.setField(planner, "batchSecurityCheckMapper", reads);
            ReflectionTestUtils.setField(planner, "investmentPoolMapper", pools);
            ReflectionTestUtils.setField(planner, "flowMapper", flows);
            ReflectionTestUtils.setField(planner, "absIdentityService", absIdentity);
            ReflectionTestUtils.setField(planner, "absCompanySyncService", sync);
            ReflectionTestUtils.setField(planner, "sysAttachmentService", mock(SysAttachmentService.class));
            ReflectionTestUtils.setField(service, "securityPoolAdjustService", planner);
            ReflectionTestUtils.setField(service, "batchSecurityAdjustSubmitMapper", writes);
        }
    }

    private SecurityPoolAdjustSubmitReq request(String code, String mode, SecurityPoolAdjustSubmitReq.AdjustItem... items) {
        SecurityPoolAdjustSubmitReq req = new SecurityPoolAdjustSubmitReq();
        req.setSecurityCode(code);
        req.setAdjusterId("1");
        req.setAdjusterName("测试用户");
        req.setAdjustType("调入".equals(mode) ? "Excel导入" : "Excel清空");
        req.setSelectedRatingCompany(new RatingCompanySelectionReq());
        req.setItems(Arrays.asList(items));
        return req;
    }

    private SecurityPoolAdjustSubmitReq.AdjustItem item(String code, String mode, String tag) {
        SecurityPoolAdjustSubmitReq.AdjustItem item = new SecurityPoolAdjustSubmitReq.AdjustItem();
        item.setSecurityCode(code);
        item.setTargetPoolId(2L);
        item.setAdjustMode(mode);
        item.setItemTag(tag);
        item.setAdjustGroupKey("ROW");
        item.setFlowId(112L);
        return item;
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

    private SqlSession openSession(String name) throws Exception {
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + name + ";MODE=MySQL", "sa", "");
        Configuration config = new Configuration(new Environment(name, new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/BatchSecurityAdjustSubmitMapper.xml";
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
        String snapshots = new String(Files.readAllBytes(Paths.get("sql/rrs_adjust_snapshot_schema.sql")), StandardCharsets.UTF_8);
        int start = snapshots.indexOf("CREATE TABLE " + (char) 96 + "ip_adjust_security_snapshot" + (char) 96);
        sql.execute(snapshots.substring(start, snapshots.indexOf(" ENGINE", start)));
    }

    private int count(Statement sql, String query) throws Exception {
        try (ResultSet result = sql.executeQuery(query)) {
            result.next();
            return result.getInt(1);
        }
    }
}
