package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.IdRequest;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.model.CategoryDto;
import com.znty.sirm.model.PresetSetDto;
import com.znty.sirm.model.RuleDto;
import com.znty.sirm.model.RuleReq;
import com.znty.sirm.model.RuleRunResultDto;
import com.znty.sirm.service.RuleService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 规则管理 REST 接口，路由前缀 /api/v1/rules。
 */
@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    @Resource
    private RuleService ruleService;

    // ==================== 规则管理 ====================

    /** 分页查询规则列表，支持按关键字和状态筛选 */
    @PostMapping("/queryRulePage")
    public ApiResponse<PageResult<RuleDto>> queryRulePage(@RequestBody(required = false) RuleReq req) {
        return ApiResponse.success(ruleService.queryRulePage(req));
    }

    /** 按规则 ID 查询规则详情，含关联参数和选项 */
    @PostMapping("/queryRuleDetail")
    public ApiResponse<RuleDto> queryRuleDetail(@RequestBody IdRequest req) {
        return ApiResponse.success(ruleService.queryRuleDetail(req));
    }

    /** 新增或编辑规则，含脚本和参数的全量保存 */
    @PostMapping("/saveRule")
    public ApiResponse<RuleDto> saveRule(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.saveRule(req));
    }

    /** 更新规则启用状态（active / disabled） */
    @PostMapping("/updateRuleStatus")
    public ApiResponse<RuleDto> updateRuleStatus(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.updateRuleStatus(req));
    }

    /** 软删除规则（标记删除，不物理删除） */
    @PostMapping("/deleteRule")
    public ApiResponse<RuleDto> deleteRule(@RequestBody IdRequest req) {
        return ApiResponse.success(ruleService.deleteRule(req));
    }

    // ==================== 规则执行 ====================

    /** 按规则 ID 和输入参数临时执行规则，返回执行日志和结果 */
    @PostMapping("/rule-runs/executeRule")
    public ApiResponse<RuleRunResultDto> executeRule(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.executeRule(req));
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
