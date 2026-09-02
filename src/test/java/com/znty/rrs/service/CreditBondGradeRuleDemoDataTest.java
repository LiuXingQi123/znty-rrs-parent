package com.znty.rrs.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 主体内评分档规则 Demo 数据契约测试 */
public class CreditBondGradeRuleDemoDataTest {

    /** 校验关系 ID 连续，且 3≥期限>1 × 3+ 对应三级库至五级库 */
    @Test
    public void demoRulesShouldKeepContinuousIdsAndExpectedGradeRule() throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get("sql", "rrs_credit_bond_grade_rule_demo_data.sql")),
                StandardCharsets.UTF_8);
        Pattern rulePattern = Pattern.compile(
                "(?m)^\\((\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*'credit_bond_level_[1-5]',\\s*'[^']+',\\s*1,\\s*(\\d+),");
        Matcher matcher = rulePattern.matcher(sql);
        List<Integer> ids = new ArrayList<Integer>();
        List<Integer> targetPoolIds = new ArrayList<Integer>();
        List<Integer> targetSortNos = new ArrayList<Integer>();

        while (matcher.find()) {
            ids.add(Integer.valueOf(matcher.group(1)));
            int termBucketId = Integer.parseInt(matcher.group(2));
            int innerRatingGradeId = Integer.parseInt(matcher.group(3));
            if (termBucketId == 3 && innerRatingGradeId == 5) {
                targetPoolIds.add(Integer.valueOf(matcher.group(4)));
                targetSortNos.add(Integer.valueOf(matcher.group(5)));
            }
        }

        // 构造期望的连续关系 ID
        assertThat(ids).containsExactly(buildContinuousIds(40).toArray(new Integer[0]));
        assertThat(targetPoolIds).containsExactly(4, 5, 6);
        assertThat(targetSortNos).containsExactly(1, 2, 3);
    }

    /** 构造从 1 开始的连续 ID 列表 */
    private List<Integer> buildContinuousIds(int count) {
        List<Integer> ids = new ArrayList<Integer>();
        for (int id = 1; id <= count; id++) {
            ids.add(Integer.valueOf(id));
        }
        return ids;
    }
}
