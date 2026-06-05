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
 * 风控规则管理控制器
 * <p>
 * 负责风控规则的完整 CRUD 管理及 QLExpress 脚本的在线执行。
 * 规则由脚本代码和参数定义组成，脚本使用 QLExpress 引擎执行，
 * 执行结果和步骤日志会持久化记录，支持通过测试用例进行回归验证。
 * 路由前缀 /api/v1/rules。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    @Resource
    private RuleService ruleService;

    // ==================== 规则管理 ====================

    /**
     * 分页查询规则列表，支持按规则名称关键字和启用状态筛选
     */
    @PostMapping("/queryRulePage")
    public ApiResponse<PageResult<RuleDto>> queryRulePage(@RequestBody(required = false) RuleReq req) {
        return ApiResponse.success(ruleService.queryRulePage(req));
    }

    /**
     * 按规则 ID 查询规则详情，含关联的输入参数定义和参数可选项
     */
    @PostMapping("/queryRuleDetail")
    public ApiResponse<RuleDto> queryRuleDetail(@RequestBody IdRequest req) {
        return ApiResponse.success(ruleService.queryRuleDetail(req));
    }

    /**
     * 新增或编辑规则，对规则基础信息、QLExpress 脚本及参数定义做全量覆盖保存
     * <p>req.id 为空时新增，非空时更新。</p>
     */
    @PostMapping("/addOrEditRule")
    public ApiResponse<RuleDto> addOrEditRule(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.addOrEditRule(req));
    }

    /**
     * 切换规则启用/停用状态（active / disabled），停用后不可被测试用例引用执行
     */
    @PostMapping("/editRuleStatus")
    public ApiResponse<RuleDto> editRuleStatus(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.editRuleStatus(req));
    }

    /**
     * 软删除规则，标记为已删除状态而非物理删除，保留历史执行记录
     */
    @PostMapping("/deleteRule")
    public ApiResponse<RuleDto> deleteRule(@RequestBody IdRequest req) {
        return ApiResponse.success(ruleService.deleteRule(req));
    }

    // ==================== 规则执行 ====================

    /**
     * 临时执行规则：按规则 ID 和传入的参数值调用 QLExpress 引擎运行规则脚本，
     * 返回执行结果和每一步的日志明细，执行记录同时持久化到历史表
     */
    @PostMapping("/rule-runs/executeRule")
    public ApiResponse<RuleRunResultDto> executeRule(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.executeRule(req));
    }

    // ==================== 选项字典 ====================

    /**
     * 查询所有启用状态的规则分类列表，用于规则编辑时选择所属分类
     */
    @PostMapping("/options/queryCategoryList")
    public ApiResponse<List<CategoryDto>> queryCategoryList() {
        return ApiResponse.success(ruleService.queryCategoryList());
    }

    /**
     * 查询所有启用状态的预设选项集及其子选项，用于规则参数类型为枚举时绑定可选值
     */
    @PostMapping("/options/queryPresetSetList")
    public ApiResponse<List<PresetSetDto>> queryPresetSetList() {
        return ApiResponse.success(ruleService.queryPresetSetList());
    }
}
