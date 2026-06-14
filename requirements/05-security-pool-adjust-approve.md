# 证券池调库审核需求说明

## 目标与角色

审批处理人查看调库业务上下文和流程步骤，对当前待办执行通过、驳回或其他允许动作，系统根据流程配置推进下一节点并更新最终状态。

## 核心业务线

1. 加载证券详情、池状态、调整记录和完整步骤链。
2. 识别当前用户可处理的 pending 步骤。
3. 抢占节点由首位有效处理人完成，其余候选步骤标记 skipped。
4. 会签节点等待全部人员完成后再推进。
5. 通过时进入下一审批、自动任务、通知或结束节点。
6. 驳回时按路由进入修改节点或直接结束。
7. 管理员代办必须保留代操作说明，普通用户不可处理他人步骤。

## 接口清单

| 业务线 | 接口 |
|---|---|
| 审批上下文 | `/api/v1/securityPoolAdjust/querySecurityPage`、`querySecurityDetail`、`queryAdjustPoolList`、`querySecurityPoolStatus` |
| 历史与步骤 | `/api/v1/securityPoolAdjust/queryAdjustLogList`、`queryAdjustStepList` |
| 提交审批 | `/api/v1/securityPoolAdjustFlow/submitAdjustAudit` |
| 驳回修改后再提交 | `/api/v1/securityPoolAdjust/checkAdjust`、`addAdjustLog` |

## 关键校验

- `stepId`、批次号、动作、处理人必填，步骤必须为待处理状态。
- 非候选处理人不得处理；同一步骤不可重复提交。
- 流程推进需满足审批策略和条件路由。
- 最终通过才更新池状态；驳回或撤回不得错误生效。

## 验收标准

- 抢占、会签、驳回修改、结束等路径均产生完整步骤状态。
- 审批结果与调整日志审核状态一致。
- `SecurityPoolAdjustApproveApiTest` 覆盖上下文加载和审批提交。
