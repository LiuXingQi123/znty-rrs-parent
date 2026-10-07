package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.znty.rrs.entity.bo.FundInfoBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportDto;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportReq;
import com.znty.rrs.mapper.FundPoolExcelImportMapper;
import com.znty.rrs.mapper.FundPoolAdjustMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/** 使用内存库和真实 XML 验证基金导入持久化，不连接本地业务库。 */
public class FundPoolExcelImportPersistenceTest {
    /** 内存测试库查询组件 */
    private JdbcTemplate jdbc;
    /** 实际导入 Mapper */
    private FundPoolExcelImportMapper mapper;
    /** 实际数据库事务模板 */
    private TransactionTemplate transactions;
    /** 注解事务代理使用的实际事务管理器 */
    private DataSourceTransactionManager transactionManager;

    /** 创建测试所需最小表并加载生产 Mapper。 */
    @Before
    public void setUp() throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 持久化测试需 Java 11 以上运行时", major >= 11);
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.h2.Driver");
        source.setUrl("jdbc:h2:mem:fund_import_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        jdbc = new JdbcTemplate(source);
        transactionManager = new DataSourceTransactionManager(source);
        transactions = new TransactionTemplate(transactionManager);
        jdbc.execute("CREATE TABLE sys_imp_tmp (id BIGINT AUTO_INCREMENT PRIMARY KEY"
                + ",imp_id VARCHAR(32),biz_type VARCHAR(32),template_code VARCHAR(64),file_name VARCHAR(256)"
                + ",file_size BIGINT,option_json VARCHAR(2000),total_count INT,pass_count INT,fail_count INT"
                + ",chk_rslt VARCHAR(1),chk_dscr VARCHAR(500),save_rslt VARCHAR(1),save_dscr VARCHAR(500)"
                + ",imp_time TIMESTAMP,opter_id VARCHAR(32),opter_name VARCHAR(64),result_json CLOB"
                + ",fld001 VARCHAR(200),fld002 VARCHAR(200),fld003 VARCHAR(200),fld011 CLOB,fld012 CLOB"
                + ",is_deleted INT,crte_time TIMESTAMP,updt_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_imp_tmp_detl (id BIGINT AUTO_INCREMENT PRIMARY KEY"
                + ",imp_detl_id VARCHAR(32),imp_id VARCHAR(32),row_no INT,chk_rslt VARCHAR(1)"
                + ",chk_dscr VARCHAR(500),save_rslt VARCHAR(1),save_dscr VARCHAR(500),imp_time TIMESTAMP"
                + ",opter_id VARCHAR(32),fld001 VARCHAR(200),fld002 VARCHAR(200),fld003 VARCHAR(200)"
                + ",fld004 VARCHAR(200),fld005 VARCHAR(200),fld006 VARCHAR(200),fld007 VARCHAR(200)"
                + ",fld009 VARCHAR(200),fld010 VARCHAR(200),is_deleted INT,crte_time TIMESTAMP,updt_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE ip_investment_pool (id BIGINT PRIMARY KEY,parent_id BIGINT"
                + ",pool_name VARCHAR(200),variety_codes VARCHAR(200),status VARCHAR(40),is_deleted INT)");
        jdbc.execute("CREATE TABLE ip_pool_status_fund (id BIGINT PRIMARY KEY,fund_code VARCHAR(40)"
                + ",target_pool_id BIGINT,audit_status VARCHAR(2),is_deleted INT)");
        jdbc.execute("CREATE TABLE rrs_fundinfo (fund_code VARCHAR(40),fund_name VARCHAR(200)"
                + ",fund_short_name VARCHAR(200),security_type VARCHAR(40),is_deleted INT)");
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addInterceptor(new PageInterceptor());
        configuration.setEnvironment(new Environment("test", new SpringManagedTransactionFactory(), source));
        try (InputStream xml = getClass().getResourceAsStream("/mapper/FundPoolExcelImportMapper.xml")) {
            new XMLMapperBuilder(xml, configuration, "mapper/FundPoolExcelImportMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        mapper = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration))
                .getMapper(FundPoolExcelImportMapper.class);
    }

    /** 释放本用例的内存数据库。 */
    @After
    public void tearDown() {
        PageHelper.clearPage();
        if (jdbc != null) {
            jdbc.execute("SET DB_CLOSE_DELAY 0");
        }
    }

    /** 真正上传七列文件并通过实际 Mapper 验证长度和长原因意见保存。 */
    @Test
    public void shouldUploadThroughActualServiceAndKeepLongReasonAdvice() throws Exception {
        // 创建带有注解事务的实际导入服务
        FundPoolExcelImportService service = importService(mapper);
        FundPoolExcelImportReq req = new FundPoolExcelImportReq();
        req.setDirection("in");
        req.setAllowLinkMutex(true);
        req.setCurrentUserId("1");
        req.setCurrentUserName("测试用户");
        String longText = String.join("", Collections.nCopies(900, "理"));
        req.setAdjustReason(longText);
        req.setAdjustAdvice(longText);
        // 上传真实 Excel 文件以覆盖解析、主表和明细表写入
        FundPoolExcelImportDto dto = service.uploadExcel(req, excel(), null);
        assertThat(dto.getImpId()).hasSize(32);
        assertThat(dto.getReason()).isEqualTo(longText);
        assertThat(dto.getAdvice()).isEqualTo(longText);
        assertThat(mapper.queryBatchItemList(dto.getImpId()).get(0).getImpDetlId()).hasSize(32);
        assertThat(dto.getTotalCount()).isEqualTo(1);
        assertThat(dto.getPendingCount()).isEqualTo(1);
        assertThat(dto.getCheckDone()).isFalse();
        assertThat(dto.getItems().getRecords().get(0).getNeedRiskLeaderApprovalRaw()).isEqualTo("0");
    }

    /** 实际上传注解事务遇到明细写入异常时，已写的批次也必须回滚。 */
    @Test
    public void shouldRollBackActualUploadWhenDetailWriteFails() throws Exception {
        FundPoolExcelImportMapper failing = mock(FundPoolExcelImportMapper.class, delegatesTo(mapper));
        doThrow(new IllegalStateException("模拟明细写入失败")).when(failing).addItemList(anyList());
        // 将故障 Mapper 注入真实服务并使用实际 Spring 事务代理
        FundPoolExcelImportService service = importService(failing);
        FundPoolExcelImportReq req = new FundPoolExcelImportReq();
        req.setDirection("in");
        req.setCurrentUserId("1");
        // 构造实际七列上传文件
        MockMultipartFile file = excel();
        assertThatThrownBy(() -> service.uploadExcel(req, file, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("模拟明细写入失败");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_imp_tmp", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_imp_tmp_detl", Integer.class)).isZero();
    }

    /** 原始非法字段可回读，业务校验更新不得覆盖三列原始值。 */
    @Test
    public void shouldPreserveRawColumnsAndPhysicalRowNumber() {
        // 新建批次和包含非法基金调库字段的来源行
        SysImpTmpBo batch = batch("FUND-RAW", "fund_pool_excel");
        mapper.addBatch(batch);
        // 构造需要原样保存的来源明细
        SysImpTmpDetlBo item = item(batch.getImpId());
        mapper.addItemList(Collections.singletonList(item));
        SysImpTmpDetlBo saved = mapper.queryBatchItemList(batch.getImpId()).get(0);
        assertThat(saved.getRowNo()).isEqualTo(7);
        assertThat(saved.getFld001()).isEqualTo("000001.OF");
        assertThat(saved.getFld005()).isEqualTo("非法评分");
        assertThat(saved.getFld006()).isEqualTo("unknown_type");
        assertThat(saved.getFld007()).isEqualTo("0");
        saved.setChkRslt("2");
        saved.setChkDscr("第7行：基金评分必须为合法数字");
        saved.setFld009("145");
        saved.setFld010("fund");
        saved.setFld002("主档基金名称");
        mapper.editItemCheckResult(saved);
        SysImpTmpDetlBo checked = mapper.queryItemPage(batch.getImpId(), "2", "000001").get(0);
        assertThat(checked.getFld005()).isEqualTo("非法评分");
        assertThat(checked.getFld006()).isEqualTo("unknown_type");
        assertThat(checked.getFld007()).isEqualTo("0");
        assertThat(checked.getFld009()).isEqualTo("145");
        assertThat(checked.getFld002()).isEqualTo("主档基金名称");
        assertThat(mapper.queryItemPage(batch.getImpId(), "1", null)).isEmpty();
    }

    /** 基金批次查询与行锁不得读取其他业务的临时批次。 */
    @Test
    public void shouldIsolateOtherImportBusinessTypes() {
        // 新建证券导入批次及其明细，验证基金 Mapper 不可见
        SysImpTmpBo other = batch("SECURITY-ONLY", "security_pool_excel");
        mapper.addBatch(other);
        // 构造另一业务的来源明细
        mapper.addItemList(Collections.singletonList(item(other.getImpId())));
        assertThat(mapper.queryBatchByImpId(other.getImpId())).isNull();
        assertThat(mapper.queryBatchIdForUpdate(other.getImpId())).isNull();
        assertThat(mapper.queryBatchItemList(other.getImpId())).isEmpty();
        assertThat(mapper.queryItemPage(other.getImpId(), null, null)).isEmpty();
        assertThat(mapper.deleteBatchSoft(other.getImpId())).isZero();
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM sys_imp_tmp WHERE imp_id = ?",
                Integer.class, other.getImpId())).isZero();
    }

    /** 提交状态保存后无法取消，未提交批次及明细可逻辑删除。 */
    @Test
    public void shouldCancelOnlyUnsubmittedBatch() {
        // 新建并取消未提交批次
        SysImpTmpBo cancelled = batch("CANCELLED", "fund_pool_excel");
        mapper.addBatch(cancelled);
        // 构造待取消的来源明细
        mapper.addItemList(Collections.singletonList(item(cancelled.getImpId())));
        assertThat(mapper.deleteBatchSoft(cancelled.getImpId())).isEqualTo(1);
        assertThat(mapper.deleteItemsByImpIdSoft(cancelled.getImpId())).isEqualTo(1);
        assertThat(mapper.queryBatchByImpId(cancelled.getImpId())).isNull();
        assertThat(mapper.queryBatchItemList(cancelled.getImpId())).isEmpty();
        // 新建已提交批次，验证取消条件保护
        SysImpTmpBo submitted = batch("SUBMITTED", "fund_pool_excel");
        mapper.addBatch(submitted);
        submitted.setSaveRslt("1");
        submitted.setSaveDscr("提交成功");
        submitted.setResultJson("{\"submitted\":true}");
        mapper.editBatchSaveResult(submitted);
        assertThat(mapper.deleteBatchSoft(submitted.getImpId())).isZero();
        assertThat(mapper.queryBatchByImpId(submitted.getImpId()).getSaveRslt()).isEqualTo("1");
    }

    /** 批次和明细同一事务中后续写入失败时，先前写入也必须撤销。 */
    @Test
    public void shouldRollBackBatchAndDetailWritesTogether() {
        assertThatThrownBy(() -> transactions.execute(status -> {
            // 新建批次后模拟后续明细持久化失败
            SysImpTmpBo batch = batch("ROLLBACK", "fund_pool_excel");
            mapper.addBatch(batch);
            // 插入来源行，随后触发数据库主键冲突
            mapper.addItemList(Collections.singletonList(item(batch.getImpId())));
            jdbc.update("INSERT INTO sys_imp_tmp(id,imp_id) VALUES (?,?)", batch.getId(), "DUPLICATE");
            return null;
        })).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_imp_tmp", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_imp_tmp_detl", Integer.class)).isZero();
    }

    /** 解析只返回启用基金叶子池，并保留同名多匹配供服务明确拒绝。 */
    @Test
    public void shouldResolveOnlyEnabledFundLeavesAndPreserveAmbiguity() {
        jdbc.update("INSERT INTO ip_investment_pool VALUES"
                + " (1,NULL,'基金池','[]','enabled',0)"
                + ",(2,1,'基础池','[\"fund\"]','enabled',0)"
                + ",(3,1,'基础池','[\"bond\"]','enabled',0)"
                + ",(4,1,'基础池','[\"fund\"]','disabled',0)"
                + ",(5,1,'基础池','[\"fund\"]','enabled',1)");
        assertThat(mapper.queryEnabledLeafPoolList("基金池", "基础池"))
                .extracting(InvestmentPoolBo::getId).containsExactly(2L);
        jdbc.update("INSERT INTO ip_investment_pool VALUES (6,1,'基础池','[\"fund\"]','enabled',0)");
        assertThat(mapper.queryEnabledLeafPoolList("基金池", "基础池")).hasSize(2);
        jdbc.update("INSERT INTO ip_investment_pool VALUES (7,2,'下级池','[\"fund\"]','enabled',0)");
        assertThat(mapper.queryEnabledLeafPoolList("基金池", "基础池"))
                .extracting(InvestmentPoolBo::getId).containsExactly(6L);
    }

    /** 清空成员只来自已审批通过的有效基金状态，主档缺失仍保留代码供校验报错。 */
    @Test
    public void shouldReadOnlyEffectiveFundMembersWithoutHidingMissingMaster() {
        jdbc.update("INSERT INTO rrs_fundinfo VALUES ('F001','基金全称','基金简称','fund',0)");
        jdbc.update("INSERT INTO ip_pool_status_fund VALUES"
                + " (1,'F001',145,'20',0),(2,'F002',145,'00',0)"
                + ",(3,'F003',145,'20',1),(4,'F004',146,'20',0),(5,'MISSING',145,'20',0)");
        assertThat(mapper.queryPoolMemberList(145L)).extracting(FundInfoBo::getFundCode)
                .containsExactly("F001", "MISSING");
        assertThat(mapper.queryPoolMemberList(145L).get(1).getFundName()).isNull();
    }

    /**
     * 构造最小待校验批次。
     *
     * @param impId 导入批次号
     * @param bizType 用于验证业务隔离的导入类型
     */
    private SysImpTmpBo batch(String impId, String bizType) {
        SysImpTmpBo batch = new SysImpTmpBo();
        batch.setImpId(impId);
        batch.setBizType(bizType);
        batch.setTemplateCode("fund_pool_import");
        batch.setFileName("基金模板.xlsx");
        batch.setFileSize(100L);
        batch.setOptionJson("{\"allowLinkMutex\":true}");
        batch.setTotalCount(1);
        batch.setPassCount(0);
        batch.setFailCount(0);
        batch.setChkRslt("0");
        batch.setSaveRslt("0");
        batch.setOpterId("1");
        batch.setOpterName("测试用户");
        batch.setFld001("in");
        batch.setIsDeleted(0);
        return batch;
    }

    /**
     * 构造包含原始非法输入的第七行。
     *
     * @param impId 来源明细所属的导入批次号
     */
    private SysImpTmpDetlBo item(String impId) {
        SysImpTmpDetlBo item = new SysImpTmpDetlBo();
        item.setImpId(impId);
        item.setImpDetlId(UUID.randomUUID().toString().replace("-", ""));
        item.setRowNo(7);
        item.setChkRslt("0");
        item.setSaveRslt("0");
        item.setFld001("000001.OF");
        item.setFld002("Excel基金名称");
        item.setFld003("基金池");
        item.setFld004("基础池");
        item.setFld005("非法评分");
        item.setFld006("unknown_type");
        item.setFld007("0");
        item.setIsDeleted(0);
        return item;
    }

    /**
     * 创建绑定真实事务和文件名处理组件的导入服务。
     *
     * @param importMapper 实际导入 Mapper 或注入故障的 Mapper 替身
     */
    private FundPoolExcelImportService importService(FundPoolExcelImportMapper importMapper) {
        FundPoolExcelImportService target = new FundPoolExcelImportService();
        ReflectionTestUtils.setField(target, "fundPoolExcelImportMapper", importMapper);
        ReflectionTestUtils.setField(target, "fundPoolAdjustMapper", mock(FundPoolAdjustMapper.class));
        ReflectionTestUtils.setField(target, "sysAttachmentService", new SysAttachmentService());
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (FundPoolExcelImportService) proxy.getProxy();
    }

    /** 构造文本基金代码及有效三字段的七列模板内容。 */
    private MockMultipartFile excel() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("基金池导入");
            Row header = sheet.createRow(0);
            String[] columns = {"父池名称", "子池名称", "基金名称", "基金代码", "基金评分", "基金投资类型", "风管领导审批"};
            String[] values = {"基金池", "基础池", "测试基金", "000001.OF", "5.125", "stock", "0"};
            Row row = sheet.createRow(1);
            for (int index = 0; index < columns.length; index++) {
                header.createCell(index).setCellValue(columns[index]);
                row.createCell(index).setCellValue(values[index]);
            }
            workbook.write(output);
            return new MockMultipartFile("file", "基金导入.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }
}
