package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** 自动入池后的互斥/反向限制池调出辅助逻辑。 */
final class AutoAdjustRelationHelper {

    private AutoAdjustRelationHelper() {
    }

    /** 合并目标池的调入互斥池，以及将目标池配置为调入限制池的反向关系。 */
    static List<Long> resolveInboundAutoOutPoolIds(Long targetPoolId, List<PoolRelationBo> relations) {
        Set<Long> poolIds = new TreeSet<>();
        if (targetPoolId == null || relations == null) {
            return new ArrayList<>(poolIds);
        }
        for (PoolRelationBo relation : relations) {
            if (relation == null) {
                continue;
            }
            if (targetPoolId.equals(relation.getPoolId())
                    && RelationType.IN_MUTEX.getCode().equals(relation.getRelationType())
                    && relation.getRelationPoolId() != null) {
                poolIds.add(relation.getRelationPoolId());
            }
            if (targetPoolId.equals(relation.getRelationPoolId())
                    && RelationType.IN_RESTRICT.getCode().equals(relation.getRelationType())
                    && relation.getPoolId() != null) {
                poolIds.add(relation.getPoolId());
            }
        }
        poolIds.remove(targetPoolId);
        return new ArrayList<>(poolIds);
    }

    /** 将刚调入目标池的债券从当前实际所在的互斥/受限池调出，并生成自动调出日志。 */
    static int autoOutCurrentRelationPools(IpAdjustLogBo inboundLog, List<Long> currentPoolIds,
                                           Map<Long, InvestmentPoolBo> poolMap,
                                           List<PoolRelationBo> relations,
                                           SecurityPoolAdjustMapper mapper) {
        List<Long> autoOutPoolIds = resolveInboundAutoOutPoolIds(inboundLog.getTargetPoolId(), relations);
        if (currentPoolIds == null || currentPoolIds.isEmpty() || autoOutPoolIds.isEmpty()) {
            return 0;
        }
        Set<Long> currentPoolIdSet = new HashSet<>(currentPoolIds);
        int count = 0;
        for (Long outPoolId : autoOutPoolIds) {
            if (!currentPoolIdSet.contains(outPoolId)) {
                continue;
            }
            InvestmentPoolBo outPool = poolMap.get(outPoolId);
            if (outPool == null) {
                throw new BizException("债券[" + inboundLog.getSecurityCode()
                        + "]自动调出池配置不存在，请检查投资池关系配置");
            }
            int deleted = mapper.deletePoolStatusSoft(inboundLog.getSecurityCode(), outPoolId);
            if (deleted == 0) {
                continue;
            }
            // 仅在实际删除池状态后记录自动调出，避免并发场景产生虚假日志
            IpAdjustLogBo autoOutLog = buildAutoOutLog(inboundLog, outPool);
            if (mapper.addAdjustLog(autoOutLog) != 1) {
                throw new BizException("债券[" + inboundLog.getSecurityCode() + "]自动调出日志写入失败");
            }
            count++;
        }
        return count;
    }

    /** 基于自动调入日志构建同批次的关系池调出日志。 */
    private static IpAdjustLogBo buildAutoOutLog(IpAdjustLogBo inboundLog, InvestmentPoolBo outPool) {
        IpAdjustLogBo autoOutLog = new IpAdjustLogBo();
        autoOutLog.setSecurityCode(inboundLog.getSecurityCode());
        autoOutLog.setSecurityShortName(inboundLog.getSecurityShortName());
        autoOutLog.setSecurityType(inboundLog.getSecurityType());
        autoOutLog.setAdjustType("互斥调整");
        autoOutLog.setAdjustMode(AdjustMode.OUT.getCode());
        autoOutLog.setAdjustBatchNo(inboundLog.getAdjustBatchNo());
        autoOutLog.setTargetPoolId(outPool.getId());
        autoOutLog.setTargetPoolName(outPool.getPoolName());
        autoOutLog.setPoolType(outPool.getPoolType());
        autoOutLog.setAuditStatus(AuditStatus.APPROVED.getCode());
        autoOutLog.setAdjusterId(inboundLog.getAdjusterId());
        autoOutLog.setAdjusterName(inboundLog.getAdjusterName());
        autoOutLog.setAdjustReason(inboundLog.getAdjustReason() + "；债券调入“"
                + inboundLog.getTargetPoolName() + "”后自动调出“" + outPool.getPoolName() + "”");
        autoOutLog.setAdjustAdvice(inboundLog.getAdjustAdvice());
        autoOutLog.setSubmitTime(inboundLog.getSubmitTime());
        return autoOutLog;
    }
}
