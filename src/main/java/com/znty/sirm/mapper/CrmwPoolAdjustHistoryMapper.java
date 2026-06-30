package com.znty.sirm.mapper;

import com.znty.sirm.entity.crmwpoolhistory.CrmwPoolAdjustHistoryDto;
import com.znty.sirm.entity.crmwpoolhistory.CrmwPoolAdjustHistoryReq;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * CRMW池调整历史数据访问接口
 */
@Mapper
public interface CrmwPoolAdjustHistoryMapper {

    /** 分页查询CRMW池调整历史列表 */
    List<CrmwPoolAdjustHistoryDto> queryCrmwPoolAdjustHistoryPage(CrmwPoolAdjustHistoryReq req);
}
