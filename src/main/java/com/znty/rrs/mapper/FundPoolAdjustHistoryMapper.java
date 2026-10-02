package com.znty.rrs.mapper;

import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.entity.fundpooladjusthistory.FundPoolAdjustHistoryDto;
import com.znty.rrs.entity.fundpooladjusthistory.FundPoolAdjustHistoryReq;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 基金池调整历史数据访问接口。
 */
@Mapper
public interface FundPoolAdjustHistoryMapper {

    /**
     * 分页查询基金池调整历史列表。
     *
     * @param req 分页参数和基金池调整历史筛选条件
     * @return 基金池调整历史记录列表
     */
    List<FundPoolAdjustHistoryDto> queryFundPoolAdjustHistoryPage(FundPoolAdjustHistoryReq req);

    /**
     * 查询调整历史中出现的基金类型选项。
     *
     * @return 基金类型选项列表
     */
    List<SecurityTypeOptionDto> queryFundTypeList();
}
