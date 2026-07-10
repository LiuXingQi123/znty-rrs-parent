package com.znty.rrs.entity.forbiddenpooladjust;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 禁投池主体调整返回对象，承载主体、所在池和旗下债券数据。
 */
@Data
public class ForbiddenPoolAdjustDto {

    /** 主键 ID */
    private Long id;
    /** 主体代码 */
    private String companyCode;
    /** 主体简称 */
    private String companyShortName;
    /** 主体全称 */
    private String companyFullName;
    /** 所属行业 */
    private String industryName;
    /** Wind 主体代码 */
    private String windCode;
    /** 经营范围 */
    private String businessScope;
    /** 注册地址 */
    private String regAddress;
    /** 注册资本 */
    private String regCapital;
    /** 法定代表人 */
    private String legaler;
    /** 旗下债券数量 */
    private Integer companyBondCount;
    /** 数据更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date ts;

    /**
     * 主体债券数量汇总。
     */
    @Data
    public static class CompanyBondCount {
        /** 主体代码 */
        private String companyCode;
        /** 债券数量 */
        private Integer bondCount;
    }

    /**
     * 当前所在池记录。
     */
    @Data
    public static class PoolStatus {
        /** 投资池 ID */
        private Long targetPoolId;
        /** 投资池名称 */
        private String poolName;
        /** 投资池类型 */
        private String poolType;
        /** 调库批次号 */
        private String adjustBatchNo;
        /** 调库记录 ID */
        private Long adjustLogId;
        /** 入池日期 */
        private String entryDate;
        /** 债券数量 */
        private Integer bondCount;
    }

    /**
     * 主体旗下债券记录。
     */
    @Data
    public static class CompanyBond {
        /** 证券代码 */
        private String windCode;
        /** 证券简称 */
        private String shortName;
        /** 证券全称 */
        private String fullName;
        /** 证券类型编码 */
        private String securityType;
        /** 证券类型名称 */
        private String securityTypeName;
        /** 投资池 ID */
        private Long targetPoolId;
        /** 投资池名称 */
        private String poolName;
        /** 入池日期 */
        private String entryDate;
    }

    /**
     * 当前池数据聚合对象。
     */
    @Data
    public static class PoolStatusBundle {
        /** 当前主体所在池 */
        private List<PoolStatus> companyCurrentPools = new ArrayList<>();
        /** 当前主体旗下债券所在池 */
        private List<PoolStatus> companyBondCurrentPools = new ArrayList<>();
    }
}
