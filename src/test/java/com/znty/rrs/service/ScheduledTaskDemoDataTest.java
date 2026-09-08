package com.znty.rrs.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
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
                new TaskConfig("bond_temp_code_replace", "债券临时代码替换", "0 40 4,8,9,13,18,22 * * ?"),
                new TaskConfig("bond_security_type_change", "债券类型变更", "0 0 22 * * ?"),
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
                new TaskConfig("pledge_blacklist_daily_increment_reminder", "质押黑名单库每日增量提醒", "0 0 9 * * ?"),
                new TaskConfig("bond_issuer_not_in_company_pool_reminder", "主体库内债券主体不在池债邮件提醒", "0 0 9 * * ?"),
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

        // 仅主体不在池债券自动出池任务在说明中强调默认关闭
        Pattern descriptionPattern = Pattern.compile(
                "\\(\\d+,\\s*'([^']+)',\\s*'[^']+',\\s*'(.*?)',\\s*'[^']+',\\s*0,",
                Pattern.DOTALL);
        Matcher descriptionMatcher = descriptionPattern.matcher(sql);
        int descriptionCount = 0;
        while (descriptionMatcher.find()) {
            descriptionCount++;
            String taskCode = descriptionMatcher.group(1);
            String description = descriptionMatcher.group(2);
            if ("company_not_in_pool_bond_auto_out".equals(taskCode)) {
                assertThat(description).contains("默认关闭调度");
            } else {
                assertThat(description).doesNotContain("默认关闭调度");
            }
        }
        assertThat(descriptionCount).isEqualTo(configs.size());
    }

    /** 校验全部 Demo 定时任务的执行实现均输出扫描条件。 */
    @Test
    public void everyScheduledTaskShouldWriteScanCondition() throws Exception {
        Map<String, String> taskSources = new LinkedHashMap<String, String>();
        taskSources.put("bond_temp_code_replace", "BondTempCodeReplaceService.java");
        taskSources.put("bond_security_type_change", "BondSecurityTypeChangeService.java");
        taskSources.put("security_expired_auto_out", "AutoAdjustService.java");
        taskSources.put("crmw_expired_auto_out", "CrmwExpiredAutoOutService.java");
        taskSources.put("company_outer_rating_not_aa_minus_auto_out",
                "CompanyOuterRatingNotAaMinusAutoOutService.java");
        taskSources.put("company_outer_rating_aa_minus_auto_in",
                "CompanyOuterRatingAaMinusAutoInService.java");
        taskSources.put("company_same_pool_bond_auto_in", "CompanySamePoolBondAutoInService.java");
        taskSources.put("company_inpool_bond_auto_in", "CompanyNewBondAutoInService.java");
        taskSources.put("company_not_in_pool_bond_auto_out", "CompanyNotInPoolBondAutoOutService.java");
        taskSources.put("bond_grade_inconformity_alert", "GradeRuleAlertService.java");
        taskSources.put("hs_pool_full_excel_export", "AbstractHsPoolExcelExportService.java");
        taskSources.put("hs_pool_full_including_expired_excel_export", "AbstractHsPoolExcelExportService.java");
        taskSources.put("pledge_blacklist_daily_increment_reminder",
                "PledgeBlacklistDailyIncrementReminderService.java");
        taskSources.put("bond_issuer_not_in_company_pool_reminder",
                "BondIssuerNotInCompanyPoolReminderService.java");
        taskSources.put("hs_pool_increment_excel_export", "AbstractHsPoolExcelExportService.java");
        taskSources.put("wind_code_sync", "WindCodeSyncService.java");

        assertThat(taskSources).hasSize(16);
        for (Map.Entry<String, String> entry : taskSources.entrySet()) {
            String source = new String(Files.readAllBytes(Paths.get("src", "main", "java", "com", "znty",
                    "rrs", "service", entry.getValue())), StandardCharsets.UTF_8);
            assertThat(source).as(entry.getKey() + " 应输出扫描条件").contains("扫描条件：");
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
