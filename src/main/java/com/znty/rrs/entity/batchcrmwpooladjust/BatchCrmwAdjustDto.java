package com.znty.rrs.entity.batchcrmwpooladjust;

import com.znty.rrs.entity.crmwpooladjust.AdjustCheckDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * CRMW 池批量调库返回对象
 */
@Data
public class BatchCrmwAdjustDto {

    /** 调库校验结果列表 */
    private List<CheckResultItem> items = new ArrayList<>();

    /** 提交的主组合数量 */
    private Integer securityCount;

    /** 提交成功的调库项数量 */
    private Integer submitCount;

    /** 生成的调库记录 ID 列表 */
    private List<Long> logIds = new ArrayList<>();

    /**
     * 单条组合调库校验结果
     */
    @Data
    public static class CheckResultItem {

        /** 标的证券代码 */
        private String securityCode;

        /** 标的证券简称 */
        private String securityShortName;

        /** 标的证券类型 */
        private String securityType;

        /** CRMW 凭证名称 */
        private String crmwName;

        /** CRMW 凭证代码 */
        private String crmwScode;

        /** CRMW 证券类型 */
        private String crmwStype;

        /** 目标投资池 ID */
        private Long targetPoolId;

        /** 投资池路径名称 */
        private String poolName;

        /** 投资池类型 */
        private String poolType;

        /**
         * 调整方向
         *
         * 取值为调入、调出
         */
        private String adjustMode;

        /**
         * 调整项来源
         *
         * manual=手工、linkage=联动、mutex=互斥、related=多市场关联码
         */
        private String itemTag;

        /** 调库分组 Key */
        private String adjustGroupKey;

        /** 触发扩批的主组合键（crmwScode|securityCode） */
        private String sourceSecurityCode;

        /** 是否可调整 */
        private boolean canAdjust;

        /** 不可调整原因列表 */
        private List<String> failReasons = new ArrayList<>();

        /** 警告原因列表 */
        private List<String> warnings = new ArrayList<>();

        /**
         * 当前行可选流程列表（与单笔 {@code checkCrmwAdjust} 一致）
         *
         * 配置了批量流程时追加并设为唯一推荐
         */
        private List<AdjustCheckDto.FlowOption> flowOptions = new ArrayList<>();
    }
}
