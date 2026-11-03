package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwPoolAdjustReq;
import com.znty.rrs.mapper.AbsIdentityMapper;
import com.znty.rrs.mapper.BatchCrmwPoolAdjustMapper;
import com.znty.rrs.mapper.CreditBondGradeRuleMapper;
import com.znty.rrs.mapper.GradeRuleAlertMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 在独立内存库执行全库 Demo 和场景白名单，验证当前业务代码实际读取的数据 */
public class DemoRuntimeConsistencyTest {

    /** 保持本次内存库连接 */
    private Connection connection;
    /** 复用脚本白名单和 SQL 拆分逻辑 */
    private ScriptToolService scripts;
    /** 使用真实身份和 CRMW 候选查询 */
    private SqlSessionFactory factory;

    /** 创建真实表结构并按全库初始化任务的顺序灌入样本 */
    @Before
    public void setUp() throws Exception {
        String version = System.getProperty("java.specification.version");
        Assume.assumeTrue("H2 2.3.232 需要 Java 11 以上运行时", !version.startsWith("1."));
        String url = "jdbc:h2:mem:demo_runtime_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false";
        connection = DriverManager.getConnection(url);
        connection.createStatement().execute("CREATE SCHEMA znty_rrs");
        connection.createStatement().execute("CREATE SCHEMA ais_inv_analysis");
        connection.createStatement().execute("CREATE SCHEMA ais_inv_ods");
        for (String schema : new String[]{"znty_rrs", "ais_inv_analysis", "ais_inv_ods"}) {
            connection.createStatement().execute("CREATE ALIAS " + schema + ".DATE_FORMAT FOR '"
                    + DemoRuntimeConsistencyTest.class.getName() + ".formatDate'");
        }
        scripts = new ScriptToolService();
        // 仅在隔离内存库执行白名单脚本，不连接本地 MySQL
        initialize(true);
        initialize(false);
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver", url + ";SCHEMA=znty_rrs", null, null);
        ReflectionTestUtils.setField(scripts, "dataSource", source);
        Configuration config = new Configuration(new Environment("demo-runtime", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        for (String name : new String[]{"AbsIdentityMapper", "BatchCrmwPoolAdjustMapper", "SecurityPoolAdjustMapper",
                "InvestmentPoolMapper", "CreditBondGradeRuleMapper", "GradeRuleAlertMapper"}) {
            String resource = "mapper/" + name + ".xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
        }
        factory = new SqlSessionFactoryBuilder().build(config);
    }

    /** 关闭连接后销毁本次测试的内存库 */
    @After
    public void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    /** 基础样本重复初始化后仍可查询 ABS 身份，并提供产品库1来源的 CRMW 组合 */
    @Test
    public void baseDemoShouldSatisfyRuntimeContractsAfterRepeatedInitialization() throws Exception {
        for (int run = 0; run < 2; run++) {
            // 每轮都核对真实主档、申请快照、流程步骤和池状态
            verifyRuntimeContracts();
            try (SqlSession session = factory.openSession()) {
                BatchCrmwPoolAdjustReq req = new BatchCrmwPoolAdjustReq();
                req.setPoolId(18L);
                assertThat(session.getMapper(BatchCrmwPoolAdjustMapper.class).queryInboundCandidatePage(req))
                        .as("基础 Demo 应有双方均在债券产品库1的可调入 CRMW 组合").isNotEmpty();
            }
            // 直接执行全库 Demo 白名单，验证实际重置顺序
            initialize(false);
        }
    }

    /** 全部场景可同时存在且重复生成不产生冲突，重新初始化能清除场景快照 */
    @Test
    public void allScenesShouldRemainConsistentAndRepeatable() throws Exception {
        Map<String, ?> scenes = ReflectionTestUtils.invokeMethod(scripts, "queryDemoSceneMap");
        assertThat(scenes).hasSizeGreaterThanOrEqualTo(14);
        int firstCount = -1;
        for (int run = 0; run < 2; run++) {
            for (String code : scenes.keySet()) {
                // 调用生产场景执行入口，实际验证提交、清理和事务边界
                ReflectionTestUtils.invokeMethod(scripts, "executeDemoSceneSql", code, new ArrayList<String>());
                // 场景之间不能靠执行顺序掩盖主档或快照冲突
                verifyRuntimeContracts();
            }
            int count = count("SELECT COUNT(*) FROM znty_rrs.ip_adjust_log");
            if (run == 0) {
                firstCount = count;
            } else {
                assertThat(count).as("重复生成全部场景的日志数量").isEqualTo(firstCount);
            }
        }
        // 重置运行态及快照后不应残留场景日志或孤立快照
        initialize(false);
        verifyRuntimeContracts();
        assertThat(count("SELECT COUNT(*) FROM znty_rrs.ip_adjust_log WHERE id < 0 OR adjust_batch_no LIKE '%DEMO%'")).isZero();
    }

    /** 场景依赖被删除时应报错，并回滚旧场景的清理操作 */
    @Test
    public void sceneGenerationShouldRollBackWhenRequiredMasterIsMissing() throws Exception {
        ReflectionTestUtils.invokeMethod(scripts, "executeDemoSceneSql", "forbidden-abs-pending", new ArrayList<String>());
        int logs = count("SELECT COUNT(*) FROM ip_adjust_log");
        int snapshots = count("SELECT COUNT(*) FROM ip_adjust_security_snapshot");
        // 删除该场景需要的流程定义，模拟只初始化部分基础数据
        execute("DELETE FROM wf_flow_definition WHERE id=113");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(scripts, "executeDemoSceneSql", "forbidden-abs-pending", new ArrayList<String>()))
                .hasMessageContaining("主数据不完整");
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log")).isEqualTo(logs);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot")).isEqualTo(snapshots);
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_step WHERE adjust_batch_no='BOND_DEMO_forbidden_abs_pending' AND step_status='pending'"))
                .as("失败后原申请的待办仍然保留").isEqualTo(1);
    }

    /** 从真实入库记录执行分级提醒，不能只检查主体主档而遗漏快照中的实际评级主体 */
    @Test
    public void demoGradingScanShouldUseActualSelectedCompany() {
        // 直接执行当前任务，复现缺少实际评级主体代码或快照仍含多个候选代码的问题
        verifyGradingScan();
    }

    /** 已成功 ABS 全部出库仍固定主体，首次驳回或撤回不会绑定主档 */
    @Test
    public void absDemoShouldRespectPermanentIdentityAfterExitAndFailedFirstApplications() throws Exception {
        // 清空已成功样本的当前池，保留历史入库日志和主档身份
        execute("DELETE FROM ip_pool_status WHERE security_code='108008901.IB'");
        execute("DELETE FROM ip_pool_status_crmw WHERE security_code='108008901.IB'");
        try (SqlSession session = factory.openSession()) {
            AbsIdentityMapper mapper = session.getMapper(AbsIdentityMapper.class);
            AbsIdentityService identity = new AbsIdentityService();
            ReflectionTestUtils.setField(identity, "absIdentityMapper", mapper);
            assertThat(mapper.queryOccupiedIdentityCodes("108008901.IB")).contains("C10008");
            assertThat(identity.queryIdentityFailure("108008901.IB", "C10008")).isNull();
            assertThat(identity.queryIdentityFailure("108008901.IB", "C10010")).contains("C10008");
        }
        for (String status : new String[]{"21", "99"}) {
            // 为从未成功的另一条 ABS 准备终态申请和所选主体快照，不向主档写入身份
            execute("INSERT INTO ip_adjust_log (security_code,security_type,adjust_mode,pool_type,audit_status,is_deleted)"
                    + " VALUES ('108008902.IB','abs','调入','forbidden','" + status + "',0)");
            execute("INSERT INTO ip_adjust_security_snapshot (adjust_log_id,wind_code,issuer_code,guarantor_id,is_deleted)"
                    + " SELECT MAX(id),'108008902.IB','C10010','C10010',0 FROM ip_adjust_log");
            try (SqlSession session = factory.openSession()) {
                AbsIdentityMapper mapper = session.getMapper(AbsIdentityMapper.class);
                assertThat(mapper.queryOccupiedIdentityCodes("108008902.IB")).isEmpty();
                assertThat(session.getMapper(SecurityPoolAdjustMapper.class).querySecurityBoByCode("108008902.IB").getIssuerCode()).isNull();
            }
        }
    }

    /** 按脚本工具实际白名单执行全库建表或 Demo 初始化 */
    private void initialize(boolean schema) throws Exception {
        List<String> files = ReflectionTestUtils.invokeMethod(scripts, "queryInitializationFiles", schema, true);
        for (String file : files) {
            String text = new String(Files.readAllBytes(Paths.get("sql", file)), StandardCharsets.UTF_8);
            List<String> statements = ReflectionTestUtils.invokeMethod(scripts, "splitSqlStatements", text);
            for (String statement : statements) {
                String sql = ReflectionTestUtils.invokeMethod(scripts, "normalizeExecutableSql", statement);
                if (sql.isEmpty() || sql.startsWith("CREATE DATABASE") || sql.startsWith("SET ")) {
                    continue;
                }
                if (schema && !sql.startsWith("CREATE TABLE") && !sql.startsWith("USE ")) {
                    continue;
                }
                if (schema) {
                    sql = sql.replaceAll("(?s)\\)\\s*ENGINE\\s*=.*", ")");
                }
                // 执行真实表定义和 INSERT，任何失败都保留文件名供定位
                try {
                    execute(sql);
                } catch (Exception ex) {
                    throw new IllegalStateException("Demo 脚本执行失败：" + file, ex);
                }
            }
        }
        connection.setSchema("znty_rrs");
    }

    /** 保持 SQL 业务条件，仅将日期函数换成 H2 的等价表达式 */
    private void execute(String sql) throws Exception {
        String compatible = sql.replaceAll("DATE_SUB\\(NOW\\(\\), INTERVAL (\\d+) DAY\\)", "DATEADD('DAY', -$1, CURRENT_TIMESTAMP)")
                .replace("DATE_FORMAT(NOW(), '%Y%m%d')", "FORMATDATETIME(CURRENT_TIMESTAMP, 'yyyyMMdd')")
                .replace("DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s')", "FORMATDATETIME(CURRENT_TIMESTAMP, 'yyyy-MM-dd HH:mm:ss')")
                .replaceAll("DATE_FORMAT\\(CURDATE\\(\\), '%Y%m%d'\\)", "FORMATDATETIME(CURRENT_DATE, 'yyyyMMdd')")
                .replaceAll("DATE_FORMAT\\(DATE_SUB\\(CURDATE\\(\\), INTERVAL (\\d+) DAY\\), '%Y%m%d'\\)",
                        "FORMATDATETIME(DATEADD('DAY', -$1, CURRENT_DATE), 'yyyyMMdd')")
                .replaceAll("DATE_FORMAT\\(DATE_ADD\\(CURDATE\\(\\), INTERVAL (\\d+) DAY\\), '%Y%m%d'\\)",
                        "FORMATDATETIME(DATEADD('DAY', $1, CURRENT_DATE), 'yyyyMMdd')");
        try (Statement statement = connection.createStatement()) {
            statement.execute(compatible);
        }
    }

    /** 核对跨表关联，并调用当前 ABS 服务检查有效在库和在途身份 */
    private void verifyRuntimeContracts() throws Exception {
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log WHERE id<=0")).as("场景日志使用有效的自增 ID").isZero();
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log l INNER JOIN ip_investment_pool p ON p.id=l.target_pool_id"
                + " WHERE l.adjust_batch_no LIKE '%DEMO%' AND l.audit_status IN ('00','11')"
                + " AND l.flow_id NOT IN (p.in_flow_id,p.simple_in_flow_id,p.batch_in_flow_id) AND l.flow_key NOT LIKE '%upgrade%'"))
                .as("在途场景必须使用目标池可选的真实流程").isZero();
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log l LEFT JOIN rrs_securityinfo s ON s.wind_code=l.security_code"
                + " LEFT JOIN ais_inv_ods.wind_compintroduction c ON c.COMP_ID=l.security_code"
                + " WHERE (l.security_type='company' AND (c.COMP_ID IS NULL OR l.security_short_name<>c.COMP_NAME))"
                + " OR (l.security_type<>'company' AND (s.wind_code IS NULL OR l.security_type<>s.security_type OR l.security_short_name<>s.short_name))"))
                .as("所有申请对象及名称应与当前主数据一致").isZero();
        for (String suffix : new String[]{"", "_crmw"}) {
            assertThat(count("SELECT COUNT(*) FROM ip_pool_status" + suffix + " p LEFT JOIN ip_adjust_log l ON l.id=p.adjust_log_id"
                    + " LEFT JOIN ip_investment_pool pool ON pool.id=p.target_pool_id"
                    + " WHERE l.id IS NULL OR pool.id IS NULL OR l.audit_status<>'20' OR l.adjust_mode<>'调入'"
                    + " OR l.security_code<>p.security_code OR l.target_pool_id<>p.target_pool_id OR l.adjust_batch_no<>p.adjust_batch_no"))
                    .as("%s 池状态只能来自相应已通过申请", suffix).isZero();
            assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot" + suffix + " s LEFT JOIN ip_adjust_log l ON l.id=s.adjust_log_id"
                    + " WHERE l.id IS NULL OR l.security_code<>s.wind_code"))
                    .as("%s 快照必须关联真实申请对象", suffix).isZero();
            assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot" + suffix + " s"
                    + " WHERE (s.abs_flag=1 OR s.security_type='abs' OR s.guarant_flag=1)"
                    + " AND (s.guarantor_id IS NULL OR TRIM(s.guarantor_id)='' OR s.guarantor_id LIKE '%,%')"))
                    .as("%s ABS 或担保债快照必须记录单一本次评级主体", suffix).isZero();
            assertThat(count("SELECT COUNT(*) FROM ip_adjust_security_snapshot" + suffix + " s"
                    + " WHERE (s.abs_flag=1 OR s.security_type='abs') AND s.guarantor_id<>s.issuer_code"))
                    .as("%s ABS 评级主体与永久固定主体一致", suffix).isZero();
        }
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log l WHERE l.security_type<>'company' AND"
                + " ((l.pool_type<>'crmw' AND (SELECT COUNT(*) FROM ip_adjust_security_snapshot s WHERE s.adjust_log_id=l.id AND s.wind_code=l.security_code AND s.is_deleted=0)<>1)"
                + " OR (l.pool_type='crmw' AND (SELECT COUNT(*) FROM ip_adjust_security_snapshot_crmw s WHERE s.adjust_log_id=l.id AND s.wind_code=l.security_code AND s.is_deleted=0)<>1))"))
                .as("每笔证券或 CRMW 申请必须有且仅有自己的快照").isZero();
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_step s LEFT JOIN wf_flow_node n ON n.id=s.flow_node_id"
                + " LEFT JOIN wf_node_approval_config cfg ON cfg.node_id=n.id"
                + " LEFT JOIN ais_inv_analysis.t_sys_user u ON CAST(u.id AS VARCHAR)=s.handler_id"
                + " WHERE n.id IS NULL OR s.node_code<>n.node_id OR s.node_label<>n.label OR s.node_type<>n.node_type"
                + " OR COALESCE(s.approval_strategy,'')<>COALESCE(cfg.approval_strategy,'')"
                + " OR (s.handler_id IS NOT NULL AND (u.id IS NULL OR s.handler_name<>u.name))"))
                .as("步骤使用真实流程节点、策略和用户").isZero();
        assertThat(count("SELECT COUNT(*) FROM ip_adjust_log l WHERE l.audit_status IN ('00','11') AND NOT EXISTS"
                + " (SELECT 1 FROM ip_adjust_step s WHERE s.adjust_batch_no=l.adjust_batch_no AND s.step_status='pending')"))
                .as("未结束申请必须有可处理节点").isZero();
        assertThat(count("SELECT COUNT(*) FROM (SELECT security_code,target_pool_id,COUNT(*) n FROM ip_pool_status"
                + " WHERE audit_status='20' AND is_deleted=0 GROUP BY security_code,target_pool_id HAVING COUNT(*)>1) duplicates"))
                .as("同证券同目标池不重复入池").isZero();
        try (SqlSession session = factory.openSession(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT * FROM rrs_securityinfo WHERE abs_flag=1 OR security_type='abs'")) {
            AbsIdentityMapper mapper = session.getMapper(AbsIdentityMapper.class);
            AbsIdentityService identity = new AbsIdentityService();
            ReflectionTestUtils.setField(identity, "absIdentityMapper", mapper);
            while (rows.next()) {
                SecurityInfoBo master = new SecurityInfoBo();
                master.setWindCode(rows.getString("wind_code"));
                master.setSecurityType(rows.getString("security_type"));
                master.setAbsFlag(rows.getInt("abs_flag"));
                master.setIssuerCode(rows.getString("issuer_code"));
                master.setIssuer(rows.getString("issuer"));
                master.setRatingBondissuer(rows.getString("rating_bondissuer"));
                master.setRatingBondissuerAgency(rows.getString("rating_bondissuer_agency"));
                master.setInnerIssuerRating(rows.getString("inner_issuer_rating"));
                // 当前服务会直接暴露空身份、跨池主体冲突和在途快照缺失
                SecurityInfoBo occupied = identity.queryCurrentIdentity(master);
                if (!mapper.queryOccupiedIdentityCodes(master.getWindCode()).isEmpty()) {
                    assertThat(occupied).as("ABS %s 的有效主体", master.getWindCode()).isNotNull();
                    assertThat(occupied.getIssuerCode()).isNotBlank();
                    assertThat(occupied.getIssuer()).isNotBlank();
                    assertThat(occupied.getInnerIssuerRating()).isNotBlank();
                    assertThat(identity.queryIdentityFailure(master.getWindCode(), occupied.getIssuerCode())).isNull();
                }
            }
        }
        // 每个场景生成后实际扫描，防止普通和 CRMW 快照字段再次退回候选列表
        verifyGradingScan();
    }

    /** 复用真实 Mapper 和服务运行分级提醒，避免仅靠字段存在性断言漏掉业务取数要求 */
    private void verifyGradingScan() {
        try (SqlSession session = factory.openSession(true)) {
            SecurityPoolAdjustMapper securityMapper = session.getMapper(SecurityPoolAdjustMapper.class);
            AbsIdentityService identity = new AbsIdentityService();
            ReflectionTestUtils.setField(identity, "absIdentityMapper", session.getMapper(AbsIdentityMapper.class));
            ReflectionTestUtils.setField(identity, "securityPoolAdjustMapper", securityMapper);
            SecurityPoolAdjustService security = new SecurityPoolAdjustService();
            ReflectionTestUtils.setField(security, "securityPoolAdjustMapper", securityMapper);
            ReflectionTestUtils.setField(security, "investmentPoolMapper", session.getMapper(InvestmentPoolMapper.class));
            ReflectionTestUtils.setField(security, "creditBondGradeRuleMapper", session.getMapper(CreditBondGradeRuleMapper.class));
            ReflectionTestUtils.setField(security, "absIdentityService", identity);
            GradeRuleAlertService alerts = new GradeRuleAlertService();
            ReflectionTestUtils.setField(alerts, "gradeRuleAlertMapper", session.getMapper(GradeRuleAlertMapper.class));
            ReflectionTestUtils.setField(alerts, "securityPoolAdjustMapper", securityMapper);
            ReflectionTestUtils.setField(alerts, "securityPoolAdjustService", security);
            assertThat(alerts.execute().isSuccess()).as("Demo 分级提醒任务正常完成").isTrue();
        }
    }

    /** 执行只读计数，供跨表契约断言使用 */
    private int count(String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getInt(1);
        }
    }

    /** 将样本使用的 MySQL 日期格式转换为等价 Java 格式，仅供 H2 调用 */
    public static String formatDate(Timestamp date, String pattern) {
        if (date == null) {
            return null;
        }
        return new SimpleDateFormat(pattern.replace("%Y", "yyyy").replace("%m", "MM").replace("%d", "dd")
                .replace("%H", "HH").replace("%i", "mm").replace("%s", "ss")).format(date);
    }
}
