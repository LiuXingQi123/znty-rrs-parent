package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.flow.FlowOptionDto;
import com.znty.rrs.entity.mymatters.MyMattersDto;
import com.znty.rrs.entity.mymatters.MyMattersReq;
import com.znty.rrs.service.MyMattersService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 我的事宜控制器，提供当前用户相关流程事项列表和流程筛选选项。
 */
@RestController
@RequestMapping("/api/v1/myMatters")
public class MyMattersController {

    /** 我的事项服务 */
    @Resource
    private MyMattersService myMattersService;

    /** 分页查询我的事宜列表 */
    @PostMapping("/queryMyMattersPage")
    public ApiResponse<PageResult<MyMattersDto>> queryMyMattersPage(@RequestBody MyMattersReq req) {
        return ApiResponse.success(myMattersService.queryMyMattersPage(req));
    }

    /** 查询我的事宜流程名称下拉选项 */
    @PostMapping("/queryFlowOptionList")
    public ApiResponse<List<FlowOptionDto>> queryFlowOptionList(@RequestBody MyMattersReq req) {
        return ApiResponse.success(myMattersService.queryFlowOptionList(req));
    }
}
