# 40 基金池批量调整

## 页面与操作

页面 `batch_fund_pool_adjust.html` 在工作台“研究管理 → 基金研究”中紧接“基金池调整”，位于“基金池Excel导入”之前；对应业务文档菜单同位。页面沿用证券池批量调整的投资池列表、两步工作台、材料区、报告选择弹窗、校验结果表、流程确认及固定操作底栏。

- 投资池列表仅展示当前用户具有调整权限、支持 `fund` 品种的启用叶子池；有效在池基金数量来自 `ip_pool_status_fund` 的 `audit_status='20'`、未删除记录。
- 选择目标池与调入/调出方向后，候选基金按代码、简称、基金产品类型、基金管理人筛选，字段与基金单笔列表一致。
- 调入排除已在目标池的有效成员，调出只保留已在目标池的有效成员；终止、退市基金不作为候选。
- 跨页保留基金勾选状态，支持移除单只及清空全部；报告和上传材料由整批共享。
- 点击“下一步”先校验基金评分、基金类型、分管领导审批三个必填字段，评分和审批值 `0` 均合法；通过后逐基金校验，展示完整手工、联动、互斥项、阻断原因、警告及一般流程。返回上一步保留已选基金、材料和三个字段，重新校验更新结果与流程选择。
- 调整原因和建议整批选填，页面输入各最多 500 字。提交成功返回投资池列表并刷新，提交期间禁用重复点击。
- 不展示“是否放开规则”，不增加债券主体、担保人或权益人字段。

## 整批基金调库信息

“基金调库信息”位于工作台公共区域、可选基金之前，第一步和第二步均可填写，第二步仍可编辑。三个字段整批统一填写，跨步骤保留；提交时复制到全部有效基金组的主项、联动及互斥日志，沿用基金单笔的采集保存语义，不改变准入规则或审批节点。

| 页面字段 | 请求字段 | 约束 |
|---|---|---|
| 基金评分 | `fundScore` | 必填合法数字，允许 `0`；遵守既有 `DECIMAL(10,4)`，最多六位整数、四位小数，超精度明确失败；不新增评分准入范围 |
| 基金类型 | `fundInvestmentType` | 必填，`stock` 股票型 / `equity_hybrid` 偏股混合型 / `money_market` 货币型 / `bond_hybrid` 偏债混合型 / `other` 其余类型 |
| 分管领导审批 | `needRiskLeaderApproval` | 必选 `0` 否 / `1` 是，`0` 为有效值；仅记录，不据此增加审批节点 |

“基金类型”指内部投资分类 `fundInvestmentType`，与候选基金主档的“基金产品类型” `securityType` 分开展示和保存。三个字段均由提交根请求提供，客户端明细不能另设每只基金不同值。

## 接口与数据契约

统一前缀 `/api/v1/batchFundPoolAdjust`，全部使用 POST；标准响应为 `ApiResponse<T>`，分页数据使用 `PageResult<T>`。

| 接口 | 主要入参 | 返回及用途 |
|---|---|---|
| `queryPoolPage` | `currentUserId/poolIds/pageIndex/pageSize` | 当前用户可调整基金叶子池分页、投资池全路径和有效在池数量 |
| `queryFundPage` | `currentUserId/poolId/direction/fundCode/fundShortName/securityType/fundAdministrator/pageIndex/pageSize` | 按目标池与方向过滤的基金候选分页 |
| `checkAdjust` | `currentUserId/poolId/direction/funds:[{fundCode}]` | `items` 使用单笔基金校验结构，含分组键、手工/联动/互斥标记、方向、校验结果及一般流程候选 |
| `addAdjustLog` | JSON 提交根参数与完整 `items` | 无本次上传文件的批量提交，返回 `fundCount/submitCount/logIds/adjustBatchNos` |
| `addAdjustLogWithFiles` | multipart：`request` JSON、`files`、`originalFileNameListJson` | 同一批共享报告、材料文件并提交，返回同上 |

公开批量参数 `direction` 使用 `in/out`；逐基金调用单笔服务前转换为 `AdjustMode` 的 `调入/调出` code。返回校验明细以及提交 `items[].adjustMode` 保持单笔中文 code，不混用公开方向值。

提交根参数包含 `currentUserId/poolId/direction/fundScore/fundInvestmentType/needRiskLeaderApproval/adjustReason/adjustAdvice/adjusterId/adjusterName/items`。有效主项选择服务端提供且可选的一般流程，将 `flowId/flowKey/flowType` 和对应关系项完整回传。报告与材料沿用单笔字段 `reportFileIndexes/materialFileIndexes/reportSourceAttachmentIds/materialSourceAttachmentIds`，上传索引对应整批 `files`。

multipart 原始文件名数组与文件顺序一致，保留中文文件名；数组数量与文件数不一致时拒绝提交。附件复用基金专属分类及 `sys_attachment`，同一物理上传文件按各基金的手工主项日志分别绑定，关系项不另绑材料。

## 校验、分组与提交

- 批量层负责目标池权限、候选分页和多基金编排；每只基金调用 `FundPoolAdjustService.checkAdjust`，复用品种、市场、状态、容量、在途、来源、限制、联动互斥、冻结期、开放日与一般流程规则。报告要求在提交阶段按基金单笔规则复核。
- 不注入池配置的批量流程，不读取快速流程；每组主项选择单笔服务返回的一般流程，关系项跟随其主项，流程下拉可完整展开查看候选。
- 某基金组校验失败时，整组不提交，不能拆出该组的联动/互斥项。可提交其他校验通过的完整组；全部失败或没有有效主项时不可提交。
- 有效基金组必须包含服务端展开的全部关系项；互斥项允许与整批主方向相反，不能统一过滤为根方向。
- 提交重新读取基金主档，不信任客户端名称、产品类型、池名和可调整状态；按基金主档主键顺序锁定，先统一复核所有组，再逐组保存，防止本批新建流程阻断后续组。
- 批量使用基金单笔的通用多单提交入口；Excel 导入仍保留自己的权限、报告、渠道标签、清空和服务器暂存快照限制。
- 每只基金的主项及关系项共用一个 `FUND` 批次号，不同基金拥有独立批次。返回基金数量与实际日志条数，关系项计入日志条数，不重复计入基金数量。
- 所有有效组共用外层事务，任一最新复核、附件绑定或日志/步骤写入失败，整批回滚；不能反馈部分保存成功。基金现有在途与短时间重复提交规则继续适用。
- 提交只写基金独立日志及步骤，初始 `audit_status='00'`；后续审批继续 `FundPoolAdjustFlowService`，仅最终审批通过 `20` 时更新基金池状态。

## 数据表与附件

无需建表、迁移或新增 SQL 脚本，复用既有基金业务表及公共配置：

- `rrs_fundinfo`：候选基金和提交时权威基础信息。
- `ip_investment_pool/ip_pool_permission/ip_pool_relation/ip_pool_open_day`：池定义、权限、关系和开放日。
- `ip_pool_status_fund`：有效在池数量、候选过滤及最终审批生效状态。
- `ip_adjust_log_fund/ip_adjust_step_fund`：逐基金每池日志、共享三字段、独立批次与运行审批步骤。
- 已发布 `wf_flow_*`、审批配置及 AIS 用户角色：一般流程快照与处理人。
- `rrs_report_in/rrs_report_out/sys_attachment`：报告选择与文件绑定；`table_name='ip_adjust_log_fund'`。

基金附件分类仍为 `fund_report_hand/fund_report_in/fund_report_out/fund_material_hand/fund_material_in/fund_material_out`。报告限制在提交阶段按单笔规则复核，整批共享材料不会绕过需要内部报告的目标池要求。

## 测试与验收

- `BatchFundPoolAdjustServiceTest`：目标池权限、启用叶子池、基金品种，调入/调出候选，评分精度与有效 `0`、投资类型及审批 `0/1`，逐基金调用、报告限制、一般流程、完整关系项、反向互斥及失败组隔离，共享附件、独立批次、防重复与整批失败回滚。
- `BatchFundPoolAdjustApiTest`：五个 POST 路由、请求反序列化、统一响应、multipart JSON 和原始中文文件名。
- `BatchFundPoolAdjustMapperTest`：真实 MyBatis XML 与 H2 验证有效基金主档、调入/调出候选、全部基金筛选条件，以及启用基金叶子池和去重在池数量，共 4 个用例。
- `SysAttachmentServiceTest`：共享附件只保存一份物理文件，独立保留各日志归属、分类与中文原名；普通单笔入口保持原存储方式，整批回滚清理共享物理文件。
- 前端 `tests/batch_fund_pool_adjust.test.js`：跨页勾选、移除/清空、三字段校验、有效组提交、关系项方向、流程、文件和重复点击保护。
- 回归 `FundPoolAdjust*Test` 与 `FundPoolExcelImport*Test`，确认通用多单入口不改变单笔或 Excel 既有行为；H2 集成用例使用 JDK 17，源码目标保持 Java 8。
- 工作台 iframe 检查两步页面、报告弹窗、返回与刷新，候选分页和末尾内容均不被固定底栏遮挡。

建议验证命令：`mvn "-Dtest=BatchFundPoolAdjust*Test" test` → `mvn "-Dtest=FundPoolAdjust*Test,FundPoolExcelImport*Test" test`；前端执行 `node --test tests/batch_fund_pool_adjust.test.js tests/fund_pool_excel_import.test.js`。
