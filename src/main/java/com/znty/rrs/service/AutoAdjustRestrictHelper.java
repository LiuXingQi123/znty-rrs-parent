package com.znty.rrs.service;

import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.schedule.PoolSecurityRowDto;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import com.znty.rrs.mapper.AutoAdjustMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 自动调库池关系校验与候选数据查询
 *
 * 本类是自动调库辅助类，供到期出池、旗下债券同步和 ABS 对账读取池关系、在池状态及在途流程
 */
final class AutoAdjustRestrictHelper {

    /** 管理办法（一）：公司信用债禁止库 */
    static final long COMPANY_FORBIDDEN_POOL_ID = 15L;

    /** 管理办法（三）：重点观察名单 */
    static final long KEY_WATCH_POOL_ID = 23L;

    /** 每次批量读取的证券或主体编码上限 */
    static final int QUERY_BATCH_SIZE = 500;

    /**
     * 限制自动调库限制校验工具类的实例化
     */
    private AutoAdjustRestrictHelper() {
    }

    /**
     * 提取本组待处理的不同证券代码
     *
     * 重复的候选记录共用同一份查询结果
     *
     * 执行顺序：
     * 1. 从有效记录中提取证券代码，去掉空白和重复代码
     *
     * @param candidates 本组待处理的证券记录
     * @return 去掉空代码和重复代码后的列表
     */
    static List<String> candidateCodes(List<ScheduledAdjustCandidateDto> candidates) {
        return candidates.stream().filter(Objects::nonNull).map(ScheduledAdjustCandidateDto::getSecurityCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
    }

    /**
     * 按证券代码分批读取生效池状态
     *
     * 同一只债券在同一池可能有多条入池依据，保留每条记录供跟随出池判断
     *
     * 执行顺序：
     * 1. 将证券代码分批查询生效池记录，按证券代码归集每条记录的池 ID
     *
     * @param mapper 自动调库查询组件
     * @param codes 本组去重后的证券代码
     * @return 证券代码及其每条生效记录对应的池 ID
     */
    static Map<String, List<Long>> queryActivePools(AutoAdjustMapper mapper, List<String> codes) {
        Map<String, List<Long>> poolsByCode = new HashMap<>();
        for (int offset = 0; offset < codes.size(); offset += QUERY_BATCH_SIZE) {
            List<String> batch = codes.subList(offset, Math.min(offset + QUERY_BATCH_SIZE, codes.size()));
            for (PoolSecurityRowDto row : mapper.queryActiveSecurityPoolRows(batch)) {
                poolsByCode.computeIfAbsent(row.getSecurityCode(), key -> new ArrayList<>()).add(row.getPoolId());
            }
        }
        return poolsByCode;
    }

    /**
     * 分批查询存在待处理调库步骤的证券
     *
     * 执行顺序：
     * 1. 将证券代码分批查询，把有待处理调库步骤的代码合并成集合
     *
     * @param mapper 自动调库查询组件
     * @param codes 本组去重后的证券代码
     * @return 存在待处理调库步骤的证券代码集合
     */
    static Set<String> queryPendingCodes(AutoAdjustMapper mapper, List<String> codes) {
        Set<String> pending = new HashSet<>();
        for (int offset = 0; offset < codes.size(); offset += QUERY_BATCH_SIZE) {
            List<String> batch = codes.subList(offset, Math.min(offset + QUERY_BATCH_SIZE, codes.size()));
            pending.addAll(mapper.queryPendingSecurityCodes(batch));
        }
        return pending;
    }

    /**
     * 取出目标池某类关系下的关联池 ID
     *
     * 执行顺序：
     * 1. 按目标池和关系类型筛选配置，收集有值的关联池 ID
     *
     * @param poolId 目标池 ID
     * @param relationType 关系类型
     * @param relations 全量池关系
     * @return 关联池 ID 列表
     */
    static List<Long> resolveRelationPoolIds(Long poolId, String relationType, List<PoolRelationBo> relations) {
        List<Long> result = new ArrayList<>();
        if (poolId == null || relationType == null || relations == null) {
            return result;
        }
        for (PoolRelationBo relation : relations) {
            if (relation == null || !poolId.equals(relation.getPoolId())
                    || !relationType.equals(relation.getRelationType())
                    || relation.getRelationPoolId() == null) {
                continue;
            }
            result.add(relation.getRelationPoolId());
        }
        return result;
    }

    /**
     * 判断当前所在池是否命中自动调库限制
     *
     * 执行顺序：
     * 1. 检查当前所在池与限制池是否有相同的 ID，任一列表为空时按未命中处理
     *
     * @param currentPoolIds 主体或证券当前所在池
     * @param restrictPoolIds 限制关联池
     * @return true=命中限制池，应阻断自动调库，false=未命中限制池
     */
    static boolean isInAnyPool(List<Long> currentPoolIds, List<Long> restrictPoolIds) {
        if (currentPoolIds == null || currentPoolIds.isEmpty()
                || restrictPoolIds == null || restrictPoolIds.isEmpty()) {
            return false;
        }
        for (Long currentPoolId : currentPoolIds) {
            if (currentPoolId != null && restrictPoolIds.contains(currentPoolId)) {
                return true;
            }
        }
        return false;
    }
}
