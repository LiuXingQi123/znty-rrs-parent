# 证券池批量调整（批量调入 / 批量调出）需求说明

> 前端页面：`batch_security_pool_adjust.html`（主页面，自包含两步式工作台）、`batch_security_pool_adjust_select.html`（独立选择屏，**当前未与主页打通**）
> 后端前缀：`/api/v1/batchSecurityPoolAdjust`
> 角色定位：业务人员选定一个目标投资池与方向后，批量勾选多只证券一次性发起调入/调出申请。

### 同券关联市场范围

同券关联市场与投资池投资市场共用 `MarketCode` 集合（`SSE/SZSE/NIB/BSE/COMPANY/OTC/HKEX/QDII/JWCW/UNKNOWN/OTHER`），不另设白名单。先按 Wind `EXCHMARKET` 筛选主代码和关联代码，再查询本地主档；范围外代码不参与关联展示、关联校验和关联出入库，缺少主档不阻断，范围内缺少主档或市场仍报错。不按代码后缀或 `S_INFO_EXCHMARKET` 推断；目标池勾选的市场仍是逐券入池限制，不用于裁剪同券集合。证券池、ABS、CRMW、批量、Excel 及临时转正复用同一查询。


## 列表查询字段

- 查询条件新增主体编码 `issuerCode`、证券全称 `securityFullName`，分别对主档 `issuer_code`、`full_name` 做包含式模糊查询；留空不限制，与既有条件按 AND 组合
- 列表展示主体编码 `issuerCode`、证券全称 `fullName`，已有证券全称列直接保留；值为空时留白
- 原“发行人”“发行主体全称”查询条件及对应列表列统一显示为“主体全称”，继续使用 `issuer` 字段及原有匹配规则
- 新增条件支持回车查询，查询回到第一页，重置时清空；证券基础信息等非查询、非列表区域保持原样
- 批量页仅修改工作台内“可选证券”区域，初始投资池列表保持原样；重新进入调入/调出时清空新增条件

“是否特征”追加“是否标准信用债”（`bondYesFlags` 选项 `stdCredit`），勾选仅筛选证券主档 `std_credit_flag=1`，与其他特征按 AND 组合；不勾选时不限制该字段。对应列表追加 `stdCreditFlag` 列：1=是，其他值（含 NULL）按现有是否类标识显示否，接口保留原始数值，沿用是=`success`、否=`info` 的 Tag。仅使用上游维护的原始标志，不按其他特征或 `std_clause_flag` 推断；CRMW 按标的证券筛选及展示。

## 同券跨市场整组校验

同一非空 ISIN 的主券与关联券逐码校验后，同一调库组（`adjustGroupKey`）任一项失败即整组不可调整；保留失败代码和具体原因，不允许跳过失败券而只提交其他市场。其他独立调库组不受影响。

“放开规则”由主券传递给所有关联券，仅跳过既有主体债入库矩阵规则；在途流程、市场、品种、到期、容量及其他硬限制仍按每个实际代码校验。

单笔提交携带基本信息已查询的 `relatedMarketCodes: [{ windCode, marketCode }]`；后端直接使用传入集合核对各目标池的调入明细是否带齐主券及关联券，提交阶段不再查询同券关系。批量及 Excel 入口复用本次校验结果中的实际代码集合。逐券活动流程检查继续执行。

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
- 视图 A `pageMode === 'poolList'`：投资池筛选面板（投资池树多选 popover + 查询/重置）+ 投资池表格（序号/全路径名/描述/投资市场/投资品种/上限数量/现有数量/操作[调入|调出]）+ 分页。**现有数量**按类型分项展示（如 `主体：1只` / `债券：120只` / `CRMW：n只`）：统计 `ip_pool_status` 全部有效在池，用池状态 `security_type` 关联 `dict_security_type`（**不经** `rrs_securityinfo`，主体无证券主数据行也能计入）；`crmw`/`company` 单独分类，其余按 `category_type`；前端字典转中文。点「调出」后的可选证券包含 CRMW，仍排除主体和退市证券，故单行总数不必与可选 total 相等。
- 视图 B `pageMode === 'adjustWorkbench'`：工作台头（返回 + 池名 + 方向标签 + 汇总 chips：证券/信评报告/其他附件/步骤 x/2）。
  - 步骤 1（`adjustStep === 1`）：「调整材料」折叠卡（信评报告、其他附件、是否放开规则、已选证券）+「可选证券」候选表格（跨页多选）。
  - 步骤 2（`adjustStep === 2`）：「调库校验结果」表格（逐条展示可/不可调整 + 行内审批流程下拉 + 材料计数 + 调整说明）+「原因和建议」（调整原因/调整建议 textarea）+ 操作底栏（上一步/提交）。
- 工作台内容区独立滚动并为固定操作底栏预留安全区，步骤 1 候选表格分页和步骤 2 末尾内容均不得被底栏遮挡。
- 报告选择弹窗：内部报告/外部报告双 Tab，带类型快筛、关键字/证券编码/撰写日期范围筛选、分页、附件下载、已选汇总。

**初始化**（`mounted → initPage`）：
1. `axios.defaults.baseURL = 'http://localhost:18090'`
2. `apiPost('/api/v1/common/queryPoolTreeList', { excludePoolTypes: ['crmw'], includeVarietyCodes: ['bond'] })` → 构建投资池筛选树（排除 CRMW，只保留投资品种包含债券的池及其上级；树默认收起）。
3. `loadPoolPage()` → `apiPost('/api/v1/batchSecurityPoolAdjust/queryPoolPage', {...})`（启用叶子池，SQL `pool_type != 'crmw'` 且 `variety_codes` 包含 `bond`）。

---

## 2. 批量选择证券逻辑

### 2.1 候选证券筛选条件（主页面内嵌表）

| 控件 | 字段 | 说明 |
|---|---|---|
| 文本输入 | `securityCode` | 证券代码 |
| 文本输入 | `issuer` | 主体全称，仅按名称模糊查询，调入、调出均支持；留空不限制，与其他条件按 AND 组合 |
| 文本输入 | `securityShortName` | 证券简称 |
| 文本输入 | `securityFullName` | 证券全称 |
| 文本输入 | `issuerCode` | 主体编码 |
| 多选 | `marketCodes` | 市场（SSE=上海证券交易所 / SZSE=深圳证券交易所 / NIB=银行间市场 / BSE=北京证券交易所 / COMPANY=主体 / OTC=场外市场 / QDII=其他QDII市场 / OTHER=其他） |
| 多选 | `bondYesFlags` | 是否特征：ABS/担保/永续/次级/私募/含权/标准信用债；勾选即筛「是」，多选 AND |

主体全称输入框支持回车查询；查询时回到第一页，重置及重新进入调入/调出工作台时清空主体全称条件。

查询接口：`POST /api/v1/batchSecurityPoolAdjust/querySecurityPage`（`loadSecurityList`），请求体：
```json
{ "currentUserId": "1", "poolId": <Long>, "direction": "in|out",
  "issuer": null, "securityCode": null, "securityShortName": null, "securityFullName": null, "issuerCode": null, "marketCodes": null, "bondYesFlags": null,
  "pageIndex": 1, "pageSize": 20 }
```
返回 `{ records:[...], total, pageIndex, pageSize }`，`records` 为 `BatchSecurityCandidateDto`（含证券基础信息及 ABS/担保/永续/次级/私募/含权/标准信用债原始字段；`dateExists` 为剩余期限天数，`dateExistsStr` 为直接展示并供年口径判断解析的证券期限文本）。候选表按证券池调整页样式展示六个「是/否」Tag。
候选证券固定排除 `security_type='company'` 的公司主体，包含 CRMW 凭证；CRMW 可通过普通证券池单笔、批量调整进入债券产品库等普通池，目标投资池仍排除 `pool_type='crmw'`，凭证+标的组合走独立 CRMW 池链路。

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
2. `validateAdjustPoolPermission`：管理员(1)放行；否则校验当前用户对 poolId 拥有 adjustable 权限。
3. `resolveAdjustMode`：`in→调入`、`out→调出`。
4. **逐只证券循环**：对每个 `SecurityItem` 构造单证券校验请求 `buildSingleCheckReq`（目标池=当前 poolId，方向=中文），调用**单笔调库服务** `securityPoolAdjustService.checkBatchAdjust(singleReq)`，复用完整单笔校验并追加已配置的批量候选。
5. 单笔校验返回 `AdjustCheckDto.items`（可能含手工项 + 联动/互斥/关联码自动项），用 `buildBatchCheckResult` 转成批量结果项：
   - `adjustGroupKey` 改写为 `sourceSecurityCode + "_" + 原始groupKey`（触发主券前缀，related 与主券同批）。
   - 透传 `canAdjust`、`failReasons`、`flowOptions`（完整单笔候选 + 配置的 batchIn/Out，推荐批量）。

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

**后端 `addAdjustLog(req, files)`**（编排层；落库委托单券）：
1. `@Transactional(rollbackFor = Exception.class)`——**整个批量提交是一个事务**。
2. `validateAdjustSubmitReq`：poolId/direction/items/adjusterId/adjusterName 非空；目标池为启用叶子池；每条 item 的 securityCode 非空、手工项 `adjustMode` 必须等于本次方向、`targetPoolId` 非空。**不强制**每条都带 `flowId`/`flowKey`（无流程或直通流程允许空，与单券一致；前端可提交行仍会尽量选 recommended）。
3. `validateAdjustPoolPermission`：权限校验同校验阶段。
4. **整批防重复** `checkRecentBatchDuplicateSubmit`：约 30 秒窗口内，比较调整人最近一次「手动批量」手工项集合键（证券|目标池|方向|flowId|flowKey）与原因/意见文本，相同则拒绝。
5. **直通预检** `needsWholeBatchDirectRecheck`：任一手工项经 `securityPoolAdjustService.isDirectAdjustFlow(flowId, flowKey)` 判定为直通时，提交前调用 `recheckBeforeFinalApproval`（锁池 + 容量/在池/限制等动态复核），整批共用一次。
6. **按触发主券分组**：`resolveBatchSubmitGroupKey`（`sourceSecurityCode` 优先，否则 `securityCode`），保证 related 关联码与主券同组提交、共享批次与流程步骤。
7. `sysAttachmentService.createSubmissionFiles(files, adjusterId)`：multipart 包成 `SubmissionFiles`，**整批共用一份物理文件**。
8. `new SecurityPoolAdjustService.BatchNoContext()`：创建**批次号上下文**（`batchTimeText` yyyyMMddHHmmss + inbound/outbound/noFlow 序号），**整批共用**，多证券序号连续。
9. **逐组循环**：`buildSingleSubmitReq`（请求级证券取组内 manual 主券；`adjustType="手动批量调整"`；透传整批 reason/advice/adjuster 与每条明细的 flow/附件下标）→ 调用**单笔落库入口** `securityPoolAdjustService.submitAdjustLog(singleReq, submissionFiles, batchNoContext)`（非页面用的 `addAdjustLog`：后者自建附件上下文，批量需共享已创建的 `SubmissionFiles`/`BatchNoContext`）。
10. 累加 `submitCount`、收集 `logIds` 到返回 dto。

**返回结构** `BatchSecurityInboundAdjustDto`：`{ items:[], securityCount, submitCount, logIds:[...] }`。

### 3.4 后端批量校验逻辑（逐条列举，与单笔异同）

批量校验通过 `SecurityPoolAdjustService.checkBatchAdjust` 复用完整单笔校验，在生成单笔候选后追加配置的批量流程，其内部阶段：

1. **前置校验**（`validateCheckAdjustReq`）：securityCode/items 非空。
2. **参数初始化**（`loadSharedData`）：一次性查证券基础信息（兼存在性校验）、全量投资池 Map、证券当前有效入池 ID 集（audit_status='20'）、全量池关系三层 Map、证券级标志（是否有进行中流程 + 当前节点名、证券/主体是否在观察池、证券/主体是否在重点观察名单）、本次请求调入/调出目标池 ID 集。
3. **调入校验**（`executeInAdjustCheck`）：对每个手工调入项执行 `checkInConditions`，并按池关系自动追加联动调入项（`in_linked`/linkage）、互斥配套调出项（`in_mutex`/mutex）；自动项与手工项失败状态联动。
4. **调出校验**（`executeOutAdjustCheck`）：对每个手工调出项执行 `checkOutConditions`，追加联动调出项（`out_linked`）。
5. **流程类型判断**（`resolveAdjustFlowOptions`）：为每个可调整手工项生成 FlowOption 列表（同单笔规则，见 [04-security-pool-adjust.md](04-security-pool-adjust.md) §3.6 ⑤），命中当前已在目标池 `in_mutex` 互斥池时优先走 `specialInbound`（`bond:special-inbound`）；**信用债大库目标池默认排除**互斥特殊审批。

**调入校验规则顺序**（与单笔 `checkCommonIn` + 类型特有一致）：

池锁定 → 品种 → 市场 → pending → 已在目标池 → 容量 → 来源池 → 调入限制池(in_restrict) → 同请求互斥冲突 → 弹性禁投(in_soft_restrict，警告) → 全局禁止池 → **行业限制（已注释）** → 开放日 →（债券）到期 → 主体内评矩阵（期限口径同 [04]/[23]：普通债及含权赎回侧解析 `date_exists_str`，含权回售侧用年字段，两侧都有取更短；不再用 `date_exists` 总天数÷365；非 ABS 非担保债使用债务主体内评，非 ABS 担保债取债务主体与所选担保人内评较优值；ABS 直接使用自选权益人〔优先〕或普通权益人内评；私募、永续、次级、重点观察名单及特殊债降档规则与单券调库一致；可转债/可交换/CRMW 不适用 1～5；期限无法解析时默认最长档继续走矩阵；校验和提交阶段均重查所选主体及最新 `total_score`） /（股票）退市 → 评级限制（空实现）/（基金）评分（仅 check）。

批量页面对所有证券统一查询 `115004000=担保人`、`115203000=差额支付承诺人`、`115202000=债务主体`、`115201000=原始权益人` 四类关系主体。同一主体兼多类关系时按类型分别返回，仅对同证券、同主体、同类型去重；四类依次映射排序号 `1/2/3/4` 升序，同类型按内评时间倒序，页面默认选中第一条。非 ABS 页面展示为担保人，ABS 展示为权益人，并可从 `t_inv_company` 分页选择自选权益人；自选弹窗与单笔调库一致，单选列位于序号列左侧。ABS 的“担保人主体内评分”自选优先，否则显示普通权益人内评。展开项为“名称 + 类型标签 + 内评”，收起后为“名称（类型）”。

**调出校验规则顺序**（与单笔 `checkCommonOut` + 类型特有一致）：

池锁定 → pending → 未入池 → 冻结期（入池时间缺失报「证券入池生效时间缺失」，与单券相同）→ 调出限制池(out_restrict) → 调出互斥池(out_mutex) → 同请求互斥冲突 → 弹性禁投(out_soft_restrict，警告) → 开放日 →（债券/股票）到期或退市。

**流程展示与提交**：逐券复用单笔完整校验和匹配（白名单/信用债简易/产品库快速/默认/特殊/升降级等），再通过公共方法追加目标池配置的 `batchIn/batchOut` 专用流程并设为唯一推荐。仅配置了流程 ID 或 Key 时追加；不凭空生成批量无需审批选项。同一 ID/Key 的快速与批量候选按类型分别保留，材料豁免由主项资格决定，选批量流程也可豁免。前端默认选中 `recommended` 项，操作员可按行改选。提交透传所选 `flowId/flowKey/flowType`，不再用批量配置覆盖选择。

**与单笔的异同**：准入规则与单笔一致，候选额外追加并推荐配置的批量流程；区别在外层：①逐证券循环；②`adjustGroupKey` 加主券 `sourceSecurityCode_` 前缀；③互斥冲突仅单券内多目标池；④整批事务、防重复、直通预检按整批聚合。

### 3.5 后端批量提交逻辑

批量 Service **不复制**单券落库代码：每组调用 `SecurityPoolAdjustService.submitAdjustLog`，内部仍为单券五阶段（详见 [04-security-pool-adjust.md](04-security-pool-adjust.md) §3.7）：

1. **前置校验**（`validateSubmitReq`）。
2. **参数初始化**（`loadSubmitSharedData`，注入共享 `BatchNoContext`）。
3. **调入处理**（`executeInboundSubmit`）：直通判断用流程快照 `isDirectFlow`（与对外 `isDirectAdjustFlow` 口径一致）；直通写 `audit_status='20'` 并落 `ip_pool_status`，非直通写 `'00'` 并建初始步骤。
4. **调出处理**（`executeOutboundSubmit`）：对称；生效时 `deletePoolStatusSoft`。
5. **后续处理**（`postSubmitProcess`）：批量场景一般无 `securityInfo`，跳过主档合并快照或按单券规则处理。

**批次号规则**（单券 `buildAdjustBatchNo`）：`BOND + batchTimeText + 4位序号`；调入 `1000+`、调出 `2000+`、无流程 `3000+`。同 `adjustGroupKey` 共用批次号。批量共享同一个 `BatchNoContext`，跨主券组序号递增。

**附件绑定**（单券 `bindSubmitAttachments`）：按下标从**共享** `SubmissionFiles` 绑定；报告库附件 `copyReportAttachments`。同一物理文件可挂到多条 log。

**事务范围与部分失败**：整个批量提交在**单一 `@Transactional`** 内。任一证券任一步骤异常→**整体回滚**。**不存在「部分成功」**。

### 3.6 涉及的数据库表及字段写入

| 表 | 写入动作 | 关键字段 |
|---|---|---|
| `ip_adjust_log` | 单券 `submitAdjustLog` 内 insert | security_code, security_short_name, security_type, adjust_type(**手动批量调整**/联动/互斥/关联等), adjust_mode(调入/调出), adjust_batch_no, target_pool_id, target_pool_name, pool_type, flow_id/key/type, audit_status('00'待审/'20'通过), adjuster_id/name, adjust_reason/advice, submit_time, crte_time/updt_time, is_deleted=0 |
| `ip_pool_status` | `addPoolStatus`(调入生效) / `deletePoolStatusSoft`(调出生效) | 同上调库字段 + adjust_log_id(回链), audit_status='20', is_deleted |
| `ip_adjust_step` | `addAdjustStep`(每步一条，审批节点按处理人展开多条) | adjust_log_id, adjust_batch_no, flow_node_id, node_code/label/type, approval_strategy, sort_order, step_status(pending/submit/auto_process), handler_id/name, process_action, start_time/process_time |
| `sys_attachment` | `bindAttachments` / `copyReportAttachments` | table_name='ip_adjust_log', main_id=日志ID, attachment_category(credit_report_hand/material_hand/credit_report_in/out/...), file_type, original/new_file_name, file_size, content_type, full_url |

查询用到的表：`ip_investment_pool`（投资池，status='enabled' 且无启用子节点=叶子池）、`ip_pool_relation`（池关系）、`ip_pool_permission`（权限）、`rrs_securityinfo`（证券基础信息）、`wf_flow_definition/version/node/edge` 等（流程快照）。

---

## 4. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `common/queryPoolTreeList` | `{ excludePoolTypes: ['crmw'], includeVarietyCodes: ['bond'] }` | `List<PoolTreeDto>` | 主页面投资池筛选树，默认收起，只含投资品种包含债券的池及其上级 |
| `batchSecurityPoolAdjust/queryPoolPage` | currentUserId, poolIds, pageIndex, pageSize | `PageResult<BatchSecurityPoolDto>`（records:[id/poolName/poolFullName/poolType/marketCodes/varietyCodes/description/maxCapacity/currentCount], total） | 分页查询当前用户可调整的启用叶子投资池；**排除 CRMW 池，且 `variety_codes` 包含 `bond`**；候选证券包含 CRMW 凭证、排除 `security_type='company'` |
| `batchSecurityPoolAdjust/querySecurityPage` | currentUserId, poolId, direction(in/out), securityCode, securityShortName, securityFullName, issuerCode, marketCodes, bondYesFlags, pageIndex, pageSize | `PageResult<BatchSecurityCandidateDto>` | 分页查询目标池候选证券；特征多选按 AND |
| `batchSecurityPoolAdjust/checkAdjust` | `BatchSecurityInboundAdjustReq`: currentUserId, direction, poolId, poolName, poolType, securities:[{securityCode,securityShortName,securityType}] | `BatchSecurityInboundAdjustDto`（items:[{...,canAdjust,failReasons,flowOptions}]） | 批量调库下一步校验 |
| `batchSecurityPoolAdjust/addAdjustLog`（JSON） | `BatchSecurityInboundAdjustReq`（含 items:[{...,flowId/flowKey/flowType,creditReportFileIndexes,...}]） | `BatchSecurityInboundAdjustDto`（securityCount, submitCount, logIds） | 批量提交调库申请（无附件） |
| `batchSecurityPoolAdjust/addAdjustLogWithFiles`（multipart） | `request`=JSON Blob + `files`=MultipartFile[] | 同上 | 批量提交调库申请及附件（前端实际用此入口；JSON 无附件入口为 `addAdjustLog`） |
| `reports/queryInReportPage` | pageIndex, pageSize, reportTitle, securityCode, reportType, crteTimeStart/End | PageResult | 报告弹窗内部报告查询 |
| `reports/queryOutReportPage` | 同上 | 同上 | 报告弹窗外部报告查询 |
| `attachments/downloadAttachment` | `{id}` | `ApiResponse<String>`（Base64） | 下载报告库附件 |

> 路径均带前缀 `/api/v1/`。

---

## 5. 关键数据库表

### 5.1 `ip_investment_pool`（投资池主表）
关键字段：`id`、`parent_id`、`pool_name`、`pool_type`、`market_codes`、`variety_codes`、`max_capacity`、`status`(enabled/disabled)、`is_deleted`(0/1)、`inner_sort`、`outer_sort`、`in_flow_id/key`、`out_flow_id/key`、`simple_in_flow_id/key`。

### 5.2 `ip_adjust_log`（调库记录表）
- `adjust_type`：手工调整/联动调整/互斥调整/关联调整/Excel导入/手动批量调整。
- `adjust_mode`：调入/调出。
- `adjust_batch_no`：同组调库记录共用。
- `audit_status`：`-1`无效调整 / `00`流程中（待审批/审批中） / `11`驳回待修改 / `20`审批通过 / `21`审批驳回 / `32`O32自动审批 / `99`发起人已撤回。
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
| **校验接口** | `checkAdjust`（单证券多目标池） | 同名 `checkAdjust`，外层**逐只证券**调 `SPA.checkBatchAdjust`；`adjustGroupKey` 加 `sourceSecurityCode_` 前缀；返回单笔 `flowOptions` 并追加配置的 batchIn/Out 为推荐 |
| **校验规则** | 四阶段 + 调入/调出规则 + 联动/互斥 + 流程候选 | **完全复用**，无批量专属规则 |
| **提交接口** | 页面/Controller：`addAdjustLog`（内部 `submitAdjustLog`） | 批量 Controller 仍 `addAdjustLog`/`addAdjustLogWithFiles`；Service 编排后调 **`SPA.submitAdjustLog`**；`adjustType="手动批量调整"` |
| **分组** | 单次请求内多目标池 | 按 **`sourceSecurityCode`/主券** 分组，related 与主券同组 |
| **批次号** | 单次 `new BatchNoContext()` | **整批共用一个 `BatchNoContext`**，跨组序号递增 |
| **附件** | 单次 `SubmissionFiles` | **整批共用** `SubmissionFiles` |
| **防重复 / 直通** | 单券窗口防重复；直通/终审 `recheckBeforeFinalApproval` | 整批防重复；存在直通项时提交前整批 `isDirectAdjustFlow` + `recheckBeforeFinalApproval` |
| **事务范围** | 单笔 `@Transactional` | 外层批量 `@Transactional` 包裹各组 `submitAdjustLog`，**整批原子** |
| **审批流程选择** | 弹窗选流程，`dto.flowOptions` | 行内 `flowOptions`；提交透传所选流程，**不回填** batch 流程 |
| **调整原因/建议** | 单笔一份 | **整批一份**，透传到每组 |
| **候选/池查询** | 证券检索 + 可调池树 | `queryPoolPage` + 按目标池方向的候选证券分页 |
| **编排层自有逻辑** | — | 权限、候选方向过滤、防重复、直通预检、分组、共享附件/批次号、整批事务 |
| **复用的单笔逻辑** | — | `checkAdjust`、`submitAdjustLog`（五阶段）、`isDirectAdjustFlow`、`recheckBeforeFinalApproval`、`BatchNoContext` 及单券内建步骤/落池/附件 |

**一句话总结**：批量是「编排层」——权限/候选/防重复/直通预检/分组/共享附件与批次号/整批事务；校验与落库全部委托 `SecurityPoolAdjustService`，不重复实现业务规则。与 [27 存量证券批量](27-stock-security-batch-adjust.md) **同构**（存量仅多产品库目标 + 来源池）。

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
- Service：`BatchSecurityPoolAdjustService.java`（编排：`checkAdjust`→`SPA.checkBatchAdjust`，`addAdjustLog`→防重复/直通预检/分组→`SPA.submitAdjustLog`）、`SecurityPoolAdjustService.java`（`checkAdjust`/`checkBatchAdjust`/`submitAdjustLog`/`isDirectAdjustFlow`/`recheckBeforeFinalApproval`/`BatchNoContext`）、`SysAttachmentService.java`
- Mapper：`BatchSecurityPoolAdjustMapper.java` / `BatchSecurityPoolAdjustMapper.xml`
- 实体：`BatchSecurityPoolAdjustReq`、`BatchSecurityInboundAdjustReq`、`BatchSecurityInboundAdjustDto`、`BatchSecurityPoolDto`、`BatchSecurityCandidateDto`
- SQL：`sql/rrs_external_import_schema.sql`（`rrs_securityinfo`）、`sql/rrs_security_pool_adjust_schema.sql`、`sql/rrs_pool_init_schema.sql`、`sql/rrs_sys_attachment_schema.sql`

## 当前市场口径

市场按当前代码在 `rrs_securityinfo.market` 的本地市场值校验（外部导入与 Wind `EXCHMARKET` 保持一致），银行间使用 NIB；临时代码使用登记表市场。关联券其他市场不使当前代码通过市场限制。批量候选直接按主档市场筛选，临时代码用登记表 EXISTS 筛选，展示按当前页批量查询唯一市场，避免关联连接重复分页或逐行市场查询。CRMW 导入复用证券主档缓存，同一代码的市场只查询一次。缺失、重复或不支持的映射报出具体代码。


## 报告与手工文件统一交互

报告库引用按 `sourceReportSource + sourceReportId` 聚合为一份报告入口，不直接铺开报告下的 PDF。点击报告标题在工作台新页签打开 `pages/report_detail.html`，展示元信息及该报告全部有效附件，逐个下载；手工上传文件仍点击直接下载。此规则用于申请、批量、审核与详情的报告/其他材料栏。最近信评报告自动回填携带完整附件列表；修改节点移除报告会删除本日志、本材料栏的全部关联。

`sys_attachment.source_attachment_id` 保存复制来源的报告库附件 ID，附件查询返回 `sourceAttachmentId/sourceReportId/sourceReportSource/sourceReportTitle`；基金代码转正继承关联时保留该来源。新增 `POST /api/v1/reports/queryReportDetail { reportId, source: internal|external }`，非法来源、无效 ID、不存在或已删除报告明确失败。

新环境通过更新后的原始附件 schema 初始化；存量升级使用 `sql/tool_report_source_upgrade.sql`（不加入初始化/重置执行）。仅修复唯一可定位来源，未定位项明确输出供核对，运行时不猜测归属。前端完整说明见 `docs/report-materials.html`。

## 快速入库与本次材料豁免

适用证券池调整（包括详情页调库和审核页下一步模式）、证券池批量调整、存量证券批量调整及证券池 Excel 导入。后三个入口保留完整单笔候选，另共用 BatchAdjustFlowOptionHelper 追加配置的批量流程并统一推荐批量；未配置时保留单笔推荐。

- 信用债沿用 isSimpleInboundFlowMatched，资格在“下一步”计算，提交不重复查询矩阵、180 天入库经历或历史报告条件。资格中的历史报告要求仍保留，豁免的是本次提交材料。
- 产品库沿用独立 isProductInboundFlowMatched：目标为 bond_product_root 子树启用叶子；当前有效在信用债一至三级、转债核心/重点或 CRMW 库之一才命中。来源编码与存量入口共用，本次待申请调入来源池不算当前在库。
- 材料豁免按主项快速资格决定，与选择快速、一般、特殊或批量流程无关。免材料不代表免审批，提交保留所选流程 ID、Key、类型，仍由该流程决定人工审批或直接生效。
- 行级 CheckResultItem.materialExemptionType 返回 creditBondSimple / productSource，无资格时为空；前端随主项透传，批量和 Excel 映射保持该字段。FlowOption.materialExemptionType 只记录快速候选的资格匹配信息，不再按当前选中候选决定材料规则。
- 同组联动、互斥和关联市场项跟随主项豁免；关联码逐码判断快速资格，任一不符则整组不获得快速材料豁免。独立调出及无资格主项沿用原材料规则，混合批次逐组处理，整批事务不变。
- 提交复用已加载的池配置和当前在库集合检查产品来源；信用债资格不重复查库，不新增校验凭证或重复资格查询。材料判断不要求所选流程等于 simple_in，也不要求其为直通流程。
- 六个相关页面统一提示“本次免提交材料（信评报告及其他附件）”，切换流程不撤去有资格主项的提示；详情和审核页补齐批量调入/调出标签。保留现有材料入口和内容，不显示合同投资范围确认提示，不增加勾选。
- 补充的报告引用仍检查有效性，材料绑定及完成后的报告生成沿用原逻辑；准入硬限制和落池复核保留，不改表，不改变其他调库链路。
