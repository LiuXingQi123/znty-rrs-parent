# CRMW池查询需求说明

> 前端页面：`crmw_pool_query.html`
> 后端前缀：`/api/v1/crmwPoolQuery`
> 角色定位：查询当前在 CRMW 池（信用风险缓释工具池）中已生效（`audit_status='20'`）的凭证记录，支持按 CRMW 代码/名称、入池时间、调整人筛选，并可点击跳转详情。

---

## 1. 页面概览

单页结构（`el: '#crmw_pool_query'`），无步骤切换。顶栏（图标 +「CRMW池查询」+ 系统名 + 当天日期 `today`）+ `list-body`（`search-panel` 筛选区 + `table-card` 表格与分页）。

**初始化**（`mounted`）：`this.loadList()`；同时绑定 `window resize` → `refreshTableLayout`（重算 `crmwTableRef.doLayout()`）。`baseURL` 由 `js/api.js` 注入（`http://localhost:18090`），`apiPost` 返回 `{ success, data, message }`，`!json.success` 弹错并抛异常。初始分页 `pageIndex=1, pageSize=20`，`page-sizes=[10,20,50,100]`。

---

## 2. 筛选与查询逻辑

### 2.1 筛选项（`searchForm`）

| 控件 | 绑定字段 | 匹配 |
|---|---|---|
| el-input | `crmwScode` | 模糊 |
| el-input | `crmwName` | 模糊 |
| el-date-picker daterange | `entryTimeRange` | 范围（`value-format="yyyy-MM-dd"`） |
| el-input | `adjusterName` | 模糊 |

- **查询** `handleSearch()`：重置 `pageIndex=1` 后 `loadList()`。
- **重置** `handleReset()`：清空筛选后重查。

### 2.2 loadList → 接口

- 路径：`POST /api/v1/crmwPoolQuery/queryCrmwPoolPage`
- 请求体（`CrmwPoolQueryReq extends PageRequest`）：`{ crmwScode, crmwName, entryTimeStart, entryTimeEnd, adjusterName, pageIndex, pageSize }`。前端 `loadList` 将 `entryTimeRange[0]+' 00:00:00'` / `[1]+' 23:59:59'` 拼接为 `entryTimeStart`/`entryTimeEnd`。
- 后端 `CrmwPoolQueryService.queryCrmwPoolPage`：`PageHelper.startPage` → `crmwPoolQueryMapper.queryCrmwPoolPage` → `PageInfo` → `PageResult`。
- SQL（`CrmwPoolQueryMapper.xml` `queryCrmwPoolPage`）：

```sql
SELECT ips.id, ips.adjust_log_id, ips.adjust_batch_no, ips.crmw_name, ips.crmw_scode,
       ips.security_short_name, ips.security_code, ips.adjuster_name, ips.entry_time,
       ips.target_pool_id, p.pool_name AS target_pool_name
FROM ip_pool_status_crmw ips
LEFT JOIN ip_investment_pool p ON p.id = ips.target_pool_id AND p.is_deleted = 0
<where>
    ips.is_deleted = 0
    AND ips.audit_status = '20'
    AND ips.pool_type = 'crmw'
    <if crmwScode>  AND ips.crmw_scode LIKE CONCAT('%', #{crmwScode}, '%') </if>
    <if crmwName>   AND ips.crmw_name LIKE CONCAT('%', #{crmwName}, '%') </if>
    <if entryTimeStart> AND ips.entry_time >= #{entryTimeStart} </if>
    <if entryTimeEnd>   AND ips.entry_time <= #{entryTimeEnd} </if>
    <if adjusterName> AND ips.adjuster_name LIKE CONCAT('%', #{adjusterName}, '%') </if>
</where>
ORDER BY ips.entry_time DESC, ips.id DESC
```

- **固定过滤**：`is_deleted=0 AND audit_status='20' AND pool_type='crmw'` — 只返回当前有效的 CRMW 池记录。
- **Service 后处理**：`fillPoolFullName` 用 `investmentPoolService.queryPoolFullNameMap()`（递归 CTE 全路径映射）覆盖 `targetPoolName` 为全路径名。
- 返回 `PageResult<CrmwPoolQueryDto>`，前端取 `data.records` 与 `data.total`。

### 2.3 表格列与跳转

| 列 | prop | 渲染 |
|---|---|---|
| 序号 | `$index` | `(pageIndex-1)*pageSize+$index+1` |
| CRMW名称 | `crmwName` | `desc-link` 可点击 → `openCrmwAdjustDetail(row)` |
| CRMW代码 | `crmwScode` | 同上 |
| 证券名称 | `securityShortName` | 同上 |
| 证券代码 | `securityCode` | 同上 |
| 调整人 | `adjusterName` | 直接显示 |
| 入池时间 | `entryTime` | `moment(entryTime).format('YYYY-MM-DD HH:mm')`，空则空 |
| 投资池名称 | `targetPoolName` | 全路径名 |

**跳转**：`openCrmwAdjustDetail(row)` 拼 `securityCode/crmwScode/targetPoolId/adjustLogId/adjustBatchNo/entryMode=view`，跳 `crmw_pool_adjust_detail.html?...`。

---

## 3. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `crmwPoolQuery/queryCrmwPoolPage` | crmwScode, crmwName, entryTimeStart, entryTimeEnd, adjusterName, pageIndex, pageSize | `PageResult<CrmwPoolQueryDto>` | CRMW池当前有效记录分页查询（仅 `audit_status='20'`） |

> 路径带前缀 `/api/v1/`。该 Controller **只有一个接口**（无下拉选项接口）。

---

## 4. 关键数据库表

### 4.1 `ip_pool_status_crmw`（CRMW 投资池当前状态表，主表）

`id, security_code, security_short_name, security_type, crmw_name, crmw_scode, crmw_mktcode, crmw_stype, adjust_type, adjust_mode, adjust_batch_no, adjust_log_id, target_pool_id, target_pool_name, pool_type, flow_id, flow_key, flow_type, audit_status, adjuster_id, adjuster_name, adjust_reason, adjust_advice, submit_time, audit_time, entry_time, is_deleted, crte_time, updt_time`

### 4.2 `ip_investment_pool`（LEFT JOIN 取池名）

---

## 5. 状态流转

查询页固定 `audit_status='20'`（审批通过，已生效），不涉及状态变迁。状态枚举与全站一致（`-1/00/11/20/21/32/99`），见 [19-crmw-pool-adjust.md](19-crmw-pool-adjust.md)。

---

## 6. 与其他池模块的差异

- **vs 证券池查询（07）**：CRMW 查询页**极简** — 无投资池树（`queryPoolTreeList`）、无证券类型下拉、无「我的证券池」收藏、无证券状态过滤；筛选项为 CRMW 代码/名称 + 入池时间 + 调整人。
- **vs 禁投池查询（08）**：CRMW 用独立表 `ip_pool_status_crmw`（含 `crmw_name/crmw_scode/crmw_mktcode/crmw_stype` 字段），禁投池复用 `ip_pool_status` 并以 `pool_type='forbidden'` 过滤。
- 表格列以 CRMW 名称/代码为主键展示，证券名称/代码为标的证券。

---

## 7. 验收标准

- 仅返回 `audit_status='20' AND pool_type='crmw' AND is_deleted=0` 的记录。
- 入池时间范围筛选边界含当日 00:00:00 ~ 23:59:59。
- 投资池名称显示全路径。
- 点击 CRMW 名称/代码/证券名称/代码均跳转详情页（`entryMode=view`）。

## 8. 关键源码索引

- 前端：`znty-rrs-ui/crmw_pool_query.html`（`loadList`、`openCrmwAdjustDetail`、`refreshTableLayout`）
- Controller：`CrmwPoolQueryController.java`（`@RequestMapping("/api/v1/crmwPoolQuery")`）
- Service：`CrmwPoolQueryService.java`（`queryCrmwPoolPage`、`fillPoolFullName`）
- Mapper：`CrmwPoolQueryMapper.java` / `resources/mapper/CrmwPoolQueryMapper.xml`
- 实体：`entity/crmwpoolquery/CrmwPoolQueryReq.java`、`CrmwPoolQueryDto.java`
- SQL：`sql/rrs_crmw_pool_status_schema.sql`、`sql/rrs_crmw_pool_status_demo_data.sql`
