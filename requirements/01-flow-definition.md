# 流程定义需求说明

## 目标与角色

流程管理员维护审批流程的基础信息、画布节点、条件路由和版本。业务管理员可查看流程及历史版本，但只有具备维护权限的人员可编辑、发布、停用或删除。

## 核心业务线

1. 按名称、Key、状态和业务分类分页查询流程定义。
2. 新建流程后生成草稿，进入设计器配置开始、审批、条件、自动任务、通知和结束节点。
3. 编辑基础信息；已发布流程的 Key 不应随意变更。
4. 保存草稿不生成正式版本；发布时固化节点、连线及专属配置并递增版本号。
5. 已发布流程更新时创建可编辑草稿，运行中实例继续使用原版本。
6. 查看版本历史和指定版本画布；历史版本只读。
7. 停用流程后不可发起新业务；删除需遵循已关联业务限制。

## 接口清单

| 业务线 | 接口 |
|---|---|
| 列表与选项 | `/api/v1/flows/queryFlowPage`、`queryFlowList` |
| 新增与详情 | `addFlow`、`queryFlowDetail` |
| 基础信息与状态 | `editFlow`、`editFlowStatus`、`deleteFlow` |
| 设计与发布 | `editFlowDraft`、`editFlowToPublished` |
| 版本历史 | `queryFlowVersionList`、`queryFlowVersionDetail` |
| 设计器字典 | `dict/queryRoleList`、`dict/queryUserList`、`dict/queryAutoTaskList`、`dict/queryCondFieldList` |

## 关键校验

- 名称和 `flowKey` 必填，`flowKey` 在未删除定义中唯一。
- 每个可发布版本必须有且仅有一个开始节点、至少一个结束节点，不存在孤立节点或悬空连线。
- 审批节点处理人、自动任务、通知对象和条件规则必须配置完整。
- 发布版本快照与归一化节点、连线和专属配置保持一致。

## 验收标准

- 新建与编辑基础信息使用相同字段和视觉结构。
- 草稿、已发布、停用及“已发布后更新中”状态显示正确。
- 历史列表按版本倒序展示，指定版本可只读打开。
- `FlowDefinitionApiTest` 覆盖本页全部接口路由。
