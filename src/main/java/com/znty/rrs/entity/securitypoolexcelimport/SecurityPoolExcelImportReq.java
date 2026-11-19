package com.znty.rrs.entity.securitypoolexcelimport;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 证券/主体 Excel 导入请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SecurityPoolExcelImportReq extends PageRequest {

    /** 导入批次号 */
    private String impId;
    /** 导入对象类型：security=证券 / company=主体 */
    private String importType;
    /** 目标池 ID */
    private Long targetPoolId;
    /** 调整方向：in / out */
    private String direction;
    /** 清空目标池选中的池 ID */
    private List<Long> poolIds;
    /** 是否允许联动与互斥调整 */
    private Boolean allowLinkMutex;
    /** 调整建议（提交弹窗） */
    private String adjustAdvice;
    /** 提交时必填，包含逐条勾选的调整项及所选手工项的流程选择 */
    private List<SecurityPoolExcelImportCheckItemDto> checkItems;
    /** 调整原因 */
    private String adjustReason;
    /** 当前用户 ID */
    private String currentUserId;
    /** 当前用户名称 */
    private String currentUserName;
    /** 校验结果筛选：0/1/2 */
    private String chkRslt;
    /** 证券代码关键字 */
    private String keyword;
    /** 修改评级主体的导入明细 ID */
    private Long itemId;
    /** 页面选中的关联担保人或权益人代码，空字符串表示清除 */
    private String relatedCompanyCode;

    /** ABS 担保债独立选择的担保人代码 */
    private String guarantorCode;
    /** 页面选中的 ABS 自选权益人代码，空字符串表示清除 */
    private String selfSelectedRightsHolderCode;
}
