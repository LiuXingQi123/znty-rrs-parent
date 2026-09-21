package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.IpAdjustStepBo;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** AdjustStepHandlerExcludeUtil 单元测试。 */
public class AdjustStepHandlerExcludeUtilTest {

    /** 收集已实际处理过的非空 handler_id，忽略空白。 */
    @Test
    public void collectParticipatedHandlerIdsShouldIgnoreBlank() {
        IpAdjustStepBo submit = buildStep("2", "submit", "submit");
        IpAdjustStepBo blank = buildStep("  ", "approve", "approve");
        IpAdjustStepBo nullHandler = buildStep(null, "approve", "approve");
        IpAdjustStepBo pending = buildStep("3", "pending", null);

        Set<String> participated = AdjustStepHandlerExcludeUtil.collectParticipatedHandlerIds(
                Arrays.asList(submit, blank, nullHandler, pending));

        assertThat(participated).containsExactly("2");
    }

    /** 待处理、抢占跳过、系统自动处理都不算已参与。 */
    @Test
    public void collectParticipatedHandlerIdsShouldIgnorePendingSkippedAndAutoProcess() {
        IpAdjustStepBo approved = buildStep("3", "approve", "approve");
        IpAdjustStepBo skipped = buildStep("4", "approve", "skipped");
        IpAdjustStepBo pending = buildStep("5", "pending", null);
        IpAdjustStepBo autoProcess = buildStep("6", "auto_process", "auto_process");

        Set<String> participated = AdjustStepHandlerExcludeUtil.collectParticipatedHandlerIds(
                Arrays.asList(approved, skipped, pending, autoProcess));

        assertThat(participated).containsExactly("3");
    }

    /** 按已参与人过滤候选处理人并保持顺序。 */
    @Test
    public void excludeParticipatedShouldKeepOrderAndDropMatched() {
        List<String> handlers = Arrays.asList("3", "4", "5");
        Set<String> participated = AdjustStepHandlerExcludeUtil.collectParticipatedHandlerIds(
                Collections.singletonList(buildStep("3", "approve", "approve")));

        List<String> filtered = AdjustStepHandlerExcludeUtil.excludeParticipated(
                handlers, participated, id -> id);

        assertThat(filtered).containsExactly("4", "5");
    }

    private IpAdjustStepBo buildStep(String handlerId) {
        return buildStep(handlerId, null, null);
    }

    private IpAdjustStepBo buildStep(String handlerId, String stepStatus, String processAction) {
        IpAdjustStepBo step = new IpAdjustStepBo();
        step.setHandlerId(handlerId);
        step.setStepStatus(stepStatus);
        step.setProcessAction(processAction);
        return step;
    }
}
