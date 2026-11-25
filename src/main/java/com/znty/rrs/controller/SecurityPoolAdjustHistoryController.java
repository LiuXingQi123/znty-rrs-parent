package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.securitypooladjusthistory.SecurityPoolAdjustHistoryDto;
import com.znty.rrs.entity.securitypooladjusthistory.SecurityPoolAdjustHistoryReq;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.service.SecurityPoolAdjustHistoryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 证券池调整历史控制器
 *
 * 提供证券入池和出池记录的分页查询、导出及证券类型筛选，供操作追溯与合规审查
 */
@RestController
@RequestMapping("/api/v1/securityPoolAdjustHistory")
public class SecurityPoolAdjustHistoryController {

    /** 证券池调库历史服务 */
    @Resource
    private SecurityPoolAdjustHistoryService securityPoolAdjustHistoryService;

    /**
     * 分页查询证券池调整历史记录
     *
     * 支持按证券代码、调整类型、操作时间区间等条件筛选，结果按操作时间倒序排列
     *
     * @param req 证券池调整历史的筛选和分页条件
     * @return 证券池调整历史及分页信息
     */
    @PostMapping("/querySecurityPoolAdjustHistoryPage")
    public ApiResponse<PageResult<SecurityPoolAdjustHistoryDto>> querySecurityPoolAdjustHistoryPage(
            @RequestBody SecurityPoolAdjustHistoryReq req) {
        return ApiResponse.success(securityPoolAdjustHistoryService.querySecurityPoolAdjustHistoryPage(req));
    }

    /**
     * 按当前查询条件导出证券池调整历史
     *
     * 筛选条件与列表一致，不传分页时导出全部命中记录
     *
     * @param req 证券池调整历史的筛选条件
     * @return Excel 文件名、内容类型、文件大小和 Base64 内容
     */
    @PostMapping("/exportSecurityPoolAdjustHistoryExcel")
    public ApiResponse<CommonFileDto> exportSecurityPoolAdjustHistoryExcel(
            @RequestBody SecurityPoolAdjustHistoryReq req) {
        return ApiResponse.success(securityPoolAdjustHistoryService.exportSecurityPoolAdjustHistoryExcel(req));
    }

    /**
     * 查询调整历史中出现的证券类型，供列表筛选
     *
     * 返回系统中存在调整历史的证券类型列表，供前端筛选条件使用
     *
     * @param req 空请求体
     * @return 证券类型选项列表，包含编码和名称
     */
    @PostMapping("/querySecurityTypeList")
    public ApiResponse<List<SecurityTypeOptionDto>> querySecurityTypeList(
            @RequestBody SecurityPoolAdjustHistoryReq req) {
        return ApiResponse.success(securityPoolAdjustHistoryService.querySecurityTypeList());
    }

}
