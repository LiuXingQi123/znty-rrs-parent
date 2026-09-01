package com.znty.rrs.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 定时任务 Demo 数据契约测试 */
public class ScheduledTaskDemoDataTest {

    /** 校验任务顺序、名称、Cron 与默认关闭状态 */
    @Test
    public void demoTasksShouldMatchConfirmedSchedule() throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get("sql", "rrs_scheduled_task_demo_data.sql")),
                StandardCharsets.UTF_8);
        List<TaskConfig> configs = Arrays.asList(
                new TaskConfig("security_expired_auto_out", "到期证券自动出池", "0 0 23 * * ?"),
                new TaskConfig("crmw_expired_auto_out", "CRMW到期自动出池", "0 5 23 * * ?"),
                new TaskConfig("company_outer_rating_not_aa_minus_auto_out", "外评非AA-及以下主体自动出池", "0 10 23 * * ?"),
                new TaskConfig("company_outer_rating_aa_minus_auto_in", "外评AA-及以下主体自动入池", "0 15 23 * * ?"),
                new TaskConfig("company_same_pool_bond_auto_in", "主体下债券自动入库", "0 20 23 * * ?"),
                new TaskConfig("company_inpool_bond_auto_in", "在池主体旗下债券自动入池", "0 */10 * * * ?"),
                new TaskConfig("company_not_in_pool_bond_auto_out", "主体不在池债券自动出池(默认关闭)", "0 0 0 * * ?"),
                new TaskConfig("bond_grade_inconformity_alert", "不符合主体债入库规则提醒", "0 0 1 * * ?"),
                new TaskConfig("hs_pool_full_excel_export", "恒生池全量数据导出（不含已到期）", "0 10 1 * * ?"),
                new TaskConfig("hs_pool_full_including_expired_excel_export", "恒生池全量数据导出（含已到期）", "0 20 1 * * ?"),
                new TaskConfig("hs_pool_increment_excel_export", "恒生池增量数据导出", "0 */6 * * * ?"),
                new TaskConfig("wind_code_sync", "Wind代码变更同步", "0 */10 * * * ?")
        );

        int previousIndex = -1;
        for (TaskConfig config : configs) {
            int currentIndex = sql.indexOf("'" + config.taskCode + "'");
            assertThat(currentIndex).as(config.taskCode + " 应存在且顺序正确").isGreaterThan(previousIndex);
            previousIndex = currentIndex;

            String taskPattern = "\\(\\d+,\\s*'" + Pattern.quote(config.taskCode)
                    + "',\\s*'" + Pattern.quote(config.taskName)
                    + "',.*?'" + Pattern.quote(config.cronExpression) + "',\\s*0,";
            assertThat(Pattern.compile(taskPattern, Pattern.DOTALL).matcher(sql).find())
                    .as(config.taskCode + " 的名称、Cron 和默认关闭状态应一致")
                    .isTrue();
        }
    }

    /** Demo 任务配置 */
    private static class TaskConfig {
        /** 任务编码 */
        private final String taskCode;
        /** 任务名称 */
        private final String taskName;
        /** Cron 表达式 */
        private final String cronExpression;

        /** 创建 Demo 任务配置 */
        private TaskConfig(String taskCode, String taskName, String cronExpression) {
            this.taskCode = taskCode;
            this.taskName = taskName;
            this.cronExpression = cronExpression;
        }
    }
}
