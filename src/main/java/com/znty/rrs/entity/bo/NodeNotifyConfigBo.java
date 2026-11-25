package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 消息通知节点配置
 */
@Data
public class NodeNotifyConfigBo {

    /** 主键 */
    private Long id;

    /** 节点 ID */
    private Long nodeId;

    /** 通知渠道 JSON */
    private String notifyChannels;

    /** 通知目标 */
    private String notifyTarget;

    /** 通知人 JSON */
    private String notifyPersons;

    /** 通知模板 */
    private String notifyTpl;

    /** 通知备注 */
    private String notifyRemark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}
