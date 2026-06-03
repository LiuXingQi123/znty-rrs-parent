package com.znty.sirm.mapper;

import com.znty.sirm.model.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface FlowMapper {

    // ==================== 流程定义 ====================

    /** 分页查询流程定义。 */
    List<FlowDefinitionBo> queryFlowPage(@Param("keyword") String keyword,
                                         @Param("status") String status,
                                         @Param("category") String category);

    /** 列表查询流程定义（不分页）。 */
    List<FlowDefinitionBo> queryFlowList(@Param("keyword") String keyword,
                                         @Param("status") String status,
                                         @Param("category") String category);

    /** 根据 ID 查询流程定义。 */
    FlowDefinitionBo queryFlowById(@Param("id") Long id);

    /** 根据 ID 查询流程定义（加行锁，用于并发安全的版本号计算）。 */
    FlowDefinitionBo queryFlowByIdForUpdate(@Param("id") Long id);

    /** 新建流程定义。 */
    int addFlowDefinition(FlowDefinitionBo def);

    /** 更新流程定义。 */
    int editFlowDefinition(FlowDefinitionBo def);

    /** 逻辑删除流程定义。 */
    int deleteFlowLogical(@Param("id") Long id, @Param("updtTime") Date now);

    /** 停用流程定义。 */
    int editFlowDefinitionStatus(@Param("id") Long id, @Param("updtTime") Date now);

    // ==================== 流程版本 ====================

    /** 查询最新流程版本。 */
    FlowVersionBo queryLatestFlowVersion(@Param("flowId") Long flowId);

    /** 查询当前草稿版本。 */
    FlowVersionBo queryDraftFlowVersion(@Param("flowId") Long flowId);

    /** 根据 ID 查询流程版本。 */
    FlowVersionBo queryFlowVersionById(@Param("id") Long id);

    /** 查询流程版本列表，可按版本号筛选。 */
    List<FlowVersionBo> queryFlowVersionListByFlowId(@Param("flowId") Long flowId,
                                                      @Param("verNum") Integer verNum);

    /** 新建流程版本。 */
    int addFlowVersion(FlowVersionBo ver);

    /** 更新流程版本。 */
    int editFlowVersion(FlowVersionBo ver);

    /** 更新流程版本状态。 */
    int editFlowVersionStatus(@Param("id") Long id,
                                @Param("status") String status,
                                @Param("updtTime") Date now);

    // ==================== 流程节点 ====================

    /** 删除指定版本的流程节点。 */
    int deleteFlowNodeByVersionId(@Param("versionId") Long versionId);

    /** 新建流程节点。 */
    int addFlowNode(FlowNodeBo node);

    // ==================== 审批节点配置 ====================

    /** 删除指定版本的审批配置。 */
    int deleteApprovalConfigByVersionId(@Param("versionId") Long versionId);

    /** 新建审批配置。 */
    int addApprovalConfig(NodeApprovalConfigBo cfg);

    // ==================== 自动执行节点配置 ====================

    /** 删除指定版本的自动任务配置。 */
    int deleteAutoConfigByVersionId(@Param("versionId") Long versionId);

    /** 新建自动任务配置。 */
    int addAutoConfig(NodeAutoConfigBo cfg);

    // ==================== 通知节点配置 ====================

    /** 删除指定版本的通知配置。 */
    int deleteNotifyConfigByVersionId(@Param("versionId") Long versionId);

    /** 新建通知配置。 */
    int addNotifyConfig(NodeNotifyConfigBo cfg);

    // ==================== 条件节点配置 ====================

    /** 删除指定版本的条件配置。 */
    int deleteConditionConfigByVersionId(@Param("versionId") Long versionId);

    /** 新建条件配置。 */
    int addConditionConfig(NodeConditionConfigBo cfg);

    // ==================== 流程连线 ====================

    /** 删除指定版本的流程连线。 */
    int deleteFlowEdgeByVersionId(@Param("versionId") Long versionId);

    /** 新建流程连线。 */
    int addFlowEdge(FlowEdgeBo edge);

    // ==================== 连线条件规则 ====================

    /** 删除指定版本的连线条件规则。 */
    int deleteCondRuleByVersionId(@Param("versionId") Long versionId);

    /** 新建连线条件规则。 */
    int addCondRule(EdgeCondRuleBo rule);

    // ==================== 角色字典 ====================

    /** 查询角色字典。 */
    List<RoleDictBo> queryRoleDictList();

    // ==================== 事件表 ====================

    /** 新建流程定义事件。 */
    int addFlowDefinitionEvt(FlowDefinitionEvtBo evt);

    /** 新建流程版本事件。 */
    int addFlowVersionEvt(FlowVersionEvtBo evt);

    /** 新建流程节点事件。 */
    int addFlowNodeEvt(FlowNodeEvtBo evt);

    /** 新建审批配置事件。 */
    int addApprovalConfigEvt(NodeApprovalConfigEvtBo evt);

    /** 新建自动任务配置事件。 */
    int addAutoConfigEvt(NodeAutoConfigEvtBo evt);

    /** 新建通知配置事件。 */
    int addNotifyConfigEvt(NodeNotifyConfigEvtBo evt);

    /** 新建条件配置事件。 */
    int addConditionConfigEvt(NodeConditionConfigEvtBo evt);

    /** 新建流程连线事件。 */
    int addFlowEdgeEvt(FlowEdgeEvtBo evt);

    /** 新建连线条件规则事件。 */
    int addCondRuleEvt(EdgeCondRuleEvtBo evt);

    /** 新建角色字典事件。 */
    int addRoleDictEvt(RoleDictEvtBo evt);

    // ==================== 归一化表 SELECT（DELETE 前捕获旧数据用于事件记录） ====================

    /** 查询指定版本的流程节点列表。 */
    List<FlowNodeBo> queryFlowNodeListByVersionId(@Param("versionId") Long versionId);

    /** 查询指定版本的审批配置列表。 */
    List<NodeApprovalConfigBo> queryApprovalConfigListByVersionId(@Param("versionId") Long versionId);

    /** 查询指定版本的自动任务配置列表。 */
    List<NodeAutoConfigBo> queryAutoConfigListByVersionId(@Param("versionId") Long versionId);

    /** 查询指定版本的通知配置列表。 */
    List<NodeNotifyConfigBo> queryNotifyConfigListByVersionId(@Param("versionId") Long versionId);

    /** 查询指定版本的条件配置列表。 */
    List<NodeConditionConfigBo> queryConditionConfigListByVersionId(@Param("versionId") Long versionId);

    /** 查询指定版本的流程连线列表。 */
    List<FlowEdgeBo> queryFlowEdgeListByVersionId(@Param("versionId") Long versionId);

    /** 查询指定版本的连线条件规则列表。 */
    List<EdgeCondRuleBo> queryCondRuleListByVersionId(@Param("versionId") Long versionId);
}
