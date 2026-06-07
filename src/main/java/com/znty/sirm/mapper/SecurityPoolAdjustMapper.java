package com.znty.sirm.mapper;

import com.znty.sirm.model.IpAdjustStepBo;
import com.znty.sirm.model.SecurityInfoBo;
import com.znty.sirm.model.IpAdjustLogBo;
import com.znty.sirm.model.PoolRelationBo;
import com.znty.sirm.model.PoolStatusDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 证券池调库数据访问接口
 */
@Mapper
public interface SecurityPoolAdjustMapper {

    /** 分页查询证券列表 */
    List<SecurityInfoBo> querySecurityPage(@Param("securityCode") String securityCode,
                                           @Param("securityShortName") String securityShortName,
                                           @Param("issuer") String issuer);

    /** 根据 ID 查询证券详情 */
    SecurityInfoBo querySecurityDetail(@Param("securityId") Long securityId);

    /** 新增调库记录（非直通流程，audit_status='00' 待审核） */
    int addAdjustLog(IpAdjustLogBo bo);

    /** 新增入池状态记录（直通流程，audit_status='20' 即时生效） */
    int addPoolStatus(IpAdjustLogBo bo);

    /** 软删除入池状态（直通调出流程，将目标池中有效记录标记为已删除） */
    int softDeletePoolStatus(@Param("securityCode") String securityCode,
                             @Param("targetPoolId") Long targetPoolId);

    /** 根据证券代码查询调库记录列表 */
    List<IpAdjustLogBo> queryAdjustLogList(@Param("securityCode") String securityCode);

    /** 查询当前证券所在池列表 */
    List<PoolStatusDto> querySecurityPoolStatus(@Param("securityCode") String securityCode);

    /** 查询当前证券主体（发行人）所在池列表 */
    List<PoolStatusDto> queryIssuerPoolStatus(@Param("securityCode") String securityCode);

    /** 查询证券当前有效所在池 ID 列表（audit_status=20） */
    List<Long> querySecurityCurrentPoolIds(@Param("securityCode") String securityCode);

    /** 查询目标投资池当前有效证券数量 */
    int queryPoolCurrentCount(@Param("poolId") Long poolId);

    /** 查询全量投资池关系配置（不限关系类型） */
    List<PoolRelationBo> queryAllPoolRelations();

    /** 查询证券是否存在进行中的调库流程（待审核或驳回待修改） */
    boolean querySecurityHasPendingProcess(@Param("securityCode") String securityCode);

    /** 查询当前证券是否在观察池（pool_type='observe'，audit_status='20'） */
    boolean querySecurityInObservePool(@Param("securityCode") String securityCode);

    /** 查询证券主体公司是否在观察池（同发行人的任意证券在观察池中） */
    boolean queryIssuerInObservePool(@Param("securityCode") String securityCode);

    /** 新增流程步骤记录 */
    int addAdjustStep(IpAdjustStepBo bo);

    /** 查询指定调库记录的流程步骤列表 */
    List<IpAdjustStepBo> queryAdjustStepList(@Param("adjustLogId") Long adjustLogId);
}
