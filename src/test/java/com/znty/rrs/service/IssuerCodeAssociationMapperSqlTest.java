package com.znty.rrs.service;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用 H2 执行实际 Mapper 中的主体关联条件，覆盖全部十五处同主体查询。 */
@RunWith(Parameterized.class)
public class IssuerCodeAssociationMapperSqlTest {

    /** 待验证的 Mapper 文件名 */
    private final String mapperFile;
    /** 待验证的查询方法 */
    private final String queryId;
    /** 独立内存数据库 */
    private JdbcTemplate jdbc;
    /** 从实际查询截取的同主体条件 */
    private String association;
    /** 关联条件使用的证券别名 */
    private String alias;

    /** 指定本组测试对应的 Mapper 查询。 */
    public IssuerCodeAssociationMapperSqlTest(String mapperFile, String queryId) {
        this.mapperFile = mapperFile;
        this.queryId = queryId;
    }

    /** 枚举四条调库链路中全部同主体查询。 */
    @Parameterized.Parameters(name = "{0}.{1}")
    public static Collection<Object[]> queries() {
        List<Object[]> cases = new ArrayList<>();
        for (String mapper : new String[]{"SecurityPoolAdjustMapper.xml", "ForbiddenAbsPoolAdjustMapper.xml",
                "CrmwPoolAdjustMapper.xml", "ForbiddenPoolAdjustMapper.xml"}) {
            cases.add(new Object[]{mapper, "queryIssuerPoolStatusList"});
            cases.add(new Object[]{mapper, "queryIssuerTargetPoolMaxRemainDays"});
            if (!"ForbiddenPoolAdjustMapper.xml".equals(mapper)) {
                cases.add(new Object[]{mapper, "queryHasRecentInboundWithReport"});
            }
            if ("SecurityPoolAdjustMapper.xml".equals(mapper)
                    || "ForbiddenAbsPoolAdjustMapper.xml".equals(mapper)) {
                cases.add(new Object[]{mapper, "queryLastInboundLogIdWithCreditReportByIssuer"});
                cases.add(new Object[]{mapper, "queryIssuerHasNonSimpleInboundWithinDays"});
            }
        }
        return cases;
    }

    /** 建立改名、同名不同主体、空代码证券数据，并读取实际关联条件。 */
    @Before
    public void setUp() throws Exception {
        String version = System.getProperty("java.specification.version");
        int javaMajor = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 数据库集成测试需要 Java 11 以上运行时", javaMajor >= 11);
        String xml = new String(Files.readAllBytes(Paths.get("src", "main", "resources", "mapper", mapperFile)),
                StandardCharsets.UTF_8);
        int start = xml.indexOf("<select id=\"" + queryId + "\"");
        assertThat(start).as(queryId + " 应存在").isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</select>", start);
        assertThat(end).as(queryId + " 应闭合").isGreaterThan(start);
        String select = xml.substring(start, end);
        // 截取真实关联条件及紧随其后的过滤条件，避开各业务查询的日期函数和池状态条件。
        Matcher matcher = Pattern.compile("WHERE (sb|si)\\.issuer(?:_code)?\\s*=\\s*\\("
                + "\\s*SELECT issuer(?:_code)?\\s+FROM rrs_securityinfo\\s+WHERE wind_code = "
                + "#\\{securityCode}\\s+LIMIT 1\\s*\\)"
                + "(?:\\s+AND \\1\\.issuer(?:_code)? (?:IS NOT NULL|!= ''))*",
                Pattern.DOTALL).matcher(select);
        assertThat(matcher.find()).as(queryId + " 应有同主体关联条件").isTrue();
        alias = matcher.group(1);
        association = matcher.group().replace("#{securityCode}", "?");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:issuer_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(40), issuer VARCHAR(100), issuer_code VARCHAR(40))");
        jdbc.update("INSERT INTO rrs_securityinfo VALUES "
                + "('CURRENT', '同名发行人', 'C001'), ('RENAMED', '发行人新名称', 'C001'),"
                + "('SAME_NAME', '同名发行人', 'C002'), ('NULL_A', '同名发行人', NULL),"
                + "('NULL_B', '同名发行人', NULL), ('EMPTY_A', '同名发行人', ''),"
                + "('EMPTY_B', '同名发行人', '')");
    }

    /** 关闭本组测试创建的内存库。 */
    @After
    public void tearDown() {
        if (jdbc != null) {
            jdbc.execute("SET DB_CLOSE_DELAY 0");
        }
    }

    /** 主体代码相同的改名证券应关联，名称相同但代码不同的证券不得混入。 */
    @Test
    public void shouldAssociateByCodeRegardlessOfName() {
        // 执行实际条件，验证改名前后的两个入口均得到相同主体证券。
        assertThat(associatedCodes("CURRENT")).containsExactlyInAnyOrder("CURRENT", "RENAMED");
        assertThat(associatedCodes("RENAMED")).containsExactlyInAnyOrder("CURRENT", "RENAMED");
        assertThat(associatedCodes("SAME_NAME")).containsExactly("SAME_NAME");
    }

    /** NULL 主体代码不得按名称或空值匹配其他证券。 */
    @Test
    public void shouldNotAssociateNullIssuerCodes() {
        // 验证缺少主体代码时不会回退到名称关联。
        assertThat(associatedCodes("NULL_A")).isEmpty();
    }

    /** 空字符串主体代码按 SQL 相等条件直接匹配。 */
    @Test
    public void shouldUseDirectEqualityForEmptyIssuerCodes() {
        // 验证空字符串按相等条件得到匹配结果。
        assertThat(associatedCodes("EMPTY_A")).containsExactlyInAnyOrder("EMPTY_A", "EMPTY_B");
    }

    /** 对最小证券数据集执行 Mapper 原有的主体关联条件。 */
    private List<String> associatedCodes(String securityCode) {
        return jdbc.queryForList("SELECT " + alias + ".wind_code FROM rrs_securityinfo " + alias
                + " " + association, String.class, securityCode);
    }
}
