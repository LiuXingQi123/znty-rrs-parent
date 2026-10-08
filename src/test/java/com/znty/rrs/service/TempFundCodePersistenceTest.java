package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.entity.bo.FundAdjustLogBo;
import com.znty.rrs.entity.bo.SysAttachmentBo;
import com.znty.rrs.entity.tempfundcode.TempFundCodeDto;
import com.znty.rrs.entity.tempfundcode.TempFundCodeReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FundPoolAdjustMapper;
import com.znty.rrs.mapper.SysAttachmentMapper;
import com.znty.rrs.mapper.TempFundCodeMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
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
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 使用生产建表列、真实 XML 和 Spring 事务验证基金临时代码，不连接业务数据库。 */
public class TempFundCodePersistenceTest {
    /** 内存库查询组件 */
    private JdbcTemplate jdbc;
    /** 生产基金临时代码 Mapper */
    private TempFundCodeMapper mapper;
    /** 基金日志及主档共同锁 Mapper */
    private FundPoolAdjustMapper fundMapper;
    /** 生产附件 Mapper */
    private SysAttachmentMapper attachmentMapper;
    /** 带注解事务的真实业务服务 */
    private TempFundCodeService service;
    /** 事务模板，用于准备真实基金日志 */
    private TransactionTemplate transactions;

    /** 创建隔离内存库，仅在内存库执行生产 DDL 的删表和建表语句。 */
    @Before
    public void setUp() throws Exception {
        // 隔离其他 Mock Mapper 测试未经过分页拦截器清理的线程分页状态
        PageHelper.clearPage();
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 持久化测试需 Java 11 以上运行时", major >= 11);
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.h2.Driver");
        source.setUrl("jdbc:h2:mem:temp_fund_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;LOCK_TIMEOUT=10000");
        jdbc = new JdbcTemplate(source);
        // 只在隔离内存库创建生产结构，避免测试与实际列契约脱节
        createSchema("sql/fund/rrs_temp_fund_code_schema.sql");
        // 创建基金主档
        createSchema("sql/fund/rrs_fundinfo_schema.sql");
        // 创建基金日志、步骤与池状态
        createSchema("sql/fund/rrs_fund_pool_adjust_schema.sql");
        // 创建附件结构
        createSchema("sql/rrs_sys_attachment_schema.sql");
        jdbc.execute("CREATE TABLE dict_security_type (id BIGINT PRIMARY KEY,security_type VARCHAR(64)"
                + ",security_type_name VARCHAR(100),category_type VARCHAR(32),sort_order INT,is_deleted INT)");
        jdbc.update("INSERT INTO dict_security_type VALUES (1,'etf_fund','ETF基金','fund',1,0)"
                + ",(2,'open_fund','开放式基金','fund',2,0),(3,'mtn','中期票据','bond',3,0)");
        jdbc.update("INSERT INTO rrs_fundinfo(fund_code,fund_name,fund_short_name,security_type,market_code"
                + ",security_status,source_system,is_deleted,crte_time,updt_time)"
                + " VALUES ('FORMAL001','正式基金全称','正式基金','etf_fund','SSE','L',NULL,0,NOW(),NOW())");
        Configuration config = new Configuration();
        config.setMapUnderscoreToCamelCase(true);
        config.addInterceptor(new PageInterceptor());
        config.setEnvironment(new Environment("test", new SpringManagedTransactionFactory(), source));
        for (String file : Arrays.asList("TempFundCodeMapper.xml", "FundPoolAdjustMapper.xml", "SysAttachmentMapper.xml")) {
            try (InputStream xml = getClass().getResourceAsStream("/mapper/" + file)) {
                new XMLMapperBuilder(xml, config, file, config.getSqlFragments()).parse();
            }
        }
        SqlSessionTemplate session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        mapper = session.getMapper(TempFundCodeMapper.class);
        fundMapper = session.getMapper(FundPoolAdjustMapper.class);
        attachmentMapper = session.getMapper(SysAttachmentMapper.class);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        transactions = new TransactionTemplate(manager);
        SysAttachmentService attachments = new SysAttachmentService();
        ReflectionTestUtils.setField(attachments, "sysAttachmentMapper", attachmentMapper);
        TempFundCodeService target = new TempFundCodeService();
        ReflectionTestUtils.setField(target, "tempFundCodeMapper", mapper);
        ReflectionTestUtils.setField(target, "fundPoolAdjustMapper", fundMapper);
        ReflectionTestUtils.setField(target, "sysAttachmentService", attachments);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        service = (TempFundCodeService) proxy.getProxy();
    }

    /** 释放本用例独立的内存库。 */
    @After
    public void tearDown() {
        PageHelper.clearPage();
        if (jdbc != null) {
            jdbc.execute("SET DB_CLOSE_DELAY 0");
        }
    }

    /** 新增实际占位和审计，名称、类型和来源均来自明确输入。 */
    @Test
    public void shouldCreatePlaceholderAndAuditWithoutInventedMasterFields() {
        // 使用生产服务新增四项信息
        TempFundCodeDto row = addTemporary("TMP001");
        assertThat(row.getStatus()).isEqualTo("temporary");
        assertThat(row.getUpdateTime()).isNull();
        assertThat(jdbc.queryForMap("SELECT fund_name,fund_short_name,security_status,source_system"
                + ",market_code,security_type FROM rrs_fundinfo WHERE fund_code='TMP001'"))
                .containsEntry("fund_name", "临时基金TMP001")
                .containsEntry("fund_short_name", "临时基金TMP001")
                .containsEntry("security_status", "L")
                .containsEntry("source_system", "temporary")
                .containsEntry("market_code", "OTC")
                .containsEntry("security_type", "open_fund");
        assertThat(jdbc.queryForMap("SELECT oprt_type,opter_id,temp_fund_code FROM rrs_temp_fund_code_evt"))
                .containsEntry("oprt_type", "INSERT").containsEntry("opter_id", "1")
                .containsEntry("temp_fund_code", "TMP001");
        assertThat(mapper.queryTemporaryCodeCountByFundCode("TMP001")).isEqualTo(1);
        assertThat(mapper.queryFundTypeList()).hasSize(2);
    }

    /** 重复执行生产建表脚本时重建登记、审计及替换明细三表，不清除基金业务表。 */
    @Test
    public void shouldRebuildManagementTablesWhenSchemaRunsAgain() throws Exception {
        // 构造真实登记及新增审计
        TempFundCodeDto row = addTemporary("TMP001");
        // 构造在途引用，以转正操作生成替换明细
        log("TMP001", "00", 10L);
        // 从实际业务入口完成转正，生成登记、操作审计及替换明细
        service.editTempFundCodeToUpdated(operation(row.getId(), "FORMAL001"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_fund_code", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_fund_code_evt", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_fund_code_update_log", Integer.class)).isEqualTo(1);

        // 再次按生产脚本顺序删表并建表，验证三张管理表完整重建
        createSchema("sql/fund/rrs_temp_fund_code_schema.sql");

        assertThat(jdbc.queryForList("SELECT * FROM rrs_temp_fund_code")).isEmpty();
        assertThat(jdbc.queryForList("SELECT * FROM rrs_temp_fund_code_evt")).isEmpty();
        assertThat(jdbc.queryForList("SELECT * FROM rrs_temp_fund_code_update_log")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_fundinfo", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT fund_code FROM ip_adjust_log_fund", String.class)).isEqualTo("FORMAL001");
    }

    /** 真实分页包含删除状态，正式选项排除临时和终止主档并限制五十条。 */
    @Test
    public void shouldFilterPageAndFormalCandidatesUsingProductionSql() {
        // 准备删除登记和主档保留口径
        TempFundCodeDto row = addTemporary("TMP001");
        // 只删除登记，不删除主档
        service.deleteTempFundCode(operation(row.getId(), null));
        TempFundCodeReq query = new TempFundCodeReq();
        query.setStatusList(Arrays.asList("deleted"));
        query.setOprtSourceList(Arrays.asList("manual"));
        query.setPageSize(10);
        assertThat(service.queryTempFundCodePage(query).getTotal()).isEqualTo(1);
        assertThat(service.queryFormalFundOptionList(null)).extracting("fundCode").containsExactly("FORMAL001");
        jdbc.update("UPDATE rrs_fundinfo SET security_status='D' WHERE fund_code='FORMAL001'");
        assertThat(service.queryFormalFundOptionList(null)).isEmpty();
        for (int index = 0; index < 60; index++) {
            jdbc.update("INSERT INTO rrs_fundinfo(fund_code,fund_name,fund_short_name,security_type,market_code"
                    + ",security_status,source_system,is_deleted,updt_time) VALUES (?,?,?,'etf_fund','SSE','L','wind',0,NOW())",
                    "F" + index, "正式" + index, "简称" + index);
        }
        assertThat(service.queryFormalFundOptionList(null)).hasSize(50);
        query.setFundKeyword("简称59");
        assertThat(service.queryFormalFundOptionList(query)).extracting("fundCode").containsExactly("F59");
    }

    /** 在途只替换四字段，主键、参数、步骤和终态历史全部保留。 */
    @Test
    public void shouldReplacePendingRowsAndKeepEndedHistoryAndSteps() {
        // 创建临时代码登记
        TempFundCodeDto row = addTemporary("TMP001");
        // 为 00、11、20、21、99 分别准备历史日志
        FundAdjustLogBo first = log("TMP001", "00", 10L);
        // 准备驳回待修改日志
        FundAdjustLogBo modify = log("TMP001", "11", 11L);
        // 保留审批通过历史
        log("TMP001", "20", 12L);
        // 保留驳回历史
        log("TMP001", "21", 13L);
        // 保留撤回历史
        log("TMP001", "99", 14L);
        jdbc.update("INSERT INTO ip_adjust_step_fund(adjust_log_id,adjust_batch_no,node_code,step_status)"
                + " VALUES (?,?, 'O32', 'pending')", first.getId(), first.getAdjustBatchNo());
        // 从服务入口转正，既有在途和后续审批继续使用原日志 ID
        service.editTempFundCodeToUpdated(operation(row.getId(), "FORMAL001"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log_fund WHERE fund_code='FORMAL001'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log_fund WHERE fund_code='TMP001'", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForMap("SELECT fund_name,fund_short_name,security_type,fund_score"
                + ",fund_investment_type,need_risk_leader_approval,adjust_batch_no FROM ip_adjust_log_fund WHERE id=?",
                first.getId())).containsEntry("fund_name", "正式基金全称")
                .containsEntry("fund_short_name", "正式基金").containsEntry("security_type", "etf_fund")
                .containsEntry("fund_score", new BigDecimal("61.2500"))
                .containsEntry("fund_investment_type", "bond_hybrid")
                .containsEntry("need_risk_leader_approval", 1)
                .containsEntry("adjust_batch_no", first.getAdjustBatchNo());
        assertThat(jdbc.queryForObject("SELECT adjust_log_id FROM ip_adjust_step_fund", Long.class)).isEqualTo(first.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_fund_code_update_log", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT audit_status FROM ip_adjust_log_fund WHERE id=?", String.class, modify.getId()))
                .isEqualTo("11");
        assertThat(mapper.queryTempFundCodeDetail(row.getId()).getFundCode()).isEqualTo("FORMAL001");
        assertThat(jdbc.queryForObject("SELECT security_status FROM rrs_fundinfo WHERE fund_code='TMP001'", String.class))
                .isEqualTo("D");
    }

    /** 在池转换生成直通日志，继承三参数、原意见和附件物理文件。 */
    @Test
    public void shouldConvertPoolStatusAndCopyAttachmentsWithoutNewPhysicalFiles() {
        // 准备临时代码
        TempFundCodeDto row = addTemporary("TMP001");
        // 准备完整的原入池记录和基金报告附件
        FundAdjustLogBo original = activePool("TMP001", 10L);
        // 附件关联写入原基金日志
        attach(original.getId(), "fund_report_hand");
        // 执行实际转正事务
        service.editTempFundCodeToUpdated(operation(row.getId(), "FORMAL001"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status_fund WHERE fund_code='TMP001' AND is_deleted=0",
                Integer.class)).isZero();
        assertThat(jdbc.queryForMap("SELECT fund_code,fund_score,fund_investment_type,need_risk_leader_approval"
                + ",adjust_reason,adjust_advice FROM ip_pool_status_fund WHERE is_deleted=0"))
                .containsEntry("fund_code", "FORMAL001")
                .containsEntry("fund_score", new BigDecimal("61.2500"))
                .containsEntry("fund_investment_type", "bond_hybrid")
                .containsEntry("need_risk_leader_approval", 1)
                .containsEntry("adjust_reason", "原调整原因").containsEntry("adjust_advice", "原调整意见");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log_fund", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log_fund WHERE audit_status='20' AND audit_time IS NOT NULL",
                Integer.class)).isEqualTo(3);
        Long target = jdbc.queryForObject("SELECT adjust_log_id FROM ip_pool_status_fund WHERE is_deleted=0", Long.class);
        assertThat(jdbc.queryForMap("SELECT attachment_category,file_name,original_file_name"
                + " FROM sys_attachment WHERE main_id=?", target))
                .containsEntry("attachment_category", "fund_report_hand")
                .containsEntry("file_name", "20261007/report.pdf")
                .containsEntry("original_file_name", "基金报告.pdf");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_step_fund", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_fund_code_update_log", Integer.class)).isEqualTo(1);
    }

    /** 正式已在同池时只调出临时成员，保留正式已有参数及状态。 */
    @Test
    public void shouldKeepExistingFormalPoolStateAndStillConvertConflictingPending() {
        // 准备临时登记及正式已有成员
        TempFundCodeDto row = addTemporary("TMP001");
        // 准备正式基金既有池状态
        FundAdjustLogBo formal = activePool("FORMAL001", 10L);
        jdbc.update("UPDATE ip_pool_status_fund SET fund_score=80,need_risk_leader_approval=0 WHERE adjust_log_id=?", formal.getId());
        // 准备同池临时成员
        activePool("TMP001", 10L);
        // 正式码同池的在途申请不提前阻止转正
        log("FORMAL001", "00", 10L);
        // 另一个来源的临时代码在途申请
        log("TMP001", "00", 10L);
        // 同池正式基金已有在途申请仍允许转正，冲突由后续审批校验
        service.editTempFundCodeToUpdated(operation(row.getId(), "FORMAL001"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log_fund WHERE audit_status='00'"
                + " AND fund_code='FORMAL001'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log_fund WHERE fund_code='FORMAL001' AND audit_status='20'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT fund_score,need_risk_leader_approval,adjust_log_id"
                + " FROM ip_pool_status_fund WHERE is_deleted=0"))
                .containsEntry("fund_score", new BigDecimal("80.0000"))
                .containsEntry("need_risk_leader_approval", 0).containsEntry("adjust_log_id", formal.getId());
    }

    /** 已在池和在途仍允许取消，登记和主档变更不推进原业务。 */
    @Test
    public void shouldCancelUsedCodeAndKeepBusinessRecords() {
        // 准备临时代码
        TempFundCodeDto row = addTemporary("TMP001");
        // 准备有效在池引用
        activePool("TMP001", 10L);
        // 准备有效在途引用
        log("TMP001", "00", 11L);
        // 保持债券取消口径，不提前处理已有引用
        service.editTempFundCodeToCancelled(operation(row.getId(), null));
        assertThat(mapper.queryTempFundCodeDetail(row.getId()).getStatus()).isEqualTo("cancelled");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log_fund WHERE fund_code='TMP001'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status_fund WHERE fund_code='TMP001' AND is_deleted=0",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT security_status,is_deleted FROM rrs_fundinfo WHERE fund_code='TMP001'"))
                .containsEntry("security_status", "D").containsEntry("is_deleted", 0);
        assertThat(mapper.queryTemporaryCodeCountByFundCode("TMP001")).isZero();
    }

    /** 终态日志也阻止删除；无引用时删除登记保留占位主档并禁止代码复用。 */
    @Test
    public void shouldGuardDeletionAndKeepPlaceholderAfterSuccessfulDeletion() {
        // 准备有终态引用的临时代码
        TempFundCodeDto used = addTemporary("USED");
        // 已撤回且未删除日志仍属于核心引用
        log("USED", "99", 10L);
        // 删除必须明确失败
        assertThatThrownBy(() -> service.deleteTempFundCode(operation(used.getId(), null)))
                .isInstanceOf(BizException.class).hasMessageContaining("被调库业务使用");
        // 准备无引用的临时代码
        TempFundCodeDto free = addTemporary("FREE");
        // 仅软删除登记
        service.deleteTempFundCode(operation(free.getId(), null));
        assertThat(jdbc.queryForMap("SELECT status,is_deleted FROM rrs_temp_fund_code WHERE id=?", free.getId()))
                .containsEntry("status", "deleted").containsEntry("is_deleted", 1);
        assertThat(jdbc.queryForMap("SELECT security_status,is_deleted FROM rrs_fundinfo WHERE fund_code='FREE'"))
                .containsEntry("security_status", "L").containsEntry("is_deleted", 0);
        // 主档仍占用代码，新登记不可覆盖
        assertThatThrownBy(() -> addTemporary("FREE")).isInstanceOf(BizException.class).hasMessageContaining("已存在于基金主档");
    }

    /** 转换中附件错误回滚已写日志、池状态、登记、替换日志和审计。 */
    @Test
    public void shouldRollBackWholeConversionWhenAttachmentAssociationIsInvalid() {
        // 准备临时代码
        TempFundCodeDto row = addTemporary("TMP001");
        // 准备在池业务
        FundAdjustLogBo original = activePool("TMP001", 10L);
        // 通过实际附件表模拟不符合基金分类的错误数据
        attach(original.getId(), "invalid_category");
        // 复制校验失败必须回滚前面所有数据库变更
        assertThatThrownBy(() -> service.editTempFundCodeToUpdated(operation(row.getId(), "FORMAL001")))
                .isInstanceOf(BizException.class).hasMessageContaining("来源附件");
        assertThat(mapper.queryTempFundCodeDetail(row.getId()).getStatus()).isEqualTo("temporary");
        assertThat(jdbc.queryForObject("SELECT security_status FROM rrs_fundinfo WHERE fund_code='TMP001'", String.class))
                .isEqualTo("L");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log_fund", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status_fund WHERE is_deleted=0", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_fund_code_update_log", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_fund_code_evt", Integer.class)).isEqualTo(1);
    }

    /** 两个临时基金并发转入同一正式基金和同一池时，当前读只保留一个正式成员。 */
    @Test
    public void shouldSerializeConcurrentConversionsToSameFormalFund() throws Exception {
        // 准备两个临时代码
        TempFundCodeDto first = addTemporary("TMP001");
        // 准备第二个登记
        TempFundCodeDto second = addTemporary("TMP002");
        // 第一条有效在池记录
        activePool("TMP001", 10L);
        // 第二条有效在池记录
        activePool("TMP002", 10L);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<TempFundCodeDto> one = executor.submit(() -> {
                start.await();
                // 共用正式主档锁，原在池状态按当前读处理
                return service.editTempFundCodeToUpdated(operation(first.getId(), "FORMAL001"));
            });
            Future<TempFundCodeDto> two = executor.submit(() -> {
                start.await();
                // 第二个事务等待主档锁后检查同池正式记录
                return service.editTempFundCodeToUpdated(operation(second.getId(), "FORMAL001"));
            });
            start.countDown();
            assertThat(one.get(15, TimeUnit.SECONDS).getStatus()).isEqualTo("updated");
            assertThat(two.get(15, TimeUnit.SECONDS).getStatus()).isEqualTo("updated");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_pool_status_fund WHERE fund_code='FORMAL001' AND is_deleted=0",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ip_adjust_log_fund", Integer.class)).isEqualTo(5);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    /** 新增在无唯一索引的结构下通过字典行锁防止并发同码登记。 */
    @Test
    public void shouldRejectConcurrentDuplicateCreation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> one = executor.submit(() -> {
                start.await();
                // 两个事务竞争相同基金字典锁
                return tryAdd("SAME");
            });
            Future<Boolean> two = executor.submit(() -> {
                start.await();
                // 后取得锁的事务必须看见已提交主档
                return tryAdd("SAME");
            });
            start.countDown();
            assertThat(Arrays.asList(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_fundinfo WHERE fund_code='SAME'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rrs_temp_fund_code WHERE temp_fund_code='SAME'", Integer.class))
                    .isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * 按生产 DDL 顺序提取删表和建表语句，仅在隔离内存库执行。
     *
     * @param file 仓库内 schema 路径
     */
    private void createSchema(String file) throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
        Pattern schema = Pattern.compile("DROP TABLE IF EXISTS\\s+`?\\w+`?\\s*;"
                + "|CREATE TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?\\w+`?\\s*\\(.*?\\)\\s*ENGINE\\s*=.*?;", Pattern.DOTALL);
        Matcher matcher = schema.matcher(sql);
        int count = 0;
        while (matcher.find()) {
            String statement = matcher.group();
            if (statement.startsWith("CREATE TABLE")) {
                statement = statement.replaceFirst("(?s)\\)\\s*ENGINE.*$", ")");
                count++;
            }
            jdbc.execute(statement);
        }
        assertThat(count).as("读取生产 schema %s 的 CREATE TABLE", file).isPositive();
    }

    /**
     * 通过实际服务新增临时代码。
     *
     * @param code 测试临时代码
     * @return 新增登记
     */
    private TempFundCodeDto addTemporary(String code) {
        TempFundCodeReq req = new TempFundCodeReq();
        req.setTempFundCode(code);
        req.setTempFundShortName("临时基金" + code);
        req.setTempMarketCode("OTC");
        req.setTempSecurityType("open_fund");
        req.setOperatorId("1");
        return service.addTempFundCode(req);
    }

    /**
     * 构造人工状态操作，正式信息不在请求中传入。
     *
     * @param id 登记主键
     * @param code 正式基金代码
     * @return 状态操作请求
     */
    private TempFundCodeReq operation(Long id, String code) {
        TempFundCodeReq req = new TempFundCodeReq();
        req.setId(id);
        req.setFundCode(code);
        req.setOperatorId("1");
        return req;
    }

    /**
     * 为并发新增返回明确的重复结果。
     *
     * @param code 临时基金代码
     * @return 新增是否成功
     */
    private boolean tryAdd(String code) {
        try {
            // 通过完整注解事务进行并发写入
            addTemporary(code);
            return true;
        } catch (BizException ex) {
            assertThat(ex.getMessage()).contains("已存在");
            return false;
        }
    }

    /**
     * 写入具有明确调库参数的原基金日志。
     *
     * @param code 基金代码
     * @param status 审核状态
     * @param poolId 目标池
     * @return 已回填主键的日志
     */
    private FundAdjustLogBo log(String code, String status, Long poolId) {
        FundAdjustLogBo log = new FundAdjustLogBo();
        log.setFundCode(code);
        log.setFundName("原基金全称");
        log.setFundShortName("原基金简称");
        log.setSecurityType("open_fund");
        log.setFundScore(new BigDecimal("61.25"));
        log.setFundInvestmentType("bond_hybrid");
        log.setNeedRiskLeaderApproval(1);
        log.setAdjustType("手工调整");
        log.setAdjustMode(AdjustMode.IN.getCode());
        log.setAdjustBatchNo("FUND" + UUID.randomUUID().toString().replace("-", ""));
        log.setTargetPoolId(poolId);
        log.setTargetPoolName("测试基金池");
        log.setPoolType("fund");
        log.setFlowId(9L);
        log.setFlowKey("original-flow");
        log.setFlowType("normalInbound");
        log.setAuditStatus(status);
        log.setAdjusterId("7");
        log.setAdjusterName("原发起人");
        log.setAdjustReason("原调整原因");
        log.setAdjustAdvice("原调整意见");
        transactions.execute(transaction -> fundMapper.addAdjustLog(log));
        return log;
    }

    /**
     * 创建实际审批通过日志和指向它的池状态。
     *
     * @param code 基金代码
     * @param poolId 目标池
     * @return 原入池日志
     */
    private FundAdjustLogBo activePool(String code, Long poolId) {
        // 写入真实审批通过入池日志
        FundAdjustLogBo log = log(code, "20", poolId);
        transactions.execute(transaction -> fundMapper.addFundPoolStatus(log));
        return log;
    }

    /**
     * 创建仅涉及关联的附件，无需生成物理文件。
     *
     * @param logId 原基金日志主键
     * @param category 附件分类或待验证的错误分类
     */
    private void attach(Long logId, String category) {
        SysAttachmentBo file = new SysAttachmentBo();
        file.setTableName("ip_adjust_log_fund");
        file.setMainId(logId);
        file.setAttachmentCategory(category);
        file.setFileType("pdf");
        file.setOriginalFileName("基金报告.pdf");
        file.setNewFileName("report.pdf");
        file.setFileSize(50L);
        file.setContentType("application/pdf");
        file.setFileName("20261007/report.pdf");
        file.setUploaderId("7");
        transactions.execute(transaction -> attachmentMapper.addAttachment(file));
    }
}
