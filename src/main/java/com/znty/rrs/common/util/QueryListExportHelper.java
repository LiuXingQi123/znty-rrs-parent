package com.znty.rrs.common.util;

import com.znty.rrs.common.enums.SecurityStatus;
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
 * 查询及历史列表的 Excel 导出工具
 *
 * 接收调用方按当前筛选条件查出的全部命中记录，使用 EasyExcel 填充模板，并提供导出字段的文案转换
 */
public final class QueryListExportHelper {

    /** 审核状态编码对应的导出文案，与前端字典一致 */
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

    /** 工具类禁止实例化 */
    private QueryListExportHelper() {
    }

    /**
     * 使用类路径下的模板生成下载文件
     *
     * 执行顺序：
     * 1. 加载模板，找不到时抛出指定的业务提示
     * 2. 将记录填充到首张工作表的 data 区域，再结束写入
     * 3. 生成带时间戳的文件名，并返回文件大小、类型和 Base64 内容
     *
     * @param templateClasspath 模板路径，如 /xlsx/xxx.xlsx
     * @param fileNamePrefix 文件名前缀，不含扩展名与时间戳
     * @param missingMessage 模板缺失时的业务提示
     * @param failMessagePrefix 生成失败提示前缀
     * @param rows 要填入模板的数据行
     * @return 含 Base64 内容及下载所需信息的 Excel 文件
     * @throws BizException 模板缺失或文件生成失败
     */
    public static CommonFileDto fillTemplate(String templateClasspath, String fileNamePrefix,
                                             String missingMessage, String failMessagePrefix,
                                             List<?> rows) {
        // 1. 从类路径读取模板，缺失时使用指定的提示
        try (InputStream template = QueryListExportHelper.class.getResourceAsStream(templateClasspath);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (template == null) {
                throw new BizException(missingMessage);
            }
            // 2. 将记录写入首张工作表的 data 区域
            ExcelWriter writer = EasyExcel.write(outputStream).withTemplate(template).build();
            try {
                WriteSheet sheet = EasyExcel.writerSheet(0).build();
                writer.fill(new FillWrapper("data", rows), sheet);
            } finally {
                writer.finish();
            }
            // 3. 组装文件名、大小、类型和 Base64 内容，供前端 downloadBase64File 下载
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
     * 将入池或提交时间转为导出使用的日期时间文本
     *
     * 日期时间格式与页面使用的 Jackson 序列化格式一致
     *
     * @param value 时间
     * @return yyyy-MM-dd HH:mm:ss 格式的文本，时间为空时返回空字符串
     */
    public static String formatDateTime(Date value) {
        return value == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value);
    }

    /**
     * 将审核状态编码转为与前端一致的中文文案
     *
     * @param code 审核状态码
     * @return 中文名称，未知编码去掉首尾空白后返回，空值或空白编码返回空字符串
     */
    public static String auditStatusLabel(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "";
        }
        String label = AUDIT_STATUS_LABELS.get(code.trim());
        return label != null ? label : code.trim();
    }

    /**
     * 将布尔标志转为导出文案
     *
     * @param value 要展示的布尔值
     * @return 为 true 时返回「是」，为 false 时返回「否」
     */
    public static String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    /**
     * 判断数字标志是否表示「是」
     *
     * @param value 标志值
     * @return true=标志值为 1，false=标志缺失或不为 1
     */
    public static boolean isOne(Integer value) {
        return value != null && value == 1;
    }

    /**
     * 判断导出字段是否含「私募」
     *
     * @param value 发行方式或内部分类等文本
     * @return true=文本含「私募」，false=文本为空或不含「私募」
     */
    public static boolean containsPrivate(String value) {
        return value != null && value.contains("私募");
    }

    /**
     * 转换债券主档状态为列表和导出的展示名称
     *
     * @param status 证券主档状态
     * @return 状态为 D 时返回「到期」，其他状态或空值返回「存续」
     */
    public static String bondStatusLabel(String status) {
        return SecurityStatus.DELISTED.getCode().equals(status) ? "到期" : "存续";
    }

    /**
     * 按支持的格式依次尝试解析日期文本
     *
     * 去掉首尾空白后按 yyyyMMdd、yyyy-MM-dd、yyyy/MM/dd、yyyy-MM-dd HH:mm:ss 的顺序尝试
     *
     * @param raw 原始日期字符串
     * @return 首次解析成功的日期，输入为空白或均无法解析时返回 null
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
