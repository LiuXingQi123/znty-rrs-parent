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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    /** 构建附件服务 */
    private SysAttachmentService buildService(SysAttachmentMapper mapper) throws Exception {
        SysAttachmentService service = new SysAttachmentService();
        ReflectionTestUtils.setField(service, "sysAttachmentMapper", mapper);
        ReflectionTestUtils.setField(service, "storagePath", temporaryFolder.getRoot().getAbsolutePath());
        service.initializeStorage();
        return service;
    }
}
