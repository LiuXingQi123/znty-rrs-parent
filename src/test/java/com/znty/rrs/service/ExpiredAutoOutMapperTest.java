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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 到期出池 SQL 的日期边界和 JDBC 参数绑定测试 */
public class ExpiredAutoOutMapperTest {

    /** 两个任务都按数据库当前日期减去配置天数，严格排除截止当天 */
    @Test
    public void expiredQueriesShouldBindOffsetDaysAndKeepExclusiveCutoff() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/AutoAdjustMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        for (String queryId : new String[]{"queryPoolSecurityByExpired", "queryCrmwPoolByExpired"}) {
            Method method = AutoAdjustMapper.class.getMethod(queryId, Long.class, int.class);
            MappedStatement statement = configuration.getMappedStatement(
                    AutoAdjustMapper.class.getName() + "." + queryId);
            for (int days : new int[]{1, 0, 3, -1}) {
                Object params = new ParamNameResolver(configuration, method)
                        .getNamedParams(new Object[]{15L, days});
                BoundSql bound = statement.getBoundSql(params);
                assertThat(bound.getSql()).contains(
                        ".maturity_date < DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL ? DAY), '%Y%m%d')")
                        .doesNotContain(".maturity_date <=");
                PreparedStatement prepared = mock(PreparedStatement.class);
                new DefaultParameterHandler(statement, params, bound).setParameters(prepared);
                verify(prepared).setLong(1, 15L);
                verify(prepared).setInt(2, days);
            }
        }
    }
}
