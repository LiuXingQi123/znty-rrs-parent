# 我的事宜：业务隔离与入口显示

## 证券身份与市场代码（当前接口契约）

- `rrs_securityinfo.isin_code` 为非空主键；正式券使用真实 ISIN，临时券使用 `TEMP:临时代码`。不再提供 `wind_code` 总码，也不增加 `security_ref_type`。
- 调库、池状态、收藏、报告、提醒及批量缓存按 `isinCode` 识别整券；同券同池只占一份容量，同券不同市场码不能绕过已在池和在途检查。主体、独立基金、股票继续使用原业务代码。
- 证券代码搜索传 `securityKeyword`，后端在括号内对 `wind_code_sh`、`wind_code_sz`、`wind_code_nib`、`wind_code_bj`、`wind_code_nbc` 做 OR 模糊筛选。任一市场符合目标池配置即可整券入池，全部有值代码同步生效、同步出池；仅审批通过 `20` 落池。
- 页面“证券代码”保留一列，通过各页面 `new Vue` 内的方法与 Element UI 插槽按沪、深、银行间、北、其他顺序换行展示全部有值代码，仅显示代码本身、不加市场名前缀；空值不占位，长码可折行；可点击代码与证券简称使用相同链接样式，普通代码继承表格文字样式。点击任一码打开同一 ISIN；临时券明确标示临时来源。无公共证券代码脚本或自定义组件。
- CRMW 凭证只使用银行间编码。`crmwIsinCode` 标识凭证、`isinCode` 标识标的，两者与目标池组成业务组合；`crmwScode` / `crmw_scode` 只作银行间代码快照，不作身份。
- POST 路径及响应包装不变。旧代码身份请求明确失败；旧 URL、导入校验草稿须重新选择或重新上传。不能使用状态行 `id` 代替证券身份；日志 ID、附件 ID 仍各有其用途。
- 通用证券 Excel 导出分别提供 ISIN 与五码列；历史及打印读取业务快照。恒生按实际市场码展开，增量删除沿用原入池事件代码，不能输出 ISIN 或临时代码作为正式银行间代码。


## 1. 入口与范围

页面 `pages/my_matters.html`，接口前缀 `/api/v1/myMatters`。页面先查询可显示的业务入口，事项查询按所选业务调用独立查询模块。

| 业务 | 查询服务 / Mapper | 运行表 | 场景 |
|---|---|---|---|
| `bond` 债券 | `BondMyMattersService` / `BondMyMattersMapper.xml` | `ip_adjust_log`、`ip_adjust_step` | `securityAdjust`、`forbiddenCompanyAdjust`、`crmwAdjust` |
| `fund` 基金 | `FundMyMattersService` / `FundMyMattersMapper.xml` | `ip_adjust_log_fund`、`ip_adjust_step_fund` | `fundAdjust` |
| `stock` 股票 | `StockMyMattersService` / `StockMyMattersMapper.xml` | `ip_adjust_log_stock`、`ip_adjust_step_stock` | `stockAdjust` |

不按角色名选择 SQL，不将各业务合并为大 SQL。用户和角色名单只决定页面显示哪些业务入口，当前业务决定查询模块。投资池查看、调整或导入权限独立。

## 2. 业务入口显示

`MyMattersService` 直接调用 `BusinessPermissionMapper.queryBusinessDomainList(Long)`，由现有 Mapper XML 的固定用户/角色名单返回 `BusinessDomainDto`，仅含 `businessDomain`。

| 固定入口对象 | 显示业务 |
|---|---|
| 用户 1 | bond / fund / stock |
| 用户 5 | stock |
| 用户 3 | fund |
| 角色 1～6 | bond |
| 角色 7～9 | fund |
| 角色 10 | bond / fund / stock |
| 角色 2、3、7、8、9 | stock（与原业务取并集） |

- 用户直接配置与直接所属启用角色的业务取并集并去重。角色来自 `ais_inv_analysis.t_sys_user_role` 与 `t_sys_role.enable=1`，不继承父子角色。
- 入口仅在新页面 `mounted` 时查询一次；返回工作台页签、切换业务、查询和分页只刷新数据，不重新查询入口。
- 固定名单不会为保留管理员 ID 自动添加入口；无匹配名单的 `10000–10100` 用户不显示业务入口。
- 后端事项分页、流程下拉、详情、步骤、附件、审批和提醒不校验入口名单。业务缺失或未知业务仍返回明确业务错误。
- 事项范围与审批接管沿用 `AdminUserIdUtil`：用户 ID `1` 或整数闭区间 `10000–10100` 为管理员；普通用户仅处理本人待办，不能处理他人或无处理人的步骤。管理员同时有自己的待办时优先处理本人待办，沿用现有代办意见标识。
- 原会签、抢占、审批状态、驳回修改、发起人回避和事务校验保留。

首版仍使用纯前端演示登录和请求中的 `currentUserId` / `handlerId`，用于演示验证。正式环境必须改为可信的后端登录上下文，不能信任客户端提交的用户身份。

## 3. 各状态范围

| 页签 | 普通用户 | 管理员 |
|---|---|---|
| 待处理 pending | 当前步骤处理人是本人，取该申请最新本人 pending 步骤 | 该业务全部待处理事项 |
| 已完成 completed | 本人参与且整个批次不存在 pending，取最新步骤 | 该业务全部已结束事项 |
| 我发起的 initiated | `adjuster_id=currentUserId`，含进行中及结束 | 同样只查本人发起 |

流程下拉只取该业务可见的本人发起/参与事项涉及的有效流程；管理员可取该业务全部事项的流程。不同业务独立 SQL、筛选和分页。

## 4. 接口

以下均为 POST，路径带 `/api/v1/`。

| 路径 | 必传字段 | 返回 |
|---|---|---|
| `myMatters/queryBusinessDomainList` | currentUserId | `[{businessDomain}]`，固定 bond→fund→stock 顺序，只返回已接入且符合显示名单的业务 |
| `myMatters/queryMyMattersPage` | businessDomain,currentUserId,stepStatus(pending/completed) | `PageResult<MyMattersDto>` |
| `myMatters/queryMyInitiatedMattersPage` | businessDomain,currentUserId | `PageResult<MyMattersDto>` |
| `myMatters/queryFlowOptionList` | businessDomain,currentUserId | `List<FlowOptionDto>` |

分页与我发起的支持 `flowIds`、`securityCode`、`securityShortName`、`startDateStart/End`、`processDescription`、`auditStatus`、`pageIndex/pageSize`；待处理/已完成还支持 `initiatorName`。兼容原请求字段名称，基金/股票 Mapper 将代码/名称筛选映射为各自主档列。

列表摘要增加布尔值 `canProcess`：仅审核状态为 `00` / `11`，且存在当前用户实际可处理的 `pending` 步骤时返回 true；按各业务独立步骤表判断，不依赖列表代表步骤。普通用户限本人处理，普通发起人仅可处理 `initiator` 策略且审批节点具有 `submit` / `resubmit` 出边的提交或修改步骤；管理员沿用接管资格。审批提交仍重新校验数据库实际状态及权限。

列表摘要包含 `businessDomain`、`businessScene`、`objectCode/objectName`、`adjustLogId`、`adjustBatchNo`、`stepId`、目标池、流程名称、步骤名称、步骤/审核状态、流程描述、发起人及开始时间；保留各业务定位字段 `securityCode` / `crmwScode` / `fundCode` / `stockCode` 等。

各业务审批由原业务审批服务执行；不新增通用审批 SQL。附件查询必须带业务编码，后端只选择固定的债券/基金/股票日志表；附件读取与下载保留记录、文件和路径校验，不增加独立业务授权检查。

## 5. 页面与导航

- 仅有一个业务入口时自动使用该业务，隐藏顶部业务 Tabs 且不留占位；有多个入口时显示债券/基金/股票业务 Tabs，默认选择第一个并允许切换；无入口时保留空状态。下方保留待处理、已完成、我发起的；只有债券显示分级规则提醒。
- 新页面 mounted 时查询一次业务选项，默认选第一个显示业务；无业务入口时显示空状态。返回事项页只刷新当前业务的列表、流程选项和角标。
- 切换业务清空筛选、流程选项、列表及角标，重置分页并加载新业务。代码/名称标签随业务切换。
- 异步请求使用业务、视图版本及列表请求序号判定归属。旧响应、旧错误和债券→基金→债券的旧请求不能更新新视图。
- 进入页面或切换业务时：当前状态加载完整列表，其他状态以 pageSize=1 取 total；查询与状态切换只刷新当前状态及角标。基金和股票不请求债券提醒。
- 场景路由映射打开原有审核/详情页；基金进入 `fund_pool_adjust_approve.html` / `fund_pool_adjust_detail.html`，独立携带 fundCode 参数；股票进入 stock_pool_adjust_approve.html / stock_pool_adjust_detail.html，独立携带 stockCode 参数。
- 审核/详情页不查询业务入口。审核页优先展示本人待办，无本人待办时管理员（ID 为 1 或整数 10000～10100）展示首个待办，其他人返回 null 并禁用审批操作。后端提交沿用相同管理员口径和数据库实际步骤的处理资格校验。
- 待处理与我发起的均按后端 `canProcess` 显示操作：true 显示“处理”并打开审核页（entryMode=process）；false 显示“查看”并打开只读详情（entryMode=view）。已完成始终只读。流程描述链接与操作按钮使用同一判断，不仅按 Tab 或“流程中”判断。工作台页签键包含业务代码和场景、记录/批次，防止不同表相同 ID 混用；无工作台时回退 location.href。
- 债券保留证券、禁投主体、CRMW 路由；禁投 ABS 债仍走证券路由。
- 默认分页 1/20，page-sizes=[10,20,50,100]，保持原有日期、状态 Tag、池全路径和表格布局。

### 分级规则提醒

继续使用 `gradeRuleAlert/queryAlertPage` 与 `editAlertProcessed`，不检查业务入口名单。提醒保持共享口径，不按事项处理人过滤，不混进审批 SQL。「去调库」新开证券池调整页；标记处理只更新提醒，不改池。旧 `grade_rule_alert.html` 仍重定向本页 `?tab=gradeRuleAlert`。

## 6. SQL 名单维护

- 用户/角色固定入口名单只在 `BusinessPermissionMapper.xml/queryBusinessDomainList` 中维护；修改 XML 后重新部署生效。
- 每次真正加载我的事宜页面时重新查询 AIS 的直接所属启用角色。角色停用或移除用户的角色关联后，下次真正加载页面更新入口显示；用户直接配置不随角色移除消失，其他有效角色来源同样保留。
- 股票已接入独立查询模块、stockAdjust 场景和审核/详情路由；入口按用户 1、5 和角色 2、3、7、8、9、10 返回，不将股票查询合入债券/基金 Mapper。

首版不新增权限维护页面。

## 7. 验证

- `MyMattersMapperSqlTest`：真实 MyBatis + H2 执行业务入口与事项 SQL，覆盖固定用户/多角色并集、禁用角色、入口不隐式扩展管理员 ID、普通用户与旧管理员范围、独立待办/已办/我发起、同批次结束、业务筛选和流程选项。
- `MyMattersServiceTest`：入口直接查询、业务必传、独立业务分派，事项分页/我发起/流程下拉不再次读取入口名单。
- 新增跨债券/基金/股票的操作资格验证：管理员本人发起的待办、普通发起人回避、驳回修改、会签代表步骤已完成但本人仍有待办、无处理人及终态残留 pending；前端验证全部五类场景按钮与描述链接的处理/查看路由一致。
- 各业务 FlowService 原有回归用例保留会签、抢占、旧管理员代办及驳回修改验证。
- 前端 `node --test tests/my_matters.test.js`：脚本语法、业务重置、快速切换、旧响应与旧错误、角标、路由页签键、入口每页面一次查询、返回页签只刷新数据、附件业务透传、详情不读取入口和待办步骤定位。
