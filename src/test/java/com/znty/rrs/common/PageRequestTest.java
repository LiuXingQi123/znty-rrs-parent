package com.znty.rrs.common;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 分页请求参数测试 */
public class PageRequestTest {

    /** 验证每页条数上限 */
    @Test
    public void shouldCapPageSizeAt4000() {
        PageRequest request = new PageRequest();

        request.setPageSize(3999);
        assertThat(request.getPageSize()).isEqualTo(3999);
        request.setPageSize(4000);
        assertThat(request.getPageSize()).isEqualTo(4000);
        request.setPageSize(4001);
        assertThat(request.getPageSize()).isEqualTo(4000);
    }

    /** 验证空值和零使用默认条数 */
    @Test
    public void shouldDefaultInvalidPageSizeTo20() {
        PageRequest request = new PageRequest();

        request.setPageSize(null);
        assertThat(request.getPageSize()).isEqualTo(20);
        request.setPageSize(0);
        assertThat(request.getPageSize()).isEqualTo(20);
    }
}
