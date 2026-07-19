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
| 应用连接驱动（参考） | `mysql-connector-java` 8.0.28（见本仓库 `pom.xml`） |
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

### 外键约束

- **禁止**使用物理外键
- 除主键外，不在数据库层定义其他外键约束
- 关联关系由业务代码维护

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
`oprt_type`   VARCHAR(20)  DEFAULT NULL              COMMENT '操作类型，存储中文，如：新增、删除、修改、审核',
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

只把文件丢进 `sql/` 而不改 `ScriptToolService`，**线上初始化页不会执行该脚本**。

### 演示 / 初始化数据（Demo）

- 新增测试数据字段时，**优先在原始 `INSERT INTO` 中补全列与值**。
- **不要**用后置 `UPDATE` 修补初始化数据，除非是明确的数据迁移脚本或用户特别要求。
- Demo 中的枚举、状态码须与表字段注释、后端枚举、前端字典一致。

### 与 Mapper XML 的边界

| 文档 | 管什么 |
|------|--------|
| 本文件（`*_mysql.md`） | 库表 DDL、demo SQL、脚本命名与 ScriptTool |
| `znty-rrs-parent` 后端规范 | Mapper XML：逗号前置、禁止冗余 AS、`<where>` + `<if>` 等 |

---

## 六、SQL 脚本输出结构规范

生成的 DDL 脚本建议按以下顺序：

1. **文件头注释**：模块说明、MySQL 目标版本（8.0.33）
2. **环境初始化**：`CREATE DATABASE IF NOT EXISTS` + `USE` + `SET NAMES utf8mb4`（目标库名正确）
3. **清理旧表**：`DROP TABLE IF EXISTS`，**先删事件表，再删主表**
4. **建表**：`CREATE TABLE`，**先建主表，再建事件表**

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
-- 2. 删除旧表（若存在）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `wf_flow_definition_evt`;
DROP TABLE IF EXISTS `wf_flow_definition`;

-- ----------------------------------------------------------------------------
-- 3. 创建主表
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
-- 4. 创建事件表
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
    `oprt_type`   VARCHAR(20)  DEFAULT NULL             COMMENT '操作类型，存储中文，如：新增、删除、修改、审核',
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
- [ ] 无物理外键
- [ ] 主表含 `crte_time` / `updt_time`（事件表中为随主表复制的字段，不是独立审计语义）
- [ ] 若需事件表：表名 `_evt`，末尾 `opter_id` / `opt_time` / `oprt_type`（`oprt_type` 存中文操作类型）

### 工程落地

- [ ] 目标库正确：`znty_rrs` / `ais_inv_analysis` / `ais_inv_ods`（无错误库名）
- [ ] 脚本路径与命名符合 `sql/` 约定；schema 与 demo 成对
- [ ] 文件头版本注释为 MySQL 8.0.33（或与全库统一后的口径一致）
- [ ] 已同步注册 `ScriptToolService`（schema/demo 清单、模块重置、可清空表、INIT 任务等按需）
- [ ] Demo 新字段已写入原始 `INSERT`，未用后置 `UPDATE` 凑数（除非迁移脚本）
