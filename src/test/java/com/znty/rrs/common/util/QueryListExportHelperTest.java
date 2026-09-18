package com.znty.rrs.common.util;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 查询列表导出公共方法测试。
 */
public class QueryListExportHelperTest {

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
