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

    /**
     * 分页查询债券产品库下已启用且无启用子池的调库目标池，可按池 ID 范围筛选
     *
     * @param req 可批量调整股票的目标池查询条件
     * @return 符合条件的可批量调整股票的目标池列表
     */
    List<StockSecurityBatchPoolDto> queryPoolPage(StockSecurityBatchAdjustReq req);

    /**
     * 查询指定投资池当前有效在池数量（按证券类型分项）
     * 分类取 ip_pool_status.security_type（不经 rrs_securityinfo）；
     * typeCode：crmw / company / category_type（bond 等）/ unknown
     *
     * @param poolIds 投资池 ID 列表
     * @return 符合条件的各池类型的当前股票在池数量列表
     */
    List<StockPoolTypeCountDto> queryPoolCurrentCountByTypeList(@Param("poolIds") List<Long> poolIds);

    /**
     * 分页查询目标池批量调整候选证券
     *
     * @param req 股票批量调库候选查询条件
     * @return 符合条件的股票批量调库候选列表
     */
    List<StockSecurityBatchCandidateDto> querySecurityPage(StockSecurityBatchAdjustReq req);

    /**
     * 统计指定池是否属于债券产品库且已启用、没有启用子池
     *
     * @param poolId 投资池 ID
     * @return 符合条件的目标池数量，未命中时为 0
     */
    int queryEnabledLeafPoolCount(@Param("poolId") Long poolId);
}
