package com.znty.rrs.service;

import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustDto;
import com.znty.rrs.mapper.ForbiddenPoolAdjustMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Test;

import java.io.InputStream;
import java.sql.Date;
import java.sql.Statement;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 Mapper 验证主体旗下互斥调出债券的五码映射。 */
public class CompanyBondMutexCodeMapperTest {

    /** 不同债券保留自身代码，空市场代码不填充，重复在池记录不产生重复债券行。 */
    @Test
    public void mutexBondsShouldReturnOwnMarketCodes() throws Exception {
        // 使用独立内存库加载真实 Mapper
        try (SqlSession session = session();
             Statement statement = session.getConnection().createStatement()) {
            // 为当前查询提供 MySQL 日期函数和最小依赖表，不连接业务数据库
            statement.execute("CREATE ALIAS DATE_FORMAT FOR '"
                    + CompanyBondMutexCodeMapperTest.class.getName() + ".dateFormat'");
            statement.execute("CREATE TABLE rrs_securityinfo (isin_code VARCHAR(100), short_name VARCHAR(100)"
                    + ", security_type VARCHAR(50), issuer_code VARCHAR(100), security_status VARCHAR(10)"
                    + ", maturity_date VARCHAR(8), abs_flag INT, wind_code_sh VARCHAR(100)"
                    + ", wind_code_sz VARCHAR(100), wind_code_nib VARCHAR(100), wind_code_bj VARCHAR(100)"
                    + ", wind_code_nbc VARCHAR(100))");
            statement.execute("CREATE TABLE dict_security_type (security_type VARCHAR(50), is_deleted INT, category_type VARCHAR(20))");
            statement.execute("CREATE TABLE ip_pool_status (isin_code VARCHAR(100), target_pool_id BIGINT, audit_status VARCHAR(10), is_deleted INT)");
            statement.execute("INSERT INTO dict_security_type VALUES ('abs', 0, 'bond')");
            statement.execute("INSERT INTO rrs_securityinfo VALUES"
                    + " ('CN001', 'ABS1', 'abs', 'C10008', 'L', '20991231', 1"
                    + ", '184901.SH', '149901.SZ', '102480001.IB', '830001.BJ', 'XS2400000001')"
                    + ",('TEMP:TMP001', 'ABS2', 'abs', 'C10008', 'L', '20991231', 1"
                    + ", NULL, NULL, 'TMP001', NULL, NULL)");
            statement.execute("INSERT INTO ip_pool_status VALUES ('CN001', 3, '20', 0)"
                    + ",('CN001', 3, '20', 0),('TEMP:TMP001', 3, '20', 0)");

            List<ForbiddenPoolAdjustDto.CompanyBond> bonds = session.getMapper(ForbiddenPoolAdjustMapper.class)
                    .queryCompanyBondMutexOutList("C10008", 15L, Collections.singletonList(3L),
                            CompanyBondSyncPolicy.currentTypeScope());

            assertThat(bonds).hasSize(2);
            ForbiddenPoolAdjustDto.CompanyBond official = bonds.get(0);
            assertThat(official.getIsinCode()).isEqualTo("CN001");
            assertThat(official.getWindCodeSh()).isEqualTo("184901.SH");
            assertThat(official.getWindCodeSz()).isEqualTo("149901.SZ");
            assertThat(official.getWindCodeNib()).isEqualTo("102480001.IB");
            assertThat(official.getWindCodeBj()).isEqualTo("830001.BJ");
            assertThat(official.getWindCodeNbc()).isEqualTo("XS2400000001");
            ForbiddenPoolAdjustDto.CompanyBond temporary = bonds.get(1);
            assertThat(temporary.getIsinCode()).isEqualTo("TEMP:TMP001");
            assertThat(temporary.getWindCodeNib()).isEqualTo("TMP001");
            assertThat(temporary.getWindCodeSh()).isNull();
            assertThat(temporary.getWindCodeSz()).isNull();
            assertThat(temporary.getWindCodeBj()).isNull();
            assertThat(temporary.getWindCodeNbc()).isNull();
        }
    }

    /** 旗下明细的普通池和 CRMW 池查询均返回主档五码，CRMW 只显示银行间代码。 */
    @Test
    public void companyBondDetailsShouldReturnCodesFromBothPoolSources() throws Exception {
        // 同时建立两类池状态，验证 UNION 两个分支及外层投影
        try (SqlSession session = session(); Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS DATE_FORMAT FOR '"
                    + CompanyBondMutexCodeMapperTest.class.getName() + ".dateFormat'");
            statement.execute("CREATE TABLE rrs_securityinfo (isin_code VARCHAR(100), short_name VARCHAR(100)"
                    + ", full_name VARCHAR(100), security_type VARCHAR(50), issuer_code VARCHAR(100)"
                    + ", wind_code_sh VARCHAR(100), wind_code_sz VARCHAR(100), wind_code_nib VARCHAR(100)"
                    + ", wind_code_bj VARCHAR(100), wind_code_nbc VARCHAR(100))");
            statement.execute("CREATE TABLE dict_security_type (security_type VARCHAR(50), security_type_name VARCHAR(100), is_deleted INT, category_type VARCHAR(20))");
            statement.execute("CREATE TABLE ip_investment_pool (id BIGINT, pool_name VARCHAR(100), is_deleted INT)");
            statement.execute("CREATE TABLE ip_pool_status (isin_code VARCHAR(100), target_pool_id BIGINT, audit_status VARCHAR(10), is_deleted INT, entry_time TIMESTAMP)");
            statement.execute("CREATE TABLE ip_pool_status_crmw (crmw_isin_code VARCHAR(100), target_pool_id BIGINT, audit_status VARCHAR(10), is_deleted INT, entry_time TIMESTAMP)");
            statement.execute("INSERT INTO dict_security_type VALUES ('abs', 'ABS', 0, 'bond'),('crmw', 'CRMW', 0, 'bond')");
            statement.execute("INSERT INTO ip_investment_pool VALUES (3, 'Credit', 0),(18, 'CRMW', 0)");
            statement.execute("INSERT INTO rrs_securityinfo VALUES"
                    + " ('CN001', 'ABS1', 'ABS1 FULL', 'abs', 'C10008'"
                    + ", '184901.SH', '149901.SZ', '102480001.IB', '830001.BJ', 'XS2400000001')"
                    + ",('CNCRMW', 'CRMW1', 'CRMW1 FULL', 'crmw', 'C10008'"
                    + ", NULL, NULL, '032400101.IB', NULL, NULL)");
            statement.execute("INSERT INTO ip_pool_status VALUES ('CN001', 3, '20', 0, TIMESTAMP '2026-03-03 12:00:00')");
            statement.execute("INSERT INTO ip_pool_status_crmw VALUES ('CNCRMW', 18, '20', 0, TIMESTAMP '2026-03-04 12:00:00')");
            ForbiddenPoolAdjustMapper mapper = session.getMapper(ForbiddenPoolAdjustMapper.class);

            assertThat(mapper.queryCompanyBondList("C10008", 3L)).singleElement().satisfies(bond -> {
                assertThat(bond.getIsinCode()).isEqualTo("CN001");
                assertThat(bond.getWindCodeSh()).isEqualTo("184901.SH");
                assertThat(bond.getWindCodeSz()).isEqualTo("149901.SZ");
                assertThat(bond.getWindCodeNib()).isEqualTo("102480001.IB");
                assertThat(bond.getWindCodeBj()).isEqualTo("830001.BJ");
                assertThat(bond.getWindCodeNbc()).isEqualTo("XS2400000001");
                assertThat(bond.getEntryDate()).isEqualTo("2026-03-03");
            });
            assertThat(mapper.queryCompanyBondList("C10008", 18L)).singleElement().satisfies(bond -> {
                assertThat(bond.getIsinCode()).isEqualTo("CNCRMW");
                assertThat(bond.getWindCodeNib()).isEqualTo("032400101.IB");
                assertThat(bond.getWindCodeSh()).isNull();
                assertThat(bond.getWindCodeSz()).isNull();
                assertThat(bond.getWindCodeBj()).isNull();
                assertThat(bond.getWindCodeNbc()).isNull();
                assertThat(bond.getEntryDate()).isEqualTo("2026-03-04");
            });
        }
    }

    /** 为每个测试加载真实 Mapper，确保不复用其他测试的表和数据。 */
    private SqlSession session() throws Exception {
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:mutex_codes_" + UUID.randomUUID()
                        + ";MODE=MySQL", "sa", "")));
        config.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/ForbiddenPoolAdjustMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        return new SqlSessionFactoryBuilder().build(config).openSession(true);
    }

    /** H2 测试用 MySQL 日期格式函数，仅支持被测查询使用的年月日格式。 */
    public static String dateFormat(Date date, String pattern) {
        if ("%Y-%m-%d".equals(pattern)) return date.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (!"%Y%m%d".equals(pattern)) throw new IllegalArgumentException("不支持的日期格式：" + pattern);
        return date.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
