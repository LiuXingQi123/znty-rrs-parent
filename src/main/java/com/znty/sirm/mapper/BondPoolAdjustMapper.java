package com.znty.sirm.mapper;

import com.znty.sirm.model.BondInfoBo;
import com.znty.sirm.model.IpAdjustLogBo;
import com.znty.sirm.model.PoolRelationBo;
import com.znty.sirm.model.PoolStatusDto;
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

    /** 根据债券代码查询调库记录列表 */
    List<IpAdjustLogBo> queryAdjustLogList(@Param("bondCode") String bondCode);

    /** 查询当前债券所在池列表 */
    List<PoolStatusDto> queryBondPoolStatus(@Param("bondCode") String bondCode);

    /** 查询当前债券主体（发行人）所在池列表 */
    List<PoolStatusDto> queryIssuerPoolStatus(@Param("bondCode") String bondCode);

    /** 查询债券当前有效所在池 ID 列表（audit_status=20） */
    List<Long> queryBondCurrentPoolIds(@Param("bondCode") String bondCode);

    /** 查询目标投资池当前有效债券数量 */
    int queryPoolCurrentCount(@Param("poolId") Long poolId);

    /** 查询全量投资池关系配置（不限关系类型） */
    List<PoolRelationBo> queryAllPoolRelations();

    /** 查询债券是否存在进行中的调库流程（待审核或驳回待修改） */
    boolean queryBondHasPendingProcess(@Param("bondCode") String bondCode);
}
