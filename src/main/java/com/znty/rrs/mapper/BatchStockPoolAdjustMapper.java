package com.znty.rrs.mapper;

import com.znty.rrs.entity.batchstockpooladjust.BatchStockPoolAdjustReq;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockPoolDto;
import com.znty.rrs.entity.stockpooladjust.StockInfoDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 股票池批量调整只读数据访问组件，写入复用股票单笔服务
 */
@Mapper
public interface BatchStockPoolAdjustMapper {
    /**
     * 分页查询已启用、支持股票且无启用子池的目标池，并统计各池当前股票数量
     *
     * @param req 股票批量调库目标池查询条件
     * @return 符合条件的股票批量调库目标池列表
     */
    List<BatchStockPoolDto> queryPoolPage(BatchStockPoolAdjustReq req);

    /**
     * 分页查询沪深港仍有效的上市股票，按调入或调出方向筛选不在池或已在池的候选
     *
     * @param req 股票信息查询条件
     * @return 符合条件的股票信息列表
     */
    List<StockInfoDto> queryStockPage(BatchStockPoolAdjustReq req);

    /**
     * 校验目标池为启用且支持股票的叶子池
     *
     * @param poolId 投资池 ID
     * @return 符合条件的股票池数量，未命中时为 0
     */
    int queryEnabledStockLeafPoolCount(@Param("poolId") Long poolId);
}
