package com.znty.rrs.mapper;

import com.znty.rrs.entity.companypool.CompanyPoolAdjustHistoryDto;
import com.znty.rrs.entity.companypool.CompanyPoolAdjustHistoryReq;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 主体池调整历史数据访问层
 */
@Mapper
public interface CompanyPoolAdjustHistoryMapper {

    /**
     * 分页查询主体池调整历史
     *
     * @param req 查询条件
     */
    List<CompanyPoolAdjustHistoryDto> queryCompanyPoolAdjustHistoryPage(CompanyPoolAdjustHistoryReq req);
}
