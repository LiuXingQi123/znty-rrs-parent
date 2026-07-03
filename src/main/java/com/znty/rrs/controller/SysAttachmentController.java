package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.entity.sysattachment.SysAttachmentDto;
import com.znty.rrs.entity.sysattachment.SysAttachmentReq;
import com.znty.rrs.service.SysAttachmentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 系统附件接口
 * <p>
 * 负责调库记录附件查询与附件文件下载，附件文件下载以二进制响应返回。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/attachments")
public class SysAttachmentController {

    /** 系统附件业务服务 */
    @Resource
    private SysAttachmentService sysAttachmentService;

    /** 查询调库日志附件列表 */
    @PostMapping("/queryAttachmentList")
    public ApiResponse<List<SysAttachmentDto>> queryAttachmentList(@RequestBody SysAttachmentReq req) {
        return ApiResponse.success(sysAttachmentService.queryAttachmentList(req));
    }

    /** 下载附件 */
    @PostMapping("/downloadAttachment")
    public ResponseEntity<byte[]> downloadAttachment(@RequestBody SysAttachmentReq req) {
        SysAttachmentService.DownloadFile downloadFile = sysAttachmentService.downloadAttachment(req);
        HttpHeaders headers = new HttpHeaders();
        // 编码下载文件名
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encodeFileName(downloadFile.getOriginalFileName()));
        headers.setContentType(MediaType.parseMediaType(downloadFile.getContentType()));
        headers.setContentLength(downloadFile.getFileSize());
        return ResponseEntity.ok()
                .headers(headers)
                .body(downloadFile.getContent());
    }

    /** 编码下载文件名 */
    private String encodeFileName(String fileName) {
        try {
            return URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 编码不可用", e);
        }
    }
}
