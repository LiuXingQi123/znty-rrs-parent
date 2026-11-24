package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.EdgeCondRuleBo;
import com.znty.rrs.entity.bo.EdgeCondRuleEvtBo;
import com.znty.rrs.entity.bo.FlowDefinitionBo;
import com.znty.rrs.entity.bo.FlowDefinitionEvtBo;
import com.znty.rrs.entity.bo.FlowEdgeBo;
import com.znty.rrs.entity.bo.FlowEdgeEvtBo;
import com.znty.rrs.entity.bo.FlowNodeBo;
import com.znty.rrs.entity.bo.FlowNodeEvtBo;
import com.znty.rrs.entity.bo.FlowVersionBo;
import com.znty.rrs.entity.bo.FlowVersionEvtBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigBo;
import com.znty.rrs.entity.bo.NodeApprovalConfigEvtBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerBo;
import com.znty.rrs.entity.bo.NodeApprovalHandlerEvtBo;
import com.znty.rrs.entity.bo.NodeAutoConfigBo;
import com.znty.rrs.entity.bo.NodeAutoConfigEvtBo;
import com.znty.rrs.entity.bo.NodeConditionConfigBo;
import com.znty.rrs.entity.bo.NodeConditionConfigEvtBo;
import com.znty.rrs.entity.bo.NodeNotifyConfigBo;
import com.znty.rrs.entity.bo.NodeNotifyConfigEvtBo;
import com.znty.rrs.entity.bo.RoleBo;
import com.znty.rrs.entity.bo.RoleDictBo;
import com.znty.rrs.entity.bo.RoleDictEvtBo;
import com.znty.rrs.entity.bo.UserBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 流程定义数据访问接口，负责流程定义、版本、节点、连线及配置数据的持久化操作
 */
@Mapper
public interface FlowMapper {

    // ==================== 流程定义 ====================

    /**
     * 分页查询流程定义
     *
     * @param keyword 流程名称或流程 Key 关键字，为空时不筛选
     * @param status 筛选的流程定义状态，为空时不筛选
     * @param category 流程业务分类代码，为空时不筛选
     * @return 当前页的流程定义，包含版本号和是否已发布的信息
     */
    List<FlowDefinitionBo> queryFlowPage(@Param("keyword") String keyword,
                                         @Param("status") String status,
                                         @Param("category") String category);

    /**
     * 列表查询流程定义（不分页）
     *
     * @param keyword 流程名称或流程 Key 关键字，为空时不筛选
     * @param status 筛选的流程定义状态，为空时不筛选
     * @param category 流程业务分类代码，为空时不筛选
     * @return 符合条件的流程定义，包含版本号和是否已发布的信息
     */
    List<FlowDefinitionBo> queryFlowList(@Param("keyword") String keyword,
                                         @Param("status") String status,
                                         @Param("category") String category);

    /**
     * 根据 ID 查询流程定义
     *
     * @param id 流程定义 ID
     * @return 流程定义，未找到时为 null
     */
    FlowDefinitionBo queryFlowById(@Param("id") Long id);

    /**
     * 根据流程 Key 查询启用流程定义
     *
     * @param flowKey 流程标识 Key
     * @return 流程定义，未找到时为 null
     */
    FlowDefinitionBo queryActiveFlowByKey(@Param("flowKey") String flowKey);

    /**
     * 统计指定流程 Key 下未删除的流程定义，用于校验 Key 是否重复
     *
     * @param flowKey 流程标识 Key
     * @param excludeId 编辑时排除的流程定义 ID，为 null 时不排除记录
     * @return 符合条件的流程定义数量
     */
    int countByFlowKey(@Param("flowKey") String flowKey, @Param("excludeId") Long excludeId);

    /**
     * 根据 ID 查询流程定义（加行锁，用于并发安全的版本号计算）
     *
     * @param id 需要加锁的流程定义 ID
     * @return 流程定义，未找到时为 null
     */
    FlowDefinitionBo queryFlowByIdForUpdate(@Param("id") Long id);

    /**
     * 新建流程定义
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param def 待写入的流程定义
     * @return 受影响的行数
     */
    int addFlowDefinition(FlowDefinitionBo def);

    /**
     * 更新流程定义
     *
     * @param def 包含 ID 及待修改字段的流程定义
     * @return 受影响的行数
     */
    int editFlowDefinition(FlowDefinitionBo def);

    /**
     * 逻辑删除流程定义
     *
     * @param id 流程定义 ID
     * @param updatedBy 修改人 ID
     * @param now 本次操作时间
     * @return 受影响的行数
     */
    int deleteFlowLogical(@Param("id") Long id, @Param("updatedBy") Long updatedBy,
                          @Param("updtTime") Date now);

    /**
     * 将指定流程定义设为停用，并记录修改人和修改时间
     *
     * @param id 流程定义 ID
     * @param updatedBy 修改人 ID
     * @param now 本次操作时间
     * @return 受影响的行数
     */
    int editFlowDefinitionStatus(@Param("id") Long id, @Param("updatedBy") Long updatedBy,
                                 @Param("updtTime") Date now);

    // ==================== 流程版本 ====================

    /**
     * 查询最新流程版本
     *
     * @param flowId 流程定义 ID
     * @return 流程版本，未找到时为 null
     */
    FlowVersionBo queryLatestFlowVersion(@Param("flowId") Long flowId);

    /**
     * 查询当前草稿版本
     *
     * @param flowId 流程定义 ID
     * @return 当前草稿版本，没有草稿时为 null
     */
    FlowVersionBo queryDraftFlowVersion(@Param("flowId") Long flowId);

    /**
     * 根据 ID 查询流程版本
     *
     * @param id 流程版本 ID
     * @return 流程版本，未找到时为 null
     */
    FlowVersionBo queryFlowVersionById(@Param("id") Long id);

    /**
     * 查询流程版本列表，可按版本号筛选
     *
     * @param flowId 流程定义 ID
     * @param verNum 流程版本号
     * @return 符合条件的流程版本列表
     */
    List<FlowVersionBo> queryFlowVersionByFlowIdList(@Param("flowId") Long flowId,
                                                      @Param("verNum") Integer verNum);

    /**
     * 新建流程版本
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param ver 待写入的流程版本
     * @return 受影响的行数
     */
    int addFlowVersion(FlowVersionBo ver);

    /**
     * 更新流程版本
     *
     * @param ver 包含 ID 及待修改字段的流程版本
     * @return 受影响的行数
     */
    int editFlowVersion(FlowVersionBo ver);

    /**
     * 更新流程版本状态
     *
     * @param id 流程版本 ID
     * @param status 要写入的流程版本状态
     * @param now 本次操作时间
     * @return 受影响的行数
     */
    int editFlowVersionStatus(@Param("id") Long id,
                                @Param("status") String status,
                                @Param("updtTime") Date now);

    // ==================== 流程节点 ====================

    /**
     * 删除指定版本的流程节点
     *
     * @param versionId 流程版本 ID
     * @return 受影响的行数
     */
    int deleteFlowNodeByVersionId(@Param("versionId") Long versionId);

    /**
     * 新建流程节点
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param node 待写入的流程节点
     * @return 受影响的行数
     */
    int addFlowNode(FlowNodeBo node);

    /**
     * 根据 ID 查询流程节点
     *
     * @param id 流程节点 ID
     * @return 流程节点，未找到时为 null
     */
    FlowNodeBo queryFlowNodeById(@Param("id") Long id);

    // ==================== 审批节点配置 ====================

    /**
     * 删除指定版本的审批配置
     *
     * @param versionId 流程版本 ID
     * @return 受影响的行数
     */
    int deleteApprovalConfigByVersionId(@Param("versionId") Long versionId);

    /**
     * 新建审批配置
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param cfg 待写入的审批节点配置
     * @return 受影响的行数
     */
    int addApprovalConfig(NodeApprovalConfigBo cfg);

    /**
     * 删除指定版本的审批处理人明细
     *
     * @param versionId 流程版本 ID
     * @return 受影响的行数
     */
    int deleteApprovalHandlerByVersionId(@Param("versionId") Long versionId);

    /**
     * 新增审批处理人明细
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param handler 待写入的审批节点处理人配置
     * @return 受影响的行数
     */
    int addApprovalHandler(NodeApprovalHandlerBo handler);

    // ==================== 自动执行节点配置 ====================

    /**
     * 删除指定版本的自动任务配置
     *
     * @param versionId 流程版本 ID
     * @return 受影响的行数
     */
    int deleteAutoConfigByVersionId(@Param("versionId") Long versionId);

    /**
     * 新建自动任务配置
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param cfg 待写入的自动审批节点配置
     * @return 受影响的行数
     */
    int addAutoConfig(NodeAutoConfigBo cfg);

    // ==================== 通知节点配置 ====================

    /**
     * 删除指定版本的通知配置
     *
     * @param versionId 流程版本 ID
     * @return 受影响的行数
     */
    int deleteNotifyConfigByVersionId(@Param("versionId") Long versionId);

    /**
     * 新建通知配置
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param cfg 待写入的通知节点配置
     * @return 受影响的行数
     */
    int addNotifyConfig(NodeNotifyConfigBo cfg);

    // ==================== 条件节点配置 ====================

    /**
     * 删除指定版本的条件配置
     *
     * @param versionId 流程版本 ID
     * @return 受影响的行数
     */
    int deleteConditionConfigByVersionId(@Param("versionId") Long versionId);

    /**
     * 新建条件配置
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param cfg 待写入的条件节点配置
     * @return 受影响的行数
     */
    int addConditionConfig(NodeConditionConfigBo cfg);

    // ==================== 流程连线 ====================

    /**
     * 删除指定版本的流程连线
     *
     * @param versionId 流程版本 ID
     * @return 受影响的行数
     */
    int deleteFlowEdgeByVersionId(@Param("versionId") Long versionId);

    /**
     * 新建流程连线
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param edge 待写入的流程连线
     * @return 受影响的行数
     */
    int addFlowEdge(FlowEdgeBo edge);

    // ==================== 连线条件规则 ====================

    /**
     * 删除指定版本的连线条件规则
     *
     * @param versionId 流程版本 ID
     * @return 受影响的行数
     */
    int deleteCondRuleByVersionId(@Param("versionId") Long versionId);

    /**
     * 新建连线条件规则
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param rule 待写入的连线条件规则
     * @return 受影响的行数
     */
    int addCondRule(EdgeCondRuleBo rule);

    // ==================== 角色字典 ====================

    /**
     * 查询角色字典
     *
     * @return 符合条件的角色字典项列表
     */
    List<RoleDictBo> queryRoleDictList();

    /**
     * 查询所有角色
     *
     * @return 符合条件的角色列表
     */
    List<RoleBo> queryRoleList();

    /**
     * 查询人员列表
     *
     * @param roleIds 角色 ID 列表，为空时不筛选
     * @param keyword 用户姓名或登录名关键字，为空时不筛选
     * @return 符合条件的用户列表
     */
    List<UserBo> queryUserList(@Param("roleIds") List<Long> roleIds, @Param("keyword") String keyword);

    // ==================== 事件表 ====================

    /**
     * 新建流程定义事件
     *
     * 写入后将生成的主键回填到入参的 evtId 字段
     *
     * @param evt 流程定义操作审计记录
     * @return 受影响的行数
     */
    int addFlowDefinitionEvt(FlowDefinitionEvtBo evt);

    /**
     * 新建流程版本事件
     *
     * 写入后将生成的主键回填到入参的 evtId 字段
     *
     * @param evt 流程版本操作审计记录
     * @return 受影响的行数
     */
    int addFlowVersionEvt(FlowVersionEvtBo evt);

    /**
     * 新建流程节点事件
     *
     * 写入后将生成的主键回填到入参的 evtId 字段
     *
     * @param evt 流程节点操作审计记录
     * @return 受影响的行数
     */
    int addFlowNodeEvt(FlowNodeEvtBo evt);

    /**
     * 新建审批配置事件
     *
     * 写入后将生成的主键回填到入参的 evtId 字段
     *
     * @param evt 审批节点配置操作审计记录
     * @return 受影响的行数
     */
    int addApprovalConfigEvt(NodeApprovalConfigEvtBo evt);

    /**
     * 新建审批处理人明细事件
     *
     * 写入后将生成的主键回填到入参的 evtId 字段
     *
     * @param evt 审批处理人配置操作审计记录
     * @return 受影响的行数
     */
    int addApprovalHandlerEvt(NodeApprovalHandlerEvtBo evt);

    /**
     * 新建自动任务配置事件
     *
     * 写入后将生成的主键回填到入参的 evtId 字段
     *
     * @param evt 自动审批节点配置操作审计记录
     * @return 受影响的行数
     */
    int addAutoConfigEvt(NodeAutoConfigEvtBo evt);

    /**
     * 新建通知配置事件
     *
     * 写入后将生成的主键回填到入参的 evtId 字段
     *
     * @param evt 通知节点配置操作审计记录
     * @return 受影响的行数
     */
    int addNotifyConfigEvt(NodeNotifyConfigEvtBo evt);

    /**
     * 新建条件配置事件
     *
     * 写入后将生成的主键回填到入参的 evtId 字段
     *
     * @param evt 条件节点配置操作审计记录
     * @return 受影响的行数
     */
    int addConditionConfigEvt(NodeConditionConfigEvtBo evt);

    /**
     * 新建流程连线事件
     *
     * 写入后将生成的主键回填到入参的 evtId 字段
     *
     * @param evt 流程连线操作审计记录
     * @return 受影响的行数
     */
    int addFlowEdgeEvt(FlowEdgeEvtBo evt);

    /**
     * 新建连线条件规则事件
     *
     * 写入后将生成的主键回填到入参的 evtId 字段
     *
     * @param evt 连线条件规则操作审计记录
     * @return 受影响的行数
     */
    int addCondRuleEvt(EdgeCondRuleEvtBo evt);

    /**
     * 新建角色字典事件
     *
     * 写入后将生成的主键回填到入参的 evtId 字段
     *
     * @param evt 角色字典操作审计记录
     * @return 受影响的行数
     */
    int addRoleDictEvt(RoleDictEvtBo evt);

    // ==================== 归一化表 SELECT（DELETE 前捕获旧数据用于事件记录） ====================

    /**
     * 查询指定版本的流程节点列表
     *
     * @param versionId 流程版本 ID
     * @return 符合条件的流程节点列表
     */
    List<FlowNodeBo> queryFlowNodeListByVersionId(@Param("versionId") Long versionId);

    /**
     * 查询指定版本的审批配置列表
     *
     * @param versionId 流程版本 ID
     * @return 符合条件的审批节点配置列表
     */
    List<NodeApprovalConfigBo> queryApprovalConfigListByVersionId(@Param("versionId") Long versionId);

    /**
     * 查询指定版本的审批处理人明细列表
     *
     * @param versionId 流程版本 ID
     * @return 符合条件的审批节点处理人配置列表
     */
    List<NodeApprovalHandlerBo> queryApprovalHandlerListByVersionId(@Param("versionId") Long versionId);

    /**
     * 查询指定版本的自动任务配置列表
     *
     * @param versionId 流程版本 ID
     * @return 符合条件的自动审批节点配置列表
     */
    List<NodeAutoConfigBo> queryAutoConfigListByVersionId(@Param("versionId") Long versionId);

    /**
     * 查询指定版本的通知配置列表
     *
     * @param versionId 流程版本 ID
     * @return 符合条件的通知节点配置列表
     */
    List<NodeNotifyConfigBo> queryNotifyConfigListByVersionId(@Param("versionId") Long versionId);

    /**
     * 查询指定版本的条件配置列表
     *
     * @param versionId 流程版本 ID
     * @return 符合条件的条件节点配置列表
     */
    List<NodeConditionConfigBo> queryConditionConfigListByVersionId(@Param("versionId") Long versionId);

    /**
     * 查询指定版本的流程连线列表
     *
     * @param versionId 流程版本 ID
     * @return 符合条件的流程连线列表
     */
    List<FlowEdgeBo> queryFlowEdgeListByVersionId(@Param("versionId") Long versionId);

    /**
     * 查询指定版本的连线条件规则列表
     *
     * @param versionId 流程版本 ID
     * @return 符合条件的连线条件规则列表
     */
    List<EdgeCondRuleBo> queryCondRuleListByVersionId(@Param("versionId") Long versionId);
}
