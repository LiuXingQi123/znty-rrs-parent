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
| `auto_out_expired` | 到期出池 | `0 0 2 * * ?` | `{"poolIds":[15]}`（扫描这些池的到期证券并调出） |
| `company_new_bond_auto_in` | 主体下新债自动入池 | `0 0 3 * * ?` | `{"poolIds":[15]}`（可多个；不同池用 mappings） |

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

- Service：`ScheduledTaskService`、`AutoAdjustService`、`CompanyNewBondAutoInService`  
- 调度：`DynamicTaskScheduler`、`RrsScheduledTask`  
- Mapper：`ScheduledTaskMapper` / `.xml`  
- Controller：`ScheduledTaskController`  
- SQL：`rrs_scheduled_task_schema.sql`、`rrs_scheduled_task_demo_data.sql`
