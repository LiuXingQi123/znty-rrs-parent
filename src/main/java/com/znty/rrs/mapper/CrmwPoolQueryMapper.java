package com.znty.rrs.mapper;

import com.znty.rrs.entity.crmwpoolquery.CrmwPoolQueryDto;
import com.znty.rrs.entity.crmwpoolquery.CrmwPoolQueryReq;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * CRMW池查询数据访问接口
 */
@Mapper
public interface CrmwPoolQueryMapper {

    /** 分页查询CRMW池列表 */
    List<CrmwPoolQueryDto> queryCrmwPoolPage(CrmwPoolQueryReq req);
}
