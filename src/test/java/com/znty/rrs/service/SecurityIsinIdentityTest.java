package com.znty.rrs.service;

import com.znty.rrs.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Statement;
import java.sql.Timestamp;
import com.znty.rrs.entity.schedule.HsPoolExportRowDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 使用真实Mapper及建表SQL验证ISIN身份、五码搜索和历史事件。 */
public class SecurityIsinIdentityTest {

    /** 不跳过H2测试，运行时必须使用支持当前H2版本的JDK。 */
    private SqlSession session() throws Exception {
        Configuration config = new Configuration(new Environment("isin-test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:isin_" + UUID.randomUUID() + ";MODE=MySQL", "sa", "")));
        config.setMapUnderscoreToCamelCase(true);
        for (String mapper : new String[] {"SecurityPoolAdjustMapper", "HsPoolExcelExportMapper"}) {
            String resource = "mapper/" + mapper + ".xml";
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
        }
        SqlSession session = new SqlSessionFactoryBuilder().build(config).openSession(true);
        try (Statement statement = session.getConnection().createStatement()) {
            // 使用项目DDL，仅移除H2不支持的MySQL引擎及排序规则尾部
            String schema = new String(Files.readAllBytes(Paths.get("sql/rrs_external_import_schema.sql")), StandardCharsets.UTF_8);
            int start = schema.indexOf("CREATE TABLE `rrs_securityinfo`");
            String ddl = schema.substring(start, schema.indexOf(';', start));
            ddl = ddl.replaceAll("(?s)\\)\\s*ENGINE=.*", ")");
            statement.execute(ddl);
            statement.execute("CREATE TABLE dict_security_type (security_type VARCHAR(50), security_type_name VARCHAR(50), category_type VARCHAR(20), is_deleted INT)");
            statement.execute("INSERT INTO dict_security_type VALUES ('mtn','中期票据','bond',0)");
            statement.execute("INSERT INTO rrs_securityinfo (isin_code, short_name, security_type, security_status, security_source, wind_code_sh, wind_code_sz, wind_code_nib, wind_code_bj, wind_code_nbc)"
                    + " VALUES ('CNTEST000001','同一只券','mtn','L','official','123456.SH','123456.SZ','123456.IB','123456.BJ','123456.OTHER')");
        }
        return session;
    }

    /** 任意五码及多字段同时命中都只返回同一只券。 */
    @Test
    public void allMarketCodesShouldResolveOneIdentity() throws Exception {
        try (SqlSession session = session()) {
            SecurityPoolAdjustMapper mapper = session.getMapper(SecurityPoolAdjustMapper.class);
            for (String code : new String[] {"123456.SH", "123456.SZ", "123456.IB", "123456.BJ", "123456.OTHER"}) {
                List<SecurityInfoBo> matches = mapper.querySecurityByMarketCodeList(code);
                assertThat(matches).hasSize(1);
                assertThat(matches.get(0).getIsinCode()).isEqualTo("CNTEST000001");
            }
            Map<String, Object> query = new HashMap<>();
            query.put("securityKeyword", "123456");
            assertThat(session.selectList("com.znty.rrs.mapper.SecurityPoolAdjustMapper.querySecurityPage", query)).hasSize(1);
        }
    }

    /** 正式ISIN不允许为空或重复，临时身份不会与同名正式ISIN混淆。 */
    @Test
    public void primaryKeyShouldRejectMissingAndDuplicateIsin() throws Exception {
        try (SqlSession session = session(); Statement statement = session.getConnection().createStatement()) {
            assertThatThrownBy(() -> statement.execute("INSERT INTO rrs_securityinfo (isin_code) VALUES (NULL)"))
                    .isInstanceOf(Exception.class);
            assertThatThrownBy(() -> statement.execute("INSERT INTO rrs_securityinfo (isin_code) VALUES ('CNTEST000001')"))
                    .isInstanceOf(Exception.class);
            statement.execute("INSERT INTO rrs_securityinfo (isin_code, security_source, wind_code_nib) VALUES ('TEMP:TMP001','temporary','TMP001')");
            assertThat(session.getMapper(SecurityPoolAdjustMapper.class).querySecurityByMarketCodeList("TMP001")
                    .get(0).getIsinCode()).isEqualTo("TEMP:TMP001");
        }
    }

    /** 同一市场码歧义时拒绝解析，不能选第一条。 */
    @Test
    public void ambiguousMarketCodeShouldBeRejected() throws Exception {
        try (SqlSession session = session(); Statement statement = session.getConnection().createStatement()) {
            statement.execute("INSERT INTO rrs_securityinfo (isin_code, wind_code_sz) VALUES ('CNTEST000002','123456.SH')");
            SecurityPoolExcelImportService service = new SecurityPoolExcelImportService();
            ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", session.getMapper(SecurityPoolAdjustMapper.class));
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "resolveMarketSecurity", "123456.SH"))
                    .hasMessageContaining("拒绝解析");
        }
    }

    /** 主档市场码发生变化，出库仍沿用原入库事件五码。 */
    @Test
    public void outboundShouldUseEntryEventSnapshot() throws Exception {
        try (SqlSession session = session(); Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE TABLE ip_adjust_log (id BIGINT, wind_code_sh VARCHAR(100), wind_code_sz VARCHAR(100), wind_code_nib VARCHAR(100), wind_code_bj VARCHAR(100), wind_code_nbc VARCHAR(100), crmw_scode VARCHAR(100))");
            statement.execute("CREATE TABLE ip_pool_status (id BIGINT, isin_code VARCHAR(100), target_pool_id BIGINT, adjust_log_id BIGINT)");
            statement.execute("INSERT INTO ip_adjust_log VALUES (1,'OLD.SH',NULL,'OLD.IB',NULL,NULL,NULL)");
            statement.execute("INSERT INTO ip_pool_status VALUES (1,'CNTEST000001',100,1)");
            IpAdjustLogBo log = new IpAdjustLogBo();
            log.setIsinCode("CNTEST000001"); log.setTargetPoolId(100L); log.setAdjustMode("调出");
            IpAdjustLogBo snapshot = session.getMapper(SecurityPoolAdjustMapper.class).queryEffectiveMarketSnapshot(log);
            assertThat(snapshot.getWindCodeSh()).isEqualTo("OLD.SH");
            assertThat(snapshot.getWindCodeNib()).isEqualTo("OLD.IB");
        }
    }
    /** 缺少新身份的旧导入校验结果必须拒绝继续使用。 */
    @Test
    public void legacyImportDraftsShouldRequireUploadAgain() {
        Object[] services = {new SecurityPoolExcelImportService(), new CrmwPoolExcelImportService()};
        for (Object service : services) {
            ReflectionTestUtils.setField(service, "objectMapper",
                    new ObjectMapper());
            assertThatThrownBy(() ->
                    ReflectionTestUtils.invokeMethod(service, "parseCheckItems",
                            "{\"checkItems\":[{\"canAdjust\":true,\"securityCode\":\"123456.SH\"}]}"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("重新上传");
        }
    }

    /** 恒生增量普通券展开五码，CRMW只导出凭证银行间代码，临时券不自动导出。 */
    @Test
    public void hsIncrementShouldRespectObjectAndMarketIdentity() throws Exception {
        try (SqlSession session = session(); Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE TABLE ip_adjust_log (id BIGINT, target_pool_id BIGINT, is_deleted INT, audit_status VARCHAR(2), adjust_mode VARCHAR(20), audit_time TIMESTAMP, isin_code VARCHAR(100), crmw_isin_code VARCHAR(100), security_short_name VARCHAR(100), crmw_name VARCHAR(100), pool_type VARCHAR(20), wind_code_sh VARCHAR(100), wind_code_sz VARCHAR(100), wind_code_nib VARCHAR(100), wind_code_bj VARCHAR(100), wind_code_nbc VARCHAR(100), crmw_scode VARCHAR(100))");
            statement.execute("INSERT INTO ip_adjust_log VALUES (1,100,0,'20','调出','2026-10-01 12:00:00','CNBOND',NULL,'普通券',NULL,'credit_bond','OLD.SH','OLD.SZ','OLD.IB','OLD.BJ','OLD.OTHER',NULL)");
            statement.execute("INSERT INTO ip_adjust_log VALUES (2,100,0,'20','调入','2026-10-01 12:00:00','CNBOND','CNCRMW','标的','凭证','crmw','UNDERLYING.SH',NULL,'UNDERLYING.IB',NULL,NULL,'CRMW001.IB')");
            statement.execute("INSERT INTO ip_adjust_log VALUES (3,100,0,'20','调入','2026-10-01 12:00:00','TEMP:TMP',NULL,'临时券',NULL,'credit_bond',NULL,NULL,'TMP',NULL,NULL,NULL)");
            Map<String, Object> params = new HashMap<>();
            params.put("poolId", 100L);
            params.put("startTime", Timestamp.valueOf("2026-10-01 00:00:00"));
            params.put("endTime", Timestamp.valueOf("2026-10-02 00:00:00"));
            List<HsPoolExportRowDto> rows = session.selectList(
                    "com.znty.rrs.mapper.HsPoolExcelExportMapper.queryIncrementExportRowList", params);
            assertThat(rows).hasSize(2);
            HsPoolExportRowDto bond = rows.stream().filter(row -> "普通券".equals(row.getSecurityShortName())).findFirst().get();
            assertThat(bond.getOperationType()).isEqualTo("删除");
            assertThat(bond.getWindCodeSh()).isEqualTo("OLD.SH");
            assertThat(bond.getWindCodeSz()).isEqualTo("OLD.SZ");
            assertThat(bond.getWindCodeNib()).isEqualTo("OLD.IB");
            assertThat(bond.getWindCodeBj()).isEqualTo("OLD.BJ");
            assertThat(bond.getWindCodeNbc()).isEqualTo("OLD.OTHER");
            HsPoolExportRowDto crmw = rows.stream().filter(row -> "凭证".equals(row.getSecurityShortName())).findFirst().get();
            assertThat(crmw.getWindCodeNib()).isEqualTo("CRMW001.IB");
            assertThat(crmw.getWindCodeSh()).isNull();
        }
    }

}
