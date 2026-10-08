package com.znty.rrs.entity.tempfundcode;

import com.znty.rrs.common.PageRequest;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 基金临时代码请求，承载筛选、四项录入信息和人工状态操作。 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TempFundCodeReq extends PageRequest {
    /** 临时代码登记 ID */
    private Long id;
    /** 临时基金代码 */
    private String tempFundCode;
    /** 临时基金简称 */
    private String tempFundShortName;
    /** 临时基金市场编码 */
    private String tempMarketCode;
    /** 临时基金产品类型编码，来自 category_type=fund 的产品类型字典 */
    private String tempSecurityType;
    /** 正式基金代码，其他正式信息从主档读取 */
    private String fundCode;
    /** 正式基金代码、全称或简称的搜索关键字 */
    private String fundKeyword;
    /** 状态多选：temporary=临时 / updated=已更新 / cancelled=已取消 / deleted=已删除 */
    private List<String> statusList;
    /** 来源多选：manual=人工 / job=定时任务 / other=其他 */
    private List<String> oprtSourceList;
    /** 当前登录操作人 ID */
    private String operatorId;
}
