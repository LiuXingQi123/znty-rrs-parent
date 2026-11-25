package com.znty.rrs.service;

import com.znty.rrs.entity.bo.IpAdjustLogBo;
import org.springframework.beans.BeanUtils;

/**
 * 为同一证券的其他市场代码构建调库记录
 *
 * 本类是关联调库辅助类，复用来源记录的流程、批次和调整原因，换成关联市场证券信息，并清空记录 ID 供调用方单独保存
 * 供证券池联动和临时代码换码使用，统一标记为关联调整
 */
public final class RelatedMarketAdjustHelper {

    /** 关联代码调库记录的调整类型 */
    public static final String ADJUST_TYPE = "关联调整";

    /**
     * 限制关联市场调库日志工具类的实例化
     */
    private RelatedMarketAdjustHelper() {
    }

    /**
     * 构建关联市场证券的独立调库记录
     *
     * 执行顺序：
     * 1. 复制原调库记录，清空两处记录 ID
     * 2. 换成关联市场的证券代码、名称和类型，并标记为关联市场调整
     *
     * @param source 来源市场已构建的调库日志
     * @param code 关联市场证券代码
     * @param name 关联市场证券简称
     * @param type 关联市场证券类型
     * @return 可独立保存的关联市场调库记录
     */
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
