# 规则管理中心需求说明

## 目标与角色

风控规则管理员维护 QLExpress 规则脚本、参数、预设选项和测试用例，通过在线执行及回归用例保证规则变更可验证、可追溯。

## 核心业务线

1. 分页查询规则，按名称、分类和状态筛选。
2. 新增或编辑规则基础信息、脚本、参数及枚举选项。
3. 启用、停用和逻辑删除规则。
4. 输入临时参数执行规则并记录结果和步骤日志。
5. 查询规则分类和预设选项集。
6. 新增、编辑、重命名和删除测试用例。
7. 单条或批量执行测试用例，展示最近结果及历史。

## 接口清单

| 业务线 | 接口 |
|---|---|
| 规则查询维护 | `/api/v1/rules/queryRulePage`、`queryRuleDetail`、`addOrEditRule`、`editRuleStatus`、`deleteRule` |
| 规则执行 | `/api/v1/rules/rule-runs/executeRule` |
| 规则选项 | `/api/v1/rules/options/queryCategoryList`、`queryPresetSetList` |
| 用例维护 | `/api/v1/testCases/queryTestCasePage`、`addOrEditTestCase`、`editTestCaseName`、`deleteTestCase` |
| 用例执行 | `/api/v1/testCases/runTestCase`、`runAllTestCases`、`queryRunHistoryList` |

## 关键校验

- 规则名称、分类和脚本必填；参数名在单条规则内唯一。
- number 参数通过 `BigDecimal` 精确解析，非法数字应返回业务错误。
- 停用或删除规则不可被新测试用例执行。
- 测试用例输入参数必须覆盖规则必填参数，执行历史需保留结果和日志。

## 验收标准

- 规则脚本保存后可立即执行，并可查看完整执行日志。
- 批量执行单条失败不阻断其他用例。
- `RuleManagerApiTest` 覆盖规则与测试用例全部接口。
