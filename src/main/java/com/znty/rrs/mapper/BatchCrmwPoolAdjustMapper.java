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

    /** 分页查询启用叶子 CRMW 投资池 */
    List<BatchCrmwPoolDto> queryPoolPage(BatchCrmwPoolAdjustReq req);

    /**
     * 查询指定 CRMW 池当前有效组合数量。
     * 统计 ip_pool_status_crmw 全部有效在池组合。
     */
    List<BatchCrmwPoolTypeCountDto> queryPoolCurrentCountByTypeList(@Param("poolIds") List<Long> poolIds);

    /** 分页查询目标池可调入候选组合 */
    List<BatchCrmwCandidateDto> queryInboundCandidatePage(BatchCrmwPoolAdjustReq req);

    /** 分页查询目标池可调出候选组合 */
    List<BatchCrmwCandidateDto> queryOutboundCandidatePage(BatchCrmwPoolAdjustReq req);

    /** 查询指定投资池是否为启用叶子 CRMW 池 */
    int queryEnabledLeafCrmwPoolCount(@Param("poolId") Long poolId);

    /** 查询操作人最近一次 CRMW 批量提交的全部手工调库记录 */
    List<IpAdjustLogBo> queryRecentBatchManualAdjustLogList(@Param("adjusterId") String adjusterId,
                                                            @Param("seconds") int seconds);
}
