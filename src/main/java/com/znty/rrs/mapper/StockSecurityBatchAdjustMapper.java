package com.znty.rrs.mapper;

import com.znty.rrs.entity.stocksecuritybatchadjust.StockPoolTypeCountDto;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchCandidateDto;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchAdjustReq;
import com.znty.rrs.entity.stocksecuritybatchadjust.StockSecurityBatchPoolDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 存量证券批量调整数据访问接口
 */
@Mapper
public interface StockSecurityBatchAdjustMapper {

    /** 分页查询启用叶子投资池 */
    List<StockSecurityBatchPoolDto> queryPoolPage(StockSecurityBatchAdjustReq req);

    /**
     * 查询指定投资池当前有效在池数量（按证券类型分项）。
     * 分类取 ip_pool_status.security_type（不经 rrs_securityinfo）；
     * typeCode：crmw / company / category_type（bond 等）/ unknown
     */
    List<StockPoolTypeCountDto> queryPoolCurrentCountByTypeList(@Param("poolIds") List<Long> poolIds);

    /** 分页查询目标池批量调整候选证券 */
    List<StockSecurityBatchCandidateDto> querySecurityPage(StockSecurityBatchAdjustReq req);

    /** 查询指定投资池是否为启用叶子池 */
    int queryEnabledLeafPoolCount(@Param("poolId") Long poolId);
}
