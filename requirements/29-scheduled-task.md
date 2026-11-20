# 29 定时任务管理

## 恒生池 Excel 导出

- `hs_pool_full_excel_export`：对应老系统 `ExportStatusToHsJob`，导出指定叶子池当前已生效的非主体证券及 CRMW；普通证券排除已到期数据，CRMW 按老系统定时任务口径不校验到期日；未指定 `poolIds` 时导出全部叶子池。
- `hs_pool_full_including_expired_excel_export`：对应老系统 `ExportStatusToHsJob2`，与上一任务使用相同池范围和 Excel 结构，但普通证券包含已到期数据；CRMW 同样不校验到期日。
- `hs_pool_increment_excel_export`：首次以 `initialStartTime` 为下界，后续以上次成功执行开始时间为下界，以本次开始时间为上界。导出审核时间大于下界且不晚于上界的审批通过事件，不要求证券当前仍在池。老系统通过调库日志 `exportflag` 控制增量，新系统明确使用任务成功时间水位线，不更新业务调库日志。
- 三个任务均使用五列表头：证券名称、证券代码、操作类型、市场名称、备注；调出操作类型写“删除”，备注留空。市场名称列按恒生市场编码输出，当前字段映射为沪市 `1`、深市 `2`、银行间 `4`、北交所 `6`、其他市场 `99999`，完整编码集中维护在 `HsMarketCode`；证券代码去掉首个 `.` 及其后的市场后缀，按实际在池记录或日志中的代码及所属市场输出，不再次展开同券其他市场代码。
- `hs_pool_name` 为空时使用投资池完整名称，支持竖线拆分多个 Sheet；名称超过 31 位截断，同名 Sheet 合并追加并写过程警告日志。老系统页面手动导出会合并同名 Sheet，但老系统定时任务未做重名判断、直接创建时可能抛异常；新系统采用页面导出的容错方式。
- 增量任务已预留交易日日期列表入口；当前列表固定为空，表示尚未启用过滤，因此交易日和非交易日均正常执行。交易日数据源、查询异常策略和非交易日强制执行配置后续确认。
- `param_json` 支持 `poolIds`、`exportEmptyPool`、`outputDir`。`exportEmptyPool` 默认 `true`，设为 `false` 时跳过无数据的池。`outputDir` 省略时使用 `rrs.hs-pool-export.storage-path`。增量首次执行还必须按 `yyyy-MM-dd HH:mm:ss` 配置 `initialStartTime`。文件写入 `输出根目录/yyyyMMdd`，并复制到同日 `bak` 目录。FTP 暂未接入。

> 前端页面：`scheduled_task.html`  
> 后端前缀：`/api/v1/scheduledTask`  
> 角色定位：运维 / 管理员在「RRS配置」下可视化查看、启停、改 cron、手动执行定时任务，并查看执行历史。

## 1. 业务目标

**配置与实现分离**（先建库表配置，再写业务实现）：

1. **库表配置** `sys_scheduled_task`：名称 / 说明 / cron / 启停 / **扩展参数（通用字段 param_json）** — 页面新增、编辑、删除  
2. **代码实现** `RrsScheduledTask`：`getTaskCode()` + `execute()` + 可选 `getParamHelp()`，与库表 `task_code` 绑定；任务编码须字母开头，仅含字母、数字、下划线，长度 2～50 位
3. **执行历史** `sys_scheduled_task_run_log`：手动 / 定时触发留痕  

外部评级相关任务与人工调库评级下调检查共用 `dict_external_rating_agency`：只读取 `is_deleted=0` 且机构编码非空的数据。业务先查询有效 `b_info_creditratingagency` 列表，再作为参数传入 Wind 评级查询；未配置有效机构时，自动调库与黑名单校验阻断，评级下调检查按 fail-open 视为未下调。

定时任务统一写入 `ip_adjust_log`（CRMW 以 `pool_type=crmw` 区分）时只赋值 `adjust_reason`，`adjust_advice` 保持 `NULL`；人工调库及人工临时代码转正不受此规则影响。新生成的调库记录以 【任务名称：任务名称】 标注来源，优先使用定时任务管理页配置的名称，同批旗下债券、联动和互斥记录沿用同一名称。括号中展示本次判断的重要依据：到期任务展示实际到期日与出池口径；主体旗下债券任务展示发行主体及主体所在/未在池；池关系联动出池展示调入池和被调出池；临时代码替换定时任务展示临时代码与正式代码。示例：`【任务名称：到期证券自动出池】（到期日：2026-09-01；出池口径：到期日早于当前日期减去 1 天）`、`【任务名称：主体下债券自动入池（已排除ABS债）】（发行主体：某集团/C001；主体所在池：债券禁止库）`。

自动调库批次号按“实际主调整对象 + 主目标池 + 调整方向”生成。主体及主体关联/互斥项使用 `COMP` 批次，独立债券同步使用 `BOND` 批次；只有债券/普通证券调整时按证券代码生成 `BOND` 批次，其关联/互斥项继承主日志批次；CRMW 按“凭证代码 + 标的证券代码”组合生成 `CRMW` 批次。不同主对象或不同主目标池不得共用批次。历史 `AUTO` 前缀记录不迁移；新任务过程日志不输出批次号，但 `adjust_batch_no` 继续写入业务调库日志和池状态。

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
| `bond_temp_code_replace` | 债券临时代码替换（仅债券） | `0 40 4,8,9,13,18,22 * * ?` | 每天 04:40、08:40、09:40、13:40、18:40、22:40 执行。无需参数；扫描 `rrs_temp_security_code.status=temporary` 且已由外部数据补齐 `security_code` 的记录，复用临时代码人工转正的完整分叉逻辑，操作来源记为 `job` |
| `bond_security_type_change` | 债券类型变更（仅债券） | `0 0 22 * * ?` | 每天 22:00 执行。无需参数。以 `rrs_securityinfo.security_type` 为准，同步普通池、CRMW 池标的证券及已审批通过的调入调出流水中的债券类型 |
| `security_expired_auto_out` | 到期证券自动出池 | `0 0 23 * * ?` | 每天 23:00 执行。`{"poolIds":[15],"maturityOffsetDays":1}`；`poolIds` 可选，Demo 为 `[15]`（债券禁止库），与投资池关系配置绑定本任务的池取并集；扫描 `ip_pool_status` 中已生效且未删除的债券（含 ABS、CRMW），到期日早于当前日期减去 `maturityOffsetDays` 天时进入候选，省略默认 1（原 T-2），支持 0、正负整数；先剔除 Excel 保护项，再跳过命中调出限制池、已提前出池或缺少证券编码的记录 |
| `crmw_expired_auto_out` | CRMW到期自动出池 | `0 5 23 * * ?` | 每天 23:05 执行。`{"poolIds":[18],"maturityOffsetDays":1}`；`poolIds` 可选，Demo 为 `[18]`（CRMW库），与关系配置绑定池取并集；扫描 `ip_pool_status_crmw` 中已生效且未删除的凭证与标的组合（含 ABS 债券作为标的的组合）；仅按凭证到期日判断，标的证券到期本身不会触发组合出池；`ip_pool_status` 中的 CRMW 券由到期证券自动出池任务扫描；省略 `maturityOffsetDays` 默认 1（原 T-2），支持 0、正负整数；命中调出限制池则跳过 |
| `company_outer_rating_not_aa_minus_auto_out` | 外评非AA-及以下主体自动出池（仅主体） | `0 10 23 * * ?` | Demo 扫描 17（黑名单质押库）。近一年最低认可外评为 AA 及以上、且未命中 15/23 留池条件及出池限制的主体自动调出，详见 7.2 节 |
| `company_outer_rating_aa_minus_auto_in` | 外评AA-及以下主体自动入池（仅主体） | `0 15 23 * * ?` | Demo 调入 17（黑名单质押库）。尚未在目标池且命中 15、近一年最低认可外评为 AA- 及以下或命中 23 的主体通过校验后调入，详见 7.2 节 |
| `company_inpool_bond_auto_in` | 在池主体旗下债券自动入池（已排除ABS债）（仅债券） | `0 1/10 * * * ?` | 普通债与 CRMW 入池，支持同池及跨池；`{"poolIds":[15,16,17,23],"minDaysToMaturity":0}` |
| `company_abs_bond_auto_in` | 主体下ABS债券自动入库（区分已确认发行人和未确认发行人）（仅债券） | `0 5/10 * * * ?` | 独立核对 ABS 入池、出池及待确认提醒 |
| `company_not_in_pool_bond_auto_out` | 出池主体旗下债券自动出池（仅债券） | `0 3/10 * * * ?` | 目标池主体 Excel 调出生效或全部主体依据失效时，撤销符合条件的跟随记录，详见 7.3 节 |
| `bond_grade_inconformity_alert` | 不符合主体债入池规则提醒（含ABS债，担保债取快照担保人内评）（仅债券） | `0 0 1 * * ?` | 每天 01:00 执行。无需参数；扫描不符合当前主体债入池规则的在池信用债（**含 ABS 债**，不含境外债），生成待办供人工处理，不自动出池。**ABS 的评级主体在确认权益人时已固定到主档发行人字段，直接取主档 `inner_issuer_rating`，不查询入库快照**；**担保债可能对应多个担保人，主档 `guarantor_id` 为逗号多值串，按当前在池记录 `adjust_log_id` 的入库快照取当时选定的担保人，再查该担保人最新内评**。摘要区分**本轮命中** / **沿用已处理** / **本轮失效** / **仍待处理** |
| `hs_pool_full_excel_export` | 恒生池全量数据导出（不含已到期） | `0 10 1 * * ?` | 每天 01:10 执行；`poolIds` 可限制叶子池，省略时导出全部叶子池；默认生成空池 Sheet；导出当前已生效的非主体证券及 CRMW，普通证券排除已到期数据，CRMW 不校验到期日 |
| `hs_pool_full_including_expired_excel_export` | 恒生池全量数据导出（含已到期） | `0 20 1 * * ?` | 每天 01:20 执行；参数与不含已到期全量任务一致；普通证券不校验到期日，包含仍在池的已到期数据，CRMW 同样不校验到期日 |
| `company_observer_pool_sync` | 主体观察名单同步（仅主体） | `0 0 6 * * ?` | 根据 AIS 名单先出后入。Demo 将类型 2、3 分别同步至观察池 16、重点观察名单 23，并按配置处理主体池关系 |
| `pledge_blacklist_daily_increment_reminder` | 质押黑名单库每日增量提醒 | `0 0 9 * * ?` | 每天 09:00 执行；`poolIds` 默认 `[17]`；按上次成功执行时间水位扫描审批通过的债券、主体调入调出流水。当前仅生成候选与过程日志，通知通道待接入 |
| `bond_issuer_not_in_company_pool_reminder` | 主体库内债券主体不在池债邮件提醒（仅债券） | `0 0 9 * * ?` | 每天 09:00 执行；`poolIds` 支持同池，`mappings` 支持债券池与主体池跨池映射；扫描发行主体不在对应主体池的在池债券，排除 CRMW。当前仅生成候选与过程日志，通知通道待接入 |
| `crmw_rule_alert` | CRMW规则提醒 | `0 13 9 * * ?` | 每天09:13；凭证创设方最新内评低于3档或已在观察/重点观察名单时生成独立待办，只提醒不改池，详见[47](47-crmw-rule-alert.md) |
| `crmw_forbidden_auto_out` | CRMW关联禁投自动出库 | `0 7/10 * * * ?` | 每10分钟第7分；标的债券、标的发行主体、凭证或创设／保护主体任一在有效禁止库时清退对应组合，一次读取、单组合事务，单条失败继续，只写CRMW出库日志，详见[48](48-crmw-forbidden-auto-out.md) |
| `hs_pool_increment_excel_export` | 恒生池增量数据导出 | `0 */6 * * * ?` | 每 6 分钟执行一次；首次从 `initialStartTime` 开始，后续从上次成功执行开始时间继续；导出窗口内审批通过的调入和调出，调出标记“删除”；按证券主档排除已失效临时代码的转正出库记录；交易日过滤暂未启用 |
| `wind_code_sync` | Wind代码变更同步（仅债券） | `0 2/10 * * * ?` | `{"scanDays":1}`，scanDays 为正整数，省略默认 1 天。每小时第 2/12/22/32/42/52 分钟执行；按唯一 SEC_ID 拆分旧临时代码失效主档和新代码主档，按四类来源准备登记，复用单码业务转换；修改普通池、CRMW及在途申请，须与其他调库错开 |

> Demo 行与 id 按上表顺序排列，且全部 `schedule_enabled=0`（默认关闭）。Demo **cron** 须与下方「执行顺序」一致；新增或改 cron 时必须先读第 4.1 节。三项恒生池 Excel 导出任务不改池状态；Wind 换码会修改池状态，Demo 与其他调库错开，已有数据库配置须在页面自行调整，不由程序自动覆盖。

Demo 的「任务说明」保存在 `sys_scheduled_task.description`，最多 500 字。按顺序写明执行时间和默认启停状态、扫描范围和参数、处理及跳过条件、执行结果。已初始化环境可在「RRS配置 → 定时任务管理 → 配置」编辑已有任务说明。

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
- Cron 回调取得任务锁后读取共享主库的最新配置，仅在 `schedule_enabled=1` 时执行业务。关闭、配置不存在（含删除）或读取失败时跳过，仅记录应用日志，不新增执行历史或更新最近执行摘要。已开始的任务继续执行，关闭调度后仍可手动执行。
- **不同 taskCode 之间没有全局锁、没有编排器**：先后完全靠 cron。有业务依赖的任务必须把 cron 配成先后执行，禁止设成同一分钟。

任务名称用「（仅主体）」或「（仅债券）」标出单一处理对象。已有配置按任务编码显示标注，自定义名称保留。到期证券自动出池和 CRMW 组合任务保留各自的名称。

### 4.1 执行顺序（新增/改 cron 必读）

调度器**不会**保证「A 跑完再跑 B」。若任务 B 依赖任务 A 改过的池状态，必须满足：`A 的 cron < B 的 cron`，并留足 A 跑完的时间（Demo 按整点错开）。

当前 16 个需要池状态先后编排的任务，其**必须顺序**与 Demo cron：

| 顺序 | 时刻 | 任务 | 为何必须在这 |
|------|------|------|----------------|
| 1 | 04:40、08:40、09:40、13:40、18:40、22:40 | `bond_temp_code_replace` | 多时点把外部数据已匹配的临时代码转正式；会改在途日志及普通/CRMW 池状态；22:40 批次直接使用正式主数据类型 |
| 2 | 22:00 | `bond_security_type_change` | 在 18:40 临时代码替换后，以最新主数据统一当前在池债券类型；早于 22:40 替换批次 |
| 3 | 23:00 | `security_expired_auto_out` | 先清到期债券，避免后面入债再扫到已到期券 |
| 4 | 23:05 | `crmw_expired_auto_out` | 先清到期 CRMW（独立表，与到期债券同属「先清理」） |
| 5 | 23:10 | `company_outer_rating_not_aa_minus_auto_out` | **先改主体**：不再满足质押券黑名单（一）（二）（三）的主体先出 17 |
| 6 | 23:15 | `company_outer_rating_aa_minus_auto_in` | 再把新满足（一）（二）（三）之一的主体入 17（须先出后入） |
| 8 | 每 10 分钟（第 5/15/25…分） | `company_abs_bond_auto_in` | 处理 ABS 入池、出池和提醒；与第 9 项错开，避免同秒抢写池状态 |
| 9 | 第 1、11、21… 分钟 | `company_inpool_bond_auto_in` | 独立补入旗下债券 |
| 10 | 第 3、13、23… 分钟 | `company_not_in_pool_bond_auto_out` | 独立处理跟随债券出池 |
| 11 | 次日 01:00 | `bond_grade_inconformity_alert` | 池状态已稳定后再扫分级库不符，生成待办；不改池 |
| 12 | 次日 06:00 | `company_observer_pool_sync` | 按用户指定时间扫描名单及九类池关系，主体同步源池及联动池，早于09:00提醒；默认关闭 |
| 13 | 次日 09:00 | `pledge_blacklist_daily_increment_reminder` | 依赖前序调库流水稳定后再汇总增量；只读，不改池 |
| 14 | 次日 09:00 | `bond_issuer_not_in_company_pool_reminder` | 在所有主体/债券自动调库完成后检查最终不一致；只读，不改池 |

| 15 | 次日 09:13 | `crmw_rule_alert` | 读取06:00同步后的主体观察名单及有效CRMW组合，仅维护独立提醒，不改池 |
| 16 | 每10分钟第7分 | `crmw_forbidden_auto_out` | 在第1分补债、第2分换码、第5分ABS对账之后扫描当前禁投状态；01:07早于全量导出、09:07早于CRMW提醒，清退已禁投组合 |

另：`wind_code_sync` 使用 `0 2/10 * * * ?`，会修改主档、普通池、CRMW及在途申请；与第 1 分钟补债、第 3 分钟债券出池和第 5 分钟「主体下ABS债券自动入库（区分已确认发行人和未确认发行人）（仅债券）」错开，22:42 在临时代码替换 22:40 之后、23:00 到期扫描之前。错开时间时须按前序任务实际耗时留足间隔。不同 taskCode 没有全局锁；同一证券更名由主档行锁、登记条件更新和事务保护。

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

观察名单和外评任务维护主体状态。债券任务读取已提交的主体状态，调度按 Demo 错开，实际环境按任务耗时调整。

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

1. `rrs_securityinfo.security_type` 为权威最新类型。
2. 扫描 `audit_status='20'` 且未删除的 `ip_pool_status`、`ip_pool_status_crmw` 标的证券字段，处理新旧类型均属于有效债券大类的差异。
3. 条件更新池状态的 `security_type`，并同步该池状态 `adjust_log_id` 指向的当前 `ip_adjust_log.security_type`。
4. 再按 `ip_adjust_log.security_code` 匹配主档 `wind_code`，同步已审批通过且未删除的调入和调出流水。主档最新类型须属于有效债券大类且与流水类型不同。池状态已一致或证券已出池时仍可同步。执行摘要单列本步骤补齐的流水条数，影响条数仍按池状态更新量统计。
5. 原有 CRMW 凭证类型 `crmw_stype` 及 `ip_adjust_security_snapshot`、`ip_adjust_security_snapshot_crmw` 中提交时保存的证券信息继续保留。
6. 排在 18:40 临时代码替换之后、22:40 替换之前。22:40 新转正式记录在替换时直接使用正式主数据类型。任务仍早于到期和主体调库，避免后序任务读取旧类型。

### 主体观察名单同步 `company_observer_pool_sync`（06:00）

1. Demo 每天 06:00 执行，默认关闭调度。参数示例 `{"observePoolId":16,"keyObservePoolId":23}`。观察类型 2 对应观察池，类型 3 对应重点观察名单。参数省略或为 `null` 时不管理该类池，至少配置一个正整数池 ID，两类池不能相同。
2. 读取 `ais_inv_analysis.company_observer_view`，按主体和观察类型匹配目标池。类型变更、变为类型 1、为空或移出名单时退出原池，先出后入。主体编码为空的记录跳过并计数。
3. 按配置同批处理主体联动、互斥和反向调入限制引起的配套调出。调入校验来源池，按调整方向校验相应限制和互斥。命中弹性禁投池时记警告，涉及 Excel 入池保护的调整跳过。
4. 主体调整直接生效并写入调库日志，执行结果按池汇总。参数、名单、池关系校验或写入失败时整轮回滚。旗下债券由 7.3 节的独立任务同步。

### 7.0 `security_expired_auto_out`（23:00）

对应老系统 `AdjustRuleByExpired` + `AdjustPoolByRule.doAdjustOutByRule`：

1. 到期日期偏移：扩展参数 `maturityOffsetDays` 可选，省略默认 `1`，沿用原先早于昨天（T-2）的口径；必须为 int 范围内的整数，允许 `0` 和负数，显式 NULL、小数、字符串、布尔值、超范围整数或错误 JSON 均在扫描前报错。每轮读取当前保存的配置，使用 `maturity_date < DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL #{maturityOffsetDays} DAY), '%Y%m%d')`，仅绑定天数，日期由数据库计算，保持严格小于、不含截止当天。例如 `{"poolIds":[15],"maturityOffsetDays":3}` 表示早于三天前才出池；`0` 为早于今天，`-1` 为早于明天；原有 `poolIds` 与关系配置取并集的规则不变
2. 扫描 `ip_pool_status` 中已生效且未删除的债券，包含符合条件的 ABS、CRMW。按证券代码关联 `rrs_securityinfo` 取到期日。`ip_pool_status_crmw` 中的凭证与标的组合由 `crmw_expired_auto_out` 扫描。
3. 按在池记录 ID 剔除 Excel 调入、同批配套入池和 Excel 主体跟随补入的保护记录。目标池若配置 `out_restrict`（老关系类型 12），扫描候选时同时判断该证券是否在任一限制池中。命中则跳过，并记录证券和跳过原因。
4. 仅 `deleteUnprotectedPoolStatusSoft` 实际删到记录才写 `ip_adjust_log` 并计入影响条数；未删到时按已被其他处理提前出池跳过。
5. 证券编码为空的候选记录只跳过，不影响其他证券继续处理。
6. 最近执行结果展示扫描候选数、实际出池数、未处理数，并按目标池列出扫描、出池和未处理数量；过程日志按池记录到期命中数、Excel 保护跳过数和未处理原因。

### 7.1 `crmw_expired_auto_out`（23:05）

对应老 IP_RULE `AdjustRuleCrmwDueOutPool`：

1. 扫描 `ip_pool_status_crmw` 中已生效且未删除的凭证与标的组合，包含 ABS 债券作为标的的组合。`ip_pool_status` 中的 CRMW 券由 `security_expired_auto_out` 扫描。
2. 按凭证 `crmw_scode` 关联 `rrs_securityinfo` 读取到期日。凭证达到出池阈值时调出整条组合。`maturityOffsetDays` 的默认值、校验和截止日期计算同 7.0 节，两任务独立配置，例如 `{"poolIds":[18],"maturityOffsetDays":1}`，省略天数仍沿用早于昨天（T-2）的口径。
3. 凭证未达到出池阈值时，即使标的证券已到期，本任务也不会调出该组合。
4. 扫描候选时按标的证券代码、凭证代码和凭证类型匹配调出限制池。命中则跳过，仅软删成功才写日志。
5. 不走审批。
6. 最近执行结果展示实际出池总数，并按 CRMW 目标池列出出池数量。

### Excel 导入的在池保护

证券池 Excel 调入生效后，任务按当前在池记录的调库批次识别导入主项和同批配套项。由 Excel 主体调入派生的普通债跟随记录也受在池保护，遇到主体依据失效时由普通债跟随出池任务处理。到期等自动出池及涉及移除受保护记录的自动换码跳过该记录，自动调入需要互斥调出受保护成员时跳过该组。

定时出池、ABS 对账和观察名单同步通过同一 Mapper 查询批量读取受保护的在池记录 ID。候选查询读取任务自身的数据，服务按在池记录 ID 排除或标记。普通债跟随出池仅排除债券独立 Excel 入池记录。

### 7.2 主体外评任务

原入出池条件、认可机构规则和主体限制池校验继续执行。这两条任务只写主体池状态。

**外评非AA-及以下主体自动出池（仅主体）**

1. Demo Cron 为每天 23:10，默认关闭调度。参数示例 `{"poolIds":[17],"limitPoolIds":[]}`。扫描范围为 `poolIds` 与投资池关系配置中绑定本任务的自动调出池的并集，只处理已生效在池的主体。无有效认可评级机构或扫描范围为空时，本轮失败。
2. 取近一年认可机构的最低外评，同档取公告日期最新的一条。空评级或近一年无认可外评的主体不因本任务出池。
3. 黑名单质押库（17）的三个留池条件，满足任一项即留池。
   1. 主体当前已生效在债券禁止库（15）。
   2. 主体近一年认可机构的最低外评为 AA- 或更低。
   3. 主体当前已生效在重点观察名单（23）。

   三项均未命中且最低外评为 AA 及以上时，可进入出池处理。
4. 直接或同批经 Excel 调入的受保护记录，以及命中目标池调出限制池或位于 `limitPoolIds` 指定池的主体，均跳过。`limitPoolIds` 省略或为 `[]` 时不追加拦截。
5. 系统直接生效，写入调库日志并软删除目标池中的主体状态，调整原因记录任务名称和近一年最低外评。写日志或更新状态失败时，本轮回滚。旗下债券由独立任务同步，见 7.3 节。

当前候选 SQL 将无法识别档位的非空评级记为 `999`。近一年认可机构的非空评级全部无法识别时，主体也可能进入出池候选，后续仍执行留池和出池限制校验。

**外评AA-及以下主体自动入池（仅主体）**

1. Demo 每天 23:15 执行，默认关闭调度。`poolIds` 与投资池关系配置中绑定本任务的自动调入池取并集，Demo 为 `{"poolIds":[17]}`。未配置有效认可评级机构或扫描范围为空时，本轮失败。
2. 尚未在目标池生效的主体，满足禁止库（15）、近一年最低认可外评为 AA- 及以下、重点观察名单（23）任一条件即进入候选。外评同档取最新。按主体代码去重，保留各项命中依据。
3. 空评级或近一年无认可外评的主体仍可凭禁止库（15）或重点观察名单（23）条件入池。目标池为 17 时复核三项条件，命中目标池调入限制池的主体跳过。
4. 入池直接生效，写池状态及调库日志，调整原因记录任务名称和命中依据。写入失败时本轮回滚，旗下债券由 7.3 节的独立任务同步。

### 7.3 旗下债券独立同步

- 两项任务的 `poolIds` 均配置同池，Demo 为 `[15,16,17,23]`。跨池 `mappings` 在补债任务中使用 `companyInPoolId` 和 `bondTargetPoolId`，在跟随出池任务中使用 `bondPoolId` 和 `companyPoolId`。
- `company_inpool_bond_auto_in` 根据主体的生效池状态及同池、跨池映射筛选普通债和普通池内的 CRMW，兼容旧 `company_same_pool_bond_auto_in` 的池参数及绑定。普通主体依据适用到期天数门槛。`minDaysToMaturity` 须为非负整数，省略或填 `0` 时到期当天可入，填 `1` 时到期当天不入。任务排除退市、ABS、已转正的临时旧码和已在目标池的主券，扩展出的实际入池代码也须满足任务证券范围。
- 各映射候选合并后，普通主体依据由 `BatchSecurityCheckService` 与证券池批量页面共用单券准入规则，包括禁止库、市场、容量、来源、调入限制、开放日、评级及 17 主体三条件。同券关联、联动调入、互斥和反向限制池调出按组校验。任务集中读取涉及投资池的容量和当前状态，查询证券主档时一并复核实际代码的品种、到期及临时代码资格。累计整轮已通过计划的容量，按组写入池状态和调库日志。需要人工审批或缺少合规报告豁免的普通候选组跳过，并在任务明细记录券码、池名和原因。
- 主体 Excel 调整按主体和池取最近一笔生效决定，包含同批联动、互斥结果。Excel 主体调入候选先校验、先落地，跳过评级、到期天数、报告和普通直通条件，仍按池关系处理配套项并检查在途。Excel 调出使该主体池的依据失效，也阻止其他映射和联动重新补入明确调出的池。Excel 调出触发的跟随出池跳过 17 常规三条件，保留调出限制、互斥、在途检查和债券独立入池记录。
- `ExcelPoolExclusionService` 和同名 Mapper 专门读取并判定应排除的主体和债券记录。服务按审核时间（缺失时取创建时间）及流水 ID 选出最近一次生效的 Excel 决定，补入、跟随出池和联动回补校验共用该判定。普通债候选和主档查询仍由原 Mapper 提供，初筛和关联券复核在 Java 中共用本轮到期截止日。
- 普通债补入按池映射找候选，后续共享批次的证券数据按不同代码每批最多 500 条读取。跟随出池按目标池分批读取生效池状态和在途流程，根据删除成功的记录判断证券是否仍在池。跟随出池复核 17 三条件时，按发行主体去重后分批判定，分别匹配 15/23 池和认可外评，再合并命中的主体。
- 扫描数按候选记录统计。证券通过本轮同券关联或联动已实际进入目标池时，后续空结果不计入未处理数。实际入池数按成功写入的状态记录统计。
- ABS 由「主体下ABS债券自动入库（区分已确认发行人和未确认发行人）（仅债券）」处理。已确认发行人按主档身份判断。两个入池开关都开启时，才为未确认发行人的 ABS 生成入池提醒。同一债券池的多个显式主体来源合并判断。
- 主体调整使用 `COMP` 批次，独立债券同步使用 `BOND` 批次。Demo 普通债补入、出池和「主体下ABS债券自动入库（区分已确认发行人和未确认发行人）（仅债券）」分别每十分钟在第 1、3、5 分钟开始，调度开关由环境配置控制。需要启用已确认发行人的 ABS 跟随入池时设置 `enableIn=true`，并核对现有池范围和 cron。15 禁止库也按任务执行时间更新旗下债券。

**出池主体旗下债券自动出池（仅债券）**

1. Demo 每 10 分钟第 3 分执行，默认关闭调度。
2. `poolIds` 配置同池，Demo 为 `[15,16,17,23]`。跨池用 `mappings` 配置 `bondPoolId`（债券所在池）和 `companyPoolId`（主体依据池）。按债券目标池合并补债映射、历史主体同池、出池参数、自动调出规则绑定和联动池等主体依据。
3. 扫描普通投资池内已生效、可识别来源的普通债和 CRMW 跟随记录。ABS 债和独立 CRMW 组合由各自的任务处理。退市债仅在涉及生效的主体 Excel 调出时继续校验。
4. 任一条件成立即进入出池校验。
   1. 该主体在债券目标池的最新 Excel 决定是已生效的调出。此时其他依据池的留池状态不影响出池判定。
   2. 发行主体在所有依据池均无有效留池依据。
5. 保留来源不清和其他渠道独立入池的记录。命中调出限制池、调出互斥池或在途流程的债券跳过。
6. 黑名单质押库（17）常规出池时复核主体是否仍在债券禁止库（15）、近一年最低认可外评是否为 AA- 及以下、是否仍在重点观察名单（23）。满足任一项即留池。主体 Excel 调出触发时跳过这三项，仍执行第 5 条。
7. 软删除符合条件的跟随记录。证券仍有其他有效在池记录时，只写任务明细。目标池最后一条有效在池记录被撤销时，出池直接生效，写调库日志并计入实际出池。写日志失败时本轮回滚。

### 7.7 `bond_grade_inconformity_alert`（01:00）

对应老系统 `InconformityMaingrade2Job`。实现类 `GradeRuleAlertService`（`implements RrsScheduledTask`），**只提醒、不改池**。

**强担保豁免开关**：统一使用 `common/constants/BusinessSwitches.java` 中的代码常量 `BusinessSwitches.STRONG_GUARANTEE_ENABLED`，当前为 `false`。改为 `true` 可恢复豁免，修改后需重新编译并重启服务生效。关闭时，非 ABS 担保人内评 1 档也执行重点观察名单限制；开启时恢复原有重点观察名单准入及池间 `in_restrict` 豁免。普通担保债内评取较优值及矩阵规则不变，ABS、禁止池及其他限制池不享受该豁免。 此任务复用人工规则：关闭开关后，重点观察名单内的强担保债也按普通债规则生成提醒；开启后恢复豁免，下一轮扫描按原有机制失效不再命中的待处理提醒。

1. 扫已在信用债 1～5 级且 `audit_status=20` 的债券（`queryGradedBondInPoolList`；不含境外债，对齐老 polidEnum）。  
2. 扫描携带当前在池记录的 `adjust_log_id`，对每条调用 `SecurityPoolAdjustService.evaluateGradedInboundForPool`（含特殊债/担保债/观察池、重点观察名单）。**ABS 的评级主体在确认权益人时已固定写入主档发行人字段且不可更换，直接取主档 `inner_issuer_rating`，不查询入库快照**；**担保债可能对应多个担保人（主档 `guarantor_id` 为逗号多值串，无法判断当时选了哪一个），按该入库日志及证券代码精确读取快照，以当时选定的单一 `guarantor_id` 从 `ais_inv_analysis.v_inv_grade_result` 取 `ts` 最新的 `total_score`**；不使用主档或快照旧评分，不重选关系列表首条，不取同券其他入库记录代替。普通非担保债继续使用原主体内评口径。重点观察名单已在一至四级时，先按完整主体债入池规则复核五级库；存在可通过的五级库才提示“只能调入五级库或调出”，五级库也不满足时提示“只能调出”。
3. 仍允许则跳过；不再允许则按「证券 + 当前池 + 档位 + 不符合原因」生成 `context_key`：同券同池已有待处理则刷新原因与扫描时间（计入**本轮命中**）；同一摘要已由人工标记已处理时跳过（计入**沿用已处理**）；否则新增 `ip_grade_rule_alert`（`alert_status=00`，计入**本轮命中**）。
4. 本轮未再命中的待处理记录置 `99` 失效（计入**本轮失效**；含义：曾告警，现已合规或已不在扫描范围）。  
5. 再统计 `alert_status=00` 为**仍待处理**（执行摘要与影响条数以此为准；过程日志四项分列）。失效比较参数传秒级时间字符串 `yyyy-MM-dd HH:mm:ss`。  
6. 人工入口在「我的事宜」第三页签（[06](06-my-matters.md)）：`queryAlertPage` 列表、`editAlertProcessed` 标记已处理（不改 `ip_pool_status`，回写当前 `context_key` 供下一轮沿用）。「去调库」只打开证券池调整页，由人下调或出库；完成调库后可按需要再标记已处理。

担保债缺少来源入库日志、精确匹配的快照或实际担保人代码时，抛出包含证券代码、当前池及入库日志 ID 的数据异常，本轮失败并回滚，不执行旧待办失效处理；历史缺失数据须核实治理，不猜选主体。ABS 不再校验来源日志与快照，主体代码直接取主档发行人字段，历史缺快照也不会中止整轮扫描。实际主体已明确但内评为空时，ABS 保留“所选权益人无内评分”提醒并附主档发行人代码；非 ABS 担保债仍按人工规则使用发行主体内评参与较优值计算。矩阵、期限档与特殊债降档规则不变，不回写主档或历史快照。

回归测试：`GradeRuleAlertServiceTest` 覆盖 ABS 取主档发行人内评且不读快照、担保债取快照选定担保人最新内评、评级下调提醒、同券不同池各自担保人、缺失数据和旧待办失效边界；`GradeRuleAlertMapperTest` 用真实 XML 与 H2 验证来源日志映射和精确主体最新内评查询。当前无独立 `GradeRuleAlertApiTest`。

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

### 7.10 主体下ABS债券自动入库（区分已确认发行人和未确认发行人）（仅债券）（每 10 分钟，第 5/15/25…分）

任务编码为 `company_abs_bond_auto_in`，按池处理 ABS 进出和提醒。

1. 扫描池 = 扩展参数 `poolIds` ∪ 关系配置绑定本任务的池（`auto_in` / `auto_out`）∪ 所有已有生效主体的普通池 ∪ 当前已有 ABS 在池的池；并集为空时本轮失败。`pool_type='crmw'` 的组合池跳过。
2. 扩展参数：`enableIn`（默认 `false`）控制入池、`enableOut`（默认 `true`）控制出池，两者同时为 `false` 时本轮失败；`enableUnconfirmedIssuerIn`（默认 `false`）控制未确认发行人是否参与入池匹配，仅在 `enableIn=true` 时生效；`minDaysToMaturity`（默认 `0`）为距到期最小剩余天数，不足则不再补入；`mappings` 支持 `{bondPoolId, companyPoolId}` 跨池映射，配置后该债券池不再按同池口径对账。
3. **已确认发行人**（主档 `issuer_code` 有值）：分别按 `enableIn` 和 `enableOut` 跟随该主体的生效状态入池或出池。
4. **未确认发行人**（`issuer_code` 为空）：当 `enableIn` 和 `enableUnconfirmedIssuerIn` 都开启时，按担保人 / 差额支付承诺人 / 债务主体 / 原始权益人四类关系（`115004000` / `115203000` / `115200000` / `115201000`）匹配池内主体，**只生成 `absCompanySync` 规则提醒与 `audit_status=00` 的待确认调库日志**，不写 `ip_pool_status`；研究员可在「我的事宜 → 规则提醒」确认发行人并完成入池。
5. **出池保护**：出池前统一走 `AbsCompanySyncService#allowScheduledSync`，命中债级独立入库依据（`adjust_type` 为手工调整 / 手动批量调整 / Excel 导入）、未结束调库流程，或未确认 ABS 仍有其他关联主体在池时，只生成提醒不自动出池。
6. 入池前按 `minDaysToMaturity` 过滤：距到期不足该天数的 ABS 不再补入，`0` 表示到期当天仍可入，设 `1` 时排除到期当天。该参数只约束入池，已在池的 ABS 不受影响，到期清理由 `security_expired_auto_out` 按其 `maturityOffsetDays` 统一执行（省略默认 1，即早于昨天）。
7. 17（黑名单质押库）的入池与出池均再次校验主体三条件；入池按目标池 `market_codes` 校验市场，池未配置时不限制，市场缺失或不受支持时跳过该只并记告警，不中断整轮。
8. 入池前查调入限制池（关系 11）阻断；入池成功后按目标池 `in_mutex` 与反向 `in_restrict` 从 ABS 当前实际所在池自动调出并记 `adjust_type='互斥调整'` 日志。
9. 事务边界：按池提交，单池写入失败只回滚该池，其余池正常提交；结果摘要列出入池数、出池数、规则提醒数与失败池数。
10. 数据装载拆成四条无关联的批量查询（ABS 主档、四类关系、各池生效主体、各池生效 ABS），在内存做差集，不再把主体与跨库关系压进同一条 SQL。
11. 按目标池先出后入。两个阶段分别批量读取本阶段候选的最新联动提醒，出池阶段同时读在途审批，入池阶段同时读所在池和待确认入池日志。每只候选在同一阶段只处理一次，下个阶段或池重新读取。

> 启用任务时核对入池和出池开关，Demo 显式开启两个方向。



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
  │  按 SEC_ID 锁定主档，检查是否已完成或已是新码，使用主档当前代码准备拆分换码
  │
  ├─ prepareWindRegistration()
  │    查询主档当前代码对应的 rrs_temp_security_code 记录
  │    无登记：按旧证券信息新增，temp_security_code=旧码、status=temporary
  │            此时不填写正式码；temporary 主档缺少原登记时直接报错
  │    有登记：校验待转正状态、目标码、类型和市场后复用
  │    在 memo 中附加事件去重标记；复用记录的备注随后续转正更新一起保存
  │
  ├─ windCodeSyncMapper.editSecurityWindCodeToTemporary()
  │    先把旧主档改用临时 SEC_ID，释放正式 ID，并将状态置 D、来源置 temporary
  │
  ├─ windCodeSyncMapper.addSecurityWindCodeMaster()
  │    再新增新代码主档，使用原正式 SEC_ID 和主数据字段
  │    新代码来源：temporary 改为 temp_converted，official/temp_converted 保持不变
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
- 一条正式 `SEC_ID` 唯一对应一条有效主档，各市场证券的 SEC_ID 独立。按 SEC_ID 锁定当前主档后，先将旧主档改用临时 SEC_ID 并设为 `security_source=temporary`、`security_status=D`，再用原正式 SEC_ID 插入新主档，兼容 SEC_ID 唯一约束。两步与后续业务写入同事务提交。主档还不是新码时，新码不得占用其他主档；主档已经是新码时直接跳过，不新增临时代码记录和调库记录。
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
- 过程日志区分成功、主档缺失跳过、已是新码跳过、重复跳过、失败。主档缺失为 WARN，包含 SEC_ID、源记录旧新码和日期；日志中的“变更记录旧代码”是源数据旧码，实际出入库记录使用拆分前主档当前代码。失败项在日期窗口内重扫，超窗需核查并专项补执行；不修改 AIS 源结构，不补主档或 ID 映射。
- 任务执行状态与单条业务结果分别统计：扫描和逐条处理正常完成即返回任务成功，即使存在单条转换失败或全部单条失败，也保留 ERROR 明细、失败条数和实际成功影响数；只有扫描或其他任务级异常导致执行未完成时返回任务失败。
- 实际成功换码固定向 `rrs_temp_security_code_update_log` 写一条登记映射（`replace_table_name='rrs_temp_security_code'`、`replace_record_id=登记ID`），记录主档实际旧码到最新新码，无业务引用时同样写入；受影响的业务记录另存原有转换明细。主档缺失、已是新码、已完成变更等跳过不新增日志；日志写入失败与主档、登记和业务转换一起回滚。
- 人工新增 ID 当前为 `TEMP_临时代码`，未与 Wind 真实 SEC_ID 建立对应时按主档缺失处理，不按名称或代码猜测证券身份。
- Demo 包含 14 条换码记录，日期按数据库 NOW() 动态生成；选取最新记录后候选 11 条，首次成功 10 条、主档缺失跳过 1 条、已是新码跳过 0 条、失败 0 条。重复执行时成功 0 条、重复跳过 10 条、主档缺失跳过 1 条，不新增业务记录。XYB 的较早记录、同码记录和窗口外历史不处理；匹配主档的 SEC_ID 唯一且不超过 10 位。
- 初始化范围：表结构就绪后，可使用脚本工具完整初始化 Demo（`INIT_ALL_DEMO`）。按模块准备时，需恢复 `rrs_external_import_demo_data.sql`、`rrs_temp_security_code_demo_data.sql`、`rrs_dict_demo_data.sql`、`rrs_pool_init_demo_data.sql`、`rrs_security_pool_adjust_demo_data.sql`、`rrs_crmw_pool_status_demo_data.sql` 和 `ais_inv_ods_demo_data.sql`。无论使用哪种方式，从头演示前还须清空普通/CRMW 调库快照（这两张表不由 Demo 脚本灌数）。任务先保持关闭，完整恢复后手动执行；只重置 ODS 事件不会恢复已换码主档、登记或业务状态。从头再演示也须恢复相同范围。

| 事件 | 初始情况 / 编码 | 预期处理 |
|------|-----------------|----------|
| `WCH_DEMO_001` | official 无登记，`108008901.IB→108008906.IB`，二级库在池 | 保留 official，新增登记，旧码出库、新码入库 |
| `WCH_DEMO_002/003` | 同一 SEC_ID 历史为 `XYB001.IB→XYB006.IB→XYB007.IB`，昨天/今天两条的 S_INFO_WINDCODE 均为 XYB007.IB，主档仍为 XYB001.IB | 仅取今天的 003，主档、临时代码记录和调库均直接执行 XYB001.IB→XYB007.IB，不生成 XYB006.IB 的业务记录 |
| `WCH_DEMO_004/005` | official 无登记，`198101.SH→198106.SH`、`128101.SZ→128106.SZ` | 沪深各自按独立 SEC_ID 换码，不因同券关联额外处理其他市场 |
| `WCH_DEMO_006` | 主档不存在，`WCDMISS001.IB→WCDMISS002.IB` | 特别提示并跳过，不创建主档、登记或调库记录 |
| `WCH_DEMO_007/008` | 旧新码相同 / 前天历史记录 | 被扫描条件过滤 |
| `WCH_DEMO_009` | temporary 有待转正登记，`WCTMP001.IB→WCTMP006.IB`，旧码已在观察池（16） | 复用 id=4 登记；新码来源为 temp_converted，市场 NIB；观察池旧出新入 |
| `WCH_DEMO_010` | temp_converted 有转正历史，`ABN002.IB→ABN007.IB`，无业务引用 | 保留来源及 TMP002.IB→ABN002.IB 原登记/日志，新增 ABN002.IB→ABN007.IB 登记，无调库流水 |
| `WCH_DEMO_011` | official 无登记，`MTN001.IB→MTN006.IB`，三级库及 CRMW 库在池 | 两类持仓各自生成旧出新入，CRMW 凭证 CRMW001.IB 不变，原终态日志保留旧码 |
| `WCH_DEMO_012` | official 无登记，`GSB001.SH→GSB006.SH`，一级库申请待审批 | 原申请 id=8、批次、流程及步骤不变，更新代码和快照，不提前生成在池状态 |
| `WCH_DEMO_013` | 主档 SEC_ID=S9001046、当前码 PPN001.IB，最新记录为 `PPN000.IB→PPN006.IB`，源记录旧码不存在 | 按 SEC_ID 直接换成 PPN006.IB，新增 PPN001.IB→PPN006.IB 记录；不创建 PPN000.IB |
| `WCH_DEMO_014` | `WCTMP002.IB→WCTMP007.IB`，旧码主档为 temporary，SEC_ID=S9001052，初始无调库业务 | 复用 id=5 登记，写成功映射；旧主档置 D 并使用 TEMP_WCTMP002.IB，新主档承接 S9001052、来源为 temp_converted；无新增池状态、调库流水或快照 |

`WCTMP001.IB` 和 `WCTMP002.IB` 均演示“外部已补齐真实 SEC_ID”的待转正场景。主档分别为 `S9001050` 和 `S9001052`，各自的登记仍保留创建时的 TEMP_ 身份。Wind 任务按已匹配的 SEC_ID 查主档。原 TMP001.IB / TMP003.IB 人工录入及取消样例继续保留。
- 测试：`WindCodeChangeDemoDataTest` 原样执行 Demo、生产 Mapper 和事务，验证四类来源、最新历史、旧临时代码失效主档与新代码主档、普通/CRMW 出入库、在途与快照、历史保留和重复执行。`WindCodeSyncServiceTest` 验证数据库扫描接口、最新日期并列冲突、相同新码去重和失败隔离；`TempSecurityCodePersistenceTest` 验证数据库日期边界、相同/不同 S_INFO_WINDCODE 的最新记录、OPDATE 不决定新旧、主档拆分保存、已是新码跳过、源记录不被改写，以及原事务回滚规则。异常数据仅在隔离测试中建立（H2 测试使用 JDK 17）。
- Demo 编码只变化数字末位，市场后缀不变；XYB 历史为 `XYB001.IB→XYB006.IB→XYB007.IB`，本次实际只做 `XYB001.IB→XYB007.IB`。


## ABS 主体联动及任意池规则提醒

「主体下ABS债券自动入库（区分已确认发行人和未确认发行人）（仅债券）」在 `enableIn=true` 时，已确认发行人的 ABS 只按主档 `issuer_code` 匹配依据池。未确认发行人的 ABS 需同时开启 `enableUnconfirmedIssuerIn`，四类关联主体任一命中时生成 `absCompanySync` 入池提醒与 `absIdentityPending` 待确认日志。研究员在「我的事宜 → 规则提醒」确认主体后，日志转为 `audit_status=20`，更新池状态并固定发行人。忽略入池则将待确认日志置为无效。CRMW 组合池使用独立流程。

主体退出后，其他关联主体仍命中同池、ABS 有债级独立入池依据或存在未结束流程时，生成 `absCompanySync` 规则提醒，暂不自动出库。只有依据明确解除且符合原自动出库条件时继续自动出库。研究员可保留或发起人工出库；现有硬限制（含17三条件）继续执行。研究员确认主体后，原待确认调库日志的 `adjust_reason` 同步改为已确认主体且已完成入池，避免 `audit_status=20` 仍显示待确认。

提醒适用于任意目标池，和分级规则提醒共用“我的事宜—规则提醒”。`ip_grade_rule_alert.rule_type` 区分规则，`context_key` 标识主体身份、各关联主体当前池状态及最近生效日志版本。相同依据不重复生成待办；已人工处理的依据不立即触发反向自动调库；主体重新进出池形成新版本后重新判断。ABS 提醒中的“确认主体”在同一事务内更新主档五字段、将 `absIdentityPending` 待确认日志转为 `audit_status=20`、写入 `ip_pool_status` 并处理关系池；“忽略入池”将待确认日志置为 `audit_status=-1`，不写入池状态。改造前已经直接落池的旧记录仍按原逻辑兼容软删除；不删除 `ip_adjust_step`、快照或人工调库历史。分级扫描失效处理只清理原分级规则提醒。

原批量预读 SELECT 次数公式描述常规数据预读；ABS 还执行主体身份及联动依据的实时复核，不把旧的初始池状态当作最终结果。

### CRMW规则提醒的依赖评估

定时任务由独立 `CrmwRuleAlertTaskService implements RrsScheduledTask` 实现：`execute()` 只包装计时、起止日志、任务级异常和结果；私有 `doScanCrmwRuleAlerts()` 处理扫描及待办维护，使用独立 `CrmwRuleAlertTaskMapper`。失败结果保留过程日志，并标记本轮事务回滚。页面查询与人工处理由 `CrmwRuleAlertService` 负责，不混入定时任务类。

1. 读取 `ip_pool_status` 主体名单与 `ip_pool_status_crmw` 组合状态，仅写 `ip_crmw_rule_alert` 及审计。
2. 晚于23:05到期清理及06:00名单同步；不依赖其他提醒任务。
3. 不自动出池或入禁止库，不覆盖后续调库；下轮扫描失效不再命中的待办。
4. 09:13避开每10分钟整点补债、第5分钟ABS对账、第12分钟换码、每6分钟导出；仍需按前序实际耗时核对。
5. Demo默认关闭，无扩展参数，cron与清单和执行顺序同步。实现与启用步骤见[47](47-crmw-rule-alert.md)。

### CRMW关联禁投自动出库的依赖评估

1. 扫描和出库都在 `CrmwForbiddenAutoOutService`：`execute()` → 私有 `doAutoOutForbidden()` → 通过ObjectProvider取得任务代理后调用本类公开方法 `editCrmwForbiddenOutByJob()`；后者使用 `@Transactional(rollbackFor = Exception.class)` 管理单组合事务，任务入口不加整体事务、不使用TransactionTemplate，也不依赖CrmwPoolAdjustService。人工或导入入口不改，CRMW调入及最终审批校验另行完善
2. Cron为 `0 7/10 * * * ?`、Demo默认关闭；依赖当前已生效禁止库状态，允许从任意手工、导入、主体联动或自动任务产生的记录统一清退
3. 组合通常一千多条、最多不到一万条，一次读取全部候选，不分页不排序；主档单表批查、禁止库两表批查，不使用全量池映射或多大表复合关联
4. 每个命中组合独立事务，该条主档异常或状态、日志写入失败时只记录该条错误并继续；正常组合照常提交。结果汇总实际出库和失败数量，不新增禁止库记录；只有公共查询异常才返回任务级失败
5. 23:07在到期出池之后，23:27在补债／ABS对账之后；01:07早于01:10全量导出，09:07早于09:13提醒；不同taskCode仍无编排，错开不保证前序完成，按实际耗时留间隔
6. 清退结果由原CRMW历史及恒生增量导出读取；规则提醒在下轮扫描失效已退出组合。详细规则、数据错误和部署见 [48](48-crmw-forbidden-auto-out.md)
