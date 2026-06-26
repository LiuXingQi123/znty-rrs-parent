# 智慧风控平台功能需求说明

本目录按前端业务页面整理需求，共 14 个功能。接口统一使用 `POST`，返回 `ApiResponse<T>`；成功时 `code=0`、`message=success`。

| 序号 | 功能 | 前端页面 | 需求文档 | 接口测试 |
|---|---|---|---|---|
| 01 | 流程定义 | `flow_definition.html` | [01-flow-definition.md](01-flow-definition.md) | `FlowDefinitionApiTest` |
| 02 | 投资池维护 | `investment_pool.html` | [02-investment-pool.md](02-investment-pool.md) | `InvestmentPoolApiTest` |
| 03 | 规则管理中心 | `rule_manager.html` | [03-rule-manager.md](03-rule-manager.md) | `RuleManagerApiTest` |
| 04 | 证券池调库申请（调入/调出） | `security_pool_adjust.html` | [04-security-pool-adjust.md](04-security-pool-adjust.md) | `SecurityPoolAdjustApiTest` |
| 05 | 证券池调库审核（审批流转） | `security_pool_adjust_approve.html` | [05-security-pool-adjust-approve.md](05-security-pool-adjust-approve.md) | `SecurityPoolAdjustApproveApiTest` |
| 06 | 我的事宜 | `my_matters.html` | [06-my-matters.md](06-my-matters.md) | `MyMattersApiTest` |
| 07 | 证券池查询 | `security_pool_query.html` | [07-security-pool-query.md](07-security-pool-query.md) | `SecurityPoolQueryApiTest` |
| 08 | 禁投池查询 | `forbidden_pool_query.html` | [08-forbidden-pool-query.md](08-forbidden-pool-query.md) | `ForbiddenPoolQueryApiTest` |
| 09 | 主体池查询 | `company_pool_query.html` | [09-subject-pool-query.md](09-subject-pool-query.md) | `SubjectPoolQueryApiTest` |
| 10 | 证券池调整历史 | `security_pool_adjust_history.html` | [10-adjust-history.md](10-adjust-history.md) | `AdjustHistoryApiTest` |
| 11 | 证券池调库详情 | `security_pool_adjust_detail.html` | [11-security-pool-adjust-detail.md](11-security-pool-adjust-detail.md) | `SecurityPoolAdjustDetailApiTest` |
| 12 | 证券池批量调整（批量调入/调出） | `batch_security_pool_adjust.html` | [12-batch-security-pool-adjust.md](12-batch-security-pool-adjust.md) | `BatchSecurityPoolAdjustApiTest` |
| 13 | 禁投池历史 | `forbidden_pool_history.html` | [13-forbidden-pool-history.md](13-forbidden-pool-history.md) | `ForbiddenPoolHistoryApiTest` |
| 14 | 主体池调整历史 | `company_pool_adjust_history.html` | [14-company-pool-adjust-history.md](14-company-pool-adjust-history.md) | `CompanyPoolAdjustHistoryApiTest` |

## 调库业务全链路索引

调库是本平台的核心业务，涉及申请、批量、审核、查询多条链路，文档间相互引用：

- **单笔调入/调出申请**：[04](04-security-pool-adjust.md) — 检索证券 → 选目标池 → `checkAdjust` 校验 → `addAdjustLog` 提交（含直通流程即时入池）。
- **批量调入/调出申请**：[12](12-batch-security-pool-adjust.md) — 选目标池 → 批量勾选证券 → 逐只复用单笔校验/提交，整批共用批次号、附件、事务。
- **审核/审批流转**：[05](05-security-pool-adjust-approve.md) — `submitAdjustAudit` 推进节点（抢占/会签/发起人），最终通过才落地 `ip_pool_status`。
- **查询入口**：[07](07-security-pool-query.md) 证券池当前状态查询、[10](10-adjust-history.md) 调库历史追溯、[06](06-my-matters.md) 我的待办/已办。
- **详情查看**：[11](11-security-pool-adjust-detail.md) 单只证券及批次完整业务上下文（只读 / 首次调库提交；修改节点重新提交在 [05]）。
- **禁投池视角**：[08](08-forbidden-pool-query.md) 当前在禁投池的证券、[13](13-forbidden-pool-history.md) 禁投池调库流水（`pool_type='forbidden'` 过滤）。
- **主体池视角**：[09](09-subject-pool-query.md) 当前在池的主体、[14](14-company-pool-adjust-history.md) 主体调库流水（`category_type='company'` 过滤，主体作为伪证券入池/调库）。
- **基础配置**：[01](01-flow-definition.md) 审批流程定义（设计器/节点/版本）、[02](02-investment-pool.md) 投资池树/关系/流程/权限维护、[03](03-rule-manager.md) QLExpress 风控规则与测试用例。

核心状态枚举（`ip_adjust_log.audit_status`）：`-1`无效 / `00`待审核 / `10`审核通过待审批 / `11`驳回待修改 / `20`审批通过 / `21`审批驳回 / `32`O32自动审批 / `99`发起人已撤回。仅 `20` 落地 `ip_pool_status`。

## 通用业务约束

- 所有分页查询均支持 `pageIndex`、`pageSize`，空筛选条件表示查询全部可见数据。
- 前端负责字典 code 到中文名称的映射，后端返回原始 code。
- 删除优先遵循各模块既有逻辑删除或级联规则，不允许产生悬空业务引用。
- 流程、规则、投资池、证券、用户和角色之间的关联必须引用有效数据。
- 涉及审批的操作必须保留调整日志和步骤记录，最终池状态只由已生效结果产生。

## 测试分层

- 当前 `controller` 目录下的 MockMvc 测试覆盖页面实际路由、请求体反序列化和统一响应结构。
- Service 现有单元测试覆盖调库步骤推进、人员权限和流程处理逻辑。
- 数据库集成测试应使用 Demo SQL 初始化后执行，重点验证跨表引用、事务回滚和状态一致性。
