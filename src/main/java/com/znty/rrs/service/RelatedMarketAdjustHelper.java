package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.CategoryType;
import com.znty.rrs.common.enums.SecurityStatus;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.common.SecurityMarketDto;
import com.znty.rrs.exception.BizException;
import org.springframework.beans.BeanUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * 核对同券不同市场的调库范围，并为各实际代码构建调库记录
 *
 * 关联债券的摘牌和在池状态按各自代码判断，参与本次调整的代码仍按组提交
 * 关联调库记录沿用来源记录的流程、批次和调整原因
 */
public final class RelatedMarketAdjustHelper {

    /** 关联代码调库记录的调整类型 */
    public static final String ADJUST_TYPE = "关联调整";

    /** 已退市或已过摘牌日的债券校验提示 */
    static final String BOND_EXPIRED = "债券已到期";

    /**
     * 限制关联市场调库日志工具类的实例化
     */
    private RelatedMarketAdjustHelper() {
    }

    /**
     * 根据证券状态和摘牌日判断是否已退市或到期
     *
     * 执行顺序：
     * 1. 状态为退市时直接判定，其他情况检查摘牌日是否早于当天
     *
     * @param security 当前实际代码的主档
     * @return true=状态为退市或摘牌日早于当天，false=状态未退市且摘牌日为空或未早于当天
     */
    static boolean isDelistedOrExpired(SecurityInfoBo security) {
        String delistDate = security.getDelistDate();
        return SecurityStatus.DELISTED.getCode().equals(security.getSecurityStatus())
                || (delistDate != null && !delistDate.isEmpty()
                && delistDate.compareTo(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)) < 0);
    }

    /**
     * 复核实际代码是否为已退市或到期的债券
     *
     * 执行顺序：
     * 1. 主档缺失或未到期且未退市时直接返回 false，其余情况按品种大类确认是否为债券
     *
     * @param security 当前实际代码的主档
     * @param findCategory 按证券类型读取品种大类
     * @return true=主档存在且为已退市或到期的债券，false=主档缺失、证券非债券或未命中退市到期规则
     */
    static boolean isExpiredBond(SecurityInfoBo security, Function<String, String> findCategory) {
        return security != null && isDelistedOrExpired(security)
                && CategoryType.BOND.getCode().equals(findCategory.apply(security.getSecurityType()));
    }

    /**
     * 判断调入结果是否命中已退市或到期的阻断规则
     *
     * 执行顺序：
     * 1. 调入项有失败原因时检查是否包含「债券已到期」
     *
     * @param adjustMode 调入或调出
     * @param failures 当前调库项的失败原因
     * @return true=调入项包含到期原因，false=调出项或调入项未包含到期原因
     */
    static boolean isExpiredInbound(String adjustMode, List<String> failures) {
        return AdjustMode.IN.getCode().equals(adjustMode)
                && failures != null && failures.contains(BOND_EXPIRED);
    }

    /**
     * 找出普通调库或自动补债时无需参与整组校验的关联债券，并给出原因
     *
     * 执行顺序：
     * 1. 品种大类为非债券时返回 null
     * 2. 调出时未在目标池则返回不在池的原因，其余情况检查退市或到期状态
     *
     * @param security 关联债券主档
     * @param categoryType 关联证券的品种大类
     * @param adjustMode 调入或调出
     * @param inTargetPool 当前实际代码是否已在目标池
     * @return 返回调出时不在池、已退市或到期的原因，非债券或未命中这些条件时返回 null
     */
    static String inactiveBondReason(SecurityInfoBo security, String categoryType, String adjustMode,
                                     boolean inTargetPool) {
        if (!CategoryType.BOND.getCode().equals(categoryType)) {
            return null;
        }
        boolean outbound = AdjustMode.OUT.getCode().equals(adjustMode);
        if (outbound && !inTargetPool) {
            return "当前不在目标池";
        }
        return isDelistedOrExpired(security) ? BOND_EXPIRED : null;
    }

    /**
     * 将自动补债任务的跳过原因放在来源调整项上
     *
     * 执行顺序：
     * 1. 复制已有提示，没有提示时创建空列表
     * 2. 按方向生成本次跳过的说明，附上证券代码和目标池后加入提示
     *
     * @param warnings 已有提示
     * @param code 本次跳过的代码
     * @param poolName 跳过项所属的目标池
     * @param reason 本次跳过的原因
     * @param adjustMode 调入或调出
     * @return 保留原提示并新增跳过原因的列表
     */
    static List<String> withSkipWarning(List<String> warnings, String code, String poolName,
                                        String reason, String adjustMode) {
        List<String> result = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        String detail = BOND_EXPIRED.equals(reason) ? "已退市或到期" : reason;
        if (BOND_EXPIRED.equals(reason) && AdjustMode.OUT.getCode().equals(adjustMode)) {
            detail += "但仍在池，本次跳过，需单独清理";
        } else {
            detail += "，本次跳过";
        }
        result.add("同券关联证券[" + code + "]在投资池[" + poolName + "]" + detail);
        return result;
    }

    /**
     * 核对同券调入组的提交清单
     *
     * 执行顺序：
     * 1. 没有调入项时结束，否则汇总主券及关联码
     * 2. 普通调库复核退市和到期状态，主券或实际提交的代码命中时终止提交，未提交的到期关联码从必需清单排除
     * 3. 有关联码时逐池核对调入清单，缺少代码时列出缺项并终止提交，Excel 沿用校验后的清单
     *
     * @param mainCode 本次发起调库的主券
     * @param relatedCodes 本次同券关联代码
     * @param inboundCodes 按目标池组织的实际调入代码
     * @param excelImport 是否为 Excel 导入
     * @param findSecurity 按证券代码取得当前主档
     * @param findCategory 按证券类型读取品种大类
     */
    static void validateInboundGroup(String mainCode, List<SecurityMarketDto> relatedCodes,
                                     Map<Long, Set<String>> inboundCodes, boolean excelImport,
                                     Function<String, SecurityInfoBo> findSecurity,
                                     Function<String, String> findCategory) {
        if (inboundCodes.isEmpty()) {
            return;
        }
        Set<String> requiredCodes = new TreeSet<>();
        requiredCodes.add(mainCode);
        if (relatedCodes != null) {
            for (SecurityMarketDto related : relatedCodes) {
                requiredCodes.add(related.getWindCode());
            }
        }
        if (!excelImport) {
            Set<String> submittedCodes = new TreeSet<>();
            inboundCodes.values().forEach(submittedCodes::addAll);
            Set<String> codesToCheck = new TreeSet<>(requiredCodes);
            codesToCheck.addAll(submittedCodes);
            for (String code : codesToCheck) {
                // 普通申请只允许提交仍可调入的同券代码
                if (isExpiredBond(findSecurity.apply(code), findCategory)) {
                    if (submittedCodes.contains(code) || mainCode.equals(code)) {
                        throw new BizException("证券[" + code + "]已退市或到期，不能调入，请重新校验");
                    }
                    requiredCodes.remove(code);
                }
            }
        }
        if (relatedCodes == null || relatedCodes.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, Set<String>> entry : inboundCodes.entrySet()) {
            Set<String> missingCodes = new TreeSet<>(requiredCodes);
            missingCodes.removeAll(entry.getValue());
            if (!missingCodes.isEmpty()) {
                throw new BizException("同券跨市场调入必须整组提交，目标池[" + entry.getKey()
                        + "]缺少证券：" + String.join("、", missingCodes) + "，请重新校验");
            }
        }
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
