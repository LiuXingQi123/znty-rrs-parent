package com.znty.rrs.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.ApprovalStrategy;
import com.znty.rrs.common.enums.AttachmentCategory;
import com.znty.rrs.common.enums.AttachmentPurpose;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.FlowStatus;
import com.znty.rrs.common.enums.FundInvestmentType;
import com.znty.rrs.common.enums.HandlerType;
import com.znty.rrs.common.enums.NodeType;
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.common.enums.ProcessAction;
import com.znty.rrs.common.enums.RelationType;
import com.znty.rrs.common.enums.StepStatus;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.FundAdjustLogBo;
import com.znty.rrs.entity.bo.FundAdjustStepBo;
import com.znty.rrs.entity.bo.FundInfoBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.RoleBo;
import com.znty.rrs.entity.bo.UserBo;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.entity.fundpooladjust.FundAdjustCheckDto;
import com.znty.rrs.entity.fundpooladjust.FundAdjustCheckReq;
import com.znty.rrs.entity.fundpooladjust.FundAdjustSubmitDto;
import com.znty.rrs.entity.fundpooladjust.FundInfoDto;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustReq;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustSubmitReq;
import com.znty.rrs.entity.fundpooladjust.FundPoolDto;
import com.znty.rrs.entity.fundpooladjust.FundPoolStatusDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.FundPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.TempFundCodeMapper;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 基金池调整申请服务，独立承载基金调库规则与运行表写入 */
@Service
public class FundPoolAdjustService {
    /** 管理员用户 ID */
    private static final String ADMIN_USER_ID = "1";
    /** 基金调库附件关联表 */
    private static final String FUND_ADJUST_LOG_TABLE = "ip_adjust_log_fund";
    /** 基金品种编码 */
    private static final String FUND_VARIETY_TOKEN = "\"fund\"";
    /** 启用状态 */
    private static final String ENABLED = "enabled";
    /** 手工调整项 */
    private static final String ITEM_MANUAL = "manual";
    /** 联动调整项 */
    private static final String ITEM_LINKAGE = "linkage";
    /** 互斥调整项 */
    private static final String ITEM_MUTEX = "mutex";

    /** 基金池调整数据访问组件 */
    @Resource
    private FundPoolAdjustMapper fundPoolAdjustMapper;
    /** 通用投资池数据访问组件 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;
    /** 通用流程定义数据访问组件 */
    @Resource
    private FlowMapper flowMapper;
    /** 投资池公共服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;
    /** 附件公共服务 */
    @Resource
    private SysAttachmentService sysAttachmentService;
    /** 基金临时代码登记数据访问组件，用于 O32 人工处理判断 */
    @Resource
    private TempFundCodeMapper tempFundCodeMapper;

    /**
     * 分页查询有效基金。
     *
     * @param req 基金代码、名称、类型及分页条件
     * @return 分页基金信息列表
     */
    public PageResult<FundInfoDto> queryFundPage(FundPoolAdjustReq req) {
        // 在查询基金列表前设置分页条件
        Page<FundInfoDto> page = PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<FundInfoDto> records = fundPoolAdjustMapper.queryFundPage(req);
        return new PageResult<>(records, page.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 查询有效基金产品类型。
     *
     * @return 基金类型选项列表
     */
    public List<SecurityTypeOptionDto> queryFundTypeList() {
        return fundPoolAdjustMapper.queryFundTypeList();
    }

    /**
     * 查询基金只读基础信息。
     *
     * @param req 需携带基金代码的查询条件
     * @return 基金只读基础信息
     */
    public FundInfoDto queryFundDetail(FundPoolAdjustReq req) {
        // 校验基金代码并读取基金基础信息
        FundInfoBo fund = requireFund(req.getFundCode(), false);
        FundInfoDto dto = new FundInfoDto();
        BeanUtils.copyProperties(fund, dto);
        return dto;
    }

    /**
     * 查询当前用户可调整的基金池。
     *
     * @param req 需携带基金代码和当前用户 ID 的查询条件
     * @return 当前用户可调整的基金池列表
     */
    public List<FundPoolDto> queryAdjustPoolList(FundPoolAdjustReq req) {
        // 校验基金可调整状态并读取基金信息
        FundInfoBo fund = requireFund(req.getFundCode(), true);
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (!ADMIN_USER_ID.equals(req.getCurrentUserId())) {
            // 解析用户 ID 并按调整权限过滤可见投资池
            allPools = filterPoolsByPermission(allPools, parseUserId(req.getCurrentUserId()));
        }
        // 保留支持该基金市场的投资池及其祖先节点
        allPools = retainFundPoolsAndAncestors(allPools, fund.getMarketCode());
        Map<Long, Integer> countMap = fundPoolAdjustMapper.queryPoolCurrentCountList().stream()
                .collect(Collectors.toMap(FundPoolDto::getId, FundPoolDto::getCurrentCount));
        Map<Long, List<Long>> inMutexMap = new HashMap<>();
        Map<Long, List<Long>> outMutexMap = new HashMap<>();
        // 按调入和调出方向收集投资池互斥关系
        for (PoolRelationBo relation : fundPoolAdjustMapper.queryAllPoolRelationList()) {
            if (RelationType.IN_MUTEX.getCode().equals(relation.getRelationType())) {
                inMutexMap.computeIfAbsent(relation.getPoolId(), key -> new ArrayList<>())
                        .add(relation.getRelationPoolId());
            } else if (RelationType.OUT_MUTEX.getCode().equals(relation.getRelationType())) {
                outMutexMap.computeIfAbsent(relation.getPoolId(), key -> new ArrayList<>())
                        .add(relation.getRelationPoolId());
            }
        }
        List<FundPoolDto> result = new ArrayList<>();
        // 组装前端展示所需的池信息、当前数量及互斥池 ID
        for (InvestmentPoolBo pool : allPools) {
            FundPoolDto dto = new FundPoolDto();
            dto.setId(pool.getId());
            dto.setParentId(pool.getParentId());
            dto.setPoolName(pool.getPoolName());
            dto.setPoolCode(pool.getPoolCode());
            dto.setPoolType(pool.getPoolType());
            dto.setPoolLevel(pool.getPoolLevel());
            dto.setMaxCapacity(pool.getMaxCapacity());
            dto.setCurrentCount(countMap.getOrDefault(pool.getId(), 0));
            dto.setInMutexPoolIds(inMutexMap.getOrDefault(pool.getId(), Collections.emptyList()));
            dto.setOutMutexPoolIds(outMutexMap.getOrDefault(pool.getId(), Collections.emptyList()));
            result.add(dto);
        }
        return result;
    }

    /**
     * 查询基金当前所在池，不查询发行主体或债券池。
     *
     * @param req 需携带基金代码的查询条件
     * @return 基金当前池状态列表
     */
    public List<FundPoolStatusDto> queryFundPoolStatus(FundPoolAdjustReq req) {
        String fundCode = req.getFundCode();
        // 校验基金代码及基金记录是否存在
        requireFund(fundCode, false);
        List<FundPoolStatusDto> statuses = fundPoolAdjustMapper.queryFundPoolStatusList(fundCode.trim());
        Map<Long, String> fullNameMap = investmentPoolService.queryPoolFullNameMap();
        // 用投资池全路径名称补全基金当前池展示信息
        for (FundPoolStatusDto status : statuses) {
            String fullName = fullNameMap.get(status.getTargetPoolId());
            if (fullName != null && !fullName.isEmpty()) {
                status.setPoolName(fullName);
            }
        }
        return statuses;
    }

    /**
     * 查询基金调库记录，详情页按批次展示完整上下文。
     *
     * @param req 需携带基金代码的查询条件
     * @return 基金调库记录列表
     */
    public List<FundAdjustLogBo> queryAdjustLogList(FundPoolAdjustReq req) {
        if (req.getFundCode() == null || req.getFundCode().trim().isEmpty()) {
            throw new BizException("基金代码不能为空");
        }
        return fundPoolAdjustMapper.queryAdjustLogList(req);
    }

    /**
     * 查询基金调库审批步骤，批次号优先于单条记录 ID。
     *
     * @param req 需携带批次号或调库记录 ID 的查询条件
     * @return 基金调库审批步骤列表
     */
    public List<FundAdjustStepBo> queryAdjustStepList(FundPoolAdjustReq req) {
        if (req.getAdjustLogId() == null
                && (req.getAdjustBatchNo() == null || req.getAdjustBatchNo().trim().isEmpty())) {
            throw new BizException("调库记录 ID 或批次号不能为空");
        }
        return fundPoolAdjustMapper.queryAdjustStepList(req.getAdjustLogId(), req.getAdjustBatchNo());
    }

    /**
     * 基金调库最终审批落池前，按当前基金状态重新校验批次中的全部调整项。
     *
     * @param logs 同一基金调库批次的日志列表
     */
    public void recheckBeforeFinalApproval(List<FundAdjustLogBo> logs) {
        if (logs == null || logs.isEmpty()) {
            throw new BizException("基金调库批次记录不存在");
        }
        FundAdjustLogBo firstLog = logs.get(0);
        // 锁定待落池基金主档，与临时代码变更串行处理
        Map<String, FundInfoBo> lockedFunds = lockFundList(Collections.singletonList(firstLog.getFundCode()));
        // 使用已锁定的当前主档复核基金状态
        FundInfoBo fund = requireLockedFund(firstLog.getFundCode(), lockedFunds);
        Map<Long, InvestmentPoolBo> poolMap = investmentPoolMapper.queryPoolList().stream()
                .filter(pool -> pool.getId() != null)
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool));
        Set<Long> currentPoolIds = new HashSet<>(fundPoolAdjustMapper.queryFundCurrentPoolIdList(fund.getFundCode()));
        List<PoolRelationBo> relations = fundPoolAdjustMapper.queryAllPoolRelationList();
        for (FundAdjustLogBo log : logs) {
            if (log == null || !fund.getFundCode().equals(log.getFundCode())) {
                throw new BizException("基金调库批次包含无效记录");
            }
            // 确认本条调库记录的目标投资池仍然存在
            InvestmentPoolBo pool = requirePool(poolMap, log.getTargetPoolId());
            // 按当前池状态复核本条调整规则，并排除正在审批的批次
            List<String> failures = validatePoolAdjust(fund, pool, log.getAdjustMode(), currentPoolIds,
                    relations, log.getAdjustBatchNo(), false);
            if (!failures.isEmpty()) {
                throw new BizException(pool.getPoolName() + "：" + String.join("；", failures));
            }
        }
    }

    /**
     * 将基金调库审批通过的整批日志应用到当前基金池状态。
     *
     * @param logs 已通过审批的同批次调库日志
     */
    public void applyPoolStatusChanges(List<FundAdjustLogBo> logs) {
        for (FundAdjustLogBo log : logs) {
            if (AdjustMode.IN.getCode().equals(log.getAdjustMode())) {
                // 调入时新增基金当前池状态
                if (fundPoolAdjustMapper.addFundPoolStatus(log) == 0) {
                    throw new BizException("基金入池状态写入失败");
                }
            } else if (AdjustMode.OUT.getCode().equals(log.getAdjustMode())) {
                // 调出时移除基金当前池状态
                if (fundPoolAdjustMapper.deleteFundPoolStatus(log.getFundCode(), log.getTargetPoolId()) == 0) {
                    throw new BizException("基金当前池状态已发生变化，请刷新后重试");
                }
            } else {
                throw new BizException("基金调库方向不合法");
            }
        }
    }

    /**
     * 执行基金调库校验并展开联动、互斥项。
     *
     * @param req 基金代码及手工选择的目标池调整项
     * @return 基金调库校验结果及联动、互斥项
     */
    public FundAdjustCheckDto checkAdjust(FundAdjustCheckReq req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("至少选择一个目标投资池");
        }
        // 校验基金可调整状态并读取基金信息
        FundInfoBo fund = requireFund(req.getFundCode(), true);
        List<InvestmentPoolBo> pools = investmentPoolMapper.queryPoolList();
        Map<Long, InvestmentPoolBo> poolMap = pools.stream()
                .filter(pool -> pool.getId() != null)
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool));
        Set<Long> currentPoolIds = new HashSet<>(fundPoolAdjustMapper.queryFundCurrentPoolIdList(fund.getFundCode()));
        List<PoolRelationBo> relations = fundPoolAdjustMapper.queryAllPoolRelationList();
        LinkedHashMap<String, FundAdjustCheckDto.CheckResultItem> resultMap = new LinkedHashMap<>();
        int groupIndex = 1;
        for (FundAdjustCheckReq.CheckItem item : req.getItems()) {
            String groupKey = "fund-group-" + groupIndex++;
            // 校验并记录手工选择的目标池调整项
            addCheckResult(resultMap, fund, item.getTargetPoolId(), item.getAdjustMode(), ITEM_MANUAL,
                    groupKey, poolMap, currentPoolIds, relations);
            // 根据目标池关系补充联动和互斥调整项
            expandRelationItems(resultMap, fund, item, groupKey, poolMap, currentPoolIds, relations);
        }
        FundAdjustCheckDto dto = new FundAdjustCheckDto();
        dto.setItems(new ArrayList<>(resultMap.values()));
        return dto;
    }

    /**
     * 校验 Excel 导入调整项，补充调整权限和无报告附件入口的限制。
     *
     * @param req 基金代码及本次导入的目标池调整项
     * @param adjusterId 当前导入用户 ID
     * @return 基金完整校验结果及一般审批流程候选
     */
    public FundAdjustCheckDto checkExcelImportAdjust(FundAdjustCheckReq req, String adjusterId) {
        FundAdjustCheckDto dto = checkAdjust(req);
        Map<Long, InvestmentPoolBo> poolMap = investmentPoolMapper.queryPoolList().stream()
                .filter(pool -> pool.getId() != null)
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool));
        // 查询当前导入用户的调整权限，管理员沿用单笔申请的权限口径
        Set<Long> adjustablePoolIds = ADMIN_USER_ID.equals(adjusterId)
                ? poolMap.keySet() : queryAdjustablePoolIds(parseUserId(adjusterId));
        for (FundAdjustCheckDto.CheckResultItem result : dto.getItems()) {
            List<String> failures = new ArrayList<>(result.getFailReasons());
            InvestmentPoolBo pool = poolMap.get(result.getTargetPoolId());
            if (pool != null && !adjustablePoolIds.contains(pool.getId())) {
                failures.add("当前用户没有投资池调整权限：" + pool.getPoolName());
            }
            if (pool != null && ITEM_MANUAL.equals(result.getItemTag())) {
                FundPoolAdjustSubmitReq.AdjustItem reportItem = new FundPoolAdjustSubmitReq.AdjustItem();
                reportItem.setItemTag(ITEM_MANUAL);
                reportItem.setAdjustMode(result.getAdjustMode());
                try {
                    // 用无附件的主项复核报告要求，关系项维持单笔申请的报告规则
                    validateReportRestriction(reportItem, pool);
                } catch (BizException exception) {
                    failures.add(exception.getMessage() + "；Excel 导入暂不支持报告附件，请通过基金池单笔调库提交");
                }
            }
            result.setFailReasons(failures);
            result.setCanAdjust(failures.isEmpty());
            for (FundAdjustCheckDto.FlowOption option : result.getFlowOptions()) {
                option.setSelectable(option.isSelectable() && failures.isEmpty());
            }
        }
        return dto;
    }

    /**
     * 原子提交 Excel 导入的来源分组，全部完成复核后才写入基金审批申请。
     *
     * @param requests 已由导入模块按持久化快照构建的来源分组请求
     * @return 按来源分组返回的基金调库提交结果
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public List<FundAdjustSubmitDto> addExcelImportAdjustLogList(List<FundPoolAdjustSubmitReq> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BizException("至少提交一个基金 Excel 导入分组");
        }
        // 在任何申请写入之前识别跨来源分组的同基金同目标池冲突
        validateExcelImportRequests(requests);
        // 沿用多单统一锁定、全部复核后再写入的原子提交路径
        return submitAdjustLogList(requests, null, false);
    }

    /**
     * 原子提交基金批量申请，支持整批共用附件及完整关系项复核。
     *
     * @param requests 每只基金独立的单笔申请
     * @param submissionFiles 整批共用的上传文件上下文
     * @return 每只基金的独立批次和日志 ID
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public List<FundAdjustSubmitDto> addBatchAdjustLogList(List<FundPoolAdjustSubmitReq> requests,
                                                         SysAttachmentService.SubmissionFiles submissionFiles) {
        if (requests == null || requests.isEmpty()) {
            throw new BizException("至少提交一组基金批量调整申请");
        }
        Set<String> fundCodes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (FundPoolAdjustSubmitReq req : requests) {
            if (req == null || req.getFundCode() == null || req.getFundCode().trim().isEmpty()) {
                throw new BizException("基金批量调整的基金代码不能为空");
            }
            if (!fundCodes.add(req.getFundCode().trim())) {
                throw new BizException("基金批量调整存在重复基金：" + req.getFundCode());
            }
            // 在取得主档锁前校验单笔必填字段
            validateSubmitRequest(req);
            if (submissionFiles == null) {
                for (FundPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
                    if (item != null && ((item.getReportFileIndexes() != null && !item.getReportFileIndexes().isEmpty())
                            || (item.getMaterialFileIndexes() != null && !item.getMaterialFileIndexes().isEmpty()))) {
                        throw new BizException("基金批量申请包含本地文件索引，请通过 multipart 同时上传对应文件");
                    }
                }
            }
        }
        // 批量页面必须提交每只基金完整的主项和关系项
        return submitAdjustLogList(requests, submissionFiles, true);
    }

    /** 统一锁定基金并在全部复核通过后按单笔规则写入多单申请。 */
    private List<FundAdjustSubmitDto> submitAdjustLogList(List<FundPoolAdjustSubmitReq> requests,
                                                         SysAttachmentService.SubmissionFiles submissionFiles,
                                                         boolean requireCompleteRelations) {
        // 一次按主档主键顺序锁定整批基金，再读取最新业务状态
        Map<String, FundInfoBo> lockedFunds = lockFundList(requests.stream()
                .map(FundPoolAdjustSubmitReq::getFundCode).collect(Collectors.toList()));
        List<PreparedSubmit> preparedRequests = new ArrayList<>();
        for (FundPoolAdjustSubmitReq req : requests) {
            if (requireCompleteRelations) {
                // 在主档锁内重新展开关系，拒绝遗漏、增补或伪造的关系明细
                validateBatchRelationItems(req, requireLockedFund(req.getFundCode(), lockedFunds));
            }
            // 先复核全部分组，防止本批新写入待办影响后续分组的校验
            preparedRequests.add(prepareSubmit(req, lockedFunds));
        }
        List<FundAdjustSubmitDto> results = new ArrayList<>();
        for (PreparedSubmit prepared : preparedRequests) {
            // 复用单笔基金申请的日志、流程快照及初始步骤写入
            results.add(submitPrepared(prepared, submissionFiles));
        }
        return results;
    }

    /** 批量页面按当前关系重新展开主项，核对分组、池、方向及来源标签。 */
    private void validateBatchRelationItems(FundPoolAdjustSubmitReq req, FundInfoBo fund) {
        Map<Long, InvestmentPoolBo> poolMap = investmentPoolMapper.queryPoolList().stream()
                .filter(pool -> pool.getId() != null)
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool));
        Set<Long> currentPoolIds = new HashSet<>(fundPoolAdjustMapper.queryFundCurrentPoolIdList(fund.getFundCode()));
        List<PoolRelationBo> relations = fundPoolAdjustMapper.queryAllPoolRelationList();
        LinkedHashMap<String, FundAdjustCheckDto.CheckResultItem> expected = new LinkedHashMap<>();
        int manualCount = 0;
        Set<String> actualKeys = new HashSet<>();
        for (FundPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (item == null || item.getTargetPoolId() == null) {
                throw new BizException("基金调库明细的目标池不能为空：" + fund.getFundCode());
            }
            // 取得完整分组标识，避免关系项跨组或缺少来源标签
            String groupKey = requireGroupKey(item);
            String actualKey = groupKey + "|" + item.getTargetPoolId() + "|"
                    + item.getAdjustMode() + "|" + item.getItemTag();
            if (!actualKeys.add(actualKey)) {
                throw new BizException("基金批量调库存在重复明细：" + fund.getFundCode());
            }
            if (ITEM_MANUAL.equals(item.getItemTag())) {
                manualCount++;
                // 按最新目标池和基金所在池重新生成主项
                addCheckResult(expected, fund, item.getTargetPoolId(), item.getAdjustMode(), ITEM_MANUAL,
                        groupKey, poolMap, currentPoolIds, relations);
                FundAdjustCheckReq.CheckItem manual = new FundAdjustCheckReq.CheckItem();
                manual.setTargetPoolId(item.getTargetPoolId());
                manual.setAdjustMode(item.getAdjustMode());
                // 关系项包含相反方向互斥调整，不按主项方向截断
                expandRelationItems(expected, fund, manual, groupKey, poolMap, currentPoolIds, relations);
            }
        }
        if (manualCount != 1) {
            throw new BizException("基金批量每组必须包含一条手工调整项：" + fund.getFundCode());
        }
        Set<String> expectedKeys = new HashSet<>();
        for (FundAdjustCheckDto.CheckResultItem item : expected.values()) {
            expectedKeys.add(item.getAdjustGroupKey() + "|" + item.getTargetPoolId() + "|"
                    + item.getAdjustMode() + "|" + item.getItemTag());
        }
        if (!actualKeys.equals(expectedKeys)) {
            throw new BizException("基金调库主项、联动或互斥明细与最新关系不一致，请重新校验：" + fund.getFundCode());
        }
    }

    /**
     * 提交不含本地文件的基金调库申请。
     *
     * @param req 基金调库申请及调整明细
     * @return 基金调库申请提交结果
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public FundAdjustSubmitDto addAdjustLog(FundPoolAdjustSubmitReq req) {
        // 按无本地上传文件的方式保存基金调库申请
        return addAdjustLogInternal(req, null);
    }

    /**
     * 提交含 multipart 文件的基金调库申请。
     *
     * @param req 基金调库申请及调整明细
     * @param files 本次上传的附件文件
     * @param originalFileNameListJson 前端提供的原始文件名列表
     * @return 基金调库申请提交结果
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public FundAdjustSubmitDto addAdjustLog(FundPoolAdjustSubmitReq req, List<MultipartFile> files,
                                             String originalFileNameListJson) {
        List<String> names = sysAttachmentService.parseOriginalFileNameListJson(originalFileNameListJson);
        SysAttachmentService.SubmissionFiles submissionFiles = sysAttachmentService.createSubmissionFiles(
                files, req.getAdjusterId(), names);
        // 将上传文件随基金调库申请一并提交
        return addAdjustLogInternal(req, submissionFiles);
    }

    /**
     * 提交基金调库日志与初始步骤。
     *
     * @param req 基金调库申请及调整明细
     * @param submissionFiles 已校验的上传文件上下文，可为空
     * @return 基金调库申请提交结果
     */
    private FundAdjustSubmitDto addAdjustLogInternal(FundPoolAdjustSubmitReq req,
                                                      SysAttachmentService.SubmissionFiles submissionFiles) {
        // 先校验提交级必填字段，避免无效请求获取主档锁
        validateSubmitRequest(req);
        // 与临时代码变更共用主档锁，在任何基金日志或步骤写入前取得
        Map<String, FundInfoBo> lockedFunds = lockFundList(Collections.singletonList(req.getFundCode()));
        // 完成全部基金、权限、目标池、报告及流程复核并固定写入上下文
        PreparedSubmit prepared = prepareSubmit(req, lockedFunds);
        // 将已通过复核的单笔申请写入基金专属运行表
        return submitPrepared(prepared, submissionFiles);
    }

    /**
     * 复核基金申请并固定主档、投资池与一般流程快照，不写运行表。
     *
     * @param req 待提交基金申请
     * @param lockedFunds 按主档主键顺序取得锁后的当前基金信息
     * @return 全部复核通过的提交上下文
     */
    private PreparedSubmit prepareSubmit(FundPoolAdjustSubmitReq req, Map<String, FundInfoBo> lockedFunds) {
        // 校验调库申请的提交级必填字段
        validateSubmitRequest(req);
        // 确认申请中的基金仍处于可调库状态
        FundInfoBo fund = requireLockedFund(req.getFundCode(), lockedFunds);
        Map<Long, InvestmentPoolBo> poolMap = investmentPoolMapper.queryPoolList().stream()
                .filter(pool -> pool.getId() != null)
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool));
        Set<Long> currentPoolIds = new HashSet<>(fundPoolAdjustMapper.queryFundCurrentPoolIdList(fund.getFundCode()));
        List<PoolRelationBo> relations = fundPoolAdjustMapper.queryAllPoolRelationList();
        // 根据最新基金、池和关系数据校验全部提交明细
        validateSubmitItems(req, fund, poolMap, currentPoolIds, relations);

        Map<String, FundPoolAdjustSubmitReq.AdjustItem> manualByGroup = new HashMap<>();
        for (FundPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (ITEM_MANUAL.equals(item.getItemTag())) {
                // 校验手工调整项的分组标识并建立分组索引
                manualByGroup.put(requireGroupKey(item), item);
            }
        }
        Map<String, FlowSnapshot> snapshotByGroup = new HashMap<>();
        for (FundPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            // 确认每条关系项均关联本请求中的手工主项
            String groupKey = requireGroupKey(item);
            FundPoolAdjustSubmitReq.AdjustItem manual = manualByGroup.get(groupKey);
            if (manual == null) {
                throw new BizException("每个调库分组必须包含一条手工调整项");
            }
            if (!snapshotByGroup.containsKey(groupKey)) {
                // 在写入前固定来源组使用的已发布一般流程快照
                FlowSnapshot snapshot = buildFlowSnapshot(manual.getFlowId(), fund.getFundCode());
                if (snapshot == null) {
                    // 读取主项目标池以定位流程不可用的业务错误
                    InvestmentPoolBo pool = requirePool(poolMap, manual.getTargetPoolId());
                    throw new BizException("目标投资池的一般审批流程未发布或不可用：" + pool.getPoolName());
                }
                // 确认流程具备起始节点，避免后续写入阶段才发现配置残缺
                if (findNode(snapshot.nodes, NodeType.START.getCode()) == null) {
                    throw new BizException("一般审批流程缺少开始节点");
                }
                snapshotByGroup.put(groupKey, snapshot);
            }
        }
        return new PreparedSubmit(req, fund, poolMap, manualByGroup, snapshotByGroup);
    }

    /**
     * 使用已复核上下文保存基金日志、审批快照、初始步骤和附件。
     *
     * @param prepared 已完成全部复核的基金申请
     * @param submissionFiles 已校验的上传文件上下文，可为空
     * @return 基金调库提交结果
     */
    private FundAdjustSubmitDto submitPrepared(PreparedSubmit prepared,
                                               SysAttachmentService.SubmissionFiles submissionFiles) {
        FundPoolAdjustSubmitReq req = prepared.req;
        Map<String, String> batchByGroup = new HashMap<>();
        List<Long> logIds = new ArrayList<>();
        LinkedHashSet<String> batchNos = new LinkedHashSet<>();
        for (FundPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            // 校验当前明细的分组标识以关联手工调整项
            String groupKey = requireGroupKey(item);
            FundPoolAdjustSubmitReq.AdjustItem manual = prepared.manualByGroup.get(groupKey);
            // 为同一分组复用批次号，首次出现时生成新批次号
            String batchNo = batchByGroup.computeIfAbsent(groupKey, key -> generateBatchNo());
            batchNos.add(batchNo);
            InvestmentPoolBo pool = prepared.poolMap.get(item.getTargetPoolId());
            FlowSnapshot snapshot = prepared.snapshotByGroup.get(groupKey);
            // 依据基金和流程快照生成当前调整项日志
            FundAdjustLogBo log = buildAdjustLog(req, prepared.fund, item, manual, pool,
                    prepared.poolMap, batchNo, snapshot);
            fundPoolAdjustMapper.addAdjustLog(log);
            logIds.add(log.getId());
            // 将上传及来源附件绑定到当前调库日志
            bindAttachments(log.getId(), item, submissionFiles, req.getAdjusterId());
            if (ITEM_MANUAL.equals(item.getItemTag())) {
                // 为手工调整项创建流程起始和待审批步骤
                createInitialSteps(log.getId(), batchNo, snapshot, req.getAdjusterId(), req.getAdjusterName());
            }
        }
        FundAdjustSubmitDto dto = new FundAdjustSubmitDto();
        dto.setAdjustLogIds(logIds);
        dto.setAdjustBatchNos(new ArrayList<>(batchNos));
        return dto;
    }

    /**
     * 校验 Excel 内部整批入口的请求边界与跨来源分组冲突。
     *
     * @param requests 按 Excel 来源分组构造的提交请求
     */
    private void validateExcelImportRequests(List<FundPoolAdjustSubmitReq> requests) {
        Map<String, String> sourceByFundPool = new HashMap<>();
        for (int index = 0; index < requests.size(); index++) {
            FundPoolAdjustSubmitReq req = requests.get(index);
            if (req == null) {
                throw new BizException("基金 Excel 导入第 " + (index + 1) + " 个来源分组为空");
            }
            // 校验每个来源分组的必填字段，确保后续冲突检查可明确定位
            validateSubmitRequest(req);
            if (req.getFundCode() == null || req.getFundCode().trim().isEmpty()) {
                throw new BizException("基金代码不能为空");
            }
            Set<String> requestPoolKeys = new HashSet<>();
            for (FundPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
                if (item == null || item.getTargetPoolId() == null) {
                    throw new BizException("基金 Excel 导入第 " + (index + 1) + " 个来源分组的目标池不能为空");
                }
                if ((item.getReportFileIndexes() != null && !item.getReportFileIndexes().isEmpty())
                        || (item.getMaterialFileIndexes() != null && !item.getMaterialFileIndexes().isEmpty())
                        || (item.getReportSourceAttachmentIds() != null && !item.getReportSourceAttachmentIds().isEmpty())
                        || (item.getMaterialSourceAttachmentIds() != null && !item.getMaterialSourceAttachmentIds().isEmpty())) {
                    throw new BizException("Excel 导入暂不支持报告或材料附件，请通过基金池单笔调库提交");
                }
                // 读取来源组标识，为批内冲突提供可追踪定位
                String groupKey = requireGroupKey(item);
                String key = req.getFundCode().trim() + "|" + item.getTargetPoolId();
                if (requestPoolKeys.add(key)) {
                    String source = "第 " + (index + 1) + " 个来源分组（" + groupKey + "）";
                    String previousSource = sourceByFundPool.putIfAbsent(key, source);
                    if (previousSource != null) {
                        throw new BizException("基金 Excel 导入来源分组冲突：基金 " + req.getFundCode().trim()
                                + "，目标池 ID " + item.getTargetPoolId() + "，" + previousSource + "与" + source
                                + "重复调整同一目标池（含相反方向）");
                    }
                }
            }
        }
    }

    /**
     * 校验提交级字段。
     *
     * @param req 待提交的基金调库申请
     */
    private void validateSubmitRequest(FundPoolAdjustSubmitReq req) {
        if (req.getFundScore() == null) {
            throw new BizException("基金评分不能为空");
        }
        if (!FundInvestmentType.isValid(req.getFundInvestmentType())) {
            throw new BizException("基金投资类型不能为空且必须为合法枚举值");
        }
        if (req.getNeedRiskLeaderApproval() == null) {
            throw new BizException("请选择风管领导审批");
        }
        if (req.getNeedRiskLeaderApproval() != 0
                && req.getNeedRiskLeaderApproval() != 1) {
            throw new BizException("风管领导审批仅允许为 0 或 1");
        }
        if (req.getAdjusterId() == null || req.getAdjusterId().trim().isEmpty()) {
            throw new BizException("调整人 ID 不能为空");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("至少提交一条基金调库明细");
        }
    }

    /**
     * 校验提交明细与重新读取后的基金、投资池和一般流程一致。
     *
     * @param req 待提交的基金调库申请
     * @param fund 最新基金主档
     * @param poolMap 当前投资池映射
     * @param currentPoolIds 基金当前所在的投资池 ID
     * @param relations 当前投资池关系配置
     */
    private void validateSubmitItems(FundPoolAdjustSubmitReq req, FundInfoBo fund,
                                     Map<Long, InvestmentPoolBo> poolMap, Set<Long> currentPoolIds,
                                     List<PoolRelationBo> relations) {
        // 非管理员需解析用户 ID 并查询可调整的投资池
        Set<Long> adjustablePoolIds = ADMIN_USER_ID.equals(req.getAdjusterId())
                ? poolMap.keySet() : queryAdjustablePoolIds(parseUserId(req.getAdjusterId()));
        Set<String> keys = new HashSet<>();
        for (FundPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            // 确认提交明细的目标投资池仍然存在
            InvestmentPoolBo pool = requirePool(poolMap, item.getTargetPoolId());
            if (!adjustablePoolIds.contains(pool.getId())) {
                throw new BizException("当前用户没有投资池调整权限：" + pool.getPoolName());
            }
            // 按最新池状态和关系配置复核当前调整项
            List<String> failures = validatePoolAdjust(fund, pool, item.getAdjustMode(), currentPoolIds, relations);
            if (!failures.isEmpty()) {
                throw new BizException(pool.getPoolName() + "：" + String.join("；", failures));
            }
            // 生成目标池与调整方向的唯一键以检查重复明细
            String itemKey = itemKey(item.getTargetPoolId(), item.getAdjustMode());
            if (!keys.add(itemKey)) {
                throw new BizException("提交明细存在重复投资池及方向：" + pool.getPoolName());
            }
            // 阻止当前用户短时间内对同一基金和目标池重复提交
            if (fundPoolAdjustMapper.queryRecentDuplicate(fund.getFundCode(), pool.getId(),
                    item.getAdjustMode(), req.getAdjusterId())) {
                throw new BizException("请勿在短时间内重复提交基金调库申请：" + pool.getPoolName());
            }
            FundPoolAdjustSubmitReq.AdjustItem manual = ITEM_MANUAL.equals(item.getItemTag()) ? item : null;
            if (manual != null) {
                // 确认手工调整项使用目标池配置的一般流程
                validateNormalFlow(manual, pool, fund.getFundCode());
            }
            // 校验目标池对基金报告附件的要求
            validateReportRestriction(item, pool);
        }
    }

    /**
     * 校验一般流程快照，禁止快速和批量流程替代。
     *
     * @param item 手工调整项及其选择的流程
     * @param pool 目标投资池的流程配置
     * @param fundCode 当前基金代码，用于临时代码的 O32 人工节点判断
     */
    private void validateNormalFlow(FundPoolAdjustSubmitReq.AdjustItem item, InvestmentPoolBo pool, String fundCode) {
        Long expectedId = AdjustMode.IN.getCode().equals(item.getAdjustMode())
                ? pool.getInFlowId() : pool.getOutFlowId();
        String expectedKey = AdjustMode.IN.getCode().equals(item.getAdjustMode())
                ? pool.getInFlowKey() : pool.getOutFlowKey();
        String expectedType = AdjustMode.IN.getCode().equals(item.getAdjustMode())
                ? "normalInbound" : "normalOutbound";
        if (expectedId == null || !expectedId.equals(item.getFlowId())
                || expectedKey == null || !expectedKey.equals(item.getFlowKey())
                || !expectedType.equals(item.getFlowType())) {
            throw new BizException("只能选择目标投资池配置的一般审批流程：" + pool.getPoolName());
        }
        // 确认目标池的一般流程已发布且包含人工审批节点
        if (buildFlowSnapshot(expectedId, fundCode) == null) {
            throw new BizException("目标投资池的一般审批流程未发布：" + pool.getPoolName());
        }
    }

    /**
     * 校验投资池报告限制。
     *
     * @param item 待提交调整项及其报告附件
     * @param pool 目标投资池的报告限制配置
     */
    private void validateReportRestriction(FundPoolAdjustSubmitReq.AdjustItem item, InvestmentPoolBo pool) {
        if (!ITEM_MANUAL.equals(item.getItemTag())) {
            return;
        }
        String restriction = AdjustMode.IN.getCode().equals(item.getAdjustMode())
                ? pool.getInReportRestriction() : pool.getOutReportRestriction();
        if (restriction == null || "none".equals(restriction)) {
            return;
        }
        boolean hasManual = item.getReportFileIndexes() != null && !item.getReportFileIndexes().isEmpty();
        boolean hasSource = item.getReportSourceAttachmentIds() != null
                && !item.getReportSourceAttachmentIds().isEmpty();
        if (!hasManual && !hasSource) {
            throw new BizException("投资池要求提交基金报告：" + pool.getPoolName());
        }
        if ("internal".equals(restriction)) {
            if (!hasSource) {
                throw new BizException("投资池要求选择有效的内部基金报告：" + pool.getPoolName());
            }
            sysAttachmentService.validateCreditReportSources(item.getReportSourceAttachmentIds(), true);
        } else if (hasSource) {
            sysAttachmentService.validateCreditReportSources(item.getReportSourceAttachmentIds(), false);
        }
    }

    /**
     * 生成单条校验结果。
     *
     * @param resultMap 按目标池与方向去重的校验结果
     * @param fund 当前基金主档
     * @param poolId 目标投资池 ID
     * @param adjustMode 调入或调出方向
     * @param itemTag 手工、联动或互斥来源
     * @param groupKey 手工调整项的分组标识
     * @param poolMap 当前投资池映射
     * @param currentPoolIds 基金当前所在的投资池 ID
     * @param relations 当前投资池关系配置
     */
    private void addCheckResult(Map<String, FundAdjustCheckDto.CheckResultItem> resultMap, FundInfoBo fund,
                                Long poolId, String adjustMode, String itemTag, String groupKey,
                                Map<Long, InvestmentPoolBo> poolMap, Set<Long> currentPoolIds,
                                List<PoolRelationBo> relations) {
        // 用目标池和调整方向定位唯一校验结果
        String key = itemKey(poolId, adjustMode);
        if (resultMap.containsKey(key)) {
            return;
        }
        InvestmentPoolBo pool = poolMap.get(poolId);
        FundAdjustCheckDto.CheckResultItem result = new FundAdjustCheckDto.CheckResultItem();
        result.setFundCode(fund.getFundCode());
        result.setFundShortName(fund.getFundShortName());
        result.setSecurityType(fund.getSecurityType());
        result.setTargetPoolId(poolId);
        // 构建目标池的完整层级名称供校验结果展示
        result.setPoolName(pool == null ? null : buildPoolPath(poolId, poolMap));
        result.setPoolType(pool == null ? null : pool.getPoolType());
        result.setAdjustMode(adjustMode);
        result.setItemTag(itemTag);
        result.setAdjustGroupKey(groupKey);
        // 对存在的目标池执行基金调库规则校验
        List<String> failures = pool == null
                ? new ArrayList<>(Collections.singletonList("投资池不存在或已删除"))
                : validatePoolAdjust(fund, pool, adjustMode, currentPoolIds, relations);
        result.setFailReasons(failures);
        // 汇总弹性限制池提示，不将其计入阻断原因
        result.setWarnings(resolveSoftRelationWarnings(poolId, adjustMode, currentPoolIds, poolMap, relations));
        result.setCanAdjust(failures.isEmpty());
        // 为可选择的目标池生成一般流程候选项
        result.setFlowOptions(buildNormalFlowOptions(fund, pool, adjustMode, failures));
        resultMap.put(key, result);
    }

    /**
     * 展开联动与互斥关系。
     *
     * @param resultMap 已生成的调整项校验结果
     * @param fund 当前基金主档
     * @param manual 手工选择的调整项
     * @param groupKey 手工调整项的分组标识
     * @param poolMap 当前投资池映射
     * @param currentPoolIds 基金当前所在的投资池 ID
     * @param relations 当前投资池关系配置
     */
    private void expandRelationItems(Map<String, FundAdjustCheckDto.CheckResultItem> resultMap, FundInfoBo fund,
                                     FundAdjustCheckReq.CheckItem manual, String groupKey,
                                     Map<Long, InvestmentPoolBo> poolMap, Set<Long> currentPoolIds,
                                     List<PoolRelationBo> relations) {
        for (PoolRelationBo relation : relations) {
            if (!manual.getTargetPoolId().equals(relation.getPoolId())) {
                continue;
            }
            if (AdjustMode.IN.getCode().equals(manual.getAdjustMode())) {
                if (RelationType.IN_LINKED.getCode().equals(relation.getRelationType())) {
                    // 为调入联动池补充同方向调整项
                    addCheckResult(resultMap, fund, relation.getRelationPoolId(), AdjustMode.IN.getCode(),
                            ITEM_LINKAGE, groupKey, poolMap, currentPoolIds, relations);
                } else if (RelationType.IN_MUTEX.getCode().equals(relation.getRelationType())
                        && currentPoolIds.contains(relation.getRelationPoolId())) {
                    // 为已入池的调入互斥池补充调出项
                    addCheckResult(resultMap, fund, relation.getRelationPoolId(), AdjustMode.OUT.getCode(),
                            ITEM_MUTEX, groupKey, poolMap, currentPoolIds, relations);
                }
            } else if (AdjustMode.OUT.getCode().equals(manual.getAdjustMode())) {
                if (RelationType.OUT_LINKED.getCode().equals(relation.getRelationType())) {
                    // 为调出联动池补充同方向调整项
                    addCheckResult(resultMap, fund, relation.getRelationPoolId(), AdjustMode.OUT.getCode(),
                            ITEM_LINKAGE, groupKey, poolMap, currentPoolIds, relations);
                } else if (RelationType.OUT_MUTEX.getCode().equals(relation.getRelationType())
                        && !currentPoolIds.contains(relation.getRelationPoolId())) {
                    // 为尚未入池的调出互斥池补充调入项
                    addCheckResult(resultMap, fund, relation.getRelationPoolId(), AdjustMode.IN.getCode(),
                            ITEM_MUTEX, groupKey, poolMap, currentPoolIds, relations);
                }
            }
        }
    }

    /**
     * 执行单池通用基金调库校验。
     *
     * @param fund 当前基金主档
     * @param pool 目标投资池
     * @param adjustMode 调入或调出方向
     * @param currentPoolIds 基金当前所在的投资池 ID
     * @param relations 当前投资池关系配置
     * @return 校验失败原因列表
     */
    private List<String> validatePoolAdjust(FundInfoBo fund, InvestmentPoolBo pool, String adjustMode,
                                            Set<Long> currentPoolIds, List<PoolRelationBo> relations) {
        // 使用默认参数校验单个基金目标池调整项
        return validatePoolAdjust(fund, pool, adjustMode, currentPoolIds, relations, null);
    }

    /**
     * 校验基金单池调整规则，可排除当前正在审批的批次。
     *
     * @param fund 当前基金主档
     * @param pool 目标投资池
     * @param adjustMode 调入或调出方向
     * @param currentPoolIds 基金当前所在的投资池 ID
     * @param relations 当前投资池关系配置
     * @param excludedBatchNo 复核时需要排除的调库批次号
     * @return 校验失败原因列表
     */
    private List<String> validatePoolAdjust(FundInfoBo fund, InvestmentPoolBo pool, String adjustMode,
                                            Set<Long> currentPoolIds, List<PoolRelationBo> relations,
                                            String excludedBatchNo) {
        // 排除指定批次后复用完整的基金单池校验
        return validatePoolAdjust(fund, pool, adjustMode, currentPoolIds, relations, excludedBatchNo, true);
    }

    /**
     * 执行基金单池规则校验，可控制是否复核当前发布的一般流程。
     *
     * @param fund 当前基金主档
     * @param pool 目标投资池
     * @param adjustMode 调入或调出方向
     * @param currentPoolIds 基金当前所在的投资池 ID
     * @param relations 当前投资池关系配置
     * @param excludedBatchNo 复核时需要排除的调库批次号
     * @param validateFlow 是否校验目标池当前发布的一般流程
     * @return 校验失败原因列表
     */
    private List<String> validatePoolAdjust(FundInfoBo fund, InvestmentPoolBo pool, String adjustMode,
                                            Set<Long> currentPoolIds, List<PoolRelationBo> relations,
                                            String excludedBatchNo, boolean validateFlow) {
        List<String> failures = new ArrayList<>();
        if (!AdjustMode.IN.getCode().equals(adjustMode) && !AdjustMode.OUT.getCode().equals(adjustMode)) {
            failures.add("调整方向不合法");
            return failures;
        }
        // 校验目标池状态及对基金品种、基金市场的准入配置
        if (!ENABLED.equals(pool.getStatus())) {
            failures.add("投资池未启用");
        }
        if (Integer.valueOf(1).equals(pool.getLockFlag())) {
            failures.add("投资池已锁定");
        }
        if (pool.getVarietyCodes() == null || !pool.getVarietyCodes().contains(FUND_VARIETY_TOKEN)) {
            failures.add("投资池不支持基金品种");
        }
        String marketToken = "\"" + fund.getMarketCode() + "\"";
        if (pool.getMarketCodes() == null || !pool.getMarketCodes().contains(marketToken)) {
            failures.add("基金市场不在投资池允许范围内");
        }
        // 对照当前池状态判断调入或调出方向是否仍然有效
        boolean currentlyIn = currentPoolIds.contains(pool.getId());
        if (AdjustMode.IN.getCode().equals(adjustMode) && currentlyIn) {
            failures.add("基金已在目标投资池中");
        }
        if (AdjustMode.OUT.getCode().equals(adjustMode) && !currentlyIn) {
            failures.add("基金当前不在目标投资池中");
        }
        // 调入前检查目标池是否仍有剩余容量
        if (AdjustMode.IN.getCode().equals(adjustMode) && pool.getMaxCapacity() != null
                && fundPoolAdjustMapper.queryPoolCurrentCount(pool.getId()) >= pool.getMaxCapacity()) {
            failures.add("目标投资池容量已满");
        }
        // 阻止同一基金在目标池存在未完成调库流程时再次申请
        if (fundPoolAdjustMapper.queryFundHasPendingProcess(fund.getFundCode(), pool.getId(), excludedBatchNo)) {
            failures.add("该基金在目标投资池存在待处理流程");
        }
        // 校验目标池配置的来源池及调入调出限制池
        validateRelationRestrictions(failures, pool.getId(), adjustMode, currentPoolIds, relations);
        // 配置开放日限制的投资池仅在开放区间内允许调整
        if (Integer.valueOf(1).equals(pool.getOpenDayAdjust())) {
            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            if (!fundPoolAdjustMapper.queryPoolInOpenDay(pool.getId(), today)) {
                failures.add("当前日期不在投资池开放日区间内");
            }
        }
        // 调出前核对基金入池时间是否满足冻结期要求
        if (AdjustMode.OUT.getCode().equals(adjustMode) && pool.getFrozenPeriodIn() != null
                && pool.getFrozenPeriodIn() > 0) {
            Date entryTime = fundPoolAdjustMapper.queryFundPoolEntryTime(fund.getFundCode(), pool.getId());
            if (entryTime != null) {
                LocalDate entryDate = entryTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                long days = ChronoUnit.DAYS.between(entryDate, LocalDate.now());
                if (days < pool.getFrozenPeriodIn()) {
                    failures.add("基金仍在调出冻结期内");
                }
            }
        }
        if (validateFlow) {
            Long flowId = AdjustMode.IN.getCode().equals(adjustMode) ? pool.getInFlowId() : pool.getOutFlowId();
            // 确认目标池配置了已发布且含人工审批的流程
            if (flowId == null || buildFlowSnapshot(flowId, fund.getFundCode()) == null) {
                failures.add("目标投资池未配置已发布的一般审批流程");
            }
        }
        return failures;
    }

    /**
     * 校验来源池、限制池关系。
     *
     * @param failures 待追加阻断原因的结果列表
     * @param poolId 目标投资池 ID
     * @param adjustMode 调入或调出方向
     * @param currentPoolIds 基金当前所在的投资池 ID
     * @param relations 当前投资池关系配置
     */
    private void validateRelationRestrictions(List<String> failures, Long poolId, String adjustMode,
                                              Set<Long> currentPoolIds, List<PoolRelationBo> relations) {
        for (PoolRelationBo relation : relations) {
            if (!poolId.equals(relation.getPoolId())) {
                continue;
            }
            if (RelationType.SOURCE.getCode().equals(relation.getRelationType())
                    && AdjustMode.IN.getCode().equals(adjustMode)
                    && !currentPoolIds.contains(relation.getRelationPoolId())) {
                failures.add("基金不在配置的来源池中");
            }
            if (RelationType.IN_RESTRICT.getCode().equals(relation.getRelationType())
                    && AdjustMode.IN.getCode().equals(adjustMode)
                    && currentPoolIds.contains(relation.getRelationPoolId())) {
                failures.add("基金命中调入限制池");
            }
            if (RelationType.OUT_RESTRICT.getCode().equals(relation.getRelationType())
                    && AdjustMode.OUT.getCode().equals(adjustMode)
                    && currentPoolIds.contains(relation.getRelationPoolId())) {
                failures.add("基金命中调出限制池");
            }
        }
    }

    /**
     * 解析弹性限制关系警告。
     *
     * @param poolId 目标投资池 ID
     * @param adjustMode 调入或调出方向
     * @param currentPoolIds 基金当前所在的投资池 ID
     * @param poolMap 当前投资池映射
     * @param relations 当前投资池关系配置
     * @return 弹性限制警告列表
     */
    private List<String> resolveSoftRelationWarnings(Long poolId, String adjustMode, Set<Long> currentPoolIds,
                                                     Map<Long, InvestmentPoolBo> poolMap,
                                                     List<PoolRelationBo> relations) {
        List<String> warnings = new ArrayList<>();
        String expected = AdjustMode.IN.getCode().equals(adjustMode)
                ? RelationType.IN_SOFT_RESTRICT.getCode() : RelationType.OUT_SOFT_RESTRICT.getCode();
        for (PoolRelationBo relation : relations) {
            if (poolId.equals(relation.getPoolId()) && expected.equals(relation.getRelationType())
                    && currentPoolIds.contains(relation.getRelationPoolId())) {
                InvestmentPoolBo relationPool = poolMap.get(relation.getRelationPoolId());
                warnings.add("基金命中弹性限制池"
                        + (relationPool == null ? "" : "：" + relationPool.getPoolName()));
            }
        }
        return warnings;
    }

    /**
     * 构建目标池唯一的一般流程候选。
     *
     * @param fund 当前基金主档
     * @param pool 目标投资池
     * @param adjustMode 调入或调出方向
     * @param failures 已发现的调库阻断原因
     * @return 一般流程候选项列表
     */
    private List<FundAdjustCheckDto.FlowOption> buildNormalFlowOptions(FundInfoBo fund, InvestmentPoolBo pool, String adjustMode,
                                                                       List<String> failures) {
        if (pool == null) {
            return Collections.emptyList();
        }
        Long flowId = AdjustMode.IN.getCode().equals(adjustMode) ? pool.getInFlowId() : pool.getOutFlowId();
        String flowKey = AdjustMode.IN.getCode().equals(adjustMode) ? pool.getInFlowKey() : pool.getOutFlowKey();
        String flowName = AdjustMode.IN.getCode().equals(adjustMode) ? pool.getInFlowName() : pool.getOutFlowName();
        // 仅为已发布且可用的一般流程生成候选项
        if (flowId == null || buildFlowSnapshot(flowId, fund.getFundCode()) == null) {
            return Collections.emptyList();
        }
        FundAdjustCheckDto.FlowOption option = new FundAdjustCheckDto.FlowOption();
        option.setFlowId(flowId);
        option.setFlowKey(flowKey);
        option.setFlowName(flowName);
        option.setFlowType(AdjustMode.IN.getCode().equals(adjustMode) ? "normalInbound" : "normalOutbound");
        option.setRecommended(true);
        option.setMatched(true);
        option.setSelectable(failures.isEmpty());
        option.setMatchReasons(Collections.singletonList("使用目标池配置的默认「" + flowName + "」"));
        option.setUnmatchReasons(Collections.emptyList());
        return Collections.singletonList(option);
    }

    /**
     * 构建基金调库日志，不信任前端基金名称和产品类型。
     *
     * @param req 基金调库申请
     * @param fund 最新基金主档
     * @param item 当前调整项
     * @param manual 同组手工调整项，用于取得流程类型
     * @param pool 目标投资池
     * @param poolMap 当前投资池映射，用于生成完整池名
     * @param batchNo 当前调库分组的批次号
     * @param snapshot 已发布的一般流程快照
     * @return 基金调库日志记录
     */
    private FundAdjustLogBo buildAdjustLog(FundPoolAdjustSubmitReq req, FundInfoBo fund,
                                           FundPoolAdjustSubmitReq.AdjustItem item,
                                           FundPoolAdjustSubmitReq.AdjustItem manual,
                                           InvestmentPoolBo pool, Map<Long, InvestmentPoolBo> poolMap,
                                           String batchNo, FlowSnapshot snapshot) {
        FundAdjustLogBo log = new FundAdjustLogBo();
        log.setFundCode(fund.getFundCode());
        log.setFundName(fund.getFundName());
        log.setFundShortName(fund.getFundShortName());
        log.setSecurityType(fund.getSecurityType());
        log.setFundScore(req.getFundScore());
        log.setFundInvestmentType(req.getFundInvestmentType());
        log.setNeedRiskLeaderApproval(req.getNeedRiskLeaderApproval());
        // 按手工、联动或互斥来源确定日志调整类型
        log.setAdjustType(resolveAdjustType(req.getAdjustType(), item.getItemTag()));
        log.setAdjustMode(item.getAdjustMode());
        log.setAdjustBatchNo(batchNo);
        log.setTargetPoolId(pool.getId());
        // 将目标投资池全路径写入调库日志快照
        log.setTargetPoolName(buildPoolPath(pool.getId(), poolMap));
        log.setPoolType(pool.getPoolType());
        log.setFlowId(snapshot.definition.getId());
        log.setFlowKey(snapshot.definition.getFlowKey());
        log.setFlowType(manual.getFlowType());
        log.setAuditStatus(AuditStatus.SUBMITTED.getCode());
        log.setAdjusterId(req.getAdjusterId());
        log.setAdjusterName(req.getAdjusterName());
        log.setAdjustReason(req.getAdjustReason());
        log.setAdjustAdvice(req.getAdjustAdvice());
        log.setSubmitTime(new Date());
        return log;
    }

    /**
     * 构建投资池全路径名称。
     *
     * @param poolId 当前投资池 ID
     * @param poolMap 当前投资池及其祖先节点映射
     * @return 投资池全路径名称
     */
    private String buildPoolPath(Long poolId, Map<Long, InvestmentPoolBo> poolMap) {
        InvestmentPoolBo pool = poolMap.get(poolId);
        if (pool == null) {
            return "";
        }
        String poolName = pool.getPoolName() == null ? "" : pool.getPoolName();
        if (pool.getParentId() == null) {
            return poolName;
        }
        // 递归取得父级路径以拼接完整池名称
        String parentName = buildPoolPath(pool.getParentId(), poolMap);
        return parentName.isEmpty() ? poolName : parentName + "/" + poolName;
    }

    /**
     * 绑定基金专属分类附件。
     *
     * @param logId 当前基金调库日志 ID
     * @param item 当前调整项及其附件索引
     * @param submissionFiles 已上传的附件文件上下文，可为空
     * @param uploaderId 附件上传人 ID
     */
    private void bindAttachments(Long logId, FundPoolAdjustSubmitReq.AdjustItem item,
                                 SysAttachmentService.SubmissionFiles submissionFiles, String uploaderId) {
        // 将本次上传的报告和材料按基金附件分类绑定
        if (submissionFiles != null) {
            sysAttachmentService.bindAttachments(FUND_ADJUST_LOG_TABLE, logId, item.getReportFileIndexes(),
                    AttachmentCategory.FUND_REPORT_HAND.getCode(), submissionFiles);
            sysAttachmentService.bindAttachments(FUND_ADJUST_LOG_TABLE, logId, item.getMaterialFileIndexes(),
                    AttachmentCategory.FUND_MATERIAL_HAND.getCode(), submissionFiles);
        }
        // 将已选择的来源报告和材料复制到当前调库记录
        sysAttachmentService.copyReportAttachments(FUND_ADJUST_LOG_TABLE, logId,
                item.getReportSourceAttachmentIds(), AttachmentPurpose.CREDIT_REPORT.getCode(), uploaderId);
        sysAttachmentService.copyReportAttachments(FUND_ADJUST_LOG_TABLE, logId,
                item.getMaterialSourceAttachmentIds(), AttachmentPurpose.MATERIAL.getCode(), uploaderId);
    }

    /**
     * 构建已发布流程快照。
     *
     * @param flowId 一般审批流程定义 ID
     * @param fundCode 当前基金代码，用于判断 O32 是否需人工处理
     * @return 已发布流程快照
     */
    private FlowSnapshot buildFlowSnapshot(Long flowId, String fundCode) {
        if (flowId == null) {
            return null;
        }
        // 读取处于启用状态的流程定义及其已发布版本
        FlowDefinitionBo definition = flowMapper.queryFlowById(flowId);
        if (definition == null || !FlowStatus.ACTIVE.getCode().equals(definition.getStatus())) {
            return null;
        }
        FlowVersionBo activeVersion = null;
        for (FlowVersionBo version : flowMapper.queryFlowVersionByFlowIdList(flowId, null)) {
            if (FlowStatus.ACTIVE.getCode().equals(version.getStatus())) {
                activeVersion = version;
                break;
            }
        }
        if (activeVersion == null) {
            return null;
        }
        // 固定当前发布版本的节点、连线、审批配置和处理人
        List<FlowNodeBo> nodes = flowMapper.queryFlowNodeListByVersionId(activeVersion.getId());
        List<FlowEdgeBo> edges = flowMapper.queryFlowEdgeListByVersionId(activeVersion.getId());
        Map<Long, NodeApprovalConfigBo> configMap = new HashMap<>();
        for (NodeApprovalConfigBo config : flowMapper.queryApprovalConfigListByVersionId(activeVersion.getId())) {
            configMap.put(config.getNodeId(), config);
        }
        Map<Long, List<NodeApprovalHandlerBo>> handlerMap = new HashMap<>();
        for (NodeApprovalHandlerBo handler : flowMapper.queryApprovalHandlerListByVersionId(activeVersion.getId())) {
            handlerMap.computeIfAbsent(handler.getApprovalConfigId(), key -> new ArrayList<>()).add(handler);
        }
        FlowSnapshot snapshot = new FlowSnapshot(definition, nodes, edges, configMap, handlerMap);
        // 将当前有效临时代码状态带入快照，供 O32 节点按人工方式处理
        snapshot.temporaryFund = tempFundCodeMapper.queryTemporaryCodeCountByFundCode(fundCode) > 0;
        // 确认流程包含实际人工审批节点后返回快照
        return hasManualApprovalNode(snapshot) ? snapshot : null;
    }

    /**
     * 判断基金调库一般流程是否包含至少一个实际人工审批节点。
     *
     * @param snapshot 已发布流程的节点及审批配置快照
     * @return 是否包含实际人工审批节点
     */
    private boolean hasManualApprovalNode(FlowSnapshot snapshot) {
        for (FlowNodeBo node : snapshot.nodes) {
            if (!NodeType.APPROVAL.getCode().equals(node.getNodeType())) {
                continue;
            }
            NodeApprovalConfigBo config = snapshot.configMap.get(node.getId());
            // 判断审批节点是否由系统自动处理
            boolean autoNode = isSystemAutoApproval(config, snapshot.temporaryFund);
            // 识别通过提交连线流转的发起人节点
            boolean submitNode = config != null
                    && ApprovalStrategy.INITIATOR.getCode().equals(config.getApprovalStrategy())
                    && hasOutgoingRouteAction(snapshot, node, ProcessAction.SUBMIT.getCode());
            if (!autoNode && !submitNode) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断基金调库审批节点是否由系统自动通过。
     *
     * @param config 当前节点的审批配置
     * @param temporaryFund 当前基金是否为有效临时代码
     * @return 是否由系统自动通过
     */
    private boolean isSystemAutoApproval(NodeApprovalConfigBo config, boolean temporaryFund) {
        return config != null && (ApprovalStrategy.AUTO.getCode().equals(config.getApprovalStrategy())
                || (ApprovalStrategy.O32.getCode().equals(config.getApprovalStrategy()) && !temporaryFund));
    }

    /**
     * 创建开始、发起人和首个人工审批步骤。
     *
     * @param logId 当前基金调库日志 ID
     * @param batchNo 当前调库批次号
     * @param snapshot 已发布流程快照
     * @param adjusterId 发起人 ID
     * @param adjusterName 发起人姓名
     */
    private void createInitialSteps(Long logId, String batchNo, FlowSnapshot snapshot,
                                    String adjusterId, String adjusterName) {
        // 定位流程开始节点以创建起始步骤
        FlowNodeBo current = findNode(snapshot.nodes, NodeType.START.getCode());
        if (current == null) {
            throw new BizException("一般审批流程缺少开始节点");
        }
        // 写入系统自动处理的流程开始步骤
        insertStep(logId, batchNo, current, null, StepStatus.AUTO_PROCESS.getCode(),
                null, null, ProcessAction.AUTO_PROCESS.getCode(), new Date());
        Set<Long> visited = new HashSet<>();
        while (current != null && current.getId() != null && visited.add(current.getId())) {
            // 沿主流程路径推进到下一个节点
            current = findNextNode(snapshot, current);
            if (current == null) {
                break;
            }
            NodeApprovalConfigBo config = snapshot.configMap.get(current.getId());
            if (NodeType.END.getCode().equals(current.getNodeType())) {
                // 为终止节点记录系统自动完成步骤
                insertStep(logId, batchNo, current, config, StepStatus.AUTO_PROCESS.getCode(),
                        null, null, ProcessAction.AUTO_PROCESS.getCode(), new Date());
                break;
            }
            // 判断非审批节点或系统自动审批节点是否可直接跳过
            if (!NodeType.APPROVAL.getCode().equals(current.getNodeType())
                    || isSystemAutoApproval(config, snapshot.temporaryFund)) {
                // 为无需人工处理的节点记录自动完成步骤
                insertStep(logId, batchNo, current, config, StepStatus.AUTO_PROCESS.getCode(),
                        null, null, ProcessAction.AUTO_PROCESS.getCode(), new Date());
                continue;
            }
            // 判断当前节点是否为提交后自动流转的发起人节点
            if (config != null && ApprovalStrategy.INITIATOR.getCode().equals(config.getApprovalStrategy())
                    && hasOutgoingRouteAction(snapshot, current, ProcessAction.SUBMIT.getCode())) {
                // 记录发起人已提交的审批步骤
                insertStep(logId, batchNo, current, config, StepStatus.SUBMIT.getCode(),
                        adjusterId, adjusterName, ProcessAction.SUBMIT.getCode(), new Date());
                continue;
            }
            // 根据审批配置解析首个人工节点的处理人
            List<HandlerTarget> handlers = resolveHandlers(config, snapshot);
            if (handlers.isEmpty()) {
                // 未配置处理人时仍创建待办步骤供后续处理
                insertStep(logId, batchNo, current, config, StepStatus.PENDING.getCode(),
                        null, null, null, null);
            } else {
                for (HandlerTarget handler : handlers) {
                    // 为每位配置的处理人创建待审批步骤
                    insertStep(logId, batchNo, current, config, StepStatus.PENDING.getCode(),
                            handler.id, handler.name, null, null);
                }
            }
            break;
        }
    }

    /**
     * 解析审批节点处理人。
     *
     * @param config 当前节点的审批配置
     * @param snapshot 包含处理人配置的流程快照
     * @return 审批节点处理人列表
     */
    private List<HandlerTarget> resolveHandlers(NodeApprovalConfigBo config, FlowSnapshot snapshot) {
        if (config == null || config.getId() == null) {
            return Collections.emptyList();
        }
        LinkedHashMap<String, HandlerTarget> targets = new LinkedHashMap<>();
        List<RoleBo> allRoles = null;
        for (NodeApprovalHandlerBo handler : snapshot.handlerMap.getOrDefault(config.getId(), Collections.emptyList())) {
            if (handler == null || handler.getHandlerId() == null) {
                continue;
            }
            if (HandlerType.USER.getCode().equals(handler.getHandlerType())) {
                String id = String.valueOf(handler.getHandlerId());
                targets.put(id, new HandlerTarget(id, handler.getHandlerName()));
            } else if (HandlerType.ROLE.getCode().equals(handler.getHandlerType())) {
                if (allRoles == null) {
                    allRoles = flowMapper.queryRoleList();
                }
                List<Long> roleIds = new ArrayList<>();
                // 收集指定角色及其子角色以展开审批用户
                collectDescendantRoleIds(handler.getHandlerId(), roleIds, allRoles, new HashSet<Long>());
                List<UserBo> users = flowMapper.queryUserList(roleIds, null);
                if (users != null) {
                    for (UserBo user : users) {
                        if (user != null && user.getId() != null) {
                            String id = String.valueOf(user.getId());
                            targets.put(id, new HandlerTarget(id, user.getName()));
                        }
                    }
                }
            }
        }
        return new ArrayList<>(targets.values());
    }

    /**
     * 递归收集角色及其子角色 ID。
     *
     * @param roleId 当前角色 ID
     * @param roleIds 已收集的角色 ID
     * @param allRoles 全部角色及父子关系
     * @param visitedRoleIds 防止角色关系循环的已访问 ID
     */
    private void collectDescendantRoleIds(Long roleId, List<Long> roleIds, List<RoleBo> allRoles,
                                          Set<Long> visitedRoleIds) {
        if (roleId == null || !visitedRoleIds.add(roleId)) {
            return;
        }
        roleIds.add(roleId);
        if (allRoles == null) {
            return;
        }
        for (RoleBo role : allRoles) {
            if (role != null && roleId.equals(role.getParentId())) {
                // 递归收集当前角色的子角色及其后代
                collectDescendantRoleIds(role.getId(), roleIds, allRoles, visitedRoleIds);
            }
        }
    }

    /**
     * 新增基金调库步骤。
     *
     * @param logId 当前基金调库日志 ID
     * @param batchNo 当前调库批次号
     * @param node 当前流程节点
     * @param config 当前节点的审批配置，可为空
     * @param status 待处理、已提交或自动处理状态
     * @param handlerId 当前步骤处理人 ID，可为空
     * @param handlerName 当前步骤处理人姓名，可为空
     * @param action 当前步骤的处理动作，可为空
     * @param processTime 当前步骤的处理时间，可为空
     */
    private void insertStep(Long logId, String batchNo, FlowNodeBo node, NodeApprovalConfigBo config,
                            String status, String handlerId, String handlerName, String action, Date processTime) {
        FundAdjustStepBo step = new FundAdjustStepBo();
        step.setAdjustLogId(logId);
        step.setAdjustBatchNo(batchNo);
        step.setFlowNodeId(node.getId());
        step.setNodeCode(node.getNodeId());
        step.setNodeLabel(node.getLabel());
        step.setNodeType(node.getNodeType());
        step.setApprovalStrategy(config == null ? null : config.getApprovalStrategy());
        step.setSortOrder(node.getSortOrder());
        step.setStepStatus(status);
        step.setHandlerId(handlerId);
        step.setHandlerName(handlerName);
        step.setProcessAction(action);
        step.setStartTime(new Date());
        step.setProcessTime(processTime);
        fundPoolAdjustMapper.addAdjustStep(step);
    }

    /**
     * 沿非驳回主路径查找下一节点。
     *
     * @param snapshot 当前流程快照
     * @param current 当前流程节点
     * @return 后续流程节点，不存在时返回 null
     */
    private FlowNodeBo findNextNode(FlowSnapshot snapshot, FlowNodeBo current) {
        for (FlowEdgeBo edge : snapshot.edges) {
            if (current.getId().equals(edge.getFromNodeId())
                    && !ProcessAction.REJECT.getCode().equals(edge.getRouteAction())) {
                for (FlowNodeBo node : snapshot.nodes) {
                    if (node.getId().equals(edge.getToNodeId())) {
                        return node;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 判断流程节点是否存在指定动作的后续连线。
     *
     * @param snapshot 当前流程快照
     * @param node 待检查的流程节点
     * @param routeAction 需要查找的流转动作
     * @return 是否存在指定动作的后续连线
     */
    private boolean hasOutgoingRouteAction(FlowSnapshot snapshot, FlowNodeBo node, String routeAction) {
        for (FlowEdgeBo edge : snapshot.edges) {
            if (node.getId().equals(edge.getFromNodeId()) && routeAction.equals(edge.getRouteAction())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按类型查找流程节点。
     *
     * @param nodes 当前流程的节点列表
     * @param nodeType 需要查找的节点类型
     * @return 流程节点，不存在时返回 null
     */
    private FlowNodeBo findNode(List<FlowNodeBo> nodes, String nodeType) {
        for (FlowNodeBo node : nodes) {
            if (nodeType.equals(node.getNodeType())) {
                return node;
            }
        }
        return null;
    }

    /**
     * 按调整权限过滤投资池并保留祖先。
     *
     * @param pools 全部投资池
     * @param userId 当前用户 ID
     * @return 按调整权限过滤后的投资池列表
     */
    private List<InvestmentPoolBo> filterPoolsByPermission(List<InvestmentPoolBo> pools, Long userId) {
        // 查询当前用户可直接调整的投资池 ID
        Set<Long> allowedIds = queryAdjustablePoolIds(userId);
        Map<Long, InvestmentPoolBo> poolMap = pools.stream()
                .filter(pool -> pool.getId() != null)
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool));
        Set<Long> visibleIds = new HashSet<>();
        for (Long allowedId : allowedIds) {
            InvestmentPoolBo current = poolMap.get(allowedId);
            while (current != null && visibleIds.add(current.getId())) {
                current = poolMap.get(current.getParentId());
            }
        }
        return pools.stream().filter(pool -> visibleIds.contains(pool.getId())).collect(Collectors.toList());
    }

    /**
     * 查询用户直接或通过角色拥有调整权限的池。
     *
     * @param userId 当前用户 ID
     * @return 用户可调整的池 ID 集合
     */
    private Set<Long> queryAdjustablePoolIds(Long userId) {
        // 汇总用户所属角色及池级可调整人员配置
        Set<Long> roleIds = new HashSet<>(investmentPoolMapper.queryUserRoleIdList(userId));
        Set<Long> poolIds = new HashSet<>();
        for (PoolPermissionBo permission : investmentPoolMapper.queryPermissionListByType(
                PermissionType.ADJUSTABLE.getCode())) {
            if (permission.getPoolId() == null || permission.getHandlerId() == null) {
                continue;
            }
            if (HandlerType.USER.getCode().equals(permission.getHandlerType())
                    && permission.getHandlerId().equals(userId)) {
                poolIds.add(permission.getPoolId());
            } else if (HandlerType.ROLE.getCode().equals(permission.getHandlerType())
                    && roleIds.contains(permission.getHandlerId())) {
                poolIds.add(permission.getPoolId());
            }
        }
        return poolIds;
    }

    /**
     * 保留支持基金和基金市场的池及其祖先。
     *
     * @param pools 待筛选的投资池列表
     * @param marketCode 当前基金所属市场编码
     * @return 支持基金及其市场的投资池列表
     */
    private List<InvestmentPoolBo> retainFundPoolsAndAncestors(List<InvestmentPoolBo> pools, String marketCode) {
        Map<Long, InvestmentPoolBo> poolMap = pools.stream()
                .filter(pool -> pool.getId() != null)
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool));
        Set<Long> retained = new HashSet<>();
        String marketToken = "\"" + marketCode + "\"";
        // 仅保留支持基金品种及当前市场的启用池，并补齐其祖先节点
        for (InvestmentPoolBo pool : pools) {
            if (!ENABLED.equals(pool.getStatus()) || pool.getVarietyCodes() == null
                    || !pool.getVarietyCodes().contains(FUND_VARIETY_TOKEN)
                    || pool.getMarketCodes() == null || !pool.getMarketCodes().contains(marketToken)) {
                continue;
            }
            InvestmentPoolBo current = pool;
            while (current != null && retained.add(current.getId())) {
                current = poolMap.get(current.getParentId());
            }
        }
        return pools.stream().filter(pool -> retained.contains(pool.getId())).collect(Collectors.toList());
    }

    /**
     * 按主档主键顺序取得整批基金锁，并保持代码大小写与数据库匹配口径一致。
     *
     * @param fundCodes 本次写入涉及的基金代码
     * @return 以代码索引的已锁定当前基金主档
     */
    private Map<String, FundInfoBo> lockFundList(List<String> fundCodes) {
        Set<String> distinctCodes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String fundCode : fundCodes) {
            if (fundCode == null || fundCode.trim().isEmpty()) {
                throw new BizException("基金代码不能为空");
            }
            distinctCodes.add(fundCode.trim());
        }
        // 通过主档行锁串行处理业务变更，并清除锁前的 MyBatis 查询缓存
        List<FundInfoBo> funds = fundPoolAdjustMapper.queryFundListForUpdate(new ArrayList<>(distinctCodes));
        Map<String, FundInfoBo> lockedFunds = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (FundInfoBo fund : funds) {
            if (lockedFunds.put(fund.getFundCode(), fund) != null) {
                throw new BizException("基金主档代码存在重复，请先处理数据，基金代码：" + fund.getFundCode());
            }
        }
        return lockedFunds;
    }

    /**
     * 使用已锁定的当前主档校验基金可调整状态。
     *
     * @param fundCode 待提交或终审的基金代码
     * @param lockedFunds 当前事务已锁定的基金主档
     * @return 可继续调库的当前基金主档
     */
    private FundInfoBo requireLockedFund(String fundCode, Map<String, FundInfoBo> lockedFunds) {
        FundInfoBo fund = lockedFunds.get(fundCode.trim());
        if (fund == null) {
            throw new BizException("基金不存在或已删除");
        }
        if ("D".equals(fund.getSecurityStatus())) {
            throw new BizException("已终止或退市基金不能发起调库");
        }
        return fund;
    }

    /**
     * 读取并校验基金。
     *
     * @param fundCode 基金代码
     * @param requireActive 是否要求基金仍可发起调库
     * @return 校验通过的基金主档
     */
    private FundInfoBo requireFund(String fundCode, boolean requireActive) {
        if (fundCode == null || fundCode.trim().isEmpty()) {
            throw new BizException("基金代码不能为空");
        }
        FundInfoBo fund = fundPoolAdjustMapper.queryFundByCode(fundCode.trim());
        if (fund == null) {
            throw new BizException("基金不存在或已删除");
        }
        if (requireActive && "D".equals(fund.getSecurityStatus())) {
            throw new BizException("已终止或退市基金不能发起调库");
        }
        return fund;
    }

    /**
     * 读取投资池。
     *
     * @param poolMap 当前投资池映射
     * @param poolId 目标投资池 ID
     * @return 投资池信息
     */
    private InvestmentPoolBo requirePool(Map<Long, InvestmentPoolBo> poolMap, Long poolId) {
        InvestmentPoolBo pool = poolMap.get(poolId);
        if (pool == null) {
            throw new BizException("投资池不存在或已删除");
        }
        return pool;
    }

    /**
     * 解析数字用户 ID。
     *
     * @param userId 当前用户 ID 字符串
     * @return 解析后的用户 ID
     */
    private Long parseUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new BizException("当前用户 ID 不能为空");
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException e) {
            throw new BizException("当前用户 ID 不合法");
        }
    }

    /**
     * 生成基金调库批次号。
     *
     * @return 基金调库批次号
     */
    private String generateBatchNo() {
        String time = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        return "FUND-" + time + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * 获取非空分组标识。
     *
     * @param item 当前调整项
     * @return 非空分组标识
     */
    private String requireGroupKey(FundPoolAdjustSubmitReq.AdjustItem item) {
        if (item.getAdjustGroupKey() == null || item.getAdjustGroupKey().trim().isEmpty()) {
            throw new BizException("调库分组标识不能为空");
        }
        return item.getAdjustGroupKey().trim();
    }

    /**
     * 生成池及方向唯一键。
     *
     * @param poolId 目标投资池 ID
     * @param adjustMode 调入或调出方向
     * @return 池及调整方向的组合键
     */
    private String itemKey(Long poolId, String adjustMode) {
        return String.valueOf(poolId) + "|" + adjustMode;
    }

    /**
     * 按调整项来源解析调整类型。
     *
     * @param requested 请求中填写的调整类型
     * @param itemTag 手工、联动或互斥来源
     * @return 基金调库调整类型
     */
    private String resolveAdjustType(String requested, String itemTag) {
        if (ITEM_LINKAGE.equals(itemTag)) {
            return "联动调整";
        }
        if (ITEM_MUTEX.equals(itemTag)) {
            return "互斥调整";
        }
        return requested == null || requested.trim().isEmpty() ? "手工调整" : requested.trim();
    }

    /** 写入前已完成复核的基金申请上下文 */
    private static class PreparedSubmit {
        /** 来源分组的申请字段与明细 */
        private final FundPoolAdjustSubmitReq req;
        /** 当前基金主档 */
        private final FundInfoBo fund;
        /** 当前投资池映射 */
        private final Map<Long, InvestmentPoolBo> poolMap;
        /** 来源组手工主项 */
        private final Map<String, FundPoolAdjustSubmitReq.AdjustItem> manualByGroup;
        /** 来源组已发布流程快照 */
        private final Map<String, FlowSnapshot> snapshotByGroup;

        /**
         * 保存已复核的主档、目标池与一般流程快照。
         *
         * @param req 来源分组申请
         * @param fund 当前基金主档
         * @param poolMap 当前投资池映射
         * @param manualByGroup 各来源组的手工主项
         * @param snapshotByGroup 各来源组的一般流程快照
         */
        PreparedSubmit(FundPoolAdjustSubmitReq req, FundInfoBo fund, Map<Long, InvestmentPoolBo> poolMap,
                       Map<String, FundPoolAdjustSubmitReq.AdjustItem> manualByGroup,
                       Map<String, FlowSnapshot> snapshotByGroup) {
            this.req = req;
            this.fund = fund;
            this.poolMap = poolMap;
            this.manualByGroup = manualByGroup;
            this.snapshotByGroup = snapshotByGroup;
        }
    }

    /** 已发布流程快照 */
    private static class FlowSnapshot {
        /** 流程定义 */
        private final FlowDefinitionBo definition;
        /** 流程节点 */
        private final List<FlowNodeBo> nodes;
        /** 流程连线 */
        private final List<FlowEdgeBo> edges;
        /** 审批配置 */
        private final Map<Long, NodeApprovalConfigBo> configMap;
        /** 审批处理人 */
        private final Map<Long, List<NodeApprovalHandlerBo>> handlerMap;
        /** 当前基金的有效临时代码标记，决定 O32 节点是否转人工 */
        private boolean temporaryFund;

        /**
         * 保存流程定义、节点、连线及审批配置快照。
         *
         * @param definition 流程定义
         * @param nodes 流程节点
         * @param edges 流程连线
         * @param configMap 节点审批配置映射
         * @param handlerMap 节点审批处理人映射
         */
        FlowSnapshot(FlowDefinitionBo definition, List<FlowNodeBo> nodes, List<FlowEdgeBo> edges,
                     Map<Long, NodeApprovalConfigBo> configMap,
                     Map<Long, List<NodeApprovalHandlerBo>> handlerMap) {
            this.definition = definition;
            this.nodes = nodes;
            this.edges = edges;
            this.configMap = configMap;
            this.handlerMap = handlerMap;
        }
    }

    /** 流程处理人 */
    private static class HandlerTarget {
        /** 人员 ID */
        private final String id;
        /** 人员姓名 */
        private final String name;

        /**
         * 保存审批处理人的 ID 和姓名。
         *
         * @param id 处理人 ID
         * @param name 处理人姓名
         */
        HandlerTarget(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
