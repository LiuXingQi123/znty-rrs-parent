package com.znty.rrs.common.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.fill.FillWrapper;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.exception.BizException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询列表 Excel 导出公共方法（EasyExcel 模板填充）。
 * <p>供各查询/历史页按当前筛选条件导出全量命中记录时复用。</p>
 */
public final class QueryListExportHelper {

    /** 审核状态码 → 中文名称（与前端 inline 字典一致） */
    private static final Map<String, String> AUDIT_STATUS_LABELS = new HashMap<String, String>();

    static {
        AUDIT_STATUS_LABELS.put("-1", "无效调整");
        AUDIT_STATUS_LABELS.put("00", "流程中");
        AUDIT_STATUS_LABELS.put("11", "驳回待修改");
        AUDIT_STATUS_LABELS.put("20", "审批通过");
        AUDIT_STATUS_LABELS.put("21", "审批驳回");
        AUDIT_STATUS_LABELS.put("32", "O32自动审批");
        AUDIT_STATUS_LABELS.put("99", "发起人已撤回");
    }

    /** 工具类禁止实例化。 */
    private QueryListExportHelper() {
    }

    /**
     * 使用 classpath 模板填充列表并返回可下载文件。
     *
     * @param templateClasspath 模板路径，如 /xlsx/xxx.xlsx
     * @param fileNamePrefix    文件名前缀（不含扩展名与时间戳）
     * @param missingMessage    模板缺失时的业务提示
     * @param failMessagePrefix 生成失败提示前缀
     * @param rows              填充行
     * @return 含 Base64 内容的下载文件
     */
    public static CommonFileDto fillTemplate(String templateClasspath, String fileNamePrefix,
                                             String missingMessage, String failMessagePrefix,
                                             List<?> rows) {
        try (InputStream template = QueryListExportHelper.class.getResourceAsStream(templateClasspath);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (template == null) {
                throw new BizException(missingMessage);
            }
            // 按模板填充 data 列表到第一个 Sheet
            ExcelWriter writer = EasyExcel.write(outputStream).withTemplate(template).build();
            try {
                WriteSheet sheet = EasyExcel.writerSheet(0).build();
                writer.fill(new FillWrapper("data", rows), sheet);
            } finally {
                writer.finish();
            }
            // 组装前端 downloadBase64File 所需字段
            byte[] bytes = outputStream.toByteArray();
            CommonFileDto file = new CommonFileDto();
            file.setFileName(fileNamePrefix + "_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx");
            file.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            file.setFileSize((long) bytes.length);
            file.setContentBase64(Base64.getEncoder().encodeToString(bytes));
            return file;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(failMessagePrefix + e.toString());
        }
    }

    /**
     * 入池/提交时间格式化，与页面 Jackson 展示一致。
     *
     * @param value 时间
     * @return yyyy-MM-dd HH:mm:ss，空则空串
     */
    public static String formatDateTime(Date value) {
        return value == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value);
    }

    /**
     * 审核状态码转中文（与前端 inline 字典一致）。
     *
     * @param code 审核状态码
     * @return 中文名称；未知码原样返回
     */
    public static String auditStatusLabel(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "";
        }
        String label = AUDIT_STATUS_LABELS.get(code.trim());
        return label != null ? label : code.trim();
    }

    /**
     * 是否类字段转「是/否」。
     *
     * @param value 是否为真
     * @return 是 / 否
     */
    public static String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    /**
     * 数值标志是否为是（1=是）。
     *
     * @param value 标志值
     * @return true 表示是
     */
    public static boolean isOne(Integer value) {
        return value != null && value == 1;
    }

    /**
     * 文本是否包含「私募」。
     *
     * @param value 发行方式或内部分类等文本
     * @return true 表示含私募
     */
    public static boolean containsPrivate(String value) {
        return value != null && value.contains("私募");
    }

    /**
     * 按到期日计算证券状态（对齐禁投池查询前端 getBondStatus）。
     * <p>支持 {@code yyyyMMdd} / {@code yyyy-MM-dd}；空值返回空串。</p>
     *
     * @param maturityDate 到期日文本
     * @return 存续 / 到期 / 空串
     */
    public static String bondStatusByMaturityDate(String maturityDate) {
        // 兼容多种到期日文本格式
        Date maturity = parseFlexibleDate(maturityDate);
        if (maturity == null) {
            return "";
        }
        // 按自然日比较：到期日早于今天为到期，否则存续
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String day = new SimpleDateFormat("yyyyMMdd").format(maturity);
        return day.compareTo(today) < 0 ? "到期" : "存续";
    }

    /**
     * 按候选格式解析日期文本。
     *
     * @param raw 原始日期字符串
     * @return 解析结果，无法解析返回 null
     */
    private static Date parseFlexibleDate(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        String[] patterns = {"yyyyMMdd", "yyyy-MM-dd", "yyyy/MM/dd", "yyyy-MM-dd HH:mm:ss"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                sdf.setLenient(false);
                return sdf.parse(text);
            } catch (ParseException ignored) {
                // 尝试下一格式
            }
        }
        return null;
    }
}
