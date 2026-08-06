package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.commonfile.CommonFileReq;
import com.znty.rrs.service.CommonFileService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 公共文件接口（模板下载等）
 */
@RestController
@RequestMapping("/api/v1/commonFile")
public class CommonFileController {

    /** 公共文件业务服务 */
    @Resource
    private CommonFileService commonFileService;

    /**
     * 下载 classpath 模板
     */
    @PostMapping("/downloadTemplate")
    public ApiResponse<CommonFileDto> downloadTemplate(@RequestBody CommonFileReq req) {
        // 委托服务层按模板编码读取 classpath 文件
        return ApiResponse.success(commonFileService.downloadTemplate(req));
    }
}
