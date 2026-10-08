package com.znty.rrs.entity.bo;

import java.util.Date;
import lombok.Data;

/** 基金临时代码替换明细，对应 rrs_temp_fund_code_update_log，仅追加成功记录。 */
@Data
public class TempFundCodeUpdateLogBo {
    /** 替换日志主键 */
    private Long id;
    /** 临时代码登记 ID */
    private Long tempCodeId;
    /** 原临时基金代码 */
    private String tempFundCode;
    /** 原临时基金简称 */
    private String tempFundShortName;
    /** 原临时基金市场 */
    private String tempMarketCode;
    /** 原临时基金产品类型编码，来自 category_type=fund 的产品类型字典 */
    private String tempSecurityType;
    /** 正式基金代码 */
    private String fundCode;
    /** 正式基金全称 */
    private String fundName;
    /** 正式基金简称 */
    private String fundShortName;
    /** 正式基金市场 */
    private String marketCode;
    /** 正式基金产品类型编码快照，来自 category_type=fund 的产品类型字典 */
    private String securityType;
    /** 被替换业务表 */
    private String replaceTableName;
    /** 被替换记录 ID */
    private Long replaceRecordId;
    /** 替换结果：success=成功 */
    private String replaceStatus;
    /** 替换时间 */
    private Date replaceTime;
    /** 人工转正操作人 ID */
    private String operatorId;
    /** 创建时间 */
    private Date crteTime;
    /** 修改时间 */
    private Date updtTime;
}
