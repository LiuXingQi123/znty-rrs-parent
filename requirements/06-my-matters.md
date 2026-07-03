# 我的事宜需求说明

> 前端页面：`my_matters.html`
> 后端前缀：`/api/v1/myMatters`
> 角色定位：登录用户集中查看与本人相关的待办、已办及流程事项，按流程、状态和时间筛选后进入对应业务详情（审核页 / 只读详情页）。

---

## 1. 页面概览与初始化

根容器 `#my_matters`，标题「我的事宜」。`mounted` 调用 `loadFlowOptions()` 与 `loadList()`。页面带 `el-tabs`：`待处理(pending)` / `已完成(completed)`，`activeTab` 默认 `'pending'`，切换触发 `handleTabClick` 重置页码并重新加载。

顶部统计徽章随页签变化：`共 {{total}} 条{{ activeTab==='pending' ? '待处理' : '已完成' }}事宜`。

---

## 2. 筛选项

`searchForm`：

| 字段 | 默认值 | 控件 | 说明 |
|---|---|---|---|
| `flowIds` | `[]` | 多选下拉（collapse-tags） | 流程名称 |
| `startDateRange` | `null` | 日期范围 | 开始日期（步骤激活时间） |
| `processDescription` | `''` | 文本输入 | 流程描述关键词，回车查询 |
| `auditStatus` | `''` | 下拉 | 调整状态（8 码） |
| `initiatorName` | `''` | 文本输入 | 发起人 |
| `currentUserId` | `'1001'` | — | TODO，1001 视为管理员 |

`auditStatusOptions`：8 码（`-1/00/10/11/20/21/32/99`），同 dict.js `DICT_AUDIT_STATUS`。无投资池树、无证券类型筛选。

---

## 3. 查询接口

### 3.1 流程下拉 `loadFlowOptions`

- 路径：`POST /api/v1/myMatters/queryFlowOptionList`，请求体 `{currentUserId}`
- 返回 `List<FlowOptionDto>`（`{flowId, flowKey, flowName, description}`）
- 后端 `MyMattersMapper.xml`：`SELECT DISTINCT f.id AS flowId, f.flow_key, f.name AS flowName, f.description FROM ip_adjust_log al INNER JOIN (max step 子查询) INNER JOIN ip_adjust_step s INNER JOIN wf_flow_node n INNER JOIN wf_flow_definition f ... WHERE al.is_deleted=0 [AND 当前用户参与过滤] ORDER BY f.name ASC, f.id ASC`

### 3.2 列表查询 `loadList`

- 路径：`POST /api/v1/myMatters/queryMyMattersPage`
- 请求体：

| 字段 | 来源 | 说明 |
|---|---|---|
| `flowIds` | 多选 | 空数组转 null |
| `startDateStart` / `startDateEnd` | `startDateRange[0/1]` | 直接取日期串 |
| `processDescription` | 表单 | 空串转 null |
| `auditStatus` | 下拉 | 空串转 null |
| `stepStatus` | `activeTab` | `pending` 或 `completed`，决定待处理/已完成 |
| `initiatorName` | 表单 | 空串转 null |
| `currentUserId` | 固定 `'1001'` | 必传 |
| `pageIndex` / `pageSize` | 分页 | — |

返回 `PageResult<MyMattersDto>`，取 `records`/`total`。

---

## 4. 表格列与状态展示

| 列 | prop/字段 | 渲染逻辑 |
|---|---|---|
| 序号 | `$index` | 同前 |
| 流程名称 | `flowName` | tooltip |
| 步骤名称 | `stepName` | tooltip，来自 `s.node_label` |
| 流程描述 | `processDescription` | `desc-link` 点击打开事宜页，后端 CONCAT 生成 |
| 步骤状态 | `stepStatus` | `el-tag` + `stepStatusLabel`/`stepStatusType` |
| 调整状态 | `auditStatus` | `el-tag` + `auditStatusLabel`/`auditStatusType` |
| 发起人 | `initiatorName` | 来自 `al.adjuster_name` |
| 开始时间 | `startTime` | `moment(startTime).format('YYYY-MM-DD HH:mm')`，步骤激活时间 |
| 操作 | — | `fixed="right"`：待处理页签→「处理」按钮（primary）；已完成页签→「查看」按钮 |

`stepStatusLabel`：`pending`→待处理 / `approve`→通过 / `reject`→驳回 / `submit`→提交 / `auto_process`→自动处理 / `canceled`→已撤回。
`stepStatusType`：`pending`→warning / `approve`→success / `reject`→danger / `submit`→primary / `auto_process`/`canceled`→info。
`auditStatusLabel`/`auditStatusType` 同其他页（`20/10/32`→success，`-1/21`→danger，`11/00`→warning，`99`→info）。

---

## 5. 跳转审核 / 详情

`openMatterPage(row)`：
```js
const params = new URLSearchParams();
if (row.securityCode) params.set('securityCode', row.securityCode);
if (row.targetPoolId) params.set('targetPoolId', row.targetPoolId);
if (row.adjustLogId) params.set('adjustLogId', row.adjustLogId);
if (row.adjustBatchNo) params.set('adjustBatchNo', row.adjustBatchNo);
params.set('entryMode', this.activeTab === 'pending' ? 'process' : 'view');
const page = this.activeTab === 'pending' ? 'security_pool_adjust_approve.html' : 'security_pool_adjust_detail.html';
window.location.href = page + '?' + params.toString();
```
- **待处理** → `security_pool_adjust_approve.html?entryMode=process`，审核页 `initStandaloneReviewPage` 读取参数，`entryMode !== 'next'` 即为 process 处理模式，`adjustStep=2` 进入校验确认。
- **已完成** → `security_pool_adjust_detail.html?entryMode=view`，详情页只读模式。

分页参数同前（pageIndex=1, pageSize=20, page-sizes=[10,20,50,100]）。

---

## 6. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `myMatters/queryMyMattersPage` | flowIds, startDateStart, startDateEnd, processDescription, auditStatus, stepStatus(pending\|completed), initiatorName, currentUserId, pageIndex, pageSize | `PageResult<MyMattersDto>`（含 flowName, stepName, processDescription, stepStatus, adjustLogId, targetPoolId, adjustBatchNo, securityCode） | 我的事宜分页列表（待处理/已完成） |
| `myMatters/queryFlowOptionList` | `{currentUserId}` | `List<FlowOptionDto>`（flowId/flowKey/flowName/description） | 我的事宜流程名称下拉 |

> 路径均带前缀 `/api/v1/`。

---

## 7. 关键数据库表与查询逻辑

### 7.1 涉及的表

| 表名 | 用途 | 关键字段 |
|---|---|---|
| `ip_adjust_log` | 证券池调库记录（主表） | id, security_code, security_short_name, adjust_mode, target_pool_name, adjust_batch_no, audit_status, adjuster_id, adjuster_name, is_deleted |
| `ip_adjust_step` | 调库流程步骤记录 | id, adjust_log_id, adjust_batch_no, flow_node_id, node_label, node_type, step_status, handler_id, handler_name, start_time, process_time |
| `wf_flow_node` / `wf_flow_definition` | 流程节点/定义 | id, flow_id, flow_key, name, description, is_deleted |

### 7.2 可见数据范围控制

- **我的事宜**：`al.is_deleted=0`；待处理页签只取最新步骤 `step_status='pending'` 的记录；已完成页签追加 `NOT EXISTS(... step_status='pending' ...)` 确保批次无 pending 步骤。
- **用户隔离**：非管理员要求该调库记录下存在 `handler_id = currentUserId` 的步骤（即只显示自己参与过的）；`currentUserId` 为空时 `AND 1=0` 强制返回空，防止全量泄露。

### 7.3 后端查询逻辑要点（`MyMattersMapper.xml`）

- **主表**：`ip_adjust_log al`
- **核心子查询**（`<choose>` 按 stepStatus 分支）：
  - `pending`：`INNER JOIN (SELECT adjust_log_id, MAX(id) AS step_id FROM ip_adjust_step WHERE step_status='pending' [AND handler_id=#{currentUserId} 当非管理员] GROUP BY adjust_log_id) latest` —— 取每条调库记录的最新 pending 步骤
  - `completed`：`INNER JOIN (SELECT adjust_log_id, MAX(id) AS step_id FROM ip_adjust_step GROUP BY adjust_log_id) latest` —— 取每条记录的最新步骤（不限状态）
  - 再 `INNER JOIN ip_adjust_step s ON s.id=latest.step_id` 取该步骤详情
- **流程关联**：`LEFT JOIN wf_flow_node n ON n.id=s.flow_node_id` → `LEFT JOIN wf_flow_definition f ON f.id=n.flow_id AND f.is_deleted=0`（LEFT JOIN，流程可能缺失）
- **WHERE**：
  - `al.is_deleted=0`
  - 用户隔离（非 1001 管理员）：`AND EXISTS(SELECT 1 FROM ip_adjust_step us WHERE us.adjust_log_id=al.id AND us.handler_id=#{currentUserId})`
  - `currentUserId` 为空时：`AND 1=0`（强制返回空）
  - `flowIds` → `f.id IN (...)`
  - `startDateStart`/`startDateEnd` → `s.start_time >= CONCAT(#{x},' 00:00:00')` / `<= CONCAT(#{x},' 23:59:59')`
  - `processDescription` → 对 `CONCAT(adjuster_name,' 将 ',security_short_name,' ',adjust_mode,' ',target_pool_name,' 的审批申请')` 整体 `LIKE`
  - `auditStatus` → `al.audit_status =`
  - `completed` 额外：`AND NOT EXISTS(SELECT 1 FROM ip_adjust_step ps WHERE ps.step_status='pending' AND ps.adjust_batch_no=al.adjust_batch_no)`
  - `initiatorName` → `al.adjuster_name LIKE`
- **GROUP BY**：`s.adjust_log_id`（去重，避免一条调库记录多条步骤导致重复）
- **SELECT 计算列**：`processDescription` 由 CONCAT 生成；`flowName`=f.name、`stepName`=s.node_label、`initiatorName`=al.adjuster_name
- **排序**：`s.id DESC`
- **Service 后处理**（`MyMattersService.replacePoolNameWithFullPath`）：用全路径映射将 `processDescription` 中的叶子池名替换为全路径（如「二级库」→「信用债大库/二级库」）

### 7.4 管理员穿透

`currentUserId='1001'` 被特判为管理员：不追加 `handler_id` 过滤，可见全部事宜。

---

## 8. 关键校验

- 必须按当前用户隔离事项，普通用户不可看到他人待办；管理员可见全部。
- 流程筛选项仅返回当前用户事项涉及的有效流程。
- 待办与步骤 pending 状态一致，已办不能再次处理。

## 9. 验收标准

- 分页、组合筛选和清空筛选行为正确。
- 跳转时携带足够的调整记录、批次和步骤标识。
- `MyMattersApiTest` 覆盖事项分页与流程筛选接口。

## 10. 关键源码索引

- 前端：`znty-sirm-ui/my_matters.html`、`znty-sirm-ui/dict.js`
- Controller：`MyMattersController.java`
- Service：`MyMattersService.java`、`InvestmentPoolService.java`
- Mapper：`MyMattersMapper.xml`
- 实体：`MyMattersDto`、`MyMattersReq`、`FlowOptionDto`
- SQL：`sql/rrs_security_pool_adjust_schema.sql`（ip_adjust_log / ip_adjust_step）、`sql/rrs_flow_definition_schema.sql`
