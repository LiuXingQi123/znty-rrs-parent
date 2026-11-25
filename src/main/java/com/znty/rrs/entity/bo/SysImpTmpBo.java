package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 通用导入临时主表实体（sys_imp_tmp）
 *
 * 业务专有入参写入 fld001~fld020，语义由 biz_type 约定
 */
@Data
public class SysImpTmpBo {

    /** 主键 ID */
    private Long id;

    /** 导入批次号 */
    private String impId;

    /** 业务类型 code */
    private String bizType;

    /** 模板编码 */
    private String templateCode;

    /** 原始文件名 */
    private String fileName;

    /** 文件字节数 */
    private Long fileSize;

    /** 文件存储路径 */
    private String filePath;

    /** 导入选项 JSON */
    private String optionJson;

    /** 明细总行数 */
    private Integer totalCount;

    /** 校验通过数 */
    private Integer passCount;

    /** 校验失败数 */
    private Integer failCount;

    /**
     * 批次校验结果
     *
     * 0=待校验、1=通过、2=失败
     */
    private String chkRslt;

    /** 批次校验说明 */
    private String chkDscr;

    /**
     * 保存结果
     *
     * 0=未保存、1=成功、2=失败、3=跳过
     */
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

    /** 字段001 */
    private String fld001;

    /** 字段002 */
    private String fld002;

    /** 字段003 */
    private String fld003;

    /** 字段004 */
    private String fld004;

    /** 字段005 */
    private String fld005;

    /** 字段006 */
    private String fld006;

    /** 字段007 */
    private String fld007;

    /** 字段008 */
    private String fld008;

    /** 字段009 */
    private String fld009;

    /** 字段010 */
    private String fld010;

    /** 字段011 */
    private String fld011;

    /** 字段012 */
    private String fld012;

    /** 字段013 */
    private String fld013;

    /** 字段014 */
    private String fld014;

    /** 字段015 */
    private String fld015;

    /** 字段016 */
    private String fld016;

    /** 字段017 */
    private String fld017;

    /** 字段018 */
    private String fld018;

    /** 字段019 */
    private String fld019;

    /** 字段020 */
    private String fld020;

    /** 逻辑删除标志 */
    private Integer isDeleted;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;

    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}
