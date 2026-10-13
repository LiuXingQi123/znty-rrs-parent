package com.znty.rrs.common.util;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 查询列表导出公共方法测试。
 */
public class QueryListExportHelperTest {

    /** 数值标志统一先转布尔值，再转换为是否文字。 */
    @Test
    public void yesNoShouldConvertFlagsThroughBooleanValues() {
        assertThat(QueryListExportHelper.yesNo(QueryListExportHelper.isOne(1))).isEqualTo("是");
        assertThat(QueryListExportHelper.yesNo(QueryListExportHelper.isOne(0))).isEqualTo("否");
        assertThat(QueryListExportHelper.yesNo(QueryListExportHelper.isOne(null))).isEqualTo("否");
    }

    /** 审核状态码转中文。 */
    @Test
    public void auditStatusLabelShouldMapKnownCodes() {
        assertThat(QueryListExportHelper.auditStatusLabel("20")).isEqualTo("审批通过");
        assertThat(QueryListExportHelper.auditStatusLabel("00")).isEqualTo("流程中");
        assertThat(QueryListExportHelper.auditStatusLabel(null)).isEqualTo("");
    }

    /** 到期日派生证券状态。 */
    @Test
    public void bondStatusByMaturityDateShouldMatchFrontendRule() {
        assertThat(QueryListExportHelper.bondStatusByMaturityDate(null)).isEqualTo("");
        assertThat(QueryListExportHelper.bondStatusByMaturityDate("20991231")).isEqualTo("存续");
        assertThat(QueryListExportHelper.bondStatusByMaturityDate("20000101")).isEqualTo("到期");
    }
}
