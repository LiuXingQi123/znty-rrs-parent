package com.znty.rrs.service;

import com.znty.rrs.entity.bo.IpAdjustLogBo;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** 定时任务调库日志判断信息查询字段测试。 */
public class ScheduledAdjustLogMapperSqlTest {

    @Test
    public void scheduledAdjustQueriesShouldReturnReasonDetails() throws Exception {
        Path path = Paths.get("src", "main", "resources", "mapper", "AutoAdjustMapper.xml");
        String xml = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

        assertThat(selectBlock(xml, "queryPoolSecurityByExpired"))
                .contains(",si.maturity_date");
        assertThat(selectBlock(xml, "queryCrmwPoolByExpired"))
                .contains(",crmw.maturity_date");
        assertIssuerColumns(selectBlock(xml, "queryCompanyNewBondForAutoIn"));
        assertIssuerColumns(selectBlock(xml, "queryCompanyBondSamePoolForAutoIn"));
        assertIssuerColumns(selectBlock(xml, "queryBondInPoolWhenCompanyNotIn"));
    }

    @Test
    public void adjustLogBoShouldOnlyContainTableFields() {
        assertThat(Arrays.stream(IpAdjustLogBo.class.getDeclaredFields())
                .map(field -> field.getName())
                .collect(Collectors.toList()))
                .doesNotContain("issuerCode", "issuerName", "maturityDate", "outerRating",
                        "inForbiddenPool", "inRestrictedPool", "inLowOuterRating");
    }

    private void assertIssuerColumns(String select) {
        assertThat(select).contains(",bond.issuer_code")
                .contains(",bond.issuer AS issuer_name");
    }

    private String selectBlock(String xml, String queryId) {
        String marker = "<select id=\"" + queryId + "\"";
        int start = xml.indexOf(marker);
        assertThat(start).as(queryId + " 应存在").isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</select>", start);
        assertThat(end).as(queryId + " 应闭合").isGreaterThan(start);
        String select = xml.substring(start, end + "</select>".length());
        assertThat(select).contains("resultType=\"com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto\"");
        return select;
    }
}
