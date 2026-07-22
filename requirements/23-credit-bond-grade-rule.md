# 信用债评级准入规则（主体内评分档矩阵）需求说明

> 前端页面：`credit_bond_grade_rule.html`（页面标题「主体内评分档规则」）
> 后端前缀：`/api/v1/creditBondGradeRule`
> 角色定位：维护「信用债期限分组 × 主体内评分档 → 可准入的信用债投资池」关系矩阵，作为信用债大库准入规则的全局唯一配置。仅查询与全量保存两个接口，无单条增删改。

---

## 1. 页面概览

单页结构（`el: '#credit_bond_grade_rule'`），顶栏（图标 +「主体内评分档规则」+ 系统名 + 当天日期 `today`）+ `page-body`（`toolbar` 含 刷新/修改/取消/保存 按钮组 + `matrix-wrap` 矩阵表 + `summary-bar` 汇总条）。

**编辑态切换** `editing` 布尔：非编辑态显示「刷新」「修改」；编辑态显示「取消」「保存」。`startEdit()` 先 `cloneMatrixMap` 存 `originalMatrixMap`；`cancelEdit()` 从 `originalMatrixMap` 恢复；`saveMatrix()` 调保存接口。

**初始化**（`created`）：`loadMatrix()` → `POST /api/v1/creditBondGradeRule/queryGradeRuleMatrix`（请求体 `{}`），回填 `termBuckets`、`ratingGrades`、`poolOptions`、`poolTreeData`（`buildPoolTreeData`）、`matrixMap`（`fillMatrixMap`），并 `cloneMatrixMap` 存 `originalMatrixMap`，记 `lastLoadedAt`。

**汇总条**：`期限 {{termBuckets.length}} 档` / `主体内评分档 {{ratingGrades.length}} 档` / `关系 {{totalRuleCount}} 条`（`totalRuleCount` = `matrixMap` 各 cell 数组长度之和）。`baseURL` 由 `js/api.js` 注入（`http://localhost:18090`）。

---

## 2. 筛选与查询逻辑

本模块**无搜索表单**，矩阵为全局唯一配置，`queryGradeRuleMatrix` 请求体为 `{}`。

- 查询接口：`POST /api/v1/creditBondGradeRule/queryGradeRuleMatrix`
- 请求体：`{}`（`CreditBondGradeRuleReq`，`rules`/`operatorId` 均不传）
- 后端 `CreditBondGradeRuleService.queryGradeRuleMatrix`：并行查 4 组数据并 `buildMatrixDto` 组装：
  1. `queryEnabledTermBucketList` → `SELECT ... FROM credit_bond_term_bucket WHERE enabled = 1 ORDER BY sort_no ASC, id ASC`
  2. `queryEnabledRatingGradeList` → `SELECT ... FROM credit_bond_inner_rating_grade WHERE enabled = 1 ORDER BY sort_no ASC, id ASC`
  3. `queryCreditBondPoolList` → `SELECT id,parent_id,pool_code,pool_name,pool_type,pool_level,inner_sort,status FROM ip_investment_pool WHERE parent_id = 1 AND pool_type = 'credit_bond' AND pool_level = 2 AND inner_sort BETWEEN 1 AND 5 AND status = 'enabled' AND is_deleted = 0 ORDER BY inner_sort ASC, id ASC`
  4. `queryEnabledRuleList` → `SELECT ... FROM credit_bond_pool_grade_rule WHERE enabled = 1 ORDER BY term_bucket_id ASC, inner_rating_grade_id ASC, sort_no ASC, id ASC`
- 返回 `CreditBondGradeRuleDto`：`termBuckets[]`、`ratingGrades[]`、`poolOptions[]`（`poolFullName` 后端拼成 `"信用债大库/" + poolName`）、`rules[]`（含 `poolCodeSnapshot`/`poolNameSnapshot` 快照）。
- 前端 `fillMatrixMap(rules)`：以 `cellKey(bucketId, gradeId) = bucketId + "_" + gradeId` 为键，先把所有 ratingGrades×termBuckets 组合初始化为空数组，再按 `rule.termBucketId + "_" + rule.innerRatingGradeId` push `rule.poolId`（去重）。
- 前端 `buildPoolTreeData`：根节点 `id:'credit_bond_root', poolName:'信用债大库', disabled:true`，子节点为 `poolOptions`（叶子，`disabled:false`）。

---

## 3. 业务逻辑

**核心定位**：维护信用债期限分组、主体内评分档与投资池的准入关系矩阵。**这不是「主体评级 × 债项评级 → 内部评级」矩阵**，而是**信用债评级准入矩阵**：

- **行 = 主体内评分档**（`credit_bond_inner_rating_grade`，`gradeCode` 如 `1`、`2+`、`2`、`2-`、`3+`、`3`、`3-`、`4`，共 8 档）
- **列 = 债券剩余期限分组**（`credit_bond_term_bucket`，`bucketCode` 如 `GT_5`(剩余期限>5)、`GT_3_LE_5`(5≥剩余期限>3)、`GT_1_LE_3`(3≥剩余期限>1)、`LE_1`(1≥剩余期限)，共 4 档；含 `expression_text`）
- **单元格 = 可准入的信用债投资池列表**（多选，限信用债大库一级库至五级库，即 `ip_investment_pool` 中 `parent_id=1, pool_type='credit_bond', pool_level=2, inner_sort 1..5` 的 5 个池）

业务含义：给定一只信用债的「剩余期限分组 × 主体内评分档」，决定它可准入到信用债大库的哪几个级别库（一~五级库）。

> **期限口径**：调库校验消费矩阵时，期限档按**剩余期限**匹配，取自 `rrs_securityinfo.date_exists`（天，INT），经 `CreditBondRemainTermUtil` 按 **天数 ÷ 365** 换算为年后再匹配 `credit_bond_term_bucket`；不是证券主数据 `term_year`（发行总期限）。`date_exists` 为空时跳过期限档（不卡矩阵）。

**CRUD 操作**（仅 2 个接口，无单条增删改、无启用/停用单条规则）：
- 查询矩阵：`queryGradeRuleMatrix`（只读）
- 保存矩阵：`editGradeRuleMatrix`（`@Transactional`）——**全量替换**：`buildRulesForSave` 校验+去重+组装 → `deleteAllRule()`（`DELETE FROM credit_bond_pool_grade_rule`，硬删除全部）→ `addRuleList(rules)`（批量 INSERT）。保存后再次 `queryGradeRuleMatrix` 返回最新矩阵。
- 期限分组、主体内评分档、投资池本身**均不在本页面维护**，由 SQL 演示数据预置（`rrs_credit_bond_grade_rule_demo_data.sql`）。

**前端编辑态**：每个单元格用 `el-popover` + `el-tree`（`show-checkbox check-strictly default-expand-all`）多选投资池；`filterValidPoolIds` 过滤掉根节点 `credit_bond_root`；`handlePoolTreeCheckChange` 即时同步 `matrixMap`；`removeCellPool` 支持标签直接移除。`collectRules()` 展开矩阵为 `[{termBucketId, innerRatingGradeId, poolId}]` 保存参数。

---

## 4. 接口清单

前缀 `/api/v1/creditBondGradeRule`，统一 `@PostMapping`、`@RequestBody`、返回 `ApiResponse<T>`。

| 路径 | 请求体字段 | 返回类型 | 用途 |
|---|---|---|---|
| `queryGradeRuleMatrix` | `{}`（`CreditBondGradeRuleReq` 空对象） | `ApiResponse<CreditBondGradeRuleDto>` | 查询期限分组×主体内评分档×投资池准入矩阵（含 4 个列表） |
| `editGradeRuleMatrix` | `rules: [{termBucketId:Long, innerRatingGradeId:Long, poolId:Long}]`, `operatorId:String?` | `ApiResponse<CreditBondGradeRuleDto>` | 全量替换保存矩阵关系（先删后增），返回最新矩阵 |

> `CreditBondGradeRuleReq` 仅含 `List<RuleItem> rules` 与 `String operatorId`；`RuleItem` 仅 3 字段 `termBucketId/innerRatingGradeId/poolId`。`operatorId` 后端**未使用**（无审计表），前端固定传 `'system'`。

---

## 5. 关键数据库表

建表脚本 `sql/rrs_credit_bond_grade_rule_schema.sql`，演示数据 `sql/rrs_credit_bond_grade_rule_demo_data.sql`。无物理外键，靠应用层维护。

| 表 | 用途 | 关键字段 |
|---|---|---|
| `credit_bond_term_bucket` | 信用债期限分组配置 | `id, bucket_code(UNIQUE), bucket_name, min_term_year DECIMAL(20,4), min_inclusive TINYINT(1), max_term_year, max_inclusive, expression_text, sort_no, enabled(1/0), crte_time, updt_time` |
| `credit_bond_inner_rating_grade` | 主体内评分档字典 | `id, grade_code(UNIQUE, 如 1/2+/2/2-/3+/3/3-/4), grade_name, sort_no, enabled(1/0), crte_time, updt_time` |
| `credit_bond_pool_grade_rule` | 期限×评分档×投资池关系 | `id, term_bucket_id, inner_rating_grade_id, pool_id, pool_code_snapshot, pool_name_snapshot, enabled(1/0), sort_no, crte_time, updt_time`；UNIQUE KEY `uk_credit_bond_pool_grade_rule (term_bucket_id, inner_rating_grade_id, pool_id)` |
| `ip_investment_pool`（只读引用） | 信用债大库一~五级库 | 查询条件 `parent_id=1 AND pool_type='credit_bond' AND pool_level=2 AND inner_sort BETWEEN 1 AND 5 AND status='enabled' AND is_deleted=0` |

> 演示数据：期限 4 档、评分档 8 档（id 1~8）、关系 38 条。pool_id 2=一级库、3=二级库、4=三级库、5=四级库、6=五级库。

---

## 6. 状态流转

本模块无业务状态枚举，仅有配置级 `enabled TINYINT(1)`（1=启用/0=停用）：
- 三个配置表均有 `enabled` 字段；查询一律 `WHERE enabled = 1`。
- 保存时 `CreditBondGradeRuleService.ENABLED = 1` 硬编码，新增关系恒为启用。
- 页面无启停单条规则的入口；`deleteAllRule` 为硬 DELETE（非软删），全量替换不保留停用规则。

---

## 7. 关键校验/业务规则

- `editGradeRuleMatrix` 入口：`req == null` 抛「请求参数不能为空」；`rules == null` 置空表。
- `buildRulesForSave` 逐条校验：
  - `termBucketId` 不在启用期限分组 → `BizException("期限分组不存在或已停用，termBucketId=" + ...)`
  - `innerRatingGradeId` 不在启用评分档 → `BizException("主体内评分档不存在或已停用，innerRatingGradeId=" + ...)`
  - `poolId` 不在信用债大库一~五级库 → `BizException("投资池不属于信用债大库一级库至五级库，poolId=" + ...)`
- 去重：`uniqueKey = termBucketId + "_" + innerRatingGradeId + "_" + poolId`，`HashSet.uniqueKeys.add` 失败则 `continue` 跳过（不报错）。
- 快照：`poolCodeSnapshot`/`poolNameSnapshot` 取自 `InvestmentPoolBo`；`sortNo = pool.getInnerSort()`；`enabled = 1`；`crteTime`/`updtTime = now`。
- `queryCreditBondPoolList` **硬编码 `parent_id = 1`**：强依赖 `ip_investment_pool` 种子数据中信用债大库根节点 id=1（潜在耦合点）。
- 事务：`editGradeRuleMatrix` 标 `@Transactional(rollbackFor = Exception.class)`，`deleteAllRule` + `addRuleList` 同事务，失败回滚。

---

## 8. 验收标准

- 查询返回 4 个列表（期限分组、评分档、投资池、规则），矩阵按 `期限×评分档` 正确填充。
- 保存为全量替换（先硬删全部再批量插入），保存后返回最新矩阵。
- 非法 `termBucketId`/`innerRatingGradeId`/`poolId`（不在启用集合/非信用债大库一~五级库）保存时抛 `BizException`。
- 同一 `(termBucketId, innerRatingGradeId, poolId)` 去重，不重复入库。
- 取消编辑恢复到 `originalMatrixMap`，不触发保存。

## 9. 关键源码索引

- 前端：`znty-rrs-ui/credit_bond_grade_rule.html`、`css/credit_bond_grade_rule.css`、`js/api.js`
- Controller：`controller/CreditBondGradeRuleController.java`
- Service：`service/CreditBondGradeRuleService.java`（`queryGradeRuleMatrix`、`editGradeRuleMatrix`、`buildMatrixDto`、`buildRulesForSave`、`buildTermBucketMap`/`buildRatingGradeMap`/`buildPoolMap`）
- Mapper：`mapper/CreditBondGradeRuleMapper.java` / `resources/mapper/CreditBondGradeRuleMapper.xml`
- 实体：`entity/creditbondgraderule/CreditBondGradeRuleDto.java`（含静态内部类 `TermBucketItem`/`InnerRatingGradeItem`/`PoolOptionItem`/`RuleItem`）、`entity/creditbondgraderule/CreditBondGradeRuleReq.java`（含 `RuleItem`）；`entity/bo/CreditBondTermBucketBo.java`、`CreditBondInnerRatingGradeBo.java`、`CreditBondPoolGradeRuleBo.java`、`InvestmentPoolBo.java`
- SQL：`sql/rrs_credit_bond_grade_rule_schema.sql`、`sql/rrs_credit_bond_grade_rule_demo_data.sql`
