package com.znty.rrs.service;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用 H2 验证四条调库链路原因和意见的实际条件更新。 */
public class AdjustLogReasonAdviceMapperTest {
    /** 修改当前批次时保留未传字段，并保护其他批次、已通过和已删除记录。 */
    @Test
    public void updatesShouldRespectBatchStatusAndOptionalFields() throws Exception {
        for (String module : new String[] {"Security", "Forbidden", "Crmw", "Fund"}) {
            String mapperName = module + "PoolAdjustMapper";
            String tableName = "Fund".equals(module) ? "ip_adjust_log_fund" : "ip_adjust_log";
            Configuration configuration = new Configuration(new Environment("test",
                    new JdbcTransactionFactory(), new UnpooledDataSource("org.h2.Driver",
                    "jdbc:h2:mem:reason_advice_" + module + ";MODE=MySQL", "sa", "")));
            String resource = "mapper/" + mapperName + ".xml";
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
            try (SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession(true)) {
                Connection connection = session.getConnection();
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE " + tableName + " (id BIGINT PRIMARY KEY"
                            + ", adjust_batch_no VARCHAR(50), audit_status VARCHAR(2), is_deleted INT"
                            + ", adjust_reason VARCHAR(1000), adjust_advice VARCHAR(1000), updt_time TIMESTAMP)");
                    statement.execute("INSERT INTO " + tableName + " VALUES"
                            + " (1, 'CURRENT', '11', 0, 'old', 'keep', NULL)"
                            + ",(2, 'CURRENT', '11', 0, 'old', 'keep', NULL)"
                            + ",(3, 'OTHER', '11', 0, 'old', 'keep', NULL)"
                            + ",(4, 'CURRENT', '20', 0, 'old', 'keep', NULL)"
                            + ",(5, 'CURRENT', '11', 1, 'old', 'keep', NULL)");
                }
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("adjustLogId", 1L);
                parameters.put("adjustBatchNo", "CURRENT");
                parameters.put("adjustReason", "new");
                parameters.put("adjustAdvice", null);
                String updateId = "com.znty.rrs.mapper." + mapperName + ".editAdjustLogReasonAdvice";
                assertThat(session.update(updateId, parameters)).as(module + " 更新当前批次").isEqualTo(2);
                // 核对未传意见保留原值以及其他记录没有被更新
                assertTexts(connection, tableName, "new", "keep");

                parameters.put("adjustReason", null);
                parameters.put("adjustAdvice", "");
                assertThat(session.update(updateId, parameters)).as(module + " 清空意见").isEqualTo(2);
                // 核对空字符串清空意见，未传原因保留更新后的内容
                assertTexts(connection, tableName, "new", "");

                if (!"Fund".equals(module)) {
                    parameters.put("adjustBatchNo", null);
                    parameters.put("adjustLogId", 2L);
                    assertThat(session.update(updateId, parameters)).as(module + " 按单条日志更新").isEqualTo(1);
                }
            }
        }
    }

    /** 核对当前批次和受保护记录的原因、意见。 */
    private void assertTexts(Connection connection, String tableName, String reason, String advice) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, adjust_reason, adjust_advice FROM " + tableName + " ORDER BY id")) {
            int count = 0;
            while (rows.next()) {
                boolean editable = rows.getLong("id") <= 2;
                assertThat(rows.getString("adjust_reason")).isEqualTo(editable ? reason : "old");
                assertThat(rows.getString("adjust_advice")).isEqualTo(editable ? advice : "keep");
                count++;
            }
            assertThat(count).isEqualTo(5);
        }
    }
}
