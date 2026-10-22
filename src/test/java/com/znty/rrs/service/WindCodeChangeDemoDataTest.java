package com.znty.rrs.service;

import com.znty.rrs.entity.schedule.WindCodeChangeDto;
import com.znty.rrs.mapper.WindCodeSyncMapper;
import com.znty.rrs.mapper.TempSecurityCodeMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.mapper.SecurityMarketMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 DDL、Demo 和 Mapper 验证 Wind 换码样例，不连接业务数据库。 */
public class WindCodeChangeDemoDataTest {

    /** 每条测试独立的内存数据库。 */
    private DriverManagerDataSource source;
    /** Demo 数据及结果校验入口。 */
    private JdbcTemplate jdbc;

    /** 创建真实列定义和日期函数，不连接业务数据库。 */
    @Before
    public void setUp() throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 需要 Java 11 以上运行时", major >= 11);
        source = new DriverManagerDataSource();
        source.setDriverClassName("org.h2.Driver");
        source.setUrl("jdbc:h2:mem:wind_change_demo_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA ais_inv_ods");
        jdbc.execute("CREATE ALIAS IF NOT EXISTS UTC_TIMESTAMP FOR '"
                + WindCodeChangeDemoDataTest.class.getName() + ".utcTimestamp'");
        jdbc.execute("CREATE ALIAS DATE_FORMAT FOR '"
                + WindCodeChangeDemoDataTest.class.getName() + ".formatDate'");
        // 模拟页面保存的扫描参数，任务通过实际 Mapper 读取。
        createTable(jdbc, "rrs_scheduled_task_schema.sql", "sys_scheduled_task", "sys_scheduled_task");
        jdbc.update("INSERT INTO sys_scheduled_task(task_code,param_json,is_deleted) VALUES(?,?,0)",
                WindCodeSyncService.TASK_CODE, "{\"scanDays\":1}");
        // 使用生产列定义，保留 ODS 的 SEC_ID 长度约束。
        createTable(jdbc, "rrs_external_import_schema.sql", "rrs_securityinfo", "rrs_securityinfo");
        // 建立真实的 Wind 身份参考表。
        createTable(jdbc, "ais_inv_ods_schema.sql", "wind_windcustomcode", "ais_inv_ods.wind_windcustomcode");
        // 建立本次补充 Demo 的事件表。
        createTable(jdbc, "ais_inv_ods_schema.sql", "wind_cbondchangewindcode", "ais_inv_ods.wind_cbondchangewindcode");
        // 四类分支使用同一份临时代码原始登记和转换历史。
        createTable(jdbc, "rrs_temp_security_code_schema.sql", "rrs_temp_security_code", "rrs_temp_security_code");
        // 保留已有转正日志，验证再次换码不会覆盖历史。
        createTable(jdbc, "rrs_temp_security_code_schema.sql", "rrs_temp_security_code_update_log", "rrs_temp_security_code_update_log");
    }

    /** 用完即释放内存库。 */
    @After
    public void tearDown() {
        if (jdbc != null) {
            jdbc.execute("SET DB_CLOSE_DELAY 0");
        }
    }

    /** 验证重复初始化、数据库日期窗口、同券最新记录及历史过滤样例。 */
    @Test
    public void demoShouldProvideUsableChangesWithinCurrentWindow() throws Exception {
        Configuration config = new Configuration(new Environment("demo", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        try (InputStream xml = getClass().getResourceAsStream("/mapper/WindCodeSyncMapper.xml")) {
            new XMLMapperBuilder(xml, config, "WindCodeSyncMapper.xml", config.getSqlFragments()).parse();
        }
        String from = jdbc.queryForObject("SELECT DATE_FORMAT(NOW() - INTERVAL '1' DAY, '%Y%m%d')", String.class);
        String to = jdbc.queryForObject("SELECT DATE_FORMAT(NOW(), '%Y%m%d')", String.class);
        for (int run = 0; run < 2; run++) {
            // 每次使用原始清空和 INSERT 语句，验证单表初始化不依赖额外会话变量。
            resetDemo(jdbc, "rrs_external_import_demo_data.sql", "rrs_securityinfo", "rrs_securityinfo");
            // 恢复三市场证券的原始 Wind 身份。
            resetDemo(jdbc, "ais_inv_ods_demo_data.sql", "wind_windcustomcode", "ais_inv_ods.wind_windcustomcode");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_securityinfo s LEFT JOIN ais_inv_ods.wind_windcustomcode w"
                    + " ON w.S_INFO_WINDCODE=s.wind_code WHERE w.S_INFO_WINDCODE IS NULL", Integer.class)).isZero();
            // 日期表达式原样执行，初始化两次后记录数仍为十三条。
            resetDemo(jdbc, "ais_inv_ods_demo_data.sql", "wind_cbondchangewindcode", "ais_inv_ods.wind_cbondchangewindcode");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ais_inv_ods.wind_cbondchangewindcode", Integer.class))
                    .isEqualTo(13);
            try (SqlSession session = new SqlSessionFactoryBuilder().build(config).openSession(true)) {
                WindCodeSyncMapper mapper = session.getMapper(WindCodeSyncMapper.class);
                List<WindCodeChangeDto> candidates = mapper.queryRecentWindCodeChangeList(1);
                assertThat(candidates).extracting(WindCodeChangeDto::getObjectId).containsExactlyInAnyOrder(
                        "WCH_DEMO_001", "WCH_DEMO_003", "WCH_DEMO_004", "WCH_DEMO_005", "WCH_DEMO_006",
                        "WCH_DEMO_009", "WCH_DEMO_010", "WCH_DEMO_011", "WCH_DEMO_012", "WCH_DEMO_013");
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
                assertThat(candidates).extracting(WindCodeChangeDto::getChangeDate).containsOnly(to);
                assertThat(candidates).filteredOn(change -> "S9001003".equals(change.getSecId()))
                        .extracting(WindCodeChangeDto::getObjectId).containsExactly("WCH_DEMO_003");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ais_inv_ods.wind_cbondchangewindcode"
                        + " WHERE SEC_ID='S9001003' AND S_INFO_WINDCODE='XYB007.IB'", Integer.class)).isEqualTo(2);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ais_inv_ods.wind_cbondchangewindcode c"
                    + " JOIN rrs_securityinfo s ON s.sec_id=c.SEC_ID"
                    + " JOIN ais_inv_ods.wind_windcustomcode w ON w.S_INFO_WINDCODE=s.wind_code"
                    + " AND w.S_INFO_ASHARECODE=c.SEC_ID"
                    + " WHERE c.OBJECT_ID BETWEEN 'WCH_DEMO_001' AND 'WCH_DEMO_005'", Integer.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ais_inv_ods.wind_cbondchangewindcode c"
                    + " JOIN rrs_securityinfo s ON s.wind_code=c.S_INFO_NEWWINDCODE"
                    + " WHERE c.S_INFO_OLDWINDCODE != c.S_INFO_NEWWINDCODE AND c.CHANGE_DATE >= ?", Integer.class, from)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*)-COUNT(DISTINCT sec_id) FROM rrs_securityinfo", Integer.class)).isZero();
        }
    }

    /** 原始 Demo 全链路执行，验证四类来源、出入池、在途和二次执行幂等。 */
    @Test
    public void demoShouldExecuteAllBranchesAndSkipCompletedEvents() throws Exception {
        // 加载实际池配置、调库流水、步骤和快照结构。
        for (String file : Arrays.asList("rrs_pool_init_schema.sql", "rrs_security_pool_adjust_schema.sql",
                "rrs_crmw_pool_status_schema.sql", "rrs_adjust_snapshot_schema.sql", "rrs_dict_schema.sql")) {
            String sql = new String(Files.readAllBytes(Paths.get("sql", file)), StandardCharsets.UTF_8);
            Matcher tables = Pattern.compile("CREATE TABLE `([^`]+)`").matcher(sql);
            while (tables.find()) {
                // 按表执行生产 DDL，避免测试改写真实字段。
                createTable(jdbc, file, tables.group(1), tables.group(1));
            }
        }
        // 装载真实主档、登记和已有转换历史。
        resetDemo(jdbc, "rrs_external_import_demo_data.sql", "rrs_securityinfo", "rrs_securityinfo");
        // 登记保留创建时的临时身份，主档使用已映射的真实 SEC_ID。
        resetDemo(jdbc, "rrs_temp_security_code_demo_data.sql", "rrs_temp_security_code", "rrs_temp_security_code");
        // 原转正日志也纳入历史保护校验。
        resetDemo(jdbc, "rrs_temp_security_code_demo_data.sql", "rrs_temp_security_code_update_log", "rrs_temp_security_code_update_log");
        // 建立普通池与 CRMW 的正式配置。
        resetDemo(jdbc, "rrs_pool_init_demo_data.sql", "ip_investment_pool", "ip_investment_pool");
        // 保留现有互斥和联动关系，验证实际转换条件。
        resetDemo(jdbc, "rrs_pool_init_demo_data.sql", "ip_pool_relation", "ip_pool_relation");
        for (String table : Arrays.asList("ip_pool_status", "ip_adjust_log", "ip_adjust_step")) {
            // 装载原在池持仓、在途申请及审批步骤。
            resetDemo(jdbc, "rrs_security_pool_adjust_demo_data.sql", table, table);
        }
        // MTN001.IB 同时作为普通池证券和 CRMW 标的。
        resetDemo(jdbc, "rrs_crmw_pool_status_demo_data.sql", "ip_pool_status_crmw", "ip_pool_status_crmw");
        // 字典 Demo 使用未加反引号的表名，保留原 INSERT。
        jdbc.execute(extractStatement("rrs_dict_demo_data.sql", "INSERT INTO", "dict_security_type"));
        // 真实事件含连续换码、主档缺失及两类过滤记录。
        resetDemo(jdbc, "ais_inv_ods_demo_data.sql", "wind_cbondchangewindcode", "ais_inv_ods.wind_cbondchangewindcode");
        // 生产 Mapper、市场查询和事务代理均参与本次执行。
        WindCodeSyncService task = createTask();
        ScheduledTaskResult first = task.execute();
        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getMessage()).as(first.getDetailLog()).isEqualTo("成功 9 条，主档缺失跳过 1 条，已是新码跳过 0 条，重复跳过 0 条，失败 0 条");
        assertThat(first.getDetailLog()).contains("特别提示", "SEC_ID=S9999999").doesNotContain("[ERROR]");
        assertThat(first.getDetailLog()).contains("换码成功：SEC_ID=S9001046", "变更记录旧代码=PPN000.IB");
        assertThat(first.getAffectedCount()).isEqualTo(9);
        assertThat(jdbc.queryForList("SELECT wind_code FROM rrs_securityinfo WHERE sec_id IN ('S9001003','S9001001','S9001002') ORDER BY sec_id", String.class))
                .containsExactly("198106.SH", "128106.SZ", "XYB007.IB");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_securityinfo WHERE wind_code='XYB006.IB'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log WHERE security_code='XYB006.IB'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_security_code WHERE temp_security_code='XYB006.IB'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT memo FROM rrs_temp_security_code WHERE temp_security_code='XYB001.IB'"
                + " AND security_code='XYB007.IB' AND status='updated'", String.class))
                .contains(":XYB006.IB:XYB007.IB:");
        assertThat(jdbc.queryForObject("SELECT security_source FROM rrs_securityinfo WHERE wind_code='108008906.IB'", String.class))
                .isEqualTo("official");
        assertThat(jdbc.queryForObject("SELECT security_source FROM rrs_securityinfo WHERE sec_id='S9001050'", String.class))
                .isEqualTo("temp_converted");
        assertThat(jdbc.queryForObject("SELECT wind_code FROM rrs_securityinfo WHERE sec_id='S9001050'", String.class))
                .isEqualTo("WCTMP006.IB");
        assertThat(jdbc.queryForObject("SELECT market FROM rrs_securityinfo WHERE sec_id='S9001050'", String.class)).isEqualTo("NIB");
        assertThat(jdbc.queryForObject("SELECT memo FROM rrs_temp_security_code WHERE id=4", String.class))
                .startsWith("Wind待转正样例：").contains("[wind_code_sync:");
        assertThat(jdbc.queryForObject("SELECT sec_id FROM rrs_temp_security_code WHERE id=4", String.class)).isEqualTo("TEMP_WCTMP001.IB");
        assertThat(jdbc.queryForObject("SELECT security_code FROM rrs_temp_security_code WHERE id=4 AND status='updated'", String.class))
                .isEqualTo("WCTMP006.IB");
        assertThat(jdbc.queryForObject("SELECT security_source FROM rrs_securityinfo WHERE wind_code='ABN007.IB'", String.class))
                .isEqualTo("temp_converted");
        assertThat(jdbc.queryForObject("SELECT security_code FROM rrs_temp_security_code WHERE id=2 AND temp_security_code='TMP002.IB'", String.class))
                .isEqualTo("ABN002.IB");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_security_code_update_log WHERE temp_security_code='TMP002.IB' AND security_code='ABN002.IB'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_security_code WHERE temp_security_code='ABN002.IB' AND security_code='ABN007.IB' AND oprt_source='job'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_security_code WHERE temp_security_code='WCDMISS001.IB'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_securityinfo WHERE sec_id='S9999999'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT wind_code FROM rrs_securityinfo WHERE sec_id='S9001046'", String.class)).isEqualTo("PPN006.IB");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_securityinfo WHERE wind_code='PPN000.IB'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_security_code WHERE temp_security_code='PPN000.IB'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_security_code WHERE temp_security_code='PPN001.IB'"
                + " AND security_code='PPN006.IB' AND status='updated'", Integer.class)).isEqualTo(1);
        // 检查普通池与 CRMW 标的均完成旧出新入，并保留终态旧流水。
        assertPoolMove("MTN001.IB", "MTN006.IB", "ip_pool_status", 4L);
        // CRMW 凭证保持原代码，只换标的。
        assertPoolMove("MTN001.IB", "MTN006.IB", "ip_pool_status_crmw", 18L);
        assertThat(jdbc.queryForObject("SELECT crmw_scode FROM ip_pool_status_crmw WHERE security_code='MTN006.IB' AND is_deleted=0", String.class))
                .isEqualTo("CRMW001.IB");
        assertThat(jdbc.queryForObject("SELECT security_code FROM ip_adjust_log WHERE id=1", String.class)).isEqualTo("MTN001.IB");
        assertThat(jdbc.queryForObject("SELECT security_code FROM ip_adjust_log WHERE id=8 AND audit_status='00' AND adjust_batch_no='BOND20260710001' AND flow_id=101", String.class))
                .isEqualTo("GSB006.SH");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_step WHERE adjust_log_id=8", Integer.class)).isGreaterThan(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status WHERE security_code='GSB006.SH' AND is_deleted=0", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_security_snapshot WHERE adjust_log_id=8 AND wind_code='GSB006.SH'", Integer.class))
                .isEqualTo(1);
        // 无持仓的两类临时来源仅改主档和登记，不新增调库流水。
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log WHERE security_code IN ('WCTMP001.IB','WCTMP006.IB','ABN002.IB','ABN007.IB')", Integer.class)).isZero();
        Map<String, Integer> before = new LinkedHashMap<>();
        for (String table : Arrays.asList("rrs_securityinfo", "rrs_temp_security_code", "rrs_temp_security_code_update_log",
                "ip_adjust_log", "ip_pool_status", "ip_pool_status_crmw", "ip_adjust_step",
                "ip_adjust_security_snapshot", "ip_adjust_security_snapshot_crmw")) {
            before.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        }
        ScheduledTaskResult second = task.execute();
        assertThat(second.isSuccess()).isTrue();
        assertThat(second.getMessage()).as(second.getDetailLog()).isEqualTo("成功 0 条，主档缺失跳过 1 条，已是新码跳过 0 条，重复跳过 9 条，失败 0 条");
        for (Map.Entry<String, Integer> entry : before.entrySet()) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + entry.getKey(), Integer.class)).as(entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    /** 验证持仓已替换，并有审批通过的旧码出库、新码入库流水。 */
    private void assertPoolMove(String oldCode, String newCode, String table, Long poolId) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE security_code=? AND target_pool_id=? AND is_deleted=0", Integer.class, oldCode, poolId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE security_code=? AND target_pool_id=? AND is_deleted=0", Integer.class, newCode, poolId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log WHERE security_code=? AND target_pool_id=? AND adjust_mode='调出' AND audit_status='20'", Integer.class, oldCode, poolId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log WHERE security_code=? AND target_pool_id=? AND adjust_mode='调入' AND audit_status='20'", Integer.class, newCode, poolId)).isEqualTo(1);
    }

    /** 装配实际 Mapper 与市场服务，使用 Spring 事务代理保持单事件事务语义。 */
    private WindCodeSyncService createTask() throws Exception {
        Configuration config = new Configuration(new Environment("demo", new SpringManagedTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        for (String name : Arrays.asList("ScheduledTaskMapper", "WindCodeSyncMapper", "TempSecurityCodeMapper", "SecurityPoolAdjustMapper",
                "CrmwPoolAdjustMapper", "SecurityMarketMapper")) {
            try (InputStream xml = getClass().getResourceAsStream("/mapper/" + name + ".xml")) {
                new XMLMapperBuilder(xml, config, name + ".xml", config.getSqlFragments()).parse();
            }
        }
        SqlSessionTemplate session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        SecurityMarketService markets = new SecurityMarketService();
        ReflectionTestUtils.setField(markets, "securityMarketMapper", session.getMapper(SecurityMarketMapper.class));
        TempSecurityCodeBusinessConverter converter = new TempSecurityCodeBusinessConverter();
        ReflectionTestUtils.setField(converter, "tempSecurityCodeMapper", session.getMapper(TempSecurityCodeMapper.class));
        ReflectionTestUtils.setField(converter, "securityPoolAdjustMapper", session.getMapper(SecurityPoolAdjustMapper.class));
        ReflectionTestUtils.setField(converter, "crmwPoolAdjustMapper", session.getMapper(CrmwPoolAdjustMapper.class));
        ReflectionTestUtils.setField(converter, "securityMarketService", markets);
        TempSecurityCodeService target = new TempSecurityCodeService();
        ReflectionTestUtils.setField(target, "tempSecurityCodeMapper", session.getMapper(TempSecurityCodeMapper.class));
        ReflectionTestUtils.setField(target, "windCodeSyncMapper", session.getMapper(WindCodeSyncMapper.class));
        ReflectionTestUtils.setField(target, "securityMarketService", markets);
        ReflectionTestUtils.setField(target, "businessConverter", converter);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source), new AnnotationTransactionAttributeSource()));
        WindCodeSyncService task = new WindCodeSyncService();
        ReflectionTestUtils.setField(task, "scheduledTaskMapper", session.getMapper(ScheduledTaskMapper.class));
        ReflectionTestUtils.setField(task, "windCodeSyncMapper", session.getMapper(WindCodeSyncMapper.class));
        ReflectionTestUtils.setField(task, "tempSecurityCodeService", proxy.getProxy());
        return task;
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

    /** 执行指定表的初始化语句，JSON 字面量仅补充 H2 所需的类型提示。 */
    private void resetDemo(JdbcTemplate jdbc, String file, String table, String qualifiedTable) throws Exception {
        // 提取本表在 Demo 中明确声明的清空范围。
        String truncate = extractStatement(file, "TRUNCATE TABLE", table);
        jdbc.execute(truncate.replace("`" + table + "`", qualifiedTable));
        // 原样执行插入内容，包括动态日期表达式。
        String insert = extractStatement(file, "INSERT INTO", table);
        if ("ip_investment_pool".equals(table)) {
            // MySQL 向 JSON 列写入数组文本会解析为数组，H2 需显式声明 FORMAT JSON。
            insert = insert.replaceAll("('\\[[^']*\\]')", "$1 FORMAT JSON");
        }
        jdbc.execute(insert.replace("INSERT INTO `" + table + "`", "INSERT INTO " + qualifiedTable));
    }

    /** 按 UTF-8 从原始脚本提取指定语句。 */
    private String extractStatement(String file, String operation, String table) throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get("sql", file)), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("(?s)" + operation + "\\s+`?" + table + "`?\\s*\\(.*?;").matcher(sql);
        if ("TRUNCATE TABLE".equals(operation)) {
            matcher = Pattern.compile("(?s)" + operation + "\\s+`" + table + "`.*?;").matcher(sql);
        }
        assertThat(matcher.find()).as("%s %s", operation, table).isTrue();
        return matcher.group();
    }
}
