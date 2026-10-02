# 33 基金池调整申请

## 1. 功能范围

基金池调整申请独立于债券证券池调整，页面流程为：基金列表 → 基金详情与选池 → 校验确认 → 提交申请。基金业务使用 `rrs_fundinfo`、`ip_adjust_log_fund`、`ip_adjust_step_fund`、`ip_pool_status_fund`，不读写债券调库运行表，不创建基金快照。

申请页面负责基金调库申请链路；基金审核与只读详情见 [36-fund-pool-adjust-approve-detail.md](36-fund-pool-adjust-approve-detail.md)，调整历史由 [35-fund-pool-adjust-history.md](35-fund-pool-adjust-history.md) 独立实现。申请提交后保持 `audit_status='00'`；仅最终审批通过后写入 `ip_pool_status_fund`。

## 2. 页面与查询

- 列表筛选：基金代码、基金简称、基金产品类型、基金管理人。
- 列表字段：代码、简称、全称、产品类型、最新净值、成立日期、基金经理、管理人、最新规模、数据日期；点击基金代码或基金简称均使用相同链接样式并进入该基金的调库申请步骤。
- 基础信息全部只读：基金简称、代码、产品类型、最新净值、成立日期、基金经理、管理人、托管人、累计净值、投资风格、交易价格、发行规模；标题处显示当前调整人及基金记录更新时间。
- 最新净值是重点关注值，在基础信息中使用醒目的品牌色数值展示；可调入库和可调出库的树形池默认折叠。
- 第二步将基金评分、基金投资类型和风管领导审批选项放在独立的“基金调库信息”区，并使用与“基金基础信息”相同的三列边框描述表格布局；“原因和建议”单独展示在其下方，调整原因与调整建议均为非必填。
- 风管领导审批为必填单选项，使用 `el-radio-group` 提供“是 / 否”两个值。
- 第二步校验结果和提交日志中的投资池名称显示全路径（如“信用债大库(new)/一级库”）；申请流程候选说明显示“使用目标池配置的默认「流程名称」”。
- 导出 PDF 时不包含“基金调库信息”和“原因和建议”区。
- 不展示近三年财务数据、发行主体、担保人、权益人或主体所在池。
- 当前所在池只查询 `ip_pool_status_fund` 中 `audit_status='20'` 且未删除的记录。

## 3. 接口

统一前缀：`/api/v1/fundPoolAdjust`，全部使用 `POST`。

| 接口 | 说明 |
|---|---|
| `queryFundPage` | 分页查询未终止、未退市基金 |
| `queryFundTypeList` | 查询有效基金产品类型 |
| `queryFundDetail` | 查询基金只读基础信息 |
| `queryAdjustPoolList` | 查询当前用户可调整且支持基金品种、基金市场的投资池 |
| `queryFundPoolStatus` | 查询基金当前有效所在池 |
| `checkAdjust` | 校验并展开手工、联动和互斥调库项 |
| `addAdjustLog` | JSON 提交基金调库申请 |
| `addAdjustLogWithFiles` | multipart 提交申请及附件 |

## 4. 校验规则

- 校验用户调整权限、池启用状态、锁定状态、基金品种和基金市场。
- 校验基金状态、已在池/不在池、容量、待处理流程和短时间重复提交。
- 校验来源池、调入/调出限制、弹性限制、联动和互斥关系。
- 校验调出冻结期、调入/调出开放日和 `none/any/internal` 报告限制。
- 最终审批通过落池前，按当前基金与目标池状态重新校验准入条件；待处理流程检查排除当前审批批次。
- 不执行债券到期、主体评级矩阵、担保人、禁投主体、财务数据或 `fund_rate_limit` 范围校验。
- 只读取目标池 `in_flow_id` / `out_flow_id` 的一般流程；不读取快速或批量流程。未配置已发布一般流程时校验不通过。

## 5. 提交字段

- `fundScore`：必填合法数字，只采集保存，不参与准入或流程选择。
- `fundInvestmentType`：必填，取值为 `stock`、`equity_hybrid`、`money_market`、`bond_hybrid`、`other`。
- `needRiskLeaderApproval`：必填，只允许 `0/1`（`1=是 / 0=否`），只采集保存，不参与准入或流程选择。
- 三个字段按整单填写并复制到手工、联动和互斥日志。
- 提交时重新读取 `rrs_fundinfo`，基金名称、简称和产品类型不信任前端值。
- 同一手工项及其关系项共用 `FUND` 批次号和流程快照。

## 6. 附件

附件复用 `sys_attachment`，`table_name='ip_adjust_log_fund'`。基金分类为：

- `fund_report_hand` / `fund_report_in` / `fund_report_out`
- `fund_material_hand` / `fund_material_in` / `fund_material_out`

每个目标池分别维护基金报告和其他材料。报告库保留内部、外部两个页签，默认按基金代码查询，可筛选全部报告类型。
