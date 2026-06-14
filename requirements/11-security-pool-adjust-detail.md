# 证券池调库详情需求说明

## 目标与角色

申请人、审批人和审计人员查看单只证券及其调库批次的完整业务上下文，包括池状态、历史记录、流程步骤和可执行的后续动作。

## 核心业务线

1. 加载证券列表入口和选中证券详情。
2. 展示可调池范围、当前证券池及主体池状态。
3. 展示指定批次的调整主从记录。
4. 展示步骤节点、审批策略、候选处理人、动作、意见和时间。
5. 对可修改或可重新提交状态执行校验和再次提交。
6. 已结束记录保持只读，不允许重复发起审批动作。

## 接口清单

| 业务线 | 接口 |
|---|---|
| 证券与池上下文 | `/api/v1/securityPoolAdjust/querySecurityPage`、`querySecurityDetail`、`queryAdjustPoolList`、`querySecurityPoolStatus` |
| 历史与步骤 | `queryAdjustLogList`、`queryAdjustStepList` |
| 修改后提交 | `checkAdjust`、`addAdjustLog` |

## 关键校验

- `adjustLogId` 或批次号必须能定位有效主流程。
- 从属记录通过批次共享主流程步骤，不重复展示独立流程。
- 已通过、已驳回、已撤回等终态不存在待处理步骤。
- 重新提交必须重新执行权限、关系和风控校验。

## 验收标准

- 详情中的日志状态、步骤状态和最终池状态相互一致。
- 待处理节点高亮，终态只读，异常数据给出明确提示。
- `SecurityPoolAdjustDetailApiTest` 覆盖详情加载与重新提交业务线。
