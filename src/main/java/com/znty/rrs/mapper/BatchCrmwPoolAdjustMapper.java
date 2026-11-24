package com.znty.rrs.mapper;

import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwCandidateDto;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwPoolAdjustReq;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwPoolDto;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwPoolTypeCountDto;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * CRMW 池批量调整数据访问接口
 */
@Mapper
public interface BatchCrmwPoolAdjustMapper {

    /**
     * 分页查询已启用且无启用子池的 CRMW 池，可按池 ID 范围筛选
     *
     * @param req CRMW 批量调库目标池查询条件
     * @return 符合条件的CRMW 批量调库目标池列表
     */
    List<BatchCrmwPoolDto> queryPoolPage(BatchCrmwPoolAdjustReq req);

    /**
     * 查询指定 CRMW 池当前有效组合数量
     * 统计 ip_pool_status_crmw 全部有效在池组合
     *
     * @param poolIds 投资池 ID 列表
     * @return 各目标池当前有效的 CRMW 组合数量，未命中的池不返回
     */
    List<BatchCrmwPoolTypeCountDto> queryPoolCurrentCountByTypeList(@Param("poolIds") List<Long> poolIds);

    /**
     * 分页查询目标池可调入候选组合
     *
     * @param req 目标池及 CRMW 组合候选的筛选条件
     * @return 符合条件的CRMW 凭证与标的证券组合候选列表
     */
    List<BatchCrmwCandidateDto> queryInboundCandidatePage(BatchCrmwPoolAdjustReq req);

    /**
     * 分页查询目标池可调出候选组合
     *
     * @param req 目标池及 CRMW 组合候选的筛选条件
     * @return 符合条件的CRMW 凭证与标的证券组合候选列表
     */
    List<BatchCrmwCandidateDto> queryOutboundCandidatePage(BatchCrmwPoolAdjustReq req);

    /**
     * 查询指定投资池是否为启用叶子 CRMW 池
     *
     * @param poolId 投资池 ID
     * @return 符合条件的 CRMW 池数量，未命中时为 0
     */
    int queryEnabledLeafCrmwPoolCount(@Param("poolId") Long poolId);

    /**
     * 查询操作人最近一次 CRMW 批量提交的全部手工调库记录
     *
     * @param adjusterId 发起调库的用户 ID
     * @param seconds 查找近期重复提交的秒数
     * @return 符合条件的调库日志列表
     */
    List<IpAdjustLogBo> queryRecentBatchManualAdjustLogList(@Param("adjusterId") String adjusterId,
                                                            @Param("seconds") int seconds);
}
