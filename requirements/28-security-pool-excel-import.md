# 证券池 Excel 导入需求说明

> 前端页面：`security_pool_excel_import.html`  
> 后端前缀：`/api/v1/securityPoolExcelImport`  
> 公共模板下载：`/api/v1/commonFile/downloadTemplate`  
> 角色定位：具备投资池 `excel_importable` 权限的用户，通过 Excel 批量发起**证券**或**主体**池调入/调出。

---

## 1. 页面概览

单页工作台：上方双卡 + 下方 Tab（导入明细 / 调库校验结果）。

1. **导入参数**
   - **导入类型**：证券 / 主体（上传后锁定）
   - **调整方向**：调入 / 调出
   - **导入选项**：首先清空目标池；**允许联动与互斥**（仅证券展示；主体始终按禁投池展开）
   - **调整原因**
   - **不选目标池**（目标池由 Excel 父池/子池名称解析）
2. **下载模板并上传**
   - 两个下载按钮：**下载证券模板** / **下载主体模板**
   - 拖拽上传 xls/xlsx（≤5MB），按当前导入类型解析
3. **导入明细** Tab：行级校验状态、筛选、分页；证券行可选择担保人/权益人、ABS 自选权益人并显示主体内评；操作：重置 / 校验
4. **调库校验结果** Tab：对齐证券池批量调整列（简称/代码/池/调整类型/方向/审批流程/说明/可调整）；操作：重新校验 / 提交
5. **提交** → 弹出「选择调库流程」对话框（与批量调整一致）

### 1.1 模板列

| 类型 | 模板编码 | 列 |
|------|----------|----|
| 证券 | `security_pool_import` | 父池名称、子池名称、证券名称、证券代码 |
| 主体 | `company_pool_import` | 父池名称、子池名称、主体名称、主体代码 |

不再包含：市场类型、证券品种、调整人、调整时间。

目标池由「父池名称 + 子池名称」解析为启用叶子池（主体根池允许父池为空）；每行可对应不同池。

证券模板不填写评级主体。上传后在「导入明细」中选择，控件和候选口径与「证券池批量调整—可选证券」一致：

- **担保人/权益人**：读取当前证券的四类关联主体，默认选中第一条，并显示名称、关系类型和最新内评。
- **自选权益人**：仅 ABS 展示，按主体代码或名称查询全市场有效主体；无需与当前证券存在关联。
- **主体内评分**：ABS 自选权益人优先；未选择自选权益人时使用关联主体。清除自选后恢复关联主体内评，不清除原关联选择。
- 各导入行独立保存担保人/权益人和 ABS 自选权益人。同一证券代码导入不同池时，上传默认值相同，但修改或清除某一行不影响其他行，翻页和刷新后仍保留各行选择。修改后本批所有行回到待校验，旧校验结果和流程选择清除，须重新校验。
- 默认第一条只在上传时初始化，后续分页和校验不覆盖手动选择或清除。ABS 必须有关联权益人或自选权益人；非 ABS 有关联主体时必须选择，无关联主体且单券规则不要求选择时允许留空。
- 旧六列模板仍按前四列读取，Excel 中的主体名称和代码不再参与选择；已提交批次不可修改主体选择。

### 1.2 证券模板示例（Demo 数据）

下载模板预填以下 5 条调入示例，每条 4 个字段均填写完整。证券简称和代码来自 `rrs_external_import_demo_data.sql`；目标池与 `rrs_pool_init_demo_data.sql` 及关联主体默认第一条的评级规则匹配。

| 父池名称 | 子池名称 | 证券名称 | 证券代码 |
|----------|----------|----------|----------|
| 信用债大库(new) | 一级库 | 24基建担保 | DBB001.IB |
| 信用债大库(new) | 一级库 | 24地产担保 | DBB002.IB |
| 信用债大库(new) | 一级库 | 25城投担保 | DBB003.IB |
| 信用债大库(new) | 一级库 | 24资ABS02 | 108008902.IB |
| 信用债大库(new) | 一级库 | 25资管ABS | ABN001.IB |

Demo 中前三条担保债及 `108008902.IB` 默认首选某金融公司（C10010），`ABN001.IB` 默认首选债务主体某资产管理公司（C10008），最新内评均为 1，对应一级库。业务可在页面更换选择；若评级发生变化，需结合目标池重新校验。导入仍受实际库中的池状态、权限和完整业务规则约束。

---

## 2. 临时表（通用导入，非本功能独占）

| 表 | 说明 |
|----|------|
| `sys_imp_tmp` | 导入临时主表（批次） |
| `sys_imp_tmp_detl` | 导入临时明细，`fld001`～`fld030` 通用槽 |

- 无 `rid`；有效性用 `is_deleted`
- `biz_type`：`security_pool_excel` / `company_pool_excel`
- 主表业务槽（由 `biz_type` 约定，DDL 注释不写业务语义）：
  - `fld001` 调整方向 in/out
  - `fld002` 调整原因
  - `fld003` 调整意见
  - `option_json`：`{ clearTarget, allowLinkMutex, importType }`
  - `result_json`：校验快照（含 checkItems）
- 明细槽：`fld001`代码 / `fld002`名称 / `fld003`父池 / `fld004`子池 / `fld009`解析池ID / `fld010`池类型 / `fld011`关联担保人或权益人代码 / `fld012`关联主体名称 / `fld013`ABS 自选权益人代码 / `fld014`自选权益人名称；主体导入不使用 `fld011`～`fld014`。使用已有通用槽，无表结构变更。

脚本：`sql/rrs_import_temp_schema.sql` / `rrs_import_temp_demo_data.sql`（已注册 ScriptTool）。

---

## 3. 接口

| 接口 | 说明 |
|------|------|
| `POST commonFile/downloadTemplate` | `{ templateCode: security_pool_import \| company_pool_import }` → Base64 xlsx |
| `POST securityPoolExcelImport/uploadExcel` | multipart：`request` JSON（含 `importType`）+ `file` + 可选 `originalFileNameListJson`（JSON 数组，单文件时长度 1；兼容公司环境中文文件名乱码） |
| `POST .../queryTask` | 批次信息 + `checkItems` |
| `POST .../queryItemPage` | 明细分页，证券行附带类型、ABS 标志、关联候选、已保存选择及最新内评 |
| `POST .../editRatingCompany` | `{ impId, itemId, relatedCompanyCode, selfSelectedRightsHolderCode, currentUserId, pageIndex, pageSize, keyword }`；按导入明细 ID 仅更新当前行，空字符串清除对应角色，并清空本批旧校验快照 |
| `POST .../checkImport` | 内联校验，回写 chk_* 与 `result_json` |
| `POST .../submitImport` | 内联提交（可带前端流程选择后的 `checkItems`） |
| `POST .../cancelImport` | 逻辑删除批次 |

权限：启用叶子池 + `excel_importable`（管理员 userId=1 放行）。

---

## 4. 校验与提交口径（分证券/主体两分支，复用既有服务）

Excel 层负责：模板/临时表、父子池解析、`excel_importable` 权限、页面评级主体选择及导入类型分支编排。
**调库可行性与落库逻辑不再自写简化版**，分别委托：

| 分支 | 校验 | 提交 |
|------|------|------|
| 证券 | `SecurityPoolAdjustService.checkAdjust`（与批量内部 `checkSingleAdjust` 同路径） | `SecurityPoolAdjustService.addAdjustLog` |
| 主体 | `ForbiddenPoolAdjustService.checkCompanyAdjust` | `ForbiddenPoolAdjustService.addCompanyAdjustLog` |

### 4.1 证券导入

- 每行 Excel → 一券 + 一目标池 + 方向，调用完整证券调库校验（含联动/互斥/关联码/流程候选）
- 上传按与批量调整相同的关系优先级及最新内评排序，批量查询默认首条主体。页面通过代码保存选择，名称由服务端回填；不按 Excel 名称解析，不自动把无效关联选择转换为自选角色。
- ABS 关联权益人写入 `rightsHolderCode`，全市场自选权益人写入 `selfSelectedRightsHolderCode`，两者分别保存且自选优先评级；非 ABS 写入 `guarantorCode`。每次校验/提交重新复核候选资格，复用完整单券调库规则计算可调入池。
- 修改、校验、提交、取消均锁定同一批次，避免交叉执行。主体修改后清空本批行级校验及 `result_json`，即使客户端回传旧 `checkItems` 也不能绕过重新校验。
- 提交从本批已通过校验的持久化明细读取各行选择，按 `sourceItemId` 分组。同一证券的不同导入行允许选择不同主体，不校验跨行选择一致性；本行的主项、联动、互斥和关联项共用该行选择及流程。先统一复核已有在途流程，再逐行落库，避免同批新建流程阻断后续行；每组调库日志保存本行所选主体的证券快照。
- 明细返回 `relatedCompanyCode/relatedCompanyName`、`relatedRatingCompanies`、`selfSelectedRightsHolderCode/selfSelectedRightsHolder`、`securityType/absFlag` 及有效 `ratingCompanyCode/ratingCompanyName/ratingCompanyInnerRating`。校验结果展示有效主体名称/代码；联动、互斥、关联项展示来源导入行选择。首先清空目标池自动生成的出库成员不来源于 Excel 行，仍沿用原有默认主体逻辑。
- **未勾选**允许联动与互斥：结果中仅保留手工项
- **勾选**后保留完整展开项（与批量一致）
- 可调整手工项额外注入目标池**批量**调入/调出流程为推荐（对齐批量 `injectBatchFlowOption`）
- 校验结果「调整类型」：手工主项 **Excel导入**（不对齐单笔「手工调整」；对齐批量渠道专属命名「手动批量调整」）；联动/互斥/关联同名；清空主项 **Excel清空**
- 某导入行手工主项校验失败时，该行的联动、互斥和关联项不单独提交，不影响其他校验通过行的提交。
- 提交：按来源导入行构建请求，通过 `addExcelImportAdjustLogList` 复用证券调库提交步骤，手工主项 `adjust_type=Excel导入`（联动/互斥/关联由 `resolveAdjustType` 落各自类型）

### 4.2 主体导入

- 每行 Excel → 一主体 + 一目标池 + 方向，调用禁投池主体完整校验
- 联动/互斥同样受「允许联动与互斥」选项过滤
- 无流程候选时注入标准入/出库（缺省回退批量配置）
- 提交：按主体分组调用 `addCompanyAdjustLog`（含审批通过后旗下债券同步等既有逻辑）

### 4.3 提交规则

- 仅提交 `canAdjust=true` 的校验结果项
- 手工项可在表格/弹窗中选择流程；联动/互斥随同组提交
- `clearTarget`（仅调入）：校验时生成「清空出库」项（差集：在池但不在本批 Excel 的成员 → 批量调出）；非本批编码在途跳过不纳入；本批编码在途由导入行校验失败。清空主项及同一来源的联动、互斥和关联项一同提交出库，先清空出库再导入。

---

## 5. 主要代码

- `SecurityPoolExcelImportController` / `Service`（校验/提交内联）
- `SecurityPoolExcelImportMapper`（批次临时表 + 明细临时表 + 目标池解析）
- `CommonFileController` / `Service`
- `ExcelImportHelper`（POI）
- 模板：`classpath:xlsx/security_pool_import.xlsx`、`company_pool_import.xlsx`
- 前端：`pages/security_pool_excel_import.html`、`css/security_pool_excel_import.css`
