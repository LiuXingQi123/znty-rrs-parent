package com.znty.rrs.mapper;

import com.znty.rrs.entity.securitypooladjusthistory.SecurityPoolAdjustHistoryDto;
import com.znty.rrs.entity.securitypooladjusthistory.SecurityPoolAdjustHistoryReq;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 证券池调整历史数据访问接口
 */
@Mapper
public interface SecurityPoolAdjustHistoryMapper {

    /** 分页查询证券池调整历史列表 */
    List<SecurityPoolAdjustHistoryDto> querySecurityPoolAdjustHistoryPage(SecurityPoolAdjustHistoryReq req);

    /** 查询调整历史中出现的证券类型选项 */
    List<SecurityTypeOptionDto> querySecurityTypeList();
}
