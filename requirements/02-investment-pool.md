# 投资池维护需求说明

> 前端页面：`investment_pool.html`（左树 + 右详情三 Tab）
> 后端前缀：`/api/v1/investmentPool`
> 角色定位：投资池管理员维护投资池树、池间关系、关联审批流程、自动规则和操作权限，为证券调库提供可执行的业务边界。

---

## 1. 页面概览与初始化

全屏三段式布局（`flex-direction: column` 撑满 100vh）：
- **顶部导航栏**：Logo + 「投资池维护」标题 + 面包屑 + 当天日期。
- **主体区**左右两栏：
  - **左侧侧边栏**：标题「投资池树」+「新增顶级」按钮 + 刷新按钮 + 树搜索框 + `el-tree`（`node-key="id"`，`default-expand-all`，自定义节点模板：父节点 folder 图标、叶子 document 图标，hover 显示「+添加子节点」「删除节点」）。
  - **右侧内容区**：`pool-header`（池名 + 启用徽章）+ `el-tabs` 三个标签页：
    - **基础配置**：基础信息表单（名称/恒生池名/投资市场复选/投资品种复选/最大上限/锁池/调入冻结期/外部排序/内部排序/调入研报限制/调出研报限制/描述）+ 审批流程表单（6 个流程下拉）+ 刷新/保存按钮。
    - **关系配置**：9 种关系类型卡片网格 + 自动调入/调出规则两张卡片 + 刷新/保存按钮。
    - **权限配置**：可查看人员/可调整人员/可 Excel 导入人员三类分区，每区 `el-tag` 列表 +「添加人员」+ 刷新/保存按钮。

- **6 个弹窗**：添加子池、新增顶级池、关系池选择、自动规则选择、配置模板选择、权限人员选择。

**初始化**（`created`）依次调用：
1. `initRelationForm()`：为 9 种关系类型各置空数组。
2. `loadFlowOptions()`：`POST /api/v1/flows/queryFlowList` 请求 `status:'active'` 流程，首位插入「不需要审批」占位项。
3. `loadPoolTree()`：`POST /api/v1/investmentPool/queryPoolList`，`buildPoolTree` 组装树、`flattenPools` 展平、`buildPoolParentMap` 构建面包屑映射，默认选中第一个节点并 `loadPoolDetail`。
4. `loadRuleCategories()` + `loadRules()`：请求规则分类和启用规则，用于自动规则选择弹窗。

请求封装 `Vue.prototype.apiPost`：`apiBase='http://localhost:18090'`，统一 POST，`!json.success` 弹错并抛异常。

---

## 2. 查询逻辑

### 2.1 投资池树查询

- 路径：`POST /api/v1/investmentPool/queryPoolList`
- 请求体：`{}`（req 字段被服务实现忽略，直接返回全量未删除池）
- 后端 `queryPoolList`：`SELECT` 全部 35 字段，`WHERE is_deleted=0`，`ORDER BY outer_sort ASC, parent_id ASC, inner_sort ASC, id ASC`，逐个 `convertPool` 转 DTO。
- 返回 `List<InvestmentPoolDto>`（含 `id, parentId, poolCode, poolName, poolFullName, poolType, poolLevel, marketCodes, varietyCodes, hsPoolName, 6 个 FlowOptionDto, inReportRestriction, outReportRestriction, maxCapacity, outerSort, innerSort, description, status, relationPoolIds, autoIn/outRuleIds/Descs, permissions, children` 等）。
- 前端 `buildPoolTree` 按 `parentId` 挂到父节点 `children`；`flattenPools` 递归拼接 `fullName`（用 ` / ` 分隔）。

### 2.2 投资池详情查询

- 路径：`POST /api/v1/investmentPool/queryPoolDetail`
- 请求体：`{ id }`
- 后端 `queryPoolDetail`：校验 id 非空、池存在；`convertPool` 转基础 DTO；`fillRelationConfig`（查 `ip_pool_relation` 按 `relationType` 分组塞 `relationPoolIds`）；`fillAutoRuleConfig`（查 `ip_pool_auto_rule`，auto_in/auto_out 分组）；`fillPermissionConfig`（查 `ip_pool_permission` 塞 `permissions`）。

### 2.3 详情区渲染

详情区用表单 + 卡片网格（非 el-table）：
- **基础配置**字段：`poolName`（必填）、`hsPoolName`、`marketCodes`（4 项：UNKNOWN/SSE/SZSE/CIBM）、`varietyCodes`（7 项：bond/warrant/trust/index/stock/issuer/fund，默认 `['bond']`）、`maxCapacity`、`lockFlag`（0=未锁定/1=锁定）、`frozenPeriodIn`（调入冻结天数，空或 0 表示不限制）、`outerSort`、`innerSort`、`inReportRestriction`/`outReportRestriction`（none/any/internal）、`description`。
- **审批流程**：6 项（inFlowId/outFlowId/simpleInFlowId/simpleOutFlowId/batchInFlowId/batchOutFlowId），下拉来自 `flowOptions`（含「不需要审批」占位），绑定值为 `String(flowId)`。
- **关系配置**：9 张卡片，每张右上角显示已选数量，下方 chip 列表显示「父路径 › 池名」面包屑。
- **状态**：详情页 `pool-header` 只硬编码显示「启用」徽章（无 disabled 差异化展示）。

> 注：前端 `poolTypeOptions` 只列 5 种类型（信用债/境外债/转债/专户产品/其他），而 `dict.js DICT_POOL_TYPE` 含 12 种（含禁投池、研究池等）——新建顶级池只能选 5 种，但字典覆盖更广（供其他查询页用）。

---

## 3. 增删改逻辑

### 3.1 新增顶级投资池

- 路径：`POST /api/v1/investmentPool/addRootPool`
- 请求体：`{poolName, poolType, outerSort?, templatePoolId?, operatorId?}`
- 后端 `addRootPool`：校验 poolName/poolType 非空；模板存在性校验；`buildRootPool`（`parentId=null`，`poolCode=poolType+"_root_"+时间戳`，`poolLevel=1`，`outerSort` 未传取最大+1，`innerSort=1`，有模板则 `copyBaseConfig` 复制市场/品种/恒生池名/6 流程/研报限制/容量/锁池/冻结期/描述，否则默认 `marketCodes="[]"`、`varietyCodes='["bond"]'`、`lockFlag=0`，`status='enabled'`）；`addPool` 插入回填 id；`addPoolEvent("新增")` 审计；有模板则复制关系/规则/权限。

### 3.2 新增子投资池

- 路径：`POST /api/v1/investmentPool/addChildPool`
- 请求体：`{parentId, poolName, innerSort?, templatePoolId?, inheritParentConfig?, operatorId?}`
- 后端 `addChildPool`：校验 parentId/poolName 非空、父池存在；模板选择（显式传 templatePoolId 优先，否则 inheritParentConfig 用父池当模板）；`buildChildPool`（**`poolType` 强制继承父池**不读 req，`poolLevel=父.level+1`，`outerSort=父.outerSort`，`innerSort` 未传取父下最大+1，`poolCode=父.poolCode+"_child_"+时间戳`）；插入+事件+模板复制。
- 前端打开弹窗时默认把 `templatePoolId` 设为父节点 id（「以父节点为模板」）。

### 3.3 编辑基础配置

- 路径：`POST /api/v1/investmentPool/editPoolConfig`
- 请求体：`{id, poolName, marketCodes, varietyCodes, hsPoolName, inFlow, outFlow, simpleInFlow, simpleOutFlow, batchInFlow, batchOutFlow, inReportRestriction, outReportRestriction, maxCapacity, outerSort, innerSort, description, operatorId}`（流程字段为完整 `FlowOptionDto`）
- 后端 `editPoolConfig`（`@Transactional`）：校验 id、池存在；`buildPoolForEdit` 仅 set 指定字段（**不更新 status、poolType、poolLevel、parentId、poolCode**）；`applyFlow` 对 6 流程逐个 `normalizeFlow`（flowId 为 null 时清空）；`editPoolConfig` UPDATE；`addPoolEvent("修改")`。
- 流程字段存储为快照（flowId/flowKey/flowName 三列），避免流程改名后历史池配置失真。

### 3.4 删除投资池节点

- 路径：`POST /api/v1/investmentPool/deletePoolNode`
- 请求体：`{id, operatorId?}`
- 前端 `$confirm`（「确认删除"X"及其所有下级节点？删除后相关关系配置也会同步清理。」）。
- 后端 `deletePoolNode`（`@Transactional`）：校验 id、池存在；`collectDescendantPoolIds` 递归收集当前节点+全部后代 id；对 deleteIds 批量：
  - 删除这些池**作为主池**的关系（先审计后逻辑删）；
  - 删除其他池**引用这些池**的关系（避免悬挂引用）；
  - 删除自动规则、权限；
  - 逻辑删除主表 `is_deleted=1`。
- 返回删除前的池 DTO。

### 3.5 池关系维护

- 路径：`POST /api/v1/investmentPool/editPoolRelation`
- 请求体：`{id, relationPoolIds(Map<relationType,Long[]>), autoInRuleIds, autoInRuleDescs, autoOutRuleIds, autoOutRuleDescs, operatorId}`

**9 种关系类型**（固定顺序）：

| key | 中文 |
|---|---|
| `source` | 来源池 |
| `in_restrict` / `out_restrict` | 调入/调出限制池 |
| `in_linked` / `out_linked` | 调入/调出联动池 |
| `in_mutex` / `out_mutex` | 调入/调出互斥池 |
| `in_soft_restrict` / `out_soft_restrict` | 调入/调出弹性禁投池 |

- 后端 `editPoolRelation`（`@Transactional`）：**全量替换**——先审计+逻辑删旧关系，再 `addRelations`（批量查名称快照，按 `RELATION_TYPES` 固定顺序逐条 insert，`relation_pool_name` 存快照名，`sort_order` 从 1 递增）；自动规则同样全量替换（auto_in/auto_out，下标对齐取 descs）。
- 前端配置：9 张关系卡片，点击「选择」打开关系池选择弹窗（深拷贝主树、预填已选、过滤不可选节点）。可选性判断 `isPoolPickerNodeSelectable`：**排除当前池本身 + 排除有子节点的父节点（只允许选叶子池）**。关系池 chip 显示面包屑，可单独移除。
- 自动规则配置：点击「选择」打开规则选择弹窗（左侧表格多选启用规则，右侧已选列表），确认后写回 `autoInRuleIds/Descs` 或 `autoOutRuleIds/Descs`。desc 实际是规则名称快照。

### 3.6 流程绑定

每个投资池可绑定 **6 个审批流程**（存储为快照三列）：

| 字段 | 用途 |
|---|---|
| `in_flow_id/key/name` | 标准调入审批流程 |
| `out_flow_id/key/name` | 标准调出审批流程 |
| `simple_in_flow_id/key/name` | 简易调入流程 |
| `simple_out_flow_id/key/name` | 简易调出流程 |
| `batch_in_flow_id/key/name` | 批量调入流程 |
| `batch_out_flow_id/key/name` | 批量调出流程 |

- 流程下拉来源：前端实际调 `/api/v1/flows/queryFlowList`（流程定义模块）。Controller 里的 `queryFlowOptionList` 接口仅后端定义、前端未直接使用。
- 研报限制：`in_report_restriction`/`out_report_restriction`（none/any/internal），与流程同属基础配置。

### 3.7 权限配置

- 路径：`POST /api/v1/investmentPool/editPoolPermission`
- 请求体：`{id, permissions:List<PoolPermissionBo>{permissionType, handlerType, handlerId, handlerName}, operatorId?}`

**权限类型**（3 种）：`viewable` 可查看 / `adjustable` 可调整 / `excel_importable` 可 Excel 导入。
**主体类型**（2 种）：`role` 角色（橙色 tag）/ `user` 人员（蓝色 tag）。

- 后端 `editPoolPermission`（`@Transactional`）：全量替换——审计+逻辑删旧权限，再逐条 `addPermission` + 审计，存 `handler_name` 快照。
- 前端：权限人员选择弹窗（左侧角色树 `show-checkbox`/`check-strictly`/可「显示全部人员」，右侧人员表格搜索+多选）。前端 `permissionType` 用驼峰（excelImportable），写入时映射成数据库下划线（excel_importable）。
- 人员/角色下拉：
  - `/queryRoleList`：查 `t_sys_role WHERE enable=1`，返回 `RoleDto{id, roleName, parentId, sortOrder}`，前端 `buildRoleTree` 组装角色树。
  - `/queryUserList`：`{roleId?, keyword?}`，`LEFT JOIN t_sys_user_role + t_sys_role`，`GROUP_CONCAT` 拼角色名（、分隔），支持按 `roleIds`（递归子角色）和 keyword 过滤。

### 3.8 关键校验汇总

| 校验点 | 行为 |
|---|---|
| 顶级池名称/类型非空 | `BizException` |
| 模板池存在 | `模板投资池不存在` / `配置模板不存在` |
| 子池 parentId/名称非空、父池存在 | `BizException` |
| 详情/编辑/删除 id 非空、池存在 | `投资池 ID 不能为空` / `投资池不存在` |
| 子池类型强制继承父池 | 不读 req |
| 子池层级 = 父层级+1 | 自动计算 |
| 关系池可选性（前端） | 排除当前池 + 排除父节点（只选叶子） |
| 自动规则空值过滤 | ruleId==null && desc 为空时跳过 |

---

## 4. 接口清单

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `queryPoolList` | `{}`（req 被忽略） | `List<InvestmentPoolDto>` | 查全量投资池平铺列表，前端组装树 |
| `queryPoolDetail` | `{id}` | `InvestmentPoolDto`（含 relationPoolIds/autoIn(out)RuleIds/Descs/permissions） | 查单池详情 |
| `editPoolConfig` | id, poolName, marketCodes, varietyCodes, hsPoolName, inFlow, outFlow, simpleInFlow, simpleOutFlow, batchInFlow, batchOutFlow, inReportRestriction, outReportRestriction, maxCapacity, lockFlag, frozenPeriodIn, outerSort, innerSort, description, operatorId | `InvestmentPoolDto` | 保存基础配置 |
| `editPoolRelation` | id, relationPoolIds(Map), autoInRuleIds, autoInRuleDescs, autoOutRuleIds, autoOutRuleDescs, operatorId | `InvestmentPoolDto` | 全量替换关系配置（9 关系 + 自动规则） |
| `addRootPool` | poolName, poolType, outerSort?, templatePoolId?, operatorId? | `InvestmentPoolDto` | 新增顶级池 |
| `addChildPool` | parentId, poolName, innerSort?, templatePoolId?, inheritParentConfig?, operatorId? | `InvestmentPoolDto` | 新增子池 |
| `deletePoolNode` | id, operatorId? | `InvestmentPoolDto`（删除前的池） | 递归级联删除节点及全部后代 + 关系/规则/权限 |
| `queryFlowOptionList` | `{}` | `List<FlowOptionDto>` | 查启用流程下拉（前端实际用 `/flows/queryFlowList`） |
| `addSeedPoolList` | operatorId? | `List<InvestmentPoolDto>` | 初始化种子数据（幂等） |
| `queryRoleList` | `{}` | `List<RoleDto>` | 查启用角色 |
| `queryUserList` | roleId?, keyword? | `List<UserDto>` | 查人员（按角色树递归 + 关键字） |
| `editPoolPermission` | id, permissions, operatorId? | `InvestmentPoolDto` | 全量替换权限配置 |

> 路径均带前缀 `/api/v1/investmentPool`。另：前端 `created` 还调用流程定义模块 `/api/v1/flows/queryFlowList` 和规则模块 `/api/v1/rules/options/queryCategoryList`、`/api/v1/rules/queryRulePage`。

---

## 5. 关键数据库表

来源 `rrs_pool_init_schema.sql`。每业务表配同结构 `_evt` 审计表（增 `evt_id/opter_id/opt_time/oprt_type`，oprt_type 存中文：新增/删除/修改/审核）。逻辑删除 `is_deleted TINYINT(1)`。

### 5.1 `ip_investment_pool`（投资池主表）

关键字段：`id, parent_id, pool_code, pool_name, pool_type, pool_level, market_codes(JSON), variety_codes(JSON), hs_pool_name, 6×3 流程快照列, in/out_report_restriction(none/any/internal), max_capacity, lock_flag, frozen_period_in, outer_sort, inner_sort, description, status(enabled/disabled), is_deleted, crte_time/updt_time`。

`pool_type` 枚举（dict.js `DICT_POOL_TYPE`）：research/fund/restricted/other/industry/whitelist/blacklist/private_placement/credit_bond/offshore_bond/convertible_bond/special_account。
`status` 枚举：enabled=启用 / disabled=停用。

### 5.2 `ip_pool_relation`（投资池关系表）

`id, pool_id, relation_type(source/in_restrict/out_restrict/in_linked/out_linked/in_mutex/out_mutex/in_soft_restrict/out_soft_restrict), relation_pool_id, relation_pool_name(快照), sort_order, remark, is_deleted`。

### 5.3 `ip_pool_auto_rule`（自动调入调出规则备注表）

`id, pool_id, rule_type(auto_in/auto_out), rule_id, rule_desc, is_deleted`。一期仅保存备注不执行。

### 5.4 `ip_pool_permission`（投资池权限配置表）

`id, pool_id, permission_type(viewable/adjustable/excel_importable), handler_type(role/user), handler_id, handler_name(快照), is_deleted`。

### 5.5 辅助表

- `t_sys_role`（角色/部门表）：`id, name, parent_id, sort_order, enable`，树形结构。
- `t_sys_user`（人员表）：`id, name, user_name(拼音), dr`。
- `t_sys_user_role`（人员角色关联表）：`user_id, role_id, dr`，多对多。
- `wf_flow_definition`（流程定义表）：被 `queryFlowOptionList` 引用（`status='active'`）。

### 5.6 审计表（7 张 `_evt` 表）

所有写操作遵循「先写审计表，再改主表」或「改主表后写审计」模式。`opter_id` 来自 `req.operatorId`，缺省 `"system"`（前端所有调用硬编码 `operatorId:'system'`，未接入真实登录用户）。

---

## 6. 关键校验与约束

### 6.1 删除约束

- **递归级联删除**：`collectDescendantPoolIds` 递归收集当前节点+全部后代，批量逻辑删除。
- **关系双向清理**：既删 `pool_id IN (deleteIds)`（池作为主池），也删 `relation_pool_id IN (deleteIds)`（池被其他池引用），避免悬挂引用。自动规则、权限按 `pool_id IN (deleteIds)` 清理。
- **删除前审计**：所有删除先 `addXxxEventByXxxIds("删除")` 写审计，再逻辑删除。
- ⚠️ **删除不检查业务引用**：`deletePoolNode` **不检查**该池是否被进行中的调库申请（`ip_adjust_log`/`ip_pool_status`）引用。删除仅清理投资池自身关系/规则/权限，其他模块若持有 `pool_id` 引用，删除后可能产生数据不一致（潜在风险点）。
- 逻辑删除 `is_deleted=1`，产品层面不可恢复，前端二次确认。

### 6.2 启停影响

- `status` 枚举 enabled/disabled，新增池默认 `status='enabled'`。
- **当前页面无启停操作入口**：`editPoolConfig` 的 `buildPoolForEdit` 不更新 status，前端 `baseForm` 无 status 控件，详情页只硬编码显示「启用」徽章。
- 后端查询大多不按 status 过滤（只按 `is_deleted=0`），停用的池仍出现在树中、仍可被选为关系池/模板池。
- 启停的实际影响在消费方（如 `SecurityPoolAdjustService` 发起调整时校验目标池状态）。

### 6.3 父子池约束

- 顶级池 `parentId=null`、`poolLevel=1`；子池 `parentId=父.id`、`poolLevel=父.level+1`。
- 子池 `poolType` **强制继承父池**，不读请求参数。
- 子池 `outerSort` 继承父池，`innerSort` 默认取父下最大+1。
- `poolCode` 自动生成：顶级=`poolType+"_root_"+时间戳`；子级=`父.poolCode+"_child_"+时间戳`。
- **无层级深度限制**；**无同名校验**（新增/编辑都不校验同父下 poolName 重复）。

### 6.4 容量与排序

- `maxCapacity`：前端 `:min="0" :max="999999999"`，后端 Long 直接存储，无业务校验（不校验是否大于现有数量）。
- `outerSort`/`innerSort`：前端 `:min="0" :max="9999"`，新增未传则自动取最大+1。
- 排序用于树展示顺序。

### 6.5 跨模块引用

投资池 Mapper 的以下方法被其他业务模块直接调用（投资池是基础数据，被多方依赖）：
- `queryMutexRelationList`（查 in_mutex+out_mutex）→ `SecurityPoolAdjustService` 调整时校验互斥池。
- `queryUserRoleIdList` + `queryPermissionListByType("adjustable")` → `SecurityPoolAdjustService` 校验当前用户对目标池的 adjustable 权限。
- `queryPoolBoList` / `queryPoolFullNameMap`（递归 CTE 拼全路径名）→ 各查询/调整模块取池基础信息与全路径名。

**意味着**：投资池删除/改名/改关系会直接影响证券池调整、禁投池查询、主体池查询等模块。但 `deletePoolNode` 不检查这些消费方的在途数据，是主要风险点。

### 6.6 事务边界

所有写操作 Service 方法均 `@Transactional(rollbackFor=Exception.class)`：`editPoolConfig`、`editPoolRelation`、`addRootPool`、`addChildPool`、`deletePoolNode`、`editPoolPermission`、`addSeedPoolList`。任一步骤抛异常全部回滚。

### 6.7 种子数据初始化

`addSeedPoolList` 幂等：`queryPoolTotalCount > 0` 直接返回现有列表。首次初始化创建信用债大库+一~五级库、境外债库、转债库、专户产品+一~五级库。该接口前端未调用（依赖 SQL 脚本预置数据），供首次部署/测试手动调用。

## 7. 验收标准

- 树节点新增、配置、移动和删除后前后端结构一致。
- 关系/规则/权限全量替换后与详情查询结果一致。
- 删除节点级联清理关系/规则/权限，不产生悬挂引用。
- `InvestmentPoolApiTest` 覆盖维护、配置和选项业务线。

## 8. 关键源码索引

- 前端：`znty-rrs-ui/investment_pool.html`（树 `loadPoolTree`/`buildPoolTree`、基础配置 `handleSaveBase`、关系配置 `handleSaveRelation`/`openPoolTreePicker`、权限 `handleSavePermission`/`handleConfirmPermissionDialog`、删除 `handleDeletePool`）
- dict.js：`DICT_POOL_TYPE`、`DICT_POOL_LEVEL`、`DICT_POOL_STATUS`、`DICT_POOL_RELATION_TYPE`、`DICT_POOL_PERMISSION_TYPE`、`DICT_PERMISSION_HANDLER_TYPE`、`DICT_AUTO_RULE_TYPE`、`DICT_REPORT_RESTRICTION`
- Controller：`InvestmentPoolController.java`
- Service：`InvestmentPoolService.java`（`queryPoolList/Detail`、`addRootPool`、`addChildPool`、`editPoolConfig`、`editPoolRelation`、`editPoolPermission`、`deletePoolNode`、`syncNormalized`、`fillRelationConfig`/`fillAutoRuleConfig`/`fillPermissionConfig`）
- Mapper：`InvestmentPoolMapper.xml`
- 实体：`InvestmentPoolReq`、`InvestmentPoolDto`、`InvestmentPoolBo`、`PoolPermissionBo`、`FlowOptionDto`、`RoleDto`、`UserDto`
- SQL：`sql/rrs_pool_init_schema.sql`、`sql/rrs_pool_init_demo_data.sql`
