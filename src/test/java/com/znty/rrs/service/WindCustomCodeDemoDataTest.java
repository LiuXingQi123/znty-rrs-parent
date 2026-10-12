package com.znty.rrs.service;

import org.junit.Assume;
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
                        .isEqualTo(10);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ais_inv_ods.wind_windcustomcode w"
                        + " LEFT JOIN znty_rrs.rrs_securityinfo s ON s.wind_code = w.S_INFO_WINDCODE"
                        + " WHERE s.wind_code IS NULL OR s.isin_code <> w.S_INFO_ISINCODE"
                        + " OR s.issuer_code <> w.S_INFO_COMPCODE OR s.short_name <> w.S_INFO_NAME"
                        + " OR s.crncy_code <> w.CRNCY_CODE", Integer.class)).isZero();
                assertThat(jdbc.queryForList("SELECT S_INFO_ISINCODE FROM ais_inv_ods.wind_windcustomcode"
                        + " WHERE S_INFO_ISINCODE IS NOT NULL GROUP BY S_INFO_ISINCODE"
                        + " HAVING COUNT(*) = 3 AND COUNT(DISTINCT EXCHMARKET) = 3", String.class))
                        .containsExactlyInAnyOrder("CNDEMO000293", "CNDEMO000301", "CNDEMO000319");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ais_inv_ods.wind_windcustomcode w"
                        + " JOIN znty_rrs.rrs_securityinfo s ON s.wind_code = w.S_INFO_WINDCODE"
                        + " WHERE w.S_INFO_SECURITIESTYPES = 'Q' AND ("
                        + " s.wind_code_sh IS NULL OR s.wind_code_sz IS NULL OR s.wind_code_nib IS NULL"
                        + " OR (w.EXCHMARKET = 'SSE' AND (s.wind_code_sh <> s.wind_code OR s.sec_id_sh <> w.S_INFO_ASHARECODE))"
                        + " OR (w.EXCHMARKET = 'SZSE' AND (s.wind_code_sz <> s.wind_code OR s.sec_id_sz <> w.S_INFO_ASHARECODE))"
                        + " OR (w.EXCHMARKET = 'IB' AND (s.wind_code_nib <> s.wind_code OR s.sec_id_nib <> w.S_INFO_ASHARECODE)))",
                        Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ais_inv_ods.wind_windcustomcode w"
                        + " LEFT JOIN ais_inv_ods.wind_cbondissuer i ON i.s_info_windcode = w.S_INFO_WINDCODE"
                        + " AND i.s_info_compcode = w.S_INFO_COMPCODE WHERE i.object_id IS NULL", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ais_inv_ods.wind_windcustomcode"
                        + " WHERE S_INFO_ISINCODE = 'CNDEMO000426' AND S_INFO_SECURITIESTYPES = 'K'", Integer.class))
                        .isEqualTo(1);
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
