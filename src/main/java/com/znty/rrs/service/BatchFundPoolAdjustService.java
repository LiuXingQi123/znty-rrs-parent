package com.znty.rrs.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.FundInvestmentType;
import com.znty.rrs.common.enums.HandlerType;
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.entity.batchfundpooladjust.BatchFundAdjustDto;
import com.znty.rrs.entity.batchfundpooladjust.BatchFundAdjustReq;
import com.znty.rrs.entity.batchfundpooladjust.BatchFundPoolAdjustReq;
import com.znty.rrs.entity.batchfundpooladjust.BatchFundPoolDto;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.fundpooladjust.FundAdjustCheckDto;
import com.znty.rrs.entity.fundpooladjust.FundAdjustCheckReq;
import com.znty.rrs.entity.fundpooladjust.FundAdjustSubmitDto;
import com.znty.rrs.entity.fundpooladjust.FundInfoDto;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.BatchFundPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 处理页面发起的基金池批量调入和调出
 *
 * 先按用户权限查询可调整的目标池与候选基金，再将同批的基金逐只送入调库校验
 * 校验与提交复用 {@link FundPoolAdjustService}，整批共用本次上传的附件
 */
@Service
public class BatchFundPoolAdjustService {
    /** 管理员用户 ID */
    private static final String ADMIN_USER_ID = "1";

    /** 手工调整标签 */
    private static final String MANUAL = "manual";

    /** 基金批量只读查询组件 */
    @Resource
    private BatchFundPoolAdjustMapper batchFundPoolAdjustMapper;

    /** 投资池权限查询组件 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 投资池全路径查询服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /** 基金单笔调整服务 */
    @Resource
    private FundPoolAdjustService fundPoolAdjustService;

    /** 报告和材料附件服务 */
    @Resource
    private SysAttachmentService sysAttachmentService;

    /**
     * 分页查询当前用户可调整的基金叶子池
     *
     * 执行顺序：
     * 1. 先取得用户可调整池集合，避免空权限被当作不筛选
     * 2. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 3. 将本页记录和查询总条数一起返回
     *
     * @param req 批次基金投资池调库
     * @return 批次基金投资池及分页信息
     */
    public PageResult<BatchFundPoolDto> queryPoolPage(BatchFundPoolAdjustReq req) {
        // 先取得用户可调整池集合，避免空权限被当作不筛选
        Set<Long> permitted = queryAdjustablePoolIds(req.getCurrentUserId());
        if (!ADMIN_USER_ID.equals(req.getCurrentUserId())) {
            List<Long> ids = req.getPoolIds() == null || req.getPoolIds().isEmpty()
                    ? new ArrayList<>(permitted) : req.getPoolIds().stream()
                    .filter(permitted::contains).collect(Collectors.toList());
            if (ids.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0L, req.getPageIndex(), req.getPageSize());
            }
            req.setPoolIds(ids);
        }
        Page<BatchFundPoolDto> page = PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BatchFundPoolDto> records = batchFundPoolAdjustMapper.queryPoolPage(req);
        Map<Long, String> fullNames = investmentPoolService.queryPoolFullNameMap();
        for (BatchFundPoolDto pool : records) {
            pool.setPoolFullName(fullNames.get(pool.getId()));
        }
        return new PageResult<>(records, page.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 分页查询可调入或可调出的基金
     *
     * 执行顺序：
     * 1. 先校验目标池和用户权限，再开启候选基金分页
     * 2. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 3. 将本页记录和查询总条数一起返回
     *
     * @param req 批次基金投资池调库
     * @return 分页基金信息列表
     */
    public PageResult<FundInfoDto> queryFundPage(BatchFundPoolAdjustReq req) {
        // 先校验目标池和用户权限，再开启候选基金分页
        validatePoolContext(req.getCurrentUserId(), req.getPoolId(), req.getDirection());
        Page<FundInfoDto> page = PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<FundInfoDto> records = batchFundPoolAdjustMapper.queryFundPage(req);
        return new PageResult<>(records, page.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 按基金调用单笔校验，关系项与主项形成完整提交组
     *
     * 执行顺序：
     * 1. 复核当前手工目标池和用户权限
     * 2. 未选择任何基金时终止处理
     * 3. 逐项构造单只基金的一个手工目标池校验请求
     * 4. 查询全部投资池 ID → 全路径名称映射
     *
     * @param req 批次基金调库
     * @return 基金批量调库结果
     */
    public BatchFundAdjustDto checkAdjust(BatchFundAdjustReq req) {
        // 复核当前手工目标池和用户权限
        Set<Long> permitted = validatePoolContext(req.getCurrentUserId(), req.getPoolId(), req.getDirection());
        if (req.getFunds() == null || req.getFunds().isEmpty()) {
            throw new BizException("至少选择一只基金");
        }
        Set<String> fundCodes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        BatchFundAdjustDto dto = new BatchFundAdjustDto();
        for (BatchFundAdjustReq.FundItem fund : req.getFunds()) {
            // 明确拒绝空代码和重复选择，避免重复生成调库主项
            String code = requireFundCode(fund == null ? null : fund.getFundCode());
            if (!fundCodes.add(code)) {
                throw new BizException("重复选择基金：" + code);
            }
            // 将目标池及方向转换为基金单笔校验请求
            FundAdjustCheckReq checkReq = buildSingleCheckReq(code, req.getPoolId(), req.getDirection());
            List<FundAdjustCheckDto.CheckResultItem> rows;
            try {
                rows = fundPoolAdjustService.checkAdjust(checkReq).getItems();
            } catch (BizException exception) {
                FundAdjustCheckDto.CheckResultItem failure = new FundAdjustCheckDto.CheckResultItem();
                failure.setFundCode(code);
                failure.setTargetPoolId(req.getPoolId());
                failure.setPoolName(investmentPoolService.queryPoolFullNameMap().get(req.getPoolId()));
                // 保留失败主项的方向与分组，供前端展示具体错误
                failure.setAdjustMode(resolveAdjustMode(req.getDirection()));
                failure.setItemTag(MANUAL);
                failure.setAdjustGroupKey("fund-group-1");
                failure.setFailReasons(Collections.singletonList(exception.getMessage()));
                failure.setWarnings(Collections.emptyList());
                failure.setFlowOptions(Collections.emptyList());
                rows = Collections.singletonList(failure);
            }
            boolean completeGroupValid = true;
            for (FundAdjustCheckDto.CheckResultItem row : rows) {
                row.setAdjustGroupKey(row.getFundCode() + "_" + row.getAdjustGroupKey());
                if (!ADMIN_USER_ID.equals(req.getCurrentUserId()) && !permitted.contains(row.getTargetPoolId())) {
                    List<String> failures = new ArrayList<>(row.getFailReasons());
                    failures.add("当前用户没有投资池调整权限：" + row.getPoolName());
                    row.setFailReasons(failures);
                    row.setCanAdjust(false);
                }
                completeGroupValid = completeGroupValid && row.isCanAdjust();
            }
            if (!completeGroupValid) {
                for (FundAdjustCheckDto.CheckResultItem row : rows) {
                    if (row.isCanAdjust()) {
                        row.setFailReasons(Collections.singletonList("同一基金调库分组存在未通过的调整项"));
                    }
                    row.setCanAdjust(false);
                    for (FundAdjustCheckDto.FlowOption option : row.getFlowOptions()) {
                        option.setSelectable(false);
                    }
                }
            }
            dto.getItems().addAll(rows);
        }
        return dto;
    }

    /**
     * 原子提交不含本地文件的基金批量申请
     *
     * 执行顺序：
     * 1. 将每只基金的完整明细转换为单笔申请并原子写入
     *
     * @param req 批次基金调库
     * @return 基金批量调库结果
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public BatchFundAdjustDto addAdjustLog(BatchFundAdjustReq req) {
        // 无本地文件时仍沿用相同整批复核与写入路径
        return submitBatch(req, null);
    }

    /**
     * 原子提交基金批量申请及整批共用附件
     *
     * 执行顺序：
     * 1. 先完成请求边界校验，再创建整批共用附件上下文
     * 2. 上传文件只创建一次，每条日志按单笔附件规则绑定
     *
     * @param req 批次基金调库
     * @param files 本次提交的本地上传附件
     * @param originalFileNameListJson 上传文件原始名称列表的 JSON
     * @return 基金批量调库结果
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public BatchFundAdjustDto addAdjustLog(BatchFundAdjustReq req, List<MultipartFile> files,
                                           String originalFileNameListJson) {
        // 1. 先完成请求边界校验，再创建整批共用附件上下文
        validateSubmitRequest(req);
        List<String> names = sysAttachmentService.parseOriginalFileNameListJson(originalFileNameListJson);
        SysAttachmentService.SubmissionFiles submissionFiles = sysAttachmentService.createSharedSubmissionFiles(
                files, req.getAdjusterId(), names);
        // 2. 上传文件只创建一次，每条日志按单笔附件规则绑定
        return submitBatch(req, submissionFiles);
    }

    /**
     * 将每只基金的完整明细转换为单笔申请并原子写入
     *
     * 执行顺序：
     * 1. 复核整批字段、用户身份和主项目标池
     * 2. 逐项构造基金独立申请，防止多主项、错误分组或主项方向伪造
     * 3. 原子提交基金批量申请，支持整批共用附件及完整关系项复核
     *
     * @param req 批次基金调库
     * @param submissionFiles 已校验的上传文件上下文，可为空
     * @return 基金批量调库结果
     */
    private BatchFundAdjustDto submitBatch(BatchFundAdjustReq req,
                                           SysAttachmentService.SubmissionFiles submissionFiles) {
        // 复核整批字段、用户身份和主项目标池
        validateSubmitRequest(req);
        Map<String, List<BatchFundAdjustReq.AdjustItem>> byFund = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (BatchFundAdjustReq.AdjustItem item : req.getItems()) {
            // 按基金代码聚合全部方向的主项和关系项
            String code = requireFundCode(item == null ? null : item.getFundCode());
            byFund.computeIfAbsent(code, key -> new ArrayList<>()).add(item);
        }
        List<FundPoolAdjustSubmitReq> requests = new ArrayList<>();
        for (Map.Entry<String, List<BatchFundAdjustReq.AdjustItem>> entry : byFund.entrySet()) {
            // 保留同一基金主项及其关系项的全部字段与附件引用
            requests.add(buildSingleSubmitReq(req, entry.getKey(), entry.getValue()));
        }
        List<FundAdjustSubmitDto> results = fundPoolAdjustService.addBatchAdjustLogList(requests, submissionFiles);
        BatchFundAdjustDto dto = new BatchFundAdjustDto();
        dto.setFundCount(requests.size());
        for (FundAdjustSubmitDto result : results) {
            dto.getLogIds().addAll(result.getAdjustLogIds());
            dto.getAdjustBatchNos().addAll(result.getAdjustBatchNos());
        }
        dto.setSubmitCount(dto.getLogIds().size());
        return dto;
    }

    /**
     * 校验整批共用的基金字段及提交身份
     *
     * 执行顺序：
     * 1. 在写入前确认基金评分不会被数据库截断
     * 2. 重新确认目标池仍为可调整的启用基金叶子池
     *
     * @param req 批次基金调库
     */
    private void validateSubmitRequest(BatchFundAdjustReq req) {
        // 1. 在写入前确认基金评分不会被数据库截断
        validateFundScore(req.getFundScore());
        if (!FundInvestmentType.isValid(req.getFundInvestmentType())) {
            throw new BizException("基金投资类型不能为空且必须为合法枚举值");
        }
        if (req.getNeedRiskLeaderApproval() == null
                || (req.getNeedRiskLeaderApproval() != 0 && req.getNeedRiskLeaderApproval() != 1)) {
            throw new BizException("分管领导审批必须为 0 或 1");
        }
        if (req.getAdjusterId() == null || !req.getAdjusterId().equals(req.getCurrentUserId())) {
            throw new BizException("调整人 ID 必须与当前用户一致");
        }
        if (req.getAdjusterName() == null || req.getAdjusterName().trim().isEmpty()) {
            throw new BizException("调整人名称不能为空");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("至少提交一组完整的基金调库明细");
        }
        // 2. 重新确认目标池仍为可调整的启用基金叶子池
        validatePoolContext(req.getCurrentUserId(), req.getPoolId(), req.getDirection());
    }

    /**
     * 基金评分只采集保存，不执行准入范围判断
     *
     * 执行顺序：
     * 1. 检查基金评分不能为空
     * 2. 基金评分最多允许六位整数和四位小数时终止处理
     *
     * @param score 计算后的数值
     */
    private void validateFundScore(BigDecimal score) {
        if (score == null) {
            throw new BizException("基金评分不能为空");
        }
        BigDecimal normalized = score.stripTrailingZeros();
        if (normalized.scale() > 4 || normalized.precision() - normalized.scale() > 6) {
            throw new BizException("基金评分最多允许六位整数和四位小数");
        }
    }

    /**
     * 将批量 API 的方向转换为基金单笔使用的枚举值
     *
     * 执行顺序：
     * 1. 检查调整方向必须为 in 或 out
     *
     * @param direction 请求中的调整方向编码
     * @return 调库校验使用的调整方向
     */
    private String resolveAdjustMode(String direction) {
        if (!"in".equals(direction) && !"out".equals(direction)) {
            throw new BizException("调整方向必须为 in 或 out");
        }
        return "in".equals(direction) ? AdjustMode.IN.getCode() : AdjustMode.OUT.getCode();
    }

    /**
     * 校验目标池上下文并返回当前用户可调整池集合
     *
     * 执行顺序：
     * 1. 校验批量调整方向
     * 2. 查询用户直接或角色授予的调整权限
     *
     * @param userId 本次权限复核的操作人 ID
     * @param poolId 目标投资池 ID
     * @param direction 请求中的调整方向编码
     * @return 投资池上下文集合
     */
    private Set<Long> validatePoolContext(String userId, Long poolId, String direction) {
        // 1. 校验批量调整方向
        resolveAdjustMode(direction);
        if (poolId == null || batchFundPoolAdjustMapper.queryEnabledFundLeafPoolCount(poolId) != 1) {
            throw new BizException("目标投资池必须是启用且支持基金的叶子池");
        }
        // 2. 查询用户直接或角色授予的调整权限
        Set<Long> permitted = queryAdjustablePoolIds(userId);
        if (!ADMIN_USER_ID.equals(userId) && !permitted.contains(poolId)) {
            throw new BizException("当前用户无权调整目标投资池");
        }
        return permitted;
    }

    /**
     * 查询人员及角色对应的可调整投资池，管理员由调用处放行
     *
     * 执行顺序：
     * 1. 查询用户角色ID列表
     * 2. 按类型查询权限列表
     *
     * @param userId 本次权限复核的操作人 ID
     * @return 用户可调整的池 ID 集合，管理员无需查询权限并返回空集合
     */
    private Set<Long> queryAdjustablePoolIds(String userId) {
        if (ADMIN_USER_ID.equals(userId)) {
            return Collections.emptySet();
        }
        Long numericUserId;
        try {
            if (userId == null || userId.trim().isEmpty()) {
                throw new BizException("当前用户 ID 不能为空");
            }
            numericUserId = Long.valueOf(userId.trim());
        } catch (NumberFormatException exception) {
            throw new BizException("当前用户 ID 不合法");
        }
        Set<Long> roleIds = new HashSet<>(investmentPoolMapper.queryUserRoleIdList(numericUserId));
        Set<Long> ids = new HashSet<>();
        for (PoolPermissionBo permission : investmentPoolMapper.queryPermissionListByType(PermissionType.ADJUSTABLE.getCode())) {
            if (permission.getPoolId() == null || permission.getHandlerId() == null) {
                continue;
            }
            if ((HandlerType.USER.getCode().equals(permission.getHandlerType())
                    && permission.getHandlerId().equals(numericUserId))
                    || (HandlerType.ROLE.getCode().equals(permission.getHandlerType())
                    && roleIds.contains(permission.getHandlerId()))) {
                ids.add(permission.getPoolId());
            }
        }
        return ids;
    }

    /**
     * 校验并归一化基金代码
     *
     * 执行顺序：
     * 1. 检查基金代码不能为空
     *
     * @param code 证券代码
     * @return 基金代码
     */
    private String requireFundCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new BizException("基金代码不能为空");
        }
        return code.trim();
    }

    /**
     * 构造单只基金的一个手工目标池校验请求
     *
     * 执行顺序：
     * 1. 基金单笔使用中文枚举方向
     *
     * @param code 证券代码
     * @param poolId 目标投资池 ID
     * @param direction 请求中的调整方向编码
     * @return 基金调库校验
     */
    private FundAdjustCheckReq buildSingleCheckReq(String code, Long poolId, String direction) {
        FundAdjustCheckReq checkReq = new FundAdjustCheckReq();
        checkReq.setFundCode(code);
        FundAdjustCheckReq.CheckItem item = new FundAdjustCheckReq.CheckItem();
        item.setTargetPoolId(poolId);
        // 基金单笔使用中文枚举方向
        item.setAdjustMode(resolveAdjustMode(direction));
        checkReq.setItems(Collections.singletonList(item));
        return checkReq;
    }

    /**
     * 构造基金独立申请，防止多主项、错误分组或主项方向伪造
     *
     * 执行顺序：
     * 1. 逐项将批量 API 的方向转换为基金单笔使用的枚举值
     *
     * @param batch 批次
     * @param code 证券代码
     * @param items 明细列表
     * @return 基金投资池调库提交
     */
    private FundPoolAdjustSubmitReq buildSingleSubmitReq(BatchFundAdjustReq batch, String code,
                                                         List<BatchFundAdjustReq.AdjustItem> items) {
        int manualCount = 0;
        FundPoolAdjustSubmitReq req = new FundPoolAdjustSubmitReq();
        req.setFundCode(code);
        req.setFundScore(batch.getFundScore());
        req.setFundInvestmentType(batch.getFundInvestmentType());
        req.setNeedRiskLeaderApproval(batch.getNeedRiskLeaderApproval());
        req.setAdjustType("手动批量调整");
        req.setAdjustReason(batch.getAdjustReason());
        req.setAdjustAdvice(batch.getAdjustAdvice());
        req.setAdjusterId(batch.getAdjusterId());
        req.setAdjusterName(batch.getAdjusterName());
        List<FundPoolAdjustSubmitReq.AdjustItem> singleItems = new ArrayList<>();
        for (BatchFundAdjustReq.AdjustItem item : items) {
            if (!(code + "_fund-group-1").equals(item.getAdjustGroupKey())) {
                throw new BizException("基金调库分组标识不匹配：" + code);
            }
            if (MANUAL.equals(item.getItemTag())) {
                manualCount++;
                // 确认手工目标和方向仍与本次批量上下文一致
                if (!batch.getPoolId().equals(item.getTargetPoolId())
                        // 将批量 API 的方向转换为基金单笔使用的枚举值
                        || !resolveAdjustMode(batch.getDirection()).equals(item.getAdjustMode())) {
                    throw new BizException("基金手工调整目标池或方向与本次批量申请不一致：" + code);
                }
            }
            FundPoolAdjustSubmitReq.AdjustItem single = new FundPoolAdjustSubmitReq.AdjustItem();
            BeanUtils.copyProperties(item, single);
            singleItems.add(single);
        }
        if (manualCount != 1) {
            throw new BizException("每只基金必须包含且仅包含一条手工调整项：" + code);
        }
        req.setItems(singleItems);
        return req;
    }
}
