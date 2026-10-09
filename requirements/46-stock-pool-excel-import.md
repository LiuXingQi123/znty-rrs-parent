# 46 股票池 Excel 导入

## 范围与页面

页面 `stock_pool_excel_import.html`，股票研究菜单独立入口；结构与基金、证券Excel导入一致，支持批量调入/调出。不提供报告材料入口，不增加表或依赖。编号45已有股票池批量调整，Excel导入使用46。

## 模板与交互

固定四列：父池名称、子池名称、股票名称、股票代码。文本代码、首表、xls/xlsx、5MB、最多2000非空数据行，显示真实行号。名称取主档、评级取已有记录；直接父池+子池名称解析支持股票的启用叶子池，多匹配失败。

下载模板内置5条示例，按公共池、股票基础及股票调库 Demo 数据设置：第2行远景科技重新调入公司股票库/基础库、第3行海岳工业调入公司港股库/港股基础库，预期通过；第4行港湾医药调入沪深基础库（市场不匹配）、第5行旧城商贸（已退市）、第6行零成交工业（已在基础库），预期失败。使用调入、关闭清空，并具备目标池双权限；具体预期及数据来源见第二表填写说明，结果随当前配置及数据变化。正式导入前须删除5条示例并填写实际数据。

双参数面板、导入明细/调库校验双页签、筛选分页统计及流程选择弹窗。默认in、clearTarget=false、allowLinkMutex=true；上传锁定固定选项，原因意见提交前可修改，最多1000字。保留取消旧批次后上传、失败现场、重校验、部分通过提交及已提交防重。

## API与存储

全部POST `/api/v1/stockPoolExcelImport/`：uploadExcel、queryTask、queryItemPage、checkImport、submitImport、cancelImport。上传multipart request JSON、file、originalFileNameListJson。公共模板 `stock_pool_import`。

复用sys_imp_tmp/detl，bizType=stock_pool_excel。主表fld001方向、option_json固定清空及关系选项、result_json服务端快照、fld011/012 TEXT原因意见；明细fld001/002代码及主档名称、fld003/004父子池、fld009/010解析目标池。客户端只回传已有候选流程选择，其他业务字段以持久化来源及快照为准。

## 业务规则

检查excel_importable、adjustable双权限，复用股票状态、市场、品种、行业、评级、容量、在途、来源、开放日、冻结期及限制池规则。API方向in/out转换为AdjustMode内部编码。

仅目标池已配置、已发布、包含实际人工审批的一般流程；快速/批量/全自动一般流程不可选。要求报告的主项明确失败。

同股票同目标池跨来源冲突阻断全部相关组；每来源独立STOCK批次，主项Excel导入/Excel清空，关系项仍为联动/互斥。主项失败阻断本组，关系失败保留原因；关闭关系只提交主项，开启提交通过关系，提交前核对最新有效集合并保持硬性来源及限制池规则。

清空仅调入。当前有效成员减去所有可识别来源代码（包括失败行），跳过在途成员；先生成清空调出审批申请，不立即出池或预释放容量。

先按稳定顺序锁定整批股票及目标池，全部复核后写日志步骤，任一失败整批事务回滚。提交audit_status=00，终审20才更新股票池。终审与驳回修改报告校验识别两种Excel主项类型；单笔快速及完整关系校验保持原有行为。

## 验证对照

- StockPoolExcelImportServiceTest：四列、权限、清空差集、来源冲突、权威代码、快照及防重。
- StockPoolExcelImportApiTest：六接口及multipart契约。
- StockPoolExcelImportPersistenceTest：真实XML、业务隔离、解析叶子池、持久化及事务回滚。
- StockPoolAdjustExcelSubmitServiceTest：真实股票规则、提交00/终审20、联动选项、报告/评级变化、写入回滚及清空容量。
- CommonFileServiceTest：模板四列、说明表、代码文本格式及5条示例首表解析。
- 前端tests/stock_pool_excel_import.test.js；回归StockPoolIntegrationTest、StockPoolApiTest、FundPoolExcelImport*Test及前端stock_pool/fund_pool_excel_import测试。
