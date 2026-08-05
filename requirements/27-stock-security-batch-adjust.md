# 存量证券批量调整（批量调入 / 批量调出）需求说明

> 前端页面：`stock_security_batch_adjust.html`（自包含两步式工作台）  
> 后端前缀：`/api/v1/stockSecurityBatchAdjust`  
> 角色定位：业务人员在**债券产品库**叶子池上，对已在指定**来源池**中的存量证券做批量调入/调出。

与 [12-batch-security-pool-adjust.md](12-batch-security-pool-adjust.md) **同构**（校验/提交/批量流程注入/附件/现有数量分项），差异如下。

---

## 1. 与证券池批量调整的差异

| 项 | 证券池批量 | 存量证券批量 |
|----|------------|--------------|
| 目标池列表 | 启用叶子（排除 crmw 池类型） | **仅 `pool_code=bond_product_root` 子树叶子**（不含「债券产品库1」） |
| 可选证券筛选 | 代码 / 简称 / 市场 | 另加 **来源池（多选，必选≥1）**、**发行主体** |
| 来源池 | 无 | 固定白名单 6 项，见 §2 |

校验 / 提交逻辑在 `StockSecurityBatchAdjustService` **本类内实现**（自 `SecurityPoolAdjustService` 迁入副本，不注入、不调用对方 Service，便于后续细微差异独立演进）；不新建业务表。

---

## 2. 来源池白名单（pool_code）

| 展示名 | pool_code | 在池状态表 |
|--------|-----------|------------|
| CRMW库 | `crmw_root` | `ip_pool_status_crmw`（`security_code` 或 `crmw_scode` = 候选 wind_code） |
| 信用债大库/一级库 | `credit_bond_level_1` | `ip_pool_status` |
| 信用债大库/二级库 | `credit_bond_level_2` | `ip_pool_status` |
| 信用债大库/三级库 | `credit_bond_level_3` | `ip_pool_status` |
| 转债库/核心库 | `convertible_bond_core` | `ip_pool_status` |
| 转债库/重点库 | `convertible_bond_focus` | `ip_pool_status` |

- 接口 `querySourcePoolList` 按 code 解析当前环境 id 返回前端。  
- `querySecurityPage` 须传 `sourcePoolIds`（至少一个且 ∈ 白名单）；证券在**任一**所选来源有效在池（OR）。  
- 方向：`in` = 不在目标产品池；`out` = 已在目标产品池；均叠加来源过滤。  
- 仍排除 `security_type IN ('crmw','company')` 与 `security_status='D'`。  
- 发行主体：`issuer` / `issuer_code` 模糊匹配。

---

## 3. 接口清单

| 接口 | 说明 |
|------|------|
| `POST .../queryPoolPage` | 债券产品库根树叶子 + 权限 + 现有数量分项 |
| `POST .../querySourcePoolList` | 来源池下拉 |
| `POST .../querySecurityPage` | 候选证券（来源池必选） |
| `POST .../checkAdjust` | 批量校验 |
| `POST .../addAdjustLog` / `addAdjustLogWithFiles` | 批量提交 |

---

## 4. 前端

- 菜单：债券研究 → 存量证券批量调整  
- 可选证券区：来源池多选（必选）、发行主体、代码、简称、市场  
- 未选来源池查询时前端提示，不发起请求  

---

## 5. 主要代码

- Controller：`StockSecurityBatchAdjustController`  
- Service：`StockSecurityBatchAdjustService`  
- Mapper：`StockSecurityBatchAdjustMapper` + XML  
- 实体：`entity.stocksecuritybatchadjust.*`  
- 测试：`StockSecurityBatchAdjustServiceTest`  
