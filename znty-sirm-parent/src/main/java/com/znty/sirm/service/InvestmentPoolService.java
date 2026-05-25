package com.znty.sirm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.sirm.exception.BizException;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.model.FlowOptionDto;
import com.znty.sirm.model.InvestmentPoolBo;
import com.znty.sirm.model.InvestmentPoolDto;
import com.znty.sirm.model.InvestmentPoolReq;
import com.znty.sirm.model.PoolAutoRuleBo;
import com.znty.sirm.model.PoolPermissionBo;
import com.znty.sirm.model.PoolRelationBo;
import com.znty.sirm.model.RoleBo;
import com.znty.sirm.model.UserBo;
import java.util.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 投资池维护业务服务
 */
@Service
public class InvestmentPoolService {

    private static final String OPERATOR_DEFAULT = "system";

    private static final List<String> RELATION_TYPES = Arrays.asList(
            "source",
            "in_restrict",
            "out_restrict",
            "in_linked",
            "out_linked",
            "in_mutex",
            "out_mutex",
            "in_soft_restrict",
            "out_soft_restrict"
    );

    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 查询投资池树
     */
    public List<InvestmentPoolDto> queryPoolTree() {
        List<InvestmentPoolDto> poolList = investmentPoolMapper.queryPoolList().stream()
                .map(this::convertPool)
                .collect(Collectors.toList());
        Map<Long, InvestmentPoolDto> poolMap = new LinkedHashMap<>();
        for (InvestmentPoolDto pool : poolList) {
            poolMap.put(pool.getId(), pool);
        }
        List<InvestmentPoolDto> tree = new ArrayList<>();
        for (InvestmentPoolDto pool : poolList) {
            if (pool.getParentId() == null || !poolMap.containsKey(pool.getParentId())) {
                tree.add(pool);
            } else {
                poolMap.get(pool.getParentId()).getChildren().add(pool);
            }
        }
        return tree;
    }

    /**
     * 查询投资池详情
     */
    public InvestmentPoolDto queryPoolDetail(InvestmentPoolReq req) {
        Long poolId = requirePoolId(req);
        InvestmentPoolBo pool = investmentPoolMapper.queryPoolById(poolId);
        if (pool == null) {
            throw new BizException("投资池不存在");
        }
        InvestmentPoolDto dto = convertPool(pool);
        fillRelationConfig(dto);
        fillAutoRuleConfig(dto);
        fillPermissionConfig(dto);
        return dto;
    }

    /**
     * 保存投资池基础配置
     */
    @Transactional(rollbackFor = Exception.class)
    public InvestmentPoolDto editPoolConfig(InvestmentPoolReq req) {
        Long poolId = requirePoolId(req);
        InvestmentPoolBo oldPool = investmentPoolMapper.queryPoolById(poolId);
        if (oldPool == null) {
            throw new BizException("投资池不存在");
        }
        InvestmentPoolBo pool = buildPoolForEdit(req, oldPool);
        investmentPoolMapper.editPoolConfig(pool);
        investmentPoolMapper.addPoolEvent(poolId, getOperatorId(req), "修改");
        return queryPoolDetail(req);
    }

    /**
     * 保存投资池关系配置
     */
    @Transactional(rollbackFor = Exception.class)
    public InvestmentPoolDto editPoolRelation(InvestmentPoolReq req) {
        Long poolId = requirePoolId(req);
        InvestmentPoolBo pool = investmentPoolMapper.queryPoolById(poolId);
        if (pool == null) {
            throw new BizException("投资池不存在");
        }
        String operatorId = getOperatorId(req);
        investmentPoolMapper.addRelationEventByPoolId(poolId, operatorId, "删除");
        investmentPoolMapper.deleteRelationByPoolId(poolId);
        addRelations(req, poolId, operatorId);

        investmentPoolMapper.addAutoRuleEventByPoolId(poolId, operatorId, "删除");
        investmentPoolMapper.deleteAutoRuleByPoolId(poolId);
        if (req.getAutoInRuleIds() != null) {
            List<String> inDescs = req.getAutoInRuleDescs();
            for (int i = 0; i < req.getAutoInRuleIds().size(); i++) {
                Long ruleId = req.getAutoInRuleIds().get(i);
                String ruleDesc = (inDescs != null && i < inDescs.size()) ? inDescs.get(i) : "";
                addAutoRule(poolId, "auto_in", ruleId, ruleDesc, operatorId);
            }
        }
        if (req.getAutoOutRuleIds() != null) {
            List<String> outDescs = req.getAutoOutRuleDescs();
            for (int i = 0; i < req.getAutoOutRuleIds().size(); i++) {
                Long ruleId = req.getAutoOutRuleIds().get(i);
                String ruleDesc = (outDescs != null && i < outDescs.size()) ? outDescs.get(i) : "";
                addAutoRule(poolId, "auto_out", ruleId, ruleDesc, operatorId);
            }
        }
        return queryPoolDetail(req);
    }

    /**
     * 添加顶级投资池
     */
    @Transactional(rollbackFor = Exception.class)
    public InvestmentPoolDto addRootPool(InvestmentPoolReq req) {
        if (req == null || req.getPoolName() == null || req.getPoolName().trim().isEmpty()) {
            throw new BizException("投资池名称不能为空");
        }
        if (req.getPoolType() == null || req.getPoolType().trim().isEmpty()) {
            throw new BizException("投资池类型不能为空");
        }
        String operatorId = getOperatorId(req);
        InvestmentPoolBo templatePool = null;
        if (req.getTemplatePoolId() != null) {
            templatePool = investmentPoolMapper.queryPoolById(req.getTemplatePoolId());
            if (templatePool == null) {
                throw new BizException("模板投资池不存在");
            }
        }
        InvestmentPoolBo rootPool = buildRootPool(req, templatePool);
        investmentPoolMapper.addPool(rootPool);
        investmentPoolMapper.addPoolEvent(rootPool.getId(), operatorId, "新增");
        if (templatePool != null) {
            copyParentRelationConfig(templatePool.getId(), rootPool.getId(), operatorId);
            copyParentAutoRuleConfig(templatePool.getId(), rootPool.getId(), operatorId);
            copyParentPermissionConfig(templatePool.getId(), rootPool.getId(), operatorId);
        }
        InvestmentPoolReq detailReq = new InvestmentPoolReq();
        detailReq.setId(rootPool.getId());
        return queryPoolDetail(detailReq);
    }

    /**
     * 添加子投资池
     */
    @Transactional(rollbackFor = Exception.class)
    public InvestmentPoolDto addChildPool(InvestmentPoolReq req) {
        if (req == null || req.getParentId() == null) {
            throw new BizException("父级投资池 ID 不能为空");
        }
        if (req.getPoolName() == null || req.getPoolName().trim().isEmpty()) {
            throw new BizException("投资池名称不能为空");
        }
        InvestmentPoolBo parentPool = investmentPoolMapper.queryPoolById(req.getParentId());
        if (parentPool == null) {
            throw new BizException("父级投资池不存在");
        }
        String operatorId = getOperatorId(req);
        InvestmentPoolBo templatePool = null;
        if (req.getTemplatePoolId() != null) {
            templatePool = investmentPoolMapper.queryPoolById(req.getTemplatePoolId());
            if (templatePool == null) {
                throw new BizException("配置模板不存在");
            }
        } else if (Boolean.TRUE.equals(req.getInheritParentConfig())) {
            templatePool = parentPool;
        }
        InvestmentPoolBo childPool = buildChildPool(req, parentPool, templatePool);
        investmentPoolMapper.addPool(childPool);
        investmentPoolMapper.addPoolEvent(childPool.getId(), operatorId, "新增");
        if (templatePool != null) {
            copyParentRelationConfig(templatePool.getId(), childPool.getId(), operatorId);
            copyParentAutoRuleConfig(templatePool.getId(), childPool.getId(), operatorId);
            copyParentPermissionConfig(templatePool.getId(), childPool.getId(), operatorId);
        }
        InvestmentPoolReq detailReq = new InvestmentPoolReq();
        detailReq.setId(childPool.getId());
        return queryPoolDetail(detailReq);
    }

    /**
     * 删除投资池节点及全部子节点
     */
    @Transactional(rollbackFor = Exception.class)
    public InvestmentPoolDto deletePoolNode(InvestmentPoolReq req) {
        Long poolId = requirePoolId(req);
        InvestmentPoolBo pool = investmentPoolMapper.queryPoolById(poolId);
        if (pool == null) {
            throw new BizException("投资池不存在");
        }
        InvestmentPoolDto deletedPool = convertPool(pool);
        String operatorId = getOperatorId(req);
        List<Long> deleteIds = new ArrayList<>();
        collectDescendantPoolIds(poolId, deleteIds);
        investmentPoolMapper.addRelationEventByPoolIds(deleteIds, operatorId, "删除");
        investmentPoolMapper.deleteRelationByPoolIds(deleteIds);
        investmentPoolMapper.addRelationEventByRelationPoolIds(deleteIds, operatorId, "删除");
        investmentPoolMapper.deleteRelationByRelationPoolIds(deleteIds);
        investmentPoolMapper.addAutoRuleEventByPoolIds(deleteIds, operatorId, "删除");
        investmentPoolMapper.deleteAutoRuleByPoolIds(deleteIds);
        investmentPoolMapper.addPermissionEventByPoolIds(deleteIds, operatorId, "删除");
        investmentPoolMapper.deletePermissionByPoolIds(deleteIds);
        investmentPoolMapper.addPoolEventByIds(deleteIds, operatorId, "删除");
        investmentPoolMapper.deletePoolByIds(deleteIds);
        return deletedPool;
    }

    /**
     * 查询流程下拉选项
     */
    public List<FlowOptionDto> queryFlowOptionList() {
        FlowOptionDto empty = new FlowOptionDto();
        empty.setFlowId(null);
        empty.setFlowKey("");
        empty.setFlowName("不需要审批");
        List<FlowOptionDto> options = new ArrayList<>();
        options.add(empty);
        options.addAll(investmentPoolMapper.queryFlowOptionList());
        return options;
    }

    /**
     * 初始化固定投资池树
     */
    @Transactional(rollbackFor = Exception.class)
    public List<InvestmentPoolDto> initPoolTree(InvestmentPoolReq req) {
        if (investmentPoolMapper.queryPoolCount() > 0) {
            return queryPoolTree();
        }
        String operatorId = getOperatorId(req);
        InvestmentPoolBo credit = addSeedPool(null, "credit_bond_root", "信用债大库", "credit_bond", 1, 1, 1, operatorId);
        addLevelPools(credit.getId(), "credit_bond", operatorId);
        addSeedPool(null, "offshore_bond_root", "境外债库", "offshore_bond", 1, 2, 1, operatorId);
        addSeedPool(null, "convertible_bond_root", "转债库", "convertible_bond", 1, 3, 1, operatorId);
        InvestmentPoolBo special = addSeedPool(null, "special_account_root", "专户产品", "special_account", 1, 4, 1, operatorId);
        addLevelPools(special.getId(), "special_account", operatorId);
        return queryPoolTree();
    }

    /**
     * 查询角色列表
     */
    public List<RoleBo> queryRoleList() {
        return investmentPoolMapper.queryRoleList();
    }

    /**
     * 查询人员列表
     */
    public List<UserBo> queryUserList(InvestmentPoolReq req) {
        Long roleId = req.getRoleId();
        String keyword = req.getKeyword();
        List<Long> roleIds = null;
        if (roleId != null) {
            roleIds = new ArrayList<>();
            List<RoleBo> allRoles = investmentPoolMapper.queryRoleList();
            collectDescendantRoleIds(roleId, roleIds, allRoles);
        }
        return investmentPoolMapper.queryUserList(roleIds, keyword);
    }

    /**
     * 递归收集角色及后代角色 ID
     */
    private void collectDescendantRoleIds(Long roleId, List<Long> roleIds, List<RoleBo> allRoles) {
        roleIds.add(roleId);
        for (RoleBo role : allRoles) {
            if (roleId.equals(role.getParentId())) {
                collectDescendantRoleIds(role.getId(), roleIds, allRoles);
            }
        }
    }

    /**
     * 保存投资池权限配置
     */
    @Transactional(rollbackFor = Exception.class)
    public InvestmentPoolDto editPoolPermission(InvestmentPoolReq req) {
        Long poolId = requirePoolId(req);
        InvestmentPoolBo pool = investmentPoolMapper.queryPoolById(poolId);
        if (pool == null) {
            throw new BizException("投资池不存在");
        }
        String operatorId = getOperatorId(req);
        investmentPoolMapper.addPermissionEventByPoolId(poolId, operatorId, "删除");
        investmentPoolMapper.deletePermissionByPoolId(poolId);
        addPermissions(req, poolId, operatorId);
        return queryPoolDetail(req);
    }

    /**
     * 新增固定层级池
     */
    private void addLevelPools(Long parentId, String prefix, String operatorId) {
        addSeedPool(parentId, prefix + "_level_1", "一级库", prefix, 2, 1, 1, operatorId);
        addSeedPool(parentId, prefix + "_level_2", "二级库", prefix, 2, 1, 2, operatorId);
        addSeedPool(parentId, prefix + "_level_3", "三级库", prefix, 2, 1, 3, operatorId);
        addSeedPool(parentId, prefix + "_level_4", "四级库", prefix, 2, 1, 4, operatorId);
        addSeedPool(parentId, prefix + "_level_5", "五级库", prefix, 2, 1, 5, operatorId);
    }

    /**
     * 新增初始化投资池
     */
    private InvestmentPoolBo addSeedPool(Long parentId, String poolCode, String poolName, String poolType,
                                         Integer poolLevel, Integer outerSort, Integer innerSort, String operatorId) {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setParentId(parentId);
        pool.setPoolCode(poolCode);
        pool.setPoolName(poolName);
        pool.setPoolType(poolType);
        pool.setPoolLevel(poolLevel);
        pool.setMarketCodes("[]");
        pool.setVarietyCodes("[\"bond\"]");
        pool.setOuterSort(outerSort);
        pool.setInnerSort(innerSort);
        pool.setStatus("enabled");
        pool.setIsDeleted(0);
        Date now = new Date();
        pool.setCrteTime(now);
        pool.setUpdtTime(now);
        investmentPoolMapper.addPool(pool);
        investmentPoolMapper.addPoolEvent(pool.getId(), operatorId, "新增");
        return pool;
    }

    /**
     * 构建顶级投资池
     */
    private InvestmentPoolBo buildRootPool(InvestmentPoolReq req, InvestmentPoolBo templatePool) {
        InvestmentPoolBo rootPool = new InvestmentPoolBo();
        rootPool.setParentId(null);
        rootPool.setPoolCode(req.getPoolType().trim() + "_root_" + System.currentTimeMillis());
        rootPool.setPoolName(req.getPoolName().trim());
        rootPool.setPoolType(req.getPoolType().trim());
        rootPool.setPoolLevel(1);
        Integer outerSort = req.getOuterSort();
        if (outerSort == null) {
            Integer maxOuterSort = investmentPoolMapper.queryMaxOuterSort();
            outerSort = maxOuterSort == null ? 1 : maxOuterSort + 1;
        }
        rootPool.setOuterSort(outerSort);
        rootPool.setInnerSort(1);
        if (templatePool != null) {
            copyBaseConfig(templatePool, rootPool);
        } else {
            rootPool.setMarketCodes("[]");
            rootPool.setVarietyCodes("[\"bond\"]");
        }
        rootPool.setStatus("enabled");
        rootPool.setIsDeleted(0);
        Date now = new Date();
        rootPool.setCrteTime(now);
        rootPool.setUpdtTime(now);
        return rootPool;
    }

    /**
     * 构建子投资池
     */
    private InvestmentPoolBo buildChildPool(InvestmentPoolReq req, InvestmentPoolBo parentPool, InvestmentPoolBo templatePool) {
        InvestmentPoolBo childPool = new InvestmentPoolBo();
        childPool.setParentId(parentPool.getId());
        childPool.setPoolCode(parentPool.getPoolCode() + "_child_" + System.currentTimeMillis());
        childPool.setPoolName(req.getPoolName().trim());
        childPool.setPoolType(parentPool.getPoolType());
        childPool.setPoolLevel(parentPool.getPoolLevel() == null ? 1 : parentPool.getPoolLevel() + 1);
        childPool.setOuterSort(parentPool.getOuterSort());
        Integer innerSort = req.getInnerSort();
        if (innerSort == null) {
            Integer maxInnerSort = investmentPoolMapper.queryMaxInnerSort(parentPool.getId());
            innerSort = maxInnerSort == null ? 1 : maxInnerSort + 1;
        }
        childPool.setInnerSort(innerSort);
        if (templatePool != null) {
            copyBaseConfig(templatePool, childPool);
        } else {
            childPool.setMarketCodes("[]");
            childPool.setVarietyCodes("[\"bond\"]");
        }
        childPool.setStatus("enabled");
        childPool.setIsDeleted(0);
        Date now = new Date();
        childPool.setCrteTime(now);
        childPool.setUpdtTime(now);
        return childPool;
    }

    /**
     * 复制父级基础配置
     */
    private void copyBaseConfig(InvestmentPoolBo parentPool, InvestmentPoolBo childPool) {
        childPool.setMarketCodes(parentPool.getMarketCodes());
        childPool.setVarietyCodes(parentPool.getVarietyCodes());
        childPool.setHsPoolName(parentPool.getHsPoolName());
        childPool.setInFlowId(parentPool.getInFlowId());
        childPool.setInFlowKey(parentPool.getInFlowKey());
        childPool.setInFlowName(parentPool.getInFlowName());
        childPool.setOutFlowId(parentPool.getOutFlowId());
        childPool.setOutFlowKey(parentPool.getOutFlowKey());
        childPool.setOutFlowName(parentPool.getOutFlowName());
        childPool.setSimpleInFlowId(parentPool.getSimpleInFlowId());
        childPool.setSimpleInFlowKey(parentPool.getSimpleInFlowKey());
        childPool.setSimpleInFlowName(parentPool.getSimpleInFlowName());
        childPool.setSimpleOutFlowId(parentPool.getSimpleOutFlowId());
        childPool.setSimpleOutFlowKey(parentPool.getSimpleOutFlowKey());
        childPool.setSimpleOutFlowName(parentPool.getSimpleOutFlowName());
        childPool.setBatchInFlowId(parentPool.getBatchInFlowId());
        childPool.setBatchInFlowKey(parentPool.getBatchInFlowKey());
        childPool.setBatchInFlowName(parentPool.getBatchInFlowName());
        childPool.setBatchOutFlowId(parentPool.getBatchOutFlowId());
        childPool.setBatchOutFlowKey(parentPool.getBatchOutFlowKey());
        childPool.setBatchOutFlowName(parentPool.getBatchOutFlowName());
        childPool.setMaxCapacity(parentPool.getMaxCapacity());
        childPool.setDescription(parentPool.getDescription());
    }

    /**
     * 复制父级关系配置
     */
    private void copyParentRelationConfig(Long parentPoolId, Long childPoolId, String operatorId) {
        List<PoolRelationBo> relations = investmentPoolMapper.queryRelationList(parentPoolId);
        for (PoolRelationBo parentRelation : relations) {
            PoolRelationBo relation = new PoolRelationBo();
            relation.setPoolId(childPoolId);
            relation.setRelationType(parentRelation.getRelationType());
            relation.setRelationPoolId(parentRelation.getRelationPoolId());
            relation.setRelationPoolName(parentRelation.getRelationPoolName());
            relation.setSortOrder(parentRelation.getSortOrder());
            relation.setRemark(parentRelation.getRemark());
            relation.setIsDeleted(0);
            Date now = new Date();
            relation.setCrteTime(now);
            relation.setUpdtTime(now);
            investmentPoolMapper.addRelation(relation);
            investmentPoolMapper.addRelationEvent(relation.getId(), operatorId, "新增");
        }
    }

    /**
     * 复制父级自动规则配置
     */
    private void copyParentAutoRuleConfig(Long parentPoolId, Long childPoolId, String operatorId) {
        List<PoolAutoRuleBo> rules = investmentPoolMapper.queryAutoRuleList(parentPoolId);
        for (PoolAutoRuleBo parentRule : rules) {
            addAutoRule(childPoolId, parentRule.getRuleType(), parentRule.getRuleId(), parentRule.getRuleDesc(), operatorId);
        }
    }

    /**
     * 递归收集节点及后代节点 ID
     */
    private void collectDescendantPoolIds(Long poolId, List<Long> poolIds) {
        poolIds.add(poolId);
        List<InvestmentPoolBo> childPools = investmentPoolMapper.queryChildPoolList(poolId);
        for (InvestmentPoolBo childPool : childPools) {
            collectDescendantPoolIds(childPool.getId(), poolIds);
        }
    }

    /**
     * 构建投资池修改对象
     */
    private InvestmentPoolBo buildPoolForEdit(InvestmentPoolReq req, InvestmentPoolBo oldPool) {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(oldPool.getId());
        pool.setPoolName(req.getPoolName());
        pool.setMarketCodes(toJson(req.getMarketCodes()));
        pool.setVarietyCodes(toJson(req.getVarietyCodes()));
        pool.setHsPoolName(req.getHsPoolName());
        applyFlow(pool, req);
        pool.setMaxCapacity(req.getMaxCapacity());
        pool.setOuterSort(req.getOuterSort());
        pool.setInnerSort(req.getInnerSort());
        pool.setDescription(req.getDescription());
        pool.setUpdtTime(new Date());
        return pool;
    }

    /**
     * 填充流程快照字段
     */
    private void applyFlow(InvestmentPoolBo pool, InvestmentPoolReq req) {
        FlowOptionDto inFlow = normalizeFlow(req.getInFlow());
        pool.setInFlowId(inFlow.getFlowId());
        pool.setInFlowKey(inFlow.getFlowKey());
        pool.setInFlowName(inFlow.getFlowName());

        FlowOptionDto outFlow = normalizeFlow(req.getOutFlow());
        pool.setOutFlowId(outFlow.getFlowId());
        pool.setOutFlowKey(outFlow.getFlowKey());
        pool.setOutFlowName(outFlow.getFlowName());

        FlowOptionDto simpleInFlow = normalizeFlow(req.getSimpleInFlow());
        pool.setSimpleInFlowId(simpleInFlow.getFlowId());
        pool.setSimpleInFlowKey(simpleInFlow.getFlowKey());
        pool.setSimpleInFlowName(simpleInFlow.getFlowName());

        FlowOptionDto simpleOutFlow = normalizeFlow(req.getSimpleOutFlow());
        pool.setSimpleOutFlowId(simpleOutFlow.getFlowId());
        pool.setSimpleOutFlowKey(simpleOutFlow.getFlowKey());
        pool.setSimpleOutFlowName(simpleOutFlow.getFlowName());

        FlowOptionDto batchInFlow = normalizeFlow(req.getBatchInFlow());
        pool.setBatchInFlowId(batchInFlow.getFlowId());
        pool.setBatchInFlowKey(batchInFlow.getFlowKey());
        pool.setBatchInFlowName(batchInFlow.getFlowName());

        FlowOptionDto batchOutFlow = normalizeFlow(req.getBatchOutFlow());
        pool.setBatchOutFlowId(batchOutFlow.getFlowId());
        pool.setBatchOutFlowKey(batchOutFlow.getFlowKey());
        pool.setBatchOutFlowName(batchOutFlow.getFlowName());
    }

    /**
     * 新增投资池关系
     */
    private void addRelations(InvestmentPoolReq req, Long poolId, String operatorId) {
        Map<String, List<Long>> relationPoolIds = req.getRelationPoolIds();
        if (relationPoolIds == null || relationPoolIds.isEmpty()) {
            return;
        }
        List<Long> allIds = relationPoolIds.values().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> poolNameMap = allIds.isEmpty()
                ? new HashMap<>()
                : investmentPoolMapper.queryPoolByIds(allIds).stream()
                .collect(Collectors.toMap(InvestmentPoolBo::getId, InvestmentPoolBo::getPoolName, (a, b) -> a));
        for (String relationType : RELATION_TYPES) {
            List<Long> ids = relationPoolIds.getOrDefault(relationType, Collections.emptyList());
            int sortOrder = 1;
            for (Long relationPoolId : ids) {
                if (relationPoolId == null) {
                    continue;
                }
                PoolRelationBo relation = new PoolRelationBo();
                relation.setPoolId(poolId);
                relation.setRelationType(relationType);
                relation.setRelationPoolId(relationPoolId);
                relation.setRelationPoolName(poolNameMap.get(relationPoolId));
                relation.setSortOrder(sortOrder++);
                relation.setIsDeleted(0);
                Date now = new Date();
                relation.setCrteTime(now);
                relation.setUpdtTime(now);
                investmentPoolMapper.addRelation(relation);
                investmentPoolMapper.addRelationEvent(relation.getId(), operatorId, "新增");
            }
        }
    }

    /**
     * 新增自动规则备注
     */
    private void addAutoRule(Long poolId, String ruleType, Long ruleId, String ruleDesc, String operatorId) {
        if ((ruleId == null) && (ruleDesc == null || ruleDesc.trim().isEmpty())) {
            return;
        }
        PoolAutoRuleBo rule = new PoolAutoRuleBo();
        rule.setPoolId(poolId);
        rule.setRuleType(ruleType);
        rule.setRuleId(ruleId);
        rule.setRuleDesc(ruleDesc);
        rule.setIsDeleted(0);
        Date now = new Date();
        rule.setCrteTime(now);
        rule.setUpdtTime(now);
        investmentPoolMapper.addAutoRule(rule);
        investmentPoolMapper.addAutoRuleEvent(rule.getId(), operatorId, "新增");
    }

    /**
     * 填充关系配置
     */
    private void fillRelationConfig(InvestmentPoolDto dto) {
        Map<String, List<Long>> relationPoolIds = new HashMap<>();
        for (String relationType : RELATION_TYPES) {
            relationPoolIds.put(relationType, new ArrayList<>());
        }
        List<PoolRelationBo> relations = investmentPoolMapper.queryRelationList(dto.getId());
        for (PoolRelationBo relation : relations) {
            relationPoolIds.computeIfAbsent(relation.getRelationType(), key -> new ArrayList<>())
                    .add(relation.getRelationPoolId());
        }
        dto.setRelationPoolIds(relationPoolIds);
    }

    /**
     * 填充自动规则配置
     */
    private void fillAutoRuleConfig(InvestmentPoolDto dto) {
        List<PoolAutoRuleBo> rules = investmentPoolMapper.queryAutoRuleList(dto.getId());
        for (PoolAutoRuleBo rule : rules) {
            if ("auto_in".equals(rule.getRuleType())) {
                dto.getAutoInRuleIds().add(rule.getRuleId());
                dto.getAutoInRuleDescs().add(rule.getRuleDesc());
            }
            if ("auto_out".equals(rule.getRuleType())) {
                dto.getAutoOutRuleIds().add(rule.getRuleId());
                dto.getAutoOutRuleDescs().add(rule.getRuleDesc());
            }
        }
    }

    /**
     * 填充权限配置
     */
    private void fillPermissionConfig(InvestmentPoolDto dto) {
        List<PoolPermissionBo> permissions = investmentPoolMapper.queryPermissionList(dto.getId());
        dto.setPermissions(permissions != null ? permissions : new ArrayList<>());
    }

    /**
     * 复制父级权限配置
     */
    private void copyParentPermissionConfig(Long parentPoolId, Long childPoolId, String operatorId) {
        List<PoolPermissionBo> permissions = investmentPoolMapper.queryPermissionList(parentPoolId);
        for (PoolPermissionBo parentPermission : permissions) {
            PoolPermissionBo permission = new PoolPermissionBo();
            permission.setPoolId(childPoolId);
            permission.setPermissionType(parentPermission.getPermissionType());
            permission.setSubjectType(parentPermission.getSubjectType());
            permission.setSubjectId(parentPermission.getSubjectId());
            permission.setSubjectName(parentPermission.getSubjectName());
            permission.setIsDeleted(0);
            Date now = new Date();
            permission.setCrteTime(now);
            permission.setUpdtTime(now);
            investmentPoolMapper.addPermission(permission);
            investmentPoolMapper.addPermissionEvent(permission.getId(), operatorId, "新增");
        }
    }

    /**
     * 新增权限配置
     */
    private void addPermissions(InvestmentPoolReq req, Long poolId, String operatorId) {
        List<PoolPermissionBo> permissions = req.getPermissions();
        if (permissions == null || permissions.isEmpty()) {
            return;
        }
        for (PoolPermissionBo perm : permissions) {
            PoolPermissionBo permission = new PoolPermissionBo();
            permission.setPoolId(poolId);
            permission.setPermissionType(perm.getPermissionType());
            permission.setSubjectType(perm.getSubjectType());
            permission.setSubjectId(perm.getSubjectId());
            permission.setSubjectName(perm.getSubjectName());
            permission.setIsDeleted(0);
            Date now = new Date();
            permission.setCrteTime(now);
            permission.setUpdtTime(now);
            investmentPoolMapper.addPermission(permission);
            investmentPoolMapper.addPermissionEvent(permission.getId(), operatorId, "新增");
        }
    }

    /**
     * 转换投资池对象
     */
    private InvestmentPoolDto convertPool(InvestmentPoolBo pool) {
        InvestmentPoolDto dto = new InvestmentPoolDto();
        dto.setId(pool.getId());
        dto.setParentId(pool.getParentId());
        dto.setPoolCode(pool.getPoolCode());
        dto.setPoolName(pool.getPoolName());
        dto.setPoolType(pool.getPoolType());
        dto.setPoolLevel(pool.getPoolLevel());
        dto.setMarketCodes(fromJson(pool.getMarketCodes()));
        dto.setVarietyCodes(fromJson(pool.getVarietyCodes()));
        dto.setHsPoolName(pool.getHsPoolName());
        dto.setInFlow(buildFlow(pool.getInFlowId(), pool.getInFlowKey(), pool.getInFlowName()));
        dto.setOutFlow(buildFlow(pool.getOutFlowId(), pool.getOutFlowKey(), pool.getOutFlowName()));
        dto.setSimpleInFlow(buildFlow(pool.getSimpleInFlowId(), pool.getSimpleInFlowKey(), pool.getSimpleInFlowName()));
        dto.setSimpleOutFlow(buildFlow(pool.getSimpleOutFlowId(), pool.getSimpleOutFlowKey(), pool.getSimpleOutFlowName()));
        dto.setBatchInFlow(buildFlow(pool.getBatchInFlowId(), pool.getBatchInFlowKey(), pool.getBatchInFlowName()));
        dto.setBatchOutFlow(buildFlow(pool.getBatchOutFlowId(), pool.getBatchOutFlowKey(), pool.getBatchOutFlowName()));
        dto.setMaxCapacity(pool.getMaxCapacity());
        dto.setOuterSort(pool.getOuterSort());
        dto.setInnerSort(pool.getInnerSort());
        dto.setDescription(pool.getDescription());
        dto.setStatus(pool.getStatus());
        dto.setCrteTime(pool.getCrteTime());
        dto.setUpdtTime(pool.getUpdtTime());
        return dto;
    }

    /**
     * 构建流程选项
     */
    private FlowOptionDto buildFlow(Long flowId, String flowKey, String flowName) {
        FlowOptionDto flow = new FlowOptionDto();
        flow.setFlowId(flowId);
        flow.setFlowKey(flowKey);
        flow.setFlowName(flowName);
        return flow;
    }

    /**
     * 规范化流程选项
     */
    private FlowOptionDto normalizeFlow(FlowOptionDto flow) {
        if (flow == null || flow.getFlowId() == null) {
            return new FlowOptionDto();
        }
        return flow;
    }

    /**
     * 校验投资池 ID
     */
    private Long requirePoolId(InvestmentPoolReq req) {
        if (req == null || req.getId() == null) {
            throw new BizException("投资池 ID 不能为空");
        }
        return req.getId();
    }

    /**
     * 获取经办人 ID
     */
    private String getOperatorId(InvestmentPoolReq req) {
        if (req == null || req.getOperatorId() == null || req.getOperatorId().trim().isEmpty()) {
            return OPERATOR_DEFAULT;
        }
        return req.getOperatorId();
    }

    /**
     * 列表转 JSON 字符串
     */
    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? Collections.emptyList() : values);
        } catch (Exception e) {
            throw new BizException("多选字段转换失败");
        }
    }

    /**
     * JSON 字符串转列表
     */
    private List<String> fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
