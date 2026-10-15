package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.common.SecurityMarketMappingDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.SecurityMarketMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Arrays;
import java.util.Collections;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 统一市场查询的错误定位与单市场校验测试。 */
public class SecurityMarketServiceTest {
    /** 待测服务。 */
    private SecurityMarketService service;
    /** 市场映射访问桩。 */
    private SecurityMarketMapper mapper;

    /** 创建独立查询组件。 */
    @Before
    public void setUp() {
        mapper = mock(SecurityMarketMapper.class);
        service = new SecurityMarketService();
        ReflectionTestUtils.setField(service, "securityMarketMapper", mapper);
    }

    /** 同券沪市代码不能使银行间代码通过仅沪市的池。 */
    @Test
    public void shouldValidateOnlyCurrentMarket() {
        when(mapper.queryMarketMappingList(anyList())).thenReturn(Collections.singletonList(mapping("A.IB", "NIB")));
        SecurityInfoBo security = new SecurityInfoBo();
        security.setWindCode("A.IB");
        assertThat(service.queryMarketCode("A.IB")).isEqualTo("NIB");
        assertThat(service.matchesPoolMarkets("[\"SSE\"]", security)).isFalse();
        assertThat(service.matchesPoolMarkets("[\"NIB\"]", security)).isTrue();
    }

    /** 不用代码后缀推断市场，源数据错误包含代码。 */
    @Test
    public void shouldRejectMissingUnsupportedAndDuplicateMappings() {
        when(mapper.queryMarketMappingList(anyList())).thenReturn(Collections.singletonList(mapping("A.SH", null)));
        assertThatThrownBy(() -> service.queryMarketCode("A.SH")).isInstanceOf(BizException.class).hasMessageContaining("A.SH").hasMessageContaining("缺少");
        when(mapper.queryMarketMappingList(anyList())).thenReturn(Collections.singletonList(mapping("A.SH", "BAD")));
        assertThatThrownBy(() -> service.queryMarketCode("A.SH")).hasMessageContaining("A.SH").hasMessageContaining("BAD");
        when(mapper.queryMarketMappingList(anyList())).thenReturn(Arrays.asList(mapping("A.SH", "SSE"), mapping("A.SH", "SSE")));
        assertThatThrownBy(() -> service.queryMarketCode("A.SH")).hasMessageContaining("重复").hasMessageContaining("A.SH");
    }

    /** 银行间只接受 NIB，不将旧编码静默转换为合法市场。 */
    @Test
    public void shouldRejectLegacyInterbankCodes() {
        for (String legacy : Arrays.asList("CIBM", "IB")) {
            when(mapper.queryMarketMappingList(anyList())).thenReturn(Collections.singletonList(mapping("A.IB", legacy)));
            assertThatThrownBy(() -> service.queryMarketCode("A.IB"))
                    .isInstanceOf(BizException.class).hasMessageContaining("A.IB").hasMessageContaining(legacy);
        }
    }

    /** 临时登记市场与正式证券统一使用 NIB。 */
    @Test
    public void shouldReadExplicitTemporaryMarket() {
        SecurityMarketMappingDto temp = mapping("TMP.SH", "NIB");
        temp.setSecuritySource("temporary");
        when(mapper.queryMarketMappingList(anyList())).thenReturn(Collections.singletonList(temp));
        assertThat(service.queryMarketCode("TMP.SH")).isEqualTo("NIB");
    }

    /** 构造一行外部映射。 */
    private SecurityMarketMappingDto mapping(String code, String market) {
        SecurityMarketMappingDto dto = new SecurityMarketMappingDto();
        dto.setWindCode(code);
        dto.setRawMarketCode(market);
        dto.setSecuritySource("official");
        return dto;
    }
}
