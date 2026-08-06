package com.znty.rrs.entity.commonfile;

import lombok.Data;

/**
 * 公共文件请求
 */
@Data
public class CommonFileReq {

    /** 模板编码（如 security_pool_import / company_pool_import） */
    private String templateCode;
}
