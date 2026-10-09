# 股票池调整

页面 `pages/stock_pool_adjust.html`。以债券交互和业务约束为基准，按基金方式使用独立运行表。本页范围为单只股票调入/调出；股票批量调整见 [45](45-batch-stock-pool-adjust.md)。不提供 Excel 导入、临时代码、评级/分管维护。

## 页面与基础信息

股票检索支持代码、名称、行业、市场。选中股票后显示当前所在池和 15 项基础信息：股票简称、股票代码、所属行业、市场、昨收、最高、最低、平均、换手率、交易总量(手)、交易市值(万)、总股本(百万)、总市值(百万)、流通A股(百万)、A股市值(百万)。空值留白，零值显示 0。

沿用债券调入/调出选池、报告材料、校验确认及固定底部提交操作。默认 normalInbound / normalOutbound；可选池配置的 fastInbound / fastOutbound。流程 ID、key、发布版本必须与目标池配置一致，配置缺失/无效明确报错。

## 校验及提交

服务校验股票状态、品种、市场、投资池调整权限、容量、当前在池状态、重复申请、来源池、行业、联动/互斥、开放日、冻结期及报告。分管关系只用于查询，不能替代投资池权限。多个来源池采用 OR；当前已生效池和本组计划调入池可作为来源，在其他批次待审不能算已生效来源。

`grade_astrict` 空配置不限制；非空允许列表只接受 buy/overweight/neutral/underweight/sell，按最新有效评级校验，未评级/不匹配禁止调入，未知配置明确报错。提交与最终通过均重新校验。

使用 STOCK 批次：整组保存日志、提交时名称/行业/评级留痕和附件，再推进主项流程。一般流程创建股票待办；快速结束同样整组复核后落池。使用事务及股票主档锁、目标池锁保护重复提交和并发容量；任何一项失败整组回滚。

## POST 接口 /api/v1/stockPoolAdjust

| 方法 | 用途 |
|---|---|
| queryStockPage | 股票分页，pageIndex/pageSize、stockCode/stockName/industryCode/marketCode |
| queryStockTypeList / queryIndustryList | 实际品种/行业选项 |
| queryStockDetail | stockCode，实时基础信息 |
| queryAdjustPoolList | stockCode/currentUserId，用户可调整的池及祖先 |
| queryStockPoolStatus | 当前有效池状态 |
| checkAdjust | stockCode/currentUserId/items，整组校验 |
| addAdjustLog | JSON 提交，stockCode/adjusterId/adjusterName/items |
| addAdjustLogWithFiles | multipart 的 request(JSON)、files、originalFileNameListJson |
| queryAdjustLogList / queryAdjustStepList | 记录/批次上下文 |

统一 ApiResponse；分页为 PageResult。附件分类 stock_report_hand / stock_report_in / stock_report_out，只绑定 `ip_adjust_log_stock`；复制报告校验报告存在、股票代码一致和股票品种，终审再次检查，审批通过后按债券方式沉淀内部报告。

数据脚本见 sql/stock/README.md；审核见 [44](44-stock-pool-adjust-approve-detail.md)。测试：StockPoolIntegrationTest、StockPoolApiTest、前端 tests/stock_pool.test.js。
