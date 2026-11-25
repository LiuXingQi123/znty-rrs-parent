package com.znty.rrs.entity.batchcrmwpooladjust;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * CRMW 池批量调整查询请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BatchCrmwPoolAdjustReq extends PageRequest {

    /** 当前用户 ID，1 视为管理员 */
    private String currentUserId;

    /** 投资池 ID 筛选列表 */
    private List<Long> poolIds;

    /** 目标投资池 ID */
    private Long poolId;

    /** 标的证券代码（前缀匹配） */
    private String securityCode;

    /** 标的证券简称（包含匹配） */
    private String securityShortName;

    /** CRMW 凭证代码（包含匹配） */
    private String crmwScode;

    /** CRMW 凭证名称（包含匹配） */
    private String crmwName;

    /**
     * 市场编码筛选列表，多选时匹配任一市场即可
     *
     * SSE=上海证券交易所、SZSE=深圳证券交易所、NIB=银行间市场、BSE=北京证券交易所、COMPANY=主体、OTC=场外市场、
     * HKEX=香港交易所、QDII=其他 QDII 市场、JWCW=JWCW 市场、UNKNOWN=未知、OTHER=其他
     */
    private List<String> marketCodes;

    /**
     * 标的证券特征多选（勾选即筛「是」，多个条件同时满足）
     *
     * guarant=担保、inright=含权、yx=永续、private=私募、abs=ABS、cj=次级、stdCredit=标准信用债
     */
    private List<String> bondYesFlags;
}
