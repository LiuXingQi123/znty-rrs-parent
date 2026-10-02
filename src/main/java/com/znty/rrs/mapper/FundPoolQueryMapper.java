package com.znty.rrs.mapper;

import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.entity.fundpoolquery.FundPoolQueryDto;
import com.znty.rrs.entity.fundpoolquery.FundPoolQueryReq;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 基金池查询数据访问层。
 */
@Mapper
public interface FundPoolQueryMapper {

    /**
     * 分页查询已生效基金池状态。
     *
     * @param req 基金池筛选条件
     * @return 基金池记录列表
     */
    List<FundPoolQueryDto> queryFundPoolPage(FundPoolQueryReq req);

    /**
     * 查询已生效基金池中出现的基金类型选项。
     *
     * @return 基金类型选项列表
     */
    List<SecurityTypeOptionDto> queryFundTypeList();
}
