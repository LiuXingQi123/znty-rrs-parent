package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.exception.BizException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/** 自动调库批次号上下文，按实际主调整对象、目标池和方向复用批次。 */
final class ScheduledAdjustBatchNoContext {

    /** 本次任务执行统一提交时间。 */
    private final Date submitTime = new Date();

    /** 本次任务执行统一批次时间片。 */
    private final String batchTimeText = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(submitTime);

    /** 自动调库分组批次号索引。 */
    private final Map<String, String> batchNoMap = new HashMap<>();

    /** 各前缀调入批次序号。 */
    private final Map<String, Integer> inboundSequenceMap = new HashMap<>();

    /** 各前缀调出批次序号。 */
    private final Map<String, Integer> outboundSequenceMap = new HashMap<>();

    /** 获取本次任务统一提交时间。 */
    Date getSubmitTime() {
        return submitTime;
    }

    /** 获取普通证券或债券批次号。 */
    String resolveBondBatchNo(String securityCode, Long targetPoolId, String adjustMode) {
        // 按证券主对象复用 BOND 批次
        return resolveBatchNo("BOND", "security", securityCode, targetPoolId, adjustMode);
    }

    /** 获取主体批次号。 */
    String resolveCompanyBatchNo(String companyCode, Long targetPoolId, String adjustMode) {
        // 按主体主对象复用 COMP 批次
        return resolveBatchNo("COMP", "company", companyCode, targetPoolId, adjustMode);
    }

    /** 获取 CRMW 凭证与标的证券组合批次号。 */
    String resolveCrmwBatchNo(String crmwScode, String securityCode, Long targetPoolId, String adjustMode) {
        // 校验 CRMW 凭证与标的证券组合的业务身份
        validateObjectCode(crmwScode, "CRMW凭证代码");
        validateObjectCode(securityCode, "CRMW标的证券代码");
        // 按凭证与标的证券组合复用 CRMW 批次
        return resolveBatchNo("CRMW", "crmw", crmwScode + "|" + securityCode, targetPoolId, adjustMode);
    }

    /** 按业务对象类型、代码、目标池和方向获取或创建批次号。 */
    private String resolveBatchNo(String prefix, String objectType, String objectCode,
                                  Long targetPoolId, String adjustMode) {
        // 校验自动调库批次分组所需字段
        validateObjectCode(objectCode, "自动调库主对象代码");
        if (targetPoolId == null) {
            throw new BizException("自动调库目标池不能为空");
        }
        if (!AdjustMode.IN.getCode().equals(adjustMode)
                && !AdjustMode.OUT.getCode().equals(adjustMode)) {
            throw new BizException("自动调库方向不正确");
        }
        String groupKey = objectType + "|" + objectCode + "|" + targetPoolId + "|" + adjustMode;
        String batchNo = batchNoMap.get(groupKey);
        if (batchNo != null) {
            return batchNo;
        }
        // 为当前业务前缀和调整方向生成下一个序号
        int serial = nextSerial(prefix, adjustMode);
        batchNo = prefix + batchTimeText + String.format("%04d", serial);
        batchNoMap.put(groupKey, batchNo);
        return batchNo;
    }

    /** 生成指定前缀和方向的下一个批次序号。 */
    private int nextSerial(String prefix, String adjustMode) {
        Map<String, Integer> sequenceMap = AdjustMode.IN.getCode().equals(adjustMode)
                ? inboundSequenceMap : outboundSequenceMap;
        Integer current = sequenceMap.get(prefix);
        int next = current == null ? 1 : current + 1;
        sequenceMap.put(prefix, next);
        return (AdjustMode.IN.getCode().equals(adjustMode) ? 1000 : 2000) + next;
    }

    /** 校验批次号主对象代码。 */
    private void validateObjectCode(String objectCode, String label) {
        if (objectCode == null || objectCode.trim().isEmpty()) {
            throw new BizException(label + "不能为空");
        }
    }
}
