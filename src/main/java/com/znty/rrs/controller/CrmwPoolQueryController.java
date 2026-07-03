package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.crmwpoolquery.CrmwPoolQueryDto;
import com.znty.rrs.entity.crmwpoolquery.CrmwPoolQueryReq;
import com.znty.rrs.service.CrmwPoolQueryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * CRMW池查询控制器
 */
@RestController
@RequestMapping("/api/v1/crmwPoolQuery")
public class CrmwPoolQueryController {

    /** CRMW池查询服务 */
    @Resource
    private CrmwPoolQueryService crmwPoolQueryService;

    /** 分页查询CRMW池列表 */
    @PostMapping("/queryCrmwPoolPage")
    public ApiResponse<PageResult<CrmwPoolQueryDto>> queryCrmwPoolPage(@RequestBody CrmwPoolQueryReq req) {
        return ApiResponse.success(crmwPoolQueryService.queryCrmwPoolPage(req));
    }
}
