package com.znty.rrs.service;

import com.znty.rrs.entity.bo.SysAttachmentBo;
import com.znty.rrs.common.enums.AttachmentPurpose;
import com.znty.rrs.entity.report.ReportReq;
import com.znty.rrs.entity.sysattachment.SysAttachmentDto;
import com.znty.rrs.mapper.ReportMapper;
import com.znty.rrs.mapper.SysAttachmentMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 用真实 DDL 和 MyBatis SQL 验证报告身份、附件完整性及复制来源。 */
public class ReportMaterialsMapperSqlTest {
    /** 独立内存数据库 */
    private JdbcTemplate jdbc;
    /** 自动提交的真实 Mapper 会话 */
    private SqlSession session;
    /** 真实报告服务 */
    private ReportService reports;
    /** 真实附件 Mapper */
    private SysAttachmentMapper attachments;

    /** 加载实际附件 schema 与报告、附件 Mapper，不连接外部数据库。 */
    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("H2 2.3 需要 Java 11 以上", !System.getProperty("java.specification.version").startsWith("1."));
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:report_materials_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        String ddl = new String(Files.readAllBytes(Paths.get("sql", "rrs_sys_attachment_schema.sql")), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("(?is)CREATE TABLE.*?;").matcher(ddl);
        while (matcher.find()) {
            jdbc.execute(matcher.group().replaceAll("(?is)\\)\\s*ENGINE\\s*=.*", ")"));
        }
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        for (String name : Arrays.asList("Report", "SysAttachment")) {
            String resource = "mapper/" + name + "Mapper.xml";
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
            }
        }
        session = new SqlSessionFactoryBuilder().build(config).openSession(true);
        attachments = session.getMapper(SysAttachmentMapper.class);
        reports = new ReportService();
        ReflectionTestUtils.setField(reports, "reportMapper", session.getMapper(ReportMapper.class));
        jdbc.update("INSERT INTO rrs_report_in (id,report_title,is_deleted) VALUES (20,'内部报告',0)");
        jdbc.update("INSERT INTO rrs_report_out (id,report_title,is_deleted) VALUES (20,'外部报告',0)");
    }

    /** 关闭独立会话及数据库。 */
    @After
    public void tearDown() {
        if (session != null) session.close();
        if (jdbc != null) jdbc.execute("SET DB_CLOSE_DELAY 0");
    }

    /** 同 ID 内外报告精确取数，全部有效附件返回，删除附件不返回。 */
    @Test
    public void detailShouldKeepSourcesSeparateAndReturnAllActiveFiles() {
        jdbc.update("INSERT INTO sys_attachment (id,table_name,main_id,attachment_category,is_deleted) VALUES "
                + "(1,'rrs_report_in',20,'report_in',0),(2,'rrs_report_in',20,'report_in',0),"
                + "(3,'rrs_report_in',20,'report_in',1),(4,'rrs_report_out',20,'report_out',0)");
        ReportReq req = new ReportReq();
        req.setReportId(20L);
        req.setSource("internal");
        assertThat(reports.queryReportDetail(req).getAttachments()).extracting(SysAttachmentDto::getId).containsExactly(1L, 2L);
        req.setSource("external");
        assertThat(reports.queryReportDetail(req).getReportTitle()).isEqualTo("外部报告");
        assertThat(reports.queryReportDetail(req).getAttachments()).extracting(SysAttachmentDto::getId).containsExactly(4L);
        jdbc.update("UPDATE rrs_report_out SET is_deleted=1 WHERE id=20");
        session.clearCache();
        assertThatThrownBy(() -> reports.queryReportDetail(req)).hasMessageContaining("不存在或已删除");
    }

    /** 三种调库业务均保存明确来源，查询时保留两个库附件与一个手工文件。 */
    @Test
    public void copiedFilesShouldRetainReportIdentityAcrossBusinessTables() {
        jdbc.update("INSERT INTO sys_attachment (id,table_name,main_id,attachment_category,file_name,is_deleted) VALUES "
                + "(1,'rrs_report_in',20,'report_in','one.pdf',0),(2,'rrs_report_in',20,'report_in','two.pdf',0)");
        SysAttachmentService service = new SysAttachmentService();
        ReflectionTestUtils.setField(service, "sysAttachmentMapper", attachments);
        String[] tables = {"ip_adjust_log", "ip_adjust_log_fund", "ip_adjust_log_stock"};
        String[] categories = {"credit_report_hand", "fund_report_hand", "stock_report_hand"};
        for (int i = 0; i < tables.length; i++) {
            service.copyReportAttachments(tables[i], 88L, Arrays.asList(1L, 2L), AttachmentPurpose.CREDIT_REPORT.getCode(), "1");
            SysAttachmentBo hand = new SysAttachmentBo();
            hand.setTableName(tables[i]);
            hand.setMainId(88L);
            hand.setAttachmentCategory(categories[i]);
            hand.setOriginalFileName("手工.pdf");
            attachments.addAttachment(hand);
            session.clearCache();
            List<SysAttachmentDto> files = attachments.queryAttachmentList(tables[i], Collections.singletonList(88L));
            assertThat(files).hasSize(3);
            assertThat(files.subList(0, 2)).extracting(SysAttachmentDto::getSourceAttachmentId).containsExactly(1L, 2L);
            assertThat(files.subList(0, 2)).extracting(SysAttachmentDto::getSourceReportId).containsOnly(20L);
            assertThat(files.subList(0, 2)).extracting(SysAttachmentDto::getSourceReportSource).containsOnly("internal");
            assertThat(files.subList(0, 2)).extracting(SysAttachmentDto::getSourceReportTitle).containsOnly("内部报告");
            assertThat(files.get(2).getSourceReportId()).isNull();
        }
    }
}
