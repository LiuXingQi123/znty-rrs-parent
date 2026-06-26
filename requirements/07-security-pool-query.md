# 证券池查询需求说明

> 前端页面：`security_pool_query.html`
> 后端前缀：`/api/v1/securityPoolQuery`、`/api/v1/common`
> 角色定位：投资和风控人员按投资池、证券类型及状态查询当前有效证券池，并维护个人关注的「我的证券池」收藏。

---

## 1. 页面概览与初始化

Vue 2 + Element UI 单页结构，根容器 `#security_pool_query`，纵向弹性布局：顶栏（图标 + 标题「证券池查询」+ 系统名 + 当前日期徽章）+ list-body（`search-panel` 筛选区 + `table-card` 表格与分页）。

`mounted` 设置后端地址并依次触发三个初始化调用：
```js
axios.defaults.baseURL = 'http://localhost:18090';
this.loadPoolTree();              // 投资池树
this.loadSecurityTypeOptions();   // 证券类型下拉
this.loadList();                  // 列表数据
```

统一请求封装 `apiPost`：`!json.success` 报错并抛异常，成功返回 `json.data`。后端统一响应 `ApiResponse<T> = {success, message, data}`，`success=true` 为成功。

---

## 2. 筛选项与初始化接口

### 2.1 `searchForm` 默认值

| 字段 | 默认值 | 控件 | 说明 |
|---|---|---|---|
| `poolIds` | `[]` | 投资池树多选弹层 | 叶子节点可选，父节点禁止勾选 |
| `securityCode` | `''` | 文本输入 | 证券代码，回车触发查询 |
| `securityShortName` | `''` | 文本输入 | 证券简称 |
| `securityType` | `''` | 下拉 | 动态加载 |
| `securityStatus` | `'active'` | 下拉 | 默认存续 |
| `entryTimeRange` | `null` | 日期范围 | 入池时间 |
| `adjusterName` | `''` | 文本输入 | 调整人 |
| `issuer` | `''` | 文本输入 | 发行主体名称 |
| `mySecurities` | `false` | 复选框 | 仅看我的证券，change 即触发查询 |
| `currentUserId` | `'1001'` | — | TODO：对接实际登录用户 |

### 2.2 初始化接口 1 — 投资池树

- 路径：`POST /api/v1/common/queryPoolTreeList`，请求体 `{}`
- 返回 `PoolTreeDto` 列表（`{id, parentId, poolName, poolFullName}`）
- 后端 `CommonMapper.xml`：用 MySQL 8 递归 CTE `WITH RECURSIVE pool_tree` 拼接 `pool_full_name`（父路径 + '/' + 子名），按 `sort_path`（outer_sort/inner_sort 拼接）排序
- 前端 `buildPoolTree` 将扁平列表转为嵌套树，并为每个节点挂 `fullPath`；`treeProps.disabled` 使父节点（有 children）不可勾选
- 确认时 `handlePoolTreeConfirm` 取 `getCheckedKeys()` + `getHalfCheckedKeys()` 合并，构建标签行

### 2.3 初始化接口 2 — 证券类型下拉

- 路径：`POST /api/v1/securityPoolQuery/querySecurityTypeList`，请求体 `{}`
- 返回 `List<SecurityTypeOptionDto>`（`{securityType, securityTypeName}`）
- 后端 SQL：`SELECT DISTINCT ips.security_type, dst.security_type_name FROM ip_pool_status ips LEFT JOIN dict_security_type dst ... WHERE ips.is_deleted=0 AND ips.audit_status='20' AND ips.security_type IS NOT NULL ORDER BY dst.sort_order ASC, ips.security_type ASC`

### 2.4 证券状态下拉

前端硬编码 `[{active, 存续}, {matured, 到期}]`。后端 `querySecurityStatusList` 也仅返回 `['active','matured']`，但前端未调用此接口。

---

## 3. 查询接口

`loadList` → `apiPost('/api/v1/securityPoolQuery/querySecurityPoolPage', params)`，请求体字段：

| 字段 | 来源 | 空值处理 |
|---|---|---|
| `poolIds` | `searchForm.poolIds` | 空数组转 `null` |
| `securityCode` / `securityShortName` / `securityType` / `securityStatus` / `adjusterName` / `issuer` | 表单 | 空串转 `null` |
| `entryTimeStart` | `entryTimeRange[0]` + ` 00:00:00` | 无则 `null` |
| `entryTimeEnd` | `entryTimeRange[1]` + ` 23:59:59` | 无则 `null` |
| `mySecurities` | 复选框 | `false` 转 `null` |
| `currentUserId` | 固定 `'1001'` | 必传 |
| `pageIndex` / `pageSize` | 分页 | — |

返回 `PageResult<SecurityPoolQueryDto>`（`{records, total, pageIndex, pageSize}`）。前端取 `data.records` 填表、`data.total` 填分页。

---

## 4. 表格列定义与渲染

表格 `el-table` 带 `stripe border size="small"`：

| 列 | prop/字段 | 渲染逻辑 |
|---|---|---|
| 序号 | `$index` | `(pageIndex-1)*pageSize + $index + 1` |
| 证券简称 | `securityShortName` | `desc-link` 可点击跳转详情，空值显示空 |
| 证券代码 | `securityCode` | 同上，点击跳转 |
| 调整人 | `adjusterName` | 直接显示 |
| 入池时间 | `entryTime` | `moment(entryTime).format('YYYY-MM-DD HH:mm')`，空则空 |
| 投资池名称 | `targetPoolName` | tooltip，由后端 `fillPoolFullName` 填充全路径 |
| 证券类型 | `securityTypeName` | 有值显示 `el-tag type="info"`，无值显示空 |
| 票面年利率(%) | `couponRate` | 右对齐，空则空 |
| 发行主体名称 | `issuer` | tooltip |
| 证券全称 | `fullName` | tooltip |
| 发行日期 | `issueDate` | 居中 |
| 起息日 | `carryDate` | 居中 |
| 到期日 | `maturityDate` | 居中 |
| 证券状态 | `securityStatus` | `active`→绿色「存续」；`matured`→琥珀「到期」；其他空 |
| 退市日期 | `delistDate` | 居中 |
| 行权日期（回售） | `repurchaseDate` | 居中 |
| 操作 | — | `fixed="right"`，收藏按钮 |

**当前有效池展示逻辑**：后端 SQL 固定 `WHERE ips.is_deleted=0 AND ips.audit_status='20'`，即只返回审批通过（20）的池状态记录，未审批/驳回数据不会混入。证券状态由后端 `CASE WHEN bi.maturity_date >= CURDATE() THEN 'active' ELSE 'matured'` 计算。

---

## 5. 「我的证券池」收藏逻辑

**关键点：前端并不调用 `queryFavoritedCodeList`**。收藏状态完全依赖列表查询时 LEFT JOIN `my_security_pool` 返回的 `mySecurityPoolId` 字段（null=未收藏，非 null=已收藏）。

判断已收藏：`isFavorited(row) { return row.mySecurityPoolId != null; }`

**切换收藏 `toggleFavorite`**：
- **已收藏 → 取消**：先 `$confirm('该证券已在我的证券池列表中，确认移除？')`，确认后调 `POST /api/v1/securityPoolQuery/deleteSecurityFromMyPool`，请求体 `{securityCode, userId}`；成功后前端乐观更新 `row.mySecurityPoolId = null`，提示「已从我的证券池移除」。
- **未收藏 → 添加**：调 `POST /api/v1/securityPoolQuery/addSecurityToMyPool`，请求体 `{securityCode, securityType: row.securityType || '证券', market: '', userId}`；成功后乐观更新 `row.mySecurityPoolId = 1`，提示「已添加到我的证券池」。

**后端实现**：
- `addSecurityToMyPool` 幂等：先 `queryByUserAndCode(userId, securityCode)` 查是否已收藏，存在则直接返回已有记录不重复插入；否则构建 `MySecurityPoolBo` 插入（`status='use'`）。
- `deleteSecurityFromMyPool` 软删除：先查询确认存在，存在则 `UPDATE my_security_pool SET status='del' WHERE user_id AND security_code AND status='use'`，不存在静默返回 null。
- `queryFavoritedCodeList`（Controller 暴露但本页未用）：`SELECT security_code FROM my_security_pool WHERE user_id AND status='use'`。

**列表查询时的收藏状态渲染**：通过 LEFT JOIN `my_security_pool`（带 `user_id=#{currentUserId} AND status='use'`）取 `mbp.id AS mySecurityPoolId`，null 即未收藏，实现查询与收藏态一次性返回，无需额外调 `queryFavoritedCodeList`。

---

## 6. 分页参数

`pagination: { pageIndex:1, pageSize:20, total:0 }`，`page-sizes=[10,20,50,100]`，layout `total, sizes, prev, pager, next, jumper`。后端 `PageRequest` 对 pageSize 硬限制：`Math.min(pageSize, 100)`。查询/翻页/切条数均重置或保留 pageIndex 后调 `loadList`。

---

## 7. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `common/queryPoolTreeList` | `{}` | `List<{id, parentId, poolName, poolFullName}>` | 投资池树（含全路径） |
| `securityPoolQuery/querySecurityPoolPage` | poolIds, securityCode, securityShortName, securityType, securityStatus, entryTimeStart, entryTimeEnd, adjusterName, issuer, mySecurities, currentUserId, pageIndex, pageSize | `PageResult<SecurityPoolQueryDto>`（records 含 mySecurityPoolId 用于收藏态） | 证券池分页查询（仅 audit_status='20'） |
| `securityPoolQuery/querySecurityTypeList` | `{}` | `List<{securityType, securityTypeName}>` | 证券类型下拉（限池内已审批） |
| `securityPoolQuery/querySecurityStatusList` | `{}` | `List<String>` = `['active','matured']` | 证券状态下拉（前端未调用，硬编码） |
| `securityPoolQuery/addSecurityToMyPool` | `{securityCode, securityType, market, userId}` | `MySecurityPoolBo` | 添加收藏（幂等） |
| `securityPoolQuery/deleteSecurityFromMyPool` | `{securityCode, userId}` | `MySecurityPoolBo`（不存在返回 null） | 取消收藏（软删除 status='del'） |
| `securityPoolQuery/queryFavoritedCodeList` | `{userId}` | `List<String>` | 批量查已收藏代码（Controller 暴露，前端未用） |

> 路径均带前缀 `/api/v1/`。

---

## 8. 关键数据库表与查询逻辑

### 8.1 涉及的表

| 表名 | 用途 | 关键字段 |
|---|---|---|
| `ip_pool_status` | 投资池当前状态（主表） | id, security_code, security_short_name, security_type, target_pool_id, target_pool_name, adjust_batch_no, audit_status, adjuster_name, entry_time, is_deleted |
| `ip_investment_pool` | 投资池定义（树/全路径/名称） | id, parent_id, pool_code, pool_name, pool_type, pool_level, outer_sort, inner_sort, status, is_deleted |
| `sirm_securityinfo` | 证券基础信息（利率/发行主体/到期日等） | wind_code, issuer, full_name, coupon_rate, firstissue_date, carry_date, maturity_date, delist_date, repurchase_date |
| `dict_security_type` | 证券类型字典 | security_type, security_type_name, category_type, sort_order, is_deleted |
| `my_security_pool` | 用户自选证券（收藏） | id, security_code, security_type, market, user_id, status(use/del), create_time, update_time |

### 8.2 可见数据范围控制

- **证券池查询**：硬性 `ips.is_deleted=0 AND ips.audit_status='20'`，只返回审批通过的当前有效池，未审批/驳回/撤回的调库记录不会进入 `ip_pool_status`，不会混入查询结果。
- **收藏用户隔离**：`my_security_pool` 所有查询均带 `user_id = #{currentUserId} AND status = 'use'`，实现用户级隔离。

### 8.3 后端查询逻辑要点（`SecurityPoolQueryMapper.xml`）

- **主表**：`ip_pool_status ips`
- **JOIN**：
  - `LEFT JOIN ip_investment_pool p ON ips.target_pool_id = p.id`（取池名称，注意此 JOIN 未带 `p.is_deleted=0`）
  - `LEFT JOIN sirm_securityinfo bi ON ips.security_code = bi.wind_code`（取证券基础信息）
  - `LEFT JOIN dict_security_type dst ON dst.security_type = ips.security_type AND dst.is_deleted=0`（取证券类型名称）
  - `LEFT JOIN my_security_pool mbp ON ips.security_code = mbp.security_code AND mbp.user_id=#{currentUserId} AND mbp.status='use'`（取收藏 ID，用户隔离）
- **WHERE 条件构造**：固定 `ips.is_deleted=0 AND ips.audit_status='20'`；动态条件用 `<if>`：
  - `securityStatus='active'` → `bi.maturity_date >= CURDATE()`；`'matured'` → `bi.maturity_date < CURDATE()`
  - `poolIds` → `ips.target_pool_id IN (...)` foreach
  - `securityCode`/`securityShortName`/`adjusterName` → `LIKE CONCAT('%', #{x}, '%')`
  - `securityType` → `=`
  - `entryTimeStart`/`entryTimeEnd` → `>=` / `<=`
  - `issuer` → `bi.issuer LIKE`
  - `mySecurities==true` → `mbp.id IS NOT NULL`
- **SELECT 计算列**：证券状态 `CASE WHEN maturity_date IS NULL OR '' THEN NULL WHEN >= CURDATE() THEN 'active' ELSE 'matured' END AS securityStatus`；`mySecurityPoolId` 直接取 `mbp.id`
- **排序**：`entry_time DESC, id DESC`
- **Service 后处理**：`fillPoolFullName` 用 `investmentPoolService.queryPoolFullNameMap()`（递归 CTE 全路径映射）覆盖 `targetPoolName` 为全路径

### 8.4 收藏去重与用户隔离

- **添加幂等**：先 `queryByUserAndCode`（`WHERE user_id AND security_code AND status='use' LIMIT 1`），已存在则直接返回，不重复 INSERT。
- **删除软删**：`UPDATE ... SET status='del' WHERE user_id AND security_code AND status='use'`，仅作用 use 状态记录。
- **用户隔离**：所有 my_security_pool 操作均以 `user_id` 为过滤条件，不同用户收藏互不可见。

---

## 9. 关键校验

- 查询只返回当前有效池状态（`audit_status='20'`），不混入未审批或已调出数据。
- 收藏证券和用户必须存在，同一用户不可重复收藏同一证券（幂等）。
- 删除收藏仅影响当前用户，不影响其他用户。

## 10. 验收标准

- 多条件筛选与分页总数一致。
- 收藏操作后列表状态即时同步（乐观更新 `mySecurityPoolId`）。
- `SecurityPoolQueryApiTest` 覆盖查询、选项和收藏业务线。

## 11. 关键源码索引

- 前端：`znty-sirm-ui/security_pool_query.html`、`znty-sirm-ui/dict.js`
- Controller：`SecurityPoolQueryController.java`、`CommonController.java`
- Service：`SecurityPoolQueryService.java`、`InvestmentPoolService.java`（`queryPoolFullNameMap`）
- Mapper：`SecurityPoolQueryMapper.xml`、`CommonMapper.xml`、`MySecurityPoolMapper.xml`、`InvestmentPoolMapper.xml`
- 实体：`SecurityPoolQueryDto`、`SecurityPoolQueryReq`、`SecurityTypeOptionDto`、`PoolTreeDto`、`MySecurityPoolBo`
- SQL：`sql/sirm_security_pool_adjust_schema.sql`（ip_pool_status）、`sql/sirm_my_security_pool_schema.sql`、`sql/sirm_dict_schema.sql`
