package com.znty.rrs.mapper;

import com.znty.rrs.entity.forbiddenpoolhistory.ForbiddenPoolHistoryDto;
import com.znty.rrs.entity.forbiddenpoolhistory.ForbiddenPoolHistoryReq;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 禁投池历史查询数据访问层
 */
@Mapper
public interface ForbiddenPoolHistoryMapper {

    /**
     * 分页查询禁投池调整历史
     *
     * @param req 查询条件
     */
    List<ForbiddenPoolHistoryDto> queryForbiddenPoolHistoryPage(ForbiddenPoolHistoryReq req);
}
