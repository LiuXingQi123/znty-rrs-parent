package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.forbiddenpoolquery.ForbiddenPoolQueryDto;
import com.znty.rrs.entity.forbiddenpoolquery.ForbiddenPoolQueryReq;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.service.ForbiddenPoolQueryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 禁投池查询控制器
 *
 * 查询禁止交易的证券，提供分页列表、导出和筛选选项
 */
@RestController
@RequestMapping("/api/v1/forbiddenPoolQuery")
public class ForbiddenPoolQueryController {

    /** 禁投池查询服务 */
    @Resource
    private ForbiddenPoolQueryService forbiddenPoolQueryService;

    /**
     * 分页查询禁投池中的证券列表，支持按证券代码、名称、类型等条件筛选
     *
     * @param req 禁投池记录的筛选和分页条件
     * @return 禁投池记录及分页信息
     */
    @PostMapping("/queryForbiddenPoolPage")
    public ApiResponse<PageResult<ForbiddenPoolQueryDto>> queryForbiddenPoolPage(@RequestBody ForbiddenPoolQueryReq req) {
        return ApiResponse.success(forbiddenPoolQueryService.queryForbiddenPoolPage(req));
    }

    /**
     * 按当前查询条件导出禁投池列表
     *
     * 筛选条件与列表一致，不传分页时导出全部命中记录
     *
     * @param req 禁投池记录的筛选条件
     * @return Excel 文件名、内容类型、文件大小和 Base64 内容
     */
    @PostMapping("/exportForbiddenPoolExcel")
    public ApiResponse<CommonFileDto> exportForbiddenPoolExcel(@RequestBody ForbiddenPoolQueryReq req) {
        return ApiResponse.success(forbiddenPoolQueryService.exportForbiddenPoolExcel(req));
    }

    /**
     * 查询可查看的禁投池记录中出现的证券类型，供列表筛选
     *
     * @param req 当前用户 ID，用于确定可查看的禁投池范围
     * @return 证券类型选项列表，包含编码和名称
     */
    @PostMapping("/querySecurityTypeList")
    public ApiResponse<List<SecurityTypeOptionDto>> querySecurityTypeList(
            @RequestBody ForbiddenPoolQueryReq req) {
        return ApiResponse.success(forbiddenPoolQueryService.querySecurityTypeList(req));
    }
}
