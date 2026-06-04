package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.model.ForbiddenPoolQueryDto;
import com.znty.sirm.model.ForbiddenPoolQueryReq;
import com.znty.sirm.service.ForbiddenPoolQueryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 禁投池查询控制器
 */
@RestController
@RequestMapping("/api/v1/forbiddenPoolQuery")
public class ForbiddenPoolQueryController {

    @Resource
    private ForbiddenPoolQueryService forbiddenPoolQueryService;

    /** 分页查询禁投池证券列表 */
    @PostMapping("/queryForbiddenPoolPage")
    public ApiResponse<PageResult<ForbiddenPoolQueryDto>> queryForbiddenPoolPage(@RequestBody ForbiddenPoolQueryReq req) {
        return ApiResponse.success(forbiddenPoolQueryService.queryForbiddenPoolPage(req));
    }

    /** 查询证券类型下拉选项 */
    @PostMapping("/queryBondTypeList")
    public ApiResponse<List<String>> queryBondTypeList() {
        return ApiResponse.success(forbiddenPoolQueryService.queryBondTypeList());
    }
}
