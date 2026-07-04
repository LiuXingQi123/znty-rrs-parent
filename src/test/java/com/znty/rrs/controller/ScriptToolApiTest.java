package com.znty.rrs.controller;

import com.znty.rrs.entity.scripttool.ScriptExecuteResultDto;
import com.znty.rrs.entity.scripttool.ScriptTableGroupDto;
import com.znty.rrs.entity.scripttool.ScriptTaskDto;
import com.znty.rrs.entity.scripttool.ScriptToolReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.service.ScriptToolService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 脚本工具接口测试。
 */
public class ScriptToolApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 脚本工具服务模拟对象。 */
    private ScriptToolService scriptToolService;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        ScriptToolController controller = new ScriptToolController();
        scriptToolService = mock(ScriptToolService.class);
        ReflectionTestUtils.setField(controller, "scriptToolService", scriptToolService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证脚本任务列表查询接口。 */
    @Test
    public void shouldQueryScriptTaskList() throws Exception {
        ScriptTaskDto task = new ScriptTaskDto();
        task.setTaskCode("INIT_SCHEMA");
        task.setTaskName("初始化建表脚本");
        task.setConfirmText("INIT_SCHEMA");
        when(scriptToolService.queryScriptTaskList(org.mockito.Matchers.any(ScriptToolReq.class)))
                .thenReturn(Collections.singletonList(task));

        assertPostSuccess(mockMvc, "/api/v1/scriptTool/queryScriptTaskList", "{}");
    }

    /** 验证脚本任务执行接口。 */
    @Test
    public void shouldExecuteScriptTask() throws Exception {
        ScriptExecuteResultDto result = new ScriptExecuteResultDto();
        result.setTaskCode("INIT_DEMO");
        result.setTaskName("初始化 Demo 数据");
        result.setStatus("success");
        when(scriptToolService.executeScriptTask(org.mockito.Matchers.any(ScriptToolReq.class))).thenReturn(result);

        assertPostSuccess(mockMvc, "/api/v1/scriptTool/executeScriptTask", "{\"taskCode\":\"INIT_DEMO\",\"confirmText\":\"INIT_DEMO\"}");
    }

    /** 验证可清空表分组查询接口。 */
    @Test
    public void shouldQueryClearTableGroupList() throws Exception {
        ScriptTableGroupDto group = new ScriptTableGroupDto();
        group.setGroupCode("security-adjust");
        group.setGroupName("证券池调库运行表");
        when(scriptToolService.queryClearTableGroupList(org.mockito.Matchers.any(ScriptToolReq.class)))
                .thenReturn(Collections.singletonList(group));

        assertPostSuccess(mockMvc, "/api/v1/scriptTool/queryClearTableGroupList", "{}");
    }

    /** 验证清空选中表接口。 */
    @Test
    public void shouldExecuteClearSelectedTables() throws Exception {
        ScriptExecuteResultDto result = new ScriptExecuteResultDto();
        result.setTaskCode("CLEAR_SELECTED_TABLES");
        result.setTaskName("自定义清空表数据");
        result.setStatus("success");
        when(scriptToolService.executeClearSelectedTables(org.mockito.Matchers.any(ScriptToolReq.class))).thenReturn(result);

        assertPostSuccess(mockMvc, "/api/v1/scriptTool/executeClearSelectedTables", "{\"confirmText\":\"CLEAR_SELECTED_TABLES\",\"tableKeys\":[\"znty_rrs.ip_adjust_log\"]}");
    }

    /** 验证重置选中表接口。 */
    @Test
    public void shouldExecuteResetSelectedTables() throws Exception {
        ScriptExecuteResultDto result = new ScriptExecuteResultDto();
        result.setTaskCode("RESET_SELECTED_TABLES");
        result.setTaskName("重置选中表数据");
        result.setStatus("success");
        when(scriptToolService.executeResetSelectedTables(org.mockito.Matchers.any(ScriptToolReq.class))).thenReturn(result);

        assertPostSuccess(mockMvc, "/api/v1/scriptTool/executeResetSelectedTables", "{\"confirmText\":\"RESET_SELECTED_TABLES\",\"tableKeys\":[\"znty_rrs.ip_adjust_log\"]}");
    }

    /** 验证确认文本错误时不会继续执行脚本。 */
    @Test
    public void shouldRejectWrongConfirmText() {
        ScriptToolService service = new ScriptToolService();
        ScriptToolReq req = new ScriptToolReq();
        req.setTaskCode("INIT_SCHEMA");
        req.setConfirmText("WRONG");
        try {
            service.executeScriptTask(req);
            fail("确认文本错误时应抛出业务异常");
        } catch (BizException e) {
            org.junit.Assert.assertTrue(e.getMessage().contains("确认文本不正确"));
        }
    }

    /** 验证未知任务编码不会继续执行脚本。 */
    @Test
    public void shouldRejectUnknownTaskCode() {
        ScriptToolService service = new ScriptToolService();
        ScriptToolReq req = new ScriptToolReq();
        req.setTaskCode("UNKNOWN_TASK");
        req.setConfirmText("UNKNOWN_TASK");
        try {
            service.executeScriptTask(req);
            fail("未知任务编码应抛出业务异常");
        } catch (BizException e) {
            org.junit.Assert.assertTrue(e.getMessage().contains("不支持的脚本任务"));
        }
    }

    /** 验证自定义清空表会拒绝未知表。 */
    @Test
    public void shouldRejectUnknownClearTable() {
        ScriptToolService service = new ScriptToolService();
        ScriptToolReq req = new ScriptToolReq();
        req.setConfirmText("CLEAR_SELECTED_TABLES");
        req.setTableKeys(Collections.singletonList("znty_rrs.not_exists"));
        try {
            service.executeClearSelectedTables(req);
            fail("未知表应抛出业务异常");
        } catch (BizException e) {
            org.junit.Assert.assertTrue(e.getMessage().contains("不支持清空的表"));
        }
    }

    /** 验证重置选中表会校验确认文本。 */
    @Test
    public void shouldRejectWrongResetSelectedConfirmText() {
        ScriptToolService service = new ScriptToolService();
        ScriptToolReq req = new ScriptToolReq();
        req.setConfirmText("CLEAR_SELECTED_TABLES");
        req.setTableKeys(Collections.singletonList("znty_rrs.ip_adjust_log"));
        try {
            service.executeResetSelectedTables(req);
            fail("重置确认文本错误时应抛出业务异常");
        } catch (BizException e) {
            org.junit.Assert.assertTrue(e.getMessage().contains("确认文本不正确"));
        }
    }
}
