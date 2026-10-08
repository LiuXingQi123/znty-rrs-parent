package com.znty.rrs.entity.bo;

import java.util.Date;
import lombok.Data;

/** 基金临时代码登记，对应 rrs_temp_fund_code，保存转正时的正式信息快照。 */
@Data
public class TempFundCodeBo {
    /** 登记主键 ID */
    private Long id;
    /** 临时基金代码 */
    private String tempFundCode;
    /** 临时基金简称 */
    private String tempFundShortName;
    /** 临时基金产品类型编码，来自 category_type=fund 的产品类型字典 */
    private String tempSecurityType;
    /** 临时基金市场 */
    private String tempMarketCode;
    /** 正式基金代码 */
    private String fundCode;
    /** 正式基金全称快照 */
    private String fundName;
    /** 正式基金简称快照 */
    private String fundShortName;
    /** 正式基金市场快照 */
    private String marketCode;
    /** 正式基金产品类型编码快照，来自 category_type=fund 的产品类型字典 */
    private String securityType;
    /** 转正、取消或删除的业务更新时间，新增时为空 */
    private Date updateTime;
    /** 状态：temporary=临时 / updated=已更新 / cancelled=已取消 / deleted=已删除 */
    private String status;
    /** 逻辑删除标志：0=正常 / 1=已删除 */
    private Integer isDeleted;
    /** 操作来源：manual=人工 / job=定时任务 / other=其他 */
    private String oprtSource;
    /** 备注 */
    private String memo;
    /** 创建时间 */
    private Date crteTime;
    /** 修改时间 */
    private Date updtTime;
}
