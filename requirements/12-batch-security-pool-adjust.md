# 证券池批量调整（批量调入 / 批量调出）需求说明

> 前端页面：`batch_security_pool_adjust.html`（主页面，自包含两步式工作台）、`batch_security_pool_adjust_select.html`（独立选择屏，**当前未与主页打通**）
> 后端前缀：`/api/v1/batchSecurityPoolAdjust`
> 角色定位：业务人员选定一个目标投资池与方向后，批量勾选多只证券一次性发起调入/调出申请。

---

## 0. 重要现状说明（两个页面的关系）

- **`batch_security_pool_adjust.html`（主页面）** 是自包含的完整两步式工作台：内部嵌入「可选证券」表格（步骤 1），勾选后点「下一步」进入步骤 2 校验结果，再点「提交」完成批量调库。**它从不跳转到选择页**。
- **`batch_security_pool_adjust_select.html`（选择页）** 是独立的选择屏，通过 URL 参数（`poolId/poolName/poolType/direction`）接收上下文，但其「确认已选证券」按钮（`confirmSelection`）仅 `$alert` 弹出所选证券名称，`goBack` 直接 `window.location.href` 硬跳回主页面——**没有任何把选中结果回传主页面**的机制（无 localStorage / sessionStorage / URL 回传 / postMessage），且该页面报告选择用的是**前端 mock 数据**（`initMockReportData`）。

因此下文以**主页面**为主线描述完整业务链路，选择页的差异单列。**需求缺口**：若要求两页真正联动，需补充「选择页确认后通过 sessionStorage/postMessage 把 `selectedSecurityMap` 回传主页」的机制。

---

## 1. 页面概览（主页面）

**用途**：证券池批量调整入口，承载「投资池列表 → 选池 → 选证券/材料 → 校验 → 提交」全流程，单页两视图（`pageMode`）。

**布局**：
- 顶栏：标题「证券池批量调整」+ 系统名 + 当日日期。
- 视图 A `pageMode === 'poolList'`：投资池筛选面板（投资池树多选 popover + 查询/重置）+ 投资池表格（序号/全路径名/描述/投资市场/投资品种/上限数量/现有数量/操作[调入|调出]）+ 分页。
- 视图 B `pageMode === 'adjustWorkbench'`：工作台头（返回 + 池名 + 方向标签 + 汇总 chips：证券/信评报告/其他附件/步骤 x/2）。
  - 步骤 1（`adjustStep === 1`）：「调整材料」折叠卡（信评报告、其他附件、是否放开规则、已选证券）+「可选证券」候选表格（跨页多选）。
  - 步骤 2（`adjustStep === 2`）：「调库校验结果」表格（逐条展示可/不可调整 + 行内审批流程下拉 + 材料计数 + 调整说明）+「原因和建议」（调整原因/调整建议 textarea）+ 操作底栏（上一步/提交）。
- 报告选择弹窗：内部报告/外部报告双 Tab，带类型快筛、关键字/证券编码/撰写日期范围筛选、分页、附件下载、已选汇总。

**初始化**（`mounted → initPage`）：
1. `axios.defaults.baseURL = 'http://localhost:18090'`
2. `apiPost('/api/v1/common/queryPoolTreeList', {})` → 构建投资池筛选树 `poolTreeData`（`buildPoolTree`）。
3. `loadPoolPage()` → `apiPost('/api/v1/batchSecurityPoolAdjust/queryPoolPage', {...})`。

---

## 2. 批量选择证券逻辑

### 2.1 候选证券筛选条件（主页面内嵌表）

| 控件 | 字段 | 说明 |
|---|---|---|
| 文本输入 | `securityCode` | 证券代码 |
| 文本输入 | `securityShortName` | 证券简称 |
| 多选 | `marketCodes` | 市场（SH/SZ/IB/BJ/NBC） |

查询接口：`POST /api/v1/batchSecurityPoolAdjust/querySecurityPage`（`loadSecurityList`），请求体：
```json
{ "currentUserId": "1001", "poolId": <Long>, "direction": "in|out",
  "securityCode": null, "securityShortName": null, "marketCodes": null,
  "pageIndex": 1, "pageSize": 20 }
```
返回 `{ records:[...], total, pageIndex, pageSize }`，`records` 为 `BatchSecurityCandidateDto`（securityCode/securityShortName/securityType/marketCodes/issuer/ratingBond/maturityDate）。
候选证券固定排除 `security_type IN ('crmw','company')` 的 CRMW 凭证和公司主体，避免混入证券池批量调整。

> 选择页 `batch_security_pool_adjust_select.html` 的筛选条件与查询接口一致（路径同为 `querySecurityPage`），区别仅在独立屏布局与 mock 报告数据。

### 2.2 候选证券方向过滤（后端）

- `direction='in'`：返回**不在目标池中**的证券（可调入候选）。
- `direction='out'`：返回**在目标池中**（`ip_pool_status.audit_status='20' and is_deleted=0`）的证券（可调出候选）。

### 2.3 跨页多选机制

- 表格 `type="selection"` + `row-key="securityCode"` + `:reserve-selection="true"`，但实际**未依赖** reserve-selection，而是用 `selectedSecurityMap`（`{securityCode: row}`）做跨页缓存。
- `handleSecuritySelectionChange(selection)`：每次当前页勾选变化，把当前页未勾选的从 map 删除、勾选的写入 map。
- `restoreCurrentPageSelection()`：翻页数据回来后按 map 恢复当前页勾选状态（`toggleRowSelection`），用 `selectionSyncing` 标记避免回调死循环。
- 移除单只 `removeSelectedSecurity`、清空 `clearSelectedSecurities`。

### 2.4 选中结果如何「回传」

- **主页面**：不需要回传，`selectedSecurityMap` 直接在同一 Vue 实例内被步骤 1「下一步」和步骤 2「提交」消费。
- **选择页**：`confirmSelection()` 只 `$alert` 弹出名称列表，**无回传机制**（功能缺口，见 §0）。

---

## 3. 批量调入 / 调出 逻辑

### 3.1 主页面操作步骤

1. 投资池列表点「调入」/「调出」→ `openAdjust(row, direction)`：记录 `currentPool`、`direction`，`resetWorkbench()`，`pageMode='adjustWorkbench'`，`loadSecurityList()`。
2. 步骤 1：在候选证券表勾选证券（跨页）；可选填调整材料（信评报告/其他附件，支持从报告库选 + 本地上传；是否放开规则 `releaseRules` yes/no）。
3. 点「下一步」→ `goToStep2()`：调用**校验接口**，渲染校验结果表，`adjustStep=2`。
4. 步骤 2：逐行查看校验结果，为可调整行选择审批流程（行内 `el-select`），填写调整原因/建议。
5. 点「提交」→ `submitAdjust()`：调用**提交接口** `addAdjustLogWithFiles`（multipart），成功后 `backToPoolList()` + `initPage()` 刷新。

### 3.2 校验接口（`checkAdjust`）

**前端调用**（`goToStep2`）：
```
POST /api/v1/batchSecurityPoolAdjust/checkAdjust
{
  currentUserId, direction, poolId, poolName, poolType,
  securities: [{ securityCode, securityShortName, securityType }, ...]
}
```

**后端处理**（`BatchSecurityPoolAdjustService.checkAdjust`）：
1. `validateAdjustCheckReq`：poolId 非空、direction ∈ {in,out}、securities 非空、每只证券 securityCode 非空、目标池为启用叶子池（`queryEnabledLeafPoolCount`）。
2. `validateAdjustPoolPermission`：管理员(1001)放行；否则校验当前用户对 poolId 拥有 adjustable 权限。
3. `resolveAdjustMode`：`in→调入`、`out→调出`。
4. **逐只证券循环**：对每个 `SecurityItem` 构造单证券校验请求 `buildSingleCheckReq`（目标池=当前 poolId，方向=中文），调用**单笔调库服务** `securityPoolAdjustService.checkAdjust(singleReq)`。
5. 单笔校验返回 `AdjustCheckDto.items`（可能含手工项 + 联动/互斥自动项），**过滤出 adjustMode 等于本次方向的项**，用 `buildBatchCheckResult` 转成批量结果项：
   - `adjustGroupKey` 被改写为 `securityCode + "_" + 原始groupKey`（保证不同证券的同名分组不冲突）。
   - 透传 `canAdjust`、`failReasons`、`flowOptions`。

**返回结构** `BatchSecurityInboundAdjustDto`：
```json
{ "items": [
  { securityCode, securityShortName, securityType, targetPoolId, poolName, poolType,
    adjustMode, itemTag, adjustGroupKey, canAdjust, failReasons:[...], flowOptions:[...] }
] }
```

**前端逐条展示**（`buildReviewRow` + 校验结果表）：
- 每行：证券简称/代码、投资池名、调整类型（manual→手工调整 / linkage→联动调整 / mutex→互斥调整）、方向（调入 success/调出 danger）、**审批流程下拉**（仅 `row.valid` 才显示）、材料计数、调整说明（`buildAdjustNote`：`{手工/联动/互斥}{调入/调出}`，失败则拼「失败：原因」）、校验结果（绿点「可调整」/红点「不可调整」）。
- 行内流程候选项 `formatRowFlowOptions`：生成唯一 `optionKey`，标记 `recommended/selectable/matched`；默认选中 recommended 且 selectable 的第一个。
- `handleRowFlowChange`：选中后写回 `row.flowId/flowKey/flowType/flowName`。
- 计算属性 `validCount`（仅 valid 且方向匹配的行）、`allValidRowsHaveFlow`（所有可提交行均已选流程）控制提交按钮可用性。

### 3.3 提交接口（`addAdjustLog`）

**前端 `submitAdjust`**：
1. 前置校验：`validCount===0` 提示无可提交项；`!allValidRowsHaveFlow` 提示需选流程。
2. 收集附件：`collectSubmitFiles` 把 `creditReportFiles`/`materialFiles` 合并到一个 `submitFiles` 数组并返回各自下标；`collectReportAttachmentIds` 从已选报告库报告的 `attachments` 中提取附件 ID。
3. 构造 `payload`：
   ```json
   {
     currentUserId, direction, adjusterId, adjusterName,
     poolId, poolName, poolType, adjustReason, adjustAdvice,
     items: [  // 仅 valid 且 direction 匹配的行
       { securityCode, securityShortName, securityType,
         targetPoolId, targetPoolName, poolType, adjustMode, itemTag, adjustGroupKey,
         flowId, flowKey, flowType, adjustmentNote,
         creditReportFileIndexes, materialFileIndexes,
         creditReportSourceAttachmentIds, materialSourceAttachmentIds }
     ]
   }
   ```
   注意：**附件下标和报告库附件 ID 是「整批共享」的**（每个 item 都带相同的 `creditReportFileIndexes` 等），由后端按需绑定到每条调库日志。
4. `submitAdjustMultipart`：`FormData`，`request` 字段为 JSON Blob，`files` 字段为多文件；`POST /api/v1/batchSecurityPoolAdjust/addAdjustLogWithFiles`（multipart；JSON 无附件入口为 `addAdjustLog`）。
5. 成功提示并返回投资池列表刷新。

**后端 `addAdjustLog(req, files)`**：
1. `@Transactional(rollbackFor = Exception.class)`——**整个批量提交是一个事务**。
2. `validateAdjustSubmitReq`：poolId/direction/items/adjusterId/adjusterName 非空；目标池为启用叶子池；每条 item 的 securityCode 非空、`adjustMode` 必须等于本次方向、`targetPoolId` 非空、`flowId` 或 `flowKey` 至少有一个。
3. `validateAdjustPoolPermission`：权限校验同校验阶段。
4. **按 securityCode 分组**：`itemMap = {securityCode: [AdjustItem...]}`。
5. `sysAttachmentService.createSubmissionFiles(files, adjusterId)`：把 multipart 文件包成 `SubmissionFiles`（含校验、缓存），**整批共用一份物理文件**。
6. `securityPoolAdjustService.createBatchNoContext()`：创建**批次号上下文** `BatchNoContext`（含 `batchTimeText` yyyyMMddHHmmss 和三个序号：inboundBatchSeq/outboundBatchSeq/noFlowBatchSeq），**整批共用**，保证多只证券间批次号递增。
7. **逐只证券循环**：对每个 `securityCode` 的 items 列表，`buildSingleSubmitReq`（强制 `adjustType="手工调整"`，把整批的 adjustReason/Advice、adjusterId/Name、附件下标/报告ID 透传到单证券提交请求）→ 调用**单笔调库服务** `securityPoolAdjustService.addAdjustLog(singleReq, submissionFiles, batchNoContext)`。
8. 累加 `submitCount`、收集 `logIds` 到返回 dto。

**返回结构** `BatchSecurityInboundAdjustDto`：`{ items:[], securityCount, submitCount, logIds:[...] }`。

### 3.4 后端批量校验逻辑（逐条列举，与单笔异同）

批量校验**完全复用**单笔 `SecurityPoolAdjustService.checkAdjust`，其内部四阶段：

1. **前置校验**（`validateCheckAdjustReq`）：securityCode/items 非空。
2. **参数初始化**（`loadSharedData`）：一次性查证券基础信息（兼存在性校验）、全量投资池 Map、证券当前有效入池 ID 集（audit_status='20'）、全量池关系三层 Map、证券级标志（是否有进行中流程 + 当前节点名、证券/主体是否在观察池）、本次请求调入/调出目标池 ID 集。
3. **调入校验**（`executeInAdjustCheck`）：对每个手工调入项执行 `checkInConditions`，并按池关系自动追加联动调入项（`in_linked`/linkage）、互斥配套调出项（`in_mutex`/mutex）；自动项与手工项失败状态联动。
4. **调出校验**（`executeOutAdjustCheck`）：对每个手工调出项执行 `checkOutConditions`，追加联动调出项（`out_linked`）。
5. **流程类型判断**（`resolveAdjustFlowOptions`）：为每个可调整手工项生成 FlowOption 列表（同单笔规则，见 [04-security-pool-adjust.md](04-security-pool-adjust.md) §3.6 ⑤）。

**调入校验规则顺序**（`checkInConditions`）：证券到期 → 进行中流程 → 重复入池 → 容量上限 → 来源池 → 调入限制池(in_restrict) → 同请求互斥冲突(in_mutex) → 调入弹性禁投池(in_soft_restrict)。

**调出校验规则顺序**（`checkOutConditions`）：证券到期 → 进行中流程 → 未入池 → 调出限制池(out_restrict) → 调出互斥池(out_mutex) → 同请求互斥冲突 → 调出弹性禁投池(out_soft_restrict)。

**与单笔的异同**：批量校验**没有新增任何校验规则**，区别仅在外层：①逐只证券循环调用单笔校验；②`adjustGroupKey` 加 `securityCode_` 前缀避免跨证券冲突；③只保留方向匹配的结果项。单笔校验内部的「同请求互斥冲突」规则在批量场景下**仅对单只证券内部的多目标池生效**，不跨证券判断。

### 3.5 后端批量提交逻辑

单笔 `addAdjustLog` 五阶段（批量外层逐证券调用）：

1. **前置校验**（`validateSubmitReq`）。
2. **参数初始化**（`loadSubmitSharedData`）：查证券信息、全量池 Map、当前入池集、池关系 Map、进行中流程标志、观察池标志、**为每个唯一 flowId 加载流程快照**（定义+活跃版本+节点+连线+审批配置+处理人，`buildFlowSnapshot`）。
3. **调入处理**（`executeInboundSubmit`）：逐项判断直通流程（`isDirectFlow`：start→发起人自动→end 无人工节点）：
   - 直通：写 `ip_adjust_log`(audit_status='20') + 绑定附件 + 手工项创建初始步骤 + 写 `ip_pool_status`(audit_status='20' 即时生效)。
   - 非直通：写 `ip_adjust_log`(audit_status='00') + 绑定附件 + 手工项创建初始步骤；若初始步骤已走到 end（`createInitialSteps` 返回 true）则升为 '20' 并写 ip_pool_status。
4. **调出处理**（`executeOutboundSubmit`）：同直通/非直通分支，直通/升完成时**软删除** `ip_pool_status` 中该证券在该池的有效记录（`deletePoolStatusSoft`）。
5. **后续处理**（`postSubmitProcess`）：批量场景 `req.securityInfo` 为 null，跳过。

**批次号规则**（`buildAdjustBatchNo`）：`BOND + batchTimeText(yyyyMMddHHmmss) + 4位序号`；序号段：调入 `1000+inboundBatchSeq`、调出 `2000+outboundBatchSeq`、无流程 `3000+noFlowBatchSeq`。同组（`adjustGroupKey`）手工项与其触发的联动/互斥项**共用一个批次号**。批量场景下 `BatchNoContext` 跨证券共享，序号持续递增。

**流程步骤创建**（`createInitialSteps`）：懒创建前 3 步——开始节点(auto_process) → 发起人节点(submit，若为 initiator 策略自动完成) → 下一审批节点(pending，按处理人展开)；若沿途直达 end 则返回 true。步骤写 `ip_adjust_step`。

**附件绑定**（`bindSubmitAttachments`）：对每条新建调库日志：
- `bindAttachments(adjustLogId, creditReportFileIndexes, "credit_report_hand", submissionFiles)`：按下标从共享 `SubmissionFiles` 取物理文件保存并写 `sys_attachment`（mainId=日志ID，tableName=ip_adjust_log）。同一文件对同一日志+分类只存一次。
- `bindAttachments(..., materialFileIndexes, "material_hand", ...)`。
- `copyReportAttachments(adjustLogId, creditReportSourceAttachmentIds, "credit_report", uploaderId)`：复制报告库附件记录为信评报告分类（`credit_report_in/out`）。
- `copyReportAttachments(..., materialSourceAttachmentIds, "material", ...)`。

**事务范围与部分失败**：整个批量提交在**单一 `@Transactional`** 内。任一证券的任一步骤抛异常→**整体回滚**（包括已处理的证券、附件、步骤）。**不存在「部分成功」**：要么全部成功提交，要么全部回滚。

### 3.6 涉及的数据库表及字段写入

| 表 | 写入动作 | 关键字段 |
|---|---|---|
| `ip_adjust_log` | `addAdjustLog` insert | security_code, security_short_name, security_type, adjust_type(手工/联动/互斥调整), adjust_mode(调入/调出), adjust_batch_no, target_pool_id, target_pool_name, pool_type, flow_id/key/type, audit_status('00'待审/'20'通过), adjuster_id/name, adjust_reason/advice, submit_time, crte_time/updt_time, is_deleted=0 |
| `ip_pool_status` | `addPoolStatus`(调入生效) / `deletePoolStatusSoft`(调出生效) | 同上调库字段 + adjust_log_id(回链), audit_status='20', is_deleted |
| `ip_adjust_step` | `addAdjustStep`(每步一条，审批节点按处理人展开多条) | adjust_log_id, adjust_batch_no, flow_node_id, node_code/label/type, approval_strategy, sort_order, step_status(pending/submit/auto_process), handler_id/name, process_action, start_time/process_time |
| `sys_attachment` | `bindAttachments` / `copyReportAttachments` | table_name='ip_adjust_log', main_id=日志ID, attachment_category(credit_report_hand/material_hand/credit_report_in/out/...), file_type, original/new_file_name, file_size, content_type, full_url |

查询用到的表：`ip_investment_pool`（投资池，status='enabled' 且无启用子节点=叶子池）、`ip_pool_relation`（池关系）、`ip_pool_permission`（权限）、`rrs_securityinfo`（证券基础信息）、`wf_flow_definition/version/node/edge` 等（流程快照）。

---

## 4. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `common/queryPoolTreeList` | `{}` | `List<PoolTreeDto>` | 主页面初始化投资池筛选树 |
| `batchSecurityPoolAdjust/queryPoolPage` | currentUserId, poolIds, pageIndex, pageSize | `PageResult<BatchSecurityPoolDto>`（records:[id/poolName/poolFullName/poolType/marketCodes/varietyCodes/description/maxCapacity/currentCount], total） | 分页查询当前用户可调整的启用叶子投资池 |
| `batchSecurityPoolAdjust/querySecurityPage` | currentUserId, poolId, direction(in/out), securityCode, securityShortName, marketCodes, pageIndex, pageSize | `PageResult<BatchSecurityCandidateDto>` | 分页查询目标池候选证券 |
| `batchSecurityPoolAdjust/checkAdjust` | `BatchSecurityInboundAdjustReq`: currentUserId, direction, poolId, poolName, poolType, securities:[{securityCode,securityShortName,securityType}] | `BatchSecurityInboundAdjustDto`（items:[{...,canAdjust,failReasons,flowOptions}]） | 批量调库下一步校验 |
| `batchSecurityPoolAdjust/addAdjustLog`（JSON） | `BatchSecurityInboundAdjustReq`（含 items:[{...,flowId/flowKey/flowType,creditReportFileIndexes,...}]） | `BatchSecurityInboundAdjustDto`（securityCount, submitCount, logIds） | 批量提交调库申请（无附件） |
| `batchSecurityPoolAdjust/addAdjustLogWithFiles`（multipart） | `request`=JSON Blob + `files`=MultipartFile[] | 同上 | 批量提交调库申请及附件（前端实际用此入口；JSON 无附件入口为 `addAdjustLog`） |
| `reports/queryInReportPage` | pageIndex, pageSize, reportTitle, securityCode, reportType, crteTimeStart/End | PageResult | 报告弹窗内部报告查询 |
| `reports/queryOutReportPage` | 同上 | 同上 | 报告弹窗外部报告查询 |
| `attachments/downloadAttachment` | `{id}` (responseType=blob) | blob | 下载报告库附件 |

> 路径均带前缀 `/api/v1/`。

---

## 5. 关键数据库表

### 5.1 `ip_investment_pool`（投资池主表）
关键字段：`id`、`parent_id`、`pool_name`、`pool_type`、`market_codes`、`variety_codes`、`max_capacity`、`status`(enabled/disabled)、`is_deleted`(0/1)、`inner_sort`、`outer_sort`、`in_flow_id/key`、`out_flow_id/key`、`simple_in_flow_id/key`。

### 5.2 `ip_adjust_log`（调库记录表）
- `adjust_type`：手工调整/联动调整/互斥调整/关联调整/Excel导入/手动批量调整。
- `adjust_mode`：调入/调出。
- `adjust_batch_no`：同组调库记录共用。
- `audit_status`：`-1`无效调整 / `00`已提交待审核 / `10`审核通过待审批 / `11`驳回待修改 / `20`审批通过 / `21`审批驳回 / `32`O32自动审批 / `99`发起人已撤回。
- `flow_id/flow_key/flow_type`：流程快照。

### 5.3 `ip_pool_status`（投资池当前状态表）
字段与 `ip_adjust_log` 基本一致，多 `adjust_log_id`（回链调库日志）。`audit_status='20'` 表示证券已生效在池。候选证券查询、当前入池判断均以此表 `audit_status='20' and is_deleted=0` 为准。

### 5.4 `ip_adjust_step`（流程步骤记录表）
- `node_type`：start/approval/auto/end/notify/condition。
- `approval_strategy`：preempt/all/initiator。
- `step_status`：pending/approve/reject/submit/auto_process/canceled。
- `process_action`：submit/approve/reject/auto_process/skipped。

### 5.5 `ip_pool_relation`（池关系表）
`relation_type`：source(来源池)/in_restrict(调入限制)/out_restrict(调出限制)/in_linked(调入联动)/out_linked(调出联动)/in_mutex(调入互斥)/out_mutex(调出互斥)/in_soft_restrict(调入弹性禁投)/out_soft_restrict(调出弹性禁投)。

### 5.6 `ip_pool_permission`（权限表）
`permission_type`：viewable/adjustable/excel_importable；`handler_type`：role/user。

### 5.7 `sys_attachment`（附件表）
`table_name='ip_adjust_log'`、`main_id=调库日志ID`、`attachment_category`（credit_report_hand/credit_report_in/credit_report_out/material_hand/material_in/material_out）、文件元信息。

> 注：批量调库主页面**未引入 dict.js**，报告类型/证券类型/流程类型等字典是页面内 `data()` 自带的，与 dict.js 存在重复维护。

---

## 6. 与单笔调库的差异对比

| 维度 | 单笔调库（`SecurityPoolAdjustService` + `security_pool_adjust.html`） | 批量调库（`BatchSecurityPoolAdjustService` + `batch_security_pool_adjust.html`） |
|---|---|---|
| **入口选择** | 选一只证券 → 查可调投资池树 → 勾选一个或多个目标池 | 选一个目标池 → 查候选证券 → 勾选多只证券 |
| **校验接口** | `checkAdjust`（单证券多目标池） | 同名 `checkAdjust`，但外层**逐只证券循环**调用单笔校验，仅保留方向匹配项；`adjustGroupKey` 加 `securityCode_` 前缀 |
| **校验规则** | 四阶段 + 调入/调出各 7~8 条规则 + 联动/互斥自动项 + 流程类型判断 | **完全复用**，无新增规则。「同请求互斥冲突」仅在单只证券内部生效，不跨证券 |
| **提交接口** | `addAdjustLog`（单证券多调库项） | 同名 `addAdjustLog`，外层**按 securityCode 分组**逐只调用单笔提交；强制 `adjustType="手工调整"` |
| **批次号** | `BatchNoContext` 单次提交内生成 | **整批共用一个 `BatchNoContext`**，跨证券序号递增 |
| **附件** | 单次 `SubmissionFiles` | **整批共用一份 `SubmissionFiles`**（物理文件只存一次），每个 item 携带相同的文件下标和报告附件 ID |
| **事务范围** | 单笔 `@Transactional` | 外层批量 `@Transactional` 包裹逐证券调用，**整批原子**：任一异常全回滚，无部分成功 |
| **审批流程选择** | 校验返回 `dto.flowOptions`（整单一组）+ recommendedFlowId | 校验返回**每行** `flowOptions`，前端行内下拉逐行选择；提交时每行带各自 flowId/flowKey |
| **调整原因/建议** | 单笔一份 | **整批一份**（`adjustReason/adjustAdvice` 在 payload 顶层，透传到每只证券） |
| **候选证券查询** | 复用 `querySecurityPage`（按证券代码/简称/发行人模糊） | 新增 `BatchSecurityPoolAdjustMapper.querySecurityPage`，按目标池 + 方向(in 不在池/out 在池)+ 市场/代码/简称过滤 |
| **投资池查询** | `queryAdjustPoolList`（树，含互斥关系） | 新增 `queryPoolPage`（分页，仅启用叶子池 + 当前用户可调权限过滤 + 现有数量填充 + 全路径名） |
| **新增的 Service 逻辑** | — | ①权限/可调池过滤；②候选证券按方向过滤；③逐证券循环校验/提交；④批次号与附件上下文跨证券共享；⑤整批事务 |
| **复用的单笔逻辑** | — | `checkAdjust`、`addAdjustLog`(五阶段)、`buildAdjustLog`、`createInitialSteps`、`bindSubmitAttachments`、`isDirectFlow`、流程快照构建、池关系三层 Map、调入/调出校验规则全套 |

**一句话总结**：批量调库是一个「编排层」（`BatchSecurityPoolAdjustService`）——它负责按证券拆分、权限过滤、整批共享批次号/附件/事务，而真正的校验与落库逻辑全部复用单笔 `SecurityPoolAdjustService`，没有重复实现任何业务规则。

---

## 7. 需求缺口（编写/改造时需注意）

1. **选择页面未与主页面打通**：`batch_security_pool_adjust_select.html` 的 `confirmSelection` 仅弹窗、`goBack` 硬跳转，选中结果无法回传。若需求要求两页联动，需补充回传机制（推荐 sessionStorage + 主页 `mounted` 恢复，或改为主页面内嵌组件）。
2. **批量提交无部分成功语义**：当前整批一个事务，N 只证券任一失败全回滚。若需求要求「失败行跳过、成功行提交」，需改造 `addAdjustLog` 把逐证券调用移出外层事务（每证券独立事务 + 收集失败明细返回），并调整前端提示。

## 8. 验收标准

- 选池 → 选多只证券 → 校验 → 提交全流程闭环，整批共用批次号、附件、事务。
- 校验结果逐条展示可/不可调整及原因，行内可选审批流程。
- 提交成功后所有可提交证券生成调库日志与初始步骤；任一失败整批回滚，不产生半提交数据。
- `BatchSecurityPoolAdjustApiTest` 覆盖选池、候选证券查询、批量校验、批量提交业务线。

## 9. 关键源码索引

- 前端：`znty-rrs-ui/batch_security_pool_adjust.html`（主页面）、`znty-rrs-ui/batch_security_pool_adjust_select.html`（独立选择页）、`znty-rrs-ui/dict.js`
- Controller：`BatchSecurityPoolAdjustController.java`
- Service：`BatchSecurityPoolAdjustService.java`（编排层）、`SecurityPoolAdjustService.java`（被复用的单笔服务）、`SysAttachmentService.java`
- Mapper：`BatchSecurityPoolAdjustMapper.java` / `BatchSecurityPoolAdjustMapper.xml`
- 实体：`BatchSecurityPoolAdjustReq`、`BatchSecurityInboundAdjustReq`、`BatchSecurityInboundAdjustDto`、`BatchSecurityPoolDto`、`BatchSecurityCandidateDto`
- SQL：`sql/rrs_security_pool_adjust_schema.sql`、`sql/rrs_pool_init_schema.sql`、`sql/rrs_sys_attachment_schema.sql`
