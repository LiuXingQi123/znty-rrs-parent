# 临时代码管理需求说明

> 前端页面：`temp_security_code.html`
> 后端前缀：`/api/v1/tempSecurityCode`
> 角色定位：为尚未取得正式 Wind 代码的信用债维护临时证券代码，待正式证券信息明确后「更新为正式证券」并同步写入 `rrs_securityinfo`，或「取消发行」终止流程。提供查询、新增、更新、取消发行和删除能力。

---

## 1. 页面概览

单页结构（`el: '#temp_security_code'`），顶栏（SVG 图标 +「临时代码管理」+ 系统名 + `currentDateText`）+ `list-body`（`search-panel` 筛选行 + `table-card` 表格+分页）。

**2 个弹窗**：
1. **新增临时代码** `addDialogVisible`（50%）：`form-tip`「新增后状态默认为临时，并同步写入临时代码占位证券，可用于后续调库，不代表正式发行。」+ 临时证券信息区（名称/代码/市场/类型/缓释凭证代码/发行主体/发行日期/到期日期）。名称、代码输入框有 placeholder；代码提示需带市场后缀。
2. **更新正式证券** `updateDialogVisible`（50%）：`form-tip`「更新后状态变为已更新，临时证券与正式证券信息将一并保存。」+ 临时证券信息区（可编辑）+ 正式证券信息区（证券名称/代码/市场/类型）。

**初始化**（`created`）：并行调用 `loadOptions()`（`/queryTempSecurityCodeOptions`）与 `loadList()`（`/queryTempSecurityCodePage`）；`mounted()` 注册 `resize` 监听，`beforeDestroy()` 清理。`baseURL` 由 `js/api.js` 注入（`http://localhost:18090`）。

---

## 2. 筛选与查询逻辑

### 2.1 筛选项（`queryParam`）

| 字段 | 控件 | 匹配 |
|---|---|---|
| `tempSecurityCode` | el-input（`v-model.trim`，回车查询） | LIKE `%xxx%` |
| `tempSecurityName` | el-input（`v-model.trim`，回车查询） | LIKE `%xxx%` |
| `statusList` | el-select 多选（`collapse-tags`，可清空） | `status IN (...)` |
| `oprtSourceList` | el-select 多选（`collapse-tags`，可清空）；**默认 `['manual']`** | `oprt_source IN (...)`；取值 `manual=人工 / job=定时任务 / other=其他` |

操作：`handleSearch`（重置 `pageIndex=1` 后 `loadList`）、`handleReset`（清空代码/名称/状态，操作来源恢复默认 `['manual']` 后回首页）、`openAddDialog`（新增）。

### 2.2 列表查询

- 接口：`POST /api/v1/tempSecurityCode/queryTempSecurityCodePage`
- 请求体：`{ tempSecurityCode, tempSecurityName, statusList, oprtSourceList, pageIndex, pageSize }`（`TempSecurityCodeReq extends PageRequest`）
- 后端 `TempSecurityCodeService.queryTempSecurityCodePage`：`PageHelper.startPage(pageIndex, pageSize)` → `tempSecurityCodeMapper.queryTempSecurityCodePage(req)` → `PageInfo` → `PageResult`。
- SQL 行为：`FROM rrs_temp_security_code t`，`LEFT JOIN dict_security_type dst_temp ON dst_temp.security_type = t.temp_security_type AND dst_temp.is_deleted = 0`（取 `tempSecurityTypeName`），`LEFT JOIN dict_security_type dst ON dst.security_type = t.security_type AND dst.is_deleted = 0`（取正式 `security_type_name`）；`<where>` 拼 `temp_security_code LIKE`、`temp_security_name LIKE`、可选 `status IN`、可选 `oprt_source IN`；排序按状态优先级 + `update_time`/`crte_time`/`id`。
- **注意**：列表 SQL **未过滤 `is_deleted = 0`**，而 `queryTempSecurityCodeById`/`queryTempSecurityCodeDetail` 均 `AND is_deleted = 0`。即软删除后（`is_deleted=1, status='deleted'`）的记录仍会出现在列表中（显示「已删除」tag），但按 id 查详情会返回 null。这是列表与详情的过滤口径不一致点。
- 分页：前端 `page-sizes=[10,20,50,100]`，默认 `pageSize=20`；`PageRequest` 后端 pageSize 上限 100。
- 表格列：序号、临时证券名称/代码/市场(el-tag)/类型(el-tag `tempSecurityTypeName`)、临时缓释凭证代码、临时关联主体(`tempCompanyNameSnapshot`)、临时发行/到期日期、状态(el-tag)、**操作来源(el-tag `oprtSource`)**、证券名称/代码/市场(el-tag)/类型(el-tag `securityTypeName`)、更新时间(`updateTime`)、操作（更新/取消发行/删除）。

### 2.3 下拉选项查询

- 接口：`POST /api/v1/tempSecurityCode/queryTempSecurityCodeOptions`
- 请求体：`{}`（`TempSecurityCodeReq`）
- 后端 `queryTempSecurityCodeOptions`：`OptionBundle` 装载 `queryCompanyOptionList(req)` + `querySecurityTypeList()`。
  - `queryCompanyOptionList`：`FROM ais_inv_ods.wind_cbondissuer`（**跨库**，不按 `used` 过滤；债券+主体粒度，**`GROUP BY s_info_compcode` 去重**），可选按 `companyKeyword` 模糊匹配名称/代码、`tempCompanyCode` 精确，`LIMIT 50`（不排序），返回 `companyCode/fullName/shortName`。
  - `querySecurityTypeList`：`FROM dict_security_type WHERE is_deleted = 0 AND category_type != 'company' ORDER BY category_type ASC, sort_order ASC, id ASC`，返回 `securityType/securityTypeName/categoryType/categoryTypeName`。

---

## 3. 业务逻辑

四条工作流：

### 3.1 新增 `addTempSecurityCode`（`@Transactional`）

`validateAddReq` → 查 `queryCompanyByCode(tempCompanyCode)` 校验主体存在（`wind_cbondissuer`）→ `resolveCompanyName`（fullName 优先，其次 shortName，否则抛「发行主体缺少名称」）→ 构造 `TempSecurityCodeBo`，`secId = "TEMP_" + tempSecurityCode`，`status = TempStatus.TEMPORARY.getCode()`（`temporary`）、`oprtSource = manual`、`isDeleted=0`、`crteTime`/`updtTime=now` → `addTempSecurityCode` INSERT（回填 id）→ 同步写入 `rrs_securityinfo`（`wind_code=tempSecurityCode`，`sec_id=secId`，名称、类型、发行主体名称+`issuer_code`、`firstissue_date`/`maturity_date` 将表单日期格式化为 `yyyyMMdd`，起息/止息/`date_exists` 未知不写，`security_status='L'` 上市中，`security_source='temporary'`；市场只保存在登记表 `temp_security_market`，占位主档不写 `market`）→ `queryTempSecurityCodeDetail` 返回详情。正式证券字段仍为 NULL。**必须写明确的 `security_status`**：临时代码占位写 `'L'`（上市中）；外部/特殊来源若无状态写 `'U'`（未知，`SecurityStatus.UNKNOWN`）。证券池调整列表条件是 `security_status != 'D'`，若留 `NULL`/空串，SQL 三值逻辑会把行滤掉。

### 3.2 更新为正式证券 `editTempSecurityCodeToUpdated`（`@Transactional`）

对齐老系统 `TempSecurityInfoTableAction#refreshTempSecurity`，**流程中 vs 已在池分叉处理**（逻辑不同）：

1. `validateUpdateReq`（仅 id + 正式证券代码必填）→ `queryOperableTempSecurityCode`（查询登记，必须 `temporary`）
2. 正式证券从主数据选券校验（`queryFormalSecurityByCode`，排除 temporary 占位）；正式码不能等于临时码
3. 临时字段只读（用库内原值）；正式名称、类型以主数据结果为准，市场经统一服务查询当前正式 Wind 代码的唯一映射；不覆盖正式主档的独立 `sec_id`
4. **统一转换入口**（`convertTempSecurityBusinessData`）按四阶段组织：①初始化替换及审计参数；②由 `TempSecurityCodeBusinessConverter.prepareOrdinaryPoolConversion` 和 `prepareCrmwConversion` 分别加载、校验普通池及 CRMW 标的整组转换计划，全部通过后更新转正登记 `status=updated`（影响行数必须为 1）；③由同组件的 `convertOrdinaryPoolBusinessData` 处理普通池当前在池状态和流程中申请，覆盖六种业务情况；④由同组件的 `convertCrmwBusinessData` 在每个凭证下整组处理标的全部市场代码，保留原凭证，与普通池一致先处理当前在池状态、再迁移流程中申请，并同步 CRMW 专属快照；匹配临时代码的 `crmw_*` 凭证字段仍沿用旧替换分支。各阶段共用外层事务，任一阶段失败时整次转正回滚。
5. **各分支规则**：生成普通池计划时复用 `SecurityMarketService.queryRelatedMarketCodes`，正式代码的非空 ISIN → 全部同券代码，去重、不递归、不按名称推断。临时占位不参与 Wind 同券查询。
   - **普通池在途调库日志**（`00/11`）：原位替换选中正式代码，其他市场生成「关联调整」日志及独立证券快照，沿用原审批状态、批次和已处理步骤，不独立发起流程、不提前落池。原快照业务填写信息保留，实际证券身份按各代码更新。正式代码组在信用债大库（`pool_type=credit_bond`，含各级库）存在独立在途申请时拒绝；其他池独立申请保持原样，不阻断或自动合并。原申请与转正后池状态冲突仍作为迁移数据异常拒绝。
   - **普通池已在池**（`ip_pool_status.audit_status=20`）：每个池
     1. 写「债券临时代码调出」`ip_adjust_log`（`adjust_mode=调出`，`audit_status=20`，系统操作人）
     2. 软删临时在池记录
     3. 若正式代码组**全部已在同池** → 不再入库；部分在池属于数据异常，整次拒绝，不自动补修
     4. 否则为全部正式代码写调入日志、池状态和快照；选中代码继承原调整类型，其他市场为「关联调整」，即时记录共用本次转换批次，调整人统一为系统（`0/系统`）。调整原因注明临时码、选中正式码和本条正式码，逐代码保存替换审计
   - **已在池且有在途调出**：当前状态先整组转正，原调出申请同时扩展为正式代码组，最终是否退出由原审批决定。多池分别整组处理，同次转正整体回滚或提交。
   - **互斥配套调出**：仅使用调入目标池自身配置的 `in_mutex`，不反向推断限制或互斥。正式组已在该互斥池时，逐市场补配套调出，选中代码为「互斥调整」，其他市场为「关联调整」。流程中调入的配套调出沿用原发起人、批次、状态和审批步骤，复制原申请业务快照；审批前不改变原互斥池持仓，审批通过后随原批次一起落池。已在池转换的配套调出与本次转正共用即时批次、系统操作人和事务。原批次已有调出时不重复追加；新增调出仍检查调出限制，失败整次回滚。例如 T 申请调入五级库、正式组已在一级库，五级库配置一级库为调入互斥池，则转正后原审批包含整组调入五级库与整组调出一级库。
   - **业务校验仅三类**：①信用债大库及各级库的独立在途；②实际新增入池时逐码检查市场；③实际新增入池及原在途申请涉及的来源/限制/互斥关系，在途按补齐配套调出后的原批次核对最终池集合。`in_restrict` 为目标池有方向的硬限制，`in_mutex` 为配套调出规则，不直接作为转正失败原因。正式组已完整在池、只清理T时不重查入池市场及关系。不以容量、池启停、业务锁定、报告、评级选择或冻结期阻断身份迁移。
   - **转换数据保障**：主数据有效、目标池存在且为普通池、本次目标池与相关关系池的整组状态一致、原申请批次及方向有效、必要写入行数符合预期；事务回滚保留。无关池异常不阻断、不自动修补。无当前在池或在途业务时仅转正身份。
   - **CRMW 池**（`ip_pool_status_crmw`）：保留原凭证，标的按正式代码的非空 ISIN 扩展全部市场。同凭证下逐池处理上述六种情况：流程中主项原位换码并补关联项，保留原发起人、批次、审批状态和步骤；当前在池临时标的退出，正式组未在池则全部进入，已完整在池则只移除临时标的。其他凭证已有同一正式标的不影响本凭证入池，不能仅按证券代码和池去重。同凭证相关池部分在池、重复在池或目标池正式组存在独立流程中申请时报错；新增入池检查市场与池关系。证券快照写 `ip_adjust_security_snapshot_crmw`，保留原业务填写信息。
   - **调整人**：人工与定时入口新生成的即时生效调出、调入及关联调入日志和池状态，统一使用 `adjuster_id=0 / adjuster_name=系统`；原流程中申请及同批扩展项保留原发起人，原已在池正式组与终态历史记录不改写。
   - **池/日志 CRMW 字段**指向临时码：仅字段替换（对齐老 `findInvestPoolStatusLC`）
   - **投资池名称展示**：新生成调整原因中的「调入池」「互斥配套调出池」，以及流程冲突、市场、整组状态、来源与池关系等业务提示，均使用投资池名称，不展示池 ID。优先读取已有池配置、申请或持仓中的名称，不额外拼接层级全名；关系冲突涉及尚未加载的池时仅补查该池名称，历史日志不改写。
6. 临时占位 `rrs_securityinfo` 置 `security_status='D'`
7. 返回详情

> 正式证券主数据已存在时**不再 upsert**；历史已终态的调库日志（如 `20`）不改码，保留审计轨迹。

### 3.3 取消发行 `editTempSecurityCodeToCancelled`（`@Transactional`）

`validateIdReq` → `queryOperableTempSecurityCode(id)`（必须 `temporary`）→ 将临时代码对应 `rrs_securityinfo` 的到期/止息日期改为前一日（`yyyyMMdd`）并置 `security_status='D'` → 更新 `updateTime`/`status=CANCELLED`(`cancelled`)/`oprtSource=manual`/`updtTime` → 返回详情。不写正式证券字段。

### 3.4 删除 `deleteTempSecurityCode`（`@Transactional`）

`validateIdReq` → `queryExistingTempSecurityCode(id)`（仅校验 `is_deleted=0` 存在，**不校验状态**）→ 检查 `ip_adjust_log`、`ip_pool_status`、`ip_pool_status_crmw` 是否仍引用该临时代码，若存在则拒绝删除 → `status=DELETED`(`deleted`)、`oprtSource=manual`、`isDeleted=1`、`updateTime`/`updtTime=now` → `deleteTempSecurityCode` UPDATE（软删）→ **返回 `null`**。

---

## 4. 接口清单

前缀 `/api/v1/tempSecurityCode`，统一 `@PostMapping`、`@RequestBody`、返回 `ApiResponse<T>`。

| 路径 | 请求体字段 | 返回类型 | 用途 |
|---|---|---|---|
| `queryTempSecurityCodePage` | `tempSecurityCode?, tempSecurityName?, statusList?, oprtSourceList?, pageIndex, pageSize` | `ApiResponse<PageResult<TempSecurityCodeDto>>` | 分页查询临时代码列表；前端默认传 `oprtSourceList=['manual']` |
| `queryTempSecurityCodeOptions` | `companyKeyword?`（远程搜索）/`tempCompanyCode?`（精确回显） | `ApiResponse<TempSecurityCodeDto.OptionBundle>` | 查下拉：发行主体（wind_cbondissuer 最多 50 条）+ 证券类型 |
| `addTempSecurityCode` | `tempSecurityName, tempSecurityCode, tempSecurityMarket, tempSecurityType, tempMitigationCode?, tempCompanyCode, tempIssueDate, tempMaturityDate` | `ApiResponse<TempSecurityCodeDto>` | 新增临时代码（status=temporary） |
| `editTempSecurityCodeToUpdated` | `id, tempSecurityName, tempSecurityCode, tempSecurityMarket, tempSecurityType, tempMitigationCode?, tempCompanyCode, tempIssueDate, tempMaturityDate, securityName, securityCode, securityMarket, securityType` | `ApiResponse<TempSecurityCodeDto>` | 更新为正式证券（status=updated，同步 upsert rrs_securityinfo） |
| `editTempSecurityCodeToCancelled` | `id` | `ApiResponse<TempSecurityCodeDto>` | 取消发行（status=cancelled） |
| `deleteTempSecurityCode` | `id` | `ApiResponse<TempSecurityCodeDto>`（实返 null） | 软删除（status=deleted, is_deleted=1） |

> `TempSecurityCodeReq extends PageRequest`，含 `id, tempSecurityName, tempSecurityCode, statusList, oprtSourceList, tempSecurityMarket, tempSecurityType, tempMitigationCode, tempCompanyCode, companyKeyword, tempIssueDate(yyyy-MM-dd), tempMaturityDate(yyyy-MM-dd), securityName, securityCode, securityMarket, securityType, operatorId`。`operatorId` **后端未使用**（无审计表），前端不传。`TempSecurityCodeDto` 含主表全字段（含 `oprtSource`）+ `tempSecurityTypeName`/`securityTypeName`（JOIN dict 得来）+ 静态内部类 `CompanyOption`/`SecurityTypeOption`/`OptionBundle`。

---

## 5. 关键数据库表

建表脚本 `sql/rrs_temp_security_code_schema.sql`，演示数据 `sql/rrs_temp_security_code_demo_data.sql`。本模块通过替换日志表记录临时代码转正式时影响的业务记录。

| 表 | 用途 | 关键字段/枚举 |
|---|---|---|
| `rrs_temp_security_code`（主表） | 临时代码 | `id, sec_id VARCHAR(100) DEFAULT NULL, temp_security_name, temp_security_code, temp_security_market, temp_security_type, temp_mitigation_code, temp_company_code, temp_company_name_snapshot, temp_issue_date DATE, temp_maturity_date DATE, security_name, security_code, security_market, security_type, update_time, status, is_deleted, oprt_source, memo, crte_time, updt_time`；`oprt_source`：manual=人工 / job=定时任务 / other=其他（仅记最近一次）；仅主键，无二级索引 |
| `rrs_temp_security_code_update_log`（日志表） | 替换日志 | 记录临时代码、正式代码、被替换表名、被替换记录 ID、替换状态、替换时间 |
| `dict_security_type`（只读+校验） | 证券类型字典 | `security_type, security_type_name, category_type(bond/stock/fund/company), category_type_name, sort_order, is_deleted`；`querySecurityTypeList` 过滤 `category_type != 'company'`，`querySecurityTypeCount` 校验类型存在 |
| `ais_inv_ods.wind_cbondissuer`（跨库只读） | 发行主体 | `s_info_compcode, s_info_compname, used`；`queryCompanyOptionList`/`queryCompanyByCode` 读取时不按 `used` 过滤，`LIMIT 50` |
| `rrs_securityinfo`（upsert 写入） | 证券基础信息库 | key=`wind_code`；`security_source` 区分来源：`official=原始正式证券 / temporary=临时代码占位证券 / temp_converted=临时代码转正式证券`；`addSecurityInfo` 按 `wind_code = securityCode` 写入：`full_name`/`short_name=securityName`、不写市场列 `market`（市场仅存登记表 `temp_security_market`）、`security_type`、`issuer=tempCompanyNameSnapshot`、`issuer_code=tempCompanyCode`、`firstissue_date`/`maturity_date` 将表单发行/到期日格式化为 `yyyyMMdd`（起息/止息/`date_exists` 未知不写）、**`security_status='L'`（上市中；上市状态枚举 `SecurityStatus`：`L`/`N`/`D`/`U`，勿留 NULL，否则调库列表 `!= 'D'` 查不到；来源无状态用 `U`）**、`create_time`/`ts=updateTime`；正式证券已存在时不覆盖 `security_source` |

---

### 证券 ID（供外部使用）

- 新增时额外写入 `sec_id = "TEMP_" + tempSecurityCode`；输入 `001.IB` 时，两表证券 ID 均为 `TEMP_001.IB`，临时代码本身仍为 `001.IB`。
- 该字段仅供外部使用，当前项目不展示、不作为接口入参或出参，也不参与查询筛选、业务判断或专门校验。原有代码处理及校验规则保持不变。
- 人工及定时转正、取消发行和软删除不修改该字段；正式证券保留原有 ID。Demo 在原始 INSERT 中补齐，不执行存量迁移或自动补数。

## 6. 状态流转

状态枚举（`rrs_temp_security_code.status`，注释「temporary=临时 / updated=已更新 / cancelled=已取消 / deleted=已删除」）：
- `TempStatus.TEMPORARY("temporary")` — 新增默认
- `TempStatus.UPDATED("updated")` — 更新为正式证券
- `TempStatus.CANCELLED("cancelled")` — 取消发行
- `TempStatus.DELETED("deleted")` — 删除

操作来源枚举（`oprt_source`，注释「manual=人工 / job=定时任务 / other=其他」）：
- `TempOprtSource.MANUAL("manual")` / `JOB("job")` / `OTHER("other")`

市场枚举 `MarketCode`：`SSE`=上海证券交易所 / `SZSE`=深圳证券交易所 / `NIB`=银行间市场 / `BSE`=北京证券交易所 / `COMPANY`=主体 / `OTC`=场外市场 / `HKEX`=香港交易所 / `QDII`=其他QDII市场 / `JWCW`=JWCW市场 / `UNKNOWN`=未知 / `OTHER`=其他（前端 `marketOptions` 与 `docs/dict.js` 同此 11 项）。

```
            addTempSecurityCode
   (无) ──────────────────────► [temporary]
                                   │
                  editTempSecurityCodeToUpdated
                 ┌─────────────────┴─────────────────┐
                 ▼                                     ▼
            [updated]                            editTempSecurityCodeToCancelled
                                                 ► [cancelled]
                 │                                     │
                 └──────────────┬──────────────────────┘
                                ▼
                  deleteTempSecurityCode (软删, is_deleted=1)
                            ► [deleted]
```

**关键守卫**：
- `queryOperableTempSecurityCode(id)`：先 `queryExistingTempSecurityCode`（`is_deleted=0` 存在），再判 `!TempStatus.TEMPORARY.getCode().equals(bo.getStatus())` → 抛「只有临时状态可以执行该操作，id=..., status=...」。**更新、取消发行均要求 `temporary` 状态**。
- `deleteTempSecurityCode` 走 `queryExistingTempSecurityCode`（仅校验存在且未删），**不校验当前状态**——即 `temporary`/`updated`/`cancelled` 均可删除。
- `updated`/`cancelled` 为「冻结态」：仅可删除，不可再更新或取消。
- 前端按钮可见性：`canOperate(row) = row.status === 'temporary'`（显示「更新」「取消发行」）；`canDelete(row) = row.status !== 'deleted'`（显示「删除」）。
- 前端状态 tag 配色 `statusTagType`：`temporary→info`、`updated→success`、`cancelled→warning`、`deleted→danger`；`statusOptions` 中文：临时/已更新/已取消/已删除。

---

## 7. 关键校验/业务规则

- **新增校验** `validateAddReq`：
  - `tempSecurityName`/`tempSecurityCode`/`tempSecurityMarket`/`tempSecurityType` 必填（`validateRequired` 空白串判定）
  - `tempCompanyCode` 非空（「发行主体不能为空」）
  - `tempIssueDate`/`tempMaturityDate` 必填，且 `tempMaturityDate.after(tempIssueDate)`（「到期日期必须晚于发行日期」）
  - `validateMarket(tempSecurityMarket)`：使用 `MarketCode.isValid` 校验上述 11 项市场编码，否则「临时证券市场不合法，market=...」
  - `validateSecurityType`：`querySecurityTypeCount > 0`，否则「证券类型不存在或已删除，securityType=...」
  - 唯一性：`queryTempSecurityCodeCount(tempSecurityCode, null) > 0` → 「临时证券代码已存在，tempSecurityCode=...」（查 `is_deleted=0`）
- **更新校验** `validateUpdateReq`：同新增全部临时字段校验 + `validateIdReq`（id 必填）+ 唯一性排除自身 `queryTempSecurityCodeCount(tempSecurityCode, id)` + 正式证券字段 `securityName`/`securityCode`/`securityMarket`/`securityType` 必填 + `validateMarket(securityMarket)` + `validateSecurityType(securityType)`。
- **取消发行/删除校验**：`validateIdReq`（id 必填）；取消发行额外要求 `temporary` 状态；删除仅要求存在且未删。
- **主体快照** `resolveCompanyName`：`fullName` 优先，其次 `shortName`，两者皆空抛「发行主体缺少名称，companyId=...」。
- **`rrs_securityinfo` 占位写入**：以 `securityCode` 为 `wind_code` 主键判定。市场只保存在登记表 `temp_security_market`，占位主档不写外部市场列 `market`，`sec_id` 与登记表一致，为 `TEMP_` + 临时代码；临时代码不写 ODS，也不参与 Wind ISIN 同券关联。`issuer` 取 `tempCompanyNameSnapshot`，`issuer_code` 取 `tempCompanyCode`。表单发行/到期以 `yyyyMMdd` 写入 `firstissue_date`/`maturity_date`；起息、止息、`date_exists` 未知不写。**`security_status` 固定 `'L'`**（上市中；枚举见 `SecurityStatus`；不写则为 NULL，证券池调整 `!= 'D'` 查不到；外部无状态数据应落 `'U'`）。`create_time`/`ts` 取新增时间。转正时正式主数据已存在则不再覆盖。
- **事务**：`addTempSecurityCode`/`editTempSecurityCodeToUpdated`/`editTempSecurityCodeToCancelled`/`deleteTempSecurityCode` 均 `@Transactional(rollbackFor = Exception.class)`。
- **前端校验** `addRules`/`updateRules`：与后端必填项对齐（`required:true`）；日期 `value-format="yyyy-MM-dd"`；`openUpdateDialog` 回显时正式证券字段缺省取临时字段（`securityName: row.securityName || row.tempSecurityName || ''` 等）。

**值得注意的不一致/风险点**：
1. **列表不过滤 `is_deleted`**：删除后记录（`is_deleted=1, status='deleted'`）仍出现在列表，但按 id 查详情返回 null。
2. **删除返回 null**：`deleteTempSecurityCode` 服务方法 `return null`，故 `ApiResponse.success(null)`，前端 `loadList()` 刷新即可。
3. **无审计表**：与投资池模块的 `_evt` 审计表不同，`operatorId` 字段定义但全程未用。
4. **跨库读主体**：`queryCompanyOptionList`/`queryCompanyByCode` 读 `ais_inv_ods.wind_cbondissuer`，依赖跨库访问权限与该库 schema 稳定；前端远程搜索关键字，最多 50 条。
5. **`rrs_securityinfo` 无幂等保护**：upsert 以 `wind_code=securityCode` 判重，若多个临时代码映射到同一 `securityCode`，后到的更新会覆盖已有记录。
6. **更新时主体变更才重查**：仅 `tempCompanyCode` 变化时重查主体并刷新快照，未变更时保留旧 `tempCompanyNameSnapshot`（即使主体名已被改名）。

---

## 8. 验收标准

- 新增临时代码初始 `temporary`，临时证券代码在未删除记录中唯一。
- 更新为正式证券要求 `temporary` 状态，成功后 `updated` 且 `rrs_securityinfo` 同步 upsert（按 `wind_code` 判重）。
- 取消发行要求 `temporary` 状态，成功后 `cancelled`，不修改证券字段。
- 删除为软删（`is_deleted=1, status=deleted`），任意非 `deleted` 状态均可删，返回 null。
- 必填校验、市场白名单、证券类型存在性、到期晚于发行日期校验生效。
- 列表展示软删后的「已删除」记录（`is_deleted` 未过滤），但 `canOperate`/`canDelete` 按状态隐藏对应操作按钮。

## 9. 关键源码索引

- 前端：`znty-rrs-ui/temp_security_code.html`、`css/temp_security_code.css`、`js/api.js`
- Controller：`controller/TempSecurityCodeController.java`
- Service：`service/TempSecurityCodeService.java`（`queryTempSecurityCodePage`、`queryTempSecurityCodeOptions`、`addTempSecurityCode`、`editTempSecurityCodeToUpdated`、`editTempSecurityCodeToCancelled`、`deleteTempSecurityCode`、`validateAddReq`/`validateUpdateReq`/`validateIdReq`/`validateRequired`/`validateMarket`/`validateSecurityType`、`queryOperableTempSecurityCode`/`queryExistingTempSecurityCode`、`upsertSecurityInfo`、`resolveCompanyName`、`queryTempSecurityCodeDetail`）
- Mapper：`mapper/TempSecurityCodeMapper.java` / `resources/mapper/TempSecurityCodeMapper.xml`
- 实体：`entity/tempsecuritycode/TempSecurityCodeDto.java`（含 `CompanyOption`/`SecurityTypeOption`/`OptionBundle`）、`entity/tempsecuritycode/TempSecurityCodeReq.java`；`entity/bo/TempSecurityCodeBo.java`
- 枚举：`common/enums/TempStatus.java`、`common/enums/TempOprtSource.java`、`common/enums/MarketCode.java`
- SQL：`sql/rrs_temp_security_code_schema.sql`、`sql/rrs_temp_security_code_demo_data.sql`；关联 `sql/rrs_external_import_schema.sql`（`rrs_securityinfo` upsert）、`sql/rrs_dict_schema.sql`（`dict_security_type`）

## 证券代码转换组件参数

独立 `SecurityCodeConversionService` 仍未接入人工转正流程，保持 `Propagation.MANDATORY` 事务边界；请求移除五个旧市场代码参数，新增必填 `tempSecurityMarket` 明确提供临时市场。快照替换只修改实际证券标识及原有业务字段，不再保存市场代码列。
