package com.znty.rrs.service;

import com.znty.rrs.entity.securitypooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** 当前主档对比接口的取数边界测试。 */
public class SecurityMasterDetailServiceTest {

    /** 携带历史申请 ID 仍只读取主档，不查询快照或调用调库逻辑。 */
    @Test
    public void shouldReadMasterWithoutSnapshotEvenWithAdjustLogId() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        SecurityPoolAdjustReq req = new SecurityPoolAdjustReq();
        req.setSecurityCode(" BOND1.IB ");
        req.setAdjustLogId(99L);
        SecurityInfoDetailDto master = new SecurityInfoDetailDto();
        master.setWindCode("BOND1.IB");
        master.setMaturityDate("20290318");
        master.setRatingBondissuer("AAA");
        when(mapper.querySecurityDetail("BOND1.IB")).thenReturn(master);

        assertThat(service.querySecurityMasterDetail(req)).isSameAs(master);
        assertThat(master.getMaturityDate()).isEqualTo("20290318");
        verify(mapper).querySecurityDetail("BOND1.IB");
        verifyNoMoreInteractions(mapper);
    }

    /** 主档缺失或证券代码为空时明确报错，不使用历史快照补值。 */
    @Test
    public void shouldRejectMissingCodeAndMissingMaster() {
        SecurityPoolAdjustMapper mapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", mapper);
        SecurityPoolAdjustReq req = new SecurityPoolAdjustReq();
        assertThatThrownBy(() -> service.querySecurityMasterDetail(req))
                .isInstanceOf(BizException.class).hasMessage("证券代码不能为空");
        req.setSecurityCode(" ");
        assertThatThrownBy(() -> service.querySecurityMasterDetail(req))
                .isInstanceOf(BizException.class).hasMessage("证券代码不能为空");
        req.setSecurityCode("MISSING.IB");
        assertThatThrownBy(() -> service.querySecurityMasterDetail(req))
                .isInstanceOf(BizException.class).hasMessageContaining("MISSING.IB");
        verify(mapper).querySecurityDetail("MISSING.IB");
        verifyNoMoreInteractions(mapper);
    }
}
