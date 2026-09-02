package com.znty.rrs.entity.sysattachment;

import lombok.Data;

import java.util.List;

/**
 * 系统附件请求对象
 */
@Data
public class SysAttachmentReq {

    /** 附件 ID */
    private Long id;

    /** 调库日志 ID */
    private Long adjustLogId;

    /** 调库日志 ID 列表 */
    private List<Long> adjustLogIds;

}
