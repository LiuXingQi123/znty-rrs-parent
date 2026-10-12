# 临时代码管理需求说明

## 证券身份与市场代码（当前接口契约）

- `rrs_securityinfo.isin_code` 为非空主键；正式券使用真实 ISIN，临时券使用 `TEMP:临时代码`。不再提供 `wind_code` 总码，也不增加 `security_ref_type`。
- 调库、池状态、收藏、报告、提醒及批量缓存按 `isinCode` 识别整券；同券同池只占一份容量，同券不同市场码不能绕过已在池和在途检查。主体、独立基金、股票继续使用原业务代码。
- 证券代码搜索传 `securityKeyword`，后端在括号内对 `wind_code_sh`、`wind_code_sz`、`wind_code_nib`、`wind_code_bj`、`wind_code_nbc` 做 OR 模糊筛选。任一市场符合目标池配置即可整券入池，全部有值代码同步生效、同步出池；仅审批通过 `20` 落池。
- 页面“证券代码”保留一列，通过各页面 `new Vue` 内的方法与 Element UI 插槽按沪、深、银行间、北、其他顺序换行展示全部有值代码，仅显示代码本身、不加市场名前缀；空值不占位，长码可折行；可点击代码与证券简称使用相同链接样式，普通代码继承表格文字样式。点击任一码打开同一 ISIN；临时券明确标示临时来源。无公共证券代码脚本或自定义组件。
- CRMW 凭证只使用银行间编码。`crmwIsinCode` 标识凭证、`isinCode` 标识标的，两者与目标池组成业务组合；`crmwScode` / `crmw_scode` 只作银行间代码快照，不作身份。
- POST 路径及响应包装不变。旧代码身份请求明确失败；旧 URL、导入校验草稿须重新选择或重新上传。不能使用状态行 `id` 代替证券身份；日志 ID、附件 ID 仍各有其用途。
- 通用证券 Excel 导出分别提供 ISIN 与五码列；历史及打印读取业务快照。恒生按实际市场码展开，增量删除沿用原入池事件代码，不能输出 ISIN 或临时代码作为正式银行间代码。


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

`validateAddReq` → 查 `queryCompanyByCode(tempCompanyCode)` 校验主体存在（`wind_cbondissuer`）→ `resolveCompanyName`（fullName 优先，其次 shortName，否则抛「发行主体缺少名称」）→ 构造 `TempSecurityCodeBo`，`status = TempStatus.TEMPORARY.getCode()`（`temporary`）、`oprtSource = manual`、`isDeleted=0`、`crteTime`/`updtTime=now` → `addTempSecurityCode` INSERT（回填 id）→ 同步写入 `rrs_securityinfo`（`isin_code=TEMP:临时代码`，名称、市场、类型、发行主体名称+`issuer_code`、`firstissue_date`/`maturity_date` 将表单日期格式化为 `yyyyMMdd`，起息/止息/`date_exists` 未知不写，`security_status='L'` 上市中，`security_source='temporary'`）→ `queryTempSecurityCodeDetail` 返回详情。正式证券字段仍为 NULL。**必须写明确的 `security_status`**：临时代码占位写 `'L'`（上市中）；外部/特殊来源若无状态写 `'U'`（未知，`SecurityStatus.UNKNOWN`）。证券池调整列表条件是 `security_status != 'D'`，若留 `NULL`/空串，SQL 三值逻辑会把行滤掉。

### 3.2 更新为正式证券（事务）

请求只需登记 `id` 与正式目标 `isinCode`。服务端读取已存在的正式主档并核验类型，不接收前端名称、市场或代码覆盖正式身份。临时登记先更新状态及目标 ISIN，再由统一替换服务在同一事务内迁移在途日志和快照；已在池执行临时出池、正式入池，正式对象已在同池时只出临时对象。CRMW 分别判断凭证和标的是否需要替换，保留另一方身份。成功后禁用 `TEMP:临时代码` 主档；终态历史保留原身份与当时代码。

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
| `editTempSecurityCodeToUpdated` | `id, isinCode` | `ApiResponse<TempSecurityCodeDto>` | 选择已有正式主档并在事务内转正；不覆盖正式主档 |
| `editTempSecurityCodeToCancelled` | `id` | `ApiResponse<TempSecurityCodeDto>` | 取消发行（status=cancelled） |
| `deleteTempSecurityCode` | `id` | `ApiResponse<TempSecurityCodeDto>`（实返 null） | 软删除（status=deleted, is_deleted=1） |

> `TempSecurityCodeReq extends PageRequest`，含 `id, tempSecurityName, tempSecurityCode, statusList, oprtSourceList, tempSecurityMarket, tempSecurityType, tempMitigationCode, tempCompanyCode, companyKeyword, tempIssueDate(yyyy-MM-dd), tempMaturityDate(yyyy-MM-dd), isinCode, securityName, securityCode, securityMarket, securityType, operatorId`。`operatorId` **后端未使用**（无审计表），前端不传。`TempSecurityCodeDto` 含主表全字段（含 `oprtSource`）+ `tempSecurityTypeName`/`securityTypeName`（JOIN dict 得来）+ 静态内部类 `CompanyOption`/`SecurityTypeOption`/`OptionBundle`。

---

## 5. 主档与引用

临时登记保留原始临时代码，长度不超过 95；主档主键为 `TEMP:` 加原始代码，所选市场对应字段保存原始代码，其余为空。CRMW 临时凭证只允许 CIBM。正式目标必须已在主档存在，并以 `isin_code` 关联；不创建或覆盖正式主档。取消发行、删除守卫、O32 人工处理及定时替换均使用该身份。

## 6. 状态流转

状态枚举（`rrs_temp_security_code.status`，注释「temporary=临时 / updated=已更新 / cancelled=已取消 / deleted=已删除」）：
- `TempStatus.TEMPORARY("temporary")` — 新增默认
- `TempStatus.UPDATED("updated")` — 更新为正式证券
- `TempStatus.CANCELLED("cancelled")` — 取消发行
- `TempStatus.DELETED("deleted")` — 删除

操作来源枚举（`oprt_source`，注释「manual=人工 / job=定时任务 / other=其他」）：
- `TempOprtSource.MANUAL("manual")` / `JOB("job")` / `OTHER("other")`

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
  - 临时代码最长 95 位，预留 `TEMP:` 前缀；CRMW 只能选择银行间 `CIBM`。
  - `tempSecurityName`/`tempSecurityCode`/`tempSecurityMarket`/`tempSecurityType` 必填（`validateRequired` 空白串判定）
  - `tempCompanyCode` 非空（「发行主体不能为空」）
  - `tempIssueDate`/`tempMaturityDate` 必填，且 `tempMaturityDate.after(tempIssueDate)`（「到期日期必须晚于发行日期」）
  - `validateMarket(tempSecurityMarket)`：白名单 `{SSE, SZSE, CIBM, BSE, COMPANY, OTC, QDII, OTHER}`，否则「临时证券市场不合法，market=...」
  - `validateSecurityType`：`querySecurityTypeCount > 0`，否则「证券类型不存在或已删除，securityType=...」
  - 唯一性：`queryTempSecurityCodeCount(tempSecurityCode, null) > 0` → 「临时证券代码已存在，tempSecurityCode=...」（查 `is_deleted=0`）
- **更新校验** `validateUpdateReq`：仅校验登记 `id` 与正式目标 `isinCode` 必填；原临时字段使用库内值，正式字段从主档读取。
- **取消发行/删除校验**：`validateIdReq`（id 必填）；取消发行额外要求 `temporary` 状态；删除仅要求存在且未删。
- **主体快照** `resolveCompanyName`：`fullName` 优先，其次 `shortName`，两者皆空抛「发行主体缺少名称，companyId=...」。
- **`rrs_securityinfo` 占位写入**：以 `isin_code=TEMP:临时代码` 为主键，所选市场列保存原始临时代码。市场→五码列映射：`SSE→wind_code_sh`、`SZSE→wind_code_sz`、`CIBM→wind_code_nib`、`BSE→wind_code_bj`、`OTC/QDII/OTHER/COMPANY→wind_code_nbc`（其余列置 NULL）。`issuer` 取 `tempCompanyNameSnapshot`，`issuer_code` 取 `tempCompanyCode`。表单发行/到期以 `yyyyMMdd` 写入 `firstissue_date`/`maturity_date`；起息、止息、`date_exists` 未知不写。**`security_status` 固定 `'L'`**（上市中；枚举见 `SecurityStatus`；不写则为 NULL，证券池调整 `!= 'D'` 查不到；外部无状态数据应落 `'U'`）。`create_time`/`ts` 取新增时间。转正时正式主数据已存在则不再覆盖。
- **事务**：`addTempSecurityCode`/`editTempSecurityCodeToUpdated`/`editTempSecurityCodeToCancelled`/`deleteTempSecurityCode` 均 `@Transactional(rollbackFor = Exception.class)`。
- **前端校验** `addRules`/`updateRules`：与后端必填项对齐（`required:true`）；日期 `value-format="yyyy-MM-dd"`；`openUpdateDialog` 只读回显临时字段，正式证券必须从远程主档选取，不以临时值推断正式身份。

**值得注意的不一致/风险点**：
1. **列表不过滤 `is_deleted`**：删除后记录（`is_deleted=1, status='deleted'`）仍出现在列表，但按 id 查详情返回 null。
2. **删除返回 null**：`deleteTempSecurityCode` 服务方法 `return null`，故 `ApiResponse.success(null)`，前端 `loadList()` 刷新即可。
3. **无审计表**：与投资池模块的 `_evt` 审计表不同，`operatorId` 字段定义但全程未用。
4. **跨库读主体**：`queryCompanyOptionList`/`queryCompanyByCode` 读 `ais_inv_ods.wind_cbondissuer`，依赖跨库访问权限与该库 schema 稳定；前端远程搜索关键字，最多 50 条。
5. **正式主档只读**：多个临时券可选定同一正式 ISIN；同池已有正式对象时只退出临时对象，不重复入池、不覆盖正式主档。
6. **转正身份复核**：服务端锁定临时与正式主档、检查在途冲突，凭证和标的分别处理；终态历史保留旧身份与代码。

---

## 8. 验收标准

- 新增临时代码初始 `temporary`，临时证券代码在未删除记录中唯一。
- 更新为正式证券要求 `temporary` 状态，成功后 `updated`，保存目标 ISIN、切换当前业务引用并禁用旧临时主档，正式主档不被覆盖。
- 取消发行要求 `temporary` 状态，成功后 `cancelled`，不修改证券字段。
- 删除为软删（`is_deleted=1, status=deleted`），任意非 `deleted` 状态均可删，返回 null。
- 必填校验、市场白名单、证券类型存在性、到期晚于发行日期校验生效。
- 列表展示软删后的「已删除」记录（`is_deleted` 未过滤），但 `canOperate`/`canDelete` 按状态隐藏对应操作按钮。

## 9. 关键源码索引

- 前端：`znty-rrs-ui/temp_security_code.html`、`css/temp_security_code.css`、`js/api.js`
- Controller：`controller/TempSecurityCodeController.java`
- Service：`service/TempSecurityCodeService.java`（`queryTempSecurityCodePage`、`queryTempSecurityCodeOptions`、`addTempSecurityCode`、`editTempSecurityCodeToUpdated`、`editTempSecurityCodeToCancelled`、`deleteTempSecurityCode`、`validateAddReq`/`validateUpdateReq`/`validateIdReq`/`validateRequired`/`validateMarket`/`validateSecurityType`、`queryOperableTempSecurityCode`/`queryExistingTempSecurityCode`、`replaceBusinessReferences`、`resolveCompanyName`、`queryTempSecurityCodeDetail`）
- Mapper：`mapper/TempSecurityCodeMapper.java` / `resources/mapper/TempSecurityCodeMapper.xml`
- 实体：`entity/tempsecuritycode/TempSecurityCodeDto.java`（含 `CompanyOption`/`SecurityTypeOption`/`OptionBundle`）、`entity/tempsecuritycode/TempSecurityCodeReq.java`；`entity/bo/TempSecurityCodeBo.java`
- 枚举：`common/enums/TempStatus.java`、`common/enums/TempOprtSource.java`、`common/enums/MarketCode.java`
- SQL：`sql/rrs_temp_security_code_schema.sql`、`sql/rrs_temp_security_code_demo_data.sql`；关联 `sql/rrs_external_import_schema.sql`（临时主档新增、转正后禁用）、`sql/rrs_dict_schema.sql`（`dict_security_type`）
