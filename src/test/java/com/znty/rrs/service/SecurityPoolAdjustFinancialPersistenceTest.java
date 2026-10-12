package com.znty.rrs.service;

import com.znty.rrs.entity.securitypooladjust.IssuerFinancialDto;
import com.znty.rrs.entity.securitypooladjust.IssuerFinancialSaveReq;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assume;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 使用实际 Mapper 和事务代理验证四期查询及批量保存，不连接本地业务库。 */
public class SecurityPoolAdjustFinancialPersistenceTest {
    /** 内存测试库 */
    private JdbcTemplate jdbc;
    /** 真实事务代理 */
    private SecurityPoolAdjustService service;
    /** 实际财报 Mapper */
    private SecurityPoolAdjustMapper mapper;

    /** 建立最小库表并加载实际 XML。 */
    @Before
    public void setUp() throws Exception {
        String version = System.getProperty("java.specification.version");
        int javaMajor = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 数据库集成测试需要 Java 11 以上运行时", javaMajor >= 11);
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:financial_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE SCHEMA ais_inv_ods");
        jdbc.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(40) PRIMARY KEY, issuer_code VARCHAR(40))");
        jdbc.update("INSERT INTO rrs_securityinfo VALUES ('B001','C001'),('B002','C002')");
        jdbc.execute("CREATE TABLE ais_inv_ods.wind_companyfinancial (COMPANYCODE VARCHAR(40), REPORTDATE BIGINT,"
                + "TOT_ASSETS DECIMAL(22,6) CHECK(TOT_ASSETS >= 0), SHAREHOLDER_EQUITY DECIMAL(22,6),"
                + "DEBT_ASSETS_RATIO DECIMAL(22,6), TOT_REV DECIMAL(22,6), NET_PROFIT DECIMAL(22,6),"
                + "NET_CASH_OPER DECIMAL(22,6), NET_CASH_INV DECIMAL(22,6), GRP DECIMAL(22,6),"
                + "GENERAL_BUDGET_REV DECIMAL(22,6), GENERAL_BUDGET_EXP DECIMAL(22,6), ROE DECIMAL(22,6),"
                + "EBIT_INT_COV DECIMAL(22,6), EBITDA DECIMAL(22,6), EBITDA_TO_DEBT DECIMAL(22,6),"
                + "PROVINCE VARCHAR(40), CITY VARCHAR(40), PRIMARY KEY(COMPANYCODE,REPORTDATE))");
        Configuration config = new Configuration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new SpringManagedTransactionFactory(), ds));
        try (InputStream xml = getClass().getResourceAsStream("/mapper/SecurityPoolAdjustMapper.xml")) {
            new XMLMapperBuilder(xml, config, "mapper/SecurityPoolAdjustMapper.xml", config.getSqlFragments()).parse();
        }
        mapper = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config)).getMapper(SecurityPoolAdjustMapper.class);
        SecurityPoolAdjustService target = new SecurityPoolAdjustService();
        ReflectionTestUtils.setField(target, "securityPoolAdjustMapper", mapper);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        service = (SecurityPoolAdjustService) proxy.getProxy();
    }

    /** 缺失年报不能用当年季报替代，第四列为最新季报。 */
    @Test
    public void shouldKeepAnnualSlotsAndLatestQuarter() {
        insert(reportDate(-3, 930), "8"); insert(reportDate(-2, 1231), "10"); insert(reportDate(-1, 1231), "12"); insert(reportDate(0, 630), "15");
        List<IssuerFinancialDto> rows = queryDefault();
        assertThat(rows).extracting(IssuerFinancialDto::getReportDate).containsExactly(reportDate(-3, 1231),reportDate(-2, 1231),reportDate(-1, 1231),reportDate(0, 630));
        assertThat(rows.get(0).getTotAssets()).isNull();
        assertThat(rows.get(3).getTotAssets()).isEqualByComparingTo("15");
    }

    /** 最新为上一年年报及无数据时都保留前四年年报。 */
    @Test
    public void shouldUseFourAnnualsWithPreviousAnnualOrNoReports() {
        assertThat(queryDefault()).extracting(IssuerFinancialDto::getReportDate).containsExactly(reportDate(-4, 1231),reportDate(-3, 1231),reportDate(-2, 1231),reportDate(-1, 1231));
        assertThat(queryDefault()).allSatisfy(row -> assertThat(row.getTotAssets()).isNull());
        insert(reportDate(-1, 1231), "12");
        assertThat(queryDefault()).extracting(IssuerFinancialDto::getReportDate).containsExactly(reportDate(-4, 1231),reportDate(-3, 1231),reportDate(-2, 1231),reportDate(-1, 1231));
    }

    /** 四个标准报告期都支持新增并精确查询。 */
    @Test
    public void shouldInsertAllFourReportTypes() {
        assertThat(mapper.queryIssuerFinancialByReportDate("B001",20240331L)).isNull();
        service.saveIssuerFinancialList(request(change(20240331L,"1"),change(20240630L,"2"),
                change(20240930L,"3"),change(20241231L,"4")));
        assertThat(mapper.queryIssuerFinancialByReportDate("B001",20240930L).getTotAssets()).isEqualByComparingTo("3");
        assertThat(count()).isEqualTo(4);
    }

    /** 完整指标覆盖及清空生效，缓存中的另一分类指标和地域信息保留。 */
    @Test
    public void shouldOverwriteCompleteMetricsAndClearExistingValues() {
        insert(20251231L,"12");
        jdbc.update("UPDATE ais_inv_ods.wind_companyfinancial SET ROE=8,GRP=100,PROVINCE='浙江',CITY='杭州'");
        IssuerFinancialDto edit = mapper.queryIssuerFinancialByReportDate("B001",20251231L);
        edit.setTotAssets(new BigDecimal("32.480712"));
        IssuerFinancialDto saved = service.saveIssuerFinancialList(request(edit)).get(0);
        assertThat(saved.getTotAssets()).isEqualByComparingTo("32.480712");
        assertThat(saved.getRoe()).isEqualByComparingTo("8");
        assertThat(saved.getGrp()).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT CITY FROM ais_inv_ods.wind_companyfinancial",String.class)).isEqualTo("杭州");
        edit.setTotAssets(null);
        edit.setRoe(new BigDecimal("999"));
        saved = service.saveIssuerFinancialList(request(edit)).get(0);
        assertThat(saved.getTotAssets()).isNull();
        assertThat(saved.getRoe()).isEqualByComparingTo("999");
        assertThat(saved.getGrp()).isEqualByComparingTo("100");
        saved = service.saveIssuerFinancialList(request(change(20251231L,null))).get(0);
        assertThat(saved.getRoe()).isNull();
        assertThat(saved.getGrp()).isNull();
        assertThat(jdbc.queryForObject("SELECT PROVINCE FROM ais_inv_ods.wind_companyfinancial",String.class)).isEqualTo("浙江");
    }

    /** 空列表和没有指标数值的缺失报告不会新增记录。 */
    @Test
    public void shouldSkipEmptyMissingReports() {
        assertThat(service.saveIssuerFinancialList(request())).isEmpty();
        service.saveIssuerFinancialList(request(change(20231231L,null)));
        assertThat(count()).isZero();
    }

    /** 第二期 SQL 失败时第一期的写入也必须回滚。 */
    @Test
    public void shouldRollBackWholeBatch() {
        insert(20251231L,"12");
        assertThatThrownBy(() -> service.saveIssuerFinancialList(request(change(20251231L,"22"),
                change(20260630L,"-1")))).isInstanceOf(RuntimeException.class);
        assertThat(mapper.queryIssuerFinancialByReportDate("B001",20251231L).getTotAssets()).isEqualByComparingTo("12");
        assertThat(mapper.queryIssuerFinancialByReportDate("B001",20260630L)).isNull();
    }

    /** 批次预校验拒绝非法报告日期、空记录和重复报告期。 */
    @Test
    public void shouldRejectInvalidChangesBeforeWriting() {
        assertThatThrownBy(() -> service.saveIssuerFinancialList(request(change(20251231L,"1"),
                change(20260501L,"2")))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.saveIssuerFinancialList(request((IssuerFinancialDto) null))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.saveIssuerFinancialList(request(change(20251231L,"1"),
                change(20251231L,"2")))).isInstanceOf(BizException.class);
        assertThat(count()).isZero();
    }

    /** 写入主体只能由证券主档确定；未知证券拒绝保存。 */
    @Test
    public void shouldResolveIssuerFromMaster() {
        IssuerFinancialSaveReq req = request(change(20251231L,"1"));
        req.setSecurityCode("unknown");
        assertThatThrownBy(() -> service.saveIssuerFinancialList(req)).isInstanceOf(BizException.class);
        req.setSecurityCode("B002"); service.saveIssuerFinancialList(req);
        assertThat(jdbc.queryForObject("SELECT COMPANYCODE FROM ais_inv_ods.wind_companyfinancial",String.class)).isEqualTo("C002");
    }

    /** 建立某期总资产源数据。 */
    private void insert(Long date,String assets) {
        jdbc.update("INSERT INTO ais_inv_ods.wind_companyfinancial(COMPANYCODE,REPORTDATE,TOT_ASSETS) VALUES('C001',?,?)",date,new BigDecimal(assets));
    }
    /** 获取默认四期。 */
    private List<IssuerFinancialDto> queryDefault() {
        SecurityPoolAdjustReq req = new SecurityPoolAdjustReq(); req.setSecurityCode("B001");
        return service.queryIssuerFinancialList(req);
    }
    /** 获取财报记录数量。 */
    private int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ais_inv_ods.wind_companyfinancial",Integer.class);
    }
    /** 组装批量请求。 */
    private IssuerFinancialSaveReq request(IssuerFinancialDto... changes) {
        IssuerFinancialSaveReq req = new IssuerFinancialSaveReq(); req.setSecurityCode("B001"); req.setRecords(Arrays.asList(changes)); return req;
    }
    /** 组装单个报告期的完整指标。 */
    private IssuerFinancialDto change(Long date,String assets) {
        IssuerFinancialDto dto = new IssuerFinancialDto();
        dto.setReportDate(date);
        dto.setTotAssets(assets == null ? null : new BigDecimal(assets));
        return dto;
    }
    /** 按当前中国标准时间计算测试报告日期，无需注入生产时钟。 */
    private Long reportDate(int yearOffset, int monthDay) {
        return (LocalDate.now(ZoneId.of("Asia/Shanghai")).getYear() + yearOffset) * 10000L + monthDay;
    }
}
