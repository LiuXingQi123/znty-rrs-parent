package com.znty.rrs.mapper;

import com.znty.rrs.entity.batchfundpooladjust.BatchFundPoolAdjustReq;
import com.znty.rrs.entity.batchfundpooladjust.BatchFundPoolDto;
import com.znty.rrs.entity.fundpooladjust.FundInfoDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 基金池批量调整只读数据访问组件，业务写入委托基金单笔服务 */
public interface BatchFundPoolAdjustMapper {
    /** 分页查询启用、支持基金的叶子投资池 */
    List<BatchFundPoolDto> queryPoolPage(BatchFundPoolAdjustReq req);

    /** 分页查询目标池可调入或可调出的基金 */
    List<FundInfoDto> queryFundPage(BatchFundPoolAdjustReq req);

    /** 校验目标池为启用且支持基金的叶子池 */
    int queryEnabledFundLeafPoolCount(@Param("poolId") Long poolId);
}
