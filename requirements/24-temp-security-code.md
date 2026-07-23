# 临时代码管理需求说明

> 前端页面：`temp_security_code.html`
> 后端前缀：`/api/v1/tempSecurityCode`
> 角色定位：为尚未取得正式 Wind 代码的信用债维护临时证券代码，待正式证券信息明确后「更新为正式证券」并同步写入 `rrs_securityinfo`，或「取消发行」终止流程。提供查询、新增、更新、取消发行和删除能力。

---

## 1. 页面概览

单页结构（`el: '#temp_security_code'`），顶栏（SVG 图标 +「临时代码管理」+ 系统名 + `currentDateText`）+ `list-body`（`search-panel` 筛选行 + `table-card` 表格+分页）。

**2 个弹窗**：
1. **新增临时代码** `addDialogVisible`（720px）：`form-tip`「新增后状态默认为临时，后续可在列表中执行更新、取消发行或删除。」+ 临时证券信息区（名称/代码/市场/类型/缓释凭证代码/发行主体/发行日期/到期日期）。
2. **更新正式证券** `updateDialogVisible`（720px）：`form-tip`「更新后状态变为已更新，临时证券与正式证券信息将一并保存。」+ 临时证券信息区（可编辑）+ 正式证券信息区（证券名称/代码/市场/类型）。

**初始化**（`created`）：并行调用 `loadOptions()`（`/queryTempSecurityCodeOptions`）与 `loadList()`（`/queryTempSecurityCodePage`）；`mounted()` 注册 `resize` 监听，`beforeDestroy()` 清理。`baseURL` 由 `js/api.js` 注入（`http://localhost:18090`）。

---

## 2. 筛选与查询逻辑

### 2.1 筛选项（`queryParam`）

| 字段 | 控件 | 匹配 |
|---|---|---|
| `tempSecurityCode` | el-input（`v-model.trim`，回车查询） | LIKE `%xxx%` |
| `tempSecurityName` | el-input（`v-model.trim`，回车查询） | LIKE `%xxx%` |

操作：`handleSearch`（重置 `pageIndex=1` 后 `loadList`）、`handleReset`（清空两项+回首页）、`openAddDialog`（新增）。

### 2.2 列表查询

- 接口：`POST /api/v1/tempSecurityCode/queryTempSecurityCodePage`
- 请求体：`{ tempSecurityCode, tempSecurityName, pageIndex, pageSize }`（`TempSecurityCodeReq extends PageRequest`）
- 后端 `TempSecurityCodeService.queryTempSecurityCodePage`：`PageHelper.startPage(pageIndex, pageSize)` → `tempSecurityCodeMapper.queryTempSecurityCodePage(req)` → `PageInfo` → `PageResult`。
- SQL 行为：`FROM rrs_temp_security_code t`，`LEFT JOIN dict_security_type dst_temp ON dst_temp.security_type = t.temp_security_type AND dst_temp.is_deleted = 0`（取 `tempSecurityTypeName`），`LEFT JOIN dict_security_type dst ON dst.security_type = t.security_type AND dst.is_deleted = 0`（取正式 `security_type_name`）；`<where>` 仅拼 `temp_security_code LIKE` 与 `temp_security_name LIKE`；`ORDER BY t.crte_time DESC, t.id DESC`。
- **注意**：列表 SQL **未过滤 `is_deleted = 0`**，而 `queryTempSecurityCodeById`/`queryTempSecurityCodeDetail` 均 `AND is_deleted = 0`。即软删除后（`is_deleted=1, status='deleted'`）的记录仍会出现在列表中（显示「已删除」tag），但按 id 查详情会返回 null。这是列表与详情的过滤口径不一致点。
- 分页：前端 `page-sizes=[10,20,50,100]`，默认 `pageSize=20`；`PageRequest` 后端 pageSize 上限 100。
- 表格列：序号、临时证券名称/代码/市场(el-tag)/类型(el-tag `tempSecurityTypeName`)、临时缓释凭证代码、临时关联主体(`tempCompanyNameSnapshot`)、临时发行/到期日期、证券名称/代码/市场(el-tag)/类型(el-tag `securityTypeName`)、更新时间(`updateTime`)、状态(el-tag)、操作（更新/取消发行/删除）。

### 2.3 下拉选项查询

- 接口：`POST /api/v1/tempSecurityCode/queryTempSecurityCodeOptions`
- 请求体：`{}`（`TempSecurityCodeReq`）
- 后端 `queryTempSecurityCodeOptions`：`OptionBundle` 装载 `queryCompanyOptionList(req)` + `querySecurityTypeList()`。
  - `queryCompanyOptionList`：`FROM ais_inv_ods.wind_cbondissuer`（**跨库**，`used=1`；债券+主体粒度，**`GROUP BY s_info_compcode` 去重**），可选按 `companyKeyword` 模糊匹配名称/代码、`tempCompanyCode` 精确，`LIMIT 50`（不排序），返回 `companyCode/fullName/shortName`。
  - `querySecurityTypeList`：`FROM dict_security_type WHERE is_deleted = 0 AND category_type != 'company' ORDER BY category_type ASC, sort_order ASC, id ASC`，返回 `securityType/securityTypeName/categoryType/categoryTypeName`。

---

## 3. 业务逻辑

四条工作流：

### 3.1 新增 `addTempSecurityCode`（`@Transactional`）

`validateAddReq` → 查 `queryCompanyByCode(tempCompanyCode)` 校验主体存在（`wind_cbondissuer`）→ `resolveCompanyName`（fullName 优先，其次 shortName，否则抛「发行主体缺少名称」）→ 构造 `TempSecurityCodeBo`，`status = TempStatus.TEMPORARY.getCode()`（`temporary`）、`operationType = TempOperationType.ADD.getCode()`（`add`）、`isDeleted=0`、`crteTime`/`updtTime=now` → `addTempSecurityCode` INSERT（回填 id）→ 同步写入 `rrs_securityinfo`（`wind_code=tempSecurityCode`，名称、市场、类型、发行主体、发行/到期日期取临时证券信息，`security_source='temporary'` 表示临时代码占位证券）→ `queryTempSecurityCodeDetail` 返回详情。正式证券字段仍为 NULL。

### 3.2 更新为正式证券 `editTempSecurityCodeToUpdated`（`@Transactional`）

对齐老系统 `TempSecurityInfoTableAction#refreshTempSecurity`，**流程中 vs 已在池分叉处理**（逻辑不同）：

1. `validateUpdateReq`（仅 id + 正式证券代码必填）→ `queryOperableTempSecurityCode`（必须 `temporary`）
2. 正式证券从主数据选券校验（`queryFormalSecurityByCode`，排除 temporary 占位）；正式码不能等于临时码
3. 临时字段只读（用库内原值）；正式字段以主数据结果为准
4. **业务分叉**（`convertTempSecurityBusinessData`）：
   - **在途调库日志**（`ip_adjust_log.audit_status in ('00','11')`，对齐老 `approvestatus=10`）：仅 UPDATE 证券/CRMW 代码字段 → 正式码；写 `rrs_temp_security_code_update_log`；**不**生成出/入库业务单
   - **已在池**（`ip_pool_status` / `ip_pool_status_crmw` 且 `audit_status=20`）：每个池
     1. 写「债券临时代码调出」`ip_adjust_log`（`adjust_mode=调出`，`audit_status=20`，系统操作人）
     2. 软删临时在池记录
     3. 若正式码**已在同池** → 不再入库
     4. 否则写正式调入日志（继承原原因/调整人）+ 新建正式在池状态
   - **池/日志 CRMW 字段**指向临时码：仅字段替换（对齐老 `findInvestPoolStatusLC`）
5. 临时占位 `rrs_securityinfo` 置 `security_status='D'`
6. 主表 `status=updated`，返回详情

> 正式证券主数据已存在时**不再 upsert**；历史已终态的调库日志（如 `20`）不改码，保留审计轨迹。

### 3.3 取消发行 `editTempSecurityCodeToCancelled`（`@Transactional`）

`validateIdReq` → `queryOperableTempSecurityCode(id)`（必须 `temporary`）→ 将临时代码对应 `rrs_securityinfo` 的到期/止息日期改为前一日并置 `security_status='D'` → 更新 `updateTime`/`status=CANCELLED`(`cancelled`)/`operationType=CANCELLE_ISSUE`(`cancel_issue`)/`updtTime` → 返回详情。不写正式证券字段。

### 3.4 删除 `deleteTempSecurityCode`（`@Transactional`）

`validateIdReq` → `queryExistingTempSecurityCode(id)`（仅校验 `is_deleted=0` 存在，**不校验状态**）→ 检查 `ip_adjust_log`、`ip_pool_status`、`ip_pool_status_crmw` 是否仍引用该临时代码，若存在则拒绝删除 → `status=DELETED`(`deleted`)、`operationType=DELETE`(`delete`)、`isDeleted=1`、`updateTime`/`updtTime=now` → `deleteTempSecurityCode` UPDATE（软删）→ **返回 `null`**。

---

## 4. 接口清单

前缀 `/api/v1/tempSecurityCode`，统一 `@PostMapping`、`@RequestBody`、返回 `ApiResponse<T>`。

| 路径 | 请求体字段 | 返回类型 | 用途 |
|---|---|---|---|
| `queryTempSecurityCodePage` | `tempSecurityCode?, tempSecurityName?, pageIndex, pageSize` | `ApiResponse<PageResult<TempSecurityCodeDto>>` | 分页查询临时代码列表 |
| `queryTempSecurityCodeOptions` | `companyKeyword?`（远程搜索）/`tempCompanyCode?`（精确回显） | `ApiResponse<TempSecurityCodeDto.OptionBundle>` | 查下拉：发行主体（wind_cbondissuer 最多 50 条）+ 证券类型 |
| `addTempSecurityCode` | `tempSecurityName, tempSecurityCode, tempSecurityMarket, tempSecurityType, tempMitigationCode?, tempCompanyCode, tempIssueDate, tempMaturityDate` | `ApiResponse<TempSecurityCodeDto>` | 新增临时代码（status=temporary） |
| `editTempSecurityCodeToUpdated` | `id, tempSecurityName, tempSecurityCode, tempSecurityMarket, tempSecurityType, tempMitigationCode?, tempCompanyCode, tempIssueDate, tempMaturityDate, securityName, securityCode, securityMarket, securityType` | `ApiResponse<TempSecurityCodeDto>` | 更新为正式证券（status=updated，同步 upsert rrs_securityinfo） |
| `editTempSecurityCodeToCancelled` | `id` | `ApiResponse<TempSecurityCodeDto>` | 取消发行（status=cancelled） |
| `deleteTempSecurityCode` | `id` | `ApiResponse<TempSecurityCodeDto>`（实返 null） | 软删除（status=deleted, is_deleted=1） |

> `TempSecurityCodeReq extends PageRequest`，含 `id, tempSecurityName, tempSecurityCode, tempSecurityMarket, tempSecurityType, tempMitigationCode, tempCompanyCode, companyKeyword, tempIssueDate(yyyy-MM-dd), tempMaturityDate(yyyy-MM-dd), securityName, securityCode, securityMarket, securityType, operatorId`。`operatorId` **后端未使用**（无审计表），前端不传。`TempSecurityCodeDto` 含主表全字段 + `tempSecurityTypeName`/`securityTypeName`（JOIN dict 得来）+ 静态内部类 `CompanyOption`/`SecurityTypeOption`/`OptionBundle`。

---

## 5. 关键数据库表

建表脚本 `sql/rrs_temp_security_code_schema.sql`，演示数据 `sql/rrs_temp_security_code_demo_data.sql`。本模块通过替换日志表记录临时代码转正式时影响的业务记录。

| 表 | 用途 | 关键字段/枚举 |
|---|---|---|
| `rrs_temp_security_code`（主表） | 临时代码 | `id, temp_security_name, temp_security_code, temp_security_market, temp_security_type, temp_mitigation_code, temp_company_code, temp_company_name_snapshot, temp_issue_date DATE, temp_maturity_date DATE, security_name, security_code, security_market, security_type, update_time, status, operation_type, is_deleted, crte_time, updt_time`；索引 `idx_..._temp_code`/`_security_code`/`_company`/`_status` |
| `rrs_temp_security_code_update_log`（日志表） | 替换日志 | 记录临时代码、正式代码、被替换表名、被替换记录 ID、替换状态、替换时间 |
| `dict_security_type`（只读+校验） | 证券类型字典 | `security_type, security_type_name, category_type(bond/stock/fund/company), category_type_name, sort_order, is_deleted`；`querySecurityTypeList` 过滤 `category_type != 'company'`，`querySecurityTypeCount` 校验类型存在 |
| `ais_inv_ods.wind_cbondissuer`（跨库只读） | 发行主体 | `s_info_compcode, s_info_compname, used`；`queryCompanyOptionList`/`queryCompanyByCode` 读取，`used=1`，`LIMIT 50` |
| `rrs_securityinfo`（upsert 写入） | 证券基础信息库 | key=`wind_code`；`security_source` 区分来源：`official=原始正式证券 / temporary=临时代码占位证券 / temp_converted=临时代码转正式证券`；`addSecurityInfo`/`editSecurityInfo` 按 `wind_code = securityCode` 写入：`full_name`/`short_name=securityName`、`wind_code_sh`(SSE)/`wind_code_sz`(SZSE)/`wind_code_nib`(CIBM)/`wind_code_bj`(BSE)/`wind_code_nbc`(OTC/QDII/OTHER/COMPANY) 按 market 用 `CASE WHEN` 填充、`security_type`、`issuer=tempCompanyNameSnapshot`、`firstissue_date`/`maturity_date` 由 `tempIssueDate`/`tempMaturityDate` 经 `DATE_FORMAT(...,'%Y-%m-%d')` 写入、`create_time`/`ts=updateTime`；正式证券已存在时不覆盖 `security_source` |

---

## 6. 状态流转

状态枚举（`rrs_temp_security_code.status`，注释「temporary=临时 / updated=已更新 / cancelled=已取消 / deleted=已删除」）：
- `TempStatus.TEMPORARY("temporary")` — 新增默认
- `TempStatus.UPDATED("updated")` — 更新为正式证券
- `TempStatus.CANCELLED("cancelled")` — 取消发行
- `TempStatus.DELETED("deleted")` — 删除

操作类型枚举（`operation_type`，注释「add=新增 / update=更新 / cancel_issue=取消发行 / delete=删除」）：
- `TempOperationType.ADD("add")` / `UPDATE("update")` / `CANCEL_ISSUE("cancel_issue")` / `DELETE("delete")`

市场枚举 `MarketCode`：`SSE`=上海证券交易所 / `SZSE`=深圳证券交易所 / `CIBM`=银行间市场 / `BSE`=北京证券交易所 / `COMPANY`=主体 / `OTC`=场外市场 / `QDII`=其他QDII市场 / `OTHER`=其他（前端 `marketOptions` 同此 8 项）。

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
  - `validateMarket(tempSecurityMarket)`：白名单 `{SSE, SZSE, CIBM, BSE, COMPANY, OTC, QDII, OTHER}`，否则「临时证券市场不合法，market=...」
  - `validateSecurityType`：`querySecurityTypeCount > 0`，否则「证券类型不存在或已删除，securityType=...」
  - 唯一性：`queryTempSecurityCodeCount(tempSecurityCode, null) > 0` → 「临时证券代码已存在，tempSecurityCode=...」（查 `is_deleted=0`）
- **更新校验** `validateUpdateReq`：同新增全部临时字段校验 + `validateIdReq`（id 必填）+ 唯一性排除自身 `queryTempSecurityCodeCount(tempSecurityCode, id)` + 正式证券字段 `securityName`/`securityCode`/`securityMarket`/`securityType` 必填 + `validateMarket(securityMarket)` + `validateSecurityType(securityType)`。
- **取消发行/删除校验**：`validateIdReq`（id 必填）；取消发行额外要求 `temporary` 状态；删除仅要求存在且未删。
- **主体快照** `resolveCompanyName`：`fullName` 优先，其次 `shortName`，两者皆空抛「发行主体缺少名称，companyId=...」。
- **`rrs_securityinfo` upsert**：以 `securityCode` 为 `wind_code` 主键判定。市场→wind_code 列映射：`SSE→wind_code_sh`、`SZSE→wind_code_sz`、`CIBM→wind_code_nib`、`BSE→wind_code_bj`、`OTC/QDII/OTHER/COMPANY→wind_code_nbc`（其余列置 NULL）。`issuer` 取 `tempCompanyNameSnapshot`。`firstissue_date`/`maturity_date` 用 `DATE_FORMAT(...,'%Y-%m-%d')`。upsert 在主表状态更新**之前**执行，同 `@Transactional`。
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
- 枚举：`common/enums/TempStatus.java`、`common/enums/TempOperationType.java`、`common/enums/MarketCode.java`
- SQL：`sql/rrs_temp_security_code_schema.sql`、`sql/rrs_temp_security_code_demo_data.sql`；关联 `sql/rrs_external_import_schema.sql`（`rrs_securityinfo` upsert）、`sql/rrs_dict_schema.sql`（`dict_security_type`）
