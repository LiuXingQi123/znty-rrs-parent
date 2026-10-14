# CRMW池调库详情需求说明

> 前端页面：`crmw_pool_adjust_detail.html`
> 后端前缀：`/api/v1/crmwPoolAdjust`（详情查询 + adjust 模式首次提交，复用 [19] 接口）
> 角色定位：只读查看 CRMW 凭证与标的证券当前所在池、调库记录与流程状态；或在 `adjust` 模式下发起首次调库提交。**修改节点重新提交不在本页**（在 [20] approve.html）。


### 证券市场与历史展示

证券主档仅保留当前 `wind_code`，主档保留独立外部 `sec_id`，末尾追加 `exchmarket VARCHAR(10) DEFAULT NULL`，与 Wind `EXCHMARKET` 原值一致。市场经统一服务直接读取 `rrs_securityinfo.exchmarket`（IB→CIBM），临时代码使用登记市场。详情返回 `marketCode`、只读 `relatedMarketCodes: [{ windCode, marketCode }]`，按 Wind 非空 ISIN 关联同券代码；空 ISIN 不关联，同市场多个代码完整展示。旧市场字段不再提交、写主档或写证券/CRMW 快照。历史详情和审批在读快照后补查当前关联关系，其他业务信息保留原快照。每条实际代码按自身市场独立校验。

---

## 1. 页面概览

单 Vue 实例（`el: '#crmw_pool_adjust_detail'`），`currentPage='detail'`（默认详情页）。

**入口模式 `entryMode`**：`view`（默认，只读）/ `adjust`（调库调整）。

**初始化**（`created`）：`this.initStandaloneDetailPage()`。从 URL 取 `securityCode`/`windCode`、`crmwScode`、`targetPoolId`、`adjustLogId`/`adjust_log_id`、`adjustBatchNo`/`adjust_batch_no`、`entryMode`（`'adjust'` 或默认 `'view'`）；`adjustStep=1`；`loadDetailData(securityCode)`。

> URL 必带 `crmwScode`：`initStandaloneDetailPage` 校验 `securityCode && crmwScode`。`baseURL` 由 `js/api.js` 注入（`http://localhost:18090`）。

---

### 财报四期规则

按中国标准时间的当前自然年确定四期：默认前三列为前三年年报，第四列为数据库最新一期；最新一期恰好为上一年年报，或主体完全没有财报时，显示前四年年报。缺失期保留报告日期，指标为空，不以季报或其他年份替代。复用证券池四期查询结果，保持返回顺序并整体只读，不重复拼接最新一期。

## 2. 筛选与查询逻辑

无独立筛选；`loadDetailData` 用 `Promise.all` 并发 **6 个接口**（与 [20] 审批页相同）：`querySecurityDetail`、`queryCrmwDetail`、`queryCrmwAdjustPoolList`(in)、`queryCrmwAdjustPoolList`(out)、`queryCrmwPoolStatus`、`queryCrmwAdjustLogList`。随后 `loadLogAttachments` + `loadFlowSteps`。

---

## 3. 业务逻辑

### 3.1 view 只读模式（默认）

- `isViewMode=true`、`isSecurityInfoReadonly=true`（所有证券字段 `el-input :disabled`）。
- 展示：证券基本信息（`el-descriptions` 3 列，字段及顺序同 [11]；非 ABS 展示担保人，ABS 展示权益人、自选权益人和担保人主体内评分；赎回/含权债剩余期限为**年**，剩余期限为**天**且旁同步展示年）、发行主体最近三年及一期财务数据（只读，可切换城投债/产业债指标口径）、CRMW 基本信息（只读）、当前所在池、调库记录表（`showAdjustLogSection=true`）、当前流程状态表（`showFlowStatusSection=true`）。
- `showLogUploadActions=false`（信评报告/其他材料的「选择报告」「上传附件」按钮均隐藏）。
- 终态记录（`20/21/99/-1`）同样只读展示。

### 3.2 adjust 可调整模式（首次调库提交）

- `entryMode='adjust'`：隐藏调库记录区与流程状态区，显示调库操作区（步骤1选池）。
- 证券基本信息中的“担保人”改为与证券池调库一致的下拉：候选类型及排序口径保持一致，页面默认选中第一条并允许改选；变更后联动展示所选主体的 AIS 最新内评分。
- 步骤1：左右双栏「可调入库」（绿）+「可调出库」（红），树形 `el-table`，叶子节点可勾选；可调入库默认仅展开「信用债大库(new)」，其他根节点默认收起（CRMW 池树不含该节点时即默认全部收起），可调出库默认全部展开；互斥校验（`handleInPoolSelect`/`handleOutPoolSelect`）。
- 步骤2（`goToStep2`）：携带所选 `guarantorCode` 调 `checkCrmwAdjust` → 展示校验结果表 + 原因建议。
- 提交（`handleSubmit`→`confirmFlowSelection`→`submitAdjustLog`）：调 `addCrmwAdjustLogWithFiles`（multipart），成功后 `backToList`（先 `closeActiveTab()`，失败再回页内列表）。顶部「返回」同样先关动态页签，禁止 `history.back()`。

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
| `crmwPoolAdjust/checkCrmwAdjust` | `AdjustCheckReq`（含 guarantorCode） | `AdjustCheckDto` | adjust 模式校验确认，后端复核所选担保人 |
| `crmwPoolAdjust/addCrmwAdjustLog`（JSON）/ `addCrmwAdjustLogWithFiles`（multipart） | `CrmwPoolAdjustSubmitReq`（含 guarantorCode） | `AdjustSubmitDto` | adjust 模式首次提交，快照保存所选担保人及最新内评分 |
| `attachments/queryAttachmentList` | adjustLogIds[]（兼容 adjustLogId） | 附件列表（含 mainId） | 批量加载调库记录附件，单页一次请求 |
| `attachments/downloadAttachment` | id | `ApiResponse<String>`（Base64） | 下载附件 |
| `reports/queryInReportPage` / `queryOutReportPage` | 分页+筛选 | PageResult | 信评报告弹窗 |

> 路径均带前缀 `/api/v1/`。

---

## 5. 关键数据库表

view 模式纯读：`ip_pool_status_crmw`（查询当前池）、`ip_adjust_log`（调库记录）、`ip_adjust_step`（流程步骤）、`ip_investment_pool`、`rrs_securityinfo`（表结构见 `rrs_external_import_schema.sql`）、`wf_flow_definition`。adjust 模式提交时写 `ip_adjust_log`/`ip_pool_status_crmw`/`ip_adjust_step`/`sys_attachment`，与 [19] 完全一致。

---

## 6. 状态流转

详情页 view 模式只读展示各状态记录；adjust 模式提交时状态变迁同 [19]（直通→`20`，非直通→`00`）。`audit_status` 枚举与全站一致（`-1/00/11/20/21/32/99`）。`queryCrmwAdjustLogList` 无批次参数时排除终态 `-1/20/21/99`，仅展示进行中（`00/11/32`）记录。

---

## 7. 与其他池模块的差异

- **CRMW基本信息 section**：详情页多一个只读 CRMW 信息区块（CRMW全称/名称/代码/证券类型/发行人）。
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

- 前端：`znty-rrs-ui/crmw_pool_adjust_detail.html`（`initStandaloneDetailPage`、`loadDetailData`、`entryMode` 归一化、`loadFlowSteps`、`uniqueAdjustLogsByFlow`、`flowStepSpanMethod`、首次提交 `submitAdjustLog`）
- Controller：复用 `CrmwPoolAdjustController.java`
- Service：复用 `CrmwPoolAdjustService.java`
- Mapper：复用 `mapper/CrmwPoolAdjustMapper.xml`
- 实体：复用 `entity/crmwpooladjust/*`
- SQL：`sql/rrs_crmw_pool_status_schema.sql`


## 标的证券主档差异提示

CRMW 申请、详情、审核的证券基本信息区保留既有快照回显、编辑权限、选池、校验与提交行为，复用 `js/security_master_diff.js` 和 `css/security_master_diff.css`。独立调用 `POST /api/v1/securityPoolAdjust/querySecurityMasterDetail`（入参 `{ securityCode: 标的证券代码 }`，返回 `SecurityInfoDetailDto`）只读查询当前主档，不传凭证代码或申请 ID，不合并快照、不写数据库。

与主档不同的字段标签以橙色轻度突出，悬停或键盘聚焦显示页面值、主档值和本次读取时间；空值提示“（空）”。日期格式及数字小数位按业务值比较，零值与空值不同。编辑即时更新标记，不重新请求。申请/详情调整模式的担保人名称、内评分取当前选择控件；详情只读模式及审核取原页面快照值。ABS 权益人/自选权益人名称和市场关联代码不参与比较。

CRMW 凭证基本信息由 `queryCrmwDetail` 直接读取主档，保持原逻辑，不重复增加对比请求。对比失败显示“主档对比数据暂不可用”，不阻断原操作；换券、同券重载、返回列表和销毁时旧响应不得回写。公共开关 `window.RrsSecurityMasterDiff.enabled` 的定义设为 `false` 并刷新页面，即可关闭所有接入页面的附加请求、样式和提示；打印/PDF 保持原有外观。主档值是本次查询结果，不保证上游实时同步，也不改变规则实际使用的数据。
