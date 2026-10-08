package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.znty.rrs.entity.batchfundpooladjust.BatchFundAdjustDto;
import com.znty.rrs.entity.batchfundpooladjust.BatchFundAdjustReq;
import com.znty.rrs.entity.batchfundpooladjust.BatchFundPoolAdjustReq;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.FundAdjustLogBo;
import com.znty.rrs.entity.bo.FundAdjustStepBo;
import com.znty.rrs.entity.bo.FundInfoBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SysAttachmentBo;
import com.znty.rrs.entity.fundpooladjust.FundAdjustCheckDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.BatchFundPoolAdjustMapper;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.FundPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SysAttachmentMapper;
import com.znty.rrs.mapper.TempFundCodeMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 串联基金批量编排与真实单笔服务，验证完整分组、字段及事务行为 */
public class BatchFundPoolAdjustServiceTest {
    /** 验证实际共享附件存储的独立临时目录 */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    /** 批量编排服务 */
    private BatchFundPoolAdjustService batchService;
    /** 真实基金单笔服务 */
    private FundPoolAdjustService fundService;
    /** 基金业务查询和写入组件 */
    private FundPoolAdjustMapper mapper;
    /** 投资池查询组件 */
    private InvestmentPoolMapper poolMapper;
    /** 批量分页查询组件 */
    private BatchFundPoolAdjustMapper batchMapper;
    /** 附件服务 */
    private SysAttachmentService attachments;
    /** 测试基金主档 */
    private List<FundInfoBo> funds;
    /** 手工目标池 */
    private InvestmentPoolBo firstPool;
    /** 联动或互斥目标池 */
    private InvestmentPoolBo secondPool;

    /** 配置两只基金、两个支持基金的投资池及一般人工审批流程。 */
    @Before
    public void setUp() {
        batchService = new BatchFundPoolAdjustService();
        fundService = new FundPoolAdjustService();
        mapper = mock(FundPoolAdjustMapper.class);
        poolMapper = mock(InvestmentPoolMapper.class);
        batchMapper = mock(BatchFundPoolAdjustMapper.class);
        attachments = mock(SysAttachmentService.class);
        FlowMapper flows = mock(FlowMapper.class);
        InvestmentPoolService poolService = mock(InvestmentPoolService.class);
        ReflectionTestUtils.setField(batchService, "batchFundPoolAdjustMapper", batchMapper);
        ReflectionTestUtils.setField(batchService, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(batchService, "investmentPoolService", poolService);
        ReflectionTestUtils.setField(batchService, "fundPoolAdjustService", fundService);
        ReflectionTestUtils.setField(batchService, "sysAttachmentService", attachments);
        ReflectionTestUtils.setField(fundService, "fundPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(fundService, "investmentPoolMapper", poolMapper);
        ReflectionTestUtils.setField(fundService, "flowMapper", flows);
        ReflectionTestUtils.setField(fundService, "sysAttachmentService", attachments);
        ReflectionTestUtils.setField(fundService, "tempFundCodeMapper", mock(TempFundCodeMapper.class));
        // 构造两个具有已发布一般流程的基金池
        firstPool = buildPool(10L);
        // 构造用于检验联动与互斥的第二个目标池
        secondPool = buildPool(20L);
        when(poolMapper.queryPoolList()).thenReturn(Arrays.asList(firstPool, secondPool));
        when(batchMapper.queryEnabledFundLeafPoolCount(10L)).thenReturn(1);
        Map<Long, String> fullNames = new HashMap<>();
        fullNames.put(10L, "基金大库/基金池10");
        fullNames.put(20L, "基金大库/基金池20");
        when(poolService.queryPoolFullNameMap()).thenReturn(fullNames);
        // 构造权威主档用于校验及提交名称读取
        FundInfoBo first = buildFund("FUND001.SH", 1L);
        // 构造第二只基金以验证独立批次和整批回滚
        FundInfoBo second = buildFund("FUND002.SH", 2L);
        funds = Arrays.asList(first, second);
        when(mapper.queryFundByCode(anyString())).thenAnswer(invocation -> funds.stream()
                .filter(fund -> fund.getFundCode().equals(invocation.getArgument(0)))
                .findFirst().orElse(null));
        when(mapper.queryFundListForUpdate(anyList())).thenAnswer(invocation -> {
            List<String> codes = invocation.getArgument(0);
            List<FundInfoBo> selected = new ArrayList<>();
            for (FundInfoBo fund : funds) {
                if (codes.contains(fund.getFundCode())) {
                    selected.add(fund);
                }
            }
            return selected;
        });
        when(mapper.queryFundCurrentPoolIdList(anyString())).thenReturn(Collections.emptyList());
        when(mapper.queryAllPoolRelationList()).thenReturn(Collections.emptyList());
        AtomicLong sequence = new AtomicLong();
        when(mapper.addAdjustLog(any())).thenAnswer(invocation -> {
            FundAdjustLogBo log = invocation.getArgument(0);
            log.setId(sequence.incrementAndGet());
            return 1;
        });
        FlowDefinitionBo definition = new FlowDefinitionBo();
        definition.setId(100L);
        definition.setFlowKey("fund-normal");
        definition.setStatus("active");
        FlowVersionBo version = new FlowVersionBo();
        version.setId(1000L);
        version.setStatus("active");
        FlowNodeBo start = new FlowNodeBo();
        start.setId(1001L);
        start.setNodeType("start");
        FlowNodeBo approval = new FlowNodeBo();
        approval.setId(1002L);
        approval.setNodeType("approval");
        FlowEdgeBo edge = new FlowEdgeBo();
        edge.setFromNodeId(1001L);
        edge.setToNodeId(1002L);
        when(flows.queryFlowById(100L)).thenReturn(definition);
        when(flows.queryFlowVersionByFlowIdList(eq(100L), any())).thenReturn(Collections.singletonList(version));
        when(flows.queryFlowNodeListByVersionId(1000L)).thenReturn(Arrays.asList(start, approval));
        when(flows.queryFlowEdgeListByVersionId(1000L)).thenReturn(Collections.singletonList(edge));
    }

    /** 清理分页线程上下文。 */
    @After
    public void tearDown() {
        PageHelper.clearPage();
    }

    /** 跨基金分组键必须唯一，且仅提供目标池的一般流程。 */
    @Test
    public void checkShouldKeepIndependentFundGroupsAndNormalFlows() {
        // 构造两只基金共享的手工目标池校验请求
        BatchFundAdjustDto dto = batchService.checkAdjust(buildCheckRequest("in", "FUND001.SH", "FUND002.SH"));
        assertThat(dto.getItems()).hasSize(2);
        assertThat(dto.getItems()).extracting(FundAdjustCheckDto.CheckResultItem::getAdjustGroupKey)
                .containsExactly("FUND001.SH_fund-group-1", "FUND002.SH_fund-group-1");
        assertThat(dto.getItems()).allMatch(FundAdjustCheckDto.CheckResultItem::isCanAdjust);
        assertThat(dto.getItems().get(0).getFlowOptions().get(0).getFlowType()).isEqualTo("normalInbound");
    }

    /** 相反方向互斥项必须与调入主项同批提交。 */
    @Test
    public void submitShouldKeepOppositeDirectionMutexAndSharedFields() {
        // 配置已入互斥池的基金，使调入主项产生调出关系项
        configureRelation("in_mutex", Collections.singletonList(20L));
        // 将单笔校验结果完整转换为批量提交请求
        BatchFundAdjustReq req = buildSubmitRequest("in", "FUND001.SH", "FUND002.SH");
        BatchFundAdjustDto result = batchService.addAdjustLog(req);
        ArgumentCaptor<FundAdjustLogBo> logs = ArgumentCaptor.forClass(FundAdjustLogBo.class);
        verify(mapper, times(4)).addAdjustLog(logs.capture());
        assertThat(result.getFundCount()).isEqualTo(2);
        assertThat(result.getSubmitCount()).isEqualTo(4);
        assertThat(result.getAdjustBatchNos()).hasSize(2).doesNotHaveDuplicates();
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getAuditStatus).containsOnly("00");
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getFundScore).containsOnly(BigDecimal.ZERO);
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getNeedRiskLeaderApproval).containsOnly(0);
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getFundInvestmentType).containsOnly("stock");
        assertThat(logs.getAllValues()).extracting(FundAdjustLogBo::getAdjustMode)
                .containsExactly("调入", "调出", "调入", "调出");
        assertThat(logs.getAllValues().get(0).getAdjustBatchNo())
                .isEqualTo(logs.getAllValues().get(1).getAdjustBatchNo()).startsWith("FUND-");
        assertThat(logs.getAllValues().get(0).getFundName()).isEqualTo("权威基金FUND001.SH");
        assertThat(logs.getAllValues().get(0).getAdjustType()).isEqualTo("手动批量调整");
        verify(mapper, never()).addFundPoolStatus(any());
    }

    /** 已在池基金的调出主项复用一般调出流程。 */
    @Test
    public void shouldSubmitOutboundWithNormalFlow() {
        when(mapper.queryFundCurrentPoolIdList(anyString())).thenReturn(Collections.singletonList(10L));
        // 保留调出方向并构造有效单基金申请
        BatchFundAdjustDto result = batchService.addAdjustLog(buildSubmitRequest("out", "FUND001.SH"));
        assertThat(result.getSubmitCount()).isEqualTo(1);
        ArgumentCaptor<FundAdjustLogBo> logs = ArgumentCaptor.forClass(FundAdjustLogBo.class);
        verify(mapper).addAdjustLog(logs.capture());
        assertThat(logs.getValue().getFlowType()).isEqualTo("normalOutbound");
        verify(mapper, never()).deleteFundPoolStatus(anyString(), any());
    }

    /** 漏掉互斥调整项时不可只提交主项。 */
    @Test
    public void submitShouldRejectMissingMutexBeforeWriting() {
        // 配置产生相反方向互斥项的基金所在池
        configureRelation("in_mutex", Collections.singletonList(20L));
        // 从完整校验结果构造请求后模拟客户端遗漏关系项
        BatchFundAdjustReq req = buildSubmitRequest("in", "FUND001.SH");
        req.setItems(Collections.singletonList(req.getItems().get(0)));
        assertThatThrownBy(() -> batchService.addAdjustLog(req)).isInstanceOf(BizException.class)
                .hasMessageContaining("联动或互斥明细与最新关系不一致");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 将关系项伪装为手工项不可绕过完整性复核。 */
    @Test
    public void submitShouldRejectForgedManualItem() {
        // 配置联动池以生成第二条明细
        configureRelation("in_linked", Collections.emptyList());
        // 将原本有效的关系项模拟篡改为手工项
        BatchFundAdjustReq req = buildSubmitRequest("in", "FUND001.SH");
        req.getItems().get(1).setItemTag("manual");
        assertThatThrownBy(() -> batchService.addAdjustLog(req)).isInstanceOf(BizException.class);
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 单只基金主项或关系项失败时整组均不可提交。 */
    @Test
    public void checkShouldBlockCompleteGroupWhenRelationPoolFails() {
        // 联动到已锁定目标池时，主项不能独立显示可提交
        configureRelation("in_linked", Collections.emptyList());
        secondPool.setLockFlag(1);
        // 批量校验须传播同组失败状态
        BatchFundAdjustDto result = batchService.checkAdjust(buildCheckRequest("in", "FUND001.SH"));
        assertThat(result.getItems()).hasSize(2).allMatch(row -> !row.isCanAdjust());
        assertThat(result.getItems().get(0).getFailReasons()).contains("同一基金调库分组存在未通过的调整项");
    }

    /** 主档失效按基金返回失败结果，允许其他正常基金继续校验。 */
    @Test
    public void checkShouldKeepOtherFundWhenOneMasterBecomesDisabled() {
        funds.get(1).setSecurityStatus("D");
        // 一只失效基金不应中断整批校验结果
        BatchFundAdjustDto result = batchService.checkAdjust(buildCheckRequest("in", "FUND001.SH", "FUND002.SH"));
        assertThat(result.getItems().get(0).isCanAdjust()).isTrue();
        assertThat(result.getItems().get(1).isCanAdjust()).isFalse();
        assertThat(result.getItems().get(1).getFailReasons()).contains("已终止或退市基金不能发起调库");
    }

    /** 非管理员缺少关系池权限时整组失败。 */
    @Test
    public void checkShouldVerifyRelationPoolAdjustablePermission() {
        // 配置联动关系，仅授予用户主池权限
        configureRelation("in_linked", Collections.emptyList());
        PoolPermissionBo permission = new PoolPermissionBo();
        permission.setPoolId(10L);
        permission.setHandlerType("user");
        permission.setHandlerId(2L);
        when(poolMapper.queryPermissionListByType("adjustable")).thenReturn(Collections.singletonList(permission));
        // 验证关系池权限失效不会被主池权限掩盖
        BatchFundAdjustReq req = buildCheckRequest("in", "FUND001.SH");
        req.setCurrentUserId("2");
        BatchFundAdjustDto result = batchService.checkAdjust(req);
        assertThat(result.getItems()).allMatch(row -> !row.isCanAdjust());
        assertThat(result.getItems().get(1).getFailReasons().get(0)).contains("没有投资池调整权限");
    }

    /** 无调整权限用户的池列表为空且不可查询候选基金。 */
    @Test
    public void queriesShouldEnforceAdjustablePermissions() {
        BatchFundPoolAdjustReq req = new BatchFundPoolAdjustReq();
        req.setCurrentUserId("2");
        assertThat(batchService.queryPoolPage(req).getRecords()).isEmpty();
        req.setPoolId(10L);
        req.setDirection("in");
        assertThatThrownBy(() -> batchService.queryFundPage(req)).isInstanceOf(BizException.class)
                .hasMessageContaining("无权调整");
        verify(batchMapper, never()).queryFundPage(any());
    }

    /** 零分合法，超数据库存储精度评分拒绝。 */
    @Test
    public void submitShouldRejectScoreStorageOverflow() {
        // 保留其他必填字段合法，仅模拟超精度评分
        BatchFundAdjustReq req = buildSubmitRequest("in", "FUND001.SH");
        req.setFundScore(new BigDecimal("1.12345"));
        assertThatThrownBy(() -> batchService.addAdjustLog(req)).hasMessageContaining("六位整数和四位小数");
        req.setFundScore(new BigDecimal("1000000"));
        assertThatThrownBy(() -> batchService.addAdjustLog(req)).hasMessageContaining("六位整数和四位小数");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 三个整批必填字段及领导审批值严格验证。 */
    @Test
    public void submitShouldRequireSharedFundFields() {
        // 模拟缺少评分、投资类型或领导审批字段
        BatchFundAdjustReq req = buildSubmitRequest("in", "FUND001.SH");
        req.setFundScore(null);
        assertThatThrownBy(() -> batchService.addAdjustLog(req)).hasMessageContaining("评分不能为空");
        req.setFundScore(BigDecimal.ZERO);
        req.setFundInvestmentType("invalid");
        assertThatThrownBy(() -> batchService.addAdjustLog(req)).hasMessageContaining("合法枚举");
        req.setFundInvestmentType("stock");
        req.setNeedRiskLeaderApproval(null);
        assertThatThrownBy(() -> batchService.addAdjustLog(req)).hasMessageContaining("必须为 0 或 1");
        req.setNeedRiskLeaderApproval(2);
        assertThatThrownBy(() -> batchService.addAdjustLog(req)).hasMessageContaining("必须为 0 或 1");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 不允许用批量专用流程替换单笔目标池的一般流程。 */
    @Test
    public void submitShouldRejectBatchFlow() {
        // 对有效申请模拟篡改流程类型
        BatchFundAdjustReq req = buildSubmitRequest("in", "FUND001.SH");
        req.getItems().get(0).setFlowType("batchInbound");
        assertThatThrownBy(() -> batchService.addAdjustLog(req)).hasMessageContaining("一般审批流程");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 内部报告限制不能因为批量提交而跳过。 */
    @Test
    public void submitShouldEnforceInternalReportRestriction() {
        firstPool.setInReportRestriction("internal");
        // 无报告申请在写入前拒绝
        BatchFundAdjustReq req = buildSubmitRequest("in", "FUND001.SH");
        assertThatThrownBy(() -> batchService.addAdjustLog(req)).hasMessageContaining("要求提交基金报告");
        req.getItems().get(0).setReportSourceAttachmentIds(Collections.singletonList(88L));
        batchService.addAdjustLog(req);
        verify(attachments).validateCreditReportSources(Collections.singletonList(88L), true);
    }

    /** JSON 没有上传上下文时不可伪造报告或材料下标绕过附件限制。 */
    @Test
    public void jsonSubmitShouldRejectLocalReportOrMaterialIndexesWithoutFiles() {
        firstPool.setInReportRestriction("any");
        // 模拟 JSON 客户端使用报告下标伪装已上传报告
        BatchFundAdjustReq req = buildSubmitRequest("in", "FUND001.SH");
        req.getItems().get(0).setReportFileIndexes(Collections.singletonList(0));
        assertThatThrownBy(() -> batchService.addAdjustLog(req)).hasMessageContaining("通过 multipart 同时上传");
        req.getItems().get(0).setReportFileIndexes(Collections.emptyList());
        req.getItems().get(0).setMaterialFileIndexes(Collections.singletonList(0));
        assertThatThrownBy(() -> batchService.addAdjustLog(req)).hasMessageContaining("通过 multipart 同时上传");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 本地附件整批创建一次，但分别绑定各基金日志。 */
    @Test
    public void multipartShouldReuseSubmissionFilesAndOriginalChineseNames() {
        // 为两只基金共用报告附件索引
        BatchFundAdjustReq req = buildSubmitRequest("in", "FUND001.SH", "FUND002.SH");
        for (BatchFundAdjustReq.AdjustItem item : req.getItems()) {
            item.setReportFileIndexes(Collections.singletonList(0));
        }
        List<String> names = Collections.singletonList("基金报告.pdf");
        when(attachments.parseOriginalFileNameListJson("[\"基金报告.pdf\"]")).thenReturn(names);
        SysAttachmentService.SubmissionFiles submission = mock(SysAttachmentService.SubmissionFiles.class);
        List<MockMultipartFile> files = Collections.singletonList(new MockMultipartFile(
                "files", "report.pdf", "application/pdf", new byte[] {1, 2}));
        when(attachments.createSharedSubmissionFiles(anyList(), eq("1"), eq(names))).thenReturn(submission);
        batchService.addAdjustLog(req, new ArrayList<>(files), "[\"基金报告.pdf\"]");
        verify(attachments, times(1)).createSharedSubmissionFiles(anyList(), eq("1"), eq(names));
        verify(attachments, times(2)).bindAttachments(eq("ip_adjust_log_fund"), any(),
                eq(Collections.singletonList(0)), eq("fund_report_hand"), eq(submission));
    }

    /** multipart 缺少真实文件时，附件服务拒绝报告下标而非跳过绑定。 */
    @Test
    public void multipartWithoutFilesShouldRejectReportIndexes() {
        // 使用真实附件服务验证空文件上下文的索引边界
        SysAttachmentService realAttachments = new SysAttachmentService();
        ReflectionTestUtils.setField(batchService, "sysAttachmentService", realAttachments);
        ReflectionTestUtils.setField(fundService, "sysAttachmentService", realAttachments);
        firstPool.setInReportRestriction("any");
        // 仅传报告下标而不上传对应文件必须明确失败
        BatchFundAdjustReq req = buildSubmitRequest("in", "FUND001.SH");
        req.getItems().get(0).setReportFileIndexes(Collections.singletonList(0));
        assertThatThrownBy(() -> batchService.addAdjustLog(req, null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("附件文件下标不合法");
        verify(mapper, never()).addFundPoolStatus(any());
    }

    /** 两只基金报告分别挂日志，同一个上传文件在物理磁盘只保存一次。 */
    @Test
    public void multipartShouldStoreOnePhysicalFileForTwoFundLogs() throws Exception {
        SysAttachmentMapper attachmentMapper = mock(SysAttachmentMapper.class);
        SysAttachmentService realAttachments = new SysAttachmentService();
        ReflectionTestUtils.setField(realAttachments, "sysAttachmentMapper", attachmentMapper);
        ReflectionTestUtils.setField(realAttachments, "storagePath", temporaryFolder.getRoot().getAbsolutePath());
        realAttachments.initializeStorage();
        ReflectionTestUtils.setField(batchService, "sysAttachmentService", realAttachments);
        ReflectionTestUtils.setField(fundService, "sysAttachmentService", realAttachments);
        // 整批申请的两只基金共用相同报告文件索引
        BatchFundAdjustReq req = buildSubmitRequest("in", "FUND001.SH", "FUND002.SH");
        for (BatchFundAdjustReq.AdjustItem item : req.getItems()) {
            item.setReportFileIndexes(Collections.singletonList(0));
        }
        MockMultipartFile file = new MockMultipartFile("files", "upload.pdf", "application/pdf", new byte[] {1, 2});
        batchService.addAdjustLog(req, Collections.singletonList(file), "[\"基金报告.pdf\"]");
        ArgumentCaptor<SysAttachmentBo> rows = ArgumentCaptor.forClass(SysAttachmentBo.class);
        verify(attachmentMapper, times(2)).addAttachment(rows.capture());
        assertThat(rows.getAllValues()).extracting(SysAttachmentBo::getMainId).containsExactly(1L, 2L);
        assertThat(rows.getAllValues()).extracting(SysAttachmentBo::getOriginalFileName).containsOnly("基金报告.pdf");
        assertThat(rows.getAllValues().get(0).getFileName()).isEqualTo(rows.getAllValues().get(1).getFileName());
        try (Stream<Path> paths = Files.walk(temporaryFolder.getRoot().toPath())) {
            assertThat(paths.filter(Files::isRegularFile).count()).isEqualTo(1L);
        }
    }

    /** 全部基金复核完成之前不得写入前面任何申请。 */
    @Test
    public void submitShouldWriteNothingWhenLaterMasterChanges() {
        // 校验通过后，模拟后续基金主档失效
        BatchFundAdjustReq req = buildSubmitRequest("in", "FUND001.SH", "FUND002.SH");
        funds.get(1).setSecurityStatus("D");
        assertThatThrownBy(() -> batchService.addAdjustLog(req)).hasMessageContaining("已终止或退市");
        verify(mapper, never()).addAdjustLog(any());
        InOrder order = inOrder(mapper);
        order.verify(mapper).queryFundListForUpdate(Arrays.asList("FUND001.SH", "FUND002.SH"));
    }

    /** 重复点击提交仍由基金单笔三十秒检测阻止。 */
    @Test
    public void submitShouldRejectRecentDuplicate() {
        // 对校验通过请求模拟近期已存在相同申请
        BatchFundAdjustReq req = buildSubmitRequest("in", "FUND001.SH");
        when(mapper.queryRecentDuplicate("FUND001.SH", 10L, "调入", "1")).thenReturn(true);
        assertThatThrownBy(() -> batchService.addAdjustLog(req)).hasMessageContaining("短时间内重复提交");
        verify(mapper, never()).addAdjustLog(any());
    }

    /** 使用真实 H2 事务验证新增公开批量入口的后组写入失败全批回滚。 */
    @Test
    public void batchEntryShouldRollbackAllLogsAndStepsWhenLaterPersistenceFails() {
        String javaVersion = System.getProperty("java.specification.version");
        int major = Integer.parseInt(javaVersion.startsWith("1.") ? javaVersion.substring(2) : javaVersion);
        Assume.assumeTrue("H2 2.3.232 持久化测试需 Java 11 以上运行时", major >= 11);
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE test_fund_log (id BIGINT PRIMARY KEY)");
            jdbc.execute("CREATE TABLE test_fund_step (log_id BIGINT)");
            AtomicLong sequence = new AtomicLong();
            doAnswer(invocation -> {
                FundAdjustLogBo log = invocation.getArgument(0);
                log.setId(sequence.incrementAndGet());
                return jdbc.update("INSERT INTO test_fund_log (id) VALUES (?)", log.getId());
            }).when(mapper).addAdjustLog(any());
            doAnswer(invocation -> {
                FundAdjustStepBo step = invocation.getArgument(0);
                if (Long.valueOf(2L).equals(step.getAdjustLogId())) {
                    throw new BizException("模拟第二只基金步骤持久化失败");
                }
                return jdbc.update("INSERT INTO test_fund_step (log_id) VALUES (?)", step.getAdjustLogId());
            }).when(mapper).addAdjustStep(any());
            TransactionInterceptor interceptor = new TransactionInterceptor(
                    new DataSourceTransactionManager(database), new AnnotationTransactionAttributeSource());
            ProxyFactory factory = new ProxyFactory(fundService);
            factory.setProxyTargetClass(true);
            factory.addAdvice(interceptor);
            ReflectionTestUtils.setField(batchService, "fundPoolAdjustService", factory.getProxy());
            // 请求经批量公开入口进入真实基金通用多单事务
            BatchFundAdjustReq req = buildSubmitRequest("in", "FUND001.SH", "FUND002.SH");
            assertThatThrownBy(() -> batchService.addAdjustLog(req)).hasMessage("模拟第二只基金步骤持久化失败");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_fund_log", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM test_fund_step", Integer.class)).isZero();
            verify(mapper, never()).addFundPoolStatus(any());
        } finally {
            database.shutdown();
        }
    }

    /** 构造支持基金且使用一般流程的目标池。 */
    private InvestmentPoolBo buildPool(Long id) {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(id);
        pool.setPoolName("基金池" + id);
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
        return pool;
    }

    /** 构造有效基金权威主档。 */
    private FundInfoBo buildFund(String code, Long id) {
        FundInfoBo fund = new FundInfoBo();
        fund.setId(id);
        fund.setFundCode(code);
        fund.setFundName("权威基金" + code);
        fund.setFundShortName("基金" + id);
        fund.setSecurityType("bond_fund");
        fund.setSecurityStatus("A");
        fund.setMarketCode("SH");
        return fund;
    }

    /** 构造多只基金共享目标池的校验请求。 */
    private BatchFundAdjustReq buildCheckRequest(String direction, String... codes) {
        BatchFundAdjustReq req = new BatchFundAdjustReq();
        req.setCurrentUserId("1");
        req.setPoolId(10L);
        req.setDirection(direction);
        List<BatchFundAdjustReq.FundItem> selected = new ArrayList<>();
        for (String code : codes) {
            BatchFundAdjustReq.FundItem fund = new BatchFundAdjustReq.FundItem();
            fund.setFundCode(code);
            selected.add(fund);
        }
        req.setFunds(selected);
        return req;
    }

    /** 将服务器完整校验结果构造成携带整批基金字段的提交请求。 */
    private BatchFundAdjustReq buildSubmitRequest(String direction, String... codes) {
        // 取得指定基金的完整手工及关系校验结果
        BatchFundAdjustReq req = buildCheckRequest(direction, codes);
        BatchFundAdjustDto checked = batchService.checkAdjust(req);
        req.setFundScore(BigDecimal.ZERO);
        req.setFundInvestmentType("stock");
        req.setNeedRiskLeaderApproval(0);
        req.setAdjusterId("1");
        req.setAdjusterName("基金研究员");
        List<BatchFundAdjustReq.AdjustItem> items = new ArrayList<>();
        for (FundAdjustCheckDto.CheckResultItem row : checked.getItems()) {
            BatchFundAdjustReq.AdjustItem item = new BatchFundAdjustReq.AdjustItem();
            BeanUtils.copyProperties(row, item);
            item.setTargetPoolName(row.getPoolName());
            if ("manual".equals(row.getItemTag()) && !row.getFlowOptions().isEmpty()) {
                FundAdjustCheckDto.FlowOption flow = row.getFlowOptions().get(0);
                item.setFlowId(flow.getFlowId());
                item.setFlowKey(flow.getFlowKey());
                item.setFlowType(flow.getFlowType());
            }
            items.add(item);
        }
        req.setItems(items);
        return req;
    }

    /** 设置单笔和批量共用的联动或互斥关系及当前在池状态。 */
    private void configureRelation(String type, List<Long> currentPools) {
        PoolRelationBo relation = new PoolRelationBo();
        relation.setPoolId(10L);
        relation.setRelationPoolId(20L);
        relation.setRelationType(type);
        when(mapper.queryAllPoolRelationList()).thenReturn(Collections.singletonList(relation));
        when(mapper.queryFundCurrentPoolIdList(anyString())).thenReturn(currentPools);
    }
}
