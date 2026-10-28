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

/** 三个批量入口共用的流程候选追加工具，不查询数据库 */
public final class BatchAdjustFlowOptionHelper {

    private BatchAdjustFlowOptionHelper() {
    }

    /**
     * 保留单笔匹配候选，额外加入目标池配置的批量流程并推荐
     *
     * 执行顺序：
     * 1. 复制原候选，避免修改单笔校验结果及其中的材料豁免信息
     * 2. 根据方向读取批量配置，未配置时直接保留原候选和推荐
     * 3. 去除重复的同类批量候选，再将配置的批量流程设为唯一推荐
     *
     * @param original 单笔已经匹配的流程候选
     * @param pool 已加载的目标池
     * @param adjustMode 调入或调出
     * @return 单笔匹配候选和额外的批量流程
     */
    public static List<AdjustCheckDto.FlowOption> appendConfiguredBatchFlow(
            List<AdjustCheckDto.FlowOption> original, InvestmentPoolBo pool, String adjustMode) {
        List<AdjustCheckDto.FlowOption> result = new ArrayList<>();
        if (original != null) {
            for (AdjustCheckDto.FlowOption option : original) {
                AdjustCheckDto.FlowOption copy = new AdjustCheckDto.FlowOption();
                BeanUtils.copyProperties(option, copy);
                result.add(copy);
            }
        }
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
        // 追加批量流程前，先删除列表中已存在的同一个批量候选，避免后面重新添加时出现重复选项
        // 删除条件：流程类型相同，且满足“流程 ID 相同”或“已配置的非空流程 Key 相同”中的任意一项
        // removeIf 会逐个检查候选，条件为 true 时从当前列表删除，为 false 时保留，不操作数据库
        // 快速流程与批量流程即使使用同一个 ID 或 Key，也因类型不同而保留，材料豁免统一由主项资格决定
        result.removeIf(option -> flowType.equals(option.getFlowType())
                && ((flowId != null && Objects.equals(flowId, option.getFlowId()))
                    || (flowKey != null && !flowKey.trim().isEmpty() && flowKey.equals(option.getFlowKey()))));
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
