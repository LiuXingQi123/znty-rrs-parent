package com.znty.rrs.service;

import com.znty.rrs.common.util.RatingComparator;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.WindIssuerRatingBo;
import com.znty.rrs.mapper.WindRatingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 评级下调判定组件（证券池/批量/禁投池/CRMW 四链路共享）
 *
 * 本类是评级校验组件，从 wind_cbondissuerrating 读取认可机构的最新评级，比较主体或担保人的本次和前次评级（序号法）
 * 展望评级下调：读 rrs_securityinfo.rating_outlook 是否为"负面"（本地列，wind 同步的中文字符串）
 *
 * 查询失败/无记录 fail open 返回 false，不阻断简易流程（H2 测试库无 wind 表、生产 wind 库不可达等场景）
 */
@Component
public class RatingDowngradeChecker {

    /** 记录外部评级配置和 Wind 查询异常的日志组件 */
    private static final Logger log = LoggerFactory.getLogger(RatingDowngradeChecker.class);

    /** 展望评级"负面"（wind 同步到 rrs_securityinfo.rating_outlook 的中文字符串） */
    private static final String OUTLOOK_NEGATIVE = "负面";

    /** 查询 Wind 发行人最新评级的数据库组件 */
    @Resource
    private WindRatingMapper windRatingMapper;

    /** 外部评级机构配置服务 */
    @Resource
    private ExternalRatingAgencyService externalRatingAgencyService;

    /**
     * 判断证券发行人的评级是否下调
     * 缺少可用评级或查询失败时沿用未下调的判定
     *
     * 执行顺序：
     * 1. 证券缺少发行人代码时直接按未下调处理
     * 2. 查询发行人在有效评级机构中的最新评级，按本次评级、上次评级及评级变动判断是否下调
     *
     * @param securityInfo 证券主数据
     * @return true=发行人评级下调，false=未判定为下调或没有可用评级
     */
    public boolean isIssuerDowngraded(SecurityInfoBo securityInfo) {
        // 1. 缺少发行人代码时按未下调处理
        if (securityInfo == null || isBlank(securityInfo.getIssuerCode())) {
            return false;
        }
        // 2. 读取有效外评机构的最新发行人评级并判断是否下调
        WindIssuerRatingBo rating = queryLatestRatingSafely(securityInfo.getIssuerCode());
        return rating != null
                && RatingComparator.isDowngraded(rating.getCreditRating(), rating.getPreCreditRating(), rating.getCreditRatingChange());
    }

    /**
     * 判断证券主数据中的评级展望是否为负面
     * 空/稳定/正面/null 均视为未下调
     *
     * 执行顺序：
     * 1. 有证券资料时，将本地评级展望与负面展望值比较
     *
     * @param securityInfo 证券主数据
     * @return true=评级展望为负面，false=评级展望不是负面或没有证券资料
     */
    public boolean isOutlookNegative(SecurityInfoBo securityInfo) {
        if (securityInfo == null) {
            return false;
        }
        return OUTLOOK_NEGATIVE.equals(securityInfo.getRatingOutlook());
    }

    /**
     * 判断指定担保人的评级是否下调
     * 担保人代码为空（无担保人/未选中/禁投池/CRMW 无担保人场景）返回 false
     * 仅检查本次实际选中的担保人
     *
     * 执行顺序：
     * 1. 担保人代码为空时直接按未下调处理
     * 2. 查询担保人在有效评级机构中的最新评级，按本次评级、上次评级及评级变动判断是否下调
     *
     * @param guarantorCode 担保人主体代码
     * @return true=担保人评级下调，false=未判定为下调或没有可用评级
     */
    public boolean isGuarantorDowngraded(String guarantorCode) {
        // 1. 缺少担保人代码时按未下调处理
        if (isBlank(guarantorCode)) {
            return false;
        }
        // 2. 读取所选担保人在有效外评机构中的最新评级并判断是否下调
        WindIssuerRatingBo rating = queryLatestRatingSafely(guarantorCode);
        return rating != null
                && RatingComparator.isDowngraded(rating.getCreditRating(), rating.getPreCreditRating(), rating.getCreditRatingChange());
    }

    /**
     * 查询可用于判断评级下调的最新 Wind 数据
     *
     * 执行顺序：
     * 1. 读取有效外部评级机构编码，无有效配置时记录告警并返回空值
     * 2. 按主体和机构编码查询 Wind 最新评级，查询异常时记录告警并返回空值
     *
     * @param compCode 待查询评级的主体代码
     * @return Wind 最新评级，无可用结果或查询失败时为 {@code null}
     */
    private WindIssuerRatingBo queryLatestRatingSafely(String compCode) {
        try {
            // 评级下调检查使用当前有效外部评级机构配置；空配置按既有 fail open 处理
            List<String> agencyCodes = externalRatingAgencyService.queryActiveAgencyCodeList();
            if (agencyCodes.isEmpty()) {
                log.warn("未配置有效外部评级机构，fail open 视为未下调：compCode={}", compCode);
                return null;
            }
            return windRatingMapper.queryLatestRating(compCode, agencyCodes);
        } catch (DataAccessException e) {
            log.warn("查询 Wind 发行人评级失败，fail open 视为未下调：compCode={}, err={}", compCode, e.getMessage());
            return null;
        }
    }

    /**
     * 判断字符串是否为空或只包含空白
     *
     * 执行顺序：
     * 1. 检查字符串是否为 null，或去掉首尾空白后没有内容
     *
     * @param s 待判断的字符串
     * @return true=值为空或只有空白，false=值包含非空白内容
     */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
