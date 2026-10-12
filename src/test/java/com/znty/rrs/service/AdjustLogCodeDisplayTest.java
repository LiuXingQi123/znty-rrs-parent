package com.znty.rrs.service;

import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustReq;
import com.znty.rrs.entity.securitypooladjust.AdjustLogDto;
import com.znty.rrs.entity.securitypooladjust.SecurityPoolAdjustReq;
import com.znty.rrs.mapper.CrmwPoolAdjustMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证数据库日志经 Service 返回页面时保留实际市场代码快照。 */
public class AdjustLogCodeDisplayTest {
    /** 同券历次调整展示各自五码，当前主档缺失也不影响历史展示。 */
    @Test
    public void bondHistoryShouldReturnEachLogsFiveCodes() throws Exception {
        // 使用真实建表结构及 Mapper，覆盖查询投影和 DTO 组装两个环节
        try (SqlSession session = session(); Statement statement = session.getConnection().createStatement()) {
            statement.execute("INSERT INTO ip_adjust_log (id, isin_code, security_type, target_pool_id, is_deleted"
                    + ", wind_code_sh, wind_code_sz, wind_code_nib, wind_code_bj, wind_code_nbc) VALUES"
                    + " (1, 'CNBOND', 'bond', 2, 0, 'OLD.SH', 'OLD.SZ', 'OLD.IB', 'OLD.BJ', 'OLD.OTHER')"
                    + ",(2, 'CNBOND', 'bond', 2, 0, 'NEW.SH', NULL, NULL, NULL, NULL)");
            SecurityPoolAdjustService service = new SecurityPoolAdjustService();
            ReflectionTestUtils.setField(service, "securityPoolAdjustMapper", session.getMapper(SecurityPoolAdjustMapper.class));
            // 提供池名称，避免依赖其他业务表
            ReflectionTestUtils.setField(service, "investmentPoolService", poolService());
            SecurityPoolAdjustReq req = new SecurityPoolAdjustReq();
            req.setIsinCode("CNBOND");
            List<AdjustLogDto> rows = service.queryAdjustLogList(req);
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).getWindCodeSh()).isEqualTo("NEW.SH");
            assertThat(rows.get(0).getWindCodeNib()).isNull();
            AdjustLogDto previous = rows.get(1);
            assertThat(previous.getIsinCode()).isEqualTo("CNBOND");
            assertThat(previous.getWindCodeSh()).isEqualTo("OLD.SH");
            assertThat(previous.getWindCodeSz()).isEqualTo("OLD.SZ");
            assertThat(previous.getWindCodeNib()).isEqualTo("OLD.IB");
            assertThat(previous.getWindCodeBj()).isEqualTo("OLD.BJ");
            assertThat(previous.getWindCodeNbc()).isEqualTo("OLD.OTHER");
        }
    }

    /** CRMW 凭证展示银行间代码，身份仍保留凭证 ISIN。 */
    @Test
    public void crmwHistoryShouldReturnInterbankCodeSnapshot() throws Exception {
        // 使用真实查询和返回 DTO，防止查询有字段但组装时遗漏
        try (SqlSession session = session(); Statement statement = session.getConnection().createStatement()) {
            statement.execute("INSERT INTO ip_adjust_log (id, isin_code, crmw_isin_code, crmw_scode"
                    + ", crmw_stype, target_pool_id, pool_type, is_deleted)"
                    + " VALUES (1, 'CNBOND', 'CNCRMW', 'OLDCRMW.IB', 'crmw', 2, 'crmw', 0)");
            CrmwPoolAdjustService service = new CrmwPoolAdjustService();
            ReflectionTestUtils.setField(service, "crmwPoolAdjustMapper", session.getMapper(CrmwPoolAdjustMapper.class));
            // 提供池名称，避免依赖其他业务表
            ReflectionTestUtils.setField(service, "investmentPoolService", poolService());
            CrmwPoolAdjustReq req = new CrmwPoolAdjustReq();
            req.setIsinCode("CNBOND");
            req.setCrmwIsinCode("CNCRMW");
            req.setCrmwStype("crmw");
            assertThat(service.queryCrmwAdjustLogList(req)).singleElement().satisfies(row -> {
                assertThat(row.getCrmwIsinCode()).isEqualTo("CNCRMW");
                assertThat(row.getCrmwScode()).isEqualTo("OLDCRMW.IB");
            });
        }
    }

    /** 提供只用于记录展示的池名称。 */
    private InvestmentPoolService poolService() {
        InvestmentPoolService service = mock(InvestmentPoolService.class);
        when(service.queryPoolFullNameMap()).thenReturn(Collections.singletonMap(2L, "test pool"));
        return service;
    }

    /** 创建独立内存数据库，直接加载当前日志建表结构。 */
    private SqlSession session() throws Exception {
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:log_codes_" + UUID.randomUUID()
                        + ";MODE=MySQL", "sa", "")));
        configuration.setMapUnderscoreToCamelCase(true);
        for (String mapper : new String[] {"SecurityPoolAdjustMapper", "CrmwPoolAdjustMapper"}) {
            String resource = "mapper/" + mapper + ".xml";
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        String schema = new String(Files.readAllBytes(Paths.get("sql/rrs_security_pool_adjust_schema.sql")), StandardCharsets.UTF_8);
        int start = schema.indexOf("CREATE TABLE `ip_adjust_log`");
        int end = schema.indexOf("ENGINE", start);
        String ddl = schema.substring(start, end).trim();
        try (Statement statement = session.getConnection().createStatement()) {
            statement.execute(ddl);
            statement.execute("CREATE TABLE wf_flow_definition (id BIGINT, name VARCHAR(100))");
        }
        return session;
    }
}
