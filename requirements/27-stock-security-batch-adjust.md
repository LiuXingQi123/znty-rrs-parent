# 存量证券批量调整（批量调入 / 批量调出）需求说明

> 前端页面：`stock_security_batch_adjust.html`（自包含两步式工作台）  
> 后端前缀：`/api/v1/stockSecurityBatchAdjust`  
> 角色定位：业务人员在**债券产品库**叶子池上，对存量证券做批量调入/调出；仅调入按指定来源池筛选。

与 [12-batch-security-pool-adjust.md](12-batch-security-pool-adjust.md) **同构**（校验/提交/附件/现有数量分项/整批防重复/直通复核），差异如下。

---

## 1. 与证券池批量调整的差异

| 项 | 证券池批量 | 存量证券批量 |
|----|------------|--------------|
| 目标池列表 | 启用叶子（排除 crmw 池类型） | **仅 `pool_code=bond_product_root` 子树叶子**（不含「债券产品库1」） |
| 可选证券筛选 | 代码 / 简称 / 市场 / 是否特征 | 另加**发行主体**；调入另加来源池；是否特征含 ABS/担保/永续/次级/私募/含权，多选 AND |
| 来源池 | 无 | 调入固定白名单 6 项，调出不设来源池查询条件 |

**校验 / 提交与证券池批量一致**（编排细节见 [12](12-batch-security-pool-adjust.md) §3.2～§3.5）：`checkAdjust` 逐券委托 `SecurityPoolAdjustService.checkAdjust`（透传流程候选，不注入 batchIn/batchOut）；`addAdjustLog` 分组后委托 `submitAdjustLog`（共享附件与 `BatchNoContext`），整批防重复、直通用 `isDirectAdjustFlow` + `recheckBeforeFinalApproval`；手工项 `adjustType=手动批量调整`。本类只编排产品库与调入来源池差异，不新建业务表。

---

## 2. 来源池与候选证券范围

- 调入：`querySecurityPage` 须传 `sourcePoolIds`（至少一个且 ∈ 白名单）；证券在任一所选来源有效在池（OR），包括 CRMW 库在内均查询 `ip_pool_status`。
- 调出：前端不展示也不传 `sourcePoolIds`，后端不应用来源池过滤；查询范围不是默认来源池的并集，而是不受来源池限制的全部候选证券。
- 方向：`in` = 不在目标产品池；`out` = 已在目标产品池。
- 仍排除 `security_type IN ('crmw','company')` 与 `security_status='D'`。  
- 发行主体：`issuer` / `issuer_code` 模糊匹配。

---

## 3. 接口清单

| 接口 | 说明 |
|------|------|
| `POST .../queryPoolPage` | 债券产品库根树叶子 + 权限 + 现有数量分项 |
| `POST .../querySourcePoolList` | 调入来源池白名单下拉 |
| `POST .../querySecurityPage` | 候选证券（调入按来源池过滤，调出不限制） |
| `POST .../checkAdjust` | 批量校验 |
| `POST .../addAdjustLog` / `addAdjustLogWithFiles` | 批量提交 |

---

## 4. 前端

- 菜单：债券研究 → 存量证券批量调整  
- 可选证券区：调入展示来源池多选（必选），调出不展示来源池；均支持发行主体、代码、简称、市场、是否特征，默认 10 条/页；列表「剩余期限(年)」同 [12]（`dateExists` 库内为**天**，前端 ÷365 展示），并展示 ABS/担保/永续/次级/私募/含权六列「是/否」Tag
- 担保人/权益人与证券池批量调整保持一致：查询四类关系主体并默认选中第一条，`115202000` 展示为“债务主体”；ABS 显示自选权益人及担保人主体内评分，自选时评分优先取自选主体。
- 工作台内容区为固定底部操作栏预留可滚动安全区，候选表格分页不得被“返回/下一步”遮挡

---

## 5. 主要代码

- Controller：`StockSecurityBatchAdjustController`  
- Service：`StockSecurityBatchAdjustService`  
- Mapper：`StockSecurityBatchAdjustMapper` + XML  
- 实体：`entity.stocksecuritybatchadjust.*`  
- 测试：`StockSecurityBatchAdjustServiceTest`  
