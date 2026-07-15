# 禁投池调整审核（审批流转）需求说明

> 前端页面：`forbidden_pool_adjust_approve.html`
> 后端前缀：`/api/v1/forbiddenPoolAdjustFlow`（审批处理）+ `/api/v1/forbiddenPoolAdjust`（详情/列表/校验，复用 [15] 接口）
> 角色定位：审核 / 审批人对主体级禁投池调库申请进行复核、驳回、修改重提、审批通过或驳回，最终通过才落地 `ip_pool_status` 并同步旗下债券。

---

## 1. 页面概览

单 Vue 实例（`el: '#forbidden_pool_adjust_approve'`），`currentPage` 默认 `'detail'`（审核页直接进详情，列表页代码保留但不默认展示）。

**入口模式 `entryMode`**：`'process'`（我的事宜处理，默认）/ `'next'`（下一步校验确认）。URL `entryMode=next` 切换；`adjustStep` 默认 `2`。

**详情页区块**（CSS order）：主体基本信息（只读）→ 当前所在池（主体所在池 + 旗下债券所在池）→ 调库记录表 → 调库校验结果（仅 `isNextMode`）→ 原因和建议 → 当前流程状态（仅 `isProcessMode`）→ 审核审批区（仅 `isProcessMode`）→ 调库操作区（恒 false，隐藏）。

**初始化**（`created`）：`this.initStandaloneReviewPage()`。读 URL `companyCode`（缺则提示「缺少主体信息，请从业务页面进入」）、`targetPoolId`/`adjustLogId`/`adjustBatchNo`、`entryMode`，`adjustStep=2`，`loadDetailData(companyCode)` + `restoreStandaloneAdjustDraft()`（从 sessionStorage 按 `adjustDraftKey` 恢复 next 模式草稿）。默认 `currentLoginUserId='1'`。

---

## 2. 筛选与查询逻辑

### 2.1 详情并发加载（`loadDetailData`，与 [15] 同构 5 接口）

`queryCompanyDetail` / `queryAdjustPoolList`(in) / `queryAdjustPoolList`(out) / `queryCompanyPoolStatus` / `queryAdjustLogList { companyCode, adjustBatchNo }`。

随后 `loadLogAttachments(adjustLogList)`（每条调 `/api/v1/attachments/queryAttachmentList`，按 `attachmentCategory` 拆信评报告/其他材料）、`loadFlowSteps(adjustLogList)`（`uniqueAdjustLogsByFlow` 按 `batch:批次号`/`log:记录ID` 去重，逐个调 `queryAdjustStepList`，选首个含 `pending` 步骤的为 `activeAdjustLog`，步骤存 `flowStepList`）。

### 2.2 调库记录表列

序号 / 调整对象（`securityShortName`+`securityCode`）/ 投资池名称（`poolPath` 全路径）/ 调整类型（el-tag）/ 调整方向（调入 success/调出 danger）/ 审批流程（flowType tag + flowName）/ 信评报告 / 其他材料 / 调整说明 / 审核状态（`auditStatus`，el-tag）。

`showLogUploadActions = isNextMode || (isProcessMode && isModifyAuditStage && !!currentPendingStep)`：仅 next 模式或 process 模式修改阶段有 pending 步骤时显示「选择报告/上传附件」按钮。

---

## 3. 审批处理逻辑

### 3.1 当前用户可处理步骤识别（`currentPendingStep` 计算属性）

1. `flowStepList.filter(stepStatus==='pending')`。
2. 优先返回 `handlerId === currentLoginUserId` 的步骤。
3. 否则若 `isAdminUser()`（ID==='1'）返回第一条 pending。
4. 否则 null（页面显示「暂无需要当前用户处理的流程步骤」）。

### 3.2 驳回待修改阶段识别（`isModifyAuditStage`）

`activeAdjustLog.auditStatus === '11'` **或** `currentPendingStep.nodeLabel` 含「修改」。

- `auditResultTitle`：修改阶段「处理结果」，否则「审核结果」。
- `approveActionLabel`：修改阶段「提交」，否则「通过」。
- `rejectActionLabel`：修改阶段「终止流程」，否则「驳回」。
- `auditSubmitButtonText`：非修改阶段「提交」；修改阶段 reject→「终止流程」，approve→「提交」。

### 3.3 `submitAdjustAudit` 前端方法

1. 防重入 `auditSubmitLoading`。
2. `currentPendingStep` 为空提示。
3. `auditAction==='reject'` 且意见空提示（修改阶段「终止流程时处理意见不能为空」）。
4. payload：`{ adjustLogId, adjustBatchNo, stepId: step.id, processAction: auditAction, processComment: auditComment, handlerId, handlerName }`。
5. 若 `shouldSubmitAuditAttachments()`（`isProcessMode && isModifyAuditStage && auditAction==='approve' && currentPendingStep`）为 true，`buildAuditAttachmentChanges` 收集每条调库记录的 `{ adjustLogId, creditReportFileIndexes, materialFileIndexes, creditReportSourceAttachmentIds, materialSourceAttachmentIds, deleteAttachmentIds }` → `payload.attachmentChanges`，调 `submitAdjustAuditMultipart`（FormData：`request` JSON Blob + `files`）→ `POST /api/v1/forbiddenPoolAdjustFlow/submitAdjustAuditWithFiles`（multipart）。
6. 否则 `POST /api/v1/forbiddenPoolAdjustFlow/submitAdjustAudit`（JSON）。
7. 成功 `$message.success(result.message)`，重置 `auditAction='approve'`/`auditComment=''`，`loadDetailData(companyCode)` 刷新。

### 3.4 后端 Controller

`ForbiddenPoolAdjustFlowController.submitAdjustAudit` / `submitAdjustAuditWithFiles` 两个重载：`consumes=application/json` → `submitAdjustAudit(req)`；`consumes=multipart/form-data` → `submitAdjustAuditWithFiles(req, files)`（`@RequestPart("request")` + `@RequestPart(value="files", required=false)`）。均委托 `forbiddenPoolAdjustFlowService.submitAdjustAudit`。

### 3.5 后端 `ForbiddenPoolAdjustFlowService.submitAdjustAudit`（`@Transactional(rollbackFor=Exception.class)`）

**阶段 1：参数与步骤校验**
- `validateAuditReq`：`stepId` 非空；`processAction` ∈ {approve, reject}；reject 时 `processComment` 必填。
- `queryAdjustStepById(stepId)`。
- `resolveActualProcessStep`：管理员（`handlerId==='1'`）且 step 不属自己时，`queryPendingStepByHandler` 定位管理员自己的 pending 步骤。
- `validatePendingStep`：step null 抛「流程步骤不存在」；`stepStatus` 必须 `pending` 否则「当前流程步骤已处理，请刷新后重试」；`handlerId` 有值且不等 req.handlerId 且非管理员抛「当前用户不是该步骤处理人」；反查回填 `adjustBatchNo`/`adjustLogId`。
- `validateSubmitterCannotProcess`：管理员/发起-修改语义节点跳过；否则查同批次所有记录（`queryAdjustLogListForAudit`），若当前处理人 ID 等于任一记录 `adjusterId` 抛「发起人不能参与后续流程操作」。

**阶段 2：修改节点附件变更** `applyAttachmentChangesForModifySubmit`：仅 `approve && isModifyStep` 允许（否则抛「仅驳回待修改提交时允许修改附件」）；校验每条 `AttachmentChange.adjustLogId` 属于当前批次；`deleteAdjustLogAttachments`/`bindAttachments`(credit_report_hand/material_hand)/`copyReportAttachments`。

**阶段 3：处理当前步骤并推进** `processAdjustAudit`
- `buildProcessComment`：管理员代办他人步骤追加「（由管理员操作）」。
- `resolveStepStatusForProcess`：发起/修改节点 approve → `stepStatus='submit'`；否则 `stepStatus=processAction`。
- `editAdjustStepProcess`（乐观锁 `WHERE id=? AND step_status='pending'`，影响 0 行抛「已处理」）。
- `completeCurrentApprovalNodeIfNeeded`：`approval_strategy='all'`+approve 需 `queryPendingStepCountByNode===0` 才完成（否则返回「当前会签节点仍有待处理人员」）；抢占/未配置 → `editOtherPendingStepSkipped` 跳过同节点其他 pending。

**阶段 4：推进下一节点**
- `buildFlowSnapshot(flowNodeId)` 加载流程版本全量。
- `resolveProcessingNodeAuditStatus`（按**本次处理节点**+动作）：

| 节点语义 | approve | reject |
|---|---|---|
| 复核 | `10` | `11` |
| 修改 | `00` | `99` |
| 审批 | `20` | `21` |
| 自动审批 | `20` | — |

- `editAdjustLogAuditStatus`（按 `adjustBatchNo` 批量更新同批次 `audit_status` + `audit_time`）。

**阶段 5：终止分支** `isTerminalByCurrentNode`：reject 且当前是修改/审批节点 → `createTerminalEndStep` 沿 reject 路径插 `auto_process` 直到 end，返回「审批流程已结束」。

**阶段 6：推进下一可处理节点** `advanceToNextAvailableStep`：自动审批节点（label 含「自动审批/o32」）直插 `auto_process` 继续；approval 节点 `createPendingSteps` 创建 pending 返回；end 节点插 `auto_process`，`finished=true` → **`finishAdjustBatch(step)`**。

### 3.6 `finishAdjustBatch`（主体级特有：落地池状态 + 同步旗下债券 + 生成内部报告）

```
finishAdjustBatch(step):
  logList = queryAdjustLogListForAudit(step.adjustLogId, step.adjustBatchNo)
  editAdjustLogAuditStatus(..., '20')          // 整批置审批通过
  for log in logList:
    if log.adjustMode == '调入': addPoolStatus(log)
    elif log.adjustMode == '调出': deletePoolStatusSoft(log.securityCode, log.targetPoolId)
    syncCompanyBonds(log)                       // ★ 主体级特有：同步旗下全部债券
  generateInternalReportsOnFinish(logList)      // 手工信评报告附件沉淀为 rrs_report_in
```

`syncCompanyBonds`（与 `syncCompanyBondsOnDirect` 同构）：仅 `categoryType==='company'` 触发；查 `queryCompanyBondForAutoList(companyLog.securityCode)` 全部旗下债券；逐条判断是否已在/不在目标池，跳过冗余；`buildCompanyBondAutoLog`（`adjustType='自动调整'`、`auditStatus='20'`）→ `addAdjustLog` → 调入 `addPoolStatus` / 调出 `deletePoolStatusSoft`。

`generateInternalReportsOnFinish`：对每条调库记录查手工信评报告附件（`queryHandCreditReportAttachments`），有则新建 `rrs_report_in`（标题「证券全称+调入/调出+投资池全路径+报告」，`reportType` 按大类+方向映射 bond_in/out_report 等），复制附件。`companyCode` 字段在 `categoryType==='company'` 时取 `log.securityCode`（即主体代码）。

### 3.7 驳回/撤回对池状态影响

- 复核驳回 → `11`，不修改 `ip_pool_status`。
- 修改节点 reject（前端「终止流程」）→ `99`，`createTerminalEndStep`，不修改池状态。
- 审批节点驳回 → `21`，`createTerminalEndStep`，不修改池状态。
- **仅审批通过 `20` 才落地 `ip_pool_status` 并触发 `syncCompanyBonds`**。

---

## 4. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `forbiddenPoolAdjustFlow/submitAdjustAudit`（application/json） | `SecurityPoolAdjustAuditReq`：stepId, adjustLogId, adjustBatchNo, processAction(approve\|reject), processComment, handlerId, handlerName, attachmentChanges? | `SecurityPoolAdjustAuditDto` | 提交审批处理意见（纯文本，复用 security-pool 请求/返回结构） |
| `forbiddenPoolAdjustFlow/submitAdjustAuditWithFiles`（multipart/form-data） | `request`(JSON Blob) + `files`(MultipartFile[]) | `SecurityPoolAdjustAuditDto` | 修改节点提交时同时上传附件变更（multipart 入口；JSON 入口为 `submitAdjustAudit`） |
| `forbiddenPoolAdjust/queryCompanyPage` | 同 [15] | `PageResult<ForbiddenPoolAdjustDto>` | 列表页主体检索 |
| `forbiddenPoolAdjust/queryCompanyDetail` | companyCode | `ForbiddenPoolAdjustDto` | 主体详情 |
| `forbiddenPoolAdjust/queryAdjustPoolList` | companyCode, adjustDirection, currentUserId | `List<PoolDto>` | 可调入/调出池 |
| `forbiddenPoolAdjust/queryCompanyPoolStatus` | companyCode | `PoolStatusBundle` | 当前主体/旗下债券所在池 |
| `forbiddenPoolAdjust/queryAdjustLogList` | companyCode, adjustBatchNo | `List<AdjustLogDto>` | 调库记录（按批次过滤；不传仅返回未终结） |
| `forbiddenPoolAdjust/queryAdjustStepList` | adjustLogId, adjustBatchNo | `List<IpAdjustStepDto>` | 同批次流程步骤 |
| `forbiddenPoolAdjust/checkAdjust` | `ForbiddenPoolAdjustCheckReq` | `AdjustCheckDto` | next 模式校验确认 |
| `forbiddenPoolAdjust/addAdjustLog`（JSON）/ `addAdjustLogWithFiles`（multipart） | `ForbiddenPoolAdjustSubmitReq` | `ForbiddenPoolAdjustSubmitDto` | next 模式提交调库申请 |
| `myMatters/queryMyMattersPage` | flowIds, startDateStart/End, processDescription, auditStatus, stepStatus(pending\|completed), initiatorName, currentUserId, pageIndex, pageSize | `PageResult<MyMattersDto>` | 我的事宜分页（待办入口） |
| `myMatters/queryFlowOptionList` | `{ currentUserId }` | `List<FlowOptionDto>` | 我的事宜流程名称下拉 |
| `attachments/queryAttachmentList` | adjustLogId | 附件列表 | 加载调库记录附件 |

> 路径均带前缀 `/api/v1/`。`SecurityPoolAdjustAuditDto`：`{ adjustLogId, adjustBatchNo, stepId, auditStatus, finished, nextStepCreated, message }`。`SecurityPoolAdjustAuditReq.AttachmentChange`：`adjustLogId/creditReportFileIndexes/materialFileIndexes/creditReportSourceAttachmentIds/materialSourceAttachmentIds/deleteAttachmentIds`。

---

## 5. 关键数据库表

| 表 | 操作 | 关键字段 |
|---|---|---|
| `ip_adjust_step` | UPDATE（`editAdjustStepProcess` 乐观锁 / `editOtherPendingStepSkipped`）/ INSERT（`addAdjustStep` 创建下一步 pending / 终止分支 auto_process） | id, step_status, process_action, process_comment, process_time |
| `ip_adjust_log` | UPDATE（`editAdjustLogAuditStatus` 按 adjustBatchNo 批量更新 `audit_status`+`audit_time`）；INSERT（旗下债券自动调整记录） | adjust_batch_no, audit_status, adjust_type(自动调整) |
| `ip_pool_status` | INSERT（调入生效）/ UPDATE 软删（调出生效） | security_code, target_pool_id, pool_type, audit_status='20', is_deleted |
| `rrs_report_in` | INSERT（`generateInternalReportsOnFinish` 审批通过后沉淀手工信评报告） | report_title, report_type, security_code, company_code, data_source='uploaded' |
| `wf_flow_*` | 只读（构建 FlowSnapshot） | 流程定义/版本/节点/连线/审批配置/处理人/角色 |
| `sys_attachment` | 绑定/复制附件 | adjustLogId, attachment_category |

---

## 6. 状态流转

### 6.1 audit_status 流转（与证券池调库完全复用 -1/00/11/20/21/32/99）

```
[提交]
   ├─ 直通流程 → 20（立即入池/调出 + syncCompanyBonds）
   └─ 非直通 → 00
         ↓ 复核 approve → 10
         ├─ 复核 reject → 11（驳回待修改，发起人在修改节点 pending）
         │     ├─ 修改 approve（前端「提交」）→ 00（回复核）
         │     └─ 修改 reject（前端「终止流程」）→ 99（createTerminalEndStep）
         ├─ 审批 approve → 20（finishAdjustBatch 落地 + syncCompanyBonds + generateInternalReports）
         └─ 审批 reject → 21（createTerminalEndStep）
```

### 6.2 step_status 流转

`pending` ──approve──→ `approve`（审批节点）/ `submit`（发起/修改节点）；`──reject──→ reject`；抢占节点其他 pending `──skipped──→` 首位；start/end/auto/notify `──自动──→ auto_process`。

### 6.3 并发与防重

`editAdjustStepProcess` SQL `WHERE step_status='pending'` 乐观锁；会签每次查 `queryPendingStepCountByNode`；抢占首位即 `editOtherPendingStepSkipped`；同批次统一 `editAdjustLogAuditStatus`；整个 `submitAdjustAudit` 单一 `@Transactional`。

### 6.4 池状态影响

| 动作 | 影响 ip_pool_status | syncCompanyBonds |
|---|---|---|
| 提交（非直通） | 否 | 否 |
| 提交（直通） | 是 | 是（`syncCompanyBondsOnDirect`，[15]） |
| 复核通过/驳回/修改重新提交 | 否 | 否 |
| 审批通过（`finishAdjustBatch`） | 是 | **是（`syncCompanyBonds`，主体级特有）** |
| 审批驳回/修改节点终止 | 否 | 否 |

---

## 7. 与证券池调库审批的差异

| 维度 | 证券池调库审批 | 禁投池调整审批 |
|---|---|---|
| Controller 前缀 | `/api/v1/securityPoolAdjustFlow` | `/api/v1/forbiddenPoolAdjustFlow` |
| Service | `SecurityPoolAdjustFlowService` | `ForbiddenPoolAdjustFlowService`（**完整复制** security-pool flow 逻辑，操作 `forbiddenPoolAdjustMapper`） |
| 请求/返回实体 | `SecurityPoolAdjustAuditReq/Dto` | **直接复用** `SecurityPoolAdjustAuditReq/Dto`（无 forbidden 专属审批实体） |
| 详情加载接口 | `querySecurityDetail`/`querySecurityPoolStatus`/`queryAdjustLogList` | `queryCompanyDetail`/`queryCompanyPoolStatus`/`queryAdjustLogList`（companyCode 维度） |
| `finishAdjustBatch` 落地 | 仅落地单只证券 `ip_pool_status` + `generateInternalReportsOnFinish` | 落地主体 `ip_pool_status` 后**额外调 `syncCompanyBonds(log)`**：按 `issuer_code=companyCode` 查全部旗下债券，逐条写 `adjust_type='自动调整'` 的 `ip_adjust_log` + `ip_pool_status` |
| 前端入口参数 | `securityCode` | `companyCode` |
| 审批策略/节点语义识别/管理员代办 | — | **完全相同**（关键字、`ADMIN_USER_ID='1'` 一致） |

---

## 8. 验收标准

- 复核 approve → `10`，复核 reject → `11` 并在修改节点创建发起人 pending。
- 修改节点 approve（前端「提交」）→ `00` 回复核；修改节点 reject（前端「终止流程」）→ `99` 终止。
- 审批 approve → `20`，`finishAdjustBatch` 落地池状态 + 同步旗下债券 + 生成内部报告；审批 reject → `21` 终止。
- 会签节点需全部 approve 才推进；抢占节点首位处理即跳过其余。
- 发起人不得参与后续流程节点处理（`validateSubmitterCannotProcess`）。
- 仅 `audit_status='20'` 落地 `ip_pool_status`。

## 9. 关键源码索引

- 前端：`znty-rrs-ui/forbidden_pool_adjust_approve.html`（`initStandaloneReviewPage`、`restoreStandaloneAdjustDraft`、`currentPendingStep`、`isModifyAuditStage`、`submitAdjustAudit`、`submitAdjustAuditMultipart`、`buildAuditAttachmentChanges`、`flowStepSpanMethod`/`getFlowStepRowClass`）、`css/forbidden_pool_adjust_approve.css`
- 前端待办入口：`znty-rrs-ui/my_matters.html`（`openMatterPage` 拼 `companyCode/targetPoolId/adjustLogId/adjustBatchNo/entryMode`，pending→`process`→approve.html，completed→`view`→detail.html）
- Controller：`ForbiddenPoolAdjustFlowController.java`（`@RequestMapping("/api/v1/forbiddenPoolAdjustFlow")`，2 端点）
- Service：`ForbiddenPoolAdjustFlowService.java`（`submitAdjustAudit`/`validateAuditReq`/`resolveActualProcessStep`/`validatePendingStep`/`validateSubmitterCannotProcess`/`applyAttachmentChangesForModifySubmit`/`processAdjustAudit`/`resolveProcessingNodeAuditStatus`/`advanceToNextAvailableStep`/`createTerminalEndStep`/`finishAdjustBatch`/`syncCompanyBonds`/`buildCompanyBondAutoLog`/`generateInternalReportsOnFinish`/`resolveReportType`/`buildFlowSnapshot`，`ADMIN_USER_ID='1'`）
- Mapper：复用 `ForbiddenPoolAdjustMapper.java` / `.xml`（审批用 `queryAdjustStepById`/`editAdjustStepProcess`/`editOtherPendingStepSkipped`/`queryPendingStepCountByNode`/`queryAdjustLogListForAudit`/`editAdjustLogAuditStatus`/`addAdjustStep`/`addPoolStatus`/`deletePoolStatusSoft`/`queryCompanyBondForAutoList`/`querySecurityCurrentPoolIdList`/`querySecurityBoByCode`/`queryCategoryTypeBySecurityType`）
- 复用实体：`entity/securitypooladjustflow/SecurityPoolAdjustAuditReq/Dto`、`entity/bo/`（`IpAdjustStepBo`/`IpAdjustLogBo`/`FlowSnapshot`/`FlowNodeBo`/`FlowEdgeBo`/`NodeApprovalConfigBo`/`NodeApprovalHandlerBo`/`SysAttachmentBo`/`ReportInBo`/`SecurityInfoBo`）
- SQL：同 [15]（`rrs_external_import_schema.sql` + `rrs_security_pool_adjust_schema.sql` + `rrs_flow_definition_schema.sql`）
- 测试：`ForbiddenPoolAdjustFlowServiceTest.java`
