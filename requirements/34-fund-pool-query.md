# 基金池查询需求说明

> 前端页面：`fund_pool_query.html`  
> 后端前缀：`/api/v1/fundPoolQuery`、`/api/v1/common`

## 1. 功能范围

基金研究人员按投资池、基金信息及入池记录查询当前已生效的基金池状态。页面仅查询，不提供收藏操作。基金名称和基金代码均可点击，并在工作台打开对应基金池调库详情页；跳转携带基金代码、当前池关联的调库日志 ID、批次号和目标池 ID。

查询主表为 `ip_pool_status_fund`，仅展示 `audit_status='20'` 且未逻辑删除的记录；基金基础资料来自 `rrs_fundinfo`，投资池全路径由 `InvestmentPoolService` 回填。

## 2. 查询条件

| 条件 | 请求字段 | 说明 |
|---|---|---|
| 投资池 | `poolIds` | 基金品种池树多选，仅叶子节点可选 |
| 基金代码 | `fundCode` | 模糊匹配 |
| 基金名称 | `fundName` | 模糊匹配全称或简称 |
| 基金类型 | `securityType` | 动态下拉，精确匹配 |
| 入池时间 | `entryTimeStart` / `entryTimeEnd` | 日期范围 |
| 调整人 | `adjusterName` | 模糊匹配 |

## 3. 表格与导出

列表字段依次为：基金名称、基金代码、投资池、调整人、入池时间、基金类型、基金经理、基金管理人、基金托管人、成立日期、发行期限（年）、最新净值、累计净值、日万份收益、7日年化收益率、最新规模、昨收盘、折/溢价。基金名称和基金代码使用与证券池查询相同的链接样式与工作台页签跳转方式。

“导出”复用当前全部筛选条件，不受当前分页限制，生成 `.xlsx` 文件；百分数字段按页面口径保存并展示。Excel 基于 `src/main/resources/xlsx/fund_pool_query_export_template.xlsx` 填充，与证券池查询模板保持一致：表头为带底色的楷体加粗文字，数据行为楷体，首行及数据行均为 20 磅行高、列宽为 15 个字符、垂直居中、无底边框并保留工作表网格线。

## 4. 接口清单

| 路径 | 用途 |
|---|---|
| `POST /api/v1/common/queryPoolTreeList` | 加载基金品种投资池树，前端传 `includeVarietyCodes: ['fund']` |
| `POST /api/v1/fundPoolQuery/queryFundPoolPage` | 分页查询基金池当前状态 |
| `POST /api/v1/fundPoolQuery/queryFundTypeList` | 加载基金类型下拉选项 |
| `POST /api/v1/fundPoolQuery/exportFundPoolExcel` | 导出当前筛选结果 |

## 5. 验收标准

- 仅返回审批通过且未删除的基金池状态。
- 查询、分页总数、投资池全路径和导出数据口径一致。
- 点击基金名称或基金代码均打开对应基金池调库详情，并保留当前池记录的日志及批次上下文。
- 页面结构、筛选区、表格、分页与导出交互与证券池查询保持一致。
