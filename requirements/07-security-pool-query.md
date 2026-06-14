# 证券池查询需求说明

## 目标与角色

投资和风控人员按投资池、证券类型及状态查询当前有效证券池，并维护个人关注的“我的证券池”收藏。

## 核心业务线

1. 按投资池、证券代码/名称、类型、市场和状态分页查询。
2. 加载证券类型、状态和投资池树筛选项。
3. 展示证券当前有效池、风险信息和基础行情属性。
4. 将证券加入当前用户收藏。
5. 从收藏中移除证券。
6. 批量查询已收藏代码，渲染收藏状态。

## 接口清单

| 业务线 | 接口 |
|---|---|
| 证券池分页 | `/api/v1/securityPoolQuery/querySecurityPoolPage` |
| 筛选选项 | `querySecurityTypeList`、`querySecurityStatusList`、`queryPoolTreeList` |
| 我的证券池 | `addToMyPool`、`deleteFromMyPool`、`queryFavoritedCodeList` |

## 关键校验

- 查询只返回当前有效池状态，不混入未审批或已调出数据。
- 收藏证券和用户必须存在，同一用户不可重复收藏同一证券。
- 删除收藏仅影响当前用户，不影响其他用户。

## 验收标准

- 多条件筛选与分页总数一致。
- 收藏操作后列表状态即时同步。
- `SecurityPoolQueryApiTest` 覆盖查询、选项和收藏业务线。
