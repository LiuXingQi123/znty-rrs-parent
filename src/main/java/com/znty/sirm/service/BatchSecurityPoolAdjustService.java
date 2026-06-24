package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.exception.BizException;
import com.znty.sirm.mapper.BatchSecurityPoolAdjustMapper;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.model.AdjustCheckDto;
import com.znty.sirm.model.AdjustCheckReq;
import com.znty.sirm.model.AdjustSubmitDto;
import com.znty.sirm.model.BatchSecurityCandidateDto;
import com.znty.sirm.model.BatchSecurityInboundAdjustDto;
import com.znty.sirm.model.BatchSecurityInboundAdjustReq;
import com.znty.sirm.model.BatchSecurityPoolAdjustReq;
import com.znty.sirm.model.BatchSecurityPoolDto;
import com.znty.sirm.model.InvestmentPoolBo;
import com.znty.sirm.model.PoolPermissionBo;
import com.znty.sirm.model.SecurityPoolAdjustSubmitReq;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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

    /** 证券池批量调整数据访问组件 */
    @Resource
    private BatchSecurityPoolAdjustMapper batchSecurityPoolAdjustMapper;

    /** 投资池数据访问组件 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 证券池调库服务 */
    @Resource
    private SecurityPoolAdjustService securityPoolAdjustService;

    /** 系统附件业务服务 */
    @Resource
    private SysAttachmentService sysAttachmentService;

    /**
     * 分页查询当前用户可调整的启用叶子投资池
     */
    public PageResult<BatchSecurityPoolDto> queryPoolPage(BatchSecurityPoolAdjustReq req) {
        // 处理当前用户可调整投资池筛选条件
        if (!prepareAdjustablePoolIds(req)) {
            return new PageResult<>(
                    new ArrayList<>(), 0L, req.getPageIndex(), req.getPageSize());
        }

        // 开启分页查询
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BatchSecurityPoolDto> poolList = batchSecurityPoolAdjustMapper.queryPoolPage(req);
        PageInfo<BatchSecurityPoolDto> pageInfo = new PageInfo<>(poolList);
        // 填充当前页投资池现有证券数量
        fillPoolCurrentCount(poolList);
        // 填充投资池全路径名称
        fillPoolFullName(poolList);
        return new PageResult<>(
                poolList, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 分页查询目标池批量调整候选证券
     */
    public PageResult<BatchSecurityCandidateDto> querySecurityPage(BatchSecurityPoolAdjustReq req) {
        // 校验候选证券查询参数
        validateSecurityPageReq(req);
        // 校验目标投资池调整权限
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
     * 批量调库下一步校验
     */
    public BatchSecurityInboundAdjustDto checkAdjust(BatchSecurityInboundAdjustReq req) {
        // 校验批量调库下一步参数
        validateAdjustCheckReq(req);
        // 校验批量调库目标池权限
        validateAdjustPoolPermission(req.getCurrentUserId(), req.getPoolId());

        BatchSecurityInboundAdjustDto dto = new BatchSecurityInboundAdjustDto();
        // 解析批量调库中文方向
        String adjustMode = resolveAdjustMode(req);
        for (BatchSecurityInboundAdjustReq.SecurityItem security : req.getSecurities()) {
            // 构建单证券调库校验请求
            AdjustCheckDto checkDto = securityPoolAdjustService.checkAdjust(buildSingleCheckReq(req, security));
            if (checkDto == null || checkDto.getItems() == null) {
                continue;
            }
            for (AdjustCheckDto.CheckResultItem item : checkDto.getItems()) {
                if (!adjustMode.equals(item.getAdjustMode())) {
                    continue;
                }
                // 构建批量调库校验结果
                dto.getItems().add(buildBatchCheckResult(security, item));
            }
        }
        return dto;
    }

    /**
     * 批量提交调库申请
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchSecurityInboundAdjustDto addAdjustLog(BatchSecurityInboundAdjustReq req) {
        return addAdjustLog(req, Collections.<MultipartFile>emptyList());
    }

    /**
     * 批量提交调库申请及附件。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchSecurityInboundAdjustDto addAdjustLog(
            BatchSecurityInboundAdjustReq req, List<MultipartFile> files) {
        // 校验批量调库提交参数
        validateAdjustSubmitReq(req);
        // 校验批量调库目标池权限
        validateAdjustPoolPermission(req.getCurrentUserId(), req.getPoolId());

        Map<String, List<BatchSecurityInboundAdjustReq.AdjustItem>> itemMap = new HashMap<>();
        for (BatchSecurityInboundAdjustReq.AdjustItem item : req.getItems()) {
            List<BatchSecurityInboundAdjustReq.AdjustItem> list = itemMap.get(item.getSecurityCode());
            if (list == null) {
                list = new ArrayList<>();
                itemMap.put(item.getSecurityCode(), list);
            }
            list.add(item);
        }

        BatchSecurityInboundAdjustDto dto = new BatchSecurityInboundAdjustDto();
        dto.setSecurityCount(itemMap.size());
        dto.setSubmitCount(0);
        SysAttachmentService.SubmissionFiles submissionFiles =
                sysAttachmentService.createSubmissionFiles(files, req.getAdjusterId());
        for (Map.Entry<String, List<BatchSecurityInboundAdjustReq.AdjustItem>> entry : itemMap.entrySet()) {
            // 构建单证券调库提交请求
            AdjustSubmitDto submitDto = securityPoolAdjustService.addAdjustLog(
                    buildSingleSubmitReq(req, entry.getValue()), submissionFiles);
            if (submitDto == null) {
                continue;
            }
            dto.setSubmitCount(dto.getSubmitCount() + (submitDto.getSubmitCount() == null ? 0 : submitDto.getSubmitCount()));
            if (submitDto.getLogIds() != null) {
                dto.getLogIds().addAll(submitDto.getLogIds());
            }
        }
        return dto;
    }

    /**
     * 校验候选证券查询参数
     */
    private void validateSecurityPageReq(BatchSecurityPoolAdjustReq req) {
        if (req.getPoolId() == null) {
            throw new BizException("目标投资池 ID 不能为空");
        }
        // 校验批量调库方向
        validateAdjustDirection(req.getDirection());
        if (batchSecurityPoolAdjustMapper.queryEnabledLeafPoolCount(req.getPoolId()) == 0) {
            throw new BizException("目标投资池不存在、未启用或不是叶子池");
        }
    }

    /**
     * 校验批量调库下一步参数
     */
    private void validateAdjustCheckReq(BatchSecurityInboundAdjustReq req) {
        if (req.getPoolId() == null) {
            throw new BizException("目标投资池 ID 不能为空");
        }
        // 校验批量调库方向
        validateAdjustDirection(req.getDirection());
        if (req.getSecurities() == null || req.getSecurities().isEmpty()) {
            throw new BizException("已选证券不能为空");
        }
        if (batchSecurityPoolAdjustMapper.queryEnabledLeafPoolCount(req.getPoolId()) == 0) {
            throw new BizException("目标投资池不存在、未启用或不是叶子池");
        }
        for (BatchSecurityInboundAdjustReq.SecurityItem security : req.getSecurities()) {
            if (security.getSecurityCode() == null || security.getSecurityCode().isEmpty()) {
                throw new BizException("已选证券代码不能为空");
            }
        }
    }

    /**
     * 校验批量调库提交参数
     */
    private void validateAdjustSubmitReq(BatchSecurityInboundAdjustReq req) {
        if (req.getPoolId() == null) {
            throw new BizException("目标投资池 ID 不能为空");
        }
        // 校验批量调库方向
        validateAdjustDirection(req.getDirection());
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("可提交调库明细不能为空");
        }
        if (req.getAdjusterId() == null || req.getAdjusterId().isEmpty()) {
            throw new BizException("调整人 ID 不能为空");
        }
        if (req.getAdjusterName() == null || req.getAdjusterName().isEmpty()) {
            throw new BizException("调整人名称不能为空");
        }
        if (batchSecurityPoolAdjustMapper.queryEnabledLeafPoolCount(req.getPoolId()) == 0) {
            throw new BizException("目标投资池不存在、未启用或不是叶子池");
        }
        // 解析批量调库中文方向
        String adjustMode = resolveAdjustMode(req);
        for (BatchSecurityInboundAdjustReq.AdjustItem item : req.getItems()) {
            if (item.getSecurityCode() == null || item.getSecurityCode().isEmpty()) {
                throw new BizException("调库明细证券代码不能为空");
            }
            if (!adjustMode.equals(item.getAdjustMode())) {
                throw new BizException("调库明细调整方向必须与本次批量调整方向一致");
            }
            if (item.getTargetPoolId() == null) {
                throw new BizException("调库明细目标投资池 ID 不能为空");
            }
            if (item.getFlowId() == null && (item.getFlowKey() == null || item.getFlowKey().isEmpty())) {
                throw new BizException("调库明细审批流程不能为空");
            }
        }
    }

    /**
     * 校验批量调库方向
     */
    private void validateAdjustDirection(String direction) {
        if (!"in".equals(direction) && !"out".equals(direction)) {
            throw new BizException("调整方向必须为 in 或 out");
        }
    }

    /**
     * 解析批量调库中文方向
     */
    private String resolveAdjustMode(BatchSecurityInboundAdjustReq req) {
        // 校验批量调库方向
        validateAdjustDirection(req.getDirection());
        return "out".equals(req.getDirection()) ? "调出" : "调入";
    }

    /**
     * 校验批量调库目标池权限
     */
    private void validateAdjustPoolPermission(String currentUserId, Long poolId) {
        BatchSecurityPoolAdjustReq permissionReq = new BatchSecurityPoolAdjustReq();
        permissionReq.setCurrentUserId(currentUserId);
        permissionReq.setPoolId(poolId);
        // 校验目标投资池调整权限
        validatePoolPermission(permissionReq);
    }

    /**
     * 构建单证券调库校验请求
     */
    private AdjustCheckReq buildSingleCheckReq(BatchSecurityInboundAdjustReq req,
                                               BatchSecurityInboundAdjustReq.SecurityItem security) {
        AdjustCheckReq.CheckItem item = new AdjustCheckReq.CheckItem();
        item.setTargetPoolId(req.getPoolId());
        item.setTargetPoolName(req.getPoolName());
        item.setPoolType(req.getPoolType());
        // 解析批量调库中文方向
        item.setAdjustMode(resolveAdjustMode(req));

        AdjustCheckReq checkReq = new AdjustCheckReq();
        checkReq.setSecurityCode(security.getSecurityCode());
        checkReq.setSecurityShortName(security.getSecurityShortName());
        checkReq.setSecurityType(security.getSecurityType());
        checkReq.setItems(Collections.singletonList(item));
        return checkReq;
    }

    /**
     * 构建批量调库校验结果
     */
    private BatchSecurityInboundAdjustDto.CheckResultItem buildBatchCheckResult(
            BatchSecurityInboundAdjustReq.SecurityItem security,
            AdjustCheckDto.CheckResultItem item) {
        BatchSecurityInboundAdjustDto.CheckResultItem result = new BatchSecurityInboundAdjustDto.CheckResultItem();
        result.setSecurityCode(security.getSecurityCode());
        result.setSecurityShortName(security.getSecurityShortName());
        result.setSecurityType(security.getSecurityType());
        result.setTargetPoolId(item.getTargetPoolId());
        result.setPoolName(item.getPoolName());
        result.setPoolType(item.getPoolType());
        result.setAdjustMode(item.getAdjustMode());
        result.setItemTag(item.getItemTag());
        result.setAdjustGroupKey(security.getSecurityCode() + "_" + item.getAdjustGroupKey());
        result.setCanAdjust(item.isCanAdjust());
        result.setFailReasons(item.getFailReasons() == null ? new ArrayList<>() : item.getFailReasons());
        result.setFlowOptions(item.getFlowOptions() == null ? new ArrayList<>() : item.getFlowOptions());
        return result;
    }

    /**
     * 构建单证券调库提交请求
     */
    private SecurityPoolAdjustSubmitReq buildSingleSubmitReq(
            BatchSecurityInboundAdjustReq req,
            List<BatchSecurityInboundAdjustReq.AdjustItem> items) {
        BatchSecurityInboundAdjustReq.AdjustItem first = items.get(0);
        SecurityPoolAdjustSubmitReq submitReq = new SecurityPoolAdjustSubmitReq();
        submitReq.setSecurityCode(first.getSecurityCode());
        submitReq.setSecurityShortName(first.getSecurityShortName());
        submitReq.setSecurityType(first.getSecurityType());
        submitReq.setAdjustType("手工调整");
        submitReq.setAdjustReason(req.getAdjustReason());
        submitReq.setAdjustAdvice(req.getAdjustAdvice());
        submitReq.setAdjusterId(req.getAdjusterId());
        submitReq.setAdjusterName(req.getAdjusterName());

        List<SecurityPoolAdjustSubmitReq.AdjustItem> submitItems = new ArrayList<>();
        for (BatchSecurityInboundAdjustReq.AdjustItem item : items) {
            SecurityPoolAdjustSubmitReq.AdjustItem submitItem = new SecurityPoolAdjustSubmitReq.AdjustItem();
            submitItem.setTargetPoolId(item.getTargetPoolId());
            submitItem.setTargetPoolName(item.getTargetPoolName());
            submitItem.setPoolType(item.getPoolType());
            submitItem.setAdjustMode(item.getAdjustMode());
            submitItem.setItemTag(item.getItemTag());
            submitItem.setAdjustGroupKey(item.getAdjustGroupKey());
            submitItem.setFlowId(item.getFlowId());
            submitItem.setFlowKey(item.getFlowKey());
            submitItem.setFlowType(item.getFlowType());
            submitItem.setAdjustmentNote(item.getAdjustmentNote());
            submitItem.setCreditReportFileIndexes(item.getCreditReportFileIndexes());
            submitItem.setMaterialFileIndexes(item.getMaterialFileIndexes());
            submitItems.add(submitItem);
        }
        submitReq.setItems(submitItems);
        return submitReq;
    }

    /**
     * 校验当前用户是否拥有目标池调整权限
     */
    private void validatePoolPermission(BatchSecurityPoolAdjustReq req) {
        // 判断当前用户是否为管理员
        if (isAdminUser(req.getCurrentUserId())) {
            return;
        }
        // 查询当前用户拥有调整权限的投资池
        if (!queryAdjustablePoolIds(req.getCurrentUserId()).contains(req.getPoolId())) {
            throw new BizException("当前用户无权调整目标投资池");
        }
    }

    /**
     * 处理当前用户可调整投资池筛选条件
     *
     * @return 是否存在可查询的投资池
     */
    private boolean prepareAdjustablePoolIds(BatchSecurityPoolAdjustReq req) {
        // 判断当前用户是否为管理员
        if (isAdminUser(req.getCurrentUserId())) {
            return true;
        }
        // 查询当前用户拥有调整权限的投资池
        Set<Long> adjustablePoolIds = queryAdjustablePoolIds(req.getCurrentUserId());
        if (adjustablePoolIds.isEmpty()) {
            return false;
        }
        if (req.getPoolIds() == null || req.getPoolIds().isEmpty()) {
            req.setPoolIds(new ArrayList<>(adjustablePoolIds));
            return true;
        }
        List<Long> permittedPoolIds = req.getPoolIds().stream()
                .filter(adjustablePoolIds::contains)
                .collect(Collectors.toList());
        if (permittedPoolIds.isEmpty()) {
            return false;
        }
        req.setPoolIds(permittedPoolIds);
        return true;
    }

    /**
     * 查询当前用户直接或通过角色拥有调整权限的投资池 ID
     */
    private Set<Long> queryAdjustablePoolIds(String currentUserId) {
        // 解析当前用户 ID
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
     * 填充当前页投资池现有证券数量
     */
    private void fillPoolCurrentCount(List<BatchSecurityPoolDto> poolList) {
        if (poolList == null || poolList.isEmpty()) {
            return;
        }
        List<Long> poolIds = poolList.stream()
                .map(BatchSecurityPoolDto::getId)
                .collect(Collectors.toList());
        List<BatchSecurityPoolDto> countList =
                batchSecurityPoolAdjustMapper.queryPoolCurrentCountList(poolIds);
        Map<Long, Integer> currentCountMap = countList.stream()
                .collect(Collectors.toMap(
                        BatchSecurityPoolDto::getId, BatchSecurityPoolDto::getCurrentCount));
        for (BatchSecurityPoolDto pool : poolList) {
            pool.setCurrentCount(currentCountMap.getOrDefault(pool.getId(), 0));
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
            // 构建投资池全路径名称
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
