package com.znty.sirm.mapper;

import com.znty.sirm.model.BondInfoBo;
import com.znty.sirm.model.IpAdjustLogBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 债券池调库数据访问接口
 */
@Mapper
public interface BondPoolAdjustMapper {

    /** 分页查询债券列表 */
    List<BondInfoBo> queryBondPage(@Param("bondCode") String bondCode,
                                   @Param("bondShortName") String bondShortName,
                                   @Param("issuer") String issuer);

    /** 根据 ID 查询债券详情 */
    BondInfoBo queryBondDetail(@Param("bondId") Long bondId);

    /** 新增调库记录 */
    int addAdjustLog(IpAdjustLogBo bo);
}
