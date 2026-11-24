package com.znty.rrs.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/** 证券主数据紧凑日期格式 SQL 测试。 */
public class SecurityInfoDateFormatMapperSqlTest {

    /** 验证证券主档的摘牌日比较使用 yyyyMMdd */
    @Test
    public void delistDatePredicatesShouldUseCompactDateFormat() throws Exception {
        Path mapperDirectory = Paths.get("src", "main", "resources", "mapper");
        for (String name : new String[]{"AutoAdjustMapper.xml", "BondReminderMapper.xml", "HsPoolExcelExportMapper.xml"}) {
            assertDelistDateFormat(mapperDirectory.resolve(name));
        }
    }

    /** 验证临时代码同步证券主数据时使用 yyyyMMdd。 */
    @Test
    public void tempSecurityCodeMapperShouldWriteCompactDateFormat() throws Exception {
        Path mapperPath = Paths.get("src", "main", "resources", "mapper", "TempSecurityCodeMapper.xml");
        String xml = new String(Files.readAllBytes(mapperPath), StandardCharsets.UTF_8);

        assertThat(xml).contains("DATE_FORMAT(#{tempIssueDate}, '%Y%m%d')");
        assertThat(xml).contains("DATE_FORMAT(#{tempMaturityDate}, '%Y%m%d')");
        assertThat(xml).doesNotContain("DATE_FORMAT(#{tempIssueDate}, '%Y-%m-%d')");
        assertThat(xml).doesNotContain("DATE_FORMAT(#{tempMaturityDate}, '%Y-%m-%d')");
    }

    /** 质押黑名单增量继续保留空摘牌日，并排除摘牌日当天 */
    @Test
    public void blacklistReminderShouldKeepExclusiveDelistDateBoundary() throws Exception {
        Path mapperPath = Paths.get("src", "main", "resources", "mapper", "BondReminderMapper.xml");
        String xml = new String(Files.readAllBytes(mapperPath), StandardCharsets.UTF_8);
        assertThat(xml)
                .contains("si.delist_date IS NULL", "si.delist_date > DATE_FORMAT(CURDATE(), '%Y%m%d')")
                .doesNotContain("si.maturity_date", "si.security_status");
    }

    /**
     * 校验单个 Mapper 中涉及 delist_date 的日期表达式。
     *
     * @param mapperPath Mapper XML 路径
     */
    private void assertDelistDateFormat(Path mapperPath) {
        try {
            String xml = new String(Files.readAllBytes(mapperPath), StandardCharsets.UTF_8);
            for (String line : xml.split("\\r?\\n")) {
                String normalized = line.toLowerCase();
                if (!normalized.contains("delist_date")
                        || (!normalized.contains("curdate")
                        && !normalized.contains("now()")
                        && !normalized.contains("date_format"))) {
                    continue;
                }
                assertThat(line)
                        .as(mapperPath.getFileName() + " 的 delist_date 日期格式")
                        .contains("%Y%m%d")
                        .doesNotContain("%Y-%m-%d");
            }
        } catch (Exception ex) {
            throw new AssertionError("读取 Mapper 失败：" + mapperPath, ex);
        }
    }
}
