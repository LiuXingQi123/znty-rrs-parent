package com.znty.rrs.service;

import com.znty.rrs.common.enums.AttachmentPurpose;
import com.znty.rrs.common.enums.AttachmentCategory;

import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.SysAttachmentMapper;
import com.znty.rrs.entity.bo.SysAttachmentBo;
import com.znty.rrs.entity.sysattachment.SysAttachmentDto;
import com.znty.rrs.entity.sysattachment.SysAttachmentReq;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 系统附件业务服务
 */
@Service
public class SysAttachmentService {

    /** 调库日志表名称 */
    private static final String ADJUST_LOG_TABLE = "ip_adjust_log";

    /** 内部报告库附件分类 */
    private static final String CATEGORY_REPORT_IN = "report_in";

    /** 外部报告库附件分类 */
    private static final String CATEGORY_REPORT_OUT = "report_out";

    /** 支持的文件扩展名 */
    private static final Set<String> ALLOWED_FILE_TYPES =
            new HashSet<>(Arrays.asList("xls", "xlsx", "pdf", "doc", "docx", "json"));

    /** 日期目录格式 */
    private static final DateTimeFormatter DATE_DIRECTORY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    /** 附件文件名时间格式 */
    private static final DateTimeFormatter FILE_NAME_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 附件数据访问组件 */
    @Resource
    private SysAttachmentMapper sysAttachmentMapper;

    /** 附件存储根目录 */
    @Value("${rrs.attachment.storage-path:D:/data/uploads}")
    private String storagePath;

    /** 初始化附件存储目录 */
    @PostConstruct
    public void initializeStorage() {
        try {
            // 获取正式文件目录
            Files.createDirectories(resolveFileRoot());
        } catch (IOException e) {
            throw new IllegalStateException("初始化附件存储目录失败：" + storagePath, e);
        }
    }

    /** 创建本次提交的文件上下文 */
    public SubmissionFiles createSubmissionFiles(List<MultipartFile> files, String uploaderId) {
        if (uploaderId == null || uploaderId.trim().isEmpty()) {
            throw new BizException("上传人 ID 不能为空");
        }
        List<MultipartFile> fileList = files == null ? new ArrayList<MultipartFile>() : files;
        for (MultipartFile file : fileList) {
            // 校验单个提交文件
            validateFile(file);
        }
        return new SubmissionFiles(fileList, uploaderId);
    }

    /** 将本次提交文件绑定到调库日志 */
    public void bindAttachments(Long adjustLogId, List<Integer> fileIndexes,
                                String attachmentCategory, SubmissionFiles submissionFiles) {
        if (fileIndexes == null || fileIndexes.isEmpty()) {
            return;
        }
        if (adjustLogId == null) {
            throw new BizException("绑定附件失败：调库日志 ID 不能为空");
        }
        if (submissionFiles == null) {
            throw new BizException("绑定附件失败：提交文件上下文不能为空");
        }
        for (Integer fileIndex : new LinkedHashSet<>(fileIndexes)) {
            StoredFile storedFile = submissionFiles.resolveStoredFile(fileIndex, attachmentCategory, adjustLogId);
            SysAttachmentBo bo = new SysAttachmentBo();
            bo.setTableName(ADJUST_LOG_TABLE);
            bo.setMainId(adjustLogId);
            bo.setAttachmentCategory(attachmentCategory);
            bo.setFileType(storedFile.fileType);
            bo.setOriginalFileName(storedFile.originalFileName);
            bo.setNewFileName(storedFile.newFileName);
            bo.setFileSize(storedFile.fileSize);
            bo.setContentType(storedFile.contentType);
            bo.setFullUrl("/api/v1/attachments/downloadAttachment");
            bo.setFileName(storedFile.relativeFileName);
            bo.setUploaderId(submissionFiles.uploaderId);
            sysAttachmentMapper.addAttachment(bo);
        }
    }

    /** 将报告库附件复制绑定到调库日志 */
    public void copyReportAttachments(Long adjustLogId, List<Long> sourceAttachmentIds, String attachmentPurpose,
                                      String uploaderId) {
        if (sourceAttachmentIds == null || sourceAttachmentIds.isEmpty()) {
            return;
        }
        if (adjustLogId == null) {
            throw new BizException("复制报告附件失败：调库日志 ID 不能为空");
        }
        if (!AttachmentPurpose.CREDIT_REPORT.getCode().equals(attachmentPurpose) && !AttachmentPurpose.MATERIAL.getCode().equals(attachmentPurpose)) {
            throw new BizException("复制报告附件失败：附件分类不合法");
        }
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(sourceAttachmentIds));
        List<SysAttachmentBo> sourceAttachments = sysAttachmentMapper.queryAttachmentListByIds(distinctIds);
        sourceAttachments = sourceAttachments == null ? new ArrayList<SysAttachmentBo>() : sourceAttachments;
        if (sourceAttachments.size() != distinctIds.size()) {
            throw new BizException("复制报告附件失败：存在无效报告附件 ID");
        }
        for (SysAttachmentBo source : sourceAttachments) {
            // 校验复制来源必须为报告库附件
            validateReportSourceAttachment(source);
            // 根据报告库来源解析落库分类
            String attachmentCategory = resolveAdjustLogReportCategory(source, attachmentPurpose);
            SysAttachmentBo bo = new SysAttachmentBo();
            bo.setTableName(ADJUST_LOG_TABLE);
            bo.setMainId(adjustLogId);
            bo.setAttachmentCategory(attachmentCategory);
            bo.setFileType(source.getFileType());
            bo.setOriginalFileName(source.getOriginalFileName());
            bo.setNewFileName(source.getNewFileName());
            bo.setFileSize(source.getFileSize());
            bo.setContentType(source.getContentType());
            bo.setFullUrl("/api/v1/attachments/downloadAttachment");
            bo.setFileName(source.getFileName());
            bo.setUploaderId(uploaderId);
            sysAttachmentMapper.addAttachment(bo);
        }
    }

    /** 逻辑删除指定调库日志下的附件 */
    public void deleteAdjustLogAttachments(Long adjustLogId, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        if (adjustLogId == null) {
            throw new BizException("删除附件失败：调库日志 ID 不能为空");
        }
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(attachmentIds));
        List<SysAttachmentBo> attachments = sysAttachmentMapper.queryAttachmentListByIds(distinctIds);
        attachments = attachments == null ? new ArrayList<SysAttachmentBo>() : attachments;
        if (attachments.size() != distinctIds.size()) {
            throw new BizException("删除附件失败：存在无效附件 ID");
        }
        for (SysAttachmentBo attachment : attachments) {
            if (!ADJUST_LOG_TABLE.equals(attachment.getTableName())
                    || !adjustLogId.equals(attachment.getMainId())) {
                throw new BizException("删除附件失败：附件不属于当前调库记录，附件 ID：" + attachment.getId());
            }
        }
        int updated = sysAttachmentMapper.deleteAttachmentByIdsList(adjustLogId, distinctIds);
        if (updated != distinctIds.size()) {
            throw new BizException("删除附件失败：附件状态已变化，请刷新后重试");
        }
    }

    /** 按调库日志 ID 查询附件列表 */
    public List<SysAttachmentDto> queryAttachmentList(SysAttachmentReq req) {
        Long adjustLogId = req.getAdjustLogId();
        if (adjustLogId == null) {
            throw new BizException("调库日志 ID 不能为空");
        }
        return sysAttachmentMapper.queryAttachmentList(adjustLogId);
    }

    /**
     * 查询指定调库记录下手工上传的信评报告附件。
     *
     * @param adjustLogId 调库日志 ID
     */
    public List<SysAttachmentBo> queryHandCreditReportAttachments(Long adjustLogId) {
        if (adjustLogId == null) {
            throw new BizException("调库日志 ID 不能为空");
        }
        return sysAttachmentMapper.queryHandCreditReportAttachments(adjustLogId);
    }

    /**
     * 将手工上传的信评报告附件复制为内部报告库附件（table_name=rrs_report_in，attachment_category=report_in）。
     * 物理文件信息原样复用，使报告库附件指向同一物理文件、可下载。
     *
     * @param reportId 内部报告 ID
     * @param sources  待复制的手工上传信评报告附件列表
     */
    public void bindReportFileAttachments(Long reportId, List<SysAttachmentBo> sources) {
        if (sources == null || sources.isEmpty()) {
            return;
        }
        if (reportId == null) {
            throw new BizException("绑定报告附件失败：内部报告 ID 不能为空");
        }
        for (SysAttachmentBo source : sources) {
            SysAttachmentBo bo = new SysAttachmentBo();
            bo.setTableName("rrs_report_in");
            bo.setMainId(reportId);
            bo.setAttachmentCategory(CATEGORY_REPORT_IN);
            bo.setFileType(source.getFileType());
            bo.setOriginalFileName(source.getOriginalFileName());
            bo.setNewFileName(source.getNewFileName());
            bo.setFileSize(source.getFileSize());
            bo.setContentType(source.getContentType());
            bo.setFullUrl(source.getFullUrl());
            bo.setFileName(source.getFileName());
            bo.setUploaderId(source.getUploaderId());
            sysAttachmentMapper.addAttachment(bo);
        }
    }

    /** 下载附件 */
    public DownloadFile downloadAttachment(SysAttachmentReq req) {
        Long id = req.getId();
        if (id == null) {
            throw new BizException("附件 ID 不能为空");
        }
        SysAttachmentBo attachment = sysAttachmentMapper.queryAttachmentById(id);
        if (attachment == null) {
            throw new BizException("附件不存在或已删除，附件 ID：" + id);
        }
        // 获取附件存储根目录
        Path filePath = resolveStorageRoot().resolve(attachment.getFileName()).normalize();
        // 获取附件存储根目录
        validatePathInRoot(filePath, resolveStorageRoot());
        if (!Files.isRegularFile(filePath)) {
            throw new BizException("附件文件不存在，附件 ID：" + id);
        }

        try {
            byte[] content = Files.readAllBytes(filePath);
            String contentType = attachment.getContentType() == null || attachment.getContentType().isEmpty()
                    ? "application/octet-stream" : attachment.getContentType();
            Long fileSize = attachment.getFileSize() == null ? filePath.toFile().length() : attachment.getFileSize();
            return new DownloadFile(attachment.getOriginalFileName(), contentType, fileSize, content);
        } catch (IOException e) {
            throw new BizException("附件下载失败，附件 ID：" + id);
        }
    }

    /** 校验提交文件 */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("提交附件不能为空");
        }
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.trim().isEmpty()) {
            throw new BizException("上传文件名称不能为空");
        }
        resolveFileType(Paths.get(originalFileName).getFileName().toString());
    }

    /** 校验复制来源必须为报告库附件 */
    private void validateReportSourceAttachment(SysAttachmentBo attachment) {
        String tableName = attachment.getTableName();
        boolean inReport = "rrs_report_in".equals(tableName)
                && CATEGORY_REPORT_IN.equals(attachment.getAttachmentCategory());
        boolean outReport = "rrs_report_out".equals(tableName)
                && CATEGORY_REPORT_OUT.equals(attachment.getAttachmentCategory());
        if (!inReport && !outReport) {
            throw new BizException("复制报告附件失败：附件不是报告库文件，附件 ID：" + attachment.getId());
        }
    }

    /** 根据报告库来源和调库附件用途解析落库分类 */
    private String resolveAdjustLogReportCategory(SysAttachmentBo source, String attachmentPurpose) {
        boolean inReport = "rrs_report_in".equals(source.getTableName());
        if (AttachmentPurpose.CREDIT_REPORT.getCode().equals(attachmentPurpose)) {
            return inReport ? AttachmentCategory.CREDIT_REPORT_IN.getCode() : AttachmentCategory.CREDIT_REPORT_OUT.getCode();
        }
        return inReport ? AttachmentCategory.MATERIAL_IN.getCode() : AttachmentCategory.MATERIAL_OUT.getCode();
    }

    /** 解析并校验文件类型 */
    private String resolveFileType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            throw new BizException("上传文件必须包含有效扩展名");
        }
        String fileType = fileName.substring(dotIndex + 1).toLowerCase();
        if (!ALLOWED_FILE_TYPES.contains(fileType)) {
            throw new BizException("不支持的附件类型：" + fileType);
        }
        return fileType;
    }

    /** 保存提交文件并返回存储信息 */
    private StoredFile storeFile(MultipartFile file, String attachmentCategory, Long adjustLogId) {
        String originalFileName = Paths.get(file.getOriginalFilename()).getFileName().toString();
        // 解析并校验文件类型
        String fileType = resolveFileType(originalFileName);
        String dateDirectory = LocalDate.now().format(DATE_DIRECTORY_FORMATTER);
        String newFileNamePrefix = attachmentCategory + "_"
                + LocalDateTime.now().format(FILE_NAME_TIME_FORMATTER) + "_"
                + adjustLogId;
        // 获取正式文件目录
        Path fileRoot = resolveFileRoot();
        Path targetDirectory = fileRoot.resolve(dateDirectory);
        // 生成不覆盖已有文件的附件名称
        String newFileName = resolveAvailableFileName(targetDirectory, newFileNamePrefix, fileType);
        Path targetPath = targetDirectory.resolve(newFileName).normalize();
        // 校验目标路径位于根目录内
        validatePathInRoot(targetPath, fileRoot);
        try {
            Files.createDirectories(targetDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BizException("附件保存失败：" + originalFileName);
        }
        // 数据库事务回滚时删除本次新保存的物理文件
        deleteFileAfterRollback(targetPath);

        StoredFile storedFile = new StoredFile();
        storedFile.originalFileName = originalFileName;
        storedFile.newFileName = newFileName;
        storedFile.fileType = fileType;
        storedFile.fileSize = file.getSize();
        storedFile.contentType = file.getContentType();
        // 转换为统一的相对路径
        storedFile.relativeFileName = toRelativePath(targetPath);
        return storedFile;
    }

    /** 生成不覆盖已有文件的附件名称 */
    private String resolveAvailableFileName(Path targetDirectory, String fileNamePrefix, String fileType) {
        String newFileName = fileNamePrefix + "." + fileType;
        int sequence = 2;
        while (Files.exists(targetDirectory.resolve(newFileName))) {
            newFileName = fileNamePrefix + "_" + sequence + "." + fileType;
            sequence++;
        }
        return newFileName;
    }

    /** 注册事务回滚后的文件清理 */
    private void deleteFileAfterRollback(final Path filePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteQuietly(filePath);
                }
            }
        });
    }

    /** 获取存储根目录 */
    private Path resolveStorageRoot() {
        return Paths.get(storagePath).toAbsolutePath().normalize();
    }

    /** 获取正式文件目录 */
    private Path resolveFileRoot() {
        // 获取附件存储根目录
        return resolveStorageRoot();
    }

    /** 转换为统一的相对路径 */
    private String toRelativePath(Path path) {
        // 获取附件存储根目录
        return resolveStorageRoot().relativize(path.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }

    /** 校验目标路径位于指定根目录内 */
    private void validatePathInRoot(Path path, Path root) {
        if (!path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
            throw new BizException("附件路径不合法");
        }
    }

    /** 安静删除文件 */
    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 回滚清理失败时保留文件，便于人工排查
        }
    }

    /**
     * 附件下载文件内容。
     */
    @Getter
    @AllArgsConstructor
    public static class DownloadFile {
        /** 原始文件名 */
        private final String originalFileName;
        /** MIME 类型 */
        private final String contentType;
        /** 文件大小 */
        private final Long fileSize;
        /** 文件内容 */
        private final byte[] content;
    }

    /**
     * 本次业务提交携带的文件上下文
     */
    public class SubmissionFiles {
        /** multipart 文件列表 */
        private final List<MultipartFile> files;
        /** 上传人 ID */
        private final String uploaderId;
        /** 已保存文件缓存，同一日志、分类和文件下标只保存一次 */
        private final Map<String, StoredFile> storedFileMap = new HashMap<>();

        SubmissionFiles(List<MultipartFile> files, String uploaderId) {
            this.files = files;
            this.uploaderId = uploaderId;
        }

        /** 按下标获取并保存文件 */
        StoredFile resolveStoredFile(Integer fileIndex, String attachmentCategory, Long adjustLogId) {
            if (fileIndex == null || fileIndex < 0 || fileIndex >= files.size()) {
                throw new BizException("附件文件下标不合法：" + fileIndex);
            }
            String storedFileKey = fileIndex + "|" + attachmentCategory + "|" + adjustLogId;
            StoredFile storedFile = storedFileMap.get(storedFileKey);
            if (storedFile == null) {
                // 保存提交文件并返回存储信息
                storedFile = storeFile(files.get(fileIndex), attachmentCategory, adjustLogId);
                storedFileMap.put(storedFileKey, storedFile);
            }
            return storedFile;
        }
    }

    /**
     * 已保存文件信息
     */
    private static class StoredFile {
        /** 原始文件名 */
        private String originalFileName;
        /** 新文件名 */
        private String newFileName;
        /** 文件类型 */
        private String fileType;
        /** 文件大小 */
        private Long fileSize;
        /** MIME 类型 */
        private String contentType;
        /** 相对存储路径 */
        private String relativeFileName;
    }
}
