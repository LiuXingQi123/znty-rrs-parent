package com.znty.rrs.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Wind 发行主体 Mapper SQL 口径测试 */
public class WindCbondIssuerMapperSqlTest {

    /** 校验读取 wind_cbondissuer 时不再按 used=1 过滤 */
    @Test
    public void windCbondIssuerQueriesShouldNotFilterUsed() throws Exception {
        List<String> mapperFiles = Arrays.asList(
                "ForbiddenPoolAdjustMapper.xml",
                "ForbiddenPoolHistoryMapper.xml",
                "ForbiddenPoolQueryMapper.xml",
                "TempSecurityCodeMapper.xml"
        );
        Pattern usedFilterPattern = Pattern.compile("(?i)\\b(?:\\w+\\.)?used\\s*=\\s*'?1'?");

        for (String mapperFile : mapperFiles) {
            Path mapperPath = Paths.get("src", "main", "resources", "mapper", mapperFile);
            String xml = new String(Files.readAllBytes(mapperPath), StandardCharsets.UTF_8);
            assertThat(xml).as(mapperFile + " 应查询 wind_cbondissuer").contains("ais_inv_ods.wind_cbondissuer");
            assertThat(usedFilterPattern.matcher(xml).find())
                    .as(mapperFile + " 不应保留 used=1 过滤")
                    .isFalse();
        }
    }
}
