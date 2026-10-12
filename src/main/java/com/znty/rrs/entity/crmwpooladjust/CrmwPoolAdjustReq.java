package com.znty.rrs.entity.crmwpooladjust;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * CRMW池调整请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CrmwPoolAdjustReq extends PageRequest {
    /** 证券身份：正式ISIN / TEMP:临时代码 */
    private String isinCode;
    /** 证券代码关键词，匹配五码 */
    private String securityKeyword;
    /** 证券代码（模糊搜索） */
    private String securityCode;
    /** 证券简称（模糊搜索） */
    private String securityShortName;
    /** 发行人（模糊搜索） */
    private String issuer;
    /**
     * 是否特征多选（勾选即筛「是」，多选 AND；可绑定证券列表使用）：
     * abs / guarant / yx / cj / private / inright
     */
    private List<String> bondYesFlags;
    /** CRMW凭证身份：正式ISIN / TEMP:临时代码 */
    private String crmwIsinCode;
    /** CRMW银行间代码快照，关联身份使用crmwIsinCode */
    private String crmwScode;
    /** CRMW 证券类型 */
    private String crmwStype;
    /** 调库方向：in=可调入库 / out=可调出库 */
    private String adjustDirection;
    /** 当前用户 ID，1 视为管理员 */
    private String currentUserId;
    /** 目标投资池 ID（从具体投资池入口查看时使用） */
    private Long targetPoolId;
    /** 调库记录 ID（查询流程步骤时使用） */
    private Long adjustLogId;
    /** 调库批次号（查询同批次共用流程步骤时使用） */
    private String adjustBatchNo;
}
