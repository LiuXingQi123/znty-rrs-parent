# 36 基金池调库审核与详情

## 功能范围

基金池审核和详情页面沿用证券池调库审核、详情的交互结构，基金数据始终使用基金专属运行表。审核页支持读取基金基础信息、当前基金池状态、批次调库记录和流程步骤，并提交当前待办的通过或驳回意见；详情页只读展示上述业务上下文。

## 页面

- `fund_pool_adjust_approve.html`：基金池调库审核。
- `fund_pool_adjust_detail.html`：基金池调库详情。
- 两页使用独立 CSS，沿用工作台详情页签返回方式和 `pool-adjust-workflow-page` 底部布局约定。
- URL 参数：`fundCode` 与 `adjustLogId` 或 `adjustBatchNo`。
- 详情包括基金基础信息、当前所在基金池、批次调库记录、原因建议和流程步骤。
- 审核页和详情页的调库记录字段顺序统一为：基金代码、投资池名称、调整类型、调整方向、调整原因、基金报告、其他材料、提交时间、审批流程、审核状态。
- 审核页和详情页的当前流程状态表格沿用证券池调库详情的字段顺序：步骤名称、步骤结果、开始时间、处理人、处理结果、处理时间、处理意见；包含节点合并、当前处理人强调和行状态样式。
- 「审核审批」标题右侧同行显示「当前步骤：步骤名称」，复用「流程名称」的 `el-tag size="mini" type="info"` 标签样式（仅存在 `currentPendingStep` 时）；内容区全宽展示处理意见，去掉处理结果/审核结果单选项。固定底栏正向主按钮调用 `submitAudit('approve')`，负向危险描边按钮调用 `submitAudit('reject')`：修改阶段显示「提交/终止流程」，普通审核阶段显示「通过/驳回」。
- `isModifyAuditStage` 根据当前申请 `auditStatus='11'` 或当前待办名称含「修改」识别修改阶段，与证券池审核页一致。驳回/终止流程须填写处理意见且二次确认，取消确认不提交；处理期间两个操作按钮均禁用，仅本次点击按钮显示 loading。

## 接口

统一使用 POST，接口前缀 `/api/v1/fundPoolAdjust`：

| 接口 | 用途 |
|---|---|
| `queryFundDetail` | 查询基金基础信息 |
| `queryFundPoolStatus` | 查询审批通过的基金池状态 |
| `queryAdjustLogList` | 查询指定基金的批次调库记录；未指定批次时仅查进行中记录 |
| `queryAdjustStepList` | 查询批次流程步骤 |
| `submitAdjustAudit` | 提交审批动作并推进流程 |

审核请求字段：`adjustLogId`、`adjustBatchNo`、`stepId`、`processAction`（`approve` / `reject`）、`processComment`、`handlerId`、`handlerName`。

审核请求还支持可选的 `adjustReason` / `adjustAdvice`：仅驳回待修改（`11`）且当前用户有 `pending` 的 `initiator` 待办时可编辑，重新提交 `approve` 时发送，终止流程不发送。后端根据流程的 `resubmit` 路由确认修改节点，校验同批全部日志为 `11`、当前用户为原发起人或管理员，仅更新当前待办所属批次的 `ip_adjust_log_fund`。未传字段保持原值，空字符串允许清空，每项最多 1000 字，核对条件更新数量；文本保存与步骤流转处于同一事务，失败整体回滚。

## 状态与落库

- 审核步骤写入 `ip_adjust_step_fund`，日志状态写入 `ip_adjust_log_fund`。
- 通过流程最终节点时将批次日志更新为 `20`，调入写入 `ip_pool_status_fund`，调出逻辑删除目标池有效状态。
- 驳回待修改、审批驳回和撤回分别使用 `11`、`21`、`99`；非 `20` 状态不落池。
- 基金池数据与证券池、CRMW 状态表隔离。
