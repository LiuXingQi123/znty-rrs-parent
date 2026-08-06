package com.znty.rrs.entity.bo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 导入临时明细实体（通用字段槽 fld001~fld030）
 */
@Data
public class SysImpTmpBo {

    /** 主键 ID */
    private Long id;
    /** 导入明细号 */
    private String impDetlId;
    /** 导入批次号 */
    private String impId;
    /** Excel 行号 */
    private Integer rowNo;
    /** 校验结果：0待校验 / 1通过 / 2失败 */
    private String chkRslt;
    /** 校验说明 */
    private String chkDscr;
    /** 保存结果：0未保存 / 1成功 / 2失败 / 3跳过 */
    private String saveRslt;
    /** 保存说明 */
    private String saveDscr;
    /** 导入时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date impTime;
    /** 经办人 ID */
    private String opterId;
    /** 提交后业务单 ID */
    private Long refId;
    /** 字段001（业务主键槽） */
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
    /** 字段021 */
    private String fld021;
    /** 字段022 */
    private String fld022;
    /** 字段023 */
    private String fld023;
    /** 字段024 */
    private String fld024;
    /** 字段025 */
    private String fld025;
    /** 字段026 */
    private String fld026;
    /** 字段027 */
    private String fld027;
    /** 字段028 */
    private String fld028;
    /** 字段029 */
    private String fld029;
    /** 字段030 */
    private String fld030;
    /** 逻辑删除标志 */
    private Integer isDeleted;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date crteTime;
    /** 修改时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtTime;
}
