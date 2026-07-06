# CRMW池调库审核（审批流转）需求说明

> 前端页面：`crmw_pool_adjust_approve.html`
> 后端前缀：`/api/v1/crmwPoolAdjustFlow`（审批处理）+ `/api/v1/crmwPoolAdjust`（详情/校验，复用 [19] 接口）
> 角色定位：审核 / 审批人对 CRMW 池调库申请进行复核、驳回、修改重提、审批通过或驳回，最终通过才落地 `ip_pool_status_crmw`。流转逻辑与证券池调库审批（[05]）同构。

---

## 1. 页面概览

单 Vue 实例（`el: '#crmw_pool_adjust_approve'`），`currentPage='detail'`（默认直接进详情页）。

**入口模式 `entryMode`**：`process`（我的事宜处理，默认）/ `next`（下一步校验确认）。

**初始化**（`created`）：`this.initStandaloneReviewPage()`。从 URL 取 `securityCode`/`windCode`、`crmwScode`、`targetPoolId`、`adjustLogId`/`adjust_log_id`、`adjustBatchNo`/`adjust_batch_no`、`entryMode`（`'next'` 或默认 `'process'`）；`adjustStep=2`；`loadDetailData(securityCode)` + `restoreStandaloneAdjustDraft`（从 sessionStorage 恢复草稿）。默认 `currentLoginUserId='1'`。

> 审批页 URL 必带 `crmwScode`：`initStandaloneReviewPage` 校验 `securityCode && crmwScode`，否则提示「缺少证券或CRMW信息」。

---

## 2. 筛选与查询逻辑

审核页无独立筛选；`loadDetailData` 用 `Promise.all` 并发 **6 个接口**：

| 接口 | 请求体 | 用途 |
|---|---|---|
| `querySecurityDetail` | `{ securityCode }` | 标的证券详情 → `bondDetail` |
| `queryCrmwDetail` | `{ securityCode: selectedCrmwCode }` | CRMW 凭证详情 → `crmwDetail` |
| `queryCrmwAdjustPoolList` | `{ securityCode, adjustDirection:'in', currentUserId }` | 可调入池 |
| `queryCrmwAdjustPoolList` | `{ securityCode, adjustDirection:'out', currentUserId }` | 可调出池 |
| `queryCrmwPoolStatus` | `{ securityCode }` | 当前证券/主体所在池 |
| `queryCrmwAdjustLogList` | `{ securityCode, adjustBatchNo }` | 调库记录（无批次仅返回未终结流程） |

随后串行：构建调入/调出树、互斥映射、`loadLogAttachments`（每条 log 调 `/api/v1/attachments/queryAttachmentList`）、`loadFlowSteps`（逐个调 `queryCrmwAdjustStepList`，找含 `stepStatus==='pending'` 的记录作为 `activeAdjustLog`）。

---

## 3. 审批处理逻辑

### 3.1 `submitAdjustAudit` 后端逻辑（`@Transactional(rollbackFor=Exception.class)`）

入口 `CrmwPoolAdjustFlowService.submitAdjustAudit(req, files)`：

**阶段1 参数与步骤校验**
1. `validateAuditReq`：`stepId` 非空；`processAction` 必须是 `ProcessAction.APPROVE`/`REJECT`；`reject` 时 `processComment` 必填。
2. `queryAdjustStepById(stepId)` 查当前 step。
3. `resolveActualProcessStep`：管理员（`handlerId==='1'`）且 step 不属于自己时，用 `queryPendingStepByHandler` 找管理员自己的 pending 步骤优先处理。
4. `validatePendingStep`：step 不存在→`"流程步骤不存在"`；`stepStatus` 必须 `pending`→否则`"当前流程步骤已处理，请刷新后重试"`；`handlerId` 有值且不等于 req 且非管理员→`"当前用户不是该步骤处理人"`；反查回填 `adjustBatchNo`/`adjustLogId`。
5. `validateSubmitterCannotProcess`：管理员、发起/修改语义节点跳过；否则查同批次所有调库记录，若 `handlerId` 等于任一记录 `adjusterId`→`"发起人不能参与后续流程操作"`。

**阶段2 修改节点附件变更**（`applyAttachmentChangesForModifySubmit`）
- 仅 `processAction==='approve'` 且 `isModifyStep(step)` 才允许（否则`"仅驳回待修改提交时允许修改附件"`）。
- 校验每条 `AttachmentChange.adjustLogId` 属于当前批次。
- `deleteAdjustLogAttachments` / `bindAttachments`（credit_report_hand/material_hand）/ `copyReportAttachments`。

**阶段3 处理当前步骤并推进**（`processAdjustAudit`）
1. `buildProcessComment`：管理员代办他人步骤时意见追加「（由管理员操作）」。
2. `resolveStepStatusForProcess`：发起/修改节点 approve → `stepStatus='submit'`；其他 → `stepStatus=processAction`。
3. `editAdjustStepProcess`（乐观锁 `WHERE id=? AND step_status='pending'`，影响 0 行→`"当前流程步骤已处理，请刷新后重试"`）。
4. `completeCurrentApprovalNodeIfNeeded`：
   - `approvalStrategy='all'`（会签）+ approve：`queryPendingStepCountByNode`，pendingCount===0 才完成。
   - 抢占/未配置：`editOtherPendingStepSkipped` 将同节点其他 pending 置 `processAction='skipped'`。

**阶段4 推进下一节点**
5. `buildFlowSnapshot(flowNodeId)` 加载流程版本全量数据。
6. `resolveProcessingNodeAuditStatus` 按**本次处理节点**+processAction 计算 `audit_status`：

| 节点语义 | approve | reject |
|---|---|---|
| 复核节点 | `10` | `11` |
| 修改节点 | `00` | `99` |
| 审批节点 | `20` | `21` |
| 自动审批节点 | `20` | — |

7. `editAdjustLogAuditStatus` 按 `adjustBatchNo` 批量更新同批次 `ip_adjust_log.audit_status` + `audit_time`。

**阶段5 终止分支**（`isTerminalByCurrentNode`：reject 且修改/审批节点）
8. `createTerminalEndStep`：沿 reject 路径插入 auto_process 步骤直到 end。

**阶段6 推进下一可处理节点**（`advanceToNextAvailableStep`）
9. 自动审批节点→直接 auto_process 继续；approval 节点→`createPendingSteps` 创建 pending；end→auto_process + `finished=true`。
10. `finished=true` 时调 `finishAdjustBatch(step)`：
    - `editAdjustLogAuditStatus(..., '20')` 整批置审批通过。
    - 逐条：调入→`addPoolStatus`（写 `ip_pool_status_crmw`）；调出→`deletePoolStatusSoft`。
    - `generateInternalReportsOnFinish`：手工信评报告附件沉淀为 `rrs_report_in` 内部报告。

### 3.2 前端提交逻辑

- `isModifyAuditStage`：`activeLog.auditStatus === '11'` 或 当前 pending 步骤 `nodeLabel` 含「修改」。
- `approveActionLabel`：修改节点显示「提交」，其他显示「通过」。
- `rejectActionLabel`：修改节点显示「终止流程」，其他显示「驳回」。
- `submitAdjustAudit` 方法：构造 payload `{adjustLogId, adjustBatchNo, stepId, processAction, processComment, handlerId, handlerName}`；`shouldSubmitAuditAttachments()`（process + 修改阶段 + approve + 有 pending 步骤）为 true 时组装 `attachmentChanges` 走 multipart `submitAdjustAuditWithFiles`，否则走 JSON `submitAdjustAudit`。

---

## 4. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `crmwPoolAdjustFlow/submitAdjustAudit`（application/json） | `CrmwPoolAdjustAuditReq`：stepId, adjustLogId, adjustBatchNo, processAction(approve\|reject), processComment, handlerId, handlerName, attachmentChanges? | `CrmwPoolAdjustAuditDto` | 提交审批处理意见（无附件变更） |
| `crmwPoolAdjustFlow/submitAdjustAuditWithFiles`（multipart/form-data） | `request`(JSON Blob) + `files`(MultipartFile[]) | `CrmwPoolAdjustAuditDto` | 修改节点提交时同时上传附件变更 |

> 上下文查询接口（`querySecurityDetail`/`queryCrmwDetail`/`queryCrmwAdjustPoolList`/`queryCrmwPoolStatus`/`queryCrmwAdjustLogList`/`queryCrmwAdjustStepList`）复用 `CrmwPoolAdjustController`，见 [19] 接口清单。
> `CrmwPoolAdjustAuditDto` 返回：`{adjustLogId, adjustBatchNo, stepId, auditStatus, finished, nextStepCreated, message}`。路径均带前缀 `/api/v1/`。

---

## 5. 关键数据库表

| 表 | 操作 | 关键字段 |
|---|---|---|
| `ip_adjust_step`（步骤表） | `editAdjustStepProcess`（乐观锁更新）、`addAdjustStep`（创建 pending）、`editOtherPendingStepSkipped`（抢占跳过）、`queryPendingStepCountByNode`（会签计数） | id, step_status, process_action, process_comment, process_time |
| `ip_adjust_log`（调库记录表） | `editAdjustLogAuditStatus` 按 `adjust_batch_no` 批量更新 `audit_status` | adjust_batch_no, audit_status |
| `ip_pool_status_crmw`（落地池状态） | `addPoolStatus`（调入生效）/`deletePoolStatusSoft`（调出软删） | security_code, target_pool_id, pool_type='crmw', audit_status='20', is_deleted |
| `wf_flow_*`（流程定义，构建 FlowSnapshot） | 只读 | — |
| `rrs_report_in` | INSERT（`generateInternalReportsOnFinish` 审批通过后沉淀手工信评报告） | report_title, report_type, security_code, data_source='uploaded' |

---

## 6. 状态流转

`audit_status` 枚举（与证券池完全一致，复用 `-1/00/10/11/20/21/32/99`）：

| code | 含义 | 触发场景 |
|---|---|---|
| `-1` | 无效调整 | 批量校验不通过 |
| `00` | 已提交待审核 | 提交调库 / 修改节点重新提交 |
| `10` | 审核通过待审批 | 复核节点 approve |
| `11` | 驳回待修改 | 复核节点 reject |
| `20` | 审批通过 | 审批/自动审批节点 approve，或直通，或 `finishAdjustBatch` |
| `21` | 审批驳回 | 审批节点 reject |
| `32` | O32自动审批 | 字典保留 |
| `99` | 发起人已撤回 | 修改节点 reject（前端「终止流程」） |

`step_status`：`pending`/`approve`/`reject`/`submit`/`auto_process`/`canceled`/`skipped`。并发与防重：`editAdjustStepProcess` 乐观锁；会签 `queryPendingStepCountByNode`；抢占首位即跳过其余；同批次统一 `editAdjustLogAuditStatus`；整个 `submitAdjustAudit` 单一 `@Transactional`。

---

## 7. 与其他池模块的差异

- **无独立 FlowMapper**：`CrmwPoolAdjustFlowService` 复用 `CrmwPoolAdjustMapper` 的所有步骤/日志/池状态操作（方法名与证券池 `SecurityPoolAdjustMapper` 一致）。
- **finishAdjustBatch 落地 `ip_pool_status_crmw`**：而非 `ip_pool_status`；`deletePoolStatusSoft` SQL 带 `AND pool_type='crmw'`。
- **审批页 URL 必带 `crmwScode`**：`initStandaloneReviewPage` 校验 `securityCode && crmwScode`。
- 其余流转逻辑（会签/抢占/发起人/自动审批/终止分支/管理员代办/内部报告生成）与证券池审批完全一致。

---

## 8. 验收标准

- 复核 approve → `10`，复核 reject → `11` 并在修改节点创建发起人 pending。
- 修改节点 approve（前端「提交」）→ `00` 回复核；修改节点 reject（前端「终止流程」）→ `99` 终止。
- 审批 approve → `20`，`finishAdjustBatch` 落地 `ip_pool_status_crmw` + 生成内部报告；审批 reject → `21` 终止。
- 会签节点需全部 approve 才推进；抢占节点首位处理即跳过其余。
- 发起人不得参与后续流程节点处理。
- 仅 `audit_status='20'` 落地 `ip_pool_status_crmw`。

## 9. 关键源码索引

- 前端：`znty-rrs-ui/crmw_pool_adjust_approve.html`（`initStandaloneReviewPage`、`loadDetailData`、`currentPendingStep`、`isModifyAuditStage`、`submitAdjustAudit`、`buildAuditAttachmentChanges`）
- Controller：`CrmwPoolAdjustFlowController.java`（`@RequestMapping("/api/v1/crmwPoolAdjustFlow")`，2 端点）
- Service：`CrmwPoolAdjustFlowService.java`（`submitAdjustAudit`、`resolveProcessingNodeAuditStatus`、`finishAdjustBatch`、`applyAttachmentChangesForModifySubmit`、`advanceToNextAvailableStep`、`createTerminalEndStep`、`generateInternalReportsOnFinish`）
- Mapper：复用 `mapper/CrmwPoolAdjustMapper.java` / `CrmwPoolAdjustMapper.xml`（**无独立 FlowMapper**）
- 实体：`entity/crmwpooladjustflow/CrmwPoolAdjustAuditReq.java`（含 `AttachmentChange` 内部类）、`CrmwPoolAdjustAuditDto.java`
- SQL：`sql/rrs_crmw_pool_status_schema.sql`、`sql/rrs_flow_definition_schema.sql`
- 测试：`CrmwPoolAdjustFlowApiTest.java`、`CrmwPoolAdjustFlowServiceTest.java`
