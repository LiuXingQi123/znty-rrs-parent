# CRMW池调库详情需求说明

> 前端页面：`crmw_pool_adjust_detail.html`
> 后端前缀：`/api/v1/crmwPoolAdjust`（详情查询 + adjust 模式首次提交，复用 [19] 接口）
> 角色定位：只读查看 CRMW 凭证与标的证券当前所在池、调库记录与流程状态；或在 `adjust` 模式下发起首次调库提交。**修改节点重新提交不在本页**（在 [20] approve.html）。

---

## 1. 页面概览

单 Vue 实例（`el: '#crmw_pool_adjust_detail'`），`currentPage='detail'`（默认详情页）。

**入口模式 `entryMode`**：`view`（默认，只读）/ `adjust`（调库调整）。

**初始化**（`created`）：`this.initStandaloneDetailPage()`。从 URL 取 `securityCode`/`windCode`、`crmwScode`、`targetPoolId`、`adjustLogId`/`adjust_log_id`、`adjustBatchNo`/`adjust_batch_no`、`entryMode`（`'adjust'` 或默认 `'view'`）；`adjustStep=1`；`loadDetailData(securityCode)`。

> URL 必带 `crmwScode`：`initStandaloneDetailPage` 校验 `securityCode && crmwScode`。`baseURL` 由 `js/api.js` 注入（`http://localhost:18090`）。

---

## 2. 筛选与查询逻辑

无独立筛选；`loadDetailData` 用 `Promise.all` 并发 **6 个接口**（与 [20] 审批页相同）：`querySecurityDetail`、`queryCrmwDetail`、`queryCrmwAdjustPoolList`(in)、`queryCrmwAdjustPoolList`(out)、`queryCrmwPoolStatus`、`queryCrmwAdjustLogList`。随后 `loadLogAttachments` + `loadFlowSteps`。

---

## 3. 业务逻辑

### 3.1 view 只读模式（默认）

- `isViewMode=true`、`isSecurityInfoReadonly=true`（所有证券字段 `el-input :disabled`）。
- 展示：证券基本信息（`el-descriptions` 3 列）、CRMW 基本信息（只读）、当前所在池、调库记录表（`showAdjustLogSection=true`）、当前流程状态表（`showFlowStatusSection=true`）。
- `showLogUploadActions=false`（信评报告/其他材料的「选择报告」「上传附件」按钮均隐藏）。
- 终态记录（`20/21/99/-1`）同样只读展示。

### 3.2 adjust 可调整模式（首次调库提交）

- `entryMode='adjust'`：隐藏调库记录区与流程状态区，显示调库操作区（步骤1选池）。
- 步骤1：左右双栏「可调入库」（绿）+「可调出库」（红），树形 `el-table`，叶子节点可勾选，互斥校验（`handleInPoolSelect`/`handleOutPoolSelect`）。
- 步骤2（`goToStep2`）：调 `checkCrmwAdjust` → 展示校验结果表 + 原因建议。
- 提交（`handleSubmit`→`confirmFlowSelection`→`submitAdjustLog`）：调 `addCrmwAdjustLogWithFiles`（multipart），成功后 `backToList`。

### 3.3 详情页不实现修改节点重新提交

- 详情页 `view`/`adjust` 两模式；URL `entryMode='process'` 会被归一化为 `view`。
- **没有** `audit_status='11'` 识别逻辑、**没有调用** `submitAdjustAudit`。
- `adjust` 模式的提交走 `addCrmwAdjustLog`（创建全新记录，初始 `00`/`20`），是首次调库申请本身。
- 真正的「修改节点重新提交」（`audit_status='11'`）发生在 `crmw_pool_adjust_approve.html`，通过 `submitAdjustAudit` 完成。

---

## 4. 接口清单

详情页复用 `CrmwPoolAdjustController` 的查询/校验/提交接口（见 [19] 接口清单），**不调用** `CrmwPoolAdjustFlowController`。主要接口：

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `crmwPoolAdjust/querySecurityDetail` | securityCode | `SecurityInfoDetailDto` | 标的证券详情 |
| `crmwPoolAdjust/queryCrmwDetail` | securityCode | `SecurityInfoDetailDto` | CRMW 凭证详情 |
| `crmwPoolAdjust/queryCrmwAdjustPoolList` | securityCode, adjustDirection, currentUserId | `List<PoolDto>` | 可调入/调出池 |
| `crmwPoolAdjust/queryCrmwPoolStatus` | securityCode | `SecurityPoolStatusDto` | 当前证券/主体所在池 |
| `crmwPoolAdjust/queryCrmwAdjustLogList` | securityCode, adjustBatchNo? | `List<AdjustLogDto>` | 调库记录（无批次时排除终态） |
| `crmwPoolAdjust/queryCrmwAdjustStepList` | adjustLogId, adjustBatchNo | `List<IpAdjustStepDto>` | 流程步骤列表 |
| `crmwPoolAdjust/checkCrmwAdjust` | `AdjustCheckReq` | `AdjustCheckDto` | adjust 模式校验确认 |
| `crmwPoolAdjust/addCrmwAdjustLog`（JSON）/ `addCrmwAdjustLogWithFiles`（multipart） | `CrmwPoolAdjustSubmitReq` | `AdjustSubmitDto` | adjust 模式首次提交 |
| `attachments/queryAttachmentList` | adjustLogId | 附件列表 | 加载调库记录附件 |
| `attachments/downloadAttachment` | id | blob | 下载附件 |
| `reports/queryInReportPage` / `queryOutReportPage` | 分页+筛选 | PageResult | 信评报告弹窗 |

> 路径均带前缀 `/api/v1/`。

---

## 5. 关键数据库表

view 模式纯读：`ip_pool_status_crmw`（查询当前池）、`ip_adjust_log`（调库记录）、`ip_adjust_step`（流程步骤）、`ip_investment_pool`、`sirm_securityinfo`、`wf_flow_definition`。adjust 模式提交时写 `ip_adjust_log`/`ip_pool_status_crmw`/`ip_adjust_step`/`sys_attachment`，与 [19] 完全一致。

---

## 6. 状态流转

详情页 view 模式只读展示各状态记录；adjust 模式提交时状态变迁同 [19]（直通→`20`，非直通→`00`）。`audit_status` 枚举与全站一致（`-1/00/10/11/20/21/32/99`）。`queryCrmwAdjustLogList` 无批次参数时排除终态 `-1/20/21/99`，仅展示进行中（`00/10/11/32`）记录。

---

## 7. 与其他池模块的差异

- **CRMW基本信息 section**：详情页多一个只读 CRMW 信息区块（CRMW全称/名称/代码/市场代码/证券类型/发行人）。
- **URL 必带 `crmwScode`**：`initStandaloneDetailPage` 校验 `securityCode && crmwScode`。
- **6 接口并发**（比证券池详情多 `queryCrmwDetail`）。
- 其余模式逻辑（view/adjust 归一化、流程步骤定位/去重/合并、附件展示下载）与证券池详情页一致。

---

## 8. 验收标准

- view 模式纯只读，无任何提交/审批入口；终态与进行中记录均能展示。
- adjust 模式提交走 `addCrmwAdjustLogWithFiles`，新建记录初始 `00`/`20`，不更新已有 `11` 记录。
- `queryCrmwAdjustLogList` 无批次参数时仅返回未终结流程记录。
- CRMW 基本信息区块正确展示。

## 9. 关键源码索引

- 前端：`znty-sirm-ui/crmw_pool_adjust_detail.html`（`initStandaloneDetailPage`、`loadDetailData`、`entryMode` 归一化、`loadFlowSteps`、`uniqueAdjustLogsByFlow`、`flowStepSpanMethod`、首次提交 `submitAdjustLog`）
- Controller：复用 `CrmwPoolAdjustController.java`
- Service：复用 `CrmwPoolAdjustService.java`
- Mapper：复用 `mapper/CrmwPoolAdjustMapper.xml`
- 实体：复用 `entity/crmwpooladjust/*`
- SQL：`sql/sirm_crmw_pool_status_schema.sql`
