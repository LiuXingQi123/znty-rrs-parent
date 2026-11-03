package com.znty.rrs.entity.forbiddenabspooladjust;

import com.znty.rrs.entity.bo.SecurityInfoBo;

import com.znty.rrs.entity.securitypooladjust.RatingCompanySelectionReq;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * ABS禁投池调库请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForbiddenAbsPoolAdjustReq extends PageRequest {

    /** 页面已加载的证券详情，直接供本次查询或校验使用 */
    private SecurityInfoBo securityInfo;

    /** 页面已选评级主体，直接复用名称及内外评 */
    private RatingCompanySelectionReq selectedRatingCompany;
    /** 证券代码（模糊搜索） */
    private String securityCode;
    /** 证券简称（模糊搜索） */
    private String securityShortName;
    /** 发行人（模糊搜索） */
    private String issuer;
    /**
     * 是否特征多选（勾选即筛「是」，多选 AND）：
     * guarant / inright / yx / private / abs / cj / stdCredit（标准信用债）
     */
    private List<String> bondYesFlags;
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
    /** 是否放开规则（false=信用债大库池需满足主体债入库矩阵才显示可调；true=不卡矩阵） */
    private boolean releaseRules;
}
