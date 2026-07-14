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
 * 投资池开放日维护服务，提供开放日区间的分页查询与增删改
 */
@Service
public class PoolOpenDayService {

    /** 投资池开放日数据访问组件 */
    @Resource
    private PoolOpenDayMapper poolOpenDayMapper;

    /**
     * 分页查询开放日配置
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
     */
    @Transactional(rollbackFor = Exception.class)
    public PoolOpenDayDto addPoolOpenDay(PoolOpenDayReq req) {
        // 校验新增/修改公共参数
        validateSaveReq(req, false);
        // 校验投资池存在
        validatePoolExists(req.getPoolId());
        Date now = new Date();
        IpPoolOpenDayBo bo = new IpPoolOpenDayBo();
        bo.setPoolId(req.getPoolId());
        bo.setBeginDate(req.getBeginDate());
        bo.setEndDate(req.getEndDate());
        bo.setDescription(trimToNull(req.getDescription()));
        bo.setIsDeleted(0);
        bo.setCrteTime(now);
        bo.setUpdtTime(now);
        poolOpenDayMapper.addPoolOpenDay(bo);
        // 回查新增后的详情
        return queryPoolOpenDayDetail(bo.getId());
    }

    /**
     * 修改开放日配置
     */
    @Transactional(rollbackFor = Exception.class)
    public PoolOpenDayDto editPoolOpenDay(PoolOpenDayReq req) {
        // 校验新增/修改公共参数
        validateSaveReq(req, true);
        // 查询并校验记录存在
        IpPoolOpenDayBo oldBo = queryRequiredOpenDay(req.getId());
        // 校验投资池存在
        validatePoolExists(req.getPoolId());
        IpPoolOpenDayBo bo = new IpPoolOpenDayBo();
        bo.setId(oldBo.getId());
        bo.setPoolId(req.getPoolId());
        bo.setBeginDate(req.getBeginDate());
        bo.setEndDate(req.getEndDate());
        bo.setDescription(trimToNull(req.getDescription()));
        bo.setUpdtTime(new Date());
        int rows = poolOpenDayMapper.editPoolOpenDay(bo);
        if (rows <= 0) {
            throw new BizException("开放日配置不存在或已删除，id=" + req.getId());
        }
        // 回查修改后的详情
        return queryPoolOpenDayDetail(req.getId());
    }

    /**
     * 逻辑删除开放日配置
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
     * @param requireId 修改时必须传 id
     */
    private void validateSaveReq(PoolOpenDayReq req, boolean requireId) {
        if (req == null) {
            throw new BizException("请求参数不能为空");
        }
        if (requireId && req.getId() == null) {
            throw new BizException("主键 ID 不能为空");
        }
        if (req.getPoolId() == null) {
            throw new BizException("投资池不能为空");
        }
        if (req.getBeginDate() == null) {
            throw new BizException("开放起始日不能为空");
        }
        if (req.getEndDate() == null) {
            throw new BizException("开放结束日不能为空");
        }
        if (req.getBeginDate().after(req.getEndDate())) {
            throw new BizException("开放起始日不能晚于结束日");
        }
        if (req.getDescription() != null && req.getDescription().length() > 200) {
            throw new BizException("描述长度不能超过 200 个字符");
        }
    }

    /**
     * 去空白；空串转 null
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
