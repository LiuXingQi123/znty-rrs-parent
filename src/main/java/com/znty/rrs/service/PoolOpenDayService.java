package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpPoolOpenDayBo;
import com.znty.rrs.entity.poolopenday.PoolOpenDayDto;
import com.znty.rrs.entity.poolopenday.PoolOpenDayReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.PoolOpenDayMapper;
import java.util.Date;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 查询和维护投资池的开放日区间
 *
 * 按投资池及日期筛选开放日记录，支持新增、编辑和删除
 * 保存时校验投资池是可配置的叶子池，并确认开放起始日不晚于结束日
 */
@Service
public class PoolOpenDayService {

    /** 投资池开放日数据访问组件 */
    @Resource
    private PoolOpenDayMapper poolOpenDayMapper;

    /**
     * 分页查询开放日配置
     *
     * 执行顺序：
     * 1. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 2. 将本页记录和查询总条数一起返回
     *
     * @param req 开放日配置的筛选条件和分页参数，允许缺省
     * @return 投资池开放日区间信息及分页信息
     */
    public PageResult<PoolOpenDayDto> queryPoolOpenDayPage(PoolOpenDayReq req) {
        if (req == null) {
            req = new PoolOpenDayReq();
        }
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        // 查询开放日分页列表
        List<PoolOpenDayDto> list = poolOpenDayMapper.queryPoolOpenDayPage(req);
        PageInfo<PoolOpenDayDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 新增开放日配置
     *
     * 执行顺序：
     * 1. 校验新增/修改公共参数
     * 2. 校验投资池存在
     * 3. 回查新增后的详情
     *
     * @param req 新增开放日的目标池、起止日期和描述
     * @return 投资池开放日区间信息
     */
    @Transactional(rollbackFor = Exception.class)
    public PoolOpenDayDto addPoolOpenDay(PoolOpenDayReq req) {
        // 1. 校验新增/修改公共参数
        validateSaveReq(req, false);
        // 2. 校验投资池存在
        validatePoolExists(req.getPoolId());
        Date now = new Date();
        IpPoolOpenDayBo bo = new IpPoolOpenDayBo();
        bo.setPoolId(req.getPoolId());
        bo.setBeginDate(req.getBeginDate());
        bo.setEndDate(req.getEndDate());
        // 去空白
        bo.setDescription(trimToNull(req.getDescription()));
        bo.setIsDeleted(0);
        bo.setCrteTime(now);
        bo.setUpdtTime(now);
        poolOpenDayMapper.addPoolOpenDay(bo);
        // 3. 回查新增后的详情
        return queryPoolOpenDayDetail(bo.getId());
    }

    /**
     * 修改开放日配置
     *
     * 执行顺序：
     * 1. 校验新增/修改公共参数
     * 2. 查询并校验记录存在
     * 3. 校验投资池存在
     * 4. 回查修改后的详情
     *
     * @param req 待修改的开放日记录 ID、投资池、起止日期和描述
     * @return 投资池开放日区间信息
     */
    @Transactional(rollbackFor = Exception.class)
    public PoolOpenDayDto editPoolOpenDay(PoolOpenDayReq req) {
        // 1. 校验新增/修改公共参数
        validateSaveReq(req, true);
        // 2. 查询并校验记录存在
        IpPoolOpenDayBo oldBo = queryRequiredOpenDay(req.getId());
        // 3. 校验投资池存在
        validatePoolExists(req.getPoolId());
        IpPoolOpenDayBo bo = new IpPoolOpenDayBo();
        bo.setId(oldBo.getId());
        bo.setPoolId(req.getPoolId());
        bo.setBeginDate(req.getBeginDate());
        bo.setEndDate(req.getEndDate());
        // 去空白
        bo.setDescription(trimToNull(req.getDescription()));
        bo.setUpdtTime(new Date());
        int rows = poolOpenDayMapper.editPoolOpenDay(bo);
        if (rows <= 0) {
            throw new BizException("开放日配置不存在或已删除，id=" + req.getId());
        }
        // 4. 回查修改后的详情
        return queryPoolOpenDayDetail(req.getId());
    }

    /**
     * 逻辑删除开放日配置
     *
     * 执行顺序：
     * 1. 检查主键 ID 不能为空
     * 2. 查询并校验记录存在
     *
     * @param req 待删除的开放日配置记录 ID
     * @return 投资池开放日区间信息
     */
    @Transactional(rollbackFor = Exception.class)
    public PoolOpenDayDto deletePoolOpenDay(PoolOpenDayReq req) {
        if (req == null || req.getId() == null) {
            throw new BizException("主键 ID 不能为空");
        }
        // 查询并校验记录存在
        IpPoolOpenDayBo oldBo = queryRequiredOpenDay(req.getId());
        IpPoolOpenDayBo bo = new IpPoolOpenDayBo();
        bo.setId(oldBo.getId());
        bo.setIsDeleted(1);
        bo.setUpdtTime(new Date());
        int rows = poolOpenDayMapper.deletePoolOpenDay(bo);
        if (rows <= 0) {
            throw new BizException("开放日配置不存在或已删除，id=" + req.getId());
        }
        PoolOpenDayDto dto = new PoolOpenDayDto();
        dto.setId(oldBo.getId());
        return dto;
    }

    /**
     * 按主键查询详情
     *
     * 执行顺序：
     * 1. 按 ID 读取开放日详情，查询不到有效记录时抛业务异常
     *
     * @param id 登记主键
     * @return 投资池开放日区间信息
     */
    private PoolOpenDayDto queryPoolOpenDayDetail(Long id) {
        PoolOpenDayDto dto = poolOpenDayMapper.queryPoolOpenDayDetail(id);
        if (dto == null) {
            throw new BizException("开放日配置不存在或已删除，id=" + id);
        }
        return dto;
    }

    /**
     * 查询并校验开放日记录存在
     *
     * 执行顺序：
     * 1. 按记录 ID 取开放日配置，缺失或已被删除时阻止继续操作
     *
     * @param id 登记主键
     * @return 要维护的开放日记录
     */
    private IpPoolOpenDayBo queryRequiredOpenDay(Long id) {
        IpPoolOpenDayBo bo = poolOpenDayMapper.queryPoolOpenDayById(id);
        if (bo == null) {
            throw new BizException("开放日配置不存在或已删除，id=" + id);
        }
        return bo;
    }

    /**
     * 校验投资池存在，且非目录节点（有子池的节点不可配置开放日）
     *
     * 执行顺序：
     * 1. 按 ID 查询投资池
     * 2. 有子池的目录/根节点不可选
     *
     * @param poolId 目标投资池 ID
     */
    private void validatePoolExists(Long poolId) {
        InvestmentPoolBo pool = poolOpenDayMapper.queryPoolById(poolId);
        if (pool == null) {
            throw new BizException("投资池不存在或已删除，poolId=" + poolId);
        }
        // 有子池的目录/根节点不可选
        int childCount = poolOpenDayMapper.countChildPool(poolId);
        if (childCount > 0) {
            throw new BizException("目录节点不可配置开放日，请选择子投资池，poolId=" + poolId);
        }
    }

    /**
     * 校验新增/修改公共参数
     *
     * 执行顺序：
     * 1. 检查请求和投资池 ID，修改时还要有记录 ID
     * 2. 检查开放起止日期均已填写，起始日晚于结束日时终止处理
     * 3. 描述超过 200 个字符时终止处理
     *
     * @param req 待保存的投资池、起止日期和描述，修改时还须包含记录 ID
     * @param requireId 修改时必须传 id
     */
    private void validateSaveReq(PoolOpenDayReq req, boolean requireId) {
        // 1. 检查请求、记录 ID 和投资池 ID
        if (req == null) {
            throw new BizException("请求参数不能为空");
        }
        if (requireId && req.getId() == null) {
            throw new BizException("主键 ID 不能为空");
        }
        if (req.getPoolId() == null) {
            throw new BizException("投资池不能为空");
        }
        // 2. 检查开放日期完整且顺序正确
        if (req.getBeginDate() == null) {
            throw new BizException("开放起始日不能为空");
        }
        if (req.getEndDate() == null) {
            throw new BizException("开放结束日不能为空");
        }
        if (req.getBeginDate().after(req.getEndDate())) {
            throw new BizException("开放起始日不能晚于结束日");
        }
        // 3. 检查描述长度
        if (req.getDescription() != null && req.getDescription().length() > 200) {
            throw new BizException("描述长度不能超过 200 个字符");
        }
    }

    /**
     * 去空白；空串转 null
     *
     * 执行顺序：
     * 1. 值为空时返回空值
     *
     * @param value 待统一空值的文本
     * @return 去掉首尾空白后的文本，输入为空或只有空白时返回 null
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
