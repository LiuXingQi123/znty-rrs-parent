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
 * 调库流程处理人排除工具
 *
 * 同一批次中，已提交、审批通过或驳回的处理人从后续人工和自动步骤的候选名单中排除
 * 待处理、抢占跳过和系统自动处理的记录不计入已参与名单
 */
public final class AdjustStepHandlerExcludeUtil {

    /** 人工节点排除已参与人员后无可用审批人时的提示 */
    public static final String NO_AVAILABLE_HANDLER_MSG = "下一节点无可用审批人（已排除本流程已参与人员）";

    /** 工具类禁止实例化 */
    private AdjustStepHandlerExcludeUtil() {
    }

    /**
     * 从同一批次的已有步骤中收集已参与处理人
     *
     * 按 {@code handler_id} 记录人员，同一人多次参与时只保留第一次
     *
     * 执行顺序：
     * 1. 没有步骤记录时返回空集合
     * 2. 保留实际参与且有处理人 ID 的步骤，按首次出现顺序收集去重后的 ID
     *
     * @param existingSteps 本批次已有步骤
     * @return 已参与处理人的 ID 集合，没有符合条件的步骤时为空集合
     */
    public static Set<String> collectParticipatedHandlerIds(List<IpAdjustStepBo> existingSteps) {
        Set<String> result = new LinkedHashSet<>();
        if (existingSteps == null || existingSteps.isEmpty()) {
            return result;
        }
        for (IpAdjustStepBo step : existingSteps) {
            // 只收集实际处理过且记录了处理人 ID 的步骤
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
     * 按步骤动作判断处理人是否已实际参与
     *
     * {@code process_action} 只统计 {@code submit}、{@code approve} 和 {@code reject}
     *
     * @param step 调库步骤记录
     * @return true=动作是提交、审批通过或驳回，false=步骤或动作缺失，或动作不在上述范围
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
     * 从候选处理人中排除已参与人员，保留候选的原有顺序
     *
     * 执行顺序：
     * 1. 没有候选时返回空列表，没有已参与人员时返回候选列表的副本
     * 2. 有排除名单时，按候选顺序保留可取得有效 ID 且未参与过的人员
     *
     * @param <T> 处理人类型
     * @param handlers 候选处理人列表
     * @param participated 已参与处理人 ID 集合
     * @param handlerIdGetter 读取候选处理人 ID 的方法
     * @return 过滤后的候选列表，没有可保留的候选时返回空列表
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
