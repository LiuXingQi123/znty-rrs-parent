package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.model.BondPoolQueryDto;
import com.znty.sirm.model.BondPoolQueryReq;
import com.znty.sirm.model.InvestmentPoolBo;
import com.znty.sirm.model.MyBondPoolReq;
import com.znty.sirm.service.BondPoolQueryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 债券池查询控制器
 */
@RestController
@RequestMapping("/api/v1/bondPoolQuery")
public class BondPoolQueryController {

    @Resource
    private BondPoolQueryService bondPoolQueryService;

    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 分页查询债券池中的债券列表 */
    @PostMapping("/queryPage")
    public ApiResponse<PageResult<BondPoolQueryDto>> queryPage(@RequestBody BondPoolQueryReq req) {
        return ApiResponse.success(bondPoolQueryService.queryPage(req));
    }

    /** 查询债券类型下拉选项 */
    @PostMapping("/queryBondTypeOptions")
    public ApiResponse<List<String>> queryBondTypeOptions() {
        return ApiResponse.success(bondPoolQueryService.queryBondTypeOptions());
    }

    /** 查询债券状态下拉选项 */
    @PostMapping("/queryBondStatusOptions")
    public ApiResponse<List<Map<String, String>>> queryBondStatusOptions() {
        return ApiResponse.success(bondPoolQueryService.queryBondStatusOptions());
    }

    /** 查询投资池树 */
    @PostMapping("/queryPoolTree")
    public ApiResponse<List<InvestmentPoolBo>> queryPoolTree() {
        return ApiResponse.success(investmentPoolMapper.queryPoolList());
    }

    /** 添加债券到我的债券池 */
    @PostMapping("/addToMyPool")
    public ApiResponse<?> addToMyPool(@RequestBody MyBondPoolReq req) {
        bondPoolQueryService.addToMyPool(req);
        return ApiResponse.success();
    }

    /** 从我的债券池移除 */
    @PostMapping("/removeFromMyPool")
    public ApiResponse<?> removeFromMyPool(@RequestBody MyBondPoolReq req) {
        bondPoolQueryService.removeFromMyPool(req);
        return ApiResponse.success();
    }

    /** 批量查询用户已收藏的证券代码 */
    @PostMapping("/queryFavoritedCodes")
    public ApiResponse<List<String>> queryFavoritedCodes(@RequestBody Map<String, String> params) {
        String userId = params.get("userId");
        return ApiResponse.success(bondPoolQueryService.queryFavoritedCodes(userId));
    }
}
