package com.znty.rrs.entity.forbiddenpooladjust;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import lombok.Data;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 主体 Excel 一次校验所需的业务数据 */
public class BatchCompanyAdjustCheckData {

    public final Map<String, CompanyState> states = new HashMap<>();
    public final Map<Long, InvestmentPoolBo> pools = new HashMap<>();
    public final Map<Long, Map<String, List<Long>>> relations = new HashMap<>();
    public final Map<Long, Integer> capacities = new HashMap<>();
    public final Set<Long> openToday = new HashSet<>();
    public String categoryType;
    public boolean ratingAgenciesConfigured;

    /** 单主体状态，按请求使用的代码定位 */
    public static class CompanyState {
        public final Set<Long> currentPoolIds = new HashSet<>();
        public final Set<Long> companyPoolIds = new HashSet<>();
        public final Set<Long> pendingPoolIds = new HashSet<>();
        public final Map<Long, Date> entryTimes = new HashMap<>();
        public String pendingNodeLabel;
        public boolean lowOuterRating;
    }

    /** 批量查询返回的在池、在途或容量字段 */
    @Data
    public static class Row {
        private String securityCode;
        private Long targetPoolId;
        private String securityType;
        private Date entryTime;
        private Long entryLogId;
        private String nodeLabel;
        private Integer currentCount;
    }
}
