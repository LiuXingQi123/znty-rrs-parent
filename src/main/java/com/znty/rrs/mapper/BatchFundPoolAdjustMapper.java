package com.znty.rrs.mapper;

import com.znty.rrs.entity.batchfundpooladjust.BatchFundPoolAdjustReq;
import com.znty.rrs.entity.batchfundpooladjust.BatchFundPoolDto;
import com.znty.rrs.entity.fundpooladjust.FundInfoDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 基金池批量调整只读数据访问组件，业务写入委托基金单笔服务
 */
@Mapper
public interface BatchFundPoolAdjustMapper {
    /**
     * 分页查询已启用、支持基金且无启用子池的目标池，并统计各池当前基金数量
     *
     * @param req 基金批量调库目标池查询条件
     * @return 符合条件的基金批量调库目标池列表
     */
    List<BatchFundPoolDto> queryPoolPage(BatchFundPoolAdjustReq req);

    /**
     * 分页查询有效基金，按调入或调出方向筛选不在池或已在池的候选
     *
     * @param req 基金信息查询条件
     * @return 符合条件的基金信息列表
     */
    List<FundInfoDto> queryFundPage(BatchFundPoolAdjustReq req);

    /**
     * 校验目标池为启用且支持基金的叶子池
     *
     * @param poolId 投资池 ID
     * @return 符合条件的基金池数量，未命中时为 0
     */
    int queryEnabledFundLeafPoolCount(@Param("poolId") Long poolId);
}
