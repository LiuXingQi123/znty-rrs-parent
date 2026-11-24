package com.znty.rrs.mapper;

import com.znty.rrs.entity.batchsecuritypooladjust.BatchPoolTypeCountDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityCandidateDto;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolAdjustReq;
import com.znty.rrs.entity.batchsecuritypooladjust.BatchSecurityPoolDto;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 证券池批量调整数据访问接口
 */
@Mapper
public interface BatchSecurityPoolAdjustMapper {

    /**
     * 分页查询已启用、支持债券且无启用子池的信用债池，可按池 ID 范围筛选
     *
     * @param req 批量调库目标池查询条件
     * @return 符合条件的批量调库目标池列表
     */
    List<BatchSecurityPoolDto> queryPoolPage(BatchSecurityPoolAdjustReq req);

    /**
     * 查询指定投资池当前有效在池数量（按证券类型分项）
     * 分类取 ip_pool_status.security_type（不经 rrs_securityinfo）；
     * typeCode：crmw / company / category_type（bond 等）/ unknown
     *
     * @param poolIds 投资池 ID 列表
     * @return 符合条件的各池类型的当前在池数量列表
     */
    List<BatchPoolTypeCountDto> queryPoolCurrentCountByTypeList(@Param("poolIds") List<Long> poolIds);

    /**
     * 分页查询批量调库候选券，按调入或调出方向筛选不在池或已在池的证券
     *
     * @param req 批量调库候选证券查询条件
     * @return 符合条件的批量调库候选证券列表
     */
    List<BatchSecurityCandidateDto> querySecurityPage(BatchSecurityPoolAdjustReq req);

    /**
     * 分页查询可作为 ABS 自选权益人的评级主体
     *
     * @param companyCode 主体代码前缀
     * @param companyName 主体名称关键字
     * @return 权益人主体列表
     */
    List<SelfSelectedRightsHolderDto> querySelfSelectedRightsHolderPage(@Param("companyCode") String companyCode,
                                                                       @Param("companyName") String companyName);

    /**
     * 查询指定投资池是否为启用叶子池
     *
     * @param poolId 投资池 ID
     * @return 符合条件的目标池数量，未命中时为 0
     */
    int queryEnabledLeafPoolCount(@Param("poolId") Long poolId);
}
