package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.model.FlowOptionDto;
import com.znty.sirm.model.TodoProcessDto;
import com.znty.sirm.model.TodoProcessReq;
import com.znty.sirm.service.TodoProcessService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 待办处理控制器，提供待审批步骤列表和流程筛选选项。
 */
@RestController
@RequestMapping("/api/v1/todoProcess")
public class TodoProcessController {

    @Resource
    private TodoProcessService todoProcessService;

    /** 分页查询待办处理列表 */
    @PostMapping("/queryTodoProcessPage")
    public ApiResponse<PageResult<TodoProcessDto>> queryTodoProcessPage(@RequestBody TodoProcessReq req) {
        return ApiResponse.success(todoProcessService.queryTodoProcessPage(req));
    }

    /** 查询待办流程名称下拉选项 */
    @PostMapping("/queryFlowOptionList")
    public ApiResponse<List<FlowOptionDto>> queryFlowOptionList(@RequestBody TodoProcessReq req) {
        return ApiResponse.success(todoProcessService.queryFlowOptionList(req));
    }
}
