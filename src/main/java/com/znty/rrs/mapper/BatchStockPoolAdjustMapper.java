package com.znty.rrs.mapper;

import com.znty.rrs.entity.batchstockpooladjust.BatchStockPoolAdjustReq;
import com.znty.rrs.entity.batchstockpooladjust.BatchStockPoolDto;
import com.znty.rrs.entity.stockpooladjust.StockInfoDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 股票池批量调整只读数据访问组件，写入复用股票单笔服务 */
public interface BatchStockPoolAdjustMapper {
    /** 分页查询启用、支持股票的叶子投资池 */
    List<BatchStockPoolDto> queryPoolPage(BatchStockPoolAdjustReq req);

    /** 分页查询目标池可调入或可调出的股票 */
    List<StockInfoDto> queryStockPage(BatchStockPoolAdjustReq req);

    /** 校验目标池为启用且支持股票的叶子池 */
    int queryEnabledStockLeafPoolCount(@Param("poolId") Long poolId);
}
