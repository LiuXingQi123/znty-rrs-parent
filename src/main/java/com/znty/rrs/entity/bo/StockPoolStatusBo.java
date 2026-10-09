package com.znty.rrs.entity.bo;

import java.util.Date;
import lombok.Data;

/** 股票当前池状态业务对象，对应 ip_pool_status_stock */
@Data
public class StockPoolStatusBo {
    /** 主键 ID */
    private Long id;
    /** 股票代码 */
    private String stockCode;
    /** 股票名称 */
    private String stockName;
    /** 股票简称 */
    private String stockShortName;
    /** 股票产品类型编码 */
    private String securityType;
    /** 冻结的行业编码 */
    private String industryCode;
    /** 冻结的行业名称 */
    private String industryName;
    /** 冻结的最新评级 */
    private String latestRating;
    /** 冻结的上次评级 */
    private String previousRating;
    /** 目标池 ID */
    private Long targetPoolId;
    /** 目标池名称 */
    private String targetPoolName;
    /** 投资池类型 */
    private String poolType;
    /** 入池时间 */
    private Date entryTime;
    /** 删除标记 */
    private Integer isDeleted;
    /** 创建时间 */
    private Date crteTime;
    /** 更新时间 */
    private Date updtTime;
    /** 来源调整记录 ID */
    private Long adjustLogId;
    /** 审核状态，仅终审通过写入 */
    private String auditStatus;
    /** 调整人 ID */
    private String adjusterId;
    /** 调整人名称 */
    private String adjusterName;
    /** 调库批次号 */
    private String adjustBatchNo;
    /** 审核完成时间 */
    private Date auditTime;
}
