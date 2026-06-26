package com.znty.sirm.mapper;

import com.znty.sirm.entity.adjusthistory.AdjustHistoryDto;
import com.znty.sirm.entity.adjusthistory.AdjustHistoryReq;
import com.znty.sirm.entity.common.SecurityTypeOptionDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 调整历史数据访问接口
 */
@Mapper
public interface AdjustHistoryMapper {

    /** 分页查询调整历史列表 */
    List<AdjustHistoryDto> queryAdjustHistoryPage(AdjustHistoryReq req);

    /** 查询调整历史中出现的证券类型选项 */
    List<SecurityTypeOptionDto> querySecurityTypeList();
}
