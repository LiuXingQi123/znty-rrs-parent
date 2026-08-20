package com.znty.rrs.mapper;

import com.znty.rrs.entity.batchsecuritypooladjust.BatchPoolTypeCountDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityCandidateDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolAdjustReq;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 证券池批量调整数据访问接口
 */
@Mapper
public interface BatchSecurityPoolAdjustMapper {

    /** 分页查询启用叶子投资池 */
    List<BatchSecurityPoolDto> queryPoolPage(BatchSecurityPoolAdjustReq req);

    /**
     * 查询指定投资池当前有效在池数量（按证券类型分项）。
     * 分类取 ip_pool_status.security_type（不经 rrs_securityinfo）；
     * typeCode：crmw / company / category_type（bond 等）/ unknown
     */
    List<BatchPoolTypeCountDto> queryPoolCurrentCountByTypeList(@Param("poolIds") List<Long> poolIds);

    /** 分页查询目标池批量调整候选证券 */
    List<BatchSecurityCandidateDto> querySecurityPage(BatchSecurityPoolAdjustReq req);

    /** 查询指定投资池是否为启用叶子池 */
    int queryEnabledLeafPoolCount(@Param("poolId") Long poolId);
}
