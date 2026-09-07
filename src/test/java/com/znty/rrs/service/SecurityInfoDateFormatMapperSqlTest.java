package com.znty.rrs.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 证券主数据紧凑日期格式 SQL 测试。 */
public class SecurityInfoDateFormatMapperSqlTest {

    /** 验证 maturity_date 的数据库日期比较统一使用 yyyyMMdd。 */
    @Test
    public void maturityDatePredicatesShouldUseCompactDateFormat() throws Exception {
        Path mapperDirectory = Paths.get("src", "main", "resources", "mapper");
        try (Stream<Path> mapperPaths = Files.list(mapperDirectory)) {
            // 逐个检查所有 Mapper，避免遗漏其他业务入口
            mapperPaths.filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .forEach(this::assertMaturityDateFormat);
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

    /**
     * 校验单个 Mapper 中涉及 maturity_date 的日期表达式。
     *
     * @param mapperPath Mapper XML 路径
     */
    private void assertMaturityDateFormat(Path mapperPath) {
        try {
            String xml = new String(Files.readAllBytes(mapperPath), StandardCharsets.UTF_8);
            for (String line : xml.split("\\r?\\n")) {
                String normalized = line.toLowerCase();
                if (!normalized.contains("maturity_date")
                        || (!normalized.contains("curdate")
                        && !normalized.contains("now()")
                        && !normalized.contains("date_format"))) {
                    continue;
                }
                assertThat(line)
                        .as(mapperPath.getFileName() + " 的 maturity_date 日期格式")
                        .contains("%Y%m%d")
                        .doesNotContain("%Y-%m-%d");
            }
        } catch (Exception ex) {
            throw new AssertionError("读取 Mapper 失败：" + mapperPath, ex);
        }
    }
}
