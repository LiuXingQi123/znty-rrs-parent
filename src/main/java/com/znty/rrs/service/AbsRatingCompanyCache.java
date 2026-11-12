package com.znty.rrs.service;

import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderDto;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ABS 评级主体候选的短期复用缓存。
 *
 * <p>权益人、自选权益人候选在页面查询时一次性返回给前端，后续校验和提交通过
 * {@code selectedRatingCompany} 原样回传，后端据此复用已查出的名称、代码与内外评，
 * 避免在每一步重复查询候选和评级。缓存只在同一次交互内有效，提交后以数据库申请快照为准。
 *
 * <p>本类只负责候选数据的存取，不涉及 ABS 身份判定、调库校验或审批落档。
 */
@Component
public class AbsRatingCompanyCache {

    /** 候选仅供短期交互复用，提交后以数据库申请快照为准 */
    private static final long CANDIDATE_LIFETIME_MS = 60 * 1000L;

    /** 关联评级主体候选读取 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** 本次交互已查出的关联候选，不缓存池状态和在途状态 */
    private final Map<String, CandidateEntry<List<RelatedRatingCompanyDto>>> relatedCandidates = new ConcurrentHashMap<>();

    /** 已查出的自选主体，不与某只证券的 Wind 关系混用 */
    private final Map<String, CandidateEntry<SelfSelectedRightsHolderDto>> selfCandidates = new ConcurrentHashMap<>();

    /**
     * 保存已查询的关联候选供后续选择复用
     * 执行顺序：
     * 1. 清理过期候选并按证券保存独立副本
     * @param securityCode 证券代码
     * @param rows 已查询候选
     */
    public void rememberRelated(String securityCode, List<RelatedRatingCompanyDto> rows) {
        // 清理过期数据，候选不跨业务长期保存
        removeExpiredCandidates();
        List<RelatedRatingCompanyDto> copies = new ArrayList<>();
        if (rows != null) {
            for (RelatedRatingCompanyDto row : rows) {
                RelatedRatingCompanyDto copy = new RelatedRatingCompanyDto();
                BeanUtils.copyProperties(row, copy);
                copies.add(copy);
            }
        }
        relatedCandidates.put(securityCode, new CandidateEntry<>(copies));
    }

    /**
     * 读取本次交互已查询的关联候选
     * 执行顺序：
     * 1. 只返回有效期内的数据，接口直调或过期时由原查询入口重新读取
     * @param securityCode 证券代码
     * @return 已查询候选，未查询时返回 null
     */
    public List<RelatedRatingCompanyDto> findRelated(String securityCode) {
        CandidateEntry<List<RelatedRatingCompanyDto>> entry = relatedCandidates.get(securityCode);
        return entry == null || entry.expiresAt < System.currentTimeMillis() ? null : entry.value;
    }

    /**
     * 查询 ABS 规则提醒需要展示的普通权益人候选
     * 执行顺序：
     * 1. 优先复用本次已查出的候选，未查询或已过期时回查数据库并重新缓存
     * @param securityCode 证券代码
     * @return 普通权益人候选，无数据时返回空集合
     */
    public List<RelatedRatingCompanyDto> queryRelatedRatingCompanies(String securityCode) {
        List<RelatedRatingCompanyDto> candidates = findRelated(securityCode);
        if (candidates == null) {
            candidates = securityPoolAdjustMapper.queryRelatedRatingCompanyList(securityCode);
            rememberRelated(securityCode, candidates);
        }
        return candidates == null ? Collections.<RelatedRatingCompanyDto>emptyList() : candidates;
    }

    /**
     * 保存已查询的自选主体及其完整评级数据
     * 执行顺序：
     * 1. 清理过期候选，再按主体代码保存独立副本
     * @param rows 当前页或精确查询结果
     */
    public void rememberSelf(List<SelfSelectedRightsHolderDto> rows) {
        // 清理过期数据，页面刷新会覆盖相同主体的旧候选
        removeExpiredCandidates();
        for (SelfSelectedRightsHolderDto row : rows) {
            SelfSelectedRightsHolderDto copy = new SelfSelectedRightsHolderDto();
            BeanUtils.copyProperties(row, copy);
            selfCandidates.put(row.getCompanyCode(), new CandidateEntry<>(copy));
        }
    }

    /**
     * 读取已查询的自选主体
     * 执行顺序：
     * 1. 按主体代码读取有效期内的候选
     * @param companyCode 主体代码
     * @return 已查询候选，未查询或已过期时返回 null
     */
    public SelfSelectedRightsHolderDto findSelf(String companyCode) {
        CandidateEntry<SelfSelectedRightsHolderDto> entry = selfCandidates.get(companyCode);
        return entry == null || entry.expiresAt < System.currentTimeMillis() ? null : entry.value;
    }

    /**
     * 清理候选交互数据
     * 执行顺序：
     * 1. 删除过期项，超出交互容量时清空后由标准查询入口重新读取
     */
    private void removeExpiredCandidates() {
        long now = System.currentTimeMillis();
        relatedCandidates.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
        selfCandidates.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
        if (relatedCandidates.size() > 10000) {
            relatedCandidates.clear();
        }
        if (selfCandidates.size() > 20000) {
            selfCandidates.clear();
        }
    }

    /** 候选数据及交互有效期 */
    private static final class CandidateEntry<T> {
        /** 已由后端查询的数据 */
        private final T value;
        /** 过期时间 */
        private final long expiresAt = System.currentTimeMillis() + CANDIDATE_LIFETIME_MS;

        private CandidateEntry(T value) {
            this.value = value;
        }
    }
}
