# 29 定时任务管理

## 恒生池 Excel 导出

- `hs_pool_full_excel_export`：对应老系统 `ExportStatusToHsJob`，导出指定叶子池当前已生效的非主体证券及 CRMW；普通证券排除已到期数据，CRMW 按老系统定时任务口径不校验到期日；未指定 `poolIds` 时导出全部叶子池。
- `hs_pool_full_including_expired_excel_export`：对应老系统 `ExportStatusToHsJob2`，与上一任务使用相同池范围和 Excel 结构，但普通证券包含已到期数据；CRMW 同样不校验到期日。
- `hs_pool_increment_excel_export`：首次以 `initialStartTime` 为下界，后续以上次成功执行开始时间为下界，导出窗口内审批通过的调入和调出事件，不要求证券当前仍在池。老系统通过调库日志 `exportflag` 控制增量，新系统明确使用任务成功时间水位线，不更新业务调库日志。
- 三个任务均使用五列表头：证券名称、证券代码、操作类型、市场名称、备注；调出操作类型写“删除”，备注留空。市场名称列按恒生市场编码输出，当前字段映射为沪市 `1`、深市 `2`、银行间 `4`、北交所 `6`、其他市场 `99999`，完整编码集中维护在 `HsMarketCode`；证券代码去掉首个 `.` 及其后的市场后缀，按实际在池记录或日志中的代码及所属市场输出，不再次展开同券其他市场代码。
- `hs_pool_name` 为空时使用投资池完整名称，支持竖线拆分多个 Sheet；名称超过 31 位截断，同名 Sheet 合并追加并写过程警告日志。老系统页面手动导出会合并同名 Sheet，但老系统定时任务未做重名判断、直接创建时可能抛异常；新系统采用页面导出的容错方式。
- 增量任务已预留交易日日期列表入口；当前列表固定为空，表示尚未启用过滤，因此交易日和非交易日均正常执行。交易日数据源、查询异常策略和非交易日强制执行配置后续确认。
- `param_json` 支持 `poolIds`、`exportEmptyPool`、`outputDir`；增量首次执行还必须配置 `initialStartTime`。文件写入 `输出根目录/yyyyMMdd`，并复制到同日 `bak` 目录；FTP 暂未接入，代码保留 TODO。

> 前端页面：`scheduled_task.html`  
> 后端前缀：`/api/v1/scheduledTask`  
> 角色定位：运维 / 管理员在「RRS配置」下可视化查看、启停、改 cron、手动执行定时任务，并查看执行历史。

## 1. 业务目标

**配置与实现分离**（先建库表配置，再写业务实现）：

1. **库表配置** `sys_scheduled_task`：名称 / 说明 / cron / 启停 / **扩展参数（通用字段 param_json）** — 页面新增、编辑、删除  
2. **代码实现** `RrsScheduledTask`：`getTaskCode()` + `execute()` + 可选 `getParamHelp()`，与库表 `task_code` 绑定；任务编码须字母开头，仅含字母、数字、下划线，长度 2～50 位
3. **执行历史** `sys_scheduled_task_run_log`：手动 / 定时触发留痕  

外部评级相关任务与人工调库评级下调检查共用 `dict_external_rating_agency`：只读取 `is_deleted=0` 且机构编码非空的数据。业务先查询有效 `b_info_creditratingagency` 列表，再作为参数传入 Wind 评级查询；未配置有效机构时，自动调库与黑名单校验阻断，评级下调检查按 fail-open 视为未下调。

定时任务写入 `ip_adjust_log` / `ip_adjust_log_crmw` 时只赋值 `adjust_reason`，`adjust_advice` 保持 `NULL`；人工调库及人工临时代码转正不受此规则影响。调整原因在基础原因后的中文括号中展示本次判断的重要依据：到期任务展示实际到期日与出池口径；主体旗下债券任务展示发行主体及主体所在/未在池；池关系联动出池展示调入池和被调出池；临时代码替换定时任务展示临时代码与正式代码。示例：`证券到期自动调出（到期日：2026-09-01；出池口径：到期日早于昨日）`、`主体下债券自动入库（发行主体：某集团/C001；主体所在池：债券禁止库）`。

自动调库批次号按“实际主调整对象 + 主目标池 + 调整方向”生成。同一主体主调整与其同步的旗下债券、关联/互斥项共用 `COMP` 批次；只有债券/普通证券调整时按证券代码生成 `BOND` 批次，其关联/互斥项继承主日志批次；CRMW 按“凭证代码 + 标的证券代码”组合生成 `CRMW` 批次。不同主对象或不同主目标池不得共用批次。历史 `AUTO` 前缀记录不迁移；新任务过程日志不输出批次号，但 `adjust_batch_no` 继续写入业务调库日志和池状态。

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
| `bond_temp_code_replace` | 债券临时代码替换 | `0 40 4,8,9,13,18,22 * * ?` | 每天 04:40、08:40、09:40、13:40、18:40、22:40 执行。无需参数；扫描 `rrs_temp_security_code.status=temporary` 且已由外部数据补齐 `security_code` 的记录，复用临时代码人工转正的完整分叉逻辑，操作来源记为 `job` |
| `bond_security_type_change` | 债券类型变更 | `0 0 22 * * ?` | 每天 22:00 执行。无需参数；以 `rrs_securityinfo.security_type` 为准，同步普通池、CRMW 池标的证券及当前关联调库日志中的债券类型 |
| `security_expired_auto_out` | 到期证券自动出池 | `0 0 23 * * ?` | 每天 23:00 执行。`poolIds` 可选，默认 `[15]`（15（债券禁止库）），与投资池关系配置绑定本任务的池取并集；扫描已生效债券、股票，到期日早于昨天（T-2）时自动调出，命中调出限制池则跳过 |
| `crmw_expired_auto_out` | CRMW到期自动出池 | `0 5 23 * * ?` | 每天 23:05 执行。`poolIds` 可选，默认 `[18]`（18（CRMW库）），与关系配置绑定池取并集；扫描已生效 CRMW 组合，凭证到期日早于昨天（T-2）时自动调出，命中调出限制池则跳过 |
| `company_outer_rating_not_aa_minus_auto_out` | 外评非AA-及以下主体自动出池 | `0 10 23 * * ?` | 每天 23:10 执行。`poolIds` 可选，默认 `[17]`（17（黑名单质押库）），与关系配置绑定池取并集；`limitPoolIds` Demo 为 `[]`。已在目标池、近一年存在认可外评，且不满足（一）禁止库 15 /（二）近一年孰低 AA-及以下 /（三）重点观察 23 时出主体；无认可外评不自动出池。成功后同批调出未退市且符合范围的旗下债 |
| `company_outer_rating_aa_minus_auto_in` | 外评AA-及以下主体自动入池 | `0 15 23 * * ?` | 每天 23:15 执行。`poolIds` 可选，默认 `[17]`（17（黑名单质押库）），与关系配置绑定池取并集。尚未在目标池且满足（一）当前在禁止库 15、（二）近一年认可外评孰低为 AA-及以下、（三）当前在重点观察 23 之一则自动入池，并同批调入未退市且符合范围的旗下债。认可机构由 `dict_external_rating_agency` 控制；主体命中调入限制池则跳过，债券命中则阻断主体入池并整批回滚 |
| `company_same_pool_bond_auto_in` | 主体下债券自动入库 | `0 20 23 * * ?` | 每天 23:20 执行。`poolIds` 可选，Demo 配置 `[15,16,17,23]`，自动并入所有已有生效主体的普通池；主体已在该池时，旗下未退市、未到期（含当天）且未在同一池的债券自动入池；扫描17时再次校验主体三条件；范围含普通债、ABS、CRMW |
| `company_inpool_bond_auto_in` | 在池主体旗下债券自动入池 | `0 */10 * * * ?` | 每 10 分钟执行一次。`poolIds` / `poolId` 指定同池，Demo 为 `[15,16,17,23]`，自动并入所有已有主体的普通池；`mappings` 指定主体池至债券目标池的跨池映射；排除退市债和已更新临时代码，普通债、ABS、CRMW 均参与；入池后按互斥/反向限制关系自动调出原池并记日志 |
| `company_not_in_pool_bond_auto_out` | 主体不在池债券自动出池(默认关闭) | `0 0 0 * * ?` | 每天 00:00 执行，建议按需手动执行。`poolIds` 指定同池，默认 `[15]`（15（债券禁止库））；`mappings` 指定债券池与主体池映射；未退市债在债券池、主体不在主体池时出债，普通债、ABS、CRMW 均参与 |
| `bond_grade_inconformity_alert` | 不符合主体债入库规则提醒 | `0 0 1 * * ?` | 每天 01:00 执行。无需参数；扫描不符合当前主体债入库规则的在池信用债，生成待办供人工处理，不自动出池；摘要区分**本轮命中** / **本轮失效** / **仍待处理** |
| `hs_pool_full_excel_export` | 恒生池全量数据导出（不含已到期） | `0 10 1 * * ?` | 每天 01:10 执行；`poolIds` 可限制叶子池，省略时导出全部叶子池；默认生成空池 Sheet；导出当前已生效的非主体证券及 CRMW，普通证券排除已到期数据，CRMW 不校验到期日 |
| `hs_pool_full_including_expired_excel_export` | 恒生池全量数据导出（含已到期） | `0 20 1 * * ?` | 每天 01:20 执行；参数与不含已到期全量任务一致；普通证券不校验到期日，包含仍在池的已到期数据，CRMW 同样不校验到期日 |
| `company_observer_pool_sync` | 主体观察名单同步 | `0 0 6 * * ?` | 每天06:00执行，调度默认关闭；默认参数 `{"observePoolId":16,"keyObservePoolId":23}` 分别对应类型2、3，后端只管理配置池；按源池in_linked/out_linked配置即时联动主体及旗下债券（例如23↔17）；先出后入、补齐差异、整轮回滚 |
| `pledge_blacklist_daily_increment_reminder` | 质押黑名单库每日增量提醒 | `0 0 9 * * ?` | 每天 09:00 执行；`poolIds` 默认 `[17]`；按上次成功执行时间水位扫描审批通过的债券、主体调入调出流水。当前仅生成候选与过程日志，通知通道待接入 |
| `bond_issuer_not_in_company_pool_reminder` | 主体库内债券主体不在池债邮件提醒 | `0 0 9 * * ?` | 每天 09:00 执行；`poolIds` 支持同池，`mappings` 支持债券池与主体池跨池映射；扫描发行主体不在对应主体池的在池债券，排除 CRMW。当前仅生成候选与过程日志，通知通道待接入 |
| `hs_pool_increment_excel_export` | 恒生池增量数据导出 | `0 */6 * * * ?` | 每 6 分钟执行一次；首次从 `initialStartTime` 开始，后续从上次成功执行开始时间继续；导出窗口内审批通过的调入和调出，调出标记“删除”；交易日过滤暂未启用 |
| `wind_code_sync` | Wind代码变更同步 | `0 2/10 * * * ?` | `{"scanDays":1}`，scanDays 为正整数，省略默认 1 天。每小时第 2/12/22/32/42/52 分钟执行；按唯一 SEC_ID 原地更名主档，按四类来源准备登记，复用单码业务转换；修改普通池、CRMW及在途申请，须与其他调库错开 |

> Demo 行与 id 按上表顺序排列，且全部 `schedule_enabled=0`（默认关闭）。Demo **cron** 须与下方「执行顺序」一致；新增或改 cron 时必须先读第 4.1 节。三项恒生池 Excel 导出任务不改池状态；Wind 换码会修改池状态，Demo 与其他调库错开，已有数据库配置须在页面自行调整，不由程序自动覆盖。

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

当前 13 个需要池状态先后编排的任务，其**必须顺序**与 Demo cron：

| 顺序 | 时刻 | 任务 | 为何必须在这 |
|------|------|------|----------------|
| 1 | 04:40、08:40、09:40、13:40、18:40、22:40 | `bond_temp_code_replace` | 多时点把外部数据已匹配的临时代码转正式；会改在途日志及普通/CRMW 池状态；22:40 批次直接使用正式主数据类型 |
| 2 | 22:00 | `bond_security_type_change` | 在 18:40 临时代码替换后，以最新主数据统一当前在池债券类型；早于 22:40 替换批次 |
| 3 | 23:00 | `security_expired_auto_out` | 先清到期债/股，避免后面入债再扫到已到期券 |
| 4 | 23:05 | `crmw_expired_auto_out` | 先清到期 CRMW（独立表，与债股到期同属「先清理」） |
| 5 | 23:10 | `company_outer_rating_not_aa_minus_auto_out` | **先改主体**：不再满足质押券黑名单（一）（二）（三）的主体先出 17，并顺带出同池债 |
| 6 | 23:15 | `company_outer_rating_aa_minus_auto_in` | 再把新满足（一）（二）（三）之一的主体入 17（须先出后入） |
| 7 | 23:20 | `company_same_pool_bond_auto_in` | 主体池已稳定后，才把「已在本池主体」的债同池入库 |
| 8 | 每 10 分钟 | `company_inpool_bond_auto_in` | Job 版入债（可跨池）；高频扫描在池主体旗下新债 |
| 9 | 次日 00:00 | `company_not_in_pool_bond_auto_out` | **最后**清「主体已不在池」的债；Demo 默认关闭，建议按需手动执行 |
| 10 | 次日 01:00 | `bond_grade_inconformity_alert` | 池状态已稳定后再扫分级库不符，生成待办；不改池 |
| 11 | 次日 06:00 | `company_observer_pool_sync` | 按用户指定时间扫描名单及九类池关系，主体和旗下债券即时同步源池/联动池，早于09:00提醒；默认关闭 |
| 12 | 次日 09:00 | `pledge_blacklist_daily_increment_reminder` | 依赖前序调库流水稳定后再汇总增量；只读，不改池 |
| 13 | 次日 09:00 | `bond_issuer_not_in_company_pool_reminder` | 在所有主体/债券自动调库完成后检查最终不一致；只读，不改池 |

另：`wind_code_sync` 使用 `0 2/10 * * * ?`，会修改主档、普通池、CRMW及在途申请；与每 10 分钟整点和每 5 分钟任务错开，22:42 在临时代码替换 22:40 之后、23:00 到期扫描之前。任务不是编排器，错开不保证前序已完成，启用时仍须按实际耗时留足间隔。`bond_temp_code_replace` 保留独立任务和原 cron；`company_inpool_bond_auto_in` 仍每 10 分钟执行。不同 taskCode 没有全局锁；同一证券更名由主档行锁、登记条件更新和事务保护。

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

本次观察名单同步的依赖评估：①读写ip_pool_status与ip_adjust_log，不写CRMW组合表；②按源池in_linked/out_linked即时联动主体及旗下债，不依赖夜间任务补做17；③夜间外评主体任务仍按自身条件独立复核17，其业务逻辑不修改；④按用户指定每天06:00，位于04:40换码后、09:00提醒前，默认关闭。06:00与每10分钟补债任务同刻触发，不同taskCode无全局锁；本次保留用户指定时间，不调整其他任务cron；⑤Demo与4.1顺序表同步更新，启用时按实际执行耗时核对并发。

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
- 业务：`BondTempCodeReplaceService`、`BondSecurityTypeChangeService`、`PledgeBlacklistDailyIncrementReminderService`、`BondIssuerNotInCompanyPoolReminderService`、`ScheduledReminderDeliveryService`（通知占位）、`AutoAdjustService`、`CrmwExpiredAutoOutService`、`CompanyOuterRatingNotAaMinusAutoOutService`、`CompanyOuterRatingAaMinusAutoInService`、`CompanySamePoolBondAutoInService`、`CompanyNewBondAutoInService`、`CompanyNotInPoolBondAutoOutService`、`ScheduledAdjustBatchNoContext`（自动调库批次分组）、`AutoAdjustPoolScopeHelper`（参数 poolIds ∪ 关系配置绑定池）、`GradeRuleAlertService`、`WindCodeSyncService`（Wind 单码换码）、`HsPoolFullExcelExportService`、`HsPoolFullIncludingExpiredExcelExportService`、`HsPoolIncrementExcelExportService`
- Mapper：`ScheduledTaskMapper` / `.xml`、`BondSecurityMaintenanceMapper` / `.xml`、`BondReminderMapper` / `.xml`、`TempSecurityCodeMapper` / `.xml`、`AutoAdjustMapper`、`GradeRuleAlertMapper` / `.xml`、`HsPoolExcelExportMapper` / `.xml`
- Controller：`ScheduledTaskController`；提醒查询/处理另见 `GradeRuleAlertController`（`/api/v1/gradeRuleAlert`）  
- SQL：`rrs_scheduled_task_schema.sql`、`rrs_scheduled_task_demo_data.sql`、`rrs_grade_rule_alert_schema.sql`、`rrs_grade_rule_alert_demo_data.sql`  

### 过程日志约定

观察名单同步相关代码：`CompanyObserverPoolSyncService`、`CompanyObserverMapper` / `.xml`、`CompanyObserverDto`、`CompanyObserverType`；名单表复用 `ais_inv_analysis_schema.sql` / `ais_inv_analysis_demo_data.sql`。

系统全部定时任务每次执行都必须输出以 `扫描条件：` 开头的过程日志，记录实际目标表、池范围、时间窗口及关键过滤条件；空壳任务须明确当前未扫描业务表。债券维护与提醒任务统一输出以下关键阶段，所有行由 `TaskDetailLog` 自动添加 `yyyy-MM-dd HH:mm:ss` 时间：

1. **任务开始**：明确任务名称，便于从长日志中识别边界。
2. **扫描条件/范围**：记录目标表或池、增量时间窗口及关键过滤条件。
3. **扫描完成**：记录各数据源候选数量。
4. **处理明细**：记录关键业务主键、证券代码、变更前后值、跳过或失败原因；提醒任务记录池映射、候选明细和投递状态。
5. **任务结束**：成功和异常路径均输出结束标识、成功/跳过/失败/候选数量以及毫秒耗时。

执行摘要仍写入 `message` 供历史列表快速查看；上述过程写入 `detail_log`，供「过程日志」弹窗核查。当前通知通道未接入时，结束日志显示 `通知状态=待接入`，不表述为已发送。

自动调库任务的过程日志只输出扫描条件、处理明细和业务汇总，不输出批次号；批次号仅作为业务日志的内部关联键保留。

以下业务摘要按 **Demo cron 执行顺序** 编排（与第 4.1 节一致）。

### 扫描范围（参数 ∪ 关系配置）

自动调入/调出类任务的扫描池 = **扩展参数 `poolIds`（及 mappings） ∪ 投资池关系配置中绑定了本任务的池**。

证券主数据 `maturity_date` 为 `yyyyMMdd` 字符串；所有到期/未到期 SQL 均使用 `DATE_FORMAT(CURDATE(), '%Y%m%d')`（需要宽限日时先 `DATE_SUB`）生成同格式比较值。

1. 投资池维护页「自动调入规则 / 自动调出规则」选择定时任务，写入 `ip_pool_auto_rule`（`rule_id`=`sys_scheduled_task.id`，`task_code`，`rule_type`=auto_in/auto_out）。  
2. 任务执行时按自身 `task_code` + 对应 `rule_type` 查出绑定池，与 `param_json.poolIds` 去重并集。  
3. 映射类任务（`company_inpool_bond_auto_in` / `company_not_in_pool_bond_auto_out`）把绑定池按**同池映射**追加，跨池 `mappings` 仍只来自参数。  
4. 参数 JSON 非法仍失败；参数为空但关系配置有绑定池则只扫配置池；两边都空则本轮失败。  
5. 恒生池导出、评级不符提醒、Wind 代码同步不走此并集。

### 债券临时代码替换 `bond_temp_code_replace`（04:40、08:40、09:40、13:40、18:40、22:40）

对应老系统 `BondTempCodeReplaceJob`，但按新系统现有临时代码表与转正分叉实现：

1. 扫描 `rrs_temp_security_code` 中 `status='temporary'`、`is_deleted=0` 且 `security_code` 已由外部数据补齐的记录；任务本身不猜测正式码映射。
2. 按 `security_code` 查询 `rrs_securityinfo` 正式主数据，且必须非 `security_source='temporary'`、非已退市、非 `company/crmw`；正式名称、市场、类型均以主数据为准。
3. 复用 `TempSecurityCodeService` 人工转正式的同一套分叉：普通池及 CRMW 标的通过正式代码的非空 ISIN 查询全部同券市场代码，整组处理、逐代码记账。流程中主项换码并补关联项及证券快照，沿用原发起人、批次、流程和 `00/11` 状态；已在池临时码退出后，正式组尚未在池则整组调入，已完整在池则不重复调入。当前在池且待调出时，同时迁移当前状态和原调出申请，等待原审批。CRMW 按凭证分别处理，去重精确到凭证、标的与目标池，凭证自身不扩展多市场；使用 CRMW 专属证券快照。普通池和 CRMW 新生成的即时生效出入库记录及池状态，调整人统一为系统（`0/系统`），来源保留 `job`。
   - 写入前完成转换计划校验；业务校验仅检查信用债大库及各级库的独立在途、实际新增入池代码的市场、来源/限制/互斥关系。调入互斥按目标池自身配置逐码补配套调出，不反向推断池关系；流程中沿用原批次等待审批，不提前出池，已在池转换随本次即时生效，原批次已有调出不重复追加。容量、池启停、业务锁定、报告、评级选择和冻结期不重新阻断转正。主数据有效性、相关池整组状态、迁移去重和写入行数等数据保障保留；无关池异常不阻断转换。任一失败回滚本条映射，不生成部分市场结果。无当前在池/在途业务时不生成调库记录，历史终态不改码。
   - 调整原因及失败提示中的投资池显示名称，不展示池 ID；复用已有名称，不额外拼接层级全名，与人工更新口径一致。历史日志不改写。
4. 成功后主表状态置 `updated`、`oprt_source='job'`，禁用临时代码占位主数据，并写 `rrs_temp_security_code_update_log`：固定一条 `replace_table_name='rrs_temp_security_code'` 的旧新码映射，`replace_record_id` 关联登记 ID；无业务引用时也写映射，有业务引用时另存原有逐记录明细。映射及明细 INSERT 影响行数均须为 1，失败整次回滚。
5. 每条映射独立事务；失败记录保持 `temporary` 供下次重试，任务摘要列出成功/失败数。
6. 与 `wind_code_sync` 使用独立任务及扫描入口，共用转换组件；本任务仍使用同券整组模式，Wind 换码仅处理事件指定代码。

### 债券类型变更 `bond_security_type_change`（22:00）

对应老系统 `BondStypeChangeJob`，以新系统当前池状态拆表后的口径实现：

1. `rrs_securityinfo.security_type` 为权威最新类型；只处理新旧类型在 `dict_security_type` 中都属于 `category_type='bond'` 的差异。
2. 扫描 `audit_status='20'` 且未删除的 `ip_pool_status`、`ip_pool_status_crmw` 标的证券字段。
3. 条件更新池状态的 `security_type`，并同步该池状态 `adjust_log_id` 指向的当前 `ip_adjust_log.security_type`；其余历史调库日志保留原始快照。
4. 不修改 CRMW 凭证字段 `crmw_stype`；该字段代表凭证自身类型，不属于老任务的标的债券类型变更范围。
5. 排在 18:40 临时代码替换之后、22:40 替换之前；22:40 新转正式记录在替换时直接使用正式主数据类型。任务仍早于到期和主体调库，避免后序任务读取旧类型。

### 主体观察名单同步 `company_observer_pool_sync`（06:00）

1. 数据来源：查询 `ais_inv_analysis.company_observer_view` 主体观察名单表。`company_id BIGINT` 对应 `t_inv_company.id`，`company_code VARCHAR(100)` 对应 `t_inv_company.wind_code` 及债券 `issuer_code`，`observer_type VARCHAR(100)` 为 `1=空 / 2=观察 / 3=重点观察`。表仅保留 `company_id`、`company_code`、`observer_type`、`isHo`，无主键及创建、修改时间字段；已接入AIS初始化、清空和表统计。
2. 目标池配置：扩展参数 `observePoolId` 对应2观察，`keyObservePoolId` 对应3重点观察，默认 `{"observePoolId":16,"keyObservePoolId":23}`。每轮读取页面保存的参数；省略字段或设为NULL表示不管理该类池，至少配置一类，且两类目标池不能相同。已有空参数任务须填写并保存参数。
3. 参数及主体信息校验：池ID须为Long范围内的正整数，目标池须已启用、未删除且非CRMW组合池；参数为空、字段错误或目标池不可用时任务失败。名单中每个主体须唯一，主体ID、编码须与 `t_inv_company` 一致，名称非空，观察类型有效。
4. 查询范围：合并观察名单主体、配置目标池内主体及在池债券的发行主体，包含人工入池的主体。修改配置后只管理当前配置的目标池，旧目标池不会自动清理；关系池仍按实际配置处理。空名单会使配置目标池内主体及旗下债券出池，应在AIS名单刷新完整后执行。
5. 主体进出池：先调出不符合当前观察类型的主体，再调入符合观察类型的主体。类型变为1、NULL、空字符串或主体从名单移除时，退出当前配置的目标池。主体已在应在池中时，继续检查旗下债券和联动池，补齐缺失记录；同一主体、池及调整方向只处理一次。
6. 旗下债券同步：未退市、到期日为空或不早于今天的普通债、ABS、CRMW，随主体同批进出池。主体已出池但旗下债券仍在池时，也要调出对应债券。债券范围由 `CompanyBondSyncPolicy.currentTypeScope()` 及候选SQL统一确定，写入 `ip_pool_status`。
7. 调入、调出联动池：读取目标池的 `in_linked`、`out_linked` 配置，处理直接配置的联动池。调入时补齐联动池内缺失的主体及旗下债券；调出时，在原目标池实际调出主体或旗下债券后，同批调出联动池中的主体及旗下债券。例如23配置联动17，则调入重点观察名单时联动调入黑名单质押库，调出时也联动调出。
8. 关系池校验：目标池、联动池及互斥产生的调出池，均按下表检查来源池、调入调出限制池、调入调出互斥池、调入调出弹性禁投池。实际需写入的关系池不存在、停用、已删除或为CRMW组合池，或关系校验不通过时，任务失败；弹性禁投池命中只记录警告。
9. 调库记录：主体、旗下债券和联动、互斥调整共用按主体、原目标池及方向生成的 `COMP` 批次。调整类型分别为自动调整、联动调整、互斥调整；以 `0/系统` 直接生效，`audit_status=20`，只写 `adjust_reason`，`adjust_advice=NULL`。参数、主体信息、关系池校验或写入失败时，整轮回滚调库记录和池状态。
10. 过程日志：记录主体调整明细，按主体及实际投资池汇总旗下债券进出池数量和弹性禁投池警告数量。例如：`主体[C10005]旗下债券：投资池=重点观察名单，调入4只，调出0只`。债券仍逐笔写入调库流水；异常保留定位信息，历史过程日志保持原记录。互斥产生的出池按实际目标池统计，不重复计入影响条数。
11. 执行安排：默认每天06:00，cron为 `0 0 6 * * ?`，调度默认关闭。联动在本次任务内完成，夜间外评任务仍按自身规则复核17；06:00会与每10分钟补债任务同刻触发，不同任务之间无全局锁。
12. 实现及验证：`execute()` 记录任务结果和处理异常，`doSync()` 及其业务方法完成名单查询、关系池处理和进出池统计。`CompanyObserverPoolSyncServiceTest` 覆盖参数、主体进出池、旗下债券、重复执行及九类关系；`CompanyObserverPoolSyncPersistenceTest` 使用实际XML、DDL和事务验证自定义目标池、23与17同批进出、债券类型及日期范围、失败整轮回滚。H2测试需JDK11以上，源码目标为Java8。
13. 批量读取：合并本轮主体后，按每批最多1000个主体编码预读符合统一类型及日期口径的全部旗下债券，再按每批最多1000个去重证券编码预读主体、债券在所有普通池的有效状态。预读仅属于当前执行，不跨轮缓存；目标池、联动池、互斥池及限制池的业务循环不再查询债券或池状态。当前所在池在每笔数据库写入成功后立即更新，本轮最初所在池另存快照，保留先出后入、来源依据及逐次关系校验。债券候选仍先处理未在池项，再检查已在池项；调出仅处理当前在池债券。池关系按投资池及关系类型预分组，调入互斥/反向限制结果每池计算一次。日志与池状态仍逐笔写入并校验影响行数，任何失败整轮回滚；其他人工、禁投池和CRMW任务沿用原入口，不改动共享候选查询。
14. 查询次数验证：正常完整执行的同步业务SELECT次数为 `5 + ceil(C/1000) + ceil(S/1000)`，C为合并后的主体数，S为主体及有效债券的去重编码总数，任务框架读取配置另计。过程日志输出实际债券查询批次、池状态查询批次。单元测试覆盖1001个主体、2002只债券仅2次债券查询和4次状态查询；实际Mapper及事务测试在增加主体并启用联动后统计JDBC准备的SELECT，验证查询次数不随目标池、债券及互斥处理逐项增长，同时回归关系校验、幂等和整轮回滚。

| 关系池配置 | 校验及处理规则（适用于主体、旗下债券和联动池） |
|---|---|
| `source` 来源池 | 调入需命中至少一个来源；认可当前在池、本轮最初在池及本主体同批计划调入的来源池；切换时先出后入不丢失原来源依据 |
| `in_restrict` 调入限制池 | 实际调入时当前命中即阻断 |
| `out_restrict` 调出限制池 | 实际调出时当前命中即阻断，互斥产生的配套调出也不可绕过 |
| `in_linked` 调入联动池 | 源池应在池时同批调入配置池的主体及旗下债，并补齐历史遗漏 |
| `out_linked` 调出联动池 | 源池本轮实际退出主体或旗下债后，同批调出配置池主体及旗下债 |
| `in_mutex` 调入互斥池 | 主体和债券调入后退出实际所在互斥池，已在目标池也修复遗漏；保留反向调入限制配套退出；同批互斥池都计划调入时阻断 |
| `out_mutex` 调出互斥池 | 当前仍在该互斥池时不允许调出 |
| `in_soft_restrict` 调入弹性禁投池 | 实际调入时命中记录WARN，警告不阻断 |
| `out_soft_restrict` 调出弹性禁投池 | 实际调出时命中记录WARN，警告不阻断，配套互斥调出同样适用 |

### 7.0 `security_expired_auto_out`（23:00）

对应老系统 `AdjustRuleByExpired` + `AdjustPoolByRule.doAdjustOutByRule`：

1. 到期宽限：`maturity_date` **早于昨天**（`enddate < trunc(sysdate-1)` / T-2），到期当天与次日仍不出。  
2. 大类对齐老 `ptype=4000 or 2000`：仅债、股；排除 `crmw`（走 `crmw_expired_auto_out`）。  
3. 调出限制：目标池若配置 `out_restrict`（老关系类型 12），证券当前已在任一限制池中则跳过。  
4. 仅 `deletePoolStatusSoft` 实际删到记录才写 `ip_adjust_log` 并计入影响条数。  
5. 最近执行结果展示实际出池总数，并按目标池列出出池数量。

### 7.1 `crmw_expired_auto_out`（23:05）

对应老 IP_RULE `AdjustRuleCrmwDueOutPool`：

1. 扫 `ip_pool_status_crmw` 已生效组合，凭证 `crmw_scode` 主数据到期日早于昨天（T-2）。  
2. 调出限制池阻断；仅软删成功才写日志。  
3. 不走审批。  
4. 最近执行结果展示实际出池总数，并按 CRMW 目标池列出出池数量。

### 7.2 `company_outer_rating_not_aa_minus_auto_out`（23:10）

对应老系统 `AdjustRuleOutAA`。现按《债券逆回购交易质押券管理办法》出质押券黑名单：

1. `poolIds`：可选，指定自动出池的目标池；默认 `[17]`，即 17（黑名单质押库）；与投资池关系配置中绑定本任务的池取并集。
2. 出库条件：已在目标池、近一年存在认可外评，且 **（一）（二）（三）均不满足**——不在禁止库 15、不在重点观察 23、近一年认可外评孰低不属于 AA-及以下；无认可外评不自动出池。
3. SQL 查条款（二）反面并排除无认可外评；Java 统一三条件服务再次校验 15/23 和低外评状态。
4. 数据源同 7.3：近一年（日历年）配置表中的有效机构多评级取孰低；一年以前忽略。认可机构由 `dict_external_rating_agency` 控制。
5. 评级临界：`AA-` 及以下不出池；`AA` 及以上在其他条件均不命中时可出池；空评级/近一年无认可外评不出池。
6. `limitPoolIds`：额外拦截池；主体已在这些池则不出。Demo 为 `[]`；省略或 `[]` 均不追加额外拦截（15/23 已由条款排除）。
7. 主体出池成功后，**本任务内自写** `outSamePoolBonds` 同批调出同池旗下未退市、未到期（含当天）的 `bond` 大类（`security_status!='D'`，含普通债、ABS、CRMW），**不复用**人工 `syncCompanyBonds`；调整原因如 `外评非AA-及以下主体自动出池（近一年孰低外评：AAA）`。目标池不限于 15/17。
8. 调出限制池（关系 12 / `out_restrict`）：主体命中时跳过该主体；旗下债命中时阻断主体联动，本轮事务整体回滚。
9. 最近执行结果同时展示主体出池数、旗下债券出池数，并按目标池列出主体和债券数量。

### 7.3 `company_outer_rating_aa_minus_auto_in`（23:15）

对应老系统 `AdjustRuleInAA`。现按管理办法纳入质押券黑名单，满足下列之一且尚未在目标池即入：

1. 扩展参数 `poolIds` 指定目标池，默认 `[17]`（黑名单质押库），与投资池关系配置中绑定本任务的池取并集。
2. （一）（二）（三）分三条查询，Java 按主体代码合并去重并打命中标记。
3. （一）当前已在公司信用债禁止库 15（`audit_status=20`、主体），且尚未在目标池。
4. （二）数据源 `ais_inv_ods.wind_cbondissuerrating`：近一年内认可机构外评取 **孰低**；一年以前忽略。命中 `AA-` / `A±` / `BBB…` / `BB…` / `B…` / `CCC/CC/C` / `D`（**不含** `AA`/`AA+`/`AAA`）。业务查询 `dict_external_rating_agency` 中有效的 `b_info_creditratingagency` 并作为 `IN` 参数；无认可外评不因（二）入库。
5. 评级临界：`AA-` 及以下因评级入池；`AA` 及以上不因评级入池；空评级/近一年无认可外评不因评级入池，但命中禁止库 15 或重点观察 23 时仍按相应条件入池。
6. （三）当前已在重点观察名单 23，且尚未在目标池。本期不含观察池 16。
7. 尚未在目标池 `audit_status=20` 的主体 → 写自动调整入池日志 + `ip_pool_status`（`security_type=company`）；调整原因按命中条款拼接，如 `外评AA-及以下主体自动入池（近一年孰低外评：AA-）`。
8. 调入限制池（关系 11 / `in_restrict`）阻断。
9. 主体入池成功后，**本任务内自写**同批调入旗下未退市、未到期（含当天）的 `bond` 大类（`security_status!='D'`，含普通债、ABS、CRMW），**不复用**人工 `syncCompanyBonds`；债命中调入限制池则阻断主体入池并整批回滚；入池后按互斥/反向限制从债当前所在池自动调出；任一写入失败整批回滚。目标池不限于 15/17。
10. 最近执行结果同时展示主体入池数、旗下债券入池数，并按目标池列出主体和债券数量。

### 7.4 `company_same_pool_bond_auto_in`（23:20）

对应老 IP_RULE type=0「主体下债券自动入库」：

1. 扩展参数仅 `poolIds`：Demo 配置 `[15,16,17,23]`，自动并入所有已有生效主体的普通池，每个 ID 既是主体所在池也是债写入池（无 mappings）。
2. 主体 `category_type=company` 且 `audit_status=20` 在池 → 旗下 `bond` 大类未退市（`security_status!='D'`）、未到期（`maturity_date` 为空或 **≥ 今天**）、未在本池 → 自动入。
3. 市场：池 `market_codes` 为空/`[]` 不限制；否则仅按当前 Wind 代码的唯一市场匹配（主档 market 与 Wind EXCHMARKET 一致，银行间使用 NIB，临时代码取登记市场），不通过同券其他市场放宽限制。
4. **不**排除临时代码已更新，范围含普通债、ABS、CRMW；扫描17时再次校验主体三条件，均不满足则不补债。
5. 调入限制池（关系 11）阻断。  
6. 入池成功后，合并目标池的 `in_mutex` 与反向指向目标池的 `in_restrict`，仅从债券当前实际所在池自动调出；同一池去重并生成 `adjust_type='互斥调整'`、`adjust_mode='调出'` 的同批日志。
7. 最近执行结果展示实际入池总数，并按目标池列出入池数量。

### 7.5 `company_inpool_bond_auto_in`（每 10 分钟）

对应老 `AutoAdjustInNewBondToLimitPoolJob`：

1. 所有已有生效主体的普通池自动追加同池映射，再合并 `poolIds`、关系绑定及 `mappings`（`companyInPoolId` → `bondTargetPoolId`）跨池配置。Demo 的 `poolIds` 为 `[15,16,17,23]`。
2. 主体已在主体池 → 旗下债未退市（`security_status!='D'`）、未到期（**不含**当天，`maturity_date > 今天`）、未在**目标池** → 自动入。
3. 另排除临时代码已更新；普通债、ABS、CRMW 均参与；**不看**限制池。
4. 「已在池」看写入目标池（老 Job 误写成主体池，跨池会错；新系统按目标池，属有意修正）。
5. 入池成功后按目标池的 `in_mutex` 与反向 `in_restrict` 配置，从债券当前实际所在池自动调出并生成 `adjust_type='互斥调整'` 的同批调出日志，对齐老 Job 调用的 `JdbcAdjustPoolUtils.adjustInPool`。
6. 最近执行结果展示实际入池总数，并按“主体所在池 → 债券写入池”列出入池数量。

两任务对照：

| 任务 | 对应老系统 | 池关系 | 到期当天 | 主要过滤 |
|------|------------|--------|----------|----------|
| `company_same_pool_bond_auto_in` | IP_RULE type=0 | **仅同池** `poolIds` | **可入** | 排除退市；`market_codes`；不排除临时代码 / ABS / CRMW；看限制池；入池后处理互斥/反向限制池 |
| `company_inpool_bond_auto_in` | `AutoAdjustInNewBondToLimitPoolJob` | 同池或跨池 `mappings` | **不入** | 排除退市及已更新临时代码；不排除 ABS / CRMW；不看限制池；入池后处理互斥/反向限制池 |

### 7.6 `company_not_in_pool_bond_auto_out`（00:00，默认关闭）

对应老 `AutoAdjustInLimitPoolToNewBondJob`（Quartz 默认不启用）：

1. 未退市债已在 `bondPoolId`，发行主体不在 `companyPoolId` → 从债券池调出。
2. 普通债、ABS、CRMW 均参与，不按债券子类型排除。
3. 独立 Job 口径，**不看**调入/调出限制池。
4. Demo 默认 `schedule_enabled=0`。
5. 最近执行结果展示实际出池总数，并按“债券池 ← 主体池”列出出池数量。

> 所有“主体旗下债券跟随主体入池/出池”链路统一排除 `security_status='D'` 的退市债，并包含普通债、ABS、CRMW；上市中 `L`、待上市 `N`、未知 `U` 均可参与。外评入/出池任务的跟债逻辑写在各自定时任务内，**不复用**人工 `syncCompanyBonds`。CRMW 证券跟随主体进入普通风险池时写 `ip_pool_status`；专门的 CRMW 组合池仍必须通过“凭证 + 标的证券”链路写 `ip_pool_status_crmw`，上述主体联动任务遇到 `pool_type='crmw'` 的目标池或来源池时跳过。若后续需排除 ABS 或特定 `security_type`，统一通过 `CompanyBondTypeScopeBo` 传递，不在各 Mapper SQL 中重新硬编码。

### 7.7 `bond_grade_inconformity_alert`（01:00）

对应老系统 `InconformityMaingrade2Job`。实现类 `GradeRuleAlertService`（`implements RrsScheduledTask`），**只提醒、不改池**。

1. 扫已在信用债 1～5 级且 `audit_status=20` 的债券（`queryGradedBondInPoolList`；不含境外债，对齐老 polidEnum）。  
2. 对每条调用 `SecurityPoolAdjustService.evaluateGradedInboundForPool`（含特殊债/担保债/观察池、重点观察名单）。  
3. 仍允许则跳过；不再允许则 `upsert` `ip_grade_rule_alert`（`alert_status=00`），刷新原因与扫描时间（计入**本轮命中**）。  
4. 本轮未再命中的待处理记录置 `99` 失效（计入**本轮失效**；含义：曾告警，现已合规或已不在扫描范围）。  
5. 再统计 `alert_status=00` 为**仍待处理**（执行摘要与影响条数以此为准；过程日志三项分列）。失效比较参数传秒级时间字符串 `yyyy-MM-dd HH:mm:ss`。  
6. 人工入口在「我的事宜」第三页签（[06](06-my-matters.md)）：`queryAlertPage` 列表、`editAlertProcessed` 标记已处理（不改 `ip_pool_status`）。「去调库」打开证券池调整页，由人下调或出库。  

当前无独立 `GradeRuleAlertApiTest` / `GradeRuleAlertServiceTest`。

### 7.8 `pledge_blacklist_daily_increment_reminder`（09:00）

对应老系统 `BlackPoolRemindJob`，只读取调库流水，不改变池状态：

1. `poolIds` 指定质押黑名单扫描池，默认 `[17]`；当前按目标池精确匹配，不自动展开子池。
2. 时间窗口为左闭右开 `[windowStart, currentStart)`：后续执行用本任务最近一次成功执行的 `start_time` 作下界，首次执行从当天 `00:00:00` 开始。
3. 扫描 `ip_adjust_log.audit_status='20'`、未删除且 `audit_time` 位于窗口内的债券/主体调入调出流水；债券主数据到期日须为空或晚于当天。
4. 候选包含证券/主体名称、代码、发行主体、目标池、调整方向和审核时间，写入任务过程日志。
5. 无增量时仍构造“当日无增量调整”消息，保持老系统每日提醒语义。
6. 当前 `ScheduledReminderDeliveryService` 明确返回 `delivered=false`，不执行外部发送；接入邮件/站内信时只替换该投递服务。

### 7.9 `bond_issuer_not_in_company_pool_reminder`（09:00）

对应老系统 `RemindLimitPoolToNewBondJob`，只检查最终池状态，不自动出池：

1. `poolIds` 表示债券池与主体池相同；`mappings` 支持 `{bondPoolId, companyPoolId}` 跨池映射；两者去重合并。未配置参数时默认同池 `[15]`。
2. 扫描 `ip_pool_status` 中债券池内 `audit_status='20'` 的债券，若其 `rrs_securityinfo.issuer_code` 未在对应主体池以 company 大类生效，则进入提醒。
3. 对齐老系统 `sdc.stype != 38`：排除 `security_type='crmw'`；普通债和 ABS 参与。
4. 发行主体主数据缺失时也进入提醒候选，主体代码/名称留空，便于暴露主数据问题。
5. 候选包含债券、发行主体、债券所在池，写入任务过程日志。
6. 当前 `ScheduledReminderDeliveryService` 明确返回 `delivered=false`，不执行外部发送；后续通知实现可直接消费已经组装的 `ScheduledReminderMessageDto.items`。

恒生导出共用统一市场查询，正式证券直接读取主档 `market`（与 Wind `EXCHMARKET` 一致，银行间使用 NIB），临时代码读取登记市场；按实际状态/日志代码批量查询所属市场，不按同 ISIN 扩行；映射缺失、重复或不支持时报告具体代码。保留现有表头、恒生编码、去市场后缀和时间窗口规则。


## Wind代码变更同步 wind_code_sync

### 执行调用链与各步写入

这里的“登记”是 `rrs_temp_security_code` 业务表中的一条记录，普通 `official` 证券换码也会使用该表。准备登记和完成转正是两个步骤：新增登记时先保存旧码和 `temporary` 状态，后续方法才写入正式码和 `updated` 状态；复用已有登记时，原有目标码必须与本次新码一致。

```text
ScheduledTaskService.runTask()
  │  按任务编码找到实现类，取得任务锁后调用 execute()
  ↓
WindCodeSyncService.execute()
  │  记录任务开始、结束、耗时和执行结果
  ↓
doSync()
  │  读取页面扩展参数 scanDays（默认 1），SQL 按数据库时间计算范围，再按 SEC_ID 取最新记录
  │  同一 SEC_ID 只处理最新代码，主档 A 直接换成最终 C，统计成功、跳过和失败
  ↓
TempSecurityCodeService.editWindCodeChangeByJob(change)
  │  单条事件的事务入口：以下操作一起提交，任一步失败全部回滚
  │  按 SEC_ID 锁定主档，检查是否已完成或已是新码，使用主档当前代码直接换码
  │
  ├─ prepareWindRegistration()
  │    查询主档当前代码对应的 rrs_temp_security_code 记录
  │    无登记：按旧证券信息新增，temp_security_code=旧码、status=temporary
  │            此时不填写正式码；temporary 主档缺少原登记时直接报错
  │    有登记：校验待转正状态、目标码、类型和市场后复用
  │    在 memo 中附加事件去重标记；复用记录的备注随后续转正更新一起保存
  │
  ├─ windCodeSyncMapper.editSecurityWindCode()
  │    更新 rrs_securityinfo：wind_code 从旧码改为新码，保留原 SEC_ID
  │    来源 temporary 改为 temp_converted，official/temp_converted 保持不变
  │
  └─ editTempSecurityCodeToUpdated(..., true)
       │  组装转正数据：正式码=本次新码、状态=updated、操作来源=job
       │  true 表示 Wind 单码模式：只处理本次新码，不扩展同券其他市场代码
       ↓
     convertTempSecurityBusinessData()
       │
       ├─ prepareWindCodeConversion() / prepareCrmwConversion()
       │    查询旧码当前在池记录和在途申请，准备普通池及 CRMW 转换计划
       │    校验新码主数据、市场、池关系和申请冲突，全部通过后再更新登记
       │
       ├─ tempSecurityCodeMapper.editTempSecurityCodeToUpdated()
       │    真正更新登记表：写入新码及正式证券信息、job 来源和事件标记
       │    将 status 从 temporary 改为 updated，此时仍在本条事务内
       │
       ├─ convertOrdinaryPoolBusinessData()
       │    已在池：生成旧码调出、新码调入流水，并更新池状态；避免重复入池
       │    在途申请：更新日志代码，按目标主档新增快照，旧快照保留，审批不变
       │    无业务引用：不生成调库流水；历史终态申请保留原样
       │
       └─ convertCrmwBusinessData()
            同一凭证下转换标的代码：已在池旧出新入，在途更新日志代码并新增快照
            保留原凭证及审批流程；另处理匹配旧码的 CRMW 凭证字段引用
```

事件标记在准备登记时就已组装，但此时不代表换码完成。只有本条事务成功提交、登记状态为 `updated` 且来源为 `job` 后，后续扫描才按该标记跳过重复事件；业务转换失败时，主档换码、登记和相关业务写入一起回滚。

### 扫描、处理规则与演示数据

- 方法分工与其他定时任务一致：`execute()` 仅包装计时、开始/结束日志、任务级异常及执行结果；核心 `doSync()` 负责查询最新变更、核对最新日期并列记录、逐只证券换码及结果汇总。
- 数据源：`ais_inv_ods.wind_cbondchangewindcode`。页面扩展参数可填 `{"scanDays":3}`；scanDays 为 int 范围内的正整数，留空或省略默认 1 天，NULL、0、负数、小数、字符串、布尔值、未知字段及错误 JSON 均在扫描前报错。每次执行重新读取配置。SQL 使用 `CHANGE_DATE >= DATE_FORMAT(TIMESTAMPADD(DAY, -#{scanDays}, NOW()), '%Y%m%d')`，仅绑定天数，日期由数据库计算，Java 不传开始/结束日期，也不增加今天上限。例如 3 表示从数据库当前日期的 3 天前（含）开始，按八位 yyyyMMdd 比较，不是精确小时窗口。按 SEC_ID 取最大 CHANGE_DATE，较早记录不处理，最新记录旧新码相同则不处理；S_INFO_WINDCODE 可重复，OPDATE、OBJECT_ID 不决定新旧。
- 一条 `SEC_ID` 唯一对应一条主档，各市场证券的 SEC_ID 独立。按 SEC_ID 锁主档，将主档当前 wind_code 直接更新为最新记录的 S_INFO_NEWWINDCODE，保留证券 ID 和其他字段。主档还不是新码时，新码不得占用其他主档；主档已经是新码时直接跳过，不新增临时代码记录和调库记录。
- 完成标记由 SEC_ID、变更记录旧代码、新代码和 CHANGE_DATE 组成，`OBJECT_ID` 不参与判重，也不做必填校验；仅 OBJECT_ID 不同、为空或缺失的同一变更仍按重复处理。证券匹配始终按 SEC_ID。下文提到的临时代码记录，就是 `rrs_temp_security_code` 表中的一条数据。
- 用户确认：源表有 A→B、B→C，主档仍是 A 时，直接执行 A→C。源记录旧码 B 不存在也继续；临时代码记录、普通池、CRMW 和待审批申请都使用主档实际旧码 A，不生成中间码 B 的出入库记录。按 SEC_ID 无主档时仍 WARN 提示并跳过，临时表即使有记录也不单独转换。本条规则替代此前“事件旧码不存在就跳过”的规则。
- 四类处理：`official` 只补任务登记并保留来源；`temporary` 复用未删除、temporary 状态的原登记，主档改为 `temp_converted` 并写入原登记的明确市场；主档缺失特别提示、跳过且不补数据；`temp_converted` 要有转正历史，新增本次旧新码登记、保留原登记。
- 任务登记仅补 `rrs_temp_security_code`，不调用页面新增入口、不创建 TEMP_ 占位主档。已有待处理登记的目标码不得与事件冲突，取消、删除或来源与登记不一致时明确失败。
- 主档更名后使用既有正式主数据及市场校验；复用 `TempSecurityCodeBusinessConverter` 的单码入口。普通池、同凭证下 CRMW 标的分别处理：当前状态旧码退出、新码进入；在途申请更新日志身份、保留旧代码快照并新增目标代码快照，沿用原批次、发起人、状态和步骤；历史终态不改写。即时日志操作人为系统、audit_status=20、adjust_advice=NULL。无当前状态或在途业务不生成调库流水。
- **证券快照**：普通池与 CRMW、当前在池与在途申请统一按目标代码当前主档新增快照，不复制旧快照的业务填写值或空值，不给旧快照改码、更新或删除。在途主项保留原日志 ID，详情和审批按“日志 ID + 证券代码”读取对应快照；CRMW 凭证字段仍取本笔日志。Wind 主档更名不额外补齐利率、评级等信息，新快照使用更名时主档已有值；此规则不保证上游尚未同步的字段非空。重复、已是新码、主档缺失等跳过事件不新增快照；无在池或在途业务时保持只改主档及登记，不额外新增快照；快照写入失败整次换码回滚。本次不自动迁移已经换码的存量数据。
- 新码不扩展其他市场的 ISIN 关联组；人工转正和 `bond_temp_code_replace` 仍沿用原整组规则。共享转换明确为新码写入“调入”方向，避免原 CRMW 状态缺少方向时生成无方向流水。
- Wind 换码生成的普通池、CRMW 旧代码出库日志，调整类型统一使用 `AdjustType.WIND_CODE_CHANGE_OUT.getCode()`，存储值仍为 `Wind代码变更调出`；新代码入库及原待审批申请的调整类型沿用现有规则。
- 每只证券通过 `TempSecurityCodeService.editWindCodeChangeByJob` 独立事务提交；主档、临时代码记录、流水、池状态和快照任一步失败全部回滚。备注保留原内容并追加 `[wind_code_sync:SEC_ID:源记录旧码:新码:日期]`，同事务成功后用于去重。即使实际换码为 A→C，标记仍使用源记录 B→C，不能改写输入记录导致重试标记变化；备注超长明确报错。
- 同一 SEC_ID 的最新日期若有多条记录且新代码相同，只执行一次；若新代码不同，记录 ERROR 并跳过该证券，不按 OPDATE、OBJECT_ID 或旧新码链猜测先后，其他证券继续。
- 过程日志区分成功、主档缺失跳过、已是新码跳过、重复跳过、失败。主档缺失为 WARN，包含 SEC_ID、源记录旧新码和日期；日志中的“变更记录旧代码”是源数据旧码，实际出入库记录使用主档当前代码。失败项在日期窗口内重扫，超窗需核查并专项补执行；不修改 AIS 源结构，不补主档或 ID 映射。
- 任务执行状态与单条业务结果分别统计：扫描和逐条处理正常完成即返回任务成功，即使存在单条转换失败或全部单条失败，也保留 ERROR 明细、失败条数和实际成功影响数；只有扫描或其他任务级异常导致执行未完成时返回任务失败。
- 实际成功换码固定向 `rrs_temp_security_code_update_log` 写一条登记映射（`replace_table_name='rrs_temp_security_code'`、`replace_record_id=登记ID`），记录主档实际旧码到最新新码，无业务引用时同样写入；受影响的业务记录另存原有转换明细。主档缺失、已是新码、已完成变更等跳过不新增日志；日志写入失败与主档、登记和业务转换一起回滚。
- 人工新增 ID 当前为 `TEMP_临时代码`，未与 Wind 真实 SEC_ID 建立对应时按主档缺失处理，不按名称或代码猜测证券身份。
- Demo 包含 13 条换码记录，日期按数据库 NOW() 动态生成；选取最新记录后候选 10 条，首次成功 9 条、主档缺失跳过 1 条、已是新码跳过 0 条、失败 0 条。重复执行时成功 0 条、重复跳过 9 条、主档缺失跳过 1 条，不新增业务记录。XYB 的较早记录、同码记录和窗口外历史不处理；匹配主档的 SEC_ID 唯一且不超过 10 位。
- 初始化范围：表结构就绪后，可使用脚本工具完整初始化 Demo（`INIT_ALL_DEMO`）。按模块准备时，需恢复 `rrs_external_import_demo_data.sql`、`rrs_temp_security_code_demo_data.sql`、`rrs_dict_demo_data.sql`、`rrs_pool_init_demo_data.sql`、`rrs_security_pool_adjust_demo_data.sql`、`rrs_crmw_pool_status_demo_data.sql` 和 `ais_inv_ods_demo_data.sql`。无论使用哪种方式，从头演示前还须清空普通/CRMW 调库快照（这两张表不由 Demo 脚本灌数）。任务先保持关闭，完整恢复后手动执行；只重置 ODS 事件不会恢复已换码主档、登记或业务状态。从头再演示也须恢复相同范围。

| 事件 | 初始情况 / 编码 | 预期处理 |
|------|-----------------|----------|
| `WCH_DEMO_001` | official 无登记，`108008901.IB→108008906.IB`，二级库在池 | 保留 official，新增登记，旧码出库、新码入库 |
| `WCH_DEMO_002/003` | 同一 SEC_ID 历史为 `XYB001.IB→XYB006.IB→XYB007.IB`，昨天/今天两条的 S_INFO_WINDCODE 均为 XYB007.IB，主档仍为 XYB001.IB | 仅取今天的 003，主档、临时代码记录和调库均直接执行 XYB001.IB→XYB007.IB，不生成 XYB006.IB 的业务记录 |
| `WCH_DEMO_004/005` | official 无登记，`198101.SH→198106.SH`、`128101.SZ→128106.SZ` | 沪深各自按独立 SEC_ID 换码，不因同券关联额外处理其他市场 |
| `WCH_DEMO_006` | 主档不存在，`WCDMISS001.IB→WCDMISS002.IB` | 特别提示并跳过，不创建主档、登记或调库记录 |
| `WCH_DEMO_007/008` | 旧新码相同 / 前天历史记录 | 被扫描条件过滤 |
| `WCH_DEMO_009` | temporary 有待转正登记，`WCTMP001.IB→WCTMP006.IB`，无业务引用 | 复用 id=4 登记并保留原备注和登记 ID；主档改为 temp_converted、市场 NIB，无调库流水 |
| `WCH_DEMO_010` | temp_converted 有转正历史，`ABN002.IB→ABN007.IB`，无业务引用 | 保留来源及 TMP002.IB→ABN002.IB 原登记/日志，新增 ABN002.IB→ABN007.IB 登记，无调库流水 |
| `WCH_DEMO_011` | official 无登记，`MTN001.IB→MTN006.IB`，三级库及 CRMW 库在池 | 两类持仓各自生成旧出新入，CRMW 凭证 CRMW001.IB 不变，原终态日志保留旧码 |
| `WCH_DEMO_012` | official 无登记，`GSB001.SH→GSB006.SH`，一级库申请待审批 | 原申请 id=8、批次、流程及步骤不变，更新代码和快照，不提前生成在池状态 |
| `WCH_DEMO_013` | 主档 SEC_ID=S9001046、当前码 PPN001.IB，最新记录为 `PPN000.IB→PPN006.IB`，源记录旧码不存在 | 按 SEC_ID 直接换成 PPN006.IB，新增 PPN001.IB→PPN006.IB 记录；不创建 PPN000.IB |

`WCTMP001.IB` 专用于演示“外部已补齐真实 SEC_ID”的待转正场景：主档为 `S9001050`，原登记仍保留创建时的 `TEMP_WCTMP001.IB`。这只是 Demo 的外部映射前置状态，不代表 Wind 任务会自动把 TEMP_ 身份转换成真实 SEC_ID。原 TMP001.IB / TMP003.IB 人工录入及取消样例继续保留。
- 测试：`WindCodeChangeDemoDataTest` 原样执行 Demo、生产 Mapper 和事务，验证四类来源、最新历史、主档直接换码、普通/CRMW 出入库、在途与快照、历史保留和重复执行。`WindCodeSyncServiceTest` 验证数据库扫描接口、最新日期并列冲突、相同新码去重和失败隔离；`TempSecurityCodePersistenceTest` 验证数据库日期边界、相同/不同 S_INFO_WINDCODE 的最新记录、OPDATE 不决定新旧、主档 A 直接改为 C、已是新码跳过、源记录不被改写，以及原事务回滚规则。异常数据仅在隔离测试中建立（H2 测试使用 JDK 17）。
- Demo 编码只变化数字末位，市场后缀不变；XYB 历史为 `XYB001.IB→XYB006.IB→XYB007.IB`，本次实际只做 `XYB001.IB→XYB007.IB`。
