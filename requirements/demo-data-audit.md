# Demo 数据检查与初始化说明

检查日期：2026-10-11。范围为 `ScriptToolService` 注册的全部 22 份 Demo，包含 AIS、外部证券主档、公共业务、基金和股票。验证仅使用 H2，没有执行实际 MySQL 的初始化、清空或迁移。

## 已修复的问题

| 范围 | 问题与修正 |
|---|---|
| 债券及主体调库 | 已通过日志缺少步骤；补齐开始、发起、复核、终审、自动处理和结束步骤。新增驳回修改、终态驳回、撤回样本，非通过记录不落池。 |
| `C10005` 禁止库 | 原日志没有流程，却伪造“直通落地”步骤；现按禁止库配置绑定流程 113，保留完整审批过程。主体进入 15 后命中黑名单质押库条件，同批联动进入 17；旗下 4 只未到期债券自动同步进入 15/17，补齐日志和池状态。自动同步日志没有独立审批流程，不伪造步骤。 |
| 主体批次 | 使用 `COMP` 前缀，与证券 `BOND`、凭证 `CRMW` 区分。早期已通过样本时间调整到流程版本发布之后。 |
| 主体待办 | 原观察池样本使用禁止库审批流程；改为禁止库申请，保持真实的人工审核待办。 |
| CRMW | 使用 CRMW 库配置的流程 112，日志与独立池状态保持一致。 |
| 信息快照 | 在现有证券调库、CRMW 池状态 Demo 内增加提交快照，保留 ISIN、各市场代码及对应主档字段，关联实际日志。无需新增 SQL 文件或注册项。 |
| 基金 | 补齐开始、O32 和结束步骤，修正终态驳回的复核→终审→结束路径。 |
| 股票 | 补齐开始、结束及终态驳回路径；为两条人工待办配置处理人，统一步骤节点名称和处理人姓名，排除发起人跨环节自审；池名称与投资池 Demo 对齐。 |
| 审批角色 | 原处理人配置把独立流程角色字典的编号当作 AIS 编号，存在不存在的角色及同号异名。补齐 AIS 审批角色 11～14，同步修改版本画布 JSON 和归一化审批处理人表，关联现有演示用户。 |
| 管理员角色 | 原 AIS Demo 的管理员用户没有角色关联，导致全部人员中所属角色为空、左侧角色树无管理员。补齐角色 15「管理员」，关联用户 1。 |

审批角色为：11 投资决策委员会（用户 16）、12 研究总监（用户 4）、13 基金经理（用户 7）、14 O32 系统自动审批（系统自动处理，无人工用户）。原业务入口名单和用户既有角色保留。

流程设计器读取 `wf_flow_version.canvas_nodes`，审批运行读取归一化配置。两处处理人必须同时维护；仅修正 `wf_node_approval_handler` 仍会让画布中的旧编号 9「研究总监」勾中 AIS 的「量化部」。本次复核补齐了上次遗漏的 20 处画布角色编号。`wf_role_dict` 是独立业务字典，不能将其编号作为 AIS 角色 ID。

## 验证范围

`DemoDataIntegrityTest` 按独立初始化任务的依赖顺序实际加载全部建表和 Demo SQL，并执行脚本工具已有的业务完整性规则及以下补充检查：

- 流程、版本、节点、连线、审批配置、用户角色、投资池关系与权限、规则及用例、评级矩阵的跨表引用。
- 全部版本画布中的角色、人员 ID 与姓名引用真实 AIS 主档，处理人及审批策略与归一化配置一致。
- 使用真实 FlowService/Mapper 查询角色树、全部人员及角色筛选：管理员角色可选且关联管理员，研究总监对应用户 4，量化部对应用户 14/15；设计器节点返回研究总监角色 12。
- 调库主档、流程标识、目标池名称和类型、步骤节点快照、批次号、处理人及姓名。
- 有效流程申请存在步骤，进行中存在待办，已通过批次存在结束步骤，终态没有残留待办。
- 人工待办处理人非空，同一申请不能由同一个人跨审核环节处理。
- 有效池状态来源于同对象同池的已通过调入日志，证券及 CRMW 调库具有对应提交快照。
- 禁止库主体同批联动进入黑名单质押库，旗下在演示落池日有效的债券同步进入 15/17。
- 临时证券占位、取消发行状态、转正主档，以及基金净值、股票评级和分管信息的主档关联。

快照按 Demo 初始化时的主档生成；演示的历史日期和业务记录均为虚构样本，不代表外部市场历史数据。H2 仅转换 MySQL 库名切换、表选项、JSON 存储、日期函数和多列 ALTER 语法，不连接外部数据库。

## 有意保留的空表和边界样本

- 导入临时表、流程运行缓存、规则运行记录及多数事件表由业务操作产生，初始化保持空白。
- 分级规则提醒由对应定时任务生成，不手工伪造待办。
- 临时基金登记脚本明确只清空，不创建登记；通过新增临时基金功能生成占位及登记。
- 股票退市、零行情、缺行情、缺评级，以及已删除报告属于查询和校验边界样本，保留其明确的业务含义。无效调库日志 `-1` 不要求人为补出审批步骤。
- 附件 Demo 只包含元数据，不创建对应 PDF 物理文件；列表和绑定关系可验证，实际预览或下载需要另行上传真实文件。

## 测试与初始化

在 `znty-rrs-parent` 下使用 Java 11 以上运行时执行：

```powershell
mvn "-Dtest=DemoDataIntegrityTest,ScriptToolServiceTest,ScheduledTaskDemoDataTest,RuleDemoScriptTest,CreditBondGradeRuleDemoDataTest,MyMattersMapperSqlTest" test
```

源码目标仍为 Java 8；测试所用 H2 2.3.232 需要 Java 11 以上。

如需重新建立完整演示环境，在开发演示库通过现有脚本工具按以下顺序执行：

1. AIS 建表和 Demo（`INIT_AIS_SCHEMA`、`INIT_AIS_DEMO`）。
2. 外部证券主档建表和 Demo（`INIT_EXTERNAL_IMPORT_SCHEMA`、`INIT_EXTERNAL_IMPORT_DEMO`）。
3. 公共业务建表和 Demo（`INIT_SCHEMA`、`INIT_DEMO`）。
4. 基金建表和 Demo（`INIT_FUND_SCHEMA`、`INIT_FUND_DEMO`）。
5. 股票建表和 Demo（`INIT_STOCK_SCHEMA`、`INIT_STOCK_DEMO`）。

公共收藏、附件和流程配置须先于股票脚本存在。公共收藏 Demo 会清空收藏表，所以完整初始化时先公共、后股票，避免覆盖股票追加的自选。`RESET_ALL` 不包含 AIS、外部主档、基金或股票，不能单独完成完整环境初始化。重新初始化会覆盖对应模块的现有数据；本次修改没有对当前数据库执行这些操作。

本次角色修正涉及 `sql/ais_inv_analysis_demo_data.sql` 和 `sql/rrs_flow_definition_demo_data.sql`；现有演示库须同步重新加载两份脚本后再关闭、重新打开流程定义页。只刷新页面无法更新已经落库的画布 JSON。两份脚本包含清空对应演示表的操作，不应直接用于需要保留自建用户或流程的环境。

## 修改文件

- Demo SQL：`sql/rrs_security_pool_adjust_demo_data.sql`、`sql/rrs_crmw_pool_status_demo_data.sql`、`sql/fund/rrs_fund_pool_adjust_demo_data.sql`、`sql/stock/rrs_stock_pool_adjust_demo_data.sql`、`sql/ais_inv_analysis_demo_data.sql`、`sql/rrs_flow_definition_demo_data.sql`。
- 回归测试：`src/test/java/com/znty/rrs/service/DemoDataIntegrityTest.java`、`src/test/java/com/znty/rrs/service/ScriptToolServiceTest.java`。
- 检查说明和快照约定：`requirements/demo-data-audit.md`、`requirements/README.md`、`AGENTS.md`、`CLAUDE.md`。
