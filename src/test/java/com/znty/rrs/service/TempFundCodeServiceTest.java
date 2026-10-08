package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.znty.rrs.entity.bo.FundAdjustLogBo;
import com.znty.rrs.entity.bo.FundInfoBo;
import com.znty.rrs.entity.bo.TempFundCodeBo;
import com.znty.rrs.entity.bo.TempFundCodeUpdateLogBo;
import com.znty.rrs.entity.tempfundcode.TempFundCodeDto;
import com.znty.rrs.entity.tempfundcode.TempFundCodeReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FundPoolAdjustMapper;
import com.znty.rrs.mapper.TempFundCodeMapper;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 基金临时代码单元测试，验证明确输入、状态门禁和业务分叉。 */
public class TempFundCodeServiceTest {
    /** 被测人工管理服务 */
    private TempFundCodeService service;
    /** 登记及业务引用替身 */
    private TempFundCodeMapper mapper;
    /** 主档锁及基金日志替身 */
    private FundPoolAdjustMapper fundMapper;
    /** 附件复制替身 */
    private SysAttachmentService attachments;
    /** 当前有效临时登记 */
    private TempFundCodeBo record;
    /** 真实主档形式的正式选项 */
    private TempFundCodeDto.FormalFundOption formal;

    /** 预置正常数据和成功写入契约，异常用例只覆盖其目标条件。 */
    @Before
    public void setUp() {
        mapper = mock(TempFundCodeMapper.class);
        fundMapper = mock(FundPoolAdjustMapper.class);
        attachments = mock(SysAttachmentService.class);
        service = new TempFundCodeService();
        ReflectionTestUtils.setField(service, "tempFundCodeMapper", mapper);
        ReflectionTestUtils.setField(service, "fundPoolAdjustMapper", fundMapper);
        ReflectionTestUtils.setField(service, "sysAttachmentService", attachments);
        TempFundCodeDto.TypeOption type = new TempFundCodeDto.TypeOption();
        type.setSecurityType("etf_fund");
        type.setSecurityTypeName("ETF基金");
        when(mapper.queryFundTypeList()).thenReturn(Collections.singletonList(type));
        when(mapper.queryFundTypeListForUpdate()).thenReturn(Collections.singletonList(type));
        record = new TempFundCodeBo();
        record.setId(1L);
        record.setTempFundCode("TMP001");
        record.setTempFundShortName("临时基金");
        record.setTempMarketCode("OTC");
        record.setTempSecurityType("etf_fund");
        record.setStatus("temporary");
        record.setIsDeleted(0);
        when(mapper.queryTempFundCodeById(1L)).thenReturn(record);
        when(mapper.queryTempFundCodeByIdForUpdate(1L)).thenReturn(record);
        FundInfoBo temporary = new FundInfoBo();
        temporary.setFundCode("TMP001");
        temporary.setId(2L);
        FundInfoBo official = new FundInfoBo();
        official.setFundCode("FORMAL001");
        official.setId(1L);
        when(fundMapper.queryFundListForUpdate(anyList())).thenReturn(Arrays.asList(official, temporary));
        formal = new TempFundCodeDto.FormalFundOption();
        formal.setFundCode("FORMAL001");
        formal.setFundName("正式基金全称");
        formal.setFundShortName("正式基金简称");
        formal.setMarketCode("SSE");
        formal.setSecurityType("etf_fund");
        when(mapper.queryFormalFundByCode("FORMAL001")).thenReturn(formal);
        when(mapper.editTempFundCodeState(any(TempFundCodeBo.class))).thenReturn(1);
        when(mapper.editTempFundInfoToDisabled(anyString(), any(Date.class))).thenReturn(1);
        when(mapper.addTempFundCodeEvent(anyLong(), anyString(), anyString(), any(Date.class))).thenReturn(1);
        when(mapper.addTempFundInfo(any(TempFundCodeBo.class))).thenReturn(1);
        when(mapper.addTempFundCodeUpdateLog(any(TempFundCodeUpdateLogBo.class))).thenReturn(1);
        doAnswer(call -> {
            call.getArgument(0, TempFundCodeBo.class).setId(2L);
            return 1;
        }).when(mapper).addTempFundCode(any(TempFundCodeBo.class));
        doAnswer(call -> {
            FundAdjustLogBo log = call.getArgument(0, FundAdjustLogBo.class);
            log.setId("调出".equals(log.getAdjustMode()) ? 101L : 102L);
            return 1;
        }).when(fundMapper).addAdjustLog(any(FundAdjustLogBo.class));
        when(fundMapper.addFundPoolStatus(any(FundAdjustLogBo.class))).thenReturn(1);
        when(mapper.deleteTempPoolStatusById(anyLong(), any(Date.class))).thenReturn(1);
    }

    /** 清理模拟分页接口留下的线程上下文。 */
    @After
    public void tearDown() {
        PageHelper.clearPage();
    }

    /** 四项输入去除首尾空格，占位和审计使用同一登记信息。 */
    @Test
    public void shouldCreatePlaceholderFromFourFieldsAndAuditOperator() {
        // 构造正常新增请求
        TempFundCodeReq req = addRequest();
        req.setTempFundCode(" TMP001 ");
        req.setTempFundShortName(" 临时基金 ");
        service.addTempFundCode(req);
        ArgumentCaptor<TempFundCodeBo> capture = ArgumentCaptor.forClass(TempFundCodeBo.class);
        verify(mapper).addTempFundInfo(capture.capture());
        assertThat(capture.getValue().getTempFundCode()).isEqualTo("TMP001");
        assertThat(capture.getValue().getTempFundShortName()).isEqualTo("临时基金");
        assertThat(capture.getValue().getStatus()).isEqualTo("temporary");
        assertThat(capture.getValue().getOprtSource()).isEqualTo("manual");
        verify(mapper).queryFundTypeListForUpdate();
        verify(mapper).addTempFundCodeEvent(eq(2L), eq("1"), eq("INSERT"), any(Date.class));
    }

    /** 空值、超长、非法市场与非基金字典类型明确失败且不写登记。 */
    @Test
    public void shouldRejectMissingOversizedAndInvalidFields() {
        // 构造新增请求并依次覆盖错误字段
        TempFundCodeReq req = addRequest();
        req.setTempFundShortName(" ");
        assertThatThrownBy(() -> service.addTempFundCode(req)).hasMessageContaining("基金简称不能为空");
        req.setTempFundShortName(String.join("", Collections.nCopies(101, "名")));
        assertThatThrownBy(() -> service.addTempFundCode(req)).hasMessageContaining("长度不能超过100");
        req.setTempFundShortName("临时基金");
        req.setTempMarketCode("BAD");
        assertThatThrownBy(() -> service.addTempFundCode(req)).hasMessageContaining("市场编码无效");
        req.setTempMarketCode("SSE");
        req.setTempSecurityType("mtn");
        assertThatThrownBy(() -> service.addTempFundCode(req)).hasMessageContaining("有效基金字典");
        req.setTempSecurityType("etf_fund");
        req.setOperatorId(null);
        assertThatThrownBy(() -> service.addTempFundCode(req)).hasMessageContaining("操作人 ID不能为空");
        verify(mapper, never()).addTempFundCode(any(TempFundCodeBo.class));
    }

    /** 原主档存在或取消、已更新登记占用代码时不得覆盖新增。 */
    @Test
    public void shouldRejectCodesAlreadyUsedByMasterOrRegistration() {
        when(mapper.queryFundCodeReferenceIdListForUpdate("TMP001")).thenReturn(Collections.singletonList(8L));
        // 同码主档存在时直接失败
        assertThatThrownBy(() -> service.addTempFundCode(addRequest())).hasMessageContaining("已存在于基金主档");
        when(mapper.queryFundCodeReferenceIdListForUpdate("TMP001")).thenReturn(Collections.emptyList());
        when(mapper.queryTempFundCodeCount("TMP001")).thenReturn(1);
        // 同码有效登记存在时直接失败
        assertThatThrownBy(() -> service.addTempFundCode(addRequest())).hasMessageContaining("已存在于登记");
        verify(mapper, never()).addTempFundCode(any(TempFundCodeBo.class));
    }

    /** 使用正式权威信息仅更新在途四字段，无在池记录时不生成出入库日志。 */
    @Test
    public void shouldUseFormalMasterAndOnlyReplacePendingReferences() {
        FundAdjustLogBo pending = new FundAdjustLogBo();
        pending.setId(10L);
        when(mapper.queryPendingAdjustLogListForUpdate("TMP001")).thenReturn(Collections.singletonList(pending));
        when(mapper.editPendingFundReference(any(TempFundCodeBo.class), anyList())).thenReturn(1);
        // 正式信息仅从 Mapper 的权威主档选项读取
        service.editTempFundCodeToUpdated(operation("FORMAL001"));
        ArgumentCaptor<TempFundCodeBo> capture = ArgumentCaptor.forClass(TempFundCodeBo.class);
        verify(mapper).editPendingFundReference(capture.capture(), eq(Collections.singletonList(10L)));
        assertThat(capture.getValue().getFundName()).isEqualTo("正式基金全称");
        assertThat(capture.getValue().getFundShortName()).isEqualTo("正式基金简称");
        assertThat(capture.getValue().getMarketCode()).isEqualTo("SSE");
        assertThat(capture.getValue().getSecurityType()).isEqualTo("etf_fund");
        verify(fundMapper, never()).addAdjustLog(any(FundAdjustLogBo.class));
        verify(mapper).editTempFundInfoToDisabled(eq("TMP001"), any(Date.class));
        verify(mapper).addTempFundCodeUpdateLog(any(TempFundCodeUpdateLogBo.class));
    }

    /** 已在池正式调入继承参数和原因意见，附件指向新入池日志。 */
    @Test
    public void shouldKeepPoolParametersAndCopyOriginalAttachments() {
        // 准备完整在池引用
        TempFundCodeDto.PoolStatusReference pool = poolReference();
        when(mapper.queryActivePoolStatusListForUpdate("TMP001")).thenReturn(Collections.singletonList(pool));
        // 转正需要一条临时调出和一条正式调入
        service.editTempFundCodeToUpdated(operation("FORMAL001"));
        ArgumentCaptor<FundAdjustLogBo> capture = ArgumentCaptor.forClass(FundAdjustLogBo.class);
        verify(fundMapper, times(2)).addAdjustLog(capture.capture());
        FundAdjustLogBo in = capture.getAllValues().get(1);
        assertThat(in.getFundCode()).isEqualTo("FORMAL001");
        assertThat(in.getFundScore()).isEqualByComparingTo("72.125");
        assertThat(in.getFundInvestmentType()).isEqualTo("stock");
        assertThat(in.getNeedRiskLeaderApproval()).isEqualTo(0);
        assertThat(in.getAdjustReason()).isEqualTo("原原因");
        assertThat(in.getAdjustAdvice()).isEqualTo("原意见");
        assertThat(in.getAuditStatus()).isEqualTo("20");
        assertThat(in.getAdjustBatchNo()).startsWith("FUND");
        verify(attachments).copyFundAdjustAttachments(90L, 102L, "1");
    }

    /** 正式已在同池时只产生调出，正式记录及附件不被覆盖。 */
    @Test
    public void shouldOnlyRemoveTemporaryWhenFormalAlreadyInSamePool() {
        // 准备原在池状态
        when(mapper.queryActivePoolStatusListForUpdate("TMP001")).thenReturn(Collections.singletonList(poolReference()));
        when(mapper.queryFormalPoolStatusCount("FORMAL001", 5L)).thenReturn(1);
        // 同池正式已存在时转正只调出临时成员
        service.editTempFundCodeToUpdated(operation("FORMAL001"));
        verify(fundMapper).addAdjustLog(any(FundAdjustLogBo.class));
        verify(fundMapper, never()).addFundPoolStatus(any(FundAdjustLogBo.class));
        verify(attachments, never()).copyFundAdjustAttachments(anyLong(), anyLong(), anyString());
    }

    /** 锁等待期间登记已更新或取消时，三个操作都必须被状态门禁阻止。 */
    @Test
    public void shouldRejectOperationsAfterStateChangedUnderLock() {
        record.setStatus("updated");
        // 锁后重新验证状态，禁止重复转正
        assertThatThrownBy(() -> service.editTempFundCodeToUpdated(operation("FORMAL001"))).hasMessageContaining("只有临时状态");
        // 锁后重新验证状态，禁止取消
        assertThatThrownBy(() -> service.editTempFundCodeToCancelled(operation(null))).hasMessageContaining("只有临时状态");
        // 已更新登记也不允许删除
        assertThatThrownBy(() -> service.deleteTempFundCode(operation(null))).hasMessageContaining("只有临时状态");
        record.setStatus("cancelled");
        // 已取消登记不允许删除
        assertThatThrownBy(() -> service.deleteTempFundCode(operation(null))).hasMessageContaining("只有临时状态");
        verify(mapper, never()).editTempFundCodeState(any(TempFundCodeBo.class));
    }

    /** 同码、无有效正式主档和残缺正式信息都不可转正。 */
    @Test
    public void shouldRejectInvalidFormalMaster() {
        // 临时和正式代码不可相同
        assertThatThrownBy(() -> service.editTempFundCodeToUpdated(operation("TMP001"))).hasMessageContaining("不能与临时代码相同");
        when(mapper.queryFormalFundByCode("FORMAL001")).thenReturn(null);
        // 终止或占位代码不能冒充正式主档
        assertThatThrownBy(() -> service.editTempFundCodeToUpdated(operation("FORMAL001"))).hasMessageContaining("正式基金不存在");
        when(mapper.queryFormalFundByCode("FORMAL001")).thenReturn(formal);
        formal.setFundShortName(null);
        // 主档残缺时不推断简称
        assertThatThrownBy(() -> service.editTempFundCodeToUpdated(operation("FORMAL001"))).hasMessageContaining("正式基金简称");
        verify(mapper, never()).editTempFundCodeState(any(TempFundCodeBo.class));
    }

    /** 取消有引用仍成功，保留业务记录且禁用主档。 */
    @Test
    public void shouldCancelUsedCodeWithoutCheckingReferenceGuard() {
        when(mapper.queryCoreReferenceCount("TMP001")).thenReturn(8);
        // 已有业务引用仍允许取消，不检查删除操作的引用门禁
        service.editTempFundCodeToCancelled(operation(null));
        assertThat(record.getStatus()).isEqualTo("cancelled");
        verify(mapper, never()).queryCoreReferenceCount(anyString());
        verify(mapper).editTempFundInfoToDisabled(eq("TMP001"), any(Date.class));
        verify(fundMapper, never()).addAdjustLog(any(FundAdjustLogBo.class));
    }

    /** 有核心引用拒绝删除，无引用只软删登记且保留主档。 */
    @Test
    public void shouldDeleteOnlyUnreferencedRegistrationAndKeepMaster() {
        when(mapper.queryCoreReferenceCount("TMP001")).thenReturn(1);
        // 核心引用为任何未删除日志或池状态
        assertThatThrownBy(() -> service.deleteTempFundCode(operation(null))).hasMessageContaining("无法删除");
        when(mapper.queryCoreReferenceCount("TMP001")).thenReturn(0);
        // 无引用时仅软删除临时代码登记
        service.deleteTempFundCode(operation(null));
        assertThat(record.getStatus()).isEqualTo("deleted");
        assertThat(record.getIsDeleted()).isEqualTo(1);
        verify(mapper, never()).editTempFundInfoToDisabled(anyString(), any(Date.class));
        verify(mapper).addTempFundCodeEvent(eq(1L), eq("1"), eq("DELETE"), any(Date.class));
    }

    /** 条件写入失败时禁止继续改业务引用和占位主档。 */
    @Test
    public void shouldStopWhenConditionalStateUpdateFails() {
        when(mapper.editTempFundCodeState(any(TempFundCodeBo.class))).thenReturn(0);
        // 状态条件写入必须严格影响一条记录
        assertThatThrownBy(() -> service.editTempFundCodeToUpdated(operation("FORMAL001"))).hasMessageContaining("状态已变化");
        verify(mapper, never()).queryPendingAdjustLogListForUpdate(anyString());
        verify(mapper, never()).editTempFundInfoToDisabled(anyString(), any(Date.class));
    }

    /**
     * 构造有效四字段新增请求。
     *
     * @return 人工新增参数
     */
    private TempFundCodeReq addRequest() {
        TempFundCodeReq req = new TempFundCodeReq();
        req.setTempFundCode("TMP001");
        req.setTempFundShortName("临时基金");
        req.setTempMarketCode("OTC");
        req.setTempSecurityType("etf_fund");
        req.setOperatorId("1");
        return req;
    }

    /**
     * 构造状态操作请求。
     *
     * @param code 正式基金代码，取消或删除时为空
     * @return 操作定位和经办人
     */
    private TempFundCodeReq operation(String code) {
        TempFundCodeReq req = new TempFundCodeReq();
        req.setId(1L);
        req.setFundCode(code);
        req.setOperatorId("1");
        return req;
    }

    /**
     * 构造具有原日志关联的完整在池状态。
     *
     * @return 原业务信息
     */
    private TempFundCodeDto.PoolStatusReference poolReference() {
        TempFundCodeDto.PoolStatusReference pool = new TempFundCodeDto.PoolStatusReference();
        pool.setPoolStatusId(20L);
        pool.setSourceAdjustLogId(90L);
        pool.setFundCode("TMP001");
        pool.setFundName("临时基金");
        pool.setFundShortName("临时基金");
        pool.setSecurityType("etf_fund");
        pool.setFundScore(new BigDecimal("72.125"));
        pool.setFundInvestmentType("stock");
        pool.setNeedRiskLeaderApproval(0);
        pool.setTargetPoolId(5L);
        pool.setTargetPoolName("基金池");
        pool.setAdjusterId("2");
        pool.setAdjusterName("原发起人");
        pool.setAdjustReason("原原因");
        pool.setAdjustAdvice("原意见");
        return pool;
    }
}
