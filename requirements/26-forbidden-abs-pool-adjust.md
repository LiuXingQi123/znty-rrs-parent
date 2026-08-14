# 禁投池调整 · ABS债 Tab（申请）需求说明

> 前端页面：`forbidden_pool_adjust.html`（列表 Tab「ABS债」+ `absDetail` 视图）  
> 后端前缀：`/api/v1/forbiddenAbsPoolAdjust`  
> 角色定位：对 **ABS 债**（`rrs_securityinfo.abs_flag=1`）发起债券禁止库(15)/观察池(16)/黑名单质押库(17)/重点观察名单(23)入/出池申请。主体级禁投同步 **排除** ABS，故 ABS 必须经本入口单独调整。

---

## 1. 背景

| 链路 | 对象 | 池范围 | 说明 |
|------|------|--------|------|
| 禁投池调整 · 主体 | 发行主体 | 债券禁止库(15)/观察池(16)/黑名单质押库(17)/重点观察名单(23) | 通过后同步旗下**非 ABS** 债券 |
| **禁投池调整 · ABS债** | 单只 ABS 债 | **债券禁止库(15)/观察池(16)/黑名单质押库(17)/重点观察名单(23)** | 本期新增；债级，不同步主体下其他债 |
| 证券池调库 | 普通债等 | 权限内全量 | 交互参考对象；后端**不共用**接口 |

老系统对应：`querybondabs.jsp`（`abs_limit=1` + `LIMITPOOLID_XYJJ` + ABS 板块过滤）。新系统用 `abs_flag=1` 标识 ABS。

---

## 2. 页面概览

同一 Vue 实例 `#forbidden_pool_adjust`：

| 状态 | 含义 |
|------|------|
| `listTab` | `company` / `abs`（仅列表页展示 Tab） |
| `currentPage` | `list` / `detail`（主体）/ `absDetail`（ABS） |
| `absAdjustStep` | `1` 选池 / `2` 校验提交 |

- **列表 · 主体**：既有主体检索与调库入口，逻辑不变。  
- **列表 · ABS债**：筛选证券代码 / 简称 / 发行人；表格列对齐证券池调库列表。  
- **ABS 详情**：证券信息只读 → 当前所在池 → 可调入/可调出（仅债券禁止库(15)/观察池(16)/黑名单质押库(17)/重点观察名单(23)）→ 校验 → 流程选择 → multipart 提交。

审批：**不**新建 ABS 审核页。非直通流程进入「我的事宜」，`businessScene` 为 `securityAdjust`，打开 `security_pool_adjust_approve.html`。

---

## 3. 接口清单

前缀 `/api/v1/forbiddenAbsPoolAdjust`，全部 POST。

| 路径 | 用途 |
|------|------|
| `querySecurityPage` | ABS 债分页（强制 `abs_flag=1`） |
| `querySecurityDetail` | 详情头部；非 ABS 抛错 |
| `queryAdjustPoolList` | 可调池，**硬编码仅债券禁止库(15)/观察池(16)/黑名单质押库(17)/重点观察名单(23)** |
| `querySecurityPoolStatus` | 当前券所在池 + 主体所在池 |
| `checkAdjust` | 可行性校验 + 联动/互斥 + 流程候选 |
| `addAdjustLog` / `addAdjustLogWithFiles` | 提交（前端主入口 multipart） |
| `queryAdjustLogList` | 调库记录 |
| `queryAdjustStepList` | 步骤 |
| `queryLastCreditReport` | 近 6 个月信评回填（可选；禁止库池通常不在信用债 1～5 级白名单） |

公共：`/api/v1/attachments/*`、`/api/v1/reports/*`。

### 请求/响应实体（独立包）

`com.znty.rrs.entity.forbiddenabspooladjust`：

- `ForbiddenAbsPoolAdjustReq`
- `ForbiddenAbsPoolAdjustCheckReq`
- `ForbiddenAbsPoolAdjustSubmitReq` / `ForbiddenAbsPoolAdjustSubmitDto`

列表/详情展示 DTO 可复用 `securitypooladjust` 包内 `SecurityInfoDto` 等结构（Controller 返回类型）。

---

## 4. 核心业务规则

1. **ABS 识别**：`COALESCE(abs_flag,0)=1`；详情/校验/提交均 `validateAbsSecurity`，否则「非 ABS 债不允许在此调整」。  
2. **目标池**：手工项 `targetPoolId ∈ {15,16,17,23}`（债券禁止库、观察池、黑名单质押库、重点观察名单），否则「ABS禁投池调整手工目标池仅允许债券禁止库(15)、观察池(16)、黑名单质押库(17)、重点观察名单(23)」。  
3. **落表**：`ip_adjust_log` / `ip_adjust_step` / `ip_pool_status`（`audit_status` 状态机与同构链路一致）；批次号前缀 `BOND`。  
4. **禁止**：调用 `syncCompanyBonds*`；调用 `SecurityPoolAdjustService` / `ForbiddenPoolAdjustService` 业务入口。  
5. **直通**：即时写/软删池状态；**非直通**：`00` 进审批。

---

## 5. 后端类索引

| 层 | 类 |
|----|-----|
| Controller | `ForbiddenAbsPoolAdjustController` |
| Service | `ForbiddenAbsPoolAdjustService` |
| Mapper | `ForbiddenAbsPoolAdjustMapper` + XML |
| 测试 | `ForbiddenAbsPoolAdjustApiTest` |

---

## 6. 验收

- [ ] 列表 Tab「主体」「ABS债」；主体行为与改前一致  
- [ ] ABS 列表仅 `abs_flag=1`  
- [ ] 仅能选/提交债券禁止库(15)/观察池(16)/黑名单质押库(17)/重点观察名单(23)  
- [ ] 非 ABS 详情/校验失败  
- [ ] 直通只动该 ABS 的 `ip_pool_status`，不同步同主体其他债  
- [ ] 非直通可在我的事宜进入证券池审核页  
- [ ] ApiTest 通过  
