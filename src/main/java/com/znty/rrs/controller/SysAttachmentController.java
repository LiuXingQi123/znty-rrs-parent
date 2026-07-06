package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.entity.sysattachment.SysAttachmentDto;
import com.znty.rrs.entity.sysattachment.SysAttachmentReq;
import com.znty.rrs.service.SysAttachmentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Base64;
import java.util.List;

/**
 * 系统附件接口
 * <p>
 * 负责调库记录附件查询与附件文件下载，附件文件下载以 Base64 字符串统一响应返回。
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
    public ApiResponse<String> downloadAttachment(@RequestBody SysAttachmentReq req) {
        SysAttachmentService.DownloadFile downloadFile = sysAttachmentService.downloadAttachment(req);
        return ApiResponse.success(Base64.getEncoder().encodeToString(downloadFile.getContent()));
    }
}
