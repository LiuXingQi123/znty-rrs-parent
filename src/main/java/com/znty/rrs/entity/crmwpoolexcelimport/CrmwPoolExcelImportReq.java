package com.znty.rrs.entity.crmwpoolexcelimport;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * CRMW 池 Excel 导入请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CrmwPoolExcelImportReq extends PageRequest {

    /** 导入批次号 */
    private String impId;
    /** 目标 CRMW 池 ID */
    private Long targetPoolId;
    /** 调整方向：in / out */
    private String direction;
    /** 调整原因 */
    private String adjustReason;
    /** 调整意见 */
    private String adjustAdvice;
    /** 前端选定的可提交校验结果项（含流程选择） */
    private List<CrmwPoolExcelImportCheckItemDto> checkItems;
    /** 当前用户 ID */
    private String currentUserId;
    /** 当前用户名称 */
    private String currentUserName;
    /** 校验结果筛选：0/1/2 */
    private String chkRslt;
    /** CRMW 代码或证券代码关键字 */
    private String keyword;
}
