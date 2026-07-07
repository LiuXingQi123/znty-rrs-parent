# 禁投池调整申请（主体级 调入/调出）需求说明

> 前端页面：`forbidden_pool_adjust.html`（列表 + 详情两视图）
> 后端前缀：`/api/v1/forbiddenPoolAdjust`
> 角色定位：研究员 / 业务人员检索发行主体 → 查看主体及其旗下债券当前所在风险池 → 在权限范围内发起禁投池 / 观察池 / 黑名单质押库的调入或调出申请，主体生效后自动同步旗下全部债券。

---

## 1. 页面概览

单 Vue 实例（`el: '#forbidden_pool_adjust'`），通过 `currentPage` 在两个视图间切换：

- **列表页**（`currentPage === 'list'`）：Topbar + 搜索面板（7 字段）+ 数据表格卡片（含分页）。
- **详情/调库操作页**（`currentPage === 'detail'`）：返回按钮 + 主体基本信息（`el-descriptions` 3 列，全部 `disabled` 只读）+ 当前所在池（当前主体所在池 / 当前主体旗下债券所在池两个子块）+ 调库操作卡（步骤 1 选池 / 步骤 2 校验确认）+ 流程选择弹窗 + 旗下债券明细弹窗 + 信评报告选择弹窗。

详情页内部通过 `adjustStep`（1=选池，2=校验确认）控制步骤切换。

**初始化**（`created`）：调用 `loadList()` 加载主体列表。`baseURL` 由 `js/api.js` 统一注入（`http://localhost:18090`），`apiPost` 返回 `{ success, data, message }`，`!json.success` 弹错并抛异常，附件下载接口返回 Base64 字符串后由前端还原为 Blob。

默认登录用户 `currentLoginUserId='1'`（管理员）、`currentLoginUserName='管理员'`。URL 入口参数 `targetPoolId`、`adjustLogId`/`adjust_log_id`、`adjustBatchNo`/`adjust_batch_no`。

---

## 2. 筛选与查询逻辑

### 2.1 列表页筛选项（`searchForm`，7 字段，均回车查询）

| 控件 | 绑定字段 | 说明 |
|---|---|---|
| 文本输入 | `companyCode` | 主体代码 |
| 文本输入 | `companyShortName` | 主体简称 |
| 文本输入 | `companyFullName` | 主体全称 |
| 文本输入 | `compType` | 发行人类型 |
| 文本输入 | `industryName` | 所属行业 |
| 文本输入 | `companyRating` | 主体评级 |
| 文本输入 | `companyInnerRating` | 主体内评分档 |

- **查询** `handleSearch()`：重置 `pageIndex=1` 后调 `loadList()`。
- **重置** `handleReset()`：清空 7 字段后重查。

### 2.2 loadList → 接口

- 路径：`POST /api/v1/forbiddenPoolAdjust/queryCompanyPage`
- 请求体：`{ companyCode, companyShortName, companyFullName, compType, industryName, companyRating, companyInnerRating, pageIndex, pageSize }`
- 后端：`ForbiddenPoolAdjustService.queryCompanyPage`，`PageHelper.startPage` 分页。SQL `INNER JOIN dict_security_type dst ON dst.security_type = si.security_type AND dst.category_type = 'company'`，即只查主体类型证券；`industryName` 同时匹配 `industry_name` 与 `industry_name2`；`ORDER BY si.id DESC`。SELECT 把 `wind_code AS companyCode`、`short_name AS companyShortName`、`rating_bondissuer AS companyRating`、`inner_issuer_rating AS companyInnerRating` 等。
- `fillCompanyBondCount`：另查 `queryCompanyBondCountList`（`issuer_code IN (...) GROUP BY issuer_code`）批量回填 `companyBondCount`。
- 返回 `PageResult<ForbiddenPoolAdjustDto>`，前端取 `data.records` 与 `data.total`。

### 2.3 表格列（列表页）

序号 / 主体代码（desc-link→`handleAdjust`）/ 主体简称（desc-link）/ 主体全称（tooltip）/ 主体类型（`securityTypeName`，el-tag info）/ 发行人类型 / 一级行业 / 二级行业 / 主体评级（el-tag）/ 评级展望 / 主体内评分档 / 旗下债券数量 / 操作（「调库」按钮）。

### 2.4 分页

`pagination: { pageIndex:1, pageSize:10, total:0 }`，`page-sizes=[10,20,50,100]`。翻页/切条数均调 `loadList()`。

### 2.5 进入详情页 `handleAdjust(row)`

读 URL `targetPoolId`/`adjustLogId`/`adjustBatchNo`，`currentPage = 'detail'`，调用 `loadDetailData(row.companyCode)`。

### 2.6 详情页初始化（并发加载）

`loadDetailData(companyCode)` 用 `Promise.all` 并发 5 个接口：

| 接口 | 请求体 | 用途 |
|---|---|---|
| `queryCompanyDetail` | `{ companyCode }` | 主体基本信息 → `companyDetail` |
| `queryAdjustPoolList` | `{ companyCode, adjustDirection:'in', currentUserId }` | 可调入池 |
| `queryAdjustPoolList` | `{ companyCode, adjustDirection:'out', currentUserId }` | 可调出池 |
| `queryCompanyPoolStatus` | `{ companyCode }` | 当前主体所在池 + 旗下债券所在池 |
| `queryAdjustLogList` | `{ companyCode, adjustBatchNo }` | 历史调库记录（不传批次仅返回未终结流程：`audit_status NOT IN ('-1','20','21','99')`） |

随后：用 `companyCurrentPools.targetPoolId` 构建 `currentSecurityPoolIds`；调出池仅保留主体已在其中的池及父节点；调入池排除主体已在的叶子节点；`buildPoolTree` 组装树；合并 `inMutexPoolIds`/`outMutexPoolIds` → `inMutexMap`/`outMutexMap`；`loadLogAttachments` 加载附件；`loadFlowSteps` 加载流程步骤。

### 2.7 当前所在池两个子块

- **当前主体所在池**（`companyCurrentPools`）：SQL `ip_pool_status ips INNER JOIN dict_security_type ... category_type='company' INNER JOIN ip_investment_pool p`，`WHERE ips.security_code=#{companyCode} AND ips.audit_status='20' AND ips.is_deleted=0`，`ORDER BY entry_time DESC, id DESC`。
- **当前主体旗下债券所在池**（`companyBondCurrentPools`）：`rrs_securityinfo bond INNER JOIN dict_security_type ... category_type='bond' INNER JOIN ip_pool_status ips ON ips.security_code=bond.wind_code AND ips.audit_status='20'`，`WHERE bond.issuer_code=#{companyCode}`，`GROUP BY target_pool_id`，含 `COUNT(DISTINCT bond.wind_code) AS bondCount`、`MIN(entry_time)` 最早入池日期。每行「查看债券」按钮 → `openCompanyBondDialog` 调 `queryCompanyBondList`。
- 池名通过 `investmentPoolService.queryPoolFullNameMap()` 填全路径。

---

## 3. 调入 / 调出 申请逻辑

### 3.1 用户操作步骤

**步骤 1（选池，`adjustStep===1`）**：左右双栏树表格（`row-key="id"`，`default-expand-all`，仅叶子可勾选）：左「可调入库」（绿），右「可调出库」（红）。每叶子行展示投资池名称、上限数量（`maxCapacity`）、现有数量（`currentCount`）、信评报告列、其他材料列。

> **可调池范围硬编码**：`ALLOWED_MANUAL_POOL_IDS = {15L, 16L, 17L}`（禁投池 / 观察池 / 黑名单质押库），`queryCompanyAdjustPoolList` 强制三池必须存在且 `status='enabled'`/`is_deleted!=1`，否则抛「禁投池调整配置不完整」或「目标池未启用」。

**前端互斥校验**：`handleInPoolSelect`/`handleOutPoolSelect` 检查 `inMutexMap`/`outMutexMap`，同面板 + 跨面板冲突弹 warning 并 `toggleRowSelection(row,false)`。

**信评报告 / 其他材料**：`openReportDialog(poolId,'credit'|'material')` 打开弹窗，含内部报告 Tab（`/api/v1/reports/queryInReportPage`）与外部报告 Tab（`/api/v1/reports/queryOutReportPage`），支持标题/证券编码/类型/撰写日期筛选 + 分页；`el-upload auto-upload=false` 暂存 File。`handleConfirmReportDialog` 回填 `creditReportSelections`/`otherMaterialSelections`。

**步骤 2（校验确认，`adjustStep===2`）**：显示「调库校验结果」表格（`adjustReviewList`）与「原因和建议」区（`adjustReason`/`adjustAdvice` 文本域）。

### 3.2 接口调用顺序

1. **`goToStep2()`**：`syncSelectedPoolsFromTables()` 规范化（仅叶子、去重）；组装 `items`（调入 `adjustMode:'调入'`，调出 `adjustMode:'调出'`）；调 `POST /api/v1/forbiddenPoolAdjust/checkAdjust`，请求体 `ForbiddenPoolAdjustCheckReq`：`{ companyCode, companyShortName, securityType, items:[{targetPoolId,targetPoolName,poolType,adjustMode}] }`；用 `adjustCheckSeq` 防旧请求覆盖；返回 `AdjustCheckDto` 映射为 `adjustReviewList`，`adjustStep=2`。

2. **`handleSubmit()`**：校验 `validCount>0` 且 `allValidRowsHaveFlow`；打开流程选择弹窗，为每条 `validManualAdjustReviewList`（`itemTag==='manual' && valid`）选流程，`confirmFlowSelection()` → `submitAdjustLog()`。

3. **`submitAdjustLog()`**：`collectSubmitFiles` 收集 File 到 `submitFiles`；`collectReportAttachmentIds` 收集已选报告库附件 ID；调 `submitAdjustMultipart('/api/v1/forbiddenPoolAdjust/addAdjustLogWithFiles', payload, submitFiles)`（multipart 入口；JSON 无附件入口为 `addAdjustLog`），`FormData`：`request` 为 JSON Blob，`files` 为 multipart 数组。成功后 `$message.success` + `backToList()`。

### 3.3 后端 checkCompanyAdjust → checkAdjust 完整逻辑

1. `validateCompanyCode` + `queryCompanyDetail`（null 抛 404「公司主体不存在或证券类型不属于 company」）。
2. `validateManualCheckPoolIds`：每个手工项 `targetPoolId` 必须 ∈ `{15,16,17}`，否则抛「禁投池调整手工目标池仅允许 15、16、17」。
3. 将 `ForbiddenPoolAdjustCheckReq` 转为 `AdjustCheckReq`（`securityCode=companyCode`），调本类内部 `checkAdjust(checkReq)`。

`checkAdjust` 五阶段（与证券池调库同源，操作 `forbiddenPoolAdjustMapper`）：

- **① 前置校验** `validateCheckAdjustReq`：`securityCode` 非空、`items` 非空。
- **② 参数初始化** `loadSharedData`：`querySecurityBoByCode(companyCode)`（null 抛「证券不存在」）、全量池 Map、`querySecurityCurrentPoolIdList`（`audit_status='20'`）、`queryAllPoolRelationList` → 三层嵌套 `poolRelationMap`、证券级标志（`querySecurityHasPendingProcess`/`querySecurityPendingProcessNodeLabel`/`querySecurityInObservePool`/`queryIssuerInObservePool`）。
- **③ 调入校验** `executeInAdjustCheck`，每项跑 `checkInConditions`（8 条规则按序）：

| # | 规则方法 | 失败原因 |
|---|---|---|
| 1 | `preCheckSecurityExpired` | 主体已到期 |
| 2 | `preCheckPendingProcess` | 主体存在进行中的调库流程 |
| 3 | `inCheckSecurityAlreadyInPool` | 主体已在目标池 |
| 4 | `inCheckPoolCapacity` | 目标池已达持仓上限 |
| 5 | `inCheckSourcePool` | 目标池配置了来源池限制（`source`） |
| 6 | `inCheckRestrictPool` | 主体在调入限制池中（`in_restrict`） |
| 7 | `inCheckMutexConflict` | 与互斥池不可同时调入（`in_mutex`） |
| 8 | `inCheckElasticPool` | 主体在调入弹性禁投池中（`in_soft_restrict`） |

  自动追加 `in_linked` 联动调入项（`itemTag='linkage'`）、`in_mutex` 配套调出项（`itemTag='mutex'`），`inheritManualItemFailure` 手工项失败则阻断自动项。

- **④ 调出校验** `executeOutAdjustCheck`，`checkOutConditions`（7 条）：到期 → 进行中 → `outCheckSecurityNotInPool` → `outCheckRestrictPool`（`out_restrict`）→ `outCheckMutexPool`（`out_mutex`）→ `outCheckMutexConflict` → `outCheckElasticPool`（`out_soft_restrict`）。自动追加 `out_linked` 联动调出项。
- **⑤ 流程类型判断** `resolveAdjustFlowOptions`（仅 `canAdjust && itemTag==='manual'`）：禁投池目标池（15/16/17）`pool_type` 为 `forbidden`/`observe`/`blacklist`，均非 `credit_bond`，故**只走默认调入/调出流程**（`normalInbound`/`normalOutbound`），使用目标池 `inFlowId/inFlowKey`、`outFlowId/outFlowKey`；简易/白名单/升降级流程对禁投池不生效。

### 3.4 后端 addCompanyAdjustLog → addAdjustLog 完整逻辑

入口 `addCompanyAdjustLog(req, files)`，`@Transactional(rollbackFor=Exception.class)`，五阶段：

1. `validateCompanyCode` + `queryCompanyDetail`（404 校验）。
2. `validateSubmitCompany`：`items` 非空；`securityType` 与 DB 一致；手工项 `targetPoolId` ∈ {15,16,17}。
3. `convertCompanySubmitReq`：把 `ForbiddenPoolAdjustSubmitReq` 转为 `SecurityPoolAdjustSubmitReq`（`securityCode=companyCode`、`securityShortName=companyShortName`）。**不设置 `securityInfo`**（主体信息只读，`postSubmitProcess` 跳过 `editSecurityInfoForAdjust`）。
4. 调本类 `addAdjustLog(submitReq, files)`：

   - **① 前置校验** `validateSubmitReq`：`securityCode`/`items`/每项 `adjustMode`/`targetPoolId` 非空。
   - **② 参数初始化** `loadSubmitSharedData`：取证券、全量池 Map、`currentPoolIds`、`poolRelationMap`、证券级标志；为每个唯一 flowId `buildFlowSnapshot`；`BatchNoContext`（时间片 `yyyyMMddHHmmss` + 调入/调出/无流程三个序号）。
   - **批次号规则** `buildAdjustBatchNo`：无流程 `BOND+batchTimeText+(3000+seq)`；调入 `+(1000+seq)`；调出 `+(2000+seq)`；同 `adjustGroupKey` 复用。
   - **直通判断** `isDirectFlow`：start 出边全部直达 end，或经发起人自动提交节点后到 end。
   - **③ 调入处理** `executeInboundSubmit`：
     - 直通：`buildAdjustLog` → `auditStatus='20'` → `addAdjustLog` → `bindSubmitAttachments` → 手工项 `createInitialSteps` → `addPoolStatus`（`audit_status='20'` 即时生效）→ **`syncCompanyBondsOnDirect(logBo)`**（主体级特有，见 3.5）。
     - 非直通：`auditStatus='00'` → `addAdjustLog` → `bindSubmitAttachments` → `createInitialSteps`（返回 true 即流程已到 end，升 `'20'` + `addPoolStatus` + `syncCompanyBondsOnDirect`）。
   - **④ 调出处理** `executeOutboundSubmit`：对称，生效操作 `deletePoolStatusSoft`（`UPDATE ip_pool_status SET is_deleted=1 WHERE security_code=? AND target_pool_id=? AND audit_status='20' AND is_deleted=0`），同样调 `syncCompanyBondsOnDirect`。
   - **⑤ 后续处理** `postSubmitProcess`：`securityInfo` 为 null，跳过。

### 3.5 主体级特有：syncCompanyBondsOnDirect（主体生效后自动同步旗下债券）

```
syncCompanyBondsOnDirect(companyLog):
  categoryType = queryCategoryTypeBySecurityType(companyLog.securityType)
  if !"company".equals(categoryType): return
  bonds = queryCompanyBondForAutoList(companyLog.securityCode)   // issuer_code=companyCode 的全部债券
  for bond in bonds:
    currentPoolIds = querySecurityCurrentPoolIdList(bond.windCode)
    inbound = (companyLog.adjustMode == '调入')
    if (inbound && currentPoolIds.contains(targetPoolId)) || (!inbound && !contains): continue
    autoLog = buildCompanyBondAutoLog(companyLog, bond)   // adjustType='自动调整', auditStatus='20'
    addAdjustLog(autoLog)
    if inbound: addPoolStatus(autoLog) else: deletePoolStatusSoft(bond.windCode, targetPoolId)
```

主体直通入池/出池后，旗下**全部债券**自动同步入/出同一目标池，每条债券写一条 `ip_adjust_log`（`adjust_type='自动调整'`，`audit_status='20'`）+ `ip_pool_status`。

### 3.6 涉及的数据库表与写入

| 表 | 操作 | 关键字段 |
|---|---|---|
| `ip_adjust_log` | INSERT（主体记录 + 旗下债券自动调整记录） | security_code(=companyCode 或 bond.windCode), adjust_type(手工调整/联动调整/互斥调整/**自动调整**), adjust_mode, adjust_batch_no, target_pool_id, pool_type(forbidden/observe/blacklist), flow_id/key/type, **audit_status**(00 或 20), adjuster_id/name, submit_time |
| `ip_pool_status` | INSERT（调入生效）/ UPDATE 软删（调出生效） | security_code, adjust_log_id, target_pool_id, pool_type, **audit_status='20'**, entry_time, is_deleted |
| `ip_adjust_step` | INSERT（初始 3 步 + 审批时按需创建） | adjust_log_id, adjust_batch_no, flow_node_id, node_type, approval_strategy, **step_status**(pending/auto_process/submit), handler_id/name |
| `rrs_securityinfo` | 读（主体行 `category_type='company'`，债券行 `category_type='bond'`，关联 `issuer_code`） | wind_code, short_name, full_name, security_type, comp_type, industry_name/2, rating_bondissuer, rating_outlook, inner_issuer_rating, issuer_code |
| `sys_attachment` | 绑定/复制附件 | adjustLogId, attachment_category(credit_report_hand/material_hand) |
| `wf_flow_*` | 只读（构建流程快照） | — |

---

## 4. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `queryCompanyPage` | companyCode, companyShortName, companyFullName, compType, industryName, companyRating, companyInnerRating, pageIndex, pageSize | `PageResult<ForbiddenPoolAdjustDto>` | 分页查询主体（JOIN `category_type='company'`，回填旗下债券数） |
| `queryCompanyDetail` | companyCode | `ForbiddenPoolAdjustDto` | 主体详情（null 抛 404） |
| `queryCompanyPoolStatus` | companyCode | `ForbiddenPoolAdjustDto.PoolStatusBundle` | 当前主体所在池 + 旗下债券所在池 |
| `queryCompanyBondList` | companyCode, targetPoolId | `List<ForbiddenPoolAdjustDto.CompanyBond>` | 主体旗下指定池中的债券明细 |
| `queryAdjustPoolList` | companyCode, adjustDirection(in/out), currentUserId | `List<PoolDto>` | 可调入/调出池（仅 15/16/17，含互斥关系与 currentCount） |
| `checkAdjust` | companyCode, companyShortName, securityType, items[{targetPoolId,targetPoolName,poolType,adjustMode}] | `AdjustCheckDto` | 主体调库可行性校验 + 自动联动/互斥项 + 流程候选 |
| `addAdjustLog`（application/json） | `ForbiddenPoolAdjustSubmitReq` | `ForbiddenPoolAdjustSubmitDto` | 提交调库申请（无附件） |
| `addAdjustLogWithFiles`（multipart/form-data） | `request`=JSON Blob + `files`=MultipartFile[] | `ForbiddenPoolAdjustSubmitDto` | 提交调库申请（带附件，**前端实际调用入口**） |
| `queryAdjustLogList` | companyCode, adjustBatchNo | `List<AdjustLogDto>` | 主体调库记录（无批次排除终态） |
| `queryAdjustStepList` | adjustLogId, adjustBatchNo | `List<IpAdjustStepDto>` | 流程步骤列表（批次号优先） |
| `attachments/queryAttachmentList` | adjustLogId | 附件列表 | 加载调库记录附件 |
| `attachments/downloadAttachment` | id | `ApiResponse<String>`（Base64） | 下载附件 |
| `reports/queryInReportPage` | pageIndex, pageSize, reportTitle, securityCode, reportType, crteTimeStart/End | PageResult | 内部报告分页 |
| `reports/queryOutReportPage` | 同上 | PageResult | 外部报告分页 |

> 路径均带前缀 `/api/v1/`；`attachments`、`reports` 前缀独立。`ForbiddenPoolAdjustSubmitDto`：`{ companyCode, submitCount, logIds:[...] }`。

---

## 5. 关键数据库表

### 5.1 `ip_adjust_log`（共享调库记录表）

主体调库写入时 `pool_type` 为 `forbidden`/`observe`/`blacklist`；`security_code` 存主体代码；旗下债券自动调整记录 `security_code` 存债券 `wind_code`，`adjust_type='自动调整'`，`adjust_reason='主体"XXX"调入/调出，旗下债券自动同步调整'`。`adjust_batch_no` 以 `BOND` 前缀。

### 5.2 `ip_pool_status`（投资池当前状态表）

字段与 `ip_adjust_log` 基本一致，额外 `adjust_log_id`。`audit_status='20'` 表示主体/债券在该池中已生效；调出生效 = `is_deleted=1`。

### 5.3 `ip_adjust_step`（流程步骤表）

`approval_strategy`：preempt=抢占 / all=会签 / initiator=发起人；`step_status`：pending/approve/reject/submit/auto_process/canceled；`process_action`：submit/approve/reject/auto_process/skipped。

### 5.4 `rrs_securityinfo` + `dict_security_type`

主体行 `dict_security_type.category_type='company'`，债券行 `category_type='bond'`，通过 `issuer_code` 关联。主体信息在禁投池调整中**全部只读**，不在提交时更新。

### 5.5 `ip_investment_pool` / `ip_pool_relation`

禁投池模块仅暴露 `id=15/16/17`（禁投池/观察池/黑名单质押库）；`ip_pool_relation` 的 `relation_type` 含 source/in_restrict/out_restrict/in_linked/out_linked/in_mutex/out_mutex/in_soft_restrict/out_soft_restrict。

---

## 6. 状态流转

### 6.1 audit_status 枚举（与证券池调库完全复用）

| code | 名称 | 含义 |
|---|---|---|
| -1 | 无效调整 | 批量校验不通过 |
| 00 | 流程中 | 非直通流程提交 |
| 11 | 驳回待修改 | 复核节点 reject |
| 20 | 审批通过 | 直通 / 审批通过，`ip_pool_status` 即时落地 |
| 21 | 审批驳回 | 审批节点 reject |
| 32 | O32自动审批 | 字典保留 |
| 99 | 发起人已撤回 | 修改节点 reject |

### 6.2 流转条件（申请阶段）

- 直通流程（无流程 或 `isDirectFlow`）→ `audit_status='20'`，直接写/删 `ip_pool_status` + `syncCompanyBondsOnDirect`。
- 非直通流程 → `audit_status='00'`，`createInitialSteps` 创建 pending 步骤；若初始即到 end 则升级 `'20'` 并落地 + 同步债券。

后续审批推进见 [16-forbidden-pool-adjust-approve.md](16-forbidden-pool-adjust-approve.md)。

---

## 7. 与证券池调库的差异

| 维度 | 证券池调库（security-level） | 禁投池调整（company-level） |
|---|---|---|
| 操作对象 | 单只证券 `securityCode` | 主体（发行人）`companyCode`（`category_type='company'`） |
| 列表查询 | `querySecurityPage` | `queryCompanyPage`（7 字段，JOIN `category_type='company'`） |
| 当前池展示 | 当前证券所在池 + 证券主体所在池 | **当前主体所在池 + 旗下债券所在池**（含债券数、查看明细） |
| 可调池范围 | 全量启用投资池（按权限） | **硬编码 15/16/17**，`ALLOWED_MANUAL_POOL_IDS` |
| `pool_type` | credit_bond 等 | forbidden/observe/blacklist |
| 主体信息 | 详情页约 28 字段可编辑 | **全部 disabled 只读**，`postSubmitProcess` 跳过 |
| 流程候选 | 信用债大库走白名单/简易/升降级 | **只走默认调入/调出流程** |
| 生效联动 | 仅落地单只证券 | **主体生效后自动同步旗下全部债券**（`syncCompanyBondsOnDirect`） |
| Service | `SecurityPoolAdjustService` 独立 | `ForbiddenPoolAdjustService` **完整复制** security-pool 逻辑，操作 `forbiddenPoolAdjustMapper`，在生效点插入 `syncCompanyBondsOnDirect` |

---

## 8. 验收标准

- 提交成功后主记录、从属记录、批次号和初始步骤一致。
- 直通流程即时入池并同步旗下债券；非直通流程进入流程中。
- 手工项目标池必须为 15/16/17，否则校验拦截。
- 仅 `audit_status='20'` 落地 `ip_pool_status` 并触发债券同步。
- `ForbiddenPoolAdjustApiTest` 覆盖查询、校验、提交和债券同步业务线。

## 9. 关键源码索引

- 前端：`znty-rrs-ui/forbidden_pool_adjust.html`（列表、详情步骤 1/2、流程弹窗、报告弹窗、`goToStep2`、`submitAdjustLog`、`submitAdjustMultipart`、`openCompanyBondDialog`）
- 前端公共：`znty-rrs-ui/js/api.js`、`css/forbidden_pool_adjust.css`
- dict.js / 内联字典：`poolTypeLabel`（forbidden:'禁投池', observe:'观察池', blacklist:'黑名单'）、`auditStatusLabel`/`auditStatusTagType`
- Controller：`ForbiddenPoolAdjustController.java`（`@RequestMapping("/api/v1/forbiddenPoolAdjust")`，11 端点）
- Service：`ForbiddenPoolAdjustService.java`（`queryCompanyPage`/`queryCompanyDetail`/`queryCompanyPoolStatus`/`queryCompanyBondList`/`checkCompanyAdjust`/`addCompanyAdjustLog`/`checkAdjust`/`addAdjustLog`/`checkIn/OutConditions`/`isDirectFlow`/`createInitialSteps`/`buildAdjustBatchNo`/`syncCompanyBondsOnDirect`/`buildCompanyBondAutoLog`，`ALLOWED_MANUAL_POOL_IDS={15L,16L,17L}`）
- Mapper：`ForbiddenPoolAdjustMapper.java` / `ForbiddenPoolAdjustMapper.xml`
- 实体：`entity/forbiddenpooladjust/`（`ForbiddenPoolAdjustReq`/`Dto`（含内部类 `CompanyBondCount`/`PoolStatus`/`CompanyBond`/`PoolStatusBundle`）/`CheckReq`/`SubmitReq`/`SubmitDto`）；复用 `entity/securitypooladjust/`（`AdjustCheckDto`/`AdjustCheckReq`/`AdjustLogDto`/`AdjustSubmitDto`/`IpAdjustStepDto`/`PoolDto`/`SecurityPoolAdjustSubmitReq`）
- SQL：`sql/rrs_security_pool_adjust_schema.sql`（共享 `rrs_securityinfo`/`ip_adjust_log`/`ip_pool_status`/`ip_adjust_step`）、`sql/rrs_pool_init_schema.sql`（`ip_investment_pool`/`ip_pool_relation`）、`sql/rrs_dict_schema.sql`（`dict_security_type`）、`sql/rrs_flow_definition_schema.sql`（流程表）
- 测试：`ForbiddenPoolAdjustApiTest.java`、`ForbiddenPoolAdjustServiceTest.java`
