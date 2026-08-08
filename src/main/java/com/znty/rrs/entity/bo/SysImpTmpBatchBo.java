package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 导入数据批次（临时主表）实体
 */
@Data
public class SysImpTmpBatchBo {

    /** 主键 ID */
    private Long id;
    /** 导入批次号 */
    private String impId;
    /** 批次类型 */
    private String bizType;
    /** 模板编码 */
    private String templateCode;
    /** 原始文件名 */
    private String fileName;
    /** 文件字节数 */
    private Long fileSize;
    /** 文件存储路径 */
    private String filePath;
    /** 目标对象 ID */
    private Long targetId;
    /** 目标对象名称快照 */
    private String targetName;
    /** 目标对象类型快照 */
    private String targetType;
    /** 业务模式：in / out */
    private String bizMode;
    /** 选项 JSON */
    private String optionJson;
    /** 调整原因 */
    private String reason;
    /** 调整意见 */
    private String advice;
    /** 明细总行数 */
    private Integer totalCount;
    /** 校验通过数 */
    private Integer passCount;
    /** 校验失败数 */
    private Integer failCount;
    /** 批次校验结果：0/1/2 */
    private String chkRslt;
    /** 批次校验说明 */
    private String chkDscr;
    /** 保存结果：0/1/2/3 */
    private String saveRslt;
    /** 保存说明 */
    private String saveDscr;
    /** 导入时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date impTime;
    /** 经办人 ID */
    private String opterId;
    /** 经办人名称 */
    private String opterName;
    /** 提交结果扩展 JSON */
    private String resultJson;
    /** 逻辑删除标志 */
    private Integer isDeleted;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;
    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}
