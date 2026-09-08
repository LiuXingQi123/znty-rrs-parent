# CRMW 池批量调整（批量调入 / 批量调出）需求说明

> 前端页面：`batch_crmw_pool_adjust.html`
> 后端前缀：`/api/v1/batchCrmwPoolAdjust`
> 角色定位：业务人员选定一个 CRMW 叶子池与方向后，批量勾选多条「凭证 + 标的」组合一次性发起调入/调出申请。
> 页面与 [12 证券池批量调整](12-batch-security-pool-adjust.md) 同构；校验/落库委托 [19 CRMW 池调库](19-crmw-pool-adjust.md)。

---

## 1. 页面概览

单页两视图（`pageMode`），样式复用证券池批量调整：

- 视图 A `poolList`：投资池树筛选（`includePoolTypes=['crmw']`）+ CRMW 叶子池表格（调入/调出）。
- 视图 B `adjustWorkbench`：步骤 1 材料 + 可选组合表；步骤 2 校验结果列：证券名称、证券代码、CRMW名称、CRMW代码、投资池名称、调整类型、调整方向、审批流程、调整说明、校验结果。

现有数量统计 `ip_pool_status_crmw` 有效组合，分项 `typeCode=crmw`。

---

## 2. 候选组合

| 方向 | 数据来源 |
|---|---|
| 调出 | `ip_pool_status_crmw` 当前有效组合，左连标的 `rrs_securityinfo` |
| 调入 | CRMW 凭证 × 可绑定标的（排除 `crmw`/`company`），且该组合尚未在目标池 |

候选表列：证券名称、证券代码、市场、CRMW名称、CRMW代码、发行人、证券评级、主体评级、主体内评分档、到期日期、剩余期限(年)、担保人*。

跨页多选键：`crmwScode|securityCode`。

---

## 3. 校验与提交

编排层 `BatchCrmwPoolAdjustService`：

- `checkAdjust` 逐组合组装与单笔相同的 `AdjustCheckReq`，直接调用 `CrmwPoolAdjustService.checkCrmwAdjust`（与证券池批量调用 `checkAdjust` 同构）；结果项身份、流程候选与 `warnings` 透传单笔返回，不在批量侧重写规则。调入「已在池」按凭证+标的组合判断。
- CRMW 单笔 `AdjustCheckReq` **没有** `releaseRules` / `guarantorCode`（凭证级无担保人、目标池不是信用债大库，不走主体债矩阵）。页面工作台仍保留与证券池批量相同的「放开规则 / 担保人」控件；`releaseRules=yes` 仅在提交时把说明追加到调整意见/调整说明，**不改变**单笔校验结果。
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
| `batchCrmwPoolAdjust/queryInboundCandidatePage` | 可调入候选组合分页 |
| `batchCrmwPoolAdjust/queryOutboundCandidatePage` | 可调出候选组合分页 |
| `batchCrmwPoolAdjust/checkAdjust` | 下一步校验 |
| `batchCrmwPoolAdjust/addAdjustLog` | JSON 提交 |
| `batchCrmwPoolAdjust/addAdjustLogWithFiles` | multipart 提交（页面实际入口） |

路径前缀 `/api/v1/`。

---

## 5. 测试

- `BatchCrmwPoolAdjustApiTest`
- `BatchCrmwPoolAdjustServiceTest`
