# 证券池调库详情需求说明

> 前端页面：`security_pool_adjust_detail.html`（列表 + 详情两视图）
> 后端前缀：`/api/v1/securityPoolAdjust`（查询/校验/提交）、`/api/v1/securityPoolAdjustFlow`（修改节点重新提交）
> 角色定位：申请人、审批人和审计人员查看单只证券及其调库批次的完整业务上下文（池状态、历史记录、流程步骤），并可对未终结流程执行后续动作。

---

## 0. 重要现状说明（与"修改节点重新提交"的真实归属）

经逐行读源码确认，详情页 `security_pool_adjust_detail.html` **只区分两种有效模式**：`view`（只读）与 `adjust`（首次调库提交）。URL 传入 `entryMode='process'` 会被归一化为 `view`。

- 详情页**没有** `audit_status='11'` 识别逻辑、**没有调用** `/api/v1/securityPoolAdjustFlow/submitAdjustAudit`。
- `adjust` 模式的提交走 `addAdjustLog`（创建全新记录，初始状态 `00`/`20`），是**首次调库申请本身**，不是修改节点重新提交。
- **真正的「修改节点重新提交」（audit_status='11'）发生在 `security_pool_adjust_approve.html`**，通过 `submitAdjustAudit` 完成（详见 [05-security-pool-adjust-approve.md](05-security-pool-adjust-approve.md)）。
- `addAdjustLog` 永远是创建新 `ip_adjust_log` 记录，不会更新已存在的 `11` 记录。

---

## 1. 页面概览

单文件 Vue2 + ElementUI 应用，根容器 `#security_pool_adjust_detail`，内部两个互斥页面由 `currentPage` 控制：
- **list 页**：证券搜索 + 分页表格，点「调库」按钮内嵌跳到 detail 页（`handleAdjust`）。
- **detail 页**：调库详情主体，含 7 个 `section-card` 区块，CSS `order` 控制顺序：

| order | 区块 | 标题 | 显示条件 |
|---|---|---|---|
| 1 | section-bond-info | 证券基本信息 | 始终 |
| 2 | section-current-pools | 当前所在池 | 始终 |
| 3 | section-adjust-log | 调库记录 | `isViewMode` |
| 4 | section-flow-status | 当前流程状态 | `isViewMode` |
| 5 | section-adjust-op | 调库操作（步骤1选池） | `isAdjustMode && adjustStep===1` |
| 6 | section-adjust-review | 调库校验结果（步骤2） | `adjustStep===2` |
| 7 | section-reason-advice | 原因和建议（步骤2） | `adjustStep===2` |

顶栏含返回按钮 + 标题（`pageTitle`：adjust 模式显示「证券池调库」，否则「证券详情」）+ 证券代码/简称徽标。

### 1.1 entryMode 模式区别

| 模式 | 来源 | 页面表现 |
|---|---|---|
| `view` | URL `entryMode=view/省略/process`，或从 `forbidden_pool_query.html`、`security_pool_adjust_history.html`、`security_pool_query.html` 跳入（均传 `entryMode=view`） | 只读：显示证券信息、当前池、调库记录、流程步骤；隐藏调库操作区 |
| `adjust` | URL `entryMode=adjust`，或 list 页「调库」按钮 `handleAdjust` | 可调整：隐藏调库记录/流程步骤区，显示调库操作区（步骤1选池→步骤2校验→提交） |
| `process` | URL `entryMode=process` | **在详情页中等同 view**（归一化）；真正的 process 处理在 `approve.html` |

### 1.2 初始化加载接口（并发）

`created` 设 baseURL，调 `initStandaloneDetailPage`：
1. 从 URL 取 `securityCode`/`windCode`、`targetPoolId`、`adjustLogId`/`adjust_log_id`、`adjustBatchNo`/`adjust_batch_no`、`entryMode`；`adjustStep=1`。
2. 调 `loadDetailData(securityCode)`，用 `Promise.all` **并发**5 个接口：
   - `querySecurityDetail` `{ securityCode }` → 证券基本信息
   - `queryAdjustPoolList` `{ securityCode, adjustDirection:'in', currentUserId }` → 可调入池
   - `queryAdjustPoolList` `{ securityCode, adjustDirection:'out', currentUserId }` → 可调出池
   - `querySecurityPoolStatus` `{ securityCode }` → 当前所在池（证券+主体）
   - `queryAdjustLogList` `{ securityCode, adjustBatchNo }` → 调库记录
3. 并发完成后**串行**：组装调入/调出树（`buildPoolTree`）、过滤已在池、构建互斥映射、`loadLogAttachments`（加载每条记录附件）、`loadFlowSteps`（加载流程步骤）。

---

## 2. 各模式逻辑

### 2.1 view 只读模式

- `isViewMode=true`、`isSecurityInfoReadonly=true`（所有证券字段 `el-input :disabled`）。
- 展示：证券基本信息（`el-descriptions` 3 列）、当前所在池、调库记录表、当前流程状态表。
- 按钮：仅顶部「返回」（`history.back()` 或跳 `security_pool_adjust.html`）。调库记录/流程区内**无**操作按钮（`showLogUploadActions=false`，信评报告/其他材料的「选择报告」「上传附件」按钮均隐藏）。
- 已结束记录（`audit_status` ∈ `20/21/99/-1`）同样以 view 模式只读展示，无任何提交入口。

### 2.2 adjust 可调整模式（首次调库提交）

- 入口：URL `entryMode=adjust`，或 list 页「调库」按钮。
- `isAdjustMode=true`，隐藏调库记录区与流程状态区，显示调库操作区（步骤1）。
- 步骤1：左右双栏——「可调入库」（绿）+「可调出库」（红），均为树形 `el-table`，叶子节点可勾选。勾选时做互斥校验（`handleInPoolSelect`/`handleOutPoolSelect`，同面板+跨面板互斥）。每池可挂信评报告/其他材料附件。
- 点「下一步」（`goToStep2`）：调 `checkAdjust` 校验 → 进入步骤2，展示校验结果表 + 原因建议。
- 点「提交」（`handleSubmit`）：打开流程选择弹窗，为每个手工项选流程后 `confirmFlowSelection` → `submitAdjustLog` → `addAdjustLogWithFiles`（multipart）→ 成功后 `backToList`。

### 2.3 修改节点重新提交（实际在 approve.html，非详情页）

> 详情页**不实现**此流程。以下为 `approve.html` + `SecurityPoolAdjustFlowService` 的真实实现，供对照。

- 识别条件（`approve.html` `isModifyAuditStage`）：`activeLog.auditStatus === '11'` **或** 当前待处理步骤 `nodeLabel` 含「修改」。
- 提交（`approve.html` `submitAdjustAudit`）：`processAction='approve'`（修改节点语义为「提交」）；`reject` 语义为「终止流程」。当 `shouldSubmitAuditAttachments()` 为真（process 模式 + 修改阶段 + approve + 有待处理步骤）时，以 multipart 提交，`payload.attachmentChanges = buildAuditAttachmentChanges(submitFiles)`。调 `/api/v1/securityPoolAdjustFlow/submitAdjustAudit`。
- 后端：`validateAuditReq` → `queryAdjustStepById` → `resolveActualProcessStep`（管理员代办）→ `validatePendingStep`（必须 pending）→ `validateSubmitterCannotProcess`（发起人不可处理后续节点，修改/发起节点除外）；`applyAttachmentChangesForModifySubmit`（仅 approve + isModifyStep 时允许附件变更）；`processAdjustAudit`（修改节点+approve → `stepStatus='submit'`）→ 推进流程；`resolveProcessingNodeAuditStatus`（修改节点+approve → `audit_status='00'`，回到流程中）。

**与「checkAdjust + addAdjustLog」的差异**：修改节点重新提交**不重新校验、不新建记录**，而是更新现有 `ip_adjust_step` 并把同批次 `ip_adjust_log` 状态从 `11` 改回 `00`，附件变更走 `attachmentChanges`。

---

## 3. 页面显示内容逻辑

### 3.1 证券基本信息（section-bond-info）

`el-descriptions :column="3" border`，字段全部 `el-input`/`el-date-picker`，`:disabled="isSecurityInfoReadonly"`。共 29 项：证券全称/简称/代码、发行人、银行间/沪/深/北交所代码、发行总额、当期利率、行权剩余期限、起息/到期日、质押比率、评级机构/证券评级/主体评级/展望评级、担保情况、主承销商、主体内评分档、证券类型、回售/赎回/含权债剩余期限、担保人主体内评分、证券期限、募集资金用途/提示原因/证券分析（3 个 textarea，span=3）。数据来自 `querySecurityDetail` → `bondDetail`。

### 3.2 当前所在池（section-current-pools）

两个子区块：
- **当前证券所在池**（`securityCurrentPools`，来自 `querySecurityPoolStatus`）：折叠态显示池名 chip + 数量；展开态表格列：序号/投资池名称/池类型（`poolTypeLabel`）/入池日期。后端取 `audit_status='20'` 且叶子节点（`NOT EXISTS child`），池名填全路径（`fillPoolStatusFullName`）。
- **当前证券主体所在池**（`issuerCurrentPools`，绿色 accent）：表格多一列「主体在该池证券数」（`bondCount`）和「最早入池日期」。SQL 按发行人关联、`GROUP BY` 池、`COUNT(DISTINCT security_code)`。

### 3.3 调库记录表（section-adjust-log，仅 view 模式）

`adjustLogList` 来自 `queryAdjustLogList`。列：序号/投资池名称（`poolPath` 全路径）/调整类型（`adjustType`）/调整方向（`adjustMode` 调入绿/调出红 tag）/审批流程（`flowType` tag + `flowName`）/信评报告/其他材料/调整说明（`adjustmentNote`）/审核状态（`auditStatus`，`getStatusStyle`+`auditStatusLabel`）。

- 信评报告/其他材料列：展示已绑定附件（`row.attachmentFiles`/`materialFiles`，`loadLogAttachments` 按 category 拆分：`credit_report_*`/`material_*`），点击下载（`downloadAttachment`）。adjust 模式下额外显示「选择报告/上传附件」按钮。
- SQL：按 `securityCode`，若传 `adjustBatchNo` 则按批次过滤；否则**排除终态** `audit_status NOT IN ('-1','20','21','99')`，即只展示进行中的记录。`ORDER BY crte_time DESC`。

### 3.4 流程步骤表（section-flow-status，仅 view 模式）

`flowStepList` 来自 `queryAdjustStepList`（`loadFlowSteps`）。列：序号/步骤名称（`nodeLabel`）/步骤结果（`stepStatus`，`stepStatusLabel`：pending待处理/approve通过/reject驳回/submit提交/auto_process自动处理/canceled已撤回）/开始时间/处理人（`handlerName`，当前用户高亮 `isCurrentHandler`）/处理结果（`processAction`，`processActionLabel`）/处理时间/处理意见。

- **行合并逻辑**（`flowStepSpanMethod`）：仅前 3 列合并。列0 按 `nodeLabel`；列1 按 `nodeLabel|stepStatus`；列2 按 `nodeLabel|stepStatus|startTime`。连续相同 key 合并，首行 `rowspan=span`，后续 `rowspan=0`。
- **行样式**（`getFlowStepRowClass`）：按节点类型/关键字识别角色（terminal/auto/initiator/review/modify/approve/default），叠加状态类（pending-current/pending-other/auto/rejected/approved/ended），左侧 accent 边框着色。
- **定位主流程**：`loadFlowSteps` 优先用 `selectedAdjustLogId` 定位记录；`uniqueAdjustLogsByFlow` 按批次号去重（`batch:X`）或按记录（`log:id`），同批次只查一次步骤；优先选含 `pending` 步骤的记录为 `activeAdjustLog`，否则取首条 fallback。

### 3.5 附件展示与下载

- 调库记录附件：`loadLogAttachments` 对每条 log 调 `/api/v1/attachments/queryAttachmentList { adjustLogId }`，按 `attachmentCategory` 拆为信评报告（`credit_report_hand/in/out`）与其他材料（`material_hand/in/out`）。
- 下载：`downloadAttachment` `POST /api/v1/attachments/downloadAttachment { id }` 返回 `ApiResponse<String>`（Base64），前端解码为 Blob 后 `URL.createObjectURL` + `<a download>`。本地待提交文件用 `downloadLocalFile`。
- 信评报告选择弹窗：内/外报告 Tab，分页查询 `/api/v1/reports/queryInReportPage`、`/api/v1/reports/queryOutReportPage`，支持标题/证券编码/类型/撰写日期筛选，选中后回填到 `creditReportSelections`/`otherMaterialSelections`。

---

## 4. 重新提交逻辑（核心）

### 4.1 详情页 adjust 模式的「提交」（首次申请，非重新提交）

详情页 `adjust` 模式是**首次调库申请**，不识别 `audit_status='11'`：

1. **允许提交的状态**：无显式状态判断；只要用户在步骤1选了池即可。后端 `checkAdjust` 会前置校验「证券是否已到期」和「是否存在进行中的调库流程」（`preCheckPendingProcess`，有 pending 步骤则拒绝），从而间接阻止对已有进行中流程的证券再次提交。
2. **调用顺序**：
   - 步骤1→步骤2：`goToStep2` 调 `checkAdjust`（`POST /api/v1/securityPoolAdjust/checkAdjust`），请求体 `{ securityCode, securityShortName, securityType, items:[{targetPoolId,targetPoolName,poolType,adjustMode}] }`。
   - 步骤2→提交：`handleSubmit`→`confirmFlowSelection`→`submitAdjustLog` 调 `addAdjustLogWithFiles`（`POST /api/v1/securityPoolAdjust/addAdjustLogWithFiles`，multipart）。请求体 `payload`：`securityCode/securityShortName/securityType/adjustType:'手工调整'/adjustReason/adjustAdvice/securityInfo(全量字段)/adjusterId/adjusterName/items:[{targetPoolId,targetPoolName,poolType,adjustMode,itemTag,adjustGroupKey,flowId,flowKey,flowType,adjustmentNote,creditReportFileIndexes,materialFileIndexes}]`，附件通过 `files` 字段追加，下标由 `collectSubmitFiles` 回填。
3. **与首次申请差异**：无——详情页 adjust 模式即首次申请本身。

### 4.2 真正的「修改节点重新提交」（approve.html，audit_status='11'）

1. **允许状态**：`audit_status='11'`（驳回待修改）且当前有待处理步骤落在「修改」节点。
2. **调用顺序**：`submitAdjustAudit`（approve.html）→ `POST /api/v1/securityPoolAdjustFlow/submitAdjustAudit`（无附件变更）或 `POST /api/v1/securityPoolAdjustFlow/submitAdjustAuditWithFiles`（有附件变更，multipart）。请求体：`{ adjustLogId, adjustBatchNo, stepId, processAction:'approve', processComment, handlerId, handlerName, attachmentChanges? }`。有附件变更时走 multipart，`attachmentChanges` 每项含 `{ adjustLogId, creditReportFileIndexes, materialFileIndexes, creditReportSourceAttachmentIds, materialSourceAttachmentIds, deleteAttachmentIds }`。
3. **与首次申请差异**：
   - 不调 `checkAdjust`（不重新校验池可行性）；
   - 不调 `addAdjustLog`（不新建 `ip_adjust_log`），而是更新现有 `ip_adjust_step`（`editAdjustStepProcess`）并把同批次 `ip_adjust_log.audit_status` 从 `11` 改回 `00`；
   - 附件通过 `attachmentChanges` 增量变更（删除/绑定本地上传/复制报告库），而非随新建记录一起写入；
   - 后端 `applyAttachmentChangesForModifySubmit` 严格校验：仅 `approve` + `isModifyStep` 允许，且 `attachmentChanges.adjustLogId` 必须属于当前批次。

---

## 5. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `securityPoolAdjust/querySecurityPage` | securityCode, securityShortName, issuer, pageIndex, pageSize | `PageResult<SecurityInfoDto>` | 列表页分页查询证券 |
| `securityPoolAdjust/querySecurityDetail` | securityCode | `SecurityInfoDetailDto`（29+证券字段） | 详情页顶部证券基本信息 |
| `securityPoolAdjust/queryAdjustPoolList` | securityCode, adjustDirection(in/out), currentUserId | `List<PoolDto>`（含互斥关系，树由前端组装） | 可调入/调出投资池 |
| `securityPoolAdjust/querySecurityPoolStatus` | securityCode | `SecurityPoolStatusDto`（securityCurrentPools + issuerCurrentPools） | 当前证券所在池 + 主体所在池 |
| `securityPoolAdjust/queryAdjustLogList` | securityCode, adjustBatchNo? | `List<AdjustLogDto>` | 调库记录（无批次时排除终态） |
| `securityPoolAdjust/checkAdjust` | securityCode, securityShortName, securityType, items[{targetPoolId,targetPoolName,poolType,adjustMode}] | `AdjustCheckDto` | 调库可行性预校验 + 流程候选 |
| `securityPoolAdjust/addAdjustLog`（application/json） | `SecurityPoolAdjustSubmitReq` | `AdjustSubmitDto` | 提交调库申请（无附件，新建记录，状态 00/20） |
| `securityPoolAdjust/addAdjustLogWithFiles`（multipart/form-data） | `request`(JSON Blob) + `files`(MultipartFile[]) | `AdjustSubmitDto` | 提交调库申请（带附件，新建记录，状态 00/20） |
| `securityPoolAdjust/queryAdjustStepList` | adjustLogId, adjustBatchNo | `List<IpAdjustStepDto>` | 流程步骤列表（批次号优先） |
| `securityPoolAdjustFlow/submitAdjustAudit`（application/json） | adjustLogId, adjustBatchNo, stepId, processAction(approve/reject), processComment, handlerId, handlerName, attachmentChanges? | `SecurityPoolAdjustAuditDto` | 审批/修改节点重新提交（无附件变更） |
| `securityPoolAdjustFlow/submitAdjustAuditWithFiles`（multipart/form-data） | `request`(JSON Blob) + `files`(MultipartFile[]) | `SecurityPoolAdjustAuditDto` | 审批/修改节点重新提交（带附件变更，approve.html 用） |
| `attachments/queryAttachmentList` | adjustLogId | 附件列表 | 加载调库记录附件 |
| `attachments/downloadAttachment` | id | `ApiResponse<String>`（Base64） | 下载附件 |
| `reports/queryInReportPage` / `queryOutReportPage` | pageIndex, pageSize, reportTitle, securityCode, reportType, crteTimeStart/End | PageResult | 信评报告弹窗内/外报告查询 |

> 路径均带前缀 `/api/v1/`。

---

## 6. 关键校验

1. **adjustLogId 或批次号定位有效主流程**：
   - 前端 `loadFlowSteps` 优先用 URL `adjustLogId` 精确定位，否则用全量 `adjustLogList`；`uniqueAdjustLogsByFlow` 按 `batch:批次号` 或 `log:记录ID` 去重。
   - 后端 `queryAdjustStepByBatchList`：`adjustBatchNo` 非空时按批次查（覆盖整批步骤），否则按 `adjustLogId` 查。
   - 后端 `queryAdjustLogListForAudit`：审批时按 `(adjustLogId, adjustBatchNo)` 查同批次全部记录，确保附件变更/状态更新作用于整批。

2. **从属记录通过批次共享主流程步骤，不重复展示**：
   - 同一批次（`adjustBatchNo`）多条 `ip_adjust_log`（手工项 + 联动/互斥自动项）共用一组 `ip_adjust_step`（步骤按 `adjust_batch_no` 关联）。
   - 前端 `uniqueAdjustLogsByFlow` 保证同批次只调一次 `queryAdjustStepList`，流程步骤表只渲染一份。
   - 提交时 `adjustGroupKey` 让同组手工+联动+互斥项共用一个批次号。

3. **终态只读，重新提交须重新校验**：
   - 详情页 view 模式（含 process 归一化）对终态（`20/21/99/-1`）记录无任何操作入口，`showLogUploadActions=false`。
   - `queryAdjustLogList` 无批次参数时排除终态，列表只显示进行中记录。
   - 首次申请 `checkAdjust` 的 `preCheckPendingProcess` 阻止对存在 pending 流程的证券再次发起；`preCheckSecurityExpired` 阻止已到期证券调库。
   - 修改节点重新提交（approve.html）虽不调 `checkAdjust`，但后端 `validatePendingStep` 强制步骤必须 pending，`validateSubmitterCannotProcess` 强制发起人只能在「发起/修改」语义节点操作，管理员例外。

## 7. 与 approve.html 的区别

| 维度 | security_pool_adjust_detail.html | security_pool_adjust_approve.html |
|---|---|---|
| 默认 entryMode | `view`，URL `adjust` 切换 | `process`，URL `next` 切换 |
| 有效模式 | view（只读）/ adjust（首次调库提交） | process（审批处理）/ next（校验确认） |
| `process` 处理 | 归一化为 view，**无处理能力** | 真正的审批处理入口 |
| 调库操作区 | 有（adjust 模式步骤1选池） | 有（next 模式步骤2校验） |
| 调库记录/流程步骤区 | 仅 view 模式显示 | 始终显示（含审批区 order 7） |
| 审批区 | **无** | 有（auditAction approve/reject、auditComment、提交按钮） |
| 识别 `audit_status='11'` | 否 | 是（`isModifyAuditStage`，改按钮文案为「提交」/「终止流程」） |
| 提交接口 | `addAdjustLog`（新建记录） | `submitAdjustAudit`（更新步骤/状态） |
| 附件变更 | 随新建记录一次性上传 | `attachmentChanges` 增量，仅修改阶段+approve |
| 管理员代办 | 不涉及 | `resolveActualProcessStep` + `buildProcessComment` 追加「（由管理员操作）」 |
| 流程推进 | 不涉及（由 `addAdjustLog` 内 `createInitialSteps` 创建前 3 步） | `processAdjustAudit` 推进：会签/抢占、自动节点直通、终止分支、`finishAdjustBatch` 落地池状态 |

## 8. 验收标准

- 详情中的日志状态、步骤状态和最终池状态相互一致。
- 待处理节点高亮，终态只读，异常数据给出明确提示。
- adjust 模式首次提交流程闭环；view 模式附件可下载。
- `SecurityPoolAdjustDetailApiTest` 覆盖详情加载与首次提交业务线。

## 9. 关键源码索引

- 前端：`znty-rrs-ui/security_pool_adjust_detail.html`（entryMode 归一化、模式计算属性、并发加载、流程步骤定位/去重/合并、首次提交 `submitAdjustLog`）
- dict.js：`DICT_AUDIT_STATUS`、`DICT_ADJUST_MODE`、`DICT_ADJUST_TYPE`、`DICT_POOL_TYPE`
- Controller：`SecurityPoolAdjustController.java`、`SecurityPoolAdjustFlowController.java`
- Service：`SecurityPoolAdjustService.java`（查询/校验/提交/`createInitialSteps`）、`SecurityPoolAdjustFlowService.java`（修改节点重新提交/`resolveProcessingNodeAuditStatus`/`applyAttachmentChangesForModifySubmit`）
- Mapper：`SecurityPoolAdjustMapper.xml`
- SQL：`sql/rrs_security_pool_adjust_schema.sql`
