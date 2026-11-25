package com.znty.rrs.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.FlowType;
import com.znty.rrs.common.util.StockQueryFilterHelper;
import com.znty.rrs.common.enums.StockRating;
import com.znty.rrs.common.util.AdminUserIdUtil;
import com.znty.rrs.common.enums.ApprovalStrategy;
import com.znty.rrs.common.enums.AttachmentCategory;
import com.znty.rrs.common.enums.AttachmentPurpose;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.FlowStatus;
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
import com.znty.rrs.entity.bo.StockAdjustLogBo;
import com.znty.rrs.entity.bo.StockAdjustStepBo;
import com.znty.rrs.entity.bo.StockInfoBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.RoleBo;
import com.znty.rrs.entity.bo.UserBo;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.entity.stockpooladjust.StockAdjustCheckDto;
import com.znty.rrs.entity.stockpooladjust.StockAdjustCheckReq;
import com.znty.rrs.entity.stockpooladjust.StockAdjustSubmitDto;
import com.znty.rrs.entity.stockpooladjust.StockInfoDto;
import com.znty.rrs.entity.stockpooladjusthistory.StockIndustryOptionDto;
import com.znty.rrs.entity.stockpooladjust.StockPoolAdjustReq;
import com.znty.rrs.entity.stockpooladjust.StockPoolAdjustSubmitReq;
import com.znty.rrs.entity.stockpooladjust.StockPoolDto;
import com.znty.rrs.entity.stockpooladjust.StockPoolStatusDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.StockPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
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
import java.util.Objects;
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

/**
 * 校验和提交股票池调入调出申请
 *
 * 为页面和 Excel 导入检查股票市场、池状态与调库规则，返回可选的审批流程
 * 提交时保存调库日志、审批步骤和材料，直通项及 {@link StockPoolAdjustFlowService} 提交的终审通过项复核后更新池状态
 */
@Service
public class StockPoolAdjustService {
    /** 股票调库附件关联表 */
    private static final String STOCK_ADJUST_LOG_TABLE = "ip_adjust_log_stock";

    /** 股票终审报告归集服务 */
    @Resource
    private ReportService reportService;

    /** 股票品种编码 */
    private static final String STOCK_VARIETY_TOKEN = "\"stock\"";

    /** 启用状态 */
    private static final String ENABLED = "enabled";

    /** 手工调整项 */
    private static final String ITEM_MANUAL = "manual";

    /** 联动调整项 */
    private static final String ITEM_LINKAGE = "linkage";

    /** 互斥调整项 */
    private static final String ITEM_MUTEX = "mutex";

    /** 股票池调整数据访问组件 */
    @Resource
    private StockPoolAdjustMapper stockPoolAdjustMapper;

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

    /**
     * 分页查询有效股票
     *
     * 执行顺序：
     * 1. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 2. 将本页记录和查询总条数一起返回
     *
     * @param req 股票代码、名称、类型及分页条件
     * @return 分页股票信息列表
     */
    public PageResult<StockInfoDto> queryStockPage(StockPoolAdjustReq req) {
        // 在查询股票列表前设置分页条件
        Page<StockInfoDto> page = PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<StockInfoDto> records = stockPoolAdjustMapper.queryStockPage(req);
        return new PageResult<>(records, page.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 查询实际股票行业选项
     *
     * 执行顺序：
     * 1. 查询调整历史中出现的股票行业选项
     *
     * @return 股票行业选项列表
     */
    public List<StockIndustryOptionDto> queryIndustryList() {
        return stockPoolAdjustMapper.queryIndustryList();
    }

    /**
     * 查询有效股票产品类型
     *
     * 执行顺序：
     * 1. 读取已启用的股票产品类型，供调库页面查询候选股票
     *
     * @return 证券类型选项列表
     */
    public List<SecurityTypeOptionDto> queryStockTypeList() {
        return stockPoolAdjustMapper.queryStockTypeList();
    }

    /**
     * 查询股票只读基础信息
     *
     * 执行顺序：
     * 1. 校验股票代码并读取股票基础信息
     * 2. 查询股票只读基础信息
     *
     * @param req 需携带股票代码的查询条件
     * @return 股票只读基础信息
     */
    public StockInfoDto queryStockDetail(StockPoolAdjustReq req) {
        // 校验股票代码并读取股票基础信息
        StockInfoBo stock = requireStock(req.getStockCode(), false);
        StockInfoDto dto = new StockInfoDto();
        BeanUtils.copyProperties(stock, dto);
        return dto;
    }

    /**
     * 查询当前用户可调整的股票池
     *
     * 执行顺序：
     * 1. 校验股票可调整状态并读取股票信息
     * 2. 保留支持该股票市场的投资池及其祖先节点
     * 3. 按调入和调出方向收集投资池互斥关系
     * 4. 组装前端展示所需的池信息、当前数量及互斥池 ID
     *
     * @param req 需携带股票代码和当前用户 ID 的查询条件
     * @return 当前用户可调整的股票池列表
     */
    public List<StockPoolDto> queryAdjustPoolList(StockPoolAdjustReq req) {
        // 1. 校验股票可调整状态并读取股票信息
        StockInfoBo stock = requireStock(req.getStockCode(), true);
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (!AdminUserIdUtil.isAdminUser(req.getCurrentUserId())) {
            // 解析用户 ID 并按调整权限过滤可见投资池
            allPools = filterPoolsByPermission(allPools, parseUserId(req.getCurrentUserId()));
        }
        // 2. 保留支持该股票市场的投资池及其祖先节点
        allPools = retainStockPoolsAndAncestors(allPools, stock.getMarketCode());
        Map<Long, Integer> countMap = stockPoolAdjustMapper.queryPoolCurrentCountList().stream()
                .collect(Collectors.toMap(StockPoolDto::getId, StockPoolDto::getCurrentCount));
        Map<Long, List<Long>> inMutexMap = new HashMap<>();
        Map<Long, List<Long>> outMutexMap = new HashMap<>();
        // 3. 按调入和调出方向收集投资池互斥关系
        for (PoolRelationBo relation : stockPoolAdjustMapper.queryAllPoolRelationList()) {
            if (RelationType.IN_MUTEX.getCode().equals(relation.getRelationType())) {
                inMutexMap.computeIfAbsent(relation.getPoolId(), key -> new ArrayList<>())
                        .add(relation.getRelationPoolId());
            } else if (RelationType.OUT_MUTEX.getCode().equals(relation.getRelationType())) {
                outMutexMap.computeIfAbsent(relation.getPoolId(), key -> new ArrayList<>())
                        .add(relation.getRelationPoolId());
            }
        }
        List<StockPoolDto> result = new ArrayList<>();
        // 4. 组装前端展示所需的池信息、当前数量及互斥池 ID
        for (InvestmentPoolBo pool : allPools) {
            StockPoolDto dto = new StockPoolDto();
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
     * 查询股票当前所在池，不查询发行主体或债券池
     *
     * 执行顺序：
     * 1. 校验股票代码及股票记录是否存在
     * 2. 用投资池全路径名称补全股票当前池展示信息
     *
     * @param req 需携带股票代码的查询条件
     * @return 股票当前池状态列表
     */
    public List<StockPoolStatusDto> queryStockPoolStatus(StockPoolAdjustReq req) {
        String stockCode = req.getStockCode();
        // 1. 校验股票代码及股票记录是否存在
        requireStock(stockCode, false);
        List<StockPoolStatusDto> statuses = stockPoolAdjustMapper.queryStockPoolStatusList(stockCode.trim());
        Map<Long, String> fullNameMap = investmentPoolService.queryPoolFullNameMap();
        // 2. 用投资池全路径名称补全股票当前池展示信息
        for (StockPoolStatusDto status : statuses) {
            String fullName = fullNameMap.get(status.getTargetPoolId());
            if (fullName != null && !fullName.isEmpty()) {
                status.setPoolName(fullName);
            }
        }
        return statuses;
    }

    /**
     * 查询股票调库记录，详情页按批次展示完整上下文
     *
     * 执行顺序：
     * 1. 检查股票代码不能为空
     * 2. 查询调库记录列表（全量，不分页）
     *
     * @param req 需携带股票代码的查询条件
     * @return 股票调库记录列表
     */
    public List<StockAdjustLogBo> queryAdjustLogList(StockPoolAdjustReq req) {
        if (req.getStockCode() == null || req.getStockCode().trim().isEmpty()) {
            throw new BizException("股票代码不能为空");
        }
        return stockPoolAdjustMapper.queryAdjustLogList(req);
    }

    /**
     * 查询股票调库审批步骤，批次号优先于单条记录 ID
     *
     * 执行顺序：
     * 1. 检查查询请求不能为空
     * 2. 查询指定调库记录或同批次共用流程的步骤列表
     *
     * @param req 需携带批次号或调库记录 ID 的查询条件
     * @return 股票调库审批步骤列表
     */
    public List<StockAdjustStepBo> queryAdjustStepList(StockPoolAdjustReq req) {
        if (req == null) {
            throw new BizException("查询请求不能为空");
        }
        if (req.getAdjustLogId() == null
                && (req.getAdjustBatchNo() == null || req.getAdjustBatchNo().trim().isEmpty())) {
            throw new BizException("调库记录 ID 或批次号不能为空");
        }
        return stockPoolAdjustMapper.queryAdjustStepList(req.getAdjustLogId(), req.getAdjustBatchNo());
    }

    /**
     * 股票调库最终审批落池前，按当前股票状态重新校验批次中的全部调整项
     *
     * 执行顺序：
     * 1. 锁定待落池股票主档，防止同股票并发调整
     * 2. 使用已锁定的当前主档复核股票状态
     * 3. 锁定股票主档后确认整组目标池仍存在
     *
     * @param logs 同一股票调库批次的日志列表
     */
    public void recheckBeforeFinalApproval(List<StockAdjustLogBo> logs) {
        if (logs == null || logs.isEmpty()) {
            throw new BizException("股票调库批次记录不存在");
        }
        StockAdjustLogBo firstLog = logs.get(0);
        // 1. 锁定待落池股票主档，防止同股票并发调整
        Map<String, StockInfoBo> lockedStocks = lockStockList(Collections.singletonList(firstLog.getStockCode()));
        // 2. 使用已锁定的当前主档复核股票状态
        StockInfoBo stock = requireLockedStock(firstLog.getStockCode(), lockedStocks);
        // 3. 锁定股票主档后确认整组目标池仍存在
        checkTargetPoolsExist(logs.stream().map(StockAdjustLogBo::getTargetPoolId).collect(Collectors.toList()));
        Map<Long, InvestmentPoolBo> poolMap = investmentPoolMapper.queryPoolList().stream()
                .filter(pool -> pool.getId() != null)
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool));
        Set<Long> currentPoolIds = new HashSet<>(stockPoolAdjustMapper.queryStockCurrentPoolIdList(stock.getStockCode()));
        List<PoolRelationBo> relations = stockPoolAdjustMapper.queryAllPoolRelationList();
        Set<Long> sourcePoolIds = new HashSet<>(currentPoolIds);
        logs.stream().filter(log -> AdjustMode.IN.getCode().equals(log.getAdjustMode()))
                .forEach(log -> sourcePoolIds.add(log.getTargetPoolId()));
        for (StockAdjustLogBo log : logs) {
            if (log == null || !stock.getStockCode().equals(log.getStockCode())) {
                throw new BizException("股票调库批次包含无效记录");
            }
            // 确认本条调库记录的目标投资池仍然存在
            InvestmentPoolBo pool = requirePool(poolMap, log.getTargetPoolId());
            // 重新解析调整人并查询池权限，防止审批期间权限变化后仍落池
            if (!AdminUserIdUtil.isAdminUser(log.getAdjusterId())
                    // 查询用户直接或通过角色拥有调整权限的池，再解析数字用户 ID
                    && !queryAdjustablePoolIds(parseUserId(log.getAdjusterId())).contains(pool.getId())) {
                throw new BizException("调整人已无目标投资池调整权限：" + pool.getPoolName());
            }
            // 按当前池状态复核本条调整规则，并排除正在审批的批次
            List<String> failures = validatePoolAdjust(stock, pool, log.getAdjustMode(), currentPoolIds,
                    relations, log.getAdjustBatchNo(), false, sourcePoolIds);
            if (!failures.isEmpty()) {
                throw new BizException(pool.getPoolName() + "：" + String.join("；", failures));
            }
            // 关系项继承主项流程；单笔和批量手工主项均强制报告，已绑定来源须有效
            String restriction = ("手工调整".equals(log.getAdjustType()) || "手动批量调整".equals(log.getAdjustType())
                    || "Excel导入".equals(log.getAdjustType()) || "Excel清空".equals(log.getAdjustType()))
                    ? (AdjustMode.IN.getCode().equals(log.getAdjustMode()) ? pool.getInReportRestriction() : pool.getOutReportRestriction()) : "none";
            sysAttachmentService.validateStockBoundReports(log.getId(), stock.getStockCode(), restriction);
        }
    }

    /**
     * 将股票调库审批通过的整批日志应用到当前股票池状态
     *
     * 执行顺序：
     * 1. 逐项新增股票投资池状态
     * 2. 删除股票投资池状态
     *
     * @param logs 已通过审批的同批次调库日志
     */
    public void applyPoolStatusChanges(List<StockAdjustLogBo> logs) {
        for (StockAdjustLogBo log : logs) {
            if (!AuditStatus.APPROVED.getCode().equals(log.getAuditStatus())) {
                throw new BizException("只有终审通过的股票记录允许落池");
            }
            if (AdjustMode.IN.getCode().equals(log.getAdjustMode())) {
                // 调入时新增股票当前池状态
                if (stockPoolAdjustMapper.addStockPoolStatus(log) == 0) {
                    throw new BizException("股票入池状态写入失败");
                }
            } else if (AdjustMode.OUT.getCode().equals(log.getAdjustMode())) {
                // 调出时移除股票当前池状态
                if (stockPoolAdjustMapper.deleteStockPoolStatus(log.getStockCode(), log.getTargetPoolId()) == 0) {
                    throw new BizException("股票当前池状态已发生变化，请刷新后重试");
                }
            } else {
                throw new BizException("股票调库方向不合法");
            }
        }
    }

    /**
     * 终审成功后原子更新整组记录、成员与内部报告
     *
     * 执行顺序：
     * 1. 落池前复核当前主档、投资池规则与容量，来源只读取已通过状态
     * 2. 仅终审通过记录允许应用到池状态
     *
     * @param batchNo 当前调库批次号
     */
    public void finishApprovedBatch(String batchNo) {
        List<StockAdjustLogBo> logs = stockPoolAdjustMapper.queryAdjustLogListForAudit(batchNo);
        // 1. 落池前复核当前主档、投资池规则与容量，来源只读取已通过状态
        recheckBeforeFinalApproval(logs);
        if (stockPoolAdjustMapper.editAdjustLogAuditStatus(batchNo, AuditStatus.APPROVED.getCode()) != logs.size()) {
            throw new BizException("股票调库申请状态已发生变化，请刷新后重试");
        }
        for (StockAdjustLogBo log : logs) {
            log.setAuditStatus(AuditStatus.APPROVED.getCode());
        }
        // 2. 仅终审通过记录允许应用到池状态
        applyPoolStatusChanges(logs);
        reportService.addInternalStockReportsOnFinish(logs);
    }

    /**
     * 查询并确认整组目标池仍存在
     *
     * 执行顺序：
     * 1. 目标投资池不存在或已删除时终止处理
     *
     * @param poolIds 待处理的投资池 ID 列表
     */
    private void checkTargetPoolsExist(List<Long> poolIds) {
        List<Long> ordered = poolIds.stream().filter(id -> id != null).distinct().sorted().collect(Collectors.toList());
        if (ordered.size() == 0 || investmentPoolMapper.queryExistingPoolIdList(ordered).size() != ordered.size()) {
            throw new BizException("目标投资池不存在或已删除");
        }
    }

    /**
     * 校验股票行业及最新评级准入，未知配置显式报错
     *
     * 执行顺序：
     * 1. 先核对股票行业与目标池配置
     * 2. 逐项校验配置的准入评级，再比较股票最新评级是否在许可范围中
     *
     * @param failures 待追加阻断原因的结果列表
     * @param stock 当前股票主档
     * @param pool 目标投资池
     */
    private void validateStockAdmission(List<String> failures, StockInfoBo stock, InvestmentPoolBo pool) {
        if (pool.getIndustryCode() != null && !pool.getIndustryCode().trim().isEmpty()
                && (pool.getIndustryExponent() == null || pool.getIndustryExponent() == 0)
                && !pool.getIndustryCode().trim().equals(stock.getIndustryName())) {
            failures.add("股票行业不符合投资池行业限制");
        }
        if (pool.getGradeAstrict() == null || pool.getGradeAstrict().trim().isEmpty()) {
            return;
        }
        Set<String> allowed = new HashSet<>();
        for (String value : pool.getGradeAstrict().split(",", -1)) {
            String rating = value.trim();
            if (!StockRating.isValid(rating)) {
                throw new BizException("股票池评级准入配置不合法：" + value);
            }
            allowed.add(rating);
        }
        if (stock.getLatestRating() == null || !allowed.contains(stock.getLatestRating())) {
            failures.add("股票最新评级不符合投资池评级准入");
        }
    }

    /**
     * 股票首版支持的市场
     *
     * 执行顺序：
     * 1. 检查是否符合上海、深圳或香港市场的条件
     *
     * @param market 从主档或临时代码记录查询并校验通过的市场编码
     * @return true=上海、深圳或香港市场，false=其他市场或未提供市场
     */
    private boolean isSupportedMarket(String market) {
        return "SSE".equals(market) || "SZSE".equals(market) || "HKEX".equals(market);
    }

    /**
     * 将配置生成显式可选或不可选候选，不对未配置流程自动放行
     *
     * 执行顺序：
     * 1. 写入流程 ID、Key、类型和推荐标志，再根据已发布快照确定候选是否可用
     * 2. 为每个候选记录可选状态及原因，未配置或配置不完整的流程标为不可选
     *
     * @param options 选项列表
     * @param stock 当前股票主档
     * @param id 登记主键
     * @param key 标识
     * @param type 类型
     * @param recommended 是否将此审批流程标为推荐候选
     * @param failures 待追加阻断原因的结果列表
     */
    private void addFlowOption(List<StockAdjustCheckDto.FlowOption> options, StockInfoBo stock,
                               Long id, String key, String type, boolean recommended, List<String> failures) {
        StockAdjustCheckDto.FlowOption option = new StockAdjustCheckDto.FlowOption();
        option.setFlowId(id);
        option.setFlowKey(key);
        option.setFlowType(type);
        option.setRecommended(recommended);
        // 固定已发布的流程版本，完整性不满足时保留不可选原因
        FlowSnapshot snapshot = buildFlowSnapshot(id, stock.getStockCode());
        boolean usable = snapshot != null && key != null && key.equals(snapshot.definition.getFlowKey());
        option.setFlowName(snapshot == null ? (recommended ? "一般流程未配置" : "快速流程未配置") : snapshot.definition.getName());
        option.setMatched(usable);
        option.setSelectable(usable && failures.isEmpty());
        option.setMatchReasons(usable ? Collections.singletonList(recommended ? "默认一般审批流程" : "用户可选择快速审批流程") : Collections.emptyList());
        option.setUnmatchReasons(usable ? failures : Collections.singletonList("流程未配置、未发布或配置不完整"));
        options.add(option);
    }

    /**
     * 复核所有提交项与服务端当前联动和互斥关系一致
     *
     * 执行顺序：
     * 1. 逐项生成单条校验结果
     * 2. 联动或互斥调整项已变化时终止处理
     *
     * @param req 股票调库申请及调整明细
     * @param stock 当前股票主档
     * @param poolMap 当前投资池映射
     * @param currentPoolIds 股票当前所在的投资池 ID
     * @param relations 当前投资池关系配置
     */
    private void validateSubmittedRelations(StockPoolAdjustSubmitReq req, StockInfoBo stock,
                                             Map<Long, InvestmentPoolBo> poolMap, Set<Long> currentPoolIds,
                                             List<PoolRelationBo> relations) {
        Set<Long> sourcePoolIds = new HashSet<>(currentPoolIds);
        req.getItems().stream().filter(item -> AdjustMode.IN.getCode().equals(item.getAdjustMode()))
                .forEach(item -> sourcePoolIds.add(item.getTargetPoolId()));
        LinkedHashMap<String, StockAdjustCheckDto.CheckResultItem> expected = new LinkedHashMap<>();
        for (StockPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (!ITEM_MANUAL.equals(item.getItemTag())) {
                continue;
            }
            // 读取手工主项的分组标识，以便重算同组关系项
            String group = requireGroupKey(item);
            // 根据手工主项重算关系项，与前端传回明细逐条对照
            addCheckResult(expected, stock, item.getTargetPoolId(), item.getAdjustMode(), ITEM_MANUAL,
                    group, poolMap, currentPoolIds, relations, sourcePoolIds);
            StockAdjustCheckReq.CheckItem manual = new StockAdjustCheckReq.CheckItem();
            manual.setTargetPoolId(item.getTargetPoolId());
            manual.setAdjustMode(item.getAdjustMode());
            // 按当前配置展开该手工主项的联动和互斥明细
            expandRelationItems(expected, stock, manual, group, poolMap, currentPoolIds, relations, sourcePoolIds);
        }
        if (expected.size() != req.getItems().size()) {
            throw new BizException("联动或互斥调整项已变化，请重新校验");
        }
        for (StockPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            // 目标池、方向、来源标签与组均须保持服务端校验结果
            StockAdjustCheckDto.CheckResultItem expectedItem = expected.get(itemKey(item.getTargetPoolId(), item.getAdjustMode()));
            // 核实分组标识与当前规则重算出的关系项一致
            if (expectedItem == null || !item.getItemTag().equals(expectedItem.getItemTag())
                    // 获取非空分组标识
                    || !requireGroupKey(item).equals(expectedItem.getAdjustGroupKey())) {
                throw new BizException("联动或互斥调整项不符合当前配置，请重新校验");
            }
        }
    }

    /**
     * 执行股票调库校验并展开联动、互斥项
     *
     * 执行顺序：
     * 1. 校验股票可调整状态并读取股票信息
     * 2. 普通用户解析真实用户 ID 后查询可调整池，管理员沿用全池范围
     *
     * @param req 股票代码及手工选择的目标池调整项
     * @return 股票调库校验结果及联动、互斥项
     */
    public StockAdjustCheckDto checkAdjust(StockAdjustCheckReq req) {
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("至少选择一个目标投资池");
        }
        // 1. 校验股票可调整状态并读取股票信息
        StockInfoBo stock = requireStock(req.getStockCode(), true);
        List<InvestmentPoolBo> pools = investmentPoolMapper.queryPoolList();
        Map<Long, InvestmentPoolBo> poolMap = pools.stream()
                .filter(pool -> pool.getId() != null)
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool));
        Set<Long> currentPoolIds = new HashSet<>(stockPoolAdjustMapper.queryStockCurrentPoolIdList(stock.getStockCode()));
        List<PoolRelationBo> relations = stockPoolAdjustMapper.queryAllPoolRelationList();
        LinkedHashMap<String, StockAdjustCheckDto.CheckResultItem> resultMap = new LinkedHashMap<>();
        Set<Long> sourcePoolIds = new HashSet<>(currentPoolIds);
        req.getItems().stream().filter(item -> item != null && AdjustMode.IN.getCode().equals(item.getAdjustMode()))
                .forEach(item -> sourcePoolIds.add(item.getTargetPoolId()));
        String currentUserId = StockQueryFilterHelper.requireUserId(req.getCurrentUserId());
        // 2. 普通用户解析真实用户 ID 后查询可调整池，管理员沿用全池范围
        Set<Long> allowed = AdminUserIdUtil.isAdminUser(currentUserId) ? poolMap.keySet()
                // 查询用户直接或通过角色拥有调整权限的池，再解析数字用户 ID
                : queryAdjustablePoolIds(parseUserId(currentUserId));
        int groupIndex = 1;
        for (StockAdjustCheckReq.CheckItem item : req.getItems()) {
            if (item == null || item.getTargetPoolId() == null) {
                throw new BizException("目标投资池不能为空");
            }
            String groupKey = "stock-group-" + groupIndex++;
            // 校验并记录手工选择的目标池调整项
            addCheckResult(resultMap, stock, item.getTargetPoolId(), item.getAdjustMode(), ITEM_MANUAL,
                    groupKey, poolMap, currentPoolIds, relations, sourcePoolIds);
            // 根据目标池关系补充联动和互斥调整项
            expandRelationItems(resultMap, stock, item, groupKey, poolMap, currentPoolIds, relations, sourcePoolIds);
        }
        for (StockAdjustCheckDto.CheckResultItem item : resultMap.values()) {
            if (!allowed.contains(item.getTargetPoolId())) {
                item.getFailReasons().add("当前用户没有投资池调整权限");
                item.setCanAdjust(false);
                for (StockAdjustCheckDto.FlowOption option : item.getFlowOptions()) {
                    option.setSelectable(false);
                }
            }
        }
        StockAdjustCheckDto dto = new StockAdjustCheckDto();
        dto.setItems(new ArrayList<>(resultMap.values()));
        return dto;
    }

    /**
     * 校验 Excel 来源行，复用股票规则并限定双权限、无报告及人工一般流程
     *
     * 执行顺序：
     * 1. 复用单笔准入、调整权限及关系展开
     * 2. 读取最新股票信息以固定流程候选
     *
     * @param req 股票代码及手工选择的目标池调整项
     * @param adjusterId 发起人 ID
     * @return 股票调库校验
     */
    public StockAdjustCheckDto checkExcelImportAdjust(StockAdjustCheckReq req, String adjusterId) {
        if (req == null) {
            throw new BizException("股票导入校验请求不能为空");
        }
        req.setCurrentUserId(adjusterId);
        // 1. 复用单笔准入、调整权限及关系展开
        StockAdjustCheckDto result = checkAdjust(req);
        // 2. 读取最新股票信息以固定流程候选
        StockInfoBo stock = requireStock(req.getStockCode(), true);
        Map<Long, InvestmentPoolBo> pools = investmentPoolMapper.queryPoolList().stream()
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool));
        for (StockAdjustCheckDto.CheckResultItem item : result.getItems()) {
            List<String> failures = new ArrayList<>(item.getFailReasons());
            // 关系项使用实际目标池，不采信客户端名称
            InvestmentPoolBo pool = requirePool(pools, item.getTargetPoolId());
            try {
                // 每个实际目标池均要求 Excel 导入权限
                validateExcelImportPermission(adjusterId, pool.getId());
                if (ITEM_MANUAL.equals(item.getItemTag())) {
                    StockPoolAdjustSubmitReq.AdjustItem report = new StockPoolAdjustSubmitReq.AdjustItem();
                    report.setItemTag(ITEM_MANUAL);
                    report.setAdjustMode(item.getAdjustMode());
                    // 没有报告入口仍完整校验目标池报告限制
                    validateReportRestriction(report, pool, stock.getStockCode());
                }
            } catch (BizException exception) {
                failures.add(exception.getMessage());
            }
            boolean inbound = AdjustMode.IN.getCode().equals(item.getAdjustMode());
            Long flowId = inbound ? pool.getInFlowId() : pool.getOutFlowId();
            String flowKey = inbound ? pool.getInFlowKey() : pool.getOutFlowKey();
            String type = inbound ? FlowType.NORMAL_INBOUND.getCode() : FlowType.NORMAL_OUTBOUND.getCode();
            List<StockAdjustCheckDto.FlowOption> options = new ArrayList<>();
            // 快速配置不参与 Excel 可行性判断
            addFlowOption(options, stock, flowId, flowKey, type, true, failures);
            try {
                // 排除未发布、错误 Key 和没有人工待办的一般流程
                requireExcelFlow(flowId, flowKey, stock.getStockCode());
            } catch (BizException exception) {
                failures.add(exception.getMessage());
            }
            item.setFailReasons(failures);
            item.setCanAdjust(failures.isEmpty());
            for (StockAdjustCheckDto.FlowOption option : options) {
                option.setSelectable(option.isSelectable() && failures.isEmpty());
                if (!failures.isEmpty()) {
                    option.setUnmatchReasons(new ArrayList<>(failures));
                }
            }
            item.setFlowOptions(options);
        }
        return result;
    }

    /**
     * 原子提交服务器导入来源组，关系开关只由持久化 optionJson 提供
     *
     * 执行顺序：
     * 1. 整批主档按主键统一锁定，不允许边写边校验
     * 2. 统一检查所有来源组的目标池是否存在
     *
     * @param requests 各股票完整的手工及关系项申请
     * @param allowLinkMutex 是否启用联动和互斥调整
     * @return 股票调库提交列表
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public List<StockAdjustSubmitDto> addExcelImportAdjustLogList(List<StockPoolAdjustSubmitReq> requests,
                                                                boolean allowLinkMutex) {
        if (requests == null || requests.isEmpty()) {
            throw new BizException("至少提交一个股票 Excel 导入分组");
        }
        Set<String> targets = new HashSet<>();
        for (StockPoolAdjustSubmitReq req : requests) {
            // 写入前校验专属渠道类型与必填字段
            validateSubmitRequest(req, false, true);
            for (StockPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
                if (!targets.add(req.getStockCode() + "|" + item.getTargetPoolId())) {
                    throw new BizException("不同来源组不能调整相同股票和投资池");
                }
                if ((item.getReportFileIndexes() != null && !item.getReportFileIndexes().isEmpty())
                        || (item.getMaterialFileIndexes() != null && !item.getMaterialFileIndexes().isEmpty())
                        || (item.getReportSourceAttachmentIds() != null && !item.getReportSourceAttachmentIds().isEmpty())
                        || (item.getMaterialSourceAttachmentIds() != null && !item.getMaterialSourceAttachmentIds().isEmpty())) {
                    throw new BizException("股票 Excel 导入不支持报告或材料附件");
                }
            }
        }
        // 1. 整批主档按主键统一锁定，不允许边写边校验
        Map<String, StockInfoBo> stocks = lockStockList(requests.stream()
                .map(StockPoolAdjustSubmitReq::getStockCode).collect(Collectors.toList()));
        // 2. 统一检查所有来源组的目标池是否存在
        checkTargetPoolsExist(requests.stream().flatMap(req -> req.getItems().stream())
                .map(StockPoolAdjustSubmitReq.AdjustItem::getTargetPoolId).collect(Collectors.toList()));
        List<PreparedSubmit> prepared = new ArrayList<>();
        for (StockPoolAdjustSubmitReq req : requests) {
            // 重算最新可提交集合，拒绝漏传、伪造或失效关系项
            validateExcelRelations(req, allowLinkMutex);
            // 全部请求复核完成前不写日志、步骤或池状态
            prepared.add(prepareSubmit(req, stocks, false, true));
        }
        List<StockAdjustSubmitDto> results = new ArrayList<>();
        for (PreparedSubmit item : prepared) {
            // Excel 不允许初始步骤直达结束并自动落池
            results.add(submitPrepared(item, null, true));
        }
        return results;
    }

    /**
     * 核对本来源有效关系集合，失败关系不额外阻断成功主项
     *
     * 执行顺序：
     * 1. 主档锁内重新复核双权限、报告和一般流程
     * 2. 服务端来源主项决定全组标识
     *
     * @param req 股票调库申请及调整明细
     * @param allowRelations 是否保留清空项产生的联动和互斥项
     */
    private void validateExcelRelations(StockPoolAdjustSubmitReq req, boolean allowRelations) {
        List<StockPoolAdjustSubmitReq.AdjustItem> manual = req.getItems().stream()
                .filter(item -> ITEM_MANUAL.equals(item.getItemTag())).collect(Collectors.toList());
        if (manual.size() != 1) {
            throw new BizException("股票 Excel 每个来源组必须恰有一条主项");
        }
        StockPoolAdjustSubmitReq.AdjustItem primary = manual.get(0);
        StockAdjustCheckReq check = new StockAdjustCheckReq();
        check.setStockCode(req.getStockCode());
        StockAdjustCheckReq.CheckItem source = new StockAdjustCheckReq.CheckItem();
        source.setTargetPoolId(primary.getTargetPoolId());
        source.setAdjustMode(primary.getAdjustMode());
        check.setItems(Collections.singletonList(source));
        // 1. 主档锁内重新复核双权限、报告和一般流程
        StockAdjustCheckDto result = checkExcelImportAdjust(check, req.getAdjusterId());
        StockAdjustCheckDto.CheckResultItem main = result.getItems().stream()
                .filter(item -> ITEM_MANUAL.equals(item.getItemTag())).findFirst()
                .orElseThrow(() -> new BizException("股票导入复核缺少主项"));
        if (!main.isCanAdjust()) {
            throw new BizException(String.join("；", main.getFailReasons()));
        }
        Map<String, StockAdjustCheckDto.CheckResultItem> expected = result.getItems().stream()
                .filter(item -> item.isCanAdjust() && (allowRelations || ITEM_MANUAL.equals(item.getItemTag())))
                .collect(Collectors.toMap(item -> item.getTargetPoolId() + "|" + item.getAdjustMode() + "|" + item.getItemTag(), item -> item));
        Set<String> actual = new HashSet<>();
        // 2. 服务端来源主项决定全组标识
        String group = requireGroupKey(primary);
        for (StockPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            String key = item.getTargetPoolId() + "|" + item.getAdjustMode() + "|" + item.getItemTag();
            if (!group.equals(item.getAdjustGroupKey()) || !expected.containsKey(key) || !actual.add(key)) {
                throw new BizException("股票导入关系项已变化或来源不符，请重新校验");
            }
        }
        if (actual.size() != expected.size()) {
            throw new BizException("股票导入有效关系项已变化，请重新校验");
        }
        boolean allowed = main.getFlowOptions().stream().anyMatch(option -> option.isSelectable()
                && Objects.equals(option.getFlowId(), primary.getFlowId())
                && Objects.equals(option.getFlowKey(), primary.getFlowKey())
                && Objects.equals(option.getFlowType(), primary.getFlowType()));
        if (!allowed) {
            throw new BizException("股票 Excel 只能使用目标池一般审批流程");
        }
    }

    /**
     * 统一校验 Excel 人员或角色权限，管理员沿用既有口径
     *
     * 执行顺序：
     * 1. 校验操作人并读取实际角色
     * 2. 查询用户角色ID列表
     * 3. 按类型查询权限列表
     *
     * @param userId 当前用户 ID 字符串
     * @param poolId 目标投资池 ID
     */
    void validateExcelImportPermission(String userId, Long poolId) {
        if (AdminUserIdUtil.isAdminUser(userId)) {
            return;
        }
        // 校验操作人并读取实际角色
        Long id = parseUserId(userId);
        Set<Long> roles = new HashSet<>(investmentPoolMapper.queryUserRoleIdList(id));
        for (PoolPermissionBo permission : investmentPoolMapper.queryPermissionListByType(PermissionType.EXCEL_IMPORTABLE.getCode())) {
            if (poolId.equals(permission.getPoolId()) && permission.getHandlerId() != null
                    && ((HandlerType.USER.getCode().equals(permission.getHandlerType()) && id.equals(permission.getHandlerId()))
                    || (HandlerType.ROLE.getCode().equals(permission.getHandlerType()) && roles.contains(permission.getHandlerId())))) {
                return;
            }
        }
        throw new BizException("当前用户无权对投资池[" + poolId + "]进行 Excel 导入");
    }

    /**
     * 检查 Excel 发布版本及人工审批，单笔快速路径不受影响
     *
     * 执行顺序：
     * 1. 复用既有股票快照，不改变单笔的快照可用规则
     * 2. 从开始节点复核实际审批路径
     *
     * @param id 登记主键
     * @param key 标识
     * @param stockCode 当前股票代码，用于定位当前股票
     * @return 已加载的审批流程快照
     */
    private FlowSnapshot requireExcelFlow(Long id, String key, String stockCode) {
        // 1. 复用既有股票快照，不改变单笔的快照可用规则
        FlowSnapshot snapshot = buildFlowSnapshot(id, stockCode);
        if (snapshot == null || key == null || !key.equals(snapshot.definition.getFlowKey())) {
            throw new BizException("目标池一般审批流程未配置、未发布或配置不完整");
        }
        // 2. 从开始节点复核实际审批路径
        FlowNodeBo node = findNode(snapshot.nodes, NodeType.START.getCode());
        Set<Long> visited = new HashSet<>();
        while (node != null && node.getId() != null && visited.add(node.getId())) {
            // 沿实际提交路由查找人工待办，孤立或仅驳回可达节点不算初始审批
            node = findNextNode(snapshot, node);
            if (node == null || NodeType.END.getCode().equals(node.getNodeType())) {
                break;
            }
            if (!NodeType.APPROVAL.getCode().equals(node.getNodeType())) {
                continue;
            }
            NodeApprovalConfigBo config = snapshot.configMap.get(node.getId());
            // 自动和 O32 节点不算人工待办
            boolean auto = isSystemAutoApproval(config);
            // 发起人提交节点不算实际审批
            boolean submit = config != null && ApprovalStrategy.INITIATOR.getCode().equals(config.getApprovalStrategy())
                    // 判断流程节点是否存在指定动作的后续连线
                    && hasOutgoingRouteAction(snapshot, node, ProcessAction.SUBMIT.getCode());
            if (!auto && !submit) {
                return snapshot;
            }
        }
        throw new BizException("股票 Excel 一般流程必须包含实际人工审批节点");
    }

    /**
     * 提交不含本地文件的股票调库申请
     *
     * 执行顺序：
     * 1. 提交股票调库日志与初始步骤
     *
     * @param req 股票调库申请及调整明细
     * @return 股票调库申请提交结果
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public StockAdjustSubmitDto addAdjustLog(StockPoolAdjustSubmitReq req) {
        // 按无本地上传文件的方式保存股票调库申请
        return addAdjustLogInternal(req, null);
    }

    /**
     * 提交含 multipart 文件的股票调库申请
     *
     * 执行顺序：
     * 1. 解析前端 FormData 字段 {@code originalFileNameListJson}（JSON 数组字符串）
     * 2. 创建本次提交的文件上下文（无前端原始文件名列表）
     * 3. 将上传文件随股票调库申请一并提交
     *
     * @param req 股票调库申请及调整明细
     * @param files 本次上传的附件文件
     * @param originalFileNameListJson 前端提供的原始文件名列表
     * @return 股票调库申请提交结果
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public StockAdjustSubmitDto addAdjustLog(StockPoolAdjustSubmitReq req, List<MultipartFile> files,
                                             String originalFileNameListJson) {
        List<String> names = sysAttachmentService.parseOriginalFileNameListJson(originalFileNameListJson);
        SysAttachmentService.SubmissionFiles submissionFiles = sysAttachmentService.createSubmissionFiles(
                files, req.getAdjusterId(), names);
        // 将上传文件随股票调库申请一并提交
        return addAdjustLogInternal(req, submissionFiles);
    }

    /**
     * 原子提交股票批量申请，锁定股票主档并完成目标池和业务复核后才写入
     *
     * 执行顺序：
     * 1. 保证整批本地文件和其他材料共享，同股票研究来源覆盖完整关系组
     * 2. 一次按主档主键顺序锁定整批股票
     * 3. 逐组复核前统一检查全批目标池是否存在
     *
     * @param requests 各股票完整的手工及关系项申请
     * @param submissionFiles 整批共用的物理上传文件上下文
     * @return 各股票独立的批次及调库日志 ID
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public List<StockAdjustSubmitDto> addBatchAdjustLogList(List<StockPoolAdjustSubmitReq> requests,
                                                           SysAttachmentService.SubmissionFiles submissionFiles) {
        if (requests == null || requests.isEmpty()) {
            throw new BizException("至少提交一组股票批量调整申请");
        }
        Set<String> codes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (StockPoolAdjustSubmitReq req : requests) {
            if (req == null || req.getStockCode() == null || req.getStockCode().trim().isEmpty()) {
                throw new BizException("股票批量调整的股票代码不能为空");
            }
            if (!codes.add(req.getStockCode().trim())) {
                throw new BizException("股票批量调整存在重复股票：" + req.getStockCode());
            }
            // 批量渠道由服务端赋值，单笔公开入口仍只接收手工调整
            req.setAdjustType("手动批量调整");
            // 在取得主档锁前确认每只股票提交字段合法
            validateSubmitRequest(req, true);
            int manualCount = 0;
            for (StockPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
                if (ITEM_MANUAL.equals(item.getItemTag())) {
                    manualCount++;
                    String expectedType = AdjustMode.IN.getCode().equals(item.getAdjustMode())
                            ? FlowType.NORMAL_INBOUND.getCode() : FlowType.NORMAL_OUTBOUND.getCode();
                    if (!expectedType.equals(item.getFlowType())) {
                        throw new BizException("股票批量调整只能使用一般审批流程：" + req.getStockCode());
                    }
                } else if (item.getFlowType() != null
                        && !FlowType.NORMAL_INBOUND.getCode().equals(item.getFlowType())
                        && !FlowType.NORMAL_OUTBOUND.getCode().equals(item.getFlowType())) {
                    throw new BizException("股票批量关系项不能使用快速或批量审批流程：" + req.getStockCode());
                }
                if (submissionFiles == null && ((item.getReportFileIndexes() != null && !item.getReportFileIndexes().isEmpty())
                        || (item.getMaterialFileIndexes() != null && !item.getMaterialFileIndexes().isEmpty()))) {
                    throw new BizException("股票批量申请包含本地文件索引，请通过 multipart 同时上传对应文件");
                }
                // 在任何日志写入前复核上传索引与材料来源，保持全批预检无副作用
                sysAttachmentService.validateSubmissionFileIndexes(submissionFiles, item.getReportFileIndexes());
                sysAttachmentService.validateSubmissionFileIndexes(submissionFiles, item.getMaterialFileIndexes());
                sysAttachmentService.validateCreditReportSources(item.getMaterialSourceAttachmentIds(), false);
            }
            if (manualCount != 1) {
                throw new BizException("股票批量每组必须包含且仅包含一条手工调整项：" + req.getStockCode());
            }
        }
        // 1. 保证整批本地文件和其他材料共享，同股票研究来源覆盖完整关系组
        validateBatchSharedMaterials(requests);
        // 2. 一次按主档主键顺序锁定整批股票
        Map<String, StockInfoBo> lockedStocks = lockStockList(new ArrayList<>(codes));
        // 3. 逐组复核前统一检查全批目标池是否存在
        checkTargetPoolsExist(requests.stream().flatMap(req -> req.getItems().stream())
                .map(StockPoolAdjustSubmitReq.AdjustItem::getTargetPoolId).collect(Collectors.toList()));
        List<PreparedSubmit> preparedRequests = new ArrayList<>();
        for (StockPoolAdjustSubmitReq req : requests) {
            // 先完成所有分组的股票、关系、权限、报告和一般流程复核
            preparedRequests.add(prepareSubmit(req, lockedStocks, true));
        }
        List<StockAdjustSubmitDto> results = new ArrayList<>();
        for (PreparedSubmit prepared : preparedRequests) {
            // 复用股票单笔日志、流程快照、步骤及附件保存行为
            results.add(submitPrepared(prepared, submissionFiles));
        }
        return results;
    }

    /**
     * 校验批量共享材料和股票分组引用一致，避免关系项材料缺失或串组
     *
     * 执行顺序：
     * 1. 以第一条明细为整批共享材料的基准，以每组第一条明细为个股材料的基准
     * 2. 逐条核对共享和个股材料的附件索引，发现不一致时拒绝提交
     *
     * @param requests 各股票完整的手工及关系项申请
     */
    private void validateBatchSharedMaterials(List<StockPoolAdjustSubmitReq> requests) {
        StockPoolAdjustSubmitReq.AdjustItem shared = requests.get(0).getItems().get(0);
        for (StockPoolAdjustSubmitReq req : requests) {
            StockPoolAdjustSubmitReq.AdjustItem stockMaterial = req.getItems().get(0);
            for (StockPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
                // 按集合比较整批本地上传和其他材料，允许引用顺序不同
                if (!sameAttachmentSelections(shared.getReportFileIndexes(), item.getReportFileIndexes())
                        // 空列表与未选择同义，附件来源及索引继续沿用公共服务的去重口径
                        || !sameAttachmentSelections(shared.getMaterialFileIndexes(), item.getMaterialFileIndexes())
                        // 空列表与未选择同义，附件来源及索引继续沿用公共服务的去重口径
                        || !sameAttachmentSelections(shared.getMaterialSourceAttachmentIds(), item.getMaterialSourceAttachmentIds())) {
                    throw new BizException("股票批量申请的本地文件和其他材料必须整批共享：" + req.getStockCode());
                }
                // 研究报告来源只在对应股票内共享，不允许遗漏同组关系项
                if (!sameAttachmentSelections(stockMaterial.getReportSourceAttachmentIds(), item.getReportSourceAttachmentIds())) {
                    throw new BizException("同一股票完整调库分组的研究报告引用必须一致：" + req.getStockCode());
                }
            }
        }
    }

    /**
     * 空列表与未选择同义，附件来源及索引继续沿用公共服务的去重口径
     *
     * 执行顺序：
     * 1. 把两组附件选择分别去重，空值转换为空集合
     * 2. 比较两组选择是否相同
     *
     * @param <T> 参与比较的附件选择项类型
     * @param left 第一组附件选择
     * @param right 第二组附件选择
     * @return true=两组按来源及索引去重后的附件选择相同，false=两组附件选择不同
     */
    private <T> boolean sameAttachmentSelections(List<T> left, List<T> right) {
        Set<T> leftSet = left == null ? Collections.emptySet() : new HashSet<>(left);
        Set<T> rightSet = right == null ? Collections.emptySet() : new HashSet<>(right);
        return leftSet.equals(rightSet);
    }

    /**
     * 提交股票调库日志与初始步骤
     *
     * 执行顺序：
     * 1. 先校验提交级必填字段，避免无效请求获取主档锁
     * 2. 取得股票主档锁，在任何股票日志或步骤写入前取得
     * 3. 完成全部股票、权限、目标池、报告及流程复核并固定写入上下文
     * 4. 将已通过复核的单笔申请写入股票专属运行表
     *
     * @param req 股票调库申请及调整明细
     * @param submissionFiles 已校验的上传文件上下文，可为空
     * @return 股票调库申请提交结果
     */
    private StockAdjustSubmitDto addAdjustLogInternal(StockPoolAdjustSubmitReq req,
                                                      SysAttachmentService.SubmissionFiles submissionFiles) {
        // 1. 先校验提交级必填字段，避免无效请求获取主档锁
        validateSubmitRequest(req);
        // 2. 取得股票主档锁，在任何股票日志或步骤写入前取得
        Map<String, StockInfoBo> lockedStocks = lockStockList(Collections.singletonList(req.getStockCode()));
        // 3. 完成全部股票、权限、目标池、报告及流程复核并固定写入上下文
        PreparedSubmit prepared = prepareSubmit(req, lockedStocks);
        if (submissionFiles == null && req.getItems().stream().anyMatch(item ->
                (item.getReportFileIndexes() != null && !item.getReportFileIndexes().isEmpty())
                || (item.getMaterialFileIndexes() != null && !item.getMaterialFileIndexes().isEmpty()))) {
            throw new BizException("本地文件必须通过 multipart 提交");
        }
        // 4. 将已通过复核的单笔申请写入股票专属运行表
        return submitPrepared(prepared, submissionFiles);
    }

    /**
     * 复核股票申请并固定主档、投资池与一般流程快照，不写运行表
     *
     * 执行顺序：
     * 1. 按普通股票调库入口复核申请和锁定的主档，准备投资池与流程快照
     *
     * @param req 待提交股票申请
     * @param lockedStocks 按主档主键顺序取得锁后的当前股票信息
     * @return 全部复核通过的提交上下文
     */
    private PreparedSubmit prepareSubmit(StockPoolAdjustSubmitReq req, Map<String, StockInfoBo> lockedStocks) {
        // 单笔仍沿用仅手工申请的字段约束
        return prepareSubmit(req, lockedStocks, false);
    }

    /**
     * 按单笔或服务端批量渠道完成相同业务复核，不写运行表
     *
     * 执行顺序：
     * 1. 复用传入的批量数据复核股票申请，沿用普通调库的处理规则
     *
     * @param req 股票调库申请及调整明细
     * @param lockedStocks 按主档主键顺序取得锁后的当前股票信息
     * @param batch 是否批次
     * @return 全部复核通过的提交上下文
     */
    private PreparedSubmit prepareSubmit(StockPoolAdjustSubmitReq req, Map<String, StockInfoBo> lockedStocks,
                                         boolean batch) {
        // 单笔与手选批量保留完整关系规则
        return prepareSubmit(req, lockedStocks, batch, false);
    }

    /**
     * Excel 按服务器有效关系集合复核，其余渠道保留原约束
     *
     * 执行顺序：
     * 1. 校验调库申请的提交级必填字段
     * 2. 确认申请中的股票仍处于可调库状态
     * 3. 锁定股票主档后确认申请中的目标池仍存在
     * 4. 根据最新股票、池和关系数据校验全部提交明细
     *
     * @param req 股票调库申请及调整明细
     * @param lockedStocks 按主档主键顺序取得锁后的当前股票信息
     * @param batch 是否批次
     * @param excelImport 是否Excel 导入
     * @return 全部复核通过的提交上下文
     */
    private PreparedSubmit prepareSubmit(StockPoolAdjustSubmitReq req, Map<String, StockInfoBo> lockedStocks,
                                         boolean batch, boolean excelImport) {
        // 1. 校验调库申请的提交级必填字段
        validateSubmitRequest(req, batch, excelImport);
        // 2. 确认申请中的股票仍处于可调库状态
        StockInfoBo stock = requireLockedStock(req.getStockCode(), lockedStocks);
        // 3. 锁定股票主档后确认申请中的目标池仍存在
        checkTargetPoolsExist(req.getItems().stream().map(StockPoolAdjustSubmitReq.AdjustItem::getTargetPoolId).collect(Collectors.toList()));
        Map<Long, InvestmentPoolBo> poolMap = investmentPoolMapper.queryPoolList().stream()
                .filter(pool -> pool.getId() != null)
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool));
        Set<Long> currentPoolIds = new HashSet<>(stockPoolAdjustMapper.queryStockCurrentPoolIdList(stock.getStockCode()));
        List<PoolRelationBo> relations = stockPoolAdjustMapper.queryAllPoolRelationList();
        // 4. 根据最新股票、池和关系数据校验全部提交明细
        validateSubmitItems(req, stock, poolMap, currentPoolIds, relations);
        if (!excelImport) {
            // Excel 已按固定关系选项复核，其他渠道必须提交完整展开项
            validateSubmittedRelations(req, stock, poolMap, currentPoolIds, relations);
        }

        Map<String, StockPoolAdjustSubmitReq.AdjustItem> manualByGroup = new HashMap<>();
        for (StockPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (ITEM_MANUAL.equals(item.getItemTag())) {
                // 校验手工调整项的分组标识并建立分组索引
                manualByGroup.put(requireGroupKey(item), item);
            }
        }
        Map<String, FlowSnapshot> snapshotByGroup = new HashMap<>();
        for (StockPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            // 确认每条关系项均关联本请求中的手工主项
            String groupKey = requireGroupKey(item);
            StockPoolAdjustSubmitReq.AdjustItem manual = manualByGroup.get(groupKey);
            if (manual == null) {
                throw new BizException("每个调库分组必须包含一条手工调整项");
            }
            if (!snapshotByGroup.containsKey(groupKey)) {
                // 在写入前固定来源组使用的已发布一般流程快照
                FlowSnapshot snapshot = excelImport
                        // 检查 Excel 发布版本及人工审批，单笔快速路径不受影响
                        ? requireExcelFlow(manual.getFlowId(), manual.getFlowKey(), stock.getStockCode())
                        // 构建已发布流程快照
                        : buildFlowSnapshot(manual.getFlowId(), stock.getStockCode());
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
        return new PreparedSubmit(req, stock, poolMap, manualByGroup, snapshotByGroup);
    }

    /**
     * 使用已复核上下文保存股票日志、审批快照、初始步骤和附件
     *
     * 执行顺序：
     * 1. 按普通调库入口写入复核后的股票申请及附件
     *
     * @param prepared 已完成全部复核的股票申请
     * @param submissionFiles 已校验的上传文件上下文，可为空
     * @return 股票调库提交结果
     */
    private StockAdjustSubmitDto submitPrepared(PreparedSubmit prepared,
                                               SysAttachmentService.SubmissionFiles submissionFiles) {
        // 保留单笔和手选批量的快速审批行为
        return submitPrepared(prepared, submissionFiles, false);
    }

    /**
     * 保存已复核请求，Excel 到结束节点时整批回滚
     *
     * 执行顺序：
     * 1. 逐项生成股票调库批次号
     * 2. 构建股票调库日志，不信任前端股票名称和产品类型
     * 3. 逐项创建开始、发起人和首个人工审批步骤
     * 4. 终审成功后原子更新整组记录、成员与内部报告
     *
     * @param prepared 已完成全部复核的股票申请
     * @param submissionFiles 已校验的上传文件上下文，可为空
     * @param excelImport 是否Excel 导入
     * @return 股票调库提交结果
     */
    private StockAdjustSubmitDto submitPrepared(PreparedSubmit prepared,
                                               SysAttachmentService.SubmissionFiles submissionFiles,
                                               boolean excelImport) {
        StockPoolAdjustSubmitReq req = prepared.req;
        Map<String, String> batchByGroup = new LinkedHashMap<>();
        Map<String, StockAdjustLogBo> primaryByGroup = new LinkedHashMap<>();
        List<Long> logIds = new ArrayList<>();
        for (StockPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            // 先保存整组日志与附件，避免快速流程终审漏掉后续关系项
            String groupKey = requireGroupKey(item);
            // 同组首次保存时生成独立股票调库批次号，后续关系项共用
            String batchNo = batchByGroup.computeIfAbsent(groupKey, key -> generateBatchNo());
            // 汇总股票主档、主项及流程快照，构建当前调库日志
            StockAdjustLogBo log = buildAdjustLog(req, prepared.stock, item, prepared.manualByGroup.get(groupKey),
                    prepared.poolMap.get(item.getTargetPoolId()), prepared.poolMap, batchNo,
                    prepared.snapshotByGroup.get(groupKey));
            if (stockPoolAdjustMapper.addAdjustLog(log) != 1 || log.getId() == null) {
                throw new BizException("股票调库记录写入失败");
            }
            logIds.add(log.getId());
            // 上传附件与报告引用均绑定到具体调整记录
            bindAttachments(log.getId(), item, submissionFiles, req.getAdjusterId());
            if (ITEM_MANUAL.equals(item.getItemTag())) {
                primaryByGroup.put(groupKey, log);
            }
        }
        for (Map.Entry<String, StockAdjustLogBo> entry : primaryByGroup.entrySet()) {
            StockAdjustLogBo log = entry.getValue();
            // 只有手工主项创建流程步骤，整组共享其审批结果
            boolean finished = createInitialSteps(log.getId(), log.getAdjustBatchNo(),
                    prepared.snapshotByGroup.get(entry.getKey()), req.getAdjusterId(), req.getAdjusterName());
            if (finished) {
                if (excelImport) {
                    throw new BizException("股票 Excel 申请必须进入人工审批，不能直接结束");
                }
                // 无待办快速流程已到结束节点，原子终审并落整组投资池
                finishApprovedBatch(log.getAdjustBatchNo());
            }
        }
        StockAdjustSubmitDto dto = new StockAdjustSubmitDto();
        dto.setAdjustLogIds(logIds);
        dto.setAdjustBatchNos(new ArrayList<>(batchByGroup.values()));
        return dto;
    }

    /**
     * 校验提交级字段
     *
     * 执行顺序：
     * 1. 单笔公开入口不接受客户端伪造的批量渠道
     *
     * @param req 待提交的股票调库申请
     */
    private void validateSubmitRequest(StockPoolAdjustSubmitReq req) {
        // 单笔公开入口不接受客户端伪造的批量渠道
        validateSubmitRequest(req, false);
    }

    /**
     * 校验单笔字段，批量渠道仅由多单编排入口授予
     *
     * 执行顺序：
     * 1. 原渠道不接纳 Excel 专属类型
     *
     * @param req 股票调库申请及调整明细
     * @param batch 是否批次
     */
    private void validateSubmitRequest(StockPoolAdjustSubmitReq req, boolean batch) {
        // 原渠道不接纳 Excel 专属类型
        validateSubmitRequest(req, batch, false);
    }

    /**
     * 校验服务端渠道授予的类型与来源，不能由公开单笔请求改变渠道
     *
     * 执行顺序：
     * 1. 检查股票代码、调整人 ID 和调库明细，缺少任意一项时终止处理
     * 2. 检查调库原因和意见的长度，并校验调整人 ID
     * 3. 按 Excel 或手工申请入口校验主项类型
     * 4. 逐项核对目标池、调整来源和分组，拒绝同组重复的手工项
     *
     * @param req 股票调库申请及调整明细
     * @param batch 是否批次
     * @param excelImport 是否Excel 导入
     */
    private void validateSubmitRequest(StockPoolAdjustSubmitReq req, boolean batch, boolean excelImport) {
        if (req == null || req.getStockCode() == null || req.getStockCode().trim().isEmpty()) {
            throw new BizException("股票代码不能为空");
        }
        if (req.getAdjusterId() == null || req.getAdjusterId().trim().isEmpty()) {
            throw new BizException("调整人 ID 不能为空");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("至少提交一条股票调库明细");
        }
        if ((req.getAdjustReason() != null && req.getAdjustReason().length() > 1000)
                || (req.getAdjustAdvice() != null && req.getAdjustAdvice().length() > 1000)) {
            throw new BizException("调整原因和意见均不能超过1000字");
        }
        StockQueryFilterHelper.requireUserId(req.getAdjusterId());
        if (excelImport && !"Excel导入".equals(req.getAdjustType()) && !"Excel清空".equals(req.getAdjustType())) {
            throw new BizException("股票 Excel 主项类型必须为 Excel导入 或 Excel清空");
        }
        if (!excelImport && req.getAdjustType() != null && !req.getAdjustType().trim().isEmpty()
                && !"手工调整".equals(req.getAdjustType()) && !(batch && "手动批量调整".equals(req.getAdjustType()))) {
            throw new BizException("股票首版只支持手工申请，联动与互斥类型由系统生成");
        }
        Set<String> manualGroups = new HashSet<>();
        for (StockPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            if (item == null || item.getTargetPoolId() == null) {
                throw new BizException("目标投资池不能为空");
            }
            if (!ITEM_MANUAL.equals(item.getItemTag()) && !ITEM_LINKAGE.equals(item.getItemTag())
                    && !ITEM_MUTEX.equals(item.getItemTag())) {
                throw new BizException("调整来源不合法");
            }
            // 确认每组恰好一条主项，防止关系项覆盖流程来源
            String group = requireGroupKey(item);
            if (ITEM_MANUAL.equals(item.getItemTag()) && !manualGroups.add(group)) {
                throw new BizException("每个調库分组只能包含一条手工调整项");
            }
        }
    }

    /**
     * 校验提交明细与重新读取后的股票、投资池和一般流程一致
     *
     * 执行顺序：
     * 1. 非管理员需解析用户 ID 并查询可调整的投资池
     * 2. 逐项使用当前成员与本次同组调入项组成来源 OR 条件，其余校验仍使用真实成员状态
     * 3. 校验一般流程快照，禁止快速和批量流程替代
     *
     * @param req 待提交的股票调库申请
     * @param stock 最新股票主档
     * @param poolMap 当前投资池映射
     * @param currentPoolIds 股票当前所在的投资池 ID
     * @param relations 当前投资池关系配置
     */
    private void validateSubmitItems(StockPoolAdjustSubmitReq req, StockInfoBo stock,
                                     Map<Long, InvestmentPoolBo> poolMap, Set<Long> currentPoolIds,
                                     List<PoolRelationBo> relations) {
        Set<Long> sourcePoolIds = new HashSet<>(currentPoolIds);
        req.getItems().stream().filter(item -> AdjustMode.IN.getCode().equals(item.getAdjustMode()))
                .forEach(item -> sourcePoolIds.add(item.getTargetPoolId()));
        // 非管理员需解析用户 ID 并查询可调整的投资池
        Set<Long> adjustablePoolIds = AdminUserIdUtil.isAdminUser(req.getAdjusterId())
                // 查询用户直接或通过角色拥有调整权限的池，再解析数字用户 ID
                ? poolMap.keySet() : queryAdjustablePoolIds(parseUserId(req.getAdjusterId()));
        Set<Long> keys = new HashSet<>();
        for (StockPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            // 确认提交明细的目标投资池仍然存在
            InvestmentPoolBo pool = requirePool(poolMap, item.getTargetPoolId());
            if (!adjustablePoolIds.contains(pool.getId())) {
                throw new BizException("当前用户没有投资池调整权限：" + pool.getPoolName());
            }
            // 按最新池状态和关系配置复核当前调整项
            List<String> failures = validatePoolAdjust(stock, pool, item.getAdjustMode(), currentPoolIds, relations, null, true, sourcePoolIds);
            if (!failures.isEmpty()) {
                throw new BizException(pool.getPoolName() + "：" + String.join("；", failures));
            }
            // 生成目标池与调整方向的唯一键以检查重复明细
            if (!keys.add(item.getTargetPoolId())) {
                throw new BizException("提交明细存在重复投资池及方向：" + pool.getPoolName());
            }
            // 阻止当前用户短时间内对同一股票和目标池重复提交
            if (stockPoolAdjustMapper.queryRecentDuplicate(stock.getStockCode(), pool.getId(),
                    item.getAdjustMode(), req.getAdjusterId(), new Date(System.currentTimeMillis() - 30000L))) {
                throw new BizException("请勿在短时间内重复提交股票调库申请：" + pool.getPoolName());
            }
            StockPoolAdjustSubmitReq.AdjustItem manual = ITEM_MANUAL.equals(item.getItemTag()) ? item : null;
            if (manual != null) {
                // 确认手工调整项使用目标池配置的一般流程
                validateNormalFlow(manual, pool, stock.getStockCode());
            }
            // 校验目标池对股票报告附件的要求
            validateReportRestriction(item, pool, stock.getStockCode());
        }
    }

    /**
     * 校验一般流程快照，禁止快速和批量流程替代
     *
     * 执行顺序：
     * 1. 校验所选配置确实指向已发布的完整流程
     * 2. 校验一般流程快照，禁止快速和批量流程替代
     *
     * @param item 手工调整项及其选择的流程
     * @param pool 目标投资池的流程配置
     * @param stockCode 当前股票代码，用于定位当前股票
     */
    private void validateNormalFlow(StockPoolAdjustSubmitReq.AdjustItem item, InvestmentPoolBo pool, String stockCode) {
        boolean inbound = AdjustMode.IN.getCode().equals(item.getAdjustMode());
        boolean fast = (inbound ? FlowType.FAST_INBOUND.getCode() : FlowType.FAST_OUTBOUND.getCode()).equals(item.getFlowType());
        String expectedType = inbound ? (fast ? FlowType.FAST_INBOUND.getCode() : FlowType.NORMAL_INBOUND.getCode())
                : (fast ? FlowType.FAST_OUTBOUND.getCode() : FlowType.NORMAL_OUTBOUND.getCode());
        Long expectedId = inbound ? (fast ? pool.getSimpleInFlowId() : pool.getInFlowId())
                : (fast ? pool.getSimpleOutFlowId() : pool.getOutFlowId());
        String expectedKey = inbound ? (fast ? pool.getSimpleInFlowKey() : pool.getInFlowKey())
                : (fast ? pool.getSimpleOutFlowKey() : pool.getOutFlowKey());
        if (!expectedType.equals(item.getFlowType()) || expectedId == null || !expectedId.equals(item.getFlowId())
                || expectedKey == null || !expectedKey.equals(item.getFlowKey())) {
            throw new BizException("只能选择目标投资池配置的一般或快速审批流程：" + pool.getPoolName());
        }
        // 校验所选配置确实指向已发布的完整流程
        FlowSnapshot snapshot = buildFlowSnapshot(expectedId, stockCode);
        if (snapshot == null || !expectedKey.equals(snapshot.definition.getFlowKey())) {
            throw new BizException("所选审批流程未发布或不可用：" + pool.getPoolName());
        }
    }

    /**
     * 校验投资池报告限制
     *
     * 执行顺序：
     * 1. 即使目标池不要求报告，已选择的研究报告也必须属于当前股票
     *
     * @param item 待提交调整项及其报告附件
     * @param pool 目标投资池的报告限制配置
     * @param stockCode 当前股票代码，用于定位当前股票
     */
    private void validateReportRestriction(StockPoolAdjustSubmitReq.AdjustItem item, InvestmentPoolBo pool, String stockCode) {
        String restriction = AdjustMode.IN.getCode().equals(item.getAdjustMode())
                ? pool.getInReportRestriction() : pool.getOutReportRestriction();
        if (restriction != null && !"none".equals(restriction) && !"any".equals(restriction) && !"internal".equals(restriction)) {
            throw new BizException("股票池报告限制配置无效：" + restriction);
        }
        // 即使目标池不要求报告，已选择的研究报告也必须属于当前股票
        sysAttachmentService.validateStockReportSources(item.getReportSourceAttachmentIds(), stockCode, "internal".equals(restriction));
        if (!ITEM_MANUAL.equals(item.getItemTag()) || restriction == null || "none".equals(restriction)) {
            return;
        }
        boolean hasManual = item.getReportFileIndexes() != null && !item.getReportFileIndexes().isEmpty();
        boolean hasSource = item.getReportSourceAttachmentIds() != null && !item.getReportSourceAttachmentIds().isEmpty();
        if ((!hasManual && !hasSource) || ("internal".equals(restriction) && !hasSource)) {
            throw new BizException("投资池要求提交符合限制的股票研究报告：" + pool.getPoolName());
        }
    }

    /**
     * 生成单条校验结果
     *
     * 执行顺序：
     * 1. 用目标池和调整方向定位唯一校验结果
     * 2. 构建目标池的完整层级名称供校验结果展示
     * 3. 对存在的目标池执行股票调库规则校验
     * 4. 汇总弹性限制池提示，不将其计入阻断原因
     * 5. 为可选择的目标池生成一般流程候选项
     *
     * @param resultMap 按目标池与方向去重的校验结果
     * @param stock 当前股票主档
     * @param poolId 目标投资池 ID
     * @param adjustMode 调入或调出方向
     * @param itemTag 手工、联动或互斥来源
     * @param groupKey 手工调整项的分组标识
     * @param poolMap 当前投资池映射
     * @param currentPoolIds 股票当前所在的投资池 ID
     * @param relations 当前投资池关系配置
     * @param sourcePoolIds 来源投资池ID 列表
     */
    private void addCheckResult(Map<String, StockAdjustCheckDto.CheckResultItem> resultMap, StockInfoBo stock,
                                Long poolId, String adjustMode, String itemTag, String groupKey,
                                Map<Long, InvestmentPoolBo> poolMap, Set<Long> currentPoolIds,
                                List<PoolRelationBo> relations, Set<Long> sourcePoolIds) {
        // 1. 用目标池和调整方向定位唯一校验结果
        String key = itemKey(poolId, adjustMode);
        if (resultMap.containsKey(key)) {
            return;
        }
        InvestmentPoolBo pool = poolMap.get(poolId);
        StockAdjustCheckDto.CheckResultItem result = new StockAdjustCheckDto.CheckResultItem();
        result.setStockCode(stock.getStockCode());
        result.setStockShortName(stock.getStockShortName());
        result.setSecurityType(stock.getSecurityType());
        result.setTargetPoolId(poolId);
        // 2. 构建目标池的完整层级名称供校验结果展示
        result.setPoolName(pool == null ? null : buildPoolPath(poolId, poolMap));
        result.setPoolType(pool == null ? null : pool.getPoolType());
        result.setAdjustMode(adjustMode);
        result.setItemTag(itemTag);
        result.setAdjustGroupKey(groupKey);
        // 3. 对存在的目标池执行股票调库规则校验
        List<String> failures = pool == null
                ? new ArrayList<>(Collections.singletonList("投资池不存在或已删除"))
                // 使用当前成员与本次同组调入项组成来源 OR 条件，其余校验仍使用真实成员状态
                : validatePoolAdjust(stock, pool, adjustMode, currentPoolIds, relations, null, true, sourcePoolIds);
        result.setFailReasons(failures);
        // 4. 汇总弹性限制池提示，不将其计入阻断原因
        result.setWarnings(resolveSoftRelationWarnings(poolId, adjustMode, currentPoolIds, poolMap, relations));
        result.setCanAdjust(failures.isEmpty());
        // 5. 为可选择的目标池生成一般流程候选项
        result.setFlowOptions(buildNormalFlowOptions(stock, pool, adjustMode, failures));
        resultMap.put(key, result);
    }

    /**
     * 展开联动与互斥关系
     *
     * 执行顺序：
     * 1. 逐项生成单条校验结果
     *
     * @param resultMap 已生成的调整项校验结果
     * @param stock 当前股票主档
     * @param manual 手工选择的调整项
     * @param groupKey 手工调整项的分组标识
     * @param poolMap 当前投资池映射
     * @param currentPoolIds 股票当前所在的投资池 ID
     * @param relations 当前投资池关系配置
     * @param sourcePoolIds 来源投资池ID 列表
     */
    private void expandRelationItems(Map<String, StockAdjustCheckDto.CheckResultItem> resultMap, StockInfoBo stock,
                                     StockAdjustCheckReq.CheckItem manual, String groupKey,
                                     Map<Long, InvestmentPoolBo> poolMap, Set<Long> currentPoolIds,
                                     List<PoolRelationBo> relations, Set<Long> sourcePoolIds) {
        for (PoolRelationBo relation : relations) {
            if (!manual.getTargetPoolId().equals(relation.getPoolId())) {
                continue;
            }
            if (AdjustMode.IN.getCode().equals(manual.getAdjustMode())) {
                if (RelationType.IN_LINKED.getCode().equals(relation.getRelationType())) {
                    // 为调入联动池补充同方向调整项
                    addCheckResult(resultMap, stock, relation.getRelationPoolId(), AdjustMode.IN.getCode(),
                            ITEM_LINKAGE, groupKey, poolMap, currentPoolIds, relations, sourcePoolIds);
                } else if (RelationType.IN_MUTEX.getCode().equals(relation.getRelationType())
                        && currentPoolIds.contains(relation.getRelationPoolId())) {
                    // 为已入池的调入互斥池补充调出项
                    addCheckResult(resultMap, stock, relation.getRelationPoolId(), AdjustMode.OUT.getCode(),
                            ITEM_MUTEX, groupKey, poolMap, currentPoolIds, relations, sourcePoolIds);
                }
            } else if (AdjustMode.OUT.getCode().equals(manual.getAdjustMode())) {
                if (RelationType.OUT_LINKED.getCode().equals(relation.getRelationType())) {
                    // 为调出联动池补充同方向调整项
                    addCheckResult(resultMap, stock, relation.getRelationPoolId(), AdjustMode.OUT.getCode(),
                            ITEM_LINKAGE, groupKey, poolMap, currentPoolIds, relations, sourcePoolIds);
                } else if (RelationType.OUT_MUTEX.getCode().equals(relation.getRelationType())
                        && !currentPoolIds.contains(relation.getRelationPoolId())) {
                    // 为尚未入池的调出互斥池补充调入项
                    addCheckResult(resultMap, stock, relation.getRelationPoolId(), AdjustMode.IN.getCode(),
                            ITEM_MUTEX, groupKey, poolMap, currentPoolIds, relations, sourcePoolIds);
                }
            }
        }
    }

    /**
     * 执行单池通用股票调库校验
     *
     * 执行顺序：
     * 1. 以证券当前所在的池和本次调整方向校验目标池，本次不排除任何审批批次
     *
     * @param stock 当前股票主档
     * @param pool 目标投资池
     * @param adjustMode 调入或调出方向
     * @param currentPoolIds 股票当前所在的投资池 ID
     * @param relations 当前投资池关系配置
     * @return 校验失败原因列表
     */
    private List<String> validatePoolAdjust(StockInfoBo stock, InvestmentPoolBo pool, String adjustMode,
                                            Set<Long> currentPoolIds, List<PoolRelationBo> relations) {
        // 使用默认参数校验单个股票目标池调整项
        return validatePoolAdjust(stock, pool, adjustMode, currentPoolIds, relations, null);
    }

    /**
     * 校验股票单池调整规则，可排除当前正在审批的批次
     *
     * 执行顺序：
     * 1. 用传入的批次号排除正在审批的本批记录，并复核目标池的一般流程配置
     *
     * @param stock 当前股票主档
     * @param pool 目标投资池
     * @param adjustMode 调入或调出方向
     * @param currentPoolIds 股票当前所在的投资池 ID
     * @param relations 当前投资池关系配置
     * @param excludedBatchNo 复核时需要排除的调库批次号
     * @return 校验失败原因列表
     */
    private List<String> validatePoolAdjust(StockInfoBo stock, InvestmentPoolBo pool, String adjustMode,
                                            Set<Long> currentPoolIds, List<PoolRelationBo> relations,
                                            String excludedBatchNo) {
        // 排除指定批次后复用完整的股票单池校验
        return validatePoolAdjust(stock, pool, adjustMode, currentPoolIds, relations, excludedBatchNo, true);
    }

    /**
     * 执行股票单池规则校验，可控制是否复核当前发布的一般流程
     *
     * 执行顺序：
     * 1. 使用当前生效池同时判断来源条件，按传入开关决定是否复核发布流程
     *
     * @param stock 当前股票主档
     * @param pool 目标投资池
     * @param adjustMode 调入或调出方向
     * @param currentPoolIds 股票当前所在的投资池 ID
     * @param relations 当前投资池关系配置
     * @param excludedBatchNo 复核时需要排除的调库批次号
     * @param validateFlow 是否校验目标池当前发布的一般流程
     * @return 校验失败原因列表
     */
    private List<String> validatePoolAdjust(StockInfoBo stock, InvestmentPoolBo pool, String adjustMode,
                                            Set<Long> currentPoolIds, List<PoolRelationBo> relations,
                                            String excludedBatchNo, boolean validateFlow) {
        // 默认来源只采用当前已通过成员，不将其他申请当作来源
        return validatePoolAdjust(stock, pool, adjustMode, currentPoolIds, relations, excludedBatchNo, validateFlow, currentPoolIds);
    }

    /**
     * 使用当前成员与本次同组调入项组成来源 OR 条件，其余校验仍使用真实成员状态
     *
     * 执行顺序：
     * 1. 校验目标池状态及对股票品种、股票市场的准入配置
     * 2. 对照当前池状态判断调入或调出方向是否仍然有效
     * 3. 调入前检查目标池是否仍有剩余容量
     * 4. 阻止同一股票在目标池存在未完成调库流程时再次申请
     * 5. 校验目标池配置的来源池及调入调出限制池
     * 6. 配置开放日限制的投资池仅在开放区间内允许调整
     * 7. 调出前核对股票入池时间是否满足冻结期要求
     *
     * @param stock 当前股票主档
     * @param pool 目标投资池
     * @param adjustMode 调入或调出方向
     * @param currentPoolIds 股票当前所在的投资池 ID
     * @param relations 当前投资池关系配置
     * @param excludedBatchNo 复核时需要排除的调库批次号
     * @param validateFlow 是否校验目标池当前发布的一般流程
     * @param sourcePoolIds 来源投资池ID 列表
     * @return 校验失败原因列表
     */
    private List<String> validatePoolAdjust(StockInfoBo stock, InvestmentPoolBo pool, String adjustMode,
                                            Set<Long> currentPoolIds, List<PoolRelationBo> relations,
                                            String excludedBatchNo, boolean validateFlow, Set<Long> sourcePoolIds) {
        List<String> failures = new ArrayList<>();
        if (!AdjustMode.IN.getCode().equals(adjustMode) && !AdjustMode.OUT.getCode().equals(adjustMode)) {
            failures.add("调整方向不合法");
            return failures;
        }
        // 1. 校验目标池状态及对股票品种、股票市场的准入配置
        if (!ENABLED.equals(pool.getStatus())) {
            failures.add("投资池未启用");
        }
        if (Integer.valueOf(1).equals(pool.getLockFlag())) {
            failures.add("投资池已锁定");
        }
        if (pool.getVarietyCodes() == null || !pool.getVarietyCodes().contains(STOCK_VARIETY_TOKEN)) {
            failures.add("投资池不支持股票品种");
        }
        String marketToken = "\"" + stock.getMarketCode() + "\"";
        if (pool.getMarketCodes() == null || !pool.getMarketCodes().contains(marketToken)) {
            failures.add("股票市场不在投资池允许范围内");
        }
        if (AdjustMode.IN.getCode().equals(adjustMode)) {
            // 按目标池配置校验股票行业和最新有效评级
            validateStockAdmission(failures, stock, pool);
        }
        // 2. 对照当前池状态判断调入或调出方向是否仍然有效
        boolean currentlyIn = currentPoolIds.contains(pool.getId());
        if (AdjustMode.IN.getCode().equals(adjustMode) && currentlyIn) {
            failures.add("股票已在目标投资池中");
        }
        if (AdjustMode.OUT.getCode().equals(adjustMode) && !currentlyIn) {
            failures.add("股票当前不在目标投资池中");
        }
        // 3. 调入前检查目标池是否仍有剩余容量
        if (AdjustMode.IN.getCode().equals(adjustMode) && pool.getMaxCapacity() != null
                && pool.getMaxCapacity() > 0
                && stockPoolAdjustMapper.queryPoolCurrentCount(pool.getId()) >= pool.getMaxCapacity()) {
            failures.add("目标投资池容量已满");
        }
        // 4. 阻止同一股票在目标池存在未完成调库流程时再次申请
        if (stockPoolAdjustMapper.queryStockHasPendingProcess(stock.getStockCode(), pool.getId(), excludedBatchNo)) {
            failures.add("该股票在目标投资池存在待处理流程");
        }
        // 5. 校验目标池配置的来源池及调入调出限制池
        validateRelationRestrictions(failures, pool.getId(), adjustMode, currentPoolIds, relations, sourcePoolIds);
        // 6. 配置开放日限制的投资池仅在开放区间内允许调整
        if (Integer.valueOf(1).equals(pool.getOpenDayAdjust())) {
            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            if (!stockPoolAdjustMapper.queryPoolInOpenDay(pool.getId(), today)) {
                failures.add("当前日期不在投资池开放日区间内");
            }
        }
        // 7. 调出前核对股票入池时间是否满足冻结期要求
        if (AdjustMode.OUT.getCode().equals(adjustMode) && pool.getFrozenPeriodIn() != null
                && pool.getFrozenPeriodIn() > 0) {
            Date entryTime = stockPoolAdjustMapper.queryStockPoolEntryTime(stock.getStockCode(), pool.getId());
            if (entryTime == null) {
                failures.add("股票入池时间缺失，无法复核冻结期");
            } else {
                LocalDate entryDate = entryTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                long days = ChronoUnit.DAYS.between(entryDate, LocalDate.now());
                if (days < pool.getFrozenPeriodIn()) {
                    failures.add("股票仍在调出冻结期内");
                }
            }
        }
        if (validateFlow) {
            Long flowId = AdjustMode.IN.getCode().equals(adjustMode) ? pool.getInFlowId() : pool.getOutFlowId();
            Long fastId = AdjustMode.IN.getCode().equals(adjustMode) ? pool.getSimpleInFlowId() : pool.getSimpleOutFlowId();
            // 分别读取一般和快速流程快照，至少一个已发布且可用才允许调整
            if (buildFlowSnapshot(flowId, stock.getStockCode()) == null
                    // 构建已发布流程快照
                    && buildFlowSnapshot(fastId, stock.getStockCode()) == null) {
                failures.add("目标投资池未配置已发布的一般或快速审批流程");
            }
        }
        return failures;
    }

    /**
     * 校验来源池、限制池关系
     *
     * 执行顺序：
     * 1. 只检查当前目标池配置的关系，核对在池情况是否触发方向对应的限制
     * 2. 调入有来源配置时还须命中至少一个来源池
     *
     * @param failures 待追加阻断原因的结果列表
     * @param poolId 目标投资池 ID
     * @param adjustMode 调入或调出方向
     * @param currentPoolIds 股票当前所在的投资池 ID
     * @param relations 当前投资池关系配置
     * @param sourcePoolIds 来源投资池ID 列表
     */
    private void validateRelationRestrictions(List<String> failures, Long poolId, String adjustMode,
                                              Set<Long> currentPoolIds, List<PoolRelationBo> relations, Set<Long> sourcePoolIds) {
        boolean hasSource = false;
        boolean matchesSource = false;
        for (PoolRelationBo relation : relations) {
            if (!poolId.equals(relation.getPoolId())) {
                continue;
            }
            if (RelationType.SOURCE.getCode().equals(relation.getRelationType())) {
                hasSource = true;
                matchesSource |= sourcePoolIds.contains(relation.getRelationPoolId());
            }
            if (RelationType.IN_RESTRICT.getCode().equals(relation.getRelationType())
                    && AdjustMode.IN.getCode().equals(adjustMode)
                    && currentPoolIds.contains(relation.getRelationPoolId())) {
                failures.add("股票命中调入限制池");
            }
            if (RelationType.OUT_RESTRICT.getCode().equals(relation.getRelationType())
                    && AdjustMode.OUT.getCode().equals(adjustMode)
                    && currentPoolIds.contains(relation.getRelationPoolId())) {
                failures.add("股票命中调出限制池");
            }
        }
        if (AdjustMode.IN.getCode().equals(adjustMode) && hasSource && !matchesSource) {
            failures.add("股票不在任一配置的来源池中");
        }
    }

    /**
     * 解析弹性限制关系警告
     *
     * 执行顺序：
     * 1. 按调整方向选择调入或调出的弹性限制关系
     * 2. 与当前在池记录对照，收集命中的警告
     *
     * @param poolId 目标投资池 ID
     * @param adjustMode 调入或调出方向
     * @param currentPoolIds 股票当前所在的投资池 ID
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
                warnings.add("股票命中弹性限制池"
                        + (relationPool == null ? "" : "：" + relationPool.getPoolName()));
            }
        }
        return warnings;
    }

    /**
     * 构建目标池唯一的一般流程候选
     *
     * 执行顺序：
     * 1. 一般流程置于首位且默认推荐，快速流程由用户显式选择
     * 2. 追加快速流程候选，保留用户显式选择且不默认推荐
     *
     * @param stock 当前股票主档
     * @param pool 目标投资池
     * @param adjustMode 调入或调出方向
     * @param failures 已发现的调库阻断原因
     * @return 一般流程候选项列表，目标池为空时返回空列表
     */
    private List<StockAdjustCheckDto.FlowOption> buildNormalFlowOptions(StockInfoBo stock, InvestmentPoolBo pool,
                                                                       String adjustMode, List<String> failures) {
        if (pool == null) {
            return Collections.emptyList();
        }
        boolean inbound = AdjustMode.IN.getCode().equals(adjustMode);
        List<StockAdjustCheckDto.FlowOption> options = new ArrayList<>();
        // 1. 一般流程置于首位且默认推荐，快速流程由用户显式选择
        addFlowOption(options, stock, inbound ? pool.getInFlowId() : pool.getOutFlowId(),
                inbound ? pool.getInFlowKey() : pool.getOutFlowKey(), inbound ? FlowType.NORMAL_INBOUND.getCode() : FlowType.NORMAL_OUTBOUND.getCode(),
                true, failures);
        // 2. 追加快速流程候选，保留用户显式选择且不默认推荐
        addFlowOption(options, stock, inbound ? pool.getSimpleInFlowId() : pool.getSimpleOutFlowId(),
                inbound ? pool.getSimpleInFlowKey() : pool.getSimpleOutFlowKey(), inbound ? FlowType.FAST_INBOUND.getCode() : FlowType.FAST_OUTBOUND.getCode(),
                false, failures);
        return options;
    }

    /**
     * 构建股票调库日志，不信任前端股票名称和产品类型
     *
     * 执行顺序：
     * 1. 按手工、联动或互斥来源确定日志调整类型
     * 2. 将目标投资池全路径写入调库日志快照
     *
     * @param req 股票调库申请
     * @param stock 最新股票主档
     * @param item 当前调整项
     * @param manual 同组手工调整项，用于取得流程类型
     * @param pool 目标投资池
     * @param poolMap 当前投资池映射，用于生成完整池名
     * @param batchNo 当前调库分组的批次号
     * @param snapshot 已发布的一般流程快照
     * @return 股票调库日志记录
     */
    private StockAdjustLogBo buildAdjustLog(StockPoolAdjustSubmitReq req, StockInfoBo stock,
                                           StockPoolAdjustSubmitReq.AdjustItem item,
                                           StockPoolAdjustSubmitReq.AdjustItem manual,
                                           InvestmentPoolBo pool, Map<Long, InvestmentPoolBo> poolMap,
                                           String batchNo, FlowSnapshot snapshot) {
        StockAdjustLogBo log = new StockAdjustLogBo();
        log.setStockCode(stock.getStockCode());
        log.setStockName(stock.getStockName());
        log.setStockShortName(stock.getStockShortName());
        log.setSecurityType(stock.getSecurityType());
        log.setIndustryCode(stock.getIndustryCode());
        log.setIndustryName(stock.getIndustryName());
        log.setLatestRating(stock.getLatestRating());
        log.setPreviousRating(stock.getPreviousRating());
        // 1. 按手工、联动或互斥来源确定日志调整类型
        log.setAdjustType(resolveAdjustType(req.getAdjustType(), item.getItemTag()));
        log.setAdjustMode(item.getAdjustMode());
        log.setAdjustBatchNo(batchNo);
        log.setTargetPoolId(pool.getId());
        // 2. 将目标投资池全路径写入调库日志快照
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
     * 构建投资池全路径名称
     *
     * 执行顺序：
     * 1. 从目标池沿父池逐级取得名称，遇到关系环时报错
     * 2. 将名称调整为从根池到目标池的顺序，用斜线拼接
     *
     * @param poolId 当前投资池 ID
     * @param poolMap 当前投资池及其祖先节点映射
     * @return 投资池全路径名称
     */
    private String buildPoolPath(Long poolId, Map<Long, InvestmentPoolBo> poolMap) {
        List<String> names = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        while (poolId != null) {
            if (!visited.add(poolId)) {
                throw new BizException("投资池层级存在循环");
            }
            InvestmentPoolBo pool = poolMap.get(poolId);
            if (pool == null) {
                throw new BizException("投资池父级配置不存在");
            }
            names.add(pool.getPoolName() == null ? "" : pool.getPoolName());
            poolId = pool.getParentId();
        }
        Collections.reverse(names);
        return String.join("/", names);
    }

    /**
     * 绑定股票专属分类附件
     *
     * 执行顺序：
     * 1. 将本次上传的报告和材料按股票附件分类绑定
     * 2. 将已选择的来源报告和材料复制到当前调库记录
     *
     * @param logId 当前股票调库日志 ID
     * @param item 当前调整项及其附件索引
     * @param submissionFiles 已上传的附件文件上下文，可为空
     * @param uploaderId 附件上传人 ID
     */
    void bindAttachments(Long logId, StockPoolAdjustSubmitReq.AdjustItem item,
                                 SysAttachmentService.SubmissionFiles submissionFiles, String uploaderId) {
        // 1. 将本次上传的报告和材料按股票附件分类绑定
        if (submissionFiles != null) {
            sysAttachmentService.bindAttachments(STOCK_ADJUST_LOG_TABLE, logId, item.getReportFileIndexes(),
                    AttachmentCategory.STOCK_REPORT_HAND.getCode(), submissionFiles);
            sysAttachmentService.bindAttachments(STOCK_ADJUST_LOG_TABLE, logId, item.getMaterialFileIndexes(),
                    AttachmentCategory.STOCK_MATERIAL_HAND.getCode(), submissionFiles);
        }
        // 2. 将已选择的来源报告和材料复制到当前调库记录
        sysAttachmentService.copyReportAttachments(STOCK_ADJUST_LOG_TABLE, logId,
                item.getReportSourceAttachmentIds(), AttachmentPurpose.CREDIT_REPORT.getCode(), uploaderId);
        sysAttachmentService.copyReportAttachments(STOCK_ADJUST_LOG_TABLE, logId,
                item.getMaterialSourceAttachmentIds(), AttachmentPurpose.MATERIAL.getCode(), uploaderId);
    }

    /**
     * 构建已发布流程快照
     *
     * 执行顺序：
     * 1. 读取处于启用状态的流程定义及其已发布版本
     * 2. 固定当前发布版本的节点、连线、审批配置和处理人
     * 3. 确认开始、结束及审批配置完整后返回快照
     *
     * @param flowId 一般审批流程定义 ID
     * @param stockCode 当前股票代码，用于定位当前股票
     * @return 已发布流程的快照，流程 ID 缺失、流程未启用或没有启用版本时返回 null
     */
    private FlowSnapshot buildFlowSnapshot(Long flowId, String stockCode) {
        if (flowId == null) {
            return null;
        }
        // 1. 读取处于启用状态的流程定义及其已发布版本
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
        // 2. 固定当前发布版本的节点、连线、审批配置和处理人
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
        // 3. 确认开始、结束及审批配置完整后返回快照
        if (findNode(nodes, NodeType.START.getCode()) == null || findNode(nodes, NodeType.END.getCode()) == null) {
            return null;
        }
        for (FlowNodeBo node : nodes) {
            if (NodeType.APPROVAL.getCode().equals(node.getNodeType()) && !configMap.containsKey(node.getId())) {
                return null;
            }
        }
        return snapshot;
    }

    /**
     * 判断股票调库审批节点是否由系统自动通过
     *
     * 执行顺序：
     * 1. 审批策略配置为自动通过或 O32 时判为系统自动通过
     *
     * @param config 当前节点的审批配置
     * @return true=此节点满足系统自动通过条件，false=此节点不满足系统自动通过条件
     */
    private boolean isSystemAutoApproval(NodeApprovalConfigBo config) {
        return config != null && (ApprovalStrategy.AUTO.getCode().equals(config.getApprovalStrategy())
                || (ApprovalStrategy.O32.getCode().equals(config.getApprovalStrategy())));
    }

    /**
     * 创建开始、发起人和首个人工审批步骤
     *
     * 执行顺序：
     * 1. 创建开始步骤，并沿所选流程推进至第一个人工待办或结束
     * 2. 保存开始节点的自动处理记录，作为本次流程的起点
     *
     * @param logId 当前股票调库日志 ID
     * @param batchNo 当前调库批次号
     * @param snapshot 已发布流程快照
     * @param adjusterId 发起人 ID
     * @param adjusterName 发起人姓名
     * @return true=流程初始化时已经到达结束节点，false=流程初始化后尚未到达结束节点
     */
    private boolean createInitialSteps(Long logId, String batchNo, FlowSnapshot snapshot,
                                    String adjusterId, String adjusterName) {
        // 1. 创建开始步骤，并沿所选流程推进至第一个人工待办或结束
        FlowNodeBo current = findNode(snapshot.nodes, NodeType.START.getCode());
        if (current == null) {
            throw new BizException("审批流程缺少开始节点");
        }
        // 2. 保存开始节点的自动处理记录，作为本次流程的起点
        insertStep(logId, batchNo, current, null, StepStatus.AUTO_PROCESS.getCode(),
                null, null, ProcessAction.AUTO_PROCESS.getCode(), new Date());
        Set<Long> visited = new HashSet<>();
        while (current != null && current.getId() != null && visited.add(current.getId())) {
            // 根据发起人提交或普通通过动作选取确定的前向连线
            current = findNextNode(snapshot, current);
            if (current == null) {
                throw new BizException("审批流程无法到达后续节点");
            }
            NodeApprovalConfigBo config = snapshot.configMap.get(current.getId());
            if (NodeType.END.getCode().equals(current.getNodeType())) {
                // 保存结束节点记录，并通知调用方本流程已结束
                insertStep(logId, batchNo, current, config, StepStatus.AUTO_PROCESS.getCode(),
                        null, null, ProcessAction.AUTO_PROCESS.getCode(), new Date());
                return true;
            }
            // 识别无需人工处理的节点和系统自动审批配置
            if (!NodeType.APPROVAL.getCode().equals(current.getNodeType()) || isSystemAutoApproval(config)) {
                // 保存自动处理记录后继续寻找下一个待办节点
                insertStep(logId, batchNo, current, config, StepStatus.AUTO_PROCESS.getCode(),
                        null, null, ProcessAction.AUTO_PROCESS.getCode(), new Date());
                continue;
            }
            // 根据发起人策略与提交连线识别初次申请的提交节点
            if (config != null && ApprovalStrategy.INITIATOR.getCode().equals(config.getApprovalStrategy())
                    // 判断流程节点是否存在指定动作的后续连线
                    && hasOutgoingRouteAction(snapshot, current, ProcessAction.SUBMIT.getCode())) {
                // 记录发起人已提交，避免把初次提交创建为人工待办
                insertStep(logId, batchNo, current, config, StepStatus.SUBMIT.getCode(),
                        adjusterId, adjusterName, ProcessAction.SUBMIT.getCode(), new Date());
                continue;
            }
            // 排除已发起者，保持债券和基金后续处理人回避规则
            List<HandlerTarget> handlers = resolveHandlers(config, snapshot).stream()
                    .filter(handler -> AdminUserIdUtil.isAdminUser(handler.id) || !adjusterId.equals(handler.id))
                    .collect(Collectors.toList());
            if (handlers.isEmpty()) {
                throw new BizException("审批节点无可用处理人：" + current.getLabel());
            }
            for (HandlerTarget handler : handlers) {
                // 为每位有效处理人创建首个人工审批待办
                insertStep(logId, batchNo, current, config, StepStatus.PENDING.getCode(), handler.id, handler.name, null, null);
            }
            return false;
        }
        throw new BizException("审批流程存在循环或缺少结束节点");
    }

    /**
     * 解析审批节点处理人
     *
     * 执行顺序：
     * 1. 审批配置或配置 ID 缺失时返回空列表
     * 2. 读取节点配置的人员，按角色配置展开该角色及其子角色中的用户
     * 3. 按用户 ID 去重并返回候选处理人
     *
     * @param config 当前节点的审批配置
     * @param snapshot 包含处理人配置的流程快照
     * @return 去重后的审批候选人，没有有效配置或候选人时返回空列表
     */
    private List<HandlerTarget> resolveHandlers(NodeApprovalConfigBo config, FlowSnapshot snapshot) {
        // 1. 审批配置或配置 ID 缺失时返回空列表
        if (config == null || config.getId() == null) {
            return Collections.emptyList();
        }
        LinkedHashMap<String, HandlerTarget> targets = new LinkedHashMap<>();
        List<RoleBo> allRoles = null;
        // 2. 读取节点配置的人员，按角色配置展开该角色及其子角色中的用户
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
        // 3. 按用户 ID 去重并返回候选处理人
        return new ArrayList<>(targets.values());
    }

    /**
     * 递归收集角色及其子角色 ID
     *
     * 执行顺序：
     * 1. 空角色或已访问角色直接跳过，从有效角色开始递归
     * 2. 收集父 ID 指向当前角色的所有子角色
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
     * 新增股票调库步骤
     *
     * 执行顺序：
     * 1. 新增调库审批步骤
     *
     * @param logId 当前股票调库日志 ID
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
        StockAdjustStepBo step = new StockAdjustStepBo();
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
        stockPoolAdjustMapper.addAdjustStep(step);
    }

    /**
     * 沿非驳回主路径查找下一节点
     *
     * 执行顺序：
     * 1. 连线为空时返回空值
     *
     * @param snapshot 当前流程快照
     * @param current 当前流程节点
     * @return 后续流程节点，不存在时返回 null
     */
    private FlowNodeBo findNextNode(FlowSnapshot snapshot, FlowNodeBo current) {
        NodeApprovalConfigBo config = snapshot.configMap.get(current.getId());
        String action = config != null && ApprovalStrategy.INITIATOR.getCode().equals(config.getApprovalStrategy())
                ? ProcessAction.SUBMIT.getCode() : ProcessAction.APPROVE.getCode();
        List<FlowEdgeBo> outgoing = snapshot.edges.stream()
                .filter(edge -> current.getId().equals(edge.getFromNodeId()))
                .filter(edge -> !ProcessAction.REJECT.getCode().equals(edge.getRouteAction()))
                .collect(Collectors.toList());
        List<FlowEdgeBo> matched = outgoing.stream().filter(edge -> action.equals(edge.getRouteAction())).collect(Collectors.toList());
        if (matched.size() > 1 || (matched.isEmpty() && outgoing.size() > 1)) {
            throw new BizException("审批流程连线路由不唯一：" + current.getLabel());
        }
        FlowEdgeBo edge = matched.isEmpty() ? (outgoing.isEmpty() ? null : outgoing.get(0)) : matched.get(0);
        if (edge == null) {
            return null;
        }
        for (FlowNodeBo node : snapshot.nodes) {
            if (node.getId().equals(edge.getToNodeId())) {
                return node;
            }
        }
        throw new BizException("审批流程连线目标节点不存在");
    }

    /**
     * 判断流程节点是否存在指定动作的后续连线
     *
     * 执行顺序：
     * 1. 逐条核对连线的起点和动作编码，两者均匹配时返回 true
     *
     * @param snapshot 当前流程快照
     * @param node 待检查的流程节点
     * @param routeAction 需要查找的流转动作
     * @return true=节点存在指定动作的出线，false=节点没有指定动作的出线
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
     * 按类型查找流程节点
     *
     * 执行顺序：
     * 1. 按列表顺序查找类型相同的第一个节点，没有匹配时返回 null
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
     * 按调整权限过滤投资池并保留祖先
     *
     * 执行顺序：
     * 1. 查询当前用户可直接调整的投资池 ID
     * 2. 按调整权限过滤投资池并保留祖先
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
     * 查询用户直接或通过角色拥有调整权限的池
     *
     * 执行顺序：
     * 1. 汇总用户所属角色及池级可调整人员配置
     * 2. 按类型查询权限列表
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
     * 保留支持股票和股票市场的池及其祖先
     *
     * 执行顺序：
     * 1. 仅保留支持股票品种及当前市场的启用池，并补齐其祖先节点
     *
     * @param pools 待筛选的投资池列表
     * @param marketCode 当前股票所属市场编码
     * @return 支持股票及其市场的投资池列表
     */
    private List<InvestmentPoolBo> retainStockPoolsAndAncestors(List<InvestmentPoolBo> pools, String marketCode) {
        Map<Long, InvestmentPoolBo> poolMap = pools.stream()
                .filter(pool -> pool.getId() != null)
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool));
        Set<Long> retained = new HashSet<>();
        String marketToken = "\"" + marketCode + "\"";
        // 仅保留支持股票品种及当前市场的启用池，并补齐其祖先节点
        for (InvestmentPoolBo pool : pools) {
            if (!ENABLED.equals(pool.getStatus()) || pool.getVarietyCodes() == null
                    || !pool.getVarietyCodes().contains(STOCK_VARIETY_TOKEN)
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
     * 按主档主键顺序取得整批股票锁，并保持代码大小写与数据库匹配口径一致
     *
     * 执行顺序：
     * 1. 通过主档行锁串行处理业务变更，并清除锁前的 MyBatis 查询缓存
     * 2. 按代码查询股票
     *
     * @param stockCodes 本次写入涉及的股票代码
     * @return 以代码索引的已锁定当前股票主档
     */
    private Map<String, StockInfoBo> lockStockList(List<String> stockCodes) {
        Set<String> distinctCodes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String stockCode : stockCodes) {
            if (stockCode == null || stockCode.trim().isEmpty()) {
                throw new BizException("股票代码不能为空");
            }
            distinctCodes.add(stockCode.trim());
        }
        // 通过主档行锁串行处理业务变更，并清除锁前的 MyBatis 查询缓存
        List<StockInfoBo> stocks = stockPoolAdjustMapper.queryStockListForUpdate(new ArrayList<>(distinctCodes));
        Map<String, StockInfoBo> lockedStocks = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (StockInfoBo stock : stocks) {
            StockInfoBo latest = stockPoolAdjustMapper.queryStockByCode(stock.getStockCode());
            if (latest == null) {
                throw new BizException("股票主档不存在或已删除");
            }
            if (lockedStocks.put(stock.getStockCode(), latest) != null) {
                throw new BizException("股票主档代码存在重复，请先处理数据，股票代码：" + stock.getStockCode());
            }
        }
        return lockedStocks;
    }

    /**
     * 使用已锁定的当前主档校验股票可调整状态
     *
     * 执行顺序：
     * 1. 股票不存在或已删除时终止处理
     * 2. 通过统一市场白名单检查锁定主档是否仍支持股票调库
     *
     * @param stockCode 待提交或终审的股票代码
     * @param lockedStocks 当前事务已锁定的股票主档
     * @return 可继续调库的当前股票主档
     */
    private StockInfoBo requireLockedStock(String stockCode, Map<String, StockInfoBo> lockedStocks) {
        StockInfoBo stock = lockedStocks.get(stockCode.trim());
        if (stock == null) {
            throw new BizException("股票不存在或已删除");
        }
        if (!"L".equals(stock.getSecurityStatus()) || (stock.getDelistDate() != null
                && !stock.getDelistDate().after(new Date()))) {
            throw new BizException("已终止或退市股票不能发起调库");
        }
        // 通过统一市场白名单检查锁定主档是否仍支持股票调库
        if (!isSupportedMarket(stock.getMarketCode())) {
            throw new BizException("股票市场仅支持 SSE、SZSE、HKEX");
        }
        return stock;
    }

    /**
     * 读取并校验股票
     *
     * 执行顺序：
     * 1. 检查股票代码不能为空
     * 2. 按代码查询股票
     * 3. 股票不存在或已删除时终止处理
     * 4. 发起调库时限制市场范围，详情查询仍可读取非活动股票
     *
     * @param stockCode 股票代码
     * @param requireActive 是否要求股票仍可发起调库
     * @return 校验通过的股票主档
     */
    private StockInfoBo requireStock(String stockCode, boolean requireActive) {
        if (stockCode == null || stockCode.trim().isEmpty()) {
            throw new BizException("股票代码不能为空");
        }
        StockInfoBo stock = stockPoolAdjustMapper.queryStockByCode(stockCode.trim());
        if (stock == null) {
            throw new BizException("股票不存在或已删除");
        }
        if (requireActive && (!"L".equals(stock.getSecurityStatus()) || (stock.getDelistDate() != null
                && !stock.getDelistDate().after(new Date())))) {
            throw new BizException("已终止或退市股票不能发起调库");
        }
        // 发起调库时限制市场范围，详情查询仍可读取非活动股票
        if (requireActive && !isSupportedMarket(stock.getMarketCode())) {
            throw new BizException("股票市场仅支持 SSE、SZSE、HKEX");
        }
        return stock;
    }

    /**
     * 读取投资池
     *
     * 执行顺序：
     * 1. 投资池不存在或已删除时终止处理
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
     * 解析数字用户 ID
     *
     * 执行顺序：
     * 1. 检查当前用户 ID 不能为空
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
     * 生成股票调库批次号
     *
     * 执行顺序：
     * 1. 取当前时间和 UUID 的前 8 位大写字符，拼成以 STOCK 开头的批次号
     *
     * @return 股票调库批次号
     */
    private String generateBatchNo() {
        String time = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        return "STOCK-" + time + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * 获取非空分组标识
     *
     * 执行顺序：
     * 1. 检查调库分组标识不能为空
     *
     * @param item 当前调整项
     * @return 非空分组标识
     */
    private String requireGroupKey(StockPoolAdjustSubmitReq.AdjustItem item) {
        if (item.getAdjustGroupKey() == null || item.getAdjustGroupKey().trim().isEmpty()) {
            throw new BizException("调库分组标识不能为空");
        }
        return item.getAdjustGroupKey().trim();
    }

    /**
     * 生成池及方向唯一键
     *
     * 执行顺序：
     * 1. 用竖线连接池 ID 和调库方向，供同一池同一方向去重
     *
     * @param poolId 目标投资池 ID
     * @param adjustMode 调入或调出方向
     * @return 池及调整方向的组合键
     */
    private String itemKey(Long poolId, String adjustMode) {
        return String.valueOf(poolId) + "|" + adjustMode;
    }

    /**
     * 按调整项来源解析调整类型
     *
     * 执行顺序：
     * 1. 按标签设置联动或互斥调整类型
     * 2. 一般项优先使用请求类型，空值按手工调整处理
     *
     * @param requested 请求中填写的调整类型
     * @param itemTag 手工、联动或互斥来源
     * @return 股票调库调整类型
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

    /**
     * 保存已通过复核的股票调库提交数据
     *
     * 在写入前保留当前股票主档和投资池配置，以及各来源组手工主项所用的流程快照
     */
    private static class PreparedSubmit {
        /** 来源分组的申请字段与明细 */
        private final StockPoolAdjustSubmitReq req;

        /** 当前股票主档 */
        private final StockInfoBo stock;

        /** 当前投资池映射 */
        private final Map<Long, InvestmentPoolBo> poolMap;

        /** 来源组手工主项 */
        private final Map<String, StockPoolAdjustSubmitReq.AdjustItem> manualByGroup;

        /** 来源组已发布流程快照 */
        private final Map<String, FlowSnapshot> snapshotByGroup;

        /**
         * 保存已复核的主档、目标池与一般流程快照
         *
         * 执行顺序：
         * 1. 保存来源分组申请、股票主档、池配置，以及各组的手工主项和一般流程快照
         *
         * @param req 来源分组申请
         * @param stock 当前股票主档
         * @param poolMap 当前投资池映射
         * @param manualByGroup 各来源组的手工主项
         * @param snapshotByGroup 各来源组的一般流程快照
         */
        PreparedSubmit(StockPoolAdjustSubmitReq req, StockInfoBo stock, Map<Long, InvestmentPoolBo> poolMap,
                       Map<String, StockPoolAdjustSubmitReq.AdjustItem> manualByGroup,
                       Map<String, FlowSnapshot> snapshotByGroup) {
            this.req = req;
            this.stock = stock;
            this.poolMap = poolMap;
            this.manualByGroup = manualByGroup;
            this.snapshotByGroup = snapshotByGroup;
        }
    }

    /**
     * 保存股票调库发起审批所用的已发布流程快照
     *
     * 在创建初始步骤时复用流程定义、节点、连线、审批配置和已解析的处理人配置
     */
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

        /**
         * 保存流程定义、节点、连线及审批配置快照
         *
         * 执行顺序：
         * 1. 记录股票调库的流程定义、节点、连线和审批索引
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

    /**
     * 保存股票调库申请的候选审批处理人
     *
     * 按用户 ID 与姓名记录从节点配置解析出的人员，供创建初始审批步骤使用
     */
    private static class HandlerTarget {
        /** 人员 ID */
        private final String id;

        /** 人员姓名 */
        private final String name;

        /**
         * 保存审批处理人的 ID 和姓名
         *
         * 执行顺序：
         * 1. 记录审批处理人的 ID 和姓名
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
