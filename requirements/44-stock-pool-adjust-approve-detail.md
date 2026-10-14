# 股票调整审核、详情与我的事宜

页面 `pages/stock_pool_adjust_approve.html` / `pages/stock_pool_adjust_detail.html`；入口来自股票池查询、调整历史和“我的事宜”。工作台按股票业务、代码及日志/批次复用详情页签。

MyMattersService 显式分派 stock → StockMyMattersService / StockMyMattersMapper，仅使用股票日志及步骤表，businessScene=stockAdjust。入口名单为直接用户 1、5 和启用角色 2、3、7、8、9、10，入口显示和实际待办处理资格分离。沿用原管理员口径、本人待办、抢占和会签、跨环节已参与人员排除规则。

## 审核状态与事务

状态 -1 无效 / 00 流程中 / 11 驳回待修改 / 20 通过 / 21 驳回 / 32 自动处理预留 / 99 撤回。只有 20 改变股票当前池状态。状态、步骤和整组日志在同一事务推进；终审锁定目标池，重新检查池权限、容量、行情状态、评级准入、行业来源、关系、开放日、冻结期和报告。

POST `/api/v1/stockPoolAdjust/submitAdjustAudit` 接收 stepId、adjustLogId、adjustBatchNo、handlerId/Name、processAction、processComment；实际步骤决定资格，伪造批次/日志上下文明确拒绝。`submitAdjustAuditWithFiles` 使用 request(JSON)、files、originalFileNameListJson。

修改阶段由发起人修改原因、建议和本批附件后重新提交；终止流程保留 99 留痕。其他阶段禁止变更附件。删除必须同时验证股票专属日志表、分类和该日志主键，不能删除相同 ID 的债券/基金绑定。新增/删除/复制报告及步骤推进失败整组事务回滚。

详情只读显示 15 项实时基础信息、当前池、调整日志、批次审批步骤及分类附件，不创建基础信息全快照。提交时日志名称、行业及评级留痕供历史追溯。

验收路径：一般申请 → 股票我的事宜 → 审批 → 股票池查询 → 历史详情；快速流程直接整组生效；驳回修改由未参与的审批人复审；修改阶段撤回与调出。测试见 StockPoolIntegrationTest、StockPoolApiTest、MyMattersMapperSqlTest、MyMattersServiceTest、前端 tests/my_matters.test.js / stock_pool.test.js。


## 报告与手工文件统一交互

报告库引用按 `sourceReportSource + sourceReportId` 聚合为一份报告入口，不直接铺开报告下的 PDF。点击报告标题在工作台新页签打开 `pages/report_detail.html`，展示元信息及该报告全部有效附件，逐个下载；手工上传文件仍点击直接下载。此规则用于申请、批量、审核与详情的报告/其他材料栏。最近信评报告自动回填携带完整附件列表；修改节点移除报告会删除本日志、本材料栏的全部关联。

`sys_attachment.source_attachment_id` 保存复制来源的报告库附件 ID，附件查询返回 `sourceAttachmentId/sourceReportId/sourceReportSource/sourceReportTitle`；基金代码转正继承关联时保留该来源。新增 `POST /api/v1/reports/queryReportDetail { reportId, source: internal|external }`，非法来源、无效 ID、不存在或已删除报告明确失败。

新环境通过更新后的原始附件 schema 初始化；存量升级使用 `sql/tool_report_source_upgrade.sql`（不加入初始化/重置执行）。仅修复唯一可定位来源，未定位项明确输出供核对，运行时不猜测归属。前端完整说明见 `docs/report-materials.html`。
