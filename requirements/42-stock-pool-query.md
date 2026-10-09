# 股票池查询

页面 `pages/stock_pool_query.html`，POST 前缀 `/api/v1/stockPoolQuery`。

查询条件严格为投资池、入池日期范围、股票代码、调整人、我分管股票、我的自选股。参数 poolIds、entryTimeStart/End、stockCode、adjusterName、myManagedStocks、myStocks、currentUserId、pageIndex/pageSize。日期采用自然日，下限包含当天零点，上限小于次日零点。

表格为股票名称、股票代码、所属行业、最新评级、上次评级、投资池、调整人、入池时间、操作。只查询 `ip_pool_status_stock` 未删除且 `audit_status=20` 的当前状态，每股票每池一行。评级按日期、主键倒序取最近两次有效记录；分管/自选同时勾选取交集，EXISTS 避免多人分管导致重复行。

| POST 方法 | 用途 |
|---|---|
| queryStockPoolPage | PageResult 股票池列表 |
| exportStockPoolExcel | 全部匹配记录，复用全部条件，忽略分页；8 列，不导出操作列 |
| addStockToMyPool | stockCode/currentUserId，收藏幂等，品种/市场从真实主档取得 |
| deleteStockFromMyPool | 移除本人股票收藏，幂等且不影响其他用户 |
| queryFavoritedCodeList | 本人股票收藏代码 |

点击名称或代码用工作台页签打开股票详情，保留 stockCode、adjustLogId、adjustBatchNo、targetPoolId。收藏复用 my_security_pool，后端锁定基础主行防并发重复。详情展示全部 15 项实时基础信息。

测试覆盖最新两次评级、已删除评级、日期末尾毫秒、分管/自选交集、多池分页、收藏并发幂等及用户隔离、导出筛选；见 StockPoolIntegrationTest / StockPoolApiTest / tests/stock_pool.test.js。
