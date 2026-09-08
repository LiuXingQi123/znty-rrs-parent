package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.BondReminderDto;
import com.znty.rrs.mapper.BondReminderMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.schedule.ScheduledTaskResult;
import java.util.Collections;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 主体不在池债券提醒测试。 */
public class BondIssuerNotInCompanyPoolReminderServiceTest {

    /** 验证跨池映射扫描，并明确记录通知未接入。 */
    @Test
    public void executeShouldScanConfiguredPoolMapping() {
        BondReminderMapper reminderMapper = mock(BondReminderMapper.class);
        ScheduledTaskMapper taskMapper = mock(ScheduledTaskMapper.class);
        BondIssuerNotInCompanyPoolReminderService service =
                new BondIssuerNotInCompanyPoolReminderService();
        ReflectionTestUtils.setField(service, "bondReminderMapper", reminderMapper);
        ReflectionTestUtils.setField(service, "scheduledTaskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "reminderDeliveryService", new ScheduledReminderDeliveryService());
        SysScheduledTaskBo conf = new SysScheduledTaskBo();
        conf.setParamJson("{\"mappings\":[{\"bondPoolId\":17,\"companyPoolId\":15}]}");
        BondReminderDto row = new BondReminderDto();
        row.setSecurityCode("SEC001");
        row.setSecurityShortName("测试债");
        when(taskMapper.queryTaskByCode(BondIssuerNotInCompanyPoolReminderService.TASK_CODE))
                .thenReturn(conf);
        when(reminderMapper.queryIssuerNotInCompanyPoolBondList(17L, 15L))
                .thenReturn(Collections.singletonList(row));

        ScheduledTaskResult result = service.execute();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAffectedCount()).isEqualTo(1);
        assertThat(result.getMessage()).contains("通知通道未接入");
        assertThat(result.getDetailLog()).contains("任务开始：【主体库内债券主体不在池债邮件提醒】")
                .contains("扫描条件：ip_pool_status.is_deleted=0、audit_status=20、category_type=bond")
                .contains("债券在 bondPoolId，发行主体不在对应 companyPoolId 的生效主体记录中")
                .contains("扫描范围：池映射 1 组")
                .contains("映射 bondPoolId=17，companyPoolId=15，命中 1 条")
                .contains("扫描完成：主体不在池债券候选 1 条")
                .contains("任务结束（成功）：扫描映射 1 组，候选 1 条，通知状态=待接入");
        verify(reminderMapper).queryIssuerNotInCompanyPoolBondList(17L, 15L);
    }
}
