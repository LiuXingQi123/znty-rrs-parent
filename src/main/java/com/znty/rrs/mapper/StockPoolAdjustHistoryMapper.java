package com.znty.rrs.mapper;

import com.znty.rrs.entity.stockpooladjusthistory.StockIndustryOptionDto;
import com.znty.rrs.entity.stockpooladjusthistory.StockPoolAdjustHistoryDto;
import com.znty.rrs.entity.stockpooladjusthistory.StockPoolAdjustHistoryReq;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 股票池调整历史数据访问接口。
 */
@Mapper
public interface StockPoolAdjustHistoryMapper {

    /**
     * 分页查询股票池调整历史列表。
     *
     * @param req 分页参数和股票池调整历史筛选条件
     * @return 股票池调整历史记录列表
     */
    List<StockPoolAdjustHistoryDto> queryStockPoolAdjustHistoryPage(StockPoolAdjustHistoryReq req);

    /**
     * 查询调整历史中出现的股票行业选项。
     *
     * @return 股票行业选项列表
     */
    List<StockIndustryOptionDto> queryIndustryList();
}
