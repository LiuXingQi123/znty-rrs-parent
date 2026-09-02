package com.znty.rrs.service;

import com.znty.rrs.entity.common.CommonReq;
import com.znty.rrs.entity.common.GuarantorGradeDto;
import com.znty.rrs.entity.common.GuarantorGradeReq;
import com.znty.rrs.mapper.CommonMapper;
import com.znty.rrs.entity.common.PoolTreeDto;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 公共查询业务服务
 */
@Service
public class CommonService {

    /** 公共查询数据访问组件 */
    @Resource
    private CommonMapper commonMapper;

    /** 投资池业务服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /**
     * 查询投资池树节点列表
     *
     * @param req 公共查询请求
     * @return 投资池树节点列表，poolName 为节点名称，poolFullName 为全路径名称
     */
    public List<PoolTreeDto> queryPoolTreeList(CommonReq req) {
        List<PoolTreeDto> nodes = commonMapper.queryPoolTreeList(req);
        if (req.getPermissionType() == null || req.getPermissionType().trim().isEmpty()) {
            return nodes;
        }
        // 查询当前用户拥有的指定类型投资池权限
        Set<Long> permittedIds = investmentPoolService.queryPermittedPoolIdsByUser(
                req.getCurrentUserId(), req.getPermissionType());
        if (permittedIds == null) {
            return nodes;
        }
        Map<Long, PoolTreeDto> nodeMap = new HashMap<>();
        for (PoolTreeDto node : nodes) {
            nodeMap.put(node.getId(), node);
        }
        Set<Long> retainedIds = new HashSet<>();
        for (Long poolId : permittedIds) {
            PoolTreeDto current = nodeMap.get(poolId);
            while (current != null && retainedIds.add(current.getId())) {
                current = nodeMap.get(current.getParentId());
            }
        }
        List<PoolTreeDto> filtered = new ArrayList<>();
        for (PoolTreeDto node : nodes) {
            if (retainedIds.contains(node.getId())) {
                filtered.add(node);
            }
        }
        return filtered;
    }

    /**
     * 批量查询担保人的最新主体内评分。
     *
     * @param req Wind 主体代码列表
     * @return 查到评分的担保人列表；无评分的代码不返回记录
     */
    public List<GuarantorGradeDto> queryGuarantorGradeList(GuarantorGradeReq req) {
        if (req == null || req.getWindCodes() == null || req.getWindCodes().isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> normalizedCodes = new LinkedHashSet<>();
        for (String windCode : req.getWindCodes()) {
            if (windCode != null && !windCode.trim().isEmpty()) {
                normalizedCodes.add(windCode.trim());
            }
        }
        if (normalizedCodes.isEmpty()) {
            return new ArrayList<>();
        }
        // 按去重后的 Wind 主体代码一次性查询最新内评
        return commonMapper.queryGuarantorGradeList(new ArrayList<>(normalizedCodes));
    }

    /**
     * 查询单个担保人的最新主体内评分。
     *
     * @param windCode Wind 主体代码
     * @return 最新主体内评分；未查到时返回 null
     */
    public GuarantorGradeDto queryLatestGuarantorGrade(String windCode) {
        if (windCode == null || windCode.trim().isEmpty()) {
            return null;
        }
        List<String> windCodes = new ArrayList<>();
        windCodes.add(windCode.trim());
        // 复用批量查询，保证页面展示与调库规则使用同一套最新记录口径
        List<GuarantorGradeDto> records = commonMapper.queryGuarantorGradeList(windCodes);
        return records.isEmpty() ? null : records.get(0);
    }
}
