package com.znty.rrs.service;

import com.znty.rrs.entity.bo.AdjustSecuritySnapshotBo;
import com.znty.rrs.entity.bo.AdjustSecuritySnapshotCrmwBo;
import com.znty.rrs.entity.securitypooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustReq;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustReq;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.mapper.SecurityMarketMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Assume;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;

/** 真实快照 SQL 写入、读取及历史详情实时关联测试。 */
public class SecuritySnapshotMarketIntegrationTest {
    /** 两类快照保持原业务信息，市场关联在历史读取时查询当前 Wind。 */
    @Test
    public void snapshotsShouldRoundTripAndReadCurrentRelatedCodes() throws Exception {
        String version = System.getProperty("java.specification.version");
        Assume.assumeTrue("H2 需要 Java 11 以上", !version.startsWith("1."));
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:snapshot_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        String schema = new String(Files.readAllBytes(Paths.get("sql/rrs_adjust_snapshot_schema.sql")), StandardCharsets.UTF_8);
        for (String table : new String[]{"ip_adjust_security_snapshot", "ip_adjust_security_snapshot_crmw"}) {
            Matcher ddl = Pattern.compile("(?s)CREATE TABLE `" + table + "`\\s*\\(.*?;").matcher(schema);
            assertThat(ddl.find()).isTrue();
            jdbc.execute(ddl.group().replaceAll("(?s)\\) ENGINE.*", ")"));
        }
        jdbc.execute("CREATE TABLE dict_security_type (security_type VARCHAR(32), security_type_name VARCHAR(100), category_type VARCHAR(32), is_deleted INT)");
        jdbc.execute("INSERT INTO dict_security_type VALUES ('mtn', '中期票据', 'bond', 0)");
        jdbc.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(100), security_source VARCHAR(32), market VARCHAR(10))");
        jdbc.execute("INSERT INTO rrs_securityinfo VALUES ('A.IB', 'official', 'NIB'), ('B.SH', 'official', 'SSE'), ('C.SZ', 'official', 'SZSE')");
        jdbc.execute("CREATE TABLE rrs_temp_security_code (temp_security_code VARCHAR(100), temp_security_market VARCHAR(32))");
        jdbc.execute("CREATE SCHEMA ais_inv_ods");
        jdbc.execute("CREATE TABLE ais_inv_ods.wind_windcustomcode (S_INFO_WINDCODE VARCHAR(100), EXCHMARKET VARCHAR(10), S_INFO_ISINCODE VARCHAR(40))");
        jdbc.execute("INSERT INTO ais_inv_ods.wind_windcustomcode VALUES ('A.IB', 'NIB', 'SAME'), ('B.SH', 'SSE', 'SAME'), ('C.SZ', 'SZSE', 'OTHER')");
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), ds));
        config.setMapUnderscoreToCamelCase(true);
        for (String mapper : new String[]{"SecurityPoolAdjustMapper", "CrmwPoolAdjustMapper", "SecurityMarketMapper"}) {
            try (InputStream xml = Files.newInputStream(Paths.get("src/main/resources/mapper/" + mapper + ".xml"))) {
                new XMLMapperBuilder(xml, config, mapper, config.getSqlFragments()).parse();
            }
        }
        try (SqlSession session = new SqlSessionFactoryBuilder().build(config).openSession(true)) {
            SecurityPoolAdjustMapper securityMapper = session.getMapper(SecurityPoolAdjustMapper.class);
            CrmwPoolAdjustMapper crmwMapper = session.getMapper(CrmwPoolAdjustMapper.class);
            AdjustSecuritySnapshotBo snapshot = new AdjustSecuritySnapshotBo();
            snapshot.setAdjustLogId(1L); snapshot.setWindCode("A.IB"); snapshot.setSecurityType("mtn");
            snapshot.setShortName("提交时简称"); snapshot.setAnalysis("提交时分析");
            assertThat(securityMapper.addAdjustSecuritySnapshot(snapshot)).isEqualTo(1);
            AdjustSecuritySnapshotBo temporary = new AdjustSecuritySnapshotBo();
            temporary.setAdjustLogId(1L); temporary.setWindCode("TEMP.IB"); temporary.setAnalysis("临时分析");
            assertThat(securityMapper.addAdjustSecuritySnapshot(temporary)).isEqualTo(1);
            assertThat(securityMapper.querySecuritySnapshotDetailByAdjustLogId(1L, "A.IB").getAnalysis()).isEqualTo("提交时分析");
            assertThat(securityMapper.querySecuritySnapshotDetailByAdjustLogId(1L, "TEMP.IB").getAnalysis()).isEqualTo("临时分析");
            AdjustSecuritySnapshotCrmwBo crmw = new AdjustSecuritySnapshotCrmwBo();
            crmw.setAdjustLogId(2L); crmw.setWindCode("A.IB"); crmw.setSecurityType("mtn");
            crmw.setCrmwScode("CRMW.IB"); crmw.setShortName("CRMW提交时简称");
            assertThat(crmwMapper.addAdjustSecuritySnapshotCrmw(crmw)).isEqualTo(1);
            AdjustSecuritySnapshotCrmwBo crmwTemporary = new AdjustSecuritySnapshotCrmwBo();
            crmwTemporary.setAdjustLogId(2L); crmwTemporary.setWindCode("TEMP.IB"); crmwTemporary.setAnalysis("CRMW临时分析");
            assertThat(crmwMapper.addAdjustSecuritySnapshotCrmw(crmwTemporary)).isEqualTo(1);
            assertThat(crmwMapper.querySecuritySnapshotDetailByAdjustLogId(2L, "A.IB").getShortName()).isEqualTo("CRMW提交时简称");
            assertThat(crmwMapper.querySecuritySnapshotDetailByAdjustLogId(2L, "TEMP.IB").getAnalysis()).isEqualTo("CRMW临时分析");
            assertThat(crmwMapper.queryLatestSecuritySnapshotDetailByWindCode("A.IB").getShortName()).isEqualTo("CRMW提交时简称");
            SecurityMarketService markets = new SecurityMarketService();
            ReflectionTestUtils.setField(markets, "securityMarketMapper", session.getMapper(SecurityMarketMapper.class));
            SecurityPoolAdjustService service = new SecurityPoolAdjustService();
            ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", securityMapper);
            ReflectionTestUtils.setField(service, "securityMarketService", markets);
            SecurityPoolAdjustReq req = new SecurityPoolAdjustReq(); req.setSecurityCode("A.IB"); req.setAdjustLogId(1L);
            assertThat(service.querySecurityDetail(req).getRelatedMarketCodes()).extracting("windCode").containsExactly("B.SH");
            jdbc.update("UPDATE ais_inv_ods.wind_windcustomcode SET S_INFO_ISINCODE = 'OTHER' WHERE S_INFO_WINDCODE = 'B.SH'");
            jdbc.update("UPDATE ais_inv_ods.wind_windcustomcode SET S_INFO_ISINCODE = 'SAME' WHERE S_INFO_WINDCODE = 'C.SZ'");
            // 模拟下一次历史详情请求，清除测试共用 SqlSession 的一级缓存
            session.clearCache();
            SecurityInfoDetailDto history = service.querySecurityDetail(req);
            assertThat(history.getRelatedMarketCodes()).extracting("windCode").containsExactly("C.SZ");
            assertThat(history.getMarketCode()).isEqualTo("NIB");
            assertThat(history.getShortName()).isEqualTo("提交时简称");
            assertThat(history.getAnalysis()).isEqualTo("提交时分析");
            CrmwPoolAdjustService crmwService = new CrmwPoolAdjustService();
            ReflectionTestUtils.setField(crmwService, "crmwPoolAdjustMapper", crmwMapper);
            ReflectionTestUtils.setField(crmwService, "securityMarketService", markets);
            CrmwPoolAdjustReq crmwReq = new CrmwPoolAdjustReq(); crmwReq.setSecurityCode("A.IB"); crmwReq.setAdjustLogId(2L);
            assertThat(crmwService.querySecurityDetail(crmwReq).getRelatedMarketCodes()).extracting("windCode").containsExactly("C.SZ");
            assertThat(crmwService.querySecurityDetail(crmwReq).getShortName()).isEqualTo("CRMW提交时简称");
        }
    }
}
