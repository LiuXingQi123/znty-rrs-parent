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
 */
@RestController
@RequestMapping("/api/v1/investmentPool")
public class InvestmentPoolController {

    @Resource
    private InvestmentPoolService investmentPoolService;

    /**
     * 查询投资池列表（树结构由前端组装）
     */
    @PostMapping("/queryPoolList")
    public ApiResponse<List<InvestmentPoolDto>> queryPoolList(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.queryPoolList(req));
    }

    /**
     * 查询投资池详情
     */
    @PostMapping("/queryPoolDetail")
    public ApiResponse<InvestmentPoolDto> queryPoolDetail(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.queryPoolDetail(req));
    }

    /**
     * 保存投资池基础配置
     */
    @PostMapping("/editPoolConfig")
    public ApiResponse<InvestmentPoolDto> editPoolConfig(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.editPoolConfig(req));
    }

    /**
     * 保存投资池关系配置
     */
    @PostMapping("/editPoolRelation")
    public ApiResponse<InvestmentPoolDto> editPoolRelation(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.editPoolRelation(req));
    }

    /**
     * 添加顶级投资池
     */
    @PostMapping("/addRootPool")
    public ApiResponse<InvestmentPoolDto> addRootPool(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.addRootPool(req));
    }

    /**
     * 添加子投资池
     */
    @PostMapping("/addChildPool")
    public ApiResponse<InvestmentPoolDto> addChildPool(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.addChildPool(req));
    }

    /**
     * 删除投资池节点及子节点
     */
    @PostMapping("/deletePoolNode")
    public ApiResponse<InvestmentPoolDto> deletePoolNode(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.deletePoolNode(req));
    }

    /**
     * 查询流程下拉选项
     */
    @PostMapping("/queryFlowOptionList")
    public ApiResponse<List<FlowOptionDto>> queryFlowOptionList(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.queryFlowOptionList(req));
    }

    /**
     * 初始化固定投资池列表（树结构由前端组装）
     */
    @PostMapping("/addSeedPoolList")
    public ApiResponse<List<InvestmentPoolDto>> addSeedPoolList(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.addSeedPoolList(req));
    }

    /**
     * 查询角色列表
     */
    @PostMapping("/queryRoleList")
    public ApiResponse<List<RoleDto>> queryRoleList(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.queryRoleList(req));
    }

    /**
     * 查询人员列表
     */
    @PostMapping("/queryUserList")
    public ApiResponse<List<UserDto>> queryUserList(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.queryUserList(req));
    }

    /**
     * 保存投资池权限配置
     */
    @PostMapping("/editPoolPermission")
    public ApiResponse<InvestmentPoolDto> editPoolPermission(@RequestBody InvestmentPoolReq req) {
        return ApiResponse.success(investmentPoolService.editPoolPermission(req));
    }
}
