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
     *
     * @param req 投资池开放日配置查询条件
     * @return 符合条件的投资池开放日配置列表
     */
    List<PoolOpenDayDto> queryPoolOpenDayPage(PoolOpenDayReq req);

    /**
     * 按主键查询开放日配置
     *
     * @param id 投资池开放日配置 ID
     * @return 投资池开放日配置，未找到时为 null
     */
    IpPoolOpenDayBo queryPoolOpenDayById(@Param("id") Long id);

    /**
     * 按主键查询开放日详情（含投资池名称）
     *
     * @param id 开放日配置 ID
     * @return 投资池开放日配置，未找到时为 null
     */
    PoolOpenDayDto queryPoolOpenDayDetail(@Param("id") Long id);

    /**
     * 校验投资池是否存在
     *
     * @param poolId 投资池 ID
     * @return 投资池配置，未找到时为 null
     */
    InvestmentPoolBo queryPoolById(@Param("poolId") Long poolId);

    /**
     * 统计子池数量（有子节点的目录节点不可配置开放日）
     *
     * @param poolId 投资池 ID
     * @return 未删除的子池数量
     */
    int countChildPool(@Param("poolId") Long poolId);

    /**
     * 新增开放日配置
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param bo 待写入的投资池开放日配置
     * @return 受影响的行数
     */
    int addPoolOpenDay(IpPoolOpenDayBo bo);

    /**
     * 修改开放日配置
     *
     * @param bo 包含 ID 及待修改字段的投资池开放日配置
     * @return 受影响的行数
     */
    int editPoolOpenDay(IpPoolOpenDayBo bo);

    /**
     * 逻辑删除开放日配置
     *
     * @param bo 包含 ID 及待修改字段的投资池开放日配置
     * @return 受影响的行数
     */
    int deletePoolOpenDay(IpPoolOpenDayBo bo);
}
