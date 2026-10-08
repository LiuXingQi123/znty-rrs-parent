package com.znty.rrs.entity.tempfundcode;

import com.znty.rrs.entity.bo.FundAdjustLogBo;
import com.znty.rrs.entity.bo.TempFundCodeBo;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 基金临时代码列表及选项，字典名称来自数据库。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TempFundCodeDto extends TempFundCodeBo {
    /** 临时基金产品类型名称 */
    private String tempSecurityTypeName;
    /** 正式基金产品类型名称 */
    private String securityTypeName;

    /** 新增表单的基金产品类型选项。 */
    @Data
    public static class OptionBundle {
        /** 基金大类的产品类型 */
        private List<TypeOption> securityTypes;
    }

    /** 基金产品类型字典项。 */
    @Data
    public static class TypeOption {
        /** 产品类型编码，来自 category_type=fund 的产品类型字典 */
        private String securityType;
        /** 数据库中的产品类型名称 */
        private String securityTypeName;
    }

    /** 从已有正式基金主档读取的选项。 */
    @Data
    public static class FormalFundOption {
        /** 正式基金代码 */
        private String fundCode;
        /** 正式基金全称 */
        private String fundName;
        /** 正式基金简称 */
        private String fundShortName;
        /** 正式基金市场 */
        private String marketCode;
        /** 正式基金产品类型编码，来自 category_type=fund 的产品类型字典 */
        private String securityType;
        /** 产品类型的数据库名称 */
        private String securityTypeName;
    }

    /** 转正时读取的在池记录及原入池业务信息。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PoolStatusReference extends FundAdjustLogBo {
        /** 原基金池状态主键 */
        private Long poolStatusId;
        /** 原入池调库日志 ID，用于继承附件关联 */
        private Long sourceAdjustLogId;
    }
}
