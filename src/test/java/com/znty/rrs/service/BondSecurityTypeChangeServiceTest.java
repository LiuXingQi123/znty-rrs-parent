package com.znty.rrs.service;

import com.znty.rrs.entity.schedule.BondSecurityTypeChangeDto;
import com.znty.rrs.mapper.BondSecurityMaintenanceMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import java.util.Collections;
import java.util.Date;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 债券类型变更任务测试。 */
public class BondSecurityTypeChangeServiceTest {

    /** 验证普通池与 CRMW 池均同步池状态及当前关联日志。 */
    @Test
    public void executeShouldUpdateBothPoolTablesAndCurrentLogs() {
        BondSecurityMaintenanceMapper mapper = mock(BondSecurityMaintenanceMapper.class);
        BondSecurityTypeChangeService service = new BondSecurityTypeChangeService();
        ReflectionTestUtils.setField(service, "bondSecurityMaintenanceMapper", mapper);
        BondSecurityTypeChangeDto normal = buildRow(1L, 11L, "old_a", "new_a");
        BondSecurityTypeChangeDto crmw = buildRow(2L, 22L, "old_b", "new_b");
        when(mapper.queryPoolSecurityTypeChangeList()).thenReturn(Collections.singletonList(normal));
        when(mapper.queryCrmwPoolSecurityTypeChangeList()).thenReturn(Collections.singletonList(crmw));
        when(mapper.editPoolSecurityType(eq(normal), any(Date.class))).thenReturn(1);
        when(mapper.editCrmwPoolSecurityType(eq(crmw), any(Date.class))).thenReturn(1);
        when(mapper.editAdjustLogSecurityType(eq(11L), eq("new_a"), any(Date.class))).thenReturn(1);
        when(mapper.editAdjustLogSecurityType(eq(22L), eq("new_b"), any(Date.class))).thenReturn(1);

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(2);
        assertThat(result.getDetailLog()).contains("任务开始：【债券类型变更】")
                .contains("扫描条件：ip_pool_status/ip_pool_status_crmw.is_deleted=0、audit_status=20")
                .contains("新旧 security_type 均属于 bond 大类且不相等")
                .contains("扫描完成：普通池候选 1 条，CRMW 池候选 1 条")
                .contains("同步成功：表=ip_pool_status，poolStatusId=1")
                .contains("同步成功：表=ip_pool_status_crmw，poolStatusId=2")
                .contains("任务结束（成功）：普通池更新 1 条，CRMW 池更新 1 条，跳过 0 条");
        verify(mapper).editPoolSecurityType(eq(normal), any(Date.class));
        verify(mapper).editCrmwPoolSecurityType(eq(crmw), any(Date.class));
        verify(mapper).editAdjustLogSecurityType(eq(11L), eq("new_a"), any(Date.class));
        verify(mapper).editAdjustLogSecurityType(eq(22L), eq("new_b"), any(Date.class));
    }

    /** 验证同步过程异常时任务返回失败，供事务代理统一回滚本轮修改。 */
    @Test
    public void executeShouldReturnFailureWhenUpdateThrows() {
        BondSecurityMaintenanceMapper mapper = mock(BondSecurityMaintenanceMapper.class);
        BondSecurityTypeChangeService service = new BondSecurityTypeChangeService();
        ReflectionTestUtils.setField(service, "bondSecurityMaintenanceMapper", mapper);
        BondSecurityTypeChangeDto normal = buildRow(1L, 11L, "old_a", "new_a");
        when(mapper.queryPoolSecurityTypeChangeList()).thenReturn(Collections.singletonList(normal));
        when(mapper.queryCrmwPoolSecurityTypeChangeList()).thenReturn(Collections.<BondSecurityTypeChangeDto>emptyList());
        when(mapper.editPoolSecurityType(eq(normal), any(Date.class)))
                .thenThrow(new IllegalStateException("模拟更新异常"));

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("模拟更新异常");
        assertThat(result.getDetailLog()).contains("任务结束（失败）");
    }

    /** 构建类型变更记录。 */
    private BondSecurityTypeChangeDto buildRow(Long poolStatusId, Long adjustLogId,
                                               String oldType, String newType) {
        BondSecurityTypeChangeDto row = new BondSecurityTypeChangeDto();
        row.setPoolStatusId(poolStatusId);
        row.setAdjustLogId(adjustLogId);
        row.setSecurityCode("SEC" + poolStatusId);
        row.setOldSecurityType(oldType);
        row.setNewSecurityType(newType);
        return row;
    }
}
