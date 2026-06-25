package com.znty.sirm.mapper;

import com.znty.sirm.model.ForbiddenPoolHistoryDto;
import com.znty.sirm.model.ForbiddenPoolHistoryReq;
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
