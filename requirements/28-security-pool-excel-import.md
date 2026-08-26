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
3. **导入明细** Tab：行级校验状态、筛选、分页；操作：重置 / 校验
4. **调库校验结果** Tab：对齐证券池批量调整列（简称/代码/池/调整类型/方向/审批流程/说明/可调整）；操作：重新校验 / 提交
5. **提交** → 弹出「选择调库流程」对话框（与批量调整一致）

### 1.1 模板列

| 类型 | 模板编码 | 列 |
|------|----------|----|
| 证券 | `security_pool_import` | 父池名称、子池名称、证券名称、证券代码 |
| 主体 | `company_pool_import` | 父池名称、子池名称、主体名称、主体代码 |

不再包含：市场类型、证券品种、调整人、调整时间。

目标池由「父池名称 + 子池名称」解析为启用叶子池（主体根池允许父池为空）；每行可对应不同池。

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
- 明细槽：`fld001`代码 / `fld002`名称 / `fld003`父池 / `fld004`子池 / `fld009`解析池ID / `fld010`池类型

脚本：`sql/rrs_import_temp_schema.sql` / `rrs_import_temp_demo_data.sql`（已注册 ScriptTool）。

---

## 3. 接口

| 接口 | 说明 |
|------|------|
| `POST commonFile/downloadTemplate` | `{ templateCode: security_pool_import \| company_pool_import }` → Base64 xlsx |
| `POST securityPoolExcelImport/uploadExcel` | multipart：`request` JSON（含 `importType`）+ `file` + 可选 `originalFileNameListJson`（JSON 数组，单文件时长度 1；兼容公司环境中文文件名乱码） |
| `POST .../queryTask` | 批次信息 + `checkItems` |
| `POST .../queryItemPage` | 明细分页 |
| `POST .../checkImport` | 内联校验，回写 chk_* 与 `result_json` |
| `POST .../submitImport` | 内联提交（可带前端流程选择后的 `checkItems`） |
| `POST .../cancelImport` | 逻辑删除批次 |

权限：启用叶子池 + `excel_importable`（管理员 userId=1 放行）。

---

## 4. 校验与提交口径（分证券/主体两分支，复用既有服务）

Excel 层只做：模板/临时表、父子池解析、`excel_importable` 权限、导入类型分支编排。  
**调库可行性与落库逻辑不再自写简化版**，分别委托：

| 分支 | 校验 | 提交 |
|------|------|------|
| 证券 | `SecurityPoolAdjustService.checkAdjust`（与批量内部 `checkSingleAdjust` 同路径） | `SecurityPoolAdjustService.addAdjustLog` |
| 主体 | `ForbiddenPoolAdjustService.checkCompanyAdjust` | `ForbiddenPoolAdjustService.addCompanyAdjustLog` |

### 4.1 证券导入

- 每行 Excel → 一券 + 一目标池 + 方向，调用完整证券调库校验（含联动/互斥/关联码/流程候选）
- **未勾选**允许联动与互斥：结果中仅保留手工项
- **勾选**后保留完整展开项（与批量一致）
- 可调整手工项额外注入目标池**批量**调入/调出流程为推荐（对齐批量 `injectBatchFlowOption`）
- 校验结果「调整类型」：手工主项 **Excel导入**（不对齐单笔「手工调整」；对齐批量渠道专属命名「手动批量调整」）；联动/互斥/关联同名；清空主项 **Excel清空**
- 提交：按主券分组调用 `addAdjustLog`，手工主项 `adjust_type=Excel导入`（联动/互斥/关联由 `resolveAdjustType` 落各自类型）

### 4.2 主体导入

- 每行 Excel → 一主体 + 一目标池 + 方向，调用禁投池主体完整校验
- 联动/互斥同样受「允许联动与互斥」选项过滤
- 无流程候选时注入标准入/出库（缺省回退批量配置）
- 提交：按主体分组调用 `addCompanyAdjustLog`（含审批通过后旗下债券同步等既有逻辑）

### 4.3 提交规则

- 仅提交 `canAdjust=true` 的校验结果项
- 手工项可在表格/弹窗中选择流程；联动/互斥随同组提交
- `clearTarget`（仅调入）：校验时生成「清空出库」项（差集：在池但不在本批 Excel 的成员 → 批量调出）；非本批编码在途跳过不纳入；本批编码在途由导入行校验失败。提交时先清空出库再导入。

---

## 5. 主要代码

- `SecurityPoolExcelImportController` / `Service`（校验/提交内联）
- `SecurityPoolExcelImportMapper`（批次临时表 + 明细临时表 + 目标池解析）
- `CommonFileController` / `Service`
- `ExcelImportHelper`（POI）
- 模板：`classpath:xlsx/security_pool_import.xlsx`、`company_pool_import.xlsx`
- 前端：`pages/security_pool_excel_import.html`、`css/security_pool_excel_import.css`
