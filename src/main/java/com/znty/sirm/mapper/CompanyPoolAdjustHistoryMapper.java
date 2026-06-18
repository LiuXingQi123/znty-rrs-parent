package com.znty.sirm.mapper;

import com.znty.sirm.model.CompanyPoolAdjustHistoryDto;
import com.znty.sirm.model.CompanyPoolAdjustHistoryReq;

import java.util.List;

/**
 * 主体池调整历史数据访问层
 */
public interface CompanyPoolAdjustHistoryMapper {

    /**
     * 分页查询主体池调整历史
     *
     * @param req 查询条件
     */
    List<CompanyPoolAdjustHistoryDto> queryCompanyPoolAdjustHistoryPage(CompanyPoolAdjustHistoryReq req);
}
