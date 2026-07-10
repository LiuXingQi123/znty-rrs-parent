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

/**
 * 评级下调判定组件（证券池/批量/禁投池/CRMW 四链路共享）。
 *
 * <p>主体/担保人评级下调：查 wind_cbondissuerrating 最新一条，比较当前 vs 前次评级（序号法）。
 * 展望评级下调：读 rrs_securityinfo.rating_outlook 是否为"负面"（本地列，wind 同步的中文字符串）。</p>
 *
 * <p>查询失败/无记录 fail open 返回 false，不阻断简易流程（H2 测试库无 wind 表、生产 wind 库不可达等场景）。</p>
 */
@Component
public class RatingDowngradeChecker {

    private static final Logger log = LoggerFactory.getLogger(RatingDowngradeChecker.class);

    /** 展望评级"负面"（wind 同步到 rrs_securityinfo.rating_outlook 的中文字符串） */
    private static final String OUTLOOK_NEGATIVE = "负面";

    @Resource
    private WindRatingMapper windRatingMapper;

    /**
     * 主体评级是否下调：按发行人代码查 wind 最新评级，比较当前 vs 前次。
     */
    public boolean isIssuerDowngraded(SecurityInfoBo securityInfo) {
        if (securityInfo == null || isBlank(securityInfo.getIssuerCode())) {
            return false;
        }
        // 按发行人代码查 wind 最新主体评级
        WindIssuerRatingBo rating = queryLatestRatingSafely(securityInfo.getIssuerCode());
        return rating != null
                && RatingComparator.isDowngraded(rating.getCreditRating(), rating.getPreCreditRating(), rating.getCreditRatingChange());
    }

    /**
     * 展望评级是否下调：读本地 rating_outlook 是否为"负面"。
     * 空/稳定/正面/null 均视为未下调。
     */
    public boolean isOutlookNegative(SecurityInfoBo securityInfo) {
        if (securityInfo == null) {
            return false;
        }
        return OUTLOOK_NEGATIVE.equals(securityInfo.getRatingOutlook());
    }

    /**
     * 担保人评级是否下调：按前端选中的担保人代码查 wind 最新评级，比较当前 vs 前次。
     * 担保人代码为空（无担保人/未选中/禁投池/CRMW 无担保人场景）返回 false。
     */
    public boolean isGuarantorDowngraded(String guarantorCode) {
        if (isBlank(guarantorCode)) {
            return false;
        }
        // 按前端选中的担保人代码查 wind 最新主体评级
        WindIssuerRatingBo rating = queryLatestRatingSafely(guarantorCode);
        return rating != null
                && RatingComparator.isDowngraded(rating.getCreditRating(), rating.getPreCreditRating(), rating.getCreditRatingChange());
    }

    /**
     * 查询 wind 最新评级，捕获跨库异常 fail open（H2 测试库无 wind 表、生产 wind 库不可达等返回 null）。
     */
    private WindIssuerRatingBo queryLatestRatingSafely(String compCode) {
        try {
            return windRatingMapper.queryLatestRating(compCode);
        } catch (DataAccessException e) {
            log.warn("查询 Wind 发行人评级失败，fail open 视为未下调：compCode={}, err={}", compCode, e.getMessage());
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
