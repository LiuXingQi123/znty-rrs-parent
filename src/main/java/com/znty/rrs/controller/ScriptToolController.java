package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.entity.scripttool.ScriptDemoSceneDto;
import com.znty.rrs.entity.scripttool.ScriptExecuteResultDto;
import com.znty.rrs.entity.scripttool.ScriptHealthCheckDto;
import com.znty.rrs.entity.scripttool.ScriptModuleTaskDto;
import com.znty.rrs.entity.scripttool.ScriptOverviewDto;
import com.znty.rrs.entity.scripttool.ScriptTableGroupDto;
import com.znty.rrs.entity.scripttool.ScriptTaskDto;
import com.znty.rrs.entity.scripttool.ScriptToolReq;
import com.znty.rrs.service.ScriptToolService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 脚本工具控制器。
 * <p>提供开发演示环境的数据建表、Demo 数据初始化和调库运行态数据清理能力。</p>
 */
@RestController
@RequestMapping("/api/v1/scriptTool")
public class ScriptToolController {

    /** 脚本工具服务 */
    @Resource
    private ScriptToolService scriptToolService;

    /**
     * 查询可执行脚本任务列表。
     */
    @PostMapping("/queryScriptTaskList")
    public ApiResponse<List<ScriptTaskDto>> queryScriptTaskList(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.queryScriptTaskList(req));
    }

    /**
     * 查询 SQL 脚本清单与依赖总览。
     */
    @PostMapping("/queryScriptOverview")
    public ApiResponse<ScriptOverviewDto> queryScriptOverview(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.queryScriptOverview(req));
    }

    /**
     * 查询脚本工具环境健康检查结果。
     */
    @PostMapping("/queryHealthCheck")
    public ApiResponse<ScriptHealthCheckDto> queryHealthCheck(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.queryHealthCheck(req));
    }

    /**
     * 查询可清空表分组列表。
     */
    @PostMapping("/queryClearTableGroupList")
    public ApiResponse<List<ScriptTableGroupDto>> queryClearTableGroupList(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.queryClearTableGroupList(req));
    }

    /**
     * 执行指定脚本任务。
     */
    @PostMapping("/executeScriptTask")
    public ApiResponse<ScriptExecuteResultDto> executeScriptTask(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.executeScriptTask(req));
    }

    /**
     * 清空选中的表数据。
     */
    @PostMapping("/executeClearSelectedTables")
    public ApiResponse<ScriptExecuteResultDto> executeClearSelectedTables(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.executeClearSelectedTables(req));
    }

    /**
     * 重置选中的表数据。
     */
    @PostMapping("/executeResetSelectedTables")
    public ApiResponse<ScriptExecuteResultDto> executeResetSelectedTables(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.executeResetSelectedTables(req));
    }

    /**
     * 查询模块级重置任务列表。
     */
    @PostMapping("/queryModuleResetTaskList")
    public ApiResponse<List<ScriptModuleTaskDto>> queryModuleResetTaskList(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.queryModuleResetTaskList(req));
    }

    /**
     * 执行模块级重置任务。
     */
    @PostMapping("/executeModuleResetTask")
    public ApiResponse<ScriptExecuteResultDto> executeModuleResetTask(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.executeModuleResetTask(req));
    }

    /**
     * 查询 Demo 场景列表。
     */
    @PostMapping("/queryDemoSceneList")
    public ApiResponse<List<ScriptDemoSceneDto>> queryDemoSceneList(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.queryDemoSceneList(req));
    }

    /**
     * 执行 Demo 场景生成。
     */
    @PostMapping("/executeDemoScene")
    public ApiResponse<ScriptExecuteResultDto> executeDemoScene(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.executeDemoScene(req));
    }
}
