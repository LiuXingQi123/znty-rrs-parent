package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SecurityInfoBo;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 原业务单元测试隔离 ABS 组件，新身份行为由专用单元和真实 SQL 测试覆盖 */
final class AbsIdentityTestSupport {
    private AbsIdentityTestSupport() {
    }

    /** 为手工组装的服务注入 ABS 依赖，保持原测试只验证自身业务 */
    static <T> T withIdentity(T target) {
        AbsIdentityService identity = mock(AbsIdentityService.class);
        when(identity.findRelated(anyString())).thenReturn(null);
        doAnswer(call -> {
            SecurityInfoBo security = call.getArgument(0);
            security.setIssuerCode(call.getArgument(1));
            security.setIssuer(call.getArgument(2));
            security.setInnerIssuerRating(call.getArgument(3));
            security.setRatingBondissuer(call.getArgument(4));
            security.setRatingBondissuerAgency(call.getArgument(5));
            return null;
        }).when(identity).applySelection(any(), any(), any(), any(), any(), any());
        // 主体联动与提醒已拆到独立服务，原测试沿用放行口径
        AbsCompanySyncService sync = mock(AbsCompanySyncService.class);
        when(sync.allowScheduledSync(any(), any(), any(), any(), anyBoolean())).thenReturn(true);
        when(sync.allowScheduledSync(any(), any(), any(), any(), anyBoolean(), any())).thenReturn(true);
        when(sync.allowCompanySync(any(), any(), any(), any(), anyBoolean())).thenReturn(true);
        injectIfPresent(target, "absIdentityService", identity);
        injectIfPresent(target, "absCompanySyncService", sync);
        return target;
    }

    /** 仅当目标服务声明了该依赖时才注入，避免拆分后的类结构差异导致测试中断 */
    private static <T> void injectIfPresent(T target, String fieldName, Object value) {
        try {
            ReflectionTestUtils.setField(target, fieldName, value);
        } catch (IllegalArgumentException ignored) {
            // 该服务未使用此依赖
        }
    }
}
