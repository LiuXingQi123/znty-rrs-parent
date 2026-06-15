package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.model.FlowOptionDto;
import com.znty.sirm.model.InvestmentPoolDto;
import com.znty.sirm.model.InvestmentPoolReq;
import com.znty.sirm.model.RoleDto;
import com.znty.sirm.model.UserDto;
import com.znty.sirm.service.InvestmentPoolService;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 投资池维护控制器
 * <p>
 * 负责投资池的完整生命周期管理，投资池采用层级树状结构（根池 → 子池），
 * 支持新建、配置、删除节点以及关联审批流程和操作权限配置。
 * 证券入池/出池调整操作需在投资池体系下发起并经过配置的审批流完成。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/investmentPool")
public class InvestmentPoolController {

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /**
     * 查询全量投资池平铺列表，树层级关系由前端根据父子 ID 自行组装
     */
    @PostMapping("/queryPoolList")
    public ApiResponse<List<InvestmentPoolDto>> queryPoolList(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.queryPoolList(req));
    }

    /**
     * 按投资池 ID 查询单个池的详情，含基础配置、关系配置及权限配置
     */
    @PostMapping("/queryPoolDetail")
    public ApiResponse<InvestmentPoolDto> queryPoolDetail(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.queryPoolDetail(req));
    }

    /**
     * 保存投资池基础配置（名称、描述、关联审批流程等）
     */
    @PostMapping("/editPoolConfig")
    public ApiResponse<InvestmentPoolDto> editPoolConfig(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.editPoolConfig(req));
    }

    /**
     * 保存投资池层级关系配置（父子池归属关系调整）
     */
    @PostMapping("/editPoolRelation")
    public ApiResponse<InvestmentPoolDto> editPoolRelation(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.editPoolRelation(req));
    }

    /**
     * 新建顶级投资池（无父节点），作为投资池树的根节点
     */
    @PostMapping("/addRootPool")
    public ApiResponse<InvestmentPoolDto> addRootPool(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.addRootPool(req));
    }

    /**
     * 在指定父池下新建子投资池，继承父池的部分配置
     */
    @PostMapping("/addChildPool")
    public ApiResponse<InvestmentPoolDto> addChildPool(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.addChildPool(req));
    }

    /**
     * 删除投资池节点，同时级联删除其所有子节点（不可恢复）
     */
    @PostMapping("/deletePoolNode")
    public ApiResponse<InvestmentPoolDto> deletePoolNode(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.deletePoolNode(req));
    }

    /**
     * 查询可用的审批流程下拉选项，用于投资池配置中关联审批流
     */
    @PostMapping("/queryFlowOptionList")
    public ApiResponse<List<FlowOptionDto>> queryFlowOptionList(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.queryFlowOptionList(req));
    }

    /**
     * 初始化系统预置的固定投资池数据，仅在首次部署时调用（树结构由前端组装）
     */
    @PostMapping("/addSeedPoolList")
    public ApiResponse<List<InvestmentPoolDto>> addSeedPoolList(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.addSeedPoolList(req));
    }

    /**
     * 查询系统角色列表，用于投资池权限配置中选择可操作角色
     */
    @PostMapping("/queryRoleList")
    public ApiResponse<List<RoleDto>> queryRoleList(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.queryRoleList(req));
    }

    /**
     * 查询系统人员列表，用于投资池权限配置中指定可操作人员
     */
    @PostMapping("/queryUserList")
    public ApiResponse<List<UserDto>> queryUserList(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.queryUserList(req));
    }

    /**
     * 保存投资池操作权限配置（指定可发起调整申请的角色或人员）
     */
    @PostMapping("/editPoolPermission")
    public ApiResponse<InvestmentPoolDto> editPoolPermission(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.editPoolPermission(req));
    }
}
