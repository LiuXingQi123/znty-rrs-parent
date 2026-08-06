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
 * 公共文件服务：classpath 模板下载等
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
        TEMPLATE_PATH_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * 按模板编码下载 classpath 下模板文件（Base64）
     */
    public CommonFileDto downloadTemplate(CommonFileReq req) {
        if (req == null || req.getTemplateCode() == null || req.getTemplateCode().trim().isEmpty()) {
            throw new BizException("模板编码不能为空");
        }
        String code = req.getTemplateCode().trim().toLowerCase();
        if (!TEMPLATE_CODE_PATTERN.matcher(code).matches()) {
            throw new BizException("模板编码不合法");
        }
        String path = TEMPLATE_PATH_MAP.get(code);
        if (path == null) {
            throw new BizException("未知模板编码：" + code);
        }
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new BizException("模板文件不存在：" + code);
        }
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
