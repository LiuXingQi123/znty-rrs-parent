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
        if (req == null) {
            req = new CommonReq();
        }
        List<PoolTreeDto> nodes = commonMapper.queryPoolTreeList(req);
        if (req.getPermissionType() == null || req.getPermissionType().trim().isEmpty()) {
            // 按投资品种或投资池编码收窄可选树，并保留命中节点的祖先
            return retainRequestedPools(nodes, req);
        }
        // 查询当前用户拥有的指定类型投资池权限
        Set<Long> permittedIds = investmentPoolService.queryPermittedPoolIdsByUser(
                req.getCurrentUserId(), req.getPermissionType());
        if (permittedIds == null) {
            // 管理员不做权限裁剪，仍按品种或池编码收窄
            return retainRequestedPools(nodes, req);
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
        // 权限裁剪后再按品种或池编码收窄，祖先只从仍可见的节点里补
        return retainRequestedPools(filtered, req);
    }

    /**
     * 按投资品种或投资池编码保留节点，并补齐命中节点的祖先。
     *
     * @param nodes 当前可见的投资池树节点
     * @param req 公共查询请求
     * @return 收窄后的节点列表；未传品种和池编码时原样返回
     */
    private List<PoolTreeDto> retainRequestedPools(List<PoolTreeDto> nodes, CommonReq req) {
        if (nodes == null || nodes.isEmpty()) {
            return nodes == null ? new ArrayList<PoolTreeDto>() : nodes;
        }
        // 去掉空白编码；空集合表示这个维度不参与过滤
        Set<String> varietyCodes = normalizeCodes(req.getIncludeVarietyCodes());
        Set<String> poolCodes = normalizeCodes(req.getIncludePoolCodes());
        // 两个条件都没传时保持原树，开放日等未收窄的页面不受影响
        if (varietyCodes.isEmpty() && poolCodes.isEmpty()) {
            return nodes;
        }
        // 按 id 建索引，后面才能沿 parentId 补祖先
        Map<Long, PoolTreeDto> nodeMap = new HashMap<>();
        for (PoolTreeDto node : nodes) {
            nodeMap.put(node.getId(), node);
        }
        // 同时传入时取交集；只传其中一个时，另一项视为不限制
        Set<Long> matchedIds = new HashSet<>();
        for (PoolTreeDto node : nodes) {
            // 判断该节点的投资品种是否命中请求
            boolean varietyMatched = varietyCodes.isEmpty() || containsAnyVariety(node.getVarietyCodes(), varietyCodes);
            boolean poolCodeMatched = poolCodes.isEmpty() || poolCodes.contains(node.getPoolCode());
            if (varietyMatched && poolCodeMatched) {
                matchedIds.add(node.getId());
            }
        }
        // 父级自己可以不含目标品种，只要下级命中就留下，树才还能展开
        Set<Long> retainedIds = new HashSet<>();
        for (Long poolId : matchedIds) {
            PoolTreeDto current = nodeMap.get(poolId);
            while (current != null && retainedIds.add(current.getId())) {
                current = nodeMap.get(current.getParentId());
            }
        }
        // 按原查询顺序输出，不打乱 outer_sort / inner_sort
        List<PoolTreeDto> filtered = new ArrayList<>();
        for (PoolTreeDto node : nodes) {
            if (retainedIds.contains(node.getId())) {
                filtered.add(node);
            }
        }
        return filtered;
    }

    /**
     * 去掉空白编码，得到精确匹配用的编码集合。
     *
     * @param codes 前端传入的编码列表
     * @return 去空白后的编码集合
     */
    private Set<String> normalizeCodes(List<String> codes) {
        Set<String> normalized = new LinkedHashSet<>();
        if (codes == null) {
            return normalized;
        }
        for (String code : codes) {
            // 池编码和品种编码都按去空白后的原值精确匹配
            if (code != null && !code.trim().isEmpty()) {
                normalized.add(code.trim());
            }
        }
        return normalized;
    }

    /**
     * 判断投资品种 JSON 是否包含任一目标编码。
     *
     * @param varietyCodes 投资品种 JSON 数组字符串
     * @param expectedCodes 目标品种编码
     * @return 包含任一目标编码时返回 true
     */
    private boolean containsAnyVariety(String varietyCodes, Set<String> expectedCodes) {
        if (varietyCodes == null || varietyCodes.isEmpty() || expectedCodes == null || expectedCodes.isEmpty()) {
            return false;
        }
        for (String code : expectedCodes) {
            // variety_codes 是 JSON 数组，带引号匹配，避免 bond 命中其他编码的子串
            if (varietyCodes.contains("\"" + code + "\"")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 批量查询证券的四类关系主体及其最新主体内评分。
     *
     * @param req Wind 证券代码列表
     * @return 担保人、差额支付承诺人、权益相关主体、原始权益人；仅含有最新内评的主体
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
        // 按去重后的 Wind 证券代码一次性查询四类关系主体及最新内评
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
