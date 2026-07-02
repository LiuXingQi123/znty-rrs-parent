# 智慧风控平台功能需求说明

本目录按前端业务页面整理需求，当前覆盖 24 个功能。接口统一使用 `POST`，返回 `ApiResponse<T>`；成功时 `success=true`、`message=success`。

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
| 09 | 主体池查询 | `company_pool_query.html` | [09-company-pool-query.md](09-company-pool-query.md) | `CompanyPoolQueryApiTest` |
| 10 | 证券池调整历史 | `security_pool_adjust_history.html` | [10-adjust-history.md](10-adjust-history.md) | `AdjustHistoryApiTest` |
| 11 | 证券池调库详情 | `security_pool_adjust_detail.html` | [11-security-pool-adjust-detail.md](11-security-pool-adjust-detail.md) | `SecurityPoolAdjustDetailApiTest` |
| 12 | 证券池批量调整（批量调入/调出） | `batch_security_pool_adjust.html` | [12-batch-security-pool-adjust.md](12-batch-security-pool-adjust.md) | `BatchSecurityPoolAdjustApiTest` |
| 13 | 禁投池历史 | `forbidden_pool_history.html` | [13-forbidden-pool-history.md](13-forbidden-pool-history.md) | `ForbiddenPoolHistoryApiTest` |
| 14 | 主体池调整历史 | `company_pool_adjust_history.html` | [14-company-pool-adjust-history.md](14-company-pool-adjust-history.md) | `CompanyPoolAdjustHistoryApiTest` |
| 15 | 禁投池调整申请（主体级 调入/调出） | `forbidden_pool_adjust.html` | [15-forbidden-pool-adjust.md](15-forbidden-pool-adjust.md) | `ForbiddenPoolAdjustApiTest` |
| 16 | 禁投池调整审核（审批流转） | `forbidden_pool_adjust_approve.html` | [16-forbidden-pool-adjust-approve.md](16-forbidden-pool-adjust-approve.md) | `ForbiddenPoolAdjustFlowServiceTest` |
| 17 | 禁投池调整详情 | `forbidden_pool_adjust_detail.html` | [17-forbidden-pool-adjust-detail.md](17-forbidden-pool-adjust-detail.md) | `ForbiddenPoolAdjustApiTest` |
| 18 | CRMW池查询 | `crmw_pool_query.html` | [18-crmw-pool-query.md](18-crmw-pool-query.md) | — |
| 19 | CRMW池调库申请（调入/调出） | `crmw_pool_adjust.html` | [19-crmw-pool-adjust.md](19-crmw-pool-adjust.md) | `CrmwPoolAdjustApiTest` |
| 20 | CRMW池调库审核（审批流转） | `crmw_pool_adjust_approve.html` | [20-crmw-pool-adjust-approve.md](20-crmw-pool-adjust-approve.md) | `CrmwPoolAdjustFlowApiTest` |
| 21 | CRMW池调库详情 | `crmw_pool_adjust_detail.html` | [21-crmw-pool-adjust-detail.md](21-crmw-pool-adjust-detail.md) | — |
| 22 | CRMW池调整历史 | `crmw_pool_adjust_history.html` | [22-crmw-pool-adjust-history.md](22-crmw-pool-adjust-history.md) | — |
| 23 | 信用债评级准入规则（主体内评分档矩阵） | `credit_bond_grade_rule.html` | [23-credit-bond-grade-rule.md](23-credit-bond-grade-rule.md) | — |
| 24 | 临时代码管理 | `temp_security_code.html` | [24-temp-security-code.md](24-temp-security-code.md) | — |

## 调库业务全链路索引

调库是本平台的核心业务，涉及申请、批量、审核、查询多条链路，文档间相互引用：

- **单笔调入/调出申请**：[04](04-security-pool-adjust.md) — 检索证券 → 选目标池 → `checkAdjust` 校验 → `addAdjustLog` 提交（含直通流程即时入池）。
- **批量调入/调出申请**：[12](12-batch-security-pool-adjust.md) — 选目标池 → 批量勾选证券 → 逐只复用单笔校验/提交，整批共用批次号、附件、事务。
- **审核/审批流转**：[05](05-security-pool-adjust-approve.md) — `submitAdjustAudit` 推进节点（抢占/会签/发起人），最终通过才落地 `ip_pool_status`。
- **查询入口**：[07](07-security-pool-query.md) 证券池当前状态查询、[10](10-adjust-history.md) 调库历史追溯、[06](06-my-matters.md) 我的待办/已办。
- **详情查看**：[11](11-security-pool-adjust-detail.md) 单只证券及批次完整业务上下文（只读 / 首次调库提交；修改节点重新提交在 [05]）。
- **禁投池查询视角**：[08](08-forbidden-pool-query.md) 当前在禁投池的证券、[13](13-forbidden-pool-history.md) 禁投池调库流水（`pool_type='forbidden'` 过滤）。
- **禁投池调整链路（主体级，独立于证券级调库）**：[15](15-forbidden-pool-adjust.md) 主体检索 → 选目标池（仅 15/16/17）→ `checkAdjust` 校验 → `addAdjustLogWithFiles` 提交；[16](16-forbidden-pool-adjust-approve.md) `submitAdjustAudit` 审批流转，通过后落地 `ip_pool_status` 并 `syncCompanyBonds` 自动同步旗下全部债券；[17](17-forbidden-pool-adjust-detail.md) 主体调库只读 / 首次提交（修改节点重提在 [16]）。
- **主体池视角**：[09](09-company-pool-query.md) 当前在池的主体、[14](14-company-pool-adjust-history.md) 主体调库流水（`category_type='company'` 过滤，主体作为伪证券入池/调库）。
- **CRMW 池链路（凭证级，独立状态表 `ip_pool_status_crmw`）**：[18](18-crmw-pool-query.md) 当前在 CRMW 池的凭证（`audit_status='20'`）、[19](19-crmw-pool-adjust.md) 选 CRMW 凭证 + 标的证券 → 校验 → `addCrmwAdjustLogWithFiles` 提交（批次号 `CRMW` 前缀）、[20](20-crmw-pool-adjust-approve.md) `submitAdjustAudit` 审批流转落地 `ip_pool_status_crmw`、[21](21-crmw-pool-adjust-detail.md) 凭证调库只读 / 首次提交（修改节点重提在 [20]）、[22](22-crmw-pool-adjust-history.md) CRMW 调库流水（`pool_type='crmw'` 过滤，所有状态）。
- **基础配置**：[01](01-flow-definition.md) 审批流程定义（设计器/节点/版本）、[02](02-investment-pool.md) 投资池树/关系/流程/权限维护、[03](03-rule-manager.md) QLExpress 风控规则与测试用例、[23](23-credit-bond-grade-rule.md) 信用债期限×主体内评分档→投资池准入矩阵、[24](24-temp-security-code.md) 临时代码录入/更新正式证券/取消发行。

> **三类调库同构说明**：证券池调库（[04]/[05]/[11]）、禁投池调整（[15]/[16]/[17]，主体级）、CRMW 池调库（[19]/[20]/[21]，凭证级）在校验规则、流程快照、审批流转、`audit_status` 状态枚举上完全同构。差异：①操作对象分别为证券 / 主体 / CRMW 凭证+标的证券；②落地表分别为 `ip_pool_status`（`pool_type` 区分）/ `ip_pool_status`（主体级 + `syncCompanyBonds` 同步债券）/ `ip_pool_status_crmw`（独立表，`pool_type='crmw'`）；③批次号前缀 `BOND` / `BOND` / `CRMW`。

核心状态枚举（`ip_adjust_log.audit_status`，三类调库通用）：`-1`无效 / `00`待审核 / `10`审核通过待审批 / `11`驳回待修改 / `20`审批通过 / `21`审批驳回 / `32`O32自动审批 / `99`发起人已撤回。仅 `20` 落地池状态。

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
