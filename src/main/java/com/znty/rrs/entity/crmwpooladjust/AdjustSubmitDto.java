package com.znty.rrs.entity.crmwpooladjust;

import lombok.Data;

import java.util.List;

/**
 * 证券调库提交结果 DTO，返回本次提交成功的调库项数量及生成的记录 ID 列表
 */
@Data
public class AdjustSubmitDto {

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

    /** 已提交的调库项数量 */
    private Integer submitCount;

    /** 生成的调库记录 ID 列表 */
    private List<Long> logIds;
}
