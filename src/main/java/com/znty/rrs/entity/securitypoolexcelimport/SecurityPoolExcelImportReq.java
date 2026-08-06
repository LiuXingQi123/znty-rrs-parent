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
    /** 是否首先清空目标池（调入时生效：差集出库 + 批量出库流程） */
    private Boolean clearTarget;
    /** 是否允许联动与互斥调整 */
    private Boolean allowLinkMutex;
    /** 调整建议（提交弹窗） */
    private String adjustAdvice;
    /** 前端选定的可提交校验结果项（含流程选择） */
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
}
