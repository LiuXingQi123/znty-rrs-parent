package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.exception.BizException;
import com.znty.sirm.mapper.SecurityPoolAdjustMapper;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.model.AdjustCheckContext;
import com.znty.sirm.model.AdjustCheckDto;
import com.znty.sirm.model.AdjustCheckReq;
import com.znty.sirm.model.AdjustLogDto;
import com.znty.sirm.model.AdjustSubmitDto;
import com.znty.sirm.model.SecurityInfoBo;
import com.znty.sirm.model.SecurityInfoDetailDto;
import com.znty.sirm.model.SecurityInfoDto;
import com.znty.sirm.model.SecurityPoolAdjustReq;
import com.znty.sirm.model.SecurityPoolAdjustSubmitReq;
import com.znty.sirm.model.SecurityPoolStatusDto;
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
 * 证券池调库业务逻辑
 *
 * <p>核心功能：
 * <ul>
 *   <li>证券/投资池查询：证券列表分页、证券详情、可调投资池列表、当前入池状态</li>
 *   <li>调库申请提交：写入调库记录，初始状态为"待审核"</li>
 *   <li>调库可行性校验（checkAdjust）：对用户选择的每个调库项执行三层校验，
 *       并根据投资池关系配置自动生成联动/互斥项追加校验</li>
 * </ul>
 *
 * <p>校验结构：
 * <ul>
 *   <li>调入校验（checkInConditions）：前置规则（到期、流程进行中）+ 调入方向规则
 *       （重复入池、容量上限、来源池、限制池、弹性禁投池）</li>
 *   <li>调出校验（checkOutConditions）：前置规则（到期、流程进行中）+ 调出方向规则
 *       （未入池、限制池、互斥池、弹性禁投池）</li>
 * </ul>
 *
 * <p>自动生成项规则：
 * <ul>
 *   <li>联动池（linkage）：调入/调出某池时，其配置的联动池需同步调入/调出，由系统自动附加</li>
 *   <li>互斥池（mutex）：调入某池时，若证券当前已在该池的互斥池中，系统自动附加对互斥池的调出项</li>
 * </ul>
 */
@Service
public class SecurityPoolAdjustService {

    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    // ─── 投资池关系类型常量（对应 ip_pool_relation.relation_type 字段值） ───
    private static final String REL_SOURCE       = "source";           // 来源池：调入目标池须先在来源池中
    private static final String REL_IN_RESTRICT  = "in_restrict";      // 调入限制池：证券在此池则不可调入目标池
    private static final String REL_OUT_RESTRICT = "out_restrict";     // 调出限制池：证券在此池则不可调出目标池
    private static final String REL_IN_LINKAGE   = "in_linked";        // 调入联动池：调入目标池时需同步调入此池
    private static final String REL_OUT_LINKAGE  = "out_linked";       // 调出联动池：调出目标池时需同步调出此池
    private static final String REL_IN_MUTEX     = "in_mutex";         // 调入互斥池：目标池与此池互斥，调入前需先调出互斥池
    private static final String REL_OUT_MUTEX    = "out_mutex";        // 调出互斥池：证券在互斥池中则不可调出目标池
    private static final String REL_IN_ELASTIC   = "in_soft_restrict"; // 调入弹性禁投池：证券在此池则不可调入目标池
    private static final String REL_OUT_ELASTIC  = "out_soft_restrict";// 调出弹性禁投池：证券在此池则不可调出目标池

    // ═══════════════════════════════════════════════════════════
    //  查询类接口
    // ═══════════════════════════════════════════════════════════

    /**
     * 分页查询证券列表
     *
     * @param req 查询条件（证券代码、简称、发行人，均为模糊匹配）
     */
    public PageResult<SecurityInfoDto> querySecurityPage(SecurityPoolAdjustReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SecurityInfoBo> entities = securityPoolAdjustMapper.querySecurityPage(
                req.getSecurityCode(), req.getSecurityShortName(), req.getIssuer());
        PageInfo<SecurityInfoBo> pageInfo = new PageInfo<>(entities);

        List<SecurityInfoDto> records = entities.stream().map(this::toSecurityInfoDto).collect(Collectors.toList());
        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 查询证券详情（调库页面顶部基础信息展示）
     *
     * @param req 需携带 securityId
     */
    public SecurityInfoDetailDto querySecurityDetail(SecurityPoolAdjustReq req) {
        if (req.getSecurityId() == null) {
            throw new BizException("证券ID不能为空");
        }
        SecurityInfoBo bo = securityPoolAdjustMapper.querySecurityDetail(req.getSecurityId());
        if (bo == null) {
            throw new BizException(404, "证券不存在");
        }
        return toSecurityInfoDetailDto(bo);
    }

    /**
     * 查询可调入/可调出的投资池列表（含互斥关系，树结构由前端自行组装）
     *
     * @param req 暂无过滤条件，返回全量启用池
     */
    public List<PoolDto> queryAdjustPoolList(SecurityPoolAdjustReq req) {
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools == null || allPools.isEmpty()) {
            return new ArrayList<>();
        }

        // 查询所有互斥关系，按池 ID 分组后挂载到对应 PoolDto，供前端渲染调库约束提示
        List<PoolRelationBo> mutexList = investmentPoolMapper.queryMutexRelationList();
        Map<Long, List<Long>> inMutexMap  = new HashMap<>();
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

    /**
     * 查询证券当前所在池及其主体（发行人）当前所在池
     *
     * @param req 需携带 securityCode
     */
    public SecurityPoolStatusDto querySecurityPoolStatus(SecurityPoolAdjustReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        SecurityPoolStatusDto dto = new SecurityPoolStatusDto();
        dto.setSecurityCurrentPools(securityPoolAdjustMapper.querySecurityPoolStatus(req.getSecurityCode()));
        dto.setIssuerCurrentPools(securityPoolAdjustMapper.queryIssuerPoolStatus(req.getSecurityCode()));
        return dto;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库申请提交
    // ═══════════════════════════════════════════════════════════

    /**
     * 提交调库申请，每个调库项写入一条 ip_adjust_log 记录，初始审核状态为"00（待审核）"
     *
     * @param req 调库申请，包含证券信息及一个或多个调库项
     */
    @Transactional(rollbackFor = Exception.class)
    public AdjustSubmitDto addAdjustLog(SecurityPoolAdjustSubmitReq req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("调库项不能为空");
        }
        List<Long> logIds = new ArrayList<>();
        for (SecurityPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            IpAdjustLogBo bo = buildAdjustLog(req, item);
            securityPoolAdjustMapper.addAdjustLog(bo);
            logIds.add(bo.getId());
        }
        AdjustSubmitDto dto = new AdjustSubmitDto();
        dto.setSecurityCode(req.getSecurityCode());
        dto.setSubmitCount(logIds.size());
        dto.setLogIds(logIds);
        return dto;
    }

    /**
     * 查询证券的历史调库记录列表（全量，不分页）
     *
     * @param req 需携带 securityCode
     */
    public List<AdjustLogDto> queryAdjustLogList(SecurityPoolAdjustReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        List<IpAdjustLogBo> logs = securityPoolAdjustMapper.queryAdjustLogList(req.getSecurityCode());
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }

        // 加载全量投资池用于构建"父级/子级"路径名称
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

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 入口
    // ═══════════════════════════════════════════════════════════

    /**
     * 校验证券调库可行性（核心入口）
     *
     * <p>分四个阶段顺序执行：
     * <ol>
     *   <li><b>前置校验</b>：对请求入参做合法性检查，无 DB 操作，不通过直接抛异常</li>
     *   <li><b>参数初始化</b>：集中执行所有 DB 查询，将本次调用所需数据封装为
     *       {@link AdjustSharedData}，后续阶段直接读取，不再单独查库</li>
     *   <li><b>调入校验</b>：处理全部调入方向的手动项（含前置规则 + 调入规则），
     *       并根据池关系自动追加联动调入项、互斥配套调出项</li>
     *   <li><b>调出校验</b>：处理全部调出方向的手动项（含前置规则 + 调出规则），
     *       并根据池关系自动追加联动调出项（已被第三阶段覆盖的自动项跳过，不重复生成）</li>
     * </ol>
     *
     * @param req 包含证券代码和一个或多个用户选择的调库项
     * @return 包含手动项和自动项的完整校验结果列表
     */
    public AdjustCheckDto checkAdjust(AdjustCheckReq req) {

        // ══ 第一阶段：前置校验 ══
        validateCheckAdjustReq(req);

        // ══ 第二阶段：参数初始化 ══
        AdjustSharedData shared = loadSharedData(req);

        // 预先将全部手动选择项注册到"已覆盖"集合，确保第三、四阶段生成的自动项
        // 不会与用户手动选择的项产生重复结果（手动项优先，自动项跳过已覆盖的 key）
        Set<String> coveredKeys = new HashSet<>();
        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            coveredKeys.add(item.getTargetPoolId() + "_" + item.getAdjustMode());
        }

        // ══ 第三阶段：调入校验 ══
        List<AdjustCheckDto.CheckResultItem> resultItems = new ArrayList<>(
                executeInAdjustCheck(req, shared, coveredKeys));

        // ══ 第四阶段：调出校验 ══
        resultItems.addAll(executeOutAdjustCheck(req, shared, coveredKeys));

        AdjustCheckDto dto = new AdjustCheckDto();
        dto.setItems(resultItems);
        return dto;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 四阶段实现
    // ═══════════════════════════════════════════════════════════

    /**
     * 第一阶段：前置校验
     *
     * <p>对请求入参进行合法性检查，均为内存操作，不涉及任何 DB 查询。
     * 任意一项不通过则直接抛出异常，终止后续流程。
     *
     * @param req 调库校验请求
     */
    private void validateCheckAdjustReq(AdjustCheckReq req) {
        if (req.getSecurityCode() == null || req.getSecurityCode().isEmpty()) {
            throw new BizException("证券代码不能为空");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("调库项不能为空");
        }
    }

    /**
     * 第二阶段：参数初始化
     *
     * <p>集中执行本次校验所需的全部 DB 查询，构建 {@link AdjustSharedData}。
     * 第三、四阶段直接从 shared 中读取数据，无需重复查库。
     * 证券存在性校验也在此阶段完成（查到 null 则抛异常）。
     *
     * @param req 调库校验请求（需携带 securityId、securityCode）
     * @return 封装了本次校验全量共享数据的 AdjustSharedData
     */
    private AdjustSharedData loadSharedData(AdjustCheckReq req) {
        // 证券基础信息（兼含存在性校验）
        SecurityInfoBo securityInfo = securityPoolAdjustMapper.querySecurityDetail(req.getSecurityId());
        if (securityInfo == null) {
            throw new BizException("证券不存在");
        }

        // 全量投资池，构建 ID → Bo 索引，供后续快速查找池详情
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools != null) {
            for (InvestmentPoolBo p : allPools) {
                poolMap.put(p.getId(), p);
            }
        }

        // 证券当前有效入池 ID 集合（audit_status='20' 表示已生效）
        List<Long> currentPoolIdList = securityPoolAdjustMapper.querySecurityCurrentPoolIds(req.getSecurityCode());
        Set<Long> currentPoolIds = new HashSet<>(currentPoolIdList != null ? currentPoolIdList : Collections.emptyList());

        // 全量投资池关系配置，构建三层嵌套 Map（poolId → relationType → 关联池列表）
        Map<Long, Map<String, List<Long>>> poolRelationMap = buildPoolRelationMap(
                securityPoolAdjustMapper.queryAllPoolRelations());

        // 提取本次请求中调入/调出各自涉及的目标池 ID 集合，供互斥冲突校验使用
        Set<Long> requestInPoolIds  = new HashSet<>();
        Set<Long> requestOutPoolIds = new HashSet<>();
        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            if ("调入".equals(item.getAdjustMode())) {
                requestInPoolIds.add(item.getTargetPoolId());
            } else if ("调出".equals(item.getAdjustMode())) {
                requestOutPoolIds.add(item.getTargetPoolId());
            }
        }

        // 证券级别的状态标志（整个请求期间保持不变，统一在此查询）
        return new AdjustSharedData(
                securityInfo,
                poolMap,
                currentPoolIds,
                poolRelationMap,
                securityPoolAdjustMapper.querySecurityHasPendingProcess(req.getSecurityCode()),
                securityPoolAdjustMapper.querySecurityInObservePool(req.getSecurityCode()),
                securityPoolAdjustMapper.queryIssuerInObservePool(req.getSecurityCode()),
                requestInPoolIds,
                requestOutPoolIds
        );
    }

    /**
     * 第三阶段：调入校验
     *
     * <p>遍历请求中全部调入方向的手动项，逐项执行前置校验 + 调入校验，
     * 并根据目标池的关系配置自动追加以下两类附加项：
     * <ul>
     *   <li><b>联动调入项（linkage）</b>：目标池配置的联动池（in_linked）需同步调入</li>
     *   <li><b>互斥配套调出项（mutex）</b>：若证券当前已在目标池的互斥池（in_mutex）中，
     *       需同步调出该互斥池，两池不可同时持有</li>
     * </ul>
     *
     * @param req         调库校验请求
     * @param shared      本次校验的共享数据
     * @param coveredKeys 已覆盖的"池ID_方向"集合，用于自动项去重
     * @return 调入方向的全部校验结果（手动项 + 自动项）
     */
    private List<AdjustCheckDto.CheckResultItem> executeInAdjustCheck(
            AdjustCheckReq req, AdjustSharedData shared, Set<String> coveredKeys) {

        List<AdjustCheckDto.CheckResultItem> results = new ArrayList<>();

        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            if (!"调入".equals(item.getAdjustMode())) {
                continue;
            }

            // 手动调入项：前置校验 + 调入校验
            int poolCurrentCount = securityPoolAdjustMapper.queryPoolCurrentCount(item.getTargetPoolId());
            AdjustCheckContext ctx = buildCheckContext(item, poolCurrentCount, shared);
            List<String> failures = checkInConditions(ctx);

            AdjustCheckDto.CheckResultItem resultItem = new AdjustCheckDto.CheckResultItem();
            resultItem.setTargetPoolId(item.getTargetPoolId());
            resultItem.setPoolName(buildPoolPath(item.getTargetPoolId(), shared.poolMap));
            resultItem.setPoolType(item.getPoolType());
            resultItem.setAdjustMode("调入");
            resultItem.setItemTag("manual");
            resultItem.setCanAdjust(failures.isEmpty());
            resultItem.setFailReasons(failures);
            results.add(resultItem);

            // 取出目标池的关系配置，用于生成自动项
            Map<String, List<Long>> relations = shared.poolRelationMap.getOrDefault(
                    item.getTargetPoolId(), Collections.emptyMap());

            // 联动调入：目标池调入时，其联动池（in_linked）需同步调入
            List<Long> inLinkage = relations.get(REL_IN_LINKAGE);
            if (inLinkage != null) {
                for (Long linkedId : inLinkage) {
                    // coveredKeys.add 返回 true 表示该 key 是首次出现，生成自动项
                    if (coveredKeys.add(linkedId + "_调入")) {
                        results.add(buildAutoResultItem(linkedId, "调入", "linkage", shared));
                    }
                }
            }

            // 互斥配套调出：目标池调入时，若证券已在其互斥池（in_mutex）中，
            // 则需同步调出该互斥池，由系统自动追加调出校验项
            List<Long> inMutex = relations.get(REL_IN_MUTEX);
            if (inMutex != null) {
                for (Long mutexId : inMutex) {
                    // 证券不在该互斥池中则无需生成配套调出项
                    if (!shared.currentPoolIds.contains(mutexId)) {
                        continue;
                    }
                    if (coveredKeys.add(mutexId + "_调出")) {
                        results.add(buildAutoResultItem(mutexId, "调出", "mutex", shared));
                    }
                }
            }
        }

        return results;
    }

    /**
     * 第四阶段：调出校验
     *
     * <p>遍历请求中全部调出方向的手动项，逐项执行前置校验 + 调出校验，
     * 并根据目标池的关系配置自动追加以下附加项：
     * <ul>
     *   <li><b>联动调出项（linkage）</b>：目标池配置的联动池（out_linked）需同步调出</li>
     * </ul>
     *
     * <p>注意：第三阶段由互斥关系生成的调出项已加入 coveredKeys，
     * 若调出方向的手动项恰好与之重叠，以手动项为准（手动项在 coveredKeys 初始化时已注册，
     * 不会被跳过）；联动调出自动项若已被覆盖则跳过。
     *
     * @param req         调库校验请求
     * @param shared      本次校验的共享数据
     * @param coveredKeys 已覆盖的"池ID_方向"集合，用于自动项去重
     * @return 调出方向的全部校验结果（手动项 + 自动项）
     */
    private List<AdjustCheckDto.CheckResultItem> executeOutAdjustCheck(
            AdjustCheckReq req, AdjustSharedData shared, Set<String> coveredKeys) {

        List<AdjustCheckDto.CheckResultItem> results = new ArrayList<>();

        for (AdjustCheckReq.CheckItem item : req.getItems()) {
            if (!"调出".equals(item.getAdjustMode())) {
                continue;
            }

            // 手动调出项：前置校验 + 调出校验
            int poolCurrentCount = securityPoolAdjustMapper.queryPoolCurrentCount(item.getTargetPoolId());
            AdjustCheckContext ctx = buildCheckContext(item, poolCurrentCount, shared);
            List<String> failures = checkOutConditions(ctx);

            AdjustCheckDto.CheckResultItem resultItem = new AdjustCheckDto.CheckResultItem();
            resultItem.setTargetPoolId(item.getTargetPoolId());
            resultItem.setPoolName(buildPoolPath(item.getTargetPoolId(), shared.poolMap));
            resultItem.setPoolType(item.getPoolType());
            resultItem.setAdjustMode("调出");
            resultItem.setItemTag("manual");
            resultItem.setCanAdjust(failures.isEmpty());
            resultItem.setFailReasons(failures);
            results.add(resultItem);

            // 联动调出：目标池调出时，其联动池（out_linked）需同步调出
            Map<String, List<Long>> relations = shared.poolRelationMap.getOrDefault(
                    item.getTargetPoolId(), Collections.emptyMap());
            List<Long> outLinkage = relations.get(REL_OUT_LINKAGE);
            if (outLinkage != null) {
                for (Long linkedId : outLinkage) {
                    if (coveredKeys.add(linkedId + "_调出")) {
                        results.add(buildAutoResultItem(linkedId, "调出", "linkage", shared));
                    }
                }
            }
        }

        return results;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 方向校验入口（含前置规则）
    // ═══════════════════════════════════════════════════════════

    /**
     * 调入校验：前置规则 + 调入方向规则
     *
     * <p>校验顺序：证券到期 → 流程进行中 → 重复入池 → 容量上限 → 来源池 → 调入限制池 → 调入弹性禁投池
     *
     * @param ctx 调库校验上下文
     * @return 不通过的失败原因列表，通过则返回空列表
     */
    public List<String> checkInConditions(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 前置检查：证券是否已到期
        addIfFailed(failures, preCheckSecurityExpired(ctx));
        // 前置检查：是否存在待处理流程
        addIfFailed(failures, preCheckPendingProcess(ctx));
        // 入池检查：证券是否已在目标池中
        addIfFailed(failures, inCheckSecurityAlreadyInPool(ctx));
        // 入池检查：目标池容量是否已满
        addIfFailed(failures, inCheckPoolCapacity(ctx));
        // 入池检查：来源池是否满足要求
        addIfFailed(failures, inCheckSourcePool(ctx));
        // 入池检查：是否触碰禁投池限制
        addIfFailed(failures, inCheckRestrictPool(ctx));
        // 入池检查：本次请求中是否同时勾选了互斥池（不可同时调入）
        addIfFailed(failures, inCheckMutexConflict(ctx));
        // 入池检查：是否满足弹性池条件
        addIfFailed(failures, inCheckElasticPool(ctx));
        return failures;
    }

    /**
     * 调出校验：前置规则 + 调出方向规则
     *
     * <p>校验顺序：证券到期 → 流程进行中 → 未入池 → 调出限制池 → 调出互斥池 → 调出弹性禁投池
     *
     * @param ctx 调库校验上下文
     * @return 不通过的失败原因列表，通过则返回空列表
     */
    public List<String> checkOutConditions(AdjustCheckContext ctx) {
        List<String> failures = new ArrayList<>();
        // 前置检查：证券是否已到期
        addIfFailed(failures, preCheckSecurityExpired(ctx));
        // 前置检查：是否存在待处理流程
        addIfFailed(failures, preCheckPendingProcess(ctx));
        // 出池检查：证券是否不在来源池中
        addIfFailed(failures, outCheckSecurityNotInPool(ctx));
        // 出池检查：是否触碰禁投池限制
        addIfFailed(failures, outCheckRestrictPool(ctx));
        // 出池检查：是否与互斥池冲突（证券当前在互斥池中）
        addIfFailed(failures, outCheckMutexPool(ctx));
        // 出池检查：本次请求中是否同时勾选了互斥池（不可同时调出）
        addIfFailed(failures, outCheckMutexConflict(ctx));
        // 出池检查：是否满足弹性池条件
        addIfFailed(failures, outCheckElasticPool(ctx));
        return failures;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 前置校验规则
    // ═══════════════════════════════════════════════════════════

    /**
     * 规则：证券是否已到期
     *
     * <p>到期日（b_info_maturitydate）早于今日则视为已到期，禁止发起任何调库操作。
     */
    private String preCheckSecurityExpired(AdjustCheckContext ctx) {
        String maturityDate = ctx.getSecurityInfo().getBInfoMaturitydate();
        if (maturityDate != null && !maturityDate.isEmpty()) {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date());
            if (maturityDate.compareTo(today) < 0) {
                return "证券已到期，不支持调库操作";
            }
        }
        return null;
    }

    /**
     * 规则：证券是否存在进行中的调库流程
     *
     * <p>audit_status 为"00（待审核）"或"11（驳回待修改）"的记录视为进行中，
     * 需等待该流程终结后方可再次发起调库，避免并发操作导致入池状态混乱。
     */
    private String preCheckPendingProcess(AdjustCheckContext ctx) {
        if (ctx.isHasPendingProcess()) {
            return "证券存在进行中的调库流程（待审核或驳回待修改），请等待流程结束后再发起调库";
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 调入校验规则
    // ═══════════════════════════════════════════════════════════

    /**
     * 规则：证券是否已在目标池中
     *
     * <p>以 ip_pool_status 中 audit_status='20' 的有效记录为准，重复调入无实际意义。
     */
    private String inCheckSecurityAlreadyInPool(AdjustCheckContext ctx) {
        Long poolId = ctx.getTargetPool() != null ? ctx.getTargetPool().getId() : null;
        if (poolId != null && ctx.getCurrentPoolIds().contains(poolId)) {
            return "证券已在目标投资池中，无需重复调入";
        }
        return null;
    }

    /**
     * 规则：目标池是否已达持仓上限
     *
     * <p>maxCapacity 为 null 或 0 时表示不限制数量；
     * 当前有效证券数量（poolCurrentCount）≥ 上限时拒绝调入。
     */
    private String inCheckPoolCapacity(AdjustCheckContext ctx) {
        InvestmentPoolBo pool = ctx.getTargetPool();
        if (pool != null && pool.getMaxCapacity() != null && pool.getMaxCapacity() > 0
                && ctx.getPoolCurrentCount() >= pool.getMaxCapacity()) {
            return "目标投资池已达持仓上限（" + pool.getMaxCapacity() + "），无法调入";
        }
        return null;
    }

    /**
     * 规则：来源池限制
     *
     * <p>若目标池配置了来源池（source），则证券必须当前已在其中至少一个来源池中，
     * 满足"从低级库向高级库晋升"等分层调库规则。未配置来源池时不做限制。
     */
    private String inCheckSourcePool(AdjustCheckContext ctx) {
        List<Long> sourcePools = ctx.getTargetPoolRelations().get(REL_SOURCE);
        if (sourcePools == null || sourcePools.isEmpty()) {
            return null;
        }
        boolean inAny = sourcePools.stream().anyMatch(ctx.getCurrentPoolIds()::contains);
        if (!inAny) {
            return "目标池配置了来源池限制，证券须先在以下池中：" + poolNames(sourcePools, ctx);
        }
        return null;
    }

    /**
     * 规则：调入限制池（in_restrict）
     *
     * <p>证券当前在配置的限制池中，则不允许调入目标池。
     */
    private String inCheckRestrictPool(AdjustCheckContext ctx) {
        return checkBlockedByPools(ctx, REL_IN_RESTRICT, "调入限制池");
    }

    /**
     * 规则：调入弹性禁投池（in_soft_restrict）
     *
     * <p>弹性禁投池与硬性限制池的校验逻辑相同，但语义上表示"柔性"限制，
     * 业务层面可由特定角色绕过（绕过逻辑在审批流程中处理，此处只做初步拦截）。
     */
    private String inCheckElasticPool(AdjustCheckContext ctx) {
        return checkBlockedByPools(ctx, REL_IN_ELASTIC, "调入弹性禁投池");
    }

    /**
     * 规则：同一请求中同时勾选了互斥调入项
     *
     * <p>若目标池配置了互斥池（in_mutex），且本次请求中同时存在对这些互斥池的调入操作，
     * 则两者不可并存，均应失败。例如：信用债大库/一级库与专户产品/一级库互斥，
     * 不可同时勾选"调入信用债大库/一级库"和"调入专户产品/一级库"。
     */
    private String inCheckMutexConflict(AdjustCheckContext ctx) {
        List<Long> inMutex = ctx.getTargetPoolRelations().get(REL_IN_MUTEX);
        if (inMutex == null || inMutex.isEmpty()) {
            return null;
        }
        List<Long> conflicting = inMutex.stream()
                .filter(id -> ctx.getRequestInPoolIds().contains(id))
                .collect(Collectors.toList());
        if (!conflicting.isEmpty()) {
            return "与以下互斥池不可同时调入：" + poolNames(conflicting, ctx);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 调出校验规则
    // ═══════════════════════════════════════════════════════════

    /**
     * 规则：证券是否不在目标池中
     *
     * <p>不在池中则无法调出，属于基础前置判断，避免操作无效数据。
     */
    private String outCheckSecurityNotInPool(AdjustCheckContext ctx) {
        Long poolId = ctx.getTargetPool() != null ? ctx.getTargetPool().getId() : null;
        if (poolId == null || !ctx.getCurrentPoolIds().contains(poolId)) {
            return "证券当前不在该投资池中，无法调出";
        }
        return null;
    }

    /**
     * 规则：调出限制池（out_restrict）
     *
     * <p>证券当前在配置的限制池中，则不允许从目标池调出。
     */
    private String outCheckRestrictPool(AdjustCheckContext ctx) {
        return checkBlockedByPools(ctx, REL_OUT_RESTRICT, "调出限制池");
    }

    /**
     * 规则：调出互斥池（out_mutex）
     *
     * <p>证券当前在配置的互斥池中，则不允许从目标池调出。
     * 注意：此处校验的是"调出方向的互斥"，与调入互斥池（in_mutex）含义不同：
     * in_mutex 用于在第三阶段自动生成配套调出项；out_mutex 用于阻止调出操作本身。
     */
    private String outCheckMutexPool(AdjustCheckContext ctx) {
        return checkBlockedByPools(ctx, REL_OUT_MUTEX, "调出互斥池");
    }

    /**
     * 规则：调出弹性禁投池（out_soft_restrict）
     *
     * <p>证券当前在配置的弹性禁投池中，则不允许从目标池调出。
     */
    private String outCheckElasticPool(AdjustCheckContext ctx) {
        return checkBlockedByPools(ctx, REL_OUT_ELASTIC, "调出弹性禁投池");
    }

    /**
     * 规则：同一请求中同时勾选了互斥调出项
     *
     * <p>若目标池配置了互斥池（in_mutex），且本次请求中同时存在对这些互斥池的调出操作，
     * 则两者不可并存，均应失败。互斥池对代表"不可同时持有"，
     * 因此也不应在同一批次中同时对两池执行调出操作。
     */
    private String outCheckMutexConflict(AdjustCheckContext ctx) {
        List<Long> inMutex = ctx.getTargetPoolRelations().get(REL_IN_MUTEX);
        if (inMutex == null || inMutex.isEmpty()) {
            return null;
        }
        List<Long> conflicting = inMutex.stream()
                .filter(id -> ctx.getRequestOutPoolIds().contains(id))
                .collect(Collectors.toList());
        if (!conflicting.isEmpty()) {
            return "与以下互斥池不可同时调出：" + poolNames(conflicting, ctx);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════
    //  调库可行性校验 — 私有工具方法
    // ═══════════════════════════════════════════════════════════

    /**
     * 构建单个调库项的校验上下文（{@link AdjustCheckContext}）
     *
     * <p>负责将"本次校验共享数据"与"当次调库项的目标池信息"合并填充到上下文中，
     * 供三层校验方法直接读取，无需再逐级传参。
     *
     * @param item             当前调库项（目标池 ID、调整方向等）
     * @param poolCurrentCount 目标池当前有效证券数量（调用方按需查询）
     * @param shared           本次 checkAdjust 调用的共享数据
     */
    private AdjustCheckContext buildCheckContext(AdjustCheckReq.CheckItem item, int poolCurrentCount, AdjustSharedData shared) {
        // 从全量投资池 Map 中取出目标池及其父级池（用于构建错误消息中的池路径）
        InvestmentPoolBo targetPool = shared.poolMap.get(item.getTargetPoolId());
        InvestmentPoolBo parentPool = (targetPool != null && targetPool.getParentId() != null)
                ? shared.poolMap.get(targetPool.getParentId()) : null;

        AdjustCheckContext ctx = new AdjustCheckContext();
        ctx.setSecurityInfo(shared.securityInfo);
        ctx.setTargetPool(targetPool);
        ctx.setParentPool(parentPool);
        ctx.setCurrentPoolIds(shared.currentPoolIds);
        ctx.setPoolCurrentCount(poolCurrentCount);
        ctx.setAdjustMode(item.getAdjustMode());
        // 取出目标池自身的所有关系配置（来源池、限制池、联动池等），供关系型校验规则直接使用
        ctx.setTargetPoolRelations(shared.poolRelationMap.getOrDefault(item.getTargetPoolId(), Collections.emptyMap()));
        ctx.setPoolMap(shared.poolMap);
        ctx.setHasPendingProcess(shared.hasPendingProcess);
        ctx.setSecurityInObservePool(shared.securityInObservePool);
        ctx.setIssuerInObservePool(shared.issuerInObservePool);
        ctx.setRequestInPoolIds(shared.requestInPoolIds);
        ctx.setRequestOutPoolIds(shared.requestOutPoolIds);
        return ctx;
    }

    /**
     * 构建自动生成的联动/互斥调整项校验结果
     *
     * <p>与手动项校验流程完全相同，区别在于：
     * <ul>
     *   <li>targetPoolId 来自池关系配置，由系统自动推导，非用户选择</li>
     *   <li>itemTag 标记为"linkage"或"mutex"，前端据此区分显示样式</li>
     * </ul>
     *
     * @param targetPoolId 自动生成项的目标池 ID
     * @param adjustMode   调整方向（"调入"或"调出"）
     * @param itemTag      来源标签：linkage（联动）/ mutex（互斥）
     * @param shared       本次 checkAdjust 调用的共享数据
     */
    private AdjustCheckDto.CheckResultItem buildAutoResultItem(
            Long targetPoolId, String adjustMode, String itemTag, AdjustSharedData shared) {

        int poolCurrentCount = securityPoolAdjustMapper.queryPoolCurrentCount(targetPoolId);
        InvestmentPoolBo pool = shared.poolMap.get(targetPoolId);

        // 构造虚拟 CheckItem，使其可以复用 buildCheckContext 逻辑
        AdjustCheckReq.CheckItem fakeItem = new AdjustCheckReq.CheckItem();
        fakeItem.setTargetPoolId(targetPoolId);
        fakeItem.setAdjustMode(adjustMode);
        fakeItem.setPoolType(pool != null ? pool.getPoolType() : null);

        AdjustCheckContext ctx = buildCheckContext(fakeItem, poolCurrentCount, shared);

        List<String> failures = "调入".equals(adjustMode) ? checkInConditions(ctx) : checkOutConditions(ctx);

        AdjustCheckDto.CheckResultItem resultItem = new AdjustCheckDto.CheckResultItem();
        resultItem.setTargetPoolId(targetPoolId);
        resultItem.setPoolName(buildPoolPath(targetPoolId, shared.poolMap));
        resultItem.setPoolType(pool != null ? pool.getPoolType() : null);
        resultItem.setAdjustMode(adjustMode);
        resultItem.setItemTag(itemTag);
        resultItem.setCanAdjust(failures.isEmpty());
        resultItem.setFailReasons(failures);
        return resultItem;
    }

    /**
     * 通用阻断校验：检查证券是否在指定关系类型的池中，在则返回失败原因，不在则返回 null
     *
     * @param relationType 关系类型常量（REL_IN_RESTRICT / REL_OUT_RESTRICT 等）
     * @param label        用于错误消息的中文标签（如"调入限制池"）
     */
    private String checkBlockedByPools(AdjustCheckContext ctx, String relationType, String label) {
        List<Long> relPools = ctx.getTargetPoolRelations().get(relationType);
        if (relPools == null || relPools.isEmpty()) {
            return null;
        }
        // 取出"证券当前在其中"的阻断池
        List<Long> blocked = relPools.stream()
                .filter(ctx.getCurrentPoolIds()::contains)
                .collect(Collectors.toList());
        if (!blocked.isEmpty()) {
            return "证券在" + label + "中，无法操作：" + poolNames(blocked, ctx);
        }
        return null;
    }

    /**
     * 构建全量投资池关系 Map
     *
     * <p>结构：poolId → relationType → 关联池 ID 列表，三层嵌套以支持快速按（池ID + 关系类型）查询。
     *
     * @param relations 从数据库查出的原始关系记录列表
     */
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

    /**
     * 将池 ID 列表转为"父级/子级"路径名称，以顿号连接，用于构建可读的失败原因消息
     *
     * @param poolIds 需要转换的池 ID 列表
     * @param ctx     上下文（内含全量投资池 Map）
     */
    private String poolNames(List<Long> poolIds, AdjustCheckContext ctx) {
        return poolIds.stream()
                .map(id -> buildPoolPath(id, ctx.getPoolMap()))
                .collect(Collectors.joining("、"));
    }

    /**
     * 若 reason 非 null 则追加到失败原因列表（避免调用方每次判空）
     *
     * @param failures 失败原因集合
     * @param reason   单条校验返回的失败原因，null 表示通过
     */
    private void addIfFailed(List<String> failures, String reason) {
        if (reason != null) {
            failures.add(reason);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  对象转换方法
    // ═══════════════════════════════════════════════════════════

    /**
     * SecurityInfoBo → SecurityInfoDto（证券列表行数据，字段精简）
     */
    private SecurityInfoDto toSecurityInfoDto(SecurityInfoBo bo) {
        SecurityInfoDto d = new SecurityInfoDto();
        d.setId(bo.getId());
        d.setSecurityCode(bo.getSInfoCode());
        d.setSecurityShortName(bo.getSInfoName());
        d.setIssuer(bo.getBInfoIssuer());
        d.setFullName(bo.getBInfoFullname());
        d.setIssueAmount(bo.getBIssueAmountplan());
        d.setCarryDate(bo.getBInfoCarrydate());
        d.setMaturityDate(bo.getBInfoMaturitydate());
        d.setSecurityRating(bo.getRatingSecurity());
        d.setIssuerRating(bo.getRatingSecurityissuer());
        d.setSecurityType(bo.getDSecurityType() != null ? String.valueOf(bo.getDSecurityType()) : null);
        d.setCurrentRate(null);
        d.setTermStr(bo.getDateExists());
        return d;
    }

    /**
     * SecurityInfoBo → SecurityInfoDetailDto（调库页面顶部详情，字段完整）
     */
    private SecurityInfoDetailDto toSecurityInfoDetailDto(SecurityInfoBo bo) {
        SecurityInfoDetailDto d = new SecurityInfoDetailDto();
        d.setFullName(bo.getBInfoFullname());
        d.setSecurityShortName(bo.getSInfoName());
        d.setSecurityCode(bo.getSInfoCode());
        d.setIssuerName(bo.getBInfoIssuer());
        d.setNibCode(bo.getSWindcodeNib());
        // 交易所代码：优先沪市（SH），其次深市（SZ）
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
        d.setRatingAgency(bo.getRatingSecurityAgency());
        d.setSecurityRating(bo.getRatingSecurity());
        d.setIssuerRating(bo.getRatingSecurityissuer());
        d.setRatingOutlook(bo.getRatingOutlook());
        d.setGuaranteeStatus(bo.getBAgencyGrnttype());
        d.setLeadUnderwriter(bo.getBAgencyName());
        d.setInnerIssuerRating(bo.getInnerIssuerRating());
        d.setSecurityType(bo.getDSecurityType() != null ? String.valueOf(bo.getDSecurityType()) : null);
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

    /**
     * InvestmentPoolBo → PoolDto（投资池树节点数据，附带互斥池 ID 列表）
     *
     * @param pool        投资池实体
     * @param inMutexMap  调入互斥关系（poolId → 互斥池 ID 列表）
     * @param outMutexMap 调出互斥关系（poolId → 互斥池 ID 列表）
     */
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

    /**
     * 构建调库记录实体（ip_adjust_log），由提交申请逻辑调用
     *
     * @param req  调库申请（含证券级别信息）
     * @param item 单个调库项（含目标池、调整方向等）
     */
    private IpAdjustLogBo buildAdjustLog(SecurityPoolAdjustSubmitReq req, SecurityPoolAdjustSubmitReq.AdjustItem item) {
        IpAdjustLogBo bo = new IpAdjustLogBo();
        bo.setSecurityCode(req.getSecurityCode());
        bo.setSecurityShortName(req.getSecurityShortName());
        bo.setSecurityType(req.getSecurityType());
        bo.setAdjustType(req.getAdjustType());
        bo.setAdjustMode(item.getAdjustMode());
        bo.setTargetPoolId(item.getTargetPoolId());
        bo.setTargetPoolName(item.getTargetPoolName());
        bo.setPoolType(item.getPoolType());
        bo.setAuditStatus("00");  // 初始状态：待审核
        bo.setAdjusterId(req.getAdjusterId());
        bo.setAdjusterName(req.getAdjusterName());
        bo.setAdjustReason(req.getAdjustReason());
        bo.setAdjustAdvice(req.getAdjustAdvice());
        bo.setAttachmentFiles(item.getAttachmentFiles());
        bo.setMaterialFiles(item.getMaterialFiles());
        return bo;
    }

    /**
     * 构建投资池显示路径（格式：父级名称/子级名称，顶级池只返回池名称）
     *
     * @param poolId  目标池 ID
     * @param poolMap 全量投资池索引 Map
     */
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

    // ═══════════════════════════════════════════════════════════
    //  内部数据类
    // ═══════════════════════════════════════════════════════════

    /**
     * 本次调库校验（checkAdjust）的共享数据载体
     *
     * <p>checkAdjust 每次调用需要加载多项证券/池的基础数据，这些数据在整个调用过程中保持不变。
     * 将其封装为此类后，buildCheckContext、buildAutoResultItem 等辅助方法只需接收一个
     * shared 参数，既能保持方法签名简洁（≤4 个参数），也便于后续扩展新的校验参数——
     * 新增字段只需改动此类和 loadSharedData 的数据加载部分，辅助方法签名无需调整。
     */
    private static class AdjustSharedData {

        /** 证券基础信息（来自 sirm_securityinfo） */
        final SecurityInfoBo securityInfo;

        /** 全量投资池索引（ID → Bo），用于快速查找池详情和构建池路径名称 */
        final Map<Long, InvestmentPoolBo> poolMap;

        /** 证券当前有效所在池 ID 集合（ip_pool_status.audit_status='20'） */
        final Set<Long> currentPoolIds;

        /** 全量投资池关系配置（poolId → relationType → 关联池 ID 列表） */
        final Map<Long, Map<String, List<Long>>> poolRelationMap;

        /** 证券是否存在进行中的调库流程（audit_status IN ('00','11')） */
        final boolean hasPendingProcess;

        /** 当前证券自身是否在观察池（pool_type='observe'，audit_status='20'） */
        final boolean securityInObservePool;

        /** 证券主体公司（发行人）旗下是否有证券在观察池中 */
        final boolean issuerInObservePool;

        /** 本次请求中所有调入操作涉及的目标池 ID 集合，用于互斥池同时勾选校验 */
        final Set<Long> requestInPoolIds;

        /** 本次请求中所有调出操作涉及的目标池 ID 集合，用于互斥池同时勾选校验 */
        final Set<Long> requestOutPoolIds;

        AdjustSharedData(SecurityInfoBo securityInfo,
                         Map<Long, InvestmentPoolBo> poolMap,
                         Set<Long> currentPoolIds,
                         Map<Long, Map<String, List<Long>>> poolRelationMap,
                         boolean hasPendingProcess,
                         boolean securityInObservePool,
                         boolean issuerInObservePool,
                         Set<Long> requestInPoolIds,
                         Set<Long> requestOutPoolIds) {
            this.securityInfo = securityInfo;
            this.poolMap = poolMap;
            this.currentPoolIds = currentPoolIds;
            this.poolRelationMap = poolRelationMap;
            this.hasPendingProcess = hasPendingProcess;
            this.securityInObservePool = securityInObservePool;
            this.issuerInObservePool = issuerInObservePool;
            this.requestInPoolIds = requestInPoolIds;
            this.requestOutPoolIds = requestOutPoolIds;
        }
    }

}
