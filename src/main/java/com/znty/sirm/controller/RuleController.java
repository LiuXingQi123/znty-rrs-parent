package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.IdRequest;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.model.CategoryDto;
import com.znty.sirm.model.PresetSetDto;
import com.znty.sirm.model.RuleDto;
import com.znty.sirm.model.RuleReq;
import com.znty.sirm.model.RuleRunResultDto;
import com.znty.sirm.model.TestCaseDto;
import com.znty.sirm.model.TestCaseReq;
import com.znty.sirm.service.RuleService;
import com.znty.sirm.service.TestCaseService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 规则管理统一 REST 接口，路由前缀 /api/v1。
 * <p>涵盖四大模块：规则 CRUD、规则执行、测试用例管理、选项字典。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class RuleController {

    @Resource
    private RuleService ruleService;

    @Resource
    private TestCaseService testCaseService;

    // ==================== 规则管理 ====================

    /** 分页查询规则列表，支持按关键字和状态筛选 */
    @PostMapping("/rules/queryRulePage")
    public ApiResponse<PageResult<RuleDto>> queryRulePage(@RequestBody(required = false) RuleReq req) {
        return ApiResponse.success(ruleService.queryRulePage(req));
    }

    /** 按规则 ID 查询规则详情，含关联参数和选项 */
    @PostMapping("/rules/queryRuleDetail")
    public ApiResponse<RuleDto> queryRuleDetail(@RequestBody IdRequest req) {
        return ApiResponse.success(ruleService.queryRuleDetail(req));
    }

    /** 新增或编辑规则，含脚本和参数的全量保存 */
    @PostMapping("/rules/saveRule")
    public ApiResponse<RuleDto> saveRule(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.saveRule(req));
    }

    /** 更新规则启用状态（active / disabled） */
    @PostMapping("/rules/updateRuleStatus")
    public ApiResponse<RuleDto> updateRuleStatus(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.updateRuleStatus(req));
    }

    /** 软删除规则（标记删除，不物理删除） */
    @PostMapping("/rules/deleteRule")
    public ApiResponse<RuleDto> deleteRule(@RequestBody IdRequest req) {
        return ApiResponse.success(ruleService.deleteRule(req));
    }

    // ==================== 规则执行 ====================

    /** 按规则 ID 和输入参数临时执行规则，返回执行日志和结果 */
    @PostMapping("/rule-runs/executeRule")
    public ApiResponse<RuleRunResultDto> executeRule(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.executeRule(req));
    }

    // ==================== 测试用例 ====================

    /** 分页查询测试用例列表，含最近一次执行结果 */
    @PostMapping("/test-cases/queryTestCasePage")
    public ApiResponse<PageResult<TestCaseDto>> queryTestCasePage(@RequestBody(required = false) TestCaseReq req) {
        return ApiResponse.success(testCaseService.queryTestCasePage(req));
    }

    /** 新增或编辑测试用例，含输入参数值绑定 */
    @PostMapping("/test-cases/saveTestCase")
    public ApiResponse<TestCaseDto> saveTestCase(@RequestBody TestCaseReq req) {
        return ApiResponse.success(testCaseService.saveTestCase(req));
    }

    /** 修改测试用例名称（不涉及参数变更） */
    @PostMapping("/test-cases/renameTestCase")
    public ApiResponse<TestCaseDto> renameTestCase(@RequestBody TestCaseReq req) {
        return ApiResponse.success(testCaseService.renameTestCase(req));
    }

    /** 物理删除测试用例及其关联参数数据 */
    @PostMapping("/test-cases/deleteTestCase")
    public ApiResponse<TestCaseDto> deleteTestCase(@RequestBody IdRequest req) {
        return ApiResponse.success(testCaseService.deleteTestCase(req));
    }

    /** 执行单个测试用例，更新该用例的最近执行结果 */
    @PostMapping("/test-cases/runTestCase")
    public ApiResponse<TestCaseDto> runTestCase(@RequestBody IdRequest req) {
        return ApiResponse.success(testCaseService.runTestCase(req));
    }

    /** 批量执行全部测试用例，每个用例独立事务互不影响 */
    @PostMapping("/test-cases/runAllTestCases")
    public ApiResponse<List<TestCaseDto>> runAllTestCases() {
        return ApiResponse.success(testCaseService.runAllTestCases());
    }

    /** 查询指定测试用例的执行历史记录（含步骤日志） */
    @PostMapping("/test-cases/queryRunHistory")
    public ApiResponse<List<RuleRunResultDto>> queryRunHistory(@RequestBody IdRequest req) {
        return ApiResponse.success(testCaseService.queryRunHistory(req));
    }

    // ==================== 选项字典 ====================

    /** 查询所有启用的规则分类列表 */
    @PostMapping("/options/queryCategoryList")
    public ApiResponse<List<CategoryDto>> queryCategoryList() {
        return ApiResponse.success(ruleService.queryCategoryList());
    }

    /** 查询所有启用的预设选项集及其选项子项 */
    @PostMapping("/options/queryPresetSetList")
    public ApiResponse<List<PresetSetDto>> queryPresetSetList() {
        return ApiResponse.success(ruleService.queryPresetSetList());
    }
}
