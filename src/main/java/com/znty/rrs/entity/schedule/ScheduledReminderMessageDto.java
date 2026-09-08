package com.znty.rrs.entity.schedule;

import java.util.List;
import lombok.Data;

/**
 * 定时提醒投递消息，隔离业务扫描与后续邮件、站内信实现。
 */
@Data
public class ScheduledReminderMessageDto {

    /** 来源任务编码 */
    private String taskCode;

    /** 消息标题 */
    private String title;

    /** 文本内容 */
    private String content;

    /** 提醒候选明细 */
    private List<BondReminderDto> items;
}
