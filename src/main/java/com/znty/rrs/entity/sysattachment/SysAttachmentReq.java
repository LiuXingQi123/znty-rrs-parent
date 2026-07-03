package com.znty.rrs.entity.sysattachment;

import lombok.Data;

/**
 * 系统附件请求对象
 */
@Data
public class SysAttachmentReq {

    /** 附件 ID */
    private Long id;

    /** 调库日志 ID */
    private Long adjustLogId;

}
