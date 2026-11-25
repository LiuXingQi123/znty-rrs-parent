package com.znty.rrs.entity.forbiddenpooladjust;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import lombok.Data;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 主体 Excel 一次校验所需的业务数据
 */
public class BatchCompanyAdjustCheckData {

    /** 按主体代码索引的校验状态 */
    public final Map<String, CompanyState> states = new HashMap<>();

    /** 按池 ID 索引的投资池配置 */
    public final Map<Long, InvestmentPoolBo> pools = new HashMap<>();

    /** 按池 ID 和关系类型索引的关联池 ID */
    public final Map<Long, Map<String, List<Long>>> relations = new HashMap<>();

    /** 相关池的当前在池数量 */
    public final Map<Long, Integer> capacities = new HashMap<>();

    /** 当前日期处于开放区间的池 ID 集合 */
    public final Set<Long> openToday = new HashSet<>();

    /** 主体证券类型对应的大类编码 */
    public String categoryType;

    /** 是否已取得黑名单外评校验使用的认可机构 */
    public boolean ratingAgenciesConfigured;

    /**
     * 单主体状态，按请求使用的代码定位
     */
    public static class CompanyState {

        /** 主体当前有效所在池 ID 集合 */
        public final Set<Long> currentPoolIds = new HashSet<>();

        /** 以主体类型有效在池的池 ID 集合 */
        public final Set<Long> companyPoolIds = new HashSet<>();

        /** 主体在途流程占用的目标池 ID 集合 */
        public final Set<Long> pendingPoolIds = new HashSet<>();

        /** 按池记录的入池时间，用于出库冻结期校验 */
        public final Map<Long, Date> entryTimes = new HashMap<>();

        /** 主体在途流程的节点名称 */
        public String pendingNodeLabel;

        /** 是否命中黑名单校验的低外评条件 */
        public boolean lowOuterRating;
    }

    /**
     * 批量查询返回的在池、在途或容量字段
     */
    @Data
    public static class Row {

        /** 主体代码 */
        private String securityCode;

        /** 目标投资池 ID */
        private Long targetPoolId;

        /** 在池记录的证券类型 */
        private String securityType;

        /** 入池时间 */
        private Date entryTime;

        /** 对应入池记录的调库日志 ID */
        private Long entryLogId;

        /** 在途节点名称 */
        private String nodeLabel;

        /** 当前有效在池数量 */
        private Integer currentCount;
    }
}
