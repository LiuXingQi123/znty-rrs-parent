# 29 定时任务管理

> 前端页面：`scheduled_task.html`  
> 后端前缀：`/api/v1/scheduledTask`  
> 角色定位：运维 / 管理员在「RRS配置」下可视化查看、启停、改 cron、手动执行定时任务，并查看执行历史。

## 1. 业务目标

将平台定时任务从 yml 写死改为：

1. **代码实现** `RrsScheduledTask`：业务逻辑 + 默认元信息  
2. **库表配置** `sys_scheduled_task`：cron / 启停 / 扩展参数（页面可改，即时重挂载）  
3. **执行历史** `sys_scheduled_task_run_log`：手动 / 定时触发留痕  

当前内置任务：

| taskCode | 名称 | 默认 cron | 扩展参数 |
|---|---|---|---|
| `auto_out_expired` | 到期出池 | `0 0 2 * * ?` | 无 |
| `company_new_bond_auto_in` | 主体下新债自动入池 | `0 0 3 * * ?` | 池映射，如 `15-15` |

## 2. 表结构

脚本：`sql/rrs_scheduled_task_schema.sql` + `rrs_scheduled_task_demo_data.sql`（已注册 ScriptTool）。

| 表 | 说明 |
|---|---|
| `sys_scheduled_task` | 任务配置（cron、schedule_enabled、param_json、最近执行摘要） |
| `sys_scheduled_task_evt` | 配置变更审计 |
| `sys_scheduled_task_run_log` | 执行历史 |

## 3. 接口

全部 `POST`，返回 `ApiResponse`。

| 路径 | 入参 | 出参 | 说明 |
|---|---|---|---|
| `/queryTaskList` | `{}` | `List<ScheduledTaskInfoDto>` | 任务清单 |
| `/queryTask` | taskCode | `ScheduledTaskInfoDto` | 单任务 |
| `/editTaskConfig` | taskCode, cronExpression, scheduleEnabled, paramJson, operatorId/Name | `ScheduledTaskInfoDto` | 保存配置并重挂载 |
| `/executeTask` | taskCode, operatorId/Name | `ScheduledTaskResult` | 立即执行 |
| `/executeTasks` | taskCodes[], operatorId/Name | `List<ScheduledTaskResult>` | 批量顺序执行 |
| `/queryRunLogPage` | taskCode?, pageIndex, pageSize | `PageResult<ScheduledTaskRunLogDto>` | 执行历史 |

## 4. 调度机制

- 启动：`ScheduledTaskService.initAfterStartup` 将代码任务种子写入库表（仅补缺失），再按 `schedule_enabled=1` 挂载 `DynamicTaskScheduler`。  
- 页面改 cron/启停 → `editTaskConfig` → 写库 + 审计 + `schedule`/`cancel`。  
- 关闭调度仍可 `executeTask` 手动跑。  
- 同 taskCode 执行串行，防止并发双跑。

## 5. 新增任务步骤（开发）

1. 新建 `XxxService implements RrsScheduledTask`（`getTaskCode` 稳定、`execute` 实现业务）。  
2. 可选：在 demo SQL 补一行配置。  
3. 无需改前端菜单（列表自动聚合）；扩展参数含义在页面 tip 或文档中说明。  
4. 部署后重启或等下次启动种子同步；也可页面直接配置。

## 6. 前端

- 菜单位置：RRS配置 → 定时任务管理  
- 能力：列表 / 启停开关 / 配置弹窗（cron+扩展参数）/ 立即执行 / 执行历史分页  

## 7. 代码索引

- Service：`ScheduledTaskService`、`AutoAdjustService`、`CompanyNewBondAutoInService`  
- 调度：`DynamicTaskScheduler`、`RrsScheduledTask`  
- Mapper：`ScheduledTaskMapper` / `.xml`  
- Controller：`ScheduledTaskController`  
- SQL：`rrs_scheduled_task_schema.sql`、`rrs_scheduled_task_demo_data.sql`
