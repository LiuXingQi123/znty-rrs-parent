package com.znty.rrs.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.FundInvestmentType;
import com.znty.rrs.common.enums.HandlerType;
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.common.util.ExcelImportHelper;
import com.znty.rrs.entity.bo.FundInfoBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.fundpooladjust.FundAdjustCheckDto;
import com.znty.rrs.entity.fundpooladjust.FundAdjustCheckReq;
import com.znty.rrs.entity.fundpooladjust.FundAdjustSubmitDto;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustSubmitReq;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportCheckItemDto;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportDto;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportItemDto;
import com.znty.rrs.entity.fundpoolexcelimport.FundPoolExcelImportReq;
import com.znty.rrs.entity.investmentpool.InvestmentPoolDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FundPoolAdjustMapper;
import com.znty.rrs.mapper.FundPoolExcelImportMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 基金 Excel 导入编排，调库规则及审批写入委托基金专属服务。 */
@Service
public class FundPoolExcelImportService {
    /** 基金导入业务类型。 */
    private static final String BIZ_TYPE = "fund_pool_excel";
    /** 七列固定表头。 */
    private static final List<String> HEADERS = Arrays.asList("父池名称", "子池名称", "基金名称", "基金代码",
            "基金评分", "基金投资类型", "风管领导审批");
    /** 最大上传字节数。 */
    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    /** 最大非空数据行数。 */
    private static final int MAX_ROWS = 2000;
    /** JSON 快照序列化组件。 */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** 导入临时表及基金池查询组件。 */
    @Resource
    private FundPoolExcelImportMapper fundPoolExcelImportMapper;
    /** 基金专属业务校验及整批提交组件。 */
    @Resource
    private FundPoolAdjustService fundPoolAdjustService;
    /** 基金主档及在途流程查询组件。 */
    @Resource
    private FundPoolAdjustMapper fundPoolAdjustMapper;
    /** 用户角色及 Excel 导入权限查询组件。 */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;
    /** 原始上传文件名处理组件。 */
    @Resource
    private SysAttachmentService sysAttachmentService;

    /**
     * 上传固定七列模板并保存原始文本，上传后导入参数锁定。
     *
     * @param req 上传方向、清空参数及当前操作人
     * @param file 七列基金导入 Excel 文件
     * @param originalFileNameListJson 上传文件原始名称列表 JSON，可为空
     */
    @Transactional(rollbackFor = Exception.class)
    public FundPoolExcelImportDto uploadExcel(FundPoolExcelImportReq req, MultipartFile file,
                                             String originalFileNameListJson) {
        // 校验上传方向、用户及清空专用参数
        validateUpload(req, file);
        List<Map<String, String>> rows = ExcelImportHelper.parseFirstSheet(file, MAX_ROWS,
                new HashSet<>(Arrays.asList("基金评分", "基金投资类型", "风管领导审批")));
        if (!rows.get(0).keySet().containsAll(HEADERS)) {
            throw new BizException("基金模板必须包含七列：" + String.join("、", HEADERS));
        }
        Date now = new Date();
        String impId = UUID.randomUUID().toString().replace("-", "");
        SysImpTmpBo batch = new SysImpTmpBo();
        batch.setImpId(impId);
        batch.setBizType(BIZ_TYPE);
        batch.setTemplateCode("fund_pool_import");
        List<String> names = sysAttachmentService.parseOriginalFileNameListJson(originalFileNameListJson);
        batch.setFileName(sysAttachmentService.resolveUploadOriginalFileName(file, names));
        batch.setFileSize(file.getSize());
        batch.setFld001(req.getDirection());
        batch.setFld011(req.getAdjustReason());
        batch.setFld012(req.getAdjustAdvice());
        // 固定导入选项和清空三字段，不接受校验或提交时覆盖
        batch.setOptionJson(writeJson(reqOptions(req)));
        batch.setTotalCount(rows.size());
        batch.setPassCount(0);
        batch.setFailCount(0);
        batch.setChkRslt("0");
        batch.setSaveRslt("0");
        batch.setOpterId(req.getCurrentUserId().trim());
        batch.setOpterName(req.getCurrentUserName());
        batch.setImpTime(now);
        batch.setIsDeleted(0);
        batch.setCrteTime(now);
        batch.setUpdtTime(now);
        List<SysImpTmpDetlBo> items = new ArrayList<>();
        for (Map<String, String> row : rows) {
            SysImpTmpDetlBo item = new SysImpTmpDetlBo();
            item.setImpId(impId);
            item.setImpDetlId(UUID.randomUUID().toString().replace("-", ""));
            item.setRowNo(Integer.valueOf(row.get("__rowNo")));
            item.setFld001(row.get("基金代码"));
            item.setFld002(row.get("基金名称"));
            item.setFld003(row.get("父池名称"));
            item.setFld004(row.get("子池名称"));
            item.setFld005(row.get("基金评分"));
            item.setFld006(row.get("基金投资类型"));
            item.setFld007(row.get("风管领导审批"));
            FundInfoBo fund = item.getFld001().isEmpty() ? null
                    : fundPoolAdjustMapper.queryFundByCode(item.getFld001());
            if (fund != null) {
                item.setFld002(fund.getFundName());
            }
            for (String header : HEADERS) {
                if (row.get(header).length() > 200) {
                    throw new BizException("Excel 第 " + item.getRowNo() + " 行「" + header + "」不能超过 200 字");
                }
            }
            item.setChkRslt("0");
            item.setSaveRslt("0");
            item.setOpterId(batch.getOpterId());
            item.setImpTime(now);
            item.setIsDeleted(0);
            item.setCrteTime(now);
            item.setUpdtTime(now);
            items.add(item);
        }
        fundPoolExcelImportMapper.addBatch(batch);
        for (int index = 0; index < items.size(); index += 200) {
            fundPoolExcelImportMapper.addItemList(items.subList(index, Math.min(index + 200, items.size())));
        }
        req.setImpId(impId);
        req.setPageIndex(1);
        // 返回批次及首屏原始明细
        return queryTask(req);
    }

    /**
     * 查询基金批次及服务器快照。
     *
     * @param req 导入批次号及原始明细分页条件
     */
    public FundPoolExcelImportDto queryTask(FundPoolExcelImportReq req) {
        // 仅加载基金业务的有效批次
        SysImpTmpBo batch = requireBatch(req, false);
        // 解析批次状态与快照
        FundPoolExcelImportDto dto = toTaskDto(batch);
        dto.setItems(queryItemPage(req));
        return dto;
    }

    /**
     * 分页查询基金原始明细。
     *
     * @param req 导入批次号、校验结果筛选及分页条件
     */
    public PageResult<FundPoolExcelImportItemDto> queryItemPage(FundPoolExcelImportReq req) {
        // 拒绝非基金或已取消批次
        requireBatch(req, false);
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SysImpTmpDetlBo> rows = fundPoolExcelImportMapper.queryItemPage(req.getImpId(), req.getChkRslt(), req.getKeyword());
        PageInfo<SysImpTmpDetlBo> page = new PageInfo<>(rows);
        List<FundPoolExcelImportItemDto> records = new ArrayList<>();
        for (SysImpTmpDetlBo row : rows) {
            // 映射原始字段，非法三字段仍保留原文
            records.add(toItemDto(row));
        }
        return new PageResult<>(records, page.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 校验原始行、清空差集及跨来源组冲突。
     *
     * @param req 待校验批次号及实时权限复核的操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public FundPoolExcelImportDto checkImport(FundPoolExcelImportReq req) {
        // 锁定批次，禁止与提交和取消交叉执行
        SysImpTmpBo batch = requireBatch(req, true);
        // 已提交批次不能重校验
        requireUnsubmitted(batch);
        List<SysImpTmpDetlBo> rows = fundPoolExcelImportMapper.queryBatchItemList(batch.getImpId());
        if (rows.isEmpty()) {
            throw new BizException("导入明细为空");
        }
        // 用户身份用于实时权限复核
        String userId = currentUserId(req, batch);
        // 解析上传时固定的参数
        JsonNode options = readJson(batch.getOptionJson());
        boolean allowRelations = options.path("allowLinkMutex").asBoolean();
        // 失败行未进入基金完整规则时也展示目标池的真实全路径
        Map<Long, String> poolNames = new HashMap<>();
        for (InvestmentPoolDto pool : investmentPoolMapper.queryPoolFullNameList()) {
            poolNames.put(pool.getId(), pool.getPoolFullName());
        }
        List<FundPoolExcelImportCheckItemDto> checks = new ArrayList<>();
        Map<Long, Set<String>> reserveCodes = new LinkedHashMap<>();
        for (SysImpTmpDetlBo row : rows) {
            // 池代码先解析，三字段失败行也纳入清空保留集合
            checks.addAll(checkRow(row, batch, userId, allowRelations, reserveCodes, poolNames));
        }
        if ("in".equals(batch.getFld001()) && options.path("clearTarget").asBoolean()) {
            // 按目标池差集生成清空出库来源组
            List<FundPoolExcelImportCheckItemDto> clears = checkClearItems(reserveCodes, options, userId, allowRelations);
            clears.addAll(checks);
            checks = clears;
        }
        // 任何方向的同基金同池跨组冲突均阻断所有相关来源组
        markConflicts(checks);
        // 主项失败的关系项不可单独提交
        blockFailedSourceGroups(checks);
        int pass = 0;
        for (SysImpTmpDetlBo row : rows) {
            FundPoolExcelImportCheckItemDto primary = checks.stream()
                    .filter(item -> Objects.equals(row.getId(), item.getSourceItemId()) && "manual".equals(item.getItemTag()))
                    .findFirst().orElse(null);
            boolean ok = primary != null && primary.isCanAdjust();
            row.setChkRslt(ok ? "1" : "2");
            // 明细说明只保存限长摘要，完整原因保留在服务器校验快照
            row.setChkDscr(ok ? "校验通过" : primary == null ? "校验无主项" : summarizeFailures(primary.getFailReasons()));
            row.setUpdtTime(new Date());
            fundPoolExcelImportMapper.editItemCheckResult(row);
            if (ok) {
                pass++;
            }
        }
        batch.setPassCount(pass);
        batch.setFailCount(rows.size() - pass);
        long failedChecks = checks.stream().filter(item -> !item.isCanAdjust()).count();
        batch.setChkRslt(failedChecks == 0 ? "1" : "2");
        batch.setChkDscr("导入行通过 " + pass + " 条，失败 " + (rows.size() - pass) + " 条；不可调整项 " + failedChecks + " 条");
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.set("checkItems", objectMapper.valueToTree(checks));
        // 以完整服务器快照保存校验结果
        batch.setResultJson(writeJson(snapshot));
        batch.setUpdtTime(new Date());
        fundPoolExcelImportMapper.editBatchCheckResult(batch);
        // 回查最新行状态及校验结果
        return queryTask(req);
    }

    /**
     * 仅从持久化快照提交通过项，客户端只选择一般流程。
     *
     * @param req 批次号、已有候选流程选择及提交说明
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public FundPoolExcelImportDto submitImport(FundPoolExcelImportReq req) {
        // 原因建议使用 TEXT 业务槽，仍限制用户输入长度
        validateReasonAdvice(req);
        // 提交与校验、取消共用批次锁
        SysImpTmpBo batch = requireBatch(req, true);
        // 防止重复提交
        requireUnsubmitted(batch);
        if ("0".equals(batch.getChkRslt()) || batch.getResultJson() == null) {
            throw new BizException("请先校验导入数据");
        }
        // 业务身份与可提交结果只读取服务器快照
        List<FundPoolExcelImportCheckItemDto> checks = readChecks(batch.getResultJson());
        // 根据客户端选择核对服务器候选，忽略其业务字段
        applySelectedFlows(checks, req.getCheckItems());
        // 再次阻断失败来源组，避免附属项独立提交
        blockFailedSourceGroups(checks);
        // 用完整来源组构造独立基金提交请求
        List<FundPoolAdjustSubmitReq> requests = buildSubmitRequests(checks, batch, req);
        if (requests.isEmpty()) {
            throw new BizException("没有可提交的校验结果");
        }
        List<FundAdjustSubmitDto> results;
        try {
            // 在任何日志写入前整批锁定主档并复核，避免继续使用已转正或取消的临时代码
            results = fundPoolAdjustService.addExcelImportAdjustLogList(requests);
        } catch (BizException exception) {
            // 主档已不可用时要求重建导入批次，保留原始行及校验快照
            if ("已终止或退市基金不能发起调库".equals(exception.getMessage())) {
                throw new BizException("基金代码已不可用（已终止、退市或临时代码已转正/取消），请重置并重新上传");
            }
            throw exception;
        }
        List<Long> logIds = new ArrayList<>();
        List<String> batchNos = new ArrayList<>();
        for (FundAdjustSubmitDto result : results) {
            logIds.addAll(result.getAdjustLogIds());
            batchNos.addAll(result.getAdjustBatchNos());
        }
        Set<Long> savedSources = checks.stream().filter(FundPoolExcelImportCheckItemDto::isCanAdjust)
                .filter(item -> "manual".equals(item.getItemTag())).map(FundPoolExcelImportCheckItemDto::getSourceItemId)
                .collect(Collectors.toSet());
        for (SysImpTmpDetlBo row : fundPoolExcelImportMapper.queryBatchItemList(batch.getImpId())) {
            if (savedSources.contains(row.getId())) {
                row.setSaveRslt("1");
                row.setSaveDscr("提交成功");
                row.setUpdtTime(new Date());
                fundPoolExcelImportMapper.editItemSaveResult(row);
            }
        }
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.set("checkItems", objectMapper.valueToTree(checks));
        snapshot.set("logIds", objectMapper.valueToTree(logIds));
        snapshot.set("adjustBatchNoList", objectMapper.valueToTree(batchNos));
        snapshot.put("submitted", true);
        // 保存可恢复的提交结果，序列化失败不能静默丢失快照
        batch.setResultJson(writeJson(snapshot));
        batch.setSaveRslt("1");
        batch.setSaveDscr("提交成功，共 " + logIds.size() + " 条基金调库记录");
        if (req.getAdjustReason() != null) {
            batch.setFld011(req.getAdjustReason());
        }
        if (req.getAdjustAdvice() != null) {
            batch.setFld012(req.getAdjustAdvice());
        }
        batch.setUpdtTime(new Date());
        fundPoolExcelImportMapper.editBatchSaveResult(batch);
        // 返回审批申请结果，池状态仍由最终审批落地
        return toTaskDto(batch);
    }

    /**
     * 取消未提交批次，返回取消前完整上下文及取消状态。
     *
     * @param req 待取消的未提交批次号
     */
    @Transactional(rollbackFor = Exception.class)
    public FundPoolExcelImportDto cancelImport(FundPoolExcelImportReq req) {
        // 与提交串行化后取消批次
        SysImpTmpBo batch = requireBatch(req, true);
        // 已提交申请不能经导入重置取消
        requireUnsubmitted(batch);
        fundPoolExcelImportMapper.deleteItemsByImpIdSoft(batch.getImpId());
        fundPoolExcelImportMapper.deleteBatchSoft(batch.getImpId());
        batch.setSaveRslt("3");
        batch.setSaveDscr("已取消");
        // 保留取消结果供页面重置
        return toTaskDto(batch);
    }

    /**
     * 解析一行目标池和三字段，复用完整基金校验。
     *
     * @param row 持久化原始明细，包含 Excel 物理行号和三字段原文
     * @param batch 包含锁定调整方向的导入批次
     * @param userId 本次权限复核的操作人 ID
     * @param allowRelations 是否保留基金校验产生的联动和互斥项
     * @param reserveCodes 按目标池归集的保留代码，其他字段失败仍保留可识别的池和代码
     * @param poolNames 目标池 ID 与完整路径的映射
     */
    private List<FundPoolExcelImportCheckItemDto> checkRow(SysImpTmpDetlBo row, SysImpTmpBo batch,
            String userId, boolean allowRelations, Map<Long, Set<String>> reserveCodes,
            Map<Long, String> poolNames) {
        List<String> failures = new ArrayList<>();
        InvestmentPoolBo pool = null;
        row.setFld009(null);
        row.setFld010(null);
        if (row.getFld001() == null || row.getFld001().isEmpty()) {
            failures.add("基金代码为空");
        }
        if (row.getFld003() == null || row.getFld003().isEmpty()
                || row.getFld004() == null || row.getFld004().isEmpty()) {
            failures.add("父池名称和子池名称不能为空");
        } else {
            List<InvestmentPoolBo> pools = fundPoolExcelImportMapper.queryEnabledLeafPoolList(row.getFld003(), row.getFld004());
            if (pools.size() != 1) {
                failures.add(pools.isEmpty() ? "未找到支持基金的启用叶子池" : "父子池名称匹配多个投资池，请修正数据");
            } else {
                pool = pools.get(0);
                row.setFld009(String.valueOf(pool.getId()));
                row.setFld010(pool.getPoolType());
                if (row.getFld001() != null && !row.getFld001().isEmpty()) {
                    // 池和代码可识别即加入清空保留集合，后续三字段失败不移除
                    reserveCodes.computeIfAbsent(pool.getId(), key -> new HashSet<>()).add(row.getFld001());
                }
            }
        }
        FundInfoBo fund = row.getFld001() == null || row.getFld001().isEmpty() ? null
                : fundPoolAdjustMapper.queryFundByCode(row.getFld001());
        if (fund != null) {
            row.setFld002(fund.getFundName());
            if (pool != null) {
                // 同时保留主档代码，避免大小写差异将同一基金误判为清空差集
                reserveCodes.computeIfAbsent(pool.getId(), key -> new HashSet<>()).add(fund.getFundCode());
            }
        }
        FundPoolExcelImportCheckItemDto primary = new FundPoolExcelImportCheckItemDto();
        primary.setFundCode(fund == null ? row.getFld001() : fund.getFundCode());
        primary.setFundShortName(fund == null ? null : fund.getFundShortName());
        primary.setSecurityType(fund == null ? null : fund.getSecurityType());
        primary.setTargetPoolId(pool == null ? null : pool.getId());
        primary.setPoolName(pool == null ? null : poolNames.get(pool.getId()));
        primary.setPoolType(pool == null ? null : pool.getPoolType());
        primary.setAdjustDirection(batch.getFld001());
        primary.setSourceItemId(row.getId());
        primary.setRowNo(row.getRowNo());
        primary.setItemTag("manual");
        primary.setAdjustType("Excel导入");
        primary.setAdjustGroupKey("excel:" + row.getId());
        primary.setFailReasons(failures);
        primary.setWarnings(Collections.emptyList());
        primary.setFlowOptions(Collections.emptyList());
        try {
            // 评分必须能无损存入现有 DECIMAL(10,4)
            primary.setFundScore(parseScore(row.getFld005()));
            if (!FundInvestmentType.isValid(row.getFld006())) {
                throw new BizException("基金投资类型必须为 stock/equity_hybrid/money_market/bond_hybrid/other");
            }
            primary.setFundInvestmentType(row.getFld006());
            if (!"0".equals(row.getFld007()) && !"1".equals(row.getFld007())) {
                throw new BizException("风管领导审批必须为 0 或 1");
            }
            primary.setNeedRiskLeaderApproval(Integer.valueOf(row.getFld007()));
        } catch (BizException ex) {
            failures.add(ex.getMessage());
        }
        if (!failures.isEmpty()) {
            return new ArrayList<>(Collections.singletonList(primary));
        }
        // 所有通过基础解析的行均走基金权限、报告及准入校验
        return checkSourceGroup(primary, userId, allowRelations);
    }

    /**
     * 为清空差集成员生成独立调出来源组。
     *
     * @param reserveCodes 本批目标池及 Excel 可识别基金的保留集合
     * @param options 上传时锁定的统一清空三字段
     * @param userId 本次权限复核的操作人 ID
     * @param allowRelations 是否保留清空项产生的联动和互斥项
     */
    private List<FundPoolExcelImportCheckItemDto> checkClearItems(Map<Long, Set<String>> reserveCodes,
            JsonNode options, String userId, boolean allowRelations) {
        List<FundPoolExcelImportCheckItemDto> checks = new ArrayList<>();
        for (Map.Entry<Long, Set<String>> entry : reserveCodes.entrySet()) {
            for (FundInfoBo member : fundPoolExcelImportMapper.queryPoolMemberList(entry.getKey())) {
                if (entry.getValue().contains(member.getFundCode())
                        || fundPoolAdjustMapper.queryFundHasPendingProcess(member.getFundCode(), entry.getKey(), null)) {
                    continue;
                }
                FundPoolExcelImportCheckItemDto source = new FundPoolExcelImportCheckItemDto();
                source.setFundCode(member.getFundCode());
                source.setFundShortName(member.getFundShortName());
                source.setSecurityType(member.getSecurityType());
                source.setTargetPoolId(entry.getKey());
                source.setAdjustDirection("out");
                source.setItemTag("clear");
                source.setAdjustType("Excel清空");
                source.setAdjustGroupKey("clear:" + member.getFundCode() + ":" + entry.getKey());
                source.setAdjustmentNote("首先清空目标池");
                // 使用已锁定的统一清空三字段
                source.setFundScore(parseScore(options.path("clearFundScore").asText()));
                source.setFundInvestmentType(options.path("clearFundInvestmentType").asText());
                source.setNeedRiskLeaderApproval(options.path("clearNeedRiskLeaderApproval").intValue());
                // 清空主项先按 manual 校验一般调出报告要求，再映射 clear 标签
                checks.addAll(checkSourceGroup(source, userId, allowRelations));
            }
        }
        return checks;
    }

    /**
     * 复用基金准入、调整权限和无附件报告限制，并映射来源组。
     *
     * @param source 手工或清空主项，携带来源标识和三字段
     * @param userId 用于导入和调整权限复核的操作人 ID
     * @param allowRelations 是否保留该来源组的联动和互斥项
     */
    private List<FundPoolExcelImportCheckItemDto> checkSourceGroup(FundPoolExcelImportCheckItemDto source,
            String userId, boolean allowRelations) {
        FundAdjustCheckReq req = new FundAdjustCheckReq();
        req.setFundCode(source.getFundCode());
        FundAdjustCheckReq.CheckItem manual = new FundAdjustCheckReq.CheckItem();
        manual.setTargetPoolId(source.getTargetPoolId());
        // 基金业务方向使用现有枚举 code，导入 API 保持 in/out
        manual.setAdjustMode(toAdjustMode(source.getAdjustDirection()));
        req.setItems(Collections.singletonList(manual));
        try {
            FundAdjustCheckDto result = fundPoolAdjustService.checkExcelImportAdjust(req, userId);
            if (result == null || result.getItems() == null || result.getItems().isEmpty()) {
                throw new BizException("基金调库校验无结果");
            }
            List<FundPoolExcelImportCheckItemDto> mapped = new ArrayList<>();
            for (FundAdjustCheckDto.CheckResultItem item : result.getItems()) {
                if (!allowRelations && !"manual".equals(item.getItemTag())) {
                    continue;
                }
                FundPoolExcelImportCheckItemDto dto = new FundPoolExcelImportCheckItemDto();
                dto.setFundCode(item.getFundCode());
                dto.setFundShortName(item.getFundShortName());
                dto.setSecurityType(item.getSecurityType());
                dto.setTargetPoolId(item.getTargetPoolId());
                dto.setPoolName(item.getPoolName());
                dto.setPoolType(item.getPoolType());
                // 将基金内部枚举方向转换为导入 API 的 in/out
                dto.setAdjustDirection(toAdjustDirection(item.getAdjustMode()));
                dto.setItemTag("manual".equals(item.getItemTag()) ? source.getItemTag() : item.getItemTag());
                dto.setAdjustGroupKey(source.getAdjustGroupKey());
                dto.setSourceItemId(source.getSourceItemId());
                dto.setRowNo(source.getRowNo());
                dto.setCanAdjust(item.isCanAdjust());
                dto.setFailReasons(new ArrayList<>(item.getFailReasons()));
                dto.setWarnings(item.getWarnings());
                dto.setFlowOptions(item.getFlowOptions());
                dto.setFundScore(source.getFundScore());
                dto.setFundInvestmentType(source.getFundInvestmentType());
                dto.setNeedRiskLeaderApproval(source.getNeedRiskLeaderApproval());
                dto.setAdjustmentNote(source.getAdjustmentNote());
                dto.setAdjustType("manual".equals(item.getItemTag()) ? source.getAdjustType()
                        : "linkage".equals(item.getItemTag()) ? "联动调整" : "互斥调整");
                try {
                    // 每个实际目标池均须具有 Excel 导入权限
                    validateExcelPermission(userId, item.getTargetPoolId());
                } catch (BizException ex) {
                    dto.setCanAdjust(false);
                    dto.getFailReasons().add(ex.getMessage());
                }
                if (dto.isCanAdjust()) {
                    for (FundAdjustCheckDto.FlowOption option : item.getFlowOptions()) {
                        if (option.isSelectable()) {
                            dto.setSelectedFlowId(option.getFlowId());
                            dto.setSelectedFlowKey(option.getFlowKey());
                            dto.setSelectedFlowType(option.getFlowType());
                            break;
                        }
                    }
                }
                mapped.add(dto);
            }
            return mapped;
        } catch (BizException ex) {
            source.setCanAdjust(false);
            source.setFailReasons(new ArrayList<>(Collections.singletonList(ex.getMessage())));
            source.setWarnings(Collections.emptyList());
            source.setFlowOptions(Collections.emptyList());
            return new ArrayList<>(Collections.singletonList(source));
        }
    }

    /**
     * 跨来源组同基金同池不得合并，全部相关组失败并定位来源。
     *
     * @param checks 待检查同基金同目标池跨来源组冲突的校验项
     */
    private void markConflicts(List<FundPoolExcelImportCheckItemDto> checks) {
        // 按基金和目标池归集不同来源组，调整方向不参与去重
        Map<String, Map<String, FundPoolExcelImportCheckItemDto>> targets = new LinkedHashMap<>();
        for (FundPoolExcelImportCheckItemDto item : checks) {
            if (item.getTargetPoolId() != null && item.getFundCode() != null && !item.getFundCode().isEmpty()) {
                targets.computeIfAbsent(item.getFundCode() + ":" + item.getTargetPoolId(), key -> new LinkedHashMap<>())
                        .putIfAbsent(item.getAdjustGroupKey(), item);
            }
        }
        // 为所有冲突来源组生成包含 Excel 行号或清空来源的定位说明
        Map<String, List<String>> groupErrors = new HashMap<>();
        for (Map<String, FundPoolExcelImportCheckItemDto> sources : targets.values()) {
            if (sources.size() < 2) {
                continue;
            }
            FundPoolExcelImportCheckItemDto first = sources.values().iterator().next();
            String locations = sources.values().stream().limit(5).map(item -> item.getRowNo() == null
                    ? "清空出库项[" + item.getAdjustGroupKey() + "]" : "Excel 第 " + item.getRowNo() + " 行")
                    .collect(Collectors.joining("、"));
            if (sources.size() > 5) {
                locations += "等共 " + sources.size() + " 个来源组";
            }
            String reason = locations + "展开到相同基金[" + first.getFundCode() + "]和投资池["
                    + first.getTargetPoolId() + "]，不同来源不能合并";
            for (String group : sources.keySet()) {
                groupErrors.computeIfAbsent(group, key -> new ArrayList<>()).add(reason);
            }
        }
        // 将冲突回写到相关来源组的全部主项和关系项
        for (FundPoolExcelImportCheckItemDto item : checks) {
            if (groupErrors.containsKey(item.getAdjustGroupKey())) {
                item.setCanAdjust(false);
                item.getFailReasons().addAll(groupErrors.get(item.getAdjustGroupKey()));
            }
        }
    }

    /**
     * 失败主项的联动互斥项禁止独立提交。
     *
     * @param checks 需同步主项与关系项可提交状态的校验项
     */
    private void blockFailedSourceGroups(List<FundPoolExcelImportCheckItemDto> checks) {
        // 收集校验失败的手工和清空主项，阻断其同组联动、互斥项
        Set<String> failed = checks.stream().filter(item -> "manual".equals(item.getItemTag()) || "clear".equals(item.getItemTag()))
                .filter(item -> !item.isCanAdjust()).map(FundPoolExcelImportCheckItemDto::getAdjustGroupKey).collect(Collectors.toSet());
        for (FundPoolExcelImportCheckItemDto item : checks) {
            if (failed.contains(item.getAdjustGroupKey()) && item.isCanAdjust()) {
                item.setCanAdjust(false);
                item.getFailReasons().add("来源主项未通过校验，不提交该组联动或互斥项");
            }
        }
    }

    /**
     * 从客户端仅读取服务器已保存项的一般流程选择。
     *
     * @param stored 服务器持久化的校验项及可选流程候选
     * @param requested 客户端流程选择，仅用于匹配已有候选
     */
    private void applySelectedFlows(List<FundPoolExcelImportCheckItemDto> stored,
                                   List<FundPoolExcelImportCheckItemDto> requested) {
        if (requested == null) {
            return;
        }
        for (FundPoolExcelImportCheckItemDto selection : requested) {
            if (selection == null) {
                throw new BizException("流程选择项不能为空");
            }
            // 通过来源组、来源明细和目标池定位服务器快照中的原始校验项
            FundPoolExcelImportCheckItemDto target = stored.stream().filter(item ->
                    Objects.equals(item.getAdjustGroupKey(), selection.getAdjustGroupKey())
                    && Objects.equals(item.getSourceItemId(), selection.getSourceItemId())
                    && Objects.equals(item.getTargetPoolId(), selection.getTargetPoolId())
                    && Objects.equals(item.getItemTag(), selection.getItemTag())).findFirst().orElse(null);
            if (target == null || !Objects.equals(target.getFundCode(), selection.getFundCode())
                    || !Objects.equals(target.getAdjustDirection(), selection.getAdjustDirection())) {
                throw new BizException("流程选择与服务器校验结果不一致，请重新校验");
            }
            if (!target.isCanAdjust() || (!"manual".equals(target.getItemTag()) && !"clear".equals(target.getItemTag()))) {
                continue;
            }
            // 仅接受服务器候选中已通过校验的一般流程，不采信客户端业务字段
            boolean allowed = target.getFlowOptions().stream().anyMatch(option -> option.isSelectable()
                    && Objects.equals(option.getFlowId(), selection.getSelectedFlowId())
                    && Objects.equals(option.getFlowKey(), selection.getSelectedFlowKey())
                    && Objects.equals(option.getFlowType(), selection.getSelectedFlowType()));
            if (!allowed) {
                throw new BizException("只能选择服务器校验通过的目标池一般审批流程");
            }
            target.setSelectedFlowId(selection.getSelectedFlowId());
            target.setSelectedFlowKey(selection.getSelectedFlowKey());
            target.setSelectedFlowType(selection.getSelectedFlowType());
        }
    }

    /**
     * 依来源组构造基金提交请求，清空项排在导入项前。
     *
     * @param checks 已匹配流程并阻断失败来源组的服务器校验项
     * @param batch 包含锁定方向、操作人及调整说明的导入批次
     * @param req 本次提交的操作人和可编辑调整说明
     */
    private List<FundPoolAdjustSubmitReq> buildSubmitRequests(List<FundPoolExcelImportCheckItemDto> checks,
                                                            SysImpTmpBo batch, FundPoolExcelImportReq req) {
        Map<Long, SysImpTmpDetlBo> rows = fundPoolExcelImportMapper.queryBatchItemList(batch.getImpId()).stream()
                .collect(Collectors.toMap(SysImpTmpDetlBo::getId, row -> row));
        Map<String, List<FundPoolExcelImportCheckItemDto>> groups = new LinkedHashMap<>();
        for (FundPoolExcelImportCheckItemDto item : checks) {
            if (item.isCanAdjust()) {
                groups.computeIfAbsent(item.getAdjustGroupKey(), key -> new ArrayList<>()).add(item);
            }
        }
        // 提交身份用于再次复核导入权限
        String userId = currentUserId(req, batch);
        List<FundPoolAdjustSubmitReq> requests = new ArrayList<>();
        for (List<FundPoolExcelImportCheckItemDto> group : groups.values()) {
            FundPoolExcelImportCheckItemDto primary = group.stream().filter(item ->
                    "manual".equals(item.getItemTag()) || "clear".equals(item.getItemTag())).findFirst()
                    .orElseThrow(() -> new BizException("校验快照缺少来源主项，请重新校验"));
            FundPoolAdjustSubmitReq submit = new FundPoolAdjustSubmitReq();
            submit.setFundCode(primary.getFundCode());
            submit.setFundScore(primary.getFundScore());
            submit.setFundInvestmentType(primary.getFundInvestmentType());
            submit.setNeedRiskLeaderApproval(primary.getNeedRiskLeaderApproval());
            if (primary.getSourceItemId() != null) {
                SysImpTmpDetlBo row = rows.get(primary.getSourceItemId());
                FundInfoBo fund = row == null ? null : fundPoolAdjustMapper.queryFundByCode(row.getFld001());
                if (row == null || fund == null || !"1".equals(row.getChkRslt())
                        || !Objects.equals(fund.getFundCode(), primary.getFundCode())
                        || !Objects.equals(row.getFld009(), String.valueOf(primary.getTargetPoolId()))
                        || !Objects.equals(batch.getFld001(), primary.getAdjustDirection())) {
                    throw new BizException("服务器校验快照与来源导入行不一致，请重新校验");
                }
                // 三字段重新读取持久化来源行，绝不使用客户端值
                submit.setFundScore(parseScore(row.getFld005()));
                submit.setFundInvestmentType(row.getFld006());
                submit.setNeedRiskLeaderApproval(Integer.valueOf(row.getFld007()));
            }
            submit.setAdjustType(primary.getAdjustType());
            submit.setAdjusterId(userId);
            submit.setAdjusterName(req.getCurrentUserName() == null ? batch.getOpterName() : req.getCurrentUserName());
            submit.setAdjustReason(req.getAdjustReason() == null ? batch.getFld011() : req.getAdjustReason());
            submit.setAdjustAdvice(req.getAdjustAdvice() == null ? batch.getFld012() : req.getAdjustAdvice());
            List<FundPoolAdjustSubmitReq.AdjustItem> items = new ArrayList<>();
            for (FundPoolExcelImportCheckItemDto checked : group) {
                // 提交前检查每个实际目标池的 Excel 导入权限
                validateExcelPermission(userId, checked.getTargetPoolId());
                FundPoolAdjustSubmitReq.AdjustItem item = new FundPoolAdjustSubmitReq.AdjustItem();
                item.setTargetPoolId(checked.getTargetPoolId());
                item.setTargetPoolName(checked.getPoolName());
                item.setPoolType(checked.getPoolType());
                // 进入基金提交链路时转换为现有枚举方向
                item.setAdjustMode(toAdjustMode(checked.getAdjustDirection()));
                item.setItemTag("clear".equals(checked.getItemTag()) ? "manual" : checked.getItemTag());
                item.setAdjustGroupKey(checked.getAdjustGroupKey());
                item.setFlowId(primary.getSelectedFlowId());
                item.setFlowKey(primary.getSelectedFlowKey());
                item.setFlowType(primary.getSelectedFlowType());
                item.setAdjustmentNote(checked.getAdjustmentNote());
                items.add(item);
            }
            submit.setItems(items);
            requests.add(submit);
        }
        return requests;
    }

    /**
     * 校验当前用户目标池 Excel 权限，沿用证券管理员 ID=1 口径。
     *
     * @param userId 当前操作人 ID
     * @param poolId 需要 Excel 导入权限的实际目标池 ID
     */
    private void validateExcelPermission(String userId, Long poolId) {
        if ("1".equals(userId)) {
            return;
        }
        Long id;
        try {
            id = Long.valueOf(userId);
        } catch (NumberFormatException ex) {
            throw new BizException("当前用户 ID 不合法");
        }
        Set<Long> roles = new HashSet<>(investmentPoolMapper.queryUserRoleIdList(id));
        for (PoolPermissionBo permission : investmentPoolMapper.queryPermissionListByType(PermissionType.EXCEL_IMPORTABLE.getCode())) {
            if (Objects.equals(poolId, permission.getPoolId()) && permission.getHandlerId() != null
                    && ((HandlerType.USER.getCode().equals(permission.getHandlerType()) && id.equals(permission.getHandlerId()))
                    || (HandlerType.ROLE.getCode().equals(permission.getHandlerType()) && roles.contains(permission.getHandlerId())))) {
                return;
            }
        }
        throw new BizException("当前用户无权对投资池[" + poolId + "]进行 Excel 导入");
    }

    /**
     * 校验文件、方向及已选清空参数。
     *
     * @param req 上传方向、当前操作人及统一清空参数
     * @param file 待检查大小及空内容的上传文件
     */
    private void validateUpload(FundPoolExcelImportReq req, MultipartFile file) {
        if (req == null || (!"in".equals(req.getDirection()) && !"out".equals(req.getDirection()))) {
            throw new BizException("调整方向必须为 in 或 out");
        }
        if (req.getCurrentUserId() == null || req.getCurrentUserId().trim().isEmpty()) {
            throw new BizException("当前用户 ID 不能为空");
        }
        // 控制原因建议输入长度，避免超出单笔业务约束
        validateReasonAdvice(req);
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BizException("文件大小不能超过 5MB");
        }
        if ("out".equals(req.getDirection()) && Boolean.TRUE.equals(req.getClearTarget())) {
            throw new BizException("首先清空目标池仅支持调入");
        }
        if (Boolean.TRUE.equals(req.getClearTarget())) {
            // 校验统一清空评分可无损保存
            parseScore(req.getClearFundScore() == null ? null : req.getClearFundScore().toPlainString());
            if (!FundInvestmentType.isValid(req.getClearFundInvestmentType())) {
                throw new BizException("清空出库项基金投资类型不能为空且必须为合法 code");
            }
            if (req.getClearNeedRiskLeaderApproval() == null
                    || (req.getClearNeedRiskLeaderApproval() != 0 && req.getClearNeedRiskLeaderApproval() != 1)) {
                throw new BizException("清空出库项风管领导审批必须为 0 或 1");
            }
        }
    }

    /**
     * 解析评分并阻止超出数据库精度导致静默取整。
     *
     * @param text Excel 行或统一清空参数中的原始评分文本
     */
    private BigDecimal parseScore(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new BizException("基金评分不能为空");
        }
        try {
            BigDecimal score = new BigDecimal(text);
            BigDecimal normalized = score.stripTrailingZeros();
            if (normalized.scale() > 4 || normalized.precision() - normalized.scale() > 6) {
                throw new BizException("基金评分最多六位整数、四位小数");
            }
            return score;
        } catch (NumberFormatException ex) {
            throw new BizException("基金评分必须为合法数字");
        }
    }

    /**
     * 构造上传时固定的业务选项。
     *
     * @param req 包含清空开关、关系开关及清空三字段的上传请求
     */
    private Map<String, Object> reqOptions(FundPoolExcelImportReq req) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("clearTarget", Boolean.TRUE.equals(req.getClearTarget()));
        options.put("allowLinkMutex", Boolean.TRUE.equals(req.getAllowLinkMutex()));
        options.put("clearFundScore", req.getClearFundScore());
        options.put("clearFundInvestmentType", req.getClearFundInvestmentType());
        options.put("clearNeedRiskLeaderApproval", req.getClearNeedRiskLeaderApproval());
        return options;
    }

    /**
     * 查询或锁定有效基金批次。
     *
     * @param req 包含基金导入批次号的请求
     * @param lock 是否取得批次行锁，校验、提交和取消均需加锁
     */
    private SysImpTmpBo requireBatch(FundPoolExcelImportReq req, boolean lock) {
        if (req == null || req.getImpId() == null || req.getImpId().trim().isEmpty()) {
            throw new BizException("导入批次号不能为空");
        }
        if (lock && fundPoolExcelImportMapper.queryBatchIdForUpdate(req.getImpId()) == null) {
            throw new BizException("基金导入批次不存在或已取消");
        }
        SysImpTmpBo batch = fundPoolExcelImportMapper.queryBatchByImpId(req.getImpId());
        if (batch == null || !BIZ_TYPE.equals(batch.getBizType())) {
            throw new BizException("基金导入批次不存在或已取消");
        }
        return batch;
    }

    /**
     * 已提交批次禁止修改和取消。
     *
     * @param batch 需要确认尚未提交的导入批次
     */
    private void requireUnsubmitted(SysImpTmpBo batch) {
        if ("1".equals(batch.getSaveRslt())) {
            throw new BizException("该批次已提交，请勿重复操作");
        }
    }

    /**
     * 确定本次实时权限复核的用户身份。
     *
     * @param req 本次请求提供的操作人
     * @param batch 保存上传操作人的导入批次
     */
    private String currentUserId(FundPoolExcelImportReq req, SysImpTmpBo batch) {
        String id = req.getCurrentUserId() == null ? batch.getOpterId() : req.getCurrentUserId().trim();
        if (id == null || id.isEmpty()) {
            throw new BizException("当前用户 ID 不能为空");
        }
        return id;
    }

    /**
     * 映射批次、上传参数和服务器快照。
     *
     * @param batch 包含业务参数和校验、提交快照的持久化批次
     */
    private FundPoolExcelImportDto toTaskDto(SysImpTmpBo batch) {
        FundPoolExcelImportDto dto = new FundPoolExcelImportDto();
        dto.setImpId(batch.getImpId());
        dto.setBizType(batch.getBizType());
        dto.setFileName(batch.getFileName());
        dto.setImpTime(batch.getImpTime());
        dto.setBizMode(batch.getFld001());
        dto.setReason(batch.getFld011());
        dto.setAdvice(batch.getFld012());
        dto.setTotalCount(batch.getTotalCount());
        dto.setPassCount(batch.getPassCount());
        dto.setFailCount(batch.getFailCount());
        dto.setPendingCount(batch.getTotalCount() - batch.getPassCount() - batch.getFailCount());
        dto.setChkRslt(batch.getChkRslt());
        dto.setChkDscr(batch.getChkDscr());
        dto.setSaveRslt(batch.getSaveRslt());
        dto.setSaveDscr(batch.getSaveDscr());
        dto.setOptionJson(batch.getOptionJson());
        // 还原上传时固定参数
        JsonNode options = readJson(batch.getOptionJson());
        dto.setAllowLinkMutex(options.path("allowLinkMutex").asBoolean());
        if (!options.path("clearFundScore").isNull() && options.has("clearFundScore")) {
            dto.setClearFundScore(options.get("clearFundScore").decimalValue());
        }
        dto.setClearFundInvestmentType(options.path("clearFundInvestmentType").isNull() ? null
                : options.path("clearFundInvestmentType").asText());
        dto.setClearNeedRiskLeaderApproval(options.path("clearNeedRiskLeaderApproval").isNull() ? null
                : options.path("clearNeedRiskLeaderApproval").intValue());
        dto.setCheckDone(batch.getResultJson() != null && !"0".equals(batch.getChkRslt()));
        // 读取已保存的校验快照
        List<FundPoolExcelImportCheckItemDto> checks = readChecks(batch.getResultJson());
        dto.setCheckItems(checks);
        int passed = (int) checks.stream().filter(FundPoolExcelImportCheckItemDto::isCanAdjust).count();
        dto.setCheckPassCount(passed);
        dto.setCheckFailCount(checks.size() - passed);
        if ("1".equals(batch.getSaveRslt())) {
            // 还原已提交批次的基金日志和审批批次号
            JsonNode snapshot = readJson(batch.getResultJson());
            dto.setLogIds(objectMapper.convertValue(snapshot.get("logIds"), new TypeReference<List<Long>>() { }));
            dto.setAdjustBatchNoList(objectMapper.convertValue(snapshot.get("adjustBatchNoList"), new TypeReference<List<String>>() { }));
        }
        return dto;
    }

    /**
     * 映射原始三字段及行级状态。
     *
     * @param row 包含原始七列及行级校验、保存状态的明细
     */
    private FundPoolExcelImportItemDto toItemDto(SysImpTmpDetlBo row) {
        FundPoolExcelImportItemDto dto = new FundPoolExcelImportItemDto();
        dto.setId(row.getId());
        dto.setRowNo(row.getRowNo());
        dto.setFundCode(row.getFld001());
        dto.setFundName(row.getFld002());
        dto.setParentPoolName(row.getFld003());
        dto.setChildPoolName(row.getFld004());
        dto.setFundScoreRaw(row.getFld005());
        dto.setFundInvestmentTypeRaw(row.getFld006());
        dto.setNeedRiskLeaderApprovalRaw(row.getFld007());
        dto.setTargetPoolId(row.getFld009() == null ? null : Long.valueOf(row.getFld009()));
        dto.setPoolType(row.getFld010());
        dto.setChkRslt(row.getChkRslt());
        dto.setChkDscr(row.getChkDscr());
        dto.setSaveRslt(row.getSaveRslt());
        dto.setSaveDscr(row.getSaveDscr());
        return dto;
    }

    /**
     * 严格解析服务器 JSON，损坏数据不得静默忽略。
     *
     * @param json 必须存在且格式有效的服务器参数或快照 JSON
     */
    private JsonNode readJson(String json) {
        try {
            if (json == null) {
                throw new BizException("基金导入批次 JSON 数据缺失");
            }
            return objectMapper.readTree(json);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("基金导入批次 JSON 数据损坏");
        }
    }

    /**
     * 无校验快照表示尚未校验，其余 JSON 必须有效。
     *
     * @param json 持久化校验快照 JSON，未校验时为空
     */
    private List<FundPoolExcelImportCheckItemDto> readChecks(String json) {
        if (json == null) {
            return new ArrayList<>();
        }
        // 严格读取服务器保存的校验项
        JsonNode snapshot = readJson(json);
        if (!snapshot.has("checkItems") || !snapshot.get("checkItems").isArray()) {
            throw new BizException("基金导入校验快照缺少有效校验项，请重新校验");
        }
        try {
            return objectMapper.convertValue(snapshot.get("checkItems"), new TypeReference<List<FundPoolExcelImportCheckItemDto>>() { });
        } catch (IllegalArgumentException ex) {
            throw new BizException("基金导入校验快照格式错误，请重新校验");
        }
    }

    /**
     * 序列化参数或快照，失败时回滚。
     *
     * @param value 需要保存到导入批次的参数或结果快照
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BizException("保存基金导入快照失败");
        }
    }

    /**
     * 调整原因和建议各允许最多 1000 字，使用已有 TEXT 业务槽。
     *
     * @param req 包含调整原因和建议的上传或提交请求
     */
    private void validateReasonAdvice(FundPoolExcelImportReq req) {
        if (req != null && ((req.getAdjustReason() != null && req.getAdjustReason().length() > 1000)
                || (req.getAdjustAdvice() != null && req.getAdjustAdvice().length() > 1000))) {
            throw new BizException("调整原因和调整建议各不能超过 1000 字");
        }
    }

    /**
     * 限制行级说明长度，完整失败原因仍可在调库校验结果查看。
     *
     * @param failures 保留在完整快照中的行级失败原因
     */
    private String summarizeFailures(List<String> failures) {
        String text = String.join("；", failures);
        return text.length() <= 500 ? text : text.substring(0, 460) + "…完整原因请查看调库校验结果";
    }

    /**
     * 将导入 API 方向转换为基金调库使用的枚举 code。
     *
     * @param direction 导入 API 调整方向，in 或 out
     */
    private String toAdjustMode(String direction) {
        if ("in".equals(direction)) {
            return AdjustMode.IN.getCode();
        }
        if ("out".equals(direction)) {
            return AdjustMode.OUT.getCode();
        }
        throw new BizException("基金导入校验快照调整方向不合法");
    }

    /**
     * 将基金调库枚举 code 转换为导入 API 的方向值。
     *
     * @param adjustMode 基金调库使用的 AdjustMode 枚举 code
     */
    private String toAdjustDirection(String adjustMode) {
        if (AdjustMode.IN.getCode().equals(adjustMode)) {
            return "in";
        }
        if (AdjustMode.OUT.getCode().equals(adjustMode)) {
            return "out";
        }
        throw new BizException("基金调库校验返回的调整方向不合法");
    }
}
