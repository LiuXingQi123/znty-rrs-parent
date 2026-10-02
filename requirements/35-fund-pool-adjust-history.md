# 基金池调整历史需求说明

> 前端页面：`fund_pool_adjust_history.html`  
> 后端前缀：`/api/v1/fundPoolAdjustHistory`、`/api/v1/common`

## 1. 功能范围

基金研究人员按投资池、基金信息、提交时间和审核状态追溯基金调库流水。查询主表为 `ip_adjust_log_fund`，展示全部未逻辑删除的审核状态记录；点击基金名称或代码可打开对应基金调库详情。本页不设操作列或直接审核入口，审核/详情功能见 [36-fund-pool-adjust-approve-detail.md](36-fund-pool-adjust-approve-detail.md)。

## 2. 查询条件

| 条件 | 请求字段 | 说明 |
|---|---|---|
| 投资池 | `poolIds` | 基金品种池树多选，仅叶子节点可选 |
| 基金代码 | `fundCode` | 模糊匹配 |
| 基金名称 | `fundName` | 模糊匹配全称或简称 |
| 基金类型 | `securityType` | 动态下拉，精确匹配 |
| 调整时间 | `adjustTimeStart` / `adjustTimeEnd` | 按提交时间查询自然日范围 |
| 调整人 | `adjusterName` | 模糊匹配 |
| 调整方向 | `adjustMode` | 调入 / 调出 |
| 审核状态 | `auditStatus` | `-1/00/11/20/21/32/99` |

## 3. 表格与导出

页面列表字段依次为：序号、调整人、提交时间、基金名称、基金代码、基金类型、调整类型、调整方向、投资池、审核状态；无操作列。点击基金名称或基金代码固定打开基金池调库详情页，并携带基金代码及当前调库记录标识。序号按分页连续计算。基金类型使用查询结果中的基金类型名称，并按类型编码固定着色：`closed_fund` danger、`open_fund` info、`qdii_fund` primary、`lof_fund` warning、`etf_fund` success；未知编码使用 info。Excel 字段依次为：调整人、提交时间、基金名称、基金代码、基金类型、调整类型、调整方向、投资池、审核状态（不含序号）。

投资池由服务层回填完整路径；审核状态在前端和导出层按统一字典转中文。结果按 `submit_time DESC, adjust_batch_no DESC, id DESC` 排序。

Excel 基于 `src/main/resources/xlsx/fund_pool_adjust_history_export_template.xlsx` 填充，与证券池调整历史模板保持一致：表头为带底色的楷体加粗文字，数据行为楷体，首行及数据行均为 20 磅行高、列宽为 15 个字符、垂直居中、无底边框并保留工作表网格线。

## 4. 接口清单

| 路径 | 用途 |
|---|---|
| `POST /api/v1/common/queryPoolTreeList` | 加载基金品种投资池树，前端传 `includeVarietyCodes: ['fund']` |
| `POST /api/v1/fundPoolAdjustHistory/queryFundPoolAdjustHistoryPage` | 分页查询基金池调整历史 |
| `POST /api/v1/fundPoolAdjustHistory/queryFundTypeList` | 加载调整历史中出现的基金类型 |
| `POST /api/v1/fundPoolAdjustHistory/exportFundPoolAdjustHistoryExcel` | 导出当前筛选条件命中的全部记录 |

## 5. 数据口径

- `ip_adjust_log_fund`：基金调库日志主表，固定过滤未逻辑删除记录，不限制审核状态。
- 基金名称和代码优先使用调库日志快照。
- `dict_security_type`：仅关联 `category_type='fund'` 的有效基金类型。
- `ip_investment_pool`：取得叶子池名称，服务层统一回填投资池全路径。

## 6. 验收标准

- 查询、分页和倒序排序口径正确。
- 页面字段、导出字段和筛选条件一致（导出不含序号），导出不受当前分页限制。
- 调整类型、调整方向、审核状态使用与证券池调整历史一致的 Tag 文案和配色。
- 点击基金名称或代码打开对应的基金池调库详情页。
