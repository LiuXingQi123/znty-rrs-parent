package com.znty.rrs.entity.common;

import lombok.Data;

import java.util.Date;

/**
 * 担保人内评结果
 */
@Data
public class GuarantorGradeDto {

    /** Wind 主体代码 */
    private String windcode;

    /** Wind 主体名称 */
    private String windname;

    /** 主体内评分档 */
    private String totalScore;

    /** 更新时间 */
    private Date ts;
}
