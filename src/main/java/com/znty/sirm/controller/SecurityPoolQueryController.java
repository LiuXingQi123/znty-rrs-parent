package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.model.SecurityPoolQueryDto;
import com.znty.sirm.model.SecurityPoolQueryReq;
import com.znty.sirm.model.SecurityTypeOptionDto;
import com.znty.sirm.model.InvestmentPoolBo;
import com.znty.sirm.model.MySecurityPoolBo;
import com.znty.sirm.model.MySecurityPoolReq;
import com.znty.sirm.service.SecurityPoolQueryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 证券池查询控制器
 */
@RestController
@RequestMapping("/api/v1/securityPoolQuery")
public class SecurityPoolQueryController {

    @Resource
    private SecurityPoolQueryService securityPoolQueryService;

    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 分页查询证券池中的证券列表 */
    @PostMapping("/querySecurityPoolPage")
    public ApiResponse<PageResult<SecurityPoolQueryDto>> querySecurityPoolPage(@RequestBody SecurityPoolQueryReq req) {
        return ApiResponse.success(securityPoolQueryService.querySecurityPoolPage(req));
    }

    /** 查询证券类型下拉选项（code + name） */
    @PostMapping("/querySecurityTypeList")
    public ApiResponse<List<SecurityTypeOptionDto>> querySecurityTypeList() {
        return ApiResponse.success(securityPoolQueryService.querySecurityTypeList());
    }

    /** 查询证券状态下拉选项 */
    @PostMapping("/querySecurityStatusList")
    public ApiResponse<List<String>> querySecurityStatusList() {
        return ApiResponse.success(securityPoolQueryService.querySecurityStatusList());
    }

    /** 查询投资池树 */
    @PostMapping("/queryPoolTreeList")
    public ApiResponse<List<InvestmentPoolBo>> queryPoolTreeList() {
        return ApiResponse.success(investmentPoolMapper.queryPoolList());
    }

    /** 添加证券到我的证券池 */
    @PostMapping("/addToMyPool")
    public ApiResponse<MySecurityPoolBo> addToMyPool(@RequestBody MySecurityPoolReq req) {
        return ApiResponse.success(securityPoolQueryService.addToMyPool(req));
    }

    /** 从我的证券池移除 */
    @PostMapping("/deleteFromMyPool")
    public ApiResponse<MySecurityPoolBo> deleteFromMyPool(@RequestBody MySecurityPoolReq req) {
        return ApiResponse.success(securityPoolQueryService.deleteFromMyPool(req));
    }

    /** 批量查询用户已收藏的证券代码 */
    @PostMapping("/queryFavoritedCodeList")
    public ApiResponse<List<String>> queryFavoritedCodeList(@RequestBody Map<String, String> params) {
        String userId = params.get("userId");
        return ApiResponse.success(securityPoolQueryService.queryFavoritedCodeList(userId));
    }
}
