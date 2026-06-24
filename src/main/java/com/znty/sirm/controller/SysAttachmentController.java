package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.model.SysAttachmentDto;
import com.znty.sirm.model.SysAttachmentReq;
import com.znty.sirm.service.SysAttachmentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 系统附件接口
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
        return ApiResponse.success(sysAttachmentService.queryAttachmentList(req.getAdjustLogId()));
    }

    /** 下载附件 */
    @PostMapping("/downloadAttachment")
    public void downloadAttachment(@RequestBody SysAttachmentReq req, HttpServletResponse response) {
        sysAttachmentService.downloadAttachment(req.getId(), response);
    }

}
