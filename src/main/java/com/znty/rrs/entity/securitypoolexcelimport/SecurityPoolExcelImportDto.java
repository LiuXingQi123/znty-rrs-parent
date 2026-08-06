package com.znty.rrs.entity.securitypoolexcelimport;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.znty.rrs.common.PageResult;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 证券/主体 Excel 导入响应（批次任务 + 校验结果快照）
 */
@Data
public class SecurityPoolExcelImportDto {

    /** 导入批次号 */
    private String impId;
    /** 批次类型 */
    private String bizType;
    /** 文件名 */
    private String fileName;
    /** 目标池 ID */
    private Long targetId;
    /** 目标池名称 */
    private String targetName;
    /** 目标池类型 */
    private String targetType;
    /** 业务模式 in/out */
    private String bizMode;
    /** 选项 JSON */
    private String optionJson;
    /** 调整原因 */
    private String reason;
    /** 总行数 */
    private Integer totalCount;
    /** 通过数 */
    private Integer passCount;
    /** 失败数 */
    private Integer failCount;
    /** 待校验数 */
    private Integer pendingCount;
    /** 批次校验结果 */
    private String chkRslt;
    /** 批次校验说明 */
    private String chkDscr;
    /** 保存结果 */
    private String saveRslt;
    /** 保存说明 */
    private String saveDscr;
    /** 提交结果扩展 */
    private String resultJson;
    /** 导入时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date impTime;
    /** 提交产生的调库批次号列表 */
    private List<String> adjustBatchNoList;
    /** 提交产生的日志 ID */
    private List<Long> logIds;
    /** 明细分页（上传首屏 / 查询） */
    private PageResult<SecurityPoolExcelImportItemDto> items;
    /** 调库校验结果（点校验后填充；含手工/联动/互斥/清空） */
    private List<SecurityPoolExcelImportCheckItemDto> checkItems;
    /** 校验结果可调整条数 */
    private Integer checkPassCount;
    /** 校验结果不可调整条数 */
    private Integer checkFailCount;
    /** 是否已完成业务校验 */
    private Boolean checkDone;
    /** 是否允许联动与互斥（来自 option_json） */
    private Boolean allowLinkMutex;
    /** 导入类型：security / company */
    private String importType;
}
