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

        /** 当前行可选流程列表（与单券 checkAdjust 一致，不再注入批量专用流程） */
        private List<AdjustCheckDto.FlowOption> flowOptions = new ArrayList<>();

        /**
         * 历史字段：曾表示目标池批量流程展示名。
         * 现流程与单券一致，不再回填；保留字段兼容旧前端。
         */
        private String batchFlowName;

        /**
         * 历史字段：曾表示批量流程未配置时的直通标识。
         * 现直通由所选流程定义判断；保留字段兼容旧前端。
         */
        private boolean directFlow;
    }
}
