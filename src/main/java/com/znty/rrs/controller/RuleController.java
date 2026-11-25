package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.rule.CategoryDto;
import com.znty.rrs.entity.rule.PresetSetDto;
import com.znty.rrs.entity.rule.RuleDto;
import com.znty.rrs.entity.rule.RuleReq;
import com.znty.rrs.entity.testcase.RuleRunResultDto;
import com.znty.rrs.service.RuleService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 风控规则管理控制器
 *
 * 管理规则、脚本和参数，使用 QLExpress 执行脚本并保存执行结果和步骤日志，供测试用例回归使用
 * 路由前缀为 {@code /api/v1/rules}
 */
@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    /** 规则管理服务 */
    @Resource
    private RuleService ruleService;

    // ==================== 规则管理 ====================

    /**
     * 分页查询规则列表，支持按规则名称关键字和启用状态筛选
     *
     * @param req 规则关键字、启用状态和分页条件
     * @return 风控规则列表及分页信息
     */
    @PostMapping("/queryRulePage")
    public ApiResponse<PageResult<RuleDto>> queryRulePage(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.queryRulePage(req));
    }

    /**
     * 按规则 ID 查询规则详情，含关联的输入参数定义和参数可选项
     *
     * @param req 待查询的规则 ID
     * @return 风控规则详情
     */
    @PostMapping("/queryRuleDetail")
    public ApiResponse<RuleDto> queryRuleDetail(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.queryRuleDetail(req));
    }

    /**
     * 新增规则，对规则基础信息、QLExpress 脚本及参数定义做全量保存
     *
     * @param req 规则基本信息、QLExpress 脚本及参数定义
     * @return 新增的规则详情
     */
    @PostMapping("/addRule")
    public ApiResponse<RuleDto> addRule(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.addRule(req));
    }

    /**
     * 编辑规则，对规则基础信息、QLExpress 脚本及参数定义做全量覆盖保存
     *
     * @param req 规则 ID、基本信息、QLExpress 脚本及参数定义
     * @return 更新后的规则详情
     */
    @PostMapping("/editRule")
    public ApiResponse<RuleDto> editRule(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.editRule(req));
    }

    /**
     * 切换规则启用、停用状态（active、disabled），停用后不可被测试用例引用执行
     *
     * @param req 规则 ID 和目标状态
     * @return 更新状态后的规则详情
     */
    @PostMapping("/editRuleStatus")
    public ApiResponse<RuleDto> editRuleStatus(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.editRuleStatus(req));
    }

    /**
     * 软删除规则，标记为已删除状态而非物理删除，保留历史执行记录
     *
     * @param req 待删除的规则 ID
     * @return 删除前的规则详情
     */
    @PostMapping("/deleteRule")
    public ApiResponse<RuleDto> deleteRule(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.deleteRule(req));
    }

    // ==================== 规则执行 ====================

    /**
     * 按规则 ID 和传入的参数值调用 QLExpress 执行规则脚本
     *
     * 记录执行结果及各步骤日志，供历史查询
     *
     * @param req 规则 ID 和本次运行的参数值
     * @return 规则执行结果
     */
    @PostMapping("/executeRule")
    public ApiResponse<RuleRunResultDto> executeRule(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.executeRule(req));
    }

    // ==================== 选项字典 ====================

    /**
     * 查询所有启用状态的规则分类列表，用于规则编辑时选择所属分类
     *
     * @param req 空请求体
     * @return 所有启用的规则分类列表
     */
    @PostMapping("/options/queryCategoryList")
    public ApiResponse<List<CategoryDto>> queryCategoryList(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.queryCategoryList());
    }

    /**
     * 查询所有启用状态的预设选项集及其子选项，用于规则参数类型为枚举时绑定可选值
     *
     * @param req 空请求体
     * @return 启用的预设选项集及对应选项，无启用项时为空列表
     */
    @PostMapping("/options/queryPresetSetList")
    public ApiResponse<List<PresetSetDto>> queryPresetSetList(@RequestBody RuleReq req) {
        return ApiResponse.success(ruleService.queryPresetSetList());
    }
}
