# 证券池调整历史需求说明

> 前端页面：`security_pool_adjust_history.html`
> 后端前缀：`/api/v1/securityPoolAdjustHistory`、`/api/v1/common`
> 角色定位：风控、合规和审计人员查询证券调入、调出及审批结果，按时间和业务维度追溯历史操作。

---

## 1. 页面概览与初始化

结构与证券池查询页一致，根容器 `#security_pool_adjust_history`，标题「证券池调整历史」。`mounted` 设置 baseURL 后调用 `loadPoolTree()`、`loadSecurityTypeOptions()`、`loadList()`。

---

## 2. 筛选项

`searchForm` 默认值：

| 字段 | 默认值 | 控件 | 说明 |
|---|---|---|---|
| `poolIds` | `[]` | 投资池树多选 | 同查询页 |
| `securityCode` / `securityShortName` | `''` | 文本输入 | 回车触发查询 |
| `securityType` | `''` | 下拉 | 动态加载 |
| `adjustTimeRange` | `null` | 日期范围 | 调整时间（提交时间） |
| `adjusterName` / `issuer` | `''` | 文本输入 | |
| `adjustMode` | `''` | 下拉 | 调入/调出 |
| `auditStatus` | `''` | 下拉 | 8 种审核状态 |
| `myBonds` | `false` | 复选框 | 我的调整，change 即查询 |
| `currentUserId` | `'1'` | — | TODO |

`adjustModeOptions`：`[{调入}, {调出}]`。

`auditStatusOptions`（8 个状态码，与 dict.js `DICT_AUDIT_STATUS` 一致）：
- `-1` 无效调整 / `00` 流程中（待审批/审批中） / `11` 驳回待修改 / `20` 审批通过 / `21` 审批驳回 / `32` O32自动审批 / `99` 发起人已撤回

**初始化接口**：
- 投资池树：`POST /api/v1/common/queryPoolTreeList`（同前）
- 证券类型：`POST /api/v1/securityPoolAdjustHistory/querySecurityTypeList`（注意路径前缀是 `securityPoolAdjustHistory`），后端 SQL：`SELECT DISTINCT al.security_type, dst.security_type_name FROM ip_adjust_log al ... WHERE al.is_deleted=0 AND al.security_type IS NOT NULL ORDER BY dst.sort_order ASC, al.security_type ASC`（**不限定 audit_status**，包含所有历史记录的证券类型）

---

## 3. 查询接口

`loadList` → `apiPost('/api/v1/securityPoolAdjustHistory/querySecurityPoolAdjustHistoryPage', params)`，请求体字段：

| 字段 | 来源 | 空值处理 |
|---|---|---|
| `poolIds` | 树多选 | 空数组转 null |
| `securityCode` / `securityShortName` / `securityType` | 表单 | 空串转 null |
| `adjustTimeStart` / `adjustTimeEnd` | `adjustTimeRange[0/1]` | 直接取日期串（无时分秒补全，后端对 End 拼 ` 23:59:59`） |
| `adjusterName` / `issuer` | 表单 | 空串转 null |
| `adjustMode` / `auditStatus` | 下拉 | 空串转 null |
| `myBonds` | 复选框 | false 转 null |
| `currentUserId` | 固定 `'1'` | 必传 |
| `pageIndex` / `pageSize` | 分页 | — |

返回 `PageResult<SecurityPoolAdjustHistoryDto>`，取 `records` / `total`。

---

## 4. 表格列与渲染

| 列 | prop/字段 | 渲染逻辑 |
|---|---|---|
| 序号 | `$index` | 同前 |
| 调整人 | `adjusterName` | 空则空 |
| 提交日期 | `submitTime` | `moment(submitTime).format('YYYY-MM-DD HH:mm')`，空则空 |
| 证券简称 | `securityShortName` | `desc-link` 点击跳转详情 |
| 证券代码 | `securityCode` | `desc-link` 点击跳转 |
| 证券类型 | `securityTypeName` | `el-tag type="info"`，空则空 |
| 发行主体名称 | `issuer` | tooltip |
| 调整类型 | `adjustType` | 直接显示（手工调整/联动调整/互斥调整/关联调整/Excel导入/手动批量调整） |
| 调整方向 | `adjustMode` | `调入`→绿色 `el-tag--success`；`调出`→红色 `el-tag--danger`；其他空 |
| 调整原因 | `adjustReason` | tooltip |
| 投资池名称 | `targetPoolPath` | tooltip，后端填充全路径 |
| 审核状态 | `auditStatus` | `el-tag` + `auditStatusLabel`/`auditStatusType` 映射 |

`auditStatusLabel` 与 `auditStatusType` 为前端内联字典，与 dict.js `DICT_AUDIT_STATUS` / `DICT_AUDIT_STATUS_TAG_TYPE` 一致（本页未引入 dict.js，内联实现）。Tag 类型映射：`20/10/32`→success，`-1/21`→danger，`11/00`→warning，`99`→info。

**主从记录/批次号**：后端按 `submit_time DESC, adjust_batch_no DESC, id DESC` 排序——**提交时间优先**保证最新记录在前；同秒内再按批次号倒序，使同一批次的手工/联动/互斥行聚拢。前端表格不显式合并或展示批次号列（批次号仅通过 `openSecurityAdjustDetail` 跳转参数传递）。

> **提交时间必须同批一致**：`addAdjustLog` 写入 `submit_time` 时使用本次请求共享的 `BatchNoContext.submitTime`（一次 `new Date()`），禁止对每条记录单独 `NOW()`。否则 1000+ 条逐条插入会差毫秒/秒，仅靠批次号第二键仍无法保证同组在「时间第一键」下相邻。

> 历史曾用 `adjust_batch_no` 作第一排序键。批次号为 `BOND`/`AUTO` + 时间戳 + 序号的字符串，前缀字典序会让 `BOND…` 整体排在 `AUTO…` 之前，较新的自动调库可能排在较旧的手工调库后面。现已改为时间优先，与禁投/主体/CRMW 历史口径一致。

---

## 5. 跳转调库详情

`openSecurityAdjustDetail`：
```js
const params = new URLSearchParams();
if (row.securityCode || row.windCode) params.set('securityCode', row.securityCode || row.windCode);
if (row.targetPoolId) params.set('targetPoolId', row.targetPoolId);
if (row.adjustBatchNo) params.set('adjustBatchNo', row.adjustBatchNo);
params.set('entryMode', 'view');
window.location.href = 'security_pool_adjust_detail.html?' + params.toString();
```
目标页 `security_pool_adjust_detail.html` 通过 `getUrlParam` 读取 `securityCode`/`targetPoolId`/`adjustLogId`/`adjustBatchNo`/`entryMode`，`entryMode !== 'adjust'` 时为只读详情模式。

分页参数同证券池查询页（pageIndex=1, pageSize=20, page-sizes=[10,20,50,100]）。

---

## 6. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `common/queryPoolTreeList` | `{}` | `List<{id, parentId, poolName, poolFullName}>` | 投资池树 |
| `securityPoolAdjustHistory/querySecurityPoolAdjustHistoryPage` | poolIds, securityCode, securityShortName, securityType, adjustTimeStart, adjustTimeEnd, adjusterName, issuer, adjustMode, auditStatus, myBonds, currentUserId, pageIndex, pageSize | `PageResult<SecurityPoolAdjustHistoryDto>`（含 targetPoolPath, adjustBatchNo, auditStatus 等） | 调库历史分页（含所有状态记录） |
| `securityPoolAdjustHistory/querySecurityTypeList` | `{}` | `List<{securityType, securityTypeName}>` | 证券类型下拉（不限 audit_status） |

> 路径均带前缀 `/api/v1/`。

---

## 7. 关键数据库表与查询逻辑

### 7.1 涉及的表

| 表名 | 用途 | 关键字段 |
|---|---|---|
| `ip_adjust_log` | 证券池调库记录（主表） | id, security_code, security_short_name, security_type, adjust_type, adjust_mode, adjust_batch_no, target_pool_id, target_pool_name, pool_type, flow_id, audit_status, adjuster_id, adjuster_name, adjust_reason, submit_time, entry_time, is_deleted |
| `ip_investment_pool` | 投资池定义（树/全路径/名称） | id, parent_id, pool_name, pool_type, outer_sort, inner_sort, status, is_deleted |
| `rrs_securityinfo` | 证券基础信息 | wind_code, issuer, full_name, maturity_date |
| `dict_security_type` | 证券类型字典 | security_type, security_type_name, sort_order, is_deleted |

### 7.2 可见数据范围控制

- **调库历史**：仅 `al.is_deleted=0`，**包含所有审核状态**（含 -1/00/11/21/99 等未通过/驳回/撤回）的历史记录，用于审计追溯。这是与证券池查询（仅 audit_status='20'）的本质区别。

### 7.3 后端查询逻辑要点（`SecurityPoolAdjustHistoryMapper.xml`）

- **主表**：`ip_adjust_log al`
- **JOIN**：
  - `LEFT JOIN ip_investment_pool p ON p.id=al.target_pool_id AND p.is_deleted=0`（带删除过滤，与证券池查询不同）
  - `LEFT JOIN rrs_securityinfo sb ON sb.wind_code=al.security_code`
  - `LEFT JOIN dict_security_type dst ON dst.security_type=al.security_type AND dst.is_deleted=0`
- **WHERE**：仅 `al.is_deleted=0`（不限 audit_status，含全量历史）；本接口不按证券状态筛选（无 securityStatus 参数）
  - `adjustTimeEnd` 后端补 `CONCAT(#{adjustTimeEnd}, ' 23:59:59')`，start 直接用日期串
  - `myBonds==true` → `al.adjuster_id = #{currentUserId}`
- **SELECT**：`p.pool_name AS target_pool_path`（先取叶子名）
- **排序**：`submit_time DESC, adjust_batch_no DESC, id DESC`（时间优先最新在前，批次号次之用于同组聚拢）
- **Service 后处理**：`fillPoolFullName` 用全路径映射覆盖 `targetPoolPath`

---

## 8. 关键校验

- 时间区间起始时间不得晚于结束时间。
- 主记录和联动、互斥、关联从属记录应通过同一批次关联。
- 状态 code 保持原始值，前端负责显示中文。
- 历史查询不修改任何业务数据。

## 9. 验收标准

- 组合筛选、分页和倒序排序正确。
- 可通过批次和日志 ID 定位完整流程步骤。
- `SecurityPoolAdjustHistoryApiTest` 覆盖页面全部接口。

## 10. 关键源码索引

- 前端：`znty-rrs-ui/security_pool_adjust_history.html`、`znty-rrs-ui/dict.js`
- Controller：`SecurityPoolAdjustHistoryController.java`、`CommonController.java`
- Service：`SecurityPoolAdjustHistoryService.java`、`InvestmentPoolService.java`
- Mapper：`SecurityPoolAdjustHistoryMapper.xml`、`CommonMapper.xml`
- 实体：`SecurityPoolAdjustHistoryDto`、`SecurityPoolAdjustHistoryReq`
- SQL：`sql/rrs_external_import_schema.sql`（`rrs_securityinfo`）、`sql/rrs_security_pool_adjust_schema.sql`（ip_adjust_log）
