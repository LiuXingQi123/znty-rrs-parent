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
 * 规则测试用例管理控制器
 * <p>
 * 负责风控规则测试用例的 CRUD 管理与执行，每个测试用例绑定一条规则及一组固定的输入参数值，
 * 支持单条执行和批量执行，执行结果和步骤日志持久化记录，用于规则变更后的回归验证。
 * 路由前缀 /api/v1/testCases。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/testCases")
public class TestCaseController {

    @Resource
    private TestCaseService testCaseService;

    /**
     * 分页查询测试用例列表，每条记录附带最近一次执行的结果状态（通过/失败/未执行）
     */
    @PostMapping("/queryTestCasePage")
    public ApiResponse<PageResult<TestCaseDto>> queryTestCasePage(@RequestBody(required = false) TestCaseReq req) {
        return ApiResponse.success(testCaseService.queryTestCasePage(req));
    }

    /**
     * 新增或编辑测试用例，含所关联规则的输入参数与期望值的全量绑定
     * <p>req.id 为空时新增，非空时全量覆盖更新。</p>
     */
    @PostMapping("/addOrEditTestCase")
    public ApiResponse<TestCaseDto> addOrEditTestCase(@RequestBody TestCaseReq req) {
        return ApiResponse.success(testCaseService.addOrEditTestCase(req));
    }

    /**
     * 仅修改测试用例名称，不涉及关联规则和参数数据的变更
     */
    @PostMapping("/editTestCaseName")
    public ApiResponse<TestCaseDto> editTestCaseName(@RequestBody TestCaseReq req) {
        return ApiResponse.success(testCaseService.editTestCaseName(req));
    }

    /**
     * 物理删除测试用例，同时级联删除其关联的参数绑定数据和执行历史记录
     */
    @PostMapping("/deleteTestCase")
    public ApiResponse<TestCaseDto> deleteTestCase(@RequestBody IdRequest req) {
        return ApiResponse.success(testCaseService.deleteTestCase(req));
    }

    /**
     * 执行指定的单个测试用例，使用其绑定参数调用 QLExpress 引擎，并更新最近执行结果
     */
    @PostMapping("/runTestCase")
    public ApiResponse<TestCaseDto> runTestCase(@RequestBody IdRequest req) {
        return ApiResponse.success(testCaseService.runTestCase(req));
    }

    /**
     * 批量执行所有测试用例，每个用例在独立事务中执行，单条失败不影响其他用例的执行
     */
    @PostMapping("/runAllTestCases")
    public ApiResponse<List<TestCaseDto>> runAllTestCases() {
        return ApiResponse.success(testCaseService.runAllTestCases());
    }

    /**
     * 查询指定测试用例的执行历史记录列表，每条记录含详细的步骤日志和最终执行结果
     */
    @PostMapping("/queryRunHistoryList")
    public ApiResponse<List<RuleRunResultDto>> queryRunHistoryList(@RequestBody IdRequest req) {
        return ApiResponse.success(testCaseService.queryRunHistoryList(req));
    }
}
