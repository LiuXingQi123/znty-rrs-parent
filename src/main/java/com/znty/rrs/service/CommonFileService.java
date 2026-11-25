package com.znty.rrs.service;

import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.commonfile.CommonFileReq;
import com.znty.rrs.exception.BizException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 提供业务页面的公共模板文件下载
 *
 * 页面用模板编码选择文件，本类只从已登记的应用资源中读取模板
 * 返回下载文件名及文件内容，模板编码不合法或文件缺失时返回业务错误
 */
@Service
public class CommonFileService {

    /** 模板编码合法字符：小写字母、数字、下划线 */
    private static final Pattern TEMPLATE_CODE_PATTERN = Pattern.compile("^[a-z0-9_]+$");

    /** 模板编码 → classpath 相对路径 */
    private static final Map<String, String> TEMPLATE_PATH_MAP;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("security_pool_import", "xlsx/security_pool_import.xlsx");
        map.put("company_pool_import", "xlsx/company_pool_import.xlsx");
        map.put("crmw_pool_import", "xlsx/crmw_pool_import.xlsx");
        map.put("fund_pool_import", "xlsx/fund_pool_import.xlsx");
        map.put("stock_pool_import", "xlsx/stock_pool_import.xlsx");
        TEMPLATE_PATH_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * 按模板编码下载 classpath 下模板文件（Base64）
     *
     * 执行顺序：
     * 1. 检查模板编码是否缺失，去掉首尾空白、转成小写后校验格式
     * 2. 用模板编码查找已登记的文件，未登记或文件不存在时提示错误
     * 3. 读取模板内容，返回文件名、类型、大小和 Base64 内容，读取失败时提示原因
     *
     * @param req 模板下载请求，包含页面选择的模板编码
     * @return 模板文件名、内容类型、字节数和 Base64 内容
     */
    public CommonFileDto downloadTemplate(CommonFileReq req) {
        // 1. 校验并规范化模板编码
        if (req == null || req.getTemplateCode() == null || req.getTemplateCode().trim().isEmpty()) {
            throw new BizException("模板编码不能为空");
        }
        String code = req.getTemplateCode().trim().toLowerCase();
        if (!TEMPLATE_CODE_PATTERN.matcher(code).matches()) {
            throw new BizException("模板编码不合法");
        }
        // 2. 从已登记的路径定位模板文件
        String path = TEMPLATE_PATH_MAP.get(code);
        if (path == null) {
            throw new BizException("未知模板编码：" + code);
        }
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new BizException("模板文件不存在：" + code);
        }
        // 3. 读取文件并组装下载结果
        try (InputStream in = resource.getInputStream()) {
            byte[] bytes = StreamUtils.copyToByteArray(in);
            CommonFileDto dto = new CommonFileDto();
            dto.setFileName(code + ".xlsx");
            dto.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            dto.setFileSize((long) bytes.length);
            dto.setContentBase64(Base64.getEncoder().encodeToString(bytes));
            return dto;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("读取模板失败：" + e.getMessage());
        }
    }
}
