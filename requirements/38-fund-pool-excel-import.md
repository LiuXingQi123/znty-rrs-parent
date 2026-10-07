# 38 基金池 Excel 导入

## 页面与模板

页面 `fund_pool_excel_import.html` 沿用证券池 Excel 导入的参数卡、模板上传卡、导入明细和调库校验结果页签，以及选择调库流程弹窗。基金业务使用独立接口 `/api/v1/fundPoolExcelImport`。

- 七列固定为：父池名称、子池名称、基金名称、基金代码、基金评分、基金投资类型、风管领导审批。目标池由直接父池和子池名称解析为支持基金品种的启用叶子池；多匹配明确失败。
- 评分必填数字，仅采集保存，不参与准入评分范围判断。现有日志字段 `DECIMAL(10,4)` 最多保存六位整数、四位小数，超过存储精度明确失败，避免数据库截断。
- 投资类型只接受 `stock/equity_hybrid/money_market/bond_hybrid/other`；风管领导审批只接受 `0/1`。预览中文由前端字典显示；非法原始文本仍可查看。
- 基金名称以 `rrs_fundinfo` 主档为准。原始基金代码保留在导入明细；主档匹配成功后校验、清空保留集合、跨行冲突及提交使用主档权威代码，避免大小写导致误清空或重复申请。
- `commonFile/downloadTemplate` 使用 `templateCode=fund_pool_import`；模板首表为七列数据，第二表为填写说明，投资类型及风管审批带下拉约束。
- 仅首 sheet、xls/xlsx、5MB、最多 2000 个非空数据行；保留 Excel 真实行号。
- 三字段为数字单元格时读取实际数值，避免显示格式把超精度评分或非法审批值舍入成合法值；代码及其他文本沿用既有显示文本读取规则。
- 调整方向、联动互斥、清空选项及清空专用三字段在上传后锁定。原始明细只读；更改导入参数或原始数据须重置后重新上传。
- 调整原因和建议各最多 1000 字，可在提交时填写或修改。

## 校验和清空

- 每行独立调用 `FundPoolAdjustService.checkExcelImportAdjust`，复用基金状态、品种、市场、池状态、容量、来源与限制关系、在途、冻结期、开放日及一般流程规则。
- 同时要求 `excel_importable` 与 `adjustable` 权限；角色和人员按投资池权限配置识别，管理员 ID=1 沿用证券导入规则。
- 本期不增加报告或材料入口。要求 `any/internal` 基金报告的手工主项在校验阶段失败；报告限制不被导入跳过，关系项沿用基金单笔现有规则。
- 仅允许目标池 `in_flow_id/out_flow_id` 已发布一般流程；不注入快速或批量流程。手工导入显示 `Excel导入`，清空主项显示 `Excel清空`，关系项保留联动/互斥调整类型。
- 未勾选联动与互斥时只保留主项；主项失败，其关系项不可独立提交。
- 不同来源组展开到相同基金与目标池时，无论方向是否相同，全部相关组失败；定位前五个来源及总组数，不按基金代码或三字段相等静默合并。
- 明细 `chk_dscr` 保存最多 500 字的可读摘要；完整原因保留在服务器校验快照并展示于调库校验结果。
- 清空仅调入生效；页面须填写清空出库项统一使用的基金评分、投资类型、风管审批三个必填字段。
- 清空保留集合为全部能解析目标池且代码非空的 Excel 行，包含评分等三字段校验失败行。差集成员取自 `ip_pool_status_fund` 中已审批通过、未删除的当前成员；存在目标池在途流程的差集成员跳过。
- 清空出库与其关系项同样调用完整基金检查，使用一般调出流程，并使用统一清空三字段。清空失败组不能单独提交关系项。

## 接口与临时表

所有接口均为 POST；上传使用 multipart，其余使用 JSON。

导入 API 的方向使用 `in/out`；进入基金内部校验与提交时转换为现有 `AdjustMode` 枚举 code，校验结果再转换回导入 API 方向。

| 接口 | 说明 |
|---|---|
| `uploadExcel` | `request` JSON + `file` + 可选原始文件名数组文本 `originalFileNameListJson` |
| `queryTask` | 批次状态、锁定参数、原始明细首屏、校验快照及提交结果 |
| `queryItemPage` | 按基金代码/名称及校验状态筛选分页明细 |
| `checkImport` | 批次加锁，重新校验导入行、清空差集及跨组冲突 |
| `submitImport` | 批次加锁，仅接纳服务器校验结果允许的一般流程选择并整批提交 |
| `cancelImport` | 批次加锁，逻辑取消尚未提交批次，返回批次 DTO |

复用已注册通用表，无 DDL 和新 SQL 脚本：

- 主表 `sys_imp_tmp`：`biz_type=fund_pool_excel`，`template_code=fund_pool_import`；`fld001` 为方向；原因/建议存现有 TEXT 槽 `fld011/fld012`，不用 VARCHAR(200) 的 `fld002/fld003`。`option_json` 存固定选项及清空三字段，`result_json` 存完整校验与提交快照。
- 明细 `sys_imp_tmp_detl`：`fld001~007` 为原始基金代码、主档名称、父池、子池、评分原文、投资类型原文、风管审批原文；`fld009/010` 为解析池 ID/类型。三字段非法原文不覆盖。
- 明细 API：`id/rowNo/fundCode/fundName/parentPoolName/childPoolName/fundScoreRaw/fundInvestmentTypeRaw/needRiskLeaderApprovalRaw/targetPoolId/poolType` 及校验/保存状态。
- 校验项包含来源明细 ID、行号、独立分组键、三字段、方向、来源类型、阻断/警告和 `FundAdjustCheckDto.FlowOption`。客户端仅选择 `selectedFlowId/selectedFlowKey/selectedFlowType`。

## 提交与审批

- 提交身份、基金、池、方向、来源标签及可调整状态均以持久化服务器快照为准；原始导入行三字段再次读回，主档权威代码再次核对。客户端业务字段不能覆盖服务器值；伪造或已不允许的流程明确失败。
- 导入按明细 ID 独立构造请求；清空按基金与原目标池独立分组。每组的主项、联动及互斥共享三字段和一般流程。
- `addExcelImportAdjustLogList` 在创建任何新流程前统一复核全部请求，之后按顺序保存，防止本批新流程误阻断后续行。清空请求排在有效导入请求前。
- 允许提交校验通过项，其余失败项保留；也允许只提交有效清空组。提交执行中任一最新复核或写入失败，外层事务整体回滚，临时表不得标记提交成功。
- 同一批次修改状态、校验、提交、取消均共用行锁；已提交不能重复提交、重校验或取消。
- 提交只写 `ip_adjust_log_fund/ip_adjust_step_fund`，使用 `FUND` 批次号并保持 `00`。清空只是先创建调出审批申请，不等待审批生效、不提前释放容量。
- 最终审批继续 `FundPoolAdjustFlowService`，重新核对准入条件后才更新 `ip_pool_status_fund`。不写证券运行表，不创建基金快照，不迁移既有数据。

## 测试

`FundPoolExcelImportServiceTest` 覆盖真实七列解析、原文、文件限制、评分存储精度、三字段、主档名称和大小写代码、双权限、报告失败、联动、清空失败行保留/在途跳过/仅清空提交、跨来源冲突、长原因建议、限长摘要、快照防篡改与批次状态。

`FundPoolExcelImportApiTest` 覆盖六个接口、multipart JSON 与原始中文文件名；`CommonFileServiceTest` 验证模板七列和下拉。真实 Mapper 和提交异常事务回滚由 `FundPoolExcelImportPersistenceTest` 验证。基金一般流程、报告要求及整批提交复核另由基金服务测试覆盖。

`FundPoolAdjustExcelSubmitServiceTest` 同时串联真实导入 Service 和基金 Service，覆盖调入/调出的方向转换及审批中日志；H2 回滚用例使用 JDK 17 运行，项目源码目标继续保持 Java 8。
