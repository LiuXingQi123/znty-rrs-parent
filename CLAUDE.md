# 后端开发规范（CLAUDE.md）

> 适用项目：Spring Boot 1.x 后端项目

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
- **字段**：`/** 字段说明 */`
- **方法**：Javadoc，说明用途，关键参数加 `@param`
- **方法内部**：关键步骤添加简洁单行注释
- **XML**：每个 `<select>` / `<insert>` / `<update>` / `<delete>` 上方添加注释

---

## Controller 规范

- 只负责接收请求和返回响应，不写业务逻辑
- 只调用 Service，禁止直接操作 Mapper
- 统一使用 `@PostMapping`
- 返回类型统一为 `ApiResponse<XxxDto>`，禁止返回 `ApiResponse<Void>`

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

- 每个 SQL 标签上方添加注释
- 禁止使用 `<sql>` + `<include>` 抽取公共片段
- 禁止手动拼 `WHERE 1=1`，统一使用 `<where>` + `<if>`

```xml
<!-- 分页查询流程列表 -->
<select id="queryFlowPage" resultType="com.example.dto.FlowDto">
    SELECT id, flow_code AS flowCode, flow_name AS flowName, create_time AS createTime
    FROM t_flow
    <where>
        <if test="flowName != null and flowName != ''">
            AND flow_name LIKE CONCAT('%', #{flowName}, '%')
        </if>
    </where>
    ORDER BY create_time DESC
</select>
```

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
| 10 | 时间字段使用 `Date` 类型 |
| 11 | Controller 方法返回 `ApiResponse<Void>` |
| 12 | 未经确认引入新的第三方库 |
| 13 | 类、方法、字段缺少注释 |
