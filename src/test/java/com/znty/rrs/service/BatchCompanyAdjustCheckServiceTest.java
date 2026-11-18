package com.znty.rrs.service;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.forbiddenpooladjust.CompanyAdjustCheckContext;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustCheckReq;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.BatchCompanyAdjustCheckMapper;
import com.znty.rrs.mapper.ForbiddenPoolAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class BatchCompanyAdjustCheckServiceTest {

    /** 对照逐主体与批量校验，区分 Excel 调入、Excel 调出和独立清空所需数据 */
    @Test
    public void batchChecksShouldMatchExistingRulesAndQueryOncePerBatch() throws Exception {
        try (SqlSession session = openSession(); Statement sql = session.getConnection().createStatement()) {
            createTables(sql);
            seed(sql);
            BatchCompanyAdjustCheckMapper batchMapper = mock(BatchCompanyAdjustCheckMapper.class,
                    delegatesTo(session.getMapper(BatchCompanyAdjustCheckMapper.class)));
            ForbiddenPoolAdjustMapper singleMapper = mock(ForbiddenPoolAdjustMapper.class,
                    delegatesTo(session.getMapper(ForbiddenPoolAdjustMapper.class)));
            AutoAdjustMapper ratingMapper = mock(AutoAdjustMapper.class,
                    delegatesTo(session.getMapper(AutoAdjustMapper.class)));
            InvestmentPoolMapper pools = mock(InvestmentPoolMapper.class);
            InvestmentPoolBo watch = pool(23L, "重点观察名单", "restricted");
            watch.setOpenDayAdjust(1);
            InvestmentPoolBo blacklist = pool(17L, "黑名单质押库", "pledge");
            blacklist.setFrozenPeriodIn(30);
            when(pools.queryPoolList()).thenReturn(Arrays.asList(watch, blacklist,
                    pool(15L, "禁止库", "forbidden"), pool(16L, "观察池", "observe")));
            ExternalRatingAgencyService agencies = mock(ExternalRatingAgencyService.class);
            when(agencies.queryActiveAgencyCodeList()).thenReturn(Collections.singletonList("2"));
            when(agencies.queryRequiredAgencyCodeList()).thenReturn(Collections.singletonList("2"));

            PledgeBlacklistRuleService pledge = new PledgeBlacklistRuleService();
            ReflectionTestUtils.setField(pledge, "autoAdjustMapper", ratingMapper);
            ReflectionTestUtils.setField(pledge, "externalRatingAgencyService", agencies);
            ForbiddenPoolAdjustService check = new ForbiddenPoolAdjustService();
            ReflectionTestUtils.setField(check, "forbiddenPoolAdjustMapper", singleMapper);
            ReflectionTestUtils.setField(check, "investmentPoolMapper", pools);
            ReflectionTestUtils.setField(check, "pledgeBlacklistRuleService", pledge);
            BatchCompanyAdjustCheckService batch = new BatchCompanyAdjustCheckService();
            ReflectionTestUtils.setField(batch, "batchCompanyAdjustCheckMapper", batchMapper);
            ReflectionTestUtils.setField(batch, "forbiddenPoolAdjustMapper", singleMapper);
            ReflectionTestUtils.setField(batch, "investmentPoolMapper", pools);
            ReflectionTestUtils.setField(batch, "externalRatingAgencyService", agencies);

            Map<String, ForbiddenPoolAdjustDto> companies = new LinkedHashMap<>();
            for (String code : Arrays.asList("C1", "C2", "C3")) {
                ForbiddenPoolAdjustDto company = new ForbiddenPoolAdjustDto();
                company.setCompanyCode(code);
                company.setCompanyFullName(code + "全称");
                companies.put(code, company);
            }
            CompanyAdjustCheckContext legacy = check.createCompanyCheckContext(companies);
            for (String mode : Arrays.asList("in", "out", "clear")) {
                boolean inbound = "in".equals(mode);
                boolean clear = "clear".equals(mode);
                ForbiddenPoolAdjustCheckReq c1 = request("C1", inbound);
                ForbiddenPoolAdjustCheckReq c2 = request("C2", inbound);
                ForbiddenPoolAdjustCheckReq c3 = request("C3", inbound);
                AdjustCheckDto previousC1 = clear ? check.checkCompanyAdjust(c1, legacy) : check.checkCompanyExcelAdjust(c1, legacy, true);
                AdjustCheckDto previousC2 = clear ? check.checkCompanyAdjust(c2, legacy) : check.checkCompanyExcelAdjust(c2, legacy, true);
                AdjustCheckDto previousC3 = clear ? check.checkCompanyAdjust(c3, legacy) : check.checkCompanyExcelAdjust(c3, legacy, true);
                clearInvocations(batchMapper, singleMapper, ratingMapper, agencies);

                CompanyAdjustCheckContext current = clear
                        ? batch.loadClear(Arrays.asList("C1", "C2", "C3"), Collections.singleton(23L))
                        : batch.load(companies, Collections.singleton(23L), inbound);
                AdjustCheckDto checkedC1 = clear ? check.checkCompanyAdjust(c1, current) : check.checkCompanyExcelAdjust(c1, current, true);
                AdjustCheckDto checkedC2 = clear ? check.checkCompanyAdjust(c2, current) : check.checkCompanyExcelAdjust(c2, current, true);
                AdjustCheckDto checkedC3 = clear ? check.checkCompanyAdjust(c3, current) : check.checkCompanyExcelAdjust(c3, current, true);

                assertThat(checkedC1).usingRecursiveComparison().isEqualTo(previousC1);
                assertThat(checkedC2).usingRecursiveComparison().isEqualTo(previousC2);
                assertThat(checkedC3).usingRecursiveComparison().isEqualTo(previousC3);
                assertThat(checkedC1.getItems().get(0).getFailReasons())
                        .anyMatch(reason -> reason.contains("首个待办"));
                assertThat(checkedC1.getItems()).hasSize("out".equals(mode) ? 2 : 1);
                if ("out".equals(mode)) {
                    assertThat(checkedC1.getItems()).noneMatch(AdjustCheckDto.CheckResultItem::isCanAdjust);
                }
                assertThat(checkedC2.getItems()).hasSize(inbound ? 1 : 2);
                assertThat(checkedC3.getItems()).hasSize(inbound ? 2 : 1);
                if (!inbound) {
                    assertThat(checkedC2.getItems().get(1).getFailReasons())
                            .anyMatch(reason -> reason.contains("冻结期"));
                }
                if (clear) {
                    verify(batchMapper).queryCompanyDetails(anyList());
                }
                verify(batchMapper).queryCurrentPools(anyList(), anySet());
                verify(batchMapper).queryPendingLabels(anyList());
                verify(batchMapper).queryPendingPools(anyList());
                verify(batchMapper).queryPoolCounts(anySet());
                verify(batchMapper).queryOpenPools(anySet(), anyString());
                if (inbound || clear) {
                    verify(batchMapper).queryLowRatedCompanies(anyList(), anyList());
                    verify(agencies).queryActiveAgencyCodeList();
                }
                verifyNoMoreInteractions(batchMapper, ratingMapper);
                verify(singleMapper).queryAllPoolRelationList();
                verify(singleMapper).queryCategoryTypeBySecurityType("company");
                verifyNoMoreInteractions(singleMapper);
                verify(agencies, never()).queryRequiredAgencyCodeList();
                verifyNoMoreInteractions(agencies);
            }
        }
    }

    private ForbiddenPoolAdjustCheckReq request(String code, boolean inbound) {
        ForbiddenPoolAdjustCheckReq req = new ForbiddenPoolAdjustCheckReq();
        req.setCompanyCode(code);
        ForbiddenPoolAdjustCheckReq.CheckItem item = new ForbiddenPoolAdjustCheckReq.CheckItem();
        item.setTargetPoolId(23L);
        item.setAdjustMode(inbound ? "调入" : "调出");
        req.setItems(Collections.singletonList(item));
        return req;
    }

    private InvestmentPoolBo pool(Long id, String name, String type) {
        InvestmentPoolBo pool = new InvestmentPoolBo();
        pool.setId(id);
        pool.setPoolName(name);
        pool.setPoolType(type);
        pool.setStatus("enabled");
        pool.setIsDeleted(0);
        return pool;
    }

    private SqlSession openSession() throws Exception {
        String name = "company_check_equivalence";
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + name + ";MODE=MySQL", "sa", "");
        Configuration config = new Configuration(new Environment(name, new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        for (String mapper : Arrays.asList("BatchCompanyAdjustCheckMapper", "ForbiddenPoolAdjustMapper", "AutoAdjustMapper")) {
            String resource = "mapper/" + mapper + ".xml";
            StringBuilder xml = new StringBuilder();
            try (Reader reader = new InputStreamReader(Resources.getResourceAsStream(resource), StandardCharsets.UTF_8)) {
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    xml.append(buffer, 0, read);
                }
            }
            // H2 使用等价的日期表达式，旧版与批量外评查询都执行相同的筛选
            String sql = xml.toString().replace("DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 YEAR), '%Y%m%d')",
                    "FORMATDATETIME(DATEADD('YEAR', -1, CURRENT_DATE), 'yyyyMMdd')");
            try (InputStream input = new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8))) {
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
        }
        return new SqlSessionFactoryBuilder().build(config).openSession(false);
    }

    private void createTables(Statement sql) throws Exception {
        String schema = new String(Files.readAllBytes(Paths.get("sql/rrs_security_pool_adjust_schema.sql")), StandardCharsets.UTF_8);
        for (String table : Arrays.asList("ip_adjust_log", "ip_adjust_step", "ip_pool_status")) {
            int start = schema.indexOf("CREATE TABLE `" + table + "`");
            sql.execute(schema.substring(start, schema.indexOf(" ENGINE", start)));
        }
        sql.execute("CREATE TABLE ip_investment_pool (id BIGINT, pool_type VARCHAR(32), is_deleted INT)");
        sql.execute("CREATE TABLE ip_pool_relation (pool_id BIGINT, relation_type VARCHAR(32), relation_pool_id BIGINT, is_deleted INT)");
        sql.execute("CREATE TABLE ip_pool_open_day (pool_id BIGINT, begin_date DATE, end_date DATE, is_deleted INT)");
        sql.execute("CREATE TABLE dict_security_type (security_type VARCHAR(32), category_type VARCHAR(32), is_deleted INT)");
        sql.execute("CREATE SCHEMA ais_inv_ods");
        sql.execute("CREATE TABLE ais_inv_ods.wind_compintroduction (COMP_ID VARCHAR(32), COMP_NAME VARCHAR(128), COMP_SNAME VARCHAR(128))");
        sql.execute("CREATE TABLE ais_inv_ods.wind_cbondissuerrating "
                + "(s_info_compcode VARCHAR(32), b_info_creditratingagency VARCHAR(32), b_info_creditrating VARCHAR(32), ann_dt VARCHAR(8))");
    }

    private void seed(Statement sql) throws Exception {
        sql.execute("INSERT INTO dict_security_type VALUES ('company', 'company', 0)");
        sql.execute("INSERT INTO ip_investment_pool VALUES (15, 'forbidden', 0), (16, 'observe', 0),"
                + " (17, 'pledge', 0), (23, 'restricted', 0), (31, 'forbidden', 1)");
        sql.execute("INSERT INTO ip_pool_relation VALUES (23, 'in_linked', 17, 0), (23, 'out_linked', 17, 0)");
        sql.execute("INSERT INTO ip_pool_open_day VALUES (23, CURRENT_DATE, CURRENT_DATE, 1)");
        sql.execute("INSERT INTO ip_adjust_log (id, security_code, target_pool_id, audit_status, is_deleted, adjust_type, adjust_batch_no, audit_time)"
                + " VALUES (1, 'C1', 23, '11', 0, '手工调整', 'PENDING', NOW()),"
                + " (2, 'C2', 17, '20', 0, 'Excel导入', 'DONE', NOW()),"
                + " (3, 'C1', 17, '00', 0, '联动调整', 'PENDING', NOW())");
        sql.execute("INSERT INTO ip_adjust_step (adjust_batch_no, node_label, step_status, sort_order)"
                + " VALUES ('PENDING', '后续', 'pending', 2), ('PENDING', '首个待办', 'pending', 1)");
        sql.execute("INSERT INTO ip_pool_status (security_code, security_type, target_pool_id, audit_status, is_deleted, adjust_log_id)"
                + " VALUES ('C1','company',23,'20',0,1), ('C2','company',17,'20',0,2),"
                + " ('C1','company',17,'20',0,1), ('C2','bond',15,'20',0,2),"
                + " ('C2','company',15,'20',1,2), ('C2','company',31,'20',0,2),"
                + " ('C2','company',16,'00',0,2)");
        sql.execute("INSERT INTO ais_inv_ods.wind_compintroduction VALUES ('C1','C1全称','C1简称'), ('C2','C2全称','C2简称'), ('C3','C3全称','C3简称')");
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        sql.execute("INSERT INTO ais_inv_ods.wind_cbondissuerrating VALUES ('C1','2','AA-','" + today + "'),"
                + " ('C2','other','AA-','" + today + "'), ('C2','2','AA-','20000101')");
    }
}
