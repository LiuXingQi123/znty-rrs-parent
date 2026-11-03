package com.znty.rrs.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ABS 主档写入边界测试。
 *
 * <p>提交阶段选中的主体只写入申请快照，主档主体字段必须等到审批通过时由
 * {@code AbsIdentityMapper.editApprovedIdentity} 统一更新。禁投池 ABS 链路历史上
 * 会在提交时回写证券基础信息，其中 guarantor 三字段承载权益人与内评，
 * 一旦回写会提前改动评级依据，出库方向未选主体时还会被清空。本测试守住该边界。
 */
public class AbsIdentityMasterWriteBoundaryTest {

    /** 主体身份八个字段：issuer 五字段 + guarantor 三字段 */
    private static final List<String> IDENTITY_COLUMNS = Arrays.asList(
            "issuer =", "issuer_code =", "rating_bondissuer =", "rating_bondissuer_agency =",
            "inner_issuer_rating =", "guarantor =", "guarantor_id =", "inner_guarantor_rating =");

    /** 禁投池 ABS 提交保存基础信息时不得写入任何主体身份字段 */
    @Test
    public void forbiddenAbsSubmitShouldNotWriteIdentityColumns() throws Exception {
        String sql = updateStatement("ForbiddenAbsPoolAdjustMapper.xml");
        for (String column : IDENTITY_COLUMNS) {
            assertThat(sql).as("禁投 ABS 提交不得提前写入 %s", column).doesNotContain(column);
        }
    }

    /** 审批落档只更新主档五字段，不覆盖担保人三字段 */
    @Test
    public void approvalShouldOnlyWriteFiveIdentityColumns() throws Exception {
        String sql = updateStatement("AbsIdentityMapper.xml", "editApprovedIdentity");
        assertThat(sql).contains("issuer =").contains("issuer_code =")
                .contains("rating_bondissuer =").contains("rating_bondissuer_agency =")
                .contains("inner_issuer_rating =");
        assertThat(sql).doesNotContain("guarantor =").doesNotContain("guarantor_id =")
                .doesNotContain("inner_guarantor_rating =");
    }

    /** 提取指定 update 语句文本，默认取 editSecurityInfoForAdjust */
    private String updateStatement(String mapperFile) throws Exception {
        return updateStatement(mapperFile, "editSecurityInfoForAdjust");
    }

    /** 提取指定 update 语句文本 */
    private String updateStatement(String mapperFile, String statementId) throws Exception {
        String xml = new String(Files.readAllBytes(
                Paths.get("src", "main", "resources", "mapper", mapperFile)), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("(?s)<update id=\"" + statementId + "\">.*?</update>").matcher(xml);
        assertThat(matcher.find()).as("%s 必须定义 %s", mapperFile, statementId).isTrue();
        return matcher.group();
    }
}
