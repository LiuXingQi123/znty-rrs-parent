# 投资池开放日维护需求说明

> 前端页面：`pool_open_day.html`
> 后端前缀：`/api/v1/poolOpenDay`
> 角色定位：维护 `ip_pool_open_day` 中各投资池的可调库开放区间。投资池主表 `open_day_adjust=1` 时，调库校验会要求当日落在本表 `begin_date ~ end_date` 区间内。

---

## 1. 页面概览

单页结构（`el: '#pool_open_day'`），顶栏（日期图标 +「投资池开放日维护」+ 系统名 + 当前日期）+ `list-body`（筛选行 + 表格 + 分页）。

**1 个弹窗**（新增/编辑共用）：投资池、开放起始日、开放结束日、描述。

**初始化**（`created`）：`loadPoolOptions()`（`/api/v1/investmentPool/queryPoolList`）+ `loadList()`（`/queryPoolOpenDayPage`）。

菜单位置：**RRS配置 → 投资池开放日维护**（紧挨「投资池维护」之后）。

---

## 2. 筛选与查询

| 字段 | 控件 | 匹配 |
|---|---|---|
| `poolId` | 投资池树弹层（单选，目录/根节点含子节点时不可选） | `pool_id =` |
| `beginDateFrom` / `endDateTo` | daterange | `begin_date >=` / `end_date <=` |
| `description` | el-input | `description LIKE` |

- 接口：`POST /api/v1/poolOpenDay/queryPoolOpenDayPage`
- 仅返回 `is_deleted` 为空或 0 的记录
- 递归 CTE 拼投资池全称 `poolFullName`（如 `信用债大库/一级库`），并带 `poolName` / `openDayAdjust`
- 列表展示投资池全称，不展示池编码
- 列表「区间状态」由前端按起止日相对今天计算三段：`未开始` / `开放中` / `已结束`（与池级 `open_day_adjust` 开关无关）
- 排序：`begin_date DESC, id DESC`
- 投资池选项来源：`POST /api/v1/common/queryPoolTreeList`

---

## 3. 业务逻辑

### 3.1 新增 `addPoolOpenDay`

校验：`poolId`、`beginDate`、`endDate` 必填；起始日不晚于结束日；描述 ≤200 字；投资池必须存在；**有子池的目录节点不可配置**。  
写入 `is_deleted=0`、`crte_time`/`updt_time=now`，回查明细。

### 3.2 修改 `editPoolOpenDay`

同新增校验，且 `id` 必填、记录未删除；更新字段后回查明细。

### 3.3 删除 `deletePoolOpenDay`

逻辑删除：`is_deleted=1`，`updt_time=now`。

---

## 4. 接口清单

前缀 `/api/v1/poolOpenDay`，统一 `POST` + `@RequestBody` + `ApiResponse<T>`。

| 路径 | 请求体字段 | 返回类型 | 用途 |
|---|---|---|---|
| `queryPoolOpenDayPage` | `poolId?, beginDateFrom?, endDateTo?, description?, pageIndex, pageSize` | `PageResult<PoolOpenDayDto>` | 分页查询 |
| `addPoolOpenDay` | `poolId, beginDate, endDate, description?` | `PoolOpenDayDto` | 新增 |
| `editPoolOpenDay` | `id, poolId, beginDate, endDate, description?` | `PoolOpenDayDto` | 修改 |
| `deletePoolOpenDay` | `id` | `PoolOpenDayDto`（仅 id） | 逻辑删除 |

---

## 5. 表结构

主表：`ip_pool_open_day`

| 列 | 说明 |
|---|---|
| `id` | 主键 |
| `pool_id` | 投资池 ID |
| `begin_date` / `end_date` | 开放区间 |
| `description` | 描述 |
| `is_deleted` | 0 正常 / 1 已删除 |
| `crte_time` / `updt_time` | 创建/修改时间 |

关联：`ip_investment_pool.open_day_adjust` 控制是否启用开放日校验；本页只维护区间数据。

---

## 6. 与调库校验的关系

证券池 / 禁投池 / CRMW / 批量调库在 `open_day_adjust=1` 时调用 `queryPoolInOpenDay(poolId, today)`：

```sql
SELECT COUNT(1) > 0 FROM ip_pool_open_day
WHERE pool_id = ? AND begin_date <= today AND end_date >= today AND is_deleted = 0
```

不在开放区间内则阻断调入/调出。
