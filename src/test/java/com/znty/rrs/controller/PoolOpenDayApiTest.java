package com.znty.rrs.controller;

import com.znty.rrs.service.PoolOpenDayService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

/**
 * 投资池开放日维护页面接口测试
 */
public class PoolOpenDayApiTest extends ControllerApiTestSupport {

    /** 接口测试客户端。 */
    private MockMvc mockMvc;

    /** 初始化测试环境。 */
    @Before
    public void setUp() {
        PoolOpenDayController controller = new PoolOpenDayController();
        ReflectionTestUtils.setField(controller, "poolOpenDayService", mock(PoolOpenDayService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 验证分页查询路由与统一响应结构。 */
    @Test
    public void shouldSupportQueryPoolOpenDayPage() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/poolOpenDay/queryPoolOpenDayPage", "{}");
    }

    /** 验证新增路由与统一响应结构。 */
    @Test
    public void shouldSupportAddPoolOpenDay() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/poolOpenDay/addPoolOpenDay",
                "{\"poolId\":8,\"beginDate\":\"2026-01-01\",\"endDate\":\"2026-06-30\",\"description\":\"测试区间\"}");
    }

    /** 验证修改路由与统一响应结构。 */
    @Test
    public void shouldSupportEditPoolOpenDay() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/poolOpenDay/editPoolOpenDay",
                "{\"id\":1,\"poolId\":8,\"beginDate\":\"2026-01-01\",\"endDate\":\"2026-12-31\"}");
    }

    /** 验证删除路由与统一响应结构。 */
    @Test
    public void shouldSupportDeletePoolOpenDay() throws Exception {
        assertPostSuccess(mockMvc, "/api/v1/poolOpenDay/deletePoolOpenDay", "{\"id\":1}");
    }
}
