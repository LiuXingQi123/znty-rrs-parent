package com.znty.rrs.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/** 调库记录按对象全量历史查询的 Mapper SQL 口径测试。 */
public class AdjustLogHistoryMapperSqlTest {

    /** 证券、主体和 CRMW 查询都只按当前对象身份过滤，不启用批次或状态过滤。 */
    @Test
    public void adjustLogQueriesShouldUseObjectIdentityAndSubmissionTimeOrder() throws Exception {
        String securityQuery = executableSelect("SecurityPoolAdjustMapper.xml", "queryAdjustLogList");
        assertThat(securityQuery)
                .contains("AND al.security_code = #{securityCode}")
                .contains("ORDER BY al.submit_time DESC")
                .contains(",al.id DESC")
                .doesNotContain("adjust_batch_no = #{adjustBatchNo}")
                .doesNotContain("al.audit_status NOT IN");

        String companyQuery = executableSelect("ForbiddenPoolAdjustMapper.xml", "queryAdjustLogList");
        assertThat(companyQuery)
                .contains("AND al.security_code = #{securityCode}")
                .contains("AND al.security_type = 'company'")
                .contains("ORDER BY al.submit_time DESC")
                .contains(",al.id DESC")
                .doesNotContain("adjust_batch_no = #{adjustBatchNo}")
                .doesNotContain("al.audit_status NOT IN");

        String crmwQuery = executableSelect("CrmwPoolAdjustMapper.xml", "queryAdjustLogList");
        assertThat(crmwQuery)
                .contains("WHERE al.security_code = #{securityCode}")
                .contains("AND al.crmw_scode = #{crmwScode}")
                .contains("AND al.crmw_stype = #{crmwStype}")
                .contains("AND al.pool_type = 'crmw'")
                .contains("ORDER BY al.submit_time DESC")
                .contains(",al.id DESC")
                .doesNotContain("adjust_batch_no = #{adjustBatchNo}")
                .doesNotContain("al.audit_status NOT IN");
    }

    /** 读取指定 select，并剔除保留备查的 XML 注释后校验实际执行 SQL。 */
    private String executableSelect(String mapperFile, String queryId) throws Exception {
        Path mapperPath = Paths.get("src", "main", "resources", "mapper", mapperFile);
        String xml = new String(Files.readAllBytes(mapperPath), StandardCharsets.UTF_8);
        String marker = "<select id=\"" + queryId + "\"";
        int start = xml.indexOf(marker);
        assertThat(start).as(queryId + " 应存在").isGreaterThanOrEqualTo(0);
        int end = xml.indexOf("</select>", start);
        assertThat(end).as(queryId + " 应闭合").isGreaterThan(start);
        return xml.substring(start, end + "</select>".length()).replaceAll("(?s)<!--.*?-->", "");
    }
}
