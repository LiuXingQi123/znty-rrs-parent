package com.znty.rrs.service;

import com.znty.rrs.common.enums.AttachmentPurpose;
import com.znty.rrs.common.enums.AttachmentCategory;

import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.SysAttachmentMapper;
import com.znty.rrs.entity.bo.SysAttachmentBo;
import com.znty.rrs.entity.bo.FundAdjustLogBo;
import com.znty.rrs.entity.sysattachment.SysAttachmentReq;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 系统附件服务测试
 */
public class SysAttachmentServiceTest {

    /** 临时目录 */
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /** 验证提交文件可以直接绑定调库日志 */
    @Test
    public void bindAttachments_ValidFileIndex_InsertsAdjustLogAttachment() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        SysAttachmentService service = buildService(mapper);
        MockMultipartFile file = new MockMultipartFile(
                "files", "信评报告.pdf", "application/pdf",
                "report".getBytes(StandardCharsets.UTF_8));
        SysAttachmentService.SubmissionFiles submissionFiles =
                service.createSubmissionFiles(Collections.singletonList(file), "1");

        service.bindAttachments(88L, Collections.singletonList(0),
                AttachmentCategory.CREDIT_REPORT_HAND.getCode(), submissionFiles);

        ArgumentCaptor<SysAttachmentBo> captor = ArgumentCaptor.forClass(SysAttachmentBo.class);
        verify(mapper).addAttachment(captor.capture());
        SysAttachmentBo attachment = captor.getValue();
        assertThat(attachment.getAttachmentCategory()).isEqualTo(AttachmentCategory.CREDIT_REPORT_HAND.getCode());
        assertThat(attachment.getNewFileName()).matches("credit_report_hand_\\d{14}_88\\.pdf");
        assertThat(attachment.getFileName()).matches("\\d{8}/credit_report_hand_\\d{14}_88\\.pdf");
        assertThat(attachment.getFileName()).endsWith("/" + attachment.getNewFileName());
    }

    /** 验证非法文件下标被拒绝 */
    @Test
    public void bindAttachments_InvalidFileIndex_ThrowsBizException() throws Exception {
        SysAttachmentService service = buildService(mock(SysAttachmentMapper.class));
        MockMultipartFile file = new MockMultipartFile(
                "files", "信评报告.pdf", "application/pdf",
                "report".getBytes(StandardCharsets.UTF_8));
        SysAttachmentService.SubmissionFiles submissionFiles =
                service.createSubmissionFiles(Collections.singletonList(file), "1");

        assertThatThrownBy(() -> service.bindAttachments(
                88L, Collections.singletonList(1),
                AttachmentCategory.CREDIT_REPORT_HAND.getCode(), submissionFiles))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("文件下标不合法");
    }

    /** 验证非法文件类型被拒绝 */
    @Test
    public void createSubmissionFiles_UnsupportedType_ThrowsBizException() throws Exception {
        SysAttachmentService service = buildService(mock(SysAttachmentMapper.class));
        MockMultipartFile file = new MockMultipartFile(
                "files", "脚本.exe", "application/octet-stream",
                "binary".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.createSubmissionFiles(
                Collections.singletonList(file), "1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的附件类型");
    }

    /** 验证单个调库日志 ID 仍兼容附件查询 */
    @Test
    public void queryAttachmentList_SingleId_QueriesSingleItemList() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        SysAttachmentService service = buildService(mapper);
        SysAttachmentReq req = new SysAttachmentReq();
        req.setAdjustLogId(88L);
        req.setBusinessDomain("bond");

        service.queryAttachmentList(req);

        verify(mapper).queryAttachmentList("ip_adjust_log", Collections.singletonList(88L));
    }

    /** 验证批量调库日志 ID 会去重并一次查询 */
    @Test
    public void queryAttachmentList_MultipleIds_DeduplicatesAndQueriesOnce() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        SysAttachmentService service = buildService(mapper);
        SysAttachmentReq req = new SysAttachmentReq();
        req.setAdjustLogIds(Arrays.asList(88L, 99L, 88L, null));
        req.setBusinessDomain("bond");

        service.queryAttachmentList(req);

        verify(mapper).queryAttachmentList("ip_adjust_log", Arrays.asList(88L, 99L));
    }

    /** 验证未提供调库日志 ID 时拒绝查询 */
    @Test
    public void queryAttachmentList_EmptyIds_ThrowsBizException() throws Exception {
        SysAttachmentService service = buildService(mock(SysAttachmentMapper.class));
        SysAttachmentReq req = new SysAttachmentReq();

        assertThatThrownBy(() -> service.queryAttachmentList(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("调库日志 ID 不能为空");
    }

    /** 验证内部报告库附件可以复制绑定为调库日志信评报告附件 */
    @Test
    public void copyReportAttachments_InReportCreditPurpose_InsertsInCreditCategory() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        SysAttachmentService service = buildService(mapper);
        SysAttachmentBo source = buildAttachment(7L, "rrs_report_in", 20L, "report_in");
        when(mapper.queryAttachmentListByIds(Collections.singletonList(7L))).thenReturn(Collections.singletonList(source));

        service.copyReportAttachments(88L, Collections.singletonList(7L),
                AttachmentPurpose.CREDIT_REPORT.getCode(), "1");

        ArgumentCaptor<SysAttachmentBo> captor = ArgumentCaptor.forClass(SysAttachmentBo.class);
        verify(mapper).addAttachment(captor.capture());
        SysAttachmentBo attachment = captor.getValue();
        assertThat(attachment.getTableName()).isEqualTo("ip_adjust_log");
        assertThat(attachment.getMainId()).isEqualTo(88L);
        assertThat(attachment.getAttachmentCategory()).isEqualTo(AttachmentCategory.CREDIT_REPORT_IN.getCode());
        assertThat(attachment.getOriginalFileName()).isEqualTo("报告附件.pdf");
        assertThat(attachment.getFileName()).isEqualTo("20260601/report.pdf");
    }

    /** 验证外部报告库附件可以复制绑定为调库日志其他材料附件 */
    @Test
    public void copyReportAttachments_OutReportMaterialPurpose_InsertsOutMaterialCategory() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        SysAttachmentService service = buildService(mapper);
        SysAttachmentBo source = buildAttachment(7L, "rrs_report_out", 20L, "report_out");
        when(mapper.queryAttachmentListByIds(Collections.singletonList(7L))).thenReturn(Collections.singletonList(source));

        service.copyReportAttachments(88L, Collections.singletonList(7L),
                AttachmentPurpose.MATERIAL.getCode(), "1");

        ArgumentCaptor<SysAttachmentBo> captor = ArgumentCaptor.forClass(SysAttachmentBo.class);
        verify(mapper).addAttachment(captor.capture());
        assertThat(captor.getValue().getAttachmentCategory()).isEqualTo(AttachmentCategory.MATERIAL_OUT.getCode());
    }

    /** 验证基金报告通过兼容重载关联基金调库表且不改变债券默认关联表 */
    @Test
    public void copyReportAttachments_FundAdjust_UsesFundTableAndCategory() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        SysAttachmentService service = buildService(mapper);
        SysAttachmentBo source = buildAttachment(7L, "rrs_report_in", 20L, "report_in");
        when(mapper.queryAttachmentListByIds(Collections.singletonList(7L))).thenReturn(Collections.singletonList(source));

        service.copyReportAttachments("ip_adjust_log_fund", 88L, Collections.singletonList(7L),
                AttachmentPurpose.CREDIT_REPORT.getCode(), "1");

        ArgumentCaptor<SysAttachmentBo> captor = ArgumentCaptor.forClass(SysAttachmentBo.class);
        verify(mapper).addAttachment(captor.capture());
        assertThat(captor.getValue().getTableName()).isEqualTo("ip_adjust_log_fund");
        assertThat(captor.getValue().getAttachmentCategory())
                .isEqualTo(AttachmentCategory.FUND_REPORT_IN.getCode());
    }

    /** 验证非报告库分类不能作为复制来源 */
    @Test
    public void copyReportAttachments_InvalidReportCategory_ThrowsBizException() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        SysAttachmentService service = buildService(mapper);
        SysAttachmentBo source = buildAttachment(7L, "rrs_report_in", 20L, "report_out");
        when(mapper.queryAttachmentListByIds(Collections.singletonList(7L))).thenReturn(Collections.singletonList(source));

        assertThatThrownBy(() -> service.copyReportAttachments(88L, Collections.singletonList(7L),
                AttachmentPurpose.CREDIT_REPORT.getCode(), "1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("附件不是报告库文件");
    }

    /** 验证内部报告限制接受有效内部报告库附件 */
    @Test
    public void validateCreditReportSources_InternalReport_Passes() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        SysAttachmentService service = buildService(mapper);
        SysAttachmentBo source = buildAttachment(7L, "rrs_report_in", 20L, "report_in");
        when(mapper.queryAttachmentListByIds(Collections.singletonList(7L)))
                .thenReturn(Collections.singletonList(source));

        service.validateCreditReportSources(Collections.singletonList(7L), true);

        verify(mapper).queryAttachmentListByIds(Collections.singletonList(7L));
    }

    /** 验证外部报告不能满足内部报告限制 */
    @Test
    public void validateCreditReportSources_ExternalReportForInternal_ThrowsBizException() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        SysAttachmentService service = buildService(mapper);
        SysAttachmentBo source = buildAttachment(7L, "rrs_report_out", 20L, "report_out");
        when(mapper.queryAttachmentListByIds(Collections.singletonList(7L)))
                .thenReturn(Collections.singletonList(source));

        assertThatThrownBy(() -> service.validateCreditReportSources(Collections.singletonList(7L), true))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("内部研究报告");
    }

    /** 验证无效或已删除的报告附件 ID 被拒绝 */
    @Test
    public void validateCreditReportSources_MissingAttachment_ThrowsBizException() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        SysAttachmentService service = buildService(mapper);
        when(mapper.queryAttachmentListByIds(Collections.singletonList(7L))).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.validateCreditReportSources(Collections.singletonList(7L), false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无效或已删除");
    }

    /** 验证指定调库日志下的附件可以逻辑删除 */
    @Test
    public void deleteAdjustLogAttachments_ValidAttachment_DeletesByLogId() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        SysAttachmentService service = buildService(mapper);
        SysAttachmentBo attachment = buildAttachment(8L, "ip_adjust_log", 88L,
                AttachmentCategory.CREDIT_REPORT_HAND.getCode());
        when(mapper.queryAttachmentListByIds(Collections.singletonList(8L))).thenReturn(Collections.singletonList(attachment));
        when(mapper.deleteAttachmentByIdsList(88L, Collections.singletonList(8L))).thenReturn(1);

        service.deleteAdjustLogAttachments(88L, Collections.singletonList(8L));

        verify(mapper).deleteAttachmentByIdsList(88L, Collections.singletonList(8L));
    }

    /** 验证不能删除其他调库记录的附件 */
    @Test
    public void deleteAdjustLogAttachments_OtherLogAttachment_ThrowsBizException() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        SysAttachmentService service = buildService(mapper);
        SysAttachmentBo attachment = buildAttachment(8L, "ip_adjust_log", 99L,
                AttachmentCategory.CREDIT_REPORT_HAND.getCode());
        when(mapper.queryAttachmentListByIds(Collections.singletonList(8L))).thenReturn(Collections.singletonList(attachment));

        assertThatThrownBy(() -> service.deleteAdjustLogAttachments(88L, Collections.singletonList(8L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("附件不属于当前调库记录");
    }

    /** 基金转正附件继承保留六种分类与物理文件，只新增目标日志关联。 */
    @Test
    public void copyFundAdjustAttachmentsShouldPreserveCategoriesAndPhysicalFiles() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        // 构造使用临时存储目录的真实附件服务
        SysAttachmentService service = buildService(mapper);
        // 配置同池已通过的来源和代码替换目标基金日志
        configureFundCopyLogs(mapper);
        List<SysAttachmentBo> sources = new ArrayList<>();
        List<String> categories = Arrays.asList("fund_report_hand", "fund_report_in", "fund_report_out",
                "fund_material_hand", "fund_material_in", "fund_material_out");
        for (String category : categories) {
            // 构造每一种允许继承的基金报告或材料附件
            sources.add(buildAttachment((long) sources.size() + 1, "ip_adjust_log_fund", 10L, category));
        }
        when(mapper.queryFundAdjustAttachmentList(10L)).thenReturn(sources);
        when(mapper.addAttachment(any())).thenReturn(1);

        service.copyFundAdjustAttachments(10L, 20L, "2");

        ArgumentCaptor<SysAttachmentBo> copied = ArgumentCaptor.forClass(SysAttachmentBo.class);
        verify(mapper, times(6)).addAttachment(copied.capture());
        assertThat(copied.getAllValues()).extracting(SysAttachmentBo::getAttachmentCategory).containsExactlyElementsOf(categories);
        assertThat(copied.getAllValues()).extracting(SysAttachmentBo::getTableName).containsOnly("ip_adjust_log_fund");
        assertThat(copied.getAllValues()).extracting(SysAttachmentBo::getMainId).containsOnly(20L);
        assertThat(copied.getAllValues()).extracting(SysAttachmentBo::getFileName).containsOnly("20260601/report.pdf");
        assertThat(copied.getAllValues()).extracting(SysAttachmentBo::getUploaderId).containsOnly("2");
        assertThat(copied.getAllValues().get(0).getNewFileName()).isEqualTo(sources.get(0).getNewFileName());
        assertThat(copied.getAllValues().get(0).getFullUrl()).isEqualTo(sources.get(0).getFullUrl());
        assertThat(copied.getAllValues().get(0).getOriginalFileName()).isEqualTo(sources.get(0).getOriginalFileName());
        assertThat(sources).extracting(SysAttachmentBo::getMainId).containsOnly(10L);
    }

    /** 来源后续附件分类错误时全部关联都不写入，不能留下先前成功复制。 */
    @Test
    public void copyFundAdjustAttachmentsShouldValidateAllSourcesBeforeWriting() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        // 构造真实服务并配置同池已通过基金日志
        SysAttachmentService service = buildService(mapper);
        // 配置仅基金业务允许使用的来源和目标日志
        configureFundCopyLogs(mapper);
        // 构造第一个合法基金附件
        SysAttachmentBo valid = buildAttachment(1L, "ip_adjust_log_fund", 10L, "fund_report_hand");
        // 构造后续误用债券附件分类的错误来源
        SysAttachmentBo invalid = buildAttachment(2L, "ip_adjust_log_fund", 10L, "credit_report_hand");
        when(mapper.queryFundAdjustAttachmentList(10L)).thenReturn(Arrays.asList(valid, invalid));

        assertThatThrownBy(() -> service.copyFundAdjustAttachments(10L, 20L, "2"))
                .isInstanceOf(BizException.class).hasMessageContaining("分类或物理文件标识无效");
        verify(mapper, never()).addAttachment(any());
    }

    /** 不能将其他业务或其他基金日志上的附件伪造为当前基金来源。 */
    @Test
    public void copyFundAdjustAttachmentsShouldRejectWrongBusinessAndSourceLog() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        // 构造真实附件服务以核对来源关联归属
        SysAttachmentService service = buildService(mapper);
        // 配置已通过的同池来源和目标基金日志
        configureFundCopyLogs(mapper);
        // 构造业务表错误的来源附件
        SysAttachmentBo source = buildAttachment(1L, "ip_adjust_log", 10L, "fund_report_hand");
        when(mapper.queryFundAdjustAttachmentList(10L)).thenReturn(Collections.singletonList(source));
        assertThatThrownBy(() -> service.copyFundAdjustAttachments(10L, 20L, "2"))
                .isInstanceOf(BizException.class).hasMessageContaining("来源附件关联");

        source.setTableName("ip_adjust_log_fund");
        source.setMainId(99L);
        assertThatThrownBy(() -> service.copyFundAdjustAttachments(10L, 20L, "2"))
                .isInstanceOf(BizException.class).hasMessageContaining("来源附件关联");
        verify(mapper, never()).addAttachment(any());
    }

    /** 缺失目标、跨池或未通过日志都不得作为附件继承目标。 */
    @Test
    public void copyFundAdjustAttachmentsShouldRejectInvalidTarget() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        // 构造服务及已通过的来源基金日志
        SysAttachmentService service = buildService(mapper);
        // 配置基金日志，随后逐一修改目标合法性
        configureFundCopyLogs(mapper);
        when(mapper.queryFundAdjustLogById(20L)).thenReturn(null);
        assertThatThrownBy(() -> service.copyFundAdjustAttachments(10L, 20L, "2"))
                .isInstanceOf(BizException.class).hasMessageContaining("同一投资池的已通过基金日志");

        FundAdjustLogBo target = new FundAdjustLogBo();
        target.setTargetPoolId(999L);
        target.setAuditStatus("20");
        when(mapper.queryFundAdjustLogById(20L)).thenReturn(target);
        assertThatThrownBy(() -> service.copyFundAdjustAttachments(10L, 20L, "2"))
                .isInstanceOf(BizException.class).hasMessageContaining("同一投资池的已通过基金日志");
        target.setTargetPoolId(100L);
        target.setAuditStatus("00");
        assertThatThrownBy(() -> service.copyFundAdjustAttachments(10L, 20L, "2"))
                .isInstanceOf(BizException.class).hasMessageContaining("同一投资池的已通过基金日志");
        verify(mapper, never()).addAttachment(any());
    }

    /** 继承入口拒绝相同日志或缺少经办人，不能重复关联自身附件。 */
    @Test
    public void copyFundAdjustAttachmentsShouldRejectInvalidIdsAndOperator() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        // 构造真实服务核对提交级必填参数
        SysAttachmentService service = buildService(mapper);
        assertThatThrownBy(() -> service.copyFundAdjustAttachments(10L, 10L, "2"))
                .isInstanceOf(BizException.class).hasMessageContaining("不同的有效 ID");
        assertThatThrownBy(() -> service.copyFundAdjustAttachments(null, 20L, "2"))
                .isInstanceOf(BizException.class).hasMessageContaining("不同的有效 ID");
        assertThatThrownBy(() -> service.copyFundAdjustAttachments(10L, 20L, " "))
                .isInstanceOf(BizException.class).hasMessageContaining("经办人 ID 不能为空");
        verify(mapper, never()).addAttachment(any());
    }

    /**
     * 配置原临时代码已在池日志和同池正式代码替换日志。
     *
     * @param mapper 附件服务使用的模拟数据访问组件
     */
    private void configureFundCopyLogs(SysAttachmentMapper mapper) {
        FundAdjustLogBo source = new FundAdjustLogBo();
        source.setId(10L);
        source.setFundCode("TEMP001");
        source.setTargetPoolId(100L);
        source.setAuditStatus("20");
        FundAdjustLogBo target = new FundAdjustLogBo();
        target.setId(20L);
        target.setFundCode("FORMAL001");
        target.setTargetPoolId(100L);
        target.setAuditStatus("20");
        when(mapper.queryFundAdjustLogById(10L)).thenReturn(source);
        when(mapper.queryFundAdjustLogById(20L)).thenReturn(target);
    }

    /** 构建附件服务 */
    private SysAttachmentService buildService(SysAttachmentMapper mapper) throws Exception {
        SysAttachmentService service = new SysAttachmentService();
        ReflectionTestUtils.setField(service, "sysAttachmentMapper", mapper);
        ReflectionTestUtils.setField(service, "storagePath", temporaryFolder.getRoot().getAbsolutePath());
        service.initializeStorage();
        return service;
    }

    /** 构建附件实体 */
    private SysAttachmentBo buildAttachment(Long id, String tableName, Long mainId, String category) {
        SysAttachmentBo attachment = new SysAttachmentBo();
        attachment.setId(id);
        attachment.setTableName(tableName);
        attachment.setMainId(mainId);
        attachment.setAttachmentCategory(category);
        attachment.setFileType("pdf");
        attachment.setOriginalFileName("报告附件.pdf");
        attachment.setNewFileName("report.pdf");
        attachment.setFileSize(1024L);
        attachment.setContentType("application/pdf");
        attachment.setFullUrl("/api/v1/attachments/downloadAttachment");
        attachment.setFileName("20260601/report.pdf");
        attachment.setUploaderId("1");
        return attachment;
    }
}
