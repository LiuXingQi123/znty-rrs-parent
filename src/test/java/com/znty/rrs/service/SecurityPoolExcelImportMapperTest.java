package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.mapper.SecurityPoolExcelImportMapper;
import com.znty.rrs.mapper.ForbiddenPoolAdjustMapper;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustReq;
import com.znty.rrs.entity.securitypoolexcelimport.CompanyImportMatchDto;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Assume;
import org.junit.Test;

import java.io.InputStream;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 Mapper 验证主体名称精确匹配、代码去重和回填持久化。 */
public class SecurityPoolExcelImportMapperTest {

    /** 旧单条 SQL 与新批量 SQL 对相同条件返回相同主体代码，保留优先级判断所需的歧义信息。 */
    @Test
    public void batchMatchesShouldEqualPreviousSingleQueries() throws Exception {
        // 在隔离测试库对照精确匹配、大小写、重名、空编码及名称特殊字符
        try (SqlSession session = openSession("company_batch_equivalence")) {
            try (Statement statement = session.getConnection().createStatement()) {
                statement.execute("CREATE SCHEMA ais_inv_ods");
                statement.execute("SET IGNORECASE TRUE");
                statement.execute("CREATE TABLE ais_inv_ods.wind_compintroduction "
                        + "(COMP_ID VARCHAR(40), COMP_NAME VARCHAR(200), COMP_SNAME VARCHAR(100), REGISTERNUMBER VARCHAR(30))");
                statement.execute("INSERT INTO ais_inv_ods.wind_compintroduction VALUES "
                        + "('C001', 'Alpha有限公司', 'Alpha', 'CREDIT001'),"
                        + " ('C001', 'Alpha有限公司', 'Alpha', 'CREDIT001'),"
                        + " ('C002', '重名主体', '重名一', 'DUPLICATE'), ('C003', '重名主体', '重名二', 'DUPLICATE'),"
                        + " ('C004', '没有简称有限公司', NULL, NULL), ('C005', '名称%_主体', '特殊主体', 'CREDIT005'),"
                        + " (NULL, '空编码主体', NULL, NULL), ('', '空编码主体', NULL, NULL), ('   ', '空编码主体', NULL, NULL)");
            }
            SecurityPoolExcelImportMapper mapper = session.getMapper(SecurityPoolExcelImportMapper.class);
            List<List<String>> inputs = Arrays.asList(
                    Arrays.asList("CREDIT001", "credit001", "DUPLICATE", "CREDIT005", "MISSING", "CREDIT%", ""),
                    Arrays.asList("C001", "c001", "C004", "C00", "C00%", "MISSING", ""),
                    Arrays.asList("Alpha有限公司", "alpha有限公司", "Alpha", "重名主体", "没有简称有限公司",
                            "名称%_主体", "名称", "空编码主体", "MISSING", ""));
            String[] conditions = {"c.REGISTERNUMBER = ?", "c.COMP_ID = ?", "(c.COMP_NAME = ? OR c.COMP_SNAME = ?)"};
            for (int type = 0; type < conditions.length; type++) {
                List<CompanyImportMatchDto> matches = mapper.queryCompanyMatchList(type, inputs.get(type));
                String previousSql = "SELECT DISTINCT c.COMP_ID FROM ais_inv_ods.wind_compintroduction c WHERE "
                        + conditions[type] + " AND c.COMP_ID IS NOT NULL AND TRIM(c.COMP_ID) != '' LIMIT 2";
                for (String value : inputs.get(type)) {
                    List<String> previous = new ArrayList<>();
                    try (PreparedStatement statement = session.getConnection().prepareStatement(previousSql)) {
                        statement.setString(1, value);
                        if (type == 2) {
                            statement.setString(2, value);
                        }
                        try (ResultSet rows = statement.executeQuery()) {
                            while (rows.next()) {
                                previous.add(rows.getString(1));
                            }
                        }
                    }
                    List<String> current = matches.stream().filter(match -> value.equals(match.getLookupValue()))
                            .map(CompanyImportMatchDto::getCompanyCode).distinct().limit(2).collect(Collectors.toList());
                    assertThat(current).as("lookupType=%s, value=%s", type, value)
                            .containsExactlyInAnyOrderElementsOf(previous);
                }
            }
        }
    }

    /** 同主体多券去重，同名不同代码保留歧义，空代码和模糊名称不命中。 */
    @Test
    public void shouldMatchExactNameAndDistinctNonEmptyCodes() throws Exception {
        // 使用与项目 H2 版本兼容的运行时建立测试数据库
        try (SqlSession session = openSession("company_excel_name")) {
            try (Statement statement = session.getConnection().createStatement()) {
                statement.execute("CREATE SCHEMA ais_inv_ods");
                // 模拟源表不区分大小写的排序规则，原查询值仍须分别返回
                statement.execute("SET IGNORECASE TRUE");
                statement.execute("CREATE TABLE ais_inv_ods.wind_compintroduction "
                        + "(COMP_ID VARCHAR(40), COMP_NAME VARCHAR(200), COMP_SNAME VARCHAR(100), REGISTERNUMBER VARCHAR(30))");
                statement.execute("INSERT INTO ais_inv_ods.wind_compintroduction VALUES "
                        + "('C001', '测试主体有限公司', '测试主体', 'CREDIT001'), ('C001', '测试主体有限公司', '测试主体', 'CREDIT001'),"
                        + " ('C002', '测试主体子公司', '子公司', NULL),"
                        + " ('C003', '重名主体', '重名一', 'DUPLICATE'), ('C004', '重名主体', '重名二', 'DUPLICATE'),"
                        + " (NULL, '空代码主体', NULL, NULL), ('', '空代码主体', NULL, NULL), ('   ', '空代码主体', NULL, NULL)");
            }
            SecurityPoolExcelImportMapper mapper = session.getMapper(SecurityPoolExcelImportMapper.class);
            assertThat(matchedCodes(mapper, 2, "测试主体")).containsExactly("C001");
            assertThat(matchedCodes(mapper, 2, "测试主体有限公司")).containsExactly("C001");
            assertThat(matchedCodes(mapper, 2, "重名主体")).containsExactlyInAnyOrder("C003", "C004");
            assertThat(matchedCodes(mapper, 2, "空代码主体")).isEmpty();
            assertThat(matchedCodes(mapper, 2, "测试主")).isEmpty();
            assertThat(matchedCodes(mapper, 2, "测试%主体")).isEmpty();
            assertThat(matchedCodes(mapper, 0, "CREDIT001")).containsExactly("C001");
            assertThat(matchedCodes(mapper, 0, "DUPLICATE")).containsExactlyInAnyOrder("C003", "C004");
            assertThat(matchedCodes(mapper, 0, "MISSING")).isEmpty();
            assertThat(matchedCodes(mapper, 1, "C002")).containsExactly("C002");
            assertThat(matchedCodes(mapper, 1, "C00")).isEmpty();
            List<String> values = new ArrayList<>(Arrays.asList("C001", "C002"));
            for (int i = 2; i < 500; i++) {
                values.add("MISSING" + i);
            }
            List<CompanyImportMatchDto> batch = mapper.queryCompanyMatchList(1, values);
            assertThat(batch).extracting(CompanyImportMatchDto::getCompanyCode).containsOnly("C001", "C002");
            assertThat(batch).extracting(CompanyImportMatchDto::getLookupValue).containsOnly("C001", "C002");
            assertThat(batch.stream().filter(row -> "C001".equals(row.getCompanyCode())).findFirst().get().getCompanyFullName())
                    .isEqualTo("测试主体有限公司");
            assertThat(batch.stream().filter(row -> "C001".equals(row.getCompanyCode())).findFirst().get().getCompanyShortName())
                    .isEqualTo("测试主体");
            List<CompanyImportMatchDto> caseMatches = mapper.queryCompanyMatchList(0, Arrays.asList("credit001", "CREDIT001"));
            assertThat(caseMatches).extracting(CompanyImportMatchDto::getLookupValue).containsOnly("credit001", "CREDIT001");
            assertThat(caseMatches).extracting(CompanyImportMatchDto::getCompanyCode).containsOnly("C001");
        }
    }

    /** 未发债主体的详情和提交信息由公司简介返回，旧发行人记录不能覆盖主体身份。 */
    @Test
    public void shouldResolveUnissuedCompanyForCheckAndSubmit() throws Exception {
        // 新表有主体而旧发行人表没有该主体，完整调库仍应取得基础信息
        try (SqlSession session = openSession("company_excel_unissued")) {
            try (Statement statement = session.getConnection().createStatement()) {
                statement.execute("CREATE SCHEMA ais_inv_ods");
                statement.execute("CREATE TABLE ais_inv_ods.wind_compintroduction "
                        + "(COMP_ID VARCHAR(40), COMP_NAME VARCHAR(200), COMP_SNAME VARCHAR(100), COMP_TYPE VARCHAR(100),"
                        + " ADDRESS VARCHAR(500), REGISTERNUMBER VARCHAR(30), COMP_PROPERTY VARCHAR(100),"
                        + " PROVINCE VARCHAR(30), CITY VARCHAR(50), FOUNDDATE VARCHAR(8))");
                statement.execute("INSERT INTO ais_inv_ods.wind_compintroduction VALUES "
                        + "('C10011', '未发债主体有限公司', '未发债主体', '有限责任公司', '北京市海淀区',"
                        + " 'DEMO_CREDIT', '国有企业', '北京市', '北京市', '20010101')");
                statement.execute("CREATE TABLE ip_pool_status (security_code VARCHAR(40), security_type VARCHAR(20),"
                        + " target_pool_id BIGINT, audit_status VARCHAR(2), is_deleted INT, security_short_name VARCHAR(200))");
                statement.execute("INSERT INTO ip_pool_status VALUES ('C10011', 'company', 16, '20', 0, '池内主体名称'),"
                        + " ('BOND001', 'bond', 16, '20', 0, '债券'), ('C10012', 'company', 16, '00', 0, '在途主体')");
            }
            ForbiddenPoolAdjustMapper mapper = session.getMapper(ForbiddenPoolAdjustMapper.class);
            assertThat(mapper.queryCompanyDetail("C10011").getCompanyShortName()).isEqualTo("未发债主体");
            assertThat(mapper.queryCompanyDetail("C10011").getCompanyFullName()).isEqualTo("未发债主体有限公司");
            assertThat(mapper.queryCompanySecurityBoByCode("C10011").getWindCode()).isEqualTo("C10011");
            assertThat(mapper.queryCompanySecurityBoByCode("C10011").getFullName()).isEqualTo("未发债主体有限公司");
            assertThat(mapper.queryCompanySecurityBoByCode("C10011").getShortName()).isEqualTo("未发债主体");
            assertThat(mapper.queryCompanyDetail("MISSING")).isNull();
            ForbiddenPoolAdjustReq req = new ForbiddenPoolAdjustReq();
            req.setCompanyFullName("未发债主体有限公司");
            req.setRegisterNumber("DEMO_CREDIT");
            assertThat(mapper.queryCompanyPage(req)).hasSize(1);
            assertThat(mapper.queryCompanyPage(req).get(0).getRegisterNumber()).isEqualTo("DEMO_CREDIT");
            assertThat(mapper.queryCompanyPage(req).get(0).getCompProperty()).isEqualTo("国有企业");
            assertThat(mapper.queryCompanyPage(req).get(0).getFoundDate()).isEqualTo("20010101");
            req.setRegisterNumber("MISSING");
            assertThat(mapper.queryCompanyPage(req)).isEmpty();
            SecurityPoolExcelImportMapper importMapper = session.getMapper(SecurityPoolExcelImportMapper.class);
            assertThat(importMapper.queryPoolMemberList(16L, "company")).hasSize(1);
            assertThat(importMapper.queryPoolMemberList(16L, "company").get(0).getSecurityCode()).isEqualTo("C10011");
            assertThat(importMapper.queryPoolMemberList(16L, "company").get(0).getSecurityShortName()).isEqualTo("池内主体名称");
            try (Statement statement = session.getConnection().createStatement()) {
                statement.execute("UPDATE ais_inv_ods.wind_compintroduction SET COMP_SNAME = NULL WHERE COMP_ID = 'C10011'");
            }
            // JDBC 直接修改测试数据后清理 MyBatis 会话缓存，验证数据库中的空简称
            session.clearCache();
            assertThat(mapper.queryCompanySecurityBoByCode("C10011").getFullName()).isEqualTo("未发债主体有限公司");
            assertThat(mapper.queryCompanySecurityBoByCode("C10011").getShortName()).isNull();
            try (Statement statement = session.getConnection().createStatement()) {
                statement.execute("DROP TABLE ais_inv_ods.wind_compintroduction");
            }
            assertThat(importMapper.queryPoolMemberList(16L, "company")).hasSize(1);
        }
    }

    /** 重新校验回填的主体代码须写入临时明细，刷新和提交使用同一代码。 */
    @Test
    public void shouldPersistResolvedCompanyCodeWithCheckResult() throws Exception {
        // 使用真实更新和查询验证代码持久化
        try (SqlSession session = openSession("company_excel_code")) {
            try (Statement statement = session.getConnection().createStatement()) {
                statement.execute("CREATE TABLE sys_imp_tmp_detl (id BIGINT, imp_id VARCHAR(32),"
                        + " chk_rslt VARCHAR(2), chk_dscr VARCHAR(128), fld001 VARCHAR(32),"
                        + " fld009 VARCHAR(32), fld010 VARCHAR(32), fld011 VARCHAR(32), fld012 VARCHAR(128),"
                        + " fld013 VARCHAR(32), fld014 VARCHAR(128), updt_time TIMESTAMP, is_deleted INT)");
                statement.execute("INSERT INTO sys_imp_tmp_detl (id, imp_id, is_deleted) VALUES (1, 'IMP1', 0)");
            }
            SecurityPoolExcelImportMapper mapper = session.getMapper(SecurityPoolExcelImportMapper.class);
            SysImpTmpDetlBo item = new SysImpTmpDetlBo();
            item.setId(1L);
            item.setFld001("C001");
            item.setChkRslt("1");
            assertThat(mapper.editItemCheckResult(item)).isEqualTo(1);
            SysImpTmpDetlBo saved = mapper.queryItemById("IMP1", 1L);
            assertThat(saved.getFld001()).isEqualTo("C001");
            assertThat(saved.getChkRslt()).isEqualTo("1");
        }
    }

    /** 查询匹配出的不同主体代码。 */
    private List<String> matchedCodes(SecurityPoolExcelImportMapper mapper, int type, String value) {
        return mapper.queryCompanyMatchList(type, Collections.singletonList(value)).stream()
                .map(match -> match.getCompanyCode()).distinct().collect(Collectors.toList());
    }

    /** 加载真实导入 Mapper，按测试名称隔离 H2 数据库。 */
    private SqlSession openSession(String name) throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 持久化测试需 Java 11 以上运行时", major >= 11);
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + name + ";MODE=MySQL", "sa", "")));
        configuration.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/SecurityPoolExcelImportMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        resource = "mapper/ForbiddenPoolAdjustMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(configuration).openSession(true);
    }
}
