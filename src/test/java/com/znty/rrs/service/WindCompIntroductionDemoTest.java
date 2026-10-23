package com.znty.rrs.service;

import org.junit.Assume;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 通过 H2 实际写入，验证公司简介 Demo 的字段长度、主体覆盖及空统一社会信用代码。 */
public class WindCompIntroductionDemoTest {

    /** 全部现有发行主体均有公司记录，并包含未发债主体和信用代码为空的情况。 */
    @Test
    public void shouldLoadAllCompanyDemoRows() throws Exception {
        String version = System.getProperty("java.specification.version");
        int major = Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
        Assume.assumeTrue("H2 2.3.232 持久化测试需 Java 11 以上运行时", major >= 11);
        String schema = new String(Files.readAllBytes(Paths.get("sql/ais_inv_ods_schema.sql")), StandardCharsets.UTF_8);
        String demo = new String(Files.readAllBytes(Paths.get("sql/ais_inv_ods_demo_data.sql")), StandardCharsets.UTF_8);
        Matcher ddl = Pattern.compile("(?s)CREATE TABLE `wind_compintroduction` \\(.*?\\) ENGINE.*?;").matcher(schema);
        Matcher insert = Pattern.compile("(?s)INSERT INTO `wind_compintroduction` \\(.*?;").matcher(demo);
        assertTrue(ddl.find());
        assertTrue(insert.find());
        // 仅移除 H2 不支持的 MySQL 表选项，字段定义及实际 INSERT 保持原样
        String createSql = ddl.group().replaceFirst("(?s)\\) ENGINE.*$", ")");
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:wind_company_demo;MODE=MySQL", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute(createSql);
            statement.execute(insert.group());
            try (ResultSet counts = statement.executeQuery("SELECT COUNT(*), COUNT(DISTINCT COMP_ID), "
                    + "SUM(CASE WHEN REGISTERNUMBER IS NULL THEN 1 ELSE 0 END) FROM wind_compintroduction")) {
                assertTrue(counts.next());
                assertEquals(12, counts.getInt(1));
                assertEquals(12, counts.getInt(2));
                assertEquals(2, counts.getInt(3));
            }
            Set<String> companies = new HashSet<>();
            try (ResultSet rows = statement.executeQuery("SELECT COMP_ID, REGISTERNUMBER FROM wind_compintroduction")) {
                while (rows.next()) {
                    companies.add(rows.getString(1));
                    String creditCode = rows.getString(2);
                    if (creditCode != null) {
                        assertEquals(18, creditCode.length());
                        assertFalse(creditCode.equals(rows.getString(1)));
                    }
                }
            }
            Matcher issuerInsert = Pattern.compile("(?s)INSERT INTO `wind_cbondissuer` \\(.*?;").matcher(demo);
            assertTrue(issuerInsert.find());
            Matcher issuerCodes = Pattern.compile("'C[0-9]+'").matcher(issuerInsert.group());
            Set<String> issuers = new HashSet<>();
            while (issuerCodes.find()) {
                String code = issuerCodes.group().replace("'", "");
                issuers.add(code);
            }
            assertEquals(10, issuers.size());
            assertTrue(companies.containsAll(issuers));
            assertTrue(companies.contains("C10011"));
            assertTrue(companies.contains("C10012"));
            assertFalse(issuers.contains("C10011"));
            assertFalse(issuers.contains("C10012"));
        }
    }
}
