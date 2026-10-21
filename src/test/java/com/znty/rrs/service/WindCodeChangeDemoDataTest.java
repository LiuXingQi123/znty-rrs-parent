package com.znty.rrs.service;

import com.znty.rrs.entity.schedule.WindCodeChangeDto;
import com.znty.rrs.mapper.WindCodeSyncMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 DDL、Demo 和 Mapper 验证 Wind 换码样例，不连接业务数据库。 */
public class WindCodeChangeDemoDataTest {

    /** 验证重复初始化、上海日期窗口、真实证券身份、连续换码及过滤样例。 */
    @Test
    public void demoShouldProvideUsableChangesWithinCurrentWindow() throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 需要 Java 11 以上运行时", major >= 11);
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.h2.Driver");
        source.setUrl("jdbc:h2:mem:wind_change_demo_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA ais_inv_ods");
        jdbc.execute("CREATE ALIAS IF NOT EXISTS UTC_TIMESTAMP FOR '"
                + WindCodeChangeDemoDataTest.class.getName() + ".utcTimestamp'");
        jdbc.execute("CREATE ALIAS DATE_FORMAT FOR '"
                + WindCodeChangeDemoDataTest.class.getName() + ".formatDate'");
        // 使用生产列定义，保留 ODS 的 SEC_ID 长度约束。
        createTable(jdbc, "rrs_external_import_schema.sql", "rrs_securityinfo", "rrs_securityinfo");
        // 建立真实的 Wind 身份参考表。
        createTable(jdbc, "ais_inv_ods_schema.sql", "wind_windcustomcode", "ais_inv_ods.wind_windcustomcode");
        // 建立本次补充 Demo 的事件表。
        createTable(jdbc, "ais_inv_ods_schema.sql", "wind_cbondchangewindcode", "ais_inv_ods.wind_cbondchangewindcode");
        Configuration config = new Configuration(new Environment("demo", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        try (InputStream xml = getClass().getResourceAsStream("/mapper/WindCodeSyncMapper.xml")) {
            new XMLMapperBuilder(xml, config, "WindCodeSyncMapper.xml", config.getSqlFragments()).parse();
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String from = today.minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
        String to = today.format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int run = 0; run < 2; run++) {
            // 每次使用原始清空和 INSERT 语句，验证单表初始化不依赖额外会话变量。
            resetDemo(jdbc, "rrs_external_import_demo_data.sql", "rrs_securityinfo", "rrs_securityinfo");
            // 恢复三市场证券的原始 Wind 身份。
            resetDemo(jdbc, "ais_inv_ods_demo_data.sql", "wind_windcustomcode", "ais_inv_ods.wind_windcustomcode");
            // 日期表达式原样执行，初始化两次后记录数仍为八条。
            resetDemo(jdbc, "ais_inv_ods_demo_data.sql", "wind_cbondchangewindcode", "ais_inv_ods.wind_cbondchangewindcode");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ais_inv_ods.wind_cbondchangewindcode", Integer.class))
                    .isEqualTo(8);
            try (SqlSession session = new SqlSessionFactoryBuilder().build(config).openSession(true)) {
                WindCodeSyncMapper mapper = session.getMapper(WindCodeSyncMapper.class);
                List<WindCodeChangeDto> candidates = mapper.queryRecentWindCodeChangeList(from, to);
                assertThat(candidates).extracting(WindCodeChangeDto::getObjectId).containsExactly(
                        "WCH_DEMO_002", "WCH_DEMO_001", "WCH_DEMO_003", "WCH_DEMO_004", "WCH_DEMO_005", "WCH_DEMO_006");
                for (WindCodeChangeDto change : candidates) {
                    assertThat(change.getChangeDate()).matches("\\d{8}");
                    assertThat(change.getSecId()).hasSizeLessThanOrEqualTo(10);
                    String oldCode = change.getOldWindCode();
                    int marketSeparator = oldCode.lastIndexOf('.');
                    assertThat(change.getNewWindCode()).as("%s 仅变化数字末位", change.getObjectId())
                            .matches(Pattern.quote(oldCode.substring(0, marketSeparator - 1))
                                    + "\\d" + Pattern.quote(oldCode.substring(marketSeparator)))
                            .isNotEqualTo(oldCode);
                    if ("WCH_DEMO_006".equals(change.getObjectId())) {
                        assertThat(mapper.querySecurityBySecIdForUpdate(change.getSecId())).isNull();
                    } else {
                        assertThat(mapper.querySecurityBySecIdForUpdate(change.getSecId())).isNotNull();
                    }
                }
                assertThat(candidates.get(0).getChangeDate()).isEqualTo(from);
                assertThat(candidates.get(0).getNewWindCode()).isEqualTo(candidates.get(2).getOldWindCode());
                assertThat(candidates.get(0).getSecId()).isEqualTo(candidates.get(2).getSecId());
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ais_inv_ods.wind_cbondchangewindcode c"
                    + " JOIN rrs_securityinfo s ON s.sec_id=c.SEC_ID"
                    + " JOIN ais_inv_ods.wind_windcustomcode w ON w.S_INFO_WINDCODE=s.wind_code"
                    + " AND w.S_INFO_ASHARECODE=c.SEC_ID"
                    + " WHERE c.OBJECT_ID BETWEEN 'WCH_DEMO_001' AND 'WCH_DEMO_005'", Integer.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ais_inv_ods.wind_cbondchangewindcode c"
                    + " JOIN rrs_securityinfo s ON s.wind_code=c.S_INFO_NEWWINDCODE"
                    + " WHERE c.OBJECT_ID BETWEEN 'WCH_DEMO_001' AND 'WCH_DEMO_005'", Integer.class)).isZero();
        }
    }

    /** 为 H2 提供 MySQL UTC_TIMESTAMP 的无时区墙上时间语义。 */
    public static Timestamp utcTimestamp() {
        return Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC));
    }

    /** 为 H2 提供本 Demo 使用的 MySQL 日期格式化函数。 */
    public static String formatDate(Timestamp value, String pattern) {
        if ("%Y%m%d".equals(pattern)) {
            return value.toLocalDateTime().toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        }
        if ("%Y-%m-%d %H:%i:%s".equals(pattern)) {
            return value.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        throw new IllegalArgumentException("未支持的测试日期格式：" + pattern);
    }

    /** 仅移除 H2 不支持的 MySQL 表选项，其余保留生产 DDL。 */
    private void createTable(JdbcTemplate jdbc, String file, String table, String qualifiedTable) throws Exception {
        // 提取对应表结构。
        String ddl = extractStatement(file, "CREATE TABLE", table);
        jdbc.execute(ddl.replaceAll("(?s)\\)\\s*ENGINE.*", ")")
                .replace("CREATE TABLE `" + table + "`", "CREATE TABLE " + qualifiedTable));
    }

    /** 原样执行指定表的初始化语句，不清理或依赖其他表。 */
    private void resetDemo(JdbcTemplate jdbc, String file, String table, String qualifiedTable) throws Exception {
        // 提取本表在 Demo 中明确声明的清空范围。
        String truncate = extractStatement(file, "TRUNCATE TABLE", table);
        jdbc.execute(truncate.replace("`" + table + "`", qualifiedTable));
        // 原样执行插入内容，包括动态日期表达式。
        String insert = extractStatement(file, "INSERT INTO", table);
        jdbc.execute(insert.replace("INSERT INTO `" + table + "`", "INSERT INTO " + qualifiedTable));
    }

    /** 按 UTF-8 从原始脚本提取指定语句。 */
    private String extractStatement(String file, String operation, String table) throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get("sql", file)), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("(?s)" + operation + "\\s+`" + table + "`.*?;").matcher(sql);
        assertThat(matcher.find()).as("%s %s", operation, table).isTrue();
        return matcher.group();
    }
}
