package com.znty.rrs.service;

import com.znty.rrs.entity.bo.IpAdjustLogBo;
import org.springframework.beans.BeanUtils;

/** 同券关联调库记录构建，保留来源项的流程、批次及业务信息。 */
public final class RelatedMarketAdjustHelper {

    /** 关联代码调库记录的调整类型。 */
    public static final String ADJUST_TYPE = "关联调整";

    private RelatedMarketAdjustHelper() {
    }

    /** 从来源记录构建另一市场代码的独立调库记录，不复制数据库主键。 */
    public static IpAdjustLogBo buildRelatedLog(IpAdjustLogBo source, String code,
                                                String name, String type) {
        IpAdjustLogBo related = new IpAdjustLogBo();
        BeanUtils.copyProperties(source, related);
        related.setId(null);
        related.setAdjustLogId(null);
        related.setSecurityCode(code);
        related.setSecurityShortName(name);
        related.setSecurityType(type);
        related.setAdjustType(ADJUST_TYPE);
        return related;
    }
}
