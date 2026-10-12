package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.entity.common.RoleDto;
import com.znty.rrs.entity.common.UserDto;
import com.znty.rrs.entity.flow.FlowReq;
import com.znty.rrs.entity.mymatters.MyMattersDto;
import com.znty.rrs.entity.mymatters.MyMattersReq;
import com.znty.rrs.mapper.FlowMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 全量 Demo 脚本加载与跨模块数据完整性回归。 */
public class DemoDataIntegrityTest {

    /** 按脚本工具的实际顺序加载全部库表和 Demo，校验业务引用及流程状态。 */
    @Test
    public void allDemoScriptsShouldBeConsistent() throws Exception {
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:demo" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        ScriptToolService service = new ScriptToolService();
        List<String> failures = new ArrayList<>();
        Method splitter = ScriptToolService.class.getDeclaredMethod("splitSqlStatements", String.class);
        splitter.setAccessible(true);
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            // 股票脚本依赖公共附件和收藏表，按各独立初始化任务的依赖顺序执行。
            for (String method : new String[]{"queryAisSchemaFiles", "queryExternalImportSchemaFiles", "queryRrsSchemaFiles",
                    "queryFundSchemaFiles", "queryStockSchemaFiles", "queryAisDemoFiles", "queryExternalImportDemoFiles",
                    "queryRrsDemoFiles", "queryFundDemoFiles", "queryStockDemoFiles"}) {
                // 复用生产白名单及 SQL 分句逻辑，防止测试漏掉新增脚本。
                List<String> files = ReflectionTestUtils.invokeMethod(service, method);
                for (String file : files) {
                    String content = new String(Files.readAllBytes(Paths.get("sql", file)), StandardCharsets.UTF_8);
                    // 让真实 SQL 分句器处理脚本中的注释和字符串。
                    @SuppressWarnings("unchecked")
                    List<String> commands = (List<String>) splitter.invoke(service, content);
                    for (String command : commands) {
                        command = command.replaceAll("(?m)^\\s*--[^\\r\\n]*", "").trim();
                        if (command.matches("(?is)^SET\\s+NAMES.*")) continue;
                        String sql = command.replaceAll("(?is)^CREATE DATABASE.*?`([^`]+)`.*", "CREATE SCHEMA IF NOT EXISTS `$1`")
                                .replaceAll("(?i)^USE\\s+", "SET SCHEMA ")
                                .replaceAll("(?i)DATE_SUB\\(NOW\\(\\),\\s*INTERVAL\\s+(\\d+)\\s+DAY\\)", "DATEADD('DAY', -$1, NOW())");
                        if (sql.matches("(?is)^CREATE TABLE.*")) {
                            sql = sql.replaceAll("(?is)\\)\\s*ENGINE\\s*=.*", ")")
                                    .replaceAll("(?i)\\bJSON\\b", "VARCHAR(20000)");
                        }
                        try {
                            // H2 不支持 MySQL 一条 ALTER 中连续修改多列，逐列保留同样的结构变更。
                            if (sql.startsWith("ALTER TABLE") && sql.contains("MODIFY COLUMN")) {
                                String prefix = sql.substring(0, sql.indexOf("MODIFY COLUMN"));
                                for (String modification : sql.substring(prefix.length()).split(",\\s*(?=MODIFY COLUMN)")) {
                                    statement.execute(prefix + modification);
                                }
                            } else {
                                statement.execute(sql);
                            }
                        } catch (Exception exception) {
                            throw new AssertionError("脚本加载失败: " + file, exception);
                        }
                    }
                }
            }
        }
        // 执行脚本工具已有的全量跨库完整性检查。
        List<?> rules = ReflectionTestUtils.invokeMethod(service, "queryIntegrityRules");
        for (Object rule : rules) {
            String sql = (String) ReflectionTestUtils.getField(rule, "sql");
            String name = (String) ReflectionTestUtils.getField(rule, "name");
            assertThat(sql).as(name + " 校验 SQL").isNotBlank();
            Integer count = jdbc.queryForObject(sql, Integer.class);
            if (count != null && count > 0) failures.add(name + ": " + count);
        }
        for (String suffix : new String[]{"", "_fund", "_stock"}) {
            String log = "znty_rrs.ip_adjust_log" + suffix;
            String step = "znty_rrs.ip_adjust_step" + suffix;
            // 所有流程步骤必须引用同一申请对应的有效流程节点。
            // 收集步骤关联、人员及状态异常。
            collect(jdbc, failures, "流程步骤关联" + suffix, "SELECT s.id FROM " + step + " s LEFT JOIN " + log
                    + " l ON l.id=s.adjust_log_id LEFT JOIN znty_rrs.wf_flow_node n ON n.id=s.flow_node_id"
                    + " WHERE l.id IS NULL OR n.id IS NULL OR l.flow_id IS NULL OR n.flow_id<>l.flow_id OR s.adjust_batch_no<>l.adjust_batch_no");
            // 有流程的申请不能只有日志；无流程直通不能伪造步骤。
            collect(jdbc, failures, "申请缺少流程步骤" + suffix, "SELECT l.id FROM " + log
                    + " l WHERE l.flow_id IS NOT NULL AND l.audit_status<>'-1' AND NOT EXISTS (SELECT 1 FROM " + step + " s WHERE s.adjust_batch_no=l.adjust_batch_no)");
            // 校验人工待办处理人缺失。
            collect(jdbc, failures, "人工待办处理人缺失" + suffix, "SELECT s.id FROM " + step
                    + " s WHERE s.step_status='pending' AND s.handler_id IS NULL AND s.approval_strategy IN ('preempt','all','initiator')");
            // 校验处理人姓名不一致。
            collect(jdbc, failures, "处理人姓名不一致" + suffix, "SELECT s.id FROM " + step
                    + " s JOIN ais_inv_analysis.t_sys_user u ON u.id=s.handler_id WHERE s.handler_name<>u.name");
            // 校验审批通过缺少结束步骤。
            collect(jdbc, failures, "审批通过缺少结束步骤" + suffix, "SELECT l.id FROM " + log
                    + " l WHERE l.audit_status='20' AND l.flow_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM " + step
                    + " s WHERE s.adjust_batch_no=l.adjust_batch_no AND s.node_type='end')");
            // 校验进行中申请缺少待办。
            collect(jdbc, failures, "进行中申请缺少待办" + suffix, "SELECT l.id FROM " + log
                    + " l WHERE l.audit_status IN ('00','11') AND NOT EXISTS (SELECT 1 FROM " + step
                    + " s WHERE s.adjust_log_id=l.id AND s.step_status='pending')");
            // 校验终态残留待办。
            collect(jdbc, failures, "终态残留待办" + suffix, "SELECT l.id FROM " + log
                    + " l JOIN " + step + " s ON s.adjust_log_id=l.id WHERE l.audit_status IN ('20','21','99','-1') AND s.step_status='pending'");
            // 流程、池、品种及发起人字段必须引用有效配置。
            collect(jdbc, failures, "日志流程配置不一致" + suffix, "SELECT l.id FROM " + log
                    + " l LEFT JOIN znty_rrs.wf_flow_definition f ON f.id=l.flow_id WHERE l.flow_id IS NOT NULL AND (f.id IS NULL OR f.flow_key<>l.flow_key)");
            // 校验日志投资池配置不一致。
            collect(jdbc, failures, "日志投资池配置不一致" + suffix, "SELECT l.id FROM " + log
                    + " l LEFT JOIN znty_rrs.ip_investment_pool p ON p.id=l.target_pool_id WHERE p.id IS NULL OR p.pool_name<>l.target_pool_name OR p.pool_type<>l.pool_type");
            // 校验日志证券品种缺失。
            collect(jdbc, failures, "日志证券品种缺失" + suffix, "SELECT l.id FROM " + log
                    + " l LEFT JOIN znty_rrs.dict_security_type t ON t.security_type=l.security_type WHERE t.id IS NULL");
            // 校验步骤节点快照不一致。
            collect(jdbc, failures, "步骤节点快照不一致" + suffix, "SELECT s.id FROM " + step
                    + " s JOIN znty_rrs.wf_flow_node n ON n.id=s.flow_node_id WHERE n.node_id<>s.node_code OR n.label<>s.node_label OR n.node_type<>s.node_type");
            // 连续步骤必须对应流程画布中的真实连线，避免伪造跳过审批的历史。
            collect(jdbc, failures, "流程步骤路径不连续" + suffix, "SELECT a.id FROM " + step + " a JOIN " + step
                    + " b ON b.adjust_log_id=a.adjust_log_id AND b.sort_order=a.sort_order+1"
                    + " WHERE NOT EXISTS (SELECT 1 FROM znty_rrs.wf_flow_edge e WHERE e.from_node_id=a.flow_node_id AND e.to_node_id=b.flow_node_id)");
            // 校验跨审核环节重复处理人。
            collect(jdbc, failures, "跨审核环节重复处理人" + suffix, "SELECT DISTINCT a.adjust_log_id FROM " + step + " a JOIN " + step
                    + " b ON b.adjust_log_id=a.adjust_log_id AND b.handler_id=a.handler_id AND b.flow_node_id<>a.flow_node_id"
                    + " WHERE a.step_status IN ('submit','approve','reject') AND b.step_status IN ('approve','reject')"
                    + " AND b.approval_strategy<>'initiator'");
            // 生效状态必须来自同对象同池的已通过调入日志。
            String state = "znty_rrs.ip_pool_status" + suffix;
            String identity = suffix.isEmpty() ? "COALESCE(p.isin_code,p.security_code)=COALESCE(l.isin_code,l.security_code)"
                    : "p." + ("_fund".equals(suffix) ? "fund_code" : "stock_code") + "=l."
                    + ("_fund".equals(suffix) ? "fund_code" : "stock_code");
            // 校验池状态来源不一致。
            collect(jdbc, failures, "池状态来源不一致" + suffix, "SELECT p.id FROM " + state + " p LEFT JOIN " + log
                    + " l ON l.id=p.adjust_log_id WHERE p.is_deleted=0 AND (l.id IS NULL OR l.audit_status<>'20' OR l.adjust_mode<>'调入'"
                    + " OR p.target_pool_id<>l.target_pool_id OR p.adjust_batch_no<>l.adjust_batch_no OR NOT (" + identity + "))");
        }
        // 禁止库主体命中黑名单条件时，应存在同批的黑名单质押库状态。
        collect(jdbc, failures, "禁止库主体缺少黑名单联动", "SELECT p.id FROM znty_rrs.ip_pool_status p"
                + " WHERE p.security_type='company' AND p.target_pool_id=15 AND p.is_deleted=0"
                + " AND NOT EXISTS (SELECT 1 FROM znty_rrs.ip_pool_status b WHERE b.security_code=p.security_code"
                + " AND b.security_type='company' AND b.target_pool_id=17 AND b.adjust_batch_no=p.adjust_batch_no AND b.is_deleted=0)");
        // 按演示落池日判断到期，避免样本因测试执行日期变化而改变；包括普通债、ABS 和 CRMW。
        collect(jdbc, failures, "主体旗下有效债券同步缺失", "SELECT p.id FROM znty_rrs.ip_pool_status p"
                + " JOIN znty_rrs.rrs_securityinfo s ON s.issuer_code=p.security_code"
                + " JOIN znty_rrs.dict_security_type t ON t.security_type=s.security_type AND t.category_type='bond' AND t.is_deleted=0"
                + " WHERE p.security_type='company' AND p.target_pool_id IN (15,17) AND p.is_deleted=0 AND s.security_status<>'D'"
                + " AND (s.maturity_date IS NULL OR s.maturity_date>=REPLACE(SUBSTRING(CAST(p.entry_time AS VARCHAR),1,10),'-',''))"
                + " AND NOT EXISTS (SELECT 1 FROM znty_rrs.ip_pool_status b WHERE b.isin_code=s.isin_code"
                + " AND b.target_pool_id=p.target_pool_id AND b.adjust_batch_no=p.adjust_batch_no AND b.is_deleted=0)");
        // 校验其他 Demo 模块的主档、角色、评级及快照引用。
        collect(jdbc, failures, "审批角色名称不一致", "SELECT h.id FROM znty_rrs.wf_node_approval_handler h"
                + " JOIN ais_inv_analysis.t_sys_role r ON r.id=h.handler_id WHERE h.handler_type='role' AND h.handler_name<>r.name");
        // 校验证券调库主档缺失。
        collect(jdbc, failures, "证券调库主档缺失", "SELECT l.id FROM znty_rrs.ip_adjust_log l LEFT JOIN znty_rrs.rrs_securityinfo s"
                + " ON s.isin_code=l.isin_code WHERE l.security_type<>'company' AND s.isin_code IS NULL");
        // 校验主体调库主档缺失。
        collect(jdbc, failures, "主体调库主档缺失", "SELECT l.id FROM znty_rrs.ip_adjust_log l LEFT JOIN ais_inv_analysis.t_inv_company c"
                + " ON c.code=l.security_code WHERE l.security_type='company' AND c.id IS NULL");
        // 校验证券调库快照缺失。
        collect(jdbc, failures, "证券调库快照缺失", "SELECT l.id FROM znty_rrs.ip_adjust_log l LEFT JOIN znty_rrs.ip_adjust_security_snapshot s"
                + " ON s.adjust_log_id=l.id WHERE l.security_type<>'company' AND l.pool_type<>'crmw' AND l.adjust_type='手工调整' AND s.id IS NULL");
        // 校验CRMW 调库快照缺失。
        collect(jdbc, failures, "CRMW 调库快照缺失", "SELECT l.id FROM znty_rrs.ip_adjust_log l LEFT JOIN znty_rrs.ip_adjust_security_snapshot_crmw s"
                + " ON s.adjust_log_id=l.id WHERE l.pool_type='crmw' AND s.id IS NULL");
        // 校验临时证券主档不一致。
        collect(jdbc, failures, "临时证券主档不一致", "SELECT t.id FROM znty_rrs.rrs_temp_security_code t LEFT JOIN znty_rrs.rrs_securityinfo s"
                + " ON s.isin_code=CONCAT('TEMP:',t.temp_security_code) WHERE t.status IN ('temporary','cancelled')"
                + " AND (s.isin_code IS NULL OR s.security_source<>'temporary' OR (t.status='cancelled' AND s.security_status<>'D'))");
        // 校验临时证券转正主档缺失。
        collect(jdbc, failures, "临时证券转正主档缺失", "SELECT t.id FROM znty_rrs.rrs_temp_security_code t LEFT JOIN znty_rrs.rrs_securityinfo s"
                + " ON s.isin_code=t.isin_code WHERE t.status='updated' AND s.isin_code IS NULL");
        for (String domain : new String[]{"fund", "stock"}) {
            String code = domain + "_code";
            // 校验调库主档缺失_。
            collect(jdbc, failures, "调库主档缺失_" + domain, "SELECT l.id FROM znty_rrs.ip_adjust_log_" + domain
                    + " l LEFT JOIN znty_rrs.rrs_" + domain + "info m ON m." + code + "=l." + code + " WHERE m.id IS NULL");
        }
        for (String table : new String[]{"rrs_stock_rating", "rrs_stock_manager"}) {
            // 校验股票扩展主档缺失_。
            collect(jdbc, failures, "股票扩展主档缺失_" + table, "SELECT d.id FROM znty_rrs." + table
                    + " d LEFT JOIN znty_rrs.rrs_stockinfo m ON m.stock_code=d.stock_code WHERE m.id IS NULL");
        }
        // 校验基金净值主档缺失。
        collect(jdbc, failures, "基金净值主档缺失", "SELECT n.id FROM znty_rrs.rrs_fund_nav n LEFT JOIN znty_rrs.rrs_fundinfo f"
                + " ON f.fund_code=n.fund_code WHERE f.id IS NULL");
        for (String table : new String[]{"rrs_report_in", "rrs_report_out"}) {
            // 报告按证券 ISIN 或主体代码关联，附件文件本身由上传功能生成。
            collect(jdbc, failures, "报告证券主档缺失_" + table, "SELECT r.id FROM znty_rrs." + table
                    + " r LEFT JOIN znty_rrs.rrs_securityinfo s ON s.isin_code=r.isin_code WHERE r.isin_code IS NOT NULL AND s.isin_code IS NULL");
            // 校验报告主体主档缺失_。
            collect(jdbc, failures, "报告主体主档缺失_" + table, "SELECT r.id FROM znty_rrs." + table
                    + " r LEFT JOIN ais_inv_analysis.t_inv_company c ON c.code=r.company_code WHERE r.company_code IS NOT NULL AND c.id IS NULL");
        }
        for (String table : new String[]{"ip_adjust_log", "ip_adjust_log_fund", "ip_adjust_log_stock", "rrs_report_in", "rrs_report_out"}) {
            // 校验附件业务记录缺失_。
            collect(jdbc, failures, "附件业务记录缺失_" + table, "SELECT a.id FROM znty_rrs.sys_attachment a LEFT JOIN znty_rrs."
                    + table + " r ON r.id=a.main_id WHERE a.table_name='" + table + "' AND r.id IS NULL");
        }
        assertThat(failures).as("Demo 数据完整性问题").isEmpty();
        // 同时验证设计器画布真源与审批运行配置，避免只修复其中一套数据。
        verifyFlowCanvas(jdbc);
        // 使用真实 Mapper 验证截图场景和三业务待办、已完成列表的流程名称。
        verifyMyMatters(source);
    }

    /** 校验所有版本画布中的审批处理人及策略，与归一化配置和 AIS 主档保持一致。 */
    private void verifyFlowCanvas(JdbcTemplate jdbc) throws Exception {
        ObjectMapper json = new ObjectMapper();
        List<Map<String, Object>> versions = jdbc.queryForList("SELECT id, canvas_nodes FROM znty_rrs.wf_flow_version");
        assertThat(versions).isNotEmpty();
        for (Map<String, Object> version : versions) {
            for (JsonNode node : json.readTree((String) version.get("canvas_nodes"))) {
                if (!"approval".equals(node.path("type").asText())) continue;
                String location = "版本 " + version.get("id") + " 节点 " + node.path("id").asText();
                List<String> canvasHandlers = new ArrayList<>();
                for (JsonNode handler : node.path("approvalPersons")) {
                    String type = handler.path("handlerType").asText();
                    long id = handler.path("handlerId").asLong();
                    String name = handler.path("handlerName").asText();
                    assertThat(type).as(location).isIn("role", "user");
                    String master = "role".equals(type)
                            ? "SELECT name FROM ais_inv_analysis.t_sys_role WHERE id=? AND enable=1"
                            : "SELECT name FROM ais_inv_analysis.t_sys_user WHERE id=? AND dr=0";
                    assertThat(jdbc.queryForObject(master, String.class, id)).as(location + " " + type + " " + id).isEqualTo(name);
                    canvasHandlers.add(type + ":" + id + ":" + name);
                }
                List<String> normalizedHandlers = jdbc.query("SELECT h.handler_type, h.handler_id, h.handler_name"
                                + " FROM znty_rrs.wf_flow_node n JOIN znty_rrs.wf_node_approval_config c ON c.node_id=n.id"
                                + " JOIN znty_rrs.wf_node_approval_handler h ON h.approval_config_id=c.id"
                                + " WHERE n.version_id=? AND n.node_id=? ORDER BY h.sort_order, h.id",
                        (rs, row) -> rs.getString("handler_type") + ":" + rs.getLong("handler_id") + ":" + rs.getString("handler_name"),
                        version.get("id"), node.path("id").asText());
                assertThat(normalizedHandlers).as(location + " 审批处理人").containsExactlyElementsOf(canvasHandlers);
                String strategy = jdbc.queryForObject("SELECT c.approval_strategy FROM znty_rrs.wf_flow_node n"
                                + " JOIN znty_rrs.wf_node_approval_config c ON c.node_id=n.id WHERE n.version_id=? AND n.node_id=?",
                        String.class, version.get("id"), node.path("id").asText());
                assertThat(strategy).as(location + " 审批策略").isEqualTo(node.path("approvalStrategy").asText());
            }
        }
    }

    /** 用真实角色和人员接口查询验证管理员可筛选，研究总监不会对应量化部。 */
    private void verifyFlowRoleQueries(SqlSession session) {
        FlowService service = new FlowService();
        ReflectionTestUtils.setField(service, "flowMapper", session.getMapper(FlowMapper.class));
        List<RoleDto> roles = service.queryRoleList(new FlowReq());
        assertThat(roles).anySatisfy(role -> {
            assertThat(role.getId()).isEqualTo(15L);
            assertThat(role.getRoleName()).isEqualTo("管理员");
        });
        assertThat(roles).anySatisfy(role -> {
            assertThat(role.getId()).isEqualTo(12L);
            assertThat(role.getRoleName()).isEqualTo("研究总监");
        });
        FlowReq req = new FlowReq();
        req.setRoleId(15L);
        List<UserDto> administrators = service.queryUserList(req);
        assertThat(administrators).singleElement().satisfies(user -> {
            assertThat(user.getId()).isEqualTo(1L);
            assertThat(user.getUserName()).isEqualTo("管理员");
            assertThat(user.getRoleName()).isEqualTo("管理员");
        });
        req.setRoleId(12L);
        assertThat(service.queryUserList(req)).extracting(UserDto::getId).containsExactly(4L);
        req.setRoleId(9L);
        assertThat(service.queryUserList(req)).extracting(UserDto::getId).containsExactly(14L, 15L);
        req.setRoleId(null);
        assertThat(service.queryUserList(req)).filteredOn(user -> user.getId().equals(1L))
                .singleElement().satisfies(user -> assertThat(user.getRoleName()).isEqualTo("管理员"));
        req.setId(103L);
        JsonNode nodes = (JsonNode) service.queryFlowDetail(req).getDraft().getNodes();
        List<Long> directorRoleIds = new ArrayList<>();
        for (JsonNode node : nodes) {
            if (!"n105".equals(node.path("id").asText())) continue;
            for (JsonNode handler : node.path("approvalPersons")) {
                if ("role".equals(handler.path("handlerType").asText())) directorRoleIds.add(handler.path("handlerId").asLong());
            }
        }
        assertThat(directorRoleIds).as("债券一般入库流程研究总监节点").containsExactly(12L);
    }

    /** 真实 Demo 经过三业务列表查询后，流程名称完整且 C10005 使用正确的主体审批流程。 */
    private void verifyMyMatters(UnpooledDataSource source) throws Exception {
        Configuration config = new Configuration(new Environment("demo", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        for (String domain : new String[]{"Bond", "Fund", "Stock"}) {
            String resource = "mapper/" + domain + "MyMattersMapper.xml";
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
            }
        }
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("mapper/FlowMapper.xml")) {
            new XMLMapperBuilder(stream, config, "mapper/FlowMapper.xml", config.getSqlFragments()).parse();
        }
        try (SqlSession session = new SqlSessionFactoryBuilder().build(config).openSession(true);
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("SET SCHEMA znty_rrs");
            // 复现处理人弹窗的角色树、全部人员及按角色筛选接口。
            verifyFlowRoleQueries(session);
            for (String domain : new String[]{"Bond", "Fund", "Stock"}) {
                for (String status : new String[]{"pending", "completed"}) {
                    MyMattersReq req = new MyMattersReq();
                    req.setCurrentUserId("1");
                    req.setStepStatus(status);
                    req.setBusinessDomain(domain.toLowerCase());
                    List<MyMattersDto> matters = session.selectList("com.znty.rrs.mapper." + domain + "MyMattersMapper.queryMyMattersPage", req);
                    assertThat(matters).as(domain + " " + status).isNotEmpty();
                    assertThat(matters).allSatisfy(row -> assertThat(row.getFlowName()).isNotBlank());
                    if ("Bond".equals(domain) && "completed".equals(status)) {
                        assertThat(matters).anySatisfy(row -> {
                            assertThat(row.getSecurityCode()).isEqualTo("C10005");
                            assertThat(row.getFlowName()).isEqualTo("禁止库入库流程");
                        });
                    }
                }
            }
        }
    }

    /** 收集异常主键，便于准确定位需要修正的 Demo 行。 */
    private void collect(JdbcTemplate jdbc, List<String> failures, String name, String sql) {
        List<Long> ids = jdbc.queryForList(sql, Long.class);
        if (!ids.isEmpty()) failures.add(name + ": " + ids);
    }
}
