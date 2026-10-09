package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.bo.*;
import com.znty.rrs.entity.stockpooladjust.*;
import com.znty.rrs.entity.investmentpool.InvestmentPoolDto;
import com.znty.rrs.entity.stockpoolexcelimport.*;
import java.io.ByteArrayOutputStream;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.mockito.AdditionalAnswers;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实 Mapper、内存事务和主行锁验证股票Excel来源整批提交、复核和审批落池。 */
public class StockPoolAdjustExcelSubmitServiceTest {
    /** 每个测试独立的附件存储目录，结束后由 JUnit 清理。 */
    @Rule public TemporaryFolder uploadDirectory = new TemporaryFolder();
    /** 独立内存数据库。 */
    private JdbcTemplate jdbc;
    /** 事务管理器，所有写入通过服务代理提交。 */
    private DataSourceTransactionManager transactions;
    /** 真实股票调整 Mapper。 */
    private StockPoolAdjustMapper adjustments;
    /** 真实池规则和权限查询，仅路径展示隔离H2递归语法差异。 */
    private InvestmentPoolMapper pools;
    /** 真实申请服务。 */
    private StockPoolAdjustService apply;

    /** 真实审批服务。 */
    private StockPoolAdjustFlowService audit;
    /** 真实公共附件服务。 */
    private SysAttachmentService attachments;
    /** 流程配置使用受控节点，业务 Mapper 和事务均为真实实现。 */
    private FlowMapper flows;
    /** 内存 Mapper 会话模板。 */
    private SqlSessionTemplate session;
    /** 所有审批处理人，测试会签时增加第二位。 */
    private List<NodeApprovalHandlerBo> handlers;

    /** 创建六张股票表及依赖的公共表，仅在 H2 中执行 DDL。 */
    @Before public void setUp() throws Exception {
        // 防止使用模拟 Mapper 的其他测试遗留分页线程状态。
        PageHelper.clearPage();
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:stock" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        jdbc = new JdbcTemplate(source);
        transactions = new DataSourceTransactionManager(source);
        Configuration config = new Configuration(new Environment("test", new SpringManagedTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        config.setLocalCacheScope(LocalCacheScope.STATEMENT);
        PageInterceptor paging = new PageInterceptor();
        Properties properties = new Properties(); properties.setProperty("helperDialect", "h2");
        paging.setProperties(properties); config.addInterceptor(paging);
        for (String name : Arrays.asList("StockPoolAdjust", "InvestmentPool", "SysAttachment", "Report", "StockPoolExcelImport")) {
            String resource = "mapper/" + name + "Mapper.xml";
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
            }
        }
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(config);
        session = new SqlSessionTemplate(factory);
        // 从真正的股票 DDL 提取建表，排除真实数据库 USE、清空及 MySQL 表选项。
        for (String name : Arrays.asList("stock/rrs_stockinfo_schema.sql", "stock/rrs_stock_pool_adjust_schema.sql",
                "rrs_pool_init_schema.sql", "rrs_sys_attachment_schema.sql", "rrs_import_temp_schema.sql")) {
            String ddl = new String(Files.readAllBytes(Paths.get("sql", name)), StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("(?is)CREATE TABLE.*?;").matcher(ddl);
            while (matcher.find()) {
                String table = matcher.group().replaceAll("(?is)\\)\\s*ENGINE\\s*=.*", ")")
                        .replaceAll("(?i)\\bJSON\\b", "VARCHAR(4000)");
                jdbc.execute(table);
            }
        }
        sql("CREATE TABLE dict_security_type(security_type VARCHAR(32),security_type_name VARCHAR(60),category_type VARCHAR(20),sort_order INT,is_deleted INT)",
                "INSERT INTO dict_security_type VALUES('stock_a','A股','stock',1,0),('fund','基金','fund',2,0)",
                "CREATE SCHEMA ais_inv_analysis",
                "CREATE TABLE ais_inv_analysis.t_sys_user_role(user_id BIGINT,role_id BIGINT)",
                "CREATE TABLE ais_inv_analysis.t_sys_role(id BIGINT,enable INT)",
                "CREATE TABLE wf_flow_definition(id BIGINT PRIMARY KEY,flow_key VARCHAR(40),name VARCHAR(40),description VARCHAR(40),is_deleted INT)",
                "CREATE TABLE wf_flow_node(id BIGINT PRIMARY KEY,flow_id BIGINT)",
                "INSERT INTO wf_flow_definition VALUES(10,'stock_normal','股票一般','股票',0),(20,'stock_fast','股票快速','股票',0)",
                "INSERT INTO wf_flow_node VALUES(101,10),(102,10),(103,10),(104,10),(105,10),(201,20),(202,20),(205,20)");
        stock("600001.SH"); stock("600002.SH"); stock("600003.SH");
        for (int id=1; id<=3; id++) {
            jdbc.update("INSERT INTO ip_investment_pool(id,pool_name,pool_code,pool_type,market_codes,variety_codes,status,is_deleted,in_flow_id,in_flow_key,out_flow_id,out_flow_key,simple_in_flow_id,simple_in_flow_key,simple_out_flow_id,simple_out_flow_key,in_report_restriction,out_report_restriction) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    id,"股票池"+id,"stock_"+id,"stock","[\"SSE\"]","[\"stock\"]","enabled",0,10,"stock_normal",10,"stock_normal",20,"stock_fast",20,"stock_fast","none","none");
        }
        adjustments = session.getMapper(StockPoolAdjustMapper.class);
        pools = mock(InvestmentPoolMapper.class, AdditionalAnswers.delegatesTo(session.getMapper(InvestmentPoolMapper.class)));
        List<InvestmentPoolDto> pathsForDisplay = new ArrayList<>();
        for (long id = 1; id <= 3; id++) {
            InvestmentPoolDto row = new InvestmentPoolDto();
            row.setId(id); row.setPoolFullName("股票库/股票池" + id); pathsForDisplay.add(row);
        }
        doReturn(pathsForDisplay).when(pools).queryPoolFullNameList();
        InvestmentPoolService paths = mock(InvestmentPoolService.class);
        Map<Long,String> names = new HashMap<>(); names.put(1L,"股票池1"); names.put(2L,"股票池2"); names.put(3L,"股票池3");
        when(paths.queryPoolFullNameMap()).thenReturn(names);
        attachments = new SysAttachmentService();
        ReflectionTestUtils.setField(attachments,"sysAttachmentMapper",session.getMapper(SysAttachmentMapper.class));
        ReflectionTestUtils.setField(attachments,"storagePath",uploadDirectory.getRoot().getAbsolutePath());
        ReportService reports = new ReportService();
        ReflectionTestUtils.setField(reports,"reportMapper",session.getMapper(ReportMapper.class));
        ReflectionTestUtils.setField(reports,"sysAttachmentService",attachments);
        ReflectionTestUtils.setField(reports,"investmentPoolService",paths);
        flows = mock(FlowMapper.class); handlers = new ArrayList<>();
        configureFlow(10L,false,"preempt"); configureFlow(20L,true,"preempt");
        apply = new StockPoolAdjustService();
        ReflectionTestUtils.setField(apply,"stockPoolAdjustMapper",adjustments);
        ReflectionTestUtils.setField(apply,"investmentPoolMapper",pools);
        ReflectionTestUtils.setField(apply,"investmentPoolService",paths);
        ReflectionTestUtils.setField(apply,"flowMapper",flows);
        ReflectionTestUtils.setField(apply,"sysAttachmentService",attachments);
        ReflectionTestUtils.setField(apply,"reportService",reports);
        apply = transactional(apply);
        audit = new StockPoolAdjustFlowService();
        ReflectionTestUtils.setField(audit,"stockPoolAdjustMapper",adjustments);
        ReflectionTestUtils.setField(audit,"flowMapper",flows);
        ReflectionTestUtils.setField(audit,"stockPoolAdjustService",apply);
        ReflectionTestUtils.setField(audit,"sysAttachmentService",attachments);
        ReflectionTestUtils.setField(audit,"investmentPoolMapper",pools);
        audit = transactional(audit);
    }





















































    /** 失败关系项保留原因但不阻断可提交主项。 */
    @Test public void failedRelationShouldRemainVisibleAndBeSkippedOnSubmit() throws Exception {
        sql("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(1,'in_linked',2,0)",
                "UPDATE ip_investment_pool SET status='disabled' WHERE id=2");
        StockPoolExcelImportService imports = importService();
        StockPoolExcelImportReq req = upload(imports, false, true, "in", "600001.SH");
        StockPoolExcelImportDto checked = imports.checkImport(req);
        assertThat(checked.getCheckItems()).hasSize(2);
        assertThat(checked.getCheckItems()).anyMatch(row -> "linkage".equals(row.getItemTag()) && !row.isCanAdjust() && !row.getFailReasons().isEmpty());
        imports.submitImport(req);
        assertThat(count("ip_adjust_log_stock")).isEqualTo(1);
        audit.submitAdjustAudit(auditReq(batchGroup("600001.SH"), "5", "approve"));
        assertThat(count("ip_pool_status_stock")).isEqualTo(1);
    }

    /** 同股票不同目标池在写入前全部复核，来源行独立审批且提交不落池。 */
    @Test public void importShouldPrepareAllSourcesBeforeWritingAndApplyOnlyAtApproval() throws Exception {
        StockPoolExcelImportService imports = importService();
        StockPoolExcelImportReq req = upload(imports, false, false, "in", "600001.SH", "600001.SH");
        StockPoolExcelImportDto checked = imports.checkImport(req);
        assertThat(checked.getCheckItems()).allMatch(StockPoolExcelImportCheckItemDto::isCanAdjust);
        StockPoolExcelImportDto submitted = imports.submitImport(req);
        assertThat(submitted.getAdjustBatchNoList()).hasSize(2).doesNotHaveDuplicates();
        assertThat(jdbc.queryForList("SELECT adjust_type FROM ip_adjust_log_stock", String.class)).containsOnly("Excel导入");
        assertThat(jdbc.queryForList("SELECT audit_status FROM ip_adjust_log_stock", String.class)).containsOnly("00");
        assertThat(count("ip_pool_status_stock")).isZero();
        assertThatThrownBy(() -> imports.submitImport(req)).isInstanceOf(BizException.class).hasMessageContaining("已提交");
        for (int index = 0; index < submitted.getLogIds().size(); index++) {
            StockAdjustSubmitDto group = new StockAdjustSubmitDto();
            group.setAdjustLogIds(Collections.singletonList(submitted.getLogIds().get(index)));
            group.setAdjustBatchNos(Collections.singletonList(submitted.getAdjustBatchNoList().get(index)));
            audit.submitAdjustAudit(auditReq(group, "5", "approve"));
        }
        assertThat(count("ip_pool_status_stock")).isEqualTo(2);
    }

    /** 调出API方向转换为内部调出编码，提交时仍保留当前成员。 */
    @Test public void outboundShouldUseInternalModeAndWaitForApproval() throws Exception {
        sql("INSERT INTO ip_pool_status_stock(stock_code,target_pool_id,audit_status,is_deleted) VALUES('600001.SH',1,'20',0)");
        StockPoolExcelImportService imports = importService();
        StockPoolExcelImportReq req = upload(imports, false, false, "out", "600001.SH");
        assertThat(imports.checkImport(req).getCheckItems().get(0).isCanAdjust()).isTrue();
        imports.submitImport(req);
        assertThat(jdbc.queryForObject("SELECT adjust_mode FROM ip_adjust_log_stock", String.class)).isEqualTo("调出");
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM ip_pool_status_stock", Integer.class)).isZero();
        audit.submitAdjustAudit(auditReq(batchGroup("600001.SH"), "5", "approve"));
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM ip_pool_status_stock", Integer.class)).isEqualTo(1);
    }

    /** 后一来源写入失败，先前日志、审批步骤及导入保存结果全部回滚。 */
    @Test public void secondSourceWriteFailureShouldRollBackEntireImport() throws Exception {
        StockPoolExcelImportService imports = importService();
        StockPoolExcelImportReq req = upload(imports, false, false, "in", "600001.SH", "600002.SH");
        imports.checkImport(req);
        String snapshot = jdbc.queryForObject("SELECT result_json FROM sys_imp_tmp", String.class);
        StockPoolAdjustMapper observed = observeAdjustments();
        doAnswer(call -> {
            StockAdjustLogBo log = call.getArgument(0);
            if ("600002.SH".equals(log.getStockCode())) { throw new IllegalStateException("模拟后续来源写入失败"); }
            return adjustments.addAdjustLog(log);
        }).when(observed).addAdjustLog(any());
        assertThatThrownBy(() -> imports.submitImport(req)).isInstanceOf(IllegalStateException.class);
        assertThat(count("ip_adjust_log_stock")).isZero();
        assertThat(count("ip_adjust_step_stock")).isZero();
        assertThat(count("ip_pool_status_stock")).isZero();
        assertThat(jdbc.queryForObject("SELECT save_rslt FROM sys_imp_tmp", String.class)).isEqualTo("0");
        assertThat(jdbc.queryForObject("SELECT result_json FROM sys_imp_tmp", String.class)).isEqualTo(snapshot);
    }

    /** 联动关闭只保存主项，开启后保存通过的关系项并由主项统一审批。 */
    @Test public void relationSwitchShouldUseSavedOptionsAndIncludeOnlyPassingRelations() throws Exception {
        sql("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(1,'in_linked',2,0)");
        StockPoolExcelImportService imports = importService();
        StockPoolExcelImportReq req = upload(imports, false, false, "in", "600001.SH");
        assertThat(imports.checkImport(req).getCheckItems()).hasSize(1);
        req.setAllowLinkMutex(true);
        imports.submitImport(req);
        assertThat(count("ip_adjust_log_stock")).isEqualTo(1);
        req = upload(imports, false, true, "in", "600002.SH");
        assertThat(imports.checkImport(req).getCheckItems()).hasSize(2);
        imports.submitImport(req);
        assertThat(jdbc.queryForList("SELECT adjust_type FROM ip_adjust_log_stock WHERE stock_code='600002.SH'", String.class))
                .containsExactlyInAnyOrder("Excel导入", "联动调整");
    }

    /** 关闭关系开关也不可绕过硬性来源池规则。 */
    @Test public void disabledRelationsShouldStillEnforceHardSourceRules() throws Exception {
        sql("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(1,'source',2,0)");
        StockPoolExcelImportService imports = importService();
        StockPoolExcelImportReq req = upload(imports, false, false, "in", "600001.SH");
        assertThat(imports.checkImport(req).getCheckItems().get(0).isCanAdjust()).isFalse();
        assertThatThrownBy(() -> imports.submitImport(req)).isInstanceOf(BizException.class);
        assertThat(count("ip_adjust_log_stock")).isZero();
    }

    /** 关系在校验后新增，提交必须要求重校验且不能写入旧有效集合。 */
    @Test public void changedRelationsShouldRejectStaleSnapshot() throws Exception {
        StockPoolExcelImportService imports = importService();
        StockPoolExcelImportReq req = upload(imports, false, true, "in", "600001.SH");
        imports.checkImport(req);
        sql("INSERT INTO ip_pool_relation(pool_id,relation_type,relation_pool_id,is_deleted) VALUES(1,'in_linked',2,0)");
        assertThatThrownBy(() -> imports.submitImport(req)).isInstanceOf(BizException.class).hasMessageContaining("关系项已变化");
        assertThat(count("ip_adjust_log_stock")).isZero();
    }

    /** 具有调整权限仍须另有Excel权限，两种权限在最新检查时独立生效。 */
    @Test public void shouldRequireBothPermissions() throws Exception {
        StockPoolExcelImportService imports = importService();
        StockPoolExcelImportReq req = upload(imports, false, false, "in", "600001.SH");
        req.setCurrentUserId("2");
        sql("INSERT INTO ip_pool_permission(pool_id,permission_type,handler_type,handler_id,is_deleted) VALUES(1,'adjustable','user',2,0)");
        assertThat(imports.checkImport(req).getCheckItems().get(0).getFailReasons()).anyMatch(value -> value.contains("Excel"));
        sql("INSERT INTO ip_pool_permission(pool_id,permission_type,handler_type,handler_id,is_deleted) VALUES(1,'excel_importable','user',2,0)");
        assertThat(imports.checkImport(req).getCheckItems().get(0).isCanAdjust()).isTrue();
        sql("DELETE FROM ip_pool_permission WHERE permission_type='adjustable'");
        assertThat(imports.checkImport(req).getCheckItems().get(0).getFailReasons()).anyMatch(value -> value.contains("调整权限"));
        sql("INSERT INTO ip_pool_permission(pool_id,permission_type,handler_type,handler_id,is_deleted) VALUES(1,'adjustable','user',2,0)");
        imports.checkImport(req);
        sql("DELETE FROM ip_pool_permission WHERE permission_type='excel_importable'");
        assertThatThrownBy(() -> imports.submitImport(req)).isInstanceOf(BizException.class).hasMessageContaining("Excel");
        assertThat(count("ip_adjust_log_stock")).isZero();
    }

    /** 快速流程配置为一般入口及无人审批流程均不允许导入。 */
    @Test public void automaticGeneralFlowShouldBeRejectedWithoutChangingSingleFastFlow() throws Exception {
        StockPoolExcelImportService imports = importService();
        StockPoolExcelImportReq req = upload(imports, false, false, "in", "600001.SH");
        sql("UPDATE ip_investment_pool SET in_flow_id=20,in_flow_key='stock_fast' WHERE id=1");
        assertThat(imports.checkImport(req).getCheckItems().get(0).isCanAdjust()).isFalse();
        assertThat(count("ip_adjust_log_stock")).isZero();
        assertThat(status(apply.addAdjustLog(request("600002.SH", true, 1L)))).isEqualTo("20");
    }

    /** 一般流程必须使用当前目标池的已发布版本与Key。 */
    @Test public void unpublishedAndMismatchedGeneralFlowShouldBeBlocked() throws Exception {
        StockPoolExcelImportService imports = importService();
        StockPoolExcelImportReq req = upload(imports, false, false, "in", "600001.SH");
        sql("UPDATE ip_investment_pool SET in_flow_key='wrong-key' WHERE id=1");
        assertThat(imports.checkImport(req).getCheckItems().get(0).isCanAdjust()).isFalse();
        sql("UPDATE ip_investment_pool SET in_flow_key='stock_normal' WHERE id=1");
        when(flows.queryFlowVersionByFlowIdList(10L, null)).thenReturn(Collections.emptyList());
        assertThat(imports.checkImport(req).getCheckItems().get(0).isCanAdjust()).isFalse();
        assertThat(count("ip_adjust_log_stock")).isZero();
    }

    /** 初始路由直接结束时，孤立人工节点不能使自动一般流程被误判为可导入。 */
    @Test public void unreachableManualNodeShouldNotMakeAutomaticFlowSelectable() throws Exception {
        StockPoolExcelImportService imports = importService();
        StockPoolExcelImportReq req = upload(imports, false, false, "in", "600001.SH");
        when(flows.queryFlowEdgeListByVersionId(10L)).thenReturn(Arrays.asList(
                edge(101, 102, "auto"), edge(102, 105, "submit"), edge(103, 105, "approve")));
        assertThat(imports.checkImport(req).getCheckItems().get(0).isCanAdjust()).isFalse();
        assertThatThrownBy(() -> imports.submitImport(req)).isInstanceOf(BizException.class);
        assertThat(count("ip_adjust_log_stock")).isZero();
    }

    /** 校验后新增报告要求阻断提交，已提交后终审和驳回修改亦继续复核。 */
    @Test public void latestReportRequirementsShouldBlockSubmissionApprovalAndRejectedModification() throws Exception {
        // 修改后重提还需一位未参与审批的处理人。
        addHandler(106L, 103L, 6L);
        StockPoolExcelImportService imports = importService();
        StockPoolExcelImportReq req = upload(imports, false, false, "in", "600001.SH");
        imports.checkImport(req);
        sql("UPDATE ip_investment_pool SET in_report_restriction='any' WHERE id=1");
        assertThatThrownBy(() -> imports.submitImport(req)).isInstanceOf(BizException.class).hasMessageContaining("报告");
        assertThat(count("ip_adjust_log_stock")).isZero();
        sql("UPDATE ip_investment_pool SET in_report_restriction='none' WHERE id=1");
        imports.checkImport(req); imports.submitImport(req);
        StockAdjustSubmitDto group = batchGroup("600001.SH");
        sql("UPDATE ip_investment_pool SET in_report_restriction='any' WHERE id=1");
        assertThatThrownBy(() -> audit.submitAdjustAudit(auditReq(group, "5", "approve")))
                .isInstanceOf(BizException.class).hasMessageContaining("报告");
        assertThat(status(group)).isEqualTo("00"); assertThat(count("ip_pool_status_stock")).isZero();
        audit.submitAdjustAudit(auditReq(group, "5", "reject"));
        assertThatThrownBy(() -> audit.submitAdjustAudit(auditReq(group, "1", "approve")))
                .isInstanceOf(BizException.class).hasMessageContaining("报告");
        assertThat(status(group)).isEqualTo("11");
    }

    /** 最终审批读取当前评级，提交后降级不能通过旧评级快照落池。 */
    @Test public void latestRatingShouldBlockFinalApproval() throws Exception {
        sql("UPDATE ip_investment_pool SET grade_astrict='buy' WHERE id=1",
                "INSERT INTO rrs_stock_rating(stock_code,rating_code,rating_date,is_deleted) VALUES('600001.SH','buy','2026-10-01',0)");
        StockPoolExcelImportService imports = importService();
        StockPoolExcelImportReq req = upload(imports, false, false, "in", "600001.SH");
        imports.checkImport(req); imports.submitImport(req);
        StockAdjustSubmitDto group = batchGroup("600001.SH");
        sql("INSERT INTO rrs_stock_rating(stock_code,rating_code,rating_date,is_deleted) VALUES('600001.SH','sell','2026-10-08',0)");
        assertThatThrownBy(() -> audit.submitAdjustAudit(auditReq(group, "5", "approve"))).isInstanceOf(BizException.class).hasMessageContaining("评级");
        assertThat(status(group)).isEqualTo("00"); assertThat(count("ip_pool_status_stock")).isZero();
    }

    /** 清空先保存调出申请，成员等待审批且不会提前释放容量。 */
    @Test public void clearShouldSaveFirstWithoutFreeingCapacity() throws Exception {
        sql("INSERT INTO ip_pool_status_stock(stock_code,target_pool_id,audit_status,is_deleted) VALUES('600003.SH',1,'20',0)");
        StockPoolExcelImportService imports = importService();
        StockPoolExcelImportReq req = upload(imports, true, false, "in", "600001.SH");
        assertThat(imports.checkImport(req).getCheckItems()).hasSize(2);
        imports.submitImport(req);
        assertThat(jdbc.queryForList("SELECT adjust_type FROM ip_adjust_log_stock ORDER BY id", String.class)).containsExactly("Excel清空", "Excel导入");
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM ip_pool_status_stock", Integer.class)).isZero();
        sql("UPDATE ip_investment_pool SET out_report_restriction='any' WHERE id=1");
        StockAdjustSubmitDto clearing = batchGroup("600003.SH");
        assertThatThrownBy(() -> audit.submitAdjustAudit(auditReq(clearing, "5", "approve")))
                .isInstanceOf(BizException.class).hasMessageContaining("报告");
        assertThat(status(clearing)).isEqualTo("00");
        sql("UPDATE ip_investment_pool SET max_capacity=1 WHERE id=1");
        req = upload(imports, true, false, "in", "600002.SH");
        assertThat(imports.checkImport(req).getCheckItems()).anyMatch(row -> "manual".equals(row.getItemTag()) && !row.isCanAdjust());
    }

    /** 构造绑定真实通用临时表、股票服务及事务的导入编排。 */
    private StockPoolExcelImportService importService() {
        sql("INSERT INTO ip_investment_pool(id,pool_name,status,is_deleted) VALUES(10,'股票库','enabled',0)",
                "UPDATE ip_investment_pool SET parent_id=10 WHERE id IN (1,2,3)");
        StockPoolExcelImportService target = new StockPoolExcelImportService();
        ReflectionTestUtils.setField(target, "stockPoolExcelImportMapper", session.getMapper(StockPoolExcelImportMapper.class));
        ReflectionTestUtils.setField(target, "stockPoolAdjustMapper", adjustments);
        ReflectionTestUtils.setField(target, "stockPoolAdjustService", apply);
        ReflectionTestUtils.setField(target, "investmentPoolMapper", pools);
        ReflectionTestUtils.setField(target, "sysAttachmentService", attachments);
        return transactional(target);
    }

    /** 上传真实四列文件，各来源按顺序映射不同目标池。 */
    private StockPoolExcelImportReq upload(StockPoolExcelImportService imports, boolean clear, boolean relations,
                                           String direction, String... codes) throws Exception {
        StockPoolExcelImportReq req = new StockPoolExcelImportReq();
        req.setCurrentUserId("1"); req.setCurrentUserName("管理员"); req.setDirection(direction);
        req.setClearTarget(clear); req.setAllowLinkMutex(relations);
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            book.createSheet("股票导入").createRow(0);
            String[] headers = {"父池名称", "子池名称", "股票名称", "股票代码"};
            for (int index = 0; index < 4; index++) { book.getSheetAt(0).getRow(0).createCell(index).setCellValue(headers[index]); }
            for (int row = 0; row < codes.length; row++) {
                book.getSheetAt(0).createRow(row + 1);
                String[] values = {"股票库", "股票池" + (row + 1), "名单名称", codes[row]};
                for (int column = 0; column < 4; column++) { book.getSheetAt(0).getRow(row + 1).createCell(column).setCellValue(values[column]); }
            }
            book.write(bytes);
            req.setImpId(imports.uploadExcel(req, new MockMultipartFile("file", "股票.xlsx", "application/octet-stream", bytes.toByteArray()), null).getImpId());
        }
        return req;
    }

    /** 设置发布的一般/快速流程和对应节点、连线、用户。 */
    private void configureFlow(Long id,boolean fast,String strategy) {
        FlowDefinitionBo definition=new FlowDefinitionBo(); definition.setId(id); definition.setFlowKey(fast?"stock_fast":"stock_normal"); definition.setName("股票流程"); definition.setStatus("active");
        FlowVersionBo version=new FlowVersionBo(); version.setId(id); version.setStatus("active");
        List<FlowNodeBo> nodes=new ArrayList<>(); List<FlowEdgeBo> edges=new ArrayList<>(); List<NodeApprovalConfigBo> configs=new ArrayList<>();
        long base=id*10; nodes.add(node(base+1,id,"start","开始")); nodes.add(node(base+2,id,"approval","发起")); nodes.add(node(base+5,id,"end","结束"));
        configs.add(config(base+2,"initiator")); edges.add(edge(base+1,base+2,"auto"));
        if(fast) { edges.add(edge(base+2,base+5,"submit")); }
        else {
            nodes.add(node(base+3,id,"approval","审核")); nodes.add(node(base+4,id,"approval","修改"));
            configs.add(config(base+3,strategy)); configs.add(config(base+4,"initiator"));
            edges.add(edge(base+2,base+3,"submit")); edges.add(edge(base+3,base+5,"approve")); edges.add(edge(base+3,base+4,"reject"));
            edges.add(edge(base+4,base+3,"resubmit")); edges.add(edge(base+4,base+5,"reject"));
            handlers.removeIf(h -> h.getApprovalConfigId().equals(base+3)); addHandler(base+3,base+3,5L);
        }
        when(flows.queryFlowById(id)).thenReturn(definition); when(flows.queryFlowVersionByFlowIdList(id,null)).thenReturn(Collections.singletonList(version));
        when(flows.queryFlowNodeListByVersionId(id)).thenReturn(nodes); when(flows.queryFlowEdgeListByVersionId(id)).thenReturn(edges);
        when(flows.queryApprovalConfigListByVersionId(id)).thenReturn(configs);
        when(flows.queryApprovalHandlerListByVersionId(id)).thenAnswer(call -> new ArrayList<>(handlers));
        for(FlowNodeBo node:nodes) when(flows.queryFlowNodeById(node.getId())).thenReturn(node);
    }

    /** 创建流程节点。 */
    private FlowNodeBo node(long id,long version,String type,String label) { FlowNodeBo n=new FlowNodeBo();n.setId(id);n.setVersionId(version);n.setNodeId("n"+id);n.setNodeType(type);n.setLabel(label);n.setSortOrder((int)(id%10));return n; }
    /** 创建动作路由连线。 */
    private FlowEdgeBo edge(long from,long to,String action) { FlowEdgeBo e=new FlowEdgeBo();e.setFromNodeId(from);e.setToNodeId(to);e.setRouteAction(action);return e; }
    /** 创建审批配置。 */
    private NodeApprovalConfigBo config(long node,String strategy) { NodeApprovalConfigBo c=new NodeApprovalConfigBo();c.setId(node);c.setNodeId(node);c.setApprovalStrategy(strategy);return c; }
    /** 添加用户审批人。 */
    private void addHandler(long id,long config,long user) { NodeApprovalHandlerBo h=new NodeApprovalHandlerBo();h.setId(id);h.setApprovalConfigId(config);h.setHandlerType("user");h.setHandlerId(user);h.setHandlerName("审批人"+user);handlers.add(h); }
    /** 在内存数据库准备有效股票基础主行。 */
    private void stock(String code) { jdbc.update("INSERT INTO rrs_stockinfo(stock_code,stock_name,stock_short_name,industry_code,industry_name,security_type,security_status,market_code,is_deleted,previous_close_price) VALUES(?,?,?,?,?,?,?,?,?,?)",code,"股票"+code,"股票简称","A02","林业","stock_a","L","SSE",0,0); }
    /** 创建手工申请。 */
    private StockPoolAdjustSubmitReq request(String code,boolean fast,Long pool) { StockPoolAdjustSubmitReq q=new StockPoolAdjustSubmitReq();q.setStockCode(code);q.setAdjusterId("1");q.setAdjusterName("管理员");q.setItems(new ArrayList<>(Collections.singletonList(item(pool,"manual",fast,"调入"))));return q; }
    /** 查询批量提交后本股票的真实日志及独立批次，复用既有审核请求助手。 */
    private StockAdjustSubmitDto batchGroup(String code) {
        StockAdjustSubmitDto group = new StockAdjustSubmitDto();
        group.setAdjustLogIds(jdbc.queryForList("SELECT id FROM ip_adjust_log_stock WHERE stock_code=? ORDER BY id", Long.class, code));
        group.setAdjustBatchNos(jdbc.queryForList("SELECT DISTINCT adjust_batch_no FROM ip_adjust_log_stock WHERE stock_code=?", String.class, code));
        return group;
    }
    /** 保持真实 Mapper 行为，同时允许观察或模拟指定写入失败。 */
    private StockPoolAdjustMapper observeAdjustments() {
        StockPoolAdjustMapper observed = mock(StockPoolAdjustMapper.class, AdditionalAnswers.delegatesTo(adjustments));
        ReflectionTestUtils.setField(apply, "stockPoolAdjustMapper", observed);
        return observed;
    }
    /** 创建同组调整项。 */
    private StockPoolAdjustSubmitReq.AdjustItem item(Long pool,String tag,boolean fast,String direction) { StockPoolAdjustSubmitReq.AdjustItem i=new StockPoolAdjustSubmitReq.AdjustItem();i.setTargetPoolId(pool);i.setItemTag(tag);i.setAdjustMode(direction);i.setAdjustGroupKey("stock-group-1");i.setFlowId(fast?20L:10L);i.setFlowKey(fast?"stock_fast":"stock_normal");i.setFlowType((fast?"fast":"normal")+(direction.equals("调入")?"Inbound":"Outbound"));return i; }
    /** 从真实待办取得审核请求，未授权用户使用现存待办 ID 验证权限。 */
    private StockPoolAdjustAuditReq auditReq(StockAdjustSubmitDto submitted,String user,String action) { List<StockAdjustStepBo> steps=adjustments.queryAdjustStepByBatchList(submitted.getAdjustLogIds().get(0),submitted.getAdjustBatchNos().get(0));StockAdjustStepBo s=steps.stream().filter(row -> "pending".equals(row.getStepStatus()) && user.equals(row.getHandlerId())).findFirst().orElseGet(() -> steps.stream().filter(row -> "pending".equals(row.getStepStatus())).findFirst().get());StockPoolAdjustAuditReq req=new StockPoolAdjustAuditReq();req.setStepId(s.getId());req.setAdjustLogId(s.getAdjustLogId());req.setAdjustBatchNo(s.getAdjustBatchNo());req.setHandlerId(user);req.setHandlerName("用户"+user);req.setProcessAction(action);req.setProcessComment("处理意见");return req; }
    /** 当前批次状态。 */
    private String status(StockAdjustSubmitDto q) { return jdbc.queryForObject("SELECT audit_status FROM ip_adjust_log_stock WHERE id=?",String.class,q.getAdjustLogIds().get(0)); }
    /** 查询固定测试表行数。 */
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class); }
    /** 在 H2 执行固定测试数据准备。 */
    private void sql(String... commands) { for(String command:commands) jdbc.execute(command); }
    /** 使用服务注解创建真正事务代理。 */
    @SuppressWarnings("unchecked") private <T> T transactional(T target) { ProxyFactory proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));return (T)proxy.getProxy(); }
}
