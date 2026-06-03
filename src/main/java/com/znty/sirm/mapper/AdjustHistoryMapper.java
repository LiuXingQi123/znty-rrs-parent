package com.znty.sirm.mapper;

import com.znty.sirm.model.AdjustHistoryDto;
import com.znty.sirm.model.AdjustHistoryReq;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 调整历史数据访问接口
 */
@Mapper
public interface AdjustHistoryMapper {

    /** 分页查询调整历史列表 */
    List<AdjustHistoryDto> queryAdjustHistoryPage(AdjustHistoryReq req);
}
