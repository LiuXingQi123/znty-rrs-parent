package com.znty.rrs.common.util;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto.FlowOption;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 为 CRMW 池批量调整和 Excel 导入补充目标池配置的批量审批流程
 *
 * 复用证券池批量调整的候选规则，使用已加载的目标池配置
 * 单笔候选和批量候选即使使用同一流程定义，也按各自的流程类型保留，供页面分别选择
 */
public final class CrmwBatchAdjustFlowOptionHelper {
    /** 工具方法均为静态方法，禁止创建实例 */
    private CrmwBatchAdjustFlowOptionHelper() {
    }

    /**
     * 为 CRMW 单笔候选追加目标池配置的批量流程
     *
     * 使用调用方已加载的池配置，不查询数据库
     * 复制候选后再应用共用规则，避免改动传入的单笔候选和推荐状态
     *
     * 执行顺序：
     * 1. 将已有的 CRMW 单笔候选复制为证券池流程候选
     * 2. 按调入或调出方向追加并推荐已配置的批量流程，未配置时保留原推荐
     * 3. 将结果转回 CRMW 流程候选，保留推荐状态、匹配原因和可选状态
     *
     * @param original 当前已匹配的 CRMW 单笔候选
     * @param pool 本次已加载的目标池配置
     * @param adjustMode 调入或调出
     * @return 包含单笔候选及已配置批量流程的 CRMW 候选列表
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
