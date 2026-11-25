package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto.FlowOption;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * CRMW 池批量审批流程候选补充工具
 *
 * 批量调整和 Excel 导入通过 {@link BatchAdjustFlowOptionHelper} 复用证券池批量流程规则
 * 单笔候选和批量候选即使使用同一流程定义，也按各自的流程类型保留，供页面分别选择
 */
public final class CrmwBatchAdjustFlowOptionHelper {

    /** 工具类禁止实例化 */
    private CrmwBatchAdjustFlowOptionHelper() {
    }

    /**
     * 为 CRMW 单笔候选追加目标池配置的批量流程
     *
     * 整个过程使用调用方已加载的池配置，在内存中复制候选并计算推荐，保留传入的单笔候选及其推荐状态
     *
     * 执行顺序：
     * 1. 将已有的 CRMW 单笔候选复制为证券池流程候选
     * 2. 按调入或调出方向追加并推荐已配置的批量流程，未配置时保留原推荐
     * 3. 将结果转回 CRMW 流程候选，保留推荐状态、匹配原因和可选状态
     *
     * @param original 当前已匹配的 CRMW 单笔候选列表，无候选时传空列表
     * @param pool 本次已加载的目标池配置
     * @param adjustMode 调入或调出
     * @return CRMW 候选列表，配置了批量流程时包含并推荐对应候选
     */
    public static List<AdjustCheckDto.FlowOption> appendConfiguredBatchFlow(
            List<AdjustCheckDto.FlowOption> original, InvestmentPoolBo pool, String adjustMode) {
        // 1. 复制单笔候选，供证券池共用规则处理
        List<FlowOption> commonOptions = new ArrayList<>();
        for (AdjustCheckDto.FlowOption option : original) {
            FlowOption common = new FlowOption();
            BeanUtils.copyProperties(option, common);
            commonOptions.add(common);
        }
        List<AdjustCheckDto.FlowOption> result = new ArrayList<>();
        // 2. 按调整方向追加并推荐目标池配置的批量流程
        for (FlowOption common : BatchAdjustFlowOptionHelper.appendConfiguredBatchFlow(commonOptions, pool, adjustMode)) {
            // 3. 转回 CRMW 候选，保留推荐状态、匹配原因和可选状态
            AdjustCheckDto.FlowOption option = new AdjustCheckDto.FlowOption();
            BeanUtils.copyProperties(common, option);
            result.add(option);
        }
        return result;
    }
}
