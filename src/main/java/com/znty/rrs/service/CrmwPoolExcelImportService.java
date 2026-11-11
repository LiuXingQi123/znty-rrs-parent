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
import com.znty.rrs.common.enums.PoolType;
import com.znty.rrs.common.util.ExcelImportHelper;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.common.SecurityMarketDto;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 处理 CRMW 池 Excel 导入任务
 *
 * 1. 检查目标池的 Excel 导入权限（excel_importable），读取工作表并将批次和明细写入导入临时表
 * 2. 逐行检查凭证和标的主数据、ABS 主体身份及调库规则，将可放行的业务失败作为提示保留
 * 3. 为可调整的组合提供单笔及批量流程候选，按用户所选流程提交并回写导入结果
 *
 * 调库校验和提交由 {@link CrmwPoolAdjustService} 处理，本服务负责导入任务和行明细的状态
 */
@Service
public class CrmwPoolExcelImportService {

    /** 统一证券市场查询组件。 */
    @Resource
    private SecurityMarketService securityMarketService;

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
     * 查询当前用户可用于 Excel 导入的 CRMW 池
     *
     * 执行顺序：
     * 1. 查找启用的叶子 CRMW 池
     * 2. 管理员可使用全部结果，其他用户逐池按导入权限筛选
     *
     * @param req 含当前用户 ID 的导入池查询请求
     * @return 允许当前用户导入的 CRMW 池列表
     */
    public List<CrmwPoolExcelImportPoolDto> queryPoolList(CrmwPoolExcelImportReq req) {
        List<CrmwPoolExcelImportPoolDto> all = crmwPoolExcelImportMapper.queryEnabledLeafCrmwPoolList();
        if (all == null || all.isEmpty()) {
            return new ArrayList<>();
        }
        // 统一未填或纯空白的值，保留非空文本
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
     * 上传 CRMW 导入 Excel
     *
     * 执行顺序：
     * 1. 将请求和文件交给支持原文件名的上传入口处理
     *
     * @param req 含目标池、方向和操作人的导入请求
     * @param file 上传的 Excel 文件
     * @return 上传后的导入任务
     */
    @Transactional(rollbackFor = Exception.class)
    public CrmwPoolExcelImportDto uploadExcel(CrmwPoolExcelImportReq req, MultipartFile file) {
        return uploadExcel(req, file, null);
    }

    /**
     * 读取 CRMW Excel 并保存待处理任务
     *
     * 执行顺序：
     * 1. 检查上传参数、目标池及用户导入权限，并读取第一张工作表
     * 2. 创建导入批次并逐行读取组合代码，检查空值和重复组合，补充展示字段并验证证券主数据
     * 3. 保存批次和分批写入明细，再查询任务详情
     *
     * @param req 含目标池、方向和操作人的导入请求
     * @param file 上传的 Excel 文件
     * @param originalFileNameListJson 上传文件原始名称列表的 JSON
     * @return 上传后的导入任务
     */
    @Transactional(rollbackFor = Exception.class)
    public CrmwPoolExcelImportDto uploadExcel(CrmwPoolExcelImportReq req, MultipartFile file,
                                              String originalFileNameListJson) {
        // 1. 检查文件、目标池和导入权限
        validateUploadReq(req, file);
        // 取得导入使用的目标 CRMW 池
        InvestmentPoolBo pool = requireTargetPool(req.getTargetPoolId());
        // 校验 Excel 导入权限
        validateExcelImportPermission(req.getCurrentUserId().trim(), pool.getId());

        List<String> originalFileNameList =
                sysAttachmentService.parseOriginalFileNameListJson(originalFileNameListJson);
        String uploadFileName = sysAttachmentService.resolveUploadOriginalFileName(file, originalFileNameList);

        // 2. 读取第一张工作表
        List<Map<String, String>> excelRows = ExcelImportHelper.parseFirstSheet(file, MAX_ROWS);
        Date now = new Date();
        // 生成 Excel 导入批次号
        String impId = nextImpId(now);

        // 3. 生成导入批次并记录文件、池和操作人信息
        SysImpTmpBo batch = new SysImpTmpBo();
        batch.setImpId(impId);
        batch.setBizType(BIZ_TYPE);
        batch.setTemplateCode(TEMPLATE_CODE);
        batch.setFileName(uploadFileName);
        batch.setFileSize(file.getSize());
        // 检查 Excel 导入方向
        batch.setFld001(normalizeDirection(req.getDirection()));
        // 统一未填或纯空白的值，保留非空文本
        batch.setFld002(trimToNull(req.getAdjustReason()));
        // 统一未填或纯空白的值，保留非空文本
        batch.setFld003(trimToNull(req.getAdjustAdvice()));
        batch.setFld004(String.valueOf(pool.getId()));
        batch.setFld005(pool.getPoolName());
        batch.setFld006(PoolType.CRMW.getCode());
        // 生成导入批次保存的目标池选项
        batch.setOptionJson(buildOptionJson(pool));
        batch.setTotalCount(0);
        batch.setPassCount(0);
        batch.setFailCount(0);
        batch.setChkRslt("0");
        batch.setSaveRslt("0");
        batch.setImpTime(now);
        batch.setOpterId(req.getCurrentUserId().trim());
        // 取得导入日志使用的操作人名称
        batch.setOpterName(resolveUserName(req));
        batch.setIsDeleted(0);
        batch.setCrteTime(now);
        batch.setUpdtTime(now);

        Map<String, SecurityInfoDetailDto> infoCache = new HashMap<>();
        List<SysImpTmpDetlBo> items = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        // 4. 逐行读取组合代码，检查重复和主数据错误
        for (Map<String, String> row : excelRows) {
            SysImpTmpDetlBo item = new SysImpTmpDetlBo();
            // 生成 Excel 导入明细号
            item.setImpDetlId(nextDetlId(now));
            item.setImpId(impId);
            // 解析导入明细的 Excel 行号
            item.setRowNo(parseRowNo(row.get("__rowNo")));
            item.setChkRslt("0");
            item.setSaveRslt("0");
            item.setImpTime(now);
            item.setOpterId(batch.getOpterId());
            item.setIsDeleted(0);
            item.setCrteTime(now);
            item.setUpdtTime(now);
            // 按 Excel 表头读取一格文本
            item.setFld001(cell(row, "CRMW代码"));
            // 按 Excel 表头读取一格文本
            item.setFld004(cell(row, "证券代码"));
            item.setFld009(String.valueOf(pool.getId()));
            item.setFld010(PoolType.CRMW.getCode());

            List<String> preFail = new ArrayList<>();
            // 清理首尾空白，未填写时使用空字符串
            String crmwScode = trimToEmpty(item.getFld001());
            // 清理首尾空白，未填写时使用空字符串
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
                // 检查导入行的凭证和标的主数据
                preFail.addAll(validateComboMasterData(item, infoCache));
            }
            if (!preFail.isEmpty()) {
                item.setChkRslt("2");
                // 整理导入失败原因以便保存
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

        // 5. 保存批次及明细，再回查任务和第一页明细
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
     * 查询 CRMW Excel 导入任务
     *
     * 执行顺序：
     * 1. 按批次 ID 查询任务并转换成展示数据
     * 2. 请求指定页码时补充导入明细分页
     *
     * @param req 导入批次号和可选的明细分页条件
     * @return 导入任务详情
     */
    public CrmwPoolExcelImportDto queryTask(CrmwPoolExcelImportReq req) {
        // 取得 Excel 导入批次
        SysImpTmpBo batch = requireBatch(req == null ? null : req.getImpId());
        // 将保存的导入批次转换成任务详情
        CrmwPoolExcelImportDto dto = toTaskDto(batch);
        if (req != null && req.getPageIndex() > 0) {
            dto.setItems(queryItemPage(req));
        }
        return dto;
    }

    /**
     * 分页查询 CRMW Excel 导入明细
     *
     * 执行顺序：
     * 1. 确认导入批次存在
     * 2. 按校验状态和关键字查询并分页
     * 3. 将保存的行数据转换成展示对象
     *
     * @param req 批次号、校验状态、关键字和分页条件
     * @return 导入明细分页结果
     */
    public PageResult<CrmwPoolExcelImportItemDto> queryItemPage(CrmwPoolExcelImportReq req) {
        // 检查该值是否已填写
        if (req == null || isBlank(req.getImpId())) {
            throw new BizException("导入批次号不能为空");
        }
        // 取得 Excel 导入批次
        requireBatch(req.getImpId());
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SysImpTmpDetlBo> list = crmwPoolExcelImportMapper.queryItemList(
                // 统一未填或纯空白的值，保留非空文本
                req.getImpId().trim(), trimToNull(req.getChkRslt()), trimToNull(req.getKeyword()));
        PageInfo<SysImpTmpDetlBo> pageInfo = new PageInfo<>(list);
        List<CrmwPoolExcelImportItemDto> records = new ArrayList<>();
        for (SysImpTmpDetlBo bo : list) {
            // 将保存的导入明细转换成展示对象
            records.add(toItemDto(bo));
        }
        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 取消未提交的 CRMW Excel 导入任务
     *
     * 执行顺序：
     * 1. 查询批次并确认未提交
     * 2. 先标记批次明细删除，再标记导入批次删除
     *
     * @param req 待取消任务的导入批次号
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelImport(CrmwPoolExcelImportReq req) {
        // 检查该值是否已填写
        if (req == null || isBlank(req.getImpId())) {
            throw new BizException("导入批次号不能为空");
        }
        // 取得 Excel 导入批次
        SysImpTmpBo batch = requireBatch(req.getImpId());
        if ("1".equals(batch.getSaveRslt())) {
            throw new BizException("该批次已提交，不能取消");
        }
        crmwPoolExcelImportMapper.deleteItemsByImpIdSoft(batch.getImpId());
        crmwPoolExcelImportMapper.deleteBatchSoft(batch.getImpId());
    }

    /**
     * 校验 CRMW Excel 批次，保存逐行结果和完整调整提示
     *
     * 可调整项仍可有 failReasons；数据错误和 ABS 主体身份冲突继续阻断，不清空原始原因
     *
     * 执行顺序：
     * 1. 检查批次未提交、明细非空，并校验目标池和当前用户导入权限
     * 2. 逐行回填凭证与标的主档，代码缺失、类型错误等数据问题记为失败行
     * 3. 对有效数据调用 Excel 专用校验，执行原规则并将业务失败保留为提示
     * 4. 映射主项及联动、互斥、关联项，回写来源明细的校验状态
     * 5. 按实际组合、目标池和方向去重，显式手工项优先，再汇总可提交和失败条数
     * 6. 保存批次汇总与校验快照，返回本批任务及当前页明细
     *
     * @param req 批次号、当前用户及分页条件
     * @return 包含逐行结果、调整提示和流程候选的导入任务
     */
    @Transactional(rollbackFor = Exception.class)
    public CrmwPoolExcelImportDto checkImport(CrmwPoolExcelImportReq req) {
        // 1. 检查批次状态、明细及目标池导入权限
        if (req == null || isBlank(req.getImpId())) {
            throw new BizException("导入批次号不能为空");
        }
        // 按批次号加载导入批次
        SysImpTmpBo batch = requireBatch(req.getImpId());
        if ("1".equals(batch.getSaveRslt())) {
            throw new BizException("该批次已提交，不能再次校验");
        }
        // 优先使用本次请求的经办人，未填写时沿用批次经办人
        String opterId = !isBlank(req.getCurrentUserId()) ? req.getCurrentUserId().trim() : batch.getOpterId();
        List<SysImpTmpDetlBo> items = crmwPoolExcelImportMapper.queryAllByImpId(batch.getImpId());
        if (items.isEmpty()) {
            throw new BizException("导入明细为空");
        }
        // 解析目标池 ID，并加载启用叶子 CRMW 池
        InvestmentPoolBo pool = requireTargetPool(parsePoolId(batch.getFld004()));
        // 校验 Excel 导入权限
        validateExcelImportPermission(opterId, pool.getId());

        boolean inbound = "in".equals(batch.getFld001());
        String adjustMode = inbound ? AdjustMode.IN.getCode() : AdjustMode.OUT.getCode();
        Date now = new Date();
        Map<String, SecurityInfoDetailDto> infoCache = new HashMap<>();
        List<CrmwPoolExcelImportCheckItemDto> checkItems = new ArrayList<>();

        // 2. 逐行回填主档并识别代码缺失、类型错误等数据问题
        for (SysImpTmpDetlBo item : items) {
            List<String> preReasons = new ArrayList<>();
            // 统一导入代码的空值和首尾空格，供必填检查及主档匹配使用
            String crmwScode = trimToEmpty(item.getFld001());
            // 统一导入代码的空值和首尾空格，供必填检查及主档匹配使用
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
                // 校验凭证与标的主数据是否可导入
                preReasons.addAll(validateComboMasterData(item, infoCache));
            }
            item.setFld009(String.valueOf(pool.getId()));
            item.setFld010(PoolType.CRMW.getCode());

            if (!preReasons.isEmpty()) {
                // 将导入明细标记为校验失败并回写临时表
                markItemFail(item, preReasons, now);
                // 构建前置失败的手工校验结果项
                checkItems.add(buildFailManualItem(item, pool, adjustMode, preReasons));
                continue;
            }

            AdjustCheckReq checkReq = new AdjustCheckReq();
            checkReq.setSecurityCode(securityCode);
            // 解析标的简称
            checkReq.setSecurityShortName(resolveShortName(item.getFld005(), securityCode, infoCache));
            // 统一主档展示字段的空值，避免把空白文本传入校验
            checkReq.setSecurityType(trimToNull(item.getFld006()));
            // 统一主档展示字段的空值，避免把空白文本传入校验
            checkReq.setCrmwName(trimToNull(item.getFld002()));
            checkReq.setCrmwScode(crmwScode);
            // 空凭证类型回退为 crmw
            checkReq.setCrmwStype(resolveCrmwStype(item.getFld008()));
            AdjustCheckReq.CheckItem checkItem = new AdjustCheckReq.CheckItem();
            checkItem.setTargetPoolId(pool.getId());
            checkItem.setTargetPoolName(pool.getPoolName());
            checkItem.setPoolType(PoolType.CRMW.getCode());
            checkItem.setAdjustMode(adjustMode);
            checkReq.setItems(Collections.singletonList(checkItem));

            // 3. 执行完整规则，Excel 业务失败保留为可提交提示
            AdjustCheckDto checkDto;
            try {
                checkDto = crmwPoolAdjustService.checkExcelAdjust(checkReq);
            } catch (BizException e) {
                List<String> reasons = Collections.singletonList(e.getMessage());
                // 将导入明细标记为校验失败并回写临时表
                markItemFail(item, reasons, now);
                // 构建前置失败的手工校验结果项
                checkItems.add(buildFailManualItem(item, pool, adjustMode, reasons));
                continue;
            }

            List<AdjustCheckDto.CheckResultItem> resultItems =
                    checkDto.getItems() == null ? new ArrayList<>() : checkDto.getItems();
            // 4. 映射展开项，并按手工主项结果回写来源明细状态
            boolean manualPass = false;
            for (AdjustCheckDto.CheckResultItem ri : resultItems) {
                // 映射单笔校验结果为导入校验行
                CrmwPoolExcelImportCheckItemDto mapped = mapCheckResult(item, ri, pool, infoCache);
                // 只用手工主项判断来源导入行是否通过
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
                            // 只汇总本来源手工主项的失败原因
                            && isManualTag(ci.getItemTag()) && !ci.isCanAdjust()
                            && ci.getFailReasons() != null) {
                        failReasons.addAll(ci.getFailReasons());
                    }
                }
                if (failReasons.isEmpty()) {
                    failReasons.add("校验未通过");
                }
                // 将导入明细标记为校验失败并回写临时表
                markItemFail(item, failReasons, now);
            }
        }

        // 5. 显式手工项优先去重，再统计导入行和展开项的校验结果
        Map<String, CrmwPoolExcelImportCheckItemDto> uniqueItems = new LinkedHashMap<>();
        for (CrmwPoolExcelImportCheckItemDto ci : checkItems) {
            String key = ci.getCrmwScode() + "|" + ci.getSecurityCode() + "|"
                    + ci.getTargetPoolId() + "|" + ci.getAdjustMode();
            CrmwPoolExcelImportCheckItemDto existing = uniqueItems.get(key);
            // 判断当前项是否由 Excel 主行显式发起
            if (existing == null || isManualTag(ci.getItemTag())) {
                uniqueItems.put(key, ci);
            }
        }
        checkItems = new ArrayList<>(uniqueItems.values());
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
        // 6. 保存批次汇总及完整校验快照，返回任务和当前页明细
        batch.setPassCount(pass);
        batch.setFailCount(fail);
        boolean allOk = fail == 0 && checkFail == 0;
        batch.setChkRslt(allOk ? "1" : "2");
        if (allOk) {
            long tipCount = checkItems.stream().filter(ci -> ci.getFailReasons() != null
                    && !ci.getFailReasons().isEmpty()).count();
            batch.setChkDscr(tipCount == 0 ? "全部校验通过（含调库展开项 " + checkItems.size() + " 条）"
                    : "全部可提交，其中 " + tipCount + " 条含调整提示");
        } else {
            batch.setChkDscr("导入行失败 " + fail + " 条，校验结果失败 " + checkFail + " 条");
        }
        // 将调库校验结果序列化写入 result_json
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
     * 按 CRMW Excel 校验结果提交整批组合调库申请
     *
     * 整批提交处于同一事务；业务提示不代表免审批，数据库异常仍整批回滚
     *
     * 执行顺序：
     * 1. 检查批次未提交，并筛选可提交校验项，没有有效项时结束并提示
     * 2. 确定经办人、调整原因和意见，复核目标池及当前用户导入权限
     * 3. 存在直通项时，携带 Excel 来源按最终审批口径提前复核整批
     * 4. 按触发主组合分组，准备整批共用的附件和批次号上下文
     * 5. 逐组构造提交请求，保留实际流程选择并委托核心服务写日志、步骤和申请快照
     * 6. 按手工主项回写来源明细保存结果，并更新批次提交状态和结果快照
     * 7. 回查任务，附上本次调库日志 ID 后返回
     *
     * @param req 批次号、经办人、原因意见及已选择流程的校验项
     * @return 提交后的导入任务及调库日志 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public CrmwPoolExcelImportDto submitImport(CrmwPoolExcelImportReq req) {
        // 1. 检查批次未提交，并筛选本次可提交项
        if (req == null || isBlank(req.getImpId())) {
            throw new BizException("导入批次号不能为空");
        }
        // 按批次号加载导入批次
        SysImpTmpBo batch = requireBatch(req.getImpId());
        if ("1".equals(batch.getSaveRslt())) {
            throw new BizException("该批次已提交，请勿重复提交");
        }
        // 从请求或批次快照中筛选可提交的校验结果项
        List<CrmwPoolExcelImportCheckItemDto> checkItems = filterSubmittableCheckItems(req, batch);
        if (checkItems.isEmpty()) {
            throw new BizException("没有可提交的校验结果，请先校验");
        }
        // 2. 确定经办人及原因意见，复核目标池导入权限
        String opterId = !isBlank(req.getCurrentUserId()) ? req.getCurrentUserId().trim() : batch.getOpterId();
        // 优先使用本次请求的经办人姓名，未填写时沿用批次姓名
        String opterName = !isBlank(req.getCurrentUserName()) ? req.getCurrentUserName().trim() : batch.getOpterName();
        // 检查经办人 ID 是否已明确，缺失时不能提交
        if (isBlank(opterId)) {
            throw new BizException("经办人 ID 不能为空");
        }
        // 经办人姓名未填写时使用经办人 ID 记录操作人
        if (isBlank(opterName)) {
            opterName = opterId;
        }
        // 本次填写调整原因时更新批次原因
        if (!isBlank(req.getAdjustReason())) {
            batch.setFld002(req.getAdjustReason().trim());
        }
        // 本次填写调整意见时更新批次意见
        if (!isBlank(req.getAdjustAdvice())) {
            batch.setFld003(req.getAdjustAdvice().trim());
        }
        // 解析目标池 ID，并加载启用叶子 CRMW 池
        InvestmentPoolBo pool = requireTargetPool(parsePoolId(batch.getFld004()));
        // 校验 Excel 导入权限
        validateExcelImportPermission(opterId, pool.getId());

        // 3. 存在直通项时，携带 Excel 来源复核整批
        if (needsWholeBatchDirectRecheck(checkItems)) {
            // 将 CRMW Excel 校验项转换为直通预检使用的日志
            crmwPoolAdjustService.recheckBeforeFinalApproval(buildDirectRecheckLogList(checkItems));
        }

        // 4. 按触发主组合分组，准备整批共用的附件和批次号上下文
        Map<String, List<CrmwPoolExcelImportCheckItemDto>> groupMap = new LinkedHashMap<>();
        for (CrmwPoolExcelImportCheckItemDto ci : checkItems) {
            // 优先按触发主组合分组，缺失时使用当前凭证与标的组合键
            String groupKey = !isBlank(ci.getSourceSecurityCode())
                    ? ci.getSourceSecurityCode()
                    // 组合键：凭证|标的
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
        // 5. 保留实际流程选择，逐组委托核心服务提交
        for (List<CrmwPoolExcelImportCheckItemDto> group : groupMap.values()) {
            // 构建单组合提交请求
            CrmwPoolAdjustSubmitReq submitReq = buildSubmitReq(group, batch, opterId, opterName, pool);
            AdjustSubmitDto submitDto = crmwPoolAdjustService.submitAdjustLog(
                    submitReq, submissionFiles, batchNoContext);
            if (submitDto != null && submitDto.getLogIds() != null) {
                logIds.addAll(submitDto.getLogIds());
            }
        }

        // 6. 回写来源明细和批次的提交结果及快照
        Date now = new Date();
        Map<Long, SysImpTmpDetlBo> sourceMap = new HashMap<>();
        for (SysImpTmpDetlBo bo : crmwPoolExcelImportMapper.queryAllByImpId(batch.getImpId())) {
            sourceMap.put(bo.getId(), bo);
        }
        for (CrmwPoolExcelImportCheckItemDto ci : checkItems) {
            // 只回写手工主项对应的来源明细保存状态
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

        // 7. 回查任务，附加本次生成的调库日志 ID
        CrmwPoolExcelImportDto dto = toTaskDto(crmwPoolExcelImportMapper.queryByImpId(batch.getImpId()));
        dto.setLogIds(logIds);
        return dto;
    }

    /**
     * 把一组 Excel 校验结果转为 CRMW 调库提交请求
     *
     * 组内关联项跟随触发它的手工主项提交，保留每项实际的标的代码
     *
     * 执行顺序：
     * 1. 以组内手工主项填入凭证和标的信息，并带入批次说明和操作人
     * 2. 逐项检查目标池并复制各项的方向、分组和所选流程
     * 3. 整理同组同券跨市场代码
     *
     * @param group 按来源组合分组的可提交结果
     * @param batch 导入批次记录
     * @param opterId 本次提交的操作人 ID
     * @param opterName 本次提交的操作人姓名
     * @param pool 投资池配置
     * @return 一组 Excel 调整项对应的调库提交请求
     */
    private CrmwPoolAdjustSubmitReq buildSubmitReq(List<CrmwPoolExcelImportCheckItemDto> group,
                                                   SysImpTmpBo batch, String opterId, String opterName,
                                                   InvestmentPoolBo pool) {
        // 1. 以组内手工主项填入凭证和标的信息，并带入批次说明和操作人
        CrmwPoolExcelImportCheckItemDto primary = resolvePrimaryItem(group);
        CrmwPoolAdjustSubmitReq submitReq = new CrmwPoolAdjustSubmitReq();
        submitReq.setSecurityCode(primary.getSecurityCode());
        submitReq.setSecurityShortName(primary.getSecurityShortName());
        submitReq.setSecurityType(primary.getSecurityType());
        submitReq.setCrmwName(primary.getCrmwName());
        submitReq.setCrmwScode(primary.getCrmwScode());
        // 凭证类型缺失时按 crmw 处理
        submitReq.setCrmwStype(resolveCrmwStype(primary.getCrmwStype()));
        submitReq.setAdjustType(ADJUST_TYPE_EXCEL);
        submitReq.setAdjustReason(batch.getFld002());
        submitReq.setAdjustAdvice(batch.getFld003());
        submitReq.setAdjusterId(opterId);
        submitReq.setAdjusterName(opterName);

        List<CrmwPoolAdjustSubmitReq.AdjustItem> submitItems = new ArrayList<>();
        // 2. 逐项检查目标池并复制各项的方向、分组和所选流程
        for (CrmwPoolExcelImportCheckItemDto ci : group) {
            if (ci.getTargetPoolId() == null) {
                throw new BizException("校验结果数据不完整，请重新校验");
            }
            CrmwPoolAdjustSubmitReq.AdjustItem si = new CrmwPoolAdjustSubmitReq.AdjustItem();
            si.setSecurityCode(ci.getSecurityCode());
            si.setSecurityShortName(ci.getSecurityShortName());
            si.setSecurityType(ci.getSecurityType());
            si.setTargetPoolId(ci.getTargetPoolId());
            // 检查该值是否已填写
            si.setTargetPoolName(!isBlank(ci.getTargetPoolName()) ? ci.getTargetPoolName() : pool.getPoolName());
            si.setPoolType(PoolType.CRMW.getCode());
            si.setAdjustMode(ci.getAdjustMode());
            // 检查该值是否已填写
            si.setItemTag(isBlank(ci.getItemTag()) ? ItemType.MANUAL.getCode() : ci.getItemTag());
            si.setAdjustGroupKey(ci.getAdjustGroupKey());
            si.setFlowId(ci.getFlowId());
            si.setFlowKey(ci.getFlowKey());
            si.setFlowType(ci.getFlowType());
            si.setAdjustmentNote(ci.getAdjustNote());
            submitItems.add(si);
        }
        // 3. 复用本次校验后传入的实际代码集合，提交阶段不再查询同券关系
        submitReq.setRelatedMarketCodes(group.stream().map(item -> item.getSecurityCode())
                .filter(code -> code != null && !code.equals(submitReq.getSecurityCode())).distinct()
                .map(code -> {
                    SecurityMarketDto related = new SecurityMarketDto();
                    related.setWindCode(code);
                    return related;
                }).collect(Collectors.toList()));
        submitReq.setItems(submitItems);
        return submitReq;
    }

    /**
     * 判断 Excel 整批提交前是否需要提前复核
     *
     * 执行顺序：
     * 1. 遍历手工调整项，检查是否有流程无需人工审批即可完成
     *
     * @param checkItems Excel 导入校验结果
     * @return 有可直接完成的手工项时返回 true
     */
    private boolean needsWholeBatchDirectRecheck(List<CrmwPoolExcelImportCheckItemDto> checkItems) {
        for (CrmwPoolExcelImportCheckItemDto item : checkItems) {
            // 判断 Excel 调整项是否为手工项
            if (!isManualTag(item.getItemTag())) {
                continue;
            }
            if (crmwPoolAdjustService.isDirectAdjustFlow(item.getFlowId(), item.getFlowKey())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将 CRMW Excel 校验项转换为直通预检使用的日志
     *
     * 执行顺序：
     * 1. 逐项复制实际凭证、标的、调整方向和目标池信息
     * 2. 标记 Excel 来源，使直通预检与正式提交采用相同的业务提示口径
     * 3. 返回完整日志列表，不写入数据库
     *
     * @param checkItems 本次可提交的完整校验项
     * @return 直通预检使用的临时日志列表
     */
    private List<IpAdjustLogBo> buildDirectRecheckLogList(List<CrmwPoolExcelImportCheckItemDto> checkItems) {
        // 1. 复制实际凭证、标的、调整方向及目标池
        List<IpAdjustLogBo> logs = new ArrayList<>();
        for (CrmwPoolExcelImportCheckItemDto item : checkItems) {
            IpAdjustLogBo log = new IpAdjustLogBo();
            log.setSecurityCode(item.getSecurityCode());
            log.setSecurityShortName(item.getSecurityShortName());
            log.setSecurityType(item.getSecurityType());
            log.setCrmwName(item.getCrmwName());
            log.setCrmwScode(item.getCrmwScode());
            // 空凭证类型回退为 crmw
            log.setCrmwStype(resolveCrmwStype(item.getCrmwStype()));
            log.setAdjustMode(item.getAdjustMode());
            // 2. 标记 Excel 来源，使预检与正式提交使用相同提示口径
            log.setAdjustType(ADJUST_TYPE_EXCEL);
            log.setTargetPoolId(item.getTargetPoolId());
            log.setTargetPoolName(item.getTargetPoolName());
            logs.add(log);
        }
        // 3. 返回内存日志列表，不写入数据库
        return logs;
    }

    /**
     * 将单笔调库检查结果转换成 Excel 调整项
     *
     * 执行顺序：
     * 1. 复制导入行的来源信息，并用检查结果确定实际凭证和标的
     * 2. 标的代码变化时补齐该证券的展示字段
     * 3. 写入目标池、方向、分组、检查结果和调整来源
     * 4. 转换流程候选并设置默认选中的流程
     *
     * @param sourceItem 原始导入明细
     * @param ri 共用调库校验返回的调整项
     * @param pool 投资池配置
     * @param infoCache 本次操作共用的证券资料缓存
     * @return 供导入页面使用的调整检查结果
     */
    private CrmwPoolExcelImportCheckItemDto mapCheckResult(SysImpTmpDetlBo sourceItem,
                                                           AdjustCheckDto.CheckResultItem ri,
                                                           InvestmentPoolBo pool,
                                                           Map<String, SecurityInfoDetailDto> infoCache) {
        // 1. 复制来源标识并取实际凭证和标的代码
        CrmwPoolExcelImportCheckItemDto dto = new CrmwPoolExcelImportCheckItemDto();
        if (sourceItem != null) {
            dto.setSourceItemId(sourceItem.getId());
            dto.setSourceRowNo(sourceItem.getRowNo());
        }
        // 检查该值是否已填写，并清理首尾空白，未填写时使用空字符串
        String crmwScode = !isBlank(ri.getCrmwScode()) ? ri.getCrmwScode() : trimToEmpty(sourceItem.getFld001());
        // 检查该值是否已填写，并清理首尾空白，未填写时使用空字符串
        String securityCode = !isBlank(ri.getSecurityCode()) ? ri.getSecurityCode() : trimToEmpty(sourceItem.getFld004());
        // 检查该值是否已填写
        String sourceKey = !isBlank(ri.getSourceSecurityCode())
                ? ri.getSourceSecurityCode()
                // 生成导入行的 CRMW 组合去重标识
                : buildCombinationKey(crmwScode, securityCode);
        dto.setCrmwScode(crmwScode);
        dto.setSecurityCode(securityCode);
        dto.setSourceSecurityCode(sourceKey);
        // 凭证类型缺失时按 crmw 处理
        dto.setCrmwStype(resolveCrmwStype(ri.getCrmwStype() != null ? ri.getCrmwStype() : sourceItem.getFld008()));
        dto.setSecurityType(ri.getSecurityType() != null ? ri.getSecurityType() : sourceItem.getFld006());
        dto.setSecurityShortName(ri.getSecurityShortName());
        // 检查该值是否已填写
        dto.setCrmwName(!isBlank(ri.getCrmwName()) ? ri.getCrmwName() : sourceItem.getFld002());
        dto.setSecurityName(sourceItem.getFld005());
        dto.setCrmwMarket(sourceItem.getFld003());
        dto.setSecurityMarket(sourceItem.getFld007());
        dto.setSecurityTypeName(sourceItem.getFld011());
        // 2. 展开项代码变化时按自身主档补齐展示字段，避免沿用源行的市场
        if (!Objects.equals(dto.getSecurityCode(), sourceItem.getFld004())) {
            dto.setSecurityName(null);
            dto.setSecurityMarket(null);
            dto.setSecurityTypeName(null);
        }
        // 补全映射后的 CRMW 组合展示字段
        fillMappedDisplay(dto, infoCache);
        // 3. 回填目标池、方向、分组及检查结果
        dto.setTargetPoolId(ri.getTargetPoolId() != null ? ri.getTargetPoolId() : pool.getId());
        // 检查该值是否已填写
        dto.setTargetPoolName(!isBlank(ri.getPoolName()) ? ri.getPoolName() : pool.getPoolName());
        dto.setPoolType(PoolType.CRMW.getCode());
        dto.setAdjustMode(ri.getAdjustMode());
        // 检查该值是否已填写
        dto.setItemTag(isBlank(ri.getItemTag()) ? ItemType.MANUAL.getCode() : ri.getItemTag());
        String gk = ri.getAdjustGroupKey();
        dto.setAdjustGroupKey(sourceKey + "_" + (gk == null ? "" : gk));
        dto.setCanAdjust(ri.isCanAdjust());
        dto.setFailReasons(ri.getFailReasons() == null ? new ArrayList<>() : new ArrayList<>(ri.getFailReasons()));
        // 取得 Excel 结果中的调整来源
        dto.setAdjustType(resolveAdjustTypeLabel(dto.getItemTag()));
        if (dto.isCanAdjust()) {
            dto.setAdjustNote(dto.getAdjustType() + (dto.getAdjustMode() == null ? "" : dto.getAdjustMode()));
        }
        // 4. 转换流程候选并设置默认选择
        List<CrmwPoolExcelImportCheckItemDto.FlowOptionDto> flowOptions = new ArrayList<>();
        if (ri.getFlowOptions() != null) {
            for (AdjustCheckDto.FlowOption fo : ri.getFlowOptions()) {
                // 转换 Excel 导入使用的流程候选
                flowOptions.add(toFlowOptionDto(fo));
            }
        }
        dto.setFlowOptions(flowOptions);
        // 为 Excel 调整项设置默认流程
        applyDefaultSelectedFlow(dto);
        return dto;
    }

    /**
     * 补全映射后的 CRMW 组合展示字段
     *
     * 执行顺序：
     * 1. 查询凭证并补齐缺少的名称和市场
     * 2. 查询标的并补齐缺少的名称、类型和市场
     *
     * @param dto 待补充信息的导入校验结果
     * @param infoCache 本次操作共用的证券资料缓存
     */
    private void fillMappedDisplay(CrmwPoolExcelImportCheckItemDto dto,
                                   Map<String, SecurityInfoDetailDto> infoCache) {
        // 查询证券资料，复用已缓存的结果
        SecurityInfoDetailDto crmw = lookupSecurity(dto.getCrmwScode(), infoCache);
        if (crmw != null) {
            // 检查该值是否已填写
            if (isBlank(dto.getCrmwName())) {
                // 取得导入页面显示的证券名称
                dto.setCrmwName(resolveDisplayName(crmw));
            }
            // 检查该值是否已填写
            if (isBlank(dto.getCrmwMarket())) {
                // 取得导入页面显示的证券市场代码
                dto.setCrmwMarket(resolveMarketCodes(crmw));
            }
        }
        // 查询证券资料，复用已缓存的结果
        SecurityInfoDetailDto security = lookupSecurity(dto.getSecurityCode(), infoCache);
        if (security != null) {
            // 检查该值是否已填写
            if (isBlank(dto.getSecurityName())) {
                // 取得导入页面显示的证券名称
                dto.setSecurityName(resolveDisplayName(security));
            }
            // 检查该值是否已填写
            if (isBlank(dto.getSecurityShortName())) {
                // 统一未填或纯空白的值，保留非空文本
                dto.setSecurityShortName(trimToNull(security.getShortName()));
            }
            // 检查该值是否已填写
            if (isBlank(dto.getSecurityType())) {
                dto.setSecurityType(security.getSecurityType());
            }
            // 检查该值是否已填写
            if (isBlank(dto.getSecurityTypeName())) {
                dto.setSecurityTypeName(security.getSecurityTypeName());
            }
            // 检查该值是否已填写
            if (isBlank(dto.getSecurityMarket())) {
                // 取得导入页面显示的证券市场代码
                dto.setSecurityMarket(resolveMarketCodes(security));
            }
        }
    }

    /**
     * 为导入行填充凭证和标的的展示信息
     *
     * 执行顺序：
     * 1. 按凭证代码查询主数据并填入凭证展示字段
     * 2. 按标的代码查询主数据并填入标的展示字段
     *
     * @param item 当前调整项
     * @param infoCache 本次操作共用的证券资料缓存
     */
    private void fillSecurityDisplay(SysImpTmpDetlBo item, Map<String, SecurityInfoDetailDto> infoCache) {
        // 查询证券资料，复用已缓存的结果
        SecurityInfoDetailDto crmw = lookupSecurity(item.getFld001(), infoCache);
        if (crmw != null) {
            // 取得导入页面显示的证券名称
            item.setFld002(resolveDisplayName(crmw));
            // 取得导入页面显示的证券市场代码
            item.setFld003(resolveMarketCodes(crmw));
            // 凭证类型缺失时按 crmw 处理
            item.setFld008(resolveCrmwStype(crmw.getSecurityType()));
        }
        // 查询证券资料，复用已缓存的结果
        SecurityInfoDetailDto security = lookupSecurity(item.getFld004(), infoCache);
        if (security != null) {
            // 取得导入页面显示的证券名称
            item.setFld005(resolveDisplayName(security));
            item.setFld006(security.getSecurityType());
            // 取得导入页面显示的证券市场代码
            item.setFld007(resolveMarketCodes(security));
            item.setFld011(security.getSecurityTypeName());
        }
    }

    /**
     * 检查导入行的凭证和标的主数据
     *
     * 执行顺序：
     * 1. 确认凭证存在且类型为 crmw
     * 2. 确认标的存在且类型不是 crmw
     * 3. 收集两项检查的失败原因
     *
     * @param item 当前调整项
     * @param infoCache 本次操作共用的证券资料缓存
     * @return 组合主数据失败原因，空列表表示通过
     */
    private List<String> validateComboMasterData(SysImpTmpDetlBo item,
                                                 Map<String, SecurityInfoDetailDto> infoCache) {
        List<String> reasons = new ArrayList<>();
        // 查询证券资料，复用已缓存的结果
        SecurityInfoDetailDto crmw = lookupSecurity(item.getFld001(), infoCache);
        if (crmw == null) {
            reasons.add("CRMW凭证不存在");
        } else if (!CRMW_SECURITY_TYPE.equals(crmw.getSecurityType())) {
            reasons.add("所选代码不是 CRMW 凭证");
        }
        // 查询证券资料，复用已缓存的结果
        SecurityInfoDetailDto security = lookupSecurity(item.getFld004(), infoCache);
        if (security == null) {
            reasons.add("证券不存在");
        } else if (CRMW_SECURITY_TYPE.equals(security.getSecurityType())) {
            reasons.add("调库对象不能是 CRMW 凭证");
        }
        return reasons;
    }

    /**
     * 按证券代码读取主数据并在本次操作中复用
     *
     * 不存在的证券也缓存结果，避免同一批次重复查询
     *
     * 执行顺序：
     * 1. 清理证券代码，空代码返回 null
     * 2. 缓存已包含该代码时直接使用结果
     * 3. 查询证券及其市场代码，将查询结果写入缓存
     *
     * @param code 待查询的证券代码
     * @param cache 本次操作已查询的证券资料
     * @return 证券资料，不存在时为 null
     */
    private SecurityInfoDetailDto lookupSecurity(String code, Map<String, SecurityInfoDetailDto> cache) {
        // 清理首尾空白，未填写时使用空字符串
        String key = trimToEmpty(code);
        if (key.isEmpty()) {
            return null;
        }
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        SecurityInfoDetailDto info = crmwPoolExcelImportMapper.querySecurityImportInfoByCode(key);
        if (info != null) {
            info.setMarketCode(securityMarketService.queryMarketCode(key));
        }
        cache.put(key, info);
        return info;
    }

    /**
     * 取得导入页面显示的证券名称
     *
     * 执行顺序：
     * 1. 优先使用非空的证券全名，否则使用证券简称
     *
     * @param info 用于页面展示的证券资料
     * @return 去掉首尾空白的证券名称，无名称时为 null
     */
    private String resolveDisplayName(SecurityInfoDetailDto info) {
        if (info == null) {
            return null;
        }
        // 检查该值是否已填写
        if (!isBlank(info.getFullName())) {
            return info.getFullName().trim();
        }
        // 统一未填或纯空白的值，保留非空文本
        return trimToNull(info.getShortName());
    }

    /**
     * 取得导入页面显示的证券市场代码
     *
     * 执行顺序：
     * 1. 证券资料存在时读取其市场代码
     *
     * @param info 用于页面展示的证券资料
     * @return 证券市场代码，无资料时为 null
     */
    private String resolveMarketCodes(SecurityInfoDetailDto info) {
        if (info == null) {
            return null;
        }
        return info.getMarketCode();
    }

    /**
     * 取得实际标的证券的简称
     *
     * 执行顺序：
     * 1. 查询标的主数据，简称非空时使用简称，否则使用传入的证券全名
     *
     * @param fullName 用作简称缺省值的证券全名
     * @param securityCode 标的证券代码
     * @param infoCache 本次操作共用的证券资料缓存
     * @return 去掉首尾空白的证券简称，无名称时为 null
     */
    private String resolveShortName(String fullName, String securityCode,
                                    Map<String, SecurityInfoDetailDto> infoCache) {
        // 查询证券资料，复用已缓存的结果
        SecurityInfoDetailDto info = lookupSecurity(securityCode, infoCache);
        // 检查该值是否已填写
        if (info != null && !isBlank(info.getShortName())) {
            return info.getShortName().trim();
        }
        // 统一未填或纯空白的值，保留非空文本
        return trimToNull(fullName);
    }

    /**
     * 取得导入结果使用的凭证类型
     *
     * 执行顺序：
     * 1. 凭证类型为空时使用 crmw，其余类型去掉首尾空白
     *
     * @param crmwStype CRMW 凭证的证券类型
     * @return 凭证证券类型
     */
    private String resolveCrmwStype(String crmwStype) {
        // 检查该值是否已填写
        return isBlank(crmwStype) ? CRMW_SECURITY_TYPE : crmwStype.trim();
    }

    /**
     * 组装未通过 Excel 校验的手工项
     *
     * 执行顺序：
     * 1. 复制可用的导入行和目标池信息
     * 2. 填入方向、手工项标记及分组，将结果标为不可调整并保留原因
     *
     * @param sourceItem 原始导入明细
     * @param pool 投资池配置
     * @param adjustMode 调整方向
     * @param reasons 用于展示或保存的失败原因
     * @return 不可提交的 Excel 校验结果
     */
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
            // 凭证类型缺失时按 crmw 处理
            dto.setCrmwStype(resolveCrmwStype(sourceItem.getFld008()));
            dto.setSecurityTypeName(sourceItem.getFld011());
            // 生成导入行的 CRMW 组合去重标识
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

    /**
     * 转换 Excel 导入使用的流程候选
     *
     * 执行顺序：
     * 1. 复制流程标识、选择状态和匹配原因
     * 2. 名称为空或看起来是编码时按类型补齐展示名称
     * 3. 生成候选键
     *
     * @param fo 共用调库校验返回的流程候选
     * @return Excel 页面使用的流程候选
     */
    private CrmwPoolExcelImportCheckItemDto.FlowOptionDto toFlowOptionDto(AdjustCheckDto.FlowOption fo) {
        CrmwPoolExcelImportCheckItemDto.FlowOptionDto opt = new CrmwPoolExcelImportCheckItemDto.FlowOptionDto();
        opt.setFlowId(fo.getFlowId());
        opt.setFlowKey(fo.getFlowKey());
        String name = fo.getFlowName();
        // 检查该值是否已填写，并判断流程名称是否需要替换为易读名称
        if (isBlank(name) || looksLikeCode(name)) {
            // 用流程类型取得默认展示名称
            name = resolveFlowTypeDisplayName(fo.getFlowType());
        }
        opt.setFlowName(name);
        opt.setFlowType(fo.getFlowType());
        opt.setRecommended(fo.isRecommended());
        opt.setMatched(fo.isMatched());
        opt.setSelectable(fo.isSelectable());
        opt.setMatchReasons(fo.getMatchReasons() == null ? new ArrayList<>() : new ArrayList<>(fo.getMatchReasons()));
        opt.setUnmatchReasons(fo.getUnmatchReasons() == null ? new ArrayList<>() : new ArrayList<>(fo.getUnmatchReasons()));
        // 为 Excel 流程候选生成选择标识
        opt.setOptionKey(buildFlowOptionKey(opt));
        return opt;
    }

    /**
     * 为 Excel 调整项设置默认流程
     *
     * 执行顺序：
     * 1. 优先选择推荐且可选的候选，其次选择第一个可选候选
     * 2. 有可选流程时写入其标识和名称
     *
     * @param dto 待补充信息的导入校验结果
     */
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
        // 检查该值是否已填写
        dto.setDirectFlow(selected.getFlowId() == null && isBlank(selected.getFlowKey()));
    }

    /**
     * 筛出 Excel 批次实际可以提交的调整项
     *
     * 执行顺序：
     * 1. 优先使用本次提交传入的校验结果，没有时读取批次保存的结果
     * 2. 保留通过且凭证代码、标的代码和目标池齐全的项
     *
     * @param req 含可选校验结果的导入提交请求
     * @param batch 导入批次记录
     * @return 本次可以提交的调整项
     */
    private List<CrmwPoolExcelImportCheckItemDto> filterSubmittableCheckItems(
            CrmwPoolExcelImportReq req, SysImpTmpBo batch) {
        List<CrmwPoolExcelImportCheckItemDto> source;
        if (req.getCheckItems() != null && !req.getCheckItems().isEmpty()) {
            source = req.getCheckItems();
        } else {
            // 从导入批次的 JSON 读取校验明细
            source = parseCheckItems(batch.getResultJson());
        }
        List<CrmwPoolExcelImportCheckItemDto> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (CrmwPoolExcelImportCheckItemDto ci : source) {
            if (ci != null && ci.isCanAdjust()
                    // 检查该值是否已填写
                    && !isBlank(ci.getSecurityCode()) && !isBlank(ci.getCrmwScode())
                    && ci.getTargetPoolId() != null) {
                result.add(ci);
            }
        }
        return result;
    }

    /**
     * 找到同组 Excel 调整中的主项
     *
     * 执行顺序：
     * 1. 优先选第一条手工项，没有手工项时使用组内第一条
     *
     * @param items 同一来源组合的调整项列表
     * @return 用于填充提交请求的主项
     */
    private CrmwPoolExcelImportCheckItemDto resolvePrimaryItem(List<CrmwPoolExcelImportCheckItemDto> items) {
        for (CrmwPoolExcelImportCheckItemDto item : items) {
            // 判断 Excel 调整项是否为手工项
            if (isManualTag(item.getItemTag())) {
                return item;
            }
        }
        return items.get(0);
    }

    /**
     * 判断 Excel 调整项是否为手工项
     *
     * 执行顺序：
     * 1. 将未填写来源标记和明确标为手工的调整项视为手工项
     *
     * @param itemTag 调整项来源标记
     * @return 调整项为手工项时返回 true
     */
    private boolean isManualTag(String itemTag) {
        return itemTag == null || itemTag.isEmpty() || ItemType.MANUAL.getCode().equals(itemTag);
    }

    /**
     * 取得 Excel 结果中的调整来源
     *
     * 执行顺序：
     * 1. 按来源标记区分联动、互斥和关联调整，其余使用 Excel 导入
     *
     * @param itemTag 调整项来源标记
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
        return ADJUST_TYPE_EXCEL;
    }

    /**
     * 为 Excel 流程候选生成选择标识
     *
     * 执行顺序：
     * 1. 拼接流程类型、ID 和编码，缺失值按约定填入
     *
     * @param opt Excel 页面使用的流程候选
     * @return 用于选择流程的候选键
     */
    private String buildFlowOptionKey(CrmwPoolExcelImportCheckItemDto.FlowOptionDto opt) {
        return (opt.getFlowType() == null ? "" : opt.getFlowType()) + "_"
                + (opt.getFlowId() == null ? "null" : opt.getFlowId()) + "_"
                + (opt.getFlowKey() == null ? "" : opt.getFlowKey());
    }

    /**
     * 用流程类型取得默认展示名称
     *
     * 执行顺序：
     * 1. 按已知流程类型选择对应名称，无法识别时使用通用审批流程名称
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
        if (FlowType.UPGRADE_INBOUND.getCode().equals(flowType)) {
            return "上调流程";
        }
        if (FlowType.DOWNGRADE_INBOUND.getCode().equals(flowType)) {
            return "下调流程";
        }
        return "审批流程";
    }

    /**
     * 判断流程名称是否需要替换为易读名称
     *
     * 执行顺序：
     * 1. 空名称直接视为需要替换，其余检查是否为不含中文的编码样式
     *
     * @param text 待处理的文本
     * @return 名称为空或像编码时返回 true
     */
    private boolean looksLikeCode(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        return text.matches("^[a-zA-Z0-9_.:\\-]+$") && !text.matches(".*[\\u4e00-\\u9fa5].*");
    }

    /**
     * 检查用户是否能向目标 CRMW 池导入 Excel
     *
     * 执行顺序：
     * 1. 管理员直接通过
     * 2. 解析用户 ID 并加载角色
     * 3. 匹配目标池的 Excel 导入权限，未直接授权给用户或其角色时拒绝导入
     *
     * @param currentUserId 当前用户 ID
     * @param poolId 投资池 ID
     */
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

    /**
     * 检查 Excel 上传所需参数和文件
     *
     * 执行顺序：
     * 1. 检查请求、方向、目标池和操作人
     * 2. 确认上传文件非空且未超过五兆字节
     *
     * @param req 含目标池、方向和用户 ID 的上传请求
     * @param file 上传的 Excel 文件
     */
    private void validateUploadReq(CrmwPoolExcelImportReq req, MultipartFile file) {
        if (req == null) {
            throw new BizException("请求参数不能为空");
        }
        // 检查 Excel 导入方向
        normalizeDirection(req.getDirection());
        if (req.getTargetPoolId() == null) {
            throw new BizException("目标投资池 ID 不能为空");
        }
        // 检查该值是否已填写
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

    /**
     * 取得导入使用的目标 CRMW 池
     *
     * 执行顺序：
     * 1. 确认目标池 ID 已填
     * 2. 查询启用的叶子 CRMW 池，找不到时提示目标池不可用
     *
     * @param poolId 投资池 ID
     * @return 导入目标池
     */
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

    /**
     * 检查 Excel 导入方向
     *
     * 执行顺序：
     * 1. 只接受调入和调出的方向编码，其他值提示错误
     *
     * @param direction 请求中的调整方向编码
     * @return 校验后的方向编码
     */
    private String normalizeDirection(String direction) {
        if (!"in".equals(direction) && !"out".equals(direction)) {
            throw new BizException("调整方向必须为 in 或 out");
        }
        return direction;
    }

    /**
     * 取得 Excel 导入批次
     *
     * 执行顺序：
     * 1. 确认批次号已填
     * 2. 去掉批次号首尾空白后查询，未找到时提示任务不存在
     *
     * @param impId Excel 导入批次号
     * @return Excel 导入批次
     */
    private SysImpTmpBo requireBatch(String impId) {
        // 检查该值是否已填写
        if (isBlank(impId)) {
            throw new BizException("导入批次号不能为空");
        }
        SysImpTmpBo batch = crmwPoolExcelImportMapper.queryByImpId(impId.trim());
        if (batch == null) {
            throw new BizException("导入批次不存在或已取消");
        }
        return batch;
    }

    /**
     * 取得导入日志使用的操作人名称
     *
     * 执行顺序：
     * 1. 有操作人名称时使用名称，否则使用用户 ID，去掉首尾空白
     *
     * @param req 含用户 ID 和可选用户名的导入请求
     * @return 用于记录导入操作的名称
     */
    private String resolveUserName(CrmwPoolExcelImportReq req) {
        // 检查该值是否已填写
        if (!isBlank(req.getCurrentUserName())) {
            return req.getCurrentUserName().trim();
        }
        return req.getCurrentUserId().trim();
    }

    /**
     * 生成 Excel 导入批次号
     *
     * 执行顺序：
     * 1. 拼接 IMP 前缀、当前时间和四位流水号
     *
     * @param now 本次操作时间
     * @return 新生成的导入批次号
     */
    private String nextImpId(Date now) {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(now);
        int seq = IMP_SEQ.incrementAndGet() % 10000;
        return "IMP" + ts + String.format("%04d", seq);
    }

    /**
     * 生成 Excel 导入明细号
     *
     * 执行顺序：
     * 1. 拼接 IMPD 前缀、当前时间和五位流水号
     *
     * @param now 本次操作时间
     * @return 新生成的导入明细号
     */
    private String nextDetlId(Date now) {
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(now);
        int seq = DETL_SEQ.incrementAndGet() % 100000;
        return "IMPD" + ts + String.format("%05d", seq);
    }

    /**
     * 将保存的导入批次转换成任务详情
     *
     * 执行顺序：
     * 1. 复制批次状态、导入选项和统计信息
     * 2. 查询待检查的明细数量
     * 3. 读取保存的检查结果并补充完成状态及通过、失败数量
     *
     * @param batch 导入批次记录
     * @return 导入任务详情
     */
    private CrmwPoolExcelImportDto toTaskDto(SysImpTmpBo batch) {
        CrmwPoolExcelImportDto dto = new CrmwPoolExcelImportDto();
        dto.setImpId(batch.getImpId());
        dto.setBizType(batch.getBizType());
        dto.setFileName(batch.getFileName());
        dto.setBizMode(batch.getFld001());
        dto.setOptionJson(batch.getOptionJson());
        dto.setReason(batch.getFld002());
        dto.setAdvice(batch.getFld003());
        // 解析导入选项中的目标池 ID
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
        // 从导入批次的 JSON 读取校验明细
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

    /**
     * 将本次 Excel 校验结果转成可保存的 JSON
     *
     * 执行顺序：
     * 1. 将完成标记、通过和失败数及结果明细写入 JSON
     * 2. 序列化失败时保留完成标记和空明细
     *
     * @param checkItems Excel 导入校验结果
     * @param checkPass 校验通过项数
     * @param checkFail 校验失败项数
     * @return 供导入批次保存的校验结果 JSON
     */
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

    /**
     * 从导入批次的 JSON 读取校验明细
     *
     * 执行顺序：
     * 1. 解析 JSON 并确认明细字段为数组
     * 2. 将数组转换成检查结果列表，无法解析时返回 null
     *
     * @param resultJson 批次保存的校验结果 JSON
     * @return 保存的校验结果列表，缺失或无法解析时为 null
     */
    private List<CrmwPoolExcelImportCheckItemDto> parseCheckItems(String resultJson) {
        // 检查该值是否已填写
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

    /**
     * 将保存的导入明细转换成展示对象
     *
     * 执行顺序：
     * 1. 把明细记录中的源数据还原成凭证和标的展示字段
     * 2. 补充行号、校验状态和失败说明
     *
     * @param bo 已保存的导入明细记录
     * @return 导入行的展示数据
     */
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
        // 解析导入选项中的目标池 ID
        dto.setResolvedPoolId(parsePoolId(bo.getFld009()));
        dto.setChkRslt(bo.getChkRslt());
        dto.setChkDscr(bo.getChkDscr());
        dto.setSaveRslt(bo.getSaveRslt());
        dto.setSaveDscr(bo.getSaveDscr());
        dto.setRefId(bo.getRefId());
        return dto;
    }

    /**
     * 生成导入批次保存的目标池选项
     *
     * 执行顺序：
     * 1. 写入池 ID、经转义的名称和 CRMW 池类型
     *
     * @param pool 投资池配置
     * @return 目标池选项 JSON
     */
    private String buildOptionJson(InvestmentPoolBo pool) {
        return "{\"targetPoolId\":" + pool.getId()
                // 转义写入 JSON 字符串的文本
                + ",\"targetPoolName\":\"" + escapeJson(pool.getPoolName())
                + "\",\"poolType\":\"" + PoolType.CRMW.getCode() + "\"}";
    }

    /**
     * 转义写入 JSON 字符串的文本
     *
     * 执行顺序：
     * 1. 空值转成空字符串，其余对反斜线和双引号转义
     *
     * @param text 待处理的文本
     * @return 转义后的文本
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 记录一条导入明细的校验失败
     *
     * 执行顺序：
     * 1. 将明细标为失败，保存合并后的原因和检查时间
     * 2. 更新该明细的校验记录
     *
     * @param item 当前调整项
     * @param reasons 用于展示或保存的失败原因
     * @param now 本次操作时间
     */
    private void markItemFail(SysImpTmpDetlBo item, List<String> reasons, Date now) {
        item.setChkRslt("2");
        // 整理导入失败原因以便保存
        item.setChkDscr(joinReasons(reasons));
        item.setUpdtTime(now);
        crmwPoolExcelImportMapper.updateItemCheckResult(item);
    }

    /**
     * 按 Excel 表头读取一格文本
     *
     * 执行顺序：
     * 1. 优先匹配原始表头，未命中时用去掉首尾空白的表头匹配
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
            // 清理首尾空白，未填写时使用空字符串
            return trimToEmpty(row.get(header));
        }
        for (Map.Entry<String, String> e : row.entrySet()) {
            if (e.getKey() != null && header.equals(e.getKey().trim())) {
                // 清理首尾空白，未填写时使用空字符串
                return trimToEmpty(e.getValue());
            }
        }
        return "";
    }

    /**
     * 解析导入选项中的目标池 ID
     *
     * 执行顺序：
     * 1. 去掉首尾空白并转换为数字，空值或格式错误时返回 null
     *
     * @param text 导入选项保存的池 ID 文本
     * @return 数字格式的池 ID，无法解析时为 null
     */
    private Long parsePoolId(String text) {
        // 检查该值是否已填写
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
     * 解析导入明细的 Excel 行号
     *
     * 执行顺序：
     * 1. 将文本转换为整数，无法转换时使用 0
     *
     * @param text 导入明细保存的行号文本
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
     * 生成导入行的 CRMW 组合去重标识
     *
     * 执行顺序：
     * 1. 将凭证和标的代码去掉首尾空白后用竖线拼接
     *
     * @param crmwScode CRMW 凭证代码
     * @param securityCode 标的证券代码
     * @return 导入组合去重标识
     */
    private String buildCombinationKey(String crmwScode, String securityCode) {
        // 清理首尾空白，未填写时使用空字符串
        return trimToEmpty(crmwScode) + "|" + trimToEmpty(securityCode);
    }

    /**
     * 整理导入失败原因以便保存
     *
     * 执行顺序：
     * 1. 按原顺序用中文分号合并原因
     * 2. 超过五百字符时截取前五百字符
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
     * 清理文本并将空白统一为 null
     *
     * 执行顺序：
     * 1. 去掉首尾空白，空值或清理后为空时返回 null
     *
     * @param s 待处理的文本
     * @return 清理后的文本
     */
    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 清理文本并将空值统一为空字符串
     *
     * 执行顺序：
     * 1. 去掉首尾空白，空值使用空字符串
     *
     * @param s 待处理的文本
     * @return 清理后的文本
     */
    private String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * 判断文本是否未填写
     *
     * 执行顺序：
     * 1. 检查文本是否为 null 或去掉首尾空白后为空
     *
     * @param s 待处理的文本
     * @return 文本未填写时返回 true
     */
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
