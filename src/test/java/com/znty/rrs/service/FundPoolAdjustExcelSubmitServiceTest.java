package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.github.pagehelper.PageHelper;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.FundAdjustLogBo;
import com.znty.rrs.entity.bo.FundAdjustStepBo;
import com.znty.rrs.entity.bo.FundInfoBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.fundpooladjust.FundAdjustCheckDto;
import com.znty.rrs.entity.fundpooladjust.FundAdjustCheckReq;
import com.znty.rrs.entity.fundpooladjust.FundAdjustSubmitDto;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustSubmitReq;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportDto;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.FundPoolAdjustMapper;
import com.znty.rrs.mapper.FundPoolExcelImportMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.TempFundCodeMapper;
import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.After;
import org.junit.Before;
import org.junit.Assume;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 基金 Excel 导入复核、来源分组提交与事务回滚测试。 */
public class FundPoolAdjustExcelSubmitServiceTest {
    /** 待测试基金调库服务 */
    private FundPoolAdjustService service;
    /** 基金调库数据访问组件 */
    private FundPoolAdjustMapper mapper;
    /** 投资池数据访问组件 */
    private InvestmentPoolMapper poolMapper;
    /** 一般审批流程数据访问组件 */
    private FlowMapper flowMapper;
    /** 附件服务 */
    private SysAttachmentService attachmentService;
    /** 有效基金临时代码登记数据访问组件 */
    private TempFundCodeMapper tempFundCodeMapper;
    /** 基金主档 */
    private FundInfoBo fund;
    /** 无报告限制的第一个投资池 */
    private InvestmentPoolBo firstPool;
    /** 无报告限制的第二个投资池 */
    private InvestmentPoolBo secondPool;

    /** 初始化真实一般流程、基金主档与两个可调入投资池。 */
    @Before
    public void setUp() {
        service = new FundPoolAdjustService();
        mapper = mock(FundPoolAdjustMapper.class);
        poolMapper = mock(InvestmentPoolMapper.class);
        flowMapper = mock(FlowMapper.class);
        attachmentService = mock(SysAttachmentService.class);
        tempFundCodeMapper = mock(TempFundCodeMapper.class);
        ReflectionTestUtils.setField(service, "fundPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(service, "flowMapper", flowMapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachmentService);
        ReflectionTestUtils.setField(service, "tempFundCodeMapper", tempFundCodeMapper);
        fund = new FundInfoBo();
        fund.setId(1L);
        fund.setFundCode("FUND001.SH");
        fund.setFundName("主档基金全称");
        fund.setFundShortName("主档基金简称");
        fund.setSecurityType("bond_fund");
        fund.setSecurityStatus("A");
        fund.setMarketCode("SH");
        // 构造目标池及其一般审批流程配置
        firstPool = buildPool(10L, "第一基金池");
        // 构造同一基金另一来源组使用的目标池
        secondPool = buildPool(20L, "第二基金池");
        when(mapper.queryFundByCode("FUND001.SH")).thenReturn(fund);
        when(mapper.queryFundListForUpdate(anyList())).thenReturn(Collections.singletonList(fund));
        when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(firstPool, secondPool));
        when(mapper.queryFundCurrentPoolIdList("FUND001.SH")).thenReturn(Collections.emptyList());
        when(mapper.queryAllPoolRelationList()).thenReturn(Collections.emptyList());
        AtomicLong logSequence = new AtomicLong();
        when(mapper.addAdjustLog(any())).thenAnswer(invocation -> {
            FundAdjustLogBo log = invocation.getArgument(0);
            log.setId(logSequence.incrementAndGet());
            return 1;
        });
        // 配置已发布、具有开始节点及人工审批节点的一般流程
        configureNormalFlow();
    }

    /** 释放导入分页线程上下文，避免影响同线程后续用例。 */
    @After
    public void tearDown() {
        PageHelper.clearPage();
    }

    /** 串联两个真实服务，API 调入值须转换为基金内部枚举值。 */
    @Test
    public void actualImportAndFundServicesShouldSubmitInboundApproval() throws Exception {
        // 使用真实上传、校验及整批基金提交链路验证方向和状态
        assertActualImportSubmission("in", AdjustMode.IN.getCode());
    }

    /** 调出走同一真实链路，不能被 in/out 与中文内部编码差异阻断。 */
    @Test
    public void actualImportAndFundServicesShouldSubmitOutboundApproval() throws Exception {
        when(mapper.queryFundCurrentPoolIdList("FUND001.SH")).thenReturn(Collections.singletonList(10L));
        // 验证真实调出校验返回 API 编码，写入基金日志使用内部编码
        assertActualImportSubmission("out", AdjustMode.OUT.getCode());
    }

    /**
     * 构造仅替代持久化的导入环境，基金规则与提交服务均使用真实实现。
     *
     * @param direction 导入 API 使用的 in 或 out 方向
     * @param internalMode 预期写入基金日志的内部调整方向编码
     */
    private void assertActualImportSubmission(String direction, String internalMode) throws Exception {
        FundPoolExcelImportMapper importMapper = mock(FundPoolExcelImportMapper.class);
        AtomicReference<SysImpTmpBo> batch = new AtomicReference<>();
        List<SysImpTmpDetlBo> rows = new ArrayList<>();
        when(importMapper.addBatch(any())).thenAnswer(invocation -> {
            batch.set(invocation.getArgument(0));
            return 1;
        });
        when(importMapper.addItemList(anyList())).thenAnswer(invocation -> {
            List<SysImpTmpDetlBo> added = invocation.getArgument(0);
            for (SysImpTmpDetlBo row : added) {
                row.setId((long) rows.size() + 1);
                rows.add(row);
            }
            return added.size();
        });
        when(importMapper.queryBatchByImpId(anyString())).thenAnswer(invocation -> batch.get());
        when(importMapper.queryBatchIdForUpdate(anyString())).thenReturn(1L);
        when(importMapper.queryBatchItemList(anyString())).thenReturn(rows);
        when(importMapper.queryItemPage(anyString(), any(), any())).thenReturn(rows);
        when(importMapper.queryEnabledLeafPoolList("基金库", "第一基金池"))
                .thenReturn(Collections.singletonList(firstPool));
        FundPoolExcelImportService importService = new FundPoolExcelImportService();
        ReflectionTestUtils.setField(importService, "fundPoolExcelImportMapper", importMapper);
        ReflectionTestUtils.setField(importService, "fundPoolAdjustService", service);
        ReflectionTestUtils.setField(importService, "fundPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(importService, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(importService, "sysAttachmentService", new SysAttachmentService());
        FundPoolExcelImportReq req = new FundPoolExcelImportReq();
        req.setDirection(direction);
        req.setCurrentUserId("1");
        req.setCurrentUserName("测试用户");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet();
            String[] headers = {"父池名称", "子池名称", "基金名称", "基金代码", "基金评分", "基金投资类型", "风管领导审批"};
            String[] values = {"基金库", "第一基金池", "上传名称", "FUND001.SH", "0", "stock", "0"};
            workbook.getSheetAt(0).createRow(0);
            workbook.getSheetAt(0).createRow(1);
            for (int column = 0; column < headers.length; column++) {
                workbook.getSheetAt(0).getRow(0).createCell(column).setCellValue(headers[column]);
                workbook.getSheetAt(0).getRow(1).createCell(column).setCellValue(values[column]);
            }
            workbook.write(output);
            // 真实解析七列文件并锁定批次参数
            importService.uploadExcel(req, new MockMultipartFile("file", "基金.xlsx",
                    "application/octet-stream", output.toByteArray()), null);
        }
        FundPoolExcelImportDto checked = importService.checkImport(req);
        assertThat(checked.getCheckItems()).hasSize(1);
        assertThat(checked.getCheckItems().get(0).getFailReasons()).isEmpty();
        assertThat(checked.getCheckItems().get(0).getAdjustDirection()).isEqualTo(direction);
        FundPoolExcelImportDto submitted = importService.submitImport(req);
        assertThat(submitted.getSaveRslt()).isEqualTo("1");
        ArgumentCaptor<FundAdjustLogBo> log = ArgumentCaptor.forClass(FundAdjustLogBo.class);
        verify(mapper).addAdjustLog(log.capture());
        assertThat(log.getValue().getAdjustMode()).isEqualTo(internalMode);
        assertThat(log.getValue().getAuditStatus()).isEqualTo("00");
        assertThat(log.getValue().getFundScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(log.getValue().getNeedRiskLeaderApproval()).isZero();
        verify(mapper, never()).addFundPoolStatus(any());
        verify(mapper, never()).deleteFundPoolStatus(any(), any());
    }

    /** 无报告限制时通过完整基金校验，并仅返回目标池的一般流程。 */
    @Test
    public void excelCheckShouldUseMasterDataAndNormalFlow() {
        // 构造含伪造池名称的 Excel 主项校验请求
        FundAdjustCheckReq req = buildCheckRequest(10L, AdjustMode.IN.getCode());
        req.getItems().get(0).setTargetPoolName("伪造池名称");

        FundAdjustCheckDto.CheckResultItem item = service.checkExcelImportAdjust(req, "1").getItems().get(0);

        assertThat(item.isCanAdjust()).isTrue();
        assertThat(item.getFundCode()).isEqualTo(fund.getFundCode());
        assertThat(item.getFundShortName()).isEqualTo("主档基金简称");
        assertThat(item.getSecurityType()).isEqualTo("bond_fund");
        assertThat(item.getPoolName()).isEqualTo("第一基金池");
        assertThat(item.getFlowOptions()).hasSize(1);
        assertThat(item.getFlowOptions().get(0).getFlowType()).isEqualTo("normalInbound");
        assertThat(item.getFlowOptions().get(0).isSelectable()).isTrue();
        verify(mapper, never()).addAdjustLog(any());
    }

    /** any 与 internal 报告限制均在 Excel 主项校验阶段明确阻断。 */
    @Test
    public void excelCheckShouldRejectAnyAndInternalReports() {
        for (String restriction : Arrays.asList("any", "internal")) {
            firstPool.setInReportRestriction(restriction);
            // 构造无报告附件的调入校验请求
            FundAdjustCheckReq req = buildCheckRequest(10L, AdjustMode.IN.getCode());

            FundAdjustCheckDto.CheckResultItem item = service.checkExcelImportAdjust(req, "1").getItems().get(0);

            assertThat(item.isCanAdjust()).isFalse();
            assertThat(item.getFailReasons()).anyMatch(reason -> reason.contains("要求提交基金报告")
                    && reason.contains("Excel 导入暂不支持报告附件") && reason.contains("单笔调库"));
            assertThat(item.getFlowOptions().get(0).isSelectable()).isFalse();
        }
    }

    /** 调出报告限制与调入保持一致，清空主项也不能绕过报告要求。 */
    @Test
    public void excelCheckShouldApplyOutboundReportRestriction() {
        firstPool.setOutReportRestriction("internal");
        when(mapper.queryFundCurrentPoolIdList("FUND001.SH")).thenReturn(Collections.singletonList(10L));
        // 构造清空组使用的调出主项校验请求
        FundAdjustCheckReq req = buildCheckRequest(10L, AdjustMode.OUT.getCode());

        FundAdjustCheckDto.CheckResultItem item = service.checkExcelImportAdjust(req, "1").getItems().get(0);

        assertThat(item.isCanAdjust()).isFalse();
        assertThat(item.getFailReasons()).anyMatch(reason -> reason.contains("Excel 导入暂不支持报告附件"));
    }

    /** 关系项的报告口径与单笔申请一致，仍独立检查关系池调整权限。 */
    @Test
    public void excelCheckShouldKeepRelationReportRuleAndCheckEveryPoolPermission() {
        secondPool.setInReportRestriction("internal");
        PoolRelationBo relation = new PoolRelationBo();
        relation.setPoolId(10L);
        relation.setRelationPoolId(20L);
        relation.setRelationType("in_linked");
        when(mapper.queryAllPoolRelationList()).thenReturn(Collections.singletonList(relation));
        PoolPermissionBo permission = new PoolPermissionBo();
        permission.setPoolId(10L);
        permission.setHandlerId(2L);
        permission.setHandlerType("user");
        when(poolMapper.queryPermissionListByType("adjustable")).thenReturn(Collections.singletonList(permission));
        // 构造主池可调整而关系池无权限的校验请求
        FundAdjustCheckReq req = buildCheckRequest(10L, AdjustMode.IN.getCode());

        List<FundAdjustCheckDto.CheckResultItem> items = service.checkExcelImportAdjust(req, "2").getItems();

        assertThat(items).hasSize(2);
        assertThat(items.get(0).isCanAdjust()).isTrue();
        assertThat(items.get(1).getItemTag()).isEqualTo("linkage");
        assertThat(items.get(1).isCanAdjust()).isFalse();
        assertThat(items.get(1).getFailReasons()).containsExactly("当前用户没有投资池调整权限：第二基金池");
    }

    /** 同一基金在不同池的来源组分别保留评分、投资类型和领导审批字段。 */
    @Test
    public void excelSubmitShouldPreserveIndependentSourceFieldsAndLeavePoolStatusUnchanged() {
        // 构造同一基金在不同池的独立来源组
        FundPoolAdjustSubmitReq first = buildSubmitRequest(10L, "excel-row-2");
        // 构造需要独立保留三字段的第二个来源组
        FundPoolAdjustSubmitReq second = buildSubmitRequest(20L, "excel-row-3");
        first.setFundScore(new BigDecimal("-12.5"));
        first.setFundInvestmentType("stock");
        first.setNeedRiskLeaderApproval(0);
        second.setFundScore(new BigDecimal("999999.99"));
        second.setFundInvestmentType("bond_hybrid");
        second.setNeedRiskLeaderApproval(1);

        List<FundAdjustSubmitDto> results = service.addExcelImportAdjustLogList(Arrays.asList(first, second));

        ArgumentCaptor<FundAdjustLogBo> logs = ArgumentCaptor.forClass(FundAdjustLogBo.class);
        verify(mapper, times(2)).addAdjustLog(logs.capture());
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getFundScore)
                .containsExactly(new BigDecimal("-12.5"), new BigDecimal("999999.99"));
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getFundInvestmentType)
                .containsExactly("stock", "bond_hybrid");
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getNeedRiskLeaderApproval).containsExactly(0, 1);
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getAuditStatus).containsOnly("00");
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getFundName).containsOnly("主档基金全称");
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getSecurityType).containsOnly("bond_fund");
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getFlowType).containsOnly("normalInbound");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getAdjustBatchNos().get(0)).startsWith("FUND-")
                .isNotEqualTo(results.get(1).getAdjustBatchNos().get(0));
        verify(mapper, times(4)).addAdjustStep(any());
        verify(mapper, never()).addFundPoolStatus(any());
        verify(mapper, never()).deleteFundPoolStatus(any(), any());
    }

    /** 所有来源组的待办与重复申请检查均发生于首次写入之前。 */
    @Test
    public void excelSubmitShouldFinishAllSourceChecksBeforeWriting() {
        // 构造两个独立来源组以验证前置复核顺序
        FundPoolAdjustSubmitReq first = buildSubmitRequest(10L, "excel-row-2");
        // 构造必须在首次写入前完成复核的后续来源组
        FundPoolAdjustSubmitReq second = buildSubmitRequest(20L, "excel-row-3");

        service.addExcelImportAdjustLogList(Arrays.asList(first, second));

        InOrder order = inOrder(mapper);
        order.verify(mapper).queryRecentDuplicate("FUND001.SH", 10L, AdjustMode.IN.getCode(), "1");
        order.verify(mapper).queryFundHasPendingProcess("FUND001.SH", 20L, null);
        order.verify(mapper).queryRecentDuplicate("FUND001.SH", 20L, AdjustMode.IN.getCode(), "1");
        order.verify(mapper).addAdjustLog(argThat(log -> Long.valueOf(10L).equals(log.getTargetPoolId())));
    }

    /** 同组关系项复制三字段并复用主流程，只有主项创建审批步骤。 */
    @Test
    public void excelSubmitShouldCopySourceFieldsToRelationItems() {
        secondPool.setInReportRestriction("internal");
        // 构造一个主项与同组关系项的来源组
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "excel-row-2");
        req.setFundScore(new BigDecimal("7.25"));
        req.setFundInvestmentType("money_market");
        req.setNeedRiskLeaderApproval(1);
        FundPoolAdjustSubmitReq.AdjustItem relation = new FundPoolAdjustSubmitReq.AdjustItem();
        relation.setTargetPoolId(20L);
        relation.setAdjustMode(AdjustMode.IN.getCode());
        relation.setItemTag("linkage");
        relation.setAdjustGroupKey("excel-row-2");
        req.setItems(Arrays.asList(req.getItems().get(0), relation));

        FundAdjustSubmitDto result = service.addExcelImportAdjustLogList(Collections.singletonList(req)).get(0);

        ArgumentCaptor<FundAdjustLogBo> logs = ArgumentCaptor.forClass(FundAdjustLogBo.class);
        verify(mapper, times(2)).addAdjustLog(logs.capture());
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getFundScore)
                .containsOnly(new BigDecimal("7.25"));
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getFundInvestmentType).containsOnly("money_market");
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getNeedRiskLeaderApproval).containsOnly(1);
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getFlowId).containsOnly(100L);
        assertThat(result.getAdjustBatchNos()).hasSize(1);
        verify(mapper, times(2)).addAdjustStep(any());
    }

    /** 批量或快速流程伪造为选择项时仍由一般流程复核拒绝。 */
    @Test
    public void excelSubmitShouldRejectBatchFlowSelection() {
        // 构造伪造为批量调入流程的主项
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "excel-row-2");
        req.getItems().get(0).setFlowType("batchInbound");

        assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Collections.singletonList(req)))
                .isInstanceOf(BizException.class).hasMessageContaining("只能选择目标投资池配置的一般审批流程");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 后续来源组的报告校验失败时，前面的合格分组也没有写入。 */
    @Test
    public void excelSubmitShouldWriteNothingWhenLaterSourceFails() {
        secondPool.setInReportRestriction("any");
        // 构造后续组缺少必填报告的整批申请
        FundPoolAdjustSubmitReq first = buildSubmitRequest(10L, "excel-row-2");
        // 构造目标池需要报告但没有附件的后续来源组
        FundPoolAdjustSubmitReq second = buildSubmitRequest(20L, "excel-row-3");

        assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Arrays.asList(first, second)))
                .isInstanceOf(BizException.class).hasMessageContaining("要求提交基金报告");

        verify(mapper, never()).addAdjustLog(any());
        verify(mapper, never()).addAdjustStep(any());
    }

    /** 跨来源组的同基金同池，无论方向，均给出可定位冲突。 */
    @Test
    public void excelSubmitShouldRejectCrossSourceDuplicatePoolInEitherDirection() {
        for (String direction : Arrays.asList(AdjustMode.IN.getCode(), AdjustMode.OUT.getCode())) {
            // 构造相同基金目标池、不同来源组的重复调整
            FundPoolAdjustSubmitReq first = buildSubmitRequest(10L, "excel-row-2");
            // 构造同目标池的另一来源组以验证任意方向冲突
            FundPoolAdjustSubmitReq second = buildSubmitRequest(10L, "excel-row-8");
            second.getItems().get(0).setAdjustMode(direction);

            assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Arrays.asList(first, second)))
                    .isInstanceOf(BizException.class).hasMessageContaining("来源分组冲突")
                    .hasMessageContaining("FUND001.SH").hasMessageContaining("目标池 ID 10")
                    .hasMessageContaining("excel-row-2").hasMessageContaining("excel-row-8");
        }
        verify(mapper, never()).addAdjustLog(any());
    }

    /** Excel 内部入口禁止伪造报告来源或上传索引绕过无附件约束。 */
    @Test
    public void excelSubmitShouldRejectAttachmentSpoofing() {
        // 构造伪造报告附件来源的内部请求
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "excel-row-2");
        req.getItems().get(0).setReportSourceAttachmentIds(Collections.singletonList(99L));

        assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Collections.singletonList(req)))
                .isInstanceOf(BizException.class).hasMessageContaining("Excel 导入暂不支持报告或材料附件");

        req.getItems().get(0).setReportSourceAttachmentIds(Collections.emptyList());
        req.getItems().get(0).setReportFileIndexes(Collections.singletonList(0));
        assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Collections.singletonList(req)))
                .isInstanceOf(BizException.class).hasMessageContaining("Excel 导入暂不支持报告或材料附件");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 提交时再次检查调整权限，不信任之前可调整的校验结果。 */
    @Test
    public void excelSubmitShouldRecheckAdjustmentPermission() {
        // 构造无调整权限用户的提交请求
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "excel-row-2");
        req.setAdjusterId("2");

        assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Collections.singletonList(req)))
                .isInstanceOf(BizException.class).hasMessageContaining("没有投资池调整权限");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 锁查询返回已终止主档时，不能使用普通查询中的旧存续状态写申请。 */
    @Test
    public void submitShouldRejectDisabledLockedMasterBeforeAnyWrites() {
        FundInfoBo disabled = new FundInfoBo();
        disabled.setId(1L);
        disabled.setFundCode(fund.getFundCode());
        disabled.setSecurityStatus("D");
        when(mapper.queryFundListForUpdate(anyList())).thenReturn(Collections.singletonList(disabled));
        // 构造仍持有旧校验结果的单笔和 Excel 请求
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "excel-row-2");

        assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Collections.singletonList(req)))
                .isInstanceOf(BizException.class).hasMessage("已终止或退市基金不能发起调库");
        assertThatThrownBy(() -> service.addAdjustLog(req))
                .isInstanceOf(BizException.class).hasMessage("已终止或退市基金不能发起调库");

        verify(mapper, never()).queryFundByCode(anyString());
        verify(mapper, never()).addAdjustLog(any());
        verify(mapper, never()).addAdjustStep(any());
    }

    /** 多基金来源组一次获取全部主档锁，再按当前主档完成复核和写入。 */
    @Test
    public void excelSubmitShouldLockAllFundMastersBeforeBusinessChecks() {
        FundInfoBo other = new FundInfoBo();
        other.setId(2L);
        other.setFundCode("FUND002.SH");
        other.setFundName("另一基金");
        other.setFundShortName("另一基金");
        other.setSecurityType(fund.getSecurityType());
        other.setMarketCode(fund.getMarketCode());
        other.setSecurityStatus("L");
        when(mapper.queryFundListForUpdate(anyList())).thenReturn(Arrays.asList(fund, other));
        // 构造两只基金的独立来源请求，输入顺序与主档顺序相反
        FundPoolAdjustSubmitReq first = buildSubmitRequest(10L, "excel-row-2");
        first.setFundCode(other.getFundCode());
        // 构造另一基金请求以核对整批一次锁定
        FundPoolAdjustSubmitReq second = buildSubmitRequest(20L, "excel-row-3");

        service.addExcelImportAdjustLogList(Arrays.asList(first, second));

        InOrder order = inOrder(mapper);
        order.verify(mapper).queryFundListForUpdate(Arrays.asList("FUND001.SH", "FUND002.SH"));
        order.verify(mapper).queryFundCurrentPoolIdList("FUND002.SH");
        order.verify(mapper).queryFundCurrentPoolIdList("FUND001.SH");
        order.verify(mapper).addAdjustLog(argThat(log -> "FUND002.SH".equals(log.getFundCode())));
        verify(mapper, times(1)).queryFundListForUpdate(anyList());
    }

    /** 有效临时代码的首个 O32 审批节点创建人工待办，不能自动通过。 */
    @Test
    public void temporaryFundShouldCreateInitialManualO32Step() {
        when(tempFundCodeMapper.queryTemporaryCodeCountByFundCode(fund.getFundCode())).thenReturn(1);
        // 配置 O32 后仍有普通人工节点的正式版本流程
        configureO32Flow("o32", false);
        // 构造有效临时代码的单笔申请
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "temporary-group");

        service.addAdjustLog(req);

        ArgumentCaptor<FundAdjustStepBo> steps = ArgumentCaptor.forClass(FundAdjustStepBo.class);
        verify(mapper, times(2)).addAdjustStep(steps.capture());
        assertThat(steps.getAllValues().get(1).getFlowNodeId()).isEqualTo(1002L);
        assertThat(steps.getAllValues().get(1).getApprovalStrategy()).isEqualTo("o32");
        assertThat(steps.getAllValues().get(1).getStepStatus()).isEqualTo("pending");
        verify(mapper, never()).addFundPoolStatus(any());
    }

    /** 正式基金的 O32 节点保持自动处理，后续人工节点仍创建待办。 */
    @Test
    public void officialFundShouldKeepInitialO32AutomaticBehavior() {
        // 配置 O32 与普通人工节点，验证正式基金旧行为
        configureO32Flow("o32", false);
        // 构造正式基金的单笔申请
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "official-group");

        service.addAdjustLog(req);

        ArgumentCaptor<FundAdjustStepBo> steps = ArgumentCaptor.forClass(FundAdjustStepBo.class);
        verify(mapper, times(3)).addAdjustStep(steps.capture());
        assertThat(steps.getAllValues().get(1).getStepStatus()).isEqualTo("auto_process");
        assertThat(steps.getAllValues().get(2).getFlowNodeId()).isEqualTo(1003L);
        assertThat(steps.getAllValues().get(2).getStepStatus()).isEqualTo("pending");
    }

    /** 临时代码仅改变 O32，普通 auto 节点仍然自动处理。 */
    @Test
    public void temporaryFundShouldKeepAutoStrategyAutomatic() {
        when(tempFundCodeMapper.queryTemporaryCodeCountByFundCode(fund.getFundCode())).thenReturn(1);
        // 配置 auto 后接人工节点，避免将所有自动策略误改成人工
        configureO32Flow("auto", false);
        // 构造有效临时代码的申请
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "temporary-auto");

        service.addAdjustLog(req);

        ArgumentCaptor<FundAdjustStepBo> steps = ArgumentCaptor.forClass(FundAdjustStepBo.class);
        verify(mapper, times(3)).addAdjustStep(steps.capture());
        assertThat(steps.getAllValues().get(1).getStepStatus()).isEqualTo("auto_process");
        assertThat(steps.getAllValues().get(2).getStepStatus()).isEqualTo("pending");
    }

    /** 只有 O32 审批的流程对临时代码是人工流程，对正式基金仍不可用。 */
    @Test
    public void onlyO32FlowShouldBeUsableOnlyForTemporaryFund() {
        // 配置只有 O32 审批的流程版本
        configureO32Flow("o32", true);
        // 构造同一流程下的正式基金申请
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "only-o32");
        assertThatThrownBy(() -> service.addAdjustLog(req))
                .isInstanceOf(BizException.class).hasMessageContaining("未配置已发布的一般审批流程");
        verify(mapper, never()).addAdjustLog(any());

        when(tempFundCodeMapper.queryTemporaryCodeCountByFundCode(fund.getFundCode())).thenReturn(1);
        service.addAdjustLog(req);
        ArgumentCaptor<FundAdjustStepBo> steps = ArgumentCaptor.forClass(FundAdjustStepBo.class);
        verify(mapper, times(2)).addAdjustStep(steps.capture());
        assertThat(steps.getAllValues().get(1).getStepStatus()).isEqualTo("pending");
    }

    /** 校验和提交均重新读取基金主档，主档不存在时不得继续。 */
    @Test
    public void bothExcelEntrypointsShouldRequireCurrentFundMaster() {
        when(mapper.queryFundByCode("FUND001.SH")).thenReturn(null);
        when(mapper.queryFundListForUpdate(anyList())).thenReturn(Collections.emptyList());
        // 构造同一不存在基金的校验及提交请求
        FundAdjustCheckReq check = buildCheckRequest(10L, AdjustMode.IN.getCode());
        // 构造提交入口需要重新读取主档的来源组
        FundPoolAdjustSubmitReq submit = buildSubmitRequest(10L, "excel-row-2");

        assertThatThrownBy(() -> service.checkExcelImportAdjust(check, "1"))
                .isInstanceOf(BizException.class).hasMessage("基金不存在或已删除");
        assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Collections.singletonList(submit)))
                .isInstanceOf(BizException.class).hasMessage("基金不存在或已删除");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 单笔入口继续允许有效内部报告，并沿用主档及基金附件绑定。 */
    @Test
    public void singleSubmitShouldKeepInternalReportAndMasterDataBehavior() {
        firstPool.setInReportRestriction("internal");
        // 构造选择内部报告的原单笔基金调库申请
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "fund-group-1");
        req.getItems().get(0).setReportSourceAttachmentIds(Collections.singletonList(99L));

        FundAdjustSubmitDto result = service.addAdjustLog(req);

        assertThat(result.getAdjustLogIds()).containsExactly(1L);
        verify(attachmentService).validateCreditReportSources(Collections.singletonList(99L), true);
        verify(attachmentService).copyReportAttachments("ip_adjust_log_fund", 1L,
                Collections.singletonList(99L), "credit_report", "1");
        ArgumentCaptor<FundAdjustLogBo> log = ArgumentCaptor.forClass(FundAdjustLogBo.class);
        verify(mapper).addAdjustLog(log.capture());
        assertThat(log.getValue().getFundName()).isEqualTo("主档基金全称");
        assertThat(log.getValue().getAuditStatus()).isEqualTo("00");
        verify(mapper, never()).addFundPoolStatus(any());
    }

    /** 关系项先于缺失主项提交时，准备阶段阻断且没有任何日志写入。 */
    @Test
    public void excelSubmitShouldRequireManualItemBeforeAnyWrite() {
        // 构造只有关系项、没有手工主项的错误来源组
        FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "excel-row-2");
        req.getItems().get(0).setItemTag("linkage");

        assertThatThrownBy(() -> service.addExcelImportAdjustLogList(Collections.singletonList(req)))
                .isInstanceOf(BizException.class).hasMessage("每个调库分组必须包含一条手工调整项");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 提交等待共同主档锁时并发转正禁用旧码，锁后必须读取 D 并在任何写入前失败。 */
    @Test
    public void excelSubmitShouldReadDisabledMasterAfterConcurrentLockWait() throws Exception {
        String javaVersion = System.getProperty("java.specification.version");
        int major = Integer.parseInt(javaVersion.startsWith("1.") ? javaVersion.substring(2) : javaVersion);
        Assume.assumeTrue("H2 2.3.232 并发持久化测试需 Java 11 以上运行时", major >= 11);
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch masterLocked = new CountDownLatch(1);
        CountDownLatch submitLockStarted = new CountDownLatch(1);
        CountDownLatch finishConversion = new CountDownLatch(1);
        try {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE test_fund_master (id BIGINT PRIMARY KEY, fund_code VARCHAR(100), security_status VARCHAR(1))");
            jdbc.update("INSERT INTO test_fund_master VALUES (1, ?, 'L')", fund.getFundCode());
            DataSourceTransactionManager manager = new DataSourceTransactionManager(database);
            TransactionTemplate conversion = new TransactionTemplate(manager);
            when(mapper.queryFundListForUpdate(anyList())).thenAnswer(invocation -> {
                submitLockStarted.countDown();
                return jdbc.query("SELECT id,fund_code,security_status FROM test_fund_master WHERE fund_code = ? ORDER BY id FOR UPDATE",
                        (result, rowIndex) -> {
                            FundInfoBo current = new FundInfoBo();
                            current.setId(result.getLong("id"));
                            current.setFundCode(result.getString("fund_code"));
                            current.setSecurityStatus(result.getString("security_status"));
                            return current;
                        }, fund.getFundCode());
            });
            TransactionInterceptor interceptor = new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource());
            ProxyFactory factory = new ProxyFactory(service);
            factory.setProxyTargetClass(true);
            factory.addAdvice(interceptor);
            FundPoolAdjustService transactional = (FundPoolAdjustService) factory.getProxy();
            Future<?> converting = executor.submit(() -> conversion.execute(status -> {
                jdbc.queryForList("SELECT id FROM test_fund_master WHERE id = 1 FOR UPDATE");
                masterLocked.countDown();
                try {
                    if (!finishConversion.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("并发提交未按时进入主档锁查询");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                jdbc.update("UPDATE test_fund_master SET security_status = 'D' WHERE id = 1");
                return null;
            }));
            assertThat(masterLocked.await(5, TimeUnit.SECONDS)).isTrue();
            // 构造在转正前已取得旧校验结果的 Excel 来源组
            FundPoolAdjustSubmitReq req = buildSubmitRequest(10L, "excel-before-conversion");
            Future<?> submitting = executor.submit(() -> transactional.addExcelImportAdjustLogList(Collections.singletonList(req)));
            assertThat(submitLockStarted.await(5, TimeUnit.SECONDS)).isTrue();
            finishConversion.countDown();
            converting.get(5, TimeUnit.SECONDS);

            assertThatThrownBy(() -> submitting.get(5, TimeUnit.SECONDS))
                    .hasRootCauseMessage("已终止或退市基金不能发起调库");
            verify(mapper, never()).queryFundByCode(anyString());
            verify(mapper, never()).addAdjustLog(any());
            verify(mapper, never()).addAdjustStep(any());
        } finally {
            finishConversion.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            database.shutdown();
        }
    }

    /** 主档锁清除真实 SqlSession 的锁前缓存，等待后重新查询日志和步骤必须读取已提交的新值。 */
    @Test
    public void masterLockShouldClearCachedLogsAndStepsAfterConcurrentWait() throws Exception {
        String javaVersion = System.getProperty("java.specification.version");
        int major = Integer.parseInt(javaVersion.startsWith("1.") ? javaVersion.substring(2) : javaVersion);
        Assume.assumeTrue("H2 2.3.232 并发持久化测试需 Java 11 以上运行时", major >= 11);
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch masterLocked = new CountDownLatch(1);
        CountDownLatch readerLockStarted = new CountDownLatch(1);
        CountDownLatch finishMutation = new CountDownLatch(1);
        try {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE rrs_fundinfo (id BIGINT PRIMARY KEY,fund_code VARCHAR(100)"
                    + ",security_status VARCHAR(1),is_deleted INT)");
            jdbc.execute("CREATE TABLE ip_adjust_log_fund (id BIGINT PRIMARY KEY,fund_code VARCHAR(100)"
                    + ",adjust_batch_no VARCHAR(64),audit_status VARCHAR(4),is_deleted INT)");
            jdbc.execute("CREATE TABLE ip_adjust_step_fund (id BIGINT PRIMARY KEY,step_status VARCHAR(16))");
            jdbc.update("INSERT INTO rrs_fundinfo VALUES (1,'TMP001','L',0)");
            jdbc.update("INSERT INTO ip_adjust_log_fund VALUES (1,'TMP001','FUND-CACHE','00',0)");
            jdbc.update("INSERT INTO ip_adjust_step_fund VALUES (1,'pending')");
            Configuration configuration = new Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setEnvironment(new Environment("cache-test", new SpringManagedTransactionFactory(), database));
            try (InputStream xml = getClass().getResourceAsStream("/mapper/FundPoolAdjustMapper.xml")) {
                new XMLMapperBuilder(xml, configuration, "FundPoolAdjustMapper.xml", configuration.getSqlFragments()).parse();
            }
            SqlSessionTemplate session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
            FundPoolAdjustMapper realMapper = session.getMapper(FundPoolAdjustMapper.class);
            DataSourceTransactionManager manager = new DataSourceTransactionManager(database);
            TransactionTemplate mutation = new TransactionTemplate(manager);
            mutation.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            TransactionTemplate reading = new TransactionTemplate(manager);
            reading.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            Future<?> mutating = executor.submit(() -> mutation.execute(status -> {
                jdbc.queryForList("SELECT id FROM rrs_fundinfo WHERE id=1 FOR UPDATE");
                masterLocked.countDown();
                try {
                    if (!finishMutation.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("缓存复核事务未按时进入主档锁查询");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                jdbc.update("UPDATE rrs_fundinfo SET security_status='D' WHERE id=1");
                jdbc.update("UPDATE ip_adjust_log_fund SET fund_code='FORMAL001' WHERE id=1");
                jdbc.update("UPDATE ip_adjust_step_fund SET step_status='approve' WHERE id=1");
                return null;
            }));
            assertThat(masterLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> rereading = executor.submit(() -> reading.execute(status -> {
                List<FundAdjustLogBo> oldLogs = realMapper.queryAdjustLogListForAudit("FUND-CACHE");
                FundAdjustStepBo oldStep = realMapper.queryAdjustStepById(1L);
                assertThat(oldLogs.get(0).getFundCode()).isEqualTo("TMP001");
                assertThat(oldStep.getStepStatus()).isEqualTo("pending");
                // 确认同一事务中的重复查询已由真实 SESSION 一级缓存提供旧对象
                assertThat(realMapper.queryAdjustLogListForAudit("FUND-CACHE")).isSameAs(oldLogs);
                assertThat(realMapper.queryAdjustStepById(1L)).isSameAs(oldStep);
                readerLockStarted.countDown();
                List<FundInfoBo> lockedFunds = realMapper.queryFundListForUpdate(Collections.singletonList("TMP001"));
                assertThat(lockedFunds.get(0).getSecurityStatus()).isEqualTo("D");
                // 共同锁语句清缓存后，日志和步骤复核必须重新查库而不能复用锁前结果
                assertThat(realMapper.queryAdjustLogListForAudit("FUND-CACHE").get(0).getFundCode())
                        .isEqualTo("FORMAL001");
                assertThat(realMapper.queryAdjustStepById(1L).getStepStatus()).isEqualTo("approve");
                return null;
            }));
            assertThat(readerLockStarted.await(5, TimeUnit.SECONDS)).isTrue();
            finishMutation.countDown();
            mutating.get(5, TimeUnit.SECONDS);
            rereading.get(5, TimeUnit.SECONDS);
        } finally {
            finishMutation.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            database.shutdown();
        }
    }

    /** 后续组的步骤持久化异常时，真实数据库事务回滚前面全部日志和步骤。 */
    @Test
    public void excelSubmitShouldRollbackWholeBatchWhenLaterPersistenceFails() {
        String javaVersion = System.getProperty("java.specification.version");
        int major = Integer.parseInt(javaVersion.startsWith("1.") ? javaVersion.substring(2) : javaVersion);
        Assume.assumeTrue("H2 2.3.232 持久化测试需 Java 11 以上运行时", major >= 11);
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE test_fund_log (id BIGINT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE test_fund_step (log_id BIGINT)");
            AtomicLong logSequence = new AtomicLong();
            doAnswer(invocation -> {
                FundAdjustLogBo log = invocation.getArgument(0);
                log.setId(logSequence.incrementAndGet());
                return jdbc.update("INSERT INTO test_fund_log (id) VALUES (?)", log.getId());
            }).when(mapper).addAdjustLog(any());
            doAnswer(invocation -> {
                FundAdjustStepBo step = invocation.getArgument(0);
                if (Long.valueOf(2L).equals(step.getAdjustLogId())) {
                    throw new BizException("模拟后续组步骤写入失败");
                }
                return jdbc.update("INSERT INTO test_fund_step (log_id) VALUES (?)", step.getAdjustLogId());
            }).when(mapper).addAdjustStep(any());
            TransactionInterceptor interceptor = new TransactionInterceptor(
                    new DataSourceTransactionManager(database), new AnnotationTransactionAttributeSource());
            ProxyFactory proxyFactory = new ProxyFactory(service);
            proxyFactory.setProxyTargetClass(true);
            proxyFactory.addAdvice(interceptor);
            FundPoolAdjustService transactionalService = (FundPoolAdjustService) proxyFactory.getProxy();
            // 构造后续组在持久化阶段失败的整批请求
            FundPoolAdjustSubmitReq first = buildSubmitRequest(10L, "excel-row-2");
            // 构造触发步骤写入失败并应回滚前组的后续来源组
            FundPoolAdjustSubmitReq second = buildSubmitRequest(20L, "excel-row-3");

            assertThatThrownBy(() -> transactionalService.addExcelImportAdjustLogList(Arrays.asList(first, second)))
                    .isInstanceOf(BizException.class).hasMessage("模拟后续组步骤写入失败");

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_fund_log", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_fund_step", Integer.class)).isZero();
            verify(mapper, never()).addFundPoolStatus(any());
        } finally {
            database.shutdown();
        }
    }

    /**
     * 构造具有基金准入及一般调入调出流程的投资池。
     *
     * @param poolId 目标池 ID
     * @param poolName 目标池名称
     */
    private InvestmentPoolBo buildPool(Long poolId, String poolName) {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(poolId);
        pool.setPoolName(poolName);
        pool.setPoolType("fund");
        pool.setStatus("enabled");
        pool.setVarietyCodes("[\"fund\"]");
        pool.setMarketCodes("[\"SH\"]");
        pool.setInFlowId(100L);
        pool.setOutFlowId(100L);
        pool.setInFlowKey("fund-normal");
        pool.setOutFlowKey("fund-normal");
        pool.setInFlowName("基金一般流程");
        pool.setOutFlowName("基金一般流程");
        pool.setInReportRestriction("none");
        pool.setOutReportRestriction("none");
        return pool;
    }

    /**
     * 配置自动策略节点后接普通人工节点的流程，或只有自动策略审批的流程。
     *
     * @param strategy 待核对的 o32 或 auto 审批策略
     * @param onlyStrategy 是否只保留该策略的审批节点
     */
    private void configureO32Flow(String strategy, boolean onlyStrategy) {
        FlowNodeBo start = new FlowNodeBo();
        start.setId(1001L);
        start.setNodeType("start");
        FlowNodeBo o32 = new FlowNodeBo();
        o32.setId(1002L);
        o32.setNodeType("approval");
        o32.setLabel("O32 审批");
        FlowNodeBo manual = new FlowNodeBo();
        manual.setId(1003L);
        manual.setNodeType("approval");
        FlowNodeBo end = new FlowNodeBo();
        end.setId(1004L);
        end.setNodeType("end");
        FlowEdgeBo first = new FlowEdgeBo();
        first.setFromNodeId(start.getId());
        first.setToNodeId(o32.getId());
        FlowEdgeBo second = new FlowEdgeBo();
        second.setFromNodeId(o32.getId());
        second.setToNodeId(onlyStrategy ? end.getId() : manual.getId());
        NodeApprovalConfigBo config = new NodeApprovalConfigBo();
        config.setId(2002L);
        config.setNodeId(o32.getId());
        config.setApprovalStrategy(strategy);
        when(flowMapper.queryFlowNodeListByVersionId(1000L)).thenReturn(onlyStrategy
                ? Arrays.asList(start, o32, end) : Arrays.asList(start, o32, manual, end));
        when(flowMapper.queryFlowEdgeListByVersionId(1000L)).thenReturn(Arrays.asList(first, second));
        when(flowMapper.queryApprovalConfigListByVersionId(1000L)).thenReturn(Collections.singletonList(config));
    }

    /** 配置已发布并包含实际人工审批节点的一般流程快照。 */
    private void configureNormalFlow() {
        FlowDefinitionBo flow = new FlowDefinitionBo();
        flow.setId(100L);
        flow.setFlowKey("fund-normal");
        flow.setStatus("active");
        FlowVersionBo version = new FlowVersionBo();
        version.setId(1000L);
        version.setStatus("active");
        FlowNodeBo start = new FlowNodeBo();
        start.setId(1001L);
        start.setNodeType("start");
        start.setLabel("开始");
        FlowNodeBo approval = new FlowNodeBo();
        approval.setId(1002L);
        approval.setNodeType("approval");
        approval.setLabel("基金审批");
        FlowEdgeBo edge = new FlowEdgeBo();
        edge.setFromNodeId(1001L);
        edge.setToNodeId(1002L);
        when(flowMapper.queryFlowById(100L)).thenReturn(flow);
        when(flowMapper.queryFlowVersionByFlowIdList(eq(100L), any())).thenReturn(Collections.singletonList(version));
        when(flowMapper.queryFlowNodeListByVersionId(1000L)).thenReturn(Arrays.asList(start, approval));
        when(flowMapper.queryFlowEdgeListByVersionId(1000L)).thenReturn(Collections.singletonList(edge));
    }

    /**
     * 构造单个 Excel 来源池的校验请求。
     *
     * @param poolId 来源主项的目标池 ID
     * @param adjustMode 基金内部调入或调出编码
     */
    private FundAdjustCheckReq buildCheckRequest(Long poolId, String adjustMode) {
        FundAdjustCheckReq req = new FundAdjustCheckReq();
        req.setFundCode("FUND001.SH");
        FundAdjustCheckReq.CheckItem item = new FundAdjustCheckReq.CheckItem();
        item.setTargetPoolId(poolId);
        item.setAdjustMode(adjustMode);
        req.setItems(Collections.singletonList(item));
        return req;
    }

    /**
     * 构造含必填评分、投资类型、领导审批及一般流程的来源组。
     *
     * @param poolId 来源主项的目标池 ID
     * @param groupKey 主项及其关系项共用的来源分组标识
     */
    private FundPoolAdjustSubmitReq buildSubmitRequest(Long poolId, String groupKey) {
        FundPoolAdjustSubmitReq req = new FundPoolAdjustSubmitReq();
        req.setFundCode("FUND001.SH");
        req.setFundScore(new BigDecimal("8.5"));
        req.setFundInvestmentType("stock");
        req.setNeedRiskLeaderApproval(0);
        req.setAdjusterId("1");
        req.setAdjusterName("基金研究员");
        FundPoolAdjustSubmitReq.AdjustItem item = new FundPoolAdjustSubmitReq.AdjustItem();
        item.setTargetPoolId(poolId);
        item.setTargetPoolName("前端传入名称");
        item.setItemTag("manual");
        item.setAdjustMode(AdjustMode.IN.getCode());
        item.setAdjustGroupKey(groupKey);
        item.setFlowId(100L);
        item.setFlowKey("fund-normal");
        item.setFlowType("normalInbound");
        req.setItems(Collections.singletonList(item));
        return req;
    }
}
