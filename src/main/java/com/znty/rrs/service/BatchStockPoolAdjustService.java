package com.znty.rrs.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.FlowType;
import com.znty.rrs.common.enums.HandlerType;
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.common.util.AdminUserIdUtil;
import com.znty.rrs.common.util.StockQueryFilterHelper;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockAdjustDto;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockAdjustReq;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockPoolAdjustReq;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockPoolDto;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.stockpooladjust.StockAdjustCheckDto;
import com.znty.rrs.entity.stockpooladjust.StockAdjustCheckReq;
import com.znty.rrs.entity.stockpooladjust.StockAdjustSubmitDto;
import com.znty.rrs.entity.stockpooladjust.StockInfoDto;
import com.znty.rrs.entity.stockpooladjust.StockPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.BatchStockPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
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
 * 处理页面发起的股票池批量调入和调出
 *
 * 按用户权限查询可调整的目标池和候选股票，逐只核对调库条件并返回审批流程候选
 * 校验和提交复用 {@link StockPoolAdjustService}，提交时整批共用上传附件
 */
@Service
public class BatchStockPoolAdjustService {
    /** 手工调整标签 */
    private static final String MANUAL = "manual";

    /** 股票批量只读查询组件 */
    @Resource
    private BatchStockPoolAdjustMapper batchStockPoolAdjustMapper;

    /** 投资池权限查询组件 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 投资池全路径查询服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /** 股票单笔调整服务 */
    @Resource
    private StockPoolAdjustService stockPoolAdjustService;

    /** 报告及材料附件服务 */
    @Resource
    private SysAttachmentService sysAttachmentService;

    /**
     * 分页查询当前用户可调整的股票叶子池
     *
     * 执行顺序：
     * 1. 先取得可调整池集合，避免空权限被当作不筛选
     * 2. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 3. 将本页记录和查询总条数一起返回
     *
     * @param req 批次股票投资池调库
     * @return 批次股票投资池及分页信息
     */
    public PageResult<BatchStockPoolDto> queryPoolPage(BatchStockPoolAdjustReq req) {
        // 先取得可调整池集合，避免空权限被当作不筛选
        Set<Long> permitted = queryAdjustablePoolIds(req.getCurrentUserId());
        if (!AdminUserIdUtil.isAdminUser(req.getCurrentUserId())) {
            List<Long> ids = req.getPoolIds() == null || req.getPoolIds().isEmpty()
                    ? new ArrayList<>(permitted) : req.getPoolIds().stream()
                    .filter(permitted::contains).collect(Collectors.toList());
            if (ids.isEmpty()) {
                return new PageResult<>(Collections.emptyList(), 0L, req.getPageIndex(), req.getPageSize());
            }
            req.setPoolIds(ids);
        }
        Page<BatchStockPoolDto> page = PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BatchStockPoolDto> records = batchStockPoolAdjustMapper.queryPoolPage(req);
        Map<Long, String> fullNames = investmentPoolService.queryPoolFullNameMap();
        for (BatchStockPoolDto pool : records) {
            pool.setPoolFullName(fullNames.get(pool.getId()));
        }
        return new PageResult<>(records, page.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 分页查询可调入或可调出的股票
     *
     * 执行顺序：
     * 1. 先确认目标池和用户权限，再开启候选股票分页
     * 2. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 3. 将本页记录和查询总条数一起返回
     *
     * @param req 批次股票投资池调库
     * @return 分页股票信息列表
     */
    public PageResult<StockInfoDto> queryStockPage(BatchStockPoolAdjustReq req) {
        // 先确认目标池和用户权限，再开启候选股票分页
        validatePoolContext(req.getCurrentUserId(), req.getPoolId(), req.getDirection());
        Page<StockInfoDto> page = PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<StockInfoDto> records = batchStockPoolAdjustMapper.queryStockPage(req);
        return new PageResult<>(records, page.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 逐股票复用单笔校验，失败股票整组隔离，仅提供一般流程
     *
     * 执行顺序：
     * 1. 复核批量主目标池及当前用户权限
     * 2. 未选择任何股票时终止处理
     * 3. 逐项构造单股票的一条手工目标池校验请求
     * 4. 将单笔基础状态失败转换为可定位股票的失败主项
     *
     * @param req 批次股票调库
     * @return 股票批量调库结果
     */
    public BatchStockAdjustDto checkAdjust(BatchStockAdjustReq req) {
        // 复核批量主目标池及当前用户权限
        validatePoolContext(req.getCurrentUserId(), req.getPoolId(), req.getDirection());
        if (req.getStocks() == null || req.getStocks().isEmpty()) {
            throw new BizException("至少选择一只股票");
        }
        Set<String> codes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        BatchStockAdjustDto dto = new BatchStockAdjustDto();
        for (BatchStockAdjustReq.StockItem stock : req.getStocks()) {
            // 拒绝空代码及重复选择，防止重复生成手工主项
            String code = requireStockCode(stock == null ? null : stock.getStockCode());
            if (!codes.add(code)) {
                throw new BizException("重复选择股票：" + code);
            }
            // 转换为当前股票的一条手工目标池校验请求
            StockAdjustCheckReq checkReq = buildSingleCheckReq(req, code);
            List<StockAdjustCheckDto.CheckResultItem> rows;
            try {
                rows = stockPoolAdjustService.checkAdjust(checkReq).getItems();
            } catch (BizException exception) {
                // 保留具体失败股票，其他股票仍可继续完成校验
                rows = Collections.singletonList(buildFailureRow(req, code, exception.getMessage()));
            }
            boolean groupValid = true;
            for (StockAdjustCheckDto.CheckResultItem row : rows) {
                row.setAdjustGroupKey(code + "_" + row.getAdjustGroupKey());
                // 批量只提供一般流程，单笔股票快速流程保持原样
                row.setFlowOptions(row.getFlowOptions().stream().filter(option ->
                        FlowType.NORMAL_INBOUND.getCode().equals(option.getFlowType())
                                || FlowType.NORMAL_OUTBOUND.getCode().equals(option.getFlowType()))
                        .collect(Collectors.toList()));
                if (row.isCanAdjust() && MANUAL.equals(row.getItemTag())
                        && row.getFlowOptions().stream().noneMatch(StockAdjustCheckDto.FlowOption::isSelectable)) {
                    row.setFailReasons(Collections.singletonList("目标投资池未配置可用的一般审批流程"));
                    row.setCanAdjust(false);
                }
                groupValid = groupValid && row.isCanAdjust();
            }
            if (!groupValid) {
                for (StockAdjustCheckDto.CheckResultItem row : rows) {
                    if (row.isCanAdjust()) {
                        row.setFailReasons(Collections.singletonList("同一股票调库分组存在未通过的调整项"));
                    }
                    row.setCanAdjust(false);
                    for (StockAdjustCheckDto.FlowOption option : row.getFlowOptions()) {
                        option.setSelectable(false);
                    }
                }
            }
            dto.getItems().addAll(rows);
        }
        dto.setStockCount(codes.size());
        return dto;
    }

    /**
     * 原子提交不含本地文件的股票批量申请
     *
     * 执行顺序：
     * 1. 按股票聚合完整主项及关系项，并委托股票多单原子提交入口
     *
     * @param req 批次股票调库
     * @return 股票批量调库结果
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public BatchStockAdjustDto addAdjustLog(BatchStockAdjustReq req) {
        // 无本地文件时仍沿用相同整批复核和写入路径
        return submitBatch(req, null);
    }

    /**
     * 原子提交股票批量申请及整批共用的物理附件
     *
     * 执行顺序：
     * 1. 先校验请求身份及目标池，再创建共享附件上下文
     * 2. 每个股票日志独立绑定附件，整批上传只创建一份物理文件
     *
     * @param req 批次股票调库
     * @param files 本次提交的本地上传附件
     * @param originalFileNameListJson 上传文件原始名称列表的 JSON
     * @return 股票批量调库结果
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public BatchStockAdjustDto addAdjustLog(BatchStockAdjustReq req, List<MultipartFile> files,
                                            String originalFileNameListJson) {
        // 1. 先校验请求身份及目标池，再创建共享附件上下文
        validateSubmitRequest(req);
        List<String> names = sysAttachmentService.parseOriginalFileNameListJson(originalFileNameListJson);
        SysAttachmentService.SubmissionFiles submissionFiles = sysAttachmentService.createSharedSubmissionFiles(
                files, req.getAdjusterId(), names);
        // 2. 每个股票日志独立绑定附件，整批上传只创建一份物理文件
        return submitBatch(req, submissionFiles);
    }

    /**
     * 按股票聚合完整主项及关系项，并委托股票多单原子提交入口
     *
     * 执行顺序：
     * 1. 复核整批提交身份与主项目标池
     * 2. 逐项构造股票独立申请，拒绝错误分组、多主项及伪造目标和快速流程
     * 3. 原子提交基金批量申请，支持整批共用附件及完整关系项复核
     *
     * @param req 批次股票调库
     * @param submissionFiles 已校验的上传文件上下文，可为空
     * @return 股票批量调库结果
     */
    private BatchStockAdjustDto submitBatch(BatchStockAdjustReq req,
                                            SysAttachmentService.SubmissionFiles submissionFiles) {
        // 复核整批提交身份与主项目标池
        validateSubmitRequest(req);
        Map<String, List<BatchStockAdjustReq.AdjustItem>> byStock = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (BatchStockAdjustReq.AdjustItem item : req.getItems()) {
            // 按股票代码保留全部方向的手工、联动和互斥项
            String code = requireStockCode(item == null ? null : item.getStockCode());
            byStock.computeIfAbsent(code, key -> new ArrayList<>()).add(item);
        }
        List<StockPoolAdjustSubmitReq> requests = new ArrayList<>();
        for (Map.Entry<String, List<BatchStockAdjustReq.AdjustItem>> entry : byStock.entrySet()) {
            // 保留股票归属的报告来源及整批共用材料引用
            requests.add(buildSingleSubmitReq(req, entry.getKey(), entry.getValue()));
        }
        List<StockAdjustSubmitDto> results = stockPoolAdjustService.addBatchAdjustLogList(requests, submissionFiles);
        BatchStockAdjustDto dto = new BatchStockAdjustDto();
        dto.setStockCount(requests.size());
        for (StockAdjustSubmitDto result : results) {
            dto.getLogIds().addAll(result.getAdjustLogIds());
            dto.getAdjustBatchNos().addAll(result.getAdjustBatchNos());
        }
        dto.setSubmitCount(dto.getLogIds().size());
        return dto;
    }

    /**
     * 校验整批提交身份、目标上下文及非空明细
     *
     * 执行顺序：
     * 1. 检查调整人 ID 必须与当前用户一致
     * 2. 检查调整人名称不能为空
     * 3. 未提交股票调库明细时终止处理
     * 4. 重新确认目标池为当前用户可调整的股票叶子池
     *
     * @param req 批次股票调库
     */
    private void validateSubmitRequest(BatchStockAdjustReq req) {
        String userId = StockQueryFilterHelper.requireUserId(req.getCurrentUserId());
        if (!userId.equals(req.getAdjusterId())) {
            throw new BizException("调整人 ID 必须与当前用户一致");
        }
        if (req.getAdjusterName() == null || req.getAdjusterName().trim().isEmpty()) {
            throw new BizException("调整人名称不能为空");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("至少提交一组完整的股票调库明细");
        }
        // 重新确认目标池为当前用户可调整的股票叶子池
        validatePoolContext(userId, req.getPoolId(), req.getDirection());
    }

    /**
     * 校验目标池上下文并返回当前用户可调整池集合
     *
     * 执行顺序：
     * 1. 确认批量 API 方向合法
     * 2. 取得用户直接及角色授予的投资池调整权限
     *
     * @param userId 本次权限复核的操作人 ID
     * @param poolId 目标投资池 ID
     * @param direction 请求中的调整方向编码
     * @return 投资池上下文集合
     */
    private Set<Long> validatePoolContext(String userId, Long poolId, String direction) {
        // 1. 确认批量 API 方向合法
        resolveAdjustMode(direction);
        if (poolId == null || batchStockPoolAdjustMapper.queryEnabledStockLeafPoolCount(poolId) != 1) {
            throw new BizException("目标投资池必须是启用且支持股票的叶子池");
        }
        // 2. 取得用户直接及角色授予的投资池调整权限
        Set<Long> permitted = queryAdjustablePoolIds(userId);
        if (!AdminUserIdUtil.isAdminUser(userId) && !permitted.contains(poolId)) {
            throw new BizException("当前用户无权调整目标投资池");
        }
        return permitted;
    }

    /**
     * 查询用户及角色对应的可调整投资池，管理员使用股票统一口径
     *
     * 执行顺序：
     * 1. 查询用户角色ID列表
     * 2. 按类型查询权限列表
     *
     * @param userId 本次权限复核的操作人 ID
     * @return 用户可调整的池 ID 集合，管理员无需查询权限并返回空集合
     */
    private Set<Long> queryAdjustablePoolIds(String userId) {
        String validatedUserId = StockQueryFilterHelper.requireUserId(userId);
        if (AdminUserIdUtil.isAdminUser(validatedUserId)) {
            return Collections.emptySet();
        }
        Long numericUserId = Long.valueOf(validatedUserId);
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
     * 将批量公开方向转换为股票单笔使用的中文枚举
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
     * 校验并归一化股票代码
     *
     * 执行顺序：
     * 1. 检查股票代码不能为空
     *
     * @param code 证券代码
     * @return 股票代码
     */
    private String requireStockCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new BizException("股票代码不能为空");
        }
        return code.trim();
    }

    /**
     * 构造单股票的一条手工目标池校验请求
     *
     * 执行顺序：
     * 1. 转换公开方向为单笔调库枚举
     *
     * @param batch 批次
     * @param code 证券代码
     * @return 股票调库校验
     */
    private StockAdjustCheckReq buildSingleCheckReq(BatchStockAdjustReq batch, String code) {
        StockAdjustCheckReq req = new StockAdjustCheckReq();
        req.setStockCode(code);
        req.setCurrentUserId(batch.getCurrentUserId());
        StockAdjustCheckReq.CheckItem item = new StockAdjustCheckReq.CheckItem();
        item.setTargetPoolId(batch.getPoolId());
        // 转换公开方向为单笔调库枚举
        item.setAdjustMode(resolveAdjustMode(batch.getDirection()));
        req.setItems(Collections.singletonList(item));
        return req;
    }

    /**
     * 将单笔基础状态失败转换为可定位股票的失败主项
     *
     * 执行顺序：
     * 1. 写入失败项的股票代码、目标池及调整方向，并标记为不可调整
     * 2. 填入实际失败原因，警告和审批候选初始化为空列表，返回该项
     *
     * @param batch 批次
     * @param code 证券代码
     * @param reason 本次校验产生的原因或提示
     * @return 单个调库项的校验结果
     */
    private StockAdjustCheckDto.CheckResultItem buildFailureRow(BatchStockAdjustReq batch, String code, String reason) {
        StockAdjustCheckDto.CheckResultItem row = new StockAdjustCheckDto.CheckResultItem();
        row.setStockCode(code);
        row.setTargetPoolId(batch.getPoolId());
        row.setPoolName(investmentPoolService.queryPoolFullNameMap().get(batch.getPoolId()));
        // 保留失败主项的方向和同组标识
        row.setAdjustMode(resolveAdjustMode(batch.getDirection()));
        row.setItemTag(MANUAL);
        row.setAdjustGroupKey("stock-group-1");
        row.setFailReasons(Collections.singletonList(reason));
        row.setWarnings(Collections.emptyList());
        row.setFlowOptions(Collections.emptyList());
        return row;
    }

    /**
     * 构造股票独立申请，拒绝错误分组、多主项及伪造目标和快速流程
     *
     * 执行顺序：
     * 1. 逐项将批量公开方向转换为股票单笔使用的中文枚举
     *
     * @param batch 批次
     * @param code 证券代码
     * @param items 明细列表
     * @return 股票投资池调库提交
     */
    private StockPoolAdjustSubmitReq buildSingleSubmitReq(BatchStockAdjustReq batch, String code,
                                                          List<BatchStockAdjustReq.AdjustItem> items) {
        StockPoolAdjustSubmitReq req = new StockPoolAdjustSubmitReq();
        req.setStockCode(code);
        req.setAdjustType("手动批量调整");
        req.setAdjustReason(batch.getAdjustReason());
        req.setAdjustAdvice(batch.getAdjustAdvice());
        req.setAdjusterId(batch.getAdjusterId());
        req.setAdjusterName(batch.getAdjusterName());
        int manualCount = 0;
        List<StockPoolAdjustSubmitReq.AdjustItem> singleItems = new ArrayList<>();
        for (BatchStockAdjustReq.AdjustItem item : items) {
            if (!(code + "_stock-group-1").equals(item.getAdjustGroupKey())) {
                throw new BizException("股票调库分组标识不匹配：" + code);
            }
            if (MANUAL.equals(item.getItemTag())) {
                manualCount++;
                // 核实每只股票手工目标和方向与整批申请一致
                if (!batch.getPoolId().equals(item.getTargetPoolId())
                        // 将批量公开方向转换为股票单笔使用的中文枚举
                        || !resolveAdjustMode(batch.getDirection()).equals(item.getAdjustMode())) {
                    throw new BizException("股票手工调整目标池或方向与本次批量申请不一致：" + code);
                }
            }
            if (item.getFlowType() != null
                    && !FlowType.NORMAL_INBOUND.getCode().equals(item.getFlowType())
                    && !FlowType.NORMAL_OUTBOUND.getCode().equals(item.getFlowType())) {
                throw new BizException("股票批量调整只能使用一般审批流程：" + code);
            }
            StockPoolAdjustSubmitReq.AdjustItem single = new StockPoolAdjustSubmitReq.AdjustItem();
            BeanUtils.copyProperties(item, single);
            singleItems.add(single);
        }
        if (manualCount != 1) {
            throw new BizException("每只股票必须包含且仅包含一条手工调整项：" + code);
        }
        req.setItems(singleItems);
        return req;
    }
}
