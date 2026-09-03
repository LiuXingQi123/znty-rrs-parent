package com.znty.rrs.entity.common;

import lombok.Data;

import java.util.List;

/**
 * 担保人内评查询请求
 */
@Data
public class GuarantorGradeReq {

    /** Wind 证券代码列表 */
    private List<String> securityCodes;
}
