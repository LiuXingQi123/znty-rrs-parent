package com.znty.rrs.mapper;

import com.znty.rrs.entity.flow.FlowOptionDto;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.investmentpool.InvestmentPoolDto;
import com.znty.rrs.entity.bo.PoolAutoRuleBo;
import com.znty.rrs.entity.bo.PoolPermissionBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.RoleBo;
import com.znty.rrs.entity.bo.UserBo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 投资池维护数据访问接口
 */
@Mapper
public interface InvestmentPoolMapper {

    /**
     * 查询投资池数量
     *
     * @return 未删除的投资池数量
     */
    int queryPoolTotalCount();

    /**
     * 查询投资池列表
     *
     * @return 符合条件的投资池配置列表
     */
    List<InvestmentPoolBo> queryPoolList();

    /**
     * 查询投资池全路径名称列表
     *
     * @return 符合条件的投资池信息列表
     */
    List<InvestmentPoolDto> queryPoolFullNameList();

    /**
     * 根据 ID 查询投资池
     *
     * @param id 投资池 ID
     * @return 投资池配置，未找到时为 null
     */
    InvestmentPoolBo queryPoolById(@Param("id") Long id);

    /**
     * 按投资池编码查询未删除记录（用于编码唯一性校验）
     *
     * @param poolCode 投资池编码
     * @return 匹配的投资池，不存在时返回 null
     */
    InvestmentPoolBo queryPoolByCode(@Param("poolCode") String poolCode);

    /**
     * 查询指定 ID 的投资池列表
     *
     * @param ids 投资池 ID 列表
     * @return 符合条件的投资池配置列表
     */
    List<InvestmentPoolBo> queryPoolByIdsList(@Param("ids") List<Long> ids);

    /**
     * 查询指定投资池中仍存在的 ID，供调整时核对目标池
     *
     * @param ids 投资池 ID 列表
     * @return 按 ID 升序返回存在的投资池 ID
     */
    List<Long> queryExistingPoolIdList(@Param("ids") List<Long> ids);

    /**
     * 新增投资池
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param pool 待写入的投资池配置
     * @return 受影响的行数
     */
    int addPool(InvestmentPoolBo pool);

    /**
     * 查询同父级最大内部排序
     *
     * @param parentId 父投资池 ID
     * @return 同一父池下的最大内部排序值，无匹配记录时为 null
     */
    Integer queryMaxInnerSortValue(@Param("parentId") Long parentId);

    /**
     * 查询顶级投资池最大外部排序
     *
     * @return 顶级投资池的最大外部排序值，无匹配记录时为 null
     */
    Integer queryMaxOuterSortValue();

    /**
     * 查询子投资池列表
     *
     * @param parentId 父投资池 ID
     * @return 符合条件的投资池配置列表
     */
    List<InvestmentPoolBo> queryChildPoolList(@Param("parentId") Long parentId);

    /**
     * 修改投资池配置
     *
     * @param pool 包含 ID 及待修改字段的投资池配置
     * @return 受影响的行数
     */
    int editPoolConfig(InvestmentPoolBo pool);

    /**
     * 写入投资池事件
     *
     * @param id 被记录操作的投资池 ID
     * @param operatorId 操作人 ID
     * @param oprtType 操作类型
     * @return 受影响的行数
     */
    int addPoolEvent(@Param("id") Long id, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 批量写入投资池事件
     *
     * @param ids 投资池操作审计记录 ID 列表
     * @param operatorId 操作人 ID
     * @param oprtType 操作类型
     * @return 受影响的行数
     */
    int addPoolEventByIds(@Param("ids") List<Long> ids, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 批量逻辑删除投资池
     *
     * @param ids 投资池 ID 列表
     * @return 受影响的行数
     */
    int deletePoolByIds(@Param("ids") List<Long> ids);

    /**
     * 查询投资池关系列表
     *
     * @param poolId 投资池 ID
     * @return 符合条件的投资池关系配置列表
     */
    List<PoolRelationBo> queryRelationList(@Param("poolId") Long poolId);

    /**
     * 新增投资池关系
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param relation 待写入的投资池关系配置
     * @return 受影响的行数
     */
    int addRelation(PoolRelationBo relation);

    /**
     * 写入投资池关系事件
     *
     * @param id 被记录操作的池关系 ID
     * @param operatorId 操作人 ID
     * @param oprtType 操作类型
     * @return 受影响的行数
     */
    int addRelationEvent(@Param("id") Long id, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 按投资池写入关系事件
     *
     * @param poolId 投资池 ID
     * @param operatorId 操作人 ID
     * @param oprtType 操作类型
     * @return 受影响的行数
     */
    int addRelationEventByPoolId(@Param("poolId") Long poolId, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 按投资池列表写入关系事件
     *
     * @param poolIds 投资池 ID 列表
     * @param operatorId 操作人 ID
     * @param oprtType 操作类型
     * @return 受影响的行数
     */
    int addRelationEventByPoolIds(@Param("poolIds") List<Long> poolIds, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 按关联投资池列表写入关系事件
     *
     * @param relationPoolIds 关联投资池 ID 列表
     * @param operatorId 操作人 ID
     * @param oprtType 操作类型
     * @return 受影响的行数
     */
    int addRelationEventByRelationPoolIds(@Param("relationPoolIds") List<Long> relationPoolIds, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 逻辑删除投资池关系
     *
     * @param poolId 投资池 ID
     * @return 受影响的行数
     */
    int deleteRelationByPoolId(@Param("poolId") Long poolId);

    /**
     * 按投资池列表逻辑删除关系
     *
     * @param poolIds 投资池 ID 列表
     * @return 受影响的行数
     */
    int deleteRelationByPoolIds(@Param("poolIds") List<Long> poolIds);

    /**
     * 按关联投资池列表逻辑删除关系
     *
     * @param relationPoolIds 关联投资池 ID 列表
     * @return 受影响的行数
     */
    int deleteRelationByRelationPoolIds(@Param("relationPoolIds") List<Long> relationPoolIds);

    /**
     * 查询自动规则列表
     *
     * @param poolId 投资池 ID
     * @return 符合条件的投资池自动调整规则列表
     */
    List<PoolAutoRuleBo> queryAutoRuleList(@Param("poolId") Long poolId);

    /**
     * 新增自动规则
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param rule 待写入的投资池自动调整规则
     * @return 受影响的行数
     */
    int addAutoRule(PoolAutoRuleBo rule);

    /**
     * 写入自动规则事件
     *
     * @param id 被记录操作的池自动调整规则 ID
     * @param operatorId 操作人 ID
     * @param oprtType 操作类型
     * @return 受影响的行数
     */
    int addAutoRuleEvent(@Param("id") Long id, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 按投资池写入自动规则事件
     *
     * @param poolId 投资池 ID
     * @param operatorId 操作人 ID
     * @param oprtType 操作类型
     * @return 受影响的行数
     */
    int addAutoRuleEventByPoolId(@Param("poolId") Long poolId, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 按投资池列表写入自动规则事件
     *
     * @param poolIds 投资池 ID 列表
     * @param operatorId 操作人 ID
     * @param oprtType 操作类型
     * @return 受影响的行数
     */
    int addAutoRuleEventByPoolIds(@Param("poolIds") List<Long> poolIds, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 逻辑删除自动规则
     *
     * @param poolId 投资池 ID
     * @return 受影响的行数
     */
    int deleteAutoRuleByPoolId(@Param("poolId") Long poolId);

    /**
     * 按投资池列表逻辑删除自动规则
     *
     * @param poolIds 投资池 ID 列表
     * @return 受影响的行数
     */
    int deleteAutoRuleByPoolIds(@Param("poolIds") List<Long> poolIds);

    /**
     * 查询所有互斥关系（调入互斥 + 调出互斥）
     *
     * @return 符合条件的投资池关系配置列表
     */
    List<PoolRelationBo> queryMutexRelationList();

    /**
     * 查询流程下拉选项
     *
     * @return 符合条件的可筛选的流程选项列表
     */
    List<FlowOptionDto> queryFlowOptionList();

    /**
     * 查询所有角色
     *
     * @return 符合条件的角色列表
     */
    List<RoleBo> queryRoleList();

    /**
     * 按角色ID列表和关键词查询人员列表
     *
     * @param roleIds 角色 ID 列表，为空时不筛选
     * @param keyword 用户关键字，为空时不筛选
     * @return 符合条件的用户列表
     */
    List<UserBo> queryUserList(@Param("roleIds") List<Long> roleIds, @Param("keyword") String keyword);

    /**
     * 查询投资池权限列表
     *
     * @param poolId 投资池 ID
     * @return 符合条件的投资池权限配置列表
     */
    List<PoolPermissionBo> queryPermissionList(@Param("poolId") Long poolId);

    /**
     * 按权限类型查询投资池权限列表
     *
     * @param permissionType 权限类型代码
     * @return 符合条件的投资池权限配置列表
     */
    List<PoolPermissionBo> queryPermissionListByType(@Param("permissionType") String permissionType);

    /**
     * 查询用户所属启用角色 ID 列表
     *
     * @param userId 用户 ID
     * @return 用户所属的启用角色 ID 列表
     */
    List<Long> queryUserRoleIdList(@Param("userId") Long userId);

    /**
     * 新增投资池权限
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param permission 待写入的投资池权限配置
     * @return 受影响的行数
     */
    int addPermission(PoolPermissionBo permission);

    /**
     * 写入权限事件
     *
     * @param id 被记录操作的池权限配置 ID
     * @param operatorId 操作人 ID
     * @param oprtType 操作类型
     * @return 受影响的行数
     */
    int addPermissionEvent(@Param("id") Long id, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 按投资池写入权限事件
     *
     * @param poolId 投资池 ID
     * @param operatorId 操作人 ID
     * @param oprtType 操作类型
     * @return 受影响的行数
     */
    int addPermissionEventByPoolId(@Param("poolId") Long poolId, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 按投资池列表写入权限事件
     *
     * @param poolIds 投资池 ID 列表
     * @param operatorId 操作人 ID
     * @param oprtType 操作类型
     * @return 受影响的行数
     */
    int addPermissionEventByPoolIds(@Param("poolIds") List<Long> poolIds, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 逻辑删除投资池权限
     *
     * @param poolId 投资池 ID
     * @return 受影响的行数
     */
    int deletePermissionByPoolId(@Param("poolId") Long poolId);

    /**
     * 按投资池列表逻辑删除权限
     *
     * @param poolIds 投资池 ID 列表
     * @return 受影响的行数
     */
    int deletePermissionByPoolIds(@Param("poolIds") List<Long> poolIds);
}
