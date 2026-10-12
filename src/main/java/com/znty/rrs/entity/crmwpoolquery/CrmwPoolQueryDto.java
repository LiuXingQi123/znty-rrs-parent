package com.znty.rrs.entity.crmwpoolquery;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * CRMW池查询返回对象
 */
@Data
public class CrmwPoolQueryDto {

    /** 主键 ID */
    private Long id;

    /** 来源调库记录 ID */
    private Long adjustLogId;

    /** 调库批次号 */
    private String adjustBatchNo;

    /** CRMW名称 */
    private String crmwName;

    /** CRMW凭证身份：正式ISIN / TEMP:临时代码 */
    private String crmwIsinCode;
    /** CRMW银行间代码快照，关联身份使用crmwIsinCode */
    private String crmwScode;

    /** 证券名称 */
    private String securityShortName;

    /** 沪市证券代码 */
    private String windCodeSh;
    /** 深市证券代码 */
    private String windCodeSz;
    /** 银行间证券代码 */
    private String windCodeNib;
    /** 北交所证券代码 */
    private String windCodeBj;
    /** 其他市场证券代码 */
    private String windCodeNbc;
    /** 证券来源：official=正式 / temporary=临时 / temp_converted=已转正 */
    private String securitySource;
    /** 证券身份：正式ISIN / TEMP:临时代码 */
    private String isinCode;
    /** 证券代码 */
    private String securityCode;

    /** 调整人 */
    private String adjusterName;

    /** 入池时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date entryTime;

    /** 投资池名称 */
    private String targetPoolName;

    /** 目标投资池 ID */
    private Long targetPoolId;
}
