package com.znty.rrs.service;

import com.znty.rrs.entity.common.SecurityMarketDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AdjustType;
import com.znty.rrs.common.enums.FlowType;
import com.znty.rrs.common.enums.HandlerType;
import com.znty.rrs.common.enums.ItemType;
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.common.util.BatchAdjustFlowOptionHelper;
import com.znty.rrs.common.util.CreditBondSpecialInboundRule;
import com.znty.rrs.common.util.ExcelImportHelper;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.common.GuarantorGradeDto;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustCheckReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustDto;
import com.znty.rrs.entity.forbiddenpooladjust.CompanyAdjustCheckContext;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustSubmitReq;
import com.znty.rrs.entity.securitypoolexcelimport.PoolMemberDto;
import com.znty.rrs.entity.securitypoolexcelimport.PoolClearSummaryDto;
import com.znty.rrs.entity.securitypoolexcelimport.CompanyImportMatchDto;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportCheckItemDto;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportDto;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportItemDto;
import com.znty.rrs.entity.securitypoolexcelimport.SecurityPoolExcelImportReq;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData.Row;
import com.znty.rrs.entity.securitypooladjust.AdjustSubmitDto;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.RatingCompanySelectionReq;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustSubmitReq;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CommonMapper;
import com.znty.rrs.mapper.FlowMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.mapper.BatchSecurityCheckMapper;
import com.znty.rrs.mapper.SecurityPoolExcelImportMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 证券/主体 Excel 导入服务
 * <p>
 * 校验/提交按导入类型分分支：
 * <ul>
 *   <li>证券：对齐证券池批量调整（checkAdjust / addAdjustLog + 批量流程注入）</li>
 *   <li>主体：对齐禁投池调整（checkCompanyAdjust / addCompanyAdjustLog）</li>
 *   <li>清空目标池：独立方法 独立入口批量校验和直通出库</li>
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
    private static final String ADJUST_TYPE_EXCEL = SecurityPoolAdjustService.EXCEL_IMPORT_ADJUST_TYPE;
    /** 清空目标池固定使用的无需审核审批流程 */
    private static final String CLEAR_POOL_FLOW_KEY = "bond:no-approval";
    /** 管理员用户 ID（放行 excel_importable） */
    private static final String ADMIN_USER_ID = "1";
    /** 上传文件大小上限（字节） */
    private static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    /** 单次导入最大数据行数 */
    private static final int MAX_ROWS = 2000;

    /** 导入明细一次批量写入的行数 */
    private static final int DETAIL_BATCH_SIZE = 200;
    /** 主体匹配每批最大条件数，避免生成过长 SQL */
    private static final int COMPANY_QUERY_BATCH_SIZE = 500;

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

    /** 清空目标池使用的已发布流程定义 */
    @Resource
    private FlowMapper flowMapper;
    /** 判断清空目标池流程是否直通 */
    @Resource
    private SecurityPoolAdjustService securityPoolAdjustService;

    /** 批量复核实际调整对象的在池和在途记录 */
    @Resource
    private BatchSecurityCheckMapper batchSecurityCheckMapper;

    /** ABS 候选与跨池主体身份 */
    @Resource
    private AbsIdentityService absIdentityService;
    /** 关联主体批量查询（与批量调整可选证券使用同一排序） */
    @Resource
    private CommonMapper commonMapper;
    /** 证券调库完整校验/提交（批量调库内部亦委托该服务） */
    @Resource
    private BatchSecurityAdjustSubmitService batchSecurityAdjustSubmitService;

    /** 证券导入及清空出库的批量校验 */
    @Resource
    private BatchSecurityCheckService batchSecurityCheckService;
    /** 禁投池主体完整校验/提交 */
    @Resource
    private ForbiddenPoolAdjustService forbiddenPoolAdjustService;

    @Resource
    private BatchCompanyAdjustSubmitService batchCompanyAdjustSubmitService;

    @Resource
    private BatchCompanyAdjustCheckService batchCompanyAdjustCheckService;
    /** 附件服务（解析前端原始文件名，兼容公司环境中文乱码） */
    @Resource
    private SysAttachmentService sysAttachmentService;

    // ═══════════════════════════════════════════════════════════
    //  上传 / 查询 / 取消
    // ═══════════════════════════════════════════════════════════

    /** 上传 Excel 并写入导入临时表 */
    @Transactional(rollbackFor = Exception.class)
    public SecurityPoolExcelImportDto uploadExcel(SecurityPoolExcelImportReq req, MultipartFile file) {
        return uploadExcel(req, file, null);
    }

    /**
     * 上传 Excel 并写入导入临时表（可带前端原始文件名 JSON 数组，兼容公司环境中文名乱码）。
     */
    @Transactional(rollbackFor = Exception.class)
    public SecurityPoolExcelImportDto uploadExcel(SecurityPoolExcelImportReq req, MultipartFile file,
                                                  String originalFileNameListJson) {
        // 校验上传请求与文件基础参数
        validateUploadReq(req, file);
        List<String> originalFileNameList =
                sysAttachmentService.parseOriginalFileNameListJson(originalFileNameListJson);
        String uploadFileName = sysAttachmentService.resolveUploadOriginalFileName(file, originalFileNameList);
        // 规范化导入类型（security / company）
        String importType = normalizeImportType(req.getImportType());

        // 解析 Excel 首 sheet
        List<Map<String, String>> excelRows = ExcelImportHelper.parseFirstSheet(file, MAX_ROWS);
        Date now = new Date();
        // 生成导入批次号
        String impId = nextImpId(now);
        // 组装 option_json（allowLinkMutex / importType）
        String optionJson = buildOptionJson(req.getAllowLinkMutex(), importType);

        SysImpTmpBo batch = new SysImpTmpBo();
        batch.setImpId(impId);
        batch.setBizType(IMPORT_TYPE_COMPANY.equals(importType) ? BIZ_TYPE_COMPANY : BIZ_TYPE_SECURITY);
        batch.setTemplateCode(IMPORT_TYPE_COMPANY.equals(importType)
                ? "company_pool_import" : "security_pool_import");
        batch.setFileName(uploadFileName);
        batch.setFileSize(file.getSize());
        // 规范化调整方向
        batch.setFld001(normalizeDirection(req.getDirection()));
        batch.setOptionJson(optionJson);
        // 空白调整原因/意见规范为 null
        batch.setFld002(trimToNull(req.getAdjustReason()));
        batch.setFld003(trimToNull(req.getAdjustAdvice()));
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

        List<SysImpTmpDetlBo> items = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (Map<String, String> row : excelRows) {
            SysImpTmpDetlBo item = new SysImpTmpDetlBo();
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
            items.add(item);
        }
        boolean company = IMPORT_TYPE_COMPANY.equals(importType);
        // 按优先级批量查询当前上传涉及的主体，行循环只做内存匹配
        CompanyImportLookup lookup = company ? loadCompanyImportLookup(items) : new CompanyImportLookup();
        for (SysImpTmpDetlBo item : items) {
            List<String> preFail = new ArrayList<>();
            if (company) {
                // 按信用代码、主体代码、主体全称顺序匹配，回填后参与重复检查
                resolveCompanyCode(item, lookup.codeMatches, preFail);
            } else if (isBlank(item.getFld001())) {
                preFail.add("证券代码为空");
            }
            if (!company && isBlank(item.getFld003())) {
                preFail.add("父池名称为空");
            }
            if (isBlank(item.getFld004())) {
                preFail.add("子池名称为空");
            }
            String codeKey = trimToEmpty(item.getFld001());
            if (!company) {
                codeKey = codeKey.toUpperCase(Locale.ROOT);
            }
            String uniq = codeKey + "|" + trimToEmpty(item.getFld004());
            if (!isBlank(item.getFld001()) && !isBlank(item.getFld004()) && !seenKeys.add(uniq)) {
                preFail.add(company ? "Excel 内相同主体代码与子池重复" : "Excel 内相同证券代码与子池重复");
            }
            if (!preFail.isEmpty()) {
                item.setChkRslt("2");
                // 拼接前置失败原因
                item.setChkDscr(joinReasons(preFail));
            }
        }
        if (IMPORT_TYPE_SECURITY.equals(importType)) {
            // 仅上传时默认选择首个关联主体，后续分页和校验保留业务的选择
            initializeRatingCompanies(items);
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

        securityPoolExcelImportMapper.addBatch(batch);
        for (int i = 0; i < items.size(); i += DETAIL_BATCH_SIZE) {
            int end = Math.min(i + DETAIL_BATCH_SIZE, items.size());
            securityPoolExcelImportMapper.addItemList(items.subList(i, end));
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
        SysImpTmpBo batch = requireBatch(req == null ? null : req.getImpId());
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
        SysImpTmpBo batch = requireBatch(req.getImpId());
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SysImpTmpDetlBo> list = securityPoolExcelImportMapper.queryItemPage(
                req.getImpId().trim(), trimToNull(req.getChkRslt()), trimToNull(req.getKeyword()));
        PageInfo<SysImpTmpDetlBo> pageInfo = new PageInfo<>(list);
        List<SecurityPoolExcelImportItemDto> records = new ArrayList<>();
        for (SysImpTmpDetlBo bo : list) {
            // 明细 Bo 转 DTO
            records.add(toItemDto(bo));
        }
        if (IMPORT_TYPE_SECURITY.equals(parseImportType(batch.getOptionJson(), batch.getBizType()))) {
            // 为当前页补齐关联候选、自选权益人和内评展示数据
            fillRatingCompanyOptions(records, null);
        }
        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 批量读取关联候选，仅首次上传时默认选择第一条。 */
    private void initializeRatingCompanies(List<SysImpTmpDetlBo> items) {
        List<String> securityCodes = items.stream().map(SysImpTmpDetlBo::getFld001)
                .filter(code -> !isBlank(code)).map(String::trim).distinct().collect(Collectors.toList());
        if (securityCodes.isEmpty()) {
            return;
        }
        Map<String, GuarantorGradeDto> firstCompanies = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Set<String>> allCompanies = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, SecurityInfoBo> currentIdentities = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        currentIdentities.putAll(absIdentityService.queryCurrentIdentities(securityCodes));
        for (GuarantorGradeDto company : commonMapper.queryGuarantorGradeList(securityCodes)) {
            if (!isBlank(company.getWindcode())) {
                firstCompanies.putIfAbsent(company.getSecurityCode(), company);
                allCompanies.computeIfAbsent(company.getSecurityCode(), key -> new HashSet<>()).add(company.getWindcode());
            }
        }
        for (SysImpTmpDetlBo item : items) {
            GuarantorGradeDto company = firstCompanies.get(trimToEmpty(item.getFld001()));
            if (company != null) {
                item.setFld011(company.getWindcode());
                item.setFld012(company.getWindname());
            }
            SecurityInfoBo identity = currentIdentities.get(trimToEmpty(item.getFld001()));
            if (identity != null) {
                Set<String> relatedCodes = allCompanies.getOrDefault(trimToEmpty(item.getFld001()), Collections.emptySet());
                if (relatedCodes.contains(identity.getIssuerCode())) {
                    item.setFld011(identity.getIssuerCode());
                    item.setFld012(identity.getIssuer());
                } else {
                    item.setFld013(identity.getIssuerCode());
                    item.setFld014(identity.getIssuer());
                }
            }
        }
    }

    /** 按当前页批量补齐证券类型、可选评级主体及自选权益人 */
    private void fillRatingCompanyOptions(List<SecurityPoolExcelImportItemDto> records, BatchSecurityCheckData data) {
        if (records.isEmpty()) {
            return;
        }
        List<String> codes = records.stream().map(row -> trimToEmpty(row.getSecurityCode()))
                .filter(code -> !code.isEmpty()).distinct().collect(Collectors.toList());
        List<String> selfSelected = records.stream().map(row -> trimToEmpty(row.getSelfSelectedRightsHolderCode()))
                .filter(code -> !code.isEmpty()).distinct().collect(Collectors.toList());
        if (data == null) {
            // 独立翻页以当前数据重新读取候选
            data = batchSecurityCheckService.loadExcelRatingCandidates(codes, selfSelected);
        } else {
            // 校验首屏复用本次候选，只补查未进入校验取数的行或选择
            batchSecurityCheckService.completeExcelRatingCandidates(data, codes, selfSelected);
        }
        for (SecurityPoolExcelImportItemDto dto : records) {
            String code = data.resolveExcelSecurityCode(trimToEmpty(dto.getSecurityCode()));
            SecurityInfoBo info = data.securities.get(code);
            List<RelatedRatingCompanyDto> related = data.ratingCompanies.getOrDefault(code, Collections.emptyList());
            if (info != null) {
                dto.setSecurityType(info.getSecurityType());
                dto.setAbsFlag(info.getAbsFlag());
            }
            dto.setRelatedRatingCompanies(related);
            for (RelatedRatingCompanyDto company : related) {
                if (trimToEmpty(dto.getRelatedCompanyCode()).equals(company.getCompanyCode())) {
                    dto.setRatingCompanyInnerRating(company.getInnerRating());
                    break;
                }
            }
            if (!isBlank(dto.getSelfSelectedRightsHolderCode())) {
                SelfSelectedRightsHolderDto self =
                        data.selfSelectedCompanies.get(trimToEmpty(dto.getSelfSelectedRightsHolderCode()));
                if (self != null) {
                    dto.setSelfSelectedRightsHolder(self);
                }
                dto.setRatingCompanyInnerRating(self == null ? null : self.getInnerRating());
            }
        }
    }

    /**
     * 保存当前导入行的权益人/担保人选择，并使本批旧校验结果失效。
     *
     * 执行顺序：
     * 1. 检查请求、导入明细 ID 和当前用户 ID 是否为空。
     * 2. 读取导入批次，确认批次尚未提交且属于证券导入。
     * 3. 按批次号和明细 ID 查询有效导入行，确认该明细属于当前批次。
     * 4. 规范化关联主体及自选权益人代码，空字符串转为 null；复核候选资格并由服务端回填名称，
     *    编辑时允许暂不选择主体。
     * 5. 仅更新当前行的主体选择，其他相同证券代码的导入行保留各自选择。
     * 6. 清空本批所有行的旧校验结果及批次校验快照，清除校验结果筛选并补齐有效页码，回查任务和明细。
     *
     * @param req 携带批次号、明细 ID、主体选择和分页条件的修改请求
     * @return 保存后的批次任务及当前页明细，主体修改后须重新校验
     */
    @Transactional(rollbackFor = Exception.class)
    public SecurityPoolExcelImportDto editRatingCompany(SecurityPoolExcelImportReq req) {
        if (req == null || req.getItemId() == null || isBlank(req.getCurrentUserId())) {
            throw new BizException("导入明细 ID 和当前用户 ID 不能为空");
        }
        // 读取本次要修改的批次
        SysImpTmpBo batch = requireBatch(req.getImpId());
        if ("1".equals(batch.getSaveRslt())) {
            throw new BizException("该批次已提交，不能修改主体选择");
        }
        // 仅证券导入使用担保人和权益人选择
        if (!IMPORT_TYPE_SECURITY.equals(parseImportType(batch.getOptionJson(), batch.getBizType()))) {
            throw new BizException("主体导入不支持修改担保人或权益人");
        }
        SysImpTmpDetlBo selection = securityPoolExcelImportMapper.queryItemById(batch.getImpId(), req.getItemId());
        if (selection == null) {
            throw new BizException("导入明细不存在或不属于当前批次");
        }
        // 空字符串允许清除关联主体或自选权益人
        selection.setFld011(trimToNull(req.getRelatedCompanyCode()));
        selection.setFld013(trimToNull(req.getSelfSelectedRightsHolderCode()));
        // 按本次选择重新查询并回填名称，编辑时允许清空主体
        String securityCode = trimToEmpty(selection.getFld001());
        List<String> selfSelected = isBlank(selection.getFld013()) ? Collections.emptyList()
                : Collections.singletonList(selection.getFld013());
        BatchSecurityCheckData ratingData = batchSecurityCheckService.loadExcelRatingCandidates(
                Collections.singletonList(securityCode), selfSelected);
        String masterCode = ratingData.resolveExcelSecurityCode(securityCode);
        if (resolveImportedRatingCompany(selection, false, ratingData.securities.get(masterCode),
                ratingData.ratingCompanies.getOrDefault(masterCode, Collections.emptyList()),
                () -> ratingData.selfSelectedCompanies.get(trimToEmpty(selection.getFld013()))) == null) {
            throw new BizException("证券不存在或已失效，不能选择评级主体");
        }
        Date now = new Date();
        selection.setUpdtTime(now);
        if (securityPoolExcelImportMapper.editRatingCompany(selection) == 0) {
            throw new BizException("导入明细不存在或不属于当前批次");
        }
        securityPoolExcelImportMapper.editItemCheckResultByImpId(batch.getImpId(), now);
        batch.setPassCount(0);
        batch.setFailCount(0);
        batch.setChkRslt("0");
        batch.setChkDscr("主体选择已更新，请重新校验");
        batch.setResultJson(null);
        batch.setUpdtTime(now);
        securityPoolExcelImportMapper.editBatchCheckResult(batch);
        req.setChkRslt(null);
        if (req.getPageIndex() <= 0) {
            req.setPageIndex(1);
        }
        // 回查持久化的选择及已清空的校验结果
        return queryTask(req);
    }

    /** 取消导入批次（逻辑删除主表与明细） */
    @Transactional(rollbackFor = Exception.class)
    public void cancelImport(SecurityPoolExcelImportReq req) {
        if (req == null || req.getImpId() == null || req.getImpId().trim().isEmpty()) {
            throw new BizException("导入批次号不能为空");
        }
        // 读取本次要取消的批次
        SysImpTmpBo batch = requireBatch(req.getImpId());
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
        // 读取本次要校验的批次
        SysImpTmpBo batch = requireBatch(req.getImpId());
        if ("1".equals(batch.getSaveRslt())) {
            throw new BizException("该批次已提交，不能再次校验");
        }
        String opterId = req.getCurrentUserId() != null && !req.getCurrentUserId().trim().isEmpty()
                ? req.getCurrentUserId().trim() : batch.getOpterId();
        List<SysImpTmpDetlBo> items = securityPoolExcelImportMapper.queryBatchItemList(batch.getImpId());
        if (items.isEmpty()) {
            throw new BizException("导入明细为空");
        }

        boolean inbound = "in".equals(batch.getFld001());
        // 解析是否允许联动与互斥
        boolean allowLinkMutex = parseAllowLinkMutex(batch.getOptionJson());
        // 解析导入类型（证券/主体）
        String importType = parseImportType(batch.getOptionJson(), batch.getBizType());
        Date now = new Date();
        // 是否主体导入
        boolean companyImport = IMPORT_TYPE_COMPANY.equals(importType);

        List<SecurityPoolExcelImportCheckItemDto> checkItems;
        BatchSecurityCheckData securityData = null;
        if (companyImport) {
            // ── 主体分支：禁投池调整同构 ──
            checkItems = checkCompanyImport(items, inbound, allowLinkMutex, opterId, now);
        } else {
            // ── 证券分支：证券池批量调整同构 ──
            SecurityImportCheckResult result = checkSecurityImport(items, inbound, allowLinkMutex, opterId, now);
            checkItems = result.items;
            securityData = result.data;
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
            long tipCount = checkItems.stream().filter(ci -> ci.getFailReasons() != null
                    && !ci.getFailReasons().isEmpty()).count();
            batch.setChkDscr(tipCount == 0 ? "全部校验通过（含调库展开项 " + checkItems.size() + " 条）"
                    : "全部可调整，其中 " + tipCount + " 条存在调整提示");
        } else {
            batch.setChkDscr("导入行失败 " + fail + " 条，校验结果失败 " + checkFail + " 条");
        }
        // 序列化调库校验结果写入主表
        batch.setResultJson(buildCheckResultJson(checkItems, checkPass, checkFail, allowLinkMutex, importType));
        batch.setUpdtTime(now);
        // 先保存各行校验结果，再写入依赖这些结果的批次摘要
        for (int i = 0; i < items.size(); i += DETAIL_BATCH_SIZE) {
            int end = Math.min(i + DETAIL_BATCH_SIZE, items.size());
            securityPoolExcelImportMapper.editItemCheckResultList(batch.getImpId(), items.subList(i, end), now);
        }
        securityPoolExcelImportMapper.editBatchCheckResult(batch);

        // 本次已按分页顺序读取并回写全部明细，直接组装校验首屏
        int pending = (int) items.stream().filter(row -> "0".equals(row.getChkRslt())).count();
        SecurityPoolExcelImportDto dto = toTaskDto(batch, pending);
        int pageSize = 20;
        List<SecurityPoolExcelImportItemDto> firstPage = items.stream().limit(pageSize)
                .map(this::toItemDto).collect(Collectors.toList());
        if (!companyImport) {
            // 复用本次证券主档及主体候选完成首屏回显
            fillRatingCompanyOptions(firstPage, securityData);
        }
        dto.setItems(new PageResult<>(firstPage, items.size(), 1, pageSize));
        return dto;
    }

    /**
     * 证券导入校验：编排对齐 {@link BatchSecurityPoolAdjustService#checkAdjust}。
     * <p>每行 Excel 对应「一券 + 一目标池 + 方向」→ 调用
     * {@link SecurityPoolAdjustService#checkAdjust}（批量内部也是此路径），
     * 再注入目标池批量流程候选；未勾选联动互斥时仅保留手工项。</p>
     *
     * 执行顺序：
     * 1. 确定调入/调出方向，并初始化本次校验结果和父子池名称查询缓存。
     * 2. 逐行检查证券代码、父池和子池名称，解析启用叶子池并检查导入权限；前置失败时回写原因。
     * 3. 使用本行已保存的权益人/担保人或 ABS 自选权益人构建单券校验请求，复核主体资格后执行
     *    完整调库校验；业务校验异常按当前行失败处理。
     * 4. 根据联动互斥选项保留完整展开结果或仅保留手工项，再映射来源明细、主体信息和流程候选。
     * 5. 以手工主项判断本行是否通过，通过时回写通过状态；失败时汇总原因，
     *    同时禁止该行的联动、互斥和关联项单独提交。
     * 6. 返回所有行的校验结果，非 ABS 各行独立选择，ABS 同码不同主体作为不可调整原因返回。
     *
     * @param items 本批有效导入明细，每行包含一只证券、一个目标池及独立主体选择
     * @param inbound 是否调入，false 表示调出
     * @param allowLinkMutex 是否保留联动、互斥和关联展开项
     * @param opterId 执行导入校验的用户 ID
     * @param now 回写行级校验结果使用的统一时间
     * @return 各行校验结果及首屏可复用的批量数据
     */
    private SecurityImportCheckResult checkSecurityImport(
            List<SysImpTmpDetlBo> items, boolean inbound, boolean allowLinkMutex,
            String opterId, Date now) {
        List<SecurityImportCheckRow> rows = new ArrayList<>();
        List<AdjustCheckReq> requests = new ArrayList<>();
        List<AdjustCheckReq> selections = new ArrayList<>();
        List<SecurityPoolExcelImportCheckItemDto> checkItems = new ArrayList<>();
        Map<String, InvestmentPoolBo> poolNameCache = new HashMap<>();
        Map<Long, String> permissionErrors = new HashMap<>();
        String adjustMode = inbound ? AdjustMode.IN.getCode() : AdjustMode.OUT.getCode();
        String direction = inbound ? "in" : "out";

        for (SysImpTmpDetlBo item : items) {
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
                pool = resolvePoolByNames(parentName, childName, true, poolNameCache);
                if (pool == null) {
                    preReasons.add("未找到启用叶子池：父池[" + parentName + "] / 子池[" + childName + "]");
                } else {
                    item.setFld009(String.valueOf(pool.getId()));
                    item.setFld010(pool.getPoolType());
                    if (!permissionErrors.containsKey(pool.getId())) {
                        String error = null;
                        try {
                            // 同批同池共用导入权限结果
                            validateExcelImportPermission(opterId, pool.getId());
                        } catch (BizException e) {
                            error = e.getMessage();
                        }
                        permissionErrors.put(pool.getId(), error);
                    }
                    if (permissionErrors.get(pool.getId()) != null) {
                        preReasons.add(permissionErrors.get(pool.getId()));
                    }
                }
            }

            AdjustCheckReq request = preReasons.isEmpty()
                    ? buildExcelSecurityCheck(code, trimToNull(item.getFld002()), pool, adjustMode)
                    : new AdjustCheckReq();
            // 入口失败的行也参与同一 ABS 的主体一致性检查
            request.setSecurityCode(code);
            request.setGuarantorCode(trimToNull(item.getFld011()));
            request.setRightsHolderCode(trimToNull(item.getFld011()));
            request.setSelfSelectedRightsHolderCode(trimToNull(item.getFld013()));
            selections.add(request);
            if (preReasons.isEmpty()) {
                requests.add(request);
            }
            rows.add(new SecurityImportCheckRow(item, pool, request, preReasons));
        }

        // 证券、池关系、状态与评级主体候选在回写行结果前批量读取
        BatchSecurityCheckData data = batchSecurityCheckService.loadExcelCheckData(requests, false);
        Set<String> identityConflicts = batchSecurityCheckService.findConflictingAbsCodes(selections, data);
        for (SecurityImportCheckRow row : rows) {
            SysImpTmpDetlBo item = row.item;
            InvestmentPoolBo pool = row.pool;
            String code = row.request.getSecurityCode();
            if (!row.errors.isEmpty()) {
                // 记录入口错误并保留原行序
                markItemFail(item, row.errors, now);
                checkItems.add(buildFailManualItem(item, code, item.getFld002(), null, pool, adjustMode, row.errors));
                continue;
            }

            // 将主档代码写回来源行，提交时与校验结果使用同一代码
            item.setFld001(code);
            AdjustCheckDto checkDto;
            try {
                // 复核并使用页面保存的权益人或担保人，不覆盖手动选择
                applyImportedRatingCompany(row.request, item, data);
                checkDto = batchSecurityCheckService.checkExcelItem(row.request, data);
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
            // 同批不同主体只阻断冲突证券，不中断其他来源行的校验
            if (identityConflicts.contains(code)) {
                String reason = "同一 ABS[" + code + "]在同批申请中选择了不同主体，请统一后重新校验";
                for (AdjustCheckDto.CheckResultItem resultItem : resultItems) {
                    absIdentityService.applyCheckFailure(resultItem, reason);
                }
            }
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

            int rowStart = checkItems.size();
            boolean manualPass = false;
            for (AdjustCheckDto.CheckResultItem ri : resultItems) {
                // 映射证券校验结果并注入批量流程
                SecurityPoolExcelImportCheckItemDto mapped =
                        mapSecurityCheckResult(item, ri, code, pool, direction);
                // 判断手工主项是否可调整
                if (isManualTag(mapped.getItemTag()) && mapped.isCanAdjust()) {
                    manualPass = true;
                }
                checkItems.add(mapped);
            }

            if (manualPass) {
                item.setChkRslt("1");
                // 原业务失败只作提示，来源明细按可提交保存，原因仍保存在校验快照中
                List<String> tips = new ArrayList<>();
                for (SecurityPoolExcelImportCheckItemDto ci : checkItems.subList(rowStart, checkItems.size())) {
                    if (ci.getFailReasons() != null) {
                        tips.addAll(ci.getFailReasons());
                    }
                }
                item.setChkDscr(tips.isEmpty() ? "校验通过" : "调整提示：" + String.join("；", tips));
            } else {
                List<String> failReasons = new ArrayList<>();
                for (SecurityPoolExcelImportCheckItemDto ci : checkItems.subList(rowStart, checkItems.size())) {
                    // 汇总本行手工主项的失败原因
                    if (isManualTag(ci.getItemTag()) && !ci.isCanAdjust()
                            && ci.getFailReasons() != null) {
                        failReasons.addAll(ci.getFailReasons());
                    }
                    // 失败行的展开项不单独提交，避免阻断其他通过行
                    if (ci.isCanAdjust()) {
                        ci.setCanAdjust(false);
                        ci.setFailReasons(Collections.singletonList("来源导入行未通过校验，不提交该行的联动、互斥或关联项"));
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
        }
        return new SecurityImportCheckResult(checkItems, data);
    }

    /** 本次证券校验结果及回显用的主档和候选 */
    static class SecurityImportCheckResult {
        final List<SecurityPoolExcelImportCheckItemDto> items;
        final BatchSecurityCheckData data;

        SecurityImportCheckResult(List<SecurityPoolExcelImportCheckItemDto> items, BatchSecurityCheckData data) {
            this.items = items;
            this.data = data;
        }
    }

    /** 构建证券导入或清空出库的一券一池校验项 */
    private AdjustCheckReq buildExcelSecurityCheck(String code, String shortName, InvestmentPoolBo pool, String adjustMode) {
        AdjustCheckReq request = new AdjustCheckReq();
        request.setSecurityCode(code);
        request.setSecurityShortName(shortName);
        AdjustCheckReq.CheckItem item = new AdjustCheckReq.CheckItem();
        item.setTargetPoolId(pool.getId());
        item.setTargetPoolName(pool.getPoolName());
        item.setPoolType(pool.getPoolType());
        item.setAdjustMode(adjustMode);
        request.setItems(Collections.singletonList(item));
        return request;
    }

    /** 映射来源行、目标池及入口校验结果 */
    private static class SecurityImportCheckRow {
        private final SysImpTmpDetlBo item;
        private final InvestmentPoolBo pool;
        private final AdjustCheckReq request;
        private final List<String> errors;

        private SecurityImportCheckRow(SysImpTmpDetlBo item, InvestmentPoolBo pool, AdjustCheckReq request,
                                       List<String> errors) {
            this.item = item;
            this.pool = pool;
            this.request = request;
            this.errors = errors;
        }
    }

    /** 仅为上一级未命中的行批量加载下一级条件，结果和主体资料限本次调用复用。 */
    private CompanyImportLookup loadCompanyImportLookup(List<SysImpTmpDetlBo> items) {
        CompanyImportLookup lookup = new CompanyImportLookup();
        for (int type = 0; type < 3; type++) {
            Set<String> values = new LinkedHashSet<>();
            for (SysImpTmpDetlBo item : items) {
                // 已命中或已有歧义的高优先级条件均不继续向下查询
                String[] conditions = companyLookupValues(item);
                boolean resolved = false;
                for (int earlier = 0; earlier < type; earlier++) {
                    if (!lookup.codeMatches.getOrDefault(earlier + "|" + conditions[earlier],
                            Collections.emptyList()).isEmpty()) {
                        resolved = true;
                        break;
                    }
                }
                if (!resolved && !conditions[type].isEmpty()) {
                    values.add(conditions[type]);
                }
            }
            List<String> conditions = new ArrayList<>(values);
            for (int start = 0; start < conditions.size(); start += COMPANY_QUERY_BATCH_SIZE) {
                List<String> chunk = conditions.subList(start,
                        Math.min(start + COMPANY_QUERY_BATCH_SIZE, conditions.size()));
                for (String value : chunk) {
                    lookup.codeMatches.put(type + "|" + value, new ArrayList<>());
                }
                List<CompanyImportMatchDto> matches = securityPoolExcelImportMapper.queryCompanyMatchList(type, chunk);
                for (CompanyImportMatchDto match : matches) {
                    List<String> codes = lookup.codeMatches.get(type + "|" + match.getLookupValue());
                    if (codes == null || isBlank(match.getCompanyCode())) {
                        continue;
                    }
                    if (!codes.contains(match.getCompanyCode()) && codes.size() < 2) {
                        codes.add(match.getCompanyCode());
                    }
                    ForbiddenPoolAdjustDto company = new ForbiddenPoolAdjustDto();
                    company.setCompanyCode(match.getCompanyCode());
                    company.setCompanyFullName(match.getCompanyFullName());
                    company.setCompanyShortName(match.getCompanyShortName());
                    lookup.companies.putIfAbsent(match.getCompanyCode(), company);
                }
            }
        }
        return lookup;
    }

    /** 取得规范化查询条件，数组位置对应信用代码、主体代码、主体名称。 */
    private String[] companyLookupValues(SysImpTmpDetlBo item) {
        return new String[]{trimToEmpty(item.getFld005()), trimToEmpty(item.getFld001()), trimToEmpty(item.getFld002())};
    }

    /** 单次上传或校验的主体匹配数据，不保存到服务字段。 */
    private static class CompanyImportLookup {
        /** 原条件对应的最多两个不同主体代码，用于判断歧义 */
        private final Map<String, List<String>> codeMatches = new HashMap<>();
        /** 已查询主体资料，供当前完整校验复用 */
        private final Map<String, ForbiddenPoolAdjustDto> companies = new HashMap<>();
    }

    /** 完整校验前确认的导入行和目标池 */
    private static class CompanyImportCheckRow {
        final SysImpTmpDetlBo item;
        final InvestmentPoolBo pool;
        final List<String> reasons;

        CompanyImportCheckRow(SysImpTmpDetlBo item, InvestmentPoolBo pool, List<String> reasons) {
            this.item = item;
            this.pool = pool;
            this.reasons = reasons;
        }
    }

    /** 按优先级读取批量匹配结果；未填写或未命中时才进入下一级。 */
    private void resolveCompanyCode(SysImpTmpDetlBo item, Map<String, List<String>> companyCache,
                                    List<String> reasons) {
        // 规范化各级查询条件，同批相同条件复用查询结果
        String creditCode = trimToEmpty(item.getFld005());
        String companyCode = trimToEmpty(item.getFld001());
        String companyName = trimToEmpty(item.getFld002());
        if (creditCode.isEmpty() && companyCode.isEmpty() && companyName.isEmpty()) {
            reasons.add("统一社会信用代码、主体代码和主体全称均为空");
            return;
        }
        String[] values = {creditCode, companyCode, companyName};
        String[] labels = {"统一社会信用代码", "主体代码", "主体全称"};
        for (int index = 0; index < values.length; index++) {
            String value = values[index];
            if (value.isEmpty()) {
                continue;
            }
            String key = index + "|" + value;
            List<String> codes = companyCache.getOrDefault(key, Collections.emptyList());
            if (codes.size() > 1) {
                reasons.add(labels[index] + "[" + value + "]匹配多个主体代码，请核对主体信息");
                return;
            }
            if (!codes.isEmpty()) {
                item.setFld001(codes.get(0));
                return;
            }
        }
        reasons.add("未找到主体：统一社会信用代码[" + creditCode + "]、主体代码[" + companyCode
                + "]、主体全称[" + companyName + "]均未匹配");
    }

    /**
     * 主体导入校验：编排对齐 {@link ForbiddenPoolAdjustService#checkCompanyAdjust}。
     * <p>每行 Excel 对应「一主体 + 一目标池 + 方向」→ 调用禁投池主体完整校验；
     * 未勾选联动互斥时仅保留手工项。</p>
     */
    private List<SecurityPoolExcelImportCheckItemDto> checkCompanyImport(
            List<SysImpTmpDetlBo> items, boolean inbound, boolean allowLinkMutex,
            String opterId, Date now) {
        List<SecurityPoolExcelImportCheckItemDto> checkItems = new ArrayList<>();
        Map<String, InvestmentPoolBo> poolNameCache = new HashMap<>();
        // 本次校验重新批量读取主体，复用资料不再逐行查询公司简介
        CompanyImportLookup lookup = loadCompanyImportLookup(items);
        List<CompanyImportCheckRow> prepared = new ArrayList<>();
        Map<String, ForbiddenPoolAdjustDto> companies = new LinkedHashMap<>();
        Set<Long> poolIds = new HashSet<>();
        Set<Long> permittedPools = null;
        String permissionLoadError = null;
        Set<String> seenKeys = new HashSet<>();
        String adjustMode = inbound ? AdjustMode.IN.getCode() : AdjustMode.OUT.getCode();

        for (SysImpTmpDetlBo item : items) {
            List<String> preReasons = new ArrayList<>();
            // 重新按优先级复核主体身份，匹配成功后继续完整调库校验
            resolveCompanyCode(item, lookup.codeMatches, preReasons);
            String code = trimToEmpty(item.getFld001());
            String parentName = trimToEmpty(item.getFld003());
            String childName = trimToEmpty(item.getFld004());

            if (childName.isEmpty()) {
                preReasons.add("子池名称为空");
            }
            if (!code.isEmpty() && !childName.isEmpty() && !seenKeys.add(code + "|" + childName)) {
                preReasons.add("Excel 内相同主体代码与子池重复");
            }

            InvestmentPoolBo pool = null;
            if (preReasons.isEmpty()) {
                // 按父子池名称解析叶子池
                pool = resolvePoolByNames(parentName, childName, false, poolNameCache);
                if (pool == null) {
                    preReasons.add(isBlank(parentName)
                            ? ("未找到启用叶子池：子池[" + childName + "]")
                            : ("未找到启用叶子池：父池[" + parentName + "] / 子池[" + childName + "]"));
                } else {
                    item.setFld009(String.valueOf(pool.getId()));
                    item.setFld010(pool.getPoolType());
                    if (permittedPools == null && permissionLoadError == null) {
                        try {
                            // 集中读取当前用户及角色可导入的所有投资池
                            permittedPools = queryExcelImportablePoolIds(opterId);
                        } catch (BizException e) {
                            permissionLoadError = e.getMessage();
                        }
                    }
                    if (permissionLoadError != null) {
                        preReasons.add(permissionLoadError);
                    } else if (!ADMIN_USER_ID.equals(opterId) && !permittedPools.contains(pool.getId())) {
                        preReasons.add("当前用户无权对该投资池进行 Excel 导入");
                    }
                }
            }

            prepared.add(new CompanyImportCheckRow(item, pool, preReasons));
            if (preReasons.isEmpty()) {
                companies.put(code, lookup.companies.get(code));
                poolIds.add(pool.getId());
            }
        }

        // 先确定全部有效主体和池，再统一取数，按 Excel 原顺序执行各行规则
        CompanyAdjustCheckContext checkContext = companies.isEmpty() ? null
                : batchCompanyAdjustCheckService.load(companies, poolIds, inbound);
        for (CompanyImportCheckRow row : prepared) {
            SysImpTmpDetlBo item = row.item;
            String code = trimToEmpty(item.getFld001());
            InvestmentPoolBo pool = row.pool;
            if (!row.reasons.isEmpty()) {
                markItemFail(item, row.reasons, now);
                checkItems.add(buildFailManualItem(item, code, item.getFld002(), "company", pool,
                        adjustMode, row.reasons));
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
                // 主体导入执行完整校验，业务失败作为调整提示
                checkDto = forbiddenPoolAdjustService.checkCompanyExcelAdjust(companyReq, checkContext, allowLinkMutex);
            } catch (BizException e) {
                List<String> reasons = Collections.singletonList(e.getMessage());
                // 标记导入行校验失败
                markItemFail(item, reasons, now);
                // 构建前置失败的校验结果项
                checkItems.add(buildFailManualItem(item, code, item.getFld002(), "company", pool,
                        adjustMode, reasons));
                continue;
            }

            List<AdjustCheckDto.CheckResultItem> resultItems = checkDto.getItems() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(checkDto.getItems());
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
            List<String> tips = new ArrayList<>();
            for (AdjustCheckDto.CheckResultItem ri : resultItems) {
                // 映射主体校验结果
                SecurityPoolExcelImportCheckItemDto mapped =
                        mapCompanyCheckResult(item, ri, code, pool);
                // 判断手工主项是否可调整
                if (isManualTag(mapped.getItemTag()) && mapped.isCanAdjust()) {
                    manualPass = true;
                }
                tips.addAll(mapped.getFailReasons());
                checkItems.add(mapped);
            }

            if (manualPass) {
                item.setChkRslt("1");
                item.setChkDscr(tips.isEmpty() ? "校验通过" : "调整提示：" + String.join("；", tips));
                item.setUpdtTime(now);
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
     *
     * 执行顺序：
     * 1. 读取本次提交的批次，拒绝重复提交及未校验、主体修改后尚未重新校验的批次。
     * 2. 从请求或批次快照筛选可提交项，确定经办人，并用请求中的非空原因和意见更新本次提交参数。
     * 3. 拆分清空出库项与导入项，将无来源明细 ID 且来源代码匹配清空主项的展开项一并归入清空出库。
     * 4. 先提交清空出库，再按导入类型提交主体或证券；证券按来源明细分组使用各行保存的主体选择。
     * 5. 根据已提交的 Excel 手工主项回写对应明细保存状态，更新批次快照和批次提交结果。
     * 6. 回查批次任务，附加本次汇总的调库日志 ID 和批次号列表后返回；业务异常由事务整体回滚。
     *
     * @param req 携带批次号、经办人、原因意见及可提交校验项的提交请求
     * @return 提交后的批次任务及调库记录信息
     */
    @Transactional(rollbackFor = Exception.class)
    public SecurityPoolExcelImportDto submitImport(SecurityPoolExcelImportReq req) {
        if (req == null || req.getImpId() == null || req.getImpId().trim().isEmpty()) {
            throw new BizException("导入批次号不能为空");
        }
        // 读取导入批次和已保存的校验结果
        SysImpTmpBo batch = requireBatch(req.getImpId());
        if ("1".equals(batch.getSaveRslt())) {
            throw new BizException("该批次已提交，请勿重复提交");
        }
        if ("0".equals(batch.getChkRslt()) || isBlank(batch.getResultJson())) {
            throw new BizException("请先校验，修改主体选择后需要重新校验");
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
            batch.setFld002(req.getAdjustReason().trim());
        }
        if (req.getAdjustAdvice() != null && !req.getAdjustAdvice().trim().isEmpty()) {
            batch.setFld003(req.getAdjustAdvice().trim());
        }

        // 解析导入类型（证券/主体）
        String importType = parseImportType(batch.getOptionJson(), batch.getBizType());
        List<Long> logIds = new ArrayList<>();
        List<String> batchNos = new ArrayList<>();
        // 在写入任何流程前一次复核本批在途与有效在池状态
        validateImportConflicts(checkItems);
        if (IMPORT_TYPE_COMPANY.equals(importType)) {
            // 主体分支：禁投池同构提交
            logIds.addAll(submitCompanyImport(checkItems, batch, opterId, opterName));
        } else {
            // 证券分支：证券调库同构提交
            logIds.addAll(submitSecurityImport(checkItems, batch, opterId, opterName, batchNos));
        }

        // 批量回写实际提交的 Excel 手工主项
        Date now = new Date();
        Set<Long> submittedSourceIds = new LinkedHashSet<>();
        Map<List<Object>, SecurityPoolExcelImportCheckItemDto> submittedFlows = new HashMap<>();
        for (SecurityPoolExcelImportCheckItemDto ci : checkItems) {
            if (isFlowSelectableTag(ci.getItemTag())) {
                // 记录本次所选流程，供提交响应回显
                submittedFlows.put(flowSelectionRowKey(ci), ci);
            }
            // 仅回写 Excel 手工主项对应的导入明细
            if (isManualTag(ci.getItemTag()) && ci.getSourceItemId() != null) {
                submittedSourceIds.add(ci.getSourceItemId());
            }
        }
        if (!submittedSourceIds.isEmpty()) {
            securityPoolExcelImportMapper.editItemSaveResultByIds(
                    batch.getImpId(), new ArrayList<>(submittedSourceIds), now);
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
        securityPoolExcelImportMapper.editBatchSaveResult(batch);

        // 主表转任务 DTO
        SecurityPoolExcelImportDto dto = toTaskDto(securityPoolExcelImportMapper.queryBatchByImpId(batch.getImpId()));
        for (SecurityPoolExcelImportCheckItemDto item : dto.getCheckItems()) {
            if (!item.isCanAdjust()) {
                continue;
            }
            // 只替换提交响应里对应行的流程
            SecurityPoolExcelImportCheckItemDto submitted = submittedFlows.get(flowSelectionRowKey(item));
            if (submitted != null) {
                item.setSelectedFlowKey(submitted.getSelectedFlowKey());
                item.setFlowId(submitted.getFlowId());
                item.setFlowKey(submitted.getFlowKey());
                item.setFlowType(submitted.getFlowType());
                item.setFlowName(submitted.getFlowName());
                item.setDirectFlow(submitted.isDirectFlow());
            }
        }
        dto.setAdjustBatchNoList(batchNos);
        dto.setLogIds(logIds);
        return dto;
    }

    /**
     * 证券提交：按来源导入行分组，各行独立使用已保存的评级主体。
     * 主项及其联动、互斥、关联项共用该行选择，整批复用证券调库提交步骤。
     *
     * 执行顺序：
     * 1. 查询当前批次的有效明细并按 ID 建立索引，后续主体选择读取这些持久化明细。
     * 2. 按 sourceItemId 分组校验项；缺少来源明细 ID 时拒绝提交，相同证券代码的不同导入行分别分组。
     * 3. 为每组确定主项，确认来源行已通过校验，并复核来源证券及手工主项目标池与明细一致。
     * 4. 构建该行提交请求，填写经办人、原因和意见，再次复核并应用本行保存的权益人/担保人选择。
     * 5. 逐项检查目标池和导入权限，携带各项的方向、标签、分组键及所选流程组装调库明细。
     * 6. 调用整批提交入口，先完成所有行的前置检查，再按行执行提交和快照保存，汇总调库日志 ID。
     *
     * @param checkItems 已筛选为可提交的证券导入校验项，包含主项及展开项
     * @param batch 当前导入批次
     * @param opterId 经办人 ID
     * @param opterName 经办人名称
     * @param batchNos 批次号汇总容器，当前证券分支不向其中追加批次号
     * @return 各来源导入行产生的全部调库日志 ID
     */
    private List<Long> submitSecurityImport(List<SecurityPoolExcelImportCheckItemDto> checkItems,
                                            SysImpTmpBo batch, String opterId, String opterName,
                                            List<String> batchNos) {
        Map<Long, SysImpTmpDetlBo> sourceItems = new HashMap<>();
        for (SysImpTmpDetlBo source : securityPoolExcelImportMapper.queryBatchItemList(batch.getImpId())) {
            sourceItems.put(source.getId(), source);
        }
        // 按来源明细 ID 分组，同码不同池的导入行不合并主体选择
        Map<Long, List<SecurityPoolExcelImportCheckItemDto>> groupMap = new LinkedHashMap<>();
        for (SecurityPoolExcelImportCheckItemDto ci : checkItems) {
            Long groupKey = ci.getSourceItemId();
            if (groupKey == null) {
                throw new BizException("校验结果缺少来源导入明细，请重新校验");
            }
            List<SecurityPoolExcelImportCheckItemDto> list = groupMap.get(groupKey);
            if (list == null) {
                list = new ArrayList<>();
                groupMap.put(groupKey, list);
            }
            list.add(ci);
        }

        List<SysImpTmpDetlBo> submittedRows = new ArrayList<>();
        for (Long itemId : groupMap.keySet()) {
            SysImpTmpDetlBo row = sourceItems.get(itemId);
            if (row == null || !"1".equals(row.getChkRslt())) {
                throw new BizException("导入明细未通过校验，请重新校验");
            }
            submittedRows.add(row);
        }
        List<String> codes = submittedRows.stream().map(row -> trimToEmpty(row.getFld001()))
                .filter(code -> !code.isEmpty()).distinct().collect(Collectors.toList());
        List<String> selfSelected = submittedRows.stream().map(row -> trimToEmpty(row.getFld013()))
                .filter(code -> !code.isEmpty()).distinct().collect(Collectors.toList());
        // 从正式提交事务中批量复核本次实际提交行的评级主体
        BatchSecurityCheckData ratingData = batchSecurityCheckService.loadExcelRatingCandidates(codes, selfSelected);
        Set<Long> permittedPools = new HashSet<>();
        List<SecurityPoolAdjustSubmitReq> submitRequests = new ArrayList<>();
        for (Map.Entry<Long, List<SecurityPoolExcelImportCheckItemDto>> entry : groupMap.entrySet()) {
            List<SecurityPoolExcelImportCheckItemDto> group = entry.getValue();
            // 同组优先取手工主项
            SecurityPoolExcelImportCheckItemDto primary = resolvePrimaryItem(group);
            SysImpTmpDetlBo source = sourceItems.get(entry.getKey());
            String securityCode = trimToEmpty(source.getFld001());
            for (SecurityPoolExcelImportCheckItemDto ci : group) {
                // 复核校验结果的来源证券，主体选择只读取本批持久化明细
                if (!securityCode.equals(trimToEmpty(ci.getSourceSecurityCode()))
                        || (isManualTag(ci.getItemTag())
                        && (!securityCode.equals(trimToEmpty(ci.getSecurityCode()))
                        || !String.valueOf(ci.getTargetPoolId()).equals(source.getFld009())))) {
                    throw new BizException("校验结果与来源导入明细不一致，请重新校验");
                }
            }

            SecurityPoolAdjustSubmitReq submitReq = new SecurityPoolAdjustSubmitReq();
            submitReq.setSecurityCode(securityCode);
            submitReq.setSecurityShortName(primary.getSecurityShortName());
            submitReq.setSecurityType(primary.getSecurityType());
            submitReq.setAdjustType(ADJUST_TYPE_EXCEL);
            submitReq.setAdjustReason(batch.getFld002());
            submitReq.setAdjustAdvice(batch.getFld003());
            submitReq.setAdjusterId(opterId);
            submitReq.setAdjusterName(opterName);
            // 使用临时明细中已校验的主体选择，提交前再次复核关联或自选主体
            applyImportedRatingCompany(submitReq, source, ratingData);

            List<SecurityPoolAdjustSubmitReq.AdjustItem> submitItems = new ArrayList<>();
            for (SecurityPoolExcelImportCheckItemDto ci : group) {
                if (ci.getTargetPoolId() == null) {
                    throw new BizException("校验结果数据不完整，请重新校验");
                }
                // 同池权限在本次提交只需要复核一次
                if (permittedPools.add(ci.getTargetPoolId())) {
                    validateExcelImportPermission(opterId, ci.getTargetPoolId());
                }

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
                si.setMaterialExemptionType(ci.getMaterialExemptionType());
                si.setAdjustmentNote(ci.getAdjustNote());
                submitItems.add(si);
            }
            // 复用本次校验后传入的实际代码集合，提交阶段不再查询同券关系
            submitReq.setRelatedMarketCodes(group.stream().map(item -> item.getSecurityCode())
                    .filter(code -> code != null && !code.equals(submitReq.getSecurityCode())).distinct()
                    .map(code -> {
                        SecurityMarketDto related = new SecurityMarketDto();
                        related.setWindCode(code);
                        return related;
                    }).collect(Collectors.toList()));
            submitReq.setItems(submitItems);
            submitRequests.add(submitReq);
        }
        // 复用本次提交已取得的评级主体和证券主档，直通项批量落库
        List<AdjustSubmitDto> submitResults = batchSecurityAdjustSubmitService.submit(submitRequests, ratingData, false);
        List<Long> allLogIds = new ArrayList<>();
        for (AdjustSubmitDto submitDto : submitResults) {
            if (submitDto != null && submitDto.getLogIds() != null) {
                allLogIds.addAll(submitDto.getLogIds());
            }
        }
        return allLogIds;
    }

    /**
     * 主体提交：按主体代码分组，逐组调用 Excel 提示放行入口。
     */
    private List<Long> submitCompanyImport(List<SecurityPoolExcelImportCheckItemDto> checkItems,
                                           SysImpTmpBo batch, String opterId, String opterName) {
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

        List<ForbiddenPoolAdjustSubmitReq> requests = new ArrayList<>();
        Set<Long> permittedPools = queryExcelImportablePoolIds(opterId);
        for (Map.Entry<String, List<SecurityPoolExcelImportCheckItemDto>> entry : groupMap.entrySet()) {
            List<SecurityPoolExcelImportCheckItemDto> group = entry.getValue();
            // 同组优先取手工主项
            SecurityPoolExcelImportCheckItemDto primary = resolvePrimaryItem(group);

            ForbiddenPoolAdjustSubmitReq submitReq = new ForbiddenPoolAdjustSubmitReq();
            submitReq.setCompanyCode(entry.getKey());
            submitReq.setCompanyShortName(primary.getSecurityShortName());
            submitReq.setSecurityType("company");
            submitReq.setAdjustType(ADJUST_TYPE_EXCEL);
            submitReq.setAdjustReason(batch.getFld002());
            submitReq.setAdjustAdvice(batch.getFld003());
            submitReq.setAdjusterId(opterId);
            submitReq.setAdjusterName(opterName);

            List<ForbiddenPoolAdjustSubmitReq.AdjustItem> submitItems = new ArrayList<>();
            for (SecurityPoolExcelImportCheckItemDto ci : group) {
                if (ci.getTargetPoolId() == null) {
                    throw new BizException("校验结果数据不完整，请重新校验");
                }
                // 使用本次读取的权限校验目标池
                validateExcelImportPermission(opterId, ci.getTargetPoolId(), permittedPools);

                ForbiddenPoolAdjustSubmitReq.AdjustItem si = new ForbiddenPoolAdjustSubmitReq.AdjustItem();
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

            requests.add(submitReq);
        }
        // 主体导入按本次选择的流程提交，原业务失败已在校验结果中提示
        return batchCompanyAdjustSubmitService.submit(requests);
    }

    // ═══════════════════════════════════════════════════════════
    //  清空目标池（独立分支，与证券/主体并列）
    // ═══════════════════════════════════════════════════════════

    /** 在同一批写入之前复核所有实际对象的在途和重复调入 */
    private void validateImportConflicts(List<SecurityPoolExcelImportCheckItemDto> items) {
        List<String> codes = items.stream().map(SecurityPoolExcelImportCheckItemDto::getSecurityCode)
                .distinct().collect(Collectors.toList());
        List<Long> targetPoolIds = items.stream().map(SecurityPoolExcelImportCheckItemDto::getTargetPoolId)
                .distinct().sorted().collect(Collectors.toList());
        // 和终审使用同一池锁，避免两批并发调入同一对象
        investmentPoolMapper.lockPoolByIdsList(targetPoolIds);
        Map<Long, InvestmentPoolBo> pools = investmentPoolMapper.queryPoolList().stream()
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool));
        Map<String, Set<Long>> pendingGroups = new HashMap<>();
        Map<String, Set<Long>> activePools = new HashMap<>();
        int batchSize = AutoAdjustRestrictHelper.QUERY_BATCH_SIZE;
        for (int start = 0; start < codes.size(); start += batchSize) {
            List<String> chunk = codes.subList(start, Math.min(start + batchSize, codes.size()));
            for (BatchSecurityCheckData.Row row : batchSecurityCheckMapper.queryPendingPools(chunk)) {
                pendingGroups.computeIfAbsent(row.getSecurityCode().toUpperCase(Locale.ROOT), key -> new HashSet<>())
                        .add(SecurityPoolCheckRules.resolveRootPoolId(row.getPoolId(), pools));
            }
            for (BatchSecurityCheckData.Row row : batchSecurityCheckMapper.queryActiveStates(chunk)) {
                activePools.computeIfAbsent(row.getSecurityCode().toUpperCase(Locale.ROOT), key -> new HashSet<>())
                        .add(row.getPoolId());
            }
        }
        Map<String, Set<Long>> inbound = new HashMap<>();
        for (SecurityPoolExcelImportCheckItemDto item : items) {
            String code = item.getSecurityCode().toUpperCase(Locale.ROOT);
            Long poolId = item.getTargetPoolId();
            if (pendingGroups.getOrDefault(code, Collections.emptySet())
                    .contains(SecurityPoolCheckRules.resolveRootPoolId(poolId, pools))) {
                throw new BizException(item.getSecurityCode() + " / " + item.getTargetPoolName()
                        + "：" + SecurityPoolCheckRules.PENDING_PROCESS_FAILURE + "，请重新校验");
            }
            if (AdjustMode.IN.getCode().equals(item.getAdjustMode())
                    && (activePools.getOrDefault(code, Collections.emptySet()).contains(poolId)
                    || !inbound.computeIfAbsent(code, key -> new HashSet<>()).add(poolId))) {
                throw new BizException(item.getSecurityCode() + " / " + item.getTargetPoolName()
                        + "：" + SecurityPoolCheckRules.ALREADY_IN_POOL_FAILURE + "或本批重复调入，请重新校验");
            }
        }
    }

    /**
     * 查询有 Excel 导入权限的债券业务叶子池及当前成员数量
     *
     * @param req 当前操作人
     * @return 可清空池列表
     */
    public List<PoolClearSummaryDto> queryClearPoolList(SecurityPoolExcelImportReq req) {
        Map<Long, InvestmentPoolBo> pools = queryClearablePools(req.getCurrentUserId());
        return pools.isEmpty() ? Collections.emptyList()
                : securityPoolExcelImportMapper.queryClearPoolSummaryList(new ArrayList<>(pools.keySet()));
    }

    /**
     * 按单笔出库规则校验所选池的全部有效成员，全部通过后直通生效
     *
     * @param req 所选叶子池和操作人
     * @return 已生效的调库日志 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> clearPools(SecurityPoolExcelImportReq req) {
        if (req.getPoolIds() == null || req.getPoolIds().isEmpty()) {
            throw new BizException("请选择需要清空的投资池");
        }
        Map<Long, InvestmentPoolBo> poolMap = queryClearablePools(req.getCurrentUserId());
        List<Long> poolIds = new ArrayList<>(new LinkedHashSet<>(req.getPoolIds()));
        if (!poolMap.keySet().containsAll(poolIds)) {
            throw new BizException("所选投资池不存在、已停用或无操作权限");
        }
        Collections.sort(poolIds);
        // 锁定所选池后再批量读取本次要处理的成员
        investmentPoolMapper.lockPoolByIdsList(poolIds);
        List<SecurityPoolExcelImportCheckItemDto> items = checkClearTargetOutbound(poolIds, poolMap);
        // 独立清空沿用单笔出库的报告要求，批量核对已有报告豁免
        checkClearReportRequirements(items);
        List<String> errors = items.stream().filter(item -> !item.isCanAdjust())
                .map(item -> item.getTargetPoolName() + " / " + item.getSecurityCode() + "："
                        + String.join("、", item.getFailReasons() == null
                                ? Collections.singletonList("校验不通过") : item.getFailReasons()))
                .distinct().collect(Collectors.toList());
        if (!errors.isEmpty()) {
            throw new BizException("清空目标池失败（" + errors.size() + " 项）："
                    + String.join("；", errors.subList(0, Math.min(errors.size(), 10))));
        }

        // 同一实际对象和池只执行一次，优先保留直接清空主项
        Map<List<Object>, SecurityPoolExcelImportCheckItemDto> distinct = new LinkedHashMap<>();
        for (SecurityPoolExcelImportCheckItemDto item : items) {
            List<Object> key = Arrays.asList(IMPORT_TYPE_COMPANY.equals(item.getSecurityType()),
                    item.getSecurityCode().toUpperCase(Locale.ROOT), item.getTargetPoolId(), item.getAdjustMode());
            SecurityPoolExcelImportCheckItemDto previous = distinct.putIfAbsent(key, item);
            if (previous != null && isClearTag(item.getItemTag())) {
                distinct.put(key, item);
            }
        }
        String userId = req.getCurrentUserId().trim();
        String userName = isBlank(req.getCurrentUserName()) ? userId : req.getCurrentUserName().trim();
        return submitClearTargetOutbound(new ArrayList<>(distinct.values()), userId, userName);
    }

    /** 按单笔出库口径核对报告必填以及六个月历史报告豁免 */
    private void checkClearReportRequirements(List<SecurityPoolExcelImportCheckItemDto> items) {
        List<SecurityPoolExcelImportCheckItemDto> outbound = items.stream()
                .filter(item -> item.isCanAdjust() && AdjustMode.OUT.getCode().equals(item.getAdjustMode()))
                .filter(item -> !ItemType.RELATED.getCode().equals(item.getItemTag()))
                .collect(Collectors.toList());
        if (outbound.isEmpty()) {
            return;
        }
        List<Long> poolIds = outbound.stream().map(SecurityPoolExcelImportCheckItemDto::getTargetPoolId)
                .distinct().collect(Collectors.toList());
        Set<Long> reportPools = investmentPoolMapper.queryPoolByIdsList(poolIds).stream()
                .filter(pool -> !isBlank(pool.getOutReportRestriction()) && !"none".equals(pool.getOutReportRestriction()))
                .map(InvestmentPoolBo::getId).collect(Collectors.toSet());
        List<SecurityPoolExcelImportCheckItemDto> requiresReport = outbound.stream()
                .filter(item -> reportPools.contains(item.getTargetPoolId())).collect(Collectors.toList());
        List<String> codes = requiresReport.stream().map(SecurityPoolExcelImportCheckItemDto::getSecurityCode)
                .distinct().collect(Collectors.toList());
        Set<String> exemptCodes = new HashSet<>();
        int batchSize = AutoAdjustRestrictHelper.QUERY_BATCH_SIZE;
        for (int start = 0; start < codes.size(); start += batchSize) {
            List<String> chunk = codes.subList(start, Math.min(start + batchSize, codes.size()));
            securityPoolExcelImportMapper.queryClearReportExemptCodes(chunk)
                    .forEach(code -> exemptCodes.add(code.toUpperCase(Locale.ROOT)));
        }
        for (SecurityPoolExcelImportCheckItemDto item : requiresReport) {
            if (!exemptCodes.contains(item.getSecurityCode().toUpperCase(Locale.ROOT))) {
                item.setCanAdjust(false);
                item.setFailReasons(Collections.singletonList("需提供调出研究报告，请通过单笔调库处理"));
            }
        }
    }

    /** 查询启用且有权限的债券业务叶子池 */
    private Map<Long, InvestmentPoolBo> queryClearablePools(String currentUserId) {
        Set<Long> permittedPools = queryExcelImportablePoolIds(currentUserId);
        List<InvestmentPoolBo> pools = investmentPoolMapper.queryPoolList();
        Set<Long> parentIds = pools.stream().map(InvestmentPoolBo::getParentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return pools.stream().filter(pool -> "enabled".equals(pool.getStatus()))
                .filter(pool -> !parentIds.contains(pool.getId()))
                .filter(pool -> pool.getVarietyCodes() != null
                        && (pool.getVarietyCodes().contains("\"bond\"")
                        || pool.getVarietyCodes().contains("\"company\"")))
                .filter(pool -> ADMIN_USER_ID.equals(currentUserId.trim()) || permittedPools.contains(pool.getId()))
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool));
    }

    /** 批量加载清空成员和所需的单笔出库校验数据 */
    private List<SecurityPoolExcelImportCheckItemDto> checkClearTargetOutbound(
            List<Long> clearPoolIds, Map<Long, InvestmentPoolBo> poolMap) {
        List<SecurityPoolExcelImportCheckItemDto> clearItems = new ArrayList<>();
        Map<Long, List<PoolMemberDto>> membersByPool = securityPoolExcelImportMapper.queryPoolMemberList(clearPoolIds)
                .stream().collect(Collectors.groupingBy(PoolMemberDto::getTargetPoolId));
        Set<String> clearCompanyCodes = new LinkedHashSet<>();
        Set<Long> clearCompanyPools = new HashSet<>();
        Map<Long, List<PoolMemberDto>> clearMembers = new LinkedHashMap<>();
        List<AdjustCheckReq> securityRequests = new ArrayList<>();
        for (Long poolId : clearPoolIds) {
            InvestmentPoolBo pool = poolMap.get(poolId);
            for (PoolMemberDto member : membersByPool.getOrDefault(poolId, Collections.emptyList())) {
                if (isBlank(member.getSecurityCode())) {
                    throw new BizException("投资池[" + pool.getPoolName() + "]存在缺少代码的在池成员");
                }
                String code = member.getSecurityCode().trim();
                boolean companyMember = IMPORT_TYPE_COMPANY.equals(member.getSecurityType());
                clearMembers.computeIfAbsent(poolId, key -> new ArrayList<>()).add(member);
                if (companyMember) {
                    clearCompanyCodes.add(code);
                    clearCompanyPools.add(poolId);
                } else {
                    securityRequests.add(buildExcelSecurityCheck(code, member.getSecurityShortName(),
                            pool, AdjustMode.OUT.getCode()));
                }
            }
        }

        // 按对象类型批量加载清空成员所需数据
        CompanyAdjustCheckContext companyData = clearCompanyCodes.isEmpty() ? null
                : batchCompanyAdjustCheckService.loadClear(new ArrayList<>(clearCompanyCodes), clearCompanyPools);
        BatchSecurityCheckData data = batchSecurityCheckService.loadExcelCheckData(securityRequests, true);
        int securityIndex = 0;
        for (Map.Entry<Long, List<PoolMemberDto>> entry : clearMembers.entrySet()) {
            InvestmentPoolBo pool = poolMap.get(entry.getKey());
            for (PoolMemberDto member : entry.getValue()) {
                AdjustCheckReq request = IMPORT_TYPE_COMPANY.equals(member.getSecurityType())
                        ? null : securityRequests.get(securityIndex++);
                // 按差集原顺序映射各成员的出库校验结果
                clearItems.addAll(buildClearOutboundCheckItems(member, pool, request, data, companyData));
            }
        }
        return clearItems;
    }

    /**
     * 对单个清空出库成员执行调出校验并映射为清空校验结果
     */
    private List<SecurityPoolExcelImportCheckItemDto> buildClearOutboundCheckItems(
            PoolMemberDto member, InvestmentPoolBo pool, AdjustCheckReq request,
            BatchSecurityCheckData data, CompanyAdjustCheckContext companyData) {
        boolean companyMember = request == null;
        List<SecurityPoolExcelImportCheckItemDto> result = new ArrayList<>();
        String code = member.getSecurityCode().trim();
        String shortName = member.getSecurityShortName();
        String securityType = member.getSecurityType();
        String adjustMode = AdjustMode.OUT.getCode();

        AdjustCheckDto checkDto;
        try {
            if (companyMember) {
                // 主体清空执行单笔主体调出校验
                ForbiddenPoolAdjustCheckReq companyReq = new ForbiddenPoolAdjustCheckReq();
                companyReq.setCompanyCode(code);
                companyReq.setCompanyShortName(shortName);
                ForbiddenPoolAdjustCheckReq.CheckItem cItem = new ForbiddenPoolAdjustCheckReq.CheckItem();
                cItem.setTargetPoolId(pool.getId());
                cItem.setTargetPoolName(pool.getPoolName());
                cItem.setPoolType(pool.getPoolType());
                cItem.setAdjustMode(adjustMode);
                companyReq.setItems(Collections.singletonList(cItem));
                checkDto = forbiddenPoolAdjustService.checkCompanyAdjust(companyReq, companyData);
            } else {
                // 证券清空使用当前有效主体执行单笔调出校验
                applyClearRatingCompany(request, data);
                checkDto = batchSecurityCheckService.checkClearItem(request, data);
            }
        } catch (BizException e) {
            // 构建清空出库失败项
            result.add(buildClearFailItem(code, shortName, securityType, pool, e.getMessage()));
            return result;
        }

        List<AdjustCheckDto.CheckResultItem> resultItems =
                checkDto.getItems() == null ? new ArrayList<>() : checkDto.getItems();
        if (resultItems.isEmpty()) {
            // 构建清空出库失败项
            result.add(buildClearFailItem(code, shortName, securityType, pool, "清空出库校验无结果"));
            return result;
        }
        for (AdjustCheckDto.CheckResultItem ri : resultItems) {
            SecurityPoolExcelImportCheckItemDto mapped;
            if (companyMember) {
                // 映射主体校验结果
                mapped = mapCompanyCheckResult(null, ri, code, pool);
            } else {
                // 映射证券校验结果并注入批量流程
                mapped = mapSecurityCheckResult(null, ri, code, pool, "out");
            }
            if (isBlank(mapped.getTargetPoolName()) && Objects.equals(mapped.getTargetPoolId(), pool.getId())) {
                mapped.setTargetPoolName(pool.getPoolName());
            }
            // 主项标记为清空；联动/互斥保留原标签（调整类型展示/落库为清空目标池）
            if (isManualTag(mapped.getItemTag())) {
                mapped.setItemTag(ItemType.CLEAR.getCode());
                mapped.setAdjustType(AdjustType.CLEAR_POOL.getCode());
                if (mapped.isCanAdjust()) {
                    mapped.setAdjustNote("清空目标池");
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
        dto.setAdjustType(AdjustType.CLEAR_POOL.getCode());
        dto.setAdjustGroupKey(code + "_" + (pool != null ? pool.getId() : "0") + "_" + AdjustType.CLEAR_POOL.getCode());
        dto.setCanAdjust(false);
        List<String> reasons = new ArrayList<>();
        reasons.add(reason == null ? "清空出库失败" : reason);
        dto.setFailReasons(reasons);
        return dto;
    }

    /**
     * 按来源对象类型和代码批量提交清空出库，统一使用无需审核审批流程
     *
     * @param clearItems 本批可提交的清空及配套调整项
     * @param opterId 经办人 ID
     * @param opterName 经办人姓名
     * @return 写入的调库日志 ID
     */
    private List<Long> submitClearTargetOutbound(List<SecurityPoolExcelImportCheckItemDto> clearItems,
                                                 String opterId, String opterName) {
        if (clearItems == null || clearItems.isEmpty()) {
            return new ArrayList<>();
        }
        // 使用已发布的固定流程并确认可以直接完成
        FlowDefinitionBo clearFlow = flowMapper.queryActiveFlowByKey(CLEAR_POOL_FLOW_KEY);
        if (clearFlow == null || clearFlow.getId() == null
                || !securityPoolAdjustService.isDirectAdjustFlow(clearFlow.getId(), CLEAR_POOL_FLOW_KEY)) {
            throw new BizException("清空目标池需要已发布且可直通的「无需审核审批流程」，请检查流程配置");
        }

        // 按对象类型和来源代码分组，证券关联项跟随主券提交
        Map<List<Object>, List<SecurityPoolExcelImportCheckItemDto>> groupMap = new LinkedHashMap<>();
        for (SecurityPoolExcelImportCheckItemDto ci : clearItems) {
            String sourceCode = !isBlank(ci.getSourceSecurityCode())
                    ? ci.getSourceSecurityCode() : ci.getSecurityCode();
            boolean companyMember = IMPORT_TYPE_COMPANY.equals(ci.getSecurityType());
            groupMap.computeIfAbsent(Arrays.asList(companyMember, sourceCode), key -> new ArrayList<>()).add(ci);
        }
        List<String> securityCodes = groupMap.keySet().stream().filter(key -> Boolean.FALSE.equals(key.get(0)))
                .map(key -> (String) key.get(1)).filter(code -> !isBlank(code)).distinct().collect(Collectors.toList());
        // 清空组共用本次查出的主档和关联候选
        BatchSecurityCheckData ratingData = securityCodes.isEmpty() ? new BatchSecurityCheckData()
                : batchSecurityCheckService.loadExcelRatingCandidates(securityCodes, Collections.emptyList());
        List<String> absCodes = ratingData.securities.values().stream().filter(CreditBondSpecialInboundRule::isAbs)
                .map(SecurityInfoBo::getWindCode).collect(Collectors.toList());
        Map<String, SecurityInfoBo> identities = absCodes.isEmpty() ? Collections.emptyMap()
                : absIdentityService.queryCurrentIdentities(absCodes);
        Set<Long> permittedPools = queryExcelImportablePoolIds(opterId);
        List<SecurityPoolAdjustSubmitReq> securityRequests = new ArrayList<>();
        List<ForbiddenPoolAdjustSubmitReq> companyRequests = new ArrayList<>();
        List<Long> allLogIds = new ArrayList<>();
        for (List<SecurityPoolExcelImportCheckItemDto> group : groupMap.values()) {
            // 同组优先取清空/手工主项
            SecurityPoolExcelImportCheckItemDto primary = resolvePrimaryItem(group);
            String sourceCode = !isBlank(primary.getSourceSecurityCode())
                    ? primary.getSourceSecurityCode() : primary.getSecurityCode();
            if (IMPORT_TYPE_COMPANY.equals(primary.getSecurityType())) {
                // 主体清空出库提交
                ForbiddenPoolAdjustSubmitReq submitReq = new ForbiddenPoolAdjustSubmitReq();
                submitReq.setCompanyCode(sourceCode);
                submitReq.setCompanyShortName(primary.getSecurityShortName());
                submitReq.setSecurityType("company");
                submitReq.setAdjustType(AdjustType.CLEAR_POOL.getCode());
                submitReq.setAdjustReason("清空目标池");
                submitReq.setAdjusterId(opterId);
                submitReq.setAdjusterName(opterName);
                List<ForbiddenPoolAdjustSubmitReq.AdjustItem> submitItems = new ArrayList<>();
                for (SecurityPoolExcelImportCheckItemDto ci : group) {
                    // 使用本次读取的权限校验目标池
                    validateExcelImportPermission(opterId, ci.getTargetPoolId(), permittedPools);
                    ForbiddenPoolAdjustSubmitReq.AdjustItem si = new ForbiddenPoolAdjustSubmitReq.AdjustItem();
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
                    si.setFlowId(clearFlow.getId());
                    si.setFlowKey(CLEAR_POOL_FLOW_KEY);
                    si.setFlowType(FlowType.NORMAL_OUTBOUND.getCode());
                    si.setAdjustmentNote(!isBlank(ci.getAdjustNote()) ? ci.getAdjustNote() : "清空目标池");
                    submitItems.add(si);
                }
                submitReq.setItems(submitItems);
                companyRequests.add(submitReq);
            } else {
                // 证券清空出库提交
                SecurityPoolAdjustSubmitReq submitReq = new SecurityPoolAdjustSubmitReq();
                submitReq.setSecurityCode(sourceCode);
                submitReq.setSecurityShortName(primary.getSecurityShortName());
                submitReq.setSecurityType(primary.getSecurityType());
                submitReq.setAdjustType(AdjustType.CLEAR_POOL.getCode());
                submitReq.setAdjustReason("清空目标池");
                submitReq.setAdjusterId(opterId);
                submitReq.setAdjusterName(opterName);
                // 清空出库继续优先使用 ABS 已占用主体，普通债默认采用首个候选
                applyDefaultRatingCompany(submitReq, ratingData, identities);
                List<SecurityPoolAdjustSubmitReq.AdjustItem> submitItems = new ArrayList<>();
                for (SecurityPoolExcelImportCheckItemDto ci : group) {
                    // 使用本次读取的权限校验目标池
                    validateExcelImportPermission(opterId, ci.getTargetPoolId(), permittedPools);
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
                    si.setFlowId(clearFlow.getId());
                    si.setFlowKey(CLEAR_POOL_FLOW_KEY);
                    si.setFlowType(FlowType.NORMAL_OUTBOUND.getCode());
                    si.setAdjustmentNote(!isBlank(ci.getAdjustNote()) ? ci.getAdjustNote() : "清空目标池");
                    submitItems.add(si);
                }
                submitReq.setItems(submitItems);
                securityRequests.add(submitReq);
            }
        }
        if (!companyRequests.isEmpty()) {
            // 批量提交主体清空项并直通生效
            allLogIds.addAll(batchCompanyAdjustSubmitService.submit(companyRequests));
        }
        if (!securityRequests.isEmpty()) {
            // 自选权益人的评级也按实际选中的代码集中读取
            List<String> selfSelected = securityRequests.stream()
                    .map(SecurityPoolAdjustSubmitReq::getSelfSelectedRightsHolderCode).filter(code -> !isBlank(code))
                    .distinct().collect(Collectors.toList());
            batchSecurityCheckService.loadRatingCandidates(ratingData, Collections.emptyList(), selfSelected);
            for (AdjustSubmitDto result : batchSecurityAdjustSubmitService.submit(securityRequests, ratingData, true)) {
                if (result != null && result.getLogIds() != null) {
                    allLogIds.addAll(result.getLogIds());
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
     *
     * 执行顺序：
     * 1. 存在来源导入行时填写明细 ID、行号及有效主体信息，ABS 自选权益人优先于关联主体展示。
     * 2. 映射证券、来源证券、目标池、方向和项目标签，缺少证券代码时使用本次校验的来源证券代码。
     * 3. 用来源证券代码、来源明细 ID 和原校验分组键构造新分组键，区分同码不同导入行；清空出库项
     *    无来源明细，因此保留不含明细 ID 的分组形式。
     * 4. 复制可调整状态和失败原因，生成调整类型及可调整项的调整说明。
     * 5. 映射原有流程候选，对可调整手工项补充目标池批量调入/调出流程，并默认选择推荐或首个可选流程。
     *
     * @param sourceItem 来源导入行，自动生成的清空出库项为 null
     * @param ri 完整单券校验返回的一条结果
     * @param excelCode 本次校验的来源证券代码，作为缺失代码的回填值
     * @param excelPool 本次校验的目标池，用于注入批量流程候选
     * @param direction 调整方向，in 表示调入，out 表示调出
     * @return 带来源行、主体展示信息、独立分组键及流程候选的导入校验结果
     */
    private SecurityPoolExcelImportCheckItemDto mapSecurityCheckResult(
            SysImpTmpDetlBo sourceItem, AdjustCheckDto.CheckResultItem ri,
            String excelCode, InvestmentPoolBo excelPool, String direction) {
        SecurityPoolExcelImportCheckItemDto dto = new SecurityPoolExcelImportCheckItemDto();
        if (sourceItem != null) {
            dto.setSourceItemId(sourceItem.getId());
            dto.setSourceRowNo(sourceItem.getRowNo());
            dto.setRatingCompanyCode(isBlank(sourceItem.getFld013())
                    ? sourceItem.getFld011() : sourceItem.getFld013());
            dto.setRatingCompanyName(isBlank(sourceItem.getFld013())
                    ? sourceItem.getFld012() : sourceItem.getFld014());
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
        // 来源行独立分组，联动、互斥和关联项沿用该行的分组键
        String gk = ri.getAdjustGroupKey();
        dto.setAdjustGroupKey(sourceCode + "_" + (sourceItem == null ? "" : sourceItem.getId() + "_")
                + (gk == null ? "" : gk));
        dto.setCanAdjust(ri.isCanAdjust());
        dto.setMaterialExemptionType(ri.getMaterialExemptionType());
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
     * 映射主体校验结果，保留单笔候选并追加目标池配置的批量流程
     */
    private SecurityPoolExcelImportCheckItemDto mapCompanyCheckResult(
            SysImpTmpDetlBo sourceItem, AdjustCheckDto.CheckResultItem ri,
            String excelCode, InvestmentPoolBo pool) {
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
            List<String> warnings = ri.getWarnings();
            if (ItemType.LINKAGE.getCode().equals(dto.getItemTag()) && warnings != null && !warnings.isEmpty()) {
                dto.setAdjustNote(String.join("；", warnings));
            } else {
                dto.setAdjustNote(dto.getAdjustType() + (dto.getAdjustMode() == null ? "" : dto.getAdjustMode()));
            }
        }

        List<SecurityPoolExcelImportCheckItemDto.FlowOptionDto> flowOptions = new ArrayList<>();
        if (ri.getFlowOptions() != null) {
            for (AdjustCheckDto.FlowOption fo : ri.getFlowOptions()) {
                // 映射单条流程候选项
                flowOptions.add(toFlowOptionDto(fo));
            }
        }
        if (dto.isCanAdjust() && isManualTag(dto.getItemTag())) {
            boolean inbound = AdjustMode.IN.getCode().equals(dto.getAdjustMode());
            // 追加目标池对应方向的批量流程候选
            injectBatchFlowOption(flowOptions, pool, inbound ? "in" : "out");
        }
        dto.setFlowOptions(flowOptions);
        // 默认选中推荐/首个可选流程
        applyDefaultSelectedFlow(dto);
        return dto;
    }

    /**
     * 转换导入页候选后共用批量流程追加方法，保留单笔快速候选及豁免信息
     *
     * 执行顺序：
     * 1. 将导入页候选转换为单笔候选类型
     * 2. 用已加载目标池配置调用公共追加方法，不查询数据库
     * 3. 转回导入页类型，保持页面唯一键和默认选中处理
     *
     * @param flowOptions 单笔已经匹配的导入页流程候选
     * @param pool 已加载的目标池
     * @param direction 调整方向，in 或 out
     */
    private void injectBatchFlowOption(List<SecurityPoolExcelImportCheckItemDto.FlowOptionDto> flowOptions,
                                       InvestmentPoolBo pool, String direction) {
        List<AdjustCheckDto.FlowOption> original = new ArrayList<>();
        for (SecurityPoolExcelImportCheckItemDto.FlowOptionDto option : flowOptions) {
            AdjustCheckDto.FlowOption common = new AdjustCheckDto.FlowOption();
            BeanUtils.copyProperties(option, common);
            original.add(common);
        }
        String adjustMode = "out".equals(direction) ? AdjustMode.OUT.getCode() : AdjustMode.IN.getCode();
        List<AdjustCheckDto.FlowOption> combined = BatchAdjustFlowOptionHelper.appendConfiguredBatchFlow(
                original, pool, adjustMode);
        flowOptions.clear();
        for (AdjustCheckDto.FlowOption option : combined) {
            // 复用导入页映射，透传材料豁免并重建唯一键
            flowOptions.add(toFlowOptionDto(option));
        }
    }

    /**
     * 从已加载的占用状态恢复清空出库的评级主体
     *
     * <p>清空出库成员使用已确认主体，只有无占用的 ABS 使用首个权益人。
     * Excel 导入行使用页面选择的主体</p>
     *
     * @param request 清空证券的校验请求
     * @param data 已加载的批次数据
     */
    private void applyClearRatingCompany(AdjustCheckReq request, BatchSecurityCheckData data) {
        String code = request.getSecurityCode();
        if (!CreditBondSpecialInboundRule.isAbs(data.securities.get(code))) {
            return;
        }
        Set<String> occupied = data.absIdentities.getOrDefault(code, Collections.emptyList()).stream()
                .map(Row::getCompanyCode).filter(value -> !isBlank(value))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (occupied.size() > 1) {
            throw new BizException("ABS[" + code + "]在库或在途主体身份不一致，请先处理冲突记录");
        }
        List<RelatedRatingCompanyDto> related = data.ratingCompanies.getOrDefault(code, Collections.emptyList());
        if (occupied.isEmpty()) {
            related.stream().findFirst().map(RelatedRatingCompanyDto::getCompanyCode)
                    .ifPresent(request::setRightsHolderCode);
            return;
        }
        String company = occupied.iterator().next();
        if (related.stream().anyMatch(row -> company.equals(row.getCompanyCode()))) {
            request.setRightsHolderCode(company);
        } else {
            request.setSelfSelectedRightsHolderCode(company);
        }
    }

    /**
     * 用本次批量读取的候选确定清空出库的默认评级主体
     *
     * @param req 当前证券的清空出库请求
     * @param data 本次读取的主档和候选
     * @param identities ABS 当前确认或在途占用的主体
     */
    private void applyDefaultRatingCompany(SecurityPoolAdjustSubmitReq req, BatchSecurityCheckData data,
                                            Map<String, SecurityInfoBo> identities) {
        String code = data.resolveExcelSecurityCode(req.getSecurityCode());
        SecurityInfoBo master = data.securities.get(code);
        if (master == null) {
            return;
        }
        List<RelatedRatingCompanyDto> candidates = data.ratingCompanies.getOrDefault(code, Collections.emptyList());
        String first = candidates.isEmpty() ? null : trimToNull(candidates.get(0).getCompanyCode());
        if (!CreditBondSpecialInboundRule.isAbs(master)) {
            return;
        }
        SecurityInfoBo identity = identities.get(code);
        if (identity == null) {
            req.setRightsHolderCode(first);
        } else if (candidates.stream().anyMatch(row -> identity.getIssuerCode().equals(row.getCompanyCode()))) {
            req.setRightsHolderCode(identity.getIssuerCode());
        } else {
            req.setSelfSelectedRightsHolderCode(identity.getIssuerCode());
        }
    }

    /** Excel 导入使用的评级主体。 */
    private static class RatingCompany {
        /** 是否 ABS 证券。 */
        private boolean abs;
        /** 评级主体编码。 */
        private String companyCode;
        /** 页面选择的关联担保人或权益人编码。 */
        private String relatedCompanyCode;
        /** 页面选择的 ABS 自选权益人编码。 */
        private String selfSelectedRightsHolderCode;
        /** 本行已读取的证券信息 */
        private SecurityInfoBo securityInfo;
        /** 本行已经取得的主体名称与评级 */
        private RatingCompanySelectionReq selection;
    }

    /** 根据批量候选复核本行选择，并回填服务端标准名称 */
    private void applyImportedRatingCompany(AdjustCheckReq req, SysImpTmpDetlBo item, BatchSecurityCheckData data) {
        String code = req.getSecurityCode();
        // 使用本批已读的证券和候选执行与提交相同的主体校验
        RatingCompany company = resolveImportedRatingCompany(item, true, data.securities.get(code),
                data.ratingCompanies.getOrDefault(code, Collections.emptyList()),
                () -> data.selfSelectedCompanies.get(trimToEmpty(item.getFld013())));
        if (company == null) {
            return;
        }
        req.setSecurityInfo(company.securityInfo);
        req.setSelectedRatingCompany(company.selection);
        if (company.abs) {
            req.setGuarantorCode(null);
            req.setRightsHolderCode(company.relatedCompanyCode);
            req.setSelfSelectedRightsHolderCode(company.selfSelectedRightsHolderCode);
        } else {
            req.setGuarantorCode(company.companyCode);
        }
    }

    /** 将页面持久化的主体选择传入单券提交。 */
    private void applyImportedRatingCompany(SecurityPoolAdjustSubmitReq req, SysImpTmpDetlBo item,
                                            BatchSecurityCheckData data) {
        String code = data.resolveExcelSecurityCode(trimToEmpty(item.getFld001()));
        SecurityInfoBo master = data.securities.get(code);
        if (master == null) {
            return;
        }
        SecurityInfoBo security = new SecurityInfoBo();
        BeanUtils.copyProperties(master, security);
        // 同券多行保留各行自己的主体选择和证券快照
        RatingCompany company = resolveImportedRatingCompany(item, true, security,
                data.ratingCompanies.getOrDefault(code, Collections.emptyList()),
                () -> data.selfSelectedCompanies.get(trimToEmpty(item.getFld013())));
        if (company == null) {
            return;
        }
        req.setSecurityInfo(company.securityInfo);
        req.setSelectedRatingCompany(company.selection);
        if (company.abs) {
            req.setRightsHolderCode(company.relatedCompanyCode);
            req.setSelfSelectedRightsHolderCode(company.selfSelectedRightsHolderCode);
        } else {
            req.setGuarantorCode(company.companyCode);
        }
    }

    /** 按传入的证券与候选校验本行选择，回填名称及评级 */
    private RatingCompany resolveImportedRatingCompany(SysImpTmpDetlBo item, boolean requireSelection,
            SecurityInfoBo securityInfo, List<RelatedRatingCompanyDto> companies,
            Supplier<SelfSelectedRightsHolderDto> lookupSelfSelected) {
        if (securityInfo == null) {
            return null;
        }
        boolean abs = CreditBondSpecialInboundRule.isAbs(securityInfo);
        String code = trimToEmpty(item.getFld011());
        String selfCode = trimToEmpty(item.getFld013());
        RelatedRatingCompanyDto selected = null;
        if (companies != null) {
            for (RelatedRatingCompanyDto company : companies) {
                if (isBlank(company.getCompanyCode())) {
                    continue;
                }
                if (code.equals(company.getCompanyCode())) {
                    selected = company;
                    break;
                }
            }
        }
        if (!code.isEmpty() && selected == null) {
            throw new BizException("所选担保人/权益人不属于当前证券，请重新选择");
        }
        SelfSelectedRightsHolderDto selfSelected = null;
        if (!selfCode.isEmpty()) {
            if (!abs) {
                throw new BizException("仅 ABS 证券支持自选权益人");
            }
            selfSelected = lookupSelfSelected.get();
            if (selfSelected == null) {
                throw new BizException("所选自选权益人不存在或已失效，请重新选择");
            }
        }
        boolean hasRelated = companies != null
                && companies.stream().anyMatch(company -> !isBlank(company.getCompanyCode()));
        if (requireSelection && selected == null && selfSelected == null && (abs || hasRelated)) {
            throw new BizException(abs ? "请选择权益人或自选权益人" : "请选择担保人/权益人");
        }
        item.setFld011(selected == null ? null : selected.getCompanyCode());
        item.setFld012(selected == null ? null : selected.getCompanyName());
        item.setFld013(selfSelected == null ? null : selfSelected.getCompanyCode());
        item.setFld014(selfSelected == null ? null : selfSelected.getCompanyName());
        RatingCompany result = new RatingCompany();
        result.abs = abs;
        result.relatedCompanyCode = item.getFld011();
        result.selfSelectedRightsHolderCode = item.getFld013();
        result.companyCode = selfSelected == null ? item.getFld011() : item.getFld013();
        result.securityInfo = securityInfo;
        RatingCompanySelectionReq selection = new RatingCompanySelectionReq();
        selection.setCompanyCode(result.companyCode);
        selection.setCompanyName(selfSelected == null ? item.getFld012() : item.getFld014());
        selection.setRightsHolderName(abs ? item.getFld012() : null);
        selection.setSelfSelectedRightsHolderName(item.getFld014());
        if (selfSelected != null) {
            selection.setInnerRating(selfSelected.getInnerRating());
            selection.setRatingBondissuer(selfSelected.getRatingBondissuer());
            selection.setRatingBondissuerAgency(selfSelected.getRatingBondissuerAgency());
        } else if (selected != null) {
            selection.setInnerRating(selected.getInnerRating());
            selection.setRatingBondissuer(selected.getRatingBondissuer());
            selection.setRatingBondissuerAgency(selected.getRatingBondissuerAgency());
        }
        result.selection = selection;
        return result;
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
        opt.setMaterialExemptionType(fo.getMaterialExemptionType());
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
    private InvestmentPoolBo resolvePoolByNames(String parentName, String childName, boolean securityImport,
                                                Map<String, InvestmentPoolBo> cache) {
        String cacheKey = trimToEmpty(parentName) + "||" + trimToEmpty(childName);
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }
        // 查询启用叶子投资池
        InvestmentPoolBo pool = securityPoolExcelImportMapper.queryEnabledLeafPoolByParentAndChildName(
                isBlank(parentName) ? null : parentName, childName, securityImport);
        cache.put(cacheKey, pool);
        return pool;
    }

    /** 在本批明细上记录校验失败，随其他行一起保存 */
    private void markItemFail(SysImpTmpDetlBo item, List<String> reasons, Date now) {
        item.setChkRslt("2");
        // 拼接失败原因
        item.setChkDscr(joinReasons(reasons));
        item.setUpdtTime(now);
    }

    /** 构建前置失败的手工校验结果项（不调用下游调库校验） */
    private SecurityPoolExcelImportCheckItemDto buildFailManualItem(
            SysImpTmpDetlBo sourceItem, String code, String shortName, String securityType,
            InvestmentPoolBo pool, String adjustMode, List<String> reasons) {
        SecurityPoolExcelImportCheckItemDto dto = new SecurityPoolExcelImportCheckItemDto();
        if (sourceItem != null) {
            dto.setSourceItemId(sourceItem.getId());
            dto.setSourceRowNo(sourceItem.getRowNo());
            dto.setRatingCompanyCode(isBlank(sourceItem.getFld013())
                    ? sourceItem.getFld011() : sourceItem.getFld013());
            dto.setRatingCompanyName(isBlank(sourceItem.getFld013())
                    ? sourceItem.getFld012() : sourceItem.getFld014());
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
        dto.setAdjustType(ADJUST_TYPE_EXCEL);
        dto.setAdjustGroupKey(code + "_" + (pool != null ? pool.getId() : "0") + "_" + adjustMode);
        dto.setCanAdjust(false);
        dto.setFailReasons(reasons == null ? new ArrayList<>() : new ArrayList<>(reasons));
        return dto;
    }

    /** 从请求或批次快照中筛选可提交的校验结果项 */
    private List<SecurityPoolExcelImportCheckItemDto> filterSubmittableCheckItems(
            SecurityPoolExcelImportReq req, SysImpTmpBo batch) {
        List<SecurityPoolExcelImportCheckItemDto> snapshot = parseCheckItems(batch.getResultJson());
        if (snapshot == null) {
            throw new BizException("校验结果缺失，请重新校验");
        }
        List<SecurityPoolExcelImportCheckItemDto> saved = snapshot.stream()
                .filter(item -> item.isCanAdjust() && item.getSourceItemId() != null
                        && !isBlank(item.getSecurityCode()) && item.getTargetPoolId() != null)
                .collect(Collectors.toList());
        if (req.getCheckItems() == null || req.getCheckItems().isEmpty()) {
            return saved;
        }
        Map<List<Object>, SecurityPoolExcelImportCheckItemDto> submitted = new HashMap<>();
        Set<List<Object>> validKeys = saved.stream().map(this::flowSelectionRowKey).collect(Collectors.toSet());
        Set<Long> selectedRows = new HashSet<>();
        for (SecurityPoolExcelImportCheckItemDto item : req.getCheckItems()) {
            if (item == null || !item.isCanAdjust()) {
                continue;
            }
            List<Object> key = flowSelectionRowKey(item);
            if (!validKeys.contains(key) || submitted.putIfAbsent(key, item) != null) {
                throw new BizException("提交项与来源导入明细不一致或未通过校验，请重新校验");
            }
            selectedRows.add(item.getSourceItemId());
        }
        List<SecurityPoolExcelImportCheckItemDto> result = new ArrayList<>();
        for (SecurityPoolExcelImportCheckItemDto item : saved) {
            if (!selectedRows.contains(item.getSourceItemId())) {
                continue;
            }
            SecurityPoolExcelImportCheckItemDto selection = submitted.get(flowSelectionRowKey(item));
            if (selection == null) {
                throw new BizException("提交项缺少同组调整明细，请重新校验");
            }
            if (isManualTag(item.getItemTag())) {
                item.setFlowId(selection.getFlowId());
                item.setFlowKey(selection.getFlowKey());
                item.setFlowType(selection.getFlowType());
                item.setSelectedFlowKey(selection.getSelectedFlowKey());
            }
            result.add(item);
        }
        return result;
    }

    /**
     * 定位需要回写流程的校验行
     *
     * 执行顺序：
     * 1. 组合来源行、实际证券或主体代码、目标池、方向及调整项标签
     *
     * @param item 导入校验项
     * @return 校验行的匹配键
     */
    private List<Object> flowSelectionRowKey(SecurityPoolExcelImportCheckItemDto item) {
        return Arrays.asList(item.getSourceItemId(), item.getSecurityCode(), item.getTargetPoolId(),
                item.getAdjustMode(), item.getItemTag());
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

    /**
     * 将 itemTag 转为前端展示的调整类型中文。
     * <p>对齐批量页「手动批量调整」的渠道专属口径：Excel 手工主项展示/落库均为「Excel导入」，
     * 清空主项为「清空目标池」；联动/互斥/关联与单笔、批量一致。</p>
     */
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
            return AdjustType.CLEAR_POOL.getCode();
        }
        // 手工主项：不用单笔「手工调整」，与提交 adjust_type、字典 DICT_ADJUST_TYPE 一致
        return ADJUST_TYPE_EXCEL;
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
        // 使用当前的用户和角色权限执行校验
        validateExcelImportPermission(currentUserId, poolId, queryExcelImportablePoolIds(currentUserId));
    }

    /** 校验目标池是否在本次读取的权限范围内 */
    private void validateExcelImportPermission(String currentUserId, Long poolId, Set<Long> permittedPools) {
        if (!ADMIN_USER_ID.equals(currentUserId) && !permittedPools.contains(poolId)) {
            throw new BizException("当前用户无权对该投资池进行 Excel 导入");
        }
    }

    /**
     * 查询当前用户可导入的投资池范围，供同批多个池复用
     *
     * @param currentUserId 当前用户 ID
     * @return 用户和角色授权的池 ID，管理员在权限校验时直接放行
     */
    private Set<Long> queryExcelImportablePoolIds(String currentUserId) {
        if (ADMIN_USER_ID.equals(currentUserId)) {
            return Collections.emptySet();
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
        Set<Long> roleIdSet = new HashSet<>(roleIds == null ? Collections.emptyList() : roleIds);
        List<PoolPermissionBo> permissions =
                investmentPoolMapper.queryPermissionListByType(PermissionType.EXCEL_IMPORTABLE.getCode());
        Set<Long> permittedPools = new HashSet<>();
        for (PoolPermissionBo permission : permissions) {
            if (permission.getPoolId() == null || permission.getHandlerId() == null) {
                continue;
            }
            if ((HandlerType.USER.getCode().equals(permission.getHandlerType())
                    && permission.getHandlerId().equals(userId))
                    || (HandlerType.ROLE.getCode().equals(permission.getHandlerType())
                    && roleIdSet.contains(permission.getHandlerId()))) {
                permittedPools.add(permission.getPoolId());
            }
        }
        return permittedPools;
    }

    /**
     * 将 Excel 行按导入类型写入通用字段槽
     * <p>证券模板四列、主体模板五列；证券评级主体在上传后由页面选择。</p>
     */
    private void fillFromExcelRow(SysImpTmpDetlBo item, Map<String, String> row, String importType) {
        // 按表头读取父池名称
        item.setFld003(cell(row, "父池名称"));
        // 按表头读取子池名称
        item.setFld004(cell(row, "子池名称"));
        if (IMPORT_TYPE_COMPANY.equals(importType)) {
            // 按表头读取主体代码/名称
            item.setFld001(cell(row, "主体代码"));
            item.setFld002(cell(row, "主体全称"));
            item.setFld005(cell(row, "统一社会信用代码"));
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
    private SysImpTmpBo requireBatch(String impId) {
        if (impId == null || impId.trim().isEmpty()) {
            throw new BizException("导入批次号不能为空");
        }
        SysImpTmpBo batch = securityPoolExcelImportMapper.queryBatchByImpId(impId.trim());
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
    private SecurityPoolExcelImportDto toTaskDto(SysImpTmpBo batch) {
        int pending = securityPoolExcelImportMapper.queryItemCountByCheckResult(batch.getImpId(), "0");
        return toTaskDto(batch, pending);
    }

    /** 使用已知待校验行数构造任务响应 */
    private SecurityPoolExcelImportDto toTaskDto(SysImpTmpBo batch, int pending) {
        SecurityPoolExcelImportDto dto = new SecurityPoolExcelImportDto();
        dto.setImpId(batch.getImpId());
        dto.setBizType(batch.getBizType());
        dto.setFileName(batch.getFileName());
        // 槽位约定：fld001=方向 / fld002=原因 / fld003=意见
        dto.setBizMode(batch.getFld001());
        dto.setOptionJson(batch.getOptionJson());
        dto.setReason(batch.getFld002());
        dto.setAdvice(batch.getFld003());
        dto.setTotalCount(batch.getTotalCount());
        dto.setPassCount(batch.getPassCount() == null ? 0 : batch.getPassCount());
        dto.setFailCount(batch.getFailCount() == null ? 0 : batch.getFailCount());
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
    private SecurityPoolExcelImportItemDto toItemDto(SysImpTmpDetlBo bo) {
        SecurityPoolExcelImportItemDto dto = new SecurityPoolExcelImportItemDto();
        dto.setId(bo.getId());
        dto.setImpDetlId(bo.getImpDetlId());
        dto.setRowNo(bo.getRowNo());
        dto.setSecurityCode(bo.getFld001());
        dto.setSecurityName(bo.getFld002());
        dto.setParentPoolName(bo.getFld003());
        dto.setChildPoolName(bo.getFld004());
        dto.setUnifiedSocialCreditCode(bo.getFld005());
        dto.setRelatedCompanyCode(bo.getFld011());
        dto.setRelatedCompanyName(bo.getFld012());
        dto.setSelfSelectedRightsHolderCode(bo.getFld013());
        if (!isBlank(bo.getFld013())) {
            SelfSelectedRightsHolderDto self = new SelfSelectedRightsHolderDto();
            self.setCompanyCode(bo.getFld013());
            self.setCompanyName(bo.getFld014());
            dto.setSelfSelectedRightsHolder(self);
        }
        dto.setRatingCompanyCode(isBlank(bo.getFld013()) ? bo.getFld011() : bo.getFld013());
        dto.setRatingCompanyName(isBlank(bo.getFld013()) ? bo.getFld012() : bo.getFld014());
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

    /** 组装导入选项 JSON */
    private String buildOptionJson(Boolean allowLinkMutex, String importType) {
        boolean link = Boolean.TRUE.equals(allowLinkMutex);
        String type = importType == null ? IMPORT_TYPE_SECURITY : importType;
        return "{\"allowLinkMutex\":" + link
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
