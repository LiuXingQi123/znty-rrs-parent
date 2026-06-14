# 投资池维护需求说明

## 目标与角色

投资池管理员维护投资池树、池间关系、关联审批流程、自动规则和操作权限，为证券调库提供可执行的业务边界。

## 核心业务线

1. 查询投资池平铺数据并按父子 ID 组装树。
2. 新建根池或子池，维护名称、描述、状态和排序。
3. 配置父子、联动、互斥等池关系，禁止循环关系和无效引用。
4. 为投资池关联有效的已发布审批流程。
5. 配置角色或人员的可见、可调入、可调出等权限。
6. 维护自动调入/调出规则，规则必须为有效启用状态。
7. 删除节点前校验子节点、池状态和业务引用。

## 接口清单

| 业务线 | 接口 |
|---|---|
| 树与详情 | `/api/v1/investmentPool/queryPoolList`、`queryPoolDetail` |
| 节点维护 | `addRootPool`、`addChildPool`、`deletePoolNode`、`addSeedPoolList` |
| 配置维护 | `editPoolConfig`、`editPoolRelation`、`editPoolPermission` |
| 关联选项 | `queryFlowOptionList`、`queryRoleList`、`queryUserList` |
| 规则选项 | `/api/v1/rules/queryRulePage`、`options/queryCategoryList` |

## 关键校验

- 同级池名称不可重复，父节点必须存在且不能指向自身或后代。
- 关联流程必须处于可用发布状态，关联规则必须处于启用状态。
- 权限主体类型仅允许角色或人员，主体 ID 必须真实存在。
- 删除操作不得留下无父节点的普通子池或悬空关系。

## 验收标准

- 树节点新增、配置、移动和删除后前后端结构一致。
- 不同用户仅能看到和操作其权限范围内的投资池。
- `InvestmentPoolApiTest` 覆盖维护、配置和选项业务线。
