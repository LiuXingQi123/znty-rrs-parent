# CRMW规则提醒

> 对应制度第十八条；业务入口：我的事宜（债券业务）→ CRMW规则提醒
> 定时任务实现：CrmwRuleAlertTaskService / CrmwRuleAlertTaskMapper
> 提醒页面实现：CrmwRuleAlertController / CrmwRuleAlertService / CrmwRuleAlertMapper

## 职责划分

| 部分 | 职责 |
|---|---|
| `CrmwRuleAlertTaskService.execute()` | 只处理计时、开始/结束日志、任务级异常、事务回滚标记及执行结果 |
| `CrmwRuleAlertTaskService.doScanCrmwRuleAlerts()` | 私有核心方法，负责风险扫描、提醒生成/刷新/失效及数量汇总，沿用 `doAutoOutExpired` 的任务写法 |
| `CrmwRuleAlertTaskMapper` / XML | 任务专用的组合扫描、提醒维护、统计及审计SQL |
| `CrmwRuleAlertController` → `CrmwRuleAlertService` → `CrmwRuleAlertMapper` / XML | 提醒页分页查询、标记已处理及人工处理审计，不实现调度接口，不持有任务编码或扫描入口 |

扫描DTO位于 `entity.schedule`，只继承表实体，不依赖提醒页展示DTO。两部分操作同一提醒表及审计表，各自使用独立Mapper；接口和任务编码保持不变。任务捕获异常后返回带完整过程日志的失败结果，同时标记整轮事务回滚。

## 业务角色示例

1. **标的债券：A 公司发行的债券**，是接受信用保护的对象，A 公司是标的债发行人
2. **CRMW 凭证：由 B 机构创设／提供信用保护**，B 机构是本任务所说的创设方／保护方
3. **创设方／保护方内评：B 机构最新的主体内评**，不等于 A 公司的主体内评，也不等于 CRMW 凭证自身的证券评级

本任务扫描的是“B 机构的 CRMW 凭证＋A 公司发行的标的债券＋当前投资池”这一组合。通过凭证代码 `crmw_scode` 查凭证主档，其 `issuer_code` 用于定位 B 机构；`security_code` 则指向 A 公司发行的标的债券。评级低于3档或进入观察／重点观察名单的判断对象均为 B 机构。

## 1. 扫描与主体口径

1. 每天09:13，任务编码 `crmw_rule_alert`，Cron `0 13 9 * * ?`，Demo默认关闭，无需扩展参数。
2. 扫描 `ip_pool_status_crmw.is_deleted=0`、`audit_status='20'`，且所在投资池有效、`pool_type='crmw'` 的组合。按凭证、标的证券和当前池分别处理；不是扫描普通信用债分级库。
3. 凭证主档 `rrs_securityinfo.wind_code=crmw_scode` 的 `issuer_code` 代表创设方或保护方；不使用标的债发行主体、标的债担保人或标的证券快照中的评级。
4. 按该主体代码查询 `ais_inv_analysis.v_inv_grade_result`，取 `ts` 最新记录的 `total_score`；最新空值不回退旧评分或主档旧评分。
5. 观察、重点观察按 `ip_pool_status` 的主体级记录（`security_type='company'`、未删除、审批通过）及有效池类型 `observe` / `restricted` 判断，不写池状态。

## 2. 规则

- 内评档位按启用的 `credit_bond_inner_rating_grade.sort_no` 比较，3档为分界；默认1、2+、2、2-、3+、3不触发评级提醒，3-、4触发。
- 创设方在观察或重点观察名单时独立触发，即使内评仍是1档也提醒；多个条件合并到同一组合、同一池的提醒原因。
- 每天检查当前风险状态，包含已有存量低评级，不要求检测评级变更事件。
- 仅生成待办，由研究员决定保留、CRMW组合出库或发起主体禁投调整。不会自动退出标的债的普通证券池。
- 四类对象已经在禁止库的组合由独立 [CRMW关联禁投自动出库](48-crmw-forbidden-auto-out.md) 任务清退；本提醒任务继续只处理低内评、观察和重点观察，不承担清退或重复禁投入库。
- 凭证主档、单一主体代码、最新有效内评或3档顺序缺失时报出具体凭证、标的、池、主体信息，整轮失败回滚，不关闭旧待办。

## 3. 待办生命周期

1. 组合键为凭证代码、标的代码、当前池ID。依据摘要包含该组合、当前入库日志ID、创设方代码、最新内评和原因。
2. 已有待处理记录时刷新；最近一条记录已处理且摘要相同时沿用结论；其他情况新增待处理记录。
3. 本轮不再命中的旧待处理记录失效，包括恢复合规、退出CRMW池或所在池失效。已处理记录保留。
4. `00=待处理 / 20=已处理 / 99=已失效`。新增、刷新、失效和人工处理均向 `_evt` 写入变更后快照；定时任务操作人为0，人工处理保留请求中的处理人。
5. 执行摘要列出本轮命中、沿用已处理、本轮失效、仍待处理；影响条数为仍待处理数量。

## 4. 接口与页面

全部POST，返回 `ApiResponse`。

| 接口 | 请求 | 返回 |
|---|---|---|
| `/api/v1/crmwRuleAlert/queryAlertPage` | crmwScode（包含匹配）、securityCode（前缀匹配）、alertStatus、pageIndex、pageSize | `PageResult<CrmwRuleAlertDto>` |
| `/api/v1/crmwRuleAlert/editAlertProcessed` | id、currentUserId、currentUserName（必传） | 更新后的提醒 |

独立Tab使用独立筛选、列表、分页、角标和请求序号，只在债券业务显示。URL `my_matters.html?tab=crmwRuleAlert` 可直达；与现有共享规则提醒口径一致，不额外按研究员归属筛选。

- 去CRMW调库：携带 `crmwScode + securityCode`，工作台新页签打开 `crmw_pool_adjust.html`。
- 主体禁投调整：携带创设方 `companyCode` 打开 `forbidden_pool_adjust.html`，仍由人工选择池和发起审批。
- 标记已处理：仅关闭待办并记录处理人、时间，不触发任何调库。跳转本身不会标记已处理。

## 5. 表与初始化

- `sql/rrs_crmw_rule_alert_schema.sql`：`ip_crmw_rule_alert` 和 `ip_crmw_rule_alert_evt`，主表保存组合、创设方、最新内评、当前池、来源日志、风险原因、依据摘要和处理信息。
- `sql/rrs_crmw_rule_alert_demo_data.sql`：仅清空本模块提醒和审计，无虚构预置提醒；实际提醒由扫描生成。
- 注册ScriptTool的schema/demo清单、模块重置、主库及债券批量初始化、清空表分组；调库运行态清空包含本模块表。
- 新库先执行建表。已有环境仅初始化新模块结构；在定时任务管理新增 `crmw_rule_alert` 配置后手动执行验证，再按需启用。不要为了增加本任务而重置其他任务配置或业务数据。

## 6. 定时任务依赖评估

1. 读取普通池主体名单及CRMW组合状态，仅写独立提醒和审计，不改变谁在池内。
2. 依赖23:05到期组合清理、06:00主体观察名单同步的结果。
3. 后序调库不会被本任务覆盖；按下轮扫描更新风险待办。
4. 09:13位于上述任务之后，避开每10分钟整点补债、第5分钟ABS对账、第12分钟Wind换码及每6分钟导出；不同任务仍无全局编排，启用时按实际耗时核对。
5. Demo及[29 定时任务](29-scheduled-task.md)的任务清单与执行顺序同步。

## 7. 回归测试

`CrmwRuleAlertTaskServiceTest`：3档边界、名单独立触发、原因合并、处理依据复用及变化、新旧待办、任务成功/失败包装和过程日志。
`CrmwRuleAlertServiceTest`：提醒页面人工处理，保留依据及处理人审计。
`CrmwRuleAlertMapperTest`：任务/页面两个真实XML和表结构、创设方最新内评、组合隔离、提醒生命周期、审计、不改池及整轮事务回滚（H2需Java11以上测试运行时）。
`CrmwRuleAlertApiTest`：独立路由及请求字段。
前端 `node --test tests/my_matters.test.js`：独立筛选分页、角标、跨业务隔离、跳转和人工处理。

## 8. 大表查询口径

1. 证券主档按几百万行、投资池按后续百万行考虑；不把凭证主档、池状态、投资池、评级和名单拼成一条多层关联SQL。
2. 任务候选只关联CRMW池状态和投资池，不做外层排序；无候选时不查询主档、评级、名单和最近提醒。
3. 凭证代码、创设方代码先去重，分别批量读取主档字段、最新评级和名单；只查询本轮涉及代码，不加载全部证券或全部投资池到内存。
4. 最新评级按本轮创设方分组MAX(ts)后匹配对应记录，最新空评分仍保留；同一主体多组合不重复查询，SQL不做逐组合ORDER BY + LIMIT。
5. 最近提醒按本轮凭证分组MAX(id)，每个凭证、标的、当前池只取最近有效记录，在内存中匹配；去掉循环中的逐条最近提醒查询。
6. 读取SQL最多涉及两张表，不使用相关子查询，候选及参考数据无需输出顺序；页面分页排序保留，避免影响用户列表顺序。
7. 回归测试验证无序候选集合、去重批量调用、最新空评分、最近有效提醒及事务回滚。尚未在百万级MySQL数据上压测，实际耗时需结合真实执行计划验证；本次不调整索引或表结构。
