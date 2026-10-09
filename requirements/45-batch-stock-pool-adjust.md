# 45 股票池批量调整

## 页面与操作

页面 `pages/batch_stock_pool_adjust.html` 在工作台“研究管理 → 股票研究”中紧接“股票池调整”，业务文档菜单同位。以基金池批量调整为直接模板，对齐证券池批量调整的投资池列表、两步工作台、共享材料、报告选择、完整分组校验、流程确认和固定底栏。

- 目标池为当前用户具有调整权限、启用且支持 `stock` 品种的叶子池，按股票单笔管理员与用户/角色权限口径处理；有效在池数量只统计 `ip_pool_status_stock` 中 `audit_status='20'` 且未删除的不同股票。
- 选择一个目标池和调入/调出方向，按股票代码、简称、行业、市场筛选；表格显示代码、简称、行业、市场、最新/上次评级、昨收和数据日期。空值留白，零值正常显示，枚举使用既有标签和前端字典。
- 调入排除目标池的有效成员，调出只含目标池有效成员；股票候选和提交采用现有股票状态、品种与市场约束。
- 跨页保留勾选，支持移除和清空；第一步选择股票与共享材料，第二步展示全部主项、联动、互斥明细、阻断原因、警告及一般流程。
- 整批原因和建议选填，页面各最多 500 字；返回上一步保留已选股票与材料，重新校验刷新结果。退出工作台后忽略迟到响应；提交中禁止重复点击，成功返回投资池列表并刷新。

## POST 接口与数据契约

前缀 `/api/v1/batchStockPoolAdjust`，统一 `ApiResponse<T>`，分页 `PageResult<T>`。

| 接口 | 主要入参 | 用途与结果 |
|---|---|---|
| `queryPoolPage` | `currentUserId/poolIds/pageIndex/pageSize` | 可调整股票叶子池、全路径、配置及有效在池数量 |
| `queryStockPage` | `currentUserId/poolId/direction/stockCode/stockShortName/industryCode/marketCode/pageIndex/pageSize` | 按目标池及方向查询股票候选分页，复用 `StockInfoDto` |
| `checkAdjust` | `currentUserId/poolId/direction/stocks:[{stockCode}]` | 复用股票单笔校验结构的完整 `items`，含一般流程候选 |
| `addAdjustLog` | JSON 根参数与完整已选流程 `items` | 无本地文件的批量提交 |
| `addAdjustLogWithFiles` | multipart：`request` JSON、`files`、`originalFileNameListJson` | 整批共享上传并提交，中文原名数组与文件顺序和数量一致 |

提交根参数为 `currentUserId/poolId/direction/adjustReason/adjustAdvice/adjusterId/adjusterName/items`，调整人与当前用户必须一致。明细复用股票单笔 `targetPoolId/targetPoolName/poolType/adjustMode/itemTag/adjustGroupKey/flowId/flowKey/flowType/adjustmentNote` 及四种附件索引/引用字段，并增加 `stockCode`。

根方向 `direction` 使用 `in/out`；返回明细及提交 `items[].adjustMode` 使用单笔的 `调入/调出` code。提交返回 `stockCount/submitCount/logIds/adjustBatchNos`：股票数统计独立股票，日志数包含联动和互斥项。校验返回 `items/stockCount`。

## 校验、一般流程与原子提交

- 批量层负责候选查询、目标池权限和多股票编排，逐股票调用 `StockPoolAdjustService.checkAdjust`，复用评级、品种、市场、状态、容量、在池、在途、来源、行业、关系、开放日和冻结期规则；报告要求在提交和终审按股票单笔规则复核。
- 每只股票恰好一条手工主项，组键为 `<stockCode>_stock-group-1`，所有联动/互斥项同组。失败组整组不可提交，允许提交其余校验通过的完整组；与主方向相反的互斥项不可丢弃。
- 只提供目标池配置的 `normalInbound/normalOutbound` 一般流程。主项选择服务端返回的合法流程，关系项共用主项流程；快速与批量专用流程均拒绝提交。股票单笔一般/快速功能保持原样。
- 提交重新读取股票、池、关系、报告和流程配置，拒绝重复股票、错误主池/方向、伪造分组、重复或遗漏关系项和错误流程；主档名称、行业及评级来自服务器当前数据。
- 扩展股票多单提交入口：一次按主档主键顺序锁定全部股票，再按池 ID 升序锁定全部目标池。所有分组完成复核后才保存日志、附件和步骤，使用 `READ_COMMITTED` 事务；任一复核或保存失败整批回滚，包括共享物理文件清理。
- 手工主项由服务器标记 `adjust_type='手动批量调整'`；联动和互斥项沿用既有类型。每只股票的主项与关系项共用一个独立 `STOCK` 批次号。
- 复用 `StockPoolAdjustFlowService` 与股票我的事宜，只有审批通过 `20` 时更新股票当前池状态；终审仍复核整组、锁定股票与目标池并验证并发容量。

## 报告与共享材料

保留内部/外部报告双页签、标题/证券编码/类型/日期筛选、分页、跨页选择、下载和已选汇总。研究报告必须是股票品种且 `securityCode` 匹配已选股票，不能把其他股票报告静默丢弃或复制给不匹配股票；报告库查询继续复用现有接口。

本地报告和其他材料整批共享，报告库引用按 `securityCode` 分配到对应股票，同股票的主项和全部关系项复用该组材料。上传文件只保存一份物理文件，按各条股票日志分别绑定，使用 `stock_report_hand/in/out` 和 `stock_material_hand/in/out`，关联表为 `ip_adjust_log_stock`。已有 `none/any/internal` 报告要求、股票归属、来源有效性及终审校验继续生效。

后端提交时按集合复核整批本地文件索引、其他材料来源一致，以及同股票完整组的报告来源一致；不一致请求在写入前拒绝，不静默覆盖或丢弃材料。

## 数据与测试

复用 `rrs_stockinfo/rrs_stock_rating`、投资池/权限/关系/开放日、`ip_pool_status_stock/ip_adjust_log_stock/ip_adjust_step_stock`、已发布流程、报告库及公共附件表。无需新增表、数据库迁移或 SQL 脚本。

- `BatchStockPoolAdjustApiTest`：五个 POST 路由、请求与统一响应、multipart 和中文原名。
- `BatchStockPoolAdjustServiceTest`：目标池权限、逐股票校验、失败组隔离、一般流程、完整关系、反向互斥、报告、共享材料、独立批次及错误请求。
- `BatchStockPoolAdjustMapperTest`：真实 XML 与 H2，验证叶子池、有效计数、方向及股票筛选。
- 股票真实事务测试覆盖全部复核前零写入、后续日志/步骤失败整批回滚、股票事宜至审批落池和历史详情；回归现有单笔与附件测试。
- `tests/batch_stock_pool_adjust.test.js`：实际 Vue 方法验证跨页选择、API 参数、失败组、关系项、一般流程、报告按股票分配、共享文件、中文原名、防重和迟到响应。

使用 JDK 17 执行 H2 测试，源码目标保持 Java 8；工作台 iframe 检查池列表、两步切换、报告弹窗、流程下拉、固定底栏及分页。相关单笔需求见 [41](41-stock-pool-adjust.md)，审核和详情见 [44](44-stock-pool-adjust-approve-detail.md)。
