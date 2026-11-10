package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.crmwrulealert.CrmwRuleAlertDto;
import com.znty.rrs.entity.crmwrulealert.CrmwRuleAlertReq;
import com.znty.rrs.service.CrmwRuleAlertService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/** CRMW 在库规则提醒接口 */
@RestController
@RequestMapping("/api/v1/crmwRuleAlert")
public class CrmwRuleAlertController {

    /** CRMW 提醒服务 */
    @Resource
    private CrmwRuleAlertService crmwRuleAlertService;

    /** 分页查询 CRMW 规则提醒 */
    @PostMapping("/queryAlertPage")
    public ApiResponse<PageResult<CrmwRuleAlertDto>> queryAlertPage(@RequestBody CrmwRuleAlertReq req) {
        return ApiResponse.success(crmwRuleAlertService.queryAlertPage(req));
    }

    /** 标记 CRMW 提醒已处理，不修改池状态 */
    @PostMapping("/editAlertProcessed")
    public ApiResponse<CrmwRuleAlertDto> editAlertProcessed(@RequestBody CrmwRuleAlertReq req) {
        return ApiResponse.success(crmwRuleAlertService.editAlertProcessed(req));
    }
}
