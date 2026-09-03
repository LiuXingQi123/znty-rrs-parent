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
     * 批量查询符合 Wind 主体类型要求的担保人及其最新主体内评分。
     *
     * @param req Wind 证券代码列表
     * @return 符合主体类型要求的担保人及类型编码列表；无评分时 totalScore 为空
     */
    public List<GuarantorGradeDto> queryGuarantorGradeList(GuarantorGradeReq req) {
        if (req == null || req.getSecurityCodes() == null || req.getSecurityCodes().isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> normalizedCodes = new LinkedHashSet<>();
        for (String securityCode : req.getSecurityCodes()) {
            if (securityCode != null && !securityCode.trim().isEmpty()) {
                normalizedCodes.add(securityCode.trim());
            }
        }
        if (normalizedCodes.isEmpty()) {
            return new ArrayList<>();
        }
        // 按去重后的 Wind 证券代码一次性筛选合格担保人并查询最新内评
        return commonMapper.queryGuarantorGradeList(new ArrayList<>(normalizedCodes));
    }

    /**
     * 查询当前证券下指定的合格担保人及其最新主体内评分。
     *
     * @param securityCode 证券 Wind 代码
     * @param guarantorCode 担保人主体代码
     * @return 当前证券下的合格担保人及其最新主体内评分；不存在时返回 null
     */
    public GuarantorGradeDto queryGuarantorGrade(String securityCode, String guarantorCode) {
        if (securityCode == null || securityCode.trim().isEmpty()
                || guarantorCode == null || guarantorCode.trim().isEmpty()) {
            return null;
        }
        List<String> securityCodes = new ArrayList<>();
        securityCodes.add(securityCode.trim());
        // 复用按证券查询，保证页面展示与调库规则使用同一套担保人关系口径
        List<GuarantorGradeDto> records = commonMapper.queryGuarantorGradeList(securityCodes);
        String selectedCode = guarantorCode.trim();
        for (GuarantorGradeDto record : records) {
            if (selectedCode.equals(record.getWindcode())) {
                return record;
            }
        }
        return null;
    }
}
