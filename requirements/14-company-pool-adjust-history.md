# 主体池调整历史需求说明

> 前端页面：`company_pool_adjust_history.html`
> 后端前缀：`/api/v1/companyPoolAdjustHistory`、`/api/v1/common`
> 角色定位：风控、合规和审计人员从发行主体维度查询主体的调库流水（含全部状态），追溯主体池调整历史。

---

## 0. 关键现状（数据来源）

**主体池调整历史不是按主体聚合，而是逐条明细展示**，复用 `ip_adjust_log`（证券池调库记录表），通过 `INNER JOIN dict_security_type dst ON dst.category_type='company'` 切出主体类记录。

核心机制：
1. `ip_adjust_log` 是证券池调库与主体池调库**共用**的流水表。每一次调库申请（无论调入/调出、无论状态）都写一条。
2. 主体与证券的区分靠 `dict_security_type.category_type='company'`（与主体池查询 [09](09-company-pool-query.md) 一致）。
3. **字段重命名映射**：`al.security_short_name AS companyName`、`al.security_code AS companyCode`。即调库记录里 `security_*` 字段存的就是主体代码/主体名称（主体作为伪证券入调库流水）。
4. **展示全部状态**：WHERE 只限定 `al.is_deleted=0`，**不限定 `audit_status`**。一条主体从「已提交待审核(00)→审核通过待审批(10)→审批通过(20)」会形成多条 `ip_adjust_log`（不同批次号），每条都列出。
5. **不按主体聚合、不按批次号聚合**：无 GROUP BY、无 DISTINCT、无 `adjust_batch_no` 排序。同一主体的多次调库各自成行，仅按 `submit_time` 倒序——这与证券级 `AdjustHistoryMapper`（按 `adjust_batch_no DESC` 聚拢）不同。

---

## 1. 页面概览与初始化

根容器 `#company_pool_adjust_history`。布局：顶栏（标题「主体池调整历史查询」+ 系统名 + 当天日期）+ 列表区（搜索面板 + 表格卡片含分页）。

**初始化**（`mounted`）：
1. `axios.defaults.baseURL = 'http://localhost:18090'`
2. `loadPoolTree()` — 加载投资池树
3. `loadList()` — 首次加载列表

表格列：序号、调整人（`adjusterName`）、提交日期（`submitTime`，`YYYY-MM-DD HH:mm`）、主体名称（`companyName`）、主体代码（`companyCode`）、调整类型（`adjustType`）、调整方向（`adjustMode`，调入=success 绿、调出=danger 红 `el-tag`）、投资池名称（`targetPoolName`）、审批状态（`auditStatus`，按 `auditStatusType` 染色 + `auditStatusLabel` 中文）。

主体名称/主体代码同样是 `desc-link` 样式，但**未绑定 `@click`**，无跳转。总数徽标文案「共 X 条记录」。

---

## 2. 筛选项与查询逻辑

### 2.1 筛选项

比主体池查询页多 4 个条件：

| 控件 | 字段 | 说明 |
|---|---|---|
| 投资池选择器（树多选） | `poolIds` | 同主体池查询页 |
| 主体代码 | `companyCode` | 模糊 |
| 主体名称 | `companyName` | 模糊（主体池查询页无此项） |
| 调整日期范围 | `adjustTimeRange` | daterange，对应 `submit_time` |
| 调整人 | `adjusterName` | 模糊 |
| 调整方向 | `adjustMode` | 下拉：调入/调出 |
| 调整状态 | `auditStatus` | 下拉 8 项 |
| 查询 / 重置 | — | — |

投资池树逻辑与主体池查询页**几乎完全相同**，但有一处差异：`handlePoolTreeConfirm` **只取 `getCheckedKeys()`，不合并 `getHalfCheckedKeys()`**（因父节点 disabled，半选本就不该出现，行为上等价，但写法更严格）。

审批状态字典（页面内联，与 `dict.js DICT_AUDIT_STATUS` 一致）：`-1/00/10/11/20/21/32/99`。染色：`20/10/32`→success、`21/-1`→danger、`00/11`→warning、`99`→info。

### 2.2 查询接口

- 路径：`POST /api/v1/companyPoolAdjustHistory/queryCompanyPoolAdjustHistoryPage`
- 请求体（`CompanyPoolAdjustHistoryReq`）：
  ```json
  { "poolIds": [2,3] | null, "companyCode": "C100" | null, "companyName": "交通" | null,
    "adjustTimeStart": "2026-06-01" | null, "adjustTimeEnd": "2026-06-30" | null,
    "adjusterName": "管理" | null, "adjustMode": "调入" | null, "auditStatus": "20" | null,
    "pageIndex": 1, "pageSize": 20 }
  ```
  注意：调整日期前端传 `yyyy-MM-dd`（不补秒），**后端**对 `adjustTimeEnd` 拼 `' 23:59:59'`，`adjustTimeStart` 直接比较。这与主体池查询页（前端补秒）的分工相反。
- 后端 `CompanyPoolAdjustHistoryService`：`PageHelper.startPage` 分页；`queryCompanyPoolAdjustHistoryPage` SQL；`fillPoolFullName`（用投资池全路径名覆盖 `targetPoolName`）；返回 `PageResult`。
- 返回 `PageResult<CompanyPoolAdjustHistoryDto>`（`id, adjusterName, submitTime, companyName, companyCode, adjustType, adjustMode, targetPoolName, targetPoolId, auditStatus`）。

### 2.3 分页

与主体池查询页一致（默认 20、可选 10/20/50/100、后端封顶 100）。排序 `submit_time DESC, id DESC`。

---

## 3. 按主体聚合调库记录的逻辑

**实际上没有「按主体聚合」**——与主体池查询一样，是**逐条明细展示**。核心 SQL 见第 5 节。无 GROUP BY、无聚合函数、无批次号排序，每条 `ip_adjust_log` 一行。

---

## 4. 跳转详情的参数传递

**无详情跳转。** 两页的「主体名称/主体代码」虽渲染为 `desc-link`（蓝色可悬停下划线），但均未绑定 `@click`。表格也无操作列、无行点击事件。当前实现下，主体池查询/历史是纯查询展示页，不下钻到主体详情或调库审批详情。

---

## 5. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `common/queryPoolTreeList` | `{}` | `List<PoolTreeDto>` | 投资池树 |
| `companyPoolAdjustHistory/queryCompanyPoolAdjustHistoryPage` | poolIds, companyCode, companyName, adjustTimeStart, adjustTimeEnd, adjusterName, adjustMode, auditStatus, pageIndex, pageSize | `PageResult<CompanyPoolAdjustHistoryDto>` | 主体池调库历史分页（含所有状态，category_type='company'） |

> 路径均带前缀 `/api/v1/`。

---

## 6. 关键数据库表与查询逻辑

涉及表：
- `ip_adjust_log`（证券池调库记录表）：调库记录流水，主体池与证券池共用。
- `dict_security_type`：用于 `category_type='company'` 过滤。
- `ip_investment_pool`：提供 `pool_name`，Service 层再用全路径覆盖。

**核心 SQL**（`CompanyPoolAdjustHistoryMapper.xml`）：
```sql
SELECT al.id, al.adjuster_name, al.submit_time,
       al.security_short_name AS companyName,
       al.security_code       AS companyCode,
       al.adjust_type, al.adjust_mode,
       al.target_pool_id, p.pool_name AS target_pool_name,
       al.audit_status
FROM ip_adjust_log al
INNER JOIN dict_security_type dst ON dst.security_type = al.security_type
                                 AND dst.is_deleted = 0
                                 AND dst.category_type = 'company'
LEFT JOIN ip_investment_pool p ON p.id = al.target_pool_id AND p.is_deleted = 0
WHERE al.is_deleted = 0
  ...动态条件
ORDER BY al.submit_time DESC, al.id DESC
```

要点：
- `INNER JOIN dict_security_type dst` 且 ON 上带 `dst.is_deleted=0 AND dst.category_type='company'`（category_type 放在 ON 而非 WHERE，语义等价但写法与主体池查询不同）。
- `LEFT JOIN ip_investment_pool p ON p.id=al.target_pool_id AND p.is_deleted=0`（**带了** `p.is_deleted=0`，与主体池查询 XML 的 LEFT JOIN 不同）。
- WHERE 固定 `al.is_deleted=0`；动态 `<if>`：`poolIds`(IN)、`companyCode`(LIKE)、`companyName`(LIKE security_short_name)、`adjustTimeStart/End`(submit_time 范围，end 拼 23:59:59)、`adjusterName`(LIKE)、`adjustMode`(=)、`auditStatus`(=)。
- 排序 `submit_time DESC, id DESC`。
- Service 层 `fillPoolFullName` 用 `queryPoolFullNameMap()` 覆盖为全路径。

---

## 7. 与主体池查询的差异对比，以及与证券级历史的区别

### 7.1 主体池查询 vs 主体池调整历史

| 维度 | 主体池查询（09） | 主体池调整历史（本文） |
|---|---|---|
| 数据源表 | `ip_pool_status`（当前状态表） | `ip_adjust_log`（调库流水表） |
| 语义 | 「当前在池的主体」——快照 | 「主体调库的全量历史」——流水 |
| `audit_status` 过滤 | **仅 `='20'`**（审批通过/已入池） | **不过滤**，全部状态可见（可用筛选举某状态） |
| 主体字段命名 | `companyCode`(Req) / `securityCode,securityShortName`(DTO) | `companyCode,companyName`(Req/DTO，SQL AS 重命名) |
| 时间筛选项 | 入池时间 `entryTime` | 提交时间 `submitTime`（调整日期） |
| 时间补秒方 | 前端补 `00:00:00`/`23:59:59` | 后端对 end 拼 ` 23:59:59` |
| 额外筛选 | 无 | 多 `companyName`、`adjustMode`、`auditStatus` 三个 |
| 表格列 | 5 列（无状态/方向/类型） | 8 列（多调整类型、调整方向、审批状态） |
| 排序 | `entry_time DESC, id DESC` | `submit_time DESC, id DESC` |
| 总数文案 | 「共 X 个主体」 | 「共 X 条记录」 |
| 详情跳转 | 无 | 无 |
| 投资池树确认 | 合并 checked + halfChecked | 仅 checked |

**关联**：两页是同一批「主体」数据的两个视角——查询页看「现在哪些主体在哪些池里（已生效）」，历史页看「这些主体是怎么一步步被调入/调出的（含未生效）」。二者通过 `security_type` 属于 `category_type='company'` 统一主体边界；调库审批通过（`audit_status='20'`）后写入 `ip_pool_status`，即从历史页「沉淀」到查询页。

### 7.2 与证券级历史的区别

| 维度 | 主体池调整历史（本文） | 证券池调整历史（AdjustHistory，[10](10-adjust-history.md)） |
|---|---|---|
| category_type 过滤 | `='company'`（INNER JOIN dict） | 不过滤，全部证券类型 |
| 关联 rrs_securityinfo | 否 | 是（取 issuer、到期状态） |
| 字段口径 | `security_short_name`→主体名称 | `security_short_name`→证券简称，另带 `issuer` |
| 证券状态筛选 | 无 | 有（存续/到期，CASE WHEN 计算） |
| 发行主体筛选 | 无（用「主体名称」替代） | 有（issuer LIKE） |
| 证券类型筛选 | 无 | 有（securityType 精确 + 下拉接口） |
| 「我的」过滤 | 无 | 有（myBonds + currentUserId，按 adjuster_id） |
| 排序 | `submit_time DESC, id DESC` | `adjust_batch_no DESC, submit_time DESC, id DESC`（按批次号聚拢） |
| DTO 投资池字段名 | `targetPoolName` | `targetPoolPath`（命名不同） |

核心区别：主体池历史 = 证券池历史的「主体子集」视图，共用 `ip_adjust_log`，区别在 SQL 是否 JOIN `dict_security_type` 并限定 `category_type='company'`。主体池历史**不按批次号聚拢**，仅按提交时间倒序；证券池历史按 `adjust_batch_no DESC` 排序便于同批次记录排在一起。

---

## 8. 验收标准

- 组合筛选（主体/投资池/方向/状态/时间）与分页正确，返回全部状态的主体调库流水。
- 审批状态字典映射正确（8 项）。
- 投资池名称显示全路径。
- `CompanyPoolAdjustHistoryApiTest`（如新增）覆盖页面接口。

## 9. 关键源码索引

- 前端：`znty-rrs-ui/company_pool_adjust_history.html`（`loadPoolTree`/`buildPoolTree`、`loadList`、`auditStatusLabel`/`auditStatusType`、`handlePoolTreeConfirm`）
- Controller：`CompanyPoolAdjustHistoryController.java`、`CommonController.java`
- Service：`CompanyPoolAdjustHistoryService.java`（`queryCompanyPoolAdjustHistoryPage`、`fillPoolFullName`）、`InvestmentPoolService.java`
- Mapper：`CompanyPoolAdjustHistoryMapper.xml`、`CommonMapper.xml`
- 实体：`CompanyPoolAdjustHistoryReq`、`CompanyPoolAdjustHistoryDto`
- SQL：`sql/rrs_security_pool_adjust_schema.sql`（ip_adjust_log）、`sql/rrs_dict_schema.sql`、`sql/rrs_pool_init_schema.sql`
