# 禁投池历史需求说明

> 前端页面：`forbidden_pool_history.html`
> 后端前缀：`/api/v1/forbiddenPoolHistory`
> 角色定位：风控、合规和审计人员查询针对禁投池、观察池和黑名单质押库的全部调库流水（含调入/调出/驳回/撤回），追溯主体风险池调整历史。

---

## 0. 关键现状（数据来源）

**风险池历史不是独立表**，而是复用 `ip_adjust_log`（证券池调库记录表），通过 `INNER JOIN ip_investment_pool p ON p.pool_type IN ('forbidden','observe','blacklist')` 过滤出三类风险池的调库流水。

- 禁投池查询（[08](08-forbidden-pool-query.md)）→ 读 `ip_pool_status`（当前在禁投池中的证券快照）
- 禁投池历史（本文）→ 读 `ip_adjust_log`（针对禁投池的全部调库流水，含所有状态）

---

## 1. 页面概览与初始化

根容器 `#forbidden_pool_history`。布局：顶栏（时钟/刷新图标 +「禁投池历史查询」+ 系统名 + 当天日期）+ 列表区（搜索面板 + 表格卡片含分页）。

**初始化**（`mounted`）：
1. `axios.defaults.baseURL = 'http://localhost:18090'`
2. `loadList()` — **不加载任何下拉**（无证券类型/无动态选项，所有下拉均为页面内联静态字典）。

初始分页 `pageIndex=1, pageSize=20`。

> 注：该页面**未引入 `dict.js`**，审核状态与调整方向字典内联。

---

## 2. 筛选项与查询逻辑

### 2.1 筛选项

| 控件 | v-model | 匹配方式 | 说明 |
|---|---|---|---|
| 文本输入 | `companyCode` | 模糊 | 主体代码，回车查询 |
| 文本输入 | `companyName` | 模糊 | 主体名称，回车查询 |
| 文本输入 | `securityCode` | 模糊 | 证券代码，回车查询 |
| 文本输入 | `securityShortName` | 模糊 | 证券名称，回车查询 |
| 日期范围 | `adjustTimeRange` | 范围 | 调整开始/结束，`value-format="yyyy-MM-dd"` |
| 文本输入 | `adjusterName` | 模糊 | 调整人，回车查询 |
| 下拉 | `adjustMode` | 精确 | 调整方向：调入/调出 |
| 下拉 | `auditStatus` | 精确 | 调整状态（7 项内联字典，含 `'32'` O32自动审批） |
| 查询 / 重置 | — | — | 查询重置页码为 1；重置清空全部条件 |

**内联字典**：
- `adjustModeOptions`：`[{调入},{调出}]`
- `auditStatusOptions`（7 项）：`-1/00/11/20/21/32/99`

### 2.2 查询接口

- 路径：`POST /api/v1/forbiddenPoolHistory/queryForbiddenPoolHistoryPage`
- 请求体：
  ```json
  { "companyCode", "companyName", "securityCode", "securityShortName",
    "adjustTimeStart": range ? range[0] : null, "adjustTimeEnd": range ? range[1] : null,
    "adjusterName", "adjustMode", "auditStatus",
    "pageIndex", "pageSize" }
  ```
- 后端 `ForbiddenPoolHistoryService`：`PageHelper.startPage` 分页；`queryForbiddenPoolHistoryPage` SQL；`fillPoolFullName`（用投资池全路径名覆盖 `targetPoolName`）；返回 `PageResult`。
- 返回 `PageResult<ForbiddenPoolHistoryDto>`（`id, adjusterName, submitTime, securityShortName, securityCode, issuer, adjustType, adjustMode, targetPoolName, targetPoolId, auditStatus`）。

### 2.3 表格列渲染

| 列 | prop/渲染 | 说明 |
|---|---|---|
| 序号 | 计算 | 居中 |
| 调整人 | `adjusterName` | |
| 提交时间 | `formatDate(row.submitTime)` → `YYYY-MM-DD HH:mm` | 居中 |
| 证券名称 | `securityShortName`，`desc-link` 样式 | **仅样式，无 `@click`**，不可点击跳转 |
| 证券代码 | `securityCode`，`desc-link` 样式 | **仅样式，无 `@click`**，不可点击跳转 |
| 发行主体 | `issuer` | tooltip |
| 调整类型 | `adjustType` | 居中（如「手工调整」） |
| 调整方向 | `adjustMode` | 居中；调入=`el-tag success`、调出=`el-tag danger` |
| 投资池名称 | `targetPoolName` | tooltip，显示投资池全路径名 |
| 审批状态 | `auditStatusLabel(row.auditStatus)` + `auditStatusType(...)` | `el-tag` 渲染 |

**审批状态映射**（页面内联）：
- `auditStatusLabel`：`-1`无效调整 / `00`流程中（待审批/审批中） / `11`驳回待修改 / `20`审批通过 / `21`审批驳回 / `32`O32自动审批 / `99`发起人已撤回。
- `auditStatusType`（el-tag type）：`20/10/32`→success；`21/-1`→danger；`00/11`→warning；`99`→info。

### 2.4 分页

`page-sizes=[10,20,50,100]`，layout `total, sizes, prev, pager, next, jumper`。后端 `PageRequest` pageSize 硬上限 100。

---

## 3. 跳转详情

表格中「证券名称」「证券代码」可跳转详情。`openPoolAdjustDetail(row)` 根据 `categoryType` 跳转主体详情或证券详情，并携带 `companyCode`/`securityCode`、`targetPoolId`、`adjustBatchNo`、`entryMode=view`；批次号用于加载同批次流程步骤。

---

## 4. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `forbiddenPoolHistory/queryForbiddenPoolHistoryPage` | companyCode, companyName, securityCode, securityShortName, adjustTimeStart, adjustTimeEnd, adjusterName, adjustMode, auditStatus, pageIndex, pageSize | `PageResult<ForbiddenPoolHistoryDto>` | 禁投池调库历史分页查询 |

> 路径带前缀 `/api/v1/`。该 Controller **只有一个接口**（无下拉选项接口）。

---

## 5. 关键数据库表与查询逻辑

涉及表：
- `ip_adjust_log`（证券池调库记录表）：每次调库申请的流水。
- `ip_investment_pool`（投资池主表）：过滤 `pool_type='forbidden'`。
- `rrs_securityinfo`（证券信息表）：关联 `wind_code = al.security_code`，提供 `issuer/issuer_code`。
- **未关联** `dict_security_type`（历史页 DTO 不返回证券类型，表格也无证券类型列）。

**主查询 SQL**（`ForbiddenPoolHistoryMapper.xml`）：
```sql
SELECT al.id, al.adjuster_name, al.submit_time, al.security_short_name,
       al.security_code, al.adjust_batch_no,
       COALESCE(wci.s_info_compname, bi.issuer, al.security_short_name) AS issuer, al.adjust_type, al.adjust_mode,
       al.target_pool_id, p.pool_name AS target_pool_name, al.audit_status
FROM ip_adjust_log al
INNER JOIN ip_investment_pool p ON p.id = al.target_pool_id
                                 AND p.pool_type IN ('forbidden', 'observe', 'blacklist')
LEFT JOIN rrs_securityinfo bi ON bi.wind_code = al.security_code
LEFT JOIN ais_inv_ods.wind_cbondissuer wci ON wci.s_info_compcode = al.security_code
                                           AND wci.used = 1
                                           AND al.security_type = 'company'
<where>
    al.is_deleted = 0
    <if companyCode>   AND (wci.s_info_compcode LIKE CONCAT('%', #{companyCode}, '%') OR bi.issuer_code LIKE CONCAT('%', #{companyCode}, '%')) </if>
    <if companyName>   AND COALESCE(wci.s_info_compname, bi.issuer) LIKE CONCAT('%', #{companyName}, '%') </if>
    <if securityCode>  AND al.security_code LIKE CONCAT('%', #{securityCode}, '%') </if>
    <if securityShortName> AND al.security_short_name LIKE CONCAT('%', #{securityShortName}, '%') </if>
    <if adjustTimeStart> AND al.submit_time >= #{adjustTimeStart} </if>
    <if adjustTimeEnd>   AND al.submit_time <= CONCAT(#{adjustTimeEnd}, ' 23:59:59') </if>
    <if adjusterName>  AND al.adjuster_name LIKE CONCAT('%', #{adjusterName}, '%') </if>
    <if adjustMode>    AND al.adjust_mode = #{adjustMode} </if>
    <if auditStatus>   AND al.audit_status = #{auditStatus} </if>
</where>
ORDER BY al.submit_time DESC, al.id DESC
```

特点：
- 主体级流水通过 Wind 的 `s_info_compcode`/`s_info_compname` 匹配；债券流水继续通过 `rrs_securityinfo` 的 `issuer_code`/`issuer` 反查。
- 返回 `adjustBatchNo`，历史页跳转详情时携带该批次号以加载同批次流程步骤。
- 历史筛选日期语义为提交日期（`al.submit_time`）；页面显示“提交开始/提交结束”。
- 投资池已删除时仍保留对应历史流水。
- 排序按 `submit_time DESC, id DESC`。
- `target_pool_name` 同样被 Service 覆盖为全路径名。
- **无可见数据范围/权限校验**。

---

## 6. 与禁投池查询的差异对比

| 维度 | 禁投池查询（08） | 禁投池历史（本文） |
|---|---|---|
| 主表 | `ip_pool_status`（当前状态表，快照） | `ip_adjust_log`（调库流水表） |
| 含义 | 此刻仍在禁投池中的证券 | 所有针对禁投池的调整动作（含已调出/驳回/撤回） |
| 风险池过滤 | `INNER JOIN ip_investment_pool p ON p.pool_type IN ('forbidden','observe','blacklist')` | 同左 |
| 筛选项 | 证券代码/简称/类型/状态/调整日期/调整人/发行主体/调整状态 | 主体代码/主体名称/证券代码/证券名称/调整日期/调整人/调整方向/调整状态 |
| 表格列 | 含证券类型/证券状态/退市日期/行权日期 | 含调整类型/调整方向/审批状态 |
| 证券名称/代码 | 可点击跳详情 | 可点击跳详情，主体记录跳主体详情 |
| 接口数 | 2（分页 + 类型下拉） | 1（仅分页） |

两页**共享** `ip_investment_pool`（用 `pool_type='forbidden'` 过滤）与 `rrs_securityinfo`，但主数据表不同：查询页看「当前状态」，历史页看「调整流水」。

---

## 7. 字典一致性缺陷（编写/改造时需注意）

- **审核状态字典不一致**：禁投池查询页 7 项（缺 `'32'`），禁投池历史页 8 项（含 `'32'`），`dict.js DICT_AUDIT_STATUS` 含 `'32'`。三处口径不统一，且两页都**未引入 `dict.js`**，各自内联维护，已与全局字典脱节。
- 历史页「证券名称/代码」具备 `desc-link` 可点击外观但无跳转，需明确是否应跳详情（与查询页对齐）。
- 两页均无数据权限过滤，需确认是否需要按 `ip_pool_permission`（viewable）做可见范围控制。
- `targetPoolName` 实际显示投资池全路径名而非 `ip_investment_pool.pool_name`。

## 8. 验收标准

- 组合筛选（主体/证券/方向/状态/时间）与分页正确，只返回禁投池的调库流水。
- 审批状态字典映射正确（8 项）。
- `ForbiddenPoolHistoryApiTest`（如新增）覆盖页面接口。

## 9. 关键源码索引

- 前端：`znty-rrs-ui/forbidden_pool_history.html`（`loadList`、`auditStatusLabel`/`auditStatusType`、`formatDate`）
- Controller：`ForbiddenPoolHistoryController.java`
- Service：`ForbiddenPoolHistoryService.java`（`queryForbiddenPoolHistoryPage`、`fillPoolFullName`）
- Mapper：`ForbiddenPoolHistoryMapper.xml`
- 实体：`ForbiddenPoolHistoryReq`、`ForbiddenPoolHistoryDto`
- SQL：`sql/rrs_external_import_schema.sql`（`rrs_securityinfo`）、`sql/rrs_security_pool_adjust_schema.sql`（ip_adjust_log）、`sql/rrs_pool_init_schema.sql`
