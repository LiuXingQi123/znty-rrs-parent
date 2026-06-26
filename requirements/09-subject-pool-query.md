# 主体池查询需求说明

> 前端页面：`company_pool_query.html`（发行主体池查询）
> 后端前缀：`/api/v1/subjectPoolQuery`、`/api/v1/common`
> 角色定位：信用研究、投资和风控人员从发行主体维度查询主体在各投资池中的准入状态，辅助主体风险和投资限额管理。

---

## 0. 关键现状（数据来源）

**主体池不是按 issuer 聚合证券池状态而来，而是独立表 `ip_pool_status` 中 `security_type` 属于 `dict_security_type.category_type='company'` 的记录**。

核心机制：
1. `ip_pool_status` 是证券池与主体池**共用**的「当前在池状态表」，字段同时承载债券证券和公司主体两类记录。
2. 主体与证券的区分靠 `dict_security_type.category_type`（bond/stock/fund/company）。`category_type='company'` 的记录即「公司主体」类型。
3. **「主体」被当作一种伪证券登记入池**：`ip_pool_status` 中主体记录的 `security_code` 存主体代码（如 `C10001`）、`security_short_name` 存主体名称。DTO 把 `security_short_name` 映射为「主体名称」、`security_code` 映射为「主体代码」。
4. **仅展示已生效状态**：`ips.audit_status='20'`（审批通过），即只看「当前在池」的主体。
5. **没有 GROUP BY、没有 issuer 聚合**：一个主体进入多个池会产生多条 `ip_pool_status` 记录，每条独立展示。

---

## 1. 页面概览与初始化

根容器 `#company_pool_query`，布局：顶栏（图标 +「主体池查询」+ 系统名 + 当天日期）+ 列表区（搜索面板 + 表格卡片含分页）。

**初始化**（`mounted`）：
1. `axios.defaults.baseURL = 'http://localhost:18090'`
2. `loadPoolTree()` — 加载投资池树
3. `loadList()` — 首次加载主体池列表（无筛选，第 1 页 20 条）

表格列：序号、主体名称（`securityShortName`）、主体代码（`securityCode`）、调整人（`adjusterName`）、入池时间（`entryTime`，`YYYY-MM-DD HH:mm`）、投资池名称（`targetPoolName`）。主体名称/主体代码以 `desc-link` 样式呈现，但**未绑定 `@click`**，仅视觉上是链接，无跳转。

总数徽标文案「共 X 个主体」。

---

## 2. 筛选项与查询逻辑

### 2.1 筛选项

| 控件 | 字段 | 说明 |
|---|---|---|
| 投资池选择器（popover + tree，多选） | `poolIds` | 父节点禁用，仅叶子可选；`check-strictly` |
| 主体代码（文本输入） | `subjectCode` | 模糊，回车查询 |
| 入池时间范围（daterange） | `entryTimeRange` | `value-format='yyyy-MM-dd'` |
| 调整人（文本输入） | `adjusterName` | 模糊，回车查询 |
| 查询 / 重置 | — | 查询重置页码为 1；重置清空全部条件并清树勾选 |

投资池树加载：`loadPoolTree()` → `POST /api/v1/common/queryPoolTreeList`（body `{}`）→ 扁平节点列表 `{id, parentId, poolName, poolFullName}`，前端 `buildPoolTree` 按 `parentId` 组装嵌套树，每节点计算 `fullPath`。`handlePoolTreeConfirm` 合并 `getCheckedKeys()` 与 `getHalfCheckedKeys()` 一并放入 `poolIds`。

### 2.2 查询接口

- 路径：`POST /api/v1/subjectPoolQuery/querySubjectPoolPage`
- 请求体（`SubjectPoolQueryReq`）：
  ```json
  { "poolIds": [2,3] | null, "subjectCode": "C100" | null,
    "entryTimeStart": "2026-05-01 00:00:00" | null, "entryTimeEnd": "2026-05-31 23:59:59" | null,
    "adjusterName": "管理" | null, "pageIndex": 1, "pageSize": 20 }
  ```
  前端将日期范围补全为 `起 00:00:00`/`止 23:59:59`；`poolIds` 为空数组时传 null。
- 后端 `SubjectPoolQueryService`：`PageHelper.startPage` 分页；`querySubjectPoolPage` SQL；`fillPoolFullName`（用投资池全路径名覆盖 `targetPoolName`）；返回 `PageResult`。
- 返回 `PageResult<SubjectPoolQueryDto>`（`id, securityShortName(主体名称), securityCode(主体代码), adjusterName, entryTime, targetPoolName, targetPoolId`）。

### 2.3 分页

`pageIndex` 默认 1、`pageSize` 默认 20，可选 `[10,20,50,100]`；后端 `PageRequest.getPageSize()` 硬上限 100。`handleSizeChange` 同时重置到第 1 页。排序 `entry_time DESC, id DESC`。

---

## 3. 关键校验与可见数据范围

- **可见范围** = `ip_pool_status` 中 `is_deleted=0` 且 `audit_status='20'` 且 `security_type` 属于 `category_type='company'` 的记录。无角色/用户级数据权限过滤（无「我的主体池」概念）。
- 筛选条件全部可选；`poolIds` 为空时不过滤投资池；`subjectCode`/`adjusterName` 走 `LIKE CONCAT('%', ?, '%')`。
- 时间范围：`entry_time >= start` 且 `entry_time <= end`，前端补秒。
- `pageSize` 后端封顶 100；`pageIndex` <1 自动归 1。
- 投资池树只返回 `is_deleted=0` 的池；已停用（`status='disabled'`）的池**仍会出现在树中**（SQL 未过滤 status）。

---

## 4. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `common/queryPoolTreeList` | `{}` | `List<PoolTreeDto>{id, parentId, poolName, poolFullName}` | 投资池树（含全路径） |
| `subjectPoolQuery/querySubjectPoolPage` | poolIds, subjectCode, entryTimeStart, entryTimeEnd, adjusterName, pageIndex, pageSize | `PageResult<SubjectPoolQueryDto>` | 主体池分页查询（仅 audit_status='20' 且 category_type='company'） |

> 路径均带前缀 `/api/v1/`。

---

## 5. 关键数据库表与查询逻辑

涉及表：
- `ip_pool_status`（投资池当前状态表）：主体池与证券池共用。
- `dict_security_type`（证券类型字典）：`security_type` + `category_type`（bond/stock/fund/company）。主体池查询靠 `category_type='company'` 切分主体记录。
- `ip_investment_pool`（投资池主表）：提供 `pool_name`，并通过递归 CTE 生成 `pool_full_name`。

**核心 SQL**（`SubjectPoolQueryMapper.xml`）：
```sql
SELECT ips.id, ips.security_code, ips.security_short_name, ips.adjuster_name,
       ips.entry_time, ips.target_pool_id, p.pool_name AS target_pool_name
FROM ip_pool_status ips
JOIN dict_security_type dst ON ips.security_type = dst.security_type
LEFT JOIN ip_investment_pool p ON ips.target_pool_id = p.id
WHERE ips.is_deleted = 0
  AND ips.audit_status = '20'
  AND dst.category_type = 'company'
  ...动态条件
ORDER BY ips.entry_time DESC, ips.id DESC
```

要点：
- `JOIN dict_security_type dst`（INNER JOIN，确保只取 `category_type='company'` 的记录）。
- `LEFT JOIN ip_investment_pool p`（**未带** `p.is_deleted=0` 条件，与调整历史 XML 不同；通常以 Service 层 `queryPoolFullNameMap` 覆盖为准）。
- 无 GROUP BY、无聚合函数，每条 `ip_pool_status` 一行。
- Service 层 `fillPoolFullName` 用 `InvestmentPoolService.queryPoolFullNameMap()`（递归 CTE 拼 `父池名/子池名` 全路径）按 `targetPoolId` 覆盖为全路径名称。

---

## 6. 与证券池查询的区别

| 维度 | 主体池查询 | 证券池查询（SecurityPoolQuery） |
|---|---|---|
| category_type 过滤 | `='company'`（INNER JOIN dict） | 不过滤，全部证券类型 |
| 关联 sirm_securityinfo | 否 | 是（取 issuer/评级/到期/票面/全称等） |
| 字段口径 | `security_short_name`→主体名称 | `security_short_name`→证券简称，另带 `issuer` |
| 证券状态筛选 | 无 | 有（active/matured，按 maturity_date 与 CURDATE 比较） |
| 发行主体筛选 | 无 | 有（issuer LIKE） |
| 证券类型筛选 | 无 | 有（securityType 精确 + 下拉接口） |
| 「我的」过滤 | 无 | 有（mySecurities + currentUserId，联 my_security_pool） |
| 收藏功能 | 无 | 有 |

核心区别：主体池 = 证券池的「主体子集」视图。两者**共用同一物理表** `ip_pool_status`，区别仅在 SQL 是否 JOIN `dict_security_type` 并限定 `category_type='company'`。主体被当作一类 `security_type='company'` 的伪证券登记入池。主体池是简化版：不联 `sirm_securityinfo`（主体没有到期日/票面利率等债券属性），因此没有证券状态、发行主体、证券类型、收藏等筛选。

## 7. 验收标准

- 主体关键字和投资池组合筛选正确，只返回当前在池（audit_status='20'）的主体。
- 投资池名称显示全路径。
- `SubjectPoolQueryApiTest` 覆盖页面全部接口。

## 8. 关键源码索引

- 前端：`znty-sirm-ui/company_pool_query.html`（`loadPoolTree`/`buildPoolTree`、`loadList`、`handlePoolTreeConfirm`）
- Controller：`SubjectPoolQueryController.java`、`CommonController.java`
- Service：`SubjectPoolQueryService.java`（`querySubjectPoolPage`、`fillPoolFullName`）、`InvestmentPoolService.java`（`queryPoolFullNameMap`）
- Mapper：`SubjectPoolQueryMapper.xml`、`CommonMapper.xml`
- 实体：`SubjectPoolQueryReq`、`SubjectPoolQueryDto`、`PoolTreeDto`
- SQL：`sql/sirm_security_pool_adjust_schema.sql`（ip_pool_status）、`sql/sirm_dict_schema.sql`（dict_security_type）、`sql/sirm_pool_init_schema.sql`
