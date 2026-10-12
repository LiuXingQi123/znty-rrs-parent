package com.znty.rrs.entity.schedule;

import lombok.Data;

/**
 * 债券类型变更待处理记录。
 */
@Data
public class BondSecurityTypeChangeDto {

    /** 池状态主键 */
    private Long poolStatusId;

    /** 当前关联调库日志主键 */
    private Long adjustLogId;

    /** 证券代码 */
    private String securityCode;

    /** 证券简称 */
    private String securityShortName;

    /** 池状态原证券类型 */
    private String oldSecurityType;

    /** 证券主数据最新类型 */
    private String newSecurityType;
}
