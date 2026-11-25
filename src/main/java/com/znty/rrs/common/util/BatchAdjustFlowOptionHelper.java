package com.znty.rrs.common.util;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.FlowType;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 证券池批量审批流程候选补充工具
 *
 * 使用已加载的目标池配置，供批量调整和 Excel 导入复用单笔匹配结果
 */
public final class BatchAdjustFlowOptionHelper {

    /** 工具类禁止实例化 */
    private BatchAdjustFlowOptionHelper() {
    }

    /**
     * 保留单笔匹配候选，额外加入目标池配置的批量流程并推荐
     *
     * 执行顺序：
     * 1. 复制原候选，避免修改单笔校验结果及其中的材料豁免信息
     * 2. 池配置缺失、调整方向无效或未配置批量流程时，返回副本并保留原推荐
     * 3. 从候选副本中去掉将要再次追加的同一个批量流程
     * 4. 将配置的批量流程放在首位，并设为唯一推荐
     *
     * @param original 单笔已经匹配的流程候选，缺失时从空列表开始
     * @param pool 已加载的目标池
     * @param adjustMode 调入或调出
     * @return 候选列表的副本，目标池已配置时包含对应的批量候选
     */
    public static List<AdjustCheckDto.FlowOption> appendConfiguredBatchFlow(
            List<AdjustCheckDto.FlowOption> original, InvestmentPoolBo pool, String adjustMode) {
        // 1. 保留单笔候选中的材料豁免信息和其他匹配结果
        List<AdjustCheckDto.FlowOption> result = new ArrayList<>();
        if (original != null) {
            for (AdjustCheckDto.FlowOption option : original) {
                AdjustCheckDto.FlowOption copy = new AdjustCheckDto.FlowOption();
                BeanUtils.copyProperties(option, copy);
                result.add(copy);
            }
        }
        // 2. 只有有效方向和目标池配置齐全时才需补充流程
        if (pool == null || (!AdjustMode.IN.getCode().equals(adjustMode)
                && !AdjustMode.OUT.getCode().equals(adjustMode))) {
            return result;
        }
        boolean outbound = AdjustMode.OUT.getCode().equals(adjustMode);
        Long flowId = outbound ? pool.getBatchOutFlowId() : pool.getBatchInFlowId();
        String flowKey = outbound ? pool.getBatchOutFlowKey() : pool.getBatchInFlowKey();
        if (flowId == null && (flowKey == null || flowKey.trim().isEmpty())) {
            return result;
        }
        String flowType = outbound ? FlowType.BATCH_OUTBOUND.getCode() : FlowType.BATCH_INBOUND.getCode();
        // 3. 追加前先移除列表中已有的同一个批量候选，避免出现重复选项
        // 流程类型须相同，且满足任一条件：已配置的流程 ID 相同，或已配置的非空白流程 Key 相同
        // removeIf 逐个检查候选，条件为 true 时从列表移除，为 false 时保留，操作仅限内存中的候选副本
        // 快速流程和批量流程即使用同一个 ID 或 Key，也因类型不同而各自保留
        // 材料豁免仍按主项资格判断
        result.removeIf(option -> flowType.equals(option.getFlowType())
                && ((flowId != null && Objects.equals(flowId, option.getFlowId()))
                    || (flowKey != null && !flowKey.trim().isEmpty() && flowKey.equals(option.getFlowKey()))));
        // 4. 清除原推荐，将配置的批量流程作为唯一推荐
        for (AdjustCheckDto.FlowOption option : result) {
            option.setRecommended(false);
        }
        String name = outbound ? pool.getBatchOutFlowName() : pool.getBatchInFlowName();
        AdjustCheckDto.FlowOption batch = new AdjustCheckDto.FlowOption();
        batch.setFlowId(flowId);
        batch.setFlowKey(flowKey);
        batch.setFlowName(name == null || name.trim().isEmpty()
                ? (outbound ? "批量调出流程" : "批量调入流程") : name.trim());
        batch.setFlowType(flowType);
        batch.setRecommended(true);
        batch.setMatched(true);
        batch.setSelectable(true);
        batch.setMatchReasons(Collections.singletonList(outbound ? "目标池配置的批量调出流程" : "目标池配置的批量调入流程"));
        batch.setUnmatchReasons(new ArrayList<String>());
        result.add(0, batch);
        return result;
    }
}
