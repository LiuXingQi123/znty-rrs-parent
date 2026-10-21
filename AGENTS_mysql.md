# 数据库表设计与 SQL 生成规范（AGENTS_mysql.md）

> 适用项目：智慧风控平台（`znty-rrs`），MySQL 8.x  
> 说明：本文件与 `CLAUDE_mysql.md` 内容同步（仅文件名不同），修改时须两边同时更新。

> 仓库级总览见上级根目录 `../AGENTS.md` / `../CLAUDE.md`；后端编码与 ScriptTool 架构见本目录 `AGENTS.md` / `CLAUDE.md`。  
> 本文件与仓库根目录同名文件应保持内容同步（除路径表述外）；修改时请两边一起更新。  
> **本文件聚焦：表结构 DDL、演示数据 SQL、脚本落库与初始化注册约定。**  
> MyBatis XML 写法（逗号前置、禁止冗余 AS、`<where>` 等）见后端规范，**不在本文展开**。

---

## 一、版本与库环境

### 版本口径（统一）

| 项 | 约定 |
|----|------|
| 表设计 / 脚本注释目标版本 | **MySQL 8.0.33**（与多数 `sql/*_schema.sql` 头注释一致） |
| 应用连接驱动（参考） | `mysql-connector-j`（由 Spring Boot 2.7.18 管理，见本仓库 `pom.xml`） |
| 字符集 / 排序规则 | `utf8mb4` / `utf8mb4_0900_ai_ci` |

> 新建或修改 schema 头注释时写 **8.0.33**，不要写与工程不一致的 8.4 等版本，除非全库已统一升级并同步改文档与脚本。

### 数据库划分（强制）

| 库名 | 用途 | 脚本前缀 / 说明 |
|------|------|----------------|
| `znty_rrs` | **主业务库**（投资池、调库、流程、规则等） | `rrs_*.sql`、`rrs_external_import_*.sql` |
| `ais_inv_analysis` | AIS 投资分析（用户 / 角色 / 主体评级等） | `ais_inv_analysis_*.sql` |
| `ais_inv_ods` | Wind 外部源（只读为主，如发行人、主体评级） | `ais_inv_ods_*.sql` |

- 业务表默认建在 **`znty_rrs`**，禁止再使用文档历史示例中的 `znty_flow` 等错误库名。
- 跨库查询（如评级下调读 `ais_inv_ods`）由业务代码 / 数据源配置处理，**不要把 ODS 表建进主库**。

### 环境初始化（与工程脚本一致）

```sql
-- 主业务库示例
CREATE DATABASE IF NOT EXISTS `znty_rrs`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;
```

AIS 库将库名换成 `ais_inv_analysis` / `ais_inv_ods` 即可。

---

## 二、基础规范

### AIS 外部源表例外（优先适用）

- `ais_inv_analysis` / `ais_inv_ods` 的相关表，以用户提供的对应截图及明确补充要求为准，严格保留表名称、字段名称（含大小写）、字段顺序、类型、长度及截图可确认的约束和表选项。
- **除非用户主动要求，禁止额外补充任何字段**，包括 `id`、`crte_time`、`updt_time` 及审计字段；不得因下文通用规则擅自增加自增主键、索引或事件表。
- 下文关于统一字符集、排序规则、主键、非空约束及主表时间字段的规则，在与 AIS 截图或用户明确要求冲突时不适用；截图未明确的结构不得自行推断添加。
- 用户明确补充要求优先于截图。例如 `ais_inv_ods.wind_cbondchangewindcode.CHANGE_DATE` 为 `VARCHAR(8)`，存储 `yyyyMMdd` 格式的 8 位字符串日期。

以下为通用规则；AIS 外部源表优先适用上述例外。

- **存储引擎**：统一 `InnoDB`（`ENGINE = InnoDB`）
- **字符集**：`utf8mb4`（`DEFAULT CHARSET = utf8mb4`）
- **排序规则**：`utf8mb4_0900_ai_ci`（`COLLATE = utf8mb4_0900_ai_ci`）

### 注释规范（强制）

- **表注释**：建表必须有表级 `COMMENT`
- **字段注释**：每个字段都必须有 `COMMENT`；状态、类型等枚举须在注释中写清：`值1=含义1 / 值2=含义2`

---

## 三、字段与约束规范

### 主键设计

| 项目 | 规则 |
|------|------|
| 命名 | 统一命名为 `id` |
| 类型 | `BIGINT NOT NULL AUTO_INCREMENT` |
| 注释 | `'主键 ID'` |

### 非空与默认值约束

- 除主键外，所有字段统一允许为 `NULL`（即 `DEFAULT NULL`）
- **严禁**在除主键外的任何字段上设置 `NOT NULL`

### 外键与索引约束

- **禁止**使用物理外键
- **禁止**唯一索引（`UNIQUE KEY`）及除主键外的任何二级索引（`KEY` / `INDEX`）
- 表结构**仅保留** `PRIMARY KEY`（主表 `id` / 事件表 `evt_id`）
- 关联关系与业务唯一性由业务代码维护，不依赖库层唯一/外键/额外索引

### 时间字段（主表强制）

所有主表必须包含：

```sql
`crte_time`   DATETIME     DEFAULT NULL              COMMENT '创建时间',
`updt_time`   DATETIME     DEFAULT NULL              COMMENT '修改时间',
```

---

## 四、事件表（审计表）设计规范

需要记录变更历史的业务表，配套生成事件表（操作审计表）。

### 表名规则

主表表名 + `_evt` 后缀  

> 示例：主表 `wf_flow_definition` → 事件表 `wf_flow_definition_evt`

### 字段继承

事件表必须包含对应主表的**所有字段**。主表的 `id` 在事件表中为普通字段，不再作主键且允许 `NULL`。

### 事件表主键

| 项目 | 规则 |
|------|------|
| 命名 | 固定为 `evt_id` |
| 类型 | `BIGINT NOT NULL AUTO_INCREMENT` |
| 注释 | `'事件主键 ID'` |

### 事件表专属公共字段（强制）

事件表末尾固定追加：

```sql
`opter_id`    VARCHAR(20)  DEFAULT NULL              COMMENT '经办人 ID',
`opt_time`    DATETIME     DEFAULT NULL              COMMENT '经办时间',
`oprt_type`   VARCHAR(20)  DEFAULT NULL              COMMENT '操作类型，存储英文：INSERT=新增 / UPDATE=修改 / DELETE=删除',
```

### 表注释

主表注释内容 + `（操作审计）`

---

## 五、本仓库 SQL 脚本约定（工程落地）

### 存放位置与命名

脚本目录：本仓库 `sql/`（即 `znty-rrs-parent/sql/`）

| 类型 | 命名 | 说明 |
|------|------|------|
| 主库建表 | `rrs_<模块>_schema.sql` | 如 `rrs_pool_init_schema.sql` |
| 主库演示数据 | `rrs_<模块>_demo_data.sql` | 与 schema **成对** |
| 外部导入表 | `rrs_external_import_schema.sql` / `rrs_external_import_demo_data.sql` | 如 `rrs_securityinfo`，与调库运行态解耦 |
| AIS 分析库 | `ais_inv_analysis_schema.sql` / `ais_inv_analysis_demo_data.sql` | 目标库 `ais_inv_analysis` |
| AIS ODS 库 | `ais_inv_ods_schema.sql` / `ais_inv_ods_demo_data.sql` | 目标库 `ais_inv_ods` |

- 同一模块 **schema 与 demo 成对维护**；改表结构时同步评估 demo 是否要补列、补数。
- DDL 列定义风格与现有 `sql/*_schema.sql` 保持一致（多数为**行尾逗号**）；**不要**为了跟 MyBatis XML「逗号前置」去改 DDL 风格。

### ScriptTool 注册（强制）

新增或重命名 `.sql` 后，**必须**在 `ScriptToolService` 中注册，才会出现在「数据初始化 / 表数据清空」等页面：

| 场景 | 需同步的方法 / 任务 |
|------|---------------------|
| 建表 / Demo 脚本清单与执行顺序 | `querySchemaFiles()` / `queryDemoFiles()` |
| 模块重置 | `queryModuleTaskMap()` |
| 可清空表分组 | `buildTableGroup()` |
| 主库批量 INIT / RESET | `queryTaskMap()` 中 `INIT_SCHEMA` / `INIT_DEMO` / `RESET_ALL`（经 `queryRrsSchemaFiles()` / `queryRrsDemoFiles()`，排除 AIS 与外部导入） |
| 外部导入 | `INIT_EXTERNAL_IMPORT_SCHEMA` / `INIT_EXTERNAL_IMPORT_DEMO` |
| AIS | `INIT_AIS_SCHEMA` / `INIT_AIS_DEMO` |
| 调库运行态清空 | `queryAdjustFlowRuntimeTables()`（含调库日志/步骤/池状态/快照/附件/报告/导入临时表） |
| 仅工具脚本不注册 | 如 `tool_table_row_counts.sql`，列入 `querySqlFilesExcludedFromRegistration()` |

只把文件丢进 `sql/` 而不改 `ScriptToolService`，**线上初始化页不会执行该脚本**。  
生产请关闭写操作：`rrs.script.enabled=false` / `RRS_SCRIPT_ENABLED=false`。

### 演示 / 初始化数据（Demo）

- 新增测试数据字段时，**优先在原始 `INSERT INTO` 中补全列与值**。
- Demo 的字段与数据值直接维护在原始 `INSERT INTO` 中，**禁止用后置 `UPDATE` 修补初始化数据**。存量数据迁移仅在用户明确要求时另行处理，不得混入 Demo 脚本。
- Demo 中的枚举、状态码须与表字段注释、后端枚举、前端字典一致。

### 与 Mapper XML 的边界

| 文档 | 管什么 |
|------|--------|
| 本文件（`*_mysql.md`） | 库表 DDL、demo SQL、脚本命名与 ScriptTool |
| `znty-rrs-parent` 后端规范 | Mapper XML：逗号前置、禁止冗余 AS、`<where>` + `<if>` 等 |

---

## 六、SQL 脚本输出结构规范

建表脚本与 Demo 脚本（含 `sql/fund/`、`sql/stock/`）必须统一注释和排版。SQL 注释使用 `-- `，不要使用 `//`。

### 建表脚本（强制）

DDL 脚本按以下顺序组织：

1. **文件头注释**：模块说明、MySQL 目标版本（8.0.33）
2. **环境初始化**：`CREATE DATABASE IF NOT EXISTS` + `USE` + `SET NAMES utf8mb4`（目标库名正确）
3. **清理旧表**：所有 `DROP TABLE IF EXISTS` 集中放在建表之前，区段标题固定为「删除旧表（若存在）」，上下使用 `-- ----------------------------------------------------------------------------`；每条 DROP 上方单独添加 `-- 删除<中文表用途>`，事件表先于对应主表删除，历史表名须标明用途。
4. **建表**：每张 `CREATE TABLE` 上方使用相同的三行分隔注释，标题为「1. 中文表用途」「2. 中文表用途」等，按文件内创建顺序从 1 连续编号；先建主表，再建对应事件表。原有业务说明保留在标题与下分隔线之间。

- 字段新增或调整直接维护原始 `CREATE TABLE` 中的列定义、类型、默认值与注释；建表脚本**禁止追加 `ALTER TABLE` 或 `UPDATE`**，也不得附带「存量库修补」「已建库升级」等迁移区段。仅在用户明确要求存量迁移时另行处理。

### Demo 脚本（强制）

- 环境初始化后设置「清空业务表」区段，上下使用 `-- ----------------------------------------------------------------------------`；需要清空的表集中列出，每条语句上方添加 `-- 清空<中文表用途>`。
- 保留原有清理方式及范围：原有 `TRUNCATE`、`DELETE` 和 WHERE 条件不因格式统一而替换或扩大；幂等追加的共享数据不得新增清空操作。无清空或无预置数据的脚本不强行添加执行语句。
- 每个 INSERT 数据区段上方使用相同的三行分隔注释，说明中文表用途、样例内容；明确样例条数时可写「主体基础信息样例（10 条）」。保留原有数据分组、业务说明和依赖顺序。
- INSERT 的表名后另起字段列表，缩进 4 空格，每行多个字段、行尾逗号，按约 100 字符的适当长度换行；禁止每行一个字段，也不要把较长字段列表全部挤在一行。该规则适用于独立 `.sql`，不改变 Mapper XML 的逗号前置规则。
- `) VALUES` 后另起数据行，每个完整记录元组占一行，不按字段换行，也不把多条记录挤在一行；字符串内部确需换行时使用等价的 `\n` / `\r` 转义，保持解码后的内容不变。`INSERT ... SELECT` 保留原有查询逻辑。

```sql
CREATE DATABASE IF NOT EXISTS `ais_inv_analysis` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `ais_inv_analysis`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 清空业务表
-- ----------------------------------------------------------------------------
-- 清空主体评级结果表
TRUNCATE TABLE `t_inv_grade_result`;
-- 清空主体基础信息表
TRUNCATE TABLE `t_inv_company`;

-- ----------------------------------------------------------------------------
-- 主体基础信息样例（2 条）
-- ----------------------------------------------------------------------------
INSERT INTO `t_inv_company` (
    `id`, `code`, `full_name`, `short_names`, `wind_code`, `industry`, `researcher`,
    `business_scope`, `description`
) VALUES
(1, 'C10001', '某省交通投资集团有限公司', '某交投集团', 'C10001', '交通运输', '研究员1', '交通基础设施投资建设', '演示主体'),
(2, 'C10002', '某城市投资开发建设有限公司', '某城投公司', 'C10002', '建筑与工程', '研究员2', '城市基础设施建设', '演示主体');
```

### 标准脚本模板（主库示意）

```sql
-- ============================================================
-- 模块说明（请替换）
-- MySQL version: 8.0.33
-- ============================================================

-- 1. 环境初始化
CREATE DATABASE IF NOT EXISTS `znty_rrs`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
USE `znty_rrs`;
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 删除旧表（若存在）
-- ----------------------------------------------------------------------------
-- 删除流程定义主表（操作审计）
DROP TABLE IF EXISTS `wf_flow_definition_evt`;
-- 删除流程定义主表
DROP TABLE IF EXISTS `wf_flow_definition`;

-- ----------------------------------------------------------------------------
-- 1. 流程定义主表
-- ----------------------------------------------------------------------------
CREATE TABLE `wf_flow_definition` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    `name`        VARCHAR(128) DEFAULT NULL             COMMENT '流程名称，如：信用债入库审批',
    `flow_key`    VARCHAR(128) DEFAULT NULL             COMMENT '流程唯一标识 Key，代码调用时使用，如：bond:credit-inbound',
    `category`    VARCHAR(32)  DEFAULT NULL             COMMENT '业务分类：bond=债券 / fund=基金 / stock=股票 / company=主体 / other=其他',
    `description` VARCHAR(512) DEFAULT NULL             COMMENT '流程描述，简述业务场景及用途',
    `remark`      VARCHAR(512) DEFAULT NULL             COMMENT '内部备注，不对发起人展示',
    `status`      VARCHAR(16)  DEFAULT NULL             COMMENT '流程状态：draft=草稿 / active=已发布 / disabled=已停用',
    `created_by`  BIGINT       DEFAULT NULL             COMMENT '创建人用户 ID',
    `updated_by`  BIGINT       DEFAULT NULL             COMMENT '最后更新人用户 ID',
    `is_deleted`  TINYINT(1)   DEFAULT NULL             COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`   DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`   DATETIME     DEFAULT NULL             COMMENT '修改时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '流程定义主表';

-- ----------------------------------------------------------------------------
-- 2. 流程定义主表（操作审计）
-- ----------------------------------------------------------------------------
CREATE TABLE `wf_flow_definition_evt` (
    `evt_id`      BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '事件主键 ID',
    `id`          BIGINT       DEFAULT NULL             COMMENT '主键 ID',
    `name`        VARCHAR(128) DEFAULT NULL             COMMENT '流程名称，如：信用债入库审批',
    `flow_key`    VARCHAR(128) DEFAULT NULL             COMMENT '流程唯一标识 Key，代码调用时使用，如：bond:credit-inbound',
    `category`    VARCHAR(32)  DEFAULT NULL             COMMENT '业务分类：bond=债券 / fund=基金 / stock=股票 / company=主体 / other=其他',
    `description` VARCHAR(512) DEFAULT NULL             COMMENT '流程描述，简述业务场景及用途',
    `remark`      VARCHAR(512) DEFAULT NULL             COMMENT '内部备注，不对发起人展示',
    `status`      VARCHAR(16)  DEFAULT NULL             COMMENT '流程状态：draft=草稿 / active=已发布 / disabled=已停用',
    `created_by`  BIGINT       DEFAULT NULL             COMMENT '创建人用户 ID',
    `updated_by`  BIGINT       DEFAULT NULL             COMMENT '最后更新人用户 ID',
    `is_deleted`  TINYINT(1)   DEFAULT NULL             COMMENT '逻辑删除标志：0=正常 / 1=已删除',
    `crte_time`   DATETIME     DEFAULT NULL             COMMENT '创建时间',
    `updt_time`   DATETIME     DEFAULT NULL             COMMENT '修改时间',
    -- 审计字段
    `opter_id`    VARCHAR(20)  DEFAULT NULL             COMMENT '经办人 ID',
    `opt_time`    DATETIME     DEFAULT NULL             COMMENT '经办时间',
    `oprt_type`   VARCHAR(20)  DEFAULT NULL             COMMENT '操作类型，存储英文：INSERT=新增 / UPDATE=修改 / DELETE=删除',
    PRIMARY KEY (`evt_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '流程定义主表（操作审计）';
```

> 表示意沿用流程定义表结构；真实模块表名、字段以业务需求为准。目标库**必须是** `znty_rrs`（或 AIS 对应库），禁止 `znty_flow`。

---

## 七、速查清单

生成 / 修改每张表或脚本前，逐项确认：

### 表结构

- [ ] 引擎 `InnoDB`，字符集 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`
- [ ] 表有 `COMMENT`；字段均有 `COMMENT`；枚举注释含全部取值含义
- [ ] **主表**主键 `id`，**事件表**主键 `evt_id`，均为 `BIGINT NOT NULL AUTO_INCREMENT`
- [ ] 除主键外均为 `DEFAULT NULL`，无额外 `NOT NULL`
- [ ] 无物理外键、无 `UNIQUE KEY`、无二级 `KEY`/`INDEX`（仅 `PRIMARY KEY`）
- [ ] 主表含 `crte_time` / `updt_time`（事件表中为随主表复制的字段，不是独立审计语义）
- [ ] 若需事件表：表名 `_evt`，末尾 `opter_id` / `opt_time` / `oprt_type`（`oprt_type` 统一存英文 INSERT/UPDATE/DELETE）

### 工程落地

- [ ] 目标库正确：`znty_rrs` / `ais_inv_analysis` / `ais_inv_ods`（无错误库名）
- [ ] 脚本路径与命名符合 `sql/` 约定；schema 与 demo 成对
- [ ] 文件头版本注释为 MySQL 8.0.33（或与全库统一后的口径一致）
- [ ] 已同步注册 `ScriptToolService`（schema/demo 清单、模块重置、可清空表、INIT 任务等按需）
- [ ] 字段结构直接写入原始 `CREATE TABLE`，建表脚本无 `ALTER TABLE`、`UPDATE` 或存量升级区段
- [ ] Demo 新字段及数据值已写入原始 `INSERT`，无后置 `UPDATE` 修补
- [ ] DROP 集中置于建表之前且逐表有中文注释，CREATE 按顺序使用编号分隔注释
- [ ] Demo 清空区段与数据区段注释统一，清理方式、范围和业务说明保留
- [ ] INSERT 字段列表按适当长度换行，VALUES 每条记录一行，字符串解码内容不变
