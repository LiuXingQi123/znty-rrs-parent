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
     */
    int queryPoolTotalCount();

    /**
     * 查询投资池列表
     */
    List<InvestmentPoolBo> queryPoolList();

    /**
     * 查询投资池全路径名称列表
     */
    List<InvestmentPoolDto> queryPoolFullNameList();

    /**
     * 根据 ID 查询投资池
     */
    InvestmentPoolBo queryPoolById(@Param("id") Long id);

    /**
     * 查询指定 ID 的投资池列表
     */
    List<InvestmentPoolBo> queryPoolByIdsList(@Param("ids") List<Long> ids);

    /** 按 ID 升序锁定投资池记录，用于最终审批容量复核 */
    List<Long> lockPoolByIdsList(@Param("ids") List<Long> ids);

    /**
     * 新增投资池
     */
    int addPool(InvestmentPoolBo pool);

    /**
     * 查询同父级最大内部排序
     */
    Integer queryMaxInnerSortValue(@Param("parentId") Long parentId);

    /**
     * 查询顶级投资池最大外部排序
     */
    Integer queryMaxOuterSortValue();

    /**
     * 查询子投资池列表
     */
    List<InvestmentPoolBo> queryChildPoolList(@Param("parentId") Long parentId);

    /**
     * 修改投资池配置
     */
    int editPoolConfig(InvestmentPoolBo pool);

    /**
     * 写入投资池事件
     */
    int addPoolEvent(@Param("id") Long id, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 批量写入投资池事件
     */
    int addPoolEventByIds(@Param("ids") List<Long> ids, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 批量逻辑删除投资池
     */
    int deletePoolByIds(@Param("ids") List<Long> ids);

    /**
     * 查询投资池关系列表
     */
    List<PoolRelationBo> queryRelationList(@Param("poolId") Long poolId);

    /**
     * 新增投资池关系
     */
    int addRelation(PoolRelationBo relation);

    /**
     * 写入投资池关系事件
     */
    int addRelationEvent(@Param("id") Long id, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 按投资池写入关系事件
     */
    int addRelationEventByPoolId(@Param("poolId") Long poolId, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 按投资池列表写入关系事件
     */
    int addRelationEventByPoolIds(@Param("poolIds") List<Long> poolIds, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 按关联投资池列表写入关系事件
     */
    int addRelationEventByRelationPoolIds(@Param("relationPoolIds") List<Long> relationPoolIds, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 逻辑删除投资池关系
     */
    int deleteRelationByPoolId(@Param("poolId") Long poolId);

    /**
     * 按投资池列表逻辑删除关系
     */
    int deleteRelationByPoolIds(@Param("poolIds") List<Long> poolIds);

    /**
     * 按关联投资池列表逻辑删除关系
     */
    int deleteRelationByRelationPoolIds(@Param("relationPoolIds") List<Long> relationPoolIds);

    /**
     * 查询自动规则列表
     */
    List<PoolAutoRuleBo> queryAutoRuleList(@Param("poolId") Long poolId);

    /**
     * 新增自动规则
     */
    int addAutoRule(PoolAutoRuleBo rule);

    /**
     * 写入自动规则事件
     */
    int addAutoRuleEvent(@Param("id") Long id, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 按投资池写入自动规则事件
     */
    int addAutoRuleEventByPoolId(@Param("poolId") Long poolId, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 按投资池列表写入自动规则事件
     */
    int addAutoRuleEventByPoolIds(@Param("poolIds") List<Long> poolIds, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 逻辑删除自动规则
     */
    int deleteAutoRuleByPoolId(@Param("poolId") Long poolId);

    /**
     * 按投资池列表逻辑删除自动规则
     */
    int deleteAutoRuleByPoolIds(@Param("poolIds") List<Long> poolIds);

    /**
     * 查询所有互斥关系（调入互斥 + 调出互斥）
     */
    List<PoolRelationBo> queryMutexRelationList();

    /**
     * 查询流程下拉选项
     */
    List<FlowOptionDto> queryFlowOptionList();

    /**
     * 查询所有角色
     */
    List<RoleBo> queryRoleList();

    /**
     * 按角色ID列表和关键词查询人员列表
     */
    List<UserBo> queryUserList(@Param("roleIds") List<Long> roleIds, @Param("keyword") String keyword);

    /**
     * 查询投资池权限列表
     */
    List<PoolPermissionBo> queryPermissionList(@Param("poolId") Long poolId);

    /**
     * 按权限类型查询投资池权限列表
     */
    List<PoolPermissionBo> queryPermissionListByType(@Param("permissionType") String permissionType);

    /**
     * 查询用户所属启用角色 ID 列表
     */
    List<Long> queryUserRoleIdList(@Param("userId") Long userId);

    /**
     * 新增投资池权限
     */
    int addPermission(PoolPermissionBo permission);

    /**
     * 写入权限事件
     */
    int addPermissionEvent(@Param("id") Long id, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 按投资池写入权限事件
     */
    int addPermissionEventByPoolId(@Param("poolId") Long poolId, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 按投资池列表写入权限事件
     */
    int addPermissionEventByPoolIds(@Param("poolIds") List<Long> poolIds, @Param("operatorId") String operatorId, @Param("oprtType") String oprtType);

    /**
     * 逻辑删除投资池权限
     */
    int deletePermissionByPoolId(@Param("poolId") Long poolId);

    /**
     * 按投资池列表逻辑删除权限
     */
    int deletePermissionByPoolIds(@Param("poolIds") List<Long> poolIds);
}
