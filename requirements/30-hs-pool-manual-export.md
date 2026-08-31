# 30 恒生格式手动导出

## 页面位置

- 菜单：研究管理 → 债券研究 → 恒生格式手动导出。
- 位于“临时代码管理”之后。
- 前端页面：`pages/hs_pool_manual_export.html`。

## 导出条件

| 字段 | 必填 | 说明 |
|---|---|---|
| 投资池 | 否 | 使用投资池树多选，仅可选择叶子池；未选择时导出全部叶子池 |
| 开始时间 | 是 | 精确到秒，格式 `yyyy-MM-dd HH:mm:ss`；增量模式作为时间窗口下界 |
| 结束时间 | 否 | 精确到秒；未填写时使用点击导出时的服务器当前时间 |
| 导出模式 | 是 | `increment`=增量，`full`=全量 |

结束时间不得早于开始时间。全量模式为保持页面条件统一仍要求开始时间，但数据范围以当前在池状态为准，不按时间过滤。

## 导出逻辑

- 全量模式复用 `HsPoolFullExcelExportService`：导出当前已生效的非主体证券和 CRMW；普通证券排除已到期数据，CRMW 不校验到期日。
- 增量模式复用 `HsPoolIncrementExcelExportService` 的查询和工作簿格式，直接使用页面时间窗口 `(开始时间, 结束时间]`，不读取定时任务水位线、不修改业务调库日志。
- Sheet、表头、中文市场名称、市场拆行、同名 Sheet 合并等规则与两项恒生池定时任务一致。
- 页面下载直接返回 Base64 `.xlsx`，不写服务器导出目录、不生成备份、不上传 FTP。

## 接口

`POST /api/v1/hsPoolManualExport/exportHsPoolExcel`

请求示例：

```json
{
  "poolIds": [15, 16],
  "startTime": "2026-08-01 00:00:00",
  "endTime": "2026-08-31 23:59:59",
  "exportMode": "increment"
}
```

返回 `CommonFileDto`：文件名、MIME 类型、文件大小和 Base64 文件内容。
