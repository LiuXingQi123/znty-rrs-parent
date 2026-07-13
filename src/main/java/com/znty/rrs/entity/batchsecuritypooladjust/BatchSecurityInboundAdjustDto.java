package com.znty.rrs.entity.batchsecuritypooladjust;


import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量证券调库返回对象
 */
@Data
public class BatchSecurityInboundAdjustDto {

    /** 调库校验结果列表 */
    private List<CheckResultItem> items = new ArrayList<>();

    /** 提交成功的证券数量 */
    private Integer securityCount;

    /** 提交成功的调库项数量 */
    private Integer submitCount;

    /** 生成的调库记录 ID 列表 */
    private List<Long> logIds = new ArrayList<>();

    /**
     * 单条证券调库校验结果
     */
    @Data
    public static class CheckResultItem {

        /** 证券代码 */
        private String securityCode;

        /** 证券简称 */
        private String securityShortName;

        /** 证券类型 */
        private String securityType;

        /** 目标投资池 ID */
        private Long targetPoolId;

        /** 投资池路径名称 */
        private String poolName;

        /** 投资池类型 */
        private String poolType;

        /** 调整方向 */
        private String adjustMode;

        /** 调整项来源 */
        private String itemTag;

        /** 调库分组 Key */
        private String adjustGroupKey;

        /** 触发扩批的主证券代码 */
        private String sourceSecurityCode;

        /** 是否可调整 */
        private boolean canAdjust;

        /** 不可调整原因列表 */
        private List<String> failReasons = new ArrayList<>();

        /** 当前行可选流程列表 */
        private List<AdjustCheckDto.FlowOption> flowOptions = new ArrayList<>();

        /** 批量流程展示名称 */
        private String batchFlowName;

        /** 是否无需审批直接生效 */
        private boolean directFlow;
    }
}
