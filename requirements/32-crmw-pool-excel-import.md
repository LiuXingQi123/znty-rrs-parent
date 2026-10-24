# CRMW 池 Excel 导入需求说明

> 前端页面：`crmw_pool_excel_import.html`
> 后端前缀：`/api/v1/crmwPoolExcelImport`
> 公共模板下载：`/api/v1/commonFile/downloadTemplate`
> 角色定位：具备 CRMW 投资池 `excel_importable` 权限的用户，通过 Excel 批量发起 **CRMW 凭证 + 标的证券** 组合的调入/调出。

### 同券关联市场范围

同券关联市场与投资池投资市场共用 `MarketCode` 集合（`SSE/SZSE/NIB/BSE/COMPANY/OTC/HKEX/QDII/JWCW/UNKNOWN/OTHER`），不另设白名单。先按 Wind `EXCHMARKET` 筛选主代码和关联代码，再查询本地主档；范围外代码不参与关联展示、关联校验和关联出入库，缺少主档不阻断，范围内缺少主档或市场仍报错。不按代码后缀或 `S_INFO_EXCHMARKET` 推断；目标池勾选的市场仍是逐券入池限制，不用于裁剪同券集合。证券池、ABS、CRMW、批量、Excel 及临时转正复用同一查询。

---

## 标的债券一券多码调库

- CRMW 凭证本身为单一代码，不存在多市场扩批；仅将其绑定标的债券按 Wind 非空 `S_INFO_ISINCODE` 展开同券代码，排除自身、去重、不递归，空 ISIN 和临时代码不关联。
- `checkCrmwAdjust` 在手工、联动、互斥项生成后追加 `itemTag=related` 的关联调整项。每个实际代码独立校验市场、凭证+标的组合在池状态、活动池组、冻结期和原调库规则；同池调入累计主项及已通过关联项的容量。主券或任一关联码校验失败，按 adjustGroupKey 反向阻断整个同券跨市场调库组，保留失败代码和具体原因；不得只提交该组中通过的部分代码，其他独立调库组不受影响。
- 单笔提交携带基本信息已查询的 `relatedMarketCodes: [{ windCode, marketCode }]`；后端直接使用传入集合核对各目标池的调入明细是否带齐主券及关联券，提交阶段不再查询同券关系。批量及 Excel 入口复用本次校验结果中的实际代码集合。逐券活动流程检查继续执行。
- 关联项保持同一个 `crmwScode/crmwStype`，`sourceSecurityCode` 为触发主组合键 `crmwScode|securityCode`，共用原主项的 `adjustGroupKey`、流程与 CRMW 批次号，仅手工项创建流程步骤；关联项的报告要求由同组手工项承担。
- 提交 `items[]` 新增 `securityCode/securityShortName/securityType`，记录、快照与最终落池使用实际标的身份。未传明细代码的旧单组合请求继续使用请求级主券代码。直通即时生效，人工审批仅最终 `20` 同批落池；出池精确匹配原凭证、实际标的和目标池，保留其他凭证的组合。
- 批量及 Excel 导入透传实际代码；同批显式选择多个同券代码时按凭证+标的+目标池+方向去重，手工主项优先。Excel 关联项的全称、市场、类型来自自身主档。
- 活动流程通过同批任一组合定位，关联码审批中不能在相同池组重复申请。`queryCrmwAdjustLogList` 返回当前组合全部历史及同批关联项，不按请求批次或终态过滤；返回项增加 `securityCode/securityShortName`，申请校验表和审核/详情记录表展示实际标的代码与名称。
- 自动到期调出继续逐条扫描实际在池组合，不再二次按 ISIN 扩批；本次不调整定时任务规则，不迁移历史数据，不变更表结构。

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

## 当前市场口径

市场按当前代码在 `rrs_securityinfo.market` 的本地市场值校验（外部导入与 Wind `EXCHMARKET` 保持一致），银行间使用 NIB；临时代码使用登记表市场。关联券其他市场不使当前代码通过市场限制。批量候选直接按主档市场筛选，临时代码用登记表 EXISTS 筛选，展示按当前页批量查询唯一市场，避免关联连接重复分页或逐行市场查询。CRMW 导入复用证券主档缓存，同一代码的市场只查询一次。缺失、重复或不支持的映射报出具体代码。
