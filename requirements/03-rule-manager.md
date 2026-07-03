# 规则管理中心需求说明

> 前端页面：`rule_manager.html`（规则列表 + 测试脚本两 Tab）
> 后端前缀：`/api/v1/rules`（规则）、`/api/v1/testCases`（测试用例）
> 角色定位：风控规则管理员维护 QLExpress 规则脚本、参数、预设选项和测试用例，通过在线执行及回归用例保证规则变更可验证、可追溯。

---

## 1. 页面概览与初始化

Vue 2.5 + Element UI 2.15 + axios + moment（CDN）。所有请求走统一封装 `apiPost()`，后端基地址 `apiBase: 'http://localhost:18090'`。

**布局**：顶部导航栏（logo +「规则管理」+ 副标题 + 当天日期）+ 内容区（内嵌 `.module-nav` 二级 Tab：规则列表 / 测试脚本）。

`currentPage` 三态：`rules`（规则列表页）、`tests`（测试脚本页）。

各分页及弹窗：
1. **规则列表页**：搜索面板（关键词 + 状态筛选 + 查询/重置/新建 + 总数徽标）+ 规则表格（带 expand 展开行）+ 分页。
2. **新建/编辑规则 Dialog**：基本信息卡片（名称/描述/分类/参数标签）+ 规则脚本卡片（QLExpress 代码编辑区 + 快速模板）。
3. **测试脚本页**：搜索面板（关键词 + 执行结果筛选 + 运行全部）+ 批量进度条 + 测试用例表格（带 expand）+ 分页。
4. **修改用例名称 Dialog**。
5. **执行测试 Dialog**：用例名称 + 输入参数表单（按类型渲染）+ 脚本预览 + 执行输出控制台 + footer（关闭/保存为测试用例/执行）。
6. **新增参数 Dialog**：两步式（选类型 → 填名称/字段名/选项来源）。
7. **删除确认 Dialog**。

**初始化**（`mounted`）顺序触发四个加载：
- `loadCategories()` → `POST /api/v1/rules/options/queryCategoryList`（规则分类下拉）
- `loadPresetSets()` → `POST /api/v1/rules/options/queryPresetSetList`（预设选项集）
- `loadRules()` → `POST /api/v1/rules/queryRulePage`（规则列表）
- `loadTestCases()` → `POST /api/v1/testCases/queryTestCasePage`（测试用例列表）

---

## 2. 规则查询逻辑

### 2.1 筛选项

- `searchKey`：关键词（规则名称/描述/参数），回车查询。
- `filterStatus`：状态下拉（`active` 已启用 / `disabled` 已禁用）。
- 操作：查询 `handleRuleSearch`、重置 `handleRuleReset`、新建 `openCreateRuleDialog`。

### 2.2 查询接口

- 路径：`POST /api/v1/rules/queryRulePage`
- 请求体：`{ keyword, status, pageIndex, pageSize }`（空值传 undefined 不序列化）
- 后端 `RuleService.queryRulePage`：`PageHelper.startPage` 分页；`ruleMapper.queryRulePage(keyword, status)`，`<where>` 拼接 `IFNULL(deleted_flag,0)=0`、`status` 精确、`keyword` 匹配 `rule_name LIKE` OR `description LIKE` OR 子查询 `rule_param` 中 `param_name/param_label LIKE`；`ORDER BY crte_time DESC, id DESC`；`loadRuleParamMap` 两阶段批量加载参数+选项避免 N+1。
- 返回 `PageResult<RuleDto>`（`id, name, description, category, params, script, status, createdAt`）。

### 2.3 表格列渲染

| 列 | 渲染 |
|---|---|
| expand | 展开后显示参数面板 + 脚本预览 + 操作按钮（执行测试/添加为测试脚本） |
| 序号 | `(pageIndex-1)*pageSize + $index + 1` |
| 规则名称 | 蓝色链接，点击 `editRule(row)` |
| 规则描述 | 直接显示 |
| 规则分类 | `ruleCategoryName(row.category)` 由 `categories` 字典映射 |
| 参数数量 | `(row.params||[]).length` |
| 状态 | `el-tag` success/info，文案「已启用/已禁用」 |
| 创建时间 | 直接显示 |
| 操作 | 编辑 / 测试 / 启用\|禁用切换 / 删除 |

分页：`page-sizes=[10,20,50,100]`，layout `total, sizes, prev, pager, next, jumper`。后端 `PageRequest` pageSize 硬上限 100。

---

## 3. 规则增删改逻辑

### 3.1 新建/编辑规则

- 入口：「新建规则」→ `openCreateRuleDialog`（`editingRule=null`）；表格「编辑」/规则名链接 → `editRule(rule)`（`editingRule=rule`，深拷贝避免污染列表）。
- 表单字段：规则名称（maxlength=50）、规则描述、规则分类、规则参数（标签云，每标签显示类型徽标+名称，可关闭）、规则脚本（textarea + 4 个快速模板：条件折扣/分级评分/开关控制/加权计算）。
- 保存 `saveRule()`：前端校验 `name` 和 `script` 非空，构造请求体：
  ```json
  { "id": "编辑时为规则ID，新建时为null", "name", "description", "category",
    "paramList": [{"label","name","type":"string|number|select|multiselect","options":["..."]}],
    "script" }
  ```
  注意：前端 `_value`/`_multiValue`（临时输入值）**不发送**，仅发配置字段。
- 接口：新建 `/api/v1/rules/addRule`，编辑 `/api/v1/rules/editRule`。成功后关弹窗、notify、`loadRules()` 刷新。

**后端 addRule**（`@Transactional`）：`validateSaveReq`（name/script 非空）；构造 `RuleDefinitionBo`（`ruleName` trim、`categoryCode` 默认 "business"、`status="active"`、`deletedFlag=0`）；`addRule` INSERT 回填 id；`replaceParams` 全量替换参数和选项（先删后增：`deleteParamOptionsByRuleId` + `deleteParamsByRuleId`，再遍历 paramList 插入 `RuleParamBo`，`paramType` 默认 "string"、`paramLabel` 默认取 `paramName`、`required=0`、`sortNo=i+1`，`saveOptions` 写候选项）；返回 `detailById`。

**后端 editRule**（`@Transactional`）：`validateSaveReq`；`requireRule(id)` 校验存在；`editRule` UPDATE（仅未删除记录）；`replaceParams` 全量替换；返回 `detailById`。

### 3.2 状态切换

`toggleStatus(rule)`：`newStatus = rule.status === 'active' ? 'disabled' : 'active'`，调 `POST /api/v1/rules/editRuleStatus`，请求体 `{id, status}`。后端校验 status 白名单 `active`/`disabled`，`editRuleStatus` UPDATE，`updated==0` 抛「规则不存在」。成功后前端本地更新 `rule.status`。

### 3.3 删除

`confirmDelete()` → `POST /api/v1/rules/deleteRule`，请求体 `{id}`。后端先 `detailById` 返回快照，再 `deleteRuleSoft` 软删除（`deleted_flag=1`），`updated==0` 抛「规则不存在」。

> 注：删除为软删除，保留数据。前端弹窗提示「相关测试用例也会受到影响」，但后端**未级联删除**测试用例——规则软删后，关联用例执行时 `queryRuleById` 返回 null 抛「测试用例关联规则不存在」。

### 3.4 参数定义（四类型）

参数由「新增参数 Dialog」两步式录入：
- **第一步**选类型：4 张卡片 `string`（任意文本）/`number`（整数或小数）/`select`（单选）/`multiselect`（多选）。
- **第二步**填信息：显示名称（`label`）、字段名（`name`，作为脚本变量名）；select/multiselect 多一个「选项来源」，可选预设选项集或自定义选项（手动输入回车追加）。

`confirmAddParam()` 前端校验：`label`/`name` 非空，select/multiselect 须有 ≥1 选项，`name` 不与已有参数重名。通过后 `form.params.push({label,name,type,options})`。

参数类型标签映射：`string→STR`、`number→NUM`、`select→单选`、`multiselect→多选`。数据库 `rule_param.param_type` 取值同（dict.js `DICT_RULE_PARAM_TYPE`）。

---

## 4. 规则执行逻辑（核心）

### 4.1 QLExpress 引擎配置

`ExpressRunner` 单例由 `AppConfig.expressRunner()` 注册（`new ExpressRunner()`），注入到 `RuleService`。

### 4.2 临时执行入口（规则列表「测试」按钮）

`openTestDialog(rule)`：为参数补充 `_value=''`/`_multiValue=[]` 临时字段，设 `testingRule`、自动生成用例名「{规则名} 测试 #{秒.毫秒}」、清空 `testOutput`，打开执行测试 Dialog。

`runTest()`：请求体 `{id: testingRule.id, runParams: collectTestingParams()}`。`collectTestingParams()`：遍历 `testingRule.params`，multiselect 取 `_multiValue.join(',')`，其余取 `_value`，组装 `paramName → value` 映射。调 `POST /api/v1/rules/executeRule`。成功：`testOutput = result.logs || []`；异常：push 一条 error 日志。

### 4.3 后端执行核心 `executeAndRecord`

`RuleService.executeRule` → `executeAndRecord(rule, runParams, caseId=null)`，流程：
1. `startTime`，新建 `RuleRunResultDto result`。
2. `addLog(result, startTime, "info", "开始执行规则: " + ruleName)`。
3. 遍历 `safeParams`，逐条 `addLog(..., "info", "参数绑定: " + key + " = " + value)`。
4. try `executeScript(rule, safeParams)`：
   - 成功：`output = String.valueOf(result)`，`addLog(...,"success","执行完成，返回值: "+output)`，`status="pass"`。
   - 异常：`status="fail"`，`errorMessage=e.getMessage()`，`addLog(...,"error","执行异常: "+errorMessage)`。
5. `finishTime`，构造 `RuleTestRunBo`（`caseId` 临时执行为 null、`ruleId`、`runStatus`、`output`、`errorMessage`、`startTime`、`finishTime`）。
6. `testCaseMapper.addRun(run)` 持久化执行记录到 `rule_test_run`（`useGeneratedKeys` 回填 id）。
7. `saveLogs(run.getId(), result.getLogs())` 批量持久化步骤日志到 `rule_test_run_log`。
8. 设置 `result.status`/`result.output` 返回。

`addLog`：`log.put("time", HH:mm:ss)`、`log.put("type", type)`、`log.put("msg", message)`。

### 4.4 参数解析（executeScript + convertValue）

`executeScript`：
- `DefaultContext<String, Object> context`。
- 重新从 DB 加载规则参数定义 `listParams(rule.getId())`，按 `paramName` 建立 `paramDefineMap`。
- `params.forEach((key, value) -> context.put(key, convertValue(value, paramDefineMap.get(key))))`。
- `expressRunner.execute(script, context, null, true, false)`。

`convertValue(value, param)`：
- `value == null` → 返回 null。
- `param == null` 或 `type != "number"` → 原样返回字符串。
- `type == "number"`：`new BigDecimal(value)` 精确解析；含小数点 `.` → `Double`，否则 → `Long`；解析失败（NumberFormatException）→ 原样返回字符串（静默降级，不抛错）。
- multiselect 在前端 `collectTestingParams` 已 join 成逗号分隔字符串，后端不拆分，直接作为字符串注入上下文。

### 4.5 执行结果展示

执行测试 Dialog 底部「执行输出」区：黑底控制台 `v-for` 渲染 `testOutput`，每行 `{time, type, msg}`：时间灰色、tag 按类型着色（info 蓝/success 绿/error 红/warn 橙）、msg。与后端 `RuleRunResultDto.logs` 结构一致。

### 4.6 测试用例管理

**列表查询** `loadTestCases()` → `POST /api/v1/testCases/queryTestCasePage`，请求体 `{keyword, result, pageIndex, pageSize}`。`result` 取值 `pass`/`fail`/`pending`。后端 `queryCasePage(keyword, result)`：keyword 匹配 `case_name`/`rule_name_snapshot`/子查询参数名；`result != 'pending'` → `last_result = #{result}`，`result == 'pending'` → `last_result IS NULL`（pending 在 DB 存 NULL）。批量加载参数值和关联规则，组装 `TestCaseDto`（`id/name/ruleId/ruleName/params(Map)/lastResult/lastOutput/lastRunTime`）。

**新增用例**（从测试弹窗保存）`addTestCaseFromDialog` → `POST /api/v1/testCases/addTestCase`，请求体 `{id:null, name, ruleId, params:{paramName:value}}`。后端 `@Transactional`：校验 name/ruleId 非空、规则存在；构造 `RuleTestCaseBo`（`ruleNameSnapshot` 快照）；`addCase`；`replaceCaseParams`（先删后增，按规则参数定义建字典，逐条插入 `RuleTestCaseParamBo`，同时快照 `paramLabelSnapshot`/`paramTypeSnapshot`，保证规则改名/改类型后历史可复现）。

**修改用例名称** `saveTestCaseName` → `POST /api/v1/testCases/editTestCaseName`，请求体 `{id, name}`。后端仅更新 `case_name` 和 `updt_time`。
（`/editTestCase` 全量编辑接口后端提供但前端未使用。）

**删除用例** `deleteTestCase(id)` → `$confirm` 后 `POST /api/v1/testCases/deleteTestCase`，请求体 `{id}`。后端**物理删除**：`requireCase` → `detailById` 返回快照 → `deleteParamsByCaseId` → `deleteCaseById`（DELETE）。**不级联删除** `rule_test_run`/`rule_test_run_log`（孤儿数据保留）。前端若当前页只剩 1 条且 `pageIndex>1`，自动回退一页。

**单条执行** `runSingleTest(tc)` → `POST /api/v1/testCases/runTestCase`，请求体 `{id}`。前端先置 `tc.lastResult='running'`。后端 `@Transactional`：`requireCase`；`queryRuleById`，规则不存在抛异常；`loadCaseParamMap` 取参数值；**调 `ruleService.executeAndRecord(rule, params, testCase.getId())`**（复用核心执行逻辑，caseId 非 null）；用 runResult 更新 `testCase.lastResult/lastOutput`，`lastRunTime=new Date()`；`editCaseLastResult`；返回 `detailById`。

**批量执行全部** `runAllTests()` → `POST /api/v1/testCases/runAllTestCases`，请求体 `{}`。后端**非事务方法**：`queryAllCaseList` 取全部用例；循环每个用例，通过 `@Lazy` 自注入代理 `self.runTestCase` 调用，**每个用例享有独立的 `@Transactional`**，单条异常被 catch 置 fail、写库、不影响后续；重新 `queryAllCaseList` + 批量加载返回 `List<TestCaseDto>`。前端成功后 `loadTestCases()` 刷新，弹 notify 统计。

**执行历史查询** `TestCaseController.queryRunHistoryList` → `TestCaseService.queryRunHistoryList`：按 caseId 查 `rule_test_run`（`start_time DESC`），逐条查日志（`log_time ASC, id ASC`），用 `RuleRunResultDto.from(run, logMaps)` 组装。前端未直接调用（后端预留能力）。

---

## 5. 接口清单

统一 `POST`、`@RequestBody`、返回 `ApiResponse<T>`（`success=true` 成功）；分页 `PageResult<T>`。

### 5.1 规则接口（前缀 `/api/v1/rules`）

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `queryRulePage` | keyword, status, pageIndex, pageSize | `PageResult<RuleDto>` | 分页查询规则列表 |
| `queryRuleDetail` | id | `RuleDto` | 查规则详情（前端未用） |
| `addRule` | id(null), name, description, category, paramList[{label,name,type,options}], script | `RuleDto` | 新增规则 |
| `editRule` | 同上（id 必填） | `RuleDto` | 编辑规则（全量覆盖参数） |
| `editRuleStatus` | id, status(active\|disabled) | `RuleDto` | 启用/禁用切换 |
| `deleteRule` | id | `RuleDto`（删除前快照） | 软删除规则 |
| `executeRule` | id, runParams{paramName:value} | `RuleRunResultDto`（含 status/output/logs） | 临时执行规则脚本 |
| `options/queryCategoryList` | `{}` | `List<CategoryDto{code,name}>` | 规则分类下拉 |
| `options/queryPresetSetList` | `{}` | `List<PresetSetDto{id,name,options[]}>` | 预设选项集下拉 |

### 5.2 测试用例接口（前缀 `/api/v1/testCases`）

| 路径 | 请求体字段 | 返回结构 | 用途 |
|---|---|---|---|
| `queryTestCasePage` | keyword, result(pass\|fail\|pending), pageIndex, pageSize | `PageResult<TestCaseDto>` | 分页查询用例 |
| `addTestCase` | id(null), name, ruleId, params{name:value} | `TestCaseDto` | 新增用例 |
| `editTestCase` | id, name, ruleId, params | `TestCaseDto` | 全量编辑（前端未用） |
| `editTestCaseName` | id, name | `TestCaseDto` | 仅改名 |
| `deleteTestCase` | id | `TestCaseDto`（删除前快照） | 物理删除用例 |
| `runTestCase` | id | `TestCaseDto`（含最新 lastResult/lastOutput） | 单条执行 |
| `runAllTestCases` | `{}` | `List<TestCaseDto>` | 批量执行全部 |
| `queryRunHistoryList` | id | `List<RuleRunResultDto>`（含日志） | 查执行历史（前端未用） |

### 5.3 返回结构细节

- `RuleRunResultDto`：`id/ruleId/caseId/status/output/errorMessage/startTime/finishTime/logs`，`logs` 每项 `{time(HH:mm:ss), type(info\|success\|error), msg}`。
- `RuleDto.params` 每项 Map：`{id:Long, label, name, type, options:List<String>}`。
- `TestCaseDto.params`：`LinkedHashMap<String,String>` 保持插入顺序。

---

## 6. 关键数据库表

建表脚本 `sql/rrs_rule_schema.sql`，演示数据 `rrs_rule_demo_data.sql`。共 10 张表，无数据库物理外键（靠应用层维护）。

| 表 | 用途 | 关键字段/枚举 |
|---|---|---|
| `rule_definition` | 规则定义 | `rule_name, description, category_code, script(MEDIUMTEXT), status(active/disabled), deleted_flag(0/1)` |
| `rule_param` | 规则参数 | `rule_id, param_name(脚本变量名), param_label, param_type(string/number/select/multiselect), required, sort_no` |
| `rule_param_option` | 参数选项 | `param_id, option_value, option_label, sort_no` |
| `rule_category` | 规则分类 | `category_code, category_name, sort_no, enabled`（6 类：risk/credit/pricing/admission/warning/other） |
| `rule_preset_option_set` + `rule_preset_option_item` | 预设选项集 | 12 套预设（主体评级/外评机构/债券期限/证券类型/行业分类等） |
| `rule_test_case` | 测试用例 | `case_name, rule_id, rule_name_snapshot, last_result(pending/running/pass/fail，pending 存 NULL), last_output, last_run_time` |
| `rule_test_case_param` | 用例参数值 | `case_id, param_name, param_label_snapshot, param_type_snapshot, param_value(TEXT，multiselect 逗号分隔)` |
| `rule_test_run` | 测试执行记录 | `case_id(临时执行为 NULL), rule_id, run_status(running/pass/fail), output, error_message, start_time, finish_time` |
| `rule_test_run_log` | 执行步骤日志 | `run_id, log_time, log_type(info/success/error), message` |

**表间关系**：
```
rule_category 1──N rule_definition 1─┬─ N rule_param 1──N rule_param_option
                                     └─ N rule_test_case 1──N rule_test_case_param
                                               └─ N rule_test_run 1──N rule_test_run_log
rule_preset_option_set 1──N rule_preset_option_item（独立，仅作选项模板）
```

---

## 7. 关键校验与约束

### 7.1 脚本语法校验

- 前端无语法校验：`saveRule` 仅校验 `name`/`script` 非空。
- 后端无语法预检：`validateSaveReq` 只校验非空，保存时不执行脚本。
- 运行时校验：语法错误在 `expressRunner.execute` 时抛异常，被 `executeAndRecord` 的 catch 捕获，置 `fail`、写 error 日志。
- QLExpress 兼容性：脚本中变量声明用弱类型（如 `poolLevel = "..."`、`spreadBp = 0`），不能用 Java 强类型声明（无 StringBuilder/boolean/int/float 声明）。

### 7.2 参数校验

- 前端：新增参数 `label`/`name` 非空，select/multiselect 须有 ≥1 选项，`name` 不重复（仅前端校验，后端不查重）；保存规则 `name`/`script` 非空；保存用例名非空。
- 后端：`validateSaveReq`（name/script 非空）；`editRuleStatus`（status 白名单）；`requireRule`（id 非空、存在）；`TestCaseService.validateSaveReq`（name/ruleId 非空）；`runTestCase`（关联规则不存在抛异常）；`PageRequest` pageSize 上限 100。

### 7.3 参数类型转换约束（关键）

- 仅 `number` 类型走数值转换，其余一律按字符串传入上下文。
- number 类型用 `BigDecimal` 精确解析；含 `.` → `Double`，否则 → `Long`。
- 解析失败（非数字字符串）→ 静默降级为原字符串（不抛错），可能导致脚本比较时类型不一致。
- multiselect 在前端已 join 成逗号分隔字符串，后端不拆分。

### 7.4 执行异常处理

- `executeAndRecord` 用 try/catch 包裹 `executeScript`：任何异常都置 `fail`，记录 `errorMessage`，**不向上抛**，保证执行记录和日志一定落库。
- `runAllTestCases` 用 try/catch 包裹 `self.runTestCase`：单条失败被捕获置 fail 写库，循环继续——单条失败不影响其余用例。
- 全局异常处理 `ExceptionConfig`：`BizException` → `ApiResponse.fail(message)`（BizException.code 仅用于日志区分 400/404，不进入响应体）；其他 Exception → 记日志 + `ApiResponse.fail("系统繁忙，请稍后重试")`。

### 7.5 事务与级联

- `addRule`/`editRule`/`editRuleStatus`/`deleteRule` 均 `@Transactional`。
- `addTestCase`/`editTestCaseName`/`deleteTestCase`/`runTestCase` 均 `@Transactional`。
- `executeAndRecord` 自身 `@Transactional`，由 `runTestCase` 调用时纳入用例事务。
- `runAllTestCases` **不加事务**，通过 `self` 代理让每条用例独立事务，避免一条失败回滚全部。
- 参数替换 `replaceParams`/`replaceCaseParams` 均为「先删后增」全量替换，依赖事务保证一致性。

### 7.6 软删除与隔离

- `rule_definition` 软删除（`deleted_flag=1`），所有查询带 `IFNULL(deleted_flag,0)=0`。
- `rule_test_case` 物理删除（DELETE）。
- `rule_test_run`/`rule_test_run_log` 永不删除（删除用例时不级联清理，存在孤儿数据）。

## 8. 验收标准

- 规则脚本保存后可立即执行，并可查看完整执行日志。
- number 参数按 BigDecimal 精确解析，含小数点→Double，否则→Long。
- 批量执行单条失败不阻断其他用例。
- 用例参数快照保证规则变更后历史可复现。
- `RuleManagerApiTest` 覆盖规则与测试用例全部接口。

## 9. 关键源码索引

- 前端：`znty-rrs-ui/rule_manager.html`（`loadRules`、`loadTestCases`、`saveRule`、`toggleStatus`、`runTest`、`addTestCaseFromDialog`、`runSingleTest`、`runAllTests`、`deleteTestCase`、新增参数 Dialog）
- dict.js：`DICT_RULE_CATEGORY`、`DICT_RULE_STATUS`、`DICT_RULE_PARAM_TYPE`、`DICT_TEST_RESULT`、`DICT_RUN_STATUS`、`DICT_LOG_TYPE`
- Controller：`RuleController.java`、`TestCaseController.java`
- Service：`RuleService.java`（`executeAndRecord`、`executeScript`、`convertValue`、`addLog`/`saveLogs`、`replaceParams`）、`TestCaseService.java`（`runTestCase`、`runAllTestCases`、`replaceCaseParams`）
- Mapper：`RuleMapper.xml`、`TestCaseMapper.xml`
- 配置：`AppConfig.java`（ExpressRunner Bean）、`ExceptionConfig.java`（全局异常）
- SQL：`sql/rrs_rule_schema.sql`、`sql/rrs_rule_demo_data.sql`
