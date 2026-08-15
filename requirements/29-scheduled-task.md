# 29 定时任务管理

> 前端页面：`scheduled_task.html`  
> 后端前缀：`/api/v1/scheduledTask`  
> 角色定位：运维 / 管理员在「RRS配置」下可视化查看、启停、改 cron、手动执行定时任务，并查看执行历史。

## 1. 业务目标

**配置与实现分离**（先建库表配置，再写业务实现）：

1. **库表配置** `sys_scheduled_task`：名称 / 说明 / cron / 启停 / **扩展参数（通用字段 param_json）** — 页面新增、编辑、删除  
2. **代码实现** `RrsScheduledTask`：`getTaskCode()` + `execute()` + 可选 `getParamHelp()`，与库表 `task_code` 绑定  
3. **执行历史** `sys_scheduled_task_run_log`：手动 / 定时触发留痕  

### 扩展参数（通用约定）

| 项 | 说明 |
|---|---|
| 存储 | `param_json` VARCHAR，框架**不解析**含义，只存/改/展示 |
| 格式 | **仅 JSON 对象**；各任务在 `execute()` 内自行解析；**不兼容**纯文本等旧格式 |
| 页面说明 | 已注册实现时，列表/详情带 `paramHelp`（来自 `RrsScheduledTask#getParamHelp`），配置页按任务展示，**前端不写死某任务格式** |
| 新任务 | 实现类定义自己的 JSON 字段 + 覆盖 `getParamHelp()` 即可 |

演示数据预置任务（`rrs_scheduled_task_demo_data.sql`）：

| taskCode | 名称 | 默认 cron | 扩展参数示例 |
|---|---|---|---|
| `security_expired_auto_out` | 到期证券自动出池 | `0 0 2 * * ?` | `{"poolIds":[15]}`：池内已生效债/股且到期日早于昨天（T-2）→ 自动调出；调出限制池阻断；软删成功才计数 |
| `crmw_expired_auto_out` | CRMW到期自动出池 | `0 0 3 * * ?` | `{"poolIds":[18]}`：CRMW 池内凭证到期日早于昨天（T-2）→ 自动调出；调出限制池阻断 |
| `company_outer_rating_not_aa_minus_auto_out` | 外评非AA-及以下主体自动出池 | `0 0 4 * * ?` | `{"poolIds":[16],"limitPoolIds":[15]}`：有效外评不在名单内则出；已在禁投拦截池则不出；顺带出同池债 |
| `company_outer_rating_aa_minus_auto_in` | 外评AA-及以下主体自动入池 | `0 0 5 * * ?` | `{"poolIds":[15]}`：有效外评在 AA-/A/BBB… 列表内且未在目标池的主体 → 自动入池 |
| `company_same_pool_bond_auto_in` | 主体下债券自动入库 | `0 0 6 * * ?` | `{"poolIds":[15]}`：主体已在本池 → 旗下债未到期（含当天）未在本池 → 同池自动入（IP_RULE 版）；调入限制池阻断 |
| `company_inpool_bond_auto_in` | 在池主体旗下债券自动入池 | `0 0 7 * * ?` | `{"poolIds":[15]}` 或 `mappings`：主体在池 → 旗下未到期未在目标池的债自动入池（排除临时代码已更新、ABS、CRMW） |
| `company_not_in_pool_bond_auto_out` | 主体不在池债券自动出池 | `0 0 8 * * ?` | `{"poolIds":[15]}` 或 mappings：债在债券池、主体不在主体池 → 出债（排除 ABS/CRMW，不看限制池） |
| `bond_grade_inconformity_alert` | 不符合主体债入库规则提醒 | `0 0 9 * * ?` | 无需参数。扫描已在 1～5 级但按当前特殊债规则不再允许的债券，写入 `ip_grade_rule_alert` 待办；**不自动出池**。对齐老系统 InconformityMaingrade2Job |

> Demo **cron** 须与下方「执行顺序」一致；列表 id 不要求和执行顺序相同。新增或改 cron 时必须先读第 4.1 节。

## 2. 表结构

脚本：`sql/rrs_scheduled_task_schema.sql` + `rrs_scheduled_task_demo_data.sql`（已注册 ScriptTool）。

| 表 | 说明 |
|---|---|
| `sys_scheduled_task` | 任务配置（cron、schedule_enabled、param_json、最近执行摘要） |
| `sys_scheduled_task_evt` | 配置变更审计 |
| `sys_scheduled_task_run_log` | 执行历史（含 `detail_log` 过程日志） |

## 3. 接口

全部 `POST`，返回 `ApiResponse`。

| 路径 | 入参 | 出参 | 说明 |
|---|---|---|---|
| `/queryTaskPage` | keyword, scheduleEnabled, pageIndex, pageSize | `PageResult<ScheduledTaskInfoDto>` | 分页查询任务配置（名称/编码模糊，调度启停筛选） |
| `/queryTaskList` | `{}` | `List<ScheduledTaskInfoDto>` | 全量任务清单（调度重挂载等内部使用） |
| `/queryTask` | taskCode | `ScheduledTaskInfoDto` | 单任务 |
| `/addTask` | taskCode, taskName, description, cron, scheduleEnabled, paramJson, operatorId/Name | `ScheduledTaskInfoDto` | 新增配置 |
| `/editTask` | 同上（taskCode 不可改） | `ScheduledTaskInfoDto` | 修改配置并重挂载 |
| `/deleteTask` | taskCode, operatorId/Name | `ScheduledTaskInfoDto` | 逻辑删除并取消调度 |
| `/executeTask` | taskCode, operatorId/Name | `ScheduledTaskResult` | 立即执行（须有业务实现） |
| `/queryRunLogPage` | taskCode?, pageIndex, pageSize | `PageResult<ScheduledTaskRunLogDto>` | 执行历史 |

## 4. 调度机制

- 启动：仅按库表 `schedule_enabled=1` 挂载 `DynamicTaskScheduler`（**不再**用代码种子写库）。  
- 页面新增/改 cron/启停 → 写库 + 审计 + `schedule`/`cancel`。  
- 无业务实现也可先建配置；执行时若无 `RrsScheduledTask` 则报错提示先开发实现。  
- 同 taskCode 执行串行（同一编码不会重叠跑）。  
- **不同 taskCode 之间没有全局锁、没有编排器**：先后完全靠 cron。有业务依赖的任务必须把 cron 配成先后执行，禁止设成同一分钟。

### 4.1 执行顺序（新增/改 cron 必读）

调度器**不会**保证「A 跑完再跑 B」。若任务 B 依赖任务 A 改过的池状态，必须满足：`A 的 cron < B 的 cron`，并留足 A 跑完的时间（Demo 按整点错开）。

当前 8 个任务的**必须顺序**与 Demo cron：

| 顺序 | 时刻 | 任务 | 为何必须在这 |
|------|------|------|----------------|
| 1 | 02:00 | `security_expired_auto_out` | 先清到期债/股，避免后面入债再扫到已到期券 |
| 2 | 03:00 | `crmw_expired_auto_out` | 先清到期 CRMW（独立表，与债股到期同属「先清理」） |
| 3 | 04:00 | `company_outer_rating_not_aa_minus_auto_out` | **先改主体**：外评升高的主体先出池，并顺带出同池债 |
| 4 | 05:00 | `company_outer_rating_aa_minus_auto_in` | 再入外评降低的主体（与上一条评级名单互斥，但仍须先出后入） |
| 5 | 06:00 | `company_same_pool_bond_auto_in` | 主体池已稳定后，才把「已在本池主体」的债同池入库 |
| 6 | 07:00 | `company_inpool_bond_auto_in` | 同上，Job 版入债（可跨池）；须在外评入主体之后，当天才能带上新入池主体的债 |
| 7 | 08:00 | `company_not_in_pool_bond_auto_out` | **最后**清「主体已不在池」的债，覆盖刚被外评出池的主体 |
| 8 | 09:00 | `bond_grade_inconformity_alert` | 池状态已稳定后再扫分级库不符，生成待办；不改池 |

违反顺序的典型后果：

- 入债早于外评入主体 → 当天新入池主体的债要等到次日。  
- 入债早于外评出主体 → 即将出池的主体还可能再入一轮债。  
- 主体不在池出债早于外评出主体 → 刚出池主体旗下债当天清不干净。

页面「立即执行」**不校验**上述顺序，运维手动跑时须自行按表执行。

### 4.2 以后每加一个定时任务必须做的检查

新增实现或新增 `sys_scheduled_task` 行时，**不得只配一个好看的整点**，必须书面回答：

1. 是否读写 `ip_pool_status` / `ip_pool_status_crmw`（或会改变「谁在池里」）？  
2. 是否依赖「主体已在池 / 已不在池 / 债已到期」等**前序任务的结果**？  
3. 是否会被后序任务覆盖或互相打架（先入后出、先出后入）？  
4. cron 是否严格晚于所有前序、早于所有后序，且不与任一已启用任务同一分钟？  
5. Demo `rrs_scheduled_task_demo_data.sql` 的 **cron**、以及本节 4.1 表格是否已按依赖更新？（列表主键 id 不必重排）

有依赖则插入到上表对应位置并重排 Demo **cron**；确认无依赖才可与现有任务并行（仍建议错开分钟，避免打满库）。

## 5. 新增任务步骤

1. **先评估执行顺序**（第 4.1 / 4.2 节），再定 cron，写入 Demo 与本节表格。  
2. **前端「新增」**：填写 task_code、名称、说明、cron、扩展参数。  
3. **开发**：新建 `XxxService implements RrsScheduledTask`，`getTaskCode()` 与库表一致，`execute()` 实现业务（含按需解析 `param_json`），覆盖 `getParamHelp()` 说明参数。  
4. 部署后可「执行」；列表 `codeRegistered` 表示是否已绑定实现；`paramHelp` 供配置页展示。

## 6. 前端

- 菜单位置：RRS配置 → 定时任务管理  
- 能力：新增 / 删除 / 分页列表 / 启停 / 配置 / 立即执行 / 执行历史  
- 列表查询走 `queryTaskPage`（`keyword` / `scheduleEnabled` / `pageIndex` / `pageSize`），默认 `pageSize=20`，可选 10/20/50/100；查询/重置回到第 1 页  
- 执行历史弹窗走 `queryRunLogPage`，与列表分页独立  
- 配置页「本任务参数说明」来自后端 `getParamHelp()`，前端不按任务写死格式  
- 「立即执行」**不校验**第 4.1 节任务间顺序，须人工按表操作  

## 7. 代码索引

- 编排：`ScheduledTaskService`、`DynamicTaskScheduler`、`RrsScheduledTask`  
- 业务：`AutoAdjustService`、`CrmwExpiredAutoOutService`、`CompanyOuterRatingNotAaMinusAutoOutService`、`CompanyOuterRatingAaMinusAutoInService`、`CompanySamePoolBondAutoInService`、`CompanyNewBondAutoInService`、`CompanyNotInPoolBondAutoOutService`  
- Mapper：`ScheduledTaskMapper` / `.xml`、`AutoAdjustMapper`  
- Controller：`ScheduledTaskController`  
- SQL：`rrs_scheduled_task_schema.sql`、`rrs_scheduled_task_demo_data.sql`  

以下业务摘要按 **Demo cron 执行顺序** 编排（与第 4.1 节一致）。

### 7.0 `security_expired_auto_out`（02:00）

对应老系统 `AdjustRuleByExpired` + `AdjustPoolByRule.doAdjustOutByRule`：

1. 到期宽限：`maturity_date` **早于昨天**（`enddate < trunc(sysdate-1)` / T-2），到期当天与次日仍不出。  
2. 大类对齐老 `ptype=4000 or 2000`：仅债、股；排除 `crmw`（走 `crmw_expired_auto_out`）。  
3. 调出限制：目标池若配置 `out_restrict`（老关系类型 12），证券当前已在任一限制池中则跳过。  
4. 仅 `deletePoolStatusSoft` 实际删到记录才写 `ip_adjust_log` 并计入影响条数。  

### 7.1 `crmw_expired_auto_out`（03:00）

对应老 IP_RULE `AdjustRuleCrmwDueOutPool`：

1. 扫 `ip_pool_status_crmw` 已生效组合，凭证 `crmw_scode` 主数据到期日早于昨天（T-2）。  
2. 调出限制池阻断；仅软删成功才写日志。  
3. 不走审批。  

### 7.2 `company_outer_rating_not_aa_minus_auto_out`（04:00）

对应老系统 `AdjustRuleOutAA`（自动导出外部评级不是 AA- 及以下的主体）：

1. 扩展参数 `poolIds` 指定目标池（替代老系统「池上勾选自动调出规则」）。  
2. 数据源同 7.3：有效外评按 12 个月拆分。  
3. 评级**不在**入池白名单内（如 `AA` / `AA+` / `AAA` 等，与 7.3 互为补集）。  
4. `limitPoolIds` 对齐老 `LIMITPOOLID_XYJJ`：主体已在这些池则不出。省略则默认全部 `pool_type=forbidden` 的池；显式 `[]` 关闭拦截。目标池本身在名单内时该池不会自动出任何人。  
5. 主体出池成功后，顺带调出同池旗下 `bond` 大类（对齐老 `findSecurityByCompanyCode(..., 4000)`）。  
6. 调出限制池（关系 12 / `out_restrict`）对主体与旗下债分别拦截。  

### 7.3 `company_outer_rating_aa_minus_auto_in`（05:00）

对应老系统 `AdjustRuleInAA`（自动入外部评级 AA- 及以下的主体）：

1. 扩展参数 `poolIds` 指定目标池（替代老系统「池上勾选自动调入规则」）。  
2. 数据源：`ais_inv_ods.wind_cbondissuerrating`。有效外评按 12 个月拆分：近 12 个月取档位最高，12 个月前取日期最新再比档位；两段候选再取日期更近的一条。  
3. 评级命中列表：`AA-` / `A±` / `BBB…` / `BB…` / `B…` / `CCC/CC/C`（**不含** `AA`/`AA+`/`AAA`）。  
4. 尚未在目标池 `audit_status=20` 的主体 → 写自动调整入池日志 + `ip_pool_status`（`security_type=company`）。  
5. 调入限制池（关系 11 / `in_restrict`）阻断。  

> 本轮不实现老系统 `CEVALUITCODES`（评级机构白名单）；未配置时老系统整轮不跑，新系统用 Wind 全量外评。

### 7.4 `company_same_pool_bond_auto_in`（06:00）

对应老 IP_RULE type=0「主体下债券自动入库」：

1. 扩展参数仅 `poolIds`：每个 ID 既是主体所在池也是债写入池（无 mappings）。  
2. 主体 `category_type=company` 且 `audit_status=20` 在池 → 旗下 `bond` 大类未到期（`maturity_date` 为空或 **≥ 今天**）、未在本池 → 自动入。  
3. 市场：池 `market_codes` 为空/`[]` 不限制；否则债须命中 SSE/SZSE/CIBM/BSE/OTHER/JWCW。  
4. **不**排除临时代码已更新，**不**排除 ABS。  
5. 调入限制池（关系 11）阻断。  

### 7.5 `company_inpool_bond_auto_in`（07:00）

对应老 `AutoAdjustInNewBondToLimitPoolJob`：

1. `poolIds` 同池，或 `mappings`（`companyInPoolId` → `bondTargetPoolId`）跨池。  
2. 主体已在主体池 → 旗下债未到期（**不含**当天，`maturity_date > 今天`）、未在**目标池** → 自动入。  
3. 排除临时代码已更新、ABS、CRMW；**不看**限制池。  
4. 「已在池」看写入目标池（老 Job 误写成主体池，跨池会错；新系统按目标池，属有意修正）。  

两任务对照：

| 任务 | 对应老系统 | 池关系 | 到期当天 | 主要过滤 |
|------|------------|--------|----------|----------|
| `company_same_pool_bond_auto_in` | IP_RULE type=0 | **仅同池** `poolIds` | **可入** | `market_codes`；不排除临时代码 / ABS；看限制池 |
| `company_inpool_bond_auto_in` | `AutoAdjustInNewBondToLimitPoolJob` | 同池或跨池 `mappings` | **不入** | 排除临时代码 / ABS / CRMW；不看限制池 |

### 7.6 `company_not_in_pool_bond_auto_out`（08:00）

对应老 `AutoAdjustInLimitPoolToNewBondJob`（Quartz 默认不启用）：

1. 债已在 `bondPoolId`，发行主体不在 `companyPoolId` → 从债券池调出。  
2. 排除 ABS / CRMW（老 Job 只排 CRMW；新系统排 ABS 是避免绕过禁投 ABS 独立链路）。  
3. 独立 Job 口径，**不看**调入/调出限制池。  
4. Demo 默认 `schedule_enabled=0`。  

> **禁投人工链路 `syncCompanyBonds` 仍含 ABS/crmw**，与 Job 版定时任务排除 ABS 不是同一条链路。本次不改，待业务确认。
