package com.znty.rrs.service;

import com.znty.rrs.mapper.AutoAdjustMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 到期出池 SQL 的日期边界和 JDBC 参数绑定测试 */
public class ExpiredAutoOutMapperTest {

    /** 验证截止日期和调出限制参数的绑定，CRMW 限制须按三个组合字段匹配 */
    @Test
    public void expiredQueriesShouldBindOffsetDaysAndKeepExclusiveCutoff() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/AutoAdjustMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        for (String queryId : new String[]{"queryPoolSecurityByExpired", "queryCrmwPoolByExpired"}) {
            Method method = AutoAdjustMapper.class.getMethod(queryId, Long.class, int.class, List.class);
            MappedStatement statement = configuration.getMappedStatement(
                    AutoAdjustMapper.class.getName() + "." + queryId);
            for (int days : new int[]{1, 0, 3, -1}) {
                Object params = new ParamNameResolver(configuration, method)
                        .getNamedParams(new Object[]{15L, days, Collections.emptyList()});
                BoundSql bound = statement.getBoundSql(params);
                assertThat(bound.getSql()).contains(
                        ".maturity_date < DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL ? DAY), '%Y%m%d')")
                        .doesNotContain(".maturity_date <=", "out_restricted");
                PreparedStatement prepared = mock(PreparedStatement.class);
                new DefaultParameterHandler(statement, params, bound).setParameters(prepared);
                verify(prepared).setLong(1, 15L);
                verify(prepared).setInt(2, days);
            }
            Object params = new ParamNameResolver(configuration, method)
                    .getNamedParams(new Object[]{15L, 1, Arrays.asList(23L, 17L)});
            BoundSql bound = statement.getBoundSql(params);
            assertThat(bound.getSql()).contains("SELECT DISTINCT security_code", "AS out_restricted",
                    "restricted.security_code = ips.security_code");
            if ("queryCrmwPoolByExpired".equals(queryId)) {
                assertThat(bound.getSql()).contains("restricted.crmw_scode = ips.crmw_scode",
                        "restricted.crmw_stype = ips.crmw_stype", "pool_type = 'crmw'");
            }
            PreparedStatement prepared = mock(PreparedStatement.class);
            new DefaultParameterHandler(statement, params, bound).setParameters(prepared);
            verify(prepared).setLong(1, 23L);
            verify(prepared).setLong(2, 17L);
            verify(prepared).setLong(3, 15L);
            verify(prepared).setInt(4, 1);
        }
    }
}
