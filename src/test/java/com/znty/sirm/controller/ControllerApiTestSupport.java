package com.znty.sirm.controller;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller 接口测试公共支持类
 */
public abstract class ControllerApiTestSupport {

    /** 发送带 JSON 请求体的 POST 请求并校验统一成功响应 */
    protected void assertPostSuccess(MockMvc mockMvc, String path, String body) throws Exception {
        postJson(mockMvc, path, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("success"));
    }

    /** 发送无请求体的 POST 请求并校验统一成功响应 */
    protected void assertPostSuccess(MockMvc mockMvc, String path) throws Exception {
        mockMvc.perform(post(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("success"));
    }

    /** 发送 JSON POST 请求 */
    protected ResultActions postJson(MockMvc mockMvc, String path, String body) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .content(body));
    }
}
