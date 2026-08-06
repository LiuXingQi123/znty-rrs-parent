package com.znty.rrs.service;

import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.commonfile.CommonFileReq;
import com.znty.rrs.exception.BizException;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 公共文件服务测试（读取 classpath 模板）
 */
public class CommonFileServiceTest {

    private final CommonFileService service = new CommonFileService();

    @Test
    public void downloadTemplate_SecurityPoolImport_ReturnsBytes() {
        CommonFileReq req = new CommonFileReq();
        req.setTemplateCode("security_pool_import");
        CommonFileDto dto = service.downloadTemplate(req);
        assertNotNull(dto);
        assertNotNull(dto.getContentBase64());
        assertTrue(dto.getContentBase64().length() > 0);
        assertTrue(dto.getFileSize() != null && dto.getFileSize() > 0);
    }

    @Test
    public void downloadTemplate_UnknownCode_Throws() {
        CommonFileReq req = new CommonFileReq();
        req.setTemplateCode("not_exists_tpl");
        try {
            service.downloadTemplate(req);
            fail();
        } catch (BizException e) {
            assertTrue(e.getMessage().contains("未知模板编码"));
        }
    }
}
