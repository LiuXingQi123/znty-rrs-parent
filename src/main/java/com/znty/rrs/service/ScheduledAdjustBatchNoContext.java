package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.exception.BizException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 生成并复用同一次自动调库任务的批次号
 *
 * 本类是单轮自动调库的批次号上下文，普通证券、主体、CRMW 凭证与标的组合分别使用 BOND、COMP、CRMW 前缀
 * 同一对象、目标池和方向复用批次号，整轮使用同一个提交时间，调入和调出分别编号
 */
final class ScheduledAdjustBatchNoContext {

    /** 本次任务执行统一提交时间 */
    private final Date submitTime = new Date();

    /** 本次任务执行统一批次时间片 */
    private final String batchTimeText = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(submitTime);

    /** 自动调库分组批次号索引 */
    private final Map<String, String> batchNoMap = new HashMap<>();

    /** 各前缀调入批次序号 */
    private final Map<String, Integer> inboundSequenceMap = new HashMap<>();

    /** 各前缀调出批次序号 */
    private final Map<String, Integer> outboundSequenceMap = new HashMap<>();

    /**
     * 获取本次任务统一提交时间
     *
     * 执行顺序：
     * 1. 读取创建当前任务批次号上下文时保存的提交时间
     *
     * @return 本次任务统一提交时间
     */
    Date getSubmitTime() {
        return submitTime;
    }

    /**
     * 获取普通证券或债券批次号
     *
     * 执行顺序：
     * 1. 用证券代码、目标池和方向查找或创建 BOND 批次号
     *
     * @param securityCode 证券代码
     * @param targetPoolId 目标投资池 ID
     * @param adjustMode 调入或调出方向编码
     * @return 证券或债券的自动调库批次号
     */
    String resolveBondBatchNo(String securityCode, Long targetPoolId, String adjustMode) {
        // 按证券主对象复用 BOND 批次
        return resolveBatchNo("BOND", "security", securityCode, targetPoolId, adjustMode);
    }

    /**
     * 获取主体批次号
     *
     * 执行顺序：
     * 1. 用主体代码、目标池和方向查找或创建 COMP 批次号
     *
     * @param companyCode 主体代码
     * @param targetPoolId 目标投资池 ID
     * @param adjustMode 调入或调出方向编码
     * @return 主体的自动调库批次号
     */
    String resolveCompanyBatchNo(String companyCode, Long targetPoolId, String adjustMode) {
        // 按主体主对象复用 COMP 批次
        return resolveBatchNo("COMP", "company", companyCode, targetPoolId, adjustMode);
    }

    /**
     * 获取 CRMW 凭证与标的证券组合批次号
     *
     * 执行顺序：
     * 1. 检查凭证代码和标的证券代码均有值
     * 2. 用两者组成的组合代码、目标池和方向查找或创建 CRMW 批次号
     *
     * @param crmwScode CRMW 凭证代码
     * @param securityCode CRMW 标的证券代码
     * @param targetPoolId 目标投资池 ID
     * @param adjustMode 调入或调出方向编码
     * @return 凭证与标的证券组合的自动调库批次号
     */
    String resolveCrmwBatchNo(String crmwScode, String securityCode, Long targetPoolId, String adjustMode) {
        // 1. 校验 CRMW 凭证代码和标的证券代码
        validateObjectCode(crmwScode, "CRMW凭证代码");
        // 组合批次的标的证券代码也必须有值
        validateObjectCode(securityCode, "CRMW标的证券代码");
        // 2. 按凭证、标的、目标池和方向查找或创建组合批次号
        return resolveBatchNo("CRMW", "crmw", crmwScode + "|" + securityCode, targetPoolId, adjustMode);
    }

    /**
     * 按业务对象类型、代码、目标池和方向获取或创建批次号
     *
     * 执行顺序：
     * 1. 核对业务对象代码、目标池和调库方向
     * 2. 按对象类型、代码、目标池和方向查找已有批次号
     * 3. 没有可复用的批次号时按前缀、时间和序号生成并保存
     *
     * @param prefix 批次号前缀
     * @param objectType 用于区分批次分组的业务对象类型
     * @param objectCode 自动调库主对象代码
     * @param targetPoolId 目标投资池 ID
     * @param adjustMode 调入或调出方向编码
     * @return 同一业务对象、目标池和调库方向共用的批次号
     */
    private String resolveBatchNo(String prefix, String objectType, String objectCode,
                                  Long targetPoolId, String adjustMode) {
        // 1. 检查批次对应的主对象代码、目标池和方向
        validateObjectCode(objectCode, "自动调库主对象代码");
        if (targetPoolId == null) {
            throw new BizException("自动调库目标池不能为空");
        }
        if (!AdjustMode.IN.getCode().equals(adjustMode)
                && !AdjustMode.OUT.getCode().equals(adjustMode)) {
            throw new BizException("自动调库方向不正确");
        }
        // 2. 按对象类型、代码、池和方向查找已有批次号
        String groupKey = objectType + "|" + objectCode + "|" + targetPoolId + "|" + adjustMode;
        String batchNo = batchNoMap.get(groupKey);
        if (batchNo != null) {
            return batchNo;
        }
        // 3. 没有可复用的批次号时生成并保存
        int serial = nextSerial(prefix, adjustMode);
        batchNo = prefix + batchTimeText + String.format("%04d", serial);
        batchNoMap.put(groupKey, batchNo);
        return batchNo;
    }

    /**
     * 生成指定前缀和方向的下一个批次序号
     *
     * 执行顺序：
     * 1. 按调库方向选择计数表，递增指定前缀的计数
     * 2. 调入使用 1000 号段，调出使用 2000 号段
     *
     * @param prefix 批次号前缀
     * @param adjustMode 调入或调出方向编码
     * @return 带调库方向号段的下一个序号
     */
    private int nextSerial(String prefix, String adjustMode) {
        Map<String, Integer> sequenceMap = AdjustMode.IN.getCode().equals(adjustMode)
                ? inboundSequenceMap : outboundSequenceMap;
        Integer current = sequenceMap.get(prefix);
        int next = current == null ? 1 : current + 1;
        sequenceMap.put(prefix, next);
        return (AdjustMode.IN.getCode().equals(adjustMode) ? 1000 : 2000) + next;
    }

    /**
     * 校验批次号主对象代码
     *
     * 执行顺序：
     * 1. 代码为空或只有空白时，用传入的业务名称提示缺少代码
     *
     * @param objectCode 自动调库主对象代码
     * @param label 用于展示的字段名称
     */
    private void validateObjectCode(String objectCode, String label) {
        if (objectCode == null || objectCode.trim().isEmpty()) {
            throw new BizException(label + "不能为空");
        }
    }
}
