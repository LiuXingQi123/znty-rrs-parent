package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.model.BondPoolQueryDto;
import com.znty.sirm.model.BondPoolQueryReq;
import com.znty.sirm.model.InvestmentPoolBo;
import com.znty.sirm.model.MyBondPoolBo;
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
    @PostMapping("/queryBondPoolPage")
    public ApiResponse<PageResult<BondPoolQueryDto>> queryBondPoolPage(@RequestBody BondPoolQueryReq req) {
        return ApiResponse.success(bondPoolQueryService.queryBondPoolPage(req));
    }

    /** 查询债券类型下拉选项 */
    @PostMapping("/queryBondTypeList")
    public ApiResponse<List<String>> queryBondTypeList() {
        return ApiResponse.success(bondPoolQueryService.queryBondTypeList());
    }

    /** 查询债券状态下拉选项 */
    @PostMapping("/queryBondStatusList")
    public ApiResponse<List<String>> queryBondStatusList() {
        return ApiResponse.success(bondPoolQueryService.queryBondStatusList());
    }

    /** 查询投资池树 */
    @PostMapping("/queryPoolTreeList")
    public ApiResponse<List<InvestmentPoolBo>> queryPoolTreeList() {
        return ApiResponse.success(investmentPoolMapper.queryPoolList());
    }

    /** 添加债券到我的债券池 */
    @PostMapping("/addToMyPool")
    public ApiResponse<MyBondPoolBo> addToMyPool(@RequestBody MyBondPoolReq req) {
        return ApiResponse.success(bondPoolQueryService.addToMyPool(req));
    }

    /** 从我的债券池移除 */
    @PostMapping("/deleteFromMyPool")
    public ApiResponse<MyBondPoolBo> deleteFromMyPool(@RequestBody MyBondPoolReq req) {
        return ApiResponse.success(bondPoolQueryService.deleteFromMyPool(req));
    }

    /** 批量查询用户已收藏的证券代码 */
    @PostMapping("/queryFavoritedCodeList")
    public ApiResponse<List<String>> queryFavoritedCodeList(@RequestBody Map<String, String> params) {
        String userId = params.get("userId");
        return ApiResponse.success(bondPoolQueryService.queryFavoritedCodeList(userId));
    }
}
