# 存量证券批量调整（批量调入 / 批量调出）需求说明

> 前端页面：`stock_security_batch_adjust.html`（自包含两步式工作台）  
> 后端前缀：`/api/v1/stockSecurityBatchAdjust`  
> 角色定位：业务人员在**债券产品库**叶子池上，对存量证券做批量调入/调出；仅调入按指定来源池筛选。

与 [12-batch-security-pool-adjust.md](12-batch-security-pool-adjust.md) **同构**（校验/提交/附件/现有数量分项/整批防重复/直通复核），差异如下。


## 列表查询字段

- 查询条件新增主体编码 `issuerCode`、证券全称 `securityFullName`，分别对主档 `issuer_code`、`full_name` 做包含式模糊查询；留空不限制，与既有条件按 AND 组合
- 列表展示主体编码 `issuerCode`、证券全称 `fullName`，已有证券全称列直接保留；值为空时留白
- 原“发行人”“发行主体全称”查询条件及对应列表列统一显示为“主体全称”，继续使用 `issuer` 字段及原有匹配规则
- 新增条件支持回车查询，查询回到第一页，重置时清空；证券基础信息等非查询、非列表区域保持原样
- 批量页仅修改工作台内“可选证券”区域，初始投资池列表保持原样；重新进入调入/调出时清空新增条件

“是否特征”追加“是否标准信用债”（`bondYesFlags` 选项 `stdCredit`），勾选仅筛选证券主档 `std_credit_flag=1`，与其他特征按 AND 组合；不勾选时不限制该字段。对应列表追加 `stdCreditFlag` 列：1=是，其他值（含 NULL）按现有是否类标识显示否，接口保留原始数值，沿用是=`success`、否=`info` 的 Tag。仅使用上游维护的原始标志，不按其他特征或 `std_clause_flag` 推断；CRMW 按标的证券筛选及展示。

## 同券跨市场整组校验

同一非空 ISIN 的主券与关联券逐码校验后，同一调库组（`adjustGroupKey`）任一项失败即整组不可调整；保留失败代码和具体原因，不允许跳过失败券而只提交其他市场。其他独立调库组不受影响。

“放开规则”由主券传递给所有关联券，仅跳过既有主体债入库矩阵规则；在途流程、市场、品种、到期、容量及其他硬限制仍按每个实际代码校验。

单笔提交携带基本信息已查询的 `relatedMarketCodes: [{ windCode, marketCode }]`；后端直接使用传入集合核对各目标池的调入明细是否带齐主券及关联券，提交阶段不再查询同券关系。批量及 Excel 入口复用本次校验结果中的实际代码集合。逐券活动流程检查继续执行。

---

## 1. 与证券池批量调整的差异

| 项 | 证券池批量 | 存量证券批量 |
|----|------------|--------------|
| 目标池列表 | 启用叶子（排除 crmw 池类型） | **仅 `pool_code=bond_product_root` 子树叶子**（不含「债券产品库1」） |
| 可选证券筛选 | 代码 / 简称 / 市场 / 是否特征 | 另加**发行主体**；调入另加来源池；是否特征含 ABS/担保/永续/次级/私募/含权/标准信用债，多选 AND |
| 来源池 | 无 | 调入固定白名单 6 项，调出不设来源池查询条件 |

**校验 / 提交与证券池批量一致**（编排细节见 [12](12-batch-security-pool-adjust.md) §3.2～§3.5）：`checkAdjust` 逐券委托 `SecurityPoolAdjustService.checkBatchAdjust`（完整单笔候选 + 公共方法追加配置的 batchIn/batchOut，统一推荐批量）；`addAdjustLog` 分组后委托 `submitAdjustLog`（共享附件与 `BatchNoContext`），整批防重复、直通用 `isDirectAdjustFlow` + `recheckBeforeFinalApproval`；手工项 `adjustType=手动批量调整`。本类只编排产品库与调入来源池差异，不新建业务表。

---

## 2. 来源池与候选证券范围

- 调入：`querySecurityPage` 须传 `sourcePoolIds`（至少一个且 ∈ 白名单）；证券在任一所选来源有效在池（OR），包括 CRMW 库在内均查询 `ip_pool_status`。
- 调出：前端不展示也不传 `sourcePoolIds`，后端不应用来源池过滤；查询范围不是默认来源池的并集，而是不受来源池限制的全部候选证券。
- 方向：`in` = 不在目标产品池；`out` = 已在目标产品池。
- 仍排除 `security_type IN ('crmw','company')` 与 `security_status='D'`。  
- 发行主体：`issuer` / `issuer_code` 模糊匹配。

---

## 3. 接口清单

| 接口 | 说明 |
|------|------|
| `POST .../queryPoolPage` | 债券产品库根树叶子 + 权限 + 现有数量分项 |
| `POST .../querySourcePoolList` | 调入来源池白名单下拉 |
| `POST .../querySecurityPage` | 候选证券（调入按来源池过滤，调出不限制） |
| `POST .../checkAdjust` | 批量校验 |
| `POST .../addAdjustLog` / `addAdjustLogWithFiles` | 批量提交 |

---

## 4. 前端

- 菜单：债券研究 → 存量证券批量调整
- 「请选择投资池」默认收起。树请求 `includePoolTypes: ['bond_product']` 且 `includeVarietyCodes: ['bond']`，只展示债券产品库里投资品种包含债券的池及其上级。
- 可选证券区：调入展示来源池多选（必选），调出不展示来源池；市场筛选直接读取 `rrs_securityinfo.market`（与 Wind `EXCHMARKET` 一致，银行间使用 NIB），临时代码使用登记市场；均支持发行主体、代码、简称、市场、是否特征，默认 10 条/页；列表「剩余期限(年)」同 [12]（`dateExists` 库内为**天**，前端 ÷365 展示），并展示 ABS/担保/永续/次级/私募/含权/标准信用债七列「是/否」Tag
- 担保人/权益人与证券池批量调整保持一致：查询四类关系主体并默认选中第一条，`115202000` 展示为“债务主体”；ABS 显示自选权益人及担保人主体内评分，自选时评分优先取自选主体。
- 工作台内容区为固定底部操作栏预留可滚动安全区，候选表格分页不得被“返回/下一步”遮挡

---

## 5. 主要代码

- Controller：`StockSecurityBatchAdjustController`  
- Service：`StockSecurityBatchAdjustService`  
- Mapper：`StockSecurityBatchAdjustMapper` + XML  
- 实体：`entity.stocksecuritybatchadjust.*`  
- 测试：`StockSecurityBatchAdjustServiceTest`  


## 报告与手工文件统一交互

报告库引用按 `sourceReportSource + sourceReportId` 聚合为一份报告入口，不直接铺开报告下的 PDF。点击报告标题在工作台新页签打开 `pages/report_detail.html`，展示元信息及该报告全部有效附件，逐个下载；手工上传文件仍点击直接下载。此规则用于申请、批量、审核与详情的报告/其他材料栏。最近信评报告自动回填携带完整附件列表；修改节点移除报告会删除本日志、本材料栏的全部关联。

`sys_attachment.source_attachment_id` 保存复制来源的报告库附件 ID，附件查询返回 `sourceAttachmentId/sourceReportId/sourceReportSource/sourceReportTitle`；基金代码转正继承关联时保留该来源。新增 `POST /api/v1/reports/queryReportDetail { reportId, source: internal|external }`，非法来源、无效 ID、不存在或已删除报告明确失败。

新环境通过更新后的原始附件 schema 初始化；存量升级使用 `sql/tool_report_source_upgrade.sql`（不加入初始化/重置执行）。仅修复唯一可定位来源，未定位项明确输出供核对，运行时不猜测归属。前端完整说明见 `docs/report-materials.html`。

## 快速入库与本次材料豁免

适用证券池调整（包括详情页调库和审核页下一步模式）、证券池批量调整、存量证券批量调整及证券池 Excel 导入。后三个入口保留完整单笔候选，另共用 BatchAdjustFlowOptionHelper 追加配置的批量流程并统一推荐批量；未配置时保留单笔推荐。

- 信用债沿用 isSimpleInboundFlowMatched，资格在“下一步”计算，提交不重复查询矩阵、180 天入库经历或历史报告条件。资格中的历史报告要求仍保留，豁免的是本次提交材料。
- 产品库沿用独立 isProductInboundFlowMatched：目标为 bond_product_root 子树启用叶子；当前有效在信用债一至三级、转债核心/重点或 CRMW 库之一才命中。来源编码与存量入口共用，本次待申请调入来源池不算当前在库。
- 材料豁免按主项快速资格决定，与选择快速、一般、特殊或批量流程无关。免材料不代表免审批，提交保留所选流程 ID、Key、类型，仍由该流程决定人工审批或直接生效。
- 行级 CheckResultItem.materialExemptionType 返回 creditBondSimple / productSource，无资格时为空；前端随主项透传，批量和 Excel 映射保持该字段。FlowOption.materialExemptionType 只记录快速候选的资格匹配信息，不再按当前选中候选决定材料规则。
- 同组联动、互斥和关联市场项跟随主项豁免；关联码逐码判断快速资格，任一不符则整组不获得快速材料豁免。独立调出及无资格主项沿用原材料规则，混合批次逐组处理，整批事务不变。
- 提交复用已加载的池配置和当前在库集合检查产品来源；信用债资格不重复查库，不新增校验凭证或重复资格查询。材料判断不要求所选流程等于 simple_in，也不要求其为直通流程。
- 六个相关页面统一提示“本次免提交材料（信评报告及其他附件）”，切换流程不撤去有资格主项的提示；详情和审核页补齐批量调入/调出标签。保留现有材料入口和内容，不显示合同投资范围确认提示，不增加勾选。
- 补充的报告引用仍检查有效性，材料绑定及完成后的报告生成沿用原逻辑；准入硬限制和落池复核保留，不改表，不改变其他调库链路。
