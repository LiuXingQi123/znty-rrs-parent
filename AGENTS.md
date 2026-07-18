# 后端开发规范（AGENTS.md）

> 适用项目：Spring Boot 1.x 后端项目（`znty-rrs-parent`）  
> 说明：本目录 `CLAUDE.md` 与 `AGENTS.md` 内容同步（仅文件名不同），修改时须两边同时更新。

> 仓库级总览（业务模块表、调库三链路、`audit_status` 状态机、常用命令、前端页面 ↔ 需求 ↔ 测试对照表）见上级 `../CLAUDE.md` / `../AGENTS.md`；数据库表设计与 SQL 生成规范见 `../CLAUDE_mysql.md` / `../AGENTS_mysql.md`。本文件聚焦后端编码约定与本工程内需跨多文件才能厘清的架构要点，不重复上级文档已有内容。

---

## 后端架构要点

### 包结构

- 根包 `com.znty.rrs`，子包：`controller` / `service` / `mapper` / `entity` / `common` / `exception`
- `entity/bo`：所有与表一一对应的 `Bo` **集中存放**（不按模块拆分）
- `entity/<模块>/`：`Dto` / `Req` 按业务模块分子包（如 `entity.flow`、`entity.scripttool`、`entity.securitypooladjust`）
- `common`：`ApiResponse` / `PageRequest` / `PageResult` + `enums/`（纯 code 风格 `getCode()`，不放中文 label；业务代码用 `枚举.XXX.getCode()` 替代字面量）
- Mapper XML 集中在 `src/main/resources/mapper/*.xml`（**扁平命名**，不按包拆分），由 `mybatis.mapper-locations: classpath*:mapper/**/*.xml` 扫描

### 异常 → 响应契约

- 业务错误一律抛 `BizException(message)`（`exception.BizException`，默认 code=400）
- `exception.ExceptionConfig`（`@RestControllerAdvice`）捕获后转 `ApiResponse.fail(message)`；`code` **仅写日志，不进入响应体**
- 系统异常统一返回 `系统繁忙，请稍后重试`
- 因此 Controller **无需 try/catch**，业务失败由 Service 抛 `BizException` 即可，前端按 `success=false` + `message` 处理

### ScriptTool 模块（开发演示环境工具，刻意例外）

`ScriptToolController` / `ScriptToolService`（`/api/v1/scriptTool/*`）用于在线执行建表 / Demo / 清空脚本，**故意不遵循「每模块三类 + 走 Mapper」约定**，改动时须意识到这是设计上的例外：

- Service 直接注入 `javax.sql.DataSource` 执行白名单 SQL 文件，**无 Mapper / 无 XML** —— 不要以「补齐分层」为由改造
- 仅执行 `sql/` 目录下固定文件名白名单（`querySchemaFiles()` / `queryDemoFiles()`）和固定 `TRUNCATE` 语句，**绝不**接收前端传入的任意 SQL
- 用 `AtomicBoolean running` 做互斥，禁止并发执行
- 危险操作需前端回传与 `taskCode` 一致的 `confirmText` 二次确认
- SQL 文件有固定执行顺序（schema 先于 demo），改 `sql/` 目录文件名时**必须同步** `ScriptToolService` 内的白名单
- 数据初始化任务：`INIT_SCHEMA` / `INIT_DEMO` / `RESET_ALL` 仅主库业务脚本（排除 AIS 与外部导入表）；外部导入表走 `INIT_EXTERNAL_IMPORT_SCHEMA` / `INIT_EXTERNAL_IMPORT_DEMO`；AIS 走 `INIT_AIS_SCHEMA` / `INIT_AIS_DEMO`
- 配置项：`rrs.script.sql-path`（默认 `sql`，相对 `user.dir`）

### 数据库与 SQL 脚本

- 主库 `znty_rrs`，另有独立库 `ais_inv_analysis`（AIS 投资分析：用户 / 角色 / 主体评级，ScriptTool 同时管理）与 `ais_inv_ods`（Wind 外部源只读：发行人 `wind_cbondissuer` / 主体评级 `wind_cbondissuerrating`，评级下调校验跨库查询）
- `sql/` 下按模块成对存放 `rrs_<模块>_schema.sql`（建表）+ `rrs_<模块>_demo_data.sql`（演示数据）
- **外部导入表**（当前含 `rrs_securityinfo`）：`rrs_external_import_schema.sql` / `rrs_external_import_demo_data.sql`，与调库运行态脚本解耦，不随主库批量初始化执行
- AIS 库：`ais_inv_analysis_*`、`ais_inv_ods_*` 前缀
- 建表 / 写 SQL 前先查 `../CLAUDE_mysql.md` / `../AGENTS_mysql.md`（数据库表设计规范，二者同步）

---

## 技术栈

| 技术 | 版本 |
|------|------|
| Spring Boot | 1.5.9.RELEASE |
| Java | 1.8 |
| Maven | — |
| MyBatis（mybatis-spring-boot-starter） | 1.2.0 |
| MySQL（mysql-connector-java） | 8.0.28 |
| PageHelper | 4.0.0 |
| H2 | 2.3.232（test） |
| Lombok | 1.18.38 |
| QLExpress | 3.3.4 |

> ⚠️ 如需引入上述以外的第三方库，须主动询问用户并经确认后方可使用，**不得擅自引入**。

---

## 接口规范

- 统一使用 `POST`，禁止使用 GET / PUT / DELETE
- 路径前缀：`/api/v1/`
- URL 路径段使用 camelCase（如 `/api/v1/testCases`），禁止 kebab-case（如 `/api/v1/test-cases`）
- 入参统一用 `@RequestBody XxxReq req`，禁止使用 `@RequestParam` / `@PathVariable`
- 所有接口统一返回 `ApiResponse<T>`，分页接口数据字段使用 `PageResult<T>`

---

## 工程分层

每个业务模块只允许创建三个类：

```
Controller   ── 接收请求，调用 Service，返回响应，不写业务逻辑
Service      ── 业务逻辑处理，调用 Mapper
Mapper       ── 数据库操作接口，对应 XML
```

实体后缀规则：

| 后缀 | 用途 |
|------|------|
| `Bo` | 与数据库表一一对应的实体 |
| `Dto` | 返回给前端的数据传输对象（1~3 个） |
| `Req` | 接收前端请求参数的对象（1~3 个） |

---

## 命名规范

**方法名 = 动词前缀 + 功能描述**，禁止单独使用前缀。

| 场景 | 前缀 | 结尾要求 | 示例 |
|------|------|----------|------|
| 分页查询 | `query` | `Page` | `queryFlowPage` |
| 列表查询 | `query` | `List` | `queryFlowList` |
| 新增 | `add` | 功能描述 | `addFlow` |
| 编辑 | `edit` | 功能描述 | `editFlow` |
| 删除 | `delete` | 功能描述 | `deleteFlow` |

- 禁止 `PageList` / `ListPage` 混合写法
- 禁止 `add()`、`query()` 等无功能描述的命名
- 主键字段统一使用 `Long` 类型
- 时间字段统一使用 `Date` + `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`
- 类名使用 PascalCase + 后缀（`FlowBo`、`FlowDto`、`FlowReq`）
- 方法名、字段名使用 camelCase

---

## 注释规范

- **类**：Javadoc，说明职责
- **字段**：`/** 字段说明 */`，**必须保留**（如 `/** 证券池批量调整数据访问组件 */` 紧邻 `private BatchSecurityPoolAdjustMapper batchSecurityPoolAdjustMapper;`），禁止以“字面翻译型注释”为由删除；判断特征：注释描述的是该字段的用途/类型/职责，与下一行字段声明直接对应
- **方法**：Javadoc，说明用途，关键参数加 `@param`
- **方法内部**：关键步骤添加简洁单行注释；**调用其他私有方法时，必须在调用行上方添加 `//` 注释说明调用目的**（如 `// 构建白名单流程候选项`），禁止省略；该注释属于“调用目的说明”，不得当作“字面翻译型注释”误删
- **XML**：每个 `<select>` / `<insert>` / `<update>` / `<delete>` 上方添加注释

---

## Controller 规范

- 只负责接收请求和返回响应，不写业务逻辑
- 只调用 Service，禁止直接操作 Mapper
- 类级别 `@RequestMapping` 必须使用完整资源路径（如 `/api/v1/flows`），禁止只写 `/api/v1`
- 统一使用 `@PostMapping`
- 返回类型统一为 `ApiResponse<XxxDto>`，禁止返回 `ApiResponse<?>` 或 `ApiResponse<Void>`

```java
@RestController
@RequestMapping("/api/v1/flow")
public class FlowController {

    @Autowired
    private FlowService flowService;

    /** 分页查询流程列表 */
    @PostMapping("/queryFlowPage")
    public ApiResponse<PageResult<FlowDto>> queryFlowPage(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.queryFlowPage(req));
    }

    /** 新增流程 */
    @PostMapping("/addFlow")
    public ApiResponse<FlowDto> addFlow(@RequestBody FlowReq req) {
        return ApiResponse.success(flowService.addFlow(req));
    }
}
```

---

## Service 规范

- 所有业务逻辑写在 Service 中
- 分页统一用 `PageHelper.startPage()` 开启，紧接着调用 Mapper

```java
public PageResult<FlowDto> queryFlowPage(FlowReq req) {
    // 开启分页
    PageHelper.startPage(req.getPageIndex(), req.getPageSize());
    // 查询列表
    List<FlowDto> list = flowMapper.queryFlowPage(req);
    // 获取分页信息
    PageInfo<FlowDto> pageInfo = new PageInfo<>(list);
    return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
}
```

---

## Mapper 规范

- 每个方法加 Javadoc 注释
- 只定义接口，SQL 在对应 XML 中编写

---

## XML 规范

### SQL 格式

- 每个 SQL 标签上方添加注释
- 禁止使用 `<sql>` + `<include>` 抽取公共片段
- 禁止手动拼 `WHERE 1=1`，统一使用 `<where>` + `<if>`
- **逗号前置**：`,` 写在列/字段/排序项的前面，不写在后缀行尾
  - SELECT 列表第二列起、INSERT 列名/值、UPDATE SET 第二项起、ORDER BY 第二项起 — 都以 `,column` 开头
  - 第一项行不加逗号，后续每行以逗号打头
- **禁止冗余 AS 别名**：已配置 `map-underscore-to-camel-case: true`，`column_name` 会自动映射为 `columnName`，无需 `AS columnName`
  - 仅以下情况保留 AS：① 计算表达式（COALESCE、COUNT、GROUP_CONCAT 等）② 字面量（如 `'在池' AS status`）③ 返回字段名与数据库列名不一致（如 `id AS flowId`、`b_info_issuer AS issuer`）
- 演示/初始化 SQL 中新增测试数据字段时，应优先在原始 `INSERT INTO` 数据中补全；不要用后置 `UPDATE` 修补初始化数据，除非是明确的数据迁移脚本或用户特别要求。

```xml
<!-- 分页查询流程列表 -->
<select id="queryFlowPage" resultType="com.example.dto.FlowDto">
    SELECT d.*
           ,COALESCE(...) AS current_ver
           ,EXISTS(...) AS has_published_version
    FROM wf_flow_definition d
    <where>
        <if test="flowName != null and flowName != ''">
            AND d.name LIKE CONCAT('%', #{flowName}, '%')
        </if>
    </where>
    ORDER BY d.updt_time DESC
             ,d.id DESC
</select>

<!-- 新增记录 -->
<insert id="addFlow" useGeneratedKeys="true" keyProperty="id">
    INSERT INTO t_flow (
        name
        ,flow_key
        ,create_time
    ) VALUES (
        #{name}
        ,#{flowKey}
        ,#{crteTime}
    )
</insert>
```

---

## 字典转换

- 后端只返回原始 code，禁止在后端做 code → name 的中文转换
- 所有 code → 中文名称的映射由前端维护字典完成
- 下拉选项接口只返回 code 列表，中文 label 由前端映射
- 例外：从数据库查出的名称字段（如角色表 `name`）不属于硬编码字典，可直接返回

---

## 前端显示约定

- 接口返回空值时保持 `null` 或空字符串，不要为了前端展示额外返回 `-`；前端字段、表格单元格、详情项为空时直接显示空白，不使用 `-`、`--` 等占位符。

---

## 错误数据处理

- 禁止为错误、残缺或不一致的数据增加推断、回退查询、默认值填充或静默兜底
- 数据不满足当前业务约束时，应直接抛出包含明确原因和定位信息的异常
- 发现历史错误数据时，应修复数据源或通过专项数据脚本治理，不得在业务代码中长期兼容
- 如确需兼容历史数据，必须由用户明确提出并确认兼容范围

---

## 禁止事项

| # | 禁止行为 |
|---|----------|
| 1 | Controller 中编写业务逻辑 |
| 2 | Controller 直接调用 Mapper |
| 3 | 使用 `@GetMapping` 等非 POST 注解 |
| 4 | 使用 `@RequestParam` / `@PathVariable` 接收参数 |
| 5 | XML 中使用 `<sql>` + `<include>` |
| 6 | 手动拼接 `WHERE 1=1` |
| 7 | 方法名只用前缀不加功能描述（如 `add()`） |
| 8 | 分页与列表后缀混用（如 `PageList`） |
| 9 | 主键使用 `Integer` 类型 |
| 10 | Controller 方法返回 `ApiResponse<?>` 或 `ApiResponse<Void>` |
| 11 | 未经确认引入新的第三方库 |
| 12 | 类、方法、字段缺少注释 |
| 13 | SELECT 列表中写可自动映射的 AS 别名（如 `column_name AS columnName`） |
| 14 | SQL 逗号放行尾（统一逗号前置） |
| 15 | 后端做 code → name 的中文字典映射（应由前端维护） |
| 16 | `@RequestMapping` 类级别只写 `/api/v1`（应写完整路径） |
| 17 | URL 路径使用 kebab-case（统一 camelCase） |
| 18 | 方法内部调用私有方法时，调用行上方缺少 `//` 注释说明调用目的 |

---

## 编码与乱码防护

- 所有包含中文的 Java、XML、SQL、Markdown 文件统一按 UTF-8 读取和写入，禁止用不明确编码的脚本批量改写文件。
- Windows PowerShell/终端可能因为 GBK 控制台把 UTF-8 中文显示成乱码；判断文件是否乱码时，必须用 UTF-8 方式读取文件或用编辑器确认，不能只依据终端显示。
- 修改中文注释、SQL 注释、演示数据中文文案后，必须扫描典型乱码特征，例如 `瀹`、`鐎`、`閰`、`鍙`、`鏉`、`涓`、`�`、`???`。
- 如发现乱码，优先从 Git 中恢复原始可读内容，再重新做最小范围业务修改；不要继续在乱码文本上猜测替换。
- 用脚本处理中文文件时，显式指定 `encoding='utf-8-sig'` 读取、`encoding='utf-8'` 写入，并避免经过会改变编码的控制台管道。
