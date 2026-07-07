# 证券池调库审核 / 审批流转需求说明

> 前端页面：`security_pool_adjust_approve.html`（审核页）、`security_pool_adjust_detail.html`（详情页，结构同审核页但 `entryMode='view'/'adjust'`，无审批操作）、`my_matters.html`（待办入口）
> 后端前缀：`/api/v1/securityPoolAdjustFlow`（审批）、`/api/v1/securityPoolAdjust`（上下文查询）、`/api/v1/myMatters`（待办）
> 角色定位：审批处理人查看调库业务上下文与流程步骤，对当前待办执行通过/驳回/撤回，系统按流程配置推进下一节点并更新最终状态。

---

## 1. 流程模型

### 1.1 流程定义数据结构

审批流程基于「流程定义 + 流程版本 + 节点 + 连线 + 节点专属配置 + 连线条件规则」六层归一化结构持久化，运行时用 `FlowSnapshot` 聚合一个版本下的全量数据用于流转决策。

| 表 | 实体 | 用途 |
|---|---|---|
| `wf_flow_definition` | `FlowDefinitionBo` | 流程主表（名称、flow_key、category、status） |
| `wf_flow_version` | `FlowVersionBo` | 版本快照（含 `canvas_nodes` / `canvas_edges` JSON 大字段） |
| `wf_flow_node` | `FlowNodeBo` | 节点归一化行（节点类型、坐标、形状、subLabel） |
| `wf_node_approval_config` | `NodeApprovalConfigBo` | approval 节点的审批策略 |
| `wf_node_approval_handler` | `NodeApprovalHandlerBo` | approval 节点的处理人明细（角色/人员混选） |
| `wf_node_auto_config` | `NodeAutoConfigBo` | auto 节点的自动任务序列 |
| `wf_node_notify_config` | `NodeNotifyConfigBo` | notify 节点的通知渠道/对象/模板 |
| `wf_node_condition_config` | `NodeConditionConfigBo` | condition 节点的备注（分支逻辑放在出线上） |
| `wf_flow_edge` | `FlowEdgeBo` | 有向边（from/to + label + routeAction + condLogic） |
| `wf_edge_cond_rule` | `EdgeCondRuleBo` | 边上的条件规则明细（field / op / val） |
| `wf_role_dict` | `RoleBo` | 业务角色字典（含 `parent_id` 子角色递归） |

每张业务表都配有同结构 `_evt` 事件表记录审计。

### 1.2 节点类型枚举（`wf_flow_node.node_type`）

| code | 含义 | 后端处理逻辑 |
|---|---|---|
| `start` | 开始节点 | 提交调库时插入 `auto_process` 步骤，沿出边推进 |
| `end` | 结束节点 | 流程到达后 `finishAdjustBatch` 落地池状态 |
| `approval` | 审批节点 | 创建 `pending` 待处理步骤（按处理人展开），等待人工处理 |
| `condition` | 条件判断节点 | 出线上配置条件规则，运行时按 `routeAction`/条件匹配（条件引擎当前预留） |
| `auto` | 自动执行节点 | 后端识别为 O32 自动审批时直接插入 `auto_process` 步骤并继续流转 |
| `notify` | 消息通知节点 | 当前与 auto 同等处理（自动完成记录后继续） |

**节点语义靠文本匹配**：后端拼接 `label + nodeId + nodeType` 后做 `contains` 匹配（`buildNodeText` + `isAutoApprovalNode`/`isInitiatorNode`/`isReviewNode`/`isModifyNode`/`isApproveNode`）：
- 自动审批节点：文本含「自动审批」或「o32」
- 发起节点：文本含「发起」
- 复核节点：文本含「复核」或「复合」
- 修改节点：文本含「修改」
- 审批节点：文本含「审批」且非自动审批

> ⚠️ 流程定义中的节点 label 命名必须包含约定关键字，否则会被识别为普通审批节点。

### 1.3 边与条件路由

`wf_flow_edge.route_action` 取值：

| route_action | 含义 | 触发条件 |
|---|---|---|
| `approve` | 通过 | 审批通过时按此边推进 |
| `reject` | 驳回 | 审批驳回时按此边推进 |
| `auto` | 自动 | 自动节点出边 |
| `submit` | 提交 | 发起/修改节点提交时按此边推进（边缺省时也按动作匹配） |

后端路由查找 `findNextNodeOnMainPath`：
1. 收集当前节点所有出边
2. 若有 `prevNode`（避免回退回路），过滤掉指向 prevNode 的边
3. 单条出边直接走
4. 多条出边先尝试 `matchesConditionRoute`（**条件引擎当前恒返回 false，预留**），再按 `routeAction` 与 `processAction` 字符串匹配
5. 找不到匹配边抛 BizException("流程配置异常")

`wf_edge_cond_rule` 已建表（`field_code/operator/field_val` 与 `cond_logic` AND/OR），但 `matchesConditionRoute` 仍是占位实现。

### 1.4 候选处理人配置

`wf_node_approval_handler.handler_type`：

| handler_type | 含义 | 展开 |
|---|---|---|
| `user` | 指定人员 | 直接取 `handler_id` 作为 handlerId |
| `role` | 指定角色 | 递归收集子角色 → 查询所有关联用户去重展开 |

展开逻辑见 `resolveApprovalHandlers`，使用 `collectDescendantRoleIds` 递归收集角色树。

### 1.5 审批策略枚举（`wf_node_approval_config.approval_strategy`）

| code | 含义 | 后端行为 |
|---|---|---|
| `preempt` | 抢占审批（任一人处理即可） | 任一人处理后，调 `editOtherPendingStepSkipped` 将同节点其他 pending 步骤置为 skipped |
| `all` | 全部处理（会签） | 必须所有人 approve 才完成节点，每处理一次检查 `queryPendingStepCountByNode` 是否为 0 |
| `initiator` | 流程发起人处理 | 提交时自动完成（`isInitiatorStep` 判定），不在审批阶段创建 pending 步骤，而是回填发起人信息 |

### 1.6 流程状态枚举

`wf_flow_definition.status` / `wf_flow_version.status`：`draft` / `active` / `disabled`，外加版本的 `archived`。

### 1.7 实际流程样例（标准升/降库）

来自 `rrs_flow_definition_demo_data.sql`，节点 `n1=开始 → n2=研究员A发起(initiator) → n3=研究员B复核(preempt) → n5=研究总监审批(all) → n6=O32自动审批(auto) → n7=结束`，n3/n5/n4 都有「驳回/不通过」分支分别路由到 n4（修改）或 n7（结束）：
- e3: n3→n4 `routeAction=reject`（复核驳回 → 修改）
- e4: n4→n3 `routeAction=approve`（修改后重新提交 → 回复核）
- e5: n3→n5 `routeAction=approve`（复核通过 → 审批）
- e6: n5→n7 `routeAction=reject`（审批不通过 → 结束）
- e7: n5→n6 `routeAction=approve`（审批通过 → O32 自动审批）
- e8: n4→n7 `routeAction=reject`（修改节点终止 → 结束）

直通流程样例（白名单入库）：`start → 研究员A发起(initiator) → end`，提交时即落地池状态。

---

## 2. 审核页面逻辑

### 2.1 入口模式

- 审核页面 `security_pool_adjust_approve.html`，`entryMode` 取值 `process`（我的事宜处理）/`next`（下一步校验确认）；URL 携带 `securityCode/targetPoolId/adjustLogId/adjustBatchNo/entryMode`。
- 页面初始化在 `created()`，调用 `initStandaloneReviewPage` → `loadDetailData(securityCode)` + `restoreStandaloneAdjustDraft`。

### 2.2 初始化加载的接口

`loadDetailData` 用 `Promise.all` 并发 5 个接口：

1. `POST /api/v1/securityPoolAdjust/querySecurityDetail` — 证券详情（顶部基础信息）
2. `POST /api/v1/securityPoolAdjust/queryAdjustPoolList {adjustDirection:'in'}` — 可调入池树
3. `POST /api/v1/securityPoolAdjust/queryAdjustPoolList {adjustDirection:'out'}` — 可调出池树
4. `POST /api/v1/securityPoolAdjust/querySecurityPoolStatus` — 当前证券/主体所在池
5. `POST /api/v1/securityPoolAdjust/queryAdjustLogList {securityCode, adjustBatchNo}` — 调库记录列表（按批次过滤；若不传批次，后端只返回未终结流程的记录：`audit_status NOT IN ('-1','20','21','99')`）

随后串行调用：
- `loadLogAttachments(adjustLogList)` — 对每条调库记录调 `POST /api/v1/attachments/queryAttachmentList {adjustLogId}`，按 `attachmentCategory` 分组为 `attachmentFiles`（信评报告）/`materialFiles`（其他材料）。
- `loadFlowSteps(adjustLogList)` — 对候选调库记录逐个调 `POST /api/v1/securityPoolAdjust/queryAdjustStepList {adjustLogId, adjustBatchNo}`，找到第一个有 `stepStatus==='pending'` 步骤的记录作为 `activeAdjustLog`，其步骤列表存入 `flowStepList`。

### 2.3 详情区与流程状态表渲染

页面按 CSS order 分块：
1. 证券基础信息（`el-descriptions` 只读）
2. 当前所在池（证券所在池 + 主体所在池两个折叠子块）
3. 调库记录表（列：序号/投资池名称/调整类型/调整方向/审批流程/信评报告/其他材料/调整说明/审核状态）
4. 调库校验结果（仅 `isNextMode` 显示）
5. 调整原因与建议
6. 当前流程状态（`el-table` 流程步骤表）
7. 审核审批区（仅 `isProcessMode` 显示）
8. 调库操作区（默认隐藏）

**流程步骤表**列与渲染规则：
- 序号（$index+1）
- 步骤名称（`row.nodeLabel`）
- 步骤结果：`stepStatusLabel` 把 `pending/approve/reject/submit/auto_process/canceled` 映射为中文徽标，样式由 `getStepStatusStyle` 决定
- 开始时间（`moment(startTime).format('YYYY-MM-DD HH:mm:ss')`）
- 处理人：当前登录用户处理时加 `el-icon-user-solid` 图标和强调样式（`isCurrentHandler(row)`）
- 处理结果：`processActionLabel(row.processAction, row)` — 修改节点上 approve 显示为「提交」、reject 显示为「终止流程」，其他节点正常显示通过/驳回/被跳过
- 处理时间
- 处理意见

前三列通过 `flowStepSpanMethod` + `getFlowStepMergeSpan` 实现「按节点+步骤结果+开始时间」分组行合并。行样式 `getFlowStepRowClass` 按节点类型与 stepStatus 加高亮类。`isCurrentHandler(row)` 当 `row.handlerId === currentLoginUserId` 时返回 true。

### 2.4 当前用户可处理步骤的识别

计算属性 `currentPendingStep`：
1. 从 `flowStepList` 过滤 `stepStatus==='pending'` 的行
2. 优先返回 `handlerId === currentLoginUserId` 的步骤
3. 否则若当前用户是管理员（ID === '1'），返回第一条 pending
4. 否则返回 null（页面显示「暂无需要当前用户处理的流程步骤」）

### 2.5 通过 / 驳回 / 撤回 按钮

审核审批区按钮：
- `el-radio-group v-model="auditAction"` 两个 radio button：`approve`（文案 `approveActionLabel`：修改节点显示「提交」、其他显示「通过」）和 `reject`（文案 `rejectActionLabel`：修改节点显示「终止流程」、其他显示「驳回」）
- `el-input type="textarea" v-model="auditComment"` 处理意见
- 提交按钮 `<el-button @click="submitAdjustAudit">`

> ⚠️ 页面**没有独立的「撤回」按钮**。「撤回」通过在修改节点（`auditStatus='11'`）上选择 `reject`（前端文案「终止流程」）实现，后端在 `isModifyNode + reject` 命中 `isTerminalByCurrentNode`，将日志置为 `99`（发起人已撤回）。

### 2.6 submitAdjustAudit 方法

1. 防重入检查 `auditSubmitLoading`
2. 若 `currentPendingStep` 为空提示「暂无需要当前用户处理的流程步骤」
3. 若 `auditAction==='reject'` 且意见为空，提示「驳回时处理意见不能为空」（修改节点显示「终止流程时处理意见不能为空」）
4. 构造 payload：
   ```js
   {
     adjustLogId, adjustBatchNo,
     stepId: step.id,
     processAction: this.auditAction,   // 'approve' | 'reject'
     processComment: this.auditComment,
     handlerId: this.currentLoginUserId,
     handlerName: this.currentLoginUserName
   }
   ```
5. 若 `shouldSubmitAuditAttachments()` 为 true（即 `isProcessMode && isModifyAuditStage && auditAction==='approve' && currentPendingStep`），调用 `buildAuditAttachmentChanges` 收集每条调库记录的 `creditReportFileIndexes/materialFileIndexes/creditReportSourceAttachmentIds/materialSourceAttachmentIds/deleteAttachmentIds`，组装为 `payload.attachmentChanges`，调 `submitAdjustAuditMultipart`：用 `FormData` 同时提交 `request`（JSON Blob）和 `files` 数组到 `POST /api/v1/securityPoolAdjustFlow/submitAdjustAuditWithFiles`（consumes=multipart/form-data，multipart 入口路径与 JSON 入口 `submitAdjustAudit` 不同）
6. 否则直接调 `POST /api/v1/securityPoolAdjustFlow/submitAdjustAudit`（consumes=application/json）
7. 成功后 `$message.success(result.message)`，重置 `auditAction='approve'`/`auditComment=''`，再次调 `loadDetailData(securityCode)` 刷新

### 2.7 请求 / 返回结构

请求体 `SecurityPoolAdjustAuditReq`：

| 字段 | 类型 | 用途 |
|---|---|---|
| `adjustLogId` | Long | 调库记录 ID（可空，后端从 step 反查回填） |
| `adjustBatchNo` | String | 调库批次号（可空，后端从 step 反查回填） |
| `stepId` | Long | **必填**，当前处理的流程步骤 ID |
| `processAction` | String | **必填**，`approve` 或 `reject` |
| `processComment` | String | 处理意见，reject 时必填 |
| `handlerId` | String | 当前处理人 ID |
| `handlerName` | String | 当前处理人名称 |
| `attachmentChanges` | List<AttachmentChange> | 修改节点提交时携带的附件变更 |

`AttachmentChange` 子结构：`adjustLogId / creditReportFileIndexes / materialFileIndexes / creditReportSourceAttachmentIds / materialSourceAttachmentIds / deleteAttachmentIds`。

返回 `SecurityPoolAdjustAuditDto`：

| 字段 | 类型 | 含义 |
|---|---|---|
| `adjustLogId` | Long | 调库记录 ID |
| `adjustBatchNo` | String | 调库批次号 |
| `stepId` | Long | 当前处理步骤 ID |
| `auditStatus` | String | 调库记录最新审核状态 |
| `finished` | Boolean | 流程是否已结束 |
| `nextStepCreated` | Boolean | 是否已创建后续待处理步骤 |
| `message` | String | 结果提示文案 |

---

## 3. 后端流转逻辑（核心）

### 3.1 入口与事务边界

`SecurityPoolAdjustFlowController.submitAdjustAudit` 有两个重载：
- `consumes=application/json` → `submitAdjustAudit(req)`
- `consumes=multipart/form-data` → `submitAdjustAuditWithFiles(req, files)`，files 转为 List

Service 入口 `submitAdjustAudit(req, files)` 标注 `@Transactional(rollbackFor = Exception.class)`，整个审批处理与池状态落地在同一事务内。

### 3.2 submitAdjustAudit 完整逻辑

#### 阶段 1：参数与步骤校验

1. `validateAuditReq`：`stepId` 不能为空；`processAction` 必须是 `approve` 或 `reject`；`reject` 时 `processComment` 必填。
2. `queryAdjustStepById(stepId)` 查出当前 step。
3. `resolveActualProcessStep`：若当前操作人是管理员（`handlerId === '1'`）且 step 不属于自己，则用 `queryPendingStepByHandler` 找到当前节点中管理员自己的 pending 步骤优先处理；普通用户直接返回原 step。
4. `validatePendingStep`：step 不存在抛「流程步骤不存在」；step.stepStatus 必须为 `pending`，否则抛「当前流程步骤已处理，请刷新后重试」；若 step.handlerId 有值且不等于 req.handlerId 且非管理员，抛「当前用户不是该步骤处理人」；反查回填 `adjustBatchNo`/`adjustLogId`。
5. `validateSubmitterCannotProcess`：管理员、发起/修改语义节点跳过；否则查同批次所有调库记录，若当前处理人 ID 等于任一记录的 `adjusterId`，抛「发起人不能参与后续流程操作」。

#### 阶段 2：修改节点附件变更（仅 modify 节点 approve）

`applyAttachmentChangesForModifySubmit`：
- 仅当 `processAction==='approve'` 且 `isModifyStep(step)` 才允许附件变更（否则抛「仅驳回待修改提交时允许修改附件」）。
- 校验每条 `AttachmentChange.adjustLogId` 必须属于当前批次。
- `deleteAdjustLogAttachments` 删除指定附件；`bindAttachments` 绑定本地新上传文件为 `credit_report_hand` 或 `material_hand`；`copyReportAttachments` 复制报告库附件。

#### 阶段 3：处理当前步骤并推进（processAdjustAudit）

1. `buildProcessComment`：若管理员代办他人步骤，意见末尾追加「（由管理员操作）」。
2. `resolveStepStatusForProcess`：发起/修改节点上 `approve` 写入 `stepStatus='submit'`；其他情况 `stepStatus = processAction`。
3. `resolveProcessActionForStore`：`submit` 状态下 `processAction` 也存为 `submit`。
4. `editAdjustStepProcess` 乐观更新当前步骤：SQL 带 `WHERE id=? AND step_status='pending'`，影响行数为 0 时抛「当前流程步骤已处理，请刷新后重试」（防并发）。
5. `completeCurrentApprovalNodeIfNeeded` 判断当前节点是否已完成：
   - `approval_strategy='all'` 且 `approve`：查询 `queryPendingStepCountByNode`，pendingCount === 0 才完成；否则返回 false（「当前会签节点仍有待处理人员」），直接 buildAuditDto 返回。
   - 抢占审批或未配置策略：调 `editOtherPendingStepSkipped` 将同节点其他 pending 步骤置为 `stepStatus=本步骤的 stepStatus, processAction='skipped'`，节点视为完成。

#### 阶段 4：推进到下一节点

6. 节点完成后 `buildFlowSnapshot(flowNodeId)` 加载流程版本全量数据。
7. `resolveProcessingNodeAuditStatus` 按**本次处理的节点**（不是下一节点）+ processAction 计算新的 `audit_status`：

   | 节点语义 | approve | reject |
   |---|---|---|
   | 任意中间节点 | `00`（流程中） | `11`（驳回待修改） |
   | 修改节点 | `00`（流程中） | `99`（发起人已撤回） |
   | 审批节点 | `20`（审批通过） | `21`（审批驳回） |
   | 自动审批节点 | `20` | — |

8. `editAdjustLogAuditStatus` 更新同批次所有调库记录的 `audit_status` + `audit_time`（按 adjustBatchNo 批量更新）。

#### 阶段 5：终止分支处理

9. `isTerminalByCurrentNode`：当 `reject` 且当前节点是修改节点或审批节点时，流程直接结束。
10. `createTerminalEndStep`：沿 reject 路径查找结束节点，每经过一个节点插入 `auto_process` 步骤记录，直到遇到 `end` 节点（返回 true）或下一个 `approval` 节点（返回 false，不应在 reject 主路径出现）。
11. 终止分支返回 buildAuditDto，message="审批流程已结束"。

#### 阶段 6：推进下一可处理节点

12. `advanceToNextAvailableStep` 从当前节点出发循环查找：
    - 自动审批节点（`isAutoApprovalNode`）：直接插入 `auto_process` 步骤，继续找下一节点。
    - `approval` 节点：调 `createPendingSteps` 创建待处理步骤并返回，`nextStepCreated=true, finished=false`。
    - `end` 节点：插入 `auto_process` 步骤，`finished=true`。
    - 其他节点：插入 `auto_process` 步骤并继续。
13. `createPendingSteps`：
    - 修改节点或发起节点 → `createInitiatorPendingStep`：查同批次首条调库记录的 `adjusterId/adjusterName` 作为处理人。
    - 其他审批节点 → `resolveApprovalHandlers` 展开角色/人员，按每个处理人插入一条 `pending` 步骤。
    - 无处理人配置时插入一条 `handlerId=null` 的 pending 步骤。
14. 若 `advanceResult.finished=true`，调 `finishAdjustBatch(step)` 落地同批次调库结果，返回 `auditStatus='20'`，message="审批已通过，调库结果已生效"。

### 3.3 最终通过时池状态落地

`finishAdjustBatch`：
1. `queryAdjustLogListForAudit` 取同批次所有未删除调库记录。
2. `editAdjustLogAuditStatus(..., '20')` 将整批 audit_status 置为「审批通过」。
3. 逐条按 `adjustMode` 处理：
   - `调入` → `addPoolStatus` 向 `ip_pool_status` 插入生效记录（audit_status='20'）。
   - `调出` → `deletePoolStatusSoft` 将 `ip_pool_status` 中该证券在该池的 `audit_status='20'` 记录 `is_deleted=1`。
4. `generateInternalReportsOnFinish`：对每条调库记录查手工上传信评报告附件，若有则新建一条 `rrs_report_in` 内部报告记录并复制附件，标题格式「证券全称 + 调入/调出 + 投资池全路径 + 报告」。

### 3.4 驳回 / 撤回对池状态的影响

- **复核节点驳回** → `audit_status='11'`（驳回待修改），不修改 `ip_pool_status`，发起人可在修改节点重新提交。
- **修改节点 reject（前端「终止流程」）** → `audit_status='99'`（发起人已撤回），`createTerminalEndStep` 沿 reject 路径写入结束节点步骤，**不修改 ip_pool_status**。
- **审批节点驳回** → `audit_status='21'`（审批驳回），同样走 `createTerminalEndStep`，**不修改 ip_pool_status**。

总之：驳回与撤回都不影响 `ip_pool_status`，只有审批通过 (`20`) 才会落地入池/出池状态。

### 3.5 管理员代办逻辑

- 管理员 ID 固定为 `'1'`（`isAdminOperator` / `SecurityPoolAdjustService.ADMIN_USER_ID`）。
- `resolveActualProcessStep`：管理员处理他人节点时优先定位自己的 pending 步骤。
- `validatePendingStep`：管理员可处理任意 handlerId 的步骤。
- `validateSubmitterCannotProcess`：管理员跳过「发起人不能处理后续」校验。
- `buildProcessComment`：管理员代办他人步骤时意见追加「（由管理员操作）」。
- 前端 `currentLoginUserId='1'`，`isAdminUser()` 返回 true，`currentPendingStep` 计算时若自己无 pending 步骤则取第一条 pending。
- 我的事宜查询时 `currentUserId='1'` 不带 `handler_id` 过滤，管理员可见全部待办。

### 3.6 事务范围

整个 `submitAdjustAudit` 标注 `@Transactional(rollbackFor = Exception.class)`：步骤更新 → 调库日志状态更新 → 自动节点步骤插入 → 下一节点 pending 步骤创建 → `finishAdjustBatch` 落地池状态 → 内部报告生成，全部在同一事务内，任一异常全部回滚。

---

## 4. 我的事宜入口

### 4.1 列表查询

`my_matters.html` 用 `el-tabs` 切换「待处理/已完成」两个 tab（`activeTab: 'pending'|'completed'`），切换触发 `handleTabClick` 重置页码并重新加载。

`loadList` 调 `POST /api/v1/myMatters/queryMyMattersPage`，请求体：
```js
{
  flowIds: [...],                // 流程 ID 多选
  startDateStart, startDateEnd,  // 开始日期范围 yyyy-MM-dd
  processDescription,            // 描述关键词
  auditStatus,                   // 审核状态码
  stepStatus: 'pending'|'completed',  // 由 activeTab 决定
  initiatorName,
  currentUserId: '1',
  pageIndex, pageSize
}
```

后端 `MyMattersMapper.xml` 核心 SQL：
- 待处理 tab：INNER JOIN 子查询取每个 `adjust_log_id` 的 `MAX(step_id)` 且 `step_status='pending'`，非管理员额外加 `handler_id = currentUserId` 过滤。
- 已完成 tab：取每个调库记录的最新步骤，并要求该批次无 pending 步骤（`NOT EXISTS(... step_status='pending' ...)`）。
- 全局过滤：非管理员要求该调库记录下存在 `handler_id = currentUserId` 的步骤（只显示自己参与过的）；`currentUserId` 为空时 `AND 1=0` 强制返回空。
- 返回字段含 `stepId`（即 `s.id`）、`flowName`、`stepName`（nodeLabel）、`processDescription`（拼接「发起人 将 证券简称 调入/调出 目标池 的审批申请」）、`auditStatus`、`stepStatus`、`initiatorName`、`startTime`。

`MyMattersService.replacePoolNameWithFullPath` 后处理将描述中的「目标池叶子名称」替换为「全路径名称」。

### 4.2 流程名称下拉

`loadFlowOptions` 调 `POST /api/v1/myMatters/queryFlowOptionList`，返回当前用户事宜中出现过的去重流程列表（`flowId/flowKey/flowName/description`）。

### 4.3 跳转审核页

`openMatterPage(row)`：
- 拼 URL 参数：`securityCode / targetPoolId / adjustLogId / adjustBatchNo / entryMode`。
- `entryMode`：pending tab → `'process'`（进入 `security_pool_adjust_approve.html`）；completed tab → `'view'`（进入 `security_pool_adjust_detail.html`）。
- `window.location.href = page + '?' + params.toString()`

审核页 `initStandaloneReviewPage` 解析这些 URL 参数并加载详情。

---

## 5. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `securityPoolAdjustFlow/submitAdjustAudit`（application/json） | `SecurityPoolAdjustAuditReq`：stepId/adjustLogId/adjustBatchNo/processAction(approve\|reject)/processComment/handlerId/handlerName | `SecurityPoolAdjustAuditDto` | 提交审批处理意见，纯文本场景 |
| `securityPoolAdjustFlow/submitAdjustAuditWithFiles`（multipart/form-data） | `request`(JSON Blob) + `files`(MultipartFile[]) | 同上 | 修改节点提交时同时上传附件变更（multipart 入口；JSON 入口为 `submitAdjustAudit`） |
| `securityPoolAdjust/querySecurityPage` | securityCode/securityShortName/issuer + 分页 | `PageResult<SecurityInfoDto>` | 列表页证券检索 |
| `securityPoolAdjust/querySecurityDetail` | securityCode | `SecurityInfoDetailDto` | 证券详情 |
| `securityPoolAdjust/queryAdjustPoolList` | securityCode/adjustDirection(in\|out)/currentUserId | `List<PoolDto>` | 可调入/调出投资池树（含互斥关系） |
| `securityPoolAdjust/querySecurityPoolStatus` | securityCode | `SecurityPoolStatusDto` | 当前证券/主体所在池 |
| `securityPoolAdjust/queryAdjustLogList` | securityCode/adjustBatchNo | `List<AdjustLogDto>` | 调库记录列表（按批次过滤；不传批次仅返回未终结流程） |
| `securityPoolAdjust/queryAdjustStepList` | adjustLogId/adjustBatchNo | `List<IpAdjustStepDto>` | 同批次流程步骤列表 |
| `securityPoolAdjust/checkAdjust` | securityCode/items[{targetPoolId,adjustMode,poolType}] | `AdjustCheckDto` | 调库可行性校验 + 推荐流程 |
| `securityPoolAdjust/addAdjustLog`（application/json） | `SecurityPoolAdjustSubmitReq` | `AdjustSubmitDto` | 提交调库申请（无附件） |
| `securityPoolAdjust/addAdjustLogWithFiles`（multipart/form-data） | `request`(JSON Blob) + `files`(MultipartFile[]) | `AdjustSubmitDto` | 提交调库申请（带附件，含直通流程即时入池） |
| `myMatters/queryMyMattersPage` | flowIds/startDateStart/startDateEnd/processDescription/auditStatus/stepStatus(pending\|completed)/initiatorName/currentUserId + 分页 | `PageResult<MyMattersDto>` | 我的事宜分页列表 |
| `myMatters/queryFlowOptionList` | `{currentUserId}` | `List<FlowOptionDto>` | 我的事宜流程名称下拉 |
| `attachments/queryAttachmentList` | adjustLogId | 附件列表 | 加载调库记录附件 |

> 路径均带前缀 `/api/v1/`。

---

## 6. 关键数据库表

### 6.1 `ip_adjust_log`（调库记录表）

一条调库申请对应一行，同批次共用 `adjust_batch_no`。关键字段：`id`、`security_code`、`adjust_type`（手工/联动/互斥/关联/Excel导入/手动批量调整）、`adjust_mode`（调入/调出）、`adjust_batch_no`、`target_pool_id/pool_name/pool_type`、`flow_id/flow_key/flow_type`、**`audit_status`**、`adjuster_id/name`、`adjust_reason/advice`、`submit_time/audit_time/entry_time`、`is_deleted`。

### 6.2 `ip_pool_status`（投资池当前状态表）

结构与 `ip_adjust_log` 几乎相同，多 `adjust_log_id` 指向来源调库日志。仅在 `audit_status='20'` 时表示证券实际入池；`is_deleted=1` 表示已调出（软删除）。

### 6.3 `ip_adjust_step`（流程步骤记录表）

| 字段 | 含义 |
|---|---|
| `id` | 主键 |
| `adjust_log_id` / `adjust_batch_no` | 关联调库记录/批次 |
| `flow_node_id` | 关联 `wf_flow_node.id` |
| `node_code` / `node_label` / `node_type` | 节点标识/名称/类型（start/approval/auto/end/notify/condition） |
| `approval_strategy` | preempt/all/initiator |
| `sort_order` | 排序序号 |
| **`step_status`** | pending/approve/reject/submit/auto_process/canceled |
| `handler_id` / `handler_name` | 处理人 |
| `process_action` | submit/approve/reject/auto_process/skipped |
| `process_comment` | 处理意见 |
| `start_time` / `process_time` | 步骤激活时间 / 处理时间 |

### 6.4 `audit_status` 审核状态枚举

| code | 含义 | 触发场景 |
|---|---|---|
| `-1` | 无效调整 | 批量校验不通过，操作中止 |
| `00` | 流程中 | 提交调库 / 修改节点重新提交（`isInitiatorNode||isModifyNode + approve`） |
| `11` | 驳回待修改 | 复核节点驳回（`isReviewNode + reject`） |
| `20` | 审批通过 | 审批/自动审批节点通过，或直通流程，或 `finishAdjustBatch` |
| `21` | 审批驳回 | 审批节点驳回（`isApproveNode + reject`） |
| `32` | O32自动审批 | （字典保留，运行时未直接写入） |
| `99` | 发起人已撤回 | 修改节点 reject（前端「终止流程」） |

### 6.5 `step_status` 步骤状态枚举

| code | 含义 | 进入条件 |
|---|---|---|
| `pending` | 待处理 | 审批节点创建待处理步骤时初始状态 |
| `approve` | 通过 | 审批节点处理人 approve |
| `reject` | 驳回 | 审批节点处理人 reject |
| `submit` | 提交 | 发起/修改节点 approve 时（`resolveStepStatusForProcess`） |
| `auto_process` | 自动处理 | start/end/auto/notify 节点自动完成时 |
| `canceled` | 已撤回 | （字典保留） |
| `skipped` | 被跳过 | 抢占节点中其他 pending 步骤在首位处理后（仅写入 `process_action='skipped'`，`step_status` 写为首位步骤的 stepStatus） |

### 6.6 流程定义相关表

见 1.1 节，均在 `rrs_flow_definition_schema.sql` 中定义，每张业务表都有同结构 `_evt` 事件表。

---

## 7. 状态流转图（文字描述）

### 7.1 调库申请单 `audit_status` 流转

```
[提交]
   │
   ├─ 直通流程（start→end 或 start→initiator→end）
   │     ↓ 20（审批通过，立即入池/调出 ip_pool_status）
   │
   └─ 非直通流程
         ↓ 00（流程中，start 节点 auto_process + initiator 节点 submit + 下一审批节点 pending）
         │
         ↓ 中间节点 approve → 00（流程中，继续流转）
         │
         ├─ 复核节点 reject → 11（驳回待修改，发起人在修改节点 pending）
         │     │
         │     ├─ 修改节点 approve（前端「提交」）→ 00（重新进入流程中，路由回复核节点）
         │     └─ 修改节点 reject（前端「终止流程」）→ 99（发起人已撤回，createTerminalEndStep 写结束节点）
         │
         ├─ 审批节点 approve（all 策略需全部处理；preempt 任一即可）→ 20（审批通过，finishAdjustBatch 落地 ip_pool_status）
         └─ 审批节点 reject → 21（审批驳回，createTerminalEndStep 写结束节点）
```

### 7.2 步骤 `step_status` 流转

```
[pending] ──approve──→ [approve]      （审批节点，普通处理）
          ──reject───→ [reject]
          ──skipped──→ [approve|reject|submit]（抢占节点中其他 pending 被首位处理后跳过，process_action='skipped'）

[发起/修改节点 pending]
          ──approve──→ [submit]        （前端按钮文案「提交」）
          ──reject───→ [reject]        （前端按钮文案「终止流程」，仅修改节点）

[start/end/auto/notify 节点]
          ──自动完成──→ [auto_process]
```

### 7.3 关键并发与防重

- `editAdjustStepProcess` SQL 带 `WHERE step_status='pending'` 乐观锁，影响 0 行抛「已处理」。
- 会签节点每次处理都检查 `queryPendingStepCountByNode`，未到 0 不推进。
- 抢占节点首位处理后立即 `editOtherPendingStepSkipped` 跳过其他 pending。
- 同批次调库日志统一更新 `audit_status`，避免同批次记录状态不一致。
- 整个 `submitAdjustAudit` 在单一 `@Transactional` 内，任一异常全量回滚。

### 7.4 池状态影响范围

| 动作 | 是否影响 ip_pool_status | 说明 |
|---|---|---|
| 提交调库（非直通） | 否 | 仅写 ip_adjust_log + ip_adjust_step |
| 提交调库（直通） | 是 | 同事务写 ip_pool_status（调入插入 / 调出软删除） |
| 复核通过 / 复核驳回 / 修改重新提交 | 否 | 仅更新 audit_status 与 step |
| 审批通过（finishAdjustBatch） | 是 | 同事务写 ip_pool_status + 生成内部报告 |
| 审批驳回 / 修改节点终止 | 否 | 流程结束，池状态保持原样 |

---

## 8. 关键校验汇总

- `stepId`、动作、处理人必填，步骤必须为待处理状态。
- 非候选处理人不得处理；同一步骤不可重复提交（乐观锁）。
- 流程推进需满足审批策略（抢占/会签/发起人）和条件路由。
- 最终通过才更新池状态；驳回或撤回不得错误生效。
- 管理员代办必须保留代操作说明，普通用户不可处理他人步骤；发起人不能参与后续流程操作。

## 9. 验收标准

- 抢占、会签、驳回修改、结束等路径均产生完整步骤状态。
- 审批结果与调整日志审核状态一致；仅审批通过落地 ip_pool_status。
- 修改节点 approve 允许附件变更，其他场景拒绝。
- `SecurityPoolAdjustApproveApiTest` 覆盖上下文加载和审批提交。

## 10. 关键源码索引

- 前端：`znty-rrs-ui/security_pool_adjust_approve.html`、`znty-rrs-ui/security_pool_adjust_detail.html`、`znty-rrs-ui/my_matters.html`、`znty-rrs-ui/flow_definition.html`、`znty-rrs-ui/dict.js`
- Controller：`SecurityPoolAdjustFlowController.java`、`MyMattersController.java`、`FlowController.java`
- Service：`SecurityPoolAdjustFlowService.java`（核心流转，`submitAdjustAudit`/`resolveProcessingNodeAuditStatus`/`finishAdjustBatch`）、`SecurityPoolAdjustService.java`（提交与初始步骤）、`MyMattersService.java`、`FlowService.java`
- Mapper：`SecurityPoolAdjustMapper.xml`、`MyMattersMapper.xml`
- 实体：`SecurityPoolAdjustAuditReq`、`SecurityPoolAdjustAuditDto`、`IpAdjustStepBo`、`IpAdjustLogBo`、`FlowSnapshot`、`FlowNodeBo`、`FlowEdgeBo`、`NodeApprovalConfigBo`、`NodeApprovalHandlerBo`
- SQL：`sql/rrs_security_pool_adjust_schema.sql`、`sql/rrs_flow_definition_schema.sql`、`sql/rrs_flow_definition_demo_data.sql`
