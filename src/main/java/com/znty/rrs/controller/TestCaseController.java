package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.testcase.RuleRunResultDto;
import com.znty.rrs.entity.testcase.TestCaseDto;
import com.znty.rrs.entity.testcase.TestCaseReq;
import com.znty.rrs.service.TestCaseService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 规则测试用例管理控制器
 *
 * 管理规则测试用例，每个用例绑定一条规则和一组固定参数，支持单条和批量执行
 * 保存执行结果和步骤日志，供规则变更后的回归验证使用
 * 路由前缀为 {@code /api/v1/testCases}
 */
@RestController
@RequestMapping("/api/v1/testCases")
public class TestCaseController {

    /** 测试用例服务 */
    @Resource
    private TestCaseService testCaseService;

    /**
     * 分页查询测试用例列表，每条记录附带最近一次执行的结果状态（通过、失败、未执行）
     *
     * @param req 用例关键字、执行结果筛选和分页条件
     * @return 规则测试用例及分页信息
     */
    @PostMapping("/queryTestCasePage")
    public ApiResponse<PageResult<TestCaseDto>> queryTestCasePage(@RequestBody TestCaseReq req) {
        return ApiResponse.success(testCaseService.queryTestCasePage(req));
    }

    /**
     * 新增测试用例，含所关联规则的输入参数与期望值的全量绑定
     *
     * @param req 关联规则 ID、用例名称及参数值
     * @return 新增的测试用例
     */
    @PostMapping("/addTestCase")
    public ApiResponse<TestCaseDto> addTestCase(@RequestBody TestCaseReq req) {
        return ApiResponse.success(testCaseService.addTestCase(req));
    }

    /**
     * 编辑测试用例，含所关联规则的输入参数与期望值的全量绑定
     *
     * @param req 用例 ID、关联规则 ID、名称及参数值
     * @return 更新后的测试用例
     */
    @PostMapping("/editTestCase")
    public ApiResponse<TestCaseDto> editTestCase(@RequestBody TestCaseReq req) {
        return ApiResponse.success(testCaseService.editTestCase(req));
    }

    /**
     * 仅修改测试用例名称，不涉及关联规则和参数数据的变更
     *
     * @param req 用例 ID 和新名称
     * @return 更名后的测试用例
     */
    @PostMapping("/editTestCaseName")
    public ApiResponse<TestCaseDto> editTestCaseName(@RequestBody TestCaseReq req) {
        return ApiResponse.success(testCaseService.editTestCaseName(req));
    }

    /**
     * 删除测试用例及其参数绑定数据
     *
     * @param req 待删除的用例 ID
     * @return 删除前的测试用例
     */
    @PostMapping("/deleteTestCase")
    public ApiResponse<TestCaseDto> deleteTestCase(@RequestBody TestCaseReq req) {
        return ApiResponse.success(testCaseService.deleteTestCase(req));
    }

    /**
     * 执行指定的单个测试用例，使用其绑定参数调用 QLExpress 引擎，并更新最近执行结果
     *
     * @param req 待执行的用例 ID
     * @return 附有本次执行结果的测试用例
     */
    @PostMapping("/runTestCase")
    public ApiResponse<TestCaseDto> runTestCase(@RequestBody TestCaseReq req) {
        return ApiResponse.success(testCaseService.runTestCase(req));
    }

    /**
     * 批量执行所有测试用例，每个用例在独立事务中执行，单条失败不影响其他用例的执行
     *
     * @param req 空请求体
     * @return 附有最新执行结果的测试用例列表
     */
    @PostMapping("/runAllTestCases")
    public ApiResponse<List<TestCaseDto>> runAllTestCases(@RequestBody TestCaseReq req) {
        return ApiResponse.success(testCaseService.runAllTestCases());
    }

    /**
     * 查询指定测试用例的执行历史记录列表，每条记录含详细的步骤日志和最终执行结果
     *
     * @param req 待查询的用例 ID
     * @return 规则执行结果列表
     */
    @PostMapping("/queryRunHistoryList")
    public ApiResponse<List<RuleRunResultDto>> queryRunHistoryList(@RequestBody TestCaseReq req) {
        return ApiResponse.success(testCaseService.queryRunHistoryList(req));
    }
}
