# 39 基金临时代码管理

## 1. 范围与页面

页面 `temp_fund_code.html` 位于基金研究菜单，沿用证券临时代码管理的筛选、分页、状态标签及新增、更新正式基金弹窗。基金实现使用独立 `TempFundCodeController → TempFundCodeService → TempFundCodeMapper/XML`，本期仅提供人工操作，不新增定时任务。

- 新增只录入临时基金代码、简称、市场和产品类型四项，均为必填。基金类型只接受未删除且属于基金大类的字典项；市场使用现有市场字典。
- 正式基金只能从已有 `rrs_fundinfo` 搜索选择，返回代码、全称、简称、市场及产品类型。转正弹窗临时信息只读，正式信息由服务器主档回填。
- 列表支持代码、简称、状态、操作来源筛选及分页。来源默认 `manual`，本期所有变更均写 `manual`。
- 状态为 `temporary/updated/cancelled/deleted`；仅 `temporary` 可以转正、取消或删除，删除还须通过核心引用校验。
- 变更请求从 `RrsAuth` 读取当前操作人 `operatorId`，禁止由页面填写或使用固定人员 ID。

## 2. 接口与数据契约

前缀 `/api/v1/tempFundCode`，全部为 POST + JSON，请求类型 `TempFundCodeReq`，统一返回 `ApiResponse<T>`。

| 方法 | 请求关键字段 | 返回内容 |
|---|---|---|
| `queryTempFundCodePage` | `tempFundCode?`、`tempFundShortName?`、`statusList?`、`oprtSourceList?`、`pageIndex`、`pageSize` | `PageResult<TempFundCodeDto>` |
| `queryTempFundCodeOptions` | 空查询参数 | `securityTypes`，每项 `securityType/securityTypeName` |
| `queryFormalFundOptionList` | `fundKeyword?` | 正式选项，含 `fundCode/fundName/fundShortName/marketCode/securityType/securityTypeName` |
| `addTempFundCode` | `tempFundCode/tempFundShortName/tempMarketCode/tempSecurityType/operatorId` | 新增登记 DTO |
| `editTempFundCodeToUpdated` | `id/fundCode/operatorId` | 转正后的登记 DTO |
| `editTempFundCodeToCancelled` | `id/operatorId` | 取消后的登记 DTO |
| `deleteTempFundCode` | `id/operatorId` | 删除结果 |

列表 DTO 包含登记 ID、四个临时字段、正式基金字段快照、`status/oprtSource/updateTime` 及基础时间字段。产品类型名称来自数据库字典，状态、来源、市场的中文显示由前端维护。

## 3. 新增

1. 校验四项必填、字段长度、市场 code、基金产品类型及操作人；临时代码不可占用已有登记或基金主档代码。
2. 新增登记：`status=temporary/is_deleted=0/oprt_source=manual`，正式基金字段保持空。
3. 同一事务写入 `rrs_fundinfo` 占位基金，代码、简称、市场、类型取录入值，**全称等于简称**；明确写 `security_status='L'`，可进入基金申请、查询及 Excel 导入。其他未采集资料保持空，不推断补齐。
4. 写 `_evt` 的 INSERT 完整快照，包含操作人和操作时间。

不额外录入发行主体、担保人、权益人、净值、发行日期、到期日期或报告资料。

## 4. 转正

仅 `temporary` 且未删除登记可以转正。先按主档 ID 顺序锁定占位及正式主档，再锁定登记并复核状态；正式码必须不同于临时码，目标必须是有效正式基金，禁止映射到临时占位主档。

正式基金全称、简称、市场、类型完全从已有主档读取，保存登记的 `fund_code/fund_name/fund_short_name/market_code/security_type`；不接受客户端业务快照，也不覆盖正式主档。

### 在途申请

- 仅替换 `ip_adjust_log_fund` 中未删除、`audit_status in ('00','11')` 的临时基金引用，名称及类型同时采用正式主档；批次号、原因、评分、投资类型和风管审批等原申请信息保留。
- 保留既有 `ip_adjust_step_fund` 的节点、处理人和步骤状态，不重新创建流程、不主动代审。
- **正式基金在同池已有在途申请时仍允许替换**；后续审批继续按现有最终准入和待处理流程规则判断，不在转正时取消任一申请。

### 已在池

- 仅处理 `ip_pool_status_fund` 中未删除、`audit_status='20'` 的临时状态。
- 逐池保留临时调出与正式调入业务审计，逻辑删除临时状态；正式基金已经在相同池时只去重，不重复新增正式状态。
- 正式基金尚未在池时继承原业务信息建立正式状态及相应日志，不重走新的调库审批。
- 新正式入池日志复制原基金日志的附件关联；旧日志及旧附件关联保留。

每条实际替换业务记录写 `rrs_temp_fund_code_update_log`，`replace_status=success`。完成后登记改为 `updated`，占位主档置 `D`，写 UPDATE 事件。全部在同一事务内，失败整体回滚，不保存失败业务替换日志。

不迁移已终态基金调库日志、已提交 Excel 快照或基金净值；不对报告库历史代码执行全库替换，不操作证券、主体或 CRMW 表。

## 5. 取消与删除

取消仅限 `temporary`，**允许已有核心业务引用**。登记改为 `cancelled`，占位主档置 `D`，记录 UPDATE 事件；不撤回原申请、不删除原在池记录，不推进或恢复现有步骤。原申请后续继续现有规则，最终读取 `D` 主档时按既有基金状态校验失败，不新增自动拒绝或恢复逻辑。

删除仅允许未删除的 `temporary` 登记，软删除登记并写 `status=deleted/is_deleted=1` 和 DELETE 事件，保留审计信息。须确认 `ip_adjust_log_fund`、`ip_pool_status_fund` **没有任何未删除引用**；两表不按审核状态过滤，终态未删除日志同样阻止删除。**不删除、不禁用占位主档**，无引用时删除仍保留基金原有 `L` 状态，不借删除恢复已转正或已取消登记。

## 6. O32 节点

- 通过登记 `status=temporary/is_deleted=0` 识别有效临时基金，不依据代码前缀。
- 有效临时基金到达 O32 时创建人工待处理步骤，人工可以直接处理该节点，不按 O32 自动通过。
- 转正不改变已经创建的人工步骤，也不自动代审；后续 O32 读取最新代码，正式基金沿用既有自动通过。
- 取消后不再满足有效 temporary 判定，但旧步骤保留。普通 `auto` 节点和正式基金 O32 行为沿用现有规则。

申请及审核见 [33 基金池调整申请](33-fund-pool-adjust.md)、[36 基金池调库审核与详情](36-fund-pool-adjust-approve-detail.md)。

## 7. 表结构与初始化

| 表 | 用途 |
|---|---|
| `rrs_temp_fund_code` | 登记表，包含临时四字段、正式全称/简称/市场/类型快照及 `update_time` |
| `rrs_temp_fund_code_evt` | 全部主表字段快照，`evt_id` 主键，追加 `opter_id/opt_time/oprt_type` |
| `rrs_temp_fund_code_update_log` | 临时与正式快照、替换表名及记录 ID、成功状态、替换时间和操作人 |

非主键字段均允许 NULL，无物理外键、唯一或二级索引，新增主表包含 `crte_time/updt_time`。

- `sql/fund/rrs_temp_fund_code_schema.sql`：先用 `DROP TABLE IF EXISTS` 删除登记审计表、替换明细日志和登记主表，再用 `CREATE TABLE IF NOT EXISTS` 完整重建三表。
- `sql/fund/rrs_temp_fund_code_demo_data.sql`：三表空初始化，不灌入不配套占位基金样例。
- ScriptTool 注册正常 schema/demo 文件，建表脚本参与基金结构初始化；不设置动态 SQL 升级脚本或独立升级模块。
- 模块 `temp-fund-code` 清空三表，可清空表分组登记三表。`CLEAR_ADJUST_FLOW` 只增加替换日志，不清登记及事件表。

该建表脚本用于完整重建，执行会清除上述三表的原有数据。

## 8. 验证重点

- 四项录入、类型和市场、代码占用、正式选择、操作人及状态限制；占位全称等于简称、`L` 状态，被基金申请与 Excel 导入读取。
- 在途和在池分叉、正式码同池在途仍替换、正式码已在同池去重、历史保留、逐记录替换日志、步骤不改及附件关联继承。
- 取消允许引用、删除检查全部未删除核心引用并保留 `L` 主档、审计完整与事务回滚。
- 临时基金 O32 人工处理，转正后旧步骤不自动处理，普通 `auto` 与正式 O32 回归。
- 七个 API 路由和响应、页面筛选分页、按钮门禁、远程正式选项和错误提示。
