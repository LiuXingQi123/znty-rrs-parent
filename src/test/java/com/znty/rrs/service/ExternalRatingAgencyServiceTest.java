package com.znty.rrs.service;

import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.ExternalRatingAgencyMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 外部评级机构配置服务测试。 */
public class ExternalRatingAgencyServiceTest {

    /** 配置数据访问组件。 */
    private ExternalRatingAgencyMapper externalRatingAgencyMapper;
    /** 待测试服务。 */
    private ExternalRatingAgencyService service;

    /** 初始化测试依赖。 */
    @Before
    public void setUp() {
        externalRatingAgencyMapper = mock(ExternalRatingAgencyMapper.class);
        service = new ExternalRatingAgencyService();
        ReflectionTestUtils.setField(service, "externalRatingAgencyMapper", externalRatingAgencyMapper);
    }

    /** 有效配置应原样返回给业务调用方。 */
    @Test
    public void queryRequiredAgencyCodeList_ShouldReturnConfiguredCodes() {
        when(externalRatingAgencyMapper.queryActiveAgencyCodeList()).thenReturn(Arrays.asList("2", "21"));

        assertThat(service.queryRequiredAgencyCodeList()).containsExactly("2", "21");
    }

    /** 空配置应阻断依赖认可外评口径的业务。 */
    @Test
    public void queryRequiredAgencyCodeList_ShouldRejectEmptyConfiguration() {
        when(externalRatingAgencyMapper.queryActiveAgencyCodeList()).thenReturn(Collections.<String>emptyList());

        assertThatThrownBy(() -> service.queryRequiredAgencyCodeList())
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未配置有效外部评级机构");
    }

    /** Mapper 返回 null 时普通查询应归一为空列表。 */
    @Test
    public void queryActiveAgencyCodeList_ShouldNormalizeNull() {
        when(externalRatingAgencyMapper.queryActiveAgencyCodeList()).thenReturn(null);

        assertThat(service.queryActiveAgencyCodeList()).isEmpty();
    }
}
