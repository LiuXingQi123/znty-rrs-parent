# 流程定义需求说明

> 前端页面：`flow_definition.html`（列表页 + 设计器页两视图）
> 后端前缀：`/api/v1/flows`
> 角色定位：流程管理员维护审批流程的基础信息、画布节点、条件路由和版本；业务管理员可查看流程及历史版本，仅具维护权限者可编辑、发布、停用、删除。

---

## 1. 页面概览与初始化

单页应用挂载 `#flow_definition`，通过 `currentPage` 在两个视图切换：
- **列表页**（`currentPage === 'list'`）：顶部导航栏 + 搜索筛选面板（关键字/状态/分类 + 统计徽标 +「新建流程」）+ 流程表格 + 分页。
- **设计器页**（`currentPage === 'designer'`）：三栏——顶部工具栏（返回/流程名+版本/只读徽标/历史/撤销恢复/保存/发布）、左侧节点面板（基础：开始/结束；流程：审批/条件判断/自动执行/消息通知，均 `draggable`）、中间 SVG 画布（`<g id="eg">` 连线层 + `<g id="ng">` 节点层，含缩放/适应/清空工具）、右侧属性面板（按选中节点/连线切换配置）。

**版本管理**以「流程历史」弹窗承载，列出全部版本，可从草稿进入编辑、从历史版本进入只读预览。

**mounted 初始化**：
1. `axios.defaults.baseURL = 'http://localhost:18090'`，`Content-Type: application/json`。
2. 注册全局事件（mousemove/mouseup/click）。
3. 并行 `loadDicts()`（角色/自动任务/条件字段三字典）与 `loadList()`。

`loadDicts()` 依次请求三个字典接口，任一失败仅 warning 不阻断；前端用 `groupNameMap`/`fieldNameMap` 把 code 译成中文（符合后端只返 code 规范）。

---

## 2. 列表与查询逻辑

### 2.1 筛选项

| 控件 | 字段 | 说明 |
|---|---|---|
| 关键字输入 | `searchText` | 匹配名称/Key，300ms 防抖 |
| 状态下拉 | `statusFilter` | `''`/`active`/`draft`/`disabled` |
| 分类下拉 | `categoryFilter` | `''`/`bond`/`fund`/`stock`/`company`/`other` |

三个筛选项变化均重置 `pageIndex=1` 并触发 `loadList()`。

### 2.2 查询接口

- 路径：`POST /api/v1/flows/queryFlowPage`
- 请求体：`{ pageIndex, pageSize, keyword, status, category }`
- 后端 `FlowService.queryFlowPage`：`PageHelper.startPage` 分页，SQL `WHERE d.is_deleted=0`，`keyword` 模糊匹配 `name`/`flow_key`，`status`/`category` 精确；子查询计算 `current_ver`（非 draft 版本最大号，回退全部版本最大号）和 `has_published_version`（EXISTS active 版本）。`ORDER BY d.updt_time DESC`。
- 返回 `PageResult<FlowDto>`（`id, name, flowKey, category, description, remark, status, currentVer, hasPublishedVersion, createdBy, crteTime, updtTime`）。

### 2.3 表格列渲染

| 列 | 字段 | 渲染 |
|---|---|---|
| 序号 | `$index` | `(pageIndex-1)*pageSize + $index + 1` |
| 流程名称 | `name` | `.proc-name` |
| 描述 | `desc` | tooltip |
| 流程 Key | `key` | 等宽 |
| 业务分类 | `category` | `el-tag` + `catLabel` |
| 版本 | `ver` | 如 `v1` |
| 状态 | `status` | `el-tag`；`draft && hasPublishedVersion` 时加脉冲点+tooltip「已有已发布版本，当前正在更新中」 |
| 更新时间 | `updateTime` | 居中 |
| 操作 | — | 按状态动态：active→更新流程；draft→编辑流程；任意→查看；更多 popover→基础信息/历史/停用/发布/删除 |

### 2.4 分页

`el-pagination`，`page-sizes=[10,20,50,100]`，layout `total, sizes, prev, pager, next, jumper`。后端 `PageRequest.getPageSize()` 硬上限 100。

---

## 3. 流程设计器逻辑（核心）

### 3.1 画布节点/边数据结构

节点类型配置 `NCfg` 定义六种节点：

| type | label | shape | 配色 |
|---|---|---|---|
| `start` | 开始 | circle | 绿 |
| `end` | 结束 | circle | 红 |
| `approval` | 审批节点 | rect | 蓝 |
| `condition` | 条件判断 | diamond | 琥珀 |
| `auto` | 自动执行 | rect | 紫 |
| `notify` | 消息通知 | rect | 青 |

**节点对象**：`{ id, type, label, x, y, shape, sub, approvalStrategy, approvalPersons:[{subjectType, subjectId, subjectName}], approvalRemark, autoTasks:[{task}], autoRemark, notifyTarget, notifyChannels, notifyPersons, notifyTpl, notifyRemark, conditionRemark }`。

**边对象**：`{ id, from, to, label, routeAction, remark, condLogic, condRules:[{field, op, val}] }`。

`normalizeNode` 合并默认值并兼容旧版字符串处理人数组。

### 3.2 审批策略（`approval_strategy`）

| code | 含义 | 行为 |
|---|---|---|
| `preempt` | 抢占审批 | 任一人通过即可 |
| `all` | 全部处理 | 所有人均须处理（会签） |
| `initiator` | 流程发起人处理 | 该策略下不显示处理人选择 |

### 3.3 节点配置面板各类型配置项

- **基本信息**（所有节点）：节点名称 `label`、节点标识 `id`（readonly）。
- **approval**：处理策略下拉；非 `initiator` 时显示处理人 tag 列表（角色=warning、人员=success）+「选择处理人」按钮；备注。
- **condition**：提示「条件分支逻辑请在出线上配置」，仅备注。
- **auto**：执行任务列表（每条 `el-select` 绑定 `task`，选项来自 `autoTaskOptions`）+「添加任务」；备注。
- **notify**：通知方式多选（system/email/sms/wecom）、通知对象（initiator/person）、person 时指定人员多选、消息模板（支持 `#{procName}` 占位符）、备注。

**连线配置面板**：连线标签、流转动作 `routeAction` 下拉（approve/reject/auto/submit）、触发条件区（**当前 `v-show="false"` 隐藏**，但数据通路完整：condLogic AND/OR、condRules 卡片）、备注、删除连线。

### 3.4 候选处理人/角色/自动任务/条件字段的下拉数据接口

四个字典接口均挂 `/api/v1/flows/dict/*`：

| 路径 | 请求体 | 返回 | 用途 |
|---|---|---|---|
| `dict/queryRoleList` | `{}` | `List<RoleDto>{id, roleName, parentId, sortOrder}` | 审批角色字典（查 `t_sys_role WHERE enable=1`） |
| `dict/queryUserList` | `{roleId?, userKeyword?}` | `List<UserDto>{id, userName, roleName}` | 候选处理人（按角色及递归子角色过滤，`GROUP_CONCAT` 拼角色名） |
| `dict/queryAutoTaskList` | `{}` | `List<DictDto>{taskCode}>` | 自动任务字典（6 项硬编码：createAccount/updatePosition/syncSettlement/riskCheck/sendNotify/archiveRecord） |
| `dict/queryCondFieldList` | `{}` | `List<DictDto>{groupCode, fields:[{fieldCode}]}>` | 条件字段字典（3 组硬编码：approvalResult/businessFlag/flowVariable） |

> 已知不一致：`queryAutoTaskList` 仅返 `taskCode`（无 taskName），前端未用 `dict.js DICT_AUTO_TASK_CODE` 兜底，自动任务下拉 label 实际为空。

### 3.5 保存草稿 / 发布版本

设计器顶部「保存」`openSaveConfirm`→`saveDraft`；「发布」`openPublishConfirm`→`confirmPublish`。请求体均为 `DesignerReq`：`{ id, name?, flowKey?, publishNote?, nodes, edges, panX, panY, zoom }`。

**保存草稿** `POST /api/v1/flows/editFlowDraft`
- 前端校验：`designerFlowId` 必须存在；行 `key` 不能为空。
- 后端 `editFlowDraft`：
  1. `queryFlowByIdForUpdate` 行锁（`FOR UPDATE`）。
  2. `name`/`flowKey` 变更：仅 `draft` 状态允许改 `flowKey`，且 `countByFlowKey(newKey, id)` 唯一性校验。
  3. 序列化 `nodes`/`edges` 为 JSON。
  4. 取草稿版本（`status='draft'`）：存在则更新其 `canvas_nodes/edges/pan/zoom`；不存在则新建草稿版本 `verNum = latest.verNum + 1`。
  5. **active 流程首次新建草稿时，同步主表 `status` 由 `active` 回退为 `draft`**（即「更新流程」语义：已发布流程进入编辑即变草稿态）。
  6. `syncNormalized` 全量替换归一化表。
  7. 返回 `FlowDto{versionId, verNum, status:'draft'}`。

**发布版本** `POST /api/v1/flows/editFlowToPublished`
- 前端 `confirmPublish` 提交，关键校验在后端 `validateBeforePublish`。
- 后端 `editFlowToPublished`：
  1. 行锁。
  2. `validateBeforePublish(nodes, edges)`。
  3. 取草稿版本：存在则转 `active`（写 `publishNote/publishedBy/publishedTime`）；不存在则新建 `active` 版本 `verNum+1`。
  4. 主表 `status='active'`。
  5. `syncNormalized`。
  6. 返回 `FlowDto{versionId, verNum, status:'active', publishedTime}`。

**列表「快速发布」** `pubRow(id)`：先 `queryFlowDetail` 取画布，前端预校验 nodes 非空、含 start、含 end，再调 `editFlowToPublished`。

### 3.6 发布前关键校验（`validateBeforePublish`）

| 校验项 | 失败行为 |
|---|---|
| 节点非空 | `画布没有节点，无法发布` |
| 开始节点唯一 | `0`→`缺少开始节点`；`>1`→`只能有一个开始节点` |
| 结束节点存在 | `0`→`缺少结束节点`（不强制唯一） |
| 节点连通性 | 任一节点不在任何边的 from/to → `节点 [label] 未连接任何连线`；无边但多节点 → `画布缺少连线，所有节点均未连接` |
| 条件节点出线带条件 | 仅警告注释，**不阻止发布** |

### 3.7 归一化持久化 syncNormalized

`syncNormalized` 采用**全量替换**模式，每次保存/发布都重建归一化表：
1. `logSyncDeletes` 删除前先查旧行，逐条写 `_evt` DELETE 事件。
2. 按依赖倒序删除：`condRule → edge → conditionConfig → notifyConfig → autoConfig → approvalHandler → approvalConfig → flowNode`。
3. 插入节点，建立 `businessId(nodeId) → surrogateId(id)` 映射；按 `type` 写专属配置（approval→config+handlers；auto→每任务一行；notify→config；condition→config）。
4. 插入连线，建立 `edgeId → surrogateId` 映射；`from`/`to` 引用不存在节点则 `log.warn` 跳过；逐条写 `EdgeCondRuleBo`。
5. 全程写对应 `_evt` INSERT 事件。

**双存储**：`wf_flow_version.canvas_nodes/canvas_edges`（LONGTEXT JSON）是画布快照**真源**；归一化表（`wf_flow_node/edge` 及各 `_config`）是派生，供运行时图遍历与 SQL 关联。两者在草稿保存与发布时同步写入。

### 3.8 版本历史查询、版本详情、启停用

**版本历史** `openHistory(row)` → `POST /api/v1/flows/queryFlowVersionList`，请求体 `{id}`。返回 `List<VersionDto>{versionId, verNum, status, publishNote, publishedBy, publishedTime, crteTime}`，`ORDER BY ver_num DESC`。历史弹窗：draft 行可「编辑流程」，其余可「查看流程」。

**版本详情（只读预览）** `viewVersion(flowId, versionId)` → `POST /api/v1/flows/queryFlowVersionDetail`，请求体 `{flowId, versionId}`。校验版本存在且 flowId 匹配；`parseJsonSafe` 解析 `canvas_nodes/canvas_edges`；返回含 `nodes/edges/pan/zoom` 的 `VersionDto`。前端置 `viewMode=true` 进设计器只读渲染。

**更新流程（active → 进入编辑）** `forkFlow(id)`：先查版本列表，若已存在 draft 版本则提示「请先编辑现有版本」→ `openDesigner`；否则进设计器（后端 `editFlowDraft` 创建新草稿并把主表状态回退为 draft）。

**停用流程** `disableFlow(row)` → `POST /api/v1/flows/editFlowStatus`，请求体 `{id}`。后端：仅 `active` 可停用；主表 `status='disabled'`；遍历版本找最大 verNum 的 active 版本置 `disabled`。含 `TODO 检查运行中实例`（未实现）。

---

## 4. 增删改逻辑

### 4.1 新增流程

`confirmCreate` → `POST /api/v1/flows/addFlow`，请求体 `{name, flowKey, category, description, remark}`。
- 前端校验：`name` 必填、`key` 非空。
- 后端 `addFlow`：`name` 空→`流程名称不能为空`；`flowKey` 空→`流程 Key 不能为空`；`countByFlowKey>0`→`流程 Key 已存在`；插入 `wf_flow_definition`（`status='draft'`）；创建初始草稿版本 `verNum=1`；返回 `FlowDto{flowId, versionId, verNum:1, status:'draft'}`。
- 前端拿到 flowId 后本地 unshift 新行并进空白画布。

### 4.2 编辑基础信息

`confirmEditInfo` → `POST /api/v1/flows/editFlow`，请求体 `{id, name, flowKey, category, description, remark}`。
- 前端：非 draft 态 `flowKey` 输入框 disabled。
- 后端 `editFlow`：流程不存在→404；`name`/`flowKey` 非空；**仅 draft 状态允许改 flowKey**；Key 变更做唯一性校验；按非 null 字段更新主表。

### 4.3 删除流程

`confirmDelete` → `POST /api/v1/flows/deleteFlow`，请求体 `{id}`。后端先 `queryFlowDetail` 留底返回，再 `deleteFlowLogical` 置 `is_deleted=1`，记 DELETE 事件。**逻辑删除**。前端删除后 `loadList()` 重新拉取。

> Controller 注释声明「已有关联业务数据的流程不允许删除」，但当前 Service 未实现引用检查，直接软删。

### 4.4 启停流程

见 3.8。**停用无逆向启用接口**（`editFlowDefinitionStatus` SQL 硬编码 `disabled`），仅支持 active→disabled 单向。

---

## 5. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `queryFlowPage` | pageIndex, pageSize, keyword, status, category | `PageResult<FlowDto>` | 分页查询流程列表 |
| `queryFlowList` | keyword, status, category | `List<FlowOptionDto{flowId, flowKey, flowName, description}>` | 流程下拉选项（供其他模块关联） |
| `addFlow` | name, flowKey, category, description, remark | `FlowDto{flowId, id, versionId, verNum, status}` | 新建流程（含初始草稿版本） |
| `queryFlowDetail` | id | `FlowDto`（含 draft 画布） | 查流程详情（进设计器，优先返草稿） |
| `editFlow` | id, name, flowKey, category, description, remark | `FlowDto` | 更新基础信息 |
| `deleteFlow` | id | `FlowDto`（被删流程信息） | 逻辑删除流程 |
| `editFlowStatus` | id | `FlowDto` | 停用流程（active→disabled） |
| `editFlowDraft` | id, name?, flowKey?, nodes, edges, panX, panY, zoom | `FlowDto{versionId, verNum, status:'draft'}` | 保存设计器草稿 |
| `editFlowToPublished` | id, publishNote?, nodes, edges, panX, panY, zoom | `FlowDto{versionId, verNum, status:'active', publishedTime}` | 发布版本 |
| `queryFlowVersionList` | id（flowId） | `List<VersionDto>` | 查询历史版本列表 |
| `queryFlowVersionDetail` | flowId, versionId | `VersionDto`（含 nodes/edges/pan/zoom） | 查版本详情（只读画布） |
| `dict/queryRoleList` | `{}` | `List<RoleDto>` | 审批角色字典 |
| `dict/queryUserList` | roleId?, userKeyword? | `List<UserDto>` | 候选处理人 |
| `dict/queryAutoTaskList` | `{}` | `List<DictDto{taskCode}>` | 自动任务字典（6 项硬编码） |
| `dict/queryCondFieldList` | `{}` | `List<DictDto{groupCode, fields}>` | 条件字段字典（3 组硬编码） |

> 路径均带前缀 `/api/v1/flows`。入参对象：列表/新增/编辑/版本用 `FlowReq`，详情/删除/停用用 `IdRequest`，设计器保存/发布用 `DesignerReq`。

---

## 6. 关键数据库表

数据库 22 张表（11 业务表 + 11 `_evt` 事件表），定义于 `sirm_flow_definition_schema.sql`。每业务表配同结构 `_evt` 审计表（增 `evt_id/opter_id/opt_time/oprt_type`）。

| 表 | 用途 | 关键字段/枚举 |
|---|---|---|
| `wf_flow_definition` | 流程主表 | `name, flow_key, category(bond/fund/stock/company/other), status(draft/active/disabled), is_deleted` |
| `wf_flow_version` | 版本表（画布真源） | `flow_id, ver_num, status(draft/active/disabled), canvas_nodes(LONGTEXT JSON), canvas_edges(LONGTEXT JSON), canvas_pan_x/y/zoom(0.25~2.5), publish_note, published_by/time` |
| `wf_flow_node` | 节点表 | `version_id, node_id(画布标识如 n101), node_type(start/end/approval/condition/auto/notify), label, shape(circle/rect/diamond), pos_x/y, sub_label` |
| `wf_node_approval_config` | 审批节点配置（1:1） | `node_id, approval_strategy(preempt/all/initiator), approval_remark` |
| `wf_node_approval_handler` | 审批处理人（1:N） | `approval_config_id, subject_type(role/user), subject_id, subject_name(快照), sort_order` |
| `wf_node_auto_config` | 自动任务配置（1:N） | `node_id, task_seq, task_code(createAccount/updatePosition/syncSettlement/riskCheck/sendNotify/archiveRecord), auto_remark` |
| `wf_node_notify_config` | 通知节点配置（1:1） | `node_id, notify_channels(JSON), notify_target(initiator/person), notify_persons(JSON), notify_tpl, notify_remark` |
| `wf_node_condition_config` | 条件节点配置（1:1） | `node_id, condition_remark`（分支逻辑在出线维护） |
| `wf_flow_edge` | 连线表 | `version_id, edge_id, from_node_id/to_node_id(BIGINT 代理键), label, route_action(approve/reject/auto/submit), cond_logic(AND/OR)` |
| `wf_edge_cond_rule` | 连线条件规则（1:N） | `edge_id, seq, field_code, operator(eq/neq/gt/lt/gte/lte), field_val` |
| `wf_role_dict` | 业务角色字典 | `role_code, role_name, sort_order, is_active`（注：实际审批角色查 `t_sys_role`，本表仅 schema+demo，Service 未直接读取） |

---

## 7. 关键校验与约束

### 7.1 发布版本校验（`validateBeforePublish`）

- 节点非空；开始节点有且仅有一个；至少一个结束节点；不存在孤立节点（任一节点必须出现在某条边的 from/to）；无边但多节点拒绝。
- 条件节点出线带条件仅警告，不阻止发布。

### 7.2 flowKey 唯一性与可改性

- 新增/编辑/保存草稿均做 `countByFlowKey` 唯一性校验（排除自身）。
- **仅 draft 状态允许改 flowKey**；前端非 draft 态 Key 输入框 disabled。

### 7.3 并发安全

- 保存草稿与发布均用 `queryFlowByIdForUpdate`（`FOR UPDATE` 行锁）+ `@Transactional(rollbackFor=Exception.class)`，保证版本号递增并发安全。

### 7.4 状态流转约束

- 流程定义 `status`：`draft → active`（发布）；`active → draft`（已发布流程进设计器保存草稿时回退）；`active → disabled`（停用，仅 active 可停用）。
- 版本 `status`：`draft → active`（发布）；`active → disabled`（停用时最大 active 版本置 disabled）。
- 停用无逆向启用接口。

### 7.5 草稿版本管理

- 每流程至多一个 draft 版本。`forkFlow` 前端先查版本列表，若已有草稿则提示「请先编辑现有版本」。
- `queryFlowDetail` 优先返草稿；无草稿时加载最新版本画布作编辑基础，**不创建草稿记录**，草稿记录在首次 `editFlowDraft` 时创建。

### 7.6 归一化数据一致性

- `syncNormalized` 全量替换：先删后插，保证归一化表与画布 JSON 一致；删除前 `logSyncDeletes` 捕获旧行写 `_evt` DELETE，保留完整审计。
- 连线引用不存在节点时 `log.warn` 跳过；处理人明细跳过 `subjectType`/`subjectId` 为空者。

### 7.7 软删除与引用检查

- 删除为逻辑删除（`is_deleted=1`），所有查询均 `WHERE is_deleted=0`。
- Controller 注释声明「已有关联业务数据的流程不允许删除」，但 Service `deleteFlow` 当前**未实现引用检查**，直接软删；停用同理有 `TODO 检查运行中实例` 未实现。

### 7.8 已知不一致点

1. **条件规则编辑器 UI 隐藏**：右侧连线属性面板「触发条件」区块整体 `v-show="false"`，当前无法在 UI 增删条件规则；但 `condRules` 数据通路完整，demo 数据中的 condRules 仍会被加载与保存。
2. **自动任务字典缺中文名**：`queryAutoTaskList` 仅返 `taskCode`，前端未用 `dict.js DICT_AUTO_TASK_CODE` 兜底，下拉 label 为空。
3. **`wf_role_dict` 未被使用**：审批角色实际查 `t_sys_role`。
4. **`archived` 状态**：`FlowVersionBo` 注释提到，但 schema 与代码仅用 `draft/active/disabled`。

## 8. 验收标准

- 新建与编辑基础信息使用相同字段和视觉结构。
- 草稿、已发布、停用及「已发布后更新中」状态显示正确。
- 历史列表按版本倒序展示，指定版本可只读打开。
- 发布前节点连通性、开始节点唯一性校验生效。
- `FlowDefinitionApiTest` 覆盖本页全部接口路由。

## 9. 关键源码索引

- 前端：`znty-sirm-ui/flow_definition.html`（列表 `loadList`、设计器画布、`saveDraft`/`confirmPublish`/`syncNormalized` 调用、版本历史）
- dict.js：`DICT_APPROVAL_STRATEGY`、`DICT_AUTO_TASK_CODE`、`DICT_FLOW_COND_FIELD`
- Controller：`FlowController.java`
- Service：`FlowService.java`（`queryFlowPage`、`addFlow`、`editFlowDraft`、`editFlowToPublished`、`validateBeforePublish`、`syncNormalized`、`editFlowStatus`、`queryFlowVersionList/Detail`）
- Mapper：`FlowMapper.xml`
- 实体：`FlowReq`、`DesignerReq`、`FlowDto`、`VersionDto`、`CanvasNodeDto`、`CanvasEdgeDto`、`RoleDto`、`UserDto`、`NodeApprovalConfigBo`、`NodeApprovalHandlerBo`、`EdgeCondRuleBo`
- SQL：`sql/sirm_flow_definition_schema.sql`、`sql/sirm_flow_definition_demo_data.sql`
