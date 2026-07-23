package com.znty.rrs.entity.securitypooladjust;

import com.znty.rrs.entity.sysattachment.SysAttachmentDto;
import lombok.Data;

import java.util.List;

/**
 * 调库选池阶段自动回填的最近信评报告（对齐老系统 getLastReprotDocs）
 */
@Data
public class LastCreditReportDto {

    /** 报告库主键 ID（内部/外部报告） */
    private Long id;

    /** 报告标题 */
    private String title;

    /** 报告类型编码 */
    private String reportType;

    /** 来源：internal=内部报告库 / external=外部报告库 */
    private String source;

    /** 报告库附件列表（提交时复制绑定用） */
    private List<SysAttachmentDto> attachments;
}
