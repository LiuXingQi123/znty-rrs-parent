package com.znty.rrs.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.FlowType;
import com.znty.rrs.common.enums.HandlerType;
import com.znty.rrs.common.enums.ItemType;
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.common.util.ExcelImportHelper;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.SysImpTmpBatchBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustCheckReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustSubmitDto;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustSubmitReq;
import com.znty.rrs.entity.securitypoolexcelimport.PoolMemberDto;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportCheckItemDto;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportDto;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportItemDto;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportReq;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustSubmitDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.mapper.SecurityPoolExcelImportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 证券/主体 Excel 导入服务
 * <p>
 * 校验/提交按导入类型分分支：
 * <ul>
 *   <li>证券：对齐证券池批量调整（checkAdjust / addAdjustLog + 批量流程注入）</li>
 *   <li>主体：对齐禁投池调整（checkCompanyAdjust / addCompanyAdjustLog）</li>
 *   <li>清空目标池：独立方法 checkClearTargetOutbound / submitClearTargetOutbound（差集批量出库）</li>
 * </ul>
 * Excel 特有逻辑（模板解析、临时表、权限、父子池解析）在本类完成；调库规则复用既有服务。
 * </p>
 */
@Service
public class SecurityPoolExcelImportService {

    /** 证券导入批次业务类型 */
    private static final String BIZ_TYPE_SECURITY = "security_pool_excel";
    /** 主体导入批次业务类型 */
    private static final String BIZ_TYPE_COMPANY = "company_pool_excel";
    /** 导入类型：证券 */
    private static final String IMPORT_TYPE_SECURITY = "security";
    /** 导入类型：主体 */
    private static final String IMPORT_TYPE_COMPANY = "company";
    /** 调库记录调整类型：Excel 行导入 */
    private static final String ADJUST_TYPE_EXCEL = "Excel导入";
    /** 调库记录调整类型：首先清空目标池 */
    private static final String ADJUST_TYPE_CLEAR = "Excel清空";
    /** 管理员用户 ID（放行 excel_importable） */
    private static final String ADMIN_USER_ID = "1";
    /** 上传文件大小上限（字节） */
    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    /** 单次导入最大数据行数 */
    private static final int MAX_ROWS = 2000;

    /** 导入批次号序号生成器 */
    private static final AtomicInteger IMP_SEQ = new AtomicInteger(0);
    /** 导入明细号序号生成器 */
    private static final AtomicInteger DETL_SEQ = new AtomicInteger(0);

    /** JSON 序列化/反序列化（option_json、result_json） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 证券/主体 Excel 导入数据访问组件（临时表 + 目标池解析） */
    @Resource
    private SecurityPoolExcelImportMapper securityPoolExcelImportMapper;
    /** 投资池数据访问组件（按 ID 查池、权限） */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;
    /** 证券调库数据访问（在途流程查询等） */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;
    /** 证券调库完整校验/提交（批量调库内部亦委托该服务） */
    @Resource
    private SecurityPoolAdjustService securityPoolAdjustService;
    /** 禁投池主体完整校验/提交 */
    @Resource
    private ForbiddenPoolAdjustService forbiddenPoolAdjustService;

    // ═══════════════════════════════════════════════════════════
    //  上传 / 查询 / 取消
    // ═══════════════════════════════════════════════════════════

    /** 上传 Excel 并写入导入临时表 */
    @Transactional(rollbackFor = Exception.class)
    public SecurityPoolExcelImportDto uploadExcel(SecurityPoolExcelImportReq req, MultipartFile file) {
        // 校验上传请求与文件基础参数
        validateUploadReq(req, file);
        // 规范化导入类型（security / company）
        String importType = normalizeImportType(req.getImportType());

        // 解析 Excel 首 sheet
        List<Map<String, String>> excelRows = ExcelImportHelper.parseFirstSheet(file, MAX_ROWS);
        Date now = new Date();
        // 生成导入批次号
        String impId = nextImpId(now);
        // 组装 option_json（clearTarget / allowLinkMutex / importType）
        String optionJson = buildOptionJson(req.getClearTarget(), req.getAllowLinkMutex(), importType);

        SysImpTmpBatchBo batch = new SysImpTmpBatchBo();
        batch.setImpId(impId);
        batch.setBizType(IMPORT_TYPE_COMPANY.equals(importType) ? BIZ_TYPE_COMPANY : BIZ_TYPE_SECURITY);
        batch.setTemplateCode(IMPORT_TYPE_COMPANY.equals(importType)
                ? "company_pool_import" : "security_pool_import");
        batch.setFileName(file.getOriginalFilename());
        batch.setFileSize(file.getSize());
        // 规范化调整方向
        batch.setBizMode(normalizeDirection(req.getDirection()));
        batch.setOptionJson(optionJson);
        // 空白调整原因/意见规范为 null
        batch.setReason(trimToNull(req.getAdjustReason()));
        batch.setAdvice(trimToNull(req.getAdjustAdvice()));
        batch.setTotalCount(0);
        batch.setPassCount(0);
        batch.setFailCount(0);
        batch.setChkRslt("0");
        batch.setSaveRslt("0");
        batch.setImpTime(now);
        batch.setOpterId(req.getCurrentUserId().trim());
        // 解析经办人名称（优先请求中的姓名）
        batch.setOpterName(resolveUserName(req));
        batch.setIsDeleted(0);
        batch.setCrteTime(now);
        batch.setUpdtTime(now);

        List<SysImpTmpBo> items = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (Map<String, String> row : excelRows) {
            SysImpTmpBo item = new SysImpTmpBo();
            // 生成导入明细号
            item.setImpDetlId(nextDetlId(now));
            item.setImpId(impId);
            // 解析 Excel 物理行号
            item.setRowNo(parseRowNo(row.get("__rowNo")));
            item.setChkRslt("0");
            item.setSaveRslt("0");
            item.setImpTime(now);
            item.setOpterId(batch.getOpterId());
            item.setIsDeleted(0);
            item.setCrteTime(now);
            item.setUpdtTime(now);
            // 按导入类型写入字段槽
            fillFromExcelRow(item, row, importType);

            List<String> preFail = new ArrayList<>();
            boolean company = IMPORT_TYPE_COMPANY.equals(importType);
            if (isBlank(item.getFld001())) {
                preFail.add(company ? "主体代码为空" : "证券代码为空");
            }
            if (!company && isBlank(item.getFld003())) {
                preFail.add("父池名称为空");
            }
            if (isBlank(item.getFld004())) {
                preFail.add("子池名称为空");
            }
            String uniq = trimToEmpty(item.getFld001()) + "|" + trimToEmpty(item.getFld004());
            if (!isBlank(item.getFld001()) && !isBlank(item.getFld004()) && !seenKeys.add(uniq)) {
                preFail.add(company ? "Excel 内相同主体代码与子池重复" : "Excel 内相同证券代码与子池重复");
            }
            if (!preFail.isEmpty()) {
                item.setChkRslt("2");
                // 拼接前置失败原因
                item.setChkDscr(joinReasons(preFail));
            }
            items.add(item);
        }
        batch.setTotalCount(items.size());
        int preFailCount = 0;
        for (SysImpTmpBo item : items) {
            if ("2".equals(item.getChkRslt())) {
                preFailCount++;
            }
        }
        batch.setFailCount(preFailCount);
        batch.setPassCount(0);

        securityPoolExcelImportMapper.insertBatch(batch);
        int batchSize = 200;
        for (int i = 0; i < items.size(); i += batchSize) {
            int end = Math.min(i + batchSize, items.size());
            securityPoolExcelImportMapper.insertItemList(items.subList(i, end));
        }

        SecurityPoolExcelImportReq pageReq = new SecurityPoolExcelImportReq();
        pageReq.setImpId(impId);
        pageReq.setPageIndex(1);
        pageReq.setPageSize(20);
        // 回查批次任务信息并返回
        return queryTask(pageReq);
    }

    /** 查询导入批次主表信息（含校验结果快照） */
    public SecurityPoolExcelImportDto queryTask(SecurityPoolExcelImportReq req) {
        // 加载导入批次
        SysImpTmpBatchBo batch = requireBatch(req == null ? null : req.getImpId());
        // 主表转任务 DTO
        SecurityPoolExcelImportDto dto = toTaskDto(batch);
        if (req != null && req.getPageIndex() > 0) {
            // 附带首屏明细分页
            dto.setItems(queryItemPage(req));
        }
        return dto;
    }

    /** 分页查询导入明细 */
    public PageResult<SecurityPoolExcelImportItemDto> queryItemPage(SecurityPoolExcelImportReq req) {
        if (req == null || req.getImpId() == null || req.getImpId().trim().isEmpty()) {
            throw new BizException("导入批次号不能为空");
        }
        // 校验批次存在
        requireBatch(req.getImpId());
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SysImpTmpBo> list = securityPoolExcelImportMapper.queryItemList(
                req.getImpId().trim(), trimToNull(req.getChkRslt()), trimToNull(req.getKeyword()));
        PageInfo<SysImpTmpBo> pageInfo = new PageInfo<>(list);
        List<SecurityPoolExcelImportItemDto> records = new ArrayList<>();
        for (SysImpTmpBo bo : list) {
            // 明细 Bo 转 DTO
            records.add(toItemDto(bo));
        }
        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 取消导入批次（逻辑删除主表与明细） */
    @Transactional(rollbackFor = Exception.class)
    public void cancelImport(SecurityPoolExcelImportReq req) {
        if (req == null || req.getImpId() == null || req.getImpId().trim().isEmpty()) {
            throw new BizException("导入批次号不能为空");
        }
        // 加载导入批次
        SysImpTmpBatchBo batch = requireBatch(req.getImpId());
        if ("1".equals(batch.getSaveRslt())) {
            throw new BizException("该批次已提交，不能取消");
        }
        securityPoolExcelImportMapper.deleteItemsByImpIdSoft(batch.getImpId());
        securityPoolExcelImportMapper.deleteBatchSoft(batch.getImpId());
    }

    // ═══════════════════════════════════════════════════════════
    //  校验入口：证券 / 主体 分支
    // ═══════════════════════════════════════════════════════════

    /**
     * 对批次明细执行业务校验。
     * <p>证券分支对齐批量调库；主体分支对齐禁投池（多主体逐条）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public SecurityPoolExcelImportDto checkImport(SecurityPoolExcelImportReq req) {
        if (req == null || req.getImpId() == null || req.getImpId().trim().isEmpty()) {
            throw new BizException("导入批次号不能为空");
        }
        // 加载导入批次
        SysImpTmpBatchBo batch = requireBatch(req.getImpId());
        if ("1".equals(batch.getSaveRslt())) {
            throw new BizException("该批次已提交，不能再次校验");
        }
        String opterId = req.getCurrentUserId() != null && !req.getCurrentUserId().trim().isEmpty()
                ? req.getCurrentUserId().trim() : batch.getOpterId();
        List<SysImpTmpBo> items = securityPoolExcelImportMapper.queryAllByImpId(batch.getImpId());
        if (items.isEmpty()) {
            throw new BizException("导入明细为空");
        }

        boolean inbound = "in".equals(batch.getBizMode());
        // 解析是否允许联动与互斥
        boolean allowLinkMutex = parseAllowLinkMutex(batch.getOptionJson());
        // 解析是否首先清空目标池
        boolean clearTarget = parseClearTarget(batch.getOptionJson());
        // 解析导入类型（证券/主体）
        String importType = parseImportType(batch.getOptionJson(), batch.getBizType());
        Date now = new Date();
        // 是否主体导入
        boolean companyImport = IMPORT_TYPE_COMPANY.equals(importType);

        List<SecurityPoolExcelImportCheckItemDto> checkItems;
        if (companyImport) {
            // ── 主体分支：禁投池调整同构 ──
            checkItems = checkCompanyImport(items, inbound, allowLinkMutex, opterId, now);
        } else {
            // ── 证券分支：证券池批量调整同构 ──
            checkItems = checkSecurityImport(items, inbound, allowLinkMutex, opterId, now);
        }
        // 清空目标池校验（独立方法，与证券/主体并列）
        List<SecurityPoolExcelImportCheckItemDto> clearItems =
                checkClearTargetOutbound(items, inbound, clearTarget, companyImport, allowLinkMutex, opterId);
        if (!clearItems.isEmpty()) {
            List<SecurityPoolExcelImportCheckItemDto> merged = new ArrayList<>(clearItems);
            merged.addAll(checkItems);
            checkItems = merged;
        }

        int pass = 0;
        int fail = 0;
        for (SysImpTmpBo item : items) {
            if ("1".equals(item.getChkRslt())) {
                pass++;
            } else if ("2".equals(item.getChkRslt())) {
                fail++;
            }
        }
        int checkPass = 0;
        int checkFail = 0;
        for (SecurityPoolExcelImportCheckItemDto ci : checkItems) {
            if (ci.isCanAdjust()) {
                checkPass++;
            } else {
                checkFail++;
            }
        }

        batch.setPassCount(pass);
        batch.setFailCount(fail);
        boolean allOk = fail == 0 && checkFail == 0;
        batch.setChkRslt(allOk ? "1" : "2");
        if (allOk) {
            batch.setChkDscr("全部校验通过（含调库展开项 " + checkItems.size() + " 条）");
        } else {
            batch.setChkDscr("导入行失败 " + fail + " 条，校验结果失败 " + checkFail + " 条");
        }
        // 序列化调库校验结果写入主表
        batch.setResultJson(buildCheckResultJson(checkItems, checkPass, checkFail, allowLinkMutex, importType));
        batch.setUpdtTime(now);
        securityPoolExcelImportMapper.updateBatchCheckResult(batch);

        SecurityPoolExcelImportReq pageReq = new SecurityPoolExcelImportReq();
        pageReq.setImpId(batch.getImpId());
        pageReq.setPageIndex(1);
        pageReq.setPageSize(20);
        // 回查批次任务信息并返回
        return queryTask(pageReq);
    }

    /**
     * 证券导入校验：编排对齐 {@link BatchSecurityPoolAdjustService#checkAdjust}。
     * <p>每行 Excel 对应「一券 + 一目标池 + 方向」→ 调用
     * {@link SecurityPoolAdjustService#checkAdjust}（批量内部也是此路径），
     * 再注入目标池批量流程候选；未勾选联动互斥时仅保留手工项。</p>
     */
    private List<SecurityPoolExcelImportCheckItemDto> checkSecurityImport(
            List<SysImpTmpBo> items, boolean inbound, boolean allowLinkMutex,
            String opterId, Date now) {
        List<SecurityPoolExcelImportCheckItemDto> checkItems = new ArrayList<>();
        Map<String, InvestmentPoolBo> poolNameCache = new HashMap<>();
        String adjustMode = inbound ? AdjustMode.IN.getCode() : AdjustMode.OUT.getCode();
        String direction = inbound ? "in" : "out";

        for (SysImpTmpBo item : items) {
            List<String> preReasons = new ArrayList<>();
            String code = trimToEmpty(item.getFld001());
            String parentName = trimToEmpty(item.getFld003());
            String childName = trimToEmpty(item.getFld004());

            if (code.isEmpty()) {
                preReasons.add("证券代码为空");
            }
            if (parentName.isEmpty()) {
                preReasons.add("父池名称为空");
            }
            if (childName.isEmpty()) {
                preReasons.add("子池名称为空");
            }

            InvestmentPoolBo pool = null;
            if (preReasons.isEmpty()) {
                // 按父子池名称解析叶子池
                pool = resolvePoolByNames(parentName, childName, poolNameCache);
                if (pool == null) {
                    preReasons.add("未找到启用叶子池：父池[" + parentName + "] / 子池[" + childName + "]");
                } else {
                    item.setFld009(String.valueOf(pool.getId()));
                    item.setFld010(pool.getPoolType());
                    try {
                        // 校验 Excel 导入权限
                        validateExcelImportPermission(opterId, pool.getId());
                    } catch (BizException e) {
                        preReasons.add(e.getMessage());
                    }
                }
            }

            if (!preReasons.isEmpty()) {
                // 标记导入行校验失败
                markItemFail(item, preReasons, now);
                // 构建前置失败的校验结果项
                checkItems.add(buildFailManualItem(item, code, item.getFld002(), null, pool,
                        adjustMode, preReasons));
                continue;
            }

            // 对齐批量：构建单证券 AdjustCheckReq 并委托完整校验
            AdjustCheckReq checkReq = new AdjustCheckReq();
            checkReq.setSecurityCode(code);
            checkReq.setSecurityShortName(trimToNull(item.getFld002()));
            AdjustCheckReq.CheckItem checkItem = new AdjustCheckReq.CheckItem();
            checkItem.setTargetPoolId(pool.getId());
            checkItem.setTargetPoolName(pool.getPoolName());
            checkItem.setPoolType(pool.getPoolType());
            checkItem.setAdjustMode(adjustMode);
            checkReq.setItems(Collections.singletonList(checkItem));

            AdjustCheckDto checkDto;
            try {
                checkDto = securityPoolAdjustService.checkAdjust(checkReq);
            } catch (BizException e) {
                List<String> reasons = Collections.singletonList(e.getMessage());
                // 标记导入行校验失败
                markItemFail(item, reasons, now);
                // 构建前置失败的校验结果项
                checkItems.add(buildFailManualItem(item, code, item.getFld002(), null, pool,
                        adjustMode, reasons));
                continue;
            }

            List<AdjustCheckDto.CheckResultItem> resultItems =
                    checkDto.getItems() == null ? new ArrayList<>() : checkDto.getItems();
            // 未勾选联动与互斥：只保留手工项（批量勾选后才完整展开；Excel 用选项控制）
            if (!allowLinkMutex) {
                List<AdjustCheckDto.CheckResultItem> manuals = new ArrayList<>();
                for (AdjustCheckDto.CheckResultItem ri : resultItems) {
                    // 判断是否为手工调库项
                    if (isManualTag(ri.getItemTag())) {
                        manuals.add(ri);
                    }
                }
                resultItems = manuals;
            }

            boolean manualPass = false;
            String shortName = trimToEmpty(item.getFld002());
            for (AdjustCheckDto.CheckResultItem ri : resultItems) {
                // 映射证券校验结果并注入批量流程
                SecurityPoolExcelImportCheckItemDto mapped =
                        mapSecurityCheckResult(item, ri, code, pool, direction);
                // 判断手工主项是否可调整
                if (isManualTag(mapped.getItemTag()) && mapped.isCanAdjust()) {
                    manualPass = true;
                    if (isBlank(shortName) && !isBlank(mapped.getSecurityShortName())) {
                        shortName = mapped.getSecurityShortName();
                        item.setFld002(shortName);
                    }
                }
                checkItems.add(mapped);
            }

            if (manualPass) {
                item.setChkRslt("1");
                item.setChkDscr("校验通过");
            } else {
                List<String> failReasons = new ArrayList<>();
                for (SecurityPoolExcelImportCheckItemDto ci : checkItems) {
                    // 汇总本行手工主项的失败原因
                    if (item.getId() != null && item.getId().equals(ci.getSourceItemId())
                            && isManualTag(ci.getItemTag()) && !ci.isCanAdjust()
                            && ci.getFailReasons() != null) {
                        failReasons.addAll(ci.getFailReasons());
                    }
                }
                if (failReasons.isEmpty()) {
                    failReasons.add("校验未通过");
                }
                // 标记导入行校验失败
                markItemFail(item, failReasons, now);
                continue;
            }
            item.setUpdtTime(now);
            securityPoolExcelImportMapper.updateItemCheckResult(item);
        }
        return checkItems;
    }

    /**
     * 主体导入校验：编排对齐 {@link ForbiddenPoolAdjustService#checkCompanyAdjust}。
     * <p>每行 Excel 对应「一主体 + 一目标池 + 方向」→ 调用禁投池主体完整校验；
     * 未勾选联动互斥时仅保留手工项。</p>
     */
    private List<SecurityPoolExcelImportCheckItemDto> checkCompanyImport(
            List<SysImpTmpBo> items, boolean inbound, boolean allowLinkMutex,
            String opterId, Date now) {
        List<SecurityPoolExcelImportCheckItemDto> checkItems = new ArrayList<>();
        Map<String, InvestmentPoolBo> poolNameCache = new HashMap<>();
        String adjustMode = inbound ? AdjustMode.IN.getCode() : AdjustMode.OUT.getCode();

        for (SysImpTmpBo item : items) {
            List<String> preReasons = new ArrayList<>();
            String code = trimToEmpty(item.getFld001());
            String parentName = trimToEmpty(item.getFld003());
            String childName = trimToEmpty(item.getFld004());

            if (code.isEmpty()) {
                preReasons.add("主体代码为空");
            }
            if (childName.isEmpty()) {
                preReasons.add("子池名称为空");
            }

            InvestmentPoolBo pool = null;
            if (preReasons.isEmpty()) {
                // 按父子池名称解析叶子池
                pool = resolvePoolByNames(isBlank(parentName) ? null : parentName, childName, poolNameCache);
                if (pool == null) {
                    preReasons.add(isBlank(parentName)
                            ? ("未找到启用叶子池：子池[" + childName + "]")
                            : ("未找到启用叶子池：父池[" + parentName + "] / 子池[" + childName + "]"));
                } else {
                    item.setFld009(String.valueOf(pool.getId()));
                    item.setFld010(pool.getPoolType());
                    try {
                        // 校验 Excel 导入权限
                        validateExcelImportPermission(opterId, pool.getId());
                    } catch (BizException e) {
                        preReasons.add(e.getMessage());
                    }
                }
            }

            if (!preReasons.isEmpty()) {
                // 标记导入行校验失败
                markItemFail(item, preReasons, now);
                // 构建前置失败的校验结果项
                checkItems.add(buildFailManualItem(item, code, item.getFld002(), "company", pool,
                        adjustMode, preReasons));
                continue;
            }

            ForbiddenPoolAdjustCheckReq companyReq = new ForbiddenPoolAdjustCheckReq();
            companyReq.setCompanyCode(code);
            companyReq.setCompanyShortName(trimToNull(item.getFld002()));
            ForbiddenPoolAdjustCheckReq.CheckItem cItem = new ForbiddenPoolAdjustCheckReq.CheckItem();
            cItem.setTargetPoolId(pool.getId());
            cItem.setTargetPoolName(pool.getPoolName());
            cItem.setPoolType(pool.getPoolType());
            cItem.setAdjustMode(adjustMode);
            companyReq.setItems(Collections.singletonList(cItem));

            AdjustCheckDto checkDto;
            try {
                checkDto = forbiddenPoolAdjustService.checkCompanyAdjust(companyReq);
            } catch (BizException e) {
                List<String> reasons = Collections.singletonList(e.getMessage());
                // 标记导入行校验失败
                markItemFail(item, reasons, now);
                // 构建前置失败的校验结果项
                checkItems.add(buildFailManualItem(item, code, item.getFld002(), "company", pool,
                        adjustMode, reasons));
                continue;
            }

            List<AdjustCheckDto.CheckResultItem> resultItems =
                    checkDto.getItems() == null ? new ArrayList<>() : checkDto.getItems();
            // 未勾选联动与互斥：只保留手工项
            if (!allowLinkMutex) {
                List<AdjustCheckDto.CheckResultItem> manuals = new ArrayList<>();
                for (AdjustCheckDto.CheckResultItem ri : resultItems) {
                    // 判断是否为手工调库项
                    if (isManualTag(ri.getItemTag())) {
                        manuals.add(ri);
                    }
                }
                resultItems = manuals;
            }

            boolean manualPass = false;
            for (AdjustCheckDto.CheckResultItem ri : resultItems) {
                // 映射主体校验结果
                SecurityPoolExcelImportCheckItemDto mapped =
                        mapCompanyCheckResult(item, ri, code);
                // 判断手工主项是否可调整
                if (isManualTag(mapped.getItemTag()) && mapped.isCanAdjust()) {
                    manualPass = true;
                    if (isBlank(item.getFld002()) && !isBlank(mapped.getSecurityShortName())) {
                        item.setFld002(mapped.getSecurityShortName());
                    }
                }
                checkItems.add(mapped);
            }

            if (manualPass) {
                item.setChkRslt("1");
                item.setChkDscr("校验通过");
                item.setUpdtTime(now);
                securityPoolExcelImportMapper.updateItemCheckResult(item);
            } else {
                List<String> failReasons = new ArrayList<>();
                for (SecurityPoolExcelImportCheckItemDto ci : checkItems) {
                    // 汇总本行手工主项的失败原因
                    if (item.getId() != null && item.getId().equals(ci.getSourceItemId())
                            && isManualTag(ci.getItemTag()) && !ci.isCanAdjust()
                            && ci.getFailReasons() != null) {
                        failReasons.addAll(ci.getFailReasons());
                    }
                }
                if (failReasons.isEmpty()) {
                    failReasons.add("校验未通过");
                }
                // 标记导入行校验失败
                markItemFail(item, failReasons, now);
            }
        }
        return checkItems;
    }

    // ═══════════════════════════════════════════════════════════
    //  提交入口：证券 / 主体 分支
    // ═══════════════════════════════════════════════════════════

    /**
     * 按校验结果提交。证券走证券池调库提交；主体走禁投池主体提交。
     */
    @Transactional(rollbackFor = Exception.class)
    public SecurityPoolExcelImportDto submitImport(SecurityPoolExcelImportReq req) {
        if (req == null || req.getImpId() == null || req.getImpId().trim().isEmpty()) {
            throw new BizException("导入批次号不能为空");
        }
        // 加载导入批次
        SysImpTmpBatchBo batch = requireBatch(req.getImpId());
        if ("1".equals(batch.getSaveRslt())) {
            throw new BizException("该批次已提交，请勿重复提交");
        }
        // 筛选可提交的校验结果
        List<SecurityPoolExcelImportCheckItemDto> checkItems = filterSubmittableCheckItems(req, batch);
        if (checkItems.isEmpty()) {
            throw new BizException("没有可提交的校验结果，请先校验");
        }
        String opterId = req.getCurrentUserId() != null && !req.getCurrentUserId().trim().isEmpty()
                ? req.getCurrentUserId().trim() : batch.getOpterId();
        String opterName = req.getCurrentUserName() != null && !req.getCurrentUserName().trim().isEmpty()
                ? req.getCurrentUserName().trim() : batch.getOpterName();
        if (isBlank(opterId)) {
            throw new BizException("经办人 ID 不能为空");
        }
        if (isBlank(opterName)) {
            opterName = opterId;
        }
        if (req.getAdjustReason() != null && !req.getAdjustReason().trim().isEmpty()) {
            batch.setReason(req.getAdjustReason().trim());
        }
        if (req.getAdjustAdvice() != null && !req.getAdjustAdvice().trim().isEmpty()) {
            batch.setAdvice(req.getAdjustAdvice().trim());
        }

        // 解析导入类型（证券/主体）
        String importType = parseImportType(batch.getOptionJson(), batch.getBizType());
        // 拆分清空出库项与 Excel 导入项
        List<SecurityPoolExcelImportCheckItemDto> clearItems = new ArrayList<>();
        List<SecurityPoolExcelImportCheckItemDto> excelItems = new ArrayList<>();
        for (SecurityPoolExcelImportCheckItemDto ci : checkItems) {
            // 拆分：清空出库项 vs Excel 导入项
            if (isClearTag(ci.getItemTag())) {
                clearItems.add(ci);
            } else {
                excelItems.add(ci);
            }
        }
        List<Long> logIds = new ArrayList<>();
        List<String> batchNos = new ArrayList<>();
        // 先提交清空出库，再提交 Excel 导入项
        if (!clearItems.isEmpty()) {
            // 提交清空目标池出库
            logIds.addAll(submitClearTargetOutbound(clearItems, batch, opterId, opterName,
                    IMPORT_TYPE_COMPANY.equals(importType), batchNos));
        }
        if (IMPORT_TYPE_COMPANY.equals(importType)) {
            // 主体分支：禁投池同构提交
            logIds.addAll(submitCompanyImport(excelItems, batch, opterId, opterName, batchNos));
        } else {
            // 证券分支：证券调库同构提交
            logIds.addAll(submitSecurityImport(excelItems, batch, opterId, opterName, batchNos));
        }

        // 回写导入明细保存状态
        Date now = new Date();
        Map<Long, SysImpTmpBo> sourceMap = new HashMap<>();
        for (SysImpTmpBo bo : securityPoolExcelImportMapper.queryAllByImpId(batch.getImpId())) {
            sourceMap.put(bo.getId(), bo);
        }
        for (SecurityPoolExcelImportCheckItemDto ci : checkItems) {
            // 仅回写 Excel 手工主项对应的导入明细
            if (!isManualTag(ci.getItemTag()) || ci.getSourceItemId() == null) {
                continue;
            }
            SysImpTmpBo source = sourceMap.get(ci.getSourceItemId());
            if (source == null) {
                continue;
            }
            source.setSaveRslt("1");
            source.setSaveDscr("提交成功");
            source.setUpdtTime(now);
            securityPoolExcelImportMapper.updateItemSaveResult(source);
        }

        String adjustBatchNo = batchNos.isEmpty() ? null : batchNos.get(0);
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(
                    batch.getResultJson() == null ? "{}" : batch.getResultJson());
            node.put("submitted", true);
            if (adjustBatchNo != null) {
                node.put("adjustBatchNo", adjustBatchNo);
            }
            node.put("logCount", logIds.size());
            batch.setResultJson(objectMapper.writeValueAsString(node));
        } catch (Exception e) {
            batch.setResultJson("{\"submitted\":true,\"logCount\":" + logIds.size() + "}");
        }
        batch.setSaveRslt("1");
        batch.setSaveDscr("提交成功，共 " + logIds.size() + " 条调库记录"
                + (adjustBatchNo != null ? "，批次号 " + adjustBatchNo : ""));
        batch.setUpdtTime(now);
        securityPoolExcelImportMapper.updateBatchSaveResult(batch);

        // 主表转任务 DTO
        SecurityPoolExcelImportDto dto = toTaskDto(securityPoolExcelImportMapper.queryByImpId(batch.getImpId()));
        dto.setAdjustBatchNoList(batchNos);
        dto.setLogIds(logIds);
        return dto;
    }

    /**
     * 证券提交：按主券分组，逐组调用 {@link SecurityPoolAdjustService#addAdjustLog}
     * （与批量调库 addSingleAdjustLog 同路径）。
     */
    private List<Long> submitSecurityImport(List<SecurityPoolExcelImportCheckItemDto> checkItems,
                                            SysImpTmpBatchBo batch, String opterId, String opterName,
                                            List<String> batchNos) {
        // 按 sourceSecurityCode（缺省 securityCode）分组，关联/联动/互斥同批提交
        Map<String, List<SecurityPoolExcelImportCheckItemDto>> groupMap = new LinkedHashMap<>();
        for (SecurityPoolExcelImportCheckItemDto ci : checkItems) {
            String groupKey = !isBlank(ci.getSourceSecurityCode())
                    ? ci.getSourceSecurityCode() : ci.getSecurityCode();
            List<SecurityPoolExcelImportCheckItemDto> list = groupMap.get(groupKey);
            if (list == null) {
                list = new ArrayList<>();
                groupMap.put(groupKey, list);
            }
            list.add(ci);
        }

        List<Long> allLogIds = new ArrayList<>();
        for (Map.Entry<String, List<SecurityPoolExcelImportCheckItemDto>> entry : groupMap.entrySet()) {
            List<SecurityPoolExcelImportCheckItemDto> group = entry.getValue();
            // 同组优先取手工主项
            SecurityPoolExcelImportCheckItemDto primary = resolvePrimaryItem(group);

            SecurityPoolAdjustSubmitReq submitReq = new SecurityPoolAdjustSubmitReq();
            submitReq.setSecurityCode(entry.getKey());
            submitReq.setSecurityShortName(primary.getSecurityShortName());
            submitReq.setSecurityType(primary.getSecurityType());
            submitReq.setAdjustType(ADJUST_TYPE_EXCEL);
            submitReq.setAdjustReason(batch.getReason());
            submitReq.setAdjustAdvice(batch.getAdvice());
            submitReq.setAdjusterId(opterId);
            submitReq.setAdjusterName(opterName);

            List<SecurityPoolAdjustSubmitReq.AdjustItem> submitItems = new ArrayList<>();
            for (SecurityPoolExcelImportCheckItemDto ci : group) {
                if (ci.getTargetPoolId() == null) {
                    throw new BizException("校验结果数据不完整，请重新校验");
                }
                // 校验 Excel 导入权限
                validateExcelImportPermission(opterId, ci.getTargetPoolId());

                SecurityPoolAdjustSubmitReq.AdjustItem si = new SecurityPoolAdjustSubmitReq.AdjustItem();
                si.setSecurityCode(ci.getSecurityCode());
                si.setSecurityShortName(ci.getSecurityShortName());
                si.setSecurityType(ci.getSecurityType());
                si.setTargetPoolId(ci.getTargetPoolId());
                si.setTargetPoolName(ci.getTargetPoolName());
                si.setPoolType(ci.getPoolType());
                si.setAdjustMode(ci.getAdjustMode());
                si.setItemTag(isBlank(ci.getItemTag()) ? ItemType.MANUAL.getCode() : ci.getItemTag());
                si.setAdjustGroupKey(ci.getAdjustGroupKey());
                si.setFlowId(ci.getFlowId());
                si.setFlowKey(ci.getFlowKey());
                si.setFlowType(ci.getFlowType());
                si.setAdjustmentNote(ci.getAdjustNote());
                submitItems.add(si);
            }
            submitReq.setItems(submitItems);

            AdjustSubmitDto submitDto = securityPoolAdjustService.addAdjustLog(submitReq);
            if (submitDto != null && submitDto.getLogIds() != null) {
                allLogIds.addAll(submitDto.getLogIds());
            }
        }
        return allLogIds;
    }

    /**
     * 主体提交：按主体代码分组，逐组调用 {@link ForbiddenPoolAdjustService#addCompanyAdjustLog}。
     */
    private List<Long> submitCompanyImport(List<SecurityPoolExcelImportCheckItemDto> checkItems,
                                           SysImpTmpBatchBo batch, String opterId, String opterName,
                                           List<String> batchNos) {
        Map<String, List<SecurityPoolExcelImportCheckItemDto>> groupMap = new LinkedHashMap<>();
        for (SecurityPoolExcelImportCheckItemDto ci : checkItems) {
            String groupKey = !isBlank(ci.getSourceSecurityCode())
                    ? ci.getSourceSecurityCode() : ci.getSecurityCode();
            List<SecurityPoolExcelImportCheckItemDto> list = groupMap.get(groupKey);
            if (list == null) {
                list = new ArrayList<>();
                groupMap.put(groupKey, list);
            }
            list.add(ci);
        }

        List<Long> allLogIds = new ArrayList<>();
        for (Map.Entry<String, List<SecurityPoolExcelImportCheckItemDto>> entry : groupMap.entrySet()) {
            List<SecurityPoolExcelImportCheckItemDto> group = entry.getValue();
            // 同组优先取手工主项
            SecurityPoolExcelImportCheckItemDto primary = resolvePrimaryItem(group);

            ForbiddenPoolAdjustSubmitReq submitReq = new ForbiddenPoolAdjustSubmitReq();
            submitReq.setCompanyCode(entry.getKey());
            submitReq.setCompanyShortName(primary.getSecurityShortName());
            submitReq.setSecurityType("company");
            submitReq.setAdjustType(ADJUST_TYPE_EXCEL);
            submitReq.setAdjustReason(batch.getReason());
            submitReq.setAdjustAdvice(batch.getAdvice());
            submitReq.setAdjusterId(opterId);
            submitReq.setAdjusterName(opterName);

            List<ForbiddenPoolAdjustSubmitReq.AdjustItem> submitItems = new ArrayList<>();
            for (SecurityPoolExcelImportCheckItemDto ci : group) {
                if (ci.getTargetPoolId() == null) {
                    throw new BizException("校验结果数据不完整，请重新校验");
                }
                // 校验 Excel 导入权限
                validateExcelImportPermission(opterId, ci.getTargetPoolId());

                ForbiddenPoolAdjustSubmitReq.AdjustItem si = new ForbiddenPoolAdjustSubmitReq.AdjustItem();
                si.setTargetPoolId(ci.getTargetPoolId());
                si.setTargetPoolName(ci.getTargetPoolName());
                si.setPoolType(ci.getPoolType());
                si.setAdjustMode(ci.getAdjustMode());
                si.setItemTag(isBlank(ci.getItemTag()) ? ItemType.MANUAL.getCode() : ci.getItemTag());
                si.setAdjustGroupKey(ci.getAdjustGroupKey());
                si.setFlowId(ci.getFlowId());
                si.setFlowKey(ci.getFlowKey());
                si.setFlowType(ci.getFlowType());
                si.setAdjustmentNote(ci.getAdjustNote());
                submitItems.add(si);
            }
            submitReq.setItems(submitItems);

            ForbiddenPoolAdjustSubmitDto submitDto =
                    forbiddenPoolAdjustService.addCompanyAdjustLog(submitReq, Collections.emptyList());
            if (submitDto != null && submitDto.getLogIds() != null) {
                allLogIds.addAll(submitDto.getLogIds());
            }
        }
        return allLogIds;
    }

    // ═══════════════════════════════════════════════════════════
    //  清空目标池（独立分支，与证券/主体并列）
    // ═══════════════════════════════════════════════════════════

    /**
     * 首先清空目标池 — 校验。
     * <p>
     * 仅调入且勾选 clearTarget 时生效。对每个本批涉及的目标池：
     * 在池成员 − 本批 Excel 代码 = 差集出库名单，走批量调出校验并注入 batchOut 流程。
     * </p>
     * <p>
     * 在途规则：本批导入编码的在途由证券/主体校验路径处理（对应行失败）；
     * 非本批导入编码若存在在途，不纳入清空名单（不特殊管控）。
     * </p>
     */
    private List<SecurityPoolExcelImportCheckItemDto> checkClearTargetOutbound(
            List<SysImpTmpBo> items, boolean inbound, boolean clearTarget,
            boolean companyImport, boolean allowLinkMutex, String opterId) {
        List<SecurityPoolExcelImportCheckItemDto> clearItems = new ArrayList<>();
        if (!clearTarget || !inbound || items == null || items.isEmpty()) {
            return clearItems;
        }

        // 汇总本批 Excel：目标池 ID → 导入代码集合
        Map<Long, Set<String>> excelCodesByPool = new LinkedHashMap<>();
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        for (SysImpTmpBo item : items) {
            // 解析明细中的目标池 ID
            Long poolId = parsePoolId(item.getFld009());
            // 读取明细证券/主体代码
            String code = trimToEmpty(item.getFld001());
            if (poolId == null || code.isEmpty()) {
                continue;
            }
            Set<String> codes = excelCodesByPool.get(poolId);
            if (codes == null) {
                codes = new HashSet<>();
                excelCodesByPool.put(poolId, codes);
            }
            codes.add(code);
            if (!poolMap.containsKey(poolId)) {
                // 按 ID 加载目标池
                InvestmentPoolBo pool = investmentPoolMapper.queryPoolById(poolId);
                if (pool != null) {
                    poolMap.put(poolId, pool);
                }
            }
        }
        if (excelCodesByPool.isEmpty()) {
            return clearItems;
        }

        String memberType = companyImport ? "company" : "security";
        for (Map.Entry<Long, Set<String>> entry : excelCodesByPool.entrySet()) {
            Long poolId = entry.getKey();
            InvestmentPoolBo pool = poolMap.get(poolId);
            if (pool == null) {
                continue;
            }
            try {
                // 校验 Excel 导入权限
                validateExcelImportPermission(opterId, poolId);
            } catch (BizException e) {
                continue;
            }
            Set<String> excelCodes = entry.getValue();
            // 查询目标池当前有效在池成员
            List<PoolMemberDto> members =
                    securityPoolExcelImportMapper.queryPoolMemberList(poolId, memberType);
            if (members == null || members.isEmpty()) {
                continue;
            }
            for (PoolMemberDto member : members) {
                if (member == null || isBlank(member.getSecurityCode())) {
                    continue;
                }
                String code = member.getSecurityCode().trim();
                // 差集：本批 Excel 中的代码不做出库清空
                if (excelCodes.contains(code)) {
                    continue;
                }
                // 非本批编码存在在途：跳过，不纳入清空
                if (securityPoolAdjustMapper.querySecurityHasPendingProcess(code)) {
                    continue;
                }
                // 对差集成员执行清空出库校验
                clearItems.addAll(buildClearOutboundCheckItems(
                        member, pool, companyImport, allowLinkMutex));
            }
        }
        return clearItems;
    }

    /**
     * 对单个清空出库成员执行调出校验并映射为 checkItem（含 batchOut 流程）
     */
    private List<SecurityPoolExcelImportCheckItemDto> buildClearOutboundCheckItems(
            PoolMemberDto member, InvestmentPoolBo pool,
            boolean companyImport, boolean allowLinkMutex) {
        List<SecurityPoolExcelImportCheckItemDto> result = new ArrayList<>();
        String code = member.getSecurityCode().trim();
        String shortName = member.getSecurityShortName();
        String securityType = member.getSecurityType();
        String adjustMode = AdjustMode.OUT.getCode();

        AdjustCheckDto checkDto;
        try {
            if (companyImport) {
                // 主体：禁投池调出校验
                ForbiddenPoolAdjustCheckReq companyReq = new ForbiddenPoolAdjustCheckReq();
                companyReq.setCompanyCode(code);
                companyReq.setCompanyShortName(shortName);
                ForbiddenPoolAdjustCheckReq.CheckItem cItem = new ForbiddenPoolAdjustCheckReq.CheckItem();
                cItem.setTargetPoolId(pool.getId());
                cItem.setTargetPoolName(pool.getPoolName());
                cItem.setPoolType(pool.getPoolType());
                cItem.setAdjustMode(adjustMode);
                companyReq.setItems(Collections.singletonList(cItem));
                checkDto = forbiddenPoolAdjustService.checkCompanyAdjust(companyReq);
            } else {
                // 证券：调库调出校验（与批量同路径）
                AdjustCheckReq checkReq = new AdjustCheckReq();
                checkReq.setSecurityCode(code);
                checkReq.setSecurityShortName(shortName);
                checkReq.setSecurityType(securityType);
                AdjustCheckReq.CheckItem checkItem = new AdjustCheckReq.CheckItem();
                checkItem.setTargetPoolId(pool.getId());
                checkItem.setTargetPoolName(pool.getPoolName());
                checkItem.setPoolType(pool.getPoolType());
                checkItem.setAdjustMode(adjustMode);
                checkReq.setItems(Collections.singletonList(checkItem));
                checkDto = securityPoolAdjustService.checkAdjust(checkReq);
            }
        } catch (BizException e) {
            // 构建清空出库失败项
            result.add(buildClearFailItem(code, shortName, securityType, pool, e.getMessage()));
            return result;
        }

        List<AdjustCheckDto.CheckResultItem> resultItems =
                checkDto.getItems() == null ? new ArrayList<>() : checkDto.getItems();
        // 未勾选联动互斥时仅保留手工主项
        if (!allowLinkMutex) {
            List<AdjustCheckDto.CheckResultItem> manuals = new ArrayList<>();
            for (AdjustCheckDto.CheckResultItem ri : resultItems) {
                // 判断是否为手工项
                if (isManualTag(ri.getItemTag())) {
                    manuals.add(ri);
                }
            }
            resultItems = manuals;
        }
        if (resultItems.isEmpty()) {
            // 构建清空出库失败项
            result.add(buildClearFailItem(code, shortName, securityType, pool, "清空出库校验无结果"));
            return result;
        }
        for (AdjustCheckDto.CheckResultItem ri : resultItems) {
            SecurityPoolExcelImportCheckItemDto mapped;
            if (companyImport) {
                // 映射主体校验结果
                mapped = mapCompanyCheckResult(null, ri, code);
            } else {
                // 映射证券校验结果并注入批量流程
                mapped = mapSecurityCheckResult(null, ri, code, pool, "out");
            }
            // 主项标记为清空出库；联动/互斥保留原标签
            if (isManualTag(mapped.getItemTag())) {
                mapped.setItemTag(ItemType.CLEAR.getCode());
                mapped.setAdjustType("清空出库");
                if (mapped.isCanAdjust()) {
                    mapped.setAdjustNote("首先清空目标池");
                    List<SecurityPoolExcelImportCheckItemDto.FlowOptionDto> flowOptions =
                            mapped.getFlowOptions() == null
                                    ? new ArrayList<>()
                                    : new ArrayList<>(mapped.getFlowOptions());
                    // 注入目标池批量调出流程
                    injectBatchFlowOption(flowOptions, pool, "out");
                    mapped.setFlowOptions(flowOptions);
                    // 默认选中推荐流程
                    applyDefaultSelectedFlow(mapped);
                }
            }
            result.add(mapped);
        }
        return result;
    }

    /**
     * 构建清空出库失败项
     */
    private SecurityPoolExcelImportCheckItemDto buildClearFailItem(
            String code, String shortName, String securityType,
            InvestmentPoolBo pool, String reason) {
        SecurityPoolExcelImportCheckItemDto dto = new SecurityPoolExcelImportCheckItemDto();
        dto.setSecurityCode(code);
        dto.setSecurityShortName(shortName);
        dto.setSecurityType(securityType);
        dto.setSourceSecurityCode(code);
        if (pool != null) {
            dto.setTargetPoolId(pool.getId());
            dto.setTargetPoolName(pool.getPoolName());
            dto.setPoolType(pool.getPoolType());
        }
        dto.setAdjustMode(AdjustMode.OUT.getCode());
        dto.setItemTag(ItemType.CLEAR.getCode());
        dto.setAdjustType("清空出库");
        dto.setAdjustGroupKey(code + "_" + (pool != null ? pool.getId() : "0") + "_清空出库");
        dto.setCanAdjust(false);
        List<String> reasons = new ArrayList<>();
        reasons.add(reason == null ? "清空出库失败" : reason);
        dto.setFailReasons(reasons);
        return dto;
    }

    /**
     * 首先清空目标池 — 提交（独立方法）
     * <p>按代码分组，证券走 addAdjustLog，主体走 addCompanyAdjustLog；adjustType=Excel清空。</p>
     */
    private List<Long> submitClearTargetOutbound(List<SecurityPoolExcelImportCheckItemDto> clearItems,
                                                 SysImpTmpBatchBo batch, String opterId, String opterName,
                                                 boolean companyImport, List<String> batchNos) {
        if (clearItems == null || clearItems.isEmpty()) {
            return new ArrayList<>();
        }
        // 按主体/证券代码分组
        Map<String, List<SecurityPoolExcelImportCheckItemDto>> groupMap = new LinkedHashMap<>();
        for (SecurityPoolExcelImportCheckItemDto ci : clearItems) {
            String groupKey = !isBlank(ci.getSourceSecurityCode())
                    ? ci.getSourceSecurityCode() : ci.getSecurityCode();
            List<SecurityPoolExcelImportCheckItemDto> list = groupMap.get(groupKey);
            if (list == null) {
                list = new ArrayList<>();
                groupMap.put(groupKey, list);
            }
            list.add(ci);
        }
        List<Long> allLogIds = new ArrayList<>();
        for (Map.Entry<String, List<SecurityPoolExcelImportCheckItemDto>> entry : groupMap.entrySet()) {
            List<SecurityPoolExcelImportCheckItemDto> group = entry.getValue();
            // 同组优先取清空/手工主项
            SecurityPoolExcelImportCheckItemDto primary = resolvePrimaryItem(group);
            if (companyImport) {
                // 主体清空出库提交
                ForbiddenPoolAdjustSubmitReq submitReq = new ForbiddenPoolAdjustSubmitReq();
                submitReq.setCompanyCode(entry.getKey());
                submitReq.setCompanyShortName(primary.getSecurityShortName());
                submitReq.setSecurityType("company");
                submitReq.setAdjustType(ADJUST_TYPE_CLEAR);
                submitReq.setAdjustReason(batch.getReason());
                submitReq.setAdjustAdvice(batch.getAdvice());
                submitReq.setAdjusterId(opterId);
                submitReq.setAdjusterName(opterName);
                List<ForbiddenPoolAdjustSubmitReq.AdjustItem> submitItems = new ArrayList<>();
                for (SecurityPoolExcelImportCheckItemDto ci : group) {
                    // 校验 Excel 导入权限
                    validateExcelImportPermission(opterId, ci.getTargetPoolId());
                    ForbiddenPoolAdjustSubmitReq.AdjustItem si = new ForbiddenPoolAdjustSubmitReq.AdjustItem();
                    si.setTargetPoolId(ci.getTargetPoolId());
                    si.setTargetPoolName(ci.getTargetPoolName());
                    si.setPoolType(ci.getPoolType());
                    si.setAdjustMode(ci.getAdjustMode());
                    // 清空标签在提交侧按手工主项落地（调库服务不识别 clear）
                    si.setItemTag(isClearTag(ci.getItemTag())
                            ? ItemType.MANUAL.getCode() : ci.getItemTag());
                    si.setAdjustGroupKey(ci.getAdjustGroupKey());
                    si.setFlowId(ci.getFlowId());
                    si.setFlowKey(ci.getFlowKey());
                    si.setFlowType(ci.getFlowType());
                    si.setAdjustmentNote(!isBlank(ci.getAdjustNote()) ? ci.getAdjustNote() : "首先清空目标池");
                    submitItems.add(si);
                }
                submitReq.setItems(submitItems);
                // 调用禁投池主体提交写调库日志
                ForbiddenPoolAdjustSubmitDto submitDto =
                        forbiddenPoolAdjustService.addCompanyAdjustLog(submitReq, Collections.emptyList());
                if (submitDto != null && submitDto.getLogIds() != null) {
                    allLogIds.addAll(submitDto.getLogIds());
                }
            } else {
                // 证券清空出库提交
                SecurityPoolAdjustSubmitReq submitReq = new SecurityPoolAdjustSubmitReq();
                submitReq.setSecurityCode(entry.getKey());
                submitReq.setSecurityShortName(primary.getSecurityShortName());
                submitReq.setSecurityType(primary.getSecurityType());
                submitReq.setAdjustType(ADJUST_TYPE_CLEAR);
                submitReq.setAdjustReason(batch.getReason());
                submitReq.setAdjustAdvice(batch.getAdvice());
                submitReq.setAdjusterId(opterId);
                submitReq.setAdjusterName(opterName);
                List<SecurityPoolAdjustSubmitReq.AdjustItem> submitItems = new ArrayList<>();
                for (SecurityPoolExcelImportCheckItemDto ci : group) {
                    // 校验 Excel 导入权限
                    validateExcelImportPermission(opterId, ci.getTargetPoolId());
                    SecurityPoolAdjustSubmitReq.AdjustItem si = new SecurityPoolAdjustSubmitReq.AdjustItem();
                    si.setSecurityCode(ci.getSecurityCode());
                    si.setSecurityShortName(ci.getSecurityShortName());
                    si.setSecurityType(ci.getSecurityType());
                    si.setTargetPoolId(ci.getTargetPoolId());
                    si.setTargetPoolName(ci.getTargetPoolName());
                    si.setPoolType(ci.getPoolType());
                    si.setAdjustMode(ci.getAdjustMode());
                    // 清空标签在提交侧按手工主项落地（调库服务不识别 clear）
                    si.setItemTag(isClearTag(ci.getItemTag())
                            ? ItemType.MANUAL.getCode() : ci.getItemTag());
                    si.setAdjustGroupKey(ci.getAdjustGroupKey());
                    si.setFlowId(ci.getFlowId());
                    si.setFlowKey(ci.getFlowKey());
                    si.setFlowType(ci.getFlowType());
                    si.setAdjustmentNote(!isBlank(ci.getAdjustNote()) ? ci.getAdjustNote() : "首先清空目标池");
                    submitItems.add(si);
                }
                submitReq.setItems(submitItems);
                // 调用证券调库提交写调库日志
                AdjustSubmitDto submitDto = securityPoolAdjustService.addAdjustLog(submitReq);
                if (submitDto != null && submitDto.getLogIds() != null) {
                    allLogIds.addAll(submitDto.getLogIds());
                }
            }
        }
        return allLogIds;
    }

    // ═══════════════════════════════════════════════════════════
    //  映射 / 流程注入（对齐批量 injectBatchFlowOption）
    // ═══════════════════════════════════════════════════════════

    /**
     * 映射证券校验结果，并对可调整手工项注入目标池「批量」流程（对齐批量 buildBatchCheckResult）。
     */
    private SecurityPoolExcelImportCheckItemDto mapSecurityCheckResult(
            SysImpTmpBo sourceItem, AdjustCheckDto.CheckResultItem ri,
            String excelCode, InvestmentPoolBo excelPool, String direction) {
        SecurityPoolExcelImportCheckItemDto dto = new SecurityPoolExcelImportCheckItemDto();
        if (sourceItem != null) {
            dto.setSourceItemId(sourceItem.getId());
            dto.setSourceRowNo(sourceItem.getRowNo());
        }
        String securityCode = !isBlank(ri.getSecurityCode()) ? ri.getSecurityCode() : excelCode;
        String sourceCode = !isBlank(ri.getSourceSecurityCode()) ? ri.getSourceSecurityCode() : excelCode;
        dto.setSecurityCode(securityCode);
        dto.setSecurityShortName(ri.getSecurityShortName());
        dto.setSecurityType(ri.getSecurityType());
        dto.setSourceSecurityCode(sourceCode);
        dto.setTargetPoolId(ri.getTargetPoolId());
        dto.setTargetPoolName(ri.getPoolName());
        dto.setPoolType(ri.getPoolType());
        dto.setAdjustMode(ri.getAdjustMode());
        dto.setItemTag(isBlank(ri.getItemTag()) ? ItemType.MANUAL.getCode() : ri.getItemTag());
        // 与批量一致：分组键以主券为前缀
        String gk = ri.getAdjustGroupKey();
        dto.setAdjustGroupKey(sourceCode + "_" + (gk == null ? "" : gk));
        dto.setCanAdjust(ri.isCanAdjust());
        dto.setFailReasons(ri.getFailReasons() == null ? new ArrayList<>() : new ArrayList<>(ri.getFailReasons()));
        // 解析调整类型中文
        dto.setAdjustType(resolveAdjustTypeLabel(dto.getItemTag()));
        if (dto.isCanAdjust()) {
            dto.setAdjustNote(dto.getAdjustType() + (dto.getAdjustMode() == null ? "" : dto.getAdjustMode()));
        }

        List<SecurityPoolExcelImportCheckItemDto.FlowOptionDto> flowOptions = new ArrayList<>();
        if (ri.getFlowOptions() != null) {
            for (AdjustCheckDto.FlowOption fo : ri.getFlowOptions()) {
                // 映射单条流程候选项
                flowOptions.add(toFlowOptionDto(fo));
            }
        }
        // 可调整手工项：注入批量流程为推荐（对齐批量 injectBatchFlowOption）
        if (dto.isCanAdjust() && isManualTag(dto.getItemTag())) {
            InvestmentPoolBo poolForFlow = excelPool;
            if (poolForFlow == null || !poolForFlow.getId().equals(dto.getTargetPoolId())) {
                poolForFlow = investmentPoolMapper.queryPoolById(dto.getTargetPoolId());
            }
            if (poolForFlow != null) {
                // 注入目标池批量流程候选
                injectBatchFlowOption(flowOptions, poolForFlow, direction);
            }
        }
        dto.setFlowOptions(flowOptions);
        // 默认选中推荐/首个可选流程
        applyDefaultSelectedFlow(dto);
        return dto;
    }

    /**
     * 映射主体校验结果（流程候选项已由禁投池 checkAdjust 产出）
     */
    private SecurityPoolExcelImportCheckItemDto mapCompanyCheckResult(
            SysImpTmpBo sourceItem, AdjustCheckDto.CheckResultItem ri, String excelCode) {
        SecurityPoolExcelImportCheckItemDto dto = new SecurityPoolExcelImportCheckItemDto();
        if (sourceItem != null) {
            dto.setSourceItemId(sourceItem.getId());
            dto.setSourceRowNo(sourceItem.getRowNo());
        }
        String securityCode = !isBlank(ri.getSecurityCode()) ? ri.getSecurityCode() : excelCode;
        String sourceCode = !isBlank(ri.getSourceSecurityCode()) ? ri.getSourceSecurityCode() : excelCode;
        dto.setSecurityCode(securityCode);
        dto.setSecurityShortName(ri.getSecurityShortName());
        dto.setSecurityType(ri.getSecurityType() != null ? ri.getSecurityType() : "company");
        dto.setSourceSecurityCode(sourceCode);
        dto.setTargetPoolId(ri.getTargetPoolId());
        dto.setTargetPoolName(ri.getPoolName());
        dto.setPoolType(ri.getPoolType());
        dto.setAdjustMode(ri.getAdjustMode());
        dto.setItemTag(isBlank(ri.getItemTag()) ? ItemType.MANUAL.getCode() : ri.getItemTag());
        String gk = ri.getAdjustGroupKey();
        dto.setAdjustGroupKey(sourceCode + "_" + (gk == null ? "" : gk));
        dto.setCanAdjust(ri.isCanAdjust());
        dto.setFailReasons(ri.getFailReasons() == null ? new ArrayList<>() : new ArrayList<>(ri.getFailReasons()));
        // 解析调整类型中文
        dto.setAdjustType(resolveAdjustTypeLabel(dto.getItemTag()));
        if (dto.isCanAdjust()) {
            dto.setAdjustNote(dto.getAdjustType() + (dto.getAdjustMode() == null ? "" : dto.getAdjustMode()));
        }

        List<SecurityPoolExcelImportCheckItemDto.FlowOptionDto> flowOptions = new ArrayList<>();
        if (ri.getFlowOptions() != null) {
            for (AdjustCheckDto.FlowOption fo : ri.getFlowOptions()) {
                // 映射单条流程候选项
                flowOptions.add(toFlowOptionDto(fo));
            }
        }
        // 可调整手工项且无流程候选时：注入标准入/出库流程
        if (dto.isCanAdjust() && isManualTag(dto.getItemTag()) && flowOptions.isEmpty()) {
            InvestmentPoolBo pool = investmentPoolMapper.queryPoolById(dto.getTargetPoolId());
            if (pool != null) {
                // 注入主体标准入/出库流程
                injectCompanyFlowOption(flowOptions, pool, AdjustMode.IN.getCode().equals(dto.getAdjustMode()));
            }
        }
        dto.setFlowOptions(flowOptions);
        // 默认选中推荐/首个可选流程
        applyDefaultSelectedFlow(dto);
        return dto;
    }

    /**
     * 在流程候选列表前插入目标池批量调入/调出流程（对齐批量 injectBatchFlowOption）。
     */
    private void injectBatchFlowOption(List<SecurityPoolExcelImportCheckItemDto.FlowOptionDto> flowOptions,
                                       InvestmentPoolBo pool, String direction) {
        boolean outbound = "out".equals(direction);
        Long flowId = outbound ? pool.getBatchOutFlowId() : pool.getBatchInFlowId();
        String flowKey = outbound ? pool.getBatchOutFlowKey() : pool.getBatchInFlowKey();
        String flowName = outbound ? pool.getBatchOutFlowName() : pool.getBatchInFlowName();
        String flowType = outbound ? FlowType.BATCH_OUTBOUND.getCode() : FlowType.BATCH_INBOUND.getCode();
        boolean direct = flowId == null && isBlank(flowKey);

        // 去重：去掉与批量流程同一 id/key 的其它候选
        if (flowOptions != null && !flowOptions.isEmpty()) {
            List<SecurityPoolExcelImportCheckItemDto.FlowOptionDto> deduped = new ArrayList<>();
            for (SecurityPoolExcelImportCheckItemDto.FlowOptionDto option : flowOptions) {
                if (option == null) {
                    continue;
                }
                boolean sameId = flowId != null && flowId.equals(option.getFlowId());
                boolean sameKey = !isBlank(flowKey) && flowKey.equals(option.getFlowKey());
                if (sameId || sameKey) {
                    continue;
                }
                option.setRecommended(false);
                deduped.add(option);
            }
            flowOptions.clear();
            flowOptions.addAll(deduped);
        }

        String displayName;
        if (direct) {
            displayName = "无需审批";
        } else if (!isBlank(flowName)) {
            displayName = flowName.trim();
        } else {
            displayName = outbound ? "批量调出流程" : "批量调入流程";
        }

        SecurityPoolExcelImportCheckItemDto.FlowOptionDto batchOption =
                new SecurityPoolExcelImportCheckItemDto.FlowOptionDto();
        batchOption.setFlowType(flowType);
        batchOption.setFlowName(displayName);
        batchOption.setFlowId(flowId);
        batchOption.setFlowKey(flowKey);
        batchOption.setRecommended(true);
        batchOption.setMatched(true);
        batchOption.setSelectable(true);
        // 生成批量流程 optionKey
        batchOption.setOptionKey(buildFlowOptionKey(batchOption));
        List<String> reasons = new ArrayList<>();
        if (direct) {
            reasons.add("目标池未配置批量审批流程，按无需审批处理");
        } else {
            reasons.add(outbound ? "目标池配置的批量调出流程" : "目标池配置的批量调入流程");
        }
        batchOption.setMatchReasons(reasons);
        batchOption.setUnmatchReasons(new ArrayList<>());
        if (flowOptions != null) {
            flowOptions.add(0, batchOption);
        }
    }

    /** 主体：注入标准入/出库流程（禁投池常用配置） */
    private void injectCompanyFlowOption(List<SecurityPoolExcelImportCheckItemDto.FlowOptionDto> flowOptions,
                                         InvestmentPoolBo pool, boolean inbound) {
        Long flowId = inbound ? pool.getInFlowId() : pool.getOutFlowId();
        String flowKey = inbound ? pool.getInFlowKey() : pool.getOutFlowKey();
        String flowName = inbound ? pool.getInFlowName() : pool.getOutFlowName();
        // 标准流程未配时回退批量流程
        if (flowId == null && isBlank(flowKey)) {
            flowId = inbound ? pool.getBatchInFlowId() : pool.getBatchOutFlowId();
            flowKey = inbound ? pool.getBatchInFlowKey() : pool.getBatchOutFlowKey();
            flowName = inbound ? pool.getBatchInFlowName() : pool.getBatchOutFlowName();
        }
        boolean direct = flowId == null && isBlank(flowKey);
        String flowType = inbound ? FlowType.NORMAL_INBOUND.getCode() : FlowType.NORMAL_OUTBOUND.getCode();
        String displayName;
        if (direct) {
            displayName = "无需审批";
        } else if (!isBlank(flowName)) {
            displayName = flowName.trim();
        } else {
            displayName = inbound ? "标准调入流程" : "标准调出流程";
        }

        SecurityPoolExcelImportCheckItemDto.FlowOptionDto opt =
                new SecurityPoolExcelImportCheckItemDto.FlowOptionDto();
        opt.setFlowId(flowId);
        opt.setFlowKey(flowKey);
        opt.setFlowName(displayName);
        opt.setFlowType(flowType);
        opt.setRecommended(true);
        opt.setMatched(true);
        opt.setSelectable(true);
        // 生成流程 optionKey
        opt.setOptionKey(buildFlowOptionKey(opt));
        List<String> reasons = new ArrayList<>();
        if (direct) {
            reasons.add("目标池未配置审批流程，按无需审批处理");
        } else {
            reasons.add(inbound ? "目标池配置的调入流程" : "目标池配置的调出流程");
        }
        opt.setMatchReasons(reasons);
        if (flowOptions != null) {
            flowOptions.add(0, opt);
        }
    }

    /** 将证券调库流程候选项映射为导入页流程 DTO */
    private SecurityPoolExcelImportCheckItemDto.FlowOptionDto toFlowOptionDto(AdjustCheckDto.FlowOption fo) {
        SecurityPoolExcelImportCheckItemDto.FlowOptionDto opt =
                new SecurityPoolExcelImportCheckItemDto.FlowOptionDto();
        opt.setFlowId(fo.getFlowId());
        opt.setFlowKey(fo.getFlowKey());
        // 流程名为空或像编码时，按流程类型回退中文展示名（禁止直接展示 flowKey）
        String name = fo.getFlowName();
        if (isBlank(name) || looksLikeCode(name)) {
            // 流程类型 code 转可读中文名称
            name = resolveFlowTypeDisplayName(fo.getFlowType());
        }
        opt.setFlowName(name);
        opt.setFlowType(fo.getFlowType());
        opt.setRecommended(fo.isRecommended());
        opt.setMatched(fo.isMatched());
        opt.setSelectable(fo.isSelectable());
        opt.setMatchReasons(fo.getMatchReasons() == null ? new ArrayList<>() : new ArrayList<>(fo.getMatchReasons()));
        opt.setUnmatchReasons(fo.getUnmatchReasons() == null ? new ArrayList<>() : new ArrayList<>(fo.getUnmatchReasons()));
        // 生成流程 optionKey
        opt.setOptionKey(buildFlowOptionKey(opt));
        return opt;
    }

    /** 为可调整项默认选中推荐流程（或首个可选流程） */
    private void applyDefaultSelectedFlow(SecurityPoolExcelImportCheckItemDto dto) {
        if (!dto.isCanAdjust() || dto.getFlowOptions() == null || dto.getFlowOptions().isEmpty()) {
            return;
        }
        SecurityPoolExcelImportCheckItemDto.FlowOptionDto selected = null;
        for (SecurityPoolExcelImportCheckItemDto.FlowOptionDto o : dto.getFlowOptions()) {
            if (o.isRecommended() && o.isSelectable()) {
                selected = o;
                break;
            }
        }
        if (selected == null) {
            for (SecurityPoolExcelImportCheckItemDto.FlowOptionDto o : dto.getFlowOptions()) {
                if (o.isSelectable()) {
                    selected = o;
                    break;
                }
            }
        }
        if (selected == null) {
            return;
        }
        dto.setSelectedFlowKey(selected.getOptionKey());
        dto.setFlowId(selected.getFlowId());
        dto.setFlowKey(selected.getFlowKey());
        dto.setFlowType(selected.getFlowType());
        dto.setFlowName(selected.getFlowName());
        dto.setDirectFlow(selected.getFlowId() == null && isBlank(selected.getFlowKey()));
    }

    // ═══════════════════════════════════════════════════════════
    //  工具
    // ═══════════════════════════════════════════════════════════

    /** 按父池名称 + 子池名称解析启用叶子池（带缓存） */
    private InvestmentPoolBo resolvePoolByNames(String parentName, String childName,
                                                Map<String, InvestmentPoolBo> cache) {
        String cacheKey = trimToEmpty(parentName) + "||" + trimToEmpty(childName);
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }
        // 查询启用叶子投资池
        InvestmentPoolBo pool = securityPoolExcelImportMapper.queryEnabledLeafPoolByParentAndChildName(
                isBlank(parentName) ? null : parentName, childName);
        cache.put(cacheKey, pool);
        return pool;
    }

    /** 将导入明细标记为校验失败并回写临时表 */
    private void markItemFail(SysImpTmpBo item, List<String> reasons, Date now) {
        item.setChkRslt("2");
        // 拼接失败原因
        item.setChkDscr(joinReasons(reasons));
        item.setUpdtTime(now);
        securityPoolExcelImportMapper.updateItemCheckResult(item);
    }

    /** 构建前置失败的手工校验结果项（不调用下游调库校验） */
    private SecurityPoolExcelImportCheckItemDto buildFailManualItem(
            SysImpTmpBo sourceItem, String code, String shortName, String securityType,
            InvestmentPoolBo pool, String adjustMode, List<String> reasons) {
        SecurityPoolExcelImportCheckItemDto dto = new SecurityPoolExcelImportCheckItemDto();
        if (sourceItem != null) {
            dto.setSourceItemId(sourceItem.getId());
            dto.setSourceRowNo(sourceItem.getRowNo());
        }
        dto.setSecurityCode(code);
        dto.setSecurityShortName(shortName);
        dto.setSecurityType(securityType);
        dto.setSourceSecurityCode(code);
        if (pool != null) {
            dto.setTargetPoolId(pool.getId());
            dto.setTargetPoolName(pool.getPoolName());
            dto.setPoolType(pool.getPoolType());
        }
        dto.setAdjustMode(adjustMode);
        dto.setItemTag(ItemType.MANUAL.getCode());
        dto.setAdjustType("手工调整");
        dto.setAdjustGroupKey(code + "_" + (pool != null ? pool.getId() : "0") + "_" + adjustMode);
        dto.setCanAdjust(false);
        dto.setFailReasons(reasons == null ? new ArrayList<>() : new ArrayList<>(reasons));
        return dto;
    }

    /** 从请求或批次快照中筛选可提交的校验结果项 */
    private List<SecurityPoolExcelImportCheckItemDto> filterSubmittableCheckItems(
            SecurityPoolExcelImportReq req, SysImpTmpBatchBo batch) {
        List<SecurityPoolExcelImportCheckItemDto> source;
        if (req.getCheckItems() != null && !req.getCheckItems().isEmpty()) {
            source = req.getCheckItems();
        } else {
            // 从批次快照读取校验结果
            source = parseCheckItems(batch.getResultJson());
        }
        List<SecurityPoolExcelImportCheckItemDto> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (SecurityPoolExcelImportCheckItemDto ci : source) {
            if (ci != null && ci.isCanAdjust()
                    && !isBlank(ci.getSecurityCode()) && ci.getTargetPoolId() != null) {
                result.add(ci);
            }
        }
        return result;
    }

    /** 同组中优先取手工项/清空项作为主项 */
    private SecurityPoolExcelImportCheckItemDto resolvePrimaryItem(
            List<SecurityPoolExcelImportCheckItemDto> items) {
        for (SecurityPoolExcelImportCheckItemDto item : items) {
            // 判断是否为需选流程的主项（手工 / 清空）
            if (isFlowSelectableTag(item.getItemTag())) {
                return item;
            }
        }
        return items.get(0);
    }

    /** 是否为手工调整项标签 */
    private boolean isManualTag(String itemTag) {
        return itemTag == null || itemTag.isEmpty() || ItemType.MANUAL.getCode().equals(itemTag);
    }

    /** 是否为清空出库项标签 */
    private boolean isClearTag(String itemTag) {
        return ItemType.CLEAR.getCode().equals(itemTag);
    }

    /** 是否需选择审批流程的主项（手工 / 清空） */
    private boolean isFlowSelectableTag(String itemTag) {
        // 判断是否为手工项或清空出库项
        return isManualTag(itemTag) || isClearTag(itemTag);
    }

    /** 将 itemTag 转为前端展示的调整类型中文 */
    private String resolveAdjustTypeLabel(String itemTag) {
        if (ItemType.LINKAGE.getCode().equals(itemTag)) {
            return "联动调整";
        }
        if (ItemType.MUTEX.getCode().equals(itemTag)) {
            return "互斥调整";
        }
        if (ItemType.RELATED.getCode().equals(itemTag)) {
            return "关联调整";
        }
        if (ItemType.CLEAR.getCode().equals(itemTag)) {
            return "清空出库";
        }
        return "手工调整";
    }

    /** 从 option_json 解析是否首先清空目标池 */
    private boolean parseClearTarget(String optionJson) {
        if (isBlank(optionJson)) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(optionJson);
            return node != null && node.has("clearTarget") && node.get("clearTarget").asBoolean(false);
        } catch (Exception e) {
            return optionJson.contains("\"clearTarget\":true");
        }
    }

    /** 解析明细中的目标池 ID */
    private Long parsePoolId(String text) {
        if (isBlank(text)) {
            return null;
        }
        try {
            return Long.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 构建流程候选项唯一键（flowType_flowId_flowKey） */
    private String buildFlowOptionKey(SecurityPoolExcelImportCheckItemDto.FlowOptionDto opt) {
        return (opt.getFlowType() == null ? "" : opt.getFlowType()) + "_"
                + (opt.getFlowId() == null ? "null" : opt.getFlowId()) + "_"
                + (opt.getFlowKey() == null ? "" : opt.getFlowKey());
    }

    /** 流程类型 code 转可读中文名称 */
    private String resolveFlowTypeDisplayName(String flowType) {
        if (flowType == null) {
            return "审批流程";
        }
        if (FlowType.BATCH_INBOUND.getCode().equals(flowType)) {
            return "批量调入流程";
        }
        if (FlowType.BATCH_OUTBOUND.getCode().equals(flowType)) {
            return "批量调出流程";
        }
        if (FlowType.NORMAL_INBOUND.getCode().equals(flowType)) {
            return "标准调入流程";
        }
        if (FlowType.NORMAL_OUTBOUND.getCode().equals(flowType)) {
            return "标准调出流程";
        }
        if (FlowType.SIMPLE_INBOUND.getCode().equals(flowType)) {
            return "简易调入流程";
        }
        if (FlowType.WHITELIST_INBOUND.getCode().equals(flowType)) {
            return "白名单调入流程";
        }
        if (FlowType.SPECIAL_INBOUND.getCode().equals(flowType)) {
            return "特殊调入流程";
        }
        return "审批流程";
    }

    /** 判断文本是否像流程编码（非中文展示名） */
    private boolean looksLikeCode(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        return text.matches("^[a-zA-Z0-9_.:\\-]+$") && !text.matches(".*[\\u4e00-\\u9fa5].*");
    }

    /** 校验当前用户是否具备目标池 excel_importable 权限 */
    private void validateExcelImportPermission(String currentUserId, Long poolId) {
        if (ADMIN_USER_ID.equals(currentUserId)) {
            return;
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
        List<Long> roleIds = investmentPoolMapper.queryUserRoleIdList(userId);
        Set<Long> roleIdSet = new HashSet<>(roleIds == null ? new ArrayList<>() : roleIds);
        List<PoolPermissionBo> permissions =
                investmentPoolMapper.queryPermissionListByType(PermissionType.EXCEL_IMPORTABLE.getCode());
        for (PoolPermissionBo permission : permissions) {
            if (permission.getPoolId() == null || !permission.getPoolId().equals(poolId)
                    || permission.getHandlerId() == null) {
                continue;
            }
            if (HandlerType.USER.getCode().equals(permission.getHandlerType())
                    && permission.getHandlerId().equals(userId)) {
                return;
            }
            if (HandlerType.ROLE.getCode().equals(permission.getHandlerType())
                    && roleIdSet.contains(permission.getHandlerId())) {
                return;
            }
        }
        throw new BizException("当前用户无权对该投资池进行 Excel 导入");
    }

    /**
     * 将 Excel 行按导入类型写入通用字段槽
     * <p>表头与模板一致：证券=父池/子池/证券名称/证券代码；主体=父池/子池/主体名称/主体代码。</p>
     */
    private void fillFromExcelRow(SysImpTmpBo item, Map<String, String> row, String importType) {
        // 按表头读取父池名称
        item.setFld003(cell(row, "父池名称"));
        // 按表头读取子池名称
        item.setFld004(cell(row, "子池名称"));
        if (IMPORT_TYPE_COMPANY.equals(importType)) {
            // 按表头读取主体代码/名称
            item.setFld001(cell(row, "主体代码"));
            item.setFld002(cell(row, "主体名称"));
        } else {
            // 按表头读取证券代码/名称
            item.setFld001(cell(row, "证券代码"));
            item.setFld002(cell(row, "证券名称"));
        }
    }

    /** 按表头读取单元格文本（兼容表头首尾空格） */
    private String cell(Map<String, String> row, String header) {
        if (row == null) {
            return "";
        }
        if (row.containsKey(header)) {
            return trimToEmpty(row.get(header));
        }
        for (Map.Entry<String, String> e : row.entrySet()) {
            if (e.getKey() != null && header.equals(e.getKey().trim())) {
                return trimToEmpty(e.getValue());
            }
        }
        return "";
    }

    /** 校验上传请求与文件基础参数 */
    private void validateUploadReq(SecurityPoolExcelImportReq req, MultipartFile file) {
        if (req == null) {
            throw new BizException("请求参数不能为空");
        }
        // 规范化导入类型
        normalizeImportType(req.getImportType());
        // 规范化调整方向
        normalizeDirection(req.getDirection());
        if (req.getCurrentUserId() == null || req.getCurrentUserId().trim().isEmpty()) {
            throw new BizException("当前用户 ID 不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BizException("文件大小不能超过 5MB");
        }
    }

    /** 规范化导入类型：security / company */
    private String normalizeImportType(String importType) {
        if (importType == null || importType.trim().isEmpty()) {
            return IMPORT_TYPE_SECURITY;
        }
        String t = importType.trim().toLowerCase();
        if (!IMPORT_TYPE_SECURITY.equals(t) && !IMPORT_TYPE_COMPANY.equals(t)) {
            throw new BizException("导入类型必须为 security 或 company");
        }
        return t;
    }

    /** 规范化调整方向：in / out */
    private String normalizeDirection(String direction) {
        if (!"in".equals(direction) && !"out".equals(direction)) {
            throw new BizException("调整方向必须为 in 或 out");
        }
        return direction;
    }

    /** 按批次号加载导入批次，不存在则抛业务异常 */
    private SysImpTmpBatchBo requireBatch(String impId) {
        if (impId == null || impId.trim().isEmpty()) {
            throw new BizException("导入批次号不能为空");
        }
        SysImpTmpBatchBo batch = securityPoolExcelImportMapper.queryByImpId(impId.trim());
        if (batch == null) {
            throw new BizException("导入批次不存在或已取消");
        }
        return batch;
    }

    /** 解析经办人名称（优先请求中的姓名） */
    private String resolveUserName(SecurityPoolExcelImportReq req) {
        if (req.getCurrentUserName() != null && !req.getCurrentUserName().trim().isEmpty()) {
            return req.getCurrentUserName().trim();
        }
        return req.getCurrentUserId().trim();
    }

    /** 生成导入批次号 IMP+时间戳+序号 */
    private String nextImpId(Date now) {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(now);
        int seq = IMP_SEQ.incrementAndGet() % 10000;
        return "IMP" + ts + String.format("%04d", seq);
    }

    /** 生成导入明细号 IMPD+时间戳+序号 */
    private String nextDetlId(Date now) {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(now);
        int seq = DETL_SEQ.incrementAndGet() % 100000;
        return "IMPD" + ts + String.format("%05d", seq);
    }

    /** 批次主表转前端任务 DTO（含 checkItems 解析） */
    private SecurityPoolExcelImportDto toTaskDto(SysImpTmpBatchBo batch) {
        SecurityPoolExcelImportDto dto = new SecurityPoolExcelImportDto();
        dto.setImpId(batch.getImpId());
        dto.setBizType(batch.getBizType());
        dto.setFileName(batch.getFileName());
        dto.setTargetId(batch.getTargetId());
        dto.setTargetName(batch.getTargetName());
        dto.setTargetType(batch.getTargetType());
        dto.setBizMode(batch.getBizMode());
        dto.setOptionJson(batch.getOptionJson());
        dto.setReason(batch.getReason());
        dto.setAdvice(batch.getAdvice());
        dto.setTotalCount(batch.getTotalCount());
        dto.setPassCount(batch.getPassCount() == null ? 0 : batch.getPassCount());
        dto.setFailCount(batch.getFailCount() == null ? 0 : batch.getFailCount());
        int pending = securityPoolExcelImportMapper.countByChkRslt(batch.getImpId(), "0");
        dto.setPendingCount(pending);
        dto.setChkRslt(batch.getChkRslt());
        dto.setChkDscr(batch.getChkDscr());
        dto.setSaveRslt(batch.getSaveRslt());
        dto.setSaveDscr(batch.getSaveDscr());
        dto.setResultJson(batch.getResultJson());
        dto.setImpTime(batch.getImpTime());
        // 回填联动互斥选项
        dto.setAllowLinkMutex(parseAllowLinkMutex(batch.getOptionJson()));
        // 回填导入类型
        dto.setImportType(parseImportType(batch.getOptionJson(), batch.getBizType()));
        // 解析批次校验快照
        List<SecurityPoolExcelImportCheckItemDto> checkItems = parseCheckItems(batch.getResultJson());
        dto.setCheckItems(checkItems == null ? new ArrayList<>() : checkItems);
        int checkPass = 0;
        int checkFail = 0;
        if (checkItems != null) {
            for (SecurityPoolExcelImportCheckItemDto ci : checkItems) {
                if (ci.isCanAdjust()) {
                    checkPass++;
                } else {
                    checkFail++;
                }
            }
        }
        dto.setCheckPassCount(checkPass);
        dto.setCheckFailCount(checkFail);
        dto.setCheckDone(checkItems != null && !checkItems.isEmpty());
        return dto;
    }

    /** 从 option_json 解析是否允许联动与互斥 */
    private boolean parseAllowLinkMutex(String optionJson) {
        if (isBlank(optionJson)) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(optionJson);
            return node != null && node.has("allowLinkMutex") && node.get("allowLinkMutex").asBoolean(false);
        } catch (Exception e) {
            return optionJson.contains("\"allowLinkMutex\":true");
        }
    }

    /** 将调库校验结果序列化写入 result_json */
    private String buildCheckResultJson(List<SecurityPoolExcelImportCheckItemDto> checkItems,
                                        int checkPass, int checkFail, boolean allowLinkMutex,
                                        String importType) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("checkDone", true);
            root.put("allowLinkMutex", allowLinkMutex);
            root.put("importType", importType == null ? IMPORT_TYPE_SECURITY : importType);
            root.put("checkPassCount", checkPass);
            root.put("checkFailCount", checkFail);
            root.set("checkItems", objectMapper.valueToTree(checkItems));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"checkDone\":true,\"checkItems\":[]}";
        }
    }

    /** 从 result_json 反序列化 checkItems */
    private List<SecurityPoolExcelImportCheckItemDto> parseCheckItems(String resultJson) {
        if (isBlank(resultJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            JsonNode arr = root.get("checkItems");
            if (arr == null || !arr.isArray()) {
                return null;
            }
            return objectMapper.convertValue(arr,
                    new TypeReference<List<SecurityPoolExcelImportCheckItemDto>>() {
                    });
        } catch (Exception e) {
            return null;
        }
    }

    /** 临时表明细转前端明细 DTO */
    private SecurityPoolExcelImportItemDto toItemDto(SysImpTmpBo bo) {
        SecurityPoolExcelImportItemDto dto = new SecurityPoolExcelImportItemDto();
        dto.setId(bo.getId());
        dto.setImpDetlId(bo.getImpDetlId());
        dto.setRowNo(bo.getRowNo());
        dto.setSecurityCode(bo.getFld001());
        dto.setSecurityName(bo.getFld002());
        dto.setParentPoolName(bo.getFld003());
        dto.setChildPoolName(bo.getFld004());
        if (!isBlank(bo.getFld009())) {
            try {
                dto.setResolvedPoolId(Long.valueOf(bo.getFld009().trim()));
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        dto.setChkRslt(bo.getChkRslt());
        dto.setChkDscr(bo.getChkDscr());
        dto.setSaveRslt(bo.getSaveRslt());
        dto.setSaveDscr(bo.getSaveDscr());
        dto.setRefId(bo.getRefId());
        return dto;
    }

    /** 组装导入选项 JSON（clearTarget/allowLinkMutex/importType） */
    private String buildOptionJson(Boolean clearTarget, Boolean allowLinkMutex, String importType) {
        boolean clear = Boolean.TRUE.equals(clearTarget);
        boolean link = Boolean.TRUE.equals(allowLinkMutex);
        String type = importType == null ? IMPORT_TYPE_SECURITY : importType;
        return "{\"clearTarget\":" + clear + ",\"allowLinkMutex\":" + link
                + ",\"importType\":\"" + type + "\"}";
    }

    /** 从 option_json 或 bizType 解析导入类型 */
    private String parseImportType(String optionJson, String bizType) {
        if (!isBlank(optionJson)) {
            try {
                JsonNode node = objectMapper.readTree(optionJson);
                if (node != null && node.has("importType") && !node.get("importType").isNull()) {
                    // 规范化导入类型
                    return normalizeImportType(node.get("importType").asText());
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (BIZ_TYPE_COMPANY.equals(bizType)) {
            return IMPORT_TYPE_COMPANY;
        }
        return IMPORT_TYPE_SECURITY;
    }

    /** 解析 Excel 物理行号 */
    private int parseRowNo(String text) {
        try {
            return Integer.parseInt(text);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 将失败原因列表拼接为校验说明（最长 500） */
    private String joinReasons(List<String> reasons) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < reasons.size(); i++) {
            if (i > 0) {
                sb.append("；");
            }
            sb.append(reasons.get(i));
        }
        String text = sb.toString();
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    /** 空白串转为 null */
    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** null 安全 trim，空白返回空串 */
    private String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    /** 判断字符串是否为 null 或空白 */
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
