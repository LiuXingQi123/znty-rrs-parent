package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.entity.crmwpoolhistory.CrmwPoolAdjustHistoryDto;
import com.znty.sirm.entity.crmwpoolhistory.CrmwPoolAdjustHistoryReq;
import com.znty.sirm.service.CrmwPoolAdjustHistoryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * CRMW池调整历史查询控制器
 */
@RestController
@RequestMapping("/api/v1/crmwPoolAdjustHistory")
public class CrmwPoolAdjustHistoryController {

    /** CRMW池调整历史查询服务 */
    @Resource
    private CrmwPoolAdjustHistoryService crmwPoolAdjustHistoryService;

    /** 分页查询CRMW池调整历史列表 */
    @PostMapping("/queryCrmwPoolAdjustHistoryPage")
    public ApiResponse<PageResult<CrmwPoolAdjustHistoryDto>> queryCrmwPoolAdjustHistoryPage(
            @RequestBody CrmwPoolAdjustHistoryReq req) {
        return ApiResponse.success(crmwPoolAdjustHistoryService.queryCrmwPoolAdjustHistoryPage(req));
    }
}
