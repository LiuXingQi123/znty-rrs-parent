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
import com.znty.rrs.common.enums.MarketCode;
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.common.enums.PoolType;
import com.znty.rrs.common.util.ExcelImportHelper;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckReq;
import com.znty.rrs.entity.crmwpooladjust.AdjustSubmitDto;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustSubmitReq;
import com.znty.rrs.entity.crmwpooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.crmwpoolexcelimport.CrmwPoolExcelImportCheckItemDto;
import com.znty.rrs.entity.crmwpoolexcelimport.CrmwPoolExcelImportDto;
import com.znty.rrs.entity.crmwpoolexcelimport.CrmwPoolExcelImportItemDto;
import com.znty.rrs.entity.crmwpoolexcelimport.CrmwPoolExcelImportPoolDto;
import com.znty.rrs.entity.crmwpoolexcelimport.CrmwPoolExcelImportReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CrmwPoolExcelImportMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
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
 * CRMW 池 Excel 导入服务
 * <p>
 * Excel 层负责模板解析、临时表与 excel_importable 权限；
 * 调库可行性与落库委托 {@link CrmwPoolAdjustService#checkCrmwAdjust} /
 * {@link CrmwPoolAdjustService#submitAdjustLog}，与 CRMW 单笔 / 批量同构。
 * </p>
 */
@Service
public class CrmwPoolExcelImportService {

    /** 导入批次业务类型 */
    private static final String BIZ_TYPE = "crmw_pool_excel";
    /** 模板编码 */
    private static final String TEMPLATE_CODE = "crmw_pool_import";
    /** 调库记录调整类型 */
    private static final String ADJUST_TYPE_EXCEL = "Excel导入";
    /** CRMW 凭证类型 */
    private static final String CRMW_SECURITY_TYPE = "crmw";
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

    /** CRMW 池 Excel 导入数据访问组件 */
    @Resource
    private CrmwPoolExcelImportMapper crmwPoolExcelImportMapper;
    /** 投资池数据访问组件（权限） */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;
    /** CRMW 单笔调库校验/提交 */
    @Resource
    private CrmwPoolAdjustService crmwPoolAdjustService;
    /** 附件服务（解析前端原始文件名） */
    @Resource
    private SysAttachmentService sysAttachmentService;

    /**
     * 查询当前用户可 Excel 导入的启用叶子 CRMW 池
     */
    public List<CrmwPoolExcelImportPoolDto> queryPoolList(CrmwPoolExcelImportReq req) {
        List<CrmwPoolExcelImportPoolDto> all = crmwPoolExcelImportMapper.queryEnabledLeafCrmwPoolList();
        if (all == null || all.isEmpty()) {
            return new ArrayList<>();
        }
        String userId = req == null ? null : trimToNull(req.getCurrentUserId());
        if (ADMIN_USER_ID.equals(userId)) {
            return all;
        }
        List<CrmwPoolExcelImportPoolDto> allowed = new ArrayList<>();
        for (CrmwPoolExcelImportPoolDto pool : all) {
            if (pool == null || pool.getId() == null) {
                continue;
            }
            try {
                // 校验当前用户对该 CRMW 池是否具备 Excel 导入权限
                validateExcelImportPermission(userId, pool.getId());
                allowed.add(pool);
            } catch (BizException ignored) {
                // 无权限的池不进入下拉
            }
        }
        return allowed;
    }

    /**
     * 上传 Excel 并写入导入临时表
     */
    @Transactional(rollbackFor = Exception.class)
    public CrmwPoolExcelImportDto uploadExcel(CrmwPoolExcelImportReq req, MultipartFile file) {
        return uploadExcel(req, file, null);
    }

    /**
     * 上传 Excel 并写入导入临时表（可带前端原始文件名 JSON 数组）
     */
    @Transactional(rollbackFor = Exception.class)
    public CrmwPoolExcelImportDto uploadExcel(CrmwPoolExcelImportReq req, MultipartFile file,
                                              String originalFileNameListJson) {
        // 校验上传请求、文件与目标池
        validateUploadReq(req, file);
        InvestmentPoolBo pool = requireTargetPool(req.getTargetPoolId());
        // 校验 Excel 导入权限
        validateExcelImportPermission(req.getCurrentUserId().trim(), pool.getId());

        List<String> originalFileNameList =
                sysAttachmentService.parseOriginalFileNameListJson(originalFileNameListJson);
        String uploadFileName = sysAttachmentService.resolveUploadOriginalFileName(file, originalFileNameList);

        // 解析 Excel 首 sheet
        List<Map<String, String>> excelRows = ExcelImportHelper.parseFirstSheet(file, MAX_ROWS);
        Date now = new Date();
        String impId = nextImpId(now);

        SysImpTmpBo batch = new SysImpTmpBo();
        batch.setImpId(impId);
        batch.setBizType(BIZ_TYPE);
        batch.setTemplateCode(TEMPLATE_CODE);
        batch.setFileName(uploadFileName);
        batch.setFileSize(file.getSize());
        batch.setFld001(normalizeDirection(req.getDirection()));
        batch.setFld002(trimToNull(req.getAdjustReason()));
        batch.setFld003(trimToNull(req.getAdjustAdvice()));
        batch.setFld004(String.valueOf(pool.getId()));
        batch.setFld005(pool.getPoolName());
        batch.setFld006(PoolType.CRMW.getCode());
        batch.setOptionJson(buildOptionJson(pool));
        batch.setTotalCount(0);
        batch.setPassCount(0);
        batch.setFailCount(0);
        batch.setChkRslt("0");
        batch.setSaveRslt("0");
        batch.setImpTime(now);
        batch.setOpterId(req.getCurrentUserId().trim());
        batch.setOpterName(resolveUserName(req));
        batch.setIsDeleted(0);
        batch.setCrteTime(now);
        batch.setUpdtTime(now);

        Map<String, SecurityInfoDetailDto> infoCache = new HashMap<>();
        List<SysImpTmpDetlBo> items = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (Map<String, String> row : excelRows) {
            SysImpTmpDetlBo item = new SysImpTmpDetlBo();
            item.setImpDetlId(nextDetlId(now));
            item.setImpId(impId);
            item.setRowNo(parseRowNo(row.get("__rowNo")));
            item.setChkRslt("0");
            item.setSaveRslt("0");
            item.setImpTime(now);
            item.setOpterId(batch.getOpterId());
            item.setIsDeleted(0);
            item.setCrteTime(now);
            item.setUpdtTime(now);
            item.setFld001(cell(row, "CRMW代码"));
            item.setFld004(cell(row, "证券代码"));
            item.setFld009(String.valueOf(pool.getId()));
            item.setFld010(PoolType.CRMW.getCode());

            List<String> preFail = new ArrayList<>();
            String crmwScode = trimToEmpty(item.getFld001());
            String securityCode = trimToEmpty(item.getFld004());
            if (crmwScode.isEmpty()) {
                preFail.add("CRMW代码为空");
            }
            if (securityCode.isEmpty()) {
                preFail.add("证券代码为空");
            }
            String uniq = crmwScode + "|" + securityCode;
            if (!crmwScode.isEmpty() && !securityCode.isEmpty() && !seenKeys.add(uniq)) {
                preFail.add("Excel 内相同 CRMW 代码与证券代码重复");
            }
            // 回填凭证与标的主数据展示字段
            fillSecurityDisplay(item, infoCache);
            if (preFail.isEmpty()) {
                preFail.addAll(validateComboMasterData(item, infoCache));
            }
            if (!preFail.isEmpty()) {
                item.setChkRslt("2");
                item.setChkDscr(joinReasons(preFail));
            }
            items.add(item);
        }
        batch.setTotalCount(items.size());
        int preFailCount = 0;
        for (SysImpTmpDetlBo item : items) {
            if ("2".equals(item.getChkRslt())) {
                preFailCount++;
            }
        }
        batch.setFailCount(preFailCount);
        batch.setPassCount(0);

        crmwPoolExcelImportMapper.insertBatch(batch);
        int batchSize = 200;
        for (int i = 0; i < items.size(); i += batchSize) {
            int end = Math.min(i + batchSize, items.size());
            crmwPoolExcelImportMapper.insertItemList(items.subList(i, end));
        }

        CrmwPoolExcelImportReq pageReq = new CrmwPoolExcelImportReq();
        pageReq.setImpId(impId);
        pageReq.setPageIndex(1);
        pageReq.setPageSize(20);
        return queryTask(pageReq);
    }

    /**
     * 查询导入批次主表信息（含校验结果快照）
     */
    public CrmwPoolExcelImportDto queryTask(CrmwPoolExcelImportReq req) {
        SysImpTmpBo batch = requireBatch(req == null ? null : req.getImpId());
        CrmwPoolExcelImportDto dto = toTaskDto(batch);
        if (req != null && req.getPageIndex() > 0) {
            dto.setItems(queryItemPage(req));
        }
        return dto;
    }

    /**
     * 分页查询导入明细
     */
    public PageResult<CrmwPoolExcelImportItemDto> queryItemPage(CrmwPoolExcelImportReq req) {
        if (req == null || isBlank(req.getImpId())) {
            throw new BizException("导入批次号不能为空");
        }
        requireBatch(req.getImpId());
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SysImpTmpDetlBo> list = crmwPoolExcelImportMapper.queryItemList(
                req.getImpId().trim(), trimToNull(req.getChkRslt()), trimToNull(req.getKeyword()));
        PageInfo<SysImpTmpDetlBo> pageInfo = new PageInfo<>(list);
        List<CrmwPoolExcelImportItemDto> records = new ArrayList<>();
        for (SysImpTmpDetlBo bo : list) {
            records.add(toItemDto(bo));
        }
        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 取消导入批次（逻辑删除主表与明细）
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelImport(CrmwPoolExcelImportReq req) {
        if (req == null || isBlank(req.getImpId())) {
            throw new BizException("导入批次号不能为空");
        }
        SysImpTmpBo batch = requireBatch(req.getImpId());
        if ("1".equals(batch.getSaveRslt())) {
            throw new BizException("该批次已提交，不能取消");
        }
        crmwPoolExcelImportMapper.deleteItemsByImpIdSoft(batch.getImpId());
        crmwPoolExcelImportMapper.deleteBatchSoft(batch.getImpId());
    }

    /**
     * 对批次明细执行 CRMW 单笔调库校验
     */
    @Transactional(rollbackFor = Exception.class)
    public CrmwPoolExcelImportDto checkImport(CrmwPoolExcelImportReq req) {
        if (req == null || isBlank(req.getImpId())) {
            throw new BizException("导入批次号不能为空");
        }
        SysImpTmpBo batch = requireBatch(req.getImpId());
        if ("1".equals(batch.getSaveRslt())) {
            throw new BizException("该批次已提交，不能再次校验");
        }
        String opterId = !isBlank(req.getCurrentUserId()) ? req.getCurrentUserId().trim() : batch.getOpterId();
        List<SysImpTmpDetlBo> items = crmwPoolExcelImportMapper.queryAllByImpId(batch.getImpId());
        if (items.isEmpty()) {
            throw new BizException("导入明细为空");
        }
        InvestmentPoolBo pool = requireTargetPool(parsePoolId(batch.getFld004()));
        // 校验 Excel 导入权限
        validateExcelImportPermission(opterId, pool.getId());

        boolean inbound = "in".equals(batch.getFld001());
        String adjustMode = inbound ? AdjustMode.IN.getCode() : AdjustMode.OUT.getCode();
        Date now = new Date();
        Map<String, SecurityInfoDetailDto> infoCache = new HashMap<>();
        List<CrmwPoolExcelImportCheckItemDto> checkItems = new ArrayList<>();

        for (SysImpTmpDetlBo item : items) {
            List<String> preReasons = new ArrayList<>();
            String crmwScode = trimToEmpty(item.getFld001());
            String securityCode = trimToEmpty(item.getFld004());
            if (crmwScode.isEmpty()) {
                preReasons.add("CRMW代码为空");
            }
            if (securityCode.isEmpty()) {
                preReasons.add("证券代码为空");
            }
            // 回填凭证与标的主数据展示字段
            fillSecurityDisplay(item, infoCache);
            if (preReasons.isEmpty()) {
                preReasons.addAll(validateComboMasterData(item, infoCache));
            }
            item.setFld009(String.valueOf(pool.getId()));
            item.setFld010(PoolType.CRMW.getCode());

            if (!preReasons.isEmpty()) {
                markItemFail(item, preReasons, now);
                checkItems.add(buildFailManualItem(item, pool, adjustMode, preReasons));
                continue;
            }

            AdjustCheckReq checkReq = new AdjustCheckReq();
            checkReq.setSecurityCode(securityCode);
            checkReq.setSecurityShortName(resolveShortName(item.getFld005(), securityCode, infoCache));
            checkReq.setSecurityType(trimToNull(item.getFld006()));
            checkReq.setCrmwName(trimToNull(item.getFld002()));
            checkReq.setCrmwScode(crmwScode);
            checkReq.setCrmwStype(resolveCrmwStype(item.getFld008()));
            AdjustCheckReq.CheckItem checkItem = new AdjustCheckReq.CheckItem();
            checkItem.setTargetPoolId(pool.getId());
            checkItem.setTargetPoolName(pool.getPoolName());
            checkItem.setPoolType(PoolType.CRMW.getCode());
            checkItem.setAdjustMode(adjustMode);
            checkReq.setItems(Collections.singletonList(checkItem));

            AdjustCheckDto checkDto;
            try {
                checkDto = crmwPoolAdjustService.checkCrmwAdjust(checkReq);
            } catch (BizException e) {
                List<String> reasons = Collections.singletonList(e.getMessage());
                markItemFail(item, reasons, now);
                checkItems.add(buildFailManualItem(item, pool, adjustMode, reasons));
                continue;
            }

            List<AdjustCheckDto.CheckResultItem> resultItems =
                    checkDto.getItems() == null ? new ArrayList<>() : checkDto.getItems();
            boolean manualPass = false;
            for (AdjustCheckDto.CheckResultItem ri : resultItems) {
                CrmwPoolExcelImportCheckItemDto mapped = mapCheckResult(item, ri, pool, infoCache);
                if (isManualTag(mapped.getItemTag()) && mapped.isCanAdjust()) {
                    manualPass = true;
                }
                checkItems.add(mapped);
            }
            if (manualPass) {
                item.setChkRslt("1");
                item.setChkDscr("校验通过");
                item.setUpdtTime(now);
                crmwPoolExcelImportMapper.updateItemCheckResult(item);
            } else {
                List<String> failReasons = new ArrayList<>();
                for (CrmwPoolExcelImportCheckItemDto ci : checkItems) {
                    if (item.getId() != null && item.getId().equals(ci.getSourceItemId())
                            && isManualTag(ci.getItemTag()) && !ci.isCanAdjust()
                            && ci.getFailReasons() != null) {
                        failReasons.addAll(ci.getFailReasons());
                    }
                }
                if (failReasons.isEmpty()) {
                    failReasons.add("校验未通过");
                }
                markItemFail(item, failReasons, now);
            }
        }

        int pass = 0;
        int fail = 0;
        for (SysImpTmpDetlBo item : items) {
            if ("1".equals(item.getChkRslt())) {
                pass++;
            } else if ("2".equals(item.getChkRslt())) {
                fail++;
            }
        }
        int checkPass = 0;
        int checkFail = 0;
        for (CrmwPoolExcelImportCheckItemDto ci : checkItems) {
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
        batch.setResultJson(buildCheckResultJson(checkItems, checkPass, checkFail));
        batch.setUpdtTime(now);
        crmwPoolExcelImportMapper.updateBatchCheckResult(batch);

        CrmwPoolExcelImportReq pageReq = new CrmwPoolExcelImportReq();
        pageReq.setImpId(batch.getImpId());
        pageReq.setPageIndex(1);
        pageReq.setPageSize(20);
        return queryTask(pageReq);
    }

    /**
     * 按校验结果提交，逐组合委托 CRMW 单笔 submitAdjustLog
     */
    @Transactional(rollbackFor = Exception.class)
    public CrmwPoolExcelImportDto submitImport(CrmwPoolExcelImportReq req) {
        if (req == null || isBlank(req.getImpId())) {
            throw new BizException("导入批次号不能为空");
        }
        SysImpTmpBo batch = requireBatch(req.getImpId());
        if ("1".equals(batch.getSaveRslt())) {
            throw new BizException("该批次已提交，请勿重复提交");
        }
        List<CrmwPoolExcelImportCheckItemDto> checkItems = filterSubmittableCheckItems(req, batch);
        if (checkItems.isEmpty()) {
            throw new BizException("没有可提交的校验结果，请先校验");
        }
        String opterId = !isBlank(req.getCurrentUserId()) ? req.getCurrentUserId().trim() : batch.getOpterId();
        String opterName = !isBlank(req.getCurrentUserName()) ? req.getCurrentUserName().trim() : batch.getOpterName();
        if (isBlank(opterId)) {
            throw new BizException("经办人 ID 不能为空");
        }
        if (isBlank(opterName)) {
            opterName = opterId;
        }
        if (!isBlank(req.getAdjustReason())) {
            batch.setFld002(req.getAdjustReason().trim());
        }
        if (!isBlank(req.getAdjustAdvice())) {
            batch.setFld003(req.getAdjustAdvice().trim());
        }
        InvestmentPoolBo pool = requireTargetPool(parsePoolId(batch.getFld004()));
        // 校验 Excel 导入权限
        validateExcelImportPermission(opterId, pool.getId());

        // 存在直通落池项时，提交前统一锁池并复核整批状态
        if (needsWholeBatchDirectRecheck(checkItems)) {
            crmwPoolAdjustService.recheckBeforeFinalApproval(buildDirectRecheckLogList(checkItems));
        }

        Map<String, List<CrmwPoolExcelImportCheckItemDto>> groupMap = new LinkedHashMap<>();
        for (CrmwPoolExcelImportCheckItemDto ci : checkItems) {
            String groupKey = !isBlank(ci.getSourceSecurityCode())
                    ? ci.getSourceSecurityCode()
                    : buildCombinationKey(ci.getCrmwScode(), ci.getSecurityCode());
            List<CrmwPoolExcelImportCheckItemDto> list = groupMap.get(groupKey);
            if (list == null) {
                list = new ArrayList<>();
                groupMap.put(groupKey, list);
            }
            list.add(ci);
        }

        SysAttachmentService.SubmissionFiles submissionFiles =
                sysAttachmentService.createSubmissionFiles(Collections.<MultipartFile>emptyList(), opterId);
        CrmwPoolAdjustService.BatchNoContext batchNoContext = new CrmwPoolAdjustService.BatchNoContext();
        List<Long> logIds = new ArrayList<>();
        for (List<CrmwPoolExcelImportCheckItemDto> group : groupMap.values()) {
            CrmwPoolAdjustSubmitReq submitReq = buildSubmitReq(group, batch, opterId, opterName, pool);
            AdjustSubmitDto submitDto = crmwPoolAdjustService.submitAdjustLog(
                    submitReq, submissionFiles, batchNoContext);
            if (submitDto != null && submitDto.getLogIds() != null) {
                logIds.addAll(submitDto.getLogIds());
            }
        }

        Date now = new Date();
        Map<Long, SysImpTmpDetlBo> sourceMap = new HashMap<>();
        for (SysImpTmpDetlBo bo : crmwPoolExcelImportMapper.queryAllByImpId(batch.getImpId())) {
            sourceMap.put(bo.getId(), bo);
        }
        for (CrmwPoolExcelImportCheckItemDto ci : checkItems) {
            if (!isManualTag(ci.getItemTag()) || ci.getSourceItemId() == null) {
                continue;
            }
            SysImpTmpDetlBo source = sourceMap.get(ci.getSourceItemId());
            if (source == null) {
                continue;
            }
            source.setSaveRslt("1");
            source.setSaveDscr("提交成功");
            source.setUpdtTime(now);
            crmwPoolExcelImportMapper.updateItemSaveResult(source);
        }

        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(
                    batch.getResultJson() == null ? "{}" : batch.getResultJson());
            node.put("submitted", true);
            node.put("logCount", logIds.size());
            batch.setResultJson(objectMapper.writeValueAsString(node));
        } catch (Exception e) {
            batch.setResultJson("{\"submitted\":true,\"logCount\":" + logIds.size() + "}");
        }
        batch.setSaveRslt("1");
        batch.setSaveDscr("提交成功，共 " + logIds.size() + " 条调库记录");
        batch.setUpdtTime(now);
        crmwPoolExcelImportMapper.updateBatchSaveResult(batch);

        CrmwPoolExcelImportDto dto = toTaskDto(crmwPoolExcelImportMapper.queryByImpId(batch.getImpId()));
        dto.setLogIds(logIds);
        return dto;
    }

    /** 构建单组合提交请求 */
    private CrmwPoolAdjustSubmitReq buildSubmitReq(List<CrmwPoolExcelImportCheckItemDto> group,
                                                   SysImpTmpBo batch, String opterId, String opterName,
                                                   InvestmentPoolBo pool) {
        CrmwPoolExcelImportCheckItemDto primary = resolvePrimaryItem(group);
        CrmwPoolAdjustSubmitReq submitReq = new CrmwPoolAdjustSubmitReq();
        submitReq.setSecurityCode(primary.getSecurityCode());
        submitReq.setSecurityShortName(primary.getSecurityShortName());
        submitReq.setSecurityType(primary.getSecurityType());
        submitReq.setCrmwName(primary.getCrmwName());
        submitReq.setCrmwScode(primary.getCrmwScode());
        submitReq.setCrmwStype(resolveCrmwStype(primary.getCrmwStype()));
        submitReq.setAdjustType(ADJUST_TYPE_EXCEL);
        submitReq.setAdjustReason(batch.getFld002());
        submitReq.setAdjustAdvice(batch.getFld003());
        submitReq.setAdjusterId(opterId);
        submitReq.setAdjusterName(opterName);

        List<CrmwPoolAdjustSubmitReq.AdjustItem> submitItems = new ArrayList<>();
        for (CrmwPoolExcelImportCheckItemDto ci : group) {
            if (ci.getTargetPoolId() == null) {
                throw new BizException("校验结果数据不完整，请重新校验");
            }
            CrmwPoolAdjustSubmitReq.AdjustItem si = new CrmwPoolAdjustSubmitReq.AdjustItem();
            si.setTargetPoolId(ci.getTargetPoolId());
            si.setTargetPoolName(!isBlank(ci.getTargetPoolName()) ? ci.getTargetPoolName() : pool.getPoolName());
            si.setPoolType(PoolType.CRMW.getCode());
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
        return submitReq;
    }

    /** 存在直通项时提交前统一复核 */
    private boolean needsWholeBatchDirectRecheck(List<CrmwPoolExcelImportCheckItemDto> checkItems) {
        for (CrmwPoolExcelImportCheckItemDto item : checkItems) {
            if (!isManualTag(item.getItemTag())) {
                continue;
            }
            if (crmwPoolAdjustService.isDirectAdjustFlow(item.getFlowId(), item.getFlowKey())) {
                return true;
            }
        }
        return false;
    }

    /** 将提交明细转换为直通复核所需的日志快照 */
    private List<IpAdjustLogBo> buildDirectRecheckLogList(List<CrmwPoolExcelImportCheckItemDto> checkItems) {
        List<IpAdjustLogBo> logs = new ArrayList<>();
        for (CrmwPoolExcelImportCheckItemDto item : checkItems) {
            IpAdjustLogBo log = new IpAdjustLogBo();
            log.setSecurityCode(item.getSecurityCode());
            log.setSecurityShortName(item.getSecurityShortName());
            log.setSecurityType(item.getSecurityType());
            log.setCrmwName(item.getCrmwName());
            log.setCrmwScode(item.getCrmwScode());
            log.setCrmwStype(resolveCrmwStype(item.getCrmwStype()));
            log.setAdjustMode(item.getAdjustMode());
            log.setTargetPoolId(item.getTargetPoolId());
            log.setTargetPoolName(item.getTargetPoolName());
            logs.add(log);
        }
        return logs;
    }

    /** 映射单笔校验结果为导入校验行 */
    private CrmwPoolExcelImportCheckItemDto mapCheckResult(SysImpTmpDetlBo sourceItem,
                                                           AdjustCheckDto.CheckResultItem ri,
                                                           InvestmentPoolBo pool,
                                                           Map<String, SecurityInfoDetailDto> infoCache) {
        CrmwPoolExcelImportCheckItemDto dto = new CrmwPoolExcelImportCheckItemDto();
        if (sourceItem != null) {
            dto.setSourceItemId(sourceItem.getId());
            dto.setSourceRowNo(sourceItem.getRowNo());
        }
        String crmwScode = !isBlank(ri.getCrmwScode()) ? ri.getCrmwScode() : trimToEmpty(sourceItem.getFld001());
        String securityCode = !isBlank(ri.getSecurityCode()) ? ri.getSecurityCode() : trimToEmpty(sourceItem.getFld004());
        String sourceKey = !isBlank(ri.getSourceSecurityCode())
                ? ri.getSourceSecurityCode()
                : buildCombinationKey(crmwScode, securityCode);
        dto.setCrmwScode(crmwScode);
        dto.setSecurityCode(securityCode);
        dto.setSourceSecurityCode(sourceKey);
        dto.setCrmwStype(resolveCrmwStype(ri.getCrmwStype() != null ? ri.getCrmwStype() : sourceItem.getFld008()));
        dto.setSecurityType(ri.getSecurityType() != null ? ri.getSecurityType() : sourceItem.getFld006());
        dto.setSecurityShortName(ri.getSecurityShortName());
        dto.setCrmwName(!isBlank(ri.getCrmwName()) ? ri.getCrmwName() : sourceItem.getFld002());
        dto.setSecurityName(sourceItem.getFld005());
        dto.setCrmwMarket(sourceItem.getFld003());
        dto.setSecurityMarket(sourceItem.getFld007());
        dto.setSecurityTypeName(sourceItem.getFld011());
        // 展开项代码变化时按主数据补齐展示字段
        fillMappedDisplay(dto, infoCache);
        dto.setTargetPoolId(ri.getTargetPoolId() != null ? ri.getTargetPoolId() : pool.getId());
        dto.setTargetPoolName(!isBlank(ri.getPoolName()) ? ri.getPoolName() : pool.getPoolName());
        dto.setPoolType(PoolType.CRMW.getCode());
        dto.setAdjustMode(ri.getAdjustMode());
        dto.setItemTag(isBlank(ri.getItemTag()) ? ItemType.MANUAL.getCode() : ri.getItemTag());
        String gk = ri.getAdjustGroupKey();
        dto.setAdjustGroupKey(sourceKey + "_" + (gk == null ? "" : gk));
        dto.setCanAdjust(ri.isCanAdjust());
        dto.setFailReasons(ri.getFailReasons() == null ? new ArrayList<>() : new ArrayList<>(ri.getFailReasons()));
        dto.setAdjustType(resolveAdjustTypeLabel(dto.getItemTag()));
        if (dto.isCanAdjust()) {
            dto.setAdjustNote(dto.getAdjustType() + (dto.getAdjustMode() == null ? "" : dto.getAdjustMode()));
        }
        List<CrmwPoolExcelImportCheckItemDto.FlowOptionDto> flowOptions = new ArrayList<>();
        if (ri.getFlowOptions() != null) {
            for (AdjustCheckDto.FlowOption fo : ri.getFlowOptions()) {
                flowOptions.add(toFlowOptionDto(fo));
            }
        }
        dto.setFlowOptions(flowOptions);
        applyDefaultSelectedFlow(dto);
        return dto;
    }

    /** 展开项代码变化时按主数据补齐名称/市场/类型 */
    private void fillMappedDisplay(CrmwPoolExcelImportCheckItemDto dto,
                                   Map<String, SecurityInfoDetailDto> infoCache) {
        SecurityInfoDetailDto crmw = lookupSecurity(dto.getCrmwScode(), infoCache);
        if (crmw != null) {
            if (isBlank(dto.getCrmwName())) {
                dto.setCrmwName(resolveDisplayName(crmw));
            }
            if (isBlank(dto.getCrmwMarket())) {
                dto.setCrmwMarket(resolveMarketCodes(crmw));
            }
        }
        SecurityInfoDetailDto security = lookupSecurity(dto.getSecurityCode(), infoCache);
        if (security != null) {
            if (isBlank(dto.getSecurityName())) {
                dto.setSecurityName(resolveDisplayName(security));
            }
            if (isBlank(dto.getSecurityShortName())) {
                dto.setSecurityShortName(trimToNull(security.getShortName()));
            }
            if (isBlank(dto.getSecurityType())) {
                dto.setSecurityType(security.getSecurityType());
            }
            if (isBlank(dto.getSecurityTypeName())) {
                dto.setSecurityTypeName(security.getSecurityTypeName());
            }
            if (isBlank(dto.getSecurityMarket())) {
                dto.setSecurityMarket(resolveMarketCodes(security));
            }
        }
    }

    /** 回填导入行的凭证/标的展示字段 */
    private void fillSecurityDisplay(SysImpTmpDetlBo item, Map<String, SecurityInfoDetailDto> infoCache) {
        SecurityInfoDetailDto crmw = lookupSecurity(item.getFld001(), infoCache);
        if (crmw != null) {
            item.setFld002(resolveDisplayName(crmw));
            item.setFld003(resolveMarketCodes(crmw));
            item.setFld008(resolveCrmwStype(crmw.getSecurityType()));
        }
        SecurityInfoDetailDto security = lookupSecurity(item.getFld004(), infoCache);
        if (security != null) {
            item.setFld005(resolveDisplayName(security));
            item.setFld006(security.getSecurityType());
            item.setFld007(resolveMarketCodes(security));
            item.setFld011(security.getSecurityTypeName());
        }
    }

    /** 校验凭证与标的主数据是否可导入 */
    private List<String> validateComboMasterData(SysImpTmpDetlBo item,
                                                 Map<String, SecurityInfoDetailDto> infoCache) {
        List<String> reasons = new ArrayList<>();
        SecurityInfoDetailDto crmw = lookupSecurity(item.getFld001(), infoCache);
        if (crmw == null) {
            reasons.add("CRMW凭证不存在");
        } else if (!CRMW_SECURITY_TYPE.equals(crmw.getSecurityType())) {
            reasons.add("所选代码不是 CRMW 凭证");
        }
        SecurityInfoDetailDto security = lookupSecurity(item.getFld004(), infoCache);
        if (security == null) {
            reasons.add("证券不存在");
        } else if (CRMW_SECURITY_TYPE.equals(security.getSecurityType())) {
            reasons.add("调库对象不能是 CRMW 凭证");
        }
        return reasons;
    }

    /** 按 Wind 代码查询证券主数据（带缓存） */
    private SecurityInfoDetailDto lookupSecurity(String code, Map<String, SecurityInfoDetailDto> cache) {
        String key = trimToEmpty(code);
        if (key.isEmpty()) {
            return null;
        }
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        SecurityInfoDetailDto info = crmwPoolExcelImportMapper.querySecurityImportInfoByCode(key);
        cache.put(key, info);
        return info;
    }

    /** 全称优先，缺省回退简称 */
    private String resolveDisplayName(SecurityInfoDetailDto info) {
        if (info == null) {
            return null;
        }
        if (!isBlank(info.getFullName())) {
            return info.getFullName().trim();
        }
        return trimToNull(info.getShortName());
    }

    /** 按 Wind 市场字段推导市场编码列表 */
    private String resolveMarketCodes(SecurityInfoDetailDto info) {
        if (info == null) {
            return null;
        }
        List<String> codes = new ArrayList<>();
        if (!isBlank(info.getWindCodeSh())) {
            codes.add(MarketCode.SSE.getCode());
        }
        if (!isBlank(info.getWindCodeSz())) {
            codes.add(MarketCode.SZSE.getCode());
        }
        if (!isBlank(info.getWindCodeNib())) {
            codes.add(MarketCode.CIBM.getCode());
        }
        if (!isBlank(info.getWindCodeBj())) {
            codes.add(MarketCode.BSE.getCode());
        }
        if (!isBlank(info.getWindCodeNbc())) {
            codes.add(MarketCode.OTHER.getCode());
        }
        return codes.isEmpty() ? null : String.join(",", codes);
    }

    /** 解析标的简称 */
    private String resolveShortName(String fullName, String securityCode,
                                    Map<String, SecurityInfoDetailDto> infoCache) {
        SecurityInfoDetailDto info = lookupSecurity(securityCode, infoCache);
        if (info != null && !isBlank(info.getShortName())) {
            return info.getShortName().trim();
        }
        return trimToNull(fullName);
    }

    /** 空凭证类型回退为 crmw */
    private String resolveCrmwStype(String crmwStype) {
        return isBlank(crmwStype) ? CRMW_SECURITY_TYPE : crmwStype.trim();
    }

    /** 构建前置失败的手工校验结果项 */
    private CrmwPoolExcelImportCheckItemDto buildFailManualItem(SysImpTmpDetlBo sourceItem,
                                                                InvestmentPoolBo pool,
                                                                String adjustMode,
                                                                List<String> reasons) {
        CrmwPoolExcelImportCheckItemDto dto = new CrmwPoolExcelImportCheckItemDto();
        if (sourceItem != null) {
            dto.setSourceItemId(sourceItem.getId());
            dto.setSourceRowNo(sourceItem.getRowNo());
            dto.setCrmwScode(sourceItem.getFld001());
            dto.setCrmwName(sourceItem.getFld002());
            dto.setCrmwMarket(sourceItem.getFld003());
            dto.setSecurityCode(sourceItem.getFld004());
            dto.setSecurityName(sourceItem.getFld005());
            dto.setSecurityType(sourceItem.getFld006());
            dto.setSecurityMarket(sourceItem.getFld007());
            dto.setCrmwStype(resolveCrmwStype(sourceItem.getFld008()));
            dto.setSecurityTypeName(sourceItem.getFld011());
            dto.setSourceSecurityCode(buildCombinationKey(sourceItem.getFld001(), sourceItem.getFld004()));
        }
        if (pool != null) {
            dto.setTargetPoolId(pool.getId());
            dto.setTargetPoolName(pool.getPoolName());
            dto.setPoolType(PoolType.CRMW.getCode());
        }
        dto.setAdjustMode(adjustMode);
        dto.setItemTag(ItemType.MANUAL.getCode());
        dto.setAdjustType(ADJUST_TYPE_EXCEL);
        dto.setAdjustGroupKey(dto.getSourceSecurityCode() + "_"
                + (pool != null ? pool.getId() : "0") + "_" + adjustMode);
        dto.setCanAdjust(false);
        dto.setFailReasons(reasons == null ? new ArrayList<>() : new ArrayList<>(reasons));
        return dto;
    }

    /** 将单笔流程候选项映射为导入页流程 DTO */
    private CrmwPoolExcelImportCheckItemDto.FlowOptionDto toFlowOptionDto(AdjustCheckDto.FlowOption fo) {
        CrmwPoolExcelImportCheckItemDto.FlowOptionDto opt = new CrmwPoolExcelImportCheckItemDto.FlowOptionDto();
        opt.setFlowId(fo.getFlowId());
        opt.setFlowKey(fo.getFlowKey());
        String name = fo.getFlowName();
        if (isBlank(name) || looksLikeCode(name)) {
            name = resolveFlowTypeDisplayName(fo.getFlowType());
        }
        opt.setFlowName(name);
        opt.setFlowType(fo.getFlowType());
        opt.setRecommended(fo.isRecommended());
        opt.setMatched(fo.isMatched());
        opt.setSelectable(fo.isSelectable());
        opt.setMatchReasons(fo.getMatchReasons() == null ? new ArrayList<>() : new ArrayList<>(fo.getMatchReasons()));
        opt.setUnmatchReasons(fo.getUnmatchReasons() == null ? new ArrayList<>() : new ArrayList<>(fo.getUnmatchReasons()));
        opt.setOptionKey(buildFlowOptionKey(opt));
        return opt;
    }

    /** 为可调整项默认选中推荐流程 */
    private void applyDefaultSelectedFlow(CrmwPoolExcelImportCheckItemDto dto) {
        if (!dto.isCanAdjust() || dto.getFlowOptions() == null || dto.getFlowOptions().isEmpty()) {
            return;
        }
        CrmwPoolExcelImportCheckItemDto.FlowOptionDto selected = null;
        for (CrmwPoolExcelImportCheckItemDto.FlowOptionDto o : dto.getFlowOptions()) {
            if (o.isRecommended() && o.isSelectable()) {
                selected = o;
                break;
            }
        }
        if (selected == null) {
            for (CrmwPoolExcelImportCheckItemDto.FlowOptionDto o : dto.getFlowOptions()) {
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

    /** 从请求或批次快照中筛选可提交的校验结果项 */
    private List<CrmwPoolExcelImportCheckItemDto> filterSubmittableCheckItems(
            CrmwPoolExcelImportReq req, SysImpTmpBo batch) {
        List<CrmwPoolExcelImportCheckItemDto> source;
        if (req.getCheckItems() != null && !req.getCheckItems().isEmpty()) {
            source = req.getCheckItems();
        } else {
            source = parseCheckItems(batch.getResultJson());
        }
        List<CrmwPoolExcelImportCheckItemDto> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (CrmwPoolExcelImportCheckItemDto ci : source) {
            if (ci != null && ci.isCanAdjust()
                    && !isBlank(ci.getSecurityCode()) && !isBlank(ci.getCrmwScode())
                    && ci.getTargetPoolId() != null) {
                result.add(ci);
            }
        }
        return result;
    }

    /** 同组优先取手工项 */
    private CrmwPoolExcelImportCheckItemDto resolvePrimaryItem(List<CrmwPoolExcelImportCheckItemDto> items) {
        for (CrmwPoolExcelImportCheckItemDto item : items) {
            if (isManualTag(item.getItemTag())) {
                return item;
            }
        }
        return items.get(0);
    }

    /** 是否为手工调整项标签 */
    private boolean isManualTag(String itemTag) {
        return itemTag == null || itemTag.isEmpty() || ItemType.MANUAL.getCode().equals(itemTag);
    }

    /** 将 itemTag 转为调整类型中文 */
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
        return ADJUST_TYPE_EXCEL;
    }

    /** 生成流程 optionKey */
    private String buildFlowOptionKey(CrmwPoolExcelImportCheckItemDto.FlowOptionDto opt) {
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
        if (FlowType.UPGRADE_INBOUND.getCode().equals(flowType)) {
            return "上调流程";
        }
        if (FlowType.DOWNGRADE_INBOUND.getCode().equals(flowType)) {
            return "下调流程";
        }
        return "审批流程";
    }

    /** 判断文本是否像流程编码 */
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
        Set<Long> roleIdSet = new HashSet<>(roleIds == null ? new ArrayList<Long>() : roleIds);
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

    /** 校验上传请求与文件基础参数 */
    private void validateUploadReq(CrmwPoolExcelImportReq req, MultipartFile file) {
        if (req == null) {
            throw new BizException("请求参数不能为空");
        }
        normalizeDirection(req.getDirection());
        if (req.getTargetPoolId() == null) {
            throw new BizException("目标投资池 ID 不能为空");
        }
        if (isBlank(req.getCurrentUserId())) {
            throw new BizException("当前用户 ID 不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BizException("文件大小不能超过 5MB");
        }
    }

    /** 加载启用叶子 CRMW 池 */
    private InvestmentPoolBo requireTargetPool(Long poolId) {
        if (poolId == null) {
            throw new BizException("目标投资池 ID 不能为空");
        }
        InvestmentPoolBo pool = crmwPoolExcelImportMapper.queryEnabledLeafCrmwPoolById(poolId);
        if (pool == null) {
            throw new BizException("目标投资池不存在、未启用、不是叶子池或不是 CRMW 池");
        }
        return pool;
    }

    /** 规范化调整方向：in / out */
    private String normalizeDirection(String direction) {
        if (!"in".equals(direction) && !"out".equals(direction)) {
            throw new BizException("调整方向必须为 in 或 out");
        }
        return direction;
    }

    /** 按批次号加载导入批次 */
    private SysImpTmpBo requireBatch(String impId) {
        if (isBlank(impId)) {
            throw new BizException("导入批次号不能为空");
        }
        SysImpTmpBo batch = crmwPoolExcelImportMapper.queryByImpId(impId.trim());
        if (batch == null) {
            throw new BizException("导入批次不存在或已取消");
        }
        return batch;
    }

    /** 解析经办人名称 */
    private String resolveUserName(CrmwPoolExcelImportReq req) {
        if (!isBlank(req.getCurrentUserName())) {
            return req.getCurrentUserName().trim();
        }
        return req.getCurrentUserId().trim();
    }

    /** 生成导入批次号 */
    private String nextImpId(Date now) {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(now);
        int seq = IMP_SEQ.incrementAndGet() % 10000;
        return "IMP" + ts + String.format("%04d", seq);
    }

    /** 生成导入明细号 */
    private String nextDetlId(Date now) {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(now);
        int seq = DETL_SEQ.incrementAndGet() % 100000;
        return "IMPD" + ts + String.format("%05d", seq);
    }

    /** 批次主表转前端任务 DTO */
    private CrmwPoolExcelImportDto toTaskDto(SysImpTmpBo batch) {
        CrmwPoolExcelImportDto dto = new CrmwPoolExcelImportDto();
        dto.setImpId(batch.getImpId());
        dto.setBizType(batch.getBizType());
        dto.setFileName(batch.getFileName());
        dto.setBizMode(batch.getFld001());
        dto.setOptionJson(batch.getOptionJson());
        dto.setReason(batch.getFld002());
        dto.setAdvice(batch.getFld003());
        dto.setTargetPoolId(parsePoolId(batch.getFld004()));
        dto.setTargetPoolName(batch.getFld005());
        dto.setTotalCount(batch.getTotalCount());
        dto.setPassCount(batch.getPassCount() == null ? 0 : batch.getPassCount());
        dto.setFailCount(batch.getFailCount() == null ? 0 : batch.getFailCount());
        dto.setPendingCount(crmwPoolExcelImportMapper.countByChkRslt(batch.getImpId(), "0"));
        dto.setChkRslt(batch.getChkRslt());
        dto.setChkDscr(batch.getChkDscr());
        dto.setSaveRslt(batch.getSaveRslt());
        dto.setSaveDscr(batch.getSaveDscr());
        dto.setResultJson(batch.getResultJson());
        dto.setImpTime(batch.getImpTime());
        List<CrmwPoolExcelImportCheckItemDto> checkItems = parseCheckItems(batch.getResultJson());
        dto.setCheckItems(checkItems == null ? new ArrayList<CrmwPoolExcelImportCheckItemDto>() : checkItems);
        int checkPass = 0;
        int checkFail = 0;
        if (checkItems != null) {
            for (CrmwPoolExcelImportCheckItemDto ci : checkItems) {
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

    /** 将调库校验结果序列化写入 result_json */
    private String buildCheckResultJson(List<CrmwPoolExcelImportCheckItemDto> checkItems,
                                        int checkPass, int checkFail) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("checkDone", true);
            root.put("checkPassCount", checkPass);
            root.put("checkFailCount", checkFail);
            root.set("checkItems", objectMapper.valueToTree(checkItems));
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"checkDone\":true,\"checkItems\":[]}";
        }
    }

    /** 从 result_json 反序列化 checkItems */
    private List<CrmwPoolExcelImportCheckItemDto> parseCheckItems(String resultJson) {
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
                    new TypeReference<List<CrmwPoolExcelImportCheckItemDto>>() {
                    });
        } catch (Exception e) {
            return null;
        }
    }

    /** 临时表明细转前端明细 DTO */
    private CrmwPoolExcelImportItemDto toItemDto(SysImpTmpDetlBo bo) {
        CrmwPoolExcelImportItemDto dto = new CrmwPoolExcelImportItemDto();
        dto.setId(bo.getId());
        dto.setImpDetlId(bo.getImpDetlId());
        dto.setRowNo(bo.getRowNo());
        dto.setCrmwScode(bo.getFld001());
        dto.setCrmwName(bo.getFld002());
        dto.setCrmwMarket(bo.getFld003());
        dto.setSecurityCode(bo.getFld004());
        dto.setSecurityName(bo.getFld005());
        dto.setSecurityType(bo.getFld006());
        dto.setSecurityMarket(bo.getFld007());
        dto.setCrmwStype(bo.getFld008());
        dto.setSecurityTypeName(bo.getFld011());
        dto.setResolvedPoolId(parsePoolId(bo.getFld009()));
        dto.setChkRslt(bo.getChkRslt());
        dto.setChkDscr(bo.getChkDscr());
        dto.setSaveRslt(bo.getSaveRslt());
        dto.setSaveDscr(bo.getSaveDscr());
        dto.setRefId(bo.getRefId());
        return dto;
    }

    /** 组装导入选项 JSON */
    private String buildOptionJson(InvestmentPoolBo pool) {
        return "{\"targetPoolId\":" + pool.getId()
                + ",\"targetPoolName\":\"" + escapeJson(pool.getPoolName())
                + "\",\"poolType\":\"" + PoolType.CRMW.getCode() + "\"}";
    }

    /** JSON 字符串转义 */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 将导入明细标记为校验失败并回写临时表 */
    private void markItemFail(SysImpTmpDetlBo item, List<String> reasons, Date now) {
        item.setChkRslt("2");
        item.setChkDscr(joinReasons(reasons));
        item.setUpdtTime(now);
        crmwPoolExcelImportMapper.updateItemCheckResult(item);
    }

    /** 按表头读取单元格文本 */
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

    /** 解析目标池 ID */
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

    /** 解析 Excel 物理行号 */
    private int parseRowNo(String text) {
        try {
            return Integer.parseInt(text);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 组合键：凭证|标的 */
    private String buildCombinationKey(String crmwScode, String securityCode) {
        return trimToEmpty(crmwScode) + "|" + trimToEmpty(securityCode);
    }

    /** 将失败原因列表拼接为校验说明 */
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

    /** 去除字符串首尾空白，并将空字符串转换为 {@code null}。 */
    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 去除字符串首尾空白，并将 {@code null} 转换为空字符串。 */
    private String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    /** 判断字符串是否为空或仅包含空白字符。 */
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
