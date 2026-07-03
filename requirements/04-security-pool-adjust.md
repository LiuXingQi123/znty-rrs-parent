# 证券池调库申请（调入 / 调出）需求说明

> 前端页面：`security_pool_adjust.html`（列表 + 详情两视图）
> 后端前缀：`/api/v1/securityPoolAdjust`
> 角色定位：研究员 / 业务人员检索证券 → 查看池状态 → 在权限范围内发起调入或调出申请，提交前完成关系、权限与风控校验。

---

## 1. 页面概览

单 Vue 实例（`el: '#security_pool_adjust'`），通过 `currentPage` 在两个视图间切换：

- **列表页**（`currentPage === 'list'`）：Topbar + 搜索面板 + 数据表格卡片（含分页）。
- **详情/调库操作页**（`currentPage === 'detail'`）：返回按钮 + 证券基本信息 + 当前所在池 + 调库操作卡（步骤 1 选池 / 步骤 2 校验确认）+ 流程选择弹窗 + 信评报告选择弹窗。

详情页内部再通过 `adjustStep`（1=选池，2=校验确认）控制步骤切换。

**初始化**（`created`）：设置 `axios.defaults.baseURL = 'http://localhost:18090'`，调用 `loadList()` 加载证券列表。

**apiPost 封装**：页面内置方法，返回 `{ success, data, message }`，`!json.success` 弹错并抛异常；支持 `responseType === 'blob'` 用于附件下载。

---

## 2. 筛选与查询逻辑

### 2.1 列表页筛选项（`searchForm`）

| 控件 | 绑定字段 | 说明 |
|---|---|---|
| 文本输入 | `securityCode` | 证券代码，回车查询 |
| 文本输入 | `securityShortName` | 证券简称，回车查询 |
| 文本输入 | `issuer` | 发行人，回车查询 |

- **查询** `handleSearch()`：重置 `pageIndex=1` 后调 `loadList()`。
- **重置** `handleReset()`：清空三字段、`pageIndex=1`、`loadList()`。

### 2.2 loadList → 接口

- 路径：`POST /api/v1/securityPoolAdjust/querySecurityPage`
- 请求体：`{ securityCode, securityShortName, issuer, pageIndex, pageSize }`
- 后端：`SecurityPoolAdjustService.querySecurityPage`，`PageHelper.startPage` 分页，SQL 按 `wind_code` / `short_name` / `issuer` 模糊匹配，`ORDER BY si.id DESC`，LEFT JOIN `dict_security_type` 取 `security_type_name`。
- 返回：`PageResult<SecurityInfoDto>`，前端取 `data.records` 与 `data.total`。

### 2.3 表格列（列表页）

| 列 | prop | 渲染 |
|---|---|---|
| 序号 | — | `(pageIndex-1)*pageSize + $index + 1` |
| 证券代码 | `windCode` | 可点击 `desc-link` → `handleAdjust(row)`；空值空白 |
| 证券简称 | `shortName` | 可点击 |
| 证券全称 | `fullName` | tooltip |
| 发行人 | `issuer` | tooltip |
| 证券类型 | `securityTypeName` | `el-tag type=info`，空值空白（后端 JOIN 出 name，前端不做 code→name） |
| 发行总额(亿) | `issueAmountplan` | 等宽字体，非空才显示 |
| 当期利率(%) | `couponRate` | 等宽字体 + 琥珀色 |
| 起息日期 | `carryDate` | 居中 |
| 到期日 | `maturityDate` | 居中 |
| 证券期限 | `dateExists` | — |
| 证券评级 | `ratingBond` | `el-tag type=success`，有值才显示 |
| 主体评级 | `ratingBondissuer` | 空值空白 |
| 操作 | — | 「调库」按钮 → `handleAdjust(row)` |

### 2.4 分页

`pagination: { pageIndex:1, pageSize:10, total:0 }`，`page-sizes=[10,20,50,100]`，layout `total, sizes, prev, pager, next, jumper`。翻页/切条数均调 `loadList()`。

### 2.5 进入详情页 `handleAdjust(row)`

- 从 URL 读取可选参数 `targetPoolId`、`adjustLogId`/`adjust_log_id`、`adjustBatchNo`/`adjust_batch_no`（支持从其他入口带参跳入）。
- `currentPage = 'detail'`，调用 `loadDetailData(row.windCode || row.securityCode)`。

### 2.6 详情页初始化（并发加载）

`loadDetailData(securityCode)` 用 `Promise.all` 并发 5 个接口：

| 接口 | 请求体 | 用途 |
|---|---|---|
| `querySecurityDetail` | `{ securityCode }` | 证券基本信息 → `bondDetail` |
| `queryAdjustPoolList` | `{ securityCode, adjustDirection:'in', currentUserId }` | 可调入池 |
| `queryAdjustPoolList` | `{ securityCode, adjustDirection:'out', currentUserId }` | 可调出池 |
| `querySecurityPoolStatus` | `{ securityCode }` | 当前所在池 + 主体所在池 |
| `queryAdjustLogList` | `{ securityCode, adjustBatchNo }` | 历史调库记录（不传批次仅返回未终结流程：`audit_status NOT IN ('-1','20','21','99')`） |

随后：
1. 用 `securityCurrentPools.targetPoolId` 构建 `currentSecurityPoolIds` 集合。
2. **调出池过滤**：仅保留证券当前已在其中的池及其父节点。
3. **调入池过滤**：排除证券已在其中的叶子节点；父节点若所有子节点均被排除则一并移除。
4. `buildPoolTree` 按 `parentId` 组装为树。
5. 由 `inPools`/`outPools` 的 `inMutexPoolIds`/`outMutexPoolIds` 构建 `inMutexMap`/`outMutexMap`（前端互斥校验用）。
6. `loadLogAttachments` 加载调库记录附件，`loadFlowSteps` 加载当前活跃流程步骤。

---

## 3. 调入 / 调出 申请逻辑

### 3.1 用户操作步骤

**步骤 1（选池，`adjustStep===1`）**：
- 左右双栏：左「可调入库」（绿色），右「可调出库」（红色），均为树表格（`row-key="id"`，`default-expand-all`），仅叶子节点可选。
- 每个叶子行展示：投资池名称、上限数量（`maxCapacity`）、现有数量（`currentCount`）、信评报告列（选择报告 + 上传附件）、其他材料列（同上）。
- 底部「下一步」按钮，`disabled` 当 `selectedInPools.length===0 && selectedOutPools.length===0`。

**前端互斥校验**：
- `handleInPoolSelect`：勾选调入池时检查 `inMutexMap[id]` 是否与已选调入池或已选调出池冲突，冲突弹 warning 并取消勾选。
- `handleOutPoolSelect`：对称处理调出，并反向检查已选调入池的 `inMutexMap` 是否含当前调出池。

**信评报告 / 其他材料选择**：
- `openReportDialog(poolId, column)` 打开报告弹窗（column=`'credit'`/`'material'`）。
- 弹窗含「内部报告」（`/api/v1/reports/queryInReportPage`）与「外部报告」（`/api/v1/reports/queryOutReportPage`）两个 Tab，支持按标题/证券编码/报告类型/撰写日期范围筛选 + 分页。
- `el-upload` `auto-upload=false` 仅在前端暂存 File 对象到 `attachmentFiles[poolId]` / `materialFiles[poolId]`。
- `handleConfirmReportDialog` 把选中报告写入 `creditReportSelections[poolId]` / `otherMaterialSelections[poolId]`，并同步到 `adjustReviewList`。

**步骤 2（校验确认，`adjustStep===2`）**：
- 显示「调库校验结果」表格（`adjustReviewList`）与「原因和建议」区（`adjustReason`/`adjustAdvice` 文本域，maxlength 500）。
- 底部「上一步」与「提交」按钮。

### 3.2 接口调用顺序

1. **`goToStep2()`**：
   - `syncSelectedPoolsFromTables()` 规范化已选池（仅保留叶子节点，去重）。
   - 组装 `items`：调入池 `{ targetPoolId, targetPoolName, poolType, adjustMode:'调入' }`，调出池 `adjustMode:'调出'`。
   - 调 `POST /api/v1/securityPoolAdjust/checkAdjust`，请求体：
     ```
     { securityCode, securityShortName, securityType,
       items: [{ targetPoolId, targetPoolName, poolType, adjustMode }] }
     ```
   - 用 `adjustCheckSeq` 序号防止旧请求覆盖新结果。
   - 将返回 `result.items` 映射为 `adjustReviewList`（含 `valid`、`failReason`、`flowOptions`、默认选中推荐流程），`adjustStep=2`。

2. **`handleSubmit()`**：
   - 校验 `validCount>0` 且 `allValidRowsHaveFlow`（所有手工可提交项均已选流程）。
   - 打开流程选择弹窗，用户为每条 `validManualAdjustReviewList`（`itemTag==='manual' && valid`）选择审批流程，`confirmFlowSelection()` → `submitAdjustLog()`。

3. **`submitAdjustLog()`**：
   - 取 `adjustReviewList.filter(r=>r.valid)` 为 `validItems`。
   - `collectSubmitFiles` 收集本地上传 File 到 `submitFiles` 数组，并记录每个 item 的 `creditReportFileIndexes`/`materialFileIndexes`。
   - `collectReportAttachmentIds` 收集已选报告库附件 ID 到 `creditReportSourceAttachmentIds`/`materialSourceAttachmentIds`。
   - `submitAdjustMultipart('/api/v1/securityPoolAdjust/addAdjustLogWithFiles', payload, submitFiles)` 用 `FormData` 提交：`request` 为 JSON Blob，`files` 为 multipart 文件数组（multipart 入口路径与 JSON 入口 `addAdjustLog` 不同，前端实际调 `addAdjustLogWithFiles`）。
   - 成功后 `$message.success` + `backToList()`。

### 3.3 addAdjustLog 请求体（`SecurityPoolAdjustSubmitReq`）

| 字段 | 类型 | 说明 |
|---|---|---|
| securityCode | String | 证券代码 |
| securityShortName | String | 证券简称 |
| securityType | String | 证券类型 |
| adjustType | String | 调整类型（前端固定传 `'手工调整'`） |
| adjustReason | String | 调整原因 |
| adjustAdvice | String | 调整建议 |
| securityInfo | SecurityInfoBo | 详情页可编辑的证券字段全量（约 28 字段） |
| adjusterId | String | 调整人 ID（前端默认 `'1001'`） |
| adjusterName | String | 调整人名称（默认 `'管理员'`） |
| items | List&lt;AdjustItem&gt; | 调库明细 |

`AdjustItem` 字段：`targetPoolId/targetPoolName/poolType`、`adjustMode`、`itemTag`（manual=手工 / linkage=联动 / mutex=互斥）、`adjustGroupKey`（手工项及其触发的联动/互斥项共用）、`flowId/flowKey/flowType`、`adjustmentNote`、`creditReportFileIndexes/materialFileIndexes`、`creditReportSourceAttachmentIds/materialSourceAttachmentIds`。

返回 `AdjustSubmitDto`：`{ securityCode, submitCount, logIds:[...] }`。

### 3.4 checkAdjust 返回结构（`AdjustCheckDto`）

- `items: List<CheckResultItem>`：每个 `targetPoolId` 的校验结果，含 `poolName`（全路径）、`poolType`、`adjustMode`、`itemTag`、`adjustGroupKey`、`canAdjust`、`failReasons: List<String>`、`flowOptions: List<FlowOption>`。
- `flowOptions`：全局去重的流程候选项。
- `recommendedFlowId/Key/Type`。

`FlowOption`：`flowType`（whitelistInbound/simpleInbound/normalInbound/upgradeInbound/downgradeInbound/normalOutbound）、`flowName`、`flowId`、`flowKey`、`recommended`、`matched`、`selectable`、`matchReasons`、`unmatchReasons`。

### 3.5 前端校验结果展示

校验结果表列：序号、投资池名称、调整类型（`getAdjustTypeLabel`：manual→手工调整 / linkage→联动调整 / mutex→互斥调整）、调整方向（调入 success / 调出 danger）、审批流程、信评报告、其他材料、调整说明、**校验结果**（绿色圆点「可调整」/ 红色圆点「不可调整」，由 `row.valid` 决定）。未通过项的 `failReason` 以 `；` 连接。

### 3.6 后端 checkAdjust 完整校验逻辑

入口 `SecurityPoolAdjustService.checkAdjust`，分五阶段：

**① 前置校验** `validateCheckAdjustReq`：`securityCode` 非空、`items` 非空，否则抛 `BizException`。

**② 参数初始化** `loadSharedData`：
- `querySecurityBoByCode` 取证券（null 抛「证券不存在」）。
- `queryPoolList` 全量投资池 → `poolMap`。
- `querySecurityCurrentPoolIdList` 取证券当前有效入池（`audit_status='20'`）→ `currentPoolIds`。
- `queryAllPoolRelationList` 取全量 `ip_pool_relation` → 三层嵌套 Map `poolRelationMap`（poolId → relationType → List&lt;poolId&gt;）。
- 拆分请求中调入/调出目标池集合。
- 查证券级标志：`querySecurityHasPendingProcess`（是否有 pending 步骤）、`querySecurityPendingProcessNodeLabel`（进行中节点名）、`querySecurityInObservePool`、`queryIssuerInObservePool`。

**③ 调入校验** `executeInAdjustCheck`，对每个 `adjustMode==='调入'` 项执行 `checkInConditions`，8 条规则按序，任一返回非 null 即加入 `failures`：

| # | 规则方法 | 失败原因 |
|---|---|---|
| 1 | `preCheckSecurityExpired` | 证券已到期，不支持调库（`maturity_date` 早于今日） |
| 2 | `preCheckPendingProcess` | 证券存在进行中的调库流程（当前节点：xxx），请等待流程结束 |
| 3 | `inCheckSecurityAlreadyInPool` | 证券已在目标投资池中，无需重复调入 |
| 4 | `inCheckPoolCapacity` | 目标投资池已达持仓上限（N），无法调入（`maxCapacity>0` 且 `currentCount>=maxCapacity`） |
| 5 | `inCheckSourcePool` | 目标池配置了来源池限制，证券须先在以下池中：xxx（`source` 关系） |
| 6 | `inCheckRestrictPool` | 证券在调入限制池中，无法操作：xxx（`in_restrict`） |
| 7 | `inCheckMutexConflict` | 与以下互斥池不可同时调入：xxx（同请求同时勾选 `in_mutex` 关系池） |
| 8 | `inCheckElasticPool` | 证券在调入弹性禁投池中，无法操作：xxx（`in_soft_restrict`） |

- **自动追加联动调入项**：取目标池 `in_linked` 关系，对每个联动池若未覆盖则 `buildAutoResultItem(linkedId,'调入','linkage')`，并 `inheritManualItemFailure`（手工项失败则阻断联动项）。
- **自动追加互斥配套调出项**：取目标池 `in_mutex` 关系，若证券当前在互斥池中则 `buildAutoResultItem(mutexId,'调出','mutex')`。

**④ 调出校验** `executeOutAdjustCheck`，对每个 `adjustMode==='调出'` 项执行 `checkOutConditions`，7 条规则：

| # | 规则方法 | 失败原因 |
|---|---|---|
| 1 | `preCheckSecurityExpired` | 证券已到期 |
| 2 | `preCheckPendingProcess` | 存在进行中的调库流程 |
| 3 | `outCheckSecurityNotInPool` | 证券当前不在该投资池中，无法调出 |
| 4 | `outCheckRestrictPool` | 证券在调出限制池中（`out_restrict`） |
| 5 | `outCheckMutexPool` | 证券在调出互斥池中（`out_mutex`） |
| 6 | `outCheckMutexConflict` | 与以下互斥池不可同时调出 |
| 7 | `outCheckElasticPool` | 证券在调出弹性禁投池中（`out_soft_restrict`） |

调出方向自动追加 `out_linked` 联动调出项。

**⑤ 流程类型判断** `resolveAdjustFlowOptions`（仅对 `canAdjust && itemTag==='manual'` 的项生成候选）：
- **调出**：目标池标准调出流程（`outFlowId/outFlowKey`），`flowType=normalOutbound`，标记 recommended。
- **调入·非信用债大库**（`poolType != 'credit_bond'`）：默认调入流程（`inFlowId/inFlowKey`），`normalInbound`。
- **调入·已在信用债大库**：`resolveCreditBondAdjustFlowType` 按同父级下 `innerSort` 比较，目标池 sort 小于当前池→`upgradeInbound`（上调）；大于→`downgradeInbound`（下调）。
- **调入·不在信用债大库**：依次评估白名单（当前默认关闭）、简易（`isSimpleInboundFlowMatched`）、默认调入，推荐优先级 白名单 > 简易 > 默认。
  - 简易命中条件：目标池为信用债一/二/三级库（`innerSort 1~3`）；剩余期限 ≤3 年且 ≥0；剩余期限 ≤ 同主体在目标池已有债券最大剩余期限；该主体近一年未在目标池走过简易调入；主体/展望评级未下调，或下调但担保人评级未下调。

### 3.7 后端 addAdjustLog 完整逻辑

入口 `addAdjustLog(req, files)`，`@Transactional(rollbackFor=Exception.class)`，分五阶段：

**① 前置校验** `validateSubmitReq`：`securityCode` 非空、`items` 非空、每项 `adjustMode` 与 `targetPoolId` 非空。

**② 参数初始化** `loadSubmitSharedData`：
- 取证券、全量池 Map、`currentPoolIds`、`poolRelationMap`、证券级标志。
- 收集 `items` 中所有 `flowId`/`flowKey`，`resolveFlowIdFromItem` 解析（flowId 优先，否则用 flowKey 查活跃流程），为每个唯一流程 `buildFlowSnapshot`（定义+活跃版本+节点+连线+审批配置+处理人）。
- 创建 `BatchNoContext`（批次号时间片 `yyyyMMddHHmmss` + 调入/调出/无流程三个序号）。

**批次号规则** `buildAdjustBatchNo`：
- 无流程：`BOND + batchTimeText + (3000 + ++noFlowBatchSeq)`
- 调入：`BOND + batchTimeText + (1000 + ++inboundBatchSeq)`
- 调出：`BOND + batchTimeText + (2000 + ++outboundBatchSeq)`
- 同 `adjustGroupKey` 复用同一批次号。

**直通流程判断** `isDirectFlow`：从 start 出发，所有出边目标为 end，或为「发起人自动提交节点」（`approvalStrategy=='initiator'` 或紧邻 start 且 label 含「发起/提交」）且其后连到 end → 直通（无需人工审批即可生效）。

**③ 调入处理** `executeInboundSubmit`，对每个调入项：
- `resolveManualSubmitItem` 取同组手工项（联动/互斥项共用手工项的流程）。
- `isDirect = noFlow || isDirectFlow(snapshot)`。
- **直通**：`buildAdjustLog` → `auditStatus='20'` → `addAdjustLog`（写 `ip_adjust_log`）→ `bindSubmitAttachments`（绑定附件）→ 手工项且有快照则 `createInitialSteps`（写前 3 步）→ `addPoolStatus`（写 `ip_pool_status`，`audit_status='20'` 即时生效）。
- **非直通**：`buildAdjustLog` → `auditStatus='00'` → `addAdjustLog` → `bindSubmitAttachments` → 手工项则 `createInitialSteps`（返回 true 表示流程已走完 end，则升 `'20'` 并 `addPoolStatus`）。

**④ 调出处理** `executeOutboundSubmit`：逻辑对称，生效操作为 `deletePoolStatusSoft`（软删除 `ip_pool_status` 中该证券在目标池的 `audit_status='20'` 记录，`is_deleted=1`）。

**⑤ 后续处理** `postSubmitProcess`：若 `req.securityInfo` 非空，`buildMergedSecurityInfo` 用 DB 当前快照 + 前端传入字段合并，`editSecurityInfoForAdjust` 全量更新 `rrs_securityinfo`（约 80 字段）。

**`createInitialSteps`**（懒创建）：start（auto_process）→ 若下一节点是发起人节点则自动 submit 并继续 → 若是审批节点则 `createPendingSteps`（按处理人展开为多条 pending 记录）返回 false → 若是 end 则 auto_process 并返回 true。

### 3.8 涉及的数据库表与写入

| 表 | 操作 | 关键字段 |
|---|---|---|
| `ip_adjust_log` | INSERT | security_code, adjust_type, adjust_mode, **adjust_batch_no**, target_pool_id, flow_id/key/type, **audit_status**(00 或 20), adjuster_id/name, adjust_reason/advice, submit_time |
| `ip_pool_status` | INSERT（调入生效）/ UPDATE 软删（调出生效） | security_code, adjust_log_id, target_pool_id, **audit_status='20'**, entry_time, is_deleted |
| `ip_adjust_step` | INSERT（初始 3 步 + 审批时按需创建） | adjust_log_id, **adjust_batch_no**, flow_node_id, node_type, approval_strategy, **step_status**(pending/auto_process/submit), handler_id/name |
| `rrs_securityinfo` | UPDATE | 详情页可编辑字段全量 |
| `sys_attachment` | 绑定/复制附件 | adjustLogId, attachment_category(credit_report_hand/material_hand) |
| `wf_flow_*` | 只读（构建流程快照） | — |

---

## 4. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `querySecurityPage` | securityCode, securityShortName, issuer, pageIndex, pageSize | `PageResult<SecurityInfoDto>` | 分页查询证券列表 |
| `querySecurityDetail` | securityCode | `SecurityInfoDetailDto` | 证券详情 |
| `queryAdjustPoolList` | securityCode, adjustDirection(in/out), currentUserId | `List<PoolDto>`（含 inMutexPoolIds/outMutexPoolIds/currentCount） | 可调入/可调出投资池列表 |
| `querySecurityPoolStatus` | securityCode | `SecurityPoolStatusDto`（securityCurrentPools[], issuerCurrentPools[]） | 证券/主体当前所在池 |
| `checkAdjust` | securityCode, securityShortName, securityType, items[{targetPoolId,targetPoolName,poolType,adjustMode}] | `AdjustCheckDto` | 提交前可行性校验 |
| `addAdjustLog`（JSON） | `SecurityPoolAdjustSubmitReq` | `AdjustSubmitDto` | 提交调库申请（无附件） |
| `addAdjustLogWithFiles`（multipart） | `request`=JSON + `files`=MultipartFile[] | `AdjustSubmitDto` | 提交调库申请（带附件，multipart 入口；JSON 无附件入口为 `addAdjustLog`） |
| `queryAdjustLogList` | securityCode, adjustBatchNo | `List<AdjustLogDto>` | 历史调库记录 |
| `queryAdjustStepList` | adjustLogId, adjustBatchNo | `List<IpAdjustStepDto>` | 流程步骤列表 |
| `attachments/queryAttachmentList` | adjustLogId | 附件列表 | 加载调库记录附件 |
| `attachments/downloadAttachment` | id | blob | 下载附件 |
| `reports/queryInReportPage` | pageIndex, pageSize, reportTitle, securityCode, reportType, crteTimeStart/End | PageResult | 内部报告分页 |
| `reports/queryOutReportPage` | 同上 | PageResult | 外部报告分页 |

> 路径均带前缀 `/api/v1/`；`attachments`、`reports` 前缀独立。

---

## 5. 关键数据库表

### 5.1 `ip_adjust_log`（调库记录表）

| 字段 | 说明 |
|---|---|
| id | 主键 |
| security_code / security_short_name / security_type | 证券信息 |
| adjust_type | 手工调整/联动调整/互斥调整/关联调整/Excel导入/手动批量调整 |
| adjust_mode | 调入/调出 |
| **adjust_batch_no** | 批次号，同组记录共用 |
| target_pool_id / target_pool_name / pool_type | 目标池 |
| flow_id / flow_key / flow_type | 流程快照 |
| **audit_status** | 见第 6 节状态枚举 |
| adjuster_id / adjuster_name | 调整人 |
| adjust_reason / adjust_advice | 原因/建议 |
| submit_time / audit_time / entry_time | 时间 |
| is_deleted | 0 正常 / 1 删除 |

### 5.2 `ip_pool_status`（投资池当前状态表）

字段与 `ip_adjust_log` 基本一致，额外有 `adjust_log_id`（来源调库日志）。`audit_status='20'` 表示证券在该池中已生效；调出生效 = `is_deleted=1`。

### 5.3 `ip_adjust_step`（流程步骤表）

| 字段 | 说明 |
|---|---|
| adjust_log_id / adjust_batch_no | 关联调库记录/批次 |
| flow_node_id / node_code / node_label / node_type | 节点信息（start/approval/auto/end/notify/condition） |
| approval_strategy | preempt=抢占 / all=会签 / initiator=发起人 |
| sort_order | 排序 |
| **step_status** | pending/approve/reject/submit/auto_process/canceled |
| handler_id / handler_name | 处理人 |
| process_action | submit/approve/reject/auto_process/skipped |
| process_comment | 处理意见 |
| start_time / process_time | 时间 |

### 5.4 `ip_pool_relation`（投资池关系表）

| relation_type | 含义 |
|---|---|
| source | 来源池（目标池调整须先在来源池） |
| in_restrict / out_restrict | 调入/调出限制池 |
| in_linked / out_linked | 调入/调出联动池 |
| in_mutex / out_mutex | 调入/调出互斥池 |
| in_soft_restrict / out_soft_restrict | 调入/调出弹性禁投池 |

### 5.5 `rrs_securityinfo`（证券信息表）

详情页可编辑字段约 28 个。关键只读字段：`maturity_date`（到期校验）、`date_next`（剩余期限，yyyyMMdd）、`guarantor`/`guarantor_id`（担保人判断）。

### 5.6 `ip_investment_pool`（投资池表）

`pool_type`（credit_bond/offshore_bond/convertible_bond/special_account/research/fund/restricted/industry/whitelist/blacklist/private_placement/other/observe）、`pool_level`、`max_capacity`、`inner_sort`（升降级判断）、`in_flow_id/key/name`、`out_flow_id/key/name`、`simple_in_flow_id/key/name`。

---

## 6. 状态流转

### 6.1 audit_status 枚举（`ip_adjust_log` / `ip_pool_status`，dict.js `DICT_AUDIT_STATUS`）

| code | 名称 | 含义 |
|---|---|---|
| -1 | 无效调整 | 批量校验不通过，操作中止，流程未正式发起 |
| 00 | 已提交待审核 | 已提交，等待审核人处理 |
| 10 | 审核通过待审批 | 审核通过，等待审批人处理 |
| 11 | 驳回待修改 | 一级审核驳回，发起人可修改后重新提交 |
| 20 | 审批通过 | 二级审批通过，证券已入池/已生效（`ip_pool_status` 即时落地） |
| 21 | 审批驳回 | 二级审批驳回，流程终止 |
| 32 | O32自动审批 | 审批通过后进入 O32 自动审批节点 |
| 99 | 发起人已撤回 | 发起人主动撤回，流程终止 |

### 6.2 流转条件（申请阶段）

**创建时**（`addAdjustLog`）：
- 直通流程（无流程 或 `isDirectFlow`）→ `audit_status='20'`，直接写/删 `ip_pool_status`。
- 非直通流程 → `audit_status='00'`，`createInitialSteps` 创建 pending 步骤；若初始步骤已走到 end（流程极短）则升级为 `'20'` 并落地池状态。

后续审批推进的状态变迁见 [05-security-pool-adjust-approve.md](05-security-pool-adjust-approve.md)。

### 6.3 业务阻断

`querySecurityHasPendingProcess` 检测同证券是否存在 `step_status='pending'` 的步骤，存在则 `checkAdjust` 前置规则 `preCheckPendingProcess` 阻止再次发起调库，避免并发入池状态混乱。

---

## 7. 关键校验汇总

- 证券、目标池、调整方向必填，目标池必须在用户可调整权限范围内。
- 调入已在池证券、调出不在池证券、重复待审批申请应被拦截。
- 同批联动和互斥记录共享主流程，不重复创建审批步骤。
- 未最终通过的申请不得生成有效池状态（仅 `audit_status='20'` 落地 `ip_pool_status`）。
- 校验失败时不生成调整日志和流程步骤。

## 8. 验收标准

- 提交成功后主记录、从属记录、批次号和初始步骤一致。
- 直通流程即时入池；非直通流程进入待审核。
- `SecurityPoolAdjustApiTest` 覆盖查询、校验、提交和追踪业务线。

## 9. 关键源码索引

- 前端：`znty-sirm-ui/security_pool_adjust.html`（列表、详情步骤 1/2、流程弹窗、报告弹窗、`goToStep2`、`submitAdjustLog`、`submitAdjustMultipart`）
- dict.js：`DICT_AUDIT_STATUS`、`DICT_POOL_RELATION_TYPE`、`DICT_POOL_TYPE`
- Controller：`SecurityPoolAdjustController.java`
- Service：`SecurityPoolAdjustService.java`（`checkAdjust`、`addAdjustLog`、`checkInConditions`、`checkOutConditions`、`isDirectFlow`、`createInitialSteps`、`buildAdjustBatchNo`）
- Mapper：`SecurityPoolAdjustMapper.java` / `SecurityPoolAdjustMapper.xml`
- 实体：`SecurityPoolAdjustSubmitReq`、`AdjustCheckReq`、`AdjustCheckDto`、`IpAdjustLogBo`、`IpAdjustStepBo`、`PoolDto`
- SQL：`sql/rrs_security_pool_adjust_schema.sql`、`sql/rrs_pool_init_schema.sql`（`ip_pool_relation`）
