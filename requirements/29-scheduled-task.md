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
| `security_expired_auto_out` | 到期证券自动出池 | `0 0 2 * * ?` | `{"poolIds":[15]}`：池内已生效且到期日早于当天 → 自动调出 |
| `company_inpool_bond_auto_in` | 在池主体旗下债券自动入池 | `0 0 3 * * ?` | `{"poolIds":[15]}` 或 `mappings`：主体在池 → 旗下未到期未在目标池的债自动入池 |
| `company_outer_rating_aa_minus_auto_in` | 外评AA-及以下主体自动入池 | `0 0 4 * * ?` | `{"poolIds":[15]}`：最新外评在 AA-/A/BBB… 列表内且未在目标池的主体 → 自动入池 |
| `company_outer_rating_not_aa_minus_auto_out` | 外评非AA-及以下主体自动出池 | `0 0 5 * * ?` | `{"poolIds":[15]}`：最新外评不在 AA-/A/BBB… 列表内（如 AA/AA+/AAA）且已在目标池 → 自动出池 |
| `company_same_pool_bond_auto_in` | 主体下债券自动入库 | `0 0 6 * * ?` | `{"poolIds":[15]}`：主体已在本池 → 旗下债未到期未在本池 → 同池自动入（IP_RULE 版） |

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
| `/queryTaskList` | `{}` | `List<ScheduledTaskInfoDto>` | 任务清单（仅库表） |
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
- 同 taskCode 执行串行。

## 5. 新增任务步骤

1. **前端「新增」**：填写 task_code、名称、说明、cron、扩展参数。  
2. **开发**：新建 `XxxService implements RrsScheduledTask`，`getTaskCode()` 与库表一致，`execute()` 实现业务（含按需解析 `param_json`），覆盖 `getParamHelp()` 说明参数。  
3. 部署后可「执行」；列表 `codeRegistered` 表示是否已绑定实现；`paramHelp` 供配置页展示。

## 6. 前端

- 菜单位置：RRS配置 → 定时任务管理  
- 能力：新增 / 删除 / 列表 / 启停 / 配置 / 立即执行 / 执行历史  

## 7. 代码索引

- Service：`ScheduledTaskService`、`AutoAdjustService`、`CompanyNewBondAutoInService`、`CompanySamePoolBondAutoInService`、`CompanyOuterRatingAaMinusAutoInService`、`CompanyOuterRatingNotAaMinusAutoOutService`  
- 调度：`DynamicTaskScheduler`、`RrsScheduledTask`  
- Mapper：`ScheduledTaskMapper` / `.xml`、`AutoAdjustMapper`（含外评低/高主体、同池/跨池主体债查询）  
- Controller：`ScheduledTaskController`  
- SQL：`rrs_scheduled_task_schema.sql`、`rrs_scheduled_task_demo_data.sql`

### 7.1 `company_outer_rating_aa_minus_auto_in` 业务摘要

对应老系统 `AdjustRuleInAA`（自动入外部评级 AA- 及以下的主体）：

1. 扩展参数 `poolIds` 指定目标池（替代老系统「池上勾选自动调入规则」）。  
2. 数据源：`ais_inv_ods.wind_cbondissuerrating`，每主体最新一条外评。  
3. 评级命中列表：`AA-` / `A±` / `BBB…` / `BB…` / `B…` / `CCC/CC/C`（**不含** `AA`/`AA+`/`AAA`）。  
4. 尚未在目标池 `audit_status=20` 的主体 → 写自动调整入池日志 + `ip_pool_status`（`security_type=company`）。

### 7.2 `company_outer_rating_not_aa_minus_auto_out` 业务摘要

对应老系统 `AdjustRuleOutAA`（自动导出外部评级不是 AA- 及以下的主体）：

1. 扩展参数 `poolIds` 指定目标池（替代老系统「池上勾选自动调出规则」）。  
2. 数据源同入池：`ais_inv_ods.wind_cbondissuerrating` 每主体最新一条外评。  
3. 评级**不在**入池白名单内（如 `AA` / `AA+` / `AAA` 等，与 7.1 互为补集）。  
4. 当前已在目标池 `audit_status=20` 且 `security_type=company` → 写自动调整出池日志 + 软删 `ip_pool_status`。  
5. 仅主体维度，不同步旗下债券（与入池任务口径一致；老系统会附带旗下债，新系统刻意简化）。

### 7.3 主体旗下债券自动入池：两任务对照

| 任务 | 对应老系统 | 池关系 | 主要过滤 |
|------|------------|--------|----------|
| `company_inpool_bond_auto_in` | `AutoAdjustInNewBondToLimitPoolJob` | 同池 `poolIds` 或跨池 `mappings` | 排除临时代码已更新 |
| `company_same_pool_bond_auto_in` | IP_RULE type=0「主体下债券自动入库」 | **仅同池** `poolIds` | 池 `market_codes`；**不**排除临时代码 |

### 7.4 `company_same_pool_bond_auto_in` 业务摘要

1. 扩展参数仅 `poolIds`：每个 ID 既是主体所在池也是债写入池。  
2. 主体 `category_type=company` 且 `audit_status=20` 在池 → 旗下 `bond` 大类未到期、未在本池 → 自动入。  
3. 市场：池 `market_codes` 为空/`[]` 不限制；否则债须命中 SSE/SZSE/CIBM/BSE/OTHER/JWCW（对齐老系统 `ip_investmarket`）。  
4. 免审直通，`adjust_type=自动调整`。
