package com.znty.rrs.service;

import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 CRMW Mapper 验证关联码的活动流程定位、同批展示及精确出池。 */
public class CrmwRelatedMarketMapperTest {
    /** H2 测试数据库。 */
    private EmbeddedDatabase database;
    /** 测试会话。 */
    private SqlSession session;
    /** 真实 CRMW Mapper。 */
    private CrmwPoolAdjustMapper mapper;

    /** 初始化最小池状态与流程数据，不访问业务数据库。 */
    @Before
    public void setUp() throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 需 Java 11 以上运行时", major >= 11);
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE ip_adjust_log (id BIGINT PRIMARY KEY, security_code VARCHAR(30), "
                + "security_short_name VARCHAR(100), security_type VARCHAR(30), crmw_name VARCHAR(100), "
                + "crmw_scode VARCHAR(30), crmw_stype VARCHAR(30), crmw_mktcode VARCHAR(30), "
                + "adjust_type VARCHAR(30), adjust_mode VARCHAR(30), adjust_batch_no VARCHAR(100), "
                + "target_pool_id BIGINT, target_pool_name VARCHAR(100), pool_type VARCHAR(30), "
                + "flow_id BIGINT, flow_key VARCHAR(100), flow_type VARCHAR(100), audit_status VARCHAR(10), "
                + "adjuster_id VARCHAR(30), adjuster_name VARCHAR(100), adjust_reason VARCHAR(200), "
                + "adjust_advice VARCHAR(200), submit_time TIMESTAMP, audit_time TIMESTAMP, entry_time TIMESTAMP, is_deleted INT)");
        jdbc.execute("CREATE TABLE ip_adjust_step (adjust_batch_no VARCHAR(100), step_status VARCHAR(30))");
        jdbc.execute("CREATE TABLE wf_flow_definition (id BIGINT PRIMARY KEY, name VARCHAR(100))");
        jdbc.execute("CREATE TABLE ip_pool_status_crmw (security_code VARCHAR(30), crmw_scode VARCHAR(30), "
                + "crmw_stype VARCHAR(30), target_pool_id BIGINT, audit_status VARCHAR(10), pool_type VARCHAR(30), "
                + "is_deleted INT, updt_time TIMESTAMP)");
        jdbc.update("INSERT INTO ip_adjust_log(id, security_code, crmw_scode, crmw_stype, adjust_type, "
                + "adjust_batch_no, target_pool_id, pool_type, audit_status, is_deleted) VALUES "
                + "(1, 'A.IB', 'C.IB', 'crmw', '手工调整', 'BATCH', 18, 'crmw', '00', 0), "
                + "(2, 'A.SH', 'C.IB', 'crmw', '关联调整', 'BATCH', 18, 'crmw', '00', 0), "
                + "(3, 'A.SH', 'OTHER.IB', 'crmw', '手工调整', 'OTHER_BATCH', 18, 'crmw', '00', 0)");
        jdbc.update("INSERT INTO ip_adjust_step VALUES ('BATCH', 'pending')");
        jdbc.update("INSERT INTO ip_pool_status_crmw VALUES "
                + "('A.IB', 'C.IB', 'crmw', 18, '20', 'crmw', 0, NULL), "
                + "('A.SH', 'C.IB', 'crmw', 18, '20', 'crmw', 0, NULL), "
                + "('A.SH', 'OTHER.IB', 'crmw', 18, '20', 'crmw', 0, NULL)");
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("crmw-related", new JdbcTransactionFactory(), database));
        String resource = "mapper/CrmwPoolAdjustMapper.xml";
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession();
        mapper = session.getMapper(CrmwPoolAdjustMapper.class);
    }

    /** 关闭会话并清理测试数据库。 */
    @After
    public void tearDown() {
        if (session != null) session.close();
        if (database != null) database.shutdown();
    }

    /** 关联日志没有独立步骤，仍能定位主项批次的活动池组。 */
    @Test
    public void relatedCodeShouldOccupyPendingBatchPoolGroup() {
        assertThat(mapper.queryPendingManualTargetPoolIdList("A.SH", "C.IB", "crmw", null)).containsExactly(18L);
        assertThat(mapper.queryPendingManualTargetPoolIdList("A.SH", "C.IB", "crmw", "BATCH")).isEmpty();
        assertThat(mapper.queryPendingManualTargetPoolIdList("A.SH", "OTHER.IB", "crmw", null)).isEmpty();
    }

    /** 从同批任一标的打开历史，都能看到主项与关联项，其他凭证不混入。 */
    @Test
    public void queryHistoryShouldIncludeRelatedBatchMembers() {
        assertThat(mapper.queryAdjustLogList("A.IB", "C.IB", "crmw", null))
                .extracting(IpAdjustLogBo::getSecurityCode).containsExactlyInAnyOrder("A.IB", "A.SH");
        assertThat(mapper.queryAdjustLogList("A.SH", "C.IB", "crmw", "BATCH"))
                .extracting(IpAdjustLogBo::getId).containsExactlyInAnyOrder(1L, 2L);
    }

    /** 多码出池按凭证和标的逐条删除，其他凭证同标的组合保留。 */
    @Test
    public void outboundShouldPreserveOtherVoucherCombination() {
        assertThat(mapper.deletePoolStatusSoft("A.IB", "C.IB", "crmw", 18L)).isEqualTo(1);
        assertThat(mapper.deletePoolStatusSoft("A.SH", "C.IB", "crmw", 18L)).isEqualTo(1);
        assertThat(mapper.queryCrmwComboInPool("C.IB", "crmw", "A.SH", 18L)).isFalse();
        assertThat(mapper.queryCrmwComboInPool("OTHER.IB", "crmw", "A.SH", 18L)).isTrue();
    }
}
