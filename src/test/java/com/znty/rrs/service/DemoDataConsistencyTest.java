package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.entity.bo.UserBo;
import com.znty.rrs.entity.mymatters.MyMattersDto;
import com.znty.rrs.entity.mymatters.MyMattersReq;
import com.znty.rrs.mapper.BondMyMattersMapper;
import com.znty.rrs.mapper.ForbiddenPoolAdjustMapper;
import com.znty.rrs.mapper.FlowMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Assume;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 基础 Demo 的跨表业务一致性测试，不连接或修改业务数据库。 */
public class DemoDataConsistencyTest {

    /** 在独立 H2 库执行原始建表和 INSERT 两次，校验 SQL 语法及重置后的流程关联。 */
    @Test
    public void demoInsertsShouldExecuteRepeatedly() throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 需要 Java 11 以上运行时", major >= 11);
        String url = "jdbc:h2:mem:demo_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false";
        try (Connection connection = DriverManager.getConnection(url)) {
            connection.createStatement().execute("CREATE SCHEMA znty_rrs");
            connection.createStatement().execute("CREATE SCHEMA ais_inv_analysis");
            connection.createStatement().execute("CREATE SCHEMA ais_inv_ods");
            String[][] tables = {{"ais_inv_analysis", "ais_inv_analysis", "t_sys_role"},
                    {"ais_inv_analysis", "ais_inv_analysis", "t_sys_user"},
                    {"ais_inv_analysis", "ais_inv_analysis", "t_sys_user_role"},
                    {"znty_rrs", "rrs_flow_definition", "wf_flow_definition"},
                    {"znty_rrs", "rrs_flow_definition", "wf_flow_version"},
                    {"znty_rrs", "rrs_flow_definition", "wf_flow_node"},
                    {"znty_rrs", "rrs_flow_definition", "wf_node_approval_handler"},
                    {"znty_rrs", "rrs_security_pool_adjust", "ip_adjust_log"},
                    {"znty_rrs", "rrs_security_pool_adjust", "ip_pool_status"},
                    {"znty_rrs", "rrs_security_pool_adjust", "ip_adjust_step"},
                    {"znty_rrs", "rrs_crmw_pool_status", "ip_pool_status_crmw"},
                    {"znty_rrs", "rrs_external_import", "rrs_securityinfo"},
                    {"znty_rrs", "rrs_dict", "dict_security_type"},
                    {"ais_inv_ods", "ais_inv_ods", "wind_cbondissuer"}};
            for (String[] table : tables) {
                connection.setSchema(table[0]);
                // 只移除 H2 不支持的 MySQL 表选项，其余使用真实 DDL
                String ddl = readStatement(table[1] + "_schema.sql", "CREATE TABLE", table[2]);
                connection.createStatement().execute(ddl.replaceAll("(?s)\\)\\s*ENGINE\\s*=.*", ")"));
            }
            for (int run = 0; run < 2; run++) {
                for (String[] table : tables) {
                    connection.setSchema(table[0]);
                    connection.createStatement().execute("TRUNCATE TABLE `" + table[2] + "`");
                    // 使用原始 INSERT 验证重复初始化，不接触运行中的 MySQL
                    connection.createStatement().execute(readStatement(table[1] + "_demo_data.sql", "INSERT INTO", table[2]));
                }
                try (Statement statement = connection.createStatement();
                     ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM znty_rrs.ip_adjust_step s"
                             + " LEFT JOIN znty_rrs.wf_flow_node n ON n.id = s.flow_node_id"
                             + " LEFT JOIN znty_rrs.wf_flow_definition f ON f.id = n.flow_id"
                             + " WHERE f.name IS NULL OR f.name = ''")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isZero();
                }
                // 用真实事项查询和详情主数据查询验证点击后的业务路由
                verifyMatterDetailObjects(url);
            }
        }
    }

    /** 主体调整仅进入四类风险池，证券调整的代码和类型必须存在于证券主档。 */
    @Test
    public void adjustmentObjectsShouldMatchSupportedPoolsAndMasterData() throws Exception {
        // 读取各业务实际使用的主数据及类型字典
        Map<String, Map<String, String>> securities = index(readRows("rrs_external_import_demo_data.sql", "rrs_securityinfo"), "wind_code");
        Map<String, Map<String, String>> types = index(readRows("rrs_dict_demo_data.sql", "dict_security_type"), "security_type");
        Set<String> companies = new HashSet<>();
        for (Map<String, String> issuer : readRows("ais_inv_ods_demo_data.sql", "wind_cbondissuer")) {
            companies.add(issuer.get("s_info_compcode"));
        }
        for (Map<String, String> log : readRows("rrs_security_pool_adjust_demo_data.sql", "ip_adjust_log")) {
            String type = log.get("security_type");
            String code = log.get("security_code");
            assertThat(types.keySet()).as("日志 %s 的类型", log.get("id")).contains(type);
            if ("company".equals(types.get(type).get("category_type"))) {
                assertThat(companies).as("主体详情代码 %s", code).contains(code);
                assertThat(log.get("target_pool_id")).as("主体 %s 不能进入证券池", code).isIn("15", "16", "17", "23");
                assertThat(log.get("pool_type")).isIn("forbidden", "observe", "blacklist", "restricted");
                assertThat(log.get("flow_key")).isIn("company:forbidden-inbound", "company:forbidden-outbound", "bond:no-approval");
            } else {
                assertThat(securities.keySet()).as("证券详情代码 %s", code).contains(code);
                assertThat(log.get("security_type")).isEqualTo(securities.get(code).get("security_type"));
                if ("crmw".equals(log.get("pool_type"))) {
                    assertThat(securities.keySet()).contains(log.get("crmw_scode"));
                    assertThat(securities.get(log.get("crmw_scode")).get("security_type")).isEqualTo("crmw");
                }
            }
        }
    }

    /** 实际查询待办、已办和我发起的，再按查询返回的场景查详情对象。 */
    private void verifyMatterDetailObjects(String url) throws Exception {
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver", url + ";SCHEMA=znty_rrs", null, null);
        Configuration config = new Configuration(new Environment("demo", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        for (String mapper : new String[]{"BondMyMatters", "ForbiddenPoolAdjust", "Flow"}) {
            String resource = "mapper/" + mapper + "Mapper.xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
        }
        try (SqlSession session = new SqlSessionFactoryBuilder().build(config).openSession()) {
            FlowMapper flow = session.getMapper(FlowMapper.class);
            assertThat(flow.queryUserList(null, "管理员")).extracting(UserBo::getRoleName).containsExactly("系统管理员");
            for (Long roleId : Arrays.asList(2L, 11L, 12L, 13L, 14L)) {
                assertThat(flow.queryUserList(Arrays.asList(roleId), null)).hasSizeGreaterThanOrEqualTo(3);
            }
            BondMyMattersMapper matters = session.getMapper(BondMyMattersMapper.class);
            ForbiddenPoolAdjustMapper details = session.getMapper(ForbiddenPoolAdjustMapper.class);
            MyMattersReq req = new MyMattersReq();
            req.setBusinessDomain("bond");
            req.setCurrentUserId("1");
            List<MyMattersDto> initiated = matters.queryMyInitiatedMattersPage(req);
            assertThat(initiated).extracting(MyMattersDto::getSecurityCode).contains("C10001", "C10002");
            List<MyMattersDto> rows = new ArrayList<>(initiated);
            for (String status : new String[]{"pending", "completed"}) {
                req.setStepStatus(status);
                rows.addAll(matters.queryMyMattersPage(req));
            }
            for (MyMattersDto row : rows) {
                assertThat(row.getFlowName()).isNotBlank();
                if ("forbiddenCompanyAdjust".equals(row.getBusinessScene())) {
                    assertThat(details.queryCompanyDetail(row.getSecurityCode())).isNotNull();
                } else {
                    assertThat(details.querySecurityBoByCode(row.getSecurityCode()))
                            .as("事项 %s 的 %s 路由必须查到详情对象", row.getAdjustLogId(), row.getBusinessScene()).isNotNull();
                }
            }
        }
    }

    /** 系统管理员只关联管理角色，不作为业务审批角色成员。 */
    @Test
    public void administratorShouldOnlyBelongToSystemAdministratorRole() throws Exception {
        // 按实际关联表检查管理员的角色归属
        Map<String, Map<String, String>> roles = index(readRows("ais_inv_analysis_demo_data.sql", "t_sys_role"), "id");
        List<String> adminRoles = new ArrayList<>();
        for (Map<String, String> member : readRows("ais_inv_analysis_demo_data.sql", "t_sys_user_role")) {
            if ("1".equals(member.get("user_id"))) {
                adminRoles.add(roles.get(member.get("role_id")).get("name"));
            }
        }
        assertThat(adminRoles).containsExactly("系统管理员");
    }

    /** 角色和成员不重复配置，画布与归一化表一致，每个非发起节点至少有三名候选人。 */
    @Test
    public void approvalCandidatesShouldBeSufficientAndNotRedundant() throws Exception {
        // 按启用角色及子角色展开有效用户，并索引双存储的审批配置
        Map<String, Set<String>> roleUsers = expandedRoleUsers();
        Map<String, Map<String, String>> nodes = index(readRows("rrs_flow_definition_demo_data.sql", "wf_flow_node"), "id");
        Map<String, Map<String, String>> configs = index(readRows("rrs_flow_definition_demo_data.sql", "wf_node_approval_config"), "node_id");
        List<Map<String, String>> handlers = readRows("rrs_flow_definition_demo_data.sql", "wf_node_approval_handler");
        ObjectMapper mapper = new ObjectMapper();
        int checked = 0;
        for (Map<String, String> version : readRows("rrs_flow_definition_demo_data.sql", "wf_flow_version")) {
            for (JsonNode node : mapper.readTree(version.get("canvas_nodes"))) {
                if (!"approval".equals(node.path("type").asText())) {
                    continue;
                }
                String databaseNodeId = null;
                for (Map<String, String> normalized : nodes.values()) {
                    if (version.get("id").equals(normalized.get("version_id"))
                            && node.path("id").asText().equals(normalized.get("node_id"))) {
                        databaseNodeId = normalized.get("id");
                    }
                }
                Map<String, String> config = configs.get(databaseNodeId);
                assertThat(config).as("画布节点 %s/%s", version.get("id"), node.path("id")).isNotNull();
                assertThat(config.get("approval_strategy")).isEqualTo(node.path("approvalStrategy").asText());
                Set<String> selected = new HashSet<>();
                Set<String> selectedUsers = new HashSet<>();
                Set<String> coveredUsers = new HashSet<>();
                for (JsonNode handler : node.path("approvalPersons")) {
                    String id = handler.path("handlerId").asText();
                    String type = handler.path("handlerType").asText();
                    assertThat(selected.add(type + ":" + id)).as("重复处理人 %s", handler).isTrue();
                    if ("role".equals(type)) {
                        assertThat(roleUsers).containsKey(id);
                        coveredUsers.addAll(roleUsers.get(id));
                    } else {
                        selectedUsers.add(id);
                    }
                }
                for (String id : selectedUsers) {
                    assertThat(coveredUsers).as("节点 %s 重复配置角色成员 %s", config.get("id"), id).doesNotContain(id);
                }
                Set<String> normalizedSelection = new HashSet<>();
                for (Map<String, String> handler : handlers) {
                    if (config.get("id").equals(handler.get("approval_config_id"))) {
                        assertThat(normalizedSelection.add(handler.get("handler_type") + ":" + handler.get("handler_id"))).isTrue();
                    }
                }
                assertThat(normalizedSelection).isEqualTo(selected);
                if ("initiator".equals(config.get("approval_strategy"))) {
                    assertThat(selected).isEmpty();
                } else {
                    coveredUsers.addAll(selectedUsers);
                    assertThat(coveredUsers).as("节点 %s 候选人", config.get("id")).hasSizeGreaterThanOrEqualTo(3).doesNotContain("1");
                    checked++;
                }
            }
        }
        assertThat(checked).isPositive();
    }

    /** 历史审批及待办的具体人员必须属于该步骤的候选范围。 */
    @Test
    public void approvalStepHandlersShouldBelongToConfiguredCandidates() throws Exception {
        // 从角色配置构建每个节点实际可处理的用户集合
        Map<String, Set<String>> roleUsers = expandedRoleUsers();
        Map<String, Map<String, String>> configs = index(readRows("rrs_flow_definition_demo_data.sql", "wf_node_approval_config"), "id");
        Map<String, Set<String>> nodeUsers = new HashMap<>();
        for (Map<String, String> handler : readRows("rrs_flow_definition_demo_data.sql", "wf_node_approval_handler")) {
            String nodeId = configs.get(handler.get("approval_config_id")).get("node_id");
            Set<String> candidates = nodeUsers.computeIfAbsent(nodeId, key -> new HashSet<>());
            if ("role".equals(handler.get("handler_type"))) {
                candidates.addAll(roleUsers.get(handler.get("handler_id")));
            } else {
                candidates.add(handler.get("handler_id"));
            }
        }
        for (String[] domain : new String[][]{{"rrs_security_pool_adjust_demo_data.sql", ""},
                {"fund/rrs_fund_pool_adjust_demo_data.sql", "_fund"}, {"stock/rrs_stock_pool_adjust_demo_data.sql", "_stock"}}) {
            for (Map<String, String> step : readRows(domain[0], "ip_adjust_step" + domain[1])) {
                if ("approval".equals(step.get("node_type")) && !"initiator".equals(step.get("approval_strategy"))
                        && step.get("handler_id") != null) {
                    assertThat(nodeUsers.get(step.get("flow_node_id"))).as("%s 步骤 %s", domain[0], step.get("flow_node_id"))
                            .contains(step.get("handler_id"));
                }
            }
        }
    }

    /** 将有效成员向父角色展开，与候选人员查询的子角色口径保持一致。 */
    private Map<String, Set<String>> expandedRoleUsers() throws Exception {
        // 校验角色和用户引用，再沿父角色链汇总成员
        Map<String, Map<String, String>> roles = index(readRows("ais_inv_analysis_demo_data.sql", "t_sys_role"), "id");
        Map<String, Map<String, String>> users = index(readRows("ais_inv_analysis_demo_data.sql", "t_sys_user"), "id");
        Map<String, Set<String>> result = new HashMap<>();
        for (Map<String, String> role : roles.values()) {
            if ("1".equals(role.get("enable"))) {
                result.put(role.get("id"), new HashSet<>());
            }
        }
        Set<String> unique = new HashSet<>();
        for (Map<String, String> member : readRows("ais_inv_analysis_demo_data.sql", "t_sys_user_role")) {
            String userId = member.get("user_id");
            String roleId = member.get("role_id");
            assertThat(users).containsKey(userId);
            assertThat(users.get(userId).get("dr")).isEqualTo("0");
            assertThat(unique.add(roleId + ":" + userId)).as("重复角色成员 %s", member.get("id")).isTrue();
            Set<String> visited = new HashSet<>();
            while (roleId != null) {
                assertThat(visited.add(roleId)).as("角色父子循环 %s", roleId).isTrue();
                assertThat(result).containsKey(roleId);
                result.get(roleId).add(userId);
                roleId = roles.get(roleId).get("parent_id");
            }
        }
        return result;
    }

    /** 审批画布及归一化处理人必须引用 AIS 中同名的真实角色/用户。 */
    @Test
    public void approvalHandlersShouldMatchAisIdentity() throws Exception {
        // 建立实际角色和用户名称索引
        Map<String, Map<String, String>> roles = index(readRows("ais_inv_analysis_demo_data.sql", "t_sys_role"), "id");
        Map<String, Map<String, String>> users = index(readRows("ais_inv_analysis_demo_data.sql", "t_sys_user"), "id");
        for (Map<String, String> handler : readRows("rrs_flow_definition_demo_data.sql", "wf_node_approval_handler")) {
            Map<String, Map<String, String>> identities = "role".equals(handler.get("handler_type")) ? roles : users;
            assertThat(identities).as("处理人 %s", handler).containsKey(handler.get("handler_id"));
            assertThat(handler.get("handler_name")).isEqualTo(identities.get(handler.get("handler_id")).get("name"));
        }
        ObjectMapper mapper = new ObjectMapper();
        for (Map<String, String> version : readRows("rrs_flow_definition_demo_data.sql", "wf_flow_version")) {
            for (JsonNode node : mapper.readTree(version.get("canvas_nodes"))) {
                JsonNode handlers = node.get("approvalPersons");
                if (handlers == null) {
                    continue;
                }
                for (JsonNode handler : handlers) {
                    Map<String, Map<String, String>> identities = "role".equals(handler.path("handlerType").asText()) ? roles : users;
                    String id = handler.path("handlerId").asText();
                    assertThat(identities).as("流程 %s 节点 %s", version.get("id"), node.path("id")).containsKey(id);
                    assertThat(handler.path("handlerName").asText()).isEqualTo(identities.get(id).get("name"));
                }
            }
        }
    }

    /** 同 ISIN 的全部市场代码必须拥有相同的在池集合，含全部不在池场景。 */
    @Test
    public void sameBondShouldHaveSamePoolsAcrossMarkets() throws Exception {
        // 按实际 Wind ISIN 构建同券市场代码分组
        Map<String, List<String>> groups = new HashMap<>();
        for (Map<String, String> security : readRows("ais_inv_ods_demo_data.sql", "wind_windcustomcode")) {
            String isin = security.get("S_INFO_ISINCODE");
            if (isin != null && !isin.trim().isEmpty()) {
                groups.computeIfAbsent(isin, key -> new ArrayList<>()).add(security.get("S_INFO_WINDCODE"));
            }
        }
        // 汇总真实有效的当前池状态
        Map<String, Set<String>> pools = activePools(readRows("rrs_security_pool_adjust_demo_data.sql", "ip_pool_status"));
        int multiMarketGroups = 0;
        for (List<String> codes : groups.values()) {
            if (codes.size() < 2) {
                continue;
            }
            multiMarketGroups++;
            Set<String> expected = pools.getOrDefault(codes.get(0), new HashSet<>());
            for (String code : codes) {
                assertThat(pools.getOrDefault(code, new HashSet<>())).as("同券代码 %s", codes).isEqualTo(expected);
            }
        }
        assertThat(multiMarketGroups).isEqualTo(3);
    }

    /** 调库记录、步骤和流程定义必须相互对应，确保我的事宜可显示流程名。 */
    @Test
    public void adjustmentStepsShouldReferenceTheirFlow() throws Exception {
        // 索引真实流程和节点，校验日志及步骤的关联
        Map<String, Map<String, String>> flows = index(readRows("rrs_flow_definition_demo_data.sql", "wf_flow_definition"), "id");
        Map<String, Map<String, String>> nodes = index(readRows("rrs_flow_definition_demo_data.sql", "wf_flow_node"), "id");
        Map<String, Map<String, String>> users = index(readRows("ais_inv_analysis_demo_data.sql", "t_sys_user"), "id");
        for (String[] domain : new String[][]{{"rrs_security_pool_adjust_demo_data.sql", ""},
                {"fund/rrs_fund_pool_adjust_demo_data.sql", "_fund"}, {"stock/rrs_stock_pool_adjust_demo_data.sql", "_stock"}}) {
            // 读取各业务独立日志与步骤
            Map<String, Map<String, String>> logs = index(readRows(domain[0], "ip_adjust_log" + domain[1]), "id");
            Set<String> stepBatches = new HashSet<>();
            for (Map<String, String> step : readRows(domain[0], "ip_adjust_step" + domain[1])) {
                Map<String, String> log = logs.get(step.get("adjust_log_id"));
                assertThat(log).as("步骤对应日志 %s", step).isNotNull();
                Map<String, String> node = nodes.get(step.get("flow_node_id"));
                assertThat(node).as("步骤对应流程节点 %s", step).isNotNull();
                assertThat(node.get("flow_id")).isEqualTo(log.get("flow_id"));
                assertThat(step.get("node_code")).isEqualTo(node.get("node_id"));
                assertThat(step.get("node_label")).isEqualTo(node.get("label"));
                assertThat(step.get("adjust_batch_no")).isEqualTo(log.get("adjust_batch_no"));
                if (step.get("handler_id") != null) {
                    assertThat(users).containsKey(step.get("handler_id"));
                    assertThat(step.get("handler_name")).isEqualTo(users.get(step.get("handler_id")).get("name"));
                }
                if ("pending".equals(step.get("step_status"))) {
                    assertThat(log.get("audit_status")).isIn("00", "11");
                    assertThat(step.get("process_time")).isNull();
                }
                stepBatches.add(step.get("adjust_batch_no"));
            }
            for (Map<String, String> log : logs.values()) {
                assertThat(flows).as("调库日志 %s", log).containsKey(log.get("flow_id"));
                assertThat(log.get("flow_key")).isEqualTo(flows.get(log.get("flow_id")).get("flow_key"));
                assertThat(flows.get(log.get("flow_id")).get("name")).isNotBlank();
                if (!"-1".equals(log.get("audit_status"))) {
                    assertThat(stepBatches).as("有效调库批次必须有流程步骤 %s", log.get("id")).contains(log.get("adjust_batch_no"));
                }
            }
        }
    }

    /** 池状态只能引用同对象、同池、同批次的已通过调入日志。 */
    @Test
    public void currentPoolsShouldMatchApprovedLogs() throws Exception {
        for (String[] domain : new String[][]{{"rrs_security_pool_adjust_demo_data.sql", "ip_pool_status", "security_code", ""},
                {"rrs_crmw_pool_status_demo_data.sql", "ip_pool_status_crmw", "security_code", ""},
                {"fund/rrs_fund_pool_adjust_demo_data.sql", "ip_pool_status_fund", "fund_code", "_fund"},
                {"stock/rrs_stock_pool_adjust_demo_data.sql", "ip_pool_status_stock", "stock_code", "_stock"}}) {
            String logFile = domain[3].isEmpty() ? "rrs_security_pool_adjust_demo_data.sql" : domain[0];
            // 按业务选择日志，避免跨业务同 ID 误关联
            Map<String, Map<String, String>> logs = index(readRows(logFile, "ip_adjust_log" + domain[3]), "id");
            Set<String> unique = new HashSet<>();
            for (Map<String, String> status : readRows(domain[0], domain[1])) {
                if (!"0".equals(status.get("is_deleted"))) {
                    continue;
                }
                Map<String, String> log = logs.get(status.get("adjust_log_id"));
                assertThat(log).as("池状态来源日志 %s", status).isNotNull();
                assertThat(log.get("audit_status")).isEqualTo("20");
                assertThat(log.get("adjust_mode")).isEqualTo("调入");
                for (String field : new String[]{domain[2], "target_pool_id", "adjust_batch_no", "flow_id", "flow_key", "pool_type", "crmw_scode"}) {
                    assertThat(status.get(field)).as("池状态字段 %s", field).isEqualTo(log.get(field));
                }
                assertThat(unique.add(status.get(domain[2]) + ":" + status.get("crmw_scode") + ":" + status.get("target_pool_id"))).isTrue();
            }
        }
    }

    /** 禁止库主体必须联动黑名单质押库，旗下未到期债券同步进入两个池。 */
    @Test
    public void forbiddenCompanyShouldSynchronizeItsBonds() throws Exception {
        // 汇总主体及债券当前所在池
        Map<String, Set<String>> pools = activePools(readRows("rrs_security_pool_adjust_demo_data.sql", "ip_pool_status"));
        List<Map<String, String>> securities = readRows("rrs_external_import_demo_data.sql", "rrs_securityinfo");
        for (Map<String, String> company : readRows("ais_inv_analysis_demo_data.sql", "t_inv_company")) {
            String code = company.get("code");
            if (!pools.getOrDefault(code, new HashSet<>()).contains("15")) {
                continue;
            }
            assertThat(pools.get(code)).contains("17");
            for (Map<String, String> bond : securities) {
                if (code.equals(bond.get("issuer_code")) && !"D".equals(bond.get("security_status"))) {
                    assertThat(pools.get(bond.get("wind_code"))).as("禁止库主体旗下债券 %s", bond.get("wind_code")).contains("15", "17");
                }
            }
        }
    }

    /** 汇总未删除且已审批通过的证券池集合。 */
    private Map<String, Set<String>> activePools(List<Map<String, String>> rows) {
        Map<String, Set<String>> result = new HashMap<>();
        for (Map<String, String> row : rows) {
            if ("0".equals(row.get("is_deleted")) && "20".equals(row.get("audit_status"))) {
                result.computeIfAbsent(row.get("security_code"), key -> new HashSet<>()).add(row.get("target_pool_id"));
            }
        }
        return result;
    }

    /** 按业务字段建立索引并拒绝重复 ID。 */
    private Map<String, Map<String, String>> index(List<Map<String, String>> rows, String field) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            assertThat(result.put(row.get(field), row)).as("重复 %s=%s", field, row.get(field)).isNull();
        }
        return result;
    }

    /** 读取原始 INSERT；按 SQL 引号和括号分层，兼容 JSON 和日期函数。 */
    private List<Map<String, String>> readRows(String file, String table) throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get("sql", file)), StandardCharsets.UTF_8)
                .replaceAll("(?m)^\\s*--[^\\r\\n]*", "");
        Matcher matcher = Pattern.compile("(?s)INSERT INTO\\s+`?" + table + "`?\\s*\\((.*?)\\)\\s*VALUES\\s*(.*?);").matcher(sql);
        assertThat(matcher.find()).as("%s 中应包含 %s 的 INSERT", file, table).isTrue();
        // 拆分列和记录，并将 SQL NULL 保留为空值
        List<String> columns = splitValues(matcher.group(1));
        List<Map<String, String>> rows = new ArrayList<>();
        for (String tuple : splitValues(matcher.group(2))) {
            // 解析每一条记录的原始列值
            List<String> values = splitValues(tuple.substring(1, tuple.length() - 1));
            assertThat(values).as("%s %s 的列数", file, table).hasSize(columns.size());
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < columns.size(); i++) {
                String value = values.get(i);
                if (value.startsWith("'")) {
                    value = value.substring(1, value.length() - 1).replace("''", "'");
                } else if ("NULL".equals(value)) {
                    value = null;
                }
                row.put(columns.get(i).replace("`", ""), value);
            }
            rows.add(row);
        }
        return rows;
    }

    /** 仅拆分括号和单引号之外的逗号。 */
    private List<String> splitValues(String source) {
        List<String> values = new ArrayList<>();
        int depth = 0;
        int start = 0;
        boolean quoted = false;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '\'') {
                if (quoted && i + 1 < source.length() && source.charAt(i + 1) == '\'') {
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted) {
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                } else if (ch == ',' && depth == 0) {
                    values.add(source.substring(start, i).trim());
                    start = i + 1;
                }
            }
        }
        values.add(source.substring(start).trim());
        return values;
    }

    /** 提取原始 SQL 中单张表的一条语句。 */
    private String readStatement(String file, String operation, String table) throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get("sql", file)), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("(?s)" + operation + "\\s+`?" + table + "`?\\s*\\(.*?;").matcher(sql);
        assertThat(matcher.find()).as("%s %s", operation, table).isTrue();
        return matcher.group();
    }
}
