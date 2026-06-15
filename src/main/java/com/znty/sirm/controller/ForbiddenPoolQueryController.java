package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.model.ForbiddenPoolQueryDto;
import com.znty.sirm.model.ForbiddenPoolQueryReq;
import com.znty.sirm.model.SecurityTypeOptionDto;
import com.znty.sirm.service.ForbiddenPoolQueryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 禁止交易池查询控制器
 * <p>
 * 负责禁止交易池（禁投池）的证券数据查询，风控合规要求禁止交易的证券
 * 将被纳入禁投池，本控制器提供分页检索及筛选条件数据接口。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/forbiddenPoolQuery")
public class ForbiddenPoolQueryController {

    /** 禁止池查询服务 */
    @Resource
    private ForbiddenPoolQueryService forbiddenPoolQueryService;

    /**
     * 分页查询禁止交易池中的证券列表，支持按证券代码、名称、类型等条件筛选
     */
    @PostMapping("/queryForbiddenPoolPage")
    public ApiResponse<PageResult<ForbiddenPoolQueryDto>> queryForbiddenPoolPage(@RequestBody ForbiddenPoolQueryReq req) {
        return ApiResponse.success(forbiddenPoolQueryService.queryForbiddenPoolPage(req));
    }

    /**
     * 查询禁投池中涉及的证券类型下拉选项（code + name），用于页面筛选条件
     */
    @PostMapping("/querySecurityTypeList")
    public ApiResponse<List<SecurityTypeOptionDto>> querySecurityTypeList(
            @RequestBody(required = false) ForbiddenPoolQueryReq req) {
        return ApiResponse.success(forbiddenPoolQueryService.querySecurityTypeList());
    }
}
