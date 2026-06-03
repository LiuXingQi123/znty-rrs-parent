package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.exception.BizException;
import com.znty.sirm.mapper.BondPoolAdjustMapper;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.model.AdjustCheckContext;
import com.znty.sirm.model.AdjustCheckDto;
import com.znty.sirm.model.AdjustCheckReq;
import com.znty.sirm.model.AdjustLogDto;
import com.znty.sirm.model.AdjustSubmitDto;
import com.znty.sirm.model.BondInfoBo;
import com.znty.sirm.model.BondInfoDetailDto;
import com.znty.sirm.model.BondInfoDto;
import com.znty.sirm.model.BondPoolAdjustReq;
import com.znty.sirm.model.BondPoolAdjustSubmitReq;
import com.znty.sirm.model.BondPoolStatusDto;
import com.znty.sirm.model.PoolStatusDto;
import com.znty.sirm.model.InvestmentPoolBo;
import com.znty.sirm.model.IpAdjustLogBo;
import com.znty.sirm.model.PoolDto;
import com.znty.sirm.model.PoolRelationBo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 债券池调库业务逻辑
 */
@Service
public class BondPoolAdjustService {

    @Resource
    private BondPoolAdjustMapper bondPoolAdjustMapper;

    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    // 投资池关系类型常量
    private static final String REL_SOURCE        = "source";              // 来源池
    private static final String REL_IN_RESTRICT   = "in_restrict";         // 调入限制池
    private static final String REL_OUT_RESTRICT  = "out_restrict";        // 调出限制池
    private static final String REL_IN_LINKAGE    = "in_linkage";          // 调入联动池
    private static final String REL_OUT_LINKAGE   = "out_linkage";         // 调出联动池
    private static final String REL_IN_MUTEX      = "in_mutex";            // 调入互斥池
    private static final String REL_OUT_MUTEX     = "out_mutex";           // 调出互斥池
    private static final String REL_IN_ELASTIC    = "in_elastic";          // 调入弹性禁投池
    private static final String REL_OUT_ELASTIC   = "out_elastic";         // 调出弹性禁投池

    /** 分页查询债券列表 */
    public PageResult<BondInfoDto> queryBondPage(BondPoolAdjustReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BondInfoBo> entities = bondPoolAdjustMapper.queryBondPage(
                req.getBondCode(), req.getBondShortName(), req.getIssuer());
        PageInfo<BondInfoBo> pageInfo = new PageInfo<>(entities);

        List<BondInfoDto> records = entities.stream().map(this::toBondInfoDto).collect(Collectors.toList());
        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 查询债券详情 */
    public BondInfoDetailDto queryBondDetail(BondPoolAdjustReq req) {
        if (req.getBondId() == null) {
            throw new BizException("债券ID不能为空");
        }
        BondInfoBo bo = bondPoolAdjustMapper.queryBondDetail(req.getBondId());
        if (bo == null) {
            throw new BizException(404, "债券不存在");
        }
        return toBondInfoDetailDto(bo);
    }

    /** 查询可调库/可调出库的投资池列表（树结构由前端组装） */
    public List<PoolDto> queryAdjustPoolList(BondPoolAdjustReq req) {
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools == null || allPools.isEmpty()) {
            return new ArrayList<>();
        }
        List<PoolRelationBo> mutexList = investmentPoolMapper.queryMutexRelationList();
        // 按池 ID 分组互斥关系：调入互斥 / 调出互斥
        Map<Long, List<Long>> inMutexMap = new HashMap<>();
        Map<Long, List<Long>> outMutexMap = new HashMap<>();
        if (mutexList != null) {
            for (PoolRelationBo r : mutexList) {
                if ("in_mutex".equals(r.getRelationType())) {
                    inMutexMap.computeIfAbsent(r.getPoolId(), k -> new ArrayList<>()).add(r.getRelationPoolId());
                } else if ("out_mutex".equals(r.getRelationType())) {
                    outMutexMap.computeIfAbsent(r.getPoolId(), k -> new ArrayList<>()).add(r.getRelationPoolId());
                }
            }
        }
        return allPools.stream().map(p -> toPoolDto(p, inMutexMap, outMutexMap)).collect(Collectors.toList());
    }

    /** BondInfoBo → BondInfoDto */
    private BondInfoDto toBondInfoDto(BondInfoBo bo) {
        BondInfoDto d = new BondInfoDto();
        d.setId(bo.getId());
        d.setBondCode(bo.getSInfoCode());
        d.setBondShortName(bo.getSInfoName());
        d.setIssuer(bo.getBInfoIssuer());
        d.setFullName(bo.getBInfoFullname());
        d.setIssueAmount(bo.getBIssueAmountplan());
        d.setCarryDate(bo.getBInfoCarrydate());
        d.setMaturityDate(bo.getBInfoMaturitydate());
        d.setBondRating(bo.getRatingBond());
        d.setIssuerRating(bo.getRatingBondissuer());
        d.setBondType(bo.getDBondType() != null ? String.valueOf(bo.getDBondType()) : null);
        d.setCurrentRate(null);
        d.setTermStr(bo.getDateExists());
        return d;
    }

    /** BondInfoBo → BondInfoDetailDto */
    private BondInfoDetailDto toBondInfoDetailDto(BondInfoBo bo) {
        BondInfoDetailDto d = new BondInfoDetailDto();
        d.setFullName(bo.getBInfoFullname());
        d.setBondShortName(bo.getSInfoName());
        d.setBondCode(bo.getSInfoCode());
        d.setIssuerName(bo.getBInfoIssuer());
        d.setNibCode(bo.getSWindcodeNib());
        // 交易所代码：优先沪市，其次深市
        if (bo.getSWindcodeSh() != null && !bo.getSWindcodeSh().isEmpty()) {
            d.setExchangeCode(bo.getSWindcodeSh());
        } else {
            d.setExchangeCode(bo.getSWindcodeSz());
        }
        d.setIssueAmount(bo.getBIssueAmountplan());
        d.setCurrentRate(null);
        d.setRemExeTerm(bo.getDateInrightExists());
        d.setCarryDate(bo.getBInfoCarrydate());
        d.setMaturityDate(bo.getBInfoMaturitydate());
        d.setPledgeRatio(bo.getBInfoPledgeRatio());
        d.setRatingAgency(bo.getRatingBondAgency());
        d.setBondRating(bo.getRatingBond());
        d.setIssuerRating(bo.getRatingBondissuer());
        d.setRatingOutlook(bo.getRatingOutlook());
        d.setGuaranteeStatus(bo.getBAgencyGrnttype());
        d.setLeadUnderwriter(bo.getBAgencyName());
        d.setInnerIssuerRating(bo.getInnerIssuerRating());
        d.setBondType(bo.getDBondType() != null ? String.valueOf(bo.getDBondType()) : null);
        d.setPutExeTerm(bo.getDateRedemtionExists());
        d.setCallRemTerm(bo.getDateCallExists());
        d.setInnerGuarantorRating(bo.getInnerGuarantorRating());
        d.setOptRemTerm(bo.getDateInrightExists());
        d.setTermStr(bo.getDateExists());
        d.setFundUsage(bo.getBFundUsage());
        d.setPromptReason(bo.getBPromptReason());
        d.setAnalysis(bo.getBAnalysis());
        return d;
    }

    /** InvestmentPoolBo → PoolDto */
    private PoolDto toPoolDto(InvestmentPoolBo pool,
                              Map<Long, List<Long>> inMutexMap,
                              Map<Long, List<Long>> outMutexMap) {
        PoolDto dto = new PoolDto();
        dto.setId(pool.getId());
        dto.setParentId(pool.getParentId());
        dto.setPoolName(pool.getPoolName());
        dto.setPoolCode(pool.getPoolCode());
        dto.setPoolType(pool.getPoolType());
        dto.setPoolLevel(pool.getPoolLevel());
        dto.setMaxCapacity(pool.getMaxCapacity());
        dto.setCurrentCount(0);
        dto.setInMutexPoolIds(inMutexMap.getOrDefault(pool.getId(), Collections.emptyList()));
        dto.setOutMutexPoolIds(outMutexMap.getOrDefault(pool.getId(), Collections.emptyList()));
        return dto;
    }

    /** 查询债券当前所在池及主体所在池 */
    public BondPoolStatusDto queryBondPoolStatus(BondPoolAdjustReq req) {
        if (req.getBondCode() == null || req.getBondCode().isEmpty()) {
            throw new BizException("债券代码不能为空");
        }
        BondPoolStatusDto dto = new BondPoolStatusDto();
        dto.setBondCurrentPools(bondPoolAdjustMapper.queryBondPoolStatus(req.getBondCode()));
        dto.setIssuerCurrentPools(bondPoolAdjustMapper.queryIssuerPoolStatus(req.getBondCode()));
        return dto;
    }

    /** 提交调库申请 */
    @Transactional(rollbackFor = Exception.class)
    public AdjustSubmitDto addAdjustLog(BondPoolAdjustSubmitReq req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("调库项不能为空");
        }
        List<Long> logIds = new ArrayList<>();
        for (BondPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            IpAdjustLogBo bo = buildAdjustLog(req, item);
            bondPoolAdjustMapper.addAdjustLog(bo);
            logIds.add(bo.getId());
        }
        AdjustSubmitDto dto = new AdjustSubmitDto();
        dto.setBondCode(req.getBondCode());
        dto.setSubmitCount(logIds.size());
        dto.setLogIds(logIds);
        return dto;
    }

    /** 构建调库记录实体 */
    private IpAdjustLogBo buildAdjustLog(BondPoolAdjustSubmitReq req, BondPoolAdjustSubmitReq.AdjustItem item) {
        IpAdjustLogBo bo = new IpAdjustLogBo();
        bo.setBondCode(req.getBondCode());
        bo.setBondShortName(req.getBondShortName());
        bo.setBondType(req.getBondType());
        bo.setAdjustType(req.getAdjustType());
        bo.setAdjustMode(item.getAdjustMode());
        bo.setTargetPoolId(item.getTargetPoolId());
        bo.setTargetPoolName(item.getTargetPoolName());
        bo.setPoolType(item.getPoolType());
        bo.setAuditStatus("00");
        bo.setAdjusterId(req.getAdjusterId());
        bo.setAdjusterName(req.getAdjusterName());
        bo.setAdjustReason(req.getAdjustReason());
        bo.setAdjustAdvice(req.getAdjustAdvice());
        bo.setAttachmentFiles(item.getAttachmentFiles());
        bo.setMaterialFiles(item.getMaterialFiles());
        return bo;
    }

    /** 查询债券的调库记录列表 */
    public List<AdjustLogDto> queryAdjustLogList(BondPoolAdjustReq req) {
        if (req.getBondCode() == null || req.getBondCode().isEmpty()) {
            throw new BizException("债券代码不能为空");
        }
        List<IpAdjustLogBo> logs = bondPoolAdjustMapper.queryAdjustLogList(req.getBondCode());
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取所有目标池及其父级信息以构建路径
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools != null) {
            for (InvestmentPoolBo p : allPools) {
                poolMap.put(p.getId(), p);
            }
        }

        List<AdjustLogDto> result = new ArrayList<>();
        for (IpAdjustLogBo log : logs) {
            AdjustLogDto dto = new AdjustLogDto();
            dto.setId(log.getId());
            dto.setPoolPath(buildPoolPath(log.getTargetPoolId(), poolMap));
            dto.setAdjustType(log.getAdjustType());
            dto.setAdjustMode(log.getAdjustMode());
            dto.setAttachmentFiles(log.getAttachmentFiles());
            dto.setMaterialFiles(log.getMaterialFiles());
            dto.setAuditStatus(log.getAuditStatus());
            dto.setAdjustReason(log.getAdjustReason());
            dto.setAdjustAdvice(log.getAdjustAdvice());
            dto.setSubmitTime(log.getSubmitTime());
            result.add(dto);
        }
        return result;
    }

    /** 构建投资池路径（父级名称/名称） */
    private String buildPoolPath(Long poolId, Map<Long, InvestmentPoolBo> poolMap) {
        InvestmentPoolBo pool = poolMap.get(poolId);
        if (pool == null) {
            return "";
        }
        if (pool.getParentId() != null) {
            InvestmentPoolBo parent = poolMap.get(pool.getParentId());
            if (parent != null && parent.getPoolName() != null) {
                return parent.getPoolName() + "/" + (pool.getPoolName() != null ? pool.getPoolName() : "");
            }
        }
        return pool.getPoolName() != null ? pool.getPoolName() : "";
    }

    // ─────────────── 调库校验 ───────────────

    /** 校验债券调库可行性 */
    public AdjustCheckDto checkAdjust(AdjustCheckReq req) {
        if (req.getBondCode() == null || req.getBondCode().isEmpty()) {
            throw new BizException("债券代码不能为空");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("调库项不能为空");
        }

        // 加载债券基础信息
        BondInfoBo bondInfo = bondPoolAdjustMapper.queryBondDetail(req.getBondId());
        if (bondInfo == null) {
            throw new BizException("债券不存在");
        }

        // 加载全量投资池 Map
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools != null) {
            for (InvestmentPoolBo p : allPools) {
                poolMap.put(p.getId(), p);
            }
        }

        // 加载债券当前有效所在池 ID
        List<Long> currentPoolIdList = bondPoolAdjustMapper.queryBondCurrentPoolIds(req.getBondCode());
        Set<Long> currentPoolIds = new HashSet<>(currentPoolIdList != null ? currentPoolIdList : Collections.emptyList());

        // 查询债券是否存在进行中的调库流程
        boolean hasPendingProcess = bondPoolAdjustMapper.queryBondHasPendingProcess(req.getBondCode());

        // 加载全量投资池关系配置
        List<PoolRelationBo> allRelations = bondPoolAdjustMapper.queryAllPoolRelations();
        Map<Long, Map<String, List<Long>>> poolRelationMap = buildPoolRelationMap(allRelations);

        // 逐项构建上下文并执行三层校验
        List<AdjustCheckDto.CheckResultItem> resultItems = new ArrayList<>();
        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            int poolCurrentCount = bondPoolAdjustMapper.queryPoolCurrentCount(item.getTargetPoolId());
            AdjustCheckContext ctx = buildCheckContext(bondInfo, item, poolMap, currentPoolIds, poolCurrentCount, poolRelationMap);
            ctx.setHasPendingProcess(hasPendingProcess);

            List<String> failures = new ArrayList<>();
            failures.addAll(checkPreConditions(ctx));
            if ("调入".equals(item.getAdjustMode())) {
                failures.addAll(checkInConditions(ctx));
            } else {
                failures.addAll(checkOutConditions(ctx));
            }

            AdjustCheckDto.CheckResultItem resultItem = new AdjustCheckDto.CheckResultItem();
            resultItem.setTargetPoolId(item.getTargetPoolId());
            resultItem.setPoolName(buildPoolPath(item.getTargetPoolId(), poolMap));
            resultItem.setPoolType(item.getPoolType());
            resultItem.setAdjustMode(item.getAdjustMode());
            resultItem.setCanAdjust(failures.isEmpty());
            resultItem.setFailReasons(failures);
            resultItems.add(resultItem);
        }

        AdjustCheckDto dto = new AdjustCheckDto();
        dto.setItems(resultItems);
        return dto;
    }

    /** 获取调库校验基础参数上下文 */
    public AdjustCheckContext buildCheckContext(BondInfoBo bondInfo,
                                               AdjustCheckReq.CheckItem item,
                                               Map<Long, InvestmentPoolBo> poolMap,
                                               Set<Long> currentPoolIds,
                                               int poolCurrentCount,
                                               Map<Long, Map<String, List<Long>>> poolRelationMap) {
        InvestmentPoolBo targetPool = poolMap.get(item.getTargetPoolId());
        InvestmentPoolBo parentPool = (targetPool != null && targetPool.getParentId() != null)
                ? poolMap.get(targetPool.getParentId()) : null;
        Map<String, List<Long>> targetPoolRelations = poolRelationMap.getOrDefault(
                item.getTargetPoolId(), Collections.emptyMap());

        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setBondInfo(bondInfo);
        ctx.setTargetPool(targetPool);
        ctx.setParentPool(parentPool);
        ctx.setCurrentPoolIds(currentPoolIds);
        ctx.setPoolCurrentCount(poolCurrentCount);
        ctx.setAdjustMode(item.getAdjustMode());
        ctx.setTargetPoolRelations(targetPoolRelations);
        ctx.setPoolMap(poolMap);
        return ctx;
    }

    /** 前置校验（与调库方向无关的通用校验） */
    public List<String> checkPreConditions(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        addIfFailed(failures, preCheckBondExpired(ctx));
        addIfFailed(failures, preCheckPendingProcess(ctx));
        return failures;
    }

    /** 调入校验 */
    public List<String> checkInConditions(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        addIfFailed(failures, inCheckBondAlreadyInPool(ctx));
        addIfFailed(failures, inCheckPoolCapacity(ctx));
        addIfFailed(failures, inCheckSourcePool(ctx));
        addIfFailed(failures, inCheckRestrictPool(ctx));
        addIfFailed(failures, inCheckLinkagePool(ctx));
        addIfFailed(failures, inCheckMutexPool(ctx));
        addIfFailed(failures, inCheckElasticPool(ctx));
        return failures;
    }

    /** 调出校验 */
    public List<String> checkOutConditions(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        addIfFailed(failures, outCheckBondNotInPool(ctx));
        addIfFailed(failures, outCheckRestrictPool(ctx));
        addIfFailed(failures, outCheckLinkagePool(ctx));
        addIfFailed(failures, outCheckMutexPool(ctx));
        addIfFailed(failures, outCheckElasticPool(ctx));
        return failures;
    }

    // ─── 前置校验规则 ───

    /** 债券是否已到期 */
    private String preCheckBondExpired(AdjustCheckContext ctx) {
        String maturityDate = ctx.getBondInfo().getBInfoMaturitydate();
        if (maturityDate != null && !maturityDate.isEmpty()) {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date());
            if (maturityDate.compareTo(today) < 0) {
                return "债券已到期，不支持调库操作";
            }
        }
        return null;
    }

    /** 债券是否存在进行中的调库流程（待审核或驳回待修改） */
    private String preCheckPendingProcess(AdjustCheckContext ctx) {
        if (ctx.isHasPendingProcess()) {
            return "债券存在进行中的调库流程（待审核或驳回待修改），请等待流程结束后再发起调库";
        }
        return null;
    }

    // ─── 调入校验规则 ───

    /** 债券是否已在目标池中（有效入池状态） */
    private String inCheckBondAlreadyInPool(AdjustCheckContext ctx) {
        Long poolId = ctx.getTargetPool() != null ? ctx.getTargetPool().getId() : null;
        if (poolId != null && ctx.getCurrentPoolIds().contains(poolId)) {
            return "债券已在目标投资池中，无需重复调入";
        }
        return null;
    }

    /** 目标池是否已达上限 */
    private String inCheckPoolCapacity(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool != null && pool.getMaxCapacity() != null && pool.getMaxCapacity() > 0
                && ctx.getPoolCurrentCount() >= pool.getMaxCapacity()) {
            return "目标投资池已达持仓上限（" + pool.getMaxCapacity() + "），无法调入";
        }
        return null;
    }

    // ─── 调出校验规则 ───

    /** 债券是否不在目标池中（不在池中则不可调出） */
    private String outCheckBondNotInPool(AdjustCheckContext ctx) {
        Long poolId = ctx.getTargetPool() != null ? ctx.getTargetPool().getId() : null;
        if (poolId == null || !ctx.getCurrentPoolIds().contains(poolId)) {
            return "债券当前不在该投资池中，无法调出";
        }
        return null;
    }

    // ─── 调入校验规则（关系型） ───

    /** 来源池：目标池配置了来源池时，债券须当前在其中至少一个来源池 */
    private String inCheckSourcePool(AdjustCheckContext ctx) {
        List<Long> sourcePools = ctx.getTargetPoolRelations().get(REL_SOURCE);
        if (sourcePools == null || sourcePools.isEmpty()) {
            return null;
        }
        boolean inAny = sourcePools.stream().anyMatch(ctx.getCurrentPoolIds()::contains);
        if (!inAny) {
            String names = poolNames(sourcePools, ctx);
            return "目标池配置了来源池限制，债券须先在以下池中：" + names;
        }
        return null;
    }

    /** 调入限制池：债券在限制池中则不可调入 */
    private String inCheckRestrictPool(AdjustCheckContext ctx) {
        return checkBlockedByPools(ctx, REL_IN_RESTRICT, "调入限制池");
    }

    /** 调入联动池：债券须已在所有联动池中，确保同步状态 */
    private String inCheckLinkagePool(AdjustCheckContext ctx) {
        List<Long> linkagePools = ctx.getTargetPoolRelations().get(REL_IN_LINKAGE);
        if (linkagePools == null || linkagePools.isEmpty()) {
            return null;
        }
        List<Long> missing = linkagePools.stream()
                .filter(id -> !ctx.getCurrentPoolIds().contains(id))
                .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            String names = poolNames(missing, ctx);
            return "调入联动池校验未通过，请同时将债券调入以下联动池：" + names;
        }
        return null;
    }

    /** 调入互斥池：债券在互斥池中则不可调入 */
    private String inCheckMutexPool(AdjustCheckContext ctx) {
        return checkBlockedByPools(ctx, REL_IN_MUTEX, "调入互斥池");
    }

    /** 调入弹性禁投池：债券在弹性禁投池中则不可调入 */
    private String inCheckElasticPool(AdjustCheckContext ctx) {
        return checkBlockedByPools(ctx, REL_IN_ELASTIC, "调入弹性禁投池");
    }

    // ─── 调出校验规则（关系型） ───

    /** 调出限制池：债券在限制池中则不可调出 */
    private String outCheckRestrictPool(AdjustCheckContext ctx) {
        return checkBlockedByPools(ctx, REL_OUT_RESTRICT, "调出限制池");
    }

    /** 调出联动池：债券须已在所有联动池中，才可同步调出 */
    private String outCheckLinkagePool(AdjustCheckContext ctx) {
        List<Long> linkagePools = ctx.getTargetPoolRelations().get(REL_OUT_LINKAGE);
        if (linkagePools == null || linkagePools.isEmpty()) {
            return null;
        }
        List<Long> missing = linkagePools.stream()
                .filter(id -> !ctx.getCurrentPoolIds().contains(id))
                .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            String names = poolNames(missing, ctx);
            return "调出联动池校验未通过，请同时将债券从以下联动池调出：" + names;
        }
        return null;
    }

    /** 调出互斥池：债券在互斥池中则不可调出 */
    private String outCheckMutexPool(AdjustCheckContext ctx) {
        return checkBlockedByPools(ctx, REL_OUT_MUTEX, "调出互斥池");
    }

    /** 调出弹性禁投池：债券在弹性禁投池中则不可调出 */
    private String outCheckElasticPool(AdjustCheckContext ctx) {
        return checkBlockedByPools(ctx, REL_OUT_ELASTIC, "调出弹性禁投池");
    }

    // ─── 共用工具 ───

    /**
     * 通用：检查债券是否在指定关系类型的池中（在则阻断）
     * @param relationType 关系类型常量
     * @param label        用于错误消息的中文标签
     */
    private String checkBlockedByPools(AdjustCheckContext ctx, String relationType, String label) {
        List<Long> relPools = ctx.getTargetPoolRelations().get(relationType);
        if (relPools == null || relPools.isEmpty()) {
            return null;
        }
        List<Long> blocked = relPools.stream()
                .filter(ctx.getCurrentPoolIds()::contains)
                .collect(Collectors.toList());
        if (!blocked.isEmpty()) {
            return "债券在" + label + "中，无法操作：" + poolNames(blocked, ctx);
        }
        return null;
    }

    /** 将池 ID 列表转为"父级/子级"路径名称，以顿号连接 */
    private String poolNames(List<Long> poolIds, AdjustCheckContext ctx) {
        return poolIds.stream()
                .map(id -> buildPoolPath(id, ctx.getPoolMap()))
                .collect(Collectors.joining("、"));
    }

    /** 构建全量投资池关系 Map：poolId → relationType → List<relationPoolId> */
    private Map<Long, Map<String, List<Long>>> buildPoolRelationMap(List<PoolRelationBo> relations) {
        Map<Long, Map<String, List<Long>>> map = new HashMap<>();
        if (relations == null) {
            return map;
        }
        for (PoolRelationBo r : relations) {
            map.computeIfAbsent(r.getPoolId(), k -> new HashMap<>())
               .computeIfAbsent(r.getRelationType(), k -> new ArrayList<>())
               .add(r.getRelationPoolId());
        }
        return map;
    }

    /** 若 reason 非空则加入失败列表 */
    private void addIfFailed(List<String> failures, String reason) {
        if (reason != null) {
            failures.add(reason);
        }
    }

}
