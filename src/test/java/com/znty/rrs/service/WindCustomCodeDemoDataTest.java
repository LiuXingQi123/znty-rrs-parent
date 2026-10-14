package com.znty.rrs.service;

import org.junit.Assume;
import com.znty.rrs.mapper.SecurityMarketMapper;
import com.znty.rrs.mapper.BatchSecurityPoolAdjustMapper;
import com.znty.rrs.mapper.BatchCrmwPoolAdjustMapper;
import com.znty.rrs.mapper.StockSecurityBatchAdjustMapper;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolAdjustReq;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwPoolAdjustReq;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustReq;
import com.znty.rrs.entity.common.SecurityMarketDto;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.InputStream;
import java.util.Arrays;
import org.junit.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Wind 跨市场证券代码表结构与演示数据关联测试。 */
public class WindCustomCodeDemoDataTest {

    /** 验证截图中的 28 列名称、类型和顺序，不追加内部业务字段。 */
    @Test
    public void schemaShouldMatchScreenshotColumnsInOrder() throws Exception {
        // 读取原始 DDL，避免数据库类型归一化影响截图类型核对
        String schema = readSql("ais_inv_ods_schema.sql");
        // 提取目标表结构
        String tableSql = extractStatement(schema, "CREATE TABLE", "wind_windcustomcode");
        Matcher columns = Pattern.compile("(?m)^\\s*`([^`]+)`\\s+(VARCHAR\\(\\d+\\)|BIGINT|DOUBLE|FLOAT|DATE)\\b?")
                .matcher(tableSql);
        List<String> actual = new ArrayList<>();
        while (columns.find()) {
            actual.add(columns.group(1) + " " + columns.group(2));
        }
        assertThat(actual).containsExactly(
                "OBJECT_ID VARCHAR(100)", "S_INFO_WINDCODE VARCHAR(40)", "S_INFO_ASHARECODE VARCHAR(10)",
                "S_INFO_COMPCODE VARCHAR(10)", "S_INFO_SECURITIESTYPES VARCHAR(10)",
                "S_INFO_SECTYPENAME VARCHAR(100)", "S_INFO_COUNTRYNAME VARCHAR(100)",
                "S_INFO_COUNTRYCODE VARCHAR(10)", "S_INFO_EXCHMARKETNAME VARCHAR(40)",
                "S_INFO_EXCHMARKET VARCHAR(10)", "CRNCY_NAME VARCHAR(40)", "CRNCY_CODE VARCHAR(10)",
                "S_INFO_ISINCODE VARCHAR(40)", "S_INFO_CODE VARCHAR(40)", "S_INFO_NAME VARCHAR(200)",
                "EXCHMARKET VARCHAR(10)", "SECURITY_STATUS BIGINT", "S_INFO_ORG_CODE VARCHAR(20)",
                "S_INFO_TYPECODE BIGINT", "S_INFO_MIN_PRICE_CHG_UNIT DOUBLE", "S_INFO_LOT_SIZE FLOAT",
                "S_INFO_ENAME VARCHAR(200)", "TRADING_HOURS_CODE VARCHAR(10)", "S_INFO_SECTYPEBCODE BIGINT",
                "S_INFO_SECTYPESCODE BIGINT", "S_INFO_PINYIN VARCHAR(40)", "OPDATE DATE", "OPMODE VARCHAR(1)");
    }

    /** 主档末尾追加外部交易市场，独立 ID 与两张快照不保留旧市场列。 */
    @Test
    public void securitySchemaShouldUseIndependentIdAndRemoveSnapshotMarketColumns() throws Exception {
        String master = extractStatement(readSql("rrs_external_import_schema.sql"), "CREATE TABLE", "rrs_securityinfo");
        assertThat(master).doesNotContain("wind_code_sh", "wind_code_sz", "wind_code_nib", "wind_code_nbc", "wind_code_bj",
                "sec_id_sh", "sec_id_sz", "sec_id_nib", "sec_id_nbc", "sec_id_bj");
        assertThat(master.substring(master.indexOf("`sec_id`"))).contains("varchar(100)", "DEFAULT NULL", "证券 ID");
        assertThat(master.indexOf("`market`")).isGreaterThan(master.indexOf("`sec_id`"));
        assertThat(master.substring(master.indexOf("`market`"))).contains("varchar(10)", "DEFAULT NULL", "交易市场代码");
        assertThat(master.substring(master.indexOf("`market`") + "`market`".length(), master.indexOf("PRIMARY KEY"))).doesNotContain("`");
        String snapshots = readSql("rrs_adjust_snapshot_schema.sql");
        assertThat(snapshots).doesNotContain("wind_code_sh", "wind_code_sz", "wind_code_nib", "wind_code_nbc", "wind_code_bj");
    }

    /** 在 H2 执行真实 DDL 和 Demo，验证跨库关联、市场代码及重复初始化。 */
    @Test
    public void demoShouldAssociateSameBondAcrossThreeMarkets() throws Exception {
        String version = System.getProperty("java.specification.version");
        int javaMajor = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 数据库集成测试需要 Java 11 以上运行时", javaMajor >= 11);
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:wind_custom_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        // 读取真实 SQL 脚本供内存库执行
        String odsSchema = readSql("ais_inv_ods_schema.sql");
        String securitySchema = readSql("rrs_external_import_schema.sql");
        String odsDemo = readSql("ais_inv_ods_demo_data.sql");
        String securityDemo = readSql("rrs_external_import_demo_data.sql");
        try (Connection connection = ds.getConnection()) {
            connection.createStatement().execute("CREATE SCHEMA znty_rrs");
            connection.createStatement().execute("CREATE SCHEMA ais_inv_ods");
            for (String table : new String[]{"rrs_securityinfo", "wind_windcustomcode", "wind_cbondissuer"}) {
                String schema = "rrs_securityinfo".equals(table) ? securitySchema : odsSchema;
                String database = "rrs_securityinfo".equals(table) ? "znty_rrs" : "ais_inv_ods";
                connection.setSchema(database);
                // 提取真实建表语句，仅移除 H2 不支持的 MySQL 表选项
                String ddl = extractStatement(schema, "CREATE TABLE", table).replaceAll("(?s)\\) ENGINE.*", ")");
                connection.createStatement().execute(ddl);
            }
            for (int run = 0; run < 2; run++) {
                for (String table : new String[]{"wind_windcustomcode", "wind_cbondissuer", "rrs_securityinfo"}) {
                    boolean security = "rrs_securityinfo".equals(table);
                    connection.setSchema(security ? "znty_rrs" : "ais_inv_ods");
                    connection.createStatement().execute("TRUNCATE TABLE `" + table + "`");
                    // 提取并执行真实 Demo 的 INSERT，保持两类初始化相互独立
                    String insert = extractStatement(security ? securityDemo : odsDemo, "INSERT INTO", table);
                    ScriptUtils.executeSqlScript(connection, new ByteArrayResource(insert.getBytes(StandardCharsets.UTF_8)));
                }
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ais_inv_ods.wind_windcustomcode", Integer.class))
                        .isEqualTo(49);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ais_inv_ods.wind_windcustomcode w"
                        + " LEFT JOIN znty_rrs.rrs_securityinfo s ON s.wind_code = w.S_INFO_WINDCODE"
                        + " WHERE s.wind_code IS NULL"
                        + " OR s.issuer_code <> w.S_INFO_COMPCODE OR s.short_name <> w.S_INFO_NAME"
                        + " OR s.crncy_code <> w.CRNCY_CODE OR s.market IS NULL"
                        + " OR s.market <> w.EXCHMARKET", Integer.class)).isZero();
                assertThat(jdbc.queryForList("SELECT S_INFO_ISINCODE FROM ais_inv_ods.wind_windcustomcode"
                        + " WHERE S_INFO_ISINCODE IS NOT NULL GROUP BY S_INFO_ISINCODE"
                        + " HAVING COUNT(*) = 3 AND COUNT(DISTINCT EXCHMARKET) = 3", String.class))
                        .containsExactlyInAnyOrder("CNDEMO000293", "CNDEMO000301", "CNDEMO000319");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM znty_rrs.rrs_securityinfo WHERE sec_id IS NULL", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT sec_id) FROM znty_rrs.rrs_securityinfo", Integer.class)).isEqualTo(51);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM znty_rrs.rrs_securityinfo WHERE security_source = 'temporary'", Integer.class)).isEqualTo(2);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM znty_rrs.rrs_securityinfo WHERE security_source = 'temporary' AND market IS NOT NULL", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ais_inv_ods.wind_windcustomcode w"
                        + " LEFT JOIN ais_inv_ods.wind_cbondissuer i ON i.s_info_windcode = w.S_INFO_WINDCODE"
                        + " AND i.s_info_compcode = w.S_INFO_COMPCODE WHERE i.object_id IS NULL", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ais_inv_ods.wind_windcustomcode"
                        + " WHERE S_INFO_ISINCODE = 'CNDEMO000426' AND S_INFO_SECURITIESTYPES = 'K'", Integer.class))
                        .isEqualTo(1);
            }
            connection.setSchema("znty_rrs");
            connection.createStatement().execute("CREATE TABLE rrs_temp_security_code (temp_security_code VARCHAR(100), temp_security_market VARCHAR(100))");
            connection.createStatement().execute("INSERT INTO rrs_temp_security_code VALUES ('TMP001.IB', 'CIBM'), ('TMP003.IB', 'CIBM')");
            connection.createStatement().execute("CREATE TABLE ip_pool_status_crmw (security_code VARCHAR(100), crmw_scode VARCHAR(100), crmw_name VARCHAR(100), crmw_stype VARCHAR(32), target_pool_id BIGINT, audit_status VARCHAR(2), is_deleted INT, pool_type VARCHAR(32))");
            connection.createStatement().execute("INSERT INTO ip_pool_status_crmw VALUES ('198101.SH', 'CRMW001.IB', 'CRMW-A', 'crmw', 1, '20', 0, 'crmw'), ('XYB001.IB', 'CRMW001.IB', 'CRMW-A', 'crmw', 1, '20', 0, 'crmw')");
            ds.setUrl(ds.getUrl() + ";SCHEMA=znty_rrs");
            Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), ds));
            config.setMapUnderscoreToCamelCase(true);
            try (InputStream xml = Files.newInputStream(Paths.get("src/main/resources/mapper/SecurityMarketMapper.xml"))) {
                new XMLMapperBuilder(xml, config, "SecurityMarketMapper.xml", config.getSqlFragments()).parse();
            }
            try (InputStream xml = Files.newInputStream(Paths.get("src/main/resources/mapper/BatchSecurityPoolAdjustMapper.xml"))) {
                new XMLMapperBuilder(xml, config, "BatchSecurityPoolAdjustMapper.xml", config.getSqlFragments()).parse();
            }
            for (String mapper : new String[]{"BatchCrmwPoolAdjustMapper", "StockSecurityBatchAdjustMapper"}) {
                try (InputStream xml = Files.newInputStream(Paths.get("src/main/resources/mapper/" + mapper + ".xml"))) {
                    new XMLMapperBuilder(xml, config, mapper, config.getSqlFragments()).parse();
                }
            }
            try (SqlSession session = new SqlSessionFactoryBuilder().build(config).openSession()) {
                SecurityMarketService service = new SecurityMarketService();
                ReflectionTestUtils.setField(service, "securityMarketMapper", session.getMapper(SecurityMarketMapper.class));
                for (List<String> group : Arrays.asList(Arrays.asList("XYB001.IB", "198101.SH", "128101.SZ"),
                        Arrays.asList("XYB002.IB", "198102.SH", "128102.SZ"), Arrays.asList("XYB003.IB", "198103.SH", "128103.SZ"))) {
                    for (String code : group) {
                        List<String> expected = new ArrayList<>(group); expected.remove(code);
                        assertThat(service.queryRelatedMarketCodes(code)).extracting(SecurityMarketDto::getWindCode)
                                .containsExactlyInAnyOrderElementsOf(expected);
                    }
                }
                assertThat(service.queryMarketCode("XYB001.IB")).isEqualTo("CIBM");
                assertThat(service.queryMarketCode("198101.SH")).isEqualTo("SSE");
                assertThat(service.queryMarketCode("CORP001.IB")).isEqualTo("OTHER");
                assertThat(service.queryRelatedMarketCodes("TMP001.IB")).isEmpty();
                assertThat(service.queryRelatedMarketCodes("TMP003.IB")).isEmpty();
                assertThat(service.queryRelatedMarketCodes("CB001.SH")).isEmpty();
                connection.createStatement().execute("INSERT INTO ais_inv_ods.wind_windcustomcode (OBJECT_ID, S_INFO_WINDCODE, S_INFO_EXCHMARKET, S_INFO_ISINCODE) VALUES ('EMPTY1', 'EMPTY1.SH', 'SSE', ''), ('EMPTY2', 'EMPTY2.SH', 'SSE', ''), ('BLANK1', 'BLANK1.SH', 'SSE', '   '), ('BLANK2', 'BLANK2.SH', 'SSE', '   ')");
                connection.createStatement().execute("INSERT INTO rrs_securityinfo (wind_code, security_source, market) VALUES ('EMPTY1.SH', 'official', 'SSE'), ('EMPTY2.SH', 'official', 'SSE'), ('BLANK1.SH', 'official', 'SSE'), ('BLANK2.SH', 'official', 'SSE')");
                for (String code : Arrays.asList("EMPTY1.SH", "EMPTY2.SH", "BLANK1.SH", "BLANK2.SH")) {
                    assertThat(service.queryRelatedMarketCodes(code)).isEmpty();
                }
                BatchSecurityPoolAdjustMapper batch = session.getMapper(BatchSecurityPoolAdjustMapper.class);
                BatchSecurityPoolAdjustReq filter = new BatchSecurityPoolAdjustReq();
                filter.setMarketCodes(Arrays.asList("SSE")); filter.setSecurityCode("XYB001.IB");
                assertThat(batch.querySecurityPage(filter)).isEmpty();
                filter.setSecurityCode("198101.SH");
                assertThat(batch.querySecurityPage(filter)).hasSize(1);
                connection.createStatement().execute("INSERT INTO ais_inv_ods.wind_windcustomcode (OBJECT_ID, S_INFO_WINDCODE, S_INFO_EXCHMARKET, S_INFO_ISINCODE) VALUES ('DUP', '198101.SH', 'SSE', 'CNDEMO000293')");
                session.clearCache();
                assertThat(batch.querySecurityPage(filter)).hasSize(1);
                assertThat(service.queryMarketCode("198101.SH")).isEqualTo("SSE");
                // ODS 市场错误或缺少映射不影响本地市场查询与批量筛选
                connection.createStatement().execute("UPDATE ais_inv_ods.wind_windcustomcode SET S_INFO_EXCHMARKET = 'SZSE', EXCHMARKET = 'SZSE' WHERE S_INFO_WINDCODE = '198101.SH'");
                connection.createStatement().execute("DELETE FROM ais_inv_ods.wind_windcustomcode WHERE S_INFO_WINDCODE = 'XYB001.IB'");
                session.clearCache();
                assertThat(service.queryMarketCode("198101.SH")).isEqualTo("SSE");
                assertThat(service.queryMarketCode("XYB001.IB")).isEqualTo("CIBM");
                assertThat(batch.querySecurityPage(filter)).hasSize(1);
                filter.setSecurityCode("XYB001.IB"); filter.setMarketCodes(Arrays.asList("CIBM"));
                assertThat(batch.querySecurityPage(filter)).hasSize(1);
                filter.setSecurityCode("TMP001.IB");
                assertThat(batch.querySecurityPage(filter)).hasSize(1);
                StockSecurityBatchAdjustReq stockFilter = new StockSecurityBatchAdjustReq();
                stockFilter.setSecurityCode("198101.SH"); stockFilter.setMarketCodes(Arrays.asList("SSE"));
                assertThat(session.getMapper(StockSecurityBatchAdjustMapper.class).querySecurityPage(stockFilter)).hasSize(1);
                BatchCrmwPoolAdjustReq crmwFilter = new BatchCrmwPoolAdjustReq();
                crmwFilter.setPoolId(2L); crmwFilter.setCrmwScode("CRMW001.IB");
                crmwFilter.setSecurityCode("198101.SH"); crmwFilter.setMarketCodes(Arrays.asList("SSE"));
                BatchCrmwPoolAdjustMapper crmwMapper = session.getMapper(BatchCrmwPoolAdjustMapper.class);
                assertThat(crmwMapper.queryInboundCandidatePage(crmwFilter)).hasSize(1);
                crmwFilter.setPoolId(1L);
                assertThat(crmwMapper.queryOutboundCandidatePage(crmwFilter)).hasSize(1);
                crmwFilter.setSecurityCode("XYB001.IB");
                assertThat(crmwMapper.queryOutboundCandidatePage(crmwFilter)).isEmpty();
                crmwFilter.setMarketCodes(Arrays.asList("CIBM"));
                assertThat(crmwMapper.queryOutboundCandidatePage(crmwFilter)).hasSize(1);
                // 本地字段缺失时必须报错，不回退查询 Wind 或推断代码后缀
                connection.createStatement().execute("UPDATE rrs_securityinfo SET market = NULL WHERE wind_code = '198101.SH'");
                session.clearCache();
                assertThatThrownBy(() -> service.queryMarketCode("198101.SH")).hasMessageContaining("缺少").hasMessageContaining("198101.SH");
            }
        }
    }

    /** 按 UTF-8 读取项目原始 SQL。 */
    private String readSql(String filename) throws Exception {
        return new String(Files.readAllBytes(Paths.get("sql", filename)), StandardCharsets.UTF_8);
    }

    /** 从脚本中提取指定表的单条建表或插入语句。 */
    private String extractStatement(String sql, String operation, String table) {
        Matcher matcher = Pattern.compile("(?s)" + operation + "\\s+`" + table + "`\\s*\\(.*?;").matcher(sql);
        assertThat(matcher.find()).as(operation + " " + table).isTrue();
        return matcher.group();
    }
}
