package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.entity.flow.FlowOptionDto;
import com.znty.rrs.entity.investmentpool.InvestmentPoolDto;
import com.znty.rrs.entity.investmentpool.InvestmentPoolReq;
import com.znty.rrs.entity.common.RoleDto;
import com.znty.rrs.entity.common.UserDto;
import com.znty.rrs.service.InvestmentPoolService;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 投资池维护控制器
 *
 * 投资池按父子层级组织，支持新增、配置、删除及维护调库流程和操作权限
 */
@RestController
@RequestMapping("/api/v1/investmentPool")
public class InvestmentPoolController {

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /**
     * 查询全量投资池平铺列表，树层级关系由前端根据父子 ID 自行组装
     *
     * @param req 空请求体
     * @return 投资池信息列表
     */
    @PostMapping("/queryPoolList")
    public ApiResponse<List<InvestmentPoolDto>> queryPoolList(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.queryPoolList(req));
    }

    /**
     * 按投资池 ID 查询单个池的详情，含基础配置、关系配置及权限配置
     *
     * @param req 投资池 ID
     * @return 投资池基础配置、关系配置及权限配置
     */
    @PostMapping("/queryPoolDetail")
    public ApiResponse<InvestmentPoolDto> queryPoolDetail(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.queryPoolDetail(req));
    }

    /**
     * 保存投资池基础配置（名称、描述、关联审批流程等）
     *
     * @param req 投资池 ID、待保存的基础配置和操作人
     * @return 更新后的投资池详情
     */
    @PostMapping("/editPoolConfig")
    public ApiResponse<InvestmentPoolDto> editPoolConfig(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.editPoolConfig(req));
    }

    /**
     * 保存投资池的自动入池和出池规则配置
     *
     * @param req 投资池 ID、自动入池和出池规则及操作人
     * @return 更新后的投资池详情
     */
    @PostMapping("/editPoolRelation")
    public ApiResponse<InvestmentPoolDto> editPoolRelation(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.editPoolRelation(req));
    }

    /**
     * 新建顶级投资池（无父节点），作为投资池树的根节点
     *
     * @param req 池名称、类型、编码、可选的模板池及操作人
     * @return 新建的顶级投资池
     */
    @PostMapping("/addRootPool")
    public ApiResponse<InvestmentPoolDto> addRootPool(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.addRootPool(req));
    }

    /**
     * 在指定父池下新建子投资池，继承父池的部分配置
     *
     * @param req 父池 ID、子池名称和编码、配置继承选项及操作人
     * @return 新建的子投资池
     */
    @PostMapping("/addChildPool")
    public ApiResponse<InvestmentPoolDto> addChildPool(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.addChildPool(req));
    }

    /**
     * 删除投资池节点及其所有子节点，同时清理关联的规则和权限
     *
     * @param req 待删除的投资池 ID 和操作人
     * @return 被删除的投资池信息
     */
    @PostMapping("/deletePoolNode")
    public ApiResponse<InvestmentPoolDto> deletePoolNode(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.deletePoolNode(req));
    }

    /**
     * 查询可用的审批流程下拉选项，用于投资池配置中关联审批流
     *
     * @param req 空请求体
     * @return 流程下拉选项
     */
    @PostMapping("/queryFlowOptionList")
    public ApiResponse<List<FlowOptionDto>> queryFlowOptionList(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.queryFlowOptionList(req));
    }

    /**
     * 初始化系统预置的固定投资池数据，仅在首次部署时调用（树结构由前端组装）
     *
     * @param req 操作人信息
     * @return 投资池信息列表
     */
    @PostMapping("/addSeedPoolList")
    public ApiResponse<List<InvestmentPoolDto>> addSeedPoolList(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.addSeedPoolList(req));
    }

    /**
     * 查询系统角色列表，用于投资池权限配置中选择可操作角色
     *
     * @param req 空请求体
     * @return 角色列表
     */
    @PostMapping("/queryRoleList")
    public ApiResponse<List<RoleDto>> queryRoleList(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.queryRoleList(req));
    }

    /**
     * 查询系统人员列表，用于投资池权限配置中指定可操作人员
     *
     * @param req 角色 ID 和人员关键字
     * @return 用户列表
     */
    @PostMapping("/queryUserList")
    public ApiResponse<List<UserDto>> queryUserList(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.queryUserList(req));
    }

    /**
     * 保存投资池操作权限配置（指定可发起调整申请的角色或人员）
     *
     * @param req 投资池 ID、角色和人员权限及操作人
     * @return 更新后的投资池详情
     */
    @PostMapping("/editPoolPermission")
    public ApiResponse<InvestmentPoolDto> editPoolPermission(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.editPoolPermission(req));
    }
}
