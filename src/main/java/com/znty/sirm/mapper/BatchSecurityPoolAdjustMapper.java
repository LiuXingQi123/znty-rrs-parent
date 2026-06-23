package com.znty.sirm.mapper;

import com.znty.sirm.model.BatchSecurityCandidateDto;
import com.znty.sirm.model.BatchSecurityPoolAdjustReq;
import com.znty.sirm.model.BatchSecurityPoolDto;
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

    /** 查询指定投资池当前有效证券数量 */
    List<BatchSecurityPoolDto> queryPoolCurrentCountList(@Param("poolIds") List<Long> poolIds);

    /** 分页查询目标池批量调整候选证券 */
    List<BatchSecurityCandidateDto> querySecurityPage(BatchSecurityPoolAdjustReq req);

    /** 查询指定投资池是否为启用叶子池 */
    int queryEnabledLeafPoolCount(@Param("poolId") Long poolId);
}
