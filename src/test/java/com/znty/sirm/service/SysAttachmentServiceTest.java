package com.znty.sirm.service;

import com.znty.sirm.exception.BizException;
import com.znty.sirm.mapper.SysAttachmentMapper;
import com.znty.sirm.model.SysAttachmentBo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
                service.createSubmissionFiles(Collections.singletonList(file), "1001");

        service.bindAttachments(88L, Collections.singletonList(0),
                SysAttachmentService.CATEGORY_CREDIT_REPORT, submissionFiles);

        ArgumentCaptor<SysAttachmentBo> captor = ArgumentCaptor.forClass(SysAttachmentBo.class);
        verify(mapper).addAttachment(captor.capture());
        SysAttachmentBo attachment = captor.getValue();
        assertThat(attachment.getNewFileName()).matches("credit_report_\\d{14}_88\\.pdf");
        assertThat(attachment.getFileName()).matches("\\d{8}/credit_report_\\d{14}_88\\.pdf");
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
                service.createSubmissionFiles(Collections.singletonList(file), "1001");

        assertThatThrownBy(() -> service.bindAttachments(
                88L, Collections.singletonList(1),
                SysAttachmentService.CATEGORY_CREDIT_REPORT, submissionFiles))
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
                Collections.singletonList(file), "1001"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的附件类型");
    }

    /** 验证报告库附件可以复制绑定为调库日志附件 */
    @Test
    public void copyReportAttachments_ReportFile_InsertsAdjustLogAttachment() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        SysAttachmentService service = buildService(mapper);
        SysAttachmentBo source = buildAttachment(7L, "sirm_report_in", 20L, "report_file");
        when(mapper.queryAttachmentListByIds(Collections.singletonList(7L))).thenReturn(Collections.singletonList(source));

        service.copyReportAttachments(88L, Collections.singletonList(7L),
                SysAttachmentService.CATEGORY_CREDIT_REPORT, "1001");

        ArgumentCaptor<SysAttachmentBo> captor = ArgumentCaptor.forClass(SysAttachmentBo.class);
        verify(mapper).addAttachment(captor.capture());
        SysAttachmentBo attachment = captor.getValue();
        assertThat(attachment.getTableName()).isEqualTo("ip_adjust_log");
        assertThat(attachment.getMainId()).isEqualTo(88L);
        assertThat(attachment.getAttachmentCategory()).isEqualTo(SysAttachmentService.CATEGORY_CREDIT_REPORT);
        assertThat(attachment.getOriginalFileName()).isEqualTo("报告附件.pdf");
        assertThat(attachment.getFileName()).isEqualTo("20260601/report.pdf");
    }

    /** 验证指定调库日志下的附件可以逻辑删除 */
    @Test
    public void deleteAdjustLogAttachments_ValidAttachment_DeletesByLogId() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        SysAttachmentService service = buildService(mapper);
        SysAttachmentBo attachment = buildAttachment(8L, "ip_adjust_log", 88L,
                SysAttachmentService.CATEGORY_CREDIT_REPORT);
        when(mapper.queryAttachmentListByIds(Collections.singletonList(8L))).thenReturn(Collections.singletonList(attachment));
        when(mapper.deleteAttachmentByIds(88L, Collections.singletonList(8L))).thenReturn(1);

        service.deleteAdjustLogAttachments(88L, Collections.singletonList(8L));

        verify(mapper).deleteAttachmentByIds(88L, Collections.singletonList(8L));
    }

    /** 验证不能删除其他调库记录的附件 */
    @Test
    public void deleteAdjustLogAttachments_OtherLogAttachment_ThrowsBizException() throws Exception {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        SysAttachmentService service = buildService(mapper);
        SysAttachmentBo attachment = buildAttachment(8L, "ip_adjust_log", 99L,
                SysAttachmentService.CATEGORY_CREDIT_REPORT);
        when(mapper.queryAttachmentListByIds(Collections.singletonList(8L))).thenReturn(Collections.singletonList(attachment));

        assertThatThrownBy(() -> service.deleteAdjustLogAttachments(88L, Collections.singletonList(8L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("附件不属于当前调库记录");
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
        attachment.setUploaderId("1001");
        return attachment;
    }
}
