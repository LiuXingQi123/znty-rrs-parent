package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.FundInvestmentType;
import com.znty.rrs.common.enums.MarketCode;
import com.znty.rrs.common.enums.TempOprtSource;
import com.znty.rrs.common.enums.TempStatus;
import com.znty.rrs.entity.bo.FundAdjustLogBo;
import com.znty.rrs.entity.bo.FundInfoBo;
import com.znty.rrs.entity.bo.TempFundCodeBo;
import com.znty.rrs.entity.bo.TempFundCodeUpdateLogBo;
import com.znty.rrs.entity.tempfundcode.TempFundCodeDto;
import com.znty.rrs.entity.tempfundcode.TempFundCodeReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FundPoolAdjustMapper;
import com.znty.rrs.mapper.TempFundCodeMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理基金临时代码的查询、新增、转正、取消发行和删除
 * 转正时替换在途申请引用，并处理原临时基金的在池记录
 */
@Service
public class TempFundCodeService {
    /** 基金临时代码及替换明细访问组件 */
    @Resource
    private TempFundCodeMapper tempFundCodeMapper;
    /** 基金主档行锁及基金日志、池状态写入组件 */
    @Resource
    private FundPoolAdjustMapper fundPoolAdjustMapper;
    /** 转正后的基金附件关联继承组件 */
    @Resource
    private SysAttachmentService sysAttachmentService;
    /** 系统调出操作人 ID */
    private static final String SYSTEM_USER_ID = "0";
    /** 系统调出操作人名称 */
    private static final String SYSTEM_USER_NAME = "系统";
    /** 与债券临时代码调出一致的业务类型 */
    private static final String TEMP_OUT_TYPE = "临时代码调出";

    /**
     * 分页查询登记，包含软删除记录
     *
     * 执行顺序：
     * 1. 补齐分页请求并开启分页，紧接着查询登记
     * 2. 读取总条数并组装分页结果
     *
     * @param req 筛选及分页条件
     * @return 登记分页
     */
    public PageResult<TempFundCodeDto> queryTempFundCodePage(TempFundCodeReq req) {
        TempFundCodeReq query = req == null ? new TempFundCodeReq() : req;
        // 1. 开启分页后立即查询登记，保留状态和来源筛选
        PageHelper.startPage(query.getPageIndex(), query.getPageSize());
        List<TempFundCodeDto> rows = tempFundCodeMapper.queryTempFundCodePage(query);
        // 2. 读取总条数并返回分页结果
        PageInfo<TempFundCodeDto> page = new PageInfo<>(rows);
        return new PageResult<>(rows, page.getTotal(), query.getPageIndex(), query.getPageSize());
    }

    /**
     * 查询新增所用的基金产品类型
     *
     * 执行顺序：
     * 1. 查询有效的基金产品类型并组装选项
     *
     * @param req 查询参数，当前无需额外条件
     * @return 基金产品类型集合
     */
    public TempFundCodeDto.OptionBundle queryTempFundCodeOptions(TempFundCodeReq req) {
        TempFundCodeDto.OptionBundle options = new TempFundCodeDto.OptionBundle();
        options.setSecurityTypes(tempFundCodeMapper.queryFundTypeList());
        return options;
    }

    /**
     * 按代码、全称或简称搜索已有正式基金
     *
     * 执行顺序：
     * 1. 缺少请求时使用空查询条件，再查询符合条件的正式基金
     *
     * @param req 搜索关键字
     * @return 最多五十条正式基金选项
     */
    public List<TempFundCodeDto.FormalFundOption> queryFormalFundOptionList(TempFundCodeReq req) {
        return tempFundCodeMapper.queryFormalFundOptionList(req == null ? new TempFundCodeReq() : req);
    }

    /**
     * 在同一事务内新增登记、占位主档和审计快照
     *
     * 基金字典行锁用于让新增登记依次检查代码是否已占用
     *
     * 执行顺序：
     * 1. 校验录入信息并锁定基金字典行
     * 2. 检查基金主档和登记是否已占用代码
     * 3. 写入临时登记并确认已生成主键
     * 4. 写入可用于调库的占位主档
     * 5. 保存新增审计快照并返回登记详情
     *
     * @param req 四项录入信息及当前操作人
     * @return 新增登记
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TempFundCodeDto addTempFundCode(TempFundCodeReq req) {
        // 1. 验证四项录入信息、产品类型和操作人
        validateAddReq(req);
        // 2. 基金字典行锁持有期间复核代码占用，已删除主档仍占用原代码
        if (!tempFundCodeMapper.queryFundCodeReferenceIdList(req.getTempFundCode()).isEmpty()) {
            throw new BizException("临时基金代码已存在于基金主档，fundCode=" + req.getTempFundCode());
        }
        if (tempFundCodeMapper.queryTempFundCodeCount(req.getTempFundCode()) > 0) {
            throw new BizException("临时基金代码已存在于登记，fundCode=" + req.getTempFundCode());
        }
        Date now = new Date();
        TempFundCodeBo bo = new TempFundCodeBo();
        bo.setTempFundCode(req.getTempFundCode());
        bo.setTempFundShortName(req.getTempFundShortName());
        bo.setTempMarketCode(req.getTempMarketCode());
        bo.setTempSecurityType(req.getTempSecurityType());
        bo.setStatus(TempStatus.TEMPORARY.getCode());
        bo.setOprtSource(TempOprtSource.MANUAL.getCode());
        bo.setIsDeleted(0);
        bo.setCrteTime(now);
        bo.setUpdtTime(now);
        // 3. 同一事务写入人工登记，并确认生成登记主键
        if (tempFundCodeMapper.addTempFundCode(bo) != 1 || bo.getId() == null) {
            throw new BizException("基金临时代码登记写入失败，fundCode=" + bo.getTempFundCode());
        }
        // 4. 创建可用于调库的占位主档，全称使用简称，来源为 temporary、状态为 L
        if (tempFundCodeMapper.addTempFundInfo(bo) != 1) {
            throw new BizException("临时基金占位主档写入失败，fundCode=" + bo.getTempFundCode());
        }
        // 5. 审计与登记同事务保存，任何失败回滚主档和登记
        saveEvent(bo, req.getOperatorId(), "INSERT", now);
        return tempFundCodeMapper.queryTempFundCodeDetail(bo.getId());
    }

    /**
     * 转正时替换在途日志，在池成员生成直通调出和调入日志
     *
     * 执行顺序：
     * 1. 校验登记 ID 和操作人
     * 2. 校验正式代码并读取原登记，确定待锁定的基金
     * 3. 按主档 ID 顺序锁定临时和正式基金
     * 4. 重新读取登记，确认仍可转正并查询有效正式基金
     * 5. 校验正式基金主档字段
     * 6. 按临时状态条件保存正式信息
     * 7. 替换在途引用并处理在池记录
     * 8. 停用原占位主档
     * 9. 写入转正审计快照并返回登记详情
     *
     * @param req 登记 ID、已有正式基金代码及当前操作人
     * @return 转正后的登记和正式信息快照
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TempFundCodeDto editTempFundCodeToUpdated(TempFundCodeReq req) {
        // 1. 校验登记 ID 和操作人
        validateIdReq(req);
        // 2. 规范正式基金代码，不接受客户端提供的正式信息
        req.setFundCode(requireText(req.getFundCode(), "正式基金代码", 100));
        TempFundCodeBo initial = tempFundCodeMapper.queryTempFundCodeById(req.getId());
        if (initial == null) {
            throw new BizException("基金临时代码不存在或已删除，id=" + req.getId());
        }
        // 3. 按主档 ID 顺序加锁，保持与提交和审批一致的顺序
        lockMasterRecords(Arrays.asList(initial.getTempFundCode(), req.getFundCode()));
        // 4. 主档锁后重新读取登记，阻断重复转正或并发取消
        TempFundCodeBo bo = requireOperableRecord(req.getId());
        if (!initial.getTempFundCode().equals(bo.getTempFundCode())) {
            throw new BizException("基金临时代码已变化，请刷新，id=" + req.getId());
        }
        if (req.getFundCode().equalsIgnoreCase(bo.getTempFundCode())) {
            throw new BizException("正式基金代码不能与临时代码相同");
        }
        TempFundCodeDto.FormalFundOption formal = tempFundCodeMapper.queryFormalFundByCode(req.getFundCode());
        if (formal == null) {
            throw new BizException("正式基金不存在、已终止或属于临时占位，fundCode=" + req.getFundCode());
        }
        if (formal.getFundCode().equalsIgnoreCase(bo.getTempFundCode())) {
            throw new BizException("正式基金代码不能与临时代码相同");
        }
        // 5. 校验正式主档字段，缺少必填信息时提示补齐
        validateFormalFund(formal);
        Date now = new Date();
        bo.setFundCode(formal.getFundCode());
        bo.setFundName(formal.getFundName());
        bo.setFundShortName(formal.getFundShortName());
        bo.setMarketCode(formal.getMarketCode());
        bo.setSecurityType(formal.getSecurityType());
        bo.setStatus(TempStatus.UPDATED.getCode());
        bo.setOprtSource(TempOprtSource.MANUAL.getCode());
        bo.setUpdateTime(now);
        bo.setUpdtTime(now);
        // 6. 按临时状态条件保存，防止同一登记重复执行
        saveState(bo);
        // 7. 替换在途基金信息并转换在池记录，正式基金的在途冲突交由后续审批校验
        convertBusinessReferences(bo, req.getOperatorId(), now);
        // 8. 禁用旧占位主档，正式主档保持已有信息
        disableTemporaryMaster(bo, now);
        // 9. 记录人工转正后的完整登记快照
        saveEvent(bo, req.getOperatorId(), "UPDATE", now);
        return tempFundCodeMapper.queryTempFundCodeDetail(bo.getId());
    }

    /**
     * 取消发行允许已有业务引用，停用主档并保留既有记录
     *
     * 执行顺序：
     * 1. 锁定临时基金主档并复核登记状态
     * 2. 按临时状态条件保存取消结果
     * 3. 停用占位主档
     * 4. 写入取消审计快照并返回登记详情
     *
     * @param req 登记 ID 和当前操作人
     * @return 已取消登记
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TempFundCodeDto editTempFundCodeToCancelled(TempFundCodeReq req) {
        // 1. 锁定对应主档后复核登记，确认仍处于临时状态
        TempFundCodeBo bo = lockOperableRecord(req);
        Date now = new Date();
        bo.setStatus(TempStatus.CANCELLED.getCode());
        bo.setOprtSource(TempOprtSource.MANUAL.getCode());
        bo.setUpdateTime(now);
        bo.setUpdtTime(now);
        // 2. 按临时状态条件将登记改为已取消，允许已有在途或在池引用
        saveState(bo);
        // 3. 取消仅将占位主档置为 D，原日志、步骤和在池记录保留
        disableTemporaryMaster(bo, now);
        // 4. 保存取消后的审计快照
        saveEvent(bo, req.getOperatorId(), "UPDATE", now);
        return tempFundCodeMapper.queryTempFundCodeDetail(bo.getId());
    }

    /**
     * 临时登记无任何未删除基金日志或池状态引用时软删除，保留占位主档当前状态
     *
     * 执行顺序：
     * 1. 锁定临时基金主档并复核登记状态
     * 2. 检查是否存在未删除的基金调库日志或在池记录
     * 3. 按临时状态条件软删除登记
     * 4. 写入删除审计快照
     *
     * @param req 登记 ID 和当前操作人
     * @return 删除成功后返回空数据
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TempFundCodeDto deleteTempFundCode(TempFundCodeReq req) {
        // 1. 主档锁阻止引用检查与基金申请交叉执行
        TempFundCodeBo bo = lockOperableRecord(req);
        // 2. 检查未删除的调库日志和在池记录，存在引用时拒绝删除
        if (tempFundCodeMapper.queryCoreReferenceCount(bo.getTempFundCode()) > 0) {
            throw new BizException("临时基金已被调库业务使用，无法删除，fundCode=" + bo.getTempFundCode());
        }
        Date now = new Date();
        bo.setStatus(TempStatus.DELETED.getCode());
        bo.setIsDeleted(1);
        bo.setOprtSource(TempOprtSource.MANUAL.getCode());
        bo.setUpdateTime(now);
        bo.setUpdtTime(now);
        // 3. 按临时状态条件软删除登记
        saveState(bo);
        // 4. 保存软删除后的登记快照供审计
        saveEvent(bo, req.getOperatorId(), "DELETE", now);
        return null;
    }

    /**
     * 校验新增登记的四项录入字段和操作人
     *
     * 执行顺序：
     * 1. 检查请求和操作人
     * 2. 校验四项录入字段的必填和长度，并去掉首尾空格
     * 3. 校验市场编码
     * 4. 锁定有效基金字典行并核对产品类型
     *
     * @param req 录入请求
     */
    private void validateAddReq(TempFundCodeReq req) {
        if (req == null) {
            throw new BizException("新增基金临时代码参数不能为空");
        }
        // 1. 校验用于登记审计的操作人字段
        validateOperator(req);
        // 2. 校验录入字段的必填和长度，并去掉首尾空格
        req.setTempFundCode(requireText(req.getTempFundCode(), "临时基金代码", 100));
        // 校验基金简称，新增占位主档时也用它作为全称
        req.setTempFundShortName(requireText(req.getTempFundShortName(), "基金简称", 100));
        // 校验市场编码的必填和长度
        req.setTempMarketCode(requireText(req.getTempMarketCode(), "市场", 32));
        // 校验基金产品类型的必填和长度
        req.setTempSecurityType(requireText(req.getTempSecurityType(), "产品类型", 64));
        // 3. 核对录入的市场编码是否有效
        if (!MarketCode.isValid(req.getTempMarketCode())) {
            throw new BizException("基金市场编码无效，marketCode=" + req.getTempMarketCode());
        }
        // 4. 锁定基金字典行并校验产品类型，让新增登记依次检查代码是否已占用
        boolean validType = tempFundCodeMapper.queryFundTypeListForUpdate().stream()
                .anyMatch(type -> req.getTempSecurityType().equals(type.getSecurityType()));
        if (!validType) {
            throw new BizException("产品类型不属于有效基金字典，securityType=" + req.getTempSecurityType());
        }
    }

    /**
     * 校验状态操作的登记定位与操作人
     *
     * 执行顺序：
     * 1. 检查请求中是否有有效的登记 ID
     * 2. 校验操作人以便记录审计
     *
     * @param req 状态操作请求
     */
    private void validateIdReq(TempFundCodeReq req) {
        // 1. 检查请求是否包含有效的登记 ID
        if (req == null || req.getId() == null || req.getId() <= 0) {
            throw new BizException("基金临时代码登记 ID 无效");
        }
        // 2. 校验状态变更审计所需的操作人
        validateOperator(req);
    }

    /**
     * 校验审计操作人字段
     *
     * 执行顺序：
     * 1. 检查操作人 ID 的必填和长度，并去掉首尾空格
     *
     * @param req 人工操作请求
     */
    private void validateOperator(TempFundCodeReq req) {
        // 检查操作人 ID 的必填和长度，保证可写入审计记录
        req.setOperatorId(requireText(req.getOperatorId(), "操作人 ID", 20));
    }

    /**
     * 校验必填文字并返回规范输入
     *
     * 执行顺序：
     * 1. 拒绝空值和只包含空白的输入
     * 2. 去掉首尾空格，校验长度后返回
     *
     * @param value 原始输入
     * @param field 展示用字段名
     * @param max 数据库字段最大长度
     * @return 去掉首尾空格的输入
     */
    private String requireText(String value, String field, int max) {
        if (value == null || value.trim().isEmpty()) {
            throw new BizException(field + "不能为空");
        }
        String text = value.trim();
        if (text.length() > max) {
            throw new BizException(field + "长度不能超过" + max);
        }
        return text;
    }

    /**
     * 校验正式基金主档的全称、简称和市场
     *
     * 正式基金查询已通过基金字典筛选产品类型
     *
     * 执行顺序：
     * 1. 检查正式基金全称的必填和长度
     * 2. 检查正式基金简称的必填和长度
     * 3. 检查正式基金的市场编码
     *
     * @param formal 已从主档读取的正式基金
     */
    private void validateFormalFund(TempFundCodeDto.FormalFundOption formal) {
        // 1. 校验正式主档全称，保证转正时能保存完整名称
        requireText(formal.getFundName(), "正式基金全称，fundCode=" + formal.getFundCode(), 300);
        // 2. 校验正式主档简称，保证转正时能保存简称
        requireText(formal.getFundShortName(), "正式基金简称，fundCode=" + formal.getFundCode(), 100);
        // 3. 校验正式主档的市场编码
        if (!MarketCode.isValid(formal.getMarketCode())) {
            throw new BizException("正式基金市场无效，fundCode=" + formal.getFundCode());
        }
    }

    /**
     * 按主档 ID 顺序加行锁，与基金提交、审批使用同一锁对象，并拒绝重复或缺失主档
     *
     * 执行顺序：
     * 1. 按主档 ID 顺序锁定指定基金的主档
     * 2. 确认每个基金代码恰好对应一条主档
     *
     * @param codes 本次需要锁定的临时和正式基金代码
     */
    private void lockMasterRecords(List<String> codes) {
        // 1. 锁定提交、审批和转正共用的基金主档
        List<FundInfoBo> funds = fundPoolAdjustMapper.queryFundListForUpdate(codes);
        // 2. 检查每个代码是否恰好对应一条主档
        for (String code : codes) {
            long count = funds.stream().filter(fund -> code.equalsIgnoreCase(fund.getFundCode())).count();
            if (count != 1) {
                throw new BizException("基金主档缺失或代码重复，fundCode=" + code);
            }
        }
    }

    /**
     * 为取消、删除锁定基金主档并复核登记状态
     *
     * 执行顺序：
     * 1. 校验登记 ID 和操作人
     * 2. 读取登记确定临时基金代码
     * 3. 锁定该基金主档
     * 4. 重新读取登记并确认仍为临时状态
     *
     * @param req 登记 ID 和操作人
     * @return 当前有效临时登记
     */
    private TempFundCodeBo lockOperableRecord(TempFundCodeReq req) {
        // 1. 校验状态操作参数
        validateIdReq(req);
        // 2. 读取登记以确定需要加锁的基金代码
        TempFundCodeBo initial = tempFundCodeMapper.queryTempFundCodeById(req.getId());
        if (initial == null) {
            throw new BizException("基金临时代码不存在或已删除，id=" + req.getId());
        }
        // 3. 锁定临时基金主档，避免状态变更与调库写入交叉执行
        lockMasterRecords(Collections.singletonList(initial.getTempFundCode()));
        // 4. 主档锁查询已清除会话缓存，重新读取登记以复核状态
        return requireOperableRecord(req.getId());
    }

    /**
     * 在主档锁之后读取临时登记，禁止重复状态操作
     *
     * 执行顺序：
     * 1. 按登记 ID 查询未删除记录
     * 2. 确认登记存在且仍为临时状态后返回
     *
     * @param id 登记主键
     * @return 尚未删除且仍为临时状态的登记
     */
    private TempFundCodeBo requireOperableRecord(Long id) {
        TempFundCodeBo bo = tempFundCodeMapper.queryTempFundCodeById(id);
        if (bo == null) {
            throw new BizException("基金临时代码不存在或已删除，id=" + id);
        }
        if (!TempStatus.TEMPORARY.getCode().equals(bo.getStatus())) {
            throw new BizException("只有临时状态允许该操作，id=" + id + "，status=" + bo.getStatus());
        }
        return bo;
    }

    /**
     * 条件更新登记并检查影响行数
     *
     * 执行顺序：
     * 1. 仅更新仍为临时状态的登记，确认恰好更新一条
     *
     * @param bo 目标状态及正式快照
     */
    private void saveState(TempFundCodeBo bo) {
        if (tempFundCodeMapper.editTempFundCodeState(bo) != 1) {
            throw new BizException("基金临时代码状态已变化，请刷新，id=" + bo.getId());
        }
    }

    /**
     * 禁用原占位主档，拒绝缺失或被外部来源覆盖的数据
     *
     * 执行顺序：
     * 1. 仅停用原临时占位主档，确认恰好更新一条
     *
     * @param bo 主档加锁后复核的临时代码登记
     * @param now 当前操作时间
     */
    private void disableTemporaryMaster(TempFundCodeBo bo, Date now) {
        if (tempFundCodeMapper.editTempFundInfoToDisabled(bo.getTempFundCode(), now) != 1) {
            throw new BizException("临时基金占位主档缺失或来源不一致，fundCode=" + bo.getTempFundCode());
        }
    }

    /**
     * 将状态变更后的全字段登记与操作人写入审计
     *
     * 执行顺序：
     * 1. 写入完整登记快照和操作人，确认恰好写入一条
     *
     * @param bo 当前登记
     * @param operatorId 人工操作人
     * @param oprtType 审计操作类型：INSERT=新增 / UPDATE=修改 / DELETE=删除
     * @param now 操作时间
     */
    private void saveEvent(TempFundCodeBo bo, String operatorId, String oprtType, Date now) {
        if (tempFundCodeMapper.addTempFundCodeEvent(bo.getId(), operatorId, oprtType, now) != 1) {
            throw new BizException("基金临时代码审计写入失败，id=" + bo.getId());
        }
    }

    /**
     * 转正时更新在途申请和有效在池记录
     *
     * 执行顺序：
     * 1. 读取在途日志，替换基金的四项主档信息并逐条记录替换明细
     * 2. 读取有效在池记录，逐条处理临时调出和必要的正式调入
     *
     * @param bo 原临时代码及权威正式快照
     * @param operatorId 人工转正操作人
     * @param now 转正时间
     */
    private void convertBusinessReferences(TempFundCodeBo bo, String operatorId, Date now) {
        // 1. 替换在途申请的基金信息并记录替换明细
        List<FundAdjustLogBo> pending = tempFundCodeMapper.queryPendingAdjustLogList(bo.getTempFundCode());
        if (!pending.isEmpty()) {
            List<Long> ids = pending.stream().map(FundAdjustLogBo::getId).collect(Collectors.toList());
            if (tempFundCodeMapper.editPendingFundReference(bo, ids) != ids.size()) {
                throw new BizException("基金在途记录已变化，fundCode=" + bo.getTempFundCode());
            }
            for (Long id : ids) {
                // 每条在途日志保留独立可追溯的替换明细
                saveReplacement(bo, "ip_adjust_log_fund", id, operatorId, now);
            }
        }
        // 2. 读取已生效的在池记录，按原入池信息逐条转换
        List<TempFundCodeDto.PoolStatusReference> pools =
                tempFundCodeMapper.queryActivePoolStatusList(bo.getTempFundCode());
        for (TempFundCodeDto.PoolStatusReference pool : pools) {
            // 处理原临时基金调出，并按同池情况决定是否调入正式基金
            convertPoolStatus(pool, bo, operatorId, now);
        }
    }

    /**
     * 对一条有效在池记录执行临时调出和必要的正式调入
     *
     * 执行顺序：
     * 1. 核对原在池记录是否有完整的入池日志引用和基金调库参数
     * 2. 写入临时基金调出日志
     * 3. 软删除原在池记录并记录替换明细
     * 4. 检查正式基金是否已在同一池中，已在池时结束
     * 5. 写入正式基金的调入日志和在池状态
     * 6. 把原入池日志的附件关联复制到新入池日志
     *
     * @param pool 原在池业务记录和原入池日志 ID
     * @param bo 转正前后基金信息
     * @param operatorId 转正操作人
     * @param now 转正时间
     */
    private void convertPoolStatus(TempFundCodeDto.PoolStatusReference pool, TempFundCodeBo bo,
                                   String operatorId, Date now) {
        // 1. 核对原记录是否具备生成出入池日志所需的信息
        if (pool.getPoolStatusId() == null || pool.getSourceAdjustLogId() == null
                || pool.getFundScore() == null || !FundInvestmentType.isValid(pool.getFundInvestmentType())
                || pool.getNeedRiskLeaderApproval() == null
                || (pool.getNeedRiskLeaderApproval() != 0 && pool.getNeedRiskLeaderApproval() != 1)) {
            throw new BizException("原基金在池业务数据不完整，poolStatusId=" + pool.getPoolStatusId());
        }
        // 2. 复制原池业务信息并生成新的独立调库批次
        FundAdjustLogBo out = buildDirectLog(pool, now);
        out.setAdjustType(TEMP_OUT_TYPE);
        out.setAdjustMode(AdjustMode.OUT.getCode());
        out.setAdjusterId(SYSTEM_USER_ID);
        out.setAdjusterName(SYSTEM_USER_NAME);
        out.setAdjustReason("基金临时代码调出（临时代码：" + bo.getTempFundCode()
                + "，正式代码：" + bo.getFundCode() + "）");
        if (fundPoolAdjustMapper.addAdjustLog(out) != 1 || out.getId() == null) {
            throw new BizException("基金临时代码调出日志写入失败，poolStatusId=" + pool.getPoolStatusId());
        }
        if (tempFundCodeMapper.deleteTempPoolStatusById(pool.getPoolStatusId(), now) != 1) {
            throw new BizException("原基金在池状态已变化，poolStatusId=" + pool.getPoolStatusId());
        }
        // 3. 记录已软删除的在池记录 ID，便于追溯本次替换
        saveReplacement(bo, "ip_pool_status_fund", pool.getPoolStatusId(), operatorId, now);
        // 4. 正式基金已在同一池时结束，避免重复入池
        if (tempFundCodeMapper.queryFormalPoolStatusCount(bo.getFundCode(), pool.getTargetPoolId()) > 0) {
            return;
        }
        // 5. 正式入池继承原参数、原因、意见和流程信息
        FundAdjustLogBo in = buildDirectLog(pool, now);
        in.setFundCode(bo.getFundCode());
        in.setFundName(bo.getFundName());
        in.setFundShortName(bo.getFundShortName());
        in.setSecurityType(bo.getSecurityType());
        in.setAdjustMode(AdjustMode.IN.getCode());
        if (fundPoolAdjustMapper.addAdjustLog(in) != 1 || in.getId() == null) {
            throw new BizException("正式基金调入日志写入失败，poolStatusId=" + pool.getPoolStatusId());
        }
        if (fundPoolAdjustMapper.addFundPoolStatus(in) != 1) {
            throw new BizException("正式基金入池状态写入失败，poolStatusId=" + pool.getPoolStatusId());
        }
        // 6. 继承原入池日志的附件关联和分类，复用原物理文件
        sysAttachmentService.copyFundAdjustAttachments(pool.getSourceAdjustLogId(), in.getId(), operatorId);
    }

    /**
     * 构造已通过日志，保留原业务参数，清空旧主键并生成独立批次
     *
     * 执行顺序：
     * 1. 复制原业务参数并清除原主键
     * 2. 生成独立批次，设置审批通过状态和提交时间后返回
     *
     * @param pool 原在池业务信息
     * @param now 转正时间
     * @return 尚未插入的新基金日志
     */
    private FundAdjustLogBo buildDirectLog(TempFundCodeDto.PoolStatusReference pool, Date now) {
        FundAdjustLogBo log = new FundAdjustLogBo();
        BeanUtils.copyProperties(pool, log);
        log.setId(null);
        log.setAdjustBatchNo("FUND" + UUID.randomUUID().toString().replace("-", ""));
        log.setAuditStatus(AuditStatus.APPROVED.getCode());
        log.setSubmitTime(now);
        return log;
    }

    /**
     * 追加一条成功替换的具体业务记录
     *
     * 执行顺序：
     * 1. 从登记复制替换前后信息，记录业务表、原记录、操作人和时间
     * 2. 写入成功替换明细并校验影响行数
     *
     * @param bo 登记和正式信息快照
     * @param table 被替换的基金业务表
     * @param recordId 原业务主键
     * @param operatorId 转正操作人
     * @param now 转正时间
     */
    private void saveReplacement(TempFundCodeBo bo, String table, Long recordId, String operatorId, Date now) {
        TempFundCodeUpdateLogBo log = new TempFundCodeUpdateLogBo();
        BeanUtils.copyProperties(bo, log);
        log.setId(null);
        log.setTempCodeId(bo.getId());
        log.setReplaceTableName(table);
        log.setReplaceRecordId(recordId);
        log.setReplaceStatus("success");
        log.setReplaceTime(now);
        log.setOperatorId(operatorId);
        log.setCrteTime(now);
        log.setUpdtTime(now);
        if (tempFundCodeMapper.addTempFundCodeUpdateLog(log) != 1) {
            throw new BizException("基金替换明细写入失败，table=" + table + "，recordId=" + recordId);
        }
    }
}
