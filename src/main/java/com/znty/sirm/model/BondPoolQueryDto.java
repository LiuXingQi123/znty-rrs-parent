package com.znty.sirm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 债券池查询返回对象
 */
@Data
public class BondPoolQueryDto {

    /** 主键 ID */
    private Long id;
    /** 债券名称 */
    private String bondShortName;
    /** 债券代码 */
    private String bondCode;
    /** 调整人 */
    private String adjusterName;
    /** 入池时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date entryTime;
    /** 投资池名称 */
    private String targetPoolName;
    /** 债券类型 */
    private String bondType;
    /** 我的债券池记录ID（null=未收藏，非null=已收藏，用于前端爱心图标状态） */
    private Long myBondPoolId;
    /** 票面年利率 */
    private String couponRate;
    /** 发行主体名称 */
    private String issuer;
    /** 债券全称 */
    private String fullName;
    /** 发行日期 */
    private String issueDate;
    /** 起息日 */
    private String carryDate;
    /** 到期日 */
    private String maturityDate;
    /** 退市日期 */
    private String delistDate;
    /** 行权日期（回售） */
    private String repurchaseDate;
}
