package com.znty.rrs.common.util;

import com.znty.rrs.common.enums.ProcessAction;
import com.znty.rrs.entity.bo.IpAdjustStepBo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 调库流程步骤处理人排除工具。
 * <p>同一批次中已实际处理过的 handler_id 不得再进入后续人工/自动步骤；
 * 待处理、抢占跳过、系统自动处理不算已参与。</p>
 */
public final class AdjustStepHandlerExcludeUtil {

    /** 人工节点过滤后无可用审批人时的业务提示。 */
    public static final String NO_AVAILABLE_HANDLER_MSG = "下一节点无可用审批人（已排除本流程已参与人员）";

    private AdjustStepHandlerExcludeUtil() {
    }

    /**
     * 从本批次已有步骤中收集已实际处理过的 handler_id。
     * <p>仅统计 submit/approve/reject；pending、skipped、auto_process 不占用后续环节。</p>
     *
     * @param existingSteps 本批次已有步骤
     * @return 已参与处理人 ID 集合
     */
    public static Set<String> collectParticipatedHandlerIds(List<IpAdjustStepBo> existingSteps) {
        Set<String> result = new LinkedHashSet<>();
        if (existingSteps == null || existingSteps.isEmpty()) {
            return result;
        }
        for (IpAdjustStepBo step : existingSteps) {
            if (step == null || step.getHandlerId() == null || !isActualParticipation(step)) {
                continue;
            }
            String handlerId = step.getHandlerId().trim();
            if (!handlerId.isEmpty()) {
                result.add(handlerId);
            }
        }
        return result;
    }

    /**
     * 是否真正审核过：只看 process_action 是否为 submit/approve/reject。
     */
    public static boolean isActualParticipation(IpAdjustStepBo step) {
        if (step == null || step.getProcessAction() == null) {
            return false;
        }
        String action = step.getProcessAction().trim();
        return ProcessAction.SUBMIT.getCode().equals(action)
                || ProcessAction.APPROVE.getCode().equals(action)
                || ProcessAction.REJECT.getCode().equals(action);
    }

    /**
     * 从候选处理人中排除已参与人员，保持原有顺序。
     *
     * @param handlers         候选处理人
     * @param participated     已参与处理人 ID
     * @param handlerIdGetter  取处理人 ID
     * @param <T>              处理人类型
     * @return 过滤后的处理人列表
     */
    public static <T> List<T> excludeParticipated(List<T> handlers, Set<String> participated,
                                                  Function<T, String> handlerIdGetter) {
        if (handlers == null || handlers.isEmpty()) {
            return Collections.emptyList();
        }
        if (participated == null || participated.isEmpty()) {
            return new ArrayList<>(handlers);
        }
        List<T> result = new ArrayList<>();
        for (T handler : handlers) {
            if (handler == null || handlerIdGetter == null) {
                continue;
            }
            String handlerId = handlerIdGetter.apply(handler);
            if (handlerId == null || handlerId.trim().isEmpty()) {
                continue;
            }
            if (!participated.contains(handlerId.trim())) {
                result.add(handler);
            }
        }
        return result;
    }
}
