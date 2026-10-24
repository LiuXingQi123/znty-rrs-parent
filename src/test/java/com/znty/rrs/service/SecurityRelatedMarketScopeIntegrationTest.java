package com.znty.rrs.service;

import com.znty.rrs.entity.common.SecurityMarketDto;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckReq;
import com.znty.rrs.entity.securitypooladjust.AdjustSharedData;
import com.znty.rrs.entity.forbiddenabspooladjust.ForbiddenAbsPoolAdjustCheckReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.ForbiddenAbsPoolAdjustMapper;
import com.znty.rrs.mapper.SecurityMarketMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 使用真实同券 SQL 验证共用市场筛选先于主档校验。 */
public class SecurityRelatedMarketScopeIntegrationTest {
    /** 内存数据库访问组件。 */
    private JdbcTemplate jdbc;
    /** 当前测试数据库会话。 */
    private SqlSession session;
    /** 使用真实 Mapper 的市场查询服务。 */
    private SecurityMarketService markets;

    /** 建立同 ISIN 的沪、深、银行间及范围外代码；范围外代码刻意不建主档。 */
    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("H2 需要 Java 11 以上", !System.getProperty("java.specification.version").startsWith("1."));
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:market_scope_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE rrs_securityinfo (wind_code VARCHAR(100), security_source VARCHAR(32), market VARCHAR(10))");
        jdbc.execute("CREATE TABLE rrs_temp_security_code (temp_security_code VARCHAR(100), temp_security_market VARCHAR(10))");
        jdbc.execute("CREATE SCHEMA ais_inv_ods");
        jdbc.execute("CREATE TABLE ais_inv_ods.wind_windcustomcode (S_INFO_WINDCODE VARCHAR(100), S_INFO_ISINCODE VARCHAR(40), EXCHMARKET VARCHAR(10), S_INFO_EXCHMARKET VARCHAR(10))");
        jdbc.execute("INSERT INTO rrs_securityinfo VALUES ('A.SH', 'official', 'SSE'), ('A.IB', 'official', 'NIB'), ('A.SZ', 'official', 'SZSE')");
        jdbc.execute("INSERT INTO ais_inv_ods.wind_windcustomcode VALUES ('A.SH', 'SAME', 'SSE', 'SSE'), ('A.IB', 'SAME', 'NIB', 'IB'), ('A.SZ', 'SAME', 'SZSE', 'SZSE'), ('A.BC', 'SAME', 'NBC', 'NBC')");
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream xml = Files.newInputStream(Paths.get("src/main/resources/mapper/SecurityMarketMapper.xml"))) {
            new XMLMapperBuilder(xml, configuration, "SecurityMarketMapper", configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        markets = new SecurityMarketService();
        ReflectionTestUtils.setField(markets, "securityMarketMapper", session.getMapper(SecurityMarketMapper.class));
    }

    /** 关闭测试会话。 */
    @After
    public void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    /** 范围外缺主档不影响允许市场，关联关系从每个允许市场查询均一致。 */
    @Test
    public void excludedMissingMasterShouldNotBlockAllowedMarkets() {
        assertThat(markets.queryRelatedMarketCodes("A.SH")).extracting(SecurityMarketDto::getWindCode)
                .containsExactly("A.IB", "A.SZ");
        assertThat(markets.queryRelatedMarketCodes("A.IB")).extracting(SecurityMarketDto::getWindCode)
                .containsExactly("A.SH", "A.SZ");
        assertThat(markets.queryRelatedMarketCodes("A.SZ")).extracting(SecurityMarketDto::getWindCode)
                .containsExactly("A.IB", "A.SH");
    }

    /** 范围内缺主档仍明确报错，不能通过有无主档决定是否忽略关联券。 */
    @Test
    public void includedMissingMasterShouldStillFail() {
        jdbc.update("DELETE FROM rrs_securityinfo WHERE wind_code = 'A.SZ'");
        assertThatThrownBy(() -> markets.queryRelatedMarketCodes("A.SH"))
                .isInstanceOf(BizException.class).hasMessage("证券缺少市场映射：A.SZ");
    }

    /** 范围外已有主档也不参与关联，主代码在范围外时同样不反向关联。 */
    @Test
    public void excludedMarketShouldRemainUnrelatedEvenWithMaster() {
        jdbc.update("INSERT INTO rrs_securityinfo VALUES ('A.BC', 'official', 'OTHER')");
        assertThat(markets.queryRelatedMarketCodes("A.SH")).extracting(SecurityMarketDto::getWindCode)
                .doesNotContain("A.BC");
        assertThat(markets.queryRelatedMarketCodes("A.BC")).isEmpty();
        assertThat(markets.queryMarketCode("A.BC")).isEqualTo("OTHER");
    }

    /** 默认范围包含投资池全部市场，按 EXCHMARKET 而非后缀或另一个 Wind 市场字段筛选。 */
    @Test
    public void sharedPoolMarketsShouldDetermineScopeInsteadOfSuffix() {
        jdbc.update("INSERT INTO rrs_securityinfo VALUES ('ALIAS.BC', 'official', 'OTC')");
        jdbc.update("INSERT INTO ais_inv_ods.wind_windcustomcode VALUES ('ALIAS.BC', 'SAME', 'OTC', 'NBC')");
        assertThat(markets.queryRelatedMarketCodes("A.SH")).extracting(SecurityMarketDto::getWindCode)
                .contains("ALIAS.BC");
        assertThat(session.getMapper(SecurityMarketMapper.class)
                .queryRelatedWindCodeList("A.SH", new CommonService().queryMarketCodeList()))
                .contains("A.IB", "A.SZ", "ALIAS.BC").doesNotContain("A.BC");
    }

    /** 同一允许市场的多个代码完整保留，不按市场去重。 */
    @Test
    public void multipleCodesInSameAllowedMarketShouldRemainRelated() {
        jdbc.update("INSERT INTO rrs_securityinfo VALUES ('A2.SH', 'official', 'SSE')");
        jdbc.update("INSERT INTO ais_inv_ods.wind_windcustomcode VALUES ('A2.SH', 'SAME', 'SSE', 'SSE')");
        assertThat(markets.queryRelatedMarketCodes("A.IB")).extracting(SecurityMarketDto::getWindCode)
                .containsExactly("A.SH", "A.SZ", "A2.SH");
    }

    /** 普通证券和 ABS 的调入、调出扩批均只生成范围内代码，不为范围外缺主档代码生成操作。 */
    @Test
    public void inboundAndOutboundShouldExpandOnlyIncludedCodes() {
        for (boolean abs : new boolean[]{false, true}) {
            for (String mode : Arrays.asList("调入", "调出")) {
                Object service = abs ? new ForbiddenAbsPoolAdjustService() : new SecurityPoolAdjustService();
                Object mapper = abs ? mock(ForbiddenAbsPoolAdjustMapper.class) : mock(SecurityPoolAdjustMapper.class);
                ReflectionTestUtils.setField(service, abs ? "forbiddenAbsPoolAdjustMapper" : "securityPoolAdjustMapper", mapper);
                ReflectionTestUtils.setField(service, "securityMarketService", markets);
                ReflectionTestUtils.setField(service, "ratingDowngradeChecker", mock(RatingDowngradeChecker.class));
                for (String code : Arrays.asList("A.IB", "A.SZ")) {
                    // 关联券的业务数据使用独立主档，市场及同券集合使用真实 SQL
                    stub(mapper, "querySecurityBoByCode", security(code), code);
                    // 调出场景准备关联券各自的在池状态
                    stub(mapper, "querySecurityCurrentPoolIdList", "调出".equals(mode)
                            ? Collections.singletonList(4L) : Collections.emptyList(), code);
                }
                // 复用原有债券业务规则，不限制市场和评级矩阵
                stub(mapper, "queryCategoryTypeBySecurityType", "bond", "mtn");
                InvestmentPoolBo pool = new InvestmentPoolBo();
                pool.setId(4L);
                pool.setPoolName("三级库");
                pool.setPoolType("credit_bond");
                pool.setInnerSort(3);
                pool.setVarietyCodes("[\"bond\"]");
                AdjustSharedData shared = new AdjustSharedData();
                // 构造已完成主券前置校验的扩批上下文
                shared.setSecurityInfo(security("A.SH"));
                shared.setPoolMap(Collections.singletonMap(4L, pool));
                shared.setPoolRelationMap(Collections.emptyMap());
                shared.setCurrentPoolIds(Collections.emptySet());
                shared.setPendingPoolGroupIds(Collections.emptySet());
                shared.setRequestInPoolIds(Collections.emptySet());
                shared.setRequestOutPoolIds(Collections.emptySet());
                shared.setReleaseRules(true);
                AdjustCheckDto.CheckResultItem main = new AdjustCheckDto.CheckResultItem();
                main.setTargetPoolId(4L);
                main.setPoolName("三级库");
                main.setPoolType("credit_bond");
                main.setAdjustMode(mode);
                main.setItemTag("manual");
                main.setAdjustGroupKey("manual_4");
                main.setCanAdjust(true);
                main.setFailReasons(Collections.emptyList());
                List<AdjustCheckDto.CheckResultItem> items = new ArrayList<>();
                items.add(main);
                Object req = abs ? new ForbiddenAbsPoolAdjustCheckReq() : new AdjustCheckReq();
                ReflectionTestUtils.setField(req, "securityCode", "A.SH");
                ReflectionTestUtils.invokeMethod(service, "expandRelatedMarketCodeItems", req, shared, items);
                assertThat(items).extracting(AdjustCheckDto.CheckResultItem::getSecurityCode)
                        .containsExactly("A.SH", "A.IB", "A.SZ");
                assertThat(items).allMatch(AdjustCheckDto.CheckResultItem::isCanAdjust);
            }
        }
    }

    /** 构造关联券业务主档。 */
    private SecurityInfoBo security(String code) {
        SecurityInfoBo security = new SecurityInfoBo();
        security.setWindCode(code);
        security.setShortName(code);
        security.setSecurityType("mtn");
        return security;
    }

    /** 给两条同构链路配置相同业务查询结果。 */
    private <T> void stub(Object mapper, String method, T result, Object... args) {
        when(ReflectionTestUtils.<T>invokeMethod(mapper, method, args)).thenReturn(result);
    }
}
