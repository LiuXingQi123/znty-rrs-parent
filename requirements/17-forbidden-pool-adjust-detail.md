# 禁投池调整详情需求说明

> 前端页面：`forbidden_pool_adjust_detail.html`
> 后端前缀：`/api/v1/forbiddenPoolAdjust`（详情查询 + adjust 模式首次提交，复用 [15] 接口）
> 角色定位：只读查看主体及其旗下债券当前所在风险池、调库记录与流程状态；或在 `adjust` 模式下发起首次调库提交。**修改节点重新提交不在本页**（在 [16] approve.html）。

---

## 1. 页面概览

单 Vue 实例（`el: '#forbidden_pool_adjust_detail'`），`currentPage` 默认 `'detail'`。

**入口模式 `entryMode`**：`'view'`（默认，只读）/ `'adjust'`（首次调库提交）。URL `entryMode=adjust` 切换；**`process` 在详情页中等同 view**（`getUrlParam('entryMode') === 'adjust' ? 'adjust' : 'view'`）。

**详情页区块**（CSS order）：主体基本信息（只读）→ 当前所在池（主体所在池 + 旗下债券所在池）→ 调库记录（仅 `isViewMode`）→ 当前流程状态（仅 `isViewMode`）→ 调库操作（`isAdjustMode && adjustStep===1`）→ 调库校验结果（`adjustStep===2`）→ 原因和建议（`adjustStep===2`）。

**初始化**（`created`）：`this.initStandaloneDetailPage()`。读 URL `companyCode`（缺提示「缺少主体信息，请从业务页面进入」）、`targetPoolId`/`adjustLogId`/`adjustBatchNo`、`entryMode`，`adjustStep=1`，`loadDetailData(companyCode)`。`pageTitle`：adjust 模式「禁投池调整」，否则「主体详情」。`baseURL` 由 `js/api.js` 注入（`http://localhost:18090`）。

---

## 2. 筛选与查询逻辑

### 2.1 详情并发加载（`loadDetailData`，与 [15]/[16] 同构 5 接口）

`queryCompanyDetail` / `queryAdjustPoolList`(in) / `queryAdjustPoolList`(out) / `queryCompanyPoolStatus` / `queryAdjustLogList { companyCode, adjustBatchNo }`。

随后 `buildPoolTree`、过滤已在池、`inMutexMap`/`outMutexMap`、`loadLogAttachments`、`loadFlowSteps`（`uniqueAdjustLogsByFlow` 去重，优先含 pending 的记录为 `activeAdjustLog`）。

### 2.2 调库记录表（仅 view 模式）

列：序号 / 调整对象（`securityShortName`+`securityCode`）/ 投资池名称（`poolPath`）/ 调整类型 / 调整方向 / 审批流程 / 信评报告 / 其他材料 / 调整说明 / 审核状态。

- `showLogUploadActions = isAdjustMode`（详情页仅 adjust 模式才显示上传按钮，view 模式隐藏）。
- SQL `queryAdjustLogList`：`FROM ip_adjust_log al LEFT JOIN wf_flow_definition fd ON fd.id=al.flow_id`，`WHERE al.is_deleted=0`，传 `adjustBatchNo` 按批次过滤，否则 `al.security_code=#{securityCode} AND (audit_status IS NULL OR audit_status NOT IN ('-1','20','21','99'))`（**排除终态，仅显示进行中**），`ORDER BY crte_time DESC`。

### 2.3 当前流程状态表（仅 view 模式）

`flowStepList` 来自 `queryAdjustStepList`（`adjustBatchNo` 优先，否则 `adjustLogId`）。列：序号/步骤名称（`nodeLabel`）/步骤结果（`stepStatus` el-tag）/开始时间/处理人（当前用户高亮 `isCurrentHandler`）/处理结果（`processAction` el-tag）/处理时间/处理意见。

- 行合并 `flowStepSpanMethod`：前 3 列按 `nodeLabel`/`nodeLabel|stepStatus`/`nodeLabel|stepStatus|startTime` 合并。
- 行样式 `getFlowStepRowClass`：按节点类型关键字（start/end/auto/发起/复核/修改/审批）+ stepStatus 着色。

---

## 3. 业务逻辑

### 3.1 view 只读模式

- `isViewMode=true`、`isSecurityInfoReadonly=true`（主体字段全 `disabled`）。
- 展示：主体基本信息、当前所在池（两子块）、调库记录表、当前流程状态表。
- **无任何提交入口**：`showLogUploadActions=false`，无审批区，无「提交」按钮。顶部「返回」（`backToList`）。终态记录（`20/21/99/-1`）同样只读。
- 「查看债券」按钮（`openCompanyBondDialog`）调 `queryCompanyBondList` 弹窗显示旗下债券明细。

### 3.2 adjust 可调整模式（首次调库提交，与 [15] 步骤 1/2 同构）

- 入口：URL `entryMode=adjust`，或列表页「调库」按钮 `handleAdjust`（设 `entryMode='adjust'`）。
- 隐藏调库记录区与流程状态区，显示调库操作区（步骤 1 选池）。
- 步骤 1：左右双栏树表格 + 互斥校验 + 信评报告/其他材料。
- `goToStep2` → `checkAdjust` → 步骤 2 校验结果 + 原因建议。
- `handleSubmit` → 流程选择弹窗 → `submitAdjustLog` → `addAdjustLogWithFiles`（multipart）→ 成功 `backToList`。

### 3.3 详情页不实现修改节点重新提交

> 与证券池调库详情页一致：详情页**没有** `audit_status='11'` 识别逻辑、**不调用** `/api/v1/forbiddenPoolAdjustFlow/submitAdjustAudit`。`adjust` 模式的提交是**首次调库申请**（`addAdjustLog` 新建记录，初始 `00`/`20`），不是修改节点重新提交。真正的「修改节点重新提交」（`audit_status='11'`）发生在 `forbidden_pool_adjust_approve.html`，通过 `submitAdjustAudit` 完成。`addAdjustLog` 永远新建 `ip_adjust_log`，不更新已存在的 `11` 记录。

---

## 4. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `forbiddenPoolAdjust/queryCompanyPage` | 同 [15] | `PageResult<ForbiddenPoolAdjustDto>` | 列表页主体检索 |
| `forbiddenPoolAdjust/queryCompanyDetail` | companyCode | `ForbiddenPoolAdjustDto` | 主体详情 |
| `forbiddenPoolAdjust/queryAdjustPoolList` | companyCode, adjustDirection(in/out), currentUserId | `List<PoolDto>` | 可调入/调出池 |
| `forbiddenPoolAdjust/queryCompanyPoolStatus` | companyCode | `PoolStatusBundle` | 当前主体/旗下债券所在池 |
| `forbiddenPoolAdjust/queryCompanyBondList` | companyCode, targetPoolId | `List<CompanyBond>` | 旗下债券明细（弹窗） |
| `forbiddenPoolAdjust/queryAdjustLogList` | companyCode, adjustBatchNo? | `List<AdjustLogDto>` | 调库记录（无批次排除终态） |
| `forbiddenPoolAdjust/queryAdjustStepList` | adjustLogId, adjustBatchNo | `List<IpAdjustStepDto>` | 流程步骤列表 |
| `forbiddenPoolAdjust/checkAdjust` | `ForbiddenPoolAdjustCheckReq` | `AdjustCheckDto` | adjust 模式校验确认 |
| `forbiddenPoolAdjust/addAdjustLog`（application/json） | `ForbiddenPoolAdjustSubmitReq` | `ForbiddenPoolAdjustSubmitDto` | adjust 模式首次提交（无附件） |
| `forbiddenPoolAdjust/addAdjustLogWithFiles`（multipart/form-data） | `request`(JSON Blob) + `files`(MultipartFile[]) | `ForbiddenPoolAdjustSubmitDto` | adjust 模式首次提交（带附件） |
| `attachments/queryAttachmentList` | adjustLogId | 附件列表 | 加载调库记录附件 |
| `attachments/downloadAttachment` | id | blob | 下载附件 |
| `reports/queryInReportPage` / `queryOutReportPage` | 分页+筛选 | PageResult | 信评报告弹窗 |

> 路径均带前缀 `/api/v1/`。详情页**不调用** `ForbiddenPoolAdjustFlowController`（审批接口）。

---

## 5. 关键数据库表

view 模式纯读：`rrs_securityinfo`/`dict_security_type`/`ip_pool_status`/`ip_investment_pool`/`ip_adjust_log`/`wf_flow_definition`/`ip_adjust_step`/`sys_attachment`。adjust 模式提交时写 `ip_adjust_log`/`ip_pool_status`/`ip_adjust_step`/`sys_attachment`，与 [15] 完全一致（含 `syncCompanyBondsOnDirect`）。

---

## 6. 状态流转

详情页本身不驱动状态流转。view 模式展示的 `audit_status`/`step_status` 由 [15] 申请与 [16] 审批写入。详情页 adjust 模式提交后，新记录初始为 `00`（非直通）或 `20`（直通，含 `syncCompanyBondsOnDirect`）。

`audit_status` 枚举与 [15]/[16] 完全一致（-1/00/10/11/20/21/32/99）。`queryAdjustLogList` 无批次参数时排除终态 `-1/20/21/99`，仅展示进行中（`00/10/11/32`）记录。

---

## 7. 与证券池调库详情的差异

| 维度 | 证券池调库详情 | 禁投池调整详情 |
|---|---|---|
| 前端文件 / 页面 id | `security_pool_adjust_detail.html` / `#security_pool_adjust_detail` | `forbidden_pool_adjust_detail.html` / `#forbidden_pool_adjust_detail` |
| 入口模式 | `view`/`adjust`（`process` 归一化为 view） | `view`/`adjust`（非 adjust 一律 view） |
| 操作对象 | `securityCode` | `companyCode` |
| 详情接口 | `querySecurityDetail`（29+ 证券字段，adjust 模式可编辑约 28 字段） | `queryCompanyDetail`（13 主体字段，**全部只读**，adjust 模式也不可编辑） |
| 当前池展示 | 当前证券所在池 + 证券主体所在池 | **当前主体所在池 + 旗下债券所在池**（含债券数、查看明细） |
| 提交时 `securityInfo` | adjust 模式传全量可编辑字段，`editSecurityInfoForAdjust` 更新 | **不传**（主体信息只读），`postSubmitProcess` 跳过 |
| 修改节点重新提交 | 不实现（在 approve.html） | **不实现**（在 forbidden_pool_adjust_approve.html） |
| `pageTitle` | adjust→「证券池调库」，否则「证券详情」 | adjust→「禁投池调整」，否则「主体详情」 |
| 池类型 | credit_bond 等 | forbidden/observe/blacklist |

---

## 8. 验收标准

- view 模式纯只读，无任何提交/审批入口；终态与进行中记录均能展示。
- adjust 模式提交走 `addAdjustLogWithFiles`，新建记录初始 `00`/`20`，不更新已有 `11` 记录。
- `queryAdjustLogList` 无批次参数时仅返回未终结流程记录。
- 当前流程状态表行合并与着色按节点语义正确渲染。

## 9. 关键源码索引

- 前端：`znty-sirm-ui/forbidden_pool_adjust_detail.html`（`initStandaloneDetailPage`、`entryMode` 归一化、`isViewMode`/`isAdjustMode`/`isSecurityInfoReadonly`/`showAdjustLogSection`/`showFlowStatusSection`/`showAdjustOpSection`/`showLogUploadActions` 计算属性、并发加载、`loadFlowSteps`/`uniqueAdjustLogsByFlow`、`flowStepSpanMethod`/`getFlowStepRowClass`、adjust 模式 `submitAdjustLog`）、`css/forbidden_pool_adjust_detail.css`
- Controller：`ForbiddenPoolAdjustController.java`（详情用 `queryCompanyDetail`/`queryCompanyPoolStatus`/`queryCompanyBondList`/`queryAdjustLogList`/`queryAdjustStepList`，adjust 模式用 `checkAdjust`/`addAdjustLogWithFiles`）
- Service：`ForbiddenPoolAdjustService.java`（详情查询方法 + adjust 模式 `checkCompanyAdjust`/`addCompanyAdjustLog`/`addAdjustLog`/`createInitialSteps`/`syncCompanyBondsOnDirect`）
- Mapper：`ForbiddenPoolAdjustMapper.java` / `ForbiddenPoolAdjustMapper.xml`
- 实体：同 [15]（`ForbiddenPoolAdjustReq/Dto/CheckReq/SubmitReq/SubmitDto` + 复用 `AdjustLogDto/IpAdjustStepDto/PoolDto`）
- SQL：`sql/rrs_security_pool_adjust_schema.sql`（共享表结构）
- 测试：`ForbiddenPoolAdjustApiTest.java`
