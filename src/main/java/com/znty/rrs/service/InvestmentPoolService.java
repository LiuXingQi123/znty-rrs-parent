package com.znty.rrs.service;

import com.znty.rrs.common.enums.PoolType;

import com.znty.rrs.common.enums.EventType;

import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.common.enums.RuleType;
import com.znty.rrs.common.enums.PoolStatus;
import com.znty.rrs.common.enums.HandlerType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.entity.flow.FlowOptionDto;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.investmentpool.InvestmentPoolDto;
import com.znty.rrs.entity.investmentpool.InvestmentPoolReq;
import com.znty.rrs.entity.bo.PoolAutoRuleBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.RoleBo;
import com.znty.rrs.entity.common.RoleDto;
import com.znty.rrs.entity.bo.UserBo;
import com.znty.rrs.entity.common.UserDto;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * 投资池维护服务。
 * <p>负责投资池树结构的 CRUD（顶级池/子池新增、配置编辑、节点删除），
 * 以及投资池关系配置、自动规则配置、权限配置的维护；
 * 同时提供初始化种子数据、角色人员下拉选项等辅助查询功能。</p>
 */
@Service
public class InvestmentPoolService {

    /** 默认操作人标识 */
    private static final String OPERATOR_DEFAULT = "system";

    /** 投资池数据访问组件 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** JSON 序列化组件 */
    @Resource
    private ObjectMapper objectMapper;

    /** 管理员用户 ID */
    private static final String ADMIN_USER_ID = "1";

    /**
     * 查询当前用户通过本人或角色拥有指定类型权限的投资池。
     *
     * @return 管理员返回 null 表示不限制，普通用户返回权限池集合
     */
    public Set<Long> queryPermittedPoolIdsByUser(String currentUserId, String permissionType) {
        if (ADMIN_USER_ID.equals(currentUserId)) {
            return null;
        }
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            throw new BizException("当前用户 ID 不能为空");
        }
        Long userId;
        try {
            userId = Long.valueOf(currentUserId.trim());
        } catch (NumberFormatException e) {
            throw new BizException("当前用户 ID 不合法");
        }
        Set<Long> roleIds = new HashSet<>(investmentPoolMapper.queryUserRoleIdList(userId));
        Set<Long> poolIds = new HashSet<>();
        for (PoolPermissionBo permission : investmentPoolMapper.queryPermissionListByType(permissionType)) {
            if (permission.getPoolId() == null || permission.getHandlerId() == null) {
                continue;
            }
            if (HandlerType.USER.getCode().equals(permission.getHandlerType())
                    && userId.equals(permission.getHandlerId())) {
                poolIds.add(permission.getPoolId());
            } else if (HandlerType.ROLE.getCode().equals(permission.getHandlerType())
                    && roleIds.contains(permission.getHandlerId())) {
                poolIds.add(permission.getPoolId());
            }
        }
        return poolIds;
    }

    /**
     * 查询投资池列表（树结构由前端组装）
     */
    public List<InvestmentPoolDto> queryPoolList(InvestmentPoolReq req) {
        return investmentPoolMapper.queryPoolList().stream()
                // 将投资池持久化对象转换为接口返回对象
                .map(this::convertPool)
                .collect(Collectors.toList());
    }

    /** 查询投资池基础数据列表，供其他查询模块复用 */
    public List<InvestmentPoolBo> queryPoolBoList() {
        return investmentPoolMapper.queryPoolList();
    }

    /** 查询全部投资池 ID → 全路径名称映射 */
    public Map<Long, String> queryPoolFullNameMap() {
        Map<Long, String> fullNameMap = new HashMap<>();
        List<InvestmentPoolDto> poolList = investmentPoolMapper.queryPoolFullNameList();
        for (InvestmentPoolDto pool : poolList) {
            fullNameMap.put(pool.getId(), pool.getPoolFullName());
        }
        return fullNameMap;
    }

    /**
     * 查询投资池详情
     */
    public InvestmentPoolDto queryPoolDetail(InvestmentPoolReq req) {
        // 校验投资池 ID
        Long poolId = requirePoolId(req);
        InvestmentPoolBo pool = investmentPoolMapper.queryPoolById(poolId);
        if (pool == null) {
            throw new BizException("投资池不存在");
        }
        // 转换投资池对象
        InvestmentPoolDto dto = convertPool(pool);
        // 填充关系配置
        fillRelationConfig(dto);
        // 填充自动规则配置
        fillAutoRuleConfig(dto);
        // 填充权限配置
        fillPermissionConfig(dto);
        return dto;
    }

    /**
     * 保存投资池基础配置
     */
    @Transactional(rollbackFor = Exception.class)
    public InvestmentPoolDto editPoolConfig(InvestmentPoolReq req) {
        // 校验投资池 ID
        Long poolId = requirePoolId(req);
        InvestmentPoolBo oldPool = investmentPoolMapper.queryPoolById(poolId);
        if (oldPool == null) {
            throw new BizException("投资池不存在");
        }
        // 构建投资池修改对象
        InvestmentPoolBo pool = buildPoolForEdit(req, oldPool);
        investmentPoolMapper.editPoolConfig(pool);
        // 解析经办人 ID
        investmentPoolMapper.addPoolEvent(poolId, getOperatorId(req), EventType.EDIT_CN.getCode());
        return queryPoolDetail(req);
    }

    /**
     * 保存投资池关系配置（全量替换模式：先删旧关系/规则，再逐条插入新数据）
     */
    @Transactional(rollbackFor = Exception.class)
    public InvestmentPoolDto editPoolRelation(InvestmentPoolReq req) {
        // 校验投资池 ID
        Long poolId = requirePoolId(req);
        InvestmentPoolBo pool = investmentPoolMapper.queryPoolById(poolId);
        if (pool == null) {
            throw new BizException("投资池不存在");
        }
        // 解析经办人 ID
        String operatorId = getOperatorId(req);
        // 先记录删除事件，再删除旧关系，最后写入新关系
        investmentPoolMapper.addRelationEventByPoolId(poolId, operatorId, EventType.DELETE_CN.getCode());
        investmentPoolMapper.deleteRelationByPoolId(poolId);
        // 新增投资池关系配置（按预定义类型顺序逐条写入
        addRelations(req, poolId, operatorId);

        // 全量替换自动规则：先删旧的调入/调出规则，再按请求顺序逐条插入
        investmentPoolMapper.addAutoRuleEventByPoolId(poolId, operatorId, EventType.DELETE_CN.getCode());
        investmentPoolMapper.deleteAutoRuleByPoolId(poolId);
        if (req.getAutoInRuleIds() != null) {
            List<String> inDescs = req.getAutoInRuleDescs();
            for (int i = 0; i < req.getAutoInRuleIds().size(); i++) {
                Long ruleId = req.getAutoInRuleIds().get(i);
                // 下标对齐取备注，防止 inDescs 列表长度不足时越界
                String ruleDesc = (inDescs != null && i < inDescs.size()) ? inDescs.get(i) : "";
                // 新增自动规则备注
                addAutoRule(poolId, RuleType.AUTO_IN.getCode(), ruleId, ruleDesc, operatorId);
            }
        }
        if (req.getAutoOutRuleIds() != null) {
            List<String> outDescs = req.getAutoOutRuleDescs();
            for (int i = 0; i < req.getAutoOutRuleIds().size(); i++) {
                Long ruleId = req.getAutoOutRuleIds().get(i);
                // 下标对齐取备注，防止 outDescs 列表长度不足时越界
                String ruleDesc = (outDescs != null && i < outDescs.size()) ? outDescs.get(i) : "";
                // 新增自动规则备注
                addAutoRule(poolId, RuleType.AUTO_OUT.getCode(), ruleId, ruleDesc, operatorId);
            }
        }
        return queryPoolDetail(req);
    }

    /**
     * 添加顶级投资池（parentId 为 null），可选指定模板池以复制其关系/规则/权限配置
     */
    @Transactional(rollbackFor = Exception.class)
    public InvestmentPoolDto addRootPool(InvestmentPoolReq req) {
        if (req == null || req.getPoolName() == null || req.getPoolName().trim().isEmpty()) {
            throw new BizException("投资池名称不能为空");
        }
        if (req.getPoolType() == null || req.getPoolType().trim().isEmpty()) {
            throw new BizException("投资池类型不能为空");
        }
        // 解析经办人 ID
        String operatorId = getOperatorId(req);
        InvestmentPoolBo templatePool = null;
        // 指定了模板池时，校验模板池是否存在，后续将复制其配置
        if (req.getTemplatePoolId() != null) {
            templatePool = investmentPoolMapper.queryPoolById(req.getTemplatePoolId());
            if (templatePool == null) {
                throw new BizException("模板投资池不存在");
            }
        }
        // 构建顶级投资池
        InvestmentPoolBo rootPool = buildRootPool(req, templatePool);
        investmentPoolMapper.addPool(rootPool);
        investmentPoolMapper.addPoolEvent(rootPool.getId(), operatorId, EventType.ADD_CN.getCode());
        // 有模板池时，将模板的关系/自动规则/权限配置复制到新顶级池
        if (templatePool != null) {
            // 复制父级关系配置
            copyParentRelationConfig(templatePool.getId(), rootPool.getId(), operatorId);
            // 复制父级自动规则配置
            copyParentAutoRuleConfig(templatePool.getId(), rootPool.getId(), operatorId);
            // 复制父级权限配置
            copyParentPermissionConfig(templatePool.getId(), rootPool.getId(), operatorId);
        }
        InvestmentPoolReq detailReq = new InvestmentPoolReq();
        detailReq.setId(rootPool.getId());
        return queryPoolDetail(detailReq);
    }

    /**
     * 添加子投资池（继承父级 poolType 和层级），可选指定模板池或继承父级配置
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
        // 解析经办人 ID
        String operatorId = getOperatorId(req);
        InvestmentPoolBo templatePool = null;
        if (req.getTemplatePoolId() != null) {
            // 明确指定了模板池，优先使用
            templatePool = investmentPoolMapper.queryPoolById(req.getTemplatePoolId());
            if (templatePool == null) {
                throw new BizException("配置模板不存在");
            }
        } else if (Boolean.TRUE.equals(req.getInheritParentConfig())) {
            // 未指定模板但勾选了继承父级配置，则以父级池作为模板
            templatePool = parentPool;
        }
        // 构建子投资池
        InvestmentPoolBo childPool = buildChildPool(req, parentPool, templatePool);
        investmentPoolMapper.addPool(childPool);
        investmentPoolMapper.addPoolEvent(childPool.getId(), operatorId, EventType.ADD_CN.getCode());
        // 有模板池时，将模板的关系/自动规则/权限配置复制到新子池
        if (templatePool != null) {
            // 复制父级关系配置
            copyParentRelationConfig(templatePool.getId(), childPool.getId(), operatorId);
            // 复制父级自动规则配置
            copyParentAutoRuleConfig(templatePool.getId(), childPool.getId(), operatorId);
            // 复制父级权限配置
            copyParentPermissionConfig(templatePool.getId(), childPool.getId(), operatorId);
        }
        InvestmentPoolReq detailReq = new InvestmentPoolReq();
        detailReq.setId(childPool.getId());
        return queryPoolDetail(detailReq);
    }

    /**
     * 删除投资池节点及其全部后代节点（递归），同时级联删除关联的关系/规则/权限数据
     */
    @Transactional(rollbackFor = Exception.class)
    public InvestmentPoolDto deletePoolNode(InvestmentPoolReq req) {
        // 校验投资池 ID
        Long poolId = requirePoolId(req);
        InvestmentPoolBo pool = investmentPoolMapper.queryPoolById(poolId);
        if (pool == null) {
            throw new BizException("投资池不存在");
        }
        // 删除前保留被删池信息用于返回
        InvestmentPoolDto deletedPool = convertPool(pool);
        // 解析经办人 ID
        String operatorId = getOperatorId(req);
        // 递归收集当前节点及全部后代节点 ID，批量删除
        List<Long> deleteIds = new ArrayList<>();
        // 递归收集节点及后代节点 ID
        collectDescendantPoolIds(poolId, deleteIds);
        // 删除这些池自身的关系记录（作为主池），并记录事件
        investmentPoolMapper.addRelationEventByPoolIds(deleteIds, operatorId, EventType.DELETE_CN.getCode());
        investmentPoolMapper.deleteRelationByPoolIds(deleteIds);
        // 删除其他池引用这些池的关系记录（作为关联池），避免悬挂引用
        investmentPoolMapper.addRelationEventByRelationPoolIds(deleteIds, operatorId, EventType.DELETE_CN.getCode());
        investmentPoolMapper.deleteRelationByRelationPoolIds(deleteIds);
        investmentPoolMapper.addAutoRuleEventByPoolIds(deleteIds, operatorId, EventType.DELETE_CN.getCode());
        investmentPoolMapper.deleteAutoRuleByPoolIds(deleteIds);
        investmentPoolMapper.addPermissionEventByPoolIds(deleteIds, operatorId, EventType.DELETE_CN.getCode());
        investmentPoolMapper.deletePermissionByPoolIds(deleteIds);
        investmentPoolMapper.addPoolEventByIds(deleteIds, operatorId, EventType.DELETE_CN.getCode());
        investmentPoolMapper.deletePoolByIds(deleteIds);
        return deletedPool;
    }

    /**
     * 查询流程下拉选项
     */
    public List<FlowOptionDto> queryFlowOptionList(InvestmentPoolReq req) {
        return investmentPoolMapper.queryFlowOptionList();
    }

    /**
     * 初始化固定投资池列表（幂等：若已有数据则直接返回，避免重复创建种子数据）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<InvestmentPoolDto> addSeedPoolList(InvestmentPoolReq req) {
        // 幂等校验：已有投资池记录时直接返回，不重复初始化
        if (investmentPoolMapper.queryPoolTotalCount() > 0) {
            return queryPoolList(req);
        }
        // 解析经办人 ID
        String operatorId = getOperatorId(req);
        // 创建信用债大库顶级节点，并追加一级~五级子库
        InvestmentPoolBo credit = addSeedPool(null, "credit_bond_root", "信用债大库", PoolType.CREDIT_BOND.getCode(), 1, 1, 1, operatorId);
        // 新增固定层级池
        addLevelPools(credit.getId(), PoolType.CREDIT_BOND.getCode(), operatorId);
        // 新增初始化投资池
        addSeedPool(null, "offshore_bond_root", "境外债库", PoolType.OFFSHORE_BOND.getCode(), 1, 2, 1, operatorId);
        // 新增初始化投资池
        addSeedPool(null, "convertible_bond_root", "转债库", PoolType.CONVERTIBLE_BOND.getCode(), 1, 3, 1, operatorId);
        // 创建专户产品顶级节点，并追加一级~五级子库
        InvestmentPoolBo special = addSeedPool(null, "special_account_root", "专户产品", PoolType.SPECIAL_ACCOUNT.getCode(), 1, 4, 1, operatorId);
        // 新增固定层级池
        addLevelPools(special.getId(), PoolType.SPECIAL_ACCOUNT.getCode(), operatorId);
        // 新增初始化投资池
        addSeedPool(null, "crmw_root", "CRMW库", PoolType.CRMW.getCode(), 1, 8, 1, operatorId);
        return queryPoolList(req);
    }

    /**
     * 查询角色列表
     */
    public List<RoleDto> queryRoleList(InvestmentPoolReq req) {
        return investmentPoolMapper.queryRoleList().stream()
                // 将角色持久化对象转换为接口返回对象
                .map(this::convertRole)
                .collect(Collectors.toList());
    }

    /**
     * 查询人员列表（可按角色树过滤：传入角色 ID 时，递归包含其全部子角色下的人员）
     */
    public List<UserDto> queryUserList(InvestmentPoolReq req) {
        Long roleId = req.getRoleId();
        String keyword = req.getKeyword();
        List<Long> roleIds = null;
        if (roleId != null) {
            // 指定了角色时，递归收集该角色及其子角色 ID，以支持角色树过滤
            roleIds = new ArrayList<>();
            List<RoleBo> allRoles = investmentPoolMapper.queryRoleList();
            // 递归收集角色及后代角色 ID
            collectDescendantRoleIds(roleId, roleIds, allRoles);
        }
        return investmentPoolMapper.queryUserList(roleIds, keyword).stream()
                // 将用户持久化对象转换为接口返回对象
                .map(this::convertUser)
                .collect(Collectors.toList());
    }

    /** RoleBo → RoleDto */
    private RoleDto convertRole(RoleBo bo) {
        RoleDto dto = new RoleDto();
        dto.setId(bo.getId());
        dto.setRoleName(bo.getName());
        dto.setParentId(bo.getParentId());
        dto.setSortOrder(bo.getSortOrder());
        return dto;
    }

    /** UserBo → UserDto */
    private UserDto convertUser(UserBo bo) {
        UserDto dto = new UserDto();
        dto.setId(bo.getId());
        dto.setUserName(bo.getName());
        dto.setRoleName(bo.getRoleName());
        return dto;
    }

    /**
     * 递归收集角色及后代角色 ID
     */
    private void collectDescendantRoleIds(Long roleId, List<Long> roleIds, List<RoleBo> allRoles) {
        roleIds.add(roleId);
        for (RoleBo role : allRoles) {
            if (roleId.equals(role.getParentId())) {
                // 递归收集角色及后代角色 ID
                collectDescendantRoleIds(role.getId(), roleIds, allRoles);
            }
        }
    }

    /**
     * 保存投资池权限配置
     */
    @Transactional(rollbackFor = Exception.class)
    public InvestmentPoolDto editPoolPermission(InvestmentPoolReq req) {
        // 校验投资池 ID
        Long poolId = requirePoolId(req);
        InvestmentPoolBo pool = investmentPoolMapper.queryPoolById(poolId);
        if (pool == null) {
            throw new BizException("投资池不存在");
        }
        // 解析经办人 ID
        String operatorId = getOperatorId(req);
        investmentPoolMapper.addPermissionEventByPoolId(poolId, operatorId, EventType.DELETE_CN.getCode());
        investmentPoolMapper.deletePermissionByPoolId(poolId);
        // 新增权限配置
        addPermissions(req, poolId, operatorId);
        return queryPoolDetail(req);
    }

    /**
     * 新增固定层级池
     */
    private void addLevelPools(Long parentId, String prefix, String operatorId) {
        // 新增初始化投资池
        addSeedPool(parentId, prefix + "_level_1", "一级库", prefix, 2, 1, 1, operatorId);
        // 新增初始化投资池
        addSeedPool(parentId, prefix + "_level_2", "二级库", prefix, 2, 1, 2, operatorId);
        // 新增初始化投资池
        addSeedPool(parentId, prefix + "_level_3", "三级库", prefix, 2, 1, 3, operatorId);
        // 新增初始化投资池
        addSeedPool(parentId, prefix + "_level_4", "四级库", prefix, 2, 1, 4, operatorId);
        // 新增初始化投资池
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
        pool.setStatus(PoolStatus.ENABLED.getCode());
        pool.setIsDeleted(0);
        Date now = new Date();
        pool.setCrteTime(now);
        pool.setUpdtTime(now);
        investmentPoolMapper.addPool(pool);
        investmentPoolMapper.addPoolEvent(pool.getId(), operatorId, EventType.ADD_CN.getCode());
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
            Integer maxOuterSort = investmentPoolMapper.queryMaxOuterSortValue();
            outerSort = maxOuterSort == null ? 1 : maxOuterSort + 1;
        }
        rootPool.setOuterSort(outerSort);
        rootPool.setInnerSort(1);
        if (templatePool != null) {
            // 复制父级基础配置
            copyBaseConfig(templatePool, rootPool);
        } else {
            rootPool.setMarketCodes("[]");
            rootPool.setVarietyCodes("[\"bond\"]");
        }
        rootPool.setStatus(PoolStatus.ENABLED.getCode());
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
            Integer maxInnerSort = investmentPoolMapper.queryMaxInnerSortValue(parentPool.getId());
            innerSort = maxInnerSort == null ? 1 : maxInnerSort + 1;
        }
        childPool.setInnerSort(innerSort);
        if (templatePool != null) {
            // 复制父级基础配置
            copyBaseConfig(templatePool, childPool);
        } else {
            childPool.setMarketCodes("[]");
            childPool.setVarietyCodes("[\"bond\"]");
        }
        childPool.setStatus(PoolStatus.ENABLED.getCode());
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
        childPool.setInReportRestriction(parentPool.getInReportRestriction());
        childPool.setOutReportRestriction(parentPool.getOutReportRestriction());
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
            investmentPoolMapper.addRelationEvent(relation.getId(), operatorId, EventType.ADD_CN.getCode());
        }
    }

    /**
     * 复制父级自动规则配置
     */
    private void copyParentAutoRuleConfig(Long parentPoolId, Long childPoolId, String operatorId) {
        List<PoolAutoRuleBo> rules = investmentPoolMapper.queryAutoRuleList(parentPoolId);
        for (PoolAutoRuleBo parentRule : rules) {
            // 新增自动规则备注
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
            // 递归收集节点及后代节点 ID
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
        // 序列化业务数据为 JSON
        pool.setMarketCodes(toJson(req.getMarketCodes()));
        // 序列化业务数据为 JSON
        pool.setVarietyCodes(toJson(req.getVarietyCodes()));
        pool.setHsPoolName(req.getHsPoolName());
        // 填充流程快照字段
        applyFlow(pool, req);
        pool.setInReportRestriction(req.getInReportRestriction());
        pool.setOutReportRestriction(req.getOutReportRestriction());
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
        // 规范化流程选项
        FlowOptionDto inFlow = normalizeFlow(req.getInFlow());
        pool.setInFlowId(inFlow.getFlowId());
        pool.setInFlowKey(inFlow.getFlowKey());
        pool.setInFlowName(inFlow.getFlowName());

        // 规范化流程选项
        FlowOptionDto outFlow = normalizeFlow(req.getOutFlow());
        pool.setOutFlowId(outFlow.getFlowId());
        pool.setOutFlowKey(outFlow.getFlowKey());
        pool.setOutFlowName(outFlow.getFlowName());

        // 规范化流程选项
        FlowOptionDto simpleInFlow = normalizeFlow(req.getSimpleInFlow());
        pool.setSimpleInFlowId(simpleInFlow.getFlowId());
        pool.setSimpleInFlowKey(simpleInFlow.getFlowKey());
        pool.setSimpleInFlowName(simpleInFlow.getFlowName());

        // 规范化流程选项
        FlowOptionDto simpleOutFlow = normalizeFlow(req.getSimpleOutFlow());
        pool.setSimpleOutFlowId(simpleOutFlow.getFlowId());
        pool.setSimpleOutFlowKey(simpleOutFlow.getFlowKey());
        pool.setSimpleOutFlowName(simpleOutFlow.getFlowName());

        // 规范化流程选项
        FlowOptionDto batchInFlow = normalizeFlow(req.getBatchInFlow());
        pool.setBatchInFlowId(batchInFlow.getFlowId());
        pool.setBatchInFlowKey(batchInFlow.getFlowKey());
        pool.setBatchInFlowName(batchInFlow.getFlowName());

        // 规范化流程选项
        FlowOptionDto batchOutFlow = normalizeFlow(req.getBatchOutFlow());
        pool.setBatchOutFlowId(batchOutFlow.getFlowId());
        pool.setBatchOutFlowKey(batchOutFlow.getFlowKey());
        pool.setBatchOutFlowName(batchOutFlow.getFlowName());
    }

    /**
     * 新增投资池关系配置（按预定义类型顺序逐条写入，快照关联池名称以备展示）
     */
    private void addRelations(InvestmentPoolReq req, Long poolId, String operatorId) {
        Map<String, List<Long>> relationPoolIds = req.getRelationPoolIds();
        if (relationPoolIds == null || relationPoolIds.isEmpty()) {
            return;
        }
        // 收集所有关联池 ID，批量查询名称快照，避免逐条查询
        List<Long> allIds = relationPoolIds.values().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> poolNameMap = allIds.isEmpty()
                ? new HashMap<>()
                : investmentPoolMapper.queryPoolByIdsList(allIds).stream()
                .collect(Collectors.toMap(InvestmentPoolBo::getId, InvestmentPoolBo::getPoolName, (a, b) -> a));
        // 按固定类型顺序处理，保证写入顺序的一致性
        for (String relationType : java.util.Arrays.stream(RelationType.values()).map(RelationType::getCode).collect(java.util.stream.Collectors.toList())) {
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
                // 快照关联池名称，防止关联池改名后历史记录无法显示
                relation.setRelationPoolName(poolNameMap.get(relationPoolId));
                relation.setSortOrder(sortOrder++);
                relation.setIsDeleted(0);
                Date now = new Date();
                relation.setCrteTime(now);
                relation.setUpdtTime(now);
                investmentPoolMapper.addRelation(relation);
                investmentPoolMapper.addRelationEvent(relation.getId(), operatorId, EventType.ADD_CN.getCode());
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
        // 新增自动规则备注
        investmentPoolMapper.addAutoRule(rule);
        investmentPoolMapper.addAutoRuleEvent(rule.getId(), operatorId, EventType.ADD_CN.getCode());
    }

    /**
     * 填充关系配置
     */
    private void fillRelationConfig(InvestmentPoolDto dto) {
        Map<String, List<Long>> relationPoolIds = new HashMap<>();
        for (String relationType : java.util.Arrays.stream(RelationType.values()).map(RelationType::getCode).collect(java.util.stream.Collectors.toList())) {
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
            if (RuleType.AUTO_IN.getCode().equals(rule.getRuleType())) {
                dto.getAutoInRuleIds().add(rule.getRuleId());
                dto.getAutoInRuleDescs().add(rule.getRuleDesc());
            }
            if (RuleType.AUTO_OUT.getCode().equals(rule.getRuleType())) {
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
        dto.setPermissions(permissions);
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
            permission.setHandlerType(parentPermission.getHandlerType());
            permission.setHandlerId(parentPermission.getHandlerId());
            permission.setHandlerName(parentPermission.getHandlerName());
            permission.setIsDeleted(0);
            Date now = new Date();
            permission.setCrteTime(now);
            permission.setUpdtTime(now);
            investmentPoolMapper.addPermission(permission);
            investmentPoolMapper.addPermissionEvent(permission.getId(), operatorId, EventType.ADD_CN.getCode());
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
            permission.setHandlerType(perm.getHandlerType());
            permission.setHandlerId(perm.getHandlerId());
            permission.setHandlerName(perm.getHandlerName());
            permission.setIsDeleted(0);
            Date now = new Date();
            permission.setCrteTime(now);
            permission.setUpdtTime(now);
            investmentPoolMapper.addPermission(permission);
            investmentPoolMapper.addPermissionEvent(permission.getId(), operatorId, EventType.ADD_CN.getCode());
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
        // 解析 JSON 列表
        dto.setMarketCodes(fromJson(pool.getMarketCodes()));
        // 解析 JSON 列表
        dto.setVarietyCodes(fromJson(pool.getVarietyCodes()));
        dto.setHsPoolName(pool.getHsPoolName());
        // 构建流程选项
        dto.setInFlow(buildFlow(pool.getInFlowId(), pool.getInFlowKey(), pool.getInFlowName()));
        // 构建流程选项
        dto.setOutFlow(buildFlow(pool.getOutFlowId(), pool.getOutFlowKey(), pool.getOutFlowName()));
        // 构建流程选项
        dto.setSimpleInFlow(buildFlow(pool.getSimpleInFlowId(), pool.getSimpleInFlowKey(), pool.getSimpleInFlowName()));
        // 构建流程选项
        dto.setSimpleOutFlow(buildFlow(pool.getSimpleOutFlowId(), pool.getSimpleOutFlowKey(), pool.getSimpleOutFlowName()));
        // 构建流程选项
        dto.setBatchInFlow(buildFlow(pool.getBatchInFlowId(), pool.getBatchInFlowKey(), pool.getBatchInFlowName()));
        // 构建流程选项
        dto.setBatchOutFlow(buildFlow(pool.getBatchOutFlowId(), pool.getBatchOutFlowKey(), pool.getBatchOutFlowName()));
        dto.setInReportRestriction(pool.getInReportRestriction());
        dto.setOutReportRestriction(pool.getOutReportRestriction());
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
