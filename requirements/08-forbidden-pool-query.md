# 禁投池查询需求说明

> 前端页面：`forbidden_pool_query.html`
> 后端前缀：`/api/v1/forbiddenPoolQuery`
> 角色定位：投资、交易和合规人员查询当前禁投池中的证券，快速识别不可交易标的。

---

## 0. 关键现状（数据来源）

**本页查询三类主体风险池**：禁投池（15 / `forbidden`）、观察池（16 / `observe`）和黑名单质押库（17 / `blacklist`）。复用 `ip_pool_status`（投资池当前状态表），通过 `pool_type IN ('forbidden','observe','blacklist')`、`ips.is_deleted=0`、`ips.audit_status='20'` 查询当前已生效数据。主体级状态按 `ips.security_code=ais_inv_ods.wind_cbondissuer.s_info_compcode` 关联有效 Wind 主体；债券仍按 Wind 证券代码关联 `rrs_securityinfo`。

---

## 1. 页面概览与初始化

Vue 实例挂载 `#forbidden_pool_query`。布局：顶栏（闪电图标 +「禁投池查询」+ 系统名 + 当天日期）+ 列表区（搜索面板 + 表格卡片含分页）。

**初始化**（`mounted`）：
1. `axios.defaults.baseURL = 'http://localhost:18090'`
2. `loadBondTypeOptions()` — 加载证券类型下拉
3. `loadList()` — 加载禁投池证券列表

初始分页 `pageIndex=1, pageSize=20`。

> 注：该页面**未引入 `dict.js`**，审核状态字典 `auditStatusOptions`（7 项，缺 `'32'` O32自动审批）与证券状态字典 `bondStatusOptions`（存续/到期）在页面内联定义。

---

## 2. 筛选项与查询逻辑

### 2.1 筛选项

| 控件 | v-model | 匹配方式 | 说明 |
|---|---|---|---|
| 文本输入 | `securityCode` | 模糊 | 证券代码，回车查询 |
| 文本输入 | `securityShortName` | 模糊 | 证券简称，回车查询 |
| 下拉 | `securityType` | 精确 | 证券类型，来自 `querySecurityTypeList` |
| 下拉 | `securityStatus` | 精确 | 证券状态：`active`=存续 / `matured`=到期 |
| 日期范围 | `entryTimeRange` | 范围 | 入池日期起/止，`value-format="yyyy-MM-dd"` |
| 文本输入 | `adjusterName` | 模糊 | 调整人 |
| 文本输入 | `issuer` | 模糊 | 发行主体名称 |

### 2.2 查询接口

- 路径：`POST /api/v1/forbiddenPoolQuery/queryForbiddenPoolPage`
- 请求体：
  ```json
  { "securityCode", "securityShortName", "securityType",
    "securityStatus",
    "entryTimeStart": range[0] || null, "entryTimeEnd": range[1] || null,
    "adjusterName", "issuer",
    "pageIndex", "pageSize" }
  ```
- 后端 `ForbiddenPoolQueryService.queryForbiddenPoolPage`：`PageHelper.startPage` 分页；`queryForbiddenPoolPage` SQL；`fillPoolFullName`（用投资池全路径名覆盖 `targetPoolName`）；返回 `PageResult`。
- 返回 `PageResult<ForbiddenPoolQueryDto>`（`id, securityShortName, securityCode, issuer, adjusterName, securityType, securityTypeName, targetPoolName, targetPoolId, adjustBatchNo, entryTime, maturityDate, delistDate, repurchaseDate`）。

### 2.3 表格列渲染

| 列 | prop/渲染 | 说明 |
|---|---|---|
| 序号 | 计算 | 居中 |
| 证券简称 | `securityShortName`，`desc-link` 可点击 | 点击 `openSecurityAdjustDetail(row)` |
| 证券代码 | `securityCode`，`desc-link` 可点击 | 点击 `openSecurityAdjustDetail(row)` |
| 发行主体 | `issuer` | 空值空白 |
| 调整人 | `adjusterName` | |
| 证券类型 | `securityTypeName` | 有值 `el-tag type=info`，无值空 |
| 投资池名称 | `targetPoolName` | tooltip，显示投资池全路径名 |
| 入池时间 | `entryTime` | `moment(...).format('YYYY-MM-DD HH:mm')` |
| 证券状态 | `getBondStatus(row)` | 存续=绿色；到期=琥珀。**前端根据到期日实时计算**：`moment(maturityDate).isBefore(moment(),'day') ? '到期' : '存续'` |
| 退市日期 | `delistDate` | 居中 |
| 行权日期（回售） | `repurchaseDate` | 居中 |

### 2.4 跳转详情

`openSecurityAdjustDetail(row)`：拼 URL 参数 `securityCode`/`targetPoolId`/`adjustBatchNo`/`entryMode=view`，跳转 `security_pool_adjust_detail.html`（只读查看模式）。

### 2.5 证券类型下拉

- 路径：`POST /api/v1/forbiddenPoolQuery/querySecurityTypeList`，请求体 `{}`
- 返回 `List<SecurityTypeOptionDto>{securityType, securityTypeName}`
- 后端 SQL：仅从三类风险池的已生效状态中提取证券类型，供当前查询筛选。

---

## 3. 关键校验

- **只返回已生效风险池数据**：`INNER JOIN ip_investment_pool p ON p.id=ips.target_pool_id AND p.is_deleted=0 AND p.pool_type IN ('forbidden','observe','blacklist')`，并强制 `ips.is_deleted=0 AND ips.audit_status='20'`。
- **逻辑删除过滤**：`ips.is_deleted=0`。
- **分页硬上限**：`PageRequest.getPageSize()` 限制最大 100；`pageIndex` <1 兜底为 1。
- **证券状态筛选 SQL**：`securityStatus == 'active'` → `bi.maturity_date >= CURDATE()`；`'matured'` → `bi.maturity_date < CURDATE()`，与证券池查询保持一致。
- **`targetPoolName` 覆盖**：XML 查出的 `p.pool_name AS target_pool_name` 会被 Service 的 `fillPoolFullName` 用投资池全路径名覆盖。即表格「投资池名称」列实际显示全路径名（含父级）。
- **无可见数据范围/权限校验**：无按当前用户或角色过滤的逻辑，未引用 `ip_pool_permission`。任何调用方都能看到全部禁投池数据。
- 日期筛选与排序均使用 `ips.entry_time`（入池时间）。

---

## 4. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `forbiddenPoolQuery/queryForbiddenPoolPage` | securityCode, securityShortName, securityType, securityStatus, entryTimeStart, entryTimeEnd, adjusterName, issuer, pageIndex, pageSize | `PageResult<ForbiddenPoolQueryDto>` | 已生效风险池分页查询 |
| `forbiddenPoolQuery/querySecurityTypeList` | `{}` | `List<SecurityTypeOptionDto>` | 证券类型下拉（限三类风险池） |

> 路径均带前缀 `/api/v1/`。

---

## 5. 关键数据库表与查询逻辑

涉及表：
- `ip_pool_status`（投资池当前状态表）：证券当前所在池快照。
- `ip_investment_pool`（投资池主表）：禁投池记录 `pool_type='forbidden'`。
- `rrs_securityinfo`（证券信息表）：关联键 `wind_code = ips.security_code`，提供 `issuer/maturity_date/delist_date/repurchase_date`。
- `dict_security_type`（证券类型字典）。

**主查询 SQL**（`ForbiddenPoolQueryMapper.xml`）：
```sql
SELECT ips.id, ips.security_code, ips.security_short_name, ips.adjuster_name,
       ips.entry_time, ips.target_pool_id, ips.adjust_batch_no,
       p.pool_name AS target_pool_name, ips.security_type,
       dst.security_type_name, bi.issuer, bi.maturity_date, bi.delist_date, bi.repurchase_date
FROM ip_pool_status ips
INNER JOIN ip_investment_pool p ON p.id = ips.target_pool_id
                                 AND p.is_deleted = 0
                                 AND p.pool_type = 'forbidden'
LEFT JOIN rrs_securityinfo bi ON bi.wind_code = ips.security_code
LEFT JOIN dict_security_type dst ON dst.security_type = ips.security_type AND dst.is_deleted = 0
<where>
    ips.is_deleted = 0
    <if securityCode> AND ips.security_code LIKE CONCAT('%', #{securityCode}, '%') </if>
    <if securityShortName> AND ips.security_short_name LIKE CONCAT('%', #{securityShortName}, '%') </if>
    <if securityType> AND ips.security_type = #{securityType} </if>
    <if securityStatus=='存续'> AND (bi.maturity_date IS NULL OR bi.maturity_date >= CURDATE()) </if>
    <if securityStatus=='到期'> AND bi.maturity_date IS NOT NULL AND bi.maturity_date < CURDATE() </if>
    <if adjustTimeStart> AND ips.submit_time >= #{adjustTimeStart} </if>
    <if adjustTimeEnd> AND ips.submit_time <= CONCAT(#{adjustTimeEnd}, ' 23:59:59') </if>
    <if adjusterName> AND ips.adjuster_name LIKE CONCAT('%', #{adjusterName}, '%') </if>
    <if issuer> AND bi.issuer LIKE CONCAT('%', #{issuer}, '%') </if>
    <if auditStatus> AND ips.audit_status = #{auditStatus} </if>
</where>
ORDER BY ips.entry_time DESC, ips.id DESC
```

## 6. 验收标准

- 关键字、类型及分页组合查询正确，只返回禁投池数据。
- 证券状态由前端按到期日计算展示。
- `ForbiddenPoolQueryApiTest` 覆盖页面全部接口。

## 7. 关键源码索引

- 前端：`znty-rrs-ui/forbidden_pool_query.html`（`loadList`、`loadBondTypeOptions`、`getBondStatus`、`openSecurityAdjustDetail`）
- Controller：`ForbiddenPoolQueryController.java`
- Service：`ForbiddenPoolQueryService.java`（`queryForbiddenPoolPage`、`fillPoolFullName`、`querySecurityTypeList`）
- Mapper：`ForbiddenPoolQueryMapper.xml`
- 实体：`ForbiddenPoolQueryReq`、`ForbiddenPoolQueryDto`、`SecurityTypeOptionDto`
- SQL：`sql/rrs_external_import_schema.sql`（`rrs_securityinfo`）、`sql/rrs_security_pool_adjust_schema.sql`（ip_pool_status）、`sql/rrs_pool_init_schema.sql`
