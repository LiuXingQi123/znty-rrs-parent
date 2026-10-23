package com.znty.rrs.entity.forbiddenpooladjust;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 单次主体批量校验的公共数据，只在当前调用中传递，不跨请求缓存。 */
@Getter
public class CompanyAdjustCheckContext {
    /** 本次从公司简介查询的主体资料 */
    private final Map<String, ForbiddenPoolAdjustDto> companyMap;
    /** 本次读取的投资池配置 */
    private final List<InvestmentPoolBo> pools;
    /** 本次读取的投资池关系 */
    private final List<PoolRelationBo> relations;

    public CompanyAdjustCheckContext(Map<String, ForbiddenPoolAdjustDto> companyMap,
                                     List<InvestmentPoolBo> pools, List<PoolRelationBo> relations) {
        this.companyMap = Collections.unmodifiableMap(new HashMap<>(companyMap));
        this.pools = Collections.unmodifiableList(new ArrayList<>(pools));
        this.relations = Collections.unmodifiableList(new ArrayList<>(relations));
    }
}
