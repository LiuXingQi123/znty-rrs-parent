package com.znty.rrs.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/** 我的事宜管理员可见范围 Mapper SQL 测试。 */
public class MyMattersMapperSqlTest {

    /** 预留管理员 ID 段应与默认管理员一样跳过处理人过滤。 */
    @Test
    public void myMattersQueriesShouldUseSharedAdminUserIdRule() throws Exception {
        Path mapperPath = Paths.get("src", "main", "resources", "mapper", "MyMattersMapper.xml");
        String xml = new String(Files.readAllBytes(mapperPath), StandardCharsets.UTF_8);

        assertThat(xml)
                .contains("@com.znty.rrs.common.util.AdminUserIdUtil@isAdminUser(currentUserId)")
                .doesNotContain("currentUserId != '1'.toString()");
        assertThat(countOccurrences(xml, "@com.znty.rrs.common.util.AdminUserIdUtil@isAdminUser(currentUserId)"))
                .isEqualTo(3);
    }

    /** 统计文本在目标内容中的出现次数。 */
    private int countOccurrences(String text, String target) {
        int count = 0;
        int start = 0;
        while ((start = text.indexOf(target, start)) >= 0) {
            count++;
            start += target.length();
        }
        return count;
    }
}
