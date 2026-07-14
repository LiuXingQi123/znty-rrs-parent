package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.IpPoolOpenDayBo;
import com.znty.rrs.entity.poolopenday.PoolOpenDayDto;
import com.znty.rrs.entity.poolopenday.PoolOpenDayReq;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 投资池开放日数据访问接口
 */
@Mapper
public interface PoolOpenDayMapper {

    /**
     * 分页查询开放日配置列表
     */
    List<PoolOpenDayDto> queryPoolOpenDayPage(PoolOpenDayReq req);

    /**
     * 按主键查询开放日配置
     */
    IpPoolOpenDayBo queryPoolOpenDayById(@Param("id") Long id);

    /**
     * 按主键查询开放日详情（含投资池名称）
     */
    PoolOpenDayDto queryPoolOpenDayDetail(@Param("id") Long id);

    /**
     * 校验投资池是否存在
     */
    InvestmentPoolBo queryPoolById(@Param("poolId") Long poolId);

    /**
     * 统计子池数量（有子节点的目录节点不可配置开放日）
     */
    int countChildPool(@Param("poolId") Long poolId);

    /**
     * 新增开放日配置
     */
    int addPoolOpenDay(IpPoolOpenDayBo bo);

    /**
     * 修改开放日配置
     */
    int editPoolOpenDay(IpPoolOpenDayBo bo);

    /**
     * 逻辑删除开放日配置
     */
    int deletePoolOpenDay(IpPoolOpenDayBo bo);
}
