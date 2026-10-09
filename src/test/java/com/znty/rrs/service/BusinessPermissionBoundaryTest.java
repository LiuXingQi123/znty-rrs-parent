package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.bo.FundAdjustStepBo;
import com.znty.rrs.entity.bo.IpGradeRuleAlertBo;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustReq;
import com.znty.rrs.entity.securitypooladjustflow.SecurityPoolAdjustAuditReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustReq;
import com.znty.rrs.entity.forbiddenabspooladjust.ForbiddenAbsPoolAdjustReq;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustReq;
import com.znty.rrs.entity.crmwpooladjustflow.CrmwPoolAdjustAuditReq;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustReq;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustAuditReq;
import com.znty.rrs.entity.graderulealert.GradeRuleAlertReq;
import com.znty.rrs.entity.sysattachment.SysAttachmentReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.mapper.ForbiddenPoolAdjustMapper;
import com.znty.rrs.mapper.GradeRuleAlertMapper;
import com.znty.rrs.mapper.SysAttachmentMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.FundPoolAdjustMapper;
import com.znty.rrs.entity.bo.FundAdjustLogBo;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 直接调用各领域服务，验证入口与业务操作解耦、原管理员审批规则及附件分表。 */
public class BusinessPermissionBoundaryTest {
    /** 详情接口仅校验各自业务参数，不读取页面入口。 */
    @Test public void detailReadsShouldValidateBusinessParametersWithoutRequiringGrant() {
        assertThatThrownBy(() -> new SecurityPoolAdjustService().querySecurityDetail(new SecurityPoolAdjustReq()))
                .isInstanceOf(BizException.class).hasMessageContaining("证券代码不能为空");
        assertThatThrownBy(() -> new ForbiddenPoolAdjustService().queryCompanyDetail(new ForbiddenPoolAdjustReq()))
                .isInstanceOf(BizException.class).hasMessageContaining("主体代码不能为空");
        assertThatThrownBy(() -> new ForbiddenAbsPoolAdjustService().querySecurityDetail(new ForbiddenAbsPoolAdjustReq()))
                .isInstanceOf(BizException.class).hasMessageContaining("证券代码不能为空");
        assertThatThrownBy(() -> new CrmwPoolAdjustService().queryCrmwDetail(new CrmwPoolAdjustReq()))
                .isInstanceOf(BizException.class).hasMessageContaining("CRMW代码不能为空");
        assertThatThrownBy(() -> new FundPoolAdjustService().queryFundDetail(new FundPoolAdjustReq()))
                .isInstanceOf(BizException.class).hasMessageContaining("基金代码不能为空");
    }

    /** 共享提醒可正常查询及处理，并保留实际操作人的审计信息。 */
    @Test public void sharedAlertsShouldNotRequireBusinessEntry() {
        GradeRuleAlertMapper alerts = mock(GradeRuleAlertMapper.class);
        GradeRuleAlertService service = new GradeRuleAlertService();
        ReflectionTestUtils.setField(service, "gradeRuleAlertMapper", alerts);
        IpGradeRuleAlertBo row = new IpGradeRuleAlertBo();
        row.setId(1L);
        row.setAlertStatus("00");
        GradeRuleAlertReq alert = new GradeRuleAlertReq();
        alert.setId(1L);
        alert.setCurrentUserId("2");
        alert.setCurrentUserName("当前处理人");
        when(alerts.queryAlertPage(alert)).thenReturn(Collections.singletonList(row));
        when(alerts.queryAlertById(1L)).thenReturn(row);
        when(alerts.editAlertProcessed(row)).thenReturn(1);
        try {
            assertThat(service.queryAlertPage(alert).getRecords()).extracting("id").containsExactly(1L);
        } finally {
            // 模拟 Mapper 未执行实际 SQL，清理分页线程状态
            PageHelper.clearPage();
        }
        assertThat(service.editAlertProcessed(alert).getDealUserId()).isEqualTo("2");
        verify(alerts).editAlertProcessed(row);
        assertThat(row.getDealUserId()).isEqualTo("2");
        assertThat(row.getDealUserName()).isEqualTo("当前处理人");
        assertThat(row.getDealTime()).isNotNull();
        assertThat(row.getUpdtTime()).isEqualTo(row.getDealTime());
    }

    /** 详情直接读取指定步骤，即使当前演示用户不是该步骤处理人。 */
    @Test public void bondStepsShouldLoadWithoutUserOrMatterAuthorization() {
        SecurityPoolAdjustMapper steps = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService service = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", steps);
        IpAdjustStepBo step = new IpAdjustStepBo();
        step.setId(7L);
        step.setHandlerId("9");
        when(steps.queryAdjustStepByBatchList(1L, "batch1")).thenReturn(Collections.singletonList(step));
        SecurityPoolAdjustReq req = new SecurityPoolAdjustReq();
        req.setCurrentUserId("2");
        req.setAdjustLogId(1L);
        req.setAdjustBatchNo("batch1");
        assertThat(service.queryAdjustStepList(req)).extracting("id", "handlerId").containsExactly(tuple(7L, "9"));
    }

    /** 基金详情展示完整批次上下文，不按当前用户再次过滤日志。 */
    @Test public void fundLogsShouldLoadWithoutFilteringByUser() {
        FundPoolAdjustMapper logs = mock(FundPoolAdjustMapper.class);
        FundPoolAdjustService service = new FundPoolAdjustService();
        ReflectionTestUtils.setField(service, "fundPoolAdjustMapper", logs);
        FundAdjustLogBo log = new FundAdjustLogBo();
        log.setId(1L);
        log.setAdjusterId("9");
        FundPoolAdjustReq req = new FundPoolAdjustReq();
        req.setCurrentUserId("2");
        req.setFundCode("F1");
        req.setAdjustBatchNo("batch1");
        when(logs.queryAdjustLogList(req)).thenReturn(Collections.singletonList(log));
        assertThat(service.queryAdjustLogList(req)).containsExactly(log);
    }

    /** 四类审批提交直接进入步骤校验，不要求预先查询或拥有页面入口。 */
    @Test public void approvalsShouldValidateStepsWithoutBusinessEntry() {
        SecurityPoolAdjustMapper bondMapper = mock(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustFlowService bondService = new SecurityPoolAdjustFlowService();
        ReflectionTestUtils.setField(bondService, "securityPoolAdjustMapper", bondMapper);
        ForbiddenPoolAdjustMapper companyMapper = mock(ForbiddenPoolAdjustMapper.class);
        ForbiddenPoolAdjustFlowService companyService = new ForbiddenPoolAdjustFlowService();
        ReflectionTestUtils.setField(companyService, "forbiddenPoolAdjustMapper", companyMapper);
        CrmwPoolAdjustMapper crmwMapper = mock(CrmwPoolAdjustMapper.class);
        CrmwPoolAdjustFlowService crmwService = new CrmwPoolAdjustFlowService();
        ReflectionTestUtils.setField(crmwService, "crmwPoolAdjustMapper", crmwMapper);
        FundPoolAdjustMapper fundMapper = mock(FundPoolAdjustMapper.class);
        FundPoolAdjustFlowService fundService = new FundPoolAdjustFlowService();
        ReflectionTestUtils.setField(fundService, "fundPoolAdjustMapper", fundMapper);
        SecurityPoolAdjustAuditReq bond=new SecurityPoolAdjustAuditReq(); bond.setHandlerId("2"); bond.setStepId(1L); bond.setProcessAction("approve");
        assertThatThrownBy(() -> bondService.submitAdjustAudit(bond))
                .isInstanceOf(BizException.class).hasMessageContaining("流程步骤不存在");
        assertThatThrownBy(() -> companyService.submitAdjustAudit(bond))
                .isInstanceOf(BizException.class).hasMessageContaining("流程步骤不存在");
        CrmwPoolAdjustAuditReq crmw=new CrmwPoolAdjustAuditReq(); crmw.setHandlerId("2"); crmw.setStepId(1L); crmw.setProcessAction("approve");
        assertThatThrownBy(() -> crmwService.submitAdjustAudit(crmw))
                .isInstanceOf(BizException.class).hasMessageContaining("流程步骤不存在");
        FundPoolAdjustAuditReq fund=new FundPoolAdjustAuditReq(); fund.setHandlerId("2"); fund.setStepId(1L); fund.setProcessAction("approve");
        assertThatThrownBy(() -> fundService.submitAdjustAudit(fund))
                .isInstanceOf(BizException.class).hasMessageContaining("流程步骤不存在");
        verify(bondMapper).queryAdjustStepById(1L);
        verify(companyMapper).queryAdjustStepById(1L);
        verify(crmwMapper).queryAdjustStepById(1L);
        verify(fundMapper).queryAdjustStepById(1L);
    }

    /** 普通用户及仅拥有入口的用户只能处理本人步骤，空处理人同样拒绝。 */
    @Test public void ordinaryUsersMustNotTakeOverOtherOrUnassignedSteps() {
        for (String operatorId : new String[]{"2", "4"}) {
            for (String handlerId : new String[]{"9", null, "", " "}) {
                // 验证四类审批对其他人或未分配步骤采用相同处理人边界
                validateAllPendingSteps(operatorId, handlerId, false);
            }
            // 验证普通处理人可以处理明确分配给本人的步骤
            validateAllPendingSteps(operatorId, operatorId, true);
        }
    }

    /** 原管理员 ID 可接管待处理步骤，管理员区间之外的 ID 不可接管。 */
    @Test public void globalAdminIdsShouldTakeOverAcrossBusinessDomains() {
        for (String operatorId : new String[]{"1", "10000", "10100"}) {
            for (String handlerId : new String[]{"9", null, "", " "}) {
                // 验证管理员可接管其他人或未分配的待处理步骤
                validateAllPendingSteps(operatorId, handlerId, true);
            }
        }
        for (String operatorId : new String[]{"9999", "10101"}) {
            // 验证管理员 ID 区间边界外仍按普通处理人校验
            validateAllPendingSteps(operatorId, "9", false);
        }
    }

    /** 附件只按明确业务编码分表，股票相同日志 ID 不读债券或基金附件。 */
    @Test public void attachmentReadsShouldRouteByBusinessWithoutRequiringGrant() {
        SysAttachmentMapper attachments = mock(SysAttachmentMapper.class);
        SysAttachmentService service = new SysAttachmentService();
        ReflectionTestUtils.setField(service, "sysAttachmentMapper", attachments);
        SysAttachmentReq req = new SysAttachmentReq();
        req.setAdjustLogIds(Arrays.asList(1L, 2L));
        req.setBusinessDomain("bond");
        service.queryAttachmentList(req);
        verify(attachments).queryAttachmentList("ip_adjust_log", Arrays.asList(1L, 2L));
        req.setBusinessDomain("fund");
        service.queryAttachmentList(req);
        verify(attachments).queryAttachmentList("ip_adjust_log_fund", Arrays.asList(1L, 2L));
        req.setBusinessDomain("stock");
        service.queryAttachmentList(req);
        verify(attachments).queryAttachmentList("ip_adjust_log_stock", Arrays.asList(1L, 2L));
        req.setBusinessDomain("unknown");
        assertThatThrownBy(() -> service.queryAttachmentList(req)).hasMessageContaining("业务未接入或编码无效");
    }

    /** 页面入口固定名单不注册授权表、初始化脚本、手工脚本或重置模块。 */
    @Test public void fixedBusinessPermissionsShouldNotRegisterTablesOrScripts() {
        ScriptToolService tool=new ScriptToolService(); ReflectionTestUtils.setField(tool,"sqlPath","sql");
        List<String> schema=ReflectionTestUtils.invokeMethod(tool,"querySchemaFiles");
        List<String> demo=ReflectionTestUtils.invokeMethod(tool,"queryDemoFiles");
        List<String> excluded=ReflectionTestUtils.invokeMethod(tool,"querySqlFilesExcludedFromRegistration");
        String[] scripts = {"rrs_business_permission_schema.sql", "rrs_business_permission_demo_data.sql",
                "tool_business_permission_grant.sql", "tool_business_permission_revoke.sql", "tool_business_permission_pending_audit.sql"};
        assertThat(schema).doesNotContain(scripts);
        assertThat(demo).doesNotContain(scripts);
        assertThat(excluded).doesNotContain(scripts);
        for (String script : scripts) {
            assertThat(Paths.get("sql", script)).doesNotExist();
        }
        Map<?,?> tables=ReflectionTestUtils.invokeMethod(tool,"queryExpectedSchemaTables");
        Map<?,?> clear=ReflectionTestUtils.invokeMethod(tool,"queryClearTableMap");
        Map<?,?> modules=ReflectionTestUtils.invokeMethod(tool,"queryModuleTaskMap");
        List<?> groups=ReflectionTestUtils.invokeMethod(tool,"queryClearTableGroups");
        assertThat(tables.containsKey("znty_rrs.sys_business_permission")).isFalse();
        assertThat(tables.containsKey("znty_rrs.sys_business_permission_evt")).isFalse();
        assertThat(clear.containsKey("znty_rrs.sys_business_permission")).isFalse();
        assertThat(clear.containsKey("znty_rrs.sys_business_permission_evt")).isFalse();
        assertThat(modules.containsKey("business-permission")).isFalse();
        assertThat(groups).extracting("groupCode").doesNotContain("business-permission");
        assertThat(clear.keySet()).isEqualTo(tables.keySet());
    }

    /**
     * 验证四类审批的待处理步骤采用一致的操作人规则。
     * @param operatorId 当前操作人 ID
     * @param handlerId 步骤分配的处理人 ID
     * @param allowed 是否允许处理
     */
    private void validateAllPendingSteps(String operatorId, String handlerId, boolean allowed) {
        IpAdjustStepBo step = new IpAdjustStepBo();
        step.setId(1L);
        step.setHandlerId(handlerId);
        step.setStepStatus("pending");
        SecurityPoolAdjustAuditReq bond = new SecurityPoolAdjustAuditReq();
        bond.setHandlerId(operatorId);
        CrmwPoolAdjustAuditReq crmw = new CrmwPoolAdjustAuditReq();
        crmw.setHandlerId(operatorId);
        FundAdjustStepBo fundStep = new FundAdjustStepBo();
        fundStep.setId(1L);
        fundStep.setHandlerId(handlerId);
        fundStep.setStepStatus("pending");
        FundPoolAdjustAuditReq fund = new FundPoolAdjustAuditReq();
        fund.setHandlerId(operatorId);
        List<Runnable> validations = Arrays.asList(
                () -> ReflectionTestUtils.invokeMethod(new SecurityPoolAdjustFlowService(), "validatePendingStep", bond, step),
                () -> ReflectionTestUtils.invokeMethod(new ForbiddenPoolAdjustFlowService(), "validatePendingStep", bond, step),
                () -> ReflectionTestUtils.invokeMethod(new CrmwPoolAdjustFlowService(), "validatePendingStep", crmw, step),
                () -> ReflectionTestUtils.invokeMethod(new FundPoolAdjustFlowService(), "validatePendingStep", fund, fundStep));
        for (Runnable validation : validations) {
            if (allowed) {
                validation.run();
            } else {
                assertThatThrownBy(validation::run).isInstanceOf(BizException.class)
                        .hasMessageContaining("不是该步骤处理人");
            }
        }
    }
}
