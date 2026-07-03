# CRMW池调整历史查询需求说明

> 前端页面：`crmw_pool_adjust_history.html`
> 后端前缀：`/api/v1/crmwPoolAdjustHistory`
> 角色定位：查询 CRMW 池调库流水（`ip_adjust_log` 以 `pool_type='crmw'` 过滤），返回**所有状态**（含 `20/21/99/-1` 终态）的调库记录，可点击跳转详情。

---

## 1. 页面概览

单页结构（`el: '#crmw_pool_adjust_history'`），顶栏（图标 +「CRMW池调整历史查询」+ 系统名 + 当天日期 `today`）+ `list-body`（`search-panel` 筛选区 + `table-card` 表格与分页）。

**初始化**（`mounted`）：`this.loadList()`；绑定 `window resize` → `refreshTableLayout`。`baseURL` 由 `js/api.js` 注入（`http://localhost:18090`）。初始分页 `pageIndex=1, pageSize=20`，`page-sizes=[10,20,50,100]`。**未引入 `dict.js`**：审核状态与调整方向字典内联。

---

## 2. 筛选与查询逻辑

### 2.1 筛选项（`searchForm`）

| 字段 | 控件 | 匹配 |
|---|---|---|
| `crmwScode` | el-input | 模糊 |
| `crmwName` | el-input | 模糊 |
| `adjustTimeRange` | el-date-picker daterange | 范围（`value-format="yyyy-MM-dd"`） |
| `adjusterName` | el-input | 模糊 |
| `adjustMode` | el-select | 精确（`[{调入},{调出}]`） |
| `auditStatus` | el-select | 精确（8 项内联字典） |

**内联字典**：
- `adjustModeOptions`：`[{value:'调入',label:'调入'},{value:'调出',label:'调出'}]`
- `auditStatusOptions`（8 项）：`-1/00/10/11/20/21/32/99`

### 2.2 loadList → 接口

- 路径：`POST /api/v1/crmwPoolAdjustHistory/queryCrmwPoolAdjustHistoryPage`
- 请求体（`CrmwPoolAdjustHistoryReq extends PageRequest`）：`{ crmwScode, crmwName, adjustTimeStart, adjustTimeEnd, adjusterName, adjustMode, auditStatus, pageIndex, pageSize }`。前端将 `adjustTimeRange[0]+' 00:00:00'` / `[1]+' 23:59:59'` 拼接。
- 返回 `PageResult<CrmwPoolAdjustHistoryDto>`。
- SQL（`CrmwPoolAdjustHistoryMapper.xml` `queryCrmwPoolAdjustHistoryPage`）：

```sql
SELECT al.id, al.adjust_batch_no, al.adjuster_name, al.submit_time, al.crmw_name, al.crmw_scode,
       al.security_short_name, al.security_code, al.adjust_type, al.adjust_mode,
       al.target_pool_id, p.pool_name AS target_pool_name, al.audit_status
FROM ip_adjust_log al
LEFT JOIN ip_investment_pool p ON p.id = al.target_pool_id AND p.is_deleted = 0
<where>
    al.is_deleted = 0
    AND al.pool_type = 'crmw'
    <if crmwScode>  AND al.crmw_scode LIKE CONCAT('%', #{crmwScode}, '%') </if>
    <if crmwName>   AND al.crmw_name LIKE CONCAT('%', #{crmwName}, '%') </if>
    <if adjustTimeStart> AND al.submit_time >= #{adjustTimeStart} </if>
    <if adjustTimeEnd>   AND al.submit_time <= CONCAT(#{adjustTimeEnd}, ' 23:59:59') </if>
    <if adjusterName> AND al.adjuster_name LIKE CONCAT('%', #{adjusterName}, '%') </if>
    <if adjustMode>  AND al.adjust_mode = #{adjustMode} </if>
    <if auditStatus> AND al.audit_status = #{auditStatus} </if>
</where>
ORDER BY al.submit_time DESC, al.id DESC
```

- **固定过滤**：`is_deleted=0 AND pool_type='crmw'` — 读共享 `ip_adjust_log` 表，按 CRMW 过滤。
- **返回所有状态**（含 `20/21/99/-1` 终态），与查询页（只返回 `20`）不同。
- **Service 后处理**：`fillPoolFullName` 用 `investmentPoolService.queryPoolFullNameMap()` 覆盖 `targetPoolName` 为全路径名。

### 2.3 表格列与跳转

| 列 | prop | 渲染 |
|---|---|---|
| 序号 | `$index` | `(pageIndex-1)*pageSize+$index+1` |
| 调整人 | `adjusterName` | 直接显示 |
| 提交时间 | `submitTime` | `moment(submitTime).format('YYYY-MM-DD HH:mm')` |
| CRMW名称 | `crmwName` | `desc-link` 可点击 → `openCrmwAdjustDetail(row)` |
| CRMW代码 | `crmwScode` | 同上 |
| 证券名称 | `securityShortName` | 同上 |
| 证券代码 | `securityCode` | 同上 |
| 调整类型 | `adjustType` | `el-tag`（`adjustTypeTagType`：手工 info / 联动 warning / 互斥 danger） |
| 调整方向 | `adjustMode` | `el-tag`（调入 success / 调出 danger） |
| 投资池名称 | `targetPoolName` | 全路径名 |
| 审核状态 | `auditStatus` | `el-tag`（`auditStatusType`：20/10/32→success；21/-1→danger；00/11→warning；99→info） |

**跳转**：`openCrmwAdjustDetail(row)` 拼 `securityCode/crmwScode/targetPoolId/adjustLogId(row.id)/adjustBatchNo/entryMode=view`，跳 `crmw_pool_adjust_detail.html?...`。

---

## 3. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `crmwPoolAdjustHistory/queryCrmwPoolAdjustHistoryPage` | crmwScode, crmwName, adjustTimeStart, adjustTimeEnd, adjusterName, adjustMode, auditStatus, pageIndex, pageSize | `PageResult<CrmwPoolAdjustHistoryDto>` | CRMW 池调库历史分页查询（所有状态） |

> 路径带前缀 `/api/v1/`。该 Controller **只有一个接口**（无下拉选项接口）。

---

## 4. 关键数据库表

### 4.1 `ip_adjust_log`（主表，共享调库记录表）

`pool_type='crmw'` 过滤；含 `crmw_name/crmw_scode` 字段。历史页看调库流水，**不关联** `ip_pool_status_crmw`（与查询页区别）。

### 4.2 `ip_investment_pool`（LEFT JOIN 取池名）

---

## 5. 状态流转

历史页展示全部 `audit_status`（`-1/00/10/11/20/21/32/99`），不涉及状态变迁。8 项内联字典与全站一致（含 `'32'`）。

---

## 6. 与其他池模块的差异

- **vs 证券池历史（10）**：CRMW 历史页筛选项为 CRMW 代码/名称（而非证券代码/简称）；主表 `ip_adjust_log` 以 `pool_type='crmw'` 过滤；表格列含 CRMW 名称/代码。
- **vs 禁投池历史（13）**：CRMW 历史页表格中 CRMW 名称/代码/证券名称/代码**可点击跳详情**（`openCrmwAdjustDetail`，`entryMode=view`），禁投池历史页「证券名称/代码」仅样式不可点击。
- **vs CRMW 查询页（18）**：历史页主表 `ip_adjust_log`（调库流水，含所有状态）；查询页主表 `ip_pool_status_crmw`（当前状态，仅 `audit_status='20'`）。
- 状态字典一致：8 项内联（含 `'32'`），与禁投池历史页一致。

---

## 7. 验收标准

- 仅返回 `pool_type='crmw' AND is_deleted=0` 的调库记录，含所有状态。
- 调整时间范围筛选边界含当日 00:00:00 ~ 23:59:59。
- 投资池名称显示全路径。
- 调整类型/方向/审核状态 el-tag 配色按字段语义差异化。
- CRMW 名称/代码/证券名称/代码可点击跳详情页（`entryMode=view`）。

## 8. 关键源码索引

- 前端：`znty-rrs-ui/crmw_pool_adjust_history.html`（`loadList`、`auditStatusLabel`/`auditStatusType`、`adjustTypeTagType`、`openCrmwAdjustDetail`、`refreshTableLayout`）
- Controller：`CrmwPoolAdjustHistoryController.java`（`@RequestMapping("/api/v1/crmwPoolAdjustHistory")`）
- Service：`CrmwPoolAdjustHistoryService.java`（`queryCrmwPoolAdjustHistoryPage`、`fillPoolFullName`）
- Mapper：`CrmwPoolAdjustHistoryMapper.java` / `resources/mapper/CrmwPoolAdjustHistoryMapper.xml`
- 实体：`entity/crmwpoolhistory/CrmwPoolAdjustHistoryReq.java`、`CrmwPoolAdjustHistoryDto.java`
- SQL：`sql/rrs_security_pool_adjust_schema.sql`（`ip_adjust_log`）
