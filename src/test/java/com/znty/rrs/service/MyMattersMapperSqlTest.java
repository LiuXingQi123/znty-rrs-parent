package com.znty.rrs.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.znty.rrs.entity.mymatters.MyMattersReq;
import com.znty.rrs.entity.mymatters.MyMattersDto;
import com.znty.rrs.mapper.BusinessPermissionMapper;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.assertj.core.api.Assertions.*;

/** 在内存数据库执行入口及债券/基金 Mapper，验证入口去重和事项范围隔离。 */
public class MyMattersMapperSqlTest {
    /** 内存数据库会话 */
    private SqlSession session;
    /** 仅用于页面入口的业务选项查询 */
    private BusinessPermissionMapper entries;

    /** 构造真实 Mapper 和按业务分表的内存测试数据。 */
    @Before
    public void setUp() throws Exception {
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:matters" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        PageInterceptor paging = new PageInterceptor();
        Properties properties = new Properties();
        properties.setProperty("helperDialect", "h2");
        paging.setProperties(properties);
        config.addInterceptor(paging);
        // 在同一测试会话中模拟下一次进入页面时重新读取角色关系。
        config.setLocalCacheScope(LocalCacheScope.STATEMENT);
        for (String name : new String[]{"BusinessPermission", "BondMyMatters", "FundMyMatters", "StockMyMatters"}) {
            String resource = "mapper/" + name + "Mapper.xml";
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse();
            }
        }
        session = new SqlSessionFactoryBuilder().build(config).openSession(true);
        entries = session.getMapper(BusinessPermissionMapper.class);
        // 初始化现有 AIS 角色关系和领域运行表，不创建业务授权表
        sql("CREATE SCHEMA ais_inv_analysis",
                "CREATE TABLE ais_inv_analysis.t_sys_user_role(user_id BIGINT, role_id BIGINT)",
                "CREATE TABLE ais_inv_analysis.t_sys_role(id BIGINT PRIMARY KEY, enable INT, parent_id BIGINT)",
                "CREATE TABLE wf_flow_definition(id BIGINT PRIMARY KEY, flow_key VARCHAR(20), name VARCHAR(40), description VARCHAR(40), is_deleted INT)",
                "CREATE TABLE wf_flow_node(id BIGINT PRIMARY KEY, flow_id BIGINT)",
                "CREATE TABLE dict_security_type(security_type VARCHAR(20), category_type VARCHAR(20), is_deleted INT)",
                "INSERT INTO wf_flow_definition VALUES(1,'bond','债券流程','债券',0),(2,'fund','基金流程','基金',0),(3,'stock','股票流程','股票',0)",
                "INSERT INTO wf_flow_node VALUES(1,1),(2,2),(3,3)",
                "INSERT INTO dict_security_type VALUES('bond','bond',0)",
                "INSERT INTO ais_inv_analysis.t_sys_role VALUES(1,1,10),(7,1,NULL),(9,0,NULL),(10,1,NULL),(99,1,7)",
                "INSERT INTO ais_inv_analysis.t_sys_user_role VALUES(2,1),(2,7),(2,7),(4,10),(5,9),(6,99)");
        for (String suffix : new String[]{"", "_fund", "_stock"}) {
            // 创建当前业务独立的日志和步骤表
            sql("CREATE TABLE ip_adjust_log" + suffix + "(id BIGINT PRIMARY KEY, security_code VARCHAR(40), security_short_name VARCHAR(40), security_type VARCHAR(20), crmw_scode VARCHAR(40), fund_code VARCHAR(40), fund_short_name VARCHAR(40), stock_code VARCHAR(40), stock_short_name VARCHAR(40), target_pool_id BIGINT, target_pool_name VARCHAR(40), adjust_batch_no VARCHAR(40), audit_status VARCHAR(10), adjuster_id VARCHAR(20), adjuster_name VARCHAR(40), adjust_mode VARCHAR(10), pool_type VARCHAR(20), flow_id BIGINT, is_deleted INT)",
                "CREATE TABLE ip_adjust_step" + suffix + "(id BIGINT PRIMARY KEY, adjust_log_id BIGINT, adjust_batch_no VARCHAR(40), flow_node_id BIGINT, node_label VARCHAR(40), step_status VARCHAR(20), handler_id VARCHAR(20), start_time TIMESTAMP)");
            for (int id=1; id<=4; id++) {
                int flow = suffix.isEmpty() ? 1 : suffix.equals("_fund") ? 2 : 3;
                // 插入本人和其他人的待处理、已完成及发起记录
                sql("INSERT INTO ip_adjust_log" + suffix + " VALUES(" + id + ",'B" + id + "','债券" + id + "','bond',NULL,'F" + id + "','基金" + id + "','S" + id + "','股票" + id + "',1,'池','batch" + id + "','00','" + (id==3 ? "2" : "9") + "','发起人','in','bond'," + flow + ",0)",
                    "INSERT INTO ip_adjust_step" + suffix + " VALUES(" + id + "," + id + ",'batch" + id + "'," + flow + ",'审核','" + (id>=3 ? "approve" : "pending") + "','" + (id==1 || id==3 ? "2" : "9") + "',CURRENT_TIMESTAMP)");
            }
        }
    }

    /** 关闭当前测试的数据库会话。 */
    @After public void tearDown() { if (session != null) session.close(); }

    /** 多角色和重复关联只返回一份业务选项，禁用和非直属角色不产生入口。 */
    @Test public void unionShouldDeduplicateAndRespectEffectiveDirectRoles() throws Exception {
        assertThat(entries.queryBusinessDomainList(2L)).extracting("businessDomain").containsExactly("bond","fund","stock");
        assertThat(entries.queryBusinessDomainList(4L)).extracting("businessDomain").containsExactly("bond","fund","stock");
        assertThat(entries.queryBusinessDomainList(5L)).extracting("businessDomain").containsExactly("stock");
        assertThat(entries.queryBusinessDomainList(6L)).isEmpty();
        assertThat(entries.queryBusinessDomainList(10000L)).isEmpty();
        // 为当前用户增加重复的跨业务角色，入口列表仍保持去重
        sql("INSERT INTO ais_inv_analysis.t_sys_user_role VALUES(2,10),(2,10)");
        assertThat(entries.queryBusinessDomainList(2L)).extracting("businessDomain").containsExactly("bond","fund","stock");
        // 停用跨业务角色，其他有效角色继续提供入口
        sql("UPDATE ais_inv_analysis.t_sys_role SET enable=0 WHERE id=10");
        assertThat(entries.queryBusinessDomainList(2L)).extracting("businessDomain").containsExactly("bond","fund","stock");
        assertThat(entries.queryBusinessDomainList(4L)).isEmpty();
    }

    /** 固定人员直授无需角色，且直授与角色授权重叠时只展示一次。 */
    @Test public void directUserPermissionsShouldNotDependOnRoles() throws Exception {
        assertThat(entries.queryBusinessDomainList(1L)).extracting("businessDomain").containsExactly("bond","fund","stock");
        assertThat(entries.queryBusinessDomainList(3L)).extracting("businessDomain").containsExactly("fund");
        // 将直授人员加入基金角色，验证相同业务仍然去重
        sql("INSERT INTO ais_inv_analysis.t_sys_user_role VALUES(3,7),(3,7)");
        assertThat(entries.queryBusinessDomainList(3L)).extracting("businessDomain").containsExactly("fund", "stock");
        // 停用基金角色，验证人员直授不随角色撤销而消失
        sql("UPDATE ais_inv_analysis.t_sys_role SET enable=0 WHERE id=7");
        assertThat(entries.queryBusinessDomainList(3L)).extracting("businessDomain").containsExactly("fund");
    }

    /** 普通用户在每个业务内只读取本人处理及本人发起的事项。 */
    @Test public void eachDomainShouldRestrictPendingCompletedAndInitiated() {
        for (String domain : new String[]{"bond","fund","stock"}) {
            // 构造普通用户的当前业务查询
            MyMattersReq req = request(domain,"pending","2");
            assertThat(rows(domain,"queryMyMattersPage",req)).extracting("adjustLogId").containsExactly(1L);
            req.setStepStatus("completed");
            assertThat(rows(domain,"queryMyMattersPage",req)).extracting("adjustLogId").containsExactly(3L);
            assertThat(rows(domain,"queryMyInitiatedMattersPage",req)).extracting("adjustLogId").containsExactly(3L);
            req.setCurrentUserId("4");
            req.setStepStatus("pending");
            assertThat(rows(domain,"queryMyMattersPage",req)).isEmpty();
        }
    }

    /** 原管理员 ID 可查全量事项，但本人发起列表不扩大；边界外 ID 仍按本人范围。 */
    @Test public void globalAdminIdsShouldControlMatterScopeWithoutEntryPermission() {
        for (String domain : new String[]{"bond", "fund", "stock"}) {
            for (String userId : new String[]{"1", "10000", "10100"}) {
                // 构造管理员查询，独立验证全量范围和本人发起范围
                MyMattersReq req = request(domain, "pending", userId);
                assertThat(rows(domain, "queryMyMattersPage", req)).extracting("adjustLogId").containsExactly(2L, 1L);
                req.setStepStatus("completed");
                assertThat(rows(domain, "queryMyMattersPage", req)).extracting("adjustLogId").containsExactly(4L, 3L);
                assertThat(rows(domain, "queryMyInitiatedMattersPage", req)).isEmpty();
            }
            for (String userId : new String[]{"9999", "10101"}) {
                // 验证管理员区间之外的用户不能读取他人待办
                MyMattersReq req = request(domain, "pending", userId);
                assertThat(rows(domain, "queryMyMattersPage", req)).isEmpty();
            }
        }
    }

    /** 跨业务角色仅提供入口，不赋予全量事项查询能力。 */
    @Test public void entryRoleShouldNotExpandPersonalMatterScope() throws Exception {
        // 将已有本人待办的用户加入角色 10，事项范围仍然只按用户 ID 计算
        sql("INSERT INTO ais_inv_analysis.t_sys_user_role VALUES(2,10)");
        assertThat(entries.queryBusinessDomainList(2L)).extracting("businessDomain").containsExactly("bond", "fund", "stock");
        for (String domain : new String[]{"bond", "fund", "stock"}) {
            // 构造跨业务角色成员的普通查询
            MyMattersReq req = request(domain, "pending", "2");
            assertThat(rows(domain, "queryMyMattersPage", req)).extracting("adjustLogId").containsExactly(1L);
            req.setStepStatus("completed");
            assertThat(rows(domain, "queryMyMattersPage", req)).extracting("adjustLogId").containsExactly(3L);
        }
    }

    /** 已完成按整批结束判断，检索和流程选项始终使用当前业务数据。 */
    @Test public void completedShouldWaitForWholeBatchAndFiltersShouldStayInDomain() throws Exception {
        // 构造同批次未结束步骤，验证已完成列表不能提前显示
        sql("INSERT INTO ip_adjust_step_fund VALUES(99,4,'batch3',2,'会签','pending','9',CURRENT_TIMESTAMP)");
        // 构造本人已完成基金事项查询
        MyMattersReq req=request("fund","completed","2");
        assertThat(rows("fund","queryMyMattersPage",req)).isEmpty();
        req.setStepStatus("pending"); req.setSecurityCode("F1");
        assertThat(rows("fund","queryMyMattersPage",req)).extracting("businessDomain","objectCode","businessScene")
                .containsExactly(tuple("fund","F1","fundAdjust"));
        req.setSecurityCode("B1");
        assertThat(rows("fund","queryMyMattersPage",req)).isEmpty();
        // 构造债券请求，验证流程选项不混入基金
        req=request("bond","pending","2");
        assertThat(session.selectList("com.znty.rrs.mapper.BondMyMattersMapper.queryFlowOptionList",req)).extracting("flowId").containsExactly(1L);
        // 构造基金请求，验证流程选项不混入债券
        req=request("fund","pending","2");
        assertThat(session.selectList("com.znty.rrs.mapper.FundMyMattersMapper.queryFlowOptionList",req)).extracting("flowId").containsExactly(2L);
    }

    /** 角色变化只更新下一次入口结果，不影响已明确指定业务的本人事项查询。 */
    @Test public void entryChangesShouldNotBecomeMatterAuthorization() throws Exception {
        assertThat(entries.queryBusinessDomainList(2L)).extracting("businessDomain").containsExactly("bond", "fund", "stock");
        // 停用债券角色后只有基金入口继续显示
        sql("UPDATE ais_inv_analysis.t_sys_role SET enable=0 WHERE id=1");
        assertThat(entries.queryBusinessDomainList(2L)).extracting("businessDomain").containsExactly("fund", "stock");
        // 重新启用恢复入口，删除关联后再次隐藏债券入口
        sql("UPDATE ais_inv_analysis.t_sys_role SET enable=1 WHERE id=1");
        assertThat(entries.queryBusinessDomainList(2L)).extracting("businessDomain").containsExactly("bond", "fund", "stock");
        // 删除债券角色的人员关联
        sql("DELETE FROM ais_inv_analysis.t_sys_user_role WHERE user_id=2 AND role_id=1");
        assertThat(entries.queryBusinessDomainList(2L)).extracting("businessDomain").containsExactly("fund", "stock");
        // 删除全部基金关联后隐藏所有入口
        sql("DELETE FROM ais_inv_analysis.t_sys_user_role WHERE user_id=2 AND role_id=7");
        assertThat(entries.queryBusinessDomainList(2L)).isEmpty();
        for (String domain : new String[]{"bond", "fund", "stock"}) {
            // 没有入口的用户仍可按原本人事项关系执行领域查询
            MyMattersReq req = request(domain, "pending", "2");
            assertThat(rows(domain, "queryMyMattersPage", req)).extracting("adjustLogId").containsExactly(1L);
        }
        // 启用此前禁用的角色，其成员的下一次入口结果随之更新
        sql("UPDATE ais_inv_analysis.t_sys_role SET enable=1 WHERE id=9");
        assertThat(entries.queryBusinessDomainList(5L)).extracting("businessDomain").containsExactly("fund", "stock");
    }

    /** 管理员的全量事项在各自业务内独立计数和分页。 */
    @Test public void paginationShouldCountAndPageEachBusinessIndependently() {
        for (String domain : new String[]{"bond", "fund", "stock"}) {
            // 构造管理员的当前业务分页查询
            MyMattersReq req = request(domain, "pending", "1");
            Page<MyMattersDto> first = PageHelper.startPage(1, 1);
            assertThat(rows(domain, "queryMyMattersPage", req)).extracting("adjustLogId").containsExactly(2L);
            assertThat(first.getTotal()).isEqualTo(2);
            Page<MyMattersDto> second = PageHelper.startPage(2, 1);
            assertThat(rows(domain, "queryMyMattersPage", req)).extracting("adjustLogId").containsExactly(1L);
            assertThat(second.getTotal()).isEqualTo(2);
        }
    }

    /** 构造指定业务、步骤状态和当前用户的查询请求。 */
    private MyMattersReq request(String domain,String status,String userId) {
        MyMattersReq req=new MyMattersReq(); req.setBusinessDomain(domain); req.setCurrentUserId(userId);
        req.setStepStatus(status); return req;
    }
    /** 执行指定业务的事项 Mapper 查询。 */
    private List<MyMattersDto> rows(String domain,String method,MyMattersReq req) {
        return session.selectList("com.znty.rrs.mapper." + (domain.equals("bond") ? "Bond" : domain.equals("fund") ? "Fund" : "Stock") + "MyMattersMapper." + method,req);
    }
    /** 在当前连接执行测试数据准备及角色关系变更。 */
    private void sql(String... statements) throws Exception {
        Connection connection=session.getConnection();
        try (Statement statement=connection.createStatement()) {
            for (String sql : statements) statement.execute(sql);
        }
    }
}
