# CRMW池调库申请（调入/调出）需求说明

> 前端页面：`crmw_pool_adjust.html`（列表 + 详情两视图）
> 后端前缀：`/api/v1/crmwPoolAdjust`
> 角色定位：业务人员先选择一个 CRMW 凭证，再选择可绑定的标的证券（不能是 CRMW 凭证），在权限范围内发起 CRMW 池的调入或调出申请。逻辑与证券池调库（[04]）同构，差异见第 7 节。

---

## 1. 页面概览

单 Vue 实例（`el: '#crmw_pool_adjust'`），`currentPage` 在 `'list'`/`'detail'` 两视图间切换：

- **list 页**：CRMW 凭证 section（单选 radio + 分页表格）+ 可绑定证券搜索区 + 可绑定证券分页表格。
- **detail 页**：返回按钮 + 证券基本信息 + CRMW 基本信息（只读）+ 当前所在池 + 调库操作卡（步骤 1 选池 / 步骤 2 校验确认）+ 流程选择弹窗 + 信评报告选择弹窗。

**初始化**（`created`）：`this.loadCrmwList()` + `this.loadList()`（并发加载 CRMW 凭证列表与可绑定证券列表）。`baseURL` 由 `js/api.js` 注入（`http://localhost:18090`）。默认用户 `currentLoginUserId='1'`、`currentLoginUserName='管理员'`。

---

## 2. 筛选与查询逻辑

### 2.1 CRMW 凭证列表（`crmwSearchForm`）

| 字段 | 控件 | 匹配 |
|---|---|---|
| `securityCode` | el-input | 模糊（`wind_code`） |
| `securityShortName` | el-input | 模糊（`short_name`） |
| `issuer` | el-input | 模糊 |

- 接口：`POST /api/v1/crmwPoolAdjust/queryCrmwPage`，请求体 `{ securityCode, securityShortName, issuer, pageIndex, pageSize }`，返回 `PageResult<SecurityInfoDto>`。
- SQL（`queryCrmwPage`）：`FROM rrs_securityinfo si LEFT JOIN dict_security_type dst ... WHERE si.security_type='crmw'`，按 `wind_code`/`short_name`/`issuer` 模糊，`ORDER BY si.ts DESC, si.wind_code DESC`。
- 分页：`crmwPagination: {pageIndex:1, pageSize:5, total:0}`，`page-sizes=[5,10,20,50]`。

### 2.2 可绑定证券列表（`searchForm`）

字段同上（`securityCode`/`securityShortName`/`issuer`）。

- 接口：`POST /api/v1/crmwPoolAdjust/queryBindableSecurityPage`，返回 `PageResult<SecurityInfoDto>`。
- SQL（`queryBindableSecurityPage`）：`WHERE (si.security_type IS NULL OR si.security_type != 'crmw')` — **排除 CRMW 凭证**。
- 分页：`pagination: {pageIndex:1, pageSize:5, total:0}`，`page-sizes=[5,10,20,50,100]`。

### 2.3 CRMW 凭证选择（`handleCrmwSelect`）

单选 radio 选中 CRMW 凭证后调 `POST /api/v1/crmwPoolAdjust/queryCrmwDetail { securityCode }` → `crmwDetail`。后端 `queryCrmwDetail`：查 `querySecurityDetail(securityCode)`，null → `BizException(404,"CRMW凭证不存在")`，`securityType != 'crmw'` → `BizException("所选证券不是 CRMW 凭证")`。

### 2.4 进入详情页（`handleAdjust`）

校验 `selectedCrmw.windCode` 非空，否则 `$message.warning('请先选择一个CRMW凭证')`；读 URL 参数 `targetPoolId/adjustLogId/adjustBatchNo`；`currentPage='detail'`，调 `loadDetailData(securityCode)`。

### 2.5 详情页初始化（`loadDetailData`，`Promise.all` 并发 5 接口）

| 接口 | 请求体 | 用途 |
|---|---|---|
| `querySecurityDetail` | `{ securityCode }` | 标的证券基本信息 → `bondDetail` |
| `queryCrmwAdjustPoolList` | `{ securityCode, adjustDirection:'in', currentUserId }` | 可调入池 |
| `queryCrmwAdjustPoolList` | `{ securityCode, adjustDirection:'out', currentUserId }` | 可调出池 |
| `queryCrmwPoolStatus` | `{ securityCode }` | 当前证券所在池 + 主体所在池 |
| `queryCrmwAdjustLogList` | `{ securityCode, adjustBatchNo }` | 历史调库记录（不传批次仅返回未终结流程：`audit_status NOT IN ('-1','20','21','99')`） |

随后串行：构建 `currentSecurityPoolIds`、过滤调出池（仅保留证券已在其中的池及父节点）、过滤调入池（排除证券已在其中的叶子节点）、`buildPoolTree`、构建 `inMutexMap`/`outMutexMap`、`loadLogAttachments`、`loadFlowSteps`。

---

## 3. 调入 / 调出 申请逻辑

### 3.1 用户操作步骤

**步骤 1（选池）**：左右双栏树表格（`row-key="id"`，`default-expand-all`，仅叶子可勾选）：左「可调入库」（绿），右「可调出库」（红）。每叶子行展示投资池名称、上限数量（`maxCapacity`）、现有数量（`currentCount`）、信评报告列、其他材料列。

**前端互斥校验**：`handleInPoolSelect`/`handleOutPoolSelect` 检查 `inMutexMap`/`outMutexMap`，冲突弹 warning 并取消勾选。

**信评报告 / 其他材料**：`openReportDialog(poolId,'credit'|'material')` 打开弹窗，含内部报告 Tab（`/api/v1/reports/queryInReportPage`）与外部报告 Tab（`/api/v1/reports/queryOutReportPage`），支持筛选 + 分页；`el-upload auto-upload=false` 暂存 File。

**步骤 2（校验确认）**：显示「调库校验结果」表格（`adjustReviewList`）与「原因和建议」区。

### 3.2 接口调用顺序

1. **`goToStep2()`**：`syncSelectedPoolsFromTables()` 规范化；组装 `items`（调入 `adjustMode:'调入'`，调出 `adjustMode:'调出'`）；调 `POST /api/v1/crmwPoolAdjust/checkCrmwAdjust`，请求体 `AdjustCheckReq`：`{ securityCode, securityShortName, securityType, items:[{targetPoolId,targetPoolName,poolType,adjustMode}] }`；用 `adjustCheckSeq` 防旧请求覆盖；返回 `AdjustCheckDto` 映射为 `adjustReviewList`，`adjustStep=2`。

2. **`handleSubmit()`**：校验 `validCount>0` 且 `allValidRowsHaveFlow`；打开流程选择弹窗，为每条 `validManualAdjustReviewList` 选流程，`confirmFlowSelection()` → `submitAdjustLog()`。

3. **`submitAdjustLog()`**：`collectSubmitFiles` 收集 File；`collectReportAttachmentIds` 收集已选报告库附件 ID；调 `submitAdjustMultipart('/api/v1/crmwPoolAdjust/addCrmwAdjustLogWithFiles', payload, submitFiles)`（multipart 入口；JSON 无附件入口为 `addCrmwAdjustLog`），`FormData`：`request` 为 JSON Blob，`files` 为 multipart 数组。成功后 `$message.success` + `backToList()`。

### 3.3 后端 checkCrmwAdjust 完整逻辑

入口 `CrmwPoolAdjustService.checkCrmwAdjust(AdjustCheckReq)`，五阶段：

1. **前置校验** `validateCheckAdjustReq`：`securityCode` 非空、`items` 非空、每项 `poolType` 必须 `PoolType.CRMW.getCode()`（`'crmw'`），否则 `BizException("CRMW池调整仅支持选择 CRMW库")`。
2. **参数初始化** `loadSharedData`：
   - `querySecurityBoByCode(securityCode)`：null → `"证券不存在"`；`securityType=='crmw'` → `"调库对象不能是 CRMW 凭证"`。
   - 全量 `investmentPoolMapper.queryPoolList()` → `poolMap`；`validateCheckTargetPools` 校验目标池均为 CRMW 池。
   - `querySecurityCurrentPoolIdList`（`ip_pool_status_crmw` 中 `audit_status='20' AND pool_type='crmw'`）→ `currentPoolIds`。
   - `queryAllPoolRelationList`（`ip_pool_relation`）→ 三层嵌套 `poolRelationMap`。
   - 证券级标志：`querySecurityHasPendingProcess`、`querySecurityPendingProcessNodeLabel`、`querySecurityInObservePool`、`queryIssuerInObservePool`。
3. **调入校验** `executeInAdjustCheck`，每项跑 `checkInConditions`：先 `checkCommonIn`（通用：池锁定/品种/市场/pending/重复入池/容量/来源/限制/互斥/弹性/禁投池/行业/开放日），再按 `categoryType` 路由类型特有校验（债券 `inCheckBondMaturity`，股票 `inCheckStockDelist`/股票入池评级限制，基金 `inCheckFundRate`）。规则明细与 [04](04-security-pool-adjust.md) 3.6 节③同构。自动追加 `in_linked` 联动调入项（`itemTag='linkage'`）、`in_mutex` 配套调出项（`itemTag='mutex'`），`inheritManualItemFailure` 手工项失败则阻断自动项。

4. **调出校验** `executeOutAdjustCheck`，每项跑 `checkOutConditions`：`checkCommonOut`（8 条通用：池锁定/pending/不在池/冻结期/限制/互斥/弹性）+ 类型特有（债券 `outCheckBondMaturity`/股票 `outCheckStockDelist`）。规则明细与 [04](04-security-pool-adjust.md) 3.6 节④同构。自动追加 `out_linked` 联动调出项。
5. **流程类型判断** `resolveAdjustFlowOptions`（仅 `canAdjust && itemTag='manual'`）：
   - 调出：`normalOutbound`，用目标池 `outFlowId/outFlowKey`。
   - 调入·命中特殊审批：若标的证券当前已在目标池 `in_mutex` 互斥池中，优先走 `specialInbound`（`bond:special-inbound`）；**信用债大库目标池默认排除**。
   - 调入·非信用债大库（`poolType != 'credit_bond'`）：`normalInbound`。
   - 调入·已在信用债大库：`resolveCreditBondAdjustFlowType` 按 `innerSort` 判断上调（`upgradeInbound`）/下调（`downgradeInbound`）。
   - 调入·不在信用债大库：白名单（默认关闭）→ 简易（`simpleInbound`）→ 默认调入，推荐优先级 白名单 > 简易 > 默认。

### 3.4 后端 addCrmwAdjustLog 完整逻辑

入口 `addCrmwAdjustLog(req, files)`，`@Transactional(rollbackFor=Exception.class)`，五阶段：

1. **前置校验** `validateSubmitReq`：`securityCode/crmwScode/crmwName` 非空、`crmwStype` 必须 `'crmw'`（否则 `"CRMW证券类型必须为 crmw"`）、`items` 非空、每项 `adjustMode/targetPoolId` 非空、`poolType` 必须 CRMW（否则 `"CRMW池调整仅支持选择 CRMW库"`）。
2. **参数初始化** `loadSubmitSharedData`：取证券（null→"证券不存在"；`securityType=='crmw'`→"调库对象不能是 CRMW 凭证"）、全量池 Map、`validateSubmitTargetPools` 校验目标池均为 CRMW、`currentPoolIds`（`ip_pool_status_crmw` `audit_status='20'`）、`poolRelationMap`、证券级标志；为每个唯一 flowId `buildFlowSnapshot`；`BatchNoContext`。
3. **调入处理** `executeInboundSubmit`，对每个调入项：
   - **报告必填校验** `checkReportRequired`（按 `in_report_restriction` none/any/internal）+ **CRMW组合校验** `checkCrmwInboundCombination`：凭证已在池（`queryCrmwAlreadyInPool` 查 `ip_pool_status_crmw` 同凭证+目标池 `audit_status='20'`）/ 凭证审批中（`queryCrmwPendingWorkflow` 查 `ip_adjust_log`+`ip_adjust_step` 同凭证 pending 步骤），不通过抛 `BizException` 中断。对应老项目 `checkBasisAndProductInPool` 的 `checkCrmwInPool`+`checkPoolCrmwWorkflow`；「池必填凭证」已在 `validateSubmitReq` 强制（CRMW 提交必带 `crmwScode`）。
   - `resolveManualSubmitItem` 取同组手工项；`isDirect = noFlow || isDirectFlow(snapshot)`。
   - **直通**：`buildAdjustLog` → `auditStatus=APPROVED('20')` → `addAdjustLog`（写 `ip_adjust_log`）→ `bindSubmitAttachments` → 手工项 `createInitialSteps` → `addPoolStatus`（写 `ip_pool_status_crmw`，`audit_status='20'` 即时生效）。
   - **非直通**：`auditStatus=SUBMITTED('00')` → `addAdjustLog` → `bindSubmitAttachments` → `createInitialSteps`（返回 true 即流程已到 end，升 `'20'` + `addPoolStatus`）。
4. **调出处理** `executeOutboundSubmit`：对称，先跑报告必填校验（`out_report_restriction`）+ **CRMW组合校验** `checkCrmwOutboundCombination`（组合在池：`queryCrmwComboInPool` 查 `ip_pool_status_crmw` 同凭证+标的证券+目标池 `audit_status='20'`，组合不在池则抛 `BizException`，对应老项目 `checkOutPool` 的 `checkCrmwInPool` 组合维度），生效操作 `deletePoolStatusSoft`（`UPDATE ip_pool_status_crmw SET is_deleted=1 WHERE security_code AND crmw_scode AND crmw_stype AND target_pool_id AND audit_status='20' AND pool_type='crmw'`），避免误删同一标的证券在同一池的其他 CRMW 凭证记录。新系统 `crmwScode` 使用完整 Wind 代码且全局唯一，市场代码不参与业务键。
5. **后续处理** `postSubmitProcess`：若 `req.securityInfo` 非空，`buildMergedSecurityInfo` → `editSecurityInfoForAdjust`（全量更新 `rrs_securityinfo` 约 80 字段）。

**批次号规则** `buildAdjustBatchNo`：
- 无流程：`"CRMW" + batchTimeText + (3000 + ++noFlowBatchSeq)`
- 调入：`"CRMW" + batchTimeText + (1000 + ++inboundBatchSeq)`
- 调出：`"CRMW" + batchTimeText + (2000 + ++outboundBatchSeq)`
- 同 `adjustGroupKey` 复用同一批次号。

**直通流程判断** `isDirectFlow` / **createInitialSteps**（懒创建）：逻辑与 [04] 完全一致。

---

## 4. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `queryCrmwPage` | securityCode, securityShortName, issuer, pageIndex, pageSize | `PageResult<SecurityInfoDto>` | 分页查询 CRMW 凭证列表（`security_type='crmw'`） |
| `queryBindableSecurityPage` | securityCode, securityShortName, issuer, pageIndex, pageSize | `PageResult<SecurityInfoDto>` | 分页查询可绑定证券（排除 crmw） |
| `queryCrmwDetail` | securityCode | `SecurityInfoDetailDto` | CRMW 凭证详情（必须 `security_type='crmw'`） |
| `querySecurityDetail` | securityCode | `SecurityInfoDetailDto` | 标的证券详情（不能是 crmw） |
| `queryCrmwAdjustPoolList` | securityCode, adjustDirection, currentUserId, targetPoolId | `List<PoolDto>` | 可调入/调出 CRMW 投资池列表（含互斥关系） |
| `queryCrmwPoolStatus` | securityCode | `SecurityPoolStatusDto` | 证券/主体当前所在 CRMW 池 |
| `checkCrmwAdjust` | securityCode, securityShortName, securityType, items[{targetPoolId,targetPoolName,poolType,adjustMode}] | `AdjustCheckDto` | 调库可行性预校验 + 流程候选 |
| `addCrmwAdjustLog`（application/json） | `CrmwPoolAdjustSubmitReq` | `AdjustSubmitDto` | 提交调库申请（无附件） |
| `addCrmwAdjustLogWithFiles`（multipart/form-data） | `request`(JSON Blob) + `files`(MultipartFile[]) | `AdjustSubmitDto` | 提交调库申请（带附件，**前端实际调用入口**） |
| `queryCrmwAdjustLogList` | securityCode, adjustBatchNo | `List<AdjustLogDto>` | 历史调库记录（无批次仅返回未终结流程） |
| `queryCrmwAdjustStepList` | adjustLogId, adjustBatchNo | `List<IpAdjustStepDto>` | 同批次流程步骤列表 |
| `attachments/queryAttachmentList` | adjustLogId | 附件列表 | 加载调库记录附件 |
| `attachments/downloadAttachment` | id | `ApiResponse<String>`（Base64） | 下载附件 |
| `reports/queryInReportPage` / `queryOutReportPage` | 分页+筛选 | PageResult | 信评报告弹窗内/外报告查询 |

> 路径均带前缀 `/api/v1/`；`attachments`、`reports` 前缀独立。`CrmwPoolAdjustSubmitReq` 多 `crmwName/crmwScode/crmwStype` 字段，`crmwStype` 必须为 `'crmw'`。

---

## 5. 关键数据库表

### 5.1 `ip_pool_status_crmw`（CRMW 当前状态表，独立）

直通/审批通过时 INSERT（调入）或 UPDATE 软删（调出）。表结构保留 `crmw_mktcode` 兼容历史数据，新调库不再写入或按该字段查询；`audit_status='20'` 表示已生效；调出生效 = `is_deleted=1`。

### 5.2 `ip_adjust_log`（共享调库记录表）

写入时 `pool_type='crmw'`，写入 `crmw_name/crmw_scode/crmw_stype`；物理字段 `crmw_mktcode` 仅为历史兼容保留。`adjust_batch_no` 以 `CRMW` 前缀，`audit_status` 枚举见第 6 节。

### 5.3 `ip_adjust_step`（共享流程步骤表）

`createInitialSteps` 写入前 3 步；`step_status`：pending/approve/reject/submit/auto_process/canceled。

### 5.4 其他

`ip_investment_pool`（过滤 `pool_type='crmw'`）、`ip_pool_relation`、`rrs_securityinfo`、`dict_security_type`、`wf_flow_*`（流程定义，只读构建快照）、`sys_attachment`。

---

## 6. 状态流转

### 6.1 audit_status 枚举（与全站一致）

| code | 名称 | 含义 |
|---|---|---|
| -1 | 无效调整 | 批量校验不通过 |
| 00 | 流程中 | 非直通流程提交 |
| 11 | 驳回待修改 | 复核节点 reject |
| 20 | 审批通过 | 直通 / 审批通过，`ip_pool_status_crmw` 即时落地 |
| 21 | 审批驳回 | 审批节点 reject |
| 32 | O32自动审批 | 字典保留 |
| 99 | 发起人已撤回 | 修改节点 reject |

### 6.2 流转条件（申请阶段）

- 直通流程（无流程 或 `isDirectFlow`）→ `audit_status='20'`，直接写/删 `ip_pool_status_crmw`。
- 非直通流程 → `audit_status='00'`，`createInitialSteps` 创建 pending 步骤；若初始即到 end 则升级 `'20'` 并落地。

后续审批推进见 [20-crmw-pool-adjust-approve.md](20-crmw-pool-adjust-approve.md)。

---

## 7. 与其他池模块的差异

- **两步选择**：列表页先选 CRMW 凭证（radio），再选可绑定标的证券；标的证券不能是 CRMW 凭证（`queryBindableSecurityPage` 排除 `security_type='crmw'`）。
- **独立状态表**：`ip_pool_status_crmw` 使用 `crmw_scode + security_code` 标识凭证与标的组合；`crmw_mktcode` 字段仅兼容历史数据；证券池调库用 `ip_pool_status`。
- **批次号前缀**：`CRMW`（证券池用 `BOND`）。
- **校验强制 poolType**：`validateCheckAdjustReq`/`validateSubmitReq`/`validateSubmitTargetPools` 均强制 `PoolType.CRMW.getCode()='crmw'`。
- **提交参数**：`CrmwPoolAdjustSubmitReq` 多 CRMW 元数据字段，`crmwStype` 必须为 `'crmw'`。
- **详情页多 CRMW基本信息 section**（只读展示 CRMW 全称/名称/代码/证券类型/发行人）。
- 审批流转、校验规则、流程快照、状态枚举与证券池调库**完全同构**。

---

## 8. 验收标准

- 提交成功后主记录、从属记录、批次号和初始步骤一致；批次号以 `CRMW` 前缀。
- 直通流程即时入池 `ip_pool_status_crmw`；非直通流程进入流程中。
- 目标池必须为 CRMW 池（`pool_type='crmw'`），否则校验拦截。
- 调库对象不能是 CRMW 凭证本身。
- 仅 `audit_status='20'` 落地 `ip_pool_status_crmw`。

## 9. 关键源码索引

- 前端：`znty-rrs-ui/crmw_pool_adjust.html`（`loadCrmwList`、`loadList`、`handleCrmwSelect`、`loadDetailData`、`goToStep2`、`submitAdjustLog`、`submitAdjustMultipart`、流程弹窗、报告弹窗）
- Controller：`CrmwPoolAdjustController.java`（`@RequestMapping("/api/v1/crmwPoolAdjust")`）
- Service：`CrmwPoolAdjustService.java`（`checkCrmwAdjust`、`addCrmwAdjustLog`、`checkInConditions`、`checkOutConditions`、`isDirectFlow`、`createInitialSteps`、`buildAdjustBatchNo`、`resolveAdjustFlowOptions`）
- Mapper：`CrmwPoolAdjustMapper.java` / `resources/mapper/CrmwPoolAdjustMapper.xml`
- 实体：`entity/crmwpooladjust/`（`CrmwPoolAdjustReq`、`CrmwPoolAdjustSubmitReq`、`AdjustCheckReq`、`AdjustCheckDto`、`AdjustSubmitDto`、`AdjustLogDto`、`PoolDto`、`SecurityInfoDto`、`SecurityInfoDetailDto`、`SecurityPoolStatusDto`、`IpAdjustStepDto` 等）
- SQL：`sql/rrs_external_import_schema.sql`（`rrs_securityinfo`）、`sql/rrs_crmw_pool_status_schema.sql`、`sql/rrs_security_pool_adjust_schema.sql`（`ip_adjust_log`）、`sql/rrs_pool_init_schema.sql`（`ip_pool_relation`）
- 测试：`CrmwPoolAdjustApiTest.java`
