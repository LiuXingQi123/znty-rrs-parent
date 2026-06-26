package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.entity.bo.TempSecurityCodeBo;
import com.znty.sirm.entity.tempsecuritycode.TempSecurityCodeDto;
import com.znty.sirm.entity.tempsecuritycode.TempSecurityCodeReq;
import com.znty.sirm.exception.BizException;
import com.znty.sirm.mapper.TempSecurityCodeMapper;
import java.util.Date;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 临时代码管理服务，处理临时代码新增、查询、更新正式证券、取消发行和删除
 */
@Service
public class TempSecurityCodeService {

    /** 临时状态 */
    private static final String STATUS_TEMPORARY = "temporary";

    /** 已更新状态 */
    private static final String STATUS_UPDATED = "updated";

    /** 已取消状态 */
    private static final String STATUS_CANCELLED = "cancelled";

    /** 已删除状态 */
    private static final String STATUS_DELETED = "deleted";

    /** 新增操作 */
    private static final String OPERATION_ADD = "add";

    /** 更新操作 */
    private static final String OPERATION_UPDATE = "update";

    /** 取消发行操作 */
    private static final String OPERATION_CANCEL_ISSUE = "cancel_issue";

    /** 删除操作 */
    private static final String OPERATION_DELETE = "delete";

    /** 上海证券交易所 */
    private static final String MARKET_SSE = "SSE";

    /** 深圳证券交易所 */
    private static final String MARKET_SZSE = "SZSE";

    /** 银行间市场 */
    private static final String MARKET_CIBM = "CIBM";

    /** 场外市场 */
    private static final String MARKET_OTC = "OTC";

    /** JWCW 市场 */
    private static final String MARKET_JWCW = "JWCW";

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
        validateAddReq(req);
        Date now = new Date();
        // 查询发行主体并生成快照
        TempSecurityCodeDto.CompanyOption company = tempSecurityCodeMapper.queryCompanyById(req.getTempCompanyId());
        if (company == null) {
            throw new BizException("发行主体不存在，tempCompanyId=" + req.getTempCompanyId());
        }
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setTempSecurityName(req.getTempSecurityName());
        bo.setTempSecurityCode(req.getTempSecurityCode());
        bo.setTempSecurityMarket(req.getTempSecurityMarket());
        bo.setTempSecurityType(req.getTempSecurityType());
        bo.setTempMitigationCode(req.getTempMitigationCode());
        bo.setTempCompanyId(req.getTempCompanyId());
        bo.setTempCompanyNameSnapshot(resolveCompanyName(company));
        bo.setTempIssueDate(req.getTempIssueDate());
        bo.setTempMaturityDate(req.getTempMaturityDate());
        bo.setStatus(STATUS_TEMPORARY);
        bo.setOperationType(OPERATION_ADD);
        bo.setIsDeleted(0);
        bo.setCrteTime(now);
        bo.setUpdtTime(now);
        tempSecurityCodeMapper.addTempSecurityCode(bo);
        // 查询新增后的临时代码详情
        return queryTempSecurityCodeDetail(bo.getId());
    }

    /**
     * 更新临时代码为正式证券
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto editTempSecurityCodeToUpdated(TempSecurityCodeReq req) {
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
        bo.setTempCompanyId(req.getTempCompanyId());
        // 主体变更时重新生成主体名称快照，未变更则保留原快照
        if (req.getTempCompanyId() != null && !req.getTempCompanyId().equals(oldBo.getTempCompanyId())) {
            TempSecurityCodeDto.CompanyOption company = tempSecurityCodeMapper.queryCompanyById(req.getTempCompanyId());
            if (company == null) {
                throw new BizException("发行主体不存在，tempCompanyId=" + req.getTempCompanyId());
            }
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
        bo.setStatus(STATUS_UPDATED);
        bo.setOperationType(OPERATION_UPDATE);
        bo.setUpdtTime(now);
        // 同步正式证券基础信息
        upsertSecurityInfo(bo);
        tempSecurityCodeMapper.editTempSecurityCodeToUpdated(bo);
        // 查询更新后的临时代码详情
        return queryTempSecurityCodeDetail(bo.getId());
    }

    /**
     * 取消发行临时代码
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto editTempSecurityCodeToCancelled(TempSecurityCodeReq req) {
        validateIdReq(req);
        // 查询并校验临时代码当前状态
        TempSecurityCodeBo oldBo = queryOperableTempSecurityCode(req.getId());
        Date now = new Date();
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setId(oldBo.getId());
        bo.setUpdateTime(now);
        bo.setStatus(STATUS_CANCELLED);
        bo.setOperationType(OPERATION_CANCEL_ISSUE);
        bo.setUpdtTime(now);
        tempSecurityCodeMapper.editTempSecurityCodeToCancelled(bo);
        // 查询取消后的临时代码详情
        return queryTempSecurityCodeDetail(bo.getId());
    }

    /**
     * 删除临时代码
     */
    @Transactional(rollbackFor = Exception.class)
    public TempSecurityCodeDto deleteTempSecurityCode(TempSecurityCodeReq req) {
        validateIdReq(req);
        // 查询并校验临时代码存在
        TempSecurityCodeBo oldBo = queryExistingTempSecurityCode(req.getId());
        Date now = new Date();
        TempSecurityCodeBo bo = new TempSecurityCodeBo();
        bo.setId(oldBo.getId());
        bo.setUpdateTime(now);
        bo.setStatus(STATUS_DELETED);
        bo.setOperationType(OPERATION_DELETE);
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
        validateRequired(req.getTempSecurityName(), "临时证券名称不能为空");
        validateRequired(req.getTempSecurityCode(), "临时证券代码不能为空");
        validateRequired(req.getTempSecurityMarket(), "临时证券市场不能为空");
        validateRequired(req.getTempSecurityType(), "临时证券类型不能为空");
        if (req.getTempCompanyId() == null) {
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
        validateMarket(req.getTempSecurityMarket(), "临时证券市场不合法");
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
        validateIdReq(req);
        // 临时证券字段校验（与新增一致，允许在更新时编辑临时证券信息）
        validateRequired(req.getTempSecurityName(), "临时证券名称不能为空");
        validateRequired(req.getTempSecurityCode(), "临时证券代码不能为空");
        validateRequired(req.getTempSecurityMarket(), "临时证券市场不能为空");
        validateRequired(req.getTempSecurityType(), "临时证券类型不能为空");
        if (req.getTempCompanyId() == null) {
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
        validateMarket(req.getTempSecurityMarket(), "临时证券市场不合法");
        validateSecurityType(req.getTempSecurityType());
        // 临时证券代码唯一性校验（排除自身）
        int count = tempSecurityCodeMapper.queryTempSecurityCodeCount(req.getTempSecurityCode(), req.getId());
        if (count > 0) {
            throw new BizException("临时证券代码已存在，tempSecurityCode=" + req.getTempSecurityCode());
        }
        // 正式证券字段校验
        validateRequired(req.getSecurityName(), "证券名称不能为空");
        validateRequired(req.getSecurityCode(), "证券代码不能为空");
        validateRequired(req.getSecurityMarket(), "证券市场不能为空");
        validateRequired(req.getSecurityType(), "证券类型不能为空");
        validateMarket(req.getSecurityMarket(), "证券市场不合法");
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
     * 校验证券市场
     */
    private void validateMarket(String market, String message) {
        if (!MARKET_SSE.equals(market)
                && !MARKET_SZSE.equals(market)
                && !MARKET_CIBM.equals(market)
                && !MARKET_OTC.equals(market)
                && !"UNKNOWN".equals(market)
                && !MARKET_JWCW.equals(market)) {
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
        if (!STATUS_TEMPORARY.equals(bo.getStatus())) {
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
            tempSecurityCodeMapper.addSecurityInfo(bo);
        }
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
        throw new BizException("发行主体缺少名称，companyId=" + company.getCompanyId());
    }
}
