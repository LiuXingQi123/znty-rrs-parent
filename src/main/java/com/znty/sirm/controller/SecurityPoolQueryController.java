package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.model.SecurityPoolQueryDto;
import com.znty.sirm.model.SecurityPoolQueryReq;
import com.znty.sirm.model.SecurityTypeOptionDto;
import com.znty.sirm.model.MySecurityPoolBo;
import com.znty.sirm.model.MySecurityPoolReq;
import com.znty.sirm.service.SecurityPoolQueryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 证券池查询控制器
 * <p>
 * 负责证券当前池状态的查询展示，支持按投资池、证券类型、证券状态等多维度筛选。
 * 同时提供"我的证券池"个人收藏功能，用户可将关注的证券加入自己的收藏池便于快速查阅。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/securityPoolQuery")
public class SecurityPoolQueryController {

    /** 证券池查询服务 */
    @Resource
    private SecurityPoolQueryService securityPoolQueryService;

    /**
     * 分页查询证券池中的证券列表，支持按投资池、证券代码/名称、类型、状态等组合筛选
     */
    @PostMapping("/querySecurityPoolPage")
    public ApiResponse<PageResult<SecurityPoolQueryDto>> querySecurityPoolPage(@RequestBody SecurityPoolQueryReq req) {
        return ApiResponse.success(securityPoolQueryService.querySecurityPoolPage(req));
    }

    /**
     * 查询证券类型下拉选项（code + name），用于页面筛选条件的类型过滤器
     */
    @PostMapping("/querySecurityTypeList")
    public ApiResponse<List<SecurityTypeOptionDto>> querySecurityTypeList(
            @RequestBody SecurityPoolQueryReq req) {
        return ApiResponse.success(securityPoolQueryService.querySecurityTypeList());
    }

    /**
     * 查询证券状态下拉选项，返回系统中存在的证券状态 code 列表
     */
    @PostMapping("/querySecurityStatusList")
    public ApiResponse<List<String>> querySecurityStatusList(
            @RequestBody SecurityPoolQueryReq req) {
        return ApiResponse.success(securityPoolQueryService.querySecurityStatusList());
    }

    /**
     * 将指定证券添加到当前用户的个人收藏池（我的证券池）
     */
    @PostMapping("/addSecurityToMyPool")
    public ApiResponse<MySecurityPoolBo> addSecurityToMyPool(@RequestBody MySecurityPoolReq req) {
        return ApiResponse.success(securityPoolQueryService.addSecurityToMyPool(req));
    }

    /**
     * 将指定证券从当前用户的个人收藏池中移除
     */
    @PostMapping("/deleteSecurityFromMyPool")
    public ApiResponse<MySecurityPoolBo> deleteSecurityFromMyPool(@RequestBody MySecurityPoolReq req) {
        return ApiResponse.success(securityPoolQueryService.deleteSecurityFromMyPool(req));
    }

    /**
     * 批量查询指定用户已收藏的证券代码列表，用于前端渲染收藏状态标记
     */
    @PostMapping("/queryFavoritedCodeList")
    public ApiResponse<List<String>> queryFavoritedCodeList(@RequestBody MySecurityPoolReq req) {
        return ApiResponse.success(securityPoolQueryService.queryFavoritedCodeList(req));
    }
}
