# CRMW 池批量调整（批量调入 / 批量调出）需求说明

> 前端页面：`batch_crmw_pool_adjust.html`
> 后端前缀：`/api/v1/batchCrmwPoolAdjust`
> 角色定位：业务人员选定一个 CRMW 叶子池与方向后，批量勾选多条「凭证 + 标的」组合一次性发起调入/调出申请。
> 页面与 [12 证券池批量调整](12-batch-security-pool-adjust.md) 同构；校验/落库委托 [19 CRMW 池调库](19-crmw-pool-adjust.md)。


“是否特征”追加“是否标准信用债”（`bondYesFlags` 选项 `stdCredit`），勾选仅筛选证券主档 `std_credit_flag=1`，与其他特征按 AND 组合；不勾选时不限制该字段。对应列表追加 `stdCreditFlag` 列：1=是，其他值（含 NULL）按现有是否类标识显示否，接口保留原始数值，沿用是=`success`、否=`info` 的 Tag。仅使用上游维护的原始标志，不按其他特征或 `std_clause_flag` 推断；CRMW 按标的证券筛选及展示。

---

## 标的债券一券多码调库

- CRMW 凭证本身为单一代码，不存在多市场扩批；仅将其绑定标的债券按 Wind 非空 `S_INFO_ISINCODE` 展开同券代码，排除自身、去重、不递归，空 ISIN 和临时代码不关联。
- `checkCrmwAdjust` 在手工、联动、互斥项生成后追加 `itemTag=related` 的关联调整项。每个实际代码独立校验市场、凭证+标的组合在池状态、活动池组、冻结期和原调库规则；同池调入累计主项及已通过关联项的容量。主项失败阻断同组关联项；关联码失败单独展示，沿用证券池仅提交通过项的规则。
- 关联项保持同一个 `crmwScode/crmwStype`，`sourceSecurityCode` 为触发主组合键 `crmwScode|securityCode`，共用原主项的 `adjustGroupKey`、流程与 CRMW 批次号，仅手工项创建流程步骤；关联项的报告要求由同组手工项承担。
- 提交 `items[]` 新增 `securityCode/securityShortName/securityType`，记录、快照与最终落池使用实际标的身份。未传明细代码的旧单组合请求继续使用请求级主券代码。直通即时生效，人工审批仅最终 `20` 同批落池；出池精确匹配原凭证、实际标的和目标池，保留其他凭证的组合。
- 批量及 Excel 导入透传实际代码；同批显式选择多个同券代码时按凭证+标的+目标池+方向去重，手工主项优先。Excel 关联项的全称、市场、类型来自自身主档。
- 活动流程通过同批任一组合定位，关联码审批中不能在相同池组重复申请。`queryCrmwAdjustLogList` 返回当前组合全部历史及同批关联项，不按请求批次或终态过滤；返回项增加 `securityCode/securityShortName`，申请校验表和审核/详情记录表展示实际标的代码与名称。
- 自动到期调出继续逐条扫描实际在池组合，不再二次按 ISIN 扩批；本次不调整定时任务规则，不迁移历史数据，不变更表结构。

## 1. 页面概览

单页两视图（`pageMode`），样式复用证券池批量调整：

- 视图 A `poolList`：投资池树筛选（`includePoolTypes=['crmw']`）+ CRMW 叶子池表格（调入/调出）。
- 视图 B `adjustWorkbench`：步骤 1 材料 + 可选组合表；步骤 2 校验结果列：证券名称、证券代码、CRMW名称、CRMW代码、投资池名称、调整类型、调整方向、审批流程、调整说明、校验结果。
- 工作台内容区独立滚动并为固定操作底栏预留安全区，步骤 1 候选组合分页和步骤 2 末尾内容均不得被底栏遮挡。

现有数量统计 `ip_pool_status_crmw` 有效组合，分项 `typeCode=crmw`。

---

## 2. 候选组合

| 方向 | 数据来源 |
|---|---|
| 调出 | `ip_pool_status_crmw` 当前有效组合，左连标的 `rrs_securityinfo` |
| 调入 | CRMW 凭证 × 可绑定标的（排除 `crmw`/`company`），且该组合尚未在目标池 |

候选表默认 10 条/页，支持按标的证券的是否特征（ABS/担保/永续/次级/私募/含权/标准信用债）多选筛选，勾选即筛「是」、多选 AND。列包含证券名称、证券代码、市场、CRMW名称、CRMW代码、发行人、证券评级、主体评级、主体内评分、是否ABS/担保/永续/次级/私募/含权/标准信用债、到期日期、剩余期限(年)、担保人/权益人、自选权益人、担保人主体内评分。关系主体按 `115004000`、`115203000`、`115202000`、`115201000` 映射为 1‑4 升序，其中 `115202000=债务主体`，页面默认选中第一条。

跨页多选键：`crmwScode|securityCode`。

市场仅展示和筛选标的当前代码的唯一市场：直接读取 `rrs_securityinfo.market`，外部导入按当前代码与 Wind `EXCHMARKET` 保持一致（IB→CIBM）；临时代码取登记市场。候选 SQL 直接使用主档市场筛选，临时代码用登记表 EXISTS 筛选，当前页批量补充市场，避免关联表造成重复分页和逐行查询。缺失、重复、不支持的市场映射报出具体代码，同券其他市场不能使当前代码通过池的市场限制。

---

## 3. 校验与提交

编排层 `BatchCrmwPoolAdjustService`：

- `checkAdjust` 逐组合组装与单笔相同的 `AdjustCheckReq`，直接调用 `CrmwPoolAdjustService.checkCrmwAdjust`（与证券池批量调用 `checkAdjust` 同构）；结果项身份、流程候选与 `warnings` 透传单笔返回，不在批量侧重写规则。调入「已在池」按凭证+标的组合判断。
- CRMW 单笔 `AdjustCheckReq` 和 `CrmwPoolAdjustSubmitReq` 均接收 `guarantorCode`、`rightsHolderCode`、`selfSelectedRightsHolderCode`；批量编排层按标的类型透传实际选中的评级主体。ABS 自选权益人优先，两者均未选时阻断。目标池仍不是信用债大库，不走主体债矩阵；`releaseRules=yes` 仅在提交时把说明追加到调整意见/调整说明，**不改变**单笔校验结果。
- `addAdjustLog` 按组合分组后委托 `submitAdjustLog`，整批共享附件与 `BatchNoContext`。
- 手工项 `adjustType=手动批量调整`；批次号前缀仍为 `CRMW`。
- 整批一个事务；约 30 秒防重复键含 `crmwScode`。
- 存在直通项时 `isDirectAdjustFlow` + `recheckBeforeFinalApproval`。
- **不注入** batchIn/batchOut。
- 候选查询与证券池批量的有意差异：证券池仍是一个 `querySecurityPage` + `direction`；CRMW 拆成 `queryInboundCandidatePage` / `queryOutboundCandidatePage`，查询请求体不带 `direction`。

落池只写 `ip_pool_status_crmw`。审批复用现有 CRMW 审核/详情/历史页。

---

## 4. 接口清单

| 路径 | 用途 |
|---|---|
| `common/queryPoolTreeList` | `{ includePoolTypes: ['crmw'] }` 筛选树 |
| `batchCrmwPoolAdjust/queryPoolPage` | CRMW 叶子池分页 |
| `batchCrmwPoolAdjust/queryInboundCandidatePage` | 可调入候选组合分页；`bondYesFlags` 按标的证券特征多选 AND |
| `batchCrmwPoolAdjust/queryOutboundCandidatePage` | 可调出候选组合分页；`bondYesFlags` 按标的证券特征多选 AND |
| `batchCrmwPoolAdjust/checkAdjust` | 下一步校验 |
| `batchCrmwPoolAdjust/addAdjustLog` | JSON 提交 |
| `batchCrmwPoolAdjust/addAdjustLogWithFiles` | multipart 提交（页面实际入口） |

路径前缀 `/api/v1/`。

---

## 5. 测试

- `BatchCrmwPoolAdjustApiTest`
- `BatchCrmwPoolAdjustServiceTest`


## 报告与手工文件统一交互

报告库引用按 `sourceReportSource + sourceReportId` 聚合为一份报告入口，不直接铺开报告下的 PDF。点击报告标题在工作台新页签打开 `pages/report_detail.html`，展示元信息及该报告全部有效附件，逐个下载；手工上传文件仍点击直接下载。此规则用于申请、批量、审核与详情的报告/其他材料栏。最近信评报告自动回填携带完整附件列表；修改节点移除报告会删除本日志、本材料栏的全部关联。

`sys_attachment.source_attachment_id` 保存复制来源的报告库附件 ID，附件查询返回 `sourceAttachmentId/sourceReportId/sourceReportSource/sourceReportTitle`；基金代码转正继承关联时保留该来源。新增 `POST /api/v1/reports/queryReportDetail { reportId, source: internal|external }`，非法来源、无效 ID、不存在或已删除报告明确失败。

新环境通过更新后的原始附件 schema 初始化；存量升级使用 `sql/tool_report_source_upgrade.sql`（不加入初始化/重置执行）。仅修复唯一可定位来源，未定位项明确输出供核对，运行时不猜测归属。前端完整说明见 `docs/report-materials.html`。
