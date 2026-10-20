package com.znty.rrs.entity.schedule;

import lombok.Data;

/** 主体观察名单与 AIS 主体主数据关联结果。 */
@Data
public class CompanyObserverDto {
    /** 观察名单记录 ID */
    private Long id;
    /** AIS 主体主键 */
    private Long companyId;
    /** Wind 主体代码及发行人编码 */
    private String companyCode;
    /** 观察者类型：1=空 / 2=观察 / 3=重点观察 */
    private String observerType;
    /** AIS 主体主数据中的 Wind 代码，用于检查关联一致性 */
    private String masterCompanyCode;
    /** AIS 主体名称 */
    private String companyName;
}
