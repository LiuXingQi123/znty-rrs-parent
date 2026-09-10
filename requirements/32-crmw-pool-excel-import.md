# CRMW 池 Excel 导入需求说明

> 前端页面：`crmw_pool_excel_import.html`
> 后端前缀：`/api/v1/crmwPoolExcelImport`
> 公共模板下载：`/api/v1/commonFile/downloadTemplate`
> 角色定位：具备 CRMW 投资池 `excel_importable` 权限的用户，通过 Excel 批量发起 **CRMW 凭证 + 标的证券** 组合的调入/调出。

---

## 1. 页面概览

单页工作台：上方双卡 + 下方 Tab（导入明细 / 调库校验结果）。布局与 [28 证券池 Excel 导入](28-security-pool-excel-import.md) 一致，字段按 CRMW 组合替换。

1. **导入参数**
   - **目标池**：启用叶子 CRMW 池（`pool_type=crmw`）；仅一个池时自动选中
   - **调整方向**：调入 / 调出
   - **调整原因**、**调整意见**（选填）
   - 无导入类型、无「首先清空目标池」、无「允许联动与互斥」
2. **下载模板并上传**
   - 一个下载按钮：**下载CRMW模板**
   - 拖拽上传 xls/xlsx（≤5MB）
3. **导入明细** Tab：行级校验状态、筛选、分页；操作：清空 / 校验
4. **调库校验结果** Tab：操作：重新校验 / 提交
5. **提交** → 弹出「选择调库流程」对话框（与证券池 Excel 导入 / CRMW 批量一致）

### 1.1 模板列

| 类型 | 模板编码 | 列 |
|------|----------|----|
| CRMW | `crmw_pool_import` | CRMW代码、证券代码 |

目标池不在 Excel 中，由页面选择。

### 1.2 导入明细列

CRMW代码、CRMW全称、CRMW市场、证券代码、证券全称、证券类型、证券市场、校验、校验说明。

### 1.3 调库校验结果列

CRMW代码、CRMW全称、CRMW市场、证券代码、证券全称、证券类型、证券市场、调整类型、调整方向、审批流程、调整说明、校验结果。

---

## 2. 临时表（通用导入）

复用 `sys_imp_tmp` / `sys_imp_tmp_detl`。

- `biz_type`：`crmw_pool_excel`
- 主表槽：`fld001` 调整方向 in/out；`fld002` 调整原因；`fld003` 调整意见；`fld004` 目标池 ID；`fld005` 目标池名称；`fld006` 池类型
- `option_json`：`{ targetPoolId, targetPoolName, poolType }`
- 明细槽：`fld001` CRMW代码 / `fld002` CRMW全称 / `fld003` CRMW市场 / `fld004` 证券代码 / `fld005` 证券全称 / `fld006` 证券类型 / `fld007` 证券市场 / `fld008` crmwStype / `fld009` 目标池 ID / `fld010` 池类型 / `fld011` 证券类型名称

---

## 3. 接口

| 接口 | 说明 |
|------|------|
| `POST crmwPoolExcelImport/queryPoolList` | 当前用户可 Excel 导入的启用叶子 CRMW 池 |
| `POST commonFile/downloadTemplate` | `{ templateCode: crmw_pool_import }` → Base64 xlsx |
| `POST crmwPoolExcelImport/uploadExcel` | multipart：`request` JSON + `file` + 可选 `originalFileNameListJson` |
| `POST .../queryTask` | 批次信息 + `checkItems` |
| `POST .../queryItemPage` | 明细分页（关键字匹配 CRMW 代码或证券代码） |
| `POST .../checkImport` | 逐组合委托 `checkCrmwAdjust` |
| `POST .../submitImport` | 按组合分组委托 `submitAdjustLog` |
| `POST .../cancelImport` | 逻辑删除批次 |

权限：启用叶子 CRMW 池 + `excel_importable`（管理员 userId=1 放行）。

---

## 4. 校验与提交口径

Excel 层只做：模板/临时表、目标池解析、`excel_importable` 权限、主数据回填。
**调库可行性与落库不再自写简化版**，委托：

| 动作 | 服务 |
|------|------|
| 校验 | `CrmwPoolAdjustService.checkCrmwAdjust`（与批量内部逐组合同路径） |
| 提交 | `CrmwPoolAdjustService.submitAdjustLog`（整批共享附件上下文与 `BatchNoContext`） |

- 每行 Excel → 一凭证 + 一标的 + 一目标池 + 方向
- 保留完整展开项（手工 / 联动 / 互斥 / 关联），**不注入** batchIn/batchOut
- 校验结果「调整类型」：手工主项 **Excel导入**；联动/互斥/关联同名
- 提交：按 `crmwScode|securityCode` 分组；手工主项 `adjust_type=Excel导入`
- 仅提交 `canAdjust=true` 的校验结果项
- 存在直通项时先 `recheckBeforeFinalApproval`
- 无「首先清空目标池」

---

## 5. 主要代码

- `CrmwPoolExcelImportController` / `Service` / `Mapper`
- `CommonFileController` / `Service`（模板编码 `crmw_pool_import`）
- `ExcelImportHelper`（POI）
- 模板：`classpath:xlsx/crmw_pool_import.xlsx`
- 前端：`pages/crmw_pool_excel_import.html`、`css/crmw_pool_excel_import.css`
