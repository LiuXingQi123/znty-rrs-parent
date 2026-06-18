package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.exception.BizException;
import com.znty.sirm.mapper.BatchSecurityPoolAdjustMapper;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.model.BatchSecurityCandidateDto;
import com.znty.sirm.model.BatchSecurityPoolAdjustReq;
import com.znty.sirm.model.BatchSecurityPoolDto;
import com.znty.sirm.model.InvestmentPoolBo;
import com.znty.sirm.model.PoolPermissionBo;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 证券池批量调整业务服务
 */
@Service
public class BatchSecurityPoolAdjustService {

    /** 管理员用户 ID */
    private static final String ADMIN_USER_ID = "1001";

    /** 可调整权限类型 */
    private static final String PERMISSION_TYPE_ADJUSTABLE = "adjustable";

    /** 支持的调整方向 */
    private static final Set<String> DIRECTION_SET = new HashSet<>(Arrays.asList("in", "out"));

    /** 支持的市场编码 */
    private static final Set<String> MARKET_CODE_SET =
            new HashSet<>(Arrays.asList("SH", "SZ", "IB", "BJ", "NBC"));

    /** 证券池批量调整数据访问组件 */
    @Resource
    private BatchSecurityPoolAdjustMapper batchSecurityPoolAdjustMapper;

    /** 投资池数据访问组件 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /**
     * 查询当前用户可调整的启用叶子投资池
     */
    public List<BatchSecurityPoolDto> queryPoolList(BatchSecurityPoolAdjustReq req) {
        List<BatchSecurityPoolDto> poolList = batchSecurityPoolAdjustMapper.queryLeafPoolList();
        fillPoolFullName(poolList);
        if (!isAdminUser(req.getCurrentUserId())) {
            Set<Long> adjustablePoolIds = queryAdjustablePoolIds(req.getCurrentUserId());
            poolList = poolList.stream()
                    .filter(pool -> adjustablePoolIds.contains(pool.getId()))
                    .collect(Collectors.toList());
        }
        return poolList;
    }

    /**
     * 分页查询目标池批量调整候选证券
     */
    public PageResult<BatchSecurityCandidateDto> querySecurityPage(BatchSecurityPoolAdjustReq req) {
        validateSecurityPageReq(req);
        validatePoolPermission(req);

        // 开启分页查询
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BatchSecurityCandidateDto> list = batchSecurityPoolAdjustMapper.querySecurityPage(req);
        PageInfo<BatchSecurityCandidateDto> pageInfo = new PageInfo<>(list);

        // 将 SQL 返回的市场文本转换为市场编码列表
        fillMarketCodes(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 校验候选证券查询参数
     */
    private void validateSecurityPageReq(BatchSecurityPoolAdjustReq req) {
        if (req.getPoolId() == null) {
            throw new BizException("目标投资池 ID 不能为空");
        }
        if (!DIRECTION_SET.contains(req.getDirection())) {
            throw new BizException("调整方向必须为 in 或 out");
        }
        if (batchSecurityPoolAdjustMapper.queryEnabledLeafPoolCount(req.getPoolId()) == 0) {
            throw new BizException("目标投资池不存在、未启用或不是叶子池");
        }
        if (req.getMarketCodes() != null) {
            for (String marketCode : req.getMarketCodes()) {
                if (!MARKET_CODE_SET.contains(marketCode)) {
                    throw new BizException("不支持的市场编码：" + marketCode);
                }
            }
        }
    }

    /**
     * 校验当前用户是否拥有目标池调整权限
     */
    private void validatePoolPermission(BatchSecurityPoolAdjustReq req) {
        if (isAdminUser(req.getCurrentUserId())) {
            return;
        }
        if (!queryAdjustablePoolIds(req.getCurrentUserId()).contains(req.getPoolId())) {
            throw new BizException("当前用户无权调整目标投资池");
        }
    }

    /**
     * 查询当前用户直接或通过角色拥有调整权限的投资池 ID
     */
    private Set<Long> queryAdjustablePoolIds(String currentUserId) {
        Long userId = parseCurrentUserId(currentUserId);
        List<Long> roleIds = investmentPoolMapper.queryUserRoleIdList(userId);
        Set<Long> roleIdSet = roleIds == null ? new HashSet<>() : new HashSet<>(roleIds);
        List<PoolPermissionBo> permissions =
                investmentPoolMapper.queryPermissionListByType(PERMISSION_TYPE_ADJUSTABLE);
        Set<Long> poolIds = new HashSet<>();
        if (permissions == null) {
            return poolIds;
        }
        for (PoolPermissionBo permission : permissions) {
            if (permission.getPoolId() == null || permission.getSubjectId() == null) {
                continue;
            }
            if ("user".equals(permission.getSubjectType()) && permission.getSubjectId().equals(userId)) {
                poolIds.add(permission.getPoolId());
            } else if ("role".equals(permission.getSubjectType())
                    && roleIdSet.contains(permission.getSubjectId())) {
                poolIds.add(permission.getPoolId());
            }
        }
        return poolIds;
    }

    /**
     * 解析当前用户 ID
     */
    private Long parseCurrentUserId(String currentUserId) {
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            throw new BizException("当前用户 ID 不能为空");
        }
        try {
            return Long.valueOf(currentUserId.trim());
        } catch (NumberFormatException e) {
            throw new BizException("当前用户 ID 不合法");
        }
    }

    /**
     * 判断当前用户是否为管理员
     */
    private boolean isAdminUser(String currentUserId) {
        return ADMIN_USER_ID.equals(currentUserId);
    }

    /**
     * 填充市场编码列表
     */
    private void fillMarketCodes(List<BatchSecurityCandidateDto> list) {
        for (BatchSecurityCandidateDto dto : list) {
            if (dto.getMarketCodeText() == null || dto.getMarketCodeText().isEmpty()) {
                dto.setMarketCodes(new ArrayList<>());
            } else {
                dto.setMarketCodes(Arrays.asList(dto.getMarketCodeText().split(",")));
            }
        }
    }

    /**
     * 填充投资池全路径名称
     */
    private void fillPoolFullName(List<BatchSecurityPoolDto> poolList) {
        if (poolList == null || poolList.isEmpty()) {
            return;
        }
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools == null || allPools.isEmpty()) {
            return;
        }
        Map<Long, InvestmentPoolBo> poolMap = allPools.stream()
                .collect(Collectors.toMap(InvestmentPoolBo::getId, Function.identity()));
        for (BatchSecurityPoolDto dto : poolList) {
            dto.setPoolFullName(buildPoolFullName(dto.getId(), poolMap));
        }
    }

    /**
     * 构建投资池全路径名称
     */
    private String buildPoolFullName(Long poolId, Map<Long, InvestmentPoolBo> poolMap) {
        InvestmentPoolBo pool = poolMap.get(poolId);
        if (pool == null) {
            return "";
        }
        String poolName = pool.getPoolName() == null ? "" : pool.getPoolName();
        if (pool.getParentId() == null) {
            return poolName;
        }
        // 构建父级投资池全路径
        String parentName = buildPoolFullName(pool.getParentId(), poolMap);
        return parentName == null || parentName.isEmpty() ? poolName : parentName + "/" + poolName;
    }
}
