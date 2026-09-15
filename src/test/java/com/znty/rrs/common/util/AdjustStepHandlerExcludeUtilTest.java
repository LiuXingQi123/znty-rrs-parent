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

    /** 收集本批次已出现的非空 handler_id。 */
    @Test
    public void collectParticipatedHandlerIdsShouldIgnoreBlank() {
        IpAdjustStepBo submit = buildStep("2");
        IpAdjustStepBo blank = buildStep("  ");
        IpAdjustStepBo nullHandler = buildStep(null);
        IpAdjustStepBo pending = buildStep("3");

        Set<String> participated = AdjustStepHandlerExcludeUtil.collectParticipatedHandlerIds(
                Arrays.asList(submit, blank, nullHandler, pending));

        assertThat(participated).containsExactly("2", "3");
    }

    /** 按已参与人过滤候选处理人并保持顺序。 */
    @Test
    public void excludeParticipatedShouldKeepOrderAndDropMatched() {
        List<String> handlers = Arrays.asList("3", "4", "5");
        Set<String> participated = AdjustStepHandlerExcludeUtil.collectParticipatedHandlerIds(
                Collections.singletonList(buildStep("3")));

        List<String> filtered = AdjustStepHandlerExcludeUtil.excludeParticipated(
                handlers, participated, id -> id);

        assertThat(filtered).containsExactly("4", "5");
    }

    private IpAdjustStepBo buildStep(String handlerId) {
        IpAdjustStepBo step = new IpAdjustStepBo();
        step.setHandlerId(handlerId);
        return step;
    }
}
