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

/**
 * 处理债券自动调入后触发的关联池调出
 *
 * 本类是自动调库辅助类，依据目标池的调入互斥配置，以及其他池把目标池设为调入限制池的配置，找出需要配套调出的池
 * 只处理债券已生效且没有出池保护的池记录，实际调出后沿用调入批次写入已通过的调出日志
 */
final class AutoAdjustRelationHelper {

    /**
     * 限制自动调库关系工具类的实例化
     */
    private AutoAdjustRelationHelper() {
    }

    /**
     * 合并目标池的调入互斥池，以及将目标池配置为调入限制池的反向关系
     *
     * 执行顺序：
     * 1. 查找目标池的调入互斥池及反向调入限制池
     * 2. 去掉目标池自身，返回去重并按 ID 排序的关联池
     *
     * @param targetPoolId 目标投资池 ID
     * @param relations 投资池关系配置
     * @return 需要检查是否自动调出的关系池 ID，按 ID 排序且去重
     */
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

    /**
     * 将刚调入目标池的债券从当前实际所在的互斥/受限池调出，并生成自动调出日志
     *
     * 执行顺序：
     * 1. 找出与目标池有互斥或反向限制关系的池，只处理证券当前所在的池
     * 2. 核对池配置，软删除没有出池保护的生效池记录
     * 3. 每次成功删除后写入同批次的自动调出日志，最后返回写入条数
     *
     * @param inboundLog 触发关系池调出的自动调入记录
     * @param currentPoolIds 证券当前生效的池 ID
     * @param poolMap 按 ID 整理的投资池配置
     * @param relations 投资池关系配置
     * @param mapper 自动调库状态及日志操作组件
     * @return 实际写入关系池自动调出日志的条数
     */
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
            int deleted = mapper.deleteUnprotectedPoolStatusSoft(inboundLog.getSecurityCode(), outPoolId);
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

    /**
     * 基于自动调入日志构建同批次的关系池调出日志
     *
     * 执行顺序：
     * 1. 取调入记录的证券、批次和经办人信息，生成已通过的互斥调出记录
     * 2. 写入调出池信息和触发这次调出的池关系
     *
     * @param inboundLog 触发关系池调出的自动调入记录
     * @param outPool 需要自动调出的投资池
     * @return 与调入记录同批次的关系池调出日志
     */
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
        String reason = ScheduledAdjustLogHelper.appendDetails(inboundLog.getAdjustReason(),
                "池关系触发：调入" + inboundLog.getTargetPoolName() + "后自动调出" + outPool.getPoolName());
        autoOutLog.setAdjustReason(reason);
        autoOutLog.setSubmitTime(inboundLog.getSubmitTime());
        return autoOutLog;
    }
}
