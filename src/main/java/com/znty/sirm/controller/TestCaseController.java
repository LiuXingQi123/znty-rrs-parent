package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.IdRequest;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.model.RuleRunResultDto;
import com.znty.sirm.model.TestCaseDto;
import com.znty.sirm.model.TestCaseReq;
import com.znty.sirm.service.TestCaseService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 测试用例管理 REST 接口，路由前缀 /api/v1/testCases。
 */
@RestController
@RequestMapping("/api/v1/testCases")
public class TestCaseController {

    @Resource
    private TestCaseService testCaseService;

    /** 分页查询测试用例列表，含最近一次执行结果 */
    @PostMapping("/queryTestCasePage")
    public ApiResponse<PageResult<TestCaseDto>> queryTestCasePage(@RequestBody(required = false) TestCaseReq req) {
        return ApiResponse.success(testCaseService.queryTestCasePage(req));
    }

    /** 新增或编辑测试用例，含输入参数值绑定 */
    @PostMapping("/addOrEditTestCase")
    public ApiResponse<TestCaseDto> addOrEditTestCase(@RequestBody TestCaseReq req) {
        return ApiResponse.success(testCaseService.addOrEditTestCase(req));
    }

    /** 修改测试用例名称（不涉及参数变更） */
    @PostMapping("/editTestCaseName")
    public ApiResponse<TestCaseDto> editTestCaseName(@RequestBody TestCaseReq req) {
        return ApiResponse.success(testCaseService.editTestCaseName(req));
    }

    /** 物理删除测试用例及其关联参数数据 */
    @PostMapping("/deleteTestCase")
    public ApiResponse<TestCaseDto> deleteTestCase(@RequestBody IdRequest req) {
        return ApiResponse.success(testCaseService.deleteTestCase(req));
    }

    /** 执行单个测试用例，更新该用例的最近执行结果 */
    @PostMapping("/runTestCase")
    public ApiResponse<TestCaseDto> runTestCase(@RequestBody IdRequest req) {
        return ApiResponse.success(testCaseService.runTestCase(req));
    }

    /** 批量执行全部测试用例，每个用例独立事务互不影响 */
    @PostMapping("/runAllTestCases")
    public ApiResponse<List<TestCaseDto>> runAllTestCases() {
        return ApiResponse.success(testCaseService.runAllTestCases());
    }

    /** 查询指定测试用例的执行历史记录（含步骤日志） */
    @PostMapping("/queryRunHistoryList")
    public ApiResponse<List<RuleRunResultDto>> queryRunHistoryList(@RequestBody IdRequest req) {
        return ApiResponse.success(testCaseService.queryRunHistoryList(req));
    }
}
