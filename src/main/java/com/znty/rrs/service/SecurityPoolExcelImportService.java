package com.znty.rrs.service;

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
import java.util.Objects;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 处理证券和主体 Excel 调库的预览、校验与提交
 *
 * 解析模板并检查导入权限和父子池配置，将上传结果保存为导入批次和明细，供页面预览和选择评级主体
 * 证券和主体分别复用各自的调库校验，正式提交分别交给 {@link BatchSecurityAdjustSubmitService} 和 {@link BatchCompanyAdjustSubmitService}
 * 清空目标池使用独立入口准备出库差集，完成复核后仍按证券和主体分开提交
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

    /** 证券调库完整校验/提交（批量调库内部亦委托该服务） */
    @Resource
    private BatchSecurityAdjustSubmitService batchSecurityAdjustSubmitService;

    /** 证券导入及清空出库的批量校验 */
    @Resource
    private BatchSecurityCheckService batchSecurityCheckService;

    /** 禁投池主体完整校验/提交 */
    @Resource
    private ForbiddenPoolAdjustService forbiddenPoolAdjustService;

    /** 处理主体 Excel 导入和清空项的正式提交 */
    @Resource
    private BatchCompanyAdjustSubmitService batchCompanyAdjustSubmitService;

    /** 批量准备主体 Excel 调库和清空项的校验数据 */
    @Resource
    private BatchCompanyAdjustCheckService batchCompanyAdjustCheckService;

    /** 附件服务（解析前端原始文件名，兼容公司环境中文乱码） */
    @Resource
    private SysAttachmentService sysAttachmentService;

    // ═══════════════════════════════════════════════════════════
    //  上传 / 查询 / 取消
    // ═══════════════════════════════════════════════════════════

    /**
     * 上传 Excel 并写入导入临时表
     *
     * 执行顺序：
     * 1. 把上传请求及文件交给导入入口，原始文件名列表留空
     *
     * @param req 携带批次号、明细 ID、主体选择和分页条件的修改请求
     * @param file 上传的 Excel 文件
     * @return 证券或主体 Excel 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public SecurityPoolExcelImportDto uploadExcel(SecurityPoolExcelImportReq req, MultipartFile file) {
        return uploadExcel(req, file, null);
    }

    /**
     * 上传 Excel 并写入导入临时表（可带前端原始文件名 JSON 数组，兼容公司环境中文名乱码）
     *
     * 执行顺序：
     * 1. 校验上传请求与文件基础参数
     * 2. 规范化导入类型（security / company）
     * 3. 解析 Excel 首 sheet
     * 4. 生成导入批次号
     * 5. 解析经办人名称（优先请求中的姓名）
     * 6. 按优先级批量查询当前上传涉及的主体，行循环只做内存匹配
     * 7. 回查批次任务信息并返回
     *
     * @param req 携带批次号、明细 ID、主体选择和分页条件的修改请求
     * @param file 上传的 Excel 文件
     * @param originalFileNameListJson 上传文件原始名称列表的 JSON
     * @return 证券或主体 Excel 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public SecurityPoolExcelImportDto uploadExcel(SecurityPoolExcelImportReq req, MultipartFile file,
                                                  String originalFileNameListJson) {
        // 1. 校验上传请求与文件基础参数
        validateUploadReq(req, file);
        List<String> originalFileNameList =
                sysAttachmentService.parseOriginalFileNameListJson(originalFileNameListJson);
        String uploadFileName = sysAttachmentService.resolveUploadOriginalFileName(file, originalFileNameList);
        // 2. 规范化导入类型（security / company）
        String importType = normalizeImportType(req.getImportType());

        // 3. 解析 Excel 首 sheet
        List<Map<String, String>> excelRows = ExcelImportHelper.parseFirstSheet(file, MAX_ROWS);
        Date now = new Date();
        // 4. 生成导入批次号
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
        // 空白串转为 null
        batch.setFld003(trimToNull(req.getAdjustAdvice()));
        batch.setTotalCount(0);
        batch.setPassCount(0);
        batch.setFailCount(0);
        batch.setChkRslt("0");
        batch.setSaveRslt("0");
        batch.setImpTime(now);
        batch.setOpterId(req.getCurrentUserId().trim());
        // 5. 解析经办人名称（优先请求中的姓名）
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
        // 6. 按优先级批量查询当前上传涉及的主体，行循环只做内存匹配
        CompanyImportLookup lookup = company ? loadCompanyImportLookup(items) : new CompanyImportLookup();
        for (SysImpTmpDetlBo item : items) {
            List<String> preFail = new ArrayList<>();
            if (company) {
                // 按信用代码、主体代码、主体全称顺序匹配，回填后参与重复检查
                resolveCompanyCode(item, lookup.codeMatches, preFail);
            // 判断字符串是否为 null 或空白
            } else if (isBlank(item.getFld001())) {
                preFail.add("证券代码为空");
            }
            // 判断字符串是否为 null 或空白
            if (!company && isBlank(item.getFld003())) {
                preFail.add("父池名称为空");
            }
            // 判断字符串是否为 null 或空白
            if (isBlank(item.getFld004())) {
                preFail.add("子池名称为空");
            }
            // null 安全 trim，空白返回空串
            String codeKey = trimToEmpty(item.getFld001());
            if (!company) {
                codeKey = codeKey.toUpperCase(Locale.ROOT);
            }
            // null 安全 trim，空白返回空串
            String uniq = codeKey + "|" + trimToEmpty(item.getFld004());
            // 判断字符串是否为 null 或空白
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
        // 7. 回查批次任务信息并返回
        return queryTask(pageReq);
    }

    /**
     * 查询导入批次主表信息（含校验结果快照）
     *
     * 执行顺序：
     * 1. 加载导入批次
     * 2. 主表转任务 DTO
     *
     * @param req 携带批次号、明细 ID、主体选择和分页条件的修改请求
     * @return 证券或主体 Excel 导入结果
     */
    public SecurityPoolExcelImportDto queryTask(SecurityPoolExcelImportReq req) {
        // 1. 加载导入批次
        SysImpTmpBo batch = requireBatch(req == null ? null : req.getImpId());
        // 2. 主表转任务 DTO
        SecurityPoolExcelImportDto dto = toTaskDto(batch);
        if (req != null && req.getPageIndex() > 0) {
            // 附带首屏明细分页
            dto.setItems(queryItemPage(req));
        }
        return dto;
    }

    /**
     * 分页查询导入明细
     *
     * 执行顺序：
     * 1. 校验批次存在
     * 2. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 3. 将本页记录和查询总条数一起返回
     *
     * @param req 携带批次号、明细 ID、主体选择和分页条件的修改请求
     * @return 证券或主体 Excel 导入明细及分页信息
     */
    public PageResult<SecurityPoolExcelImportItemDto> queryItemPage(SecurityPoolExcelImportReq req) {
        // 1. 校验批次号并读取导入批次信息
        if (req == null || req.getImpId() == null || req.getImpId().trim().isEmpty()) {
            throw new BizException("导入批次号不能为空");
        }
        // 校验批次存在
        SysImpTmpBo batch = requireBatch(req.getImpId());
        // 2. 按过滤条件分页查询明细并组装展示数据
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SysImpTmpDetlBo> list = securityPoolExcelImportMapper.queryItemPage(
                // 空白串转为 null
                req.getImpId().trim(), trimToNull(req.getChkRslt()), trimToNull(req.getKeyword()));
        PageInfo<SysImpTmpDetlBo> pageInfo = new PageInfo<>(list);
        List<SecurityPoolExcelImportItemDto> records = new ArrayList<>();
        for (SysImpTmpDetlBo bo : list) {
            // 明细 Bo 转 DTO
            records.add(toItemDto(bo));
        }
        // 3. 证券导入时补充本页评级主体候选
        if (IMPORT_TYPE_SECURITY.equals(parseImportType(batch.getOptionJson(), batch.getBizType()))) {
            // 为当前页补齐关联候选、自选权益人和内评展示数据
            fillRatingCompanyOptions(records, null);
        }
        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 仅在上传初始化适用的担保人和权益人
     *
     * 执行顺序：
     * 1. 收集导入行的有效证券代码，批量查询证券主档和评级主体候选
     * 2. 批量查询 ABS 当前身份，再按每行证券类型和已确认身份回填担保人或权益人
     *
     * @param items 本批有效导入明细，每行包含一只证券、一个目标池及独立主体选择
     */
    private void initializeRatingCompanies(List<SysImpTmpDetlBo> items) {
        List<String> securityCodes = items.stream().map(SysImpTmpDetlBo::getFld001)
                // 判断字符串是否为 null 或空白
                .filter(code -> !isBlank(code)).map(String::trim).distinct().collect(Collectors.toList());
        if (securityCodes.isEmpty()) {
            return;
        }
        BatchSecurityCheckData data = batchSecurityCheckService.loadExcelRatingCandidates(securityCodes, Collections.emptyList());
        List<String> absCodes = data.securities.values().stream().filter(CreditBondSpecialInboundRule::isAbs)
                .map(SecurityInfoBo::getWindCode).collect(Collectors.toList());
        Map<String, SecurityInfoBo> identities = absIdentityService.queryCurrentIdentities(absCodes);
        for (SysImpTmpDetlBo item : items) {
            // null 安全 trim，空白返回空串
            String code = data.resolveExcelSecurityCode(trimToEmpty(item.getFld001()));
            SecurityInfoBo security = data.securities.get(code);
            if (security == null) {
                continue;
            }
            List<RelatedRatingCompanyDto> candidates = data.ratingCompanies.getOrDefault(code, Collections.emptyList());
            // 担保债按页面顺序初始化担保人，优先回显已有担保人
            RelatedRatingCompanyDto guarantor = SecurityPoolCheckRules.defaultGuarantor(security, candidates);
            if (!CreditBondSpecialInboundRule.isAbs(security)) {
                if (guarantor != null) {
                    item.setFld011(guarantor.getCompanyCode());
                    item.setFld012(guarantor.getCompanyName());
                }
                continue;
            }
            item.setFld020(guarantor == null ? null : guarantor.getCompanyCode());
            if (!candidates.isEmpty()) {
                item.setFld011(candidates.get(0).getCompanyCode());
                item.setFld012(candidates.get(0).getCompanyName());
            }
            // ABS 权益人继续优先使用已确认或占用的身份
            SecurityInfoBo identity = identities.get(code);
            if (identity != null) {
                if (candidates.stream().anyMatch(candidate -> candidate.getCompanyCode().equals(identity.getIssuerCode()))) {
                    item.setFld011(identity.getIssuerCode());
                    item.setFld012(identity.getIssuer());
                } else {
                    item.setFld013(identity.getIssuerCode());
                    item.setFld014(identity.getIssuer());
                }
            }
        }
    }

    /**
     * 按当前页批量补齐证券类型、可选评级主体及自选权益人
     *
     * 执行顺序：
     * 1. 没有导入行时结束处理
     * 2. 整理当前页的证券和自选权益人代码，批量补齐评级主体候选
     * 3. 逐行回填已加载的评级主体候选
     *
     * @param records 记录列表
     * @param data 已加载的批次数据
     */
    private void fillRatingCompanyOptions(List<SecurityPoolExcelImportItemDto> records, BatchSecurityCheckData data) {
        // 1. 没有导入行时结束处理
        if (records.isEmpty()) {
            return;
        }
        // 2. 整理当前页的证券和自选权益人代码，批量补齐评级主体候选
        List<String> codes = records.stream().map(row -> trimToEmpty(row.getSecurityCode()))
                .filter(code -> !code.isEmpty()).distinct().collect(Collectors.toList());
        // null 安全 trim，空白返回空串
        List<String> selfSelected = records.stream().map(row -> trimToEmpty(row.getSelfSelectedRightsHolderCode()))
                .filter(code -> !code.isEmpty()).distinct().collect(Collectors.toList());
        if (data == null) {
            // 独立翻页以当前数据重新读取候选
            data = batchSecurityCheckService.loadExcelRatingCandidates(codes, selfSelected);
        } else {
            // 校验首屏复用本次候选，只补查未进入校验取数的行或选择
            batchSecurityCheckService.completeExcelRatingCandidates(data, codes, selfSelected);
        }
        // 3. 逐行回填已加载的评级主体候选
        for (SecurityPoolExcelImportItemDto dto : records) {
            // null 安全 trim，空白返回空串
            String code = data.resolveExcelSecurityCode(trimToEmpty(dto.getSecurityCode()));
            SecurityInfoBo info = data.securities.get(code);
            List<RelatedRatingCompanyDto> related = data.ratingCompanies.getOrDefault(code, Collections.emptyList());
            if (info != null) {
                dto.setSecurityType(info.getSecurityType());
                dto.setAbsFlag(info.getAbsFlag());
                dto.setGuarantFlag(info.getGuarantFlag());
                dto.setGuarantor(info.getGuarantor());
                dto.setInnerGuarantorRating(info.getInnerGuarantorRating());
            }
            dto.setRelatedRatingCompanies(related);
            for (RelatedRatingCompanyDto company : related) {
                // null 安全 trim，空白返回空串
                if (trimToEmpty(dto.getRelatedCompanyCode()).equals(company.getCompanyCode())) {
                    dto.setRatingCompanyInnerRating(company.getInnerRating());
                    break;
                }
            }
            // 判断字符串是否为 null 或空白
            if (!isBlank(dto.getSelfSelectedRightsHolderCode())) {
                SelfSelectedRightsHolderDto self =
                        // null 安全 trim，空白返回空串
                        data.selfSelectedCompanies.get(trimToEmpty(dto.getSelfSelectedRightsHolderCode()));
                if (self != null) {
                    dto.setSelfSelectedRightsHolder(self);
                }
                dto.setRatingCompanyInnerRating(self == null ? null : self.getInnerRating());
            }
        }
    }

    /**
     * 保存当前导入行的权益人/担保人选择，并使本批旧校验结果失效
     *
     * 执行顺序：
     * 1. 检查请求、导入明细 ID 和当前用户 ID 是否为空
     * 2. 读取导入批次，确认批次尚未提交且属于证券导入
     * 3. 按批次号和明细 ID 查询有效导入行，确认该明细属于当前批次
     * 4. 规范化关联主体及自选权益人代码，空字符串转为 null；复核候选资格并由服务端回填名称，编辑时允许暂不选择主体
     * 5. 仅更新当前行的主体选择，其他相同证券代码的导入行保留各自选择
     * 6. 清空本批所有行的旧校验结果及批次校验快照，清除校验结果筛选并补齐有效页码，回查任务和明细
     *
     * @param req 携带批次号、明细 ID、主体选择和分页条件的修改请求
     * @return 保存后的批次任务及当前页明细，主体修改后须重新校验
     */
    @Transactional(rollbackFor = Exception.class)
    public SecurityPoolExcelImportDto editRatingCompany(SecurityPoolExcelImportReq req) {
        // 1. 检查导入明细 ID 和当前用户
        if (req == null || req.getItemId() == null || isBlank(req.getCurrentUserId())) {
            throw new BizException("导入明细 ID 和当前用户 ID 不能为空");
        }
        // 2. 读取未提交的证券导入批次并检查明细归属
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
        // 3. 读取本行选择并清理空白，允许清空选择
        selection.setFld011(trimToNull(req.getRelatedCompanyCode()));
        if (req.getGuarantorCode() != null) {
            // 空白串转为 null
            selection.setFld020(trimToNull(req.getGuarantorCode()));
        }
        // 空白串转为 null
        selection.setFld013(trimToNull(req.getSelfSelectedRightsHolderCode()));
        // 按本次选择重新查询并回填名称，编辑时允许清空主体
        String securityCode = trimToEmpty(selection.getFld001());
        // 判断字符串是否为 null 或空白
        List<String> selfSelected = isBlank(selection.getFld013()) ? Collections.emptyList()
                : Collections.singletonList(selection.getFld013());
        // 4. 加载评级候选，复核并回填已有选择
        BatchSecurityCheckData ratingData = batchSecurityCheckService.loadExcelRatingCandidates(
                Collections.singletonList(securityCode), selfSelected);
        String masterCode = ratingData.resolveExcelSecurityCode(securityCode);
        // 按传入的证券与候选校验本行选择，回填名称及评级
        if (resolveImportedRatingCompany(selection, false, ratingData.securities.get(masterCode),
                ratingData.ratingCompanies.getOrDefault(masterCode, Collections.emptyList()),
                // null 安全 trim，空白返回空串
                () -> ratingData.selfSelectedCompanies.get(trimToEmpty(selection.getFld013()))) == null) {
            throw new BizException("证券不存在或已失效，不能选择评级主体");
        }
        // 5. 保存本行选择并清空需要重新生成的校验结果
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

    /**
     * 取消导入批次（逻辑删除主表与明细）
     *
     * 执行顺序：
     * 1. 检查导入批次号不能为空
     * 2. 读取本次要取消的批次
     * 3. 该批次已提交，不能取消时终止处理
     * 4. 删除明细列表
     *
     * @param req 携带批次号、明细 ID、主体选择和分页条件的修改请求
     */
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
     * 对批次明细执行业务校验
     *
     * 证券分支对齐批量调库；主体分支对齐禁投池（多主体逐条）
     *
     * 执行顺序：
     * 1. 读取本次要校验的批次
     * 2. 解析是否允许联动与互斥
     * 3. 解析导入类型（证券/主体）
     * 4. 是否主体导入
     * 5. 序列化调库校验结果写入主表
     * 6. 先保存各行校验结果，再写入依赖这些结果的批次摘要
     * 7. 本次已按分页顺序读取并回写全部明细，直接组装校验首屏
     *
     * @param req 携带批次号、明细 ID、主体选择和分页条件的修改请求
     * @return 证券或主体 Excel 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public SecurityPoolExcelImportDto checkImport(SecurityPoolExcelImportReq req) {
        if (req == null || req.getImpId() == null || req.getImpId().trim().isEmpty()) {
            throw new BizException("导入批次号不能为空");
        }
        // 1. 读取本次要校验的批次
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
        // 2. 解析是否允许联动与互斥
        boolean allowLinkMutex = parseAllowLinkMutex(batch.getOptionJson());
        // 3. 解析导入类型（证券/主体）
        String importType = parseImportType(batch.getOptionJson(), batch.getBizType());
        Date now = new Date();
        // 4. 是否主体导入
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
        // 5. 序列化调库校验结果写入主表
        batch.setResultJson(buildCheckResultJson(checkItems, checkPass, checkFail, allowLinkMutex, importType));
        batch.setUpdtTime(now);
        // 6. 先保存各行校验结果，再写入依赖这些结果的批次摘要
        for (int i = 0; i < items.size(); i += DETAIL_BATCH_SIZE) {
            int end = Math.min(i + DETAIL_BATCH_SIZE, items.size());
            securityPoolExcelImportMapper.editItemCheckResultList(batch.getImpId(), items.subList(i, end), now);
        }
        securityPoolExcelImportMapper.editBatchCheckResult(batch);

        // 7. 本次已按分页顺序读取并回写全部明细，直接组装校验首屏
        int pending = (int) items.stream().filter(row -> "0".equals(row.getChkRslt())).count();
        // 使用已知待校验行数构造任务响应
        SecurityPoolExcelImportDto dto = toTaskDto(batch, pending);
        int pageSize = 20;
        List<SecurityPoolExcelImportItemDto> firstPage = items.stream().limit(pageSize)
                // 临时表明细转前端明细 DTO
                .map(this::toItemDto).collect(Collectors.toList());
        if (!companyImport) {
            // 复用本次证券主档及主体候选完成首屏回显
            fillRatingCompanyOptions(firstPage, securityData);
        }
        dto.setItems(new PageResult<>(firstPage, items.size(), 1, pageSize));
        return dto;
    }

    /**
     * 校验证券 Excel 导入并保留实际调库结果
     *
     * 每行对应一只证券和一个目标池，调整方向由批次统一选择
     * 开启联动与互斥调整时，同池已展开的关联券沿用同券组结果，原始行保留并入说明
     *
     * 执行顺序：
     * 1. 确定本批调整方向，准备校验结果和投资池查询缓存
     * 2. 按 Excel 行序检查必填项和重复行，解析目标池并核对导入权限
     * 3. 批量加载证券、池状态和评级主体数据，检查同批 ABS 主体冲突
     * 4. 回写前置失败，已展开的同池关联券沿用校验结果，其余行复核主体资格后执行调库校验
     * 5. 按联动与互斥选项保留结果，补充流程候选，并按手工主项回写导入行的状态和原因
     *
     * @param items 本批导入明细，每行包含一只证券、一个目标池及已保存的主体选择
     * @param inbound 是否调入，false 表示调出
     * @param allowLinkMutex 是否保留联动、互斥和关联展开项
     * @param opterId 执行导入校验的用户 ID
     * @param now 回写行级校验结果使用的统一时间
     * @return 实际调库校验结果及首屏可复用的批量数据
     */
    private SecurityImportCheckResult checkSecurityImport(
            List<SysImpTmpDetlBo> items, boolean inbound, boolean allowLinkMutex,
            String opterId, Date now) {
        // 1. 确定本批方向，准备校验结果和目标池查询缓存
        List<SecurityImportCheckRow> rows = new ArrayList<>();
        List<AdjustCheckReq> requests = new ArrayList<>();
        List<AdjustCheckReq> selections = new ArrayList<>();
        List<SecurityPoolExcelImportCheckItemDto> checkItems = new ArrayList<>();
        Map<String, SecurityPoolExcelImportCheckItemDto> expandedRelatedItems = new HashMap<>();
        Map<String, InvestmentPoolBo> poolNameCache = new HashMap<>();
        Map<Long, String> permissionErrors = new HashMap<>();
        Set<String> seenKeys = new HashSet<>();
        String adjustMode = inbound ? AdjustMode.IN.getCode() : AdjustMode.OUT.getCode();
        String direction = inbound ? "in" : "out";

        // 2. 检查原始行，解析目标池并记录前置错误
        for (SysImpTmpDetlBo item : items) {
            List<String> preReasons = new ArrayList<>();
            // null 安全 trim，空白返回空串
            String code = trimToEmpty(item.getFld001());
            // null 安全 trim，空白返回空串
            String parentName = trimToEmpty(item.getFld003());
            // null 安全 trim，空白返回空串
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
            boolean duplicate = !code.isEmpty() && !childName.isEmpty()
                    && !seenKeys.add(code.toUpperCase(Locale.ROOT) + "|" + childName);
            if (duplicate) {
                preReasons.add("Excel 内相同证券代码与子池重复");
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
                    // 构建证券导入或清空出库的一券一池校验项，再空白串转为 null
                    ? buildExcelSecurityCheck(code, trimToNull(item.getFld002()), pool, adjustMode)
                    : new AdjustCheckReq();
            request.setSecurityCode(code);
            // 空白串转为 null，再判断字符串是否为 null 或空白
            request.setGuarantorCode(trimToNull(isBlank(item.getFld020()) ? item.getFld011() : item.getFld020()));
            // 空白串转为 null
            request.setRightsHolderCode(trimToNull(item.getFld011()));
            // 空白串转为 null
            request.setSelfSelectedRightsHolderCode(trimToNull(item.getFld013()));
            // 从去重后的导入行收集 ABS 主体选择
            if (!duplicate) {
                selections.add(request);
            }
            if (preReasons.isEmpty()) {
                requests.add(request);
            }
            rows.add(new SecurityImportCheckRow(item, pool, request, preReasons));
        }

        // 3. 批量加载证券、池状态和评级主体，并检查同批 ABS 主体冲突
        BatchSecurityCheckData data = batchSecurityCheckService.loadExcelCheckData(requests, false);
        Set<String> identityConflicts = batchSecurityCheckService.findConflictingAbsCodes(selections, data);
        // 4. 按原行顺序复用同券结果或执行调库校验
        for (SecurityImportCheckRow row : rows) {
            SysImpTmpDetlBo item = row.item;
            InvestmentPoolBo pool = row.pool;
            String code = row.request.getSecurityCode();
            if (!row.errors.isEmpty()) {
                // 记录入口错误并保留原行序
                markItemFail(item, row.errors, now);
                // 构建前置失败的手工校验结果项（不调用下游调库校验）
                checkItems.add(buildFailManualItem(item, code, item.getFld002(), null, pool, adjustMode, row.errors));
                continue;
            }

            // 将主档代码写回来源行，提交时与校验结果使用同一代码
            item.setFld001(code);
            // 同一目标池已展开的关联码沿用已有结果，已有主体冲突的行仍按原规则处理
            String poolKey = pool.getId() + "|";
            SecurityPoolExcelImportCheckItemDto expanded = expandedRelatedItems.get(poolKey + code);
            if (expanded != null && !identityConflicts.contains(code)) {
                item.setChkRslt(expanded.isCanAdjust() ? "1" : "2");
                item.setChkDscr("已并入第" + expanded.getSourceRowNo() + "行同券组");
                item.setUpdtTime(now);
                continue;
            }
            AdjustCheckDto checkDto;
            try {
                // 复核并使用页面保存的权益人或担保人，不覆盖手动选择
                applyImportedRatingCompany(row.request, item, data);
                // 执行证券 Excel 调库校验并保留逐券结果
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
            // 5. 按联动与互斥选项保留结果，并映射流程和导入行状态
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
                SecurityPoolExcelImportCheckItemDto mapped =
                        // 映射校验结果，为可调整的手工项补充目标池的批量流程
                        mapSecurityCheckResult(item, ri, code, pool, direction);
                // 判断手工主项是否可调整
                if (isManualTag(mapped.getItemTag()) && mapped.isCanAdjust()) {
                    manualPass = true;
                }
                checkItems.add(mapped);
                // 后续导入行只复用主目标池的同券结果
                if (ItemType.RELATED.getCode().equals(mapped.getItemTag()) && pool.getId().equals(mapped.getTargetPoolId())) {
                    expandedRelatedItems.putIfAbsent(poolKey + mapped.getSecurityCode(), mapped);
                }
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

    /**
     * 保存一批证券 Excel 导入的校验结果
     *
     * 同时保留本批已读取的证券主档和候选信息，供页面回显时复用
     */
    static class SecurityImportCheckResult {
        /** 本批证券导入行的逐项校验结果 */
        final List<SecurityPoolExcelImportCheckItemDto> items;

        /** 本批校验时读取的证券和评级候选等数据 */
        final BatchSecurityCheckData data;

        /**
         * 记录一批证券 Excel 调库的校验结果
         *
         * 执行顺序：
         * 1. 保存逐行校验结果和页面返回数据
         *
         * @param items 各证券导入行的校验结果
         * @param data 供页面展示的校验返回数据
         */
        SecurityImportCheckResult(List<SecurityPoolExcelImportCheckItemDto> items, BatchSecurityCheckData data) {
            this.items = items;
            this.data = data;
        }
    }

    /**
     * 构建证券导入或清空出库的一券一池校验项
     *
     * 执行顺序：
     * 1. 用导入行的证券代码和简称构造一券一池的调库校验请求
     * 2. 写入目标池和调整方向，供后续校验
     *
     * @param code 证券代码
     * @param shortName 简称
     * @param pool 已加载的目标池
     * @param adjustMode 调入或调出方向
     * @return 待校验的调库请求
     */
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

    /**
     * 保存一个证券导入行在规则校验前的准备结果
     *
     * 将原始行、目标池、生成的调库校验请求和入口错误关联起来，供后续逐行处理
     */
    private static class SecurityImportCheckRow {
        /** 当前证券导入明细的临时记录 */
        private final SysImpTmpDetlBo item;

        /** 该导入行解析出的目标投资池 */
        private final InvestmentPoolBo pool;

        /** 该行准备提交给调库校验的请求 */
        private final AdjustCheckReq request;

        /** 该行转换成调库请求前收集的错误 */
        private final List<String> errors;

        /**
         * 记录证券 Excel 调库一行的校验结果
         *
         * 执行顺序：
         * 1. 保存导入行、目标池、生成的校验请求和发现的问题
         *
         * @param item 原始证券导入行
         * @param pool 本行对应的目标池配置
         * @param request 根据导入行生成的调库校验请求
         * @param errors 本行发现的校验问题
         */
        private SecurityImportCheckRow(SysImpTmpDetlBo item, InvestmentPoolBo pool, AdjustCheckReq request,
                                       List<String> errors) {
            this.item = item;
            this.pool = pool;
            this.request = request;
            this.errors = errors;
        }
    }

    /**
     * 仅为上一级未命中的行批量加载下一级条件，结果和主体资料限本次调用复用
     *
     * 执行顺序：
     * 1. 按统一社会信用代码、主体代码和主体全称的优先级收集需要查询的条件
     * 2. 对本级条件分批匹配，记录候选主体代码及主体资料，并保留判断歧义所需的匹配结果
     *
     * @param items 本批待按信用代码、主体代码或主体全称识别的导入行
     * @return 按查找条件整理的匹配结果及按主体代码整理的主体资料
     */
    private CompanyImportLookup loadCompanyImportLookup(List<SysImpTmpDetlBo> items) {
        CompanyImportLookup lookup = new CompanyImportLookup();
        for (int type = 0; type < 3; type++) {
            // 1. 按统一社会信用代码、主体代码和主体全称的优先级收集需要查询的条件
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
            // 2. 对本级条件分批匹配，记录候选主体代码及主体资料，并保留判断歧义所需的匹配结果
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
                    // 判断字符串是否为 null 或空白
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

    /**
     * 取得规范化查询条件，数组位置对应信用代码、主体代码、主体名称
     *
     * 执行顺序：
     * 1. 按统一社会信用代码、主体代码、主体全称的顺序读取匹配值，清理首尾空白
     *
     * @param item 本次待提取主体匹配条件的 Excel 导入行
     * @return 依次包含信用代码、主体代码和主体全称的数组，未填写的值统一为空字符串
     */
    private String[] companyLookupValues(SysImpTmpDetlBo item) {
        // 按主体匹配优先级整理三项查询值
        return new String[]{trimToEmpty(item.getFld005()), trimToEmpty(item.getFld001()), trimToEmpty(item.getFld002())};
    }

    /**
     * 保存一次 Excel 主体导入的匹配结果
     *
     * 按原查询条件记录最多两个候选代码以识别歧义，并缓存已取得的主体资料供本次校验复用
     */
    private static class CompanyImportLookup {
        /** 原条件对应的最多两个不同主体代码，用于判断歧义 */
        private final Map<String, List<String>> codeMatches = new HashMap<>();

        /** 已查询主体资料，供当前完整校验复用 */
        private final Map<String, ForbiddenPoolAdjustDto> companies = new HashMap<>();
    }

    /**
     * 保存一个主体导入行的前置校验结果
     *
     * 将暂存明细、解析后的目标池和失败原因一起交给后续完整校验
     */
    private static class CompanyImportCheckRow {
        /** 当前主体导入明细的临时记录 */
        final SysImpTmpDetlBo item;

        /** 该主体导入行解析出的目标投资池 */
        final InvestmentPoolBo pool;

        /** 该行校验的失败原因 */
        final List<String> reasons;

        /**
         * 记录主体 Excel 调库一行的校验结果
         *
         * 执行顺序：
         * 1. 保存导入行、目标池和校验失败原因
         *
         * @param item 原始主体导入行
         * @param pool 本行对应的目标池配置
         * @param reasons 本行的校验失败原因
         */
        CompanyImportCheckRow(SysImpTmpDetlBo item, InvestmentPoolBo pool, List<String> reasons) {
            this.item = item;
            this.pool = pool;
            this.reasons = reasons;
        }
    }

    /**
     * 按优先级读取批量匹配结果；未填写或未命中时才进入下一级
     *
     * 执行顺序：
     * 1. 清理统一社会信用代码、主体代码和名称，三项都未填时记录失败原因
     * 2. 依次从批量查询结果中匹配，唯一命中时回填主体代码，歧义或全部未命中时记录原因
     *
     * @param item 当前调整项
     * @param companyCache 按文本索引的列表
     * @param reasons 用于展示或保存的失败原因
     */
    private void resolveCompanyCode(SysImpTmpDetlBo item, Map<String, List<String>> companyCache,
                                    List<String> reasons) {
        // 1. 清理统一社会信用代码、主体代码和名称，三项都未填时记录失败原因
        String creditCode = trimToEmpty(item.getFld005());
        // null 安全 trim，空白返回空串
        String companyCode = trimToEmpty(item.getFld001());
        // null 安全 trim，空白返回空串
        String companyName = trimToEmpty(item.getFld002());
        if (creditCode.isEmpty() && companyCode.isEmpty() && companyName.isEmpty()) {
            reasons.add("统一社会信用代码、主体代码和主体全称均为空");
            return;
        }
        // 2. 依次从批量查询结果中匹配，唯一命中时回填主体代码，歧义或全部未命中时记录原因
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
     * 主体导入校验：编排对齐 {@link ForbiddenPoolAdjustService#checkCompanyAdjust}
     *
     * 每行 Excel 对应「一主体 + 一目标池 + 方向」→ 调用禁投池主体完整校验；
     * 未勾选联动互斥时仅保留手工项
     *
     * 执行顺序：
     * 1. 本次校验重新批量读取主体，复用资料不再逐行查询公司简介
     * 2. 先确定全部有效主体和池，再统一取数，按 Excel 原顺序执行各行规则
     *
     * @param items 本批有效导入明细，每行包含一只证券、一个目标池及独立主体选择
     * @param inbound 是否调入，false 表示调出
     * @param allowLinkMutex 是否保留联动、互斥和关联展开项
     * @param opterId 经办人 ID
     * @param now 回写行级校验结果使用的统一时间
     * @return 证券投资池Excel 导入校验明细列表
     */
    private List<SecurityPoolExcelImportCheckItemDto> checkCompanyImport(
            List<SysImpTmpDetlBo> items, boolean inbound, boolean allowLinkMutex,
            String opterId, Date now) {
        List<SecurityPoolExcelImportCheckItemDto> checkItems = new ArrayList<>();
        Map<String, InvestmentPoolBo> poolNameCache = new HashMap<>();
        // 1. 本次校验重新批量读取主体，复用资料不再逐行查询公司简介
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
            // null 安全 trim，空白返回空串
            String code = trimToEmpty(item.getFld001());
            // null 安全 trim，空白返回空串
            String parentName = trimToEmpty(item.getFld003());
            // null 安全 trim，空白返回空串
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
                    // 判断字符串是否为 null 或空白
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

        // 2. 先确定全部有效主体和池，再统一取数，按 Excel 原顺序执行各行规则
        CompanyAdjustCheckContext checkContext = companies.isEmpty() ? null
                : batchCompanyAdjustCheckService.load(companies, poolIds, inbound);
        for (CompanyImportCheckRow row : prepared) {
            SysImpTmpDetlBo item = row.item;
            // null 安全 trim，空白返回空串
            String code = trimToEmpty(item.getFld001());
            InvestmentPoolBo pool = row.pool;
            if (!row.reasons.isEmpty()) {
                // 在本批明细上记录校验失败，随其他行一起保存
                markItemFail(item, row.reasons, now);
                // 构建前置失败的手工校验结果项（不调用下游调库校验）
                checkItems.add(buildFailManualItem(item, code, item.getFld002(), "company", pool,
                        adjustMode, row.reasons));
                continue;
            }

            ForbiddenPoolAdjustCheckReq companyReq = new ForbiddenPoolAdjustCheckReq();
            companyReq.setCompanyCode(code);
            // 空白串转为 null
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
                        // 映射主体校验结果，保留单笔候选并追加目标池配置的批量流程
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
                            // 是否为手工调整项标签
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
     * 按校验结果提交。证券走证券池调库提交；主体走禁投池主体提交
     *
     * 执行顺序：
     * 1. 读取本次提交的批次，拒绝重复提交及未校验、主体修改后尚未重新校验的批次
     * 2. 依据校验快照核对逐条勾选的调整项，确定经办人、原因和意见
     * 3. 复核在途和在池状态，按导入类型提交证券或主体，证券使用各来源行保存的主体选择
     * 4. 回写实际提交项对应的来源明细，更新批次状态并返回调库日志 ID 和批次号
     *
     * @param req 携带批次号、经办人、原因意见及可提交校验项的提交请求
     * @return 提交后的批次任务及调库记录信息
     */
    @Transactional(rollbackFor = Exception.class)
    public SecurityPoolExcelImportDto submitImport(SecurityPoolExcelImportReq req) {
        if (req == null || req.getImpId() == null || req.getImpId().trim().isEmpty()) {
            throw new BizException("导入批次号不能为空");
        }
        // 1. 读取导入批次，确认已校验且尚未提交
        SysImpTmpBo batch = requireBatch(req.getImpId());
        if ("1".equals(batch.getSaveRslt())) {
            throw new BizException("该批次已提交，请勿重复提交");
        }
        // 判断字符串是否为 null 或空白
        if ("0".equals(batch.getChkRslt()) || isBlank(batch.getResultJson())) {
            throw new BizException("请先校验，修改主体选择后需要重新校验");
        }
        // 2. 校验勾选项，确定本次经办人、原因和意见
        List<SecurityPoolExcelImportCheckItemDto> checkItems = filterSubmittableCheckItems(req, batch);
        String opterId = req.getCurrentUserId() != null && !req.getCurrentUserId().trim().isEmpty()
                ? req.getCurrentUserId().trim() : batch.getOpterId();
        String opterName = req.getCurrentUserName() != null && !req.getCurrentUserName().trim().isEmpty()
                ? req.getCurrentUserName().trim() : batch.getOpterName();
        // 判断字符串是否为 null 或空白
        if (isBlank(opterId)) {
            throw new BizException("经办人 ID 不能为空");
        }
        // 判断字符串是否为 null 或空白
        if (isBlank(opterName)) {
            opterName = opterId;
        }
        if (req.getAdjustReason() != null && !req.getAdjustReason().trim().isEmpty()) {
            batch.setFld002(req.getAdjustReason().trim());
        }
        if (req.getAdjustAdvice() != null && !req.getAdjustAdvice().trim().isEmpty()) {
            batch.setFld003(req.getAdjustAdvice().trim());
        }

        // 3. 确认导入类型、复核在途和在池状态，再按类型提交
        String importType = parseImportType(batch.getOptionJson(), batch.getBizType());
        List<Long> logIds = new ArrayList<>();
        List<String> batchNos = new ArrayList<>();
        // 在写入任何流程前一次复核本批在途与有效在池状态
        validateImportConflicts(checkItems, importType);
        if (IMPORT_TYPE_COMPANY.equals(importType)) {
            // 主体分支：禁投池同构提交
            logIds.addAll(submitCompanyImport(checkItems, batch, opterId, opterName));
        } else {
            // 证券分支：证券调库同构提交
            logIds.addAll(submitSecurityImport(checkItems, batch, opterId, opterName, batchNos));
        }

        // 4. 回写实际提交的来源明细和批次状态
        Date now = new Date();
        Set<Long> submittedSourceIds = new LinkedHashSet<>();
        Map<List<Object>, SecurityPoolExcelImportCheckItemDto> submittedFlows = new HashMap<>();
        for (SecurityPoolExcelImportCheckItemDto ci : checkItems) {
            // 是否需选择审批流程的主项（手工 / 清空）
            if (isFlowSelectableTag(ci.getItemTag())) {
                // 记录本次所选流程，供提交响应回显
                submittedFlows.put(flowSelectionRowKey(ci), ci);
            }
            submittedSourceIds.add(ci.getSourceItemId());
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

        // 5. 组装任务结果，补齐调库日志 ID 和批次号
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
     * 证券提交：按来源导入行分组，各行独立使用已保存的评级主体
     * 主项及其联动、互斥、关联项共用该行选择，整批复用证券调库提交步骤
     *
     * 执行顺序：
     * 1. 查询当前批次的有效明细并按 ID 建立索引，后续主体选择读取这些持久化明细
     * 2. 按 sourceItemId 分组校验项；缺少来源明细 ID 时拒绝提交，相同证券代码的不同导入行分别分组
     * 3. 为每组确定主项，确认来源行已通过校验，并复核来源证券及手工主项目标池与明细一致
     * 4. 构建该行提交请求，填写经办人、原因和意见，再次复核并应用本行保存的权益人/担保人选择
     * 5. 逐项检查目标池和导入权限，携带各项的方向、标签、分组键及所选流程组装调库明细
     * 6. 调用整批提交入口，先完成所有行的前置检查，再按行执行提交和快照保存，汇总调库日志 ID
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
        // 1. 按来源明细 ID 建立已保存导入行的索引
        Map<Long, SysImpTmpDetlBo> sourceItems = new HashMap<>();
        for (SysImpTmpDetlBo source : securityPoolExcelImportMapper.queryBatchItemList(batch.getImpId())) {
            sourceItems.put(source.getId(), source);
        }
        // 2. 按来源行对调整项分组并复核校验状态
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
        // 3. 收集实际提交的证券和自选权益人代码，批量读取候选
        List<String> codes = submittedRows.stream().map(row -> trimToEmpty(row.getFld001()))
                .filter(code -> !code.isEmpty()).distinct().collect(Collectors.toList());
        // null 安全 trim，空白返回空串
        List<String> selfSelected = submittedRows.stream().map(row -> trimToEmpty(row.getFld013()))
                .filter(code -> !code.isEmpty()).distinct().collect(Collectors.toList());
        // 从正式提交事务中批量复核本次实际提交行的评级主体
        BatchSecurityCheckData ratingData = batchSecurityCheckService.loadExcelRatingCandidates(codes, selfSelected);
        Set<Long> permittedPools = new HashSet<>();
        List<SecurityPoolAdjustSubmitReq> submitRequests = new ArrayList<>();
        // 4. 按来源行组装申请，并核对评级主体及目标池权限
        for (Map.Entry<Long, List<SecurityPoolExcelImportCheckItemDto>> entry : groupMap.entrySet()) {
            List<SecurityPoolExcelImportCheckItemDto> group = entry.getValue();
            // 同组优先取手工主项
            SecurityPoolExcelImportCheckItemDto primary = resolvePrimaryItem(group);
            SysImpTmpDetlBo source = sourceItems.get(entry.getKey());
            // null 安全 trim，空白返回空串
            String securityCode = trimToEmpty(source.getFld001());
            for (SecurityPoolExcelImportCheckItemDto ci : group) {
                // 复核校验结果的来源证券，主体选择只读取本批持久化明细
                if (!securityCode.equals(trimToEmpty(ci.getSourceSecurityCode()))
                        // 是否为手工调整项标签
                        || (isManualTag(ci.getItemTag())
                        // null 安全 trim，空白返回空串
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
                    // 校验当前用户是否具备目标池 excel_importable 权限
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
                // 判断字符串是否为 null 或空白
                si.setItemTag(ci == primary || isBlank(ci.getItemTag()) ? ItemType.MANUAL.getCode() : ci.getItemTag());
                si.setAdjustGroupKey(ci.getAdjustGroupKey());
                si.setFlowId(ci.getFlowId());
                si.setFlowKey(ci.getFlowKey());
                si.setFlowType(ci.getFlowType());
                si.setMaterialExemptionType(ci.getMaterialExemptionType());
                si.setAdjustmentNote(ci.getAdjustNote());
                submitItems.add(si);
            }
            // Excel 已按校验快照核对勾选项，仅提交实际选中的市场代码
            submitReq.setItems(submitItems);
            submitRequests.add(submitReq);
        }
        // 5. 批量提交并汇总调库日志 ID
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
     * 主体提交：按主体代码分组，逐组调用 Excel 提示放行入口
     *
     * 执行顺序：
     * 1. 只选配套项的来源行也需要由实际勾选项发起流程
     * 2. 主体导入按本次选择的流程提交，原业务失败已在校验结果中提示
     *
     * @param checkItems 已筛选为可提交的证券导入校验项，包含主项及展开项
     * @param batch 当前导入批次
     * @param opterId 经办人 ID
     * @param opterName 经办人名称
     * @return 主体导入列表
     */
    private List<Long> submitCompanyImport(List<SecurityPoolExcelImportCheckItemDto> checkItems,
                                           SysImpTmpBo batch, String opterId, String opterName) {
        // 1. 只选配套项的来源行也需要由实际勾选项发起流程
        Map<Long, SecurityPoolExcelImportCheckItemDto> primaryBySource = resolveSelectedPrimaryItems(checkItems);
        Map<String, List<SecurityPoolExcelImportCheckItemDto>> groupMap = new LinkedHashMap<>();
        for (SecurityPoolExcelImportCheckItemDto ci : checkItems) {
            // 判断字符串是否为 null 或空白
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
        // 查询当前用户可导入的投资池范围，供同批多个池复用
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
                // 判断字符串是否为 null 或空白
                si.setItemTag(ci == primaryBySource.get(ci.getSourceItemId()) || isBlank(ci.getItemTag())
                        ? ItemType.MANUAL.getCode() : ci.getItemTag());
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
        // 2. 主体导入按本次选择的流程提交，原业务失败已在校验结果中提示
        return batchCompanyAdjustSubmitService.submit(requests);
    }

    // ═══════════════════════════════════════════════════════════
    //  清空目标池（独立分支，与证券/主体并列）
    // ═══════════════════════════════════════════════════════════

    /**
     * 在同一批写入之前复核所有实际对象的在途和重复调入
     *
     * 执行顺序：
     * 1. 收集对象代码和目标池 ID，读取本次涉及的池及其配置
     * 2. 按对象代码分批读取在途顶级池组和已生效的在池状态
     * 3. 逐项阻止同顶级池组已有在途流程，以及已在池或本批重复的调入
     *
     * @param items 已勾选的校验项
     * @param importType 导入类型
     */
    private void validateImportConflicts(List<SecurityPoolExcelImportCheckItemDto> items, String importType) {
        // 1. 收集对象代码和目标池 ID，读取本次涉及的池及其配置
        List<String> codes = items.stream().map(SecurityPoolExcelImportCheckItemDto::getSecurityCode)
                .distinct().collect(Collectors.toList());
        List<Long> targetPoolIds = items.stream().map(SecurityPoolExcelImportCheckItemDto::getTargetPoolId)
                .distinct().sorted().collect(Collectors.toList());
        // 批量查询本次导入涉及的目标池，再检查在途和重复调入
        investmentPoolMapper.queryExistingPoolIdList(targetPoolIds);
        Map<Long, InvestmentPoolBo> pools = investmentPoolMapper.queryPoolList().stream()
                .collect(Collectors.toMap(InvestmentPoolBo::getId, pool -> pool));
        Map<String, Set<Long>> pendingGroups = new HashMap<>();
        Map<String, Set<Long>> activePools = new HashMap<>();
        int batchSize = AutoAdjustRestrictHelper.QUERY_BATCH_SIZE;
        // 2. 按对象代码分批读取在途顶级池组和已生效的在池状态
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
        String pendingProcessFailure = IMPORT_TYPE_COMPANY.equals(importType)
                ? ForbiddenPoolAdjustService.COMPANY_PENDING_PROCESS_FAILURE : SecurityPoolCheckRules.PENDING_PROCESS_FAILURE;
        // 3. 逐项阻止同顶级池组已有在途流程，以及已在池或本批重复的调入
        for (SecurityPoolExcelImportCheckItemDto item : items) {
            String code = item.getSecurityCode().toUpperCase(Locale.ROOT);
            Long poolId = item.getTargetPoolId();
            if (pendingGroups.getOrDefault(code, Collections.emptySet())
                    .contains(SecurityPoolCheckRules.resolveRootPoolId(poolId, pools))) {
                throw new BizException(item.getSecurityCode() + " / " + item.getTargetPoolName()
                        + "：" + pendingProcessFailure + "，请重新校验");
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
     * 执行顺序：
     * 1. 先查当前用户允许清空的债券叶子池
     * 2. 有符合条件的池时统计各池当前成员数，否则返回空列表
     *
     * @param req 当前操作人
     * @return 可清空池列表
     */
    public List<PoolClearSummaryDto> queryClearPoolList(SecurityPoolExcelImportReq req) {
        // 查询启用且有权限的债券业务叶子池
        Map<Long, InvestmentPoolBo> pools = queryClearablePools(req.getCurrentUserId());
        return pools.isEmpty() ? Collections.emptyList()
                : securityPoolExcelImportMapper.queryClearPoolSummaryList(new ArrayList<>(pools.keySet()));
    }

    /**
     * 按单笔出库规则校验所选池的全部有效成员，全部通过后直通生效
     *
     * 执行顺序：
     * 1. 查询所选池后再批量读取本次要处理的成员
     * 2. 独立清空沿用单笔出库的报告要求，批量核对已有报告豁免
     * 3. 同一实际对象和池只执行一次，优先保留直接清空主项
     *
     * @param req 所选叶子池和操作人
     * @return 已生效的调库日志 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> clearPools(SecurityPoolExcelImportReq req) {
        if (req.getPoolIds() == null || req.getPoolIds().isEmpty()) {
            throw new BizException("请选择需要清空的投资池");
        }
        // 查询启用且有权限的债券业务叶子池
        Map<Long, InvestmentPoolBo> poolMap = queryClearablePools(req.getCurrentUserId());
        List<Long> poolIds = new ArrayList<>(new LinkedHashSet<>(req.getPoolIds()));
        if (!poolMap.keySet().containsAll(poolIds)) {
            throw new BizException("所选投资池不存在、已停用或无操作权限");
        }
        Collections.sort(poolIds);
        // 1. 查询所选池后再批量读取本次要处理的成员
        investmentPoolMapper.queryExistingPoolIdList(poolIds);
        // 批量加载清空成员和所需的单笔出库校验数据
        List<SecurityPoolExcelImportCheckItemDto> items = checkClearTargetOutbound(poolIds, poolMap);
        // 2. 独立清空沿用单笔出库的报告要求，批量核对已有报告豁免
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

        // 3. 同一实际对象和池只执行一次，优先保留直接清空主项
        Map<List<Object>, SecurityPoolExcelImportCheckItemDto> distinct = new LinkedHashMap<>();
        for (SecurityPoolExcelImportCheckItemDto item : items) {
            List<Object> key = Arrays.asList(IMPORT_TYPE_COMPANY.equals(item.getSecurityType()),
                    item.getSecurityCode().toUpperCase(Locale.ROOT), item.getTargetPoolId(), item.getAdjustMode());
            SecurityPoolExcelImportCheckItemDto previous = distinct.putIfAbsent(key, item);
            // 是否为清空出库项标签
            if (previous != null && isClearTag(item.getItemTag())) {
                distinct.put(key, item);
            }
        }
        String userId = req.getCurrentUserId().trim();
        // 判断字符串是否为 null 或空白
        String userName = isBlank(req.getCurrentUserName()) ? userId : req.getCurrentUserName().trim();
        // 按来源对象类型和代码批量提交清空出库，统一使用无需审核审批流程
        return submitClearTargetOutbound(new ArrayList<>(distinct.values()), userId, userName);
    }

    /**
     * 按单笔出库口径核对报告必填以及六个月历史报告豁免
     *
     * 执行顺序：
     * 1. 按ID 列表列表查询投资池
     * 2. 按单笔出库口径核对报告必填以及六个月历史报告豁免
     *
     * @param items 已勾选的校验项
     */
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
                // 判断字符串是否为 null 或空白
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

    /**
     * 查询启用且有权限的债券业务叶子池
     *
     * 执行顺序：
     * 1. 查询当前用户可导入的投资池范围，供同批多个池复用
     * 2. 查询投资池列表
     *
     * @param currentUserId 当前用户 ID
     * @return 整理后的键值映射
     */
    private Map<Long, InvestmentPoolBo> queryClearablePools(String currentUserId) {
        // 查询当前用户可导入的投资池范围，供同批多个池复用
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

    /**
     * 批量加载清空成员和所需的单笔出库校验数据
     *
     * 执行顺序：
     * 1. 批量读取待清空池的当前成员，并按池归集
     * 2. 整理主体校验范围，并为证券成员建立逐券逐池的调出校验请求
     * 3. 分别加载主体和证券清空校验需要的共用数据
     * 4. 按原成员顺序逐项执行调出校验并组装结果
     *
     * @param clearPoolIds 本批待清空的目标池 ID 列表
     * @param poolMap 当前投资池映射
     * @return 按清空池成员顺序返回的调出校验明细，没有成员时为空列表
     */
    private List<SecurityPoolExcelImportCheckItemDto> checkClearTargetOutbound(
            List<Long> clearPoolIds, Map<Long, InvestmentPoolBo> poolMap) {
        List<SecurityPoolExcelImportCheckItemDto> clearItems = new ArrayList<>();
        // 1. 批量读取待清空池的当前成员，并按池归集
        Map<Long, List<PoolMemberDto>> membersByPool = securityPoolExcelImportMapper.queryPoolMemberList(clearPoolIds)
                .stream().collect(Collectors.groupingBy(PoolMemberDto::getTargetPoolId));
        Set<String> clearCompanyCodes = new LinkedHashSet<>();
        Set<Long> clearCompanyPools = new HashSet<>();
        Map<Long, List<PoolMemberDto>> clearMembers = new LinkedHashMap<>();
        List<AdjustCheckReq> securityRequests = new ArrayList<>();
        // 2. 整理主体校验范围，并为证券成员建立逐券逐池的调出校验请求
        for (Long poolId : clearPoolIds) {
            InvestmentPoolBo pool = poolMap.get(poolId);
            for (PoolMemberDto member : membersByPool.getOrDefault(poolId, Collections.emptyList())) {
                // 判断字符串是否为 null 或空白
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
                    // 构建证券导入或清空出库的一券一池校验项
                    securityRequests.add(buildExcelSecurityCheck(code, member.getSecurityShortName(),
                            pool, AdjustMode.OUT.getCode()));
                }
            }
        }

        // 3. 分别加载主体和证券清空校验需要的共用数据
        CompanyAdjustCheckContext companyData = clearCompanyCodes.isEmpty() ? null
                : batchCompanyAdjustCheckService.loadClear(new ArrayList<>(clearCompanyCodes), clearCompanyPools);
        BatchSecurityCheckData data = batchSecurityCheckService.loadExcelCheckData(securityRequests, true);
        int securityIndex = 0;
        // 4. 按原成员顺序逐项执行调出校验并组装结果
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
     *
     * 执行顺序：
     * 1. 主体成员使用本批主体数据校验调出，证券成员先恢复评级主体再执行单券调出校验
     * 2. 校验抛出业务异常或没有返回调整项时，返回对应的清空出库失败项
     * 3. 转换各项校验结果，将手工主项标为清空调出并保留配套项的原标签
     *
     * @param member 该池中的生效成员记录
     * @param pool 已加载的目标池
     * @param request 清空证券的校验请求
     * @param data 已加载的批次数据
     * @param companyData 主体数据
     * @return 证券投资池Excel 导入校验明细列表
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
            // 判断字符串是否为 null 或空白
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
     *
     * 执行顺序：
     * 1. 依次设置证券代码、证券简称、证券类型、来源证券代码等字段
     *
     * @param code 证券代码
     * @param shortName 简称
     * @param securityType 页面已知证券类型
     * @param pool 已加载的目标池
     * @param reason 本次校验产生的原因或提示
     * @return 证券投资池Excel 导入校验明细
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
     * 执行顺序：
     * 1. 使用已发布的固定流程并确认可以直接完成
     * 2. 按对象类型和来源代码分组，证券关联项跟随主券提交
     * 3. 清空组共用本次查出的主档和关联候选
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
        // 1. 使用已发布的固定流程并确认可以直接完成
        FlowDefinitionBo clearFlow = flowMapper.queryActiveFlowByKey(CLEAR_POOL_FLOW_KEY);
        if (clearFlow == null || clearFlow.getId() == null
                || !securityPoolAdjustService.isDirectAdjustFlow(clearFlow.getId(), CLEAR_POOL_FLOW_KEY)) {
            throw new BizException("清空目标池需要已发布且可直通的「无需审核审批流程」，请检查流程配置");
        }

        // 2. 按对象类型和来源代码分组，证券关联项跟随主券提交
        Map<List<Object>, List<SecurityPoolExcelImportCheckItemDto>> groupMap = new LinkedHashMap<>();
        for (SecurityPoolExcelImportCheckItemDto ci : clearItems) {
            // 判断字符串是否为 null 或空白
            String sourceCode = !isBlank(ci.getSourceSecurityCode())
                    ? ci.getSourceSecurityCode() : ci.getSecurityCode();
            boolean companyMember = IMPORT_TYPE_COMPANY.equals(ci.getSecurityType());
            groupMap.computeIfAbsent(Arrays.asList(companyMember, sourceCode), key -> new ArrayList<>()).add(ci);
        }
        List<String> securityCodes = groupMap.keySet().stream().filter(key -> Boolean.FALSE.equals(key.get(0)))
                // 判断字符串是否为 null 或空白
                .map(key -> (String) key.get(1)).filter(code -> !isBlank(code)).distinct().collect(Collectors.toList());
        // 3. 清空组共用本次查出的主档和关联候选
        BatchSecurityCheckData ratingData = securityCodes.isEmpty() ? new BatchSecurityCheckData()
                : batchSecurityCheckService.loadExcelRatingCandidates(securityCodes, Collections.emptyList());
        List<String> absCodes = ratingData.securities.values().stream().filter(CreditBondSpecialInboundRule::isAbs)
                .map(SecurityInfoBo::getWindCode).collect(Collectors.toList());
        Map<String, SecurityInfoBo> identities = absCodes.isEmpty() ? Collections.emptyMap()
                : absIdentityService.queryCurrentIdentities(absCodes);
        // 查询当前用户可导入的投资池范围，供同批多个池复用
        Set<Long> permittedPools = queryExcelImportablePoolIds(opterId);
        List<SecurityPoolAdjustSubmitReq> securityRequests = new ArrayList<>();
        List<ForbiddenPoolAdjustSubmitReq> companyRequests = new ArrayList<>();
        List<Long> allLogIds = new ArrayList<>();
        for (List<SecurityPoolExcelImportCheckItemDto> group : groupMap.values()) {
            // 同组优先取清空/手工主项
            SecurityPoolExcelImportCheckItemDto primary = resolvePrimaryItem(group);
            // 判断字符串是否为 null 或空白
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
                    // 判断字符串是否为 null 或空白
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
                    // 判断字符串是否为 null 或空白
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
                    // 判断字符串是否为 null 或空白
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
     * 映射证券校验结果，并对可调整手工项注入目标池「批量」流程（对齐批量 buildBatchCheckResult）
     *
     * 执行顺序：
     * 1. 存在来源导入行时填写明细 ID、行号及有效主体信息，ABS 自选权益人优先于关联主体展示
     * 2. 映射证券、来源证券、目标池、方向和项目标签，缺少证券代码时使用本次校验的来源证券代码
     * 3. 用来源证券代码、来源明细 ID 和原校验分组键构造新分组键，区分同码不同导入行；清空出库项无来源明细，因此保留不含明细 ID 的分组形式
     * 4. 复制可调整状态和失败原因，生成调整类型及可调整项的调整说明
     * 5. 映射原有流程候选，对可调整手工项补充目标池批量调入/调出流程，并默认选择推荐或首个可选流程
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
        // 1. 填写原导入行及选中的评级主体信息
        SecurityPoolExcelImportCheckItemDto dto = new SecurityPoolExcelImportCheckItemDto();
        if (sourceItem != null) {
            dto.setSourceItemId(sourceItem.getId());
            dto.setSourceRowNo(sourceItem.getRowNo());
            // 判断字符串是否为 null 或空白
            dto.setRatingCompanyCode(isBlank(sourceItem.getFld013())
                    ? sourceItem.getFld011() : sourceItem.getFld013());
            // 判断字符串是否为 null 或空白
            dto.setRatingCompanyName(isBlank(sourceItem.getFld013())
                    ? sourceItem.getFld012() : sourceItem.getFld014());
        }
        // 2. 解析实际证券、来源证券、目标池、方向和标签
        String securityCode = !isBlank(ri.getSecurityCode()) ? ri.getSecurityCode() : excelCode;
        // 判断字符串是否为 null 或空白
        String sourceCode = !isBlank(ri.getSourceSecurityCode()) ? ri.getSourceSecurityCode() : excelCode;
        dto.setSecurityCode(securityCode);
        dto.setSecurityShortName(ri.getSecurityShortName());
        dto.setSecurityType(ri.getSecurityType());
        dto.setSourceSecurityCode(sourceCode);
        dto.setTargetPoolId(ri.getTargetPoolId());
        dto.setTargetPoolName(ri.getPoolName());
        dto.setPoolType(ri.getPoolType());
        dto.setAdjustMode(ri.getAdjustMode());
        // 判断字符串是否为 null 或空白
        dto.setItemTag(isBlank(ri.getItemTag()) ? ItemType.MANUAL.getCode() : ri.getItemTag());
        // 3. 按来源行生成分组键，复制校验结论与原因
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

        // 4. 组装流程候选并默认选中推荐或首个可选流程
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
     *
     * 执行顺序：
     * 1. 解析调整类型中文
     * 2. 默认选中推荐/首个可选流程
     *
     * @param sourceItem 来源导入行，自动生成的清空出库项为 null
     * @param ri 完整单券校验返回的一条结果
     * @param excelCode 本次校验的来源证券代码，作为缺失代码的回填值
     * @param pool 已加载的目标池
     * @return 证券投资池Excel 导入校验明细
     */
    private SecurityPoolExcelImportCheckItemDto mapCompanyCheckResult(
            SysImpTmpDetlBo sourceItem, AdjustCheckDto.CheckResultItem ri,
            String excelCode, InvestmentPoolBo pool) {
        SecurityPoolExcelImportCheckItemDto dto = new SecurityPoolExcelImportCheckItemDto();
        if (sourceItem != null) {
            dto.setSourceItemId(sourceItem.getId());
            dto.setSourceRowNo(sourceItem.getRowNo());
        }
        // 判断字符串是否为 null 或空白
        String securityCode = !isBlank(ri.getSecurityCode()) ? ri.getSecurityCode() : excelCode;
        // 判断字符串是否为 null 或空白
        String sourceCode = !isBlank(ri.getSourceSecurityCode()) ? ri.getSourceSecurityCode() : excelCode;
        dto.setSecurityCode(securityCode);
        dto.setSecurityShortName(ri.getSecurityShortName());
        dto.setSecurityType(ri.getSecurityType() != null ? ri.getSecurityType() : "company");
        dto.setSourceSecurityCode(sourceCode);
        dto.setTargetPoolId(ri.getTargetPoolId());
        dto.setTargetPoolName(ri.getPoolName());
        dto.setPoolType(ri.getPoolType());
        dto.setAdjustMode(ri.getAdjustMode());
        // 判断字符串是否为 null 或空白
        dto.setItemTag(isBlank(ri.getItemTag()) ? ItemType.MANUAL.getCode() : ri.getItemTag());
        String gk = ri.getAdjustGroupKey();
        dto.setAdjustGroupKey(sourceCode + "_" + (gk == null ? "" : gk));
        dto.setCanAdjust(ri.isCanAdjust());
        dto.setFailReasons(ri.getFailReasons() == null ? new ArrayList<>() : new ArrayList<>(ri.getFailReasons()));
        // 1. 解析调整类型中文
        dto.setAdjustType(resolveAdjustTypeLabel(dto.getItemTag()));
        if (dto.isCanAdjust()) {
            dto.setAdjustNote(dto.getAdjustType() + (dto.getAdjustMode() == null ? "" : dto.getAdjustMode()));
            List<String> warnings = ri.getWarnings();
            if (warnings != null && !warnings.isEmpty()) {
                // 可调整项沿用证券 Excel 的提示列表，保留原校验原因并去重
                Set<String> tips = new LinkedHashSet<>(dto.getFailReasons());
                tips.addAll(warnings);
                dto.setFailReasons(new ArrayList<>(tips));
            }
        }

        List<SecurityPoolExcelImportCheckItemDto.FlowOptionDto> flowOptions = new ArrayList<>();
        if (ri.getFlowOptions() != null) {
            for (AdjustCheckDto.FlowOption fo : ri.getFlowOptions()) {
                // 映射单条流程候选项
                flowOptions.add(toFlowOptionDto(fo));
            }
        }
        // 是否为手工调整项标签
        if (dto.isCanAdjust() && isManualTag(dto.getItemTag())) {
            boolean inbound = AdjustMode.IN.getCode().equals(dto.getAdjustMode());
            // 追加目标池对应方向的批量流程候选
            injectBatchFlowOption(flowOptions, pool, inbound ? "in" : "out");
        }
        dto.setFlowOptions(flowOptions);
        // 2. 默认选中推荐/首个可选流程
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
     * 清空出库成员使用已确认主体，只有无占用的 ABS 使用首个权益人
     * Excel 导入行使用页面选择的主体
     *
     * 执行顺序：
     * 1. 仅对 ABS 读取在库及在途身份，发现多个占用主体时提示冲突
     * 2. 未占用时取首个关联主体，有占用时按关联或自选身份回填实际主体
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
                // 判断字符串是否为 null 或空白
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
     * 执行顺序：
     * 1. 定位本行对应主档，只有 ABS 才从本批加载的候选中选择
     * 2. 未确认时取首选权益人，已有身份时按关联或自选类型写回请求
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
        if (!CreditBondSpecialInboundRule.isAbs(master)) {
            return;
        }
        List<RelatedRatingCompanyDto> candidates = data.ratingCompanies.getOrDefault(code, Collections.emptyList());
        // 空白串转为 null
        String first = candidates.isEmpty() ? null : trimToNull(candidates.get(0).getCompanyCode());
        SecurityInfoBo identity = identities.get(code);
        if (identity == null) {
            req.setRightsHolderCode(first);
        } else if (candidates.stream().anyMatch(row -> identity.getIssuerCode().equals(row.getCompanyCode()))) {
            req.setRightsHolderCode(identity.getIssuerCode());
        } else {
            req.setSelfSelectedRightsHolderCode(identity.getIssuerCode());
        }
    }

    /**
     * 保存证券 Excel 导入一行已核实的评级主体
     *
     * 保留关联主体、自选权益人和最终评级主体的代码，以及证券主档、本次采用的名称与评级
     */
    private static class RatingCompany {
        /** 是否 ABS 证券 */
        private boolean abs;

        /** 评级主体编码 */
        private String companyCode;

        /** 页面选择的关联担保人或权益人编码 */
        private String relatedCompanyCode;

        /** 页面选择的 ABS 自选权益人编码 */
        private String selfSelectedRightsHolderCode;

        /** 本行已读取的证券信息 */
        private SecurityInfoBo securityInfo;

        /** 本行已经取得的主体名称与评级 */
        private RatingCompanySelectionReq selection;
    }

    /**
     * 根据批量候选复核本行选择，并回填服务端标准名称
     *
     * 执行顺序：
     * 1. 使用本批已读的证券和候选执行与提交相同的主体校验
     *
     * @param req 调库校验请求
     * @param item 当前调整项
     * @param data 已加载的批次数据
     */
    private void applyImportedRatingCompany(AdjustCheckReq req, SysImpTmpDetlBo item, BatchSecurityCheckData data) {
        String code = req.getSecurityCode();
        // 使用本批已读的证券和候选执行与提交相同的主体校验
        RatingCompany company = resolveImportedRatingCompany(item, true, data.securities.get(code),
                data.ratingCompanies.getOrDefault(code, Collections.emptyList()),
                // null 安全 trim，空白返回空串
                () -> data.selfSelectedCompanies.get(trimToEmpty(item.getFld013())));
        if (company == null) {
            return;
        }
        req.setSecurityInfo(company.securityInfo);
        req.setSelectedRatingCompany(company.selection);
        if (company.abs) {
            req.setGuarantorCode(item.getFld020());
            req.setRightsHolderCode(company.relatedCompanyCode);
            req.setSelfSelectedRightsHolderCode(company.selfSelectedRightsHolderCode);
        } else {
            req.setGuarantorCode(company.companyCode);
        }
    }

    /**
     * 将页面持久化的主体选择传入单券提交
     *
     * 执行顺序：
     * 1. 同券多行保留各行自己的主体选择和证券快照
     *
     * @param req 当前证券的清空出库请求
     * @param item 当前调整项
     * @param data 已加载的批次数据
     */
    private void applyImportedRatingCompany(SecurityPoolAdjustSubmitReq req, SysImpTmpDetlBo item,
                                            BatchSecurityCheckData data) {
        // null 安全 trim，空白返回空串
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
                // null 安全 trim，空白返回空串
                () -> data.selfSelectedCompanies.get(trimToEmpty(item.getFld013())));
        if (company == null) {
            return;
        }
        req.setSecurityInfo(company.securityInfo);
        req.setSelectedRatingCompany(company.selection);
        if (company.abs) {
            req.setGuarantorCode(item.getFld020());
            req.setRightsHolderCode(company.relatedCompanyCode);
            req.setSelfSelectedRightsHolderCode(company.selfSelectedRightsHolderCode);
        } else {
            req.setGuarantorCode(company.companyCode);
        }
    }

    /**
     * 按传入的证券与候选校验本行选择，回填名称及评级
     *
     * 执行顺序：
     * 1. 缺少证券主档时返回空值
     * 2. 核对所选的关联主体和自选权益人
     * 3. 有担保的 ABS 还需单独校验所选担保人
     * 4. 回填已核实的选择，自选权益人优先作为评级主体
     *
     * @param item 当前调整项
     * @param requireSelection 是否执行主体必填检查
     * @param securityInfo 标的证券资料
     * @param companies 主体列表
     * @param lookupSelfSelected 按需提供所需数据的函数
     * @return 已核实的评级主体及标的证券，证券主档不存在时返回 null
     */
    private RatingCompany resolveImportedRatingCompany(SysImpTmpDetlBo item, boolean requireSelection,
            SecurityInfoBo securityInfo, List<RelatedRatingCompanyDto> companies,
            Supplier<SelfSelectedRightsHolderDto> lookupSelfSelected) {
        // 1. 缺少证券主档时返回空值
        if (securityInfo == null) {
            return null;
        }
        // 2. 核对所选的关联主体和自选权益人
        boolean abs = CreditBondSpecialInboundRule.isAbs(securityInfo);
        boolean guaranteed = Integer.valueOf(1).equals(securityInfo.getGuarantFlag());
        // null 安全 trim，空白返回空串
        String code = abs || guaranteed ? trimToEmpty(item.getFld011()) : "";
        // null 安全 trim，空白返回空串
        String selfCode = trimToEmpty(item.getFld013());
        RelatedRatingCompanyDto selected = null;
        if (companies != null) {
            for (RelatedRatingCompanyDto company : companies) {
                // 判断字符串是否为 null 或空白
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
                // 判断字符串是否为 null 或空白
                && companies.stream().anyMatch(company -> !isBlank(company.getCompanyCode()));
        // 3. 有担保的 ABS 还需单独校验所选担保人
        String absGuarantorCode = abs && guaranteed ? trimToNull(item.getFld020()) : null;
        if (absGuarantorCode != null && (!hasRelated
                || companies.stream().noneMatch(company -> absGuarantorCode.equals(company.getCompanyCode())))) {
            throw new BizException("所选担保人不属于当前证券，请重新选择");
        }
        if (requireSelection && abs && guaranteed && hasRelated && absGuarantorCode == null) {
            throw new BizException("请选择担保人");
        }
        item.setFld020(absGuarantorCode);
        if (requireSelection && selected == null && selfSelected == null && (abs || (guaranteed && hasRelated))) {
            throw new BizException(abs ? "请选择权益人或自选权益人" : "请选择担保人");
        }
        // 4. 回填已核实的选择，自选权益人优先作为评级主体
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

    /**
     * 将证券调库流程候选项映射为导入页流程 DTO
     *
     * 执行顺序：
     * 1. 流程名为空或像编码时，按流程类型回退中文展示名（禁止直接展示 flowKey）
     * 2. 生成流程 optionKey
     *
     * @param fo 共用调库校验返回的流程候选
     * @return 流程选项
     */
    private SecurityPoolExcelImportCheckItemDto.FlowOptionDto toFlowOptionDto(AdjustCheckDto.FlowOption fo) {
        SecurityPoolExcelImportCheckItemDto.FlowOptionDto opt =
                new SecurityPoolExcelImportCheckItemDto.FlowOptionDto();
        opt.setFlowId(fo.getFlowId());
        opt.setFlowKey(fo.getFlowKey());
        // 1. 流程名为空或像编码时，按流程类型回退中文展示名（禁止直接展示 flowKey）
        String name = fo.getFlowName();
        // 判断字符串是否为 null 或空白，再判断文本是否像流程编码（非中文展示名）
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
        // 2. 生成流程 optionKey
        opt.setOptionKey(buildFlowOptionKey(opt));
        return opt;
    }

    /**
     * 为可调整项默认选中推荐流程（或首个可选流程）
     *
     * 执行顺序：
     * 1. 仅处理可调整且存在流程候选的导入项
     * 2. 优先选中推荐且可选的流程，否则使用首个可选流程
     * 3. 保存选中流程的标识，并标记无流程的直通项
     *
     * @param dto 待回填或返回的数据对象
     */
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
        // 判断字符串是否为 null 或空白
        dto.setDirectFlow(selected.getFlowId() == null && isBlank(selected.getFlowKey()));
    }

    // ═══════════════════════════════════════════════════════════
    //  工具
    // ═══════════════════════════════════════════════════════════

    /**
     * 按父池名称 + 子池名称解析启用叶子池（带缓存）
     *
     * 执行顺序：
     * 1. 根据父池和子池名称查找本批缓存
     * 2. 查询启用的叶子投资池，并把查询结果写入缓存
     *
     * @param parentName Excel 行填写的父池名称，可为空
     * @param childName Excel 行填写的叶子投资池名称
     * @param securityImport 是否证券导入
     * @param cache 按文本索引的待处理的投资池记录
     * @return 待处理的投资池记录
     */
    private InvestmentPoolBo resolvePoolByNames(String parentName, String childName, boolean securityImport,
                                                Map<String, InvestmentPoolBo> cache) {
        // 1. 根据父池和子池名称查找本批缓存
        String cacheKey = trimToEmpty(parentName) + "||" + trimToEmpty(childName);
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }
        // 2. 查询启用的叶子投资池，并把查询结果写入缓存
        InvestmentPoolBo pool = securityPoolExcelImportMapper.queryEnabledLeafPoolByParentAndChildName(
                // 判断字符串是否为 null 或空白
                isBlank(parentName) ? null : parentName, childName, securityImport);
        cache.put(cacheKey, pool);
        return pool;
    }

    /**
     * 在本批明细上记录校验失败，随其他行一起保存
     *
     * 执行顺序：
     * 1. 拼接失败原因
     *
     * @param item 当前调整项
     * @param reasons 用于展示或保存的失败原因
     * @param now 回写行级校验结果使用的统一时间
     */
    private void markItemFail(SysImpTmpDetlBo item, List<String> reasons, Date now) {
        item.setChkRslt("2");
        // 拼接失败原因
        item.setChkDscr(joinReasons(reasons));
        item.setUpdtTime(now);
    }

    /**
     * 构建前置失败的手工校验结果项（不调用下游调库校验）
     *
     * 执行顺序：
     * 1. 依次设置来源明细ID、评级主体代码、评级主体名称、证券代码等字段
     *
     * @param sourceItem 来源导入行，自动生成的清空出库项为 null
     * @param code 证券代码
     * @param shortName 简称
     * @param securityType 页面已知证券类型
     * @param pool 已加载的目标池
     * @param adjustMode 调入或调出方向
     * @param reasons 用于展示或保存的失败原因
     * @return 证券投资池Excel 导入校验明细
     */
    private SecurityPoolExcelImportCheckItemDto buildFailManualItem(
            SysImpTmpDetlBo sourceItem, String code, String shortName, String securityType,
            InvestmentPoolBo pool, String adjustMode, List<String> reasons) {
        SecurityPoolExcelImportCheckItemDto dto = new SecurityPoolExcelImportCheckItemDto();
        if (sourceItem != null) {
            dto.setSourceItemId(sourceItem.getId());
            dto.setSourceRowNo(sourceItem.getRowNo());
            // 判断字符串是否为 null 或空白
            dto.setRatingCompanyCode(isBlank(sourceItem.getFld013())
                    ? sourceItem.getFld011() : sourceItem.getFld013());
            // 判断字符串是否为 null 或空白
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

    /**
     * 按校验快照核对勾选项并读取其审批流程选择
     *
     * 执行顺序：
     * 1. 按来源行、对象、池、方向和类型核对逐条勾选的调整项
     * 2. 按快照顺序保留已勾选的可调整项，采用已选手工项的流程
     * 3. 仅勾选配套项的来源行由首个已选项使用来源主项的默认流程
     *
     * @param req 携带勾选项的提交请求
     * @param batch 已保存校验结果的导入批次
     * @return 本次实际提交的调整项
     */
    private List<SecurityPoolExcelImportCheckItemDto> filterSubmittableCheckItems(
            SecurityPoolExcelImportReq req, SysImpTmpBo batch) {
        // 1. 从校验快照读取可用调整项
        List<SecurityPoolExcelImportCheckItemDto> snapshot = parseCheckItems(batch.getResultJson());
        if (snapshot == null) {
            throw new BizException("校验结果缺失，请重新校验");
        }
        // 2. 检查已勾选的项并按快照顺序匹配
        List<SecurityPoolExcelImportCheckItemDto> requested = req.getCheckItems();
        if (requested == null || requested.isEmpty()) {
            throw new BizException("请先勾选要提交的调库项");
        }
        Map<List<Object>, SecurityPoolExcelImportCheckItemDto> submitted = new HashMap<>();
        for (SecurityPoolExcelImportCheckItemDto item : requested) {
            // 定位需要回写流程的校验行
            if (item == null || !item.isCanAdjust() || submitted.putIfAbsent(flowSelectionRowKey(item), item) != null) {
                throw new BizException("提交项与来源导入明细不一致或未通过校验，请重新校验");
            }
        }
        List<SecurityPoolExcelImportCheckItemDto> result = new ArrayList<>();
        Map<Long, SecurityPoolExcelImportCheckItemDto> savedManualBySource = new HashMap<>();
        for (SecurityPoolExcelImportCheckItemDto item : snapshot) {
            if (!item.isCanAdjust() || item.getSourceItemId() == null
                    // 判断字符串是否为 null 或空白
                    || isBlank(item.getSecurityCode()) || item.getTargetPoolId() == null) {
                continue;
            }
            // 是否为手工调整项标签
            if (isManualTag(item.getItemTag())) {
                savedManualBySource.put(item.getSourceItemId(), item);
            }
            // 同一复选框只匹配服务端快照里的对应明细
            SecurityPoolExcelImportCheckItemDto selection = submitted.remove(flowSelectionRowKey(item));
            if (selection == null) {
                continue;
            }
            // 是否为手工调整项标签
            if (isManualTag(item.getItemTag())) {
                item.setFlowId(selection.getFlowId());
                item.setFlowKey(selection.getFlowKey());
                item.setFlowType(selection.getFlowType());
                item.setSelectedFlowKey(selection.getSelectedFlowKey());
            }
            result.add(item);
        }
        if (!submitted.isEmpty()) {
            throw new BizException("提交项与来源导入明细不一致或未通过校验，请重新校验");
        }
        // 3. 仅勾选配套项时沿用对应手工主项的默认流程
        for (SecurityPoolExcelImportCheckItemDto primary : resolveSelectedPrimaryItems(result).values()) {
            // 是否为手工调整项标签
            if (isManualTag(primary.getItemTag())) {
                continue;
            }
            SecurityPoolExcelImportCheckItemDto original = savedManualBySource.get(primary.getSourceItemId());
            // 判断字符串是否为 null 或空白
            if (original == null || isBlank(original.getSelectedFlowKey())) {
                throw new BizException("配套项的来源主项没有可用的默认审批流程，请重新校验");
            }
            primary.setFlowId(original.getFlowId());
            primary.setFlowKey(original.getFlowKey());
            primary.setFlowType(original.getFlowType());
        }
        return result;
    }

    /**
     * 为每条已选来源行确定实际发起流程的项
     *
     * 执行顺序：
     * 1. 优先使用已勾选的手工项，其次使用该来源行首个已勾选项
     *
     * @param items 已勾选的校验项
     * @return Excel 来源行 ID 对应的流程主项
     */
    private Map<Long, SecurityPoolExcelImportCheckItemDto> resolveSelectedPrimaryItems(
            List<SecurityPoolExcelImportCheckItemDto> items) {
        Map<Long, SecurityPoolExcelImportCheckItemDto> primaryBySource = new LinkedHashMap<>();
        for (SecurityPoolExcelImportCheckItemDto item : items) {
            // 是否为手工调整项标签
            if (isManualTag(item.getItemTag())) {
                primaryBySource.put(item.getSourceItemId(), item);
            } else {
                primaryBySource.putIfAbsent(item.getSourceItemId(), item);
            }
        }
        return primaryBySource;
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

    /**
     * 同组中优先取手工项/清空项作为主项
     *
     * 执行顺序：
     * 1. 顺序取第一个允许选择流程的手工或清空项，全部未匹配时取组内第一项
     *
     * @param items 已勾选的校验项
     * @return 证券投资池Excel 导入校验明细
     */
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

    /**
     * 是否为手工调整项标签
     *
     * 执行顺序：
     * 1. 检查调整项标签，未填写和标为手工的项都按手工项处理
     *
     * @param itemTag 手工、联动或互斥来源
     * @return true=手工项标签或未填写标签，false=其他调库项标签
     */
    private boolean isManualTag(String itemTag) {
        return itemTag == null || itemTag.isEmpty() || ItemType.MANUAL.getCode().equals(itemTag);
    }

    /**
     * 是否为清空出库项标签
     *
     * 执行顺序：
     * 1. 将调整项标签与清空出库类型的代码比较
     *
     * @param itemTag 手工、联动或互斥来源
     * @return true=清空出库项，false=不是清空出库项
     */
    private boolean isClearTag(String itemTag) {
        return ItemType.CLEAR.getCode().equals(itemTag);
    }

    /**
     * 是否需选择审批流程的主项（手工 / 清空）
     *
     * 执行顺序：
     * 1. 把手工项和清空出库项列为需要选择流程的主项
     *
     * @param itemTag 手工、联动或互斥来源
     * @return true=手工项或清空项，需要选择审批流程，false=其他调库项，无需选择审批流程
     */
    private boolean isFlowSelectableTag(String itemTag) {
        // 判断是否为手工项或清空出库项
        return isManualTag(itemTag) || isClearTag(itemTag);
    }

    /**
     * 将 itemTag 转为前端展示的调整类型中文
     *
     * 对齐批量页「手动批量调整」的渠道专属口径：Excel 手工主项展示/落库均为「Excel导入」，
     * 清空主项为「清空目标池」；联动/互斥/关联与单笔、批量一致
     *
     * 执行顺序：
     * 1. 手工主项：不用单笔「手工调整」，与提交 adjust_type、字典 DICT_ADJUST_TYPE 一致
     *
     * @param itemTag 手工、联动或互斥来源
     * @return 调整来源文字
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

    /**
     * 解析明细中的目标池 ID
     *
     * 执行顺序：
     * 1. 文本为空时返回 null，否则去掉首尾空白后转为 Long，格式无效时仍返回 null
     *
     * @param text 待处理的文本
     * @return 数字格式的池 ID，无法解析时为 null
     */
    private Long parsePoolId(String text) {
        // 判断字符串是否为 null 或空白
        if (isBlank(text)) {
            return null;
        }
        try {
            return Long.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 构建流程候选项唯一键（flowType_flowId_flowKey）
     *
     * 执行顺序：
     * 1. 获取流程类型
     *
     * @param opt 流程选项
     * @return 由流程类型、ID 和 Key 拼成的候选键，类型或 Key 缺失时对应部分留空
     */
    private String buildFlowOptionKey(SecurityPoolExcelImportCheckItemDto.FlowOptionDto opt) {
        return (opt.getFlowType() == null ? "" : opt.getFlowType()) + "_"
                + (opt.getFlowId() == null ? "null" : opt.getFlowId()) + "_"
                + (opt.getFlowKey() == null ? "" : opt.getFlowKey());
    }

    /**
     * 流程类型 code 转可读中文名称
     *
     * 执行顺序：
     * 1. 将批量、标准、白名单等已知流程类型编码映射到中文名称
     * 2. 编码为空或未匹配时统一显示审批流程
     *
     * @param flowType 流程类型编码
     * @return 流程类型对应的默认名称
     */
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

    /**
     * 判断文本是否像流程编码（非中文展示名）
     *
     * 执行顺序：
     * 1. 空文本视为编码，非空时仅接受英文字母、数字、下划线、点、冒号或短横线
     *
     * @param text 待处理的文本
     * @return true=文本为空或符合编码格式，false=文本不符合编码格式
     */
    private boolean looksLikeCode(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        return text.matches("^[a-zA-Z0-9_.:\\-]+$") && !text.matches(".*[\\u4e00-\\u9fa5].*");
    }

    /**
     * 校验当前用户是否具备目标池 excel_importable 权限
     *
     * 执行顺序：
     * 1. 使用当前的用户和角色权限执行校验
     *
     * @param currentUserId 当前用户 ID
     * @param poolId 目标投资池 ID
     */
    private void validateExcelImportPermission(String currentUserId, Long poolId) {
        // 使用当前的用户和角色权限执行校验
        validateExcelImportPermission(currentUserId, poolId, queryExcelImportablePoolIds(currentUserId));
    }

    /**
     * 校验目标池是否在本次读取的权限范围内
     *
     * 执行顺序：
     * 1. 当前用户无权对该投资池进行 Excel 导入时终止处理
     *
     * @param currentUserId 当前用户 ID
     * @param poolId 目标投资池 ID
     * @param permittedPools ID集合
     */
    private void validateExcelImportPermission(String currentUserId, Long poolId, Set<Long> permittedPools) {
        if (!ADMIN_USER_ID.equals(currentUserId) && !permittedPools.contains(poolId)) {
            throw new BizException("当前用户无权对该投资池进行 Excel 导入");
        }
    }

    /**
     * 查询当前用户可导入的投资池范围，供同批多个池复用
     *
     * 执行顺序：
     * 1. 管理员直接返回空集合，调用方会单独放行
     * 2. 检查并解析非管理员的用户 ID，再查询其角色
     * 3. 读取 Excel 导入权限，汇总直接授予用户或授予其角色的投资池 ID
     *
     * @param currentUserId 当前用户 ID
     * @return 用户或角色获准用 Excel 导入的池 ID 集合，管理员直接返回空集合
     */
    private Set<Long> queryExcelImportablePoolIds(String currentUserId) {
        // 1. 管理员由调用方直接放行，跳过池权限查询
        if (ADMIN_USER_ID.equals(currentUserId)) {
            return Collections.emptySet();
        }
        // 2. 解析用户 ID 并查询其角色
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
        // 3. 汇总用户和角色的 Excel 导入权限
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
     *
     * 证券模板四列、主体模板五列；证券评级主体在上传后由页面选择
     *
     * 执行顺序：
     * 1. 按表头读取父池名称
     * 2. 按表头读取子池名称
     *
     * @param item 当前调整项
     * @param row 按表头保存的一行 Excel 数据
     * @param importType 导入类型
     */
    private void fillFromExcelRow(SysImpTmpDetlBo item, Map<String, String> row, String importType) {
        // 1. 按表头读取父池名称
        item.setFld003(cell(row, "父池名称"));
        // 2. 按表头读取子池名称
        item.setFld004(cell(row, "子池名称"));
        if (IMPORT_TYPE_COMPANY.equals(importType)) {
            // 按表头读取主体代码/名称
            item.setFld001(cell(row, "主体代码"));
            // 按表头读取单元格文本（兼容表头首尾空格）
            item.setFld002(cell(row, "主体全称"));
            // 按表头读取单元格文本（兼容表头首尾空格）
            item.setFld005(cell(row, "统一社会信用代码"));
        } else {
            // 按表头读取证券代码/名称
            item.setFld001(cell(row, "证券代码"));
            // 按表头读取单元格文本（兼容表头首尾空格）
            item.setFld002(cell(row, "证券名称"));
        }
    }

    /**
     * 按表头读取单元格文本（兼容表头首尾空格）
     *
     * 执行顺序：
     * 1. 先按完整表头取单元格，再尝试匹配去掉首尾空格的表头
     * 2. 返回去掉首尾空白的内容，找不到时返回空字符串
     *
     * @param row 按表头保存的一行 Excel 数据
     * @param header 要查找的 Excel 表头
     * @return 该单元格的文本
     */
    private String cell(Map<String, String> row, String header) {
        if (row == null) {
            return "";
        }
        if (row.containsKey(header)) {
            // null 安全 trim，空白返回空串
            return trimToEmpty(row.get(header));
        }
        for (Map.Entry<String, String> e : row.entrySet()) {
            if (e.getKey() != null && header.equals(e.getKey().trim())) {
                // null 安全 trim，空白返回空串
                return trimToEmpty(e.getValue());
            }
        }
        return "";
    }

    /**
     * 校验上传请求与文件基础参数
     *
     * 执行顺序：
     * 1. 规范化导入类型
     * 2. 规范化调整方向
     *
     * @param req 携带批次号、明细 ID、主体选择和分页条件的修改请求
     * @param file 上传的 Excel 文件
     */
    private void validateUploadReq(SecurityPoolExcelImportReq req, MultipartFile file) {
        if (req == null) {
            throw new BizException("请求参数不能为空");
        }
        // 1. 规范化导入类型
        normalizeImportType(req.getImportType());
        // 2. 规范化调整方向
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

    /**
     * 规范化导入类型：security / company
     *
     * 执行顺序：
     * 1. 检查导入类型必须为 security 或 company
     * 2. 规范化导入类型：security / company
     *
     * @param importType 导入类型
     * @return 导入类型
     */
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

    /**
     * 规范化调整方向：in / out
     *
     * 执行顺序：
     * 1. 检查调整方向必须为 in 或 out
     *
     * @param direction 调整方向，in 表示调入，out 表示调出
     * @return 校验后的方向编码
     */
    private String normalizeDirection(String direction) {
        if (!"in".equals(direction) && !"out".equals(direction)) {
            throw new BizException("调整方向必须为 in 或 out");
        }
        return direction;
    }

    /**
     * 按批次号加载导入批次，不存在则抛业务异常
     *
     * 执行顺序：
     * 1. 检查导入批次号不能为空
     * 2. 查询批次
     * 3. 导入批次不存在或已取消时终止处理
     *
     * @param impId Excel 导入批次号
     * @return Excel 导入批次
     */
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

    /**
     * 解析经办人名称（优先请求中的姓名）
     *
     * 执行顺序：
     * 1. 优先返回去掉首尾空白的经办人姓名，缺少姓名时使用去空白后的用户 ID
     *
     * @param req 携带批次号、明细 ID、主体选择和分页条件的修改请求
     * @return 用于记录导入操作的名称
     */
    private String resolveUserName(SecurityPoolExcelImportReq req) {
        if (req.getCurrentUserName() != null && !req.getCurrentUserName().trim().isEmpty()) {
            return req.getCurrentUserName().trim();
        }
        return req.getCurrentUserId().trim();
    }

    /**
     * 生成导入批次号 IMP+时间戳+序号
     *
     * 执行顺序：
     * 1. 将当前时间格式化到秒，取四位导入流水号
     * 2. 拼接 IMP 前缀、时间和流水号
     *
     * @param now 回写行级校验结果使用的统一时间
     * @return 新生成的导入批次号
     */
    private String nextImpId(Date now) {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(now);
        int seq = IMP_SEQ.incrementAndGet() % 10000;
        return "IMP" + ts + String.format("%04d", seq);
    }

    /**
     * 生成导入明细号 IMPD+时间戳+序号
     *
     * 执行顺序：
     * 1. 取精确到秒的时间文本和五位明细流水号
     * 2. 拼接 IMPD 前缀、时间和流水号
     *
     * @param now 回写行级校验结果使用的统一时间
     * @return 新生成的导入明细号
     */
    private String nextDetlId(Date now) {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(now);
        int seq = DETL_SEQ.incrementAndGet() % 100000;
        return "IMPD" + ts + String.format("%05d", seq);
    }

    /**
     * 批次主表转前端任务 DTO（含 checkItems 解析）
     *
     * 执行顺序：
     * 1. 按校验结果查询明细数量
     *
     * @param batch 当前导入批次
     * @return 证券或主体 Excel 导入结果
     */
    private SecurityPoolExcelImportDto toTaskDto(SysImpTmpBo batch) {
        int pending = securityPoolExcelImportMapper.queryItemCountByCheckResult(batch.getImpId(), "0");
        // 使用已知待校验行数构造任务响应
        return toTaskDto(batch, pending);
    }

    /**
     * 使用已知待校验行数构造任务响应
     *
     * 执行顺序：
     * 1. 槽位约定：fld001=方向 / fld002=原因 / fld003=意见
     * 2. 回填联动互斥选项
     * 3. 回填导入类型
     * 4. 解析批次校验快照
     *
     * @param batch 当前导入批次
     * @param pending 在途
     * @return 证券或主体 Excel 导入结果
     */
    private SecurityPoolExcelImportDto toTaskDto(SysImpTmpBo batch, int pending) {
        SecurityPoolExcelImportDto dto = new SecurityPoolExcelImportDto();
        dto.setImpId(batch.getImpId());
        dto.setBizType(batch.getBizType());
        dto.setFileName(batch.getFileName());
        // 1. 槽位约定：fld001=方向 / fld002=原因 / fld003=意见
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
        // 2. 回填联动互斥选项
        dto.setAllowLinkMutex(parseAllowLinkMutex(batch.getOptionJson()));
        // 3. 回填导入类型
        dto.setImportType(parseImportType(batch.getOptionJson(), batch.getBizType()));
        // 4. 解析批次校验快照
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

    /**
     * 从 option_json 解析是否允许联动与互斥
     *
     * 执行顺序：
     * 1. 选项为空时返回 false，有效 JSON 中按 allowLinkMutex 字段取布尔值
     * 2. 解析失败时用原始文本匹配 "allowLinkMutex":true
     *
     * @param optionJson 选项JSON
     * @return true=允许联动与互斥，false=不允许联动与互斥
     */
    private boolean parseAllowLinkMutex(String optionJson) {
        // 判断字符串是否为 null 或空白
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

    /**
     * 将调库校验结果序列化写入 result_json
     *
     * 执行顺序：
     * 1. 写入校验完成标记、联动和互斥开关、导入类型及通过和失败数量
     * 2. 将校验明细放入同一对象并转成 JSON
     * 3. 转换失败时返回标记为校验完成的空明细 JSON
     *
     * @param checkItems 已筛选为可提交的证券导入校验项，包含主项及展开项
     * @param checkPass 校验通过项数
     * @param checkFail 校验失败项数
     * @param allowLinkMutex 是否保留联动、互斥和关联展开项
     * @param importType 导入类型
     * @return 供导入批次保存的校验结果 JSON
     */
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

    /**
     * 读取批次保存的 Excel 导入校验明细
     *
     * 执行顺序：
     * 1. 结果 JSON 为空白时返回 null
     * 2. 读取 checkItems 数组并转换为校验明细，字段缺失、类型不对或解析失败时返回 null
     *
     * @param resultJson 批次保存的校验结果 JSON
     * @return Excel 导入校验明细列表，结果为空、缺少有效明细数组或解析失败时返回 null
     */
    private List<SecurityPoolExcelImportCheckItemDto> parseCheckItems(String resultJson) {
        // 1. 校验结果为空白时直接返回 null
        if (isBlank(resultJson)) {
            return null;
        }
        // 2. 解析 checkItems 数组并转换为校验明细
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

    /**
     * 临时表明细转前端明细 DTO
     *
     * 执行顺序：
     * 1. 回填导入行中的证券或主体、目标池及权益人字段
     * 2. 带上关联评级主体选择和本行校验保存结果
     *
     * @param bo 已保存的导入明细记录
     * @return 证券或主体 Excel 导入明细
     */
    private SecurityPoolExcelImportItemDto toItemDto(SysImpTmpDetlBo bo) {
        // 1. 回填导入行的证券或主体、目标池和权益人资料
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
        dto.setGuarantorCode(bo.getFld020());
        dto.setRelatedCompanyName(bo.getFld012());
        dto.setSelfSelectedRightsHolderCode(bo.getFld013());
        // 2. 补齐可选权益人并确定最终评级主体
        if (!isBlank(bo.getFld013())) {
            SelfSelectedRightsHolderDto self = new SelfSelectedRightsHolderDto();
            self.setCompanyCode(bo.getFld013());
            self.setCompanyName(bo.getFld014());
            dto.setSelfSelectedRightsHolder(self);
        }
        // 判断字符串是否为 null 或空白
        dto.setRatingCompanyCode(isBlank(bo.getFld013()) ? bo.getFld011() : bo.getFld013());
        // 判断字符串是否为 null 或空白
        dto.setRatingCompanyName(isBlank(bo.getFld013()) ? bo.getFld012() : bo.getFld014());
        // 3. 解析已匹配的池 ID，接着复制校验和提交状态
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

    /**
     * 组装导入选项 JSON
     *
     * 执行顺序：
     * 1. 将联动互斥选项转换为布尔值，导入类型缺失时用证券类型
     * 2. 把两个选项一起写入导入配置 JSON
     *
     * @param allowLinkMutex 是否允许本次 Excel 调库保留联动和互斥配套项，仅为 true 时启用
     * @param importType 导入类型
     * @return 目标池选项 JSON
     */
    private String buildOptionJson(Boolean allowLinkMutex, String importType) {
        boolean link = Boolean.TRUE.equals(allowLinkMutex);
        String type = importType == null ? IMPORT_TYPE_SECURITY : importType;
        return "{\"allowLinkMutex\":" + link
                + ",\"importType\":\"" + type + "\"}";
    }

    /**
     * 从 option_json 或 bizType 解析导入类型
     *
     * 执行顺序：
     * 1. 优先从导入选项取已保存的类型
     * 2. 选项缺失或无可识别类型时，按业务类型区分主体导入和证券导入
     *
     * @param optionJson 选项JSON
     * @param bizType 批次保存的业务类型编码，用于识别主体或证券导入
     * @return 导入类型
     */
    private String parseImportType(String optionJson, String bizType) {
        // 判断字符串是否为 null 或空白
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

    /**
     * 解析 Excel 物理行号
     *
     * 执行顺序：
     * 1. 按整数解析原始行号，解析失败时返回 0
     *
     * @param text 待处理的文本
     * @return 导入明细对应的行号
     */
    private int parseRowNo(String text) {
        try {
            return Integer.parseInt(text);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 将失败原因列表拼接为校验说明（最长 500）
     *
     * 执行顺序：
     * 1. 按顺序拼接每条失败原因
     * 2. 长度超过 500 字符时只保留前 500 字符
     *
     * @param reasons 用于展示或保存的失败原因
     * @return 写入导入明细的失败说明
     */
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

    /**
     * 空白串转为 null
     *
     * 执行顺序：
     * 1. 空值直接保留为 null，其他文本去掉首尾空白后再判断是否为空
     *
     * @param s 待处理的文本
     * @return 去掉首尾空白后的文本，输入为空或只有空白时返回 null
     */
    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 清理文本首尾空白
     *
     * 执行顺序：
     * 1. 输入为 null 时使用空字符串，其他输入去掉首尾空白
     *
     * @param s 待处理的文本
     * @return 去掉首尾空白后的文本，输入为 null 或只有空白时返回空字符串
     */
    private String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * 判断字符串是否为 null 或空白
     *
     * 执行顺序：
     * 1. 检查文本是否为空，去掉首尾空白后判断是否仍有内容
     *
     * @param s 待处理的文本
     * @return true=文本为空或只有空白，false=文本含非空白字符
     */
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
