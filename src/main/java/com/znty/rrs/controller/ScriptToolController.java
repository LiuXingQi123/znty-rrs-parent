package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.entity.scripttool.ScriptDemoSceneDto;
import com.znty.rrs.entity.scripttool.ScriptExecuteResultDto;
import com.znty.rrs.entity.scripttool.ScriptHealthCheckDto;
import com.znty.rrs.entity.scripttool.ScriptInspectionDto;
import com.znty.rrs.entity.scripttool.ScriptModuleTaskDto;
import com.znty.rrs.entity.scripttool.ScriptOverviewDto;
import com.znty.rrs.entity.scripttool.ScriptTableGroupDto;
import com.znty.rrs.entity.scripttool.ScriptTableRowCountGroupDto;
import com.znty.rrs.entity.scripttool.ScriptTaskDto;
import com.znty.rrs.entity.scripttool.ScriptToolReq;
import com.znty.rrs.entity.scripttool.ScriptToolRunLogDto;
import com.znty.rrs.entity.scripttool.ScriptToolStatusDto;
import com.znty.rrs.service.ScriptToolService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 脚本工具控制器
 *
 * 提供开发演示环境的数据建表、Demo 数据初始化和调库运行态数据清理能力
 */
@RestController
@RequestMapping("/api/v1/scriptTool")
public class ScriptToolController {

    /** 脚本工具服务 */
    @Resource
    private ScriptToolService scriptToolService;

    /**
     * 查询脚本工具开关状态
     *
     * @param req 空请求体
     * @return 脚本工具写操作开关状态，enabled=true 表示开启，enabled=false 表示关闭
     */
    @PostMapping("/queryScriptToolStatus")
    public ApiResponse<ScriptToolStatusDto> queryScriptToolStatus(@RequestBody ScriptToolReq req) {
        ScriptToolStatusDto status = new ScriptToolStatusDto();
        status.setEnabled(scriptToolService.isScriptToolEnabled());
        return ApiResponse.success(status);
    }

    /**
     * 查询脚本工具写操作审计列表
     *
     * @param req 空请求体
     * @return 最近 50 条脚本工具写操作审计记录
     */
    @PostMapping("/queryScriptToolRunLogList")
    public ApiResponse<List<ScriptToolRunLogDto>> queryScriptToolRunLogList(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.queryScriptToolRunLogList(req));
    }

    /**
     * 查询可执行脚本任务列表
     *
     * @param req 空请求体
     * @return 脚本任务列表
     */
    @PostMapping("/queryScriptTaskList")
    public ApiResponse<List<ScriptTaskDto>> queryScriptTaskList(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.queryScriptTaskList(req));
    }

    /**
     * 查询 SQL 脚本清单与依赖总览
     *
     * @param req 空请求体
     * @return 脚本、任务与库表概况
     */
    @PostMapping("/queryScriptOverview")
    public ApiResponse<ScriptOverviewDto> queryScriptOverview(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.queryScriptOverview(req));
    }

    /**
     * 查询脚本工具环境健康检查结果
     *
     * @param req 空请求体
     * @return 脚本工具健康检查汇总
     */
    @PostMapping("/queryHealthCheck")
    public ApiResponse<ScriptHealthCheckDto> queryHealthCheck(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.queryHealthCheck(req));
    }

    /**
     * 查询数据库结构差异检查结果
     *
     * @param req 空请求体
     * @return 检查项明细及汇总
     */
    @PostMapping("/querySchemaDiff")
    public ApiResponse<ScriptInspectionDto> querySchemaDiff(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.querySchemaDiff(req));
    }

    /**
     * 查询业务数据完整性检查结果
     *
     * @param req 空请求体
     * @return 检查项明细及汇总
     */
    @PostMapping("/queryDataIntegrity")
    public ApiResponse<ScriptInspectionDto> queryDataIntegrity(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.queryDataIntegrity(req));
    }

    /**
     * 查询可清空表分组列表
     *
     * @param req 空请求体
     * @return 脚本表分组列表
     */
    @PostMapping("/queryClearTableGroupList")
    public ApiResponse<List<ScriptTableGroupDto>> queryClearTableGroupList(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.queryClearTableGroupList(req));
    }

    /**
     * 查询各业务库表记录数
     *
     * @param req 空请求体
     * @return 脚本表记录数分组列表
     */
    @PostMapping("/queryTableRowCounts")
    public ApiResponse<List<ScriptTableRowCountGroupDto>> queryTableRowCounts(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.queryTableRowCounts(req));
    }

    /**
     * 执行指定脚本任务
     *
     * @param req 脚本任务编码、确认文本和操作人信息
     * @return 脚本任务的执行结果
     */
    @PostMapping("/executeScriptTask")
    public ApiResponse<ScriptExecuteResultDto> executeScriptTask(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.executeScriptTask(req));
    }

    /**
     * 清空选中的表数据
     *
     * @param req 所选表标识、确认文本和操作人信息
     * @return 脚本任务的执行结果
     */
    @PostMapping("/executeClearSelectedTables")
    public ApiResponse<ScriptExecuteResultDto> executeClearSelectedTables(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.executeClearSelectedTables(req));
    }

    /**
     * 重置选中的表数据
     *
     * @param req 所选表标识、确认文本和操作人信息
     * @return 脚本任务的执行结果
     */
    @PostMapping("/executeResetSelectedTables")
    public ApiResponse<ScriptExecuteResultDto> executeResetSelectedTables(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.executeResetSelectedTables(req));
    }

    /**
     * 查询模块级重置任务列表
     *
     * @param req 空请求体
     * @return 可执行的模块重置任务配置列表
     */
    @PostMapping("/queryModuleResetTaskList")
    public ApiResponse<List<ScriptModuleTaskDto>> queryModuleResetTaskList(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.queryModuleResetTaskList(req));
    }

    /**
     * 执行模块级重置任务
     *
     * @param req 模块编码、确认文本和操作人信息
     * @return 脚本任务的执行结果
     */
    @PostMapping("/executeModuleResetTask")
    public ApiResponse<ScriptExecuteResultDto> executeModuleResetTask(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.executeModuleResetTask(req));
    }

    /**
     * 查询 Demo 场景列表
     *
     * @param req 空请求体
     * @return 可用的 Demo 场景配置列表
     */
    @PostMapping("/queryDemoSceneList")
    public ApiResponse<List<ScriptDemoSceneDto>> queryDemoSceneList(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.queryDemoSceneList(req));
    }

    /**
     * 执行 Demo 场景生成
     *
     * @param req 场景编码、确认文本和操作人信息
     * @return 脚本任务的执行结果
     */
    @PostMapping("/executeDemoScene")
    public ApiResponse<ScriptExecuteResultDto> executeDemoScene(@RequestBody ScriptToolReq req) {
        return ApiResponse.success(scriptToolService.executeDemoScene(req));
    }
}
