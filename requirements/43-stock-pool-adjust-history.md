# 股票池调整历史查询

页面 `pages/stock_pool_adjust_history.html`，POST 前缀 `/api/v1/stockPoolAdjustHistory`。

查询条件为投资池、证券代码、调整日期范围、行业、调整人、调整方向、审核状态。参数 poolIds、stockCode、adjustTimeStart/End、industryCode、adjusterName、adjustMode、auditStatus、pageIndex/pageSize。日期按提交时间自然日筛选。

表格为调整人、提交日期、证券名称、证券代码、行业、调整类型、调整方向、投资池、审核状态；需求中的“证券每次”按“证券名称”处理。查询全部未删除的 `ip_adjust_log_stock`，行业取日志提交时记录，行业选项来自实际未删除历史数据。按 submit_time、批次、主键倒序；字典、Tag、分页和导出沿用债券。

| POST 方法 | 用途 |
|---|---|
| queryStockPoolAdjustHistoryPage | 全审核状态历史分页 |
| queryIndustryList | 实际历史行业 code/name |
| exportStockPoolAdjustHistoryExcel | 全部匹配记录，忽略分页；9 列 |

名称/代码打开对应股票调整详情，携带日志、批次及池上下文。历史行名称/行业/评级保留提交时数据，详情基础信息实时读取，不建行情快照。

测试：StockPoolIntegrationTest / StockPoolApiTest / tests/stock_pool.test.js。
