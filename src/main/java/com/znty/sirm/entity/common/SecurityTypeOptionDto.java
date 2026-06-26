package com.znty.sirm.entity.common;

import lombok.Data;

/**
 * 证券类型选项（code + 中文名称配对，供下拉框使用）
 */
@Data
public class SecurityTypeOptionDto {

    /** 证券类型编码（如 mtn、company_bond） */
    private String securityType;

    /** 证券类型名称（如 中期票据、公司债） */
    private String securityTypeName;
}
