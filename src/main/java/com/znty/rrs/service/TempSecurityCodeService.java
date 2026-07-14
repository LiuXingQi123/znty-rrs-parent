package com.znty.rrs.service;

import com.znty.rrs.common.enums.MarketCode;
import com.znty.rrs.common.enums.TempStatus;
import com.znty.rrs.common.enums.TempOperationType;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.bo.TempSecurityCodeBo;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeDto;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.TempSecurityCodeMapper;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 临时代码管理服务，处理临时代码新增、查询、更新正式证券、取消发行和删除
 */
@Service
public class TempSecurityCodeService {

    /** 临时代码管理数据访问组件 */
    @Resource
    private TempSecurityCodeMapper tempSecurityCodeMapper;

    /**
     * 分页查询临时代码列表
     */
    public PageResult<TempSecurityCodeDto> queryTempSecurityCodePage(TempSecurityCodeReq req) {
        if (req == null) {
            req = new TempSecurityCodeReq();
        }
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        // 查询临时代码分页列表
        java.util.List<TempSecurityCodeDto> list = tempSecurityCodeMapper.queryTempSecurityCodePage(req);
        PageInfo<TempSecurityCodeDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 查询新增页面下拉选项
     */
    public TempSecurityCodeDto.OptionBundle queryTempSecurityCodeOptions(TempSecurityCodeReq req) {
        if (req == null) {
            req = new TempSecurityCodeReq();
        }
        TempSecurityCodeDto.OptionBundle bundle = new TempSecurityCodeDto.OptionBundle();
        // 查询发行主体下拉选项
        bundle.setCompanies(tempSecurityCodeMapper.queryCompanyOptionList(req));
        // 查询证券类型下拉选项
        bundle.setSecurityTypes(tempSecurityCodeMapper.querySecurityTypeList());
        return bundle;
    }

    /**
     * 新增临时代码
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto addTempSecurityCode(TempSecurityCodeReq req) {
        // 校验新增请求参数
        validateAddReq(req);
        Date now = new Date();
        // 查询发行主体并生成快照
        TempSecurityCodeDto.CompanyOption company = tempSecurityCodeMapper.queryCompanyByCode(req.getTempCompanyCode());
        if (company == null) {
            throw new BizException("发行主体不存在，tempCompanyCode=" + req.getTempCompanyCode());
        }
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setTempSecurityName(req.getTempSecurityName());
        bo.setTempSecurityCode(req.getTempSecurityCode());
        bo.setTempSecurityMarket(req.getTempSecurityMarket());
        bo.setTempSecurityType(req.getTempSecurityType());
        bo.setTempMitigationCode(req.getTempMitigationCode());
        bo.setTempCompanyCode(req.getTempCompanyCode());
        // 解析主体展示名称
        bo.setTempCompanyNameSnapshot(resolveCompanyName(company));
        bo.setTempIssueDate(req.getTempIssueDate());
        bo.setTempMaturityDate(req.getTempMaturityDate());
        bo.setStatus(TempStatus.TEMPORARY.getCode());
        bo.setOperationType(TempOperationType.ADD.getCode());
        bo.setIsDeleted(0);
        bo.setCrteTime(now);
        bo.setUpdtTime(now);
        tempSecurityCodeMapper.addTempSecurityCode(bo);
        // 同步临时代码证券主数据
        addTempSecurityInfo(bo);
        // 查询新增后的临时代码详情
        return queryTempSecurityCodeDetail(bo.getId());
    }

    /**
     * 更新临时代码为正式证券
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto editTempSecurityCodeToUpdated(TempSecurityCodeReq req) {
        // 校验更新请求参数
        validateUpdateReq(req);
        // 查询并校验临时代码当前状态
        TempSecurityCodeBo oldBo = queryOperableTempSecurityCode(req.getId());
        Date now = new Date();
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setId(oldBo.getId());
        // 临时证券字段：允许在更新时编辑，从请求取值
        bo.setTempSecurityName(req.getTempSecurityName());
        bo.setTempSecurityCode(req.getTempSecurityCode());
        bo.setTempSecurityMarket(req.getTempSecurityMarket());
        bo.setTempSecurityType(req.getTempSecurityType());
        bo.setTempMitigationCode(req.getTempMitigationCode());
        bo.setTempCompanyCode(req.getTempCompanyCode());
        // 主体变更时重新生成主体名称快照，未变更则保留原快照
        if (req.getTempCompanyCode() != null && !req.getTempCompanyCode().equals(oldBo.getTempCompanyCode())) {
            TempSecurityCodeDto.CompanyOption company = tempSecurityCodeMapper.queryCompanyByCode(req.getTempCompanyCode());
            if (company == null) {
                throw new BizException("发行主体不存在，tempCompanyCode=" + req.getTempCompanyCode());
            }
            // 解析主体展示名称
            bo.setTempCompanyNameSnapshot(resolveCompanyName(company));
        } else {
            bo.setTempCompanyNameSnapshot(oldBo.getTempCompanyNameSnapshot());
        }
        bo.setTempIssueDate(req.getTempIssueDate());
        bo.setTempMaturityDate(req.getTempMaturityDate());
        // 正式证券字段
        bo.setSecurityName(req.getSecurityName());
        bo.setSecurityCode(req.getSecurityCode());
        bo.setSecurityMarket(req.getSecurityMarket());
        bo.setSecurityType(req.getSecurityType());
        bo.setUpdateTime(now);
        bo.setStatus(TempStatus.UPDATED.getCode());
        bo.setOperationType(TempOperationType.UPDATE.getCode());
        bo.setUpdtTime(now);
        // 同步正式证券基础信息
        upsertSecurityInfo(bo);
        // 替换临时代码核心业务引用
        replaceTempSecurityReferences(oldBo, bo);
        // 禁用原临时代码占位证券主数据
        disableTempSecurityInfo(oldBo, now);
        tempSecurityCodeMapper.editTempSecurityCodeToUpdated(bo);
        // 查询更新后的临时代码详情
        return queryTempSecurityCodeDetail(bo.getId());
    }

    /**
     * 取消发行临时代码
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto editTempSecurityCodeToCancelled(TempSecurityCodeReq req) {
        // 校验主键 ID
        validateIdReq(req);
        // 查询并校验临时代码当前状态
        TempSecurityCodeBo oldBo = queryOperableTempSecurityCode(req.getId());
        Date now = new Date();
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setId(oldBo.getId());
        bo.setUpdateTime(now);
        bo.setStatus(TempStatus.CANCELLED.getCode());
        bo.setOperationType(TempOperationType.CANCEL_ISSUE.getCode());
        bo.setUpdtTime(now);
        bo.setTempSecurityCode(oldBo.getTempSecurityCode());
        // 计算取消发行日期
        bo.setCancelDate(queryCancelIssueDate());
        tempSecurityCodeMapper.editSecurityInfoToCancelled(bo);
        tempSecurityCodeMapper.editTempSecurityCodeToCancelled(bo);
        // 查询取消后的临时代码详情
        return queryTempSecurityCodeDetail(bo.getId());
    }

    /**
     * 删除临时代码
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto deleteTempSecurityCode(TempSecurityCodeReq req) {
        // 校验主键 ID
        validateIdReq(req);
        // 查询并校验临时代码存在
        TempSecurityCodeBo oldBo = queryExistingTempSecurityCode(req.getId());
        // 校验临时代码未被核心调库业务引用
        validateNoCoreReference(oldBo);
        Date now = new Date();
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setId(oldBo.getId());
        bo.setUpdateTime(now);
        bo.setStatus(TempStatus.DELETED.getCode());
        bo.setOperationType(TempOperationType.DELETE.getCode());
        bo.setIsDeleted(1);
        bo.setUpdtTime(now);
        tempSecurityCodeMapper.deleteTempSecurityCode(bo);
        return null;
    }

    /**
     * 新增参数校验
     */
    private void validateAddReq(TempSecurityCodeReq req) {
        if (req == null) {
            throw new BizException("请求参数不能为空");
        }
        // 校验临时证券名称必填
        validateRequired(req.getTempSecurityName(), "临时证券名称不能为空");
        // 校验临时证券代码必填
        validateRequired(req.getTempSecurityCode(), "临时证券代码不能为空");
        // 校验临时证券市场必填
        validateRequired(req.getTempSecurityMarket(), "临时证券市场不能为空");
        // 校验临时证券类型必填
        validateRequired(req.getTempSecurityType(), "临时证券类型不能为空");
        if (req.getTempCompanyCode() == null || req.getTempCompanyCode().trim().isEmpty()) {
            throw new BizException("发行主体不能为空");
        }
        if (req.getTempIssueDate() == null) {
            throw new BizException("发行日期不能为空");
        }
        if (req.getTempMaturityDate() == null) {
            throw new BizException("到期日期不能为空");
        }
        if (!req.getTempMaturityDate().after(req.getTempIssueDate())) {
            throw new BizException("到期日期必须晚于发行日期");
        }
        // 校验临时证券市场合法性
        validateMarket(req.getTempSecurityMarket(), "临时证券市场不合法");
        // 校验临时证券类型合法性
        validateSecurityType(req.getTempSecurityType());
        int count = tempSecurityCodeMapper.queryTempSecurityCodeCount(req.getTempSecurityCode(), null);
        if (count > 0) {
            throw new BizException("临时证券代码已存在，tempSecurityCode=" + req.getTempSecurityCode());
        }
    }

    /**
     * 更新参数校验
     */
    private void validateUpdateReq(TempSecurityCodeReq req) {
        // 校验主键 ID
        validateIdReq(req);
        // 临时证券字段校验（与新增一致，允许在更新时编辑临时证券信息）
        validateRequired(req.getTempSecurityName(), "临时证券名称不能为空");
        // 校验临时证券代码必填
        validateRequired(req.getTempSecurityCode(), "临时证券代码不能为空");
        // 校验临时证券市场必填
        validateRequired(req.getTempSecurityMarket(), "临时证券市场不能为空");
        // 校验临时证券类型必填
        validateRequired(req.getTempSecurityType(), "临时证券类型不能为空");
        if (req.getTempCompanyCode() == null || req.getTempCompanyCode().trim().isEmpty()) {
            throw new BizException("发行主体不能为空");
        }
        if (req.getTempIssueDate() == null) {
            throw new BizException("发行日期不能为空");
        }
        if (req.getTempMaturityDate() == null) {
            throw new BizException("到期日期不能为空");
        }
        if (!req.getTempMaturityDate().after(req.getTempIssueDate())) {
            throw new BizException("到期日期必须晚于发行日期");
        }
        // 校验临时证券市场合法性
        validateMarket(req.getTempSecurityMarket(), "临时证券市场不合法");
        // 校验临时证券类型合法性
        validateSecurityType(req.getTempSecurityType());
        // 临时证券代码唯一性校验（排除自身）
        int count = tempSecurityCodeMapper.queryTempSecurityCodeCount(req.getTempSecurityCode(), req.getId());
        if (count > 0) {
            throw new BizException("临时证券代码已存在，tempSecurityCode=" + req.getTempSecurityCode());
        }
        // 正式证券字段校验
        validateRequired(req.getSecurityName(), "证券名称不能为空");
        // 校验正式证券代码必填
        validateRequired(req.getSecurityCode(), "证券代码不能为空");
        // 校验正式证券市场必填
        validateRequired(req.getSecurityMarket(), "证券市场不能为空");
        // 校验正式证券类型必填
        validateRequired(req.getSecurityType(), "证券类型不能为空");
        // 校验正式证券市场合法性
        validateMarket(req.getSecurityMarket(), "证券市场不合法");
        // 校验正式证券类型合法性
        validateSecurityType(req.getSecurityType());
    }

    /**
     * ID 参数校验
     */
    private void validateIdReq(TempSecurityCodeReq req) {
        if (req == null || req.getId() == null) {
            throw new BizException("主键 ID 不能为空");
        }
    }

    /**
     * 校验必填字符串
     */
    private void validateRequired(String value, String message) {
        if (value == null || value.trim().length() == 0) {
            throw new BizException(message);
        }
    }

    /**
     * 校验证券市场（仅允许 MarketCode 标准 8 码）
     */
    private void validateMarket(String market, String message) {
        if (!MarketCode.isValid(market)) {
            throw new BizException(message + "，market=" + market);
        }
    }

    /**
     * 校验证券类型
     */
    private void validateSecurityType(String securityType) {
        int count = tempSecurityCodeMapper.querySecurityTypeCount(securityType);
        if (count <= 0) {
            throw new BizException("证券类型不存在或已删除，securityType=" + securityType);
        }
    }

    /**
     * 查询可操作临时代码
     */
    private TempSecurityCodeBo queryOperableTempSecurityCode(Long id) {
        // 查询临时代码原始记录
        TempSecurityCodeBo bo = queryExistingTempSecurityCode(id);
        if (!TempStatus.TEMPORARY.getCode().equals(bo.getStatus())) {
            throw new BizException("只有临时状态可以执行该操作，id=" + id + "，status=" + bo.getStatus());
        }
        return bo;
    }

    /**
     * 查询未删除临时代码
     */
    private TempSecurityCodeBo queryExistingTempSecurityCode(Long id) {
        TempSecurityCodeBo bo = tempSecurityCodeMapper.queryTempSecurityCodeById(id);
        if (bo == null) {
            throw new BizException("临时代码不存在或已删除，id=" + id);
        }
        return bo;
    }

    /**
     * 同步正式证券基础信息
     */
    private void upsertSecurityInfo(TempSecurityCodeBo bo) {
        int count = tempSecurityCodeMapper.querySecurityInfoCount(bo.getSecurityCode());
        if (count > 0) {
            tempSecurityCodeMapper.editSecurityInfo(bo);
        } else {
            bo.setSecuritySource("temp_converted");
            tempSecurityCodeMapper.addSecurityInfo(bo);
        }
    }

    /**
     * 同步新增临时代码证券主数据
     */
    private void addTempSecurityInfo(TempSecurityCodeBo sourceBo) {
        int count = tempSecurityCodeMapper.querySecurityInfoCount(sourceBo.getTempSecurityCode());
        if (count > 0) {
            throw new BizException("临时证券代码已存在于证券主数据，tempSecurityCode=" + sourceBo.getTempSecurityCode());
        }
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setSecurityName(sourceBo.getTempSecurityName());
        bo.setSecurityCode(sourceBo.getTempSecurityCode());
        bo.setSecurityMarket(sourceBo.getTempSecurityMarket());
        bo.setSecurityType(sourceBo.getTempSecurityType());
        bo.setTempCompanyNameSnapshot(sourceBo.getTempCompanyNameSnapshot());
        bo.setTempIssueDate(sourceBo.getTempIssueDate());
        bo.setTempMaturityDate(sourceBo.getTempMaturityDate());
        bo.setUpdateTime(sourceBo.getCrteTime());
        bo.setSecuritySource("temporary");
        tempSecurityCodeMapper.addSecurityInfo(bo);
    }

    /**
     * 禁用临时代码占位证券主数据
     */
    private void disableTempSecurityInfo(TempSecurityCodeBo oldBo, Date updateTime) {
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setTempSecurityCode(oldBo.getTempSecurityCode());
        bo.setUpdateTime(updateTime);
        tempSecurityCodeMapper.editTempSecurityInfoToDisabled(bo);
    }

    /**
     * 替换临时代码在核心业务表中的引用
     */
    private void replaceTempSecurityReferences(TempSecurityCodeBo oldBo, TempSecurityCodeBo newBo) {
        // 构建临时代码替换参数
        TempSecurityCodeBo replaceBo = buildReplaceBo(oldBo, newBo);

        List<Long> adjustLogSecurityIds = tempSecurityCodeMapper.queryAdjustLogSecurityReferenceIdList(replaceBo);
        if (!adjustLogSecurityIds.isEmpty()) {
            tempSecurityCodeMapper.editAdjustLogSecurityReference(replaceBo, adjustLogSecurityIds);
            // 记录调库日志证券引用替换结果
            addReplaceLogList(replaceBo, "ip_adjust_log", adjustLogSecurityIds);
        }

        List<Long> poolStatusSecurityIds = tempSecurityCodeMapper.queryPoolStatusSecurityReferenceIdList(replaceBo);
        if (!poolStatusSecurityIds.isEmpty()) {
            tempSecurityCodeMapper.editPoolStatusSecurityReference(replaceBo, poolStatusSecurityIds);
            // 记录当前池状态证券引用替换结果
            addReplaceLogList(replaceBo, "ip_pool_status", poolStatusSecurityIds);
        }

        List<Long> adjustLogCrmwIds = tempSecurityCodeMapper.queryAdjustLogCrmwReferenceIdList(replaceBo);
        if (!adjustLogCrmwIds.isEmpty()) {
            tempSecurityCodeMapper.editAdjustLogCrmwReference(replaceBo, adjustLogCrmwIds);
            // 记录调库日志 CRMW 引用替换结果
            addReplaceLogList(replaceBo, "ip_adjust_log.crmw", adjustLogCrmwIds);
        }

        List<Long> poolStatusCrmwIds = tempSecurityCodeMapper.queryPoolStatusCrmwReferenceIdList(replaceBo);
        if (!poolStatusCrmwIds.isEmpty()) {
            tempSecurityCodeMapper.editPoolStatusCrmwReference(replaceBo, poolStatusCrmwIds);
            // 记录当前池状态 CRMW 引用替换结果
            addReplaceLogList(replaceBo, "ip_pool_status.crmw", poolStatusCrmwIds);
        }

        List<Long> crmwPoolStatusSecurityIds = tempSecurityCodeMapper.queryCrmwPoolStatusSecurityReferenceIdList(replaceBo);
        if (!crmwPoolStatusSecurityIds.isEmpty()) {
            tempSecurityCodeMapper.editCrmwPoolStatusSecurityReference(replaceBo, crmwPoolStatusSecurityIds);
            // 记录 CRMW 池状态证券引用替换结果
            addReplaceLogList(replaceBo, "ip_pool_status_crmw", crmwPoolStatusSecurityIds);
        }

        List<Long> crmwPoolStatusCrmwIds = tempSecurityCodeMapper.queryCrmwPoolStatusCrmwReferenceIdList(replaceBo);
        if (!crmwPoolStatusCrmwIds.isEmpty()) {
            tempSecurityCodeMapper.editCrmwPoolStatusCrmwReference(replaceBo, crmwPoolStatusCrmwIds);
            // 记录 CRMW 池状态 CRMW 引用替换结果
            addReplaceLogList(replaceBo, "ip_pool_status_crmw.crmw", crmwPoolStatusCrmwIds);
        }
    }

    /**
     * 构建临时代码替换参数
     */
    private TempSecurityCodeBo buildReplaceBo(TempSecurityCodeBo oldBo, TempSecurityCodeBo newBo) {
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setTempSecurityName(oldBo.getTempSecurityName());
        bo.setTempSecurityCode(oldBo.getTempSecurityCode());
        bo.setTempSecurityMarket(oldBo.getTempSecurityMarket());
        bo.setTempSecurityType(oldBo.getTempSecurityType());
        bo.setSecurityName(newBo.getSecurityName());
        bo.setSecurityCode(newBo.getSecurityCode());
        bo.setSecurityMarket(newBo.getSecurityMarket());
        bo.setSecurityType(newBo.getSecurityType());
        bo.setUpdateTime(newBo.getUpdateTime());
        bo.setReplaceStatus("success");
        return bo;
    }

    /**
     * 批量写入替换日志
     */
    private void addReplaceLogList(TempSecurityCodeBo replaceBo, String tableName, List<Long> recordIds) {
        for (Long recordId : recordIds) {
            replaceBo.setReplaceTableName(tableName);
            replaceBo.setReplaceRecordId(recordId);
            tempSecurityCodeMapper.addTempSecurityCodeUpdateLog(replaceBo);
        }
    }

    /**
     * 校验临时代码未被核心调库业务引用
     */
    private void validateNoCoreReference(TempSecurityCodeBo bo) {
        int count = tempSecurityCodeMapper.queryCoreReferenceCount(bo);
        if (count > 0) {
            throw new BizException("该临时代码已被调库业务使用，无法删除");
        }
    }

    /**
     * 查询取消发行日期
     */
    private String queryCancelIssueDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        return new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());
    }

    /**
     * 查询临时代码详情
     */
    private TempSecurityCodeDto queryTempSecurityCodeDetail(Long id) {
        return tempSecurityCodeMapper.queryTempSecurityCodeDetail(id);
    }

    /**
     * 解析主体展示名称
     */
    private String resolveCompanyName(TempSecurityCodeDto.CompanyOption company) {
        if (company.getFullName() != null && company.getFullName().trim().length() > 0) {
            return company.getFullName();
        }
        if (company.getShortName() != null && company.getShortName().trim().length() > 0) {
            return company.getShortName();
        }
        throw new BizException("发行主体缺少名称，companyCode=" + company.getCompanyCode());
    }
}
