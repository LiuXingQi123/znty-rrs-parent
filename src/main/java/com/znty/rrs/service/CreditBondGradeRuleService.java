package com.znty.rrs.service;

import com.znty.rrs.entity.bo.CreditBondInnerRatingGradeBo;
import com.znty.rrs.entity.bo.CreditBondPoolGradeRuleBo;
import com.znty.rrs.entity.bo.CreditBondTermBucketBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.creditbondgraderule.CreditBondGradeRuleDto;
import com.znty.rrs.entity.creditbondgraderule.CreditBondGradeRuleReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CreditBondGradeRuleMapper;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 主体内评分档规则服务，维护信用债期限、主体内评分档与投资池的关系矩阵
 */
@Service
public class CreditBondGradeRuleService {

    /** 矩阵关系启用状态 */
    private static final int ENABLED = 1;

    /** 主体内评分档规则数据访问组件 */
    @Resource
    private CreditBondGradeRuleMapper creditBondGradeRuleMapper;

    /**
     * 查询主体内评分档规则矩阵
     */
    public CreditBondGradeRuleDto queryGradeRuleMatrix(CreditBondGradeRuleReq req) {
        // 查询矩阵基础配置
        List<CreditBondTermBucketBo> termBuckets = creditBondGradeRuleMapper.queryEnabledTermBucketList();
        List<CreditBondInnerRatingGradeBo> ratingGrades = creditBondGradeRuleMapper.queryEnabledRatingGradeList();
        List<InvestmentPoolBo> pools = creditBondGradeRuleMapper.queryCreditBondPoolList();
        List<CreditBondPoolGradeRuleBo> rules = creditBondGradeRuleMapper.queryEnabledRuleList();
        // 组装矩阵返回对象
        return buildMatrixDto(termBuckets, ratingGrades, pools, rules);
    }

    /**
     * 保存主体内评分档规则矩阵
     */
    @Transactional(rollbackFor = Exception.class)
    public CreditBondGradeRuleDto editGradeRuleMatrix(CreditBondGradeRuleReq req) {
        if (req == null) {
            throw new BizException("请求参数不能为空");
        }
        List<CreditBondGradeRuleReq.RuleItem> items = req.getRules();
        if (items == null) {
            items = new ArrayList<>();
        }
        // 查询并校验矩阵基础配置
        List<CreditBondTermBucketBo> termBuckets = creditBondGradeRuleMapper.queryEnabledTermBucketList();
        List<CreditBondInnerRatingGradeBo> ratingGrades = creditBondGradeRuleMapper.queryEnabledRatingGradeList();
        List<InvestmentPoolBo> pools = creditBondGradeRuleMapper.queryCreditBondPoolList();
        // 构建待保存关系
        List<CreditBondPoolGradeRuleBo> rules = buildRulesForSave(items, termBuckets, ratingGrades, pools);
        creditBondGradeRuleMapper.deleteAllRule();
        if (!rules.isEmpty()) {
            creditBondGradeRuleMapper.addRuleList(rules);
        }
        return queryGradeRuleMatrix(req);
    }

    /**
     * 组装矩阵返回对象
     */
    private CreditBondGradeRuleDto buildMatrixDto(List<CreditBondTermBucketBo> termBuckets,
                                                  List<CreditBondInnerRatingGradeBo> ratingGrades,
                                                  List<InvestmentPoolBo> pools,
                                                  List<CreditBondPoolGradeRuleBo> rules) {
        CreditBondGradeRuleDto dto = new CreditBondGradeRuleDto();
        for (CreditBondTermBucketBo bucket : termBuckets) {
            CreditBondGradeRuleDto.TermBucketItem item = new CreditBondGradeRuleDto.TermBucketItem();
            item.setId(bucket.getId());
            item.setBucketCode(bucket.getBucketCode());
            item.setBucketName(bucket.getBucketName());
            item.setMinTermYear(bucket.getMinTermYear());
            item.setMinInclusive(bucket.getMinInclusive());
            item.setMaxTermYear(bucket.getMaxTermYear());
            item.setMaxInclusive(bucket.getMaxInclusive());
            item.setExpressionText(bucket.getExpressionText());
            item.setSortNo(bucket.getSortNo());
            dto.getTermBuckets().add(item);
        }
        for (CreditBondInnerRatingGradeBo grade : ratingGrades) {
            CreditBondGradeRuleDto.InnerRatingGradeItem item = new CreditBondGradeRuleDto.InnerRatingGradeItem();
            item.setId(grade.getId());
            item.setGradeCode(grade.getGradeCode());
            item.setGradeName(grade.getGradeName());
            item.setSortNo(grade.getSortNo());
            dto.getRatingGrades().add(item);
        }
        for (InvestmentPoolBo pool : pools) {
            CreditBondGradeRuleDto.PoolOptionItem item = new CreditBondGradeRuleDto.PoolOptionItem();
            item.setPoolId(pool.getId());
            item.setPoolCode(pool.getPoolCode());
            item.setPoolName(pool.getPoolName());
            item.setPoolFullName("信用债大库/" + pool.getPoolName());
            item.setInnerSort(pool.getInnerSort());
            dto.getPoolOptions().add(item);
        }
        for (CreditBondPoolGradeRuleBo rule : rules) {
            CreditBondGradeRuleDto.RuleItem item = new CreditBondGradeRuleDto.RuleItem();
            item.setId(rule.getId());
            item.setTermBucketId(rule.getTermBucketId());
            item.setInnerRatingGradeId(rule.getInnerRatingGradeId());
            item.setPoolId(rule.getPoolId());
            item.setPoolCodeSnapshot(rule.getPoolCodeSnapshot());
            item.setPoolNameSnapshot(rule.getPoolNameSnapshot());
            item.setSortNo(rule.getSortNo());
            dto.getRules().add(item);
        }
        return dto;
    }

    /**
     * 构建待保存的矩阵关系
     */
    private List<CreditBondPoolGradeRuleBo> buildRulesForSave(List<CreditBondGradeRuleReq.RuleItem> items,
                                                              List<CreditBondTermBucketBo> termBuckets,
                                                              List<CreditBondInnerRatingGradeBo> ratingGrades,
                                                              List<InvestmentPoolBo> pools) {
        // 构建期限分组校验映射
        Map<Long, CreditBondTermBucketBo> termBucketMap = buildTermBucketMap(termBuckets);
        // 构建主体内评分档校验映射
        Map<Long, CreditBondInnerRatingGradeBo> ratingGradeMap = buildRatingGradeMap(ratingGrades);
        // 构建信用债投资池校验映射
        Map<Long, InvestmentPoolBo> poolMap = buildPoolMap(pools);
        List<CreditBondPoolGradeRuleBo> rules = new ArrayList<>();
        Set<String> uniqueKeys = new HashSet<>();
        Date now = new Date();
        for (CreditBondGradeRuleReq.RuleItem item : items) {
            if (item == null) {
                continue;
            }
            Long termBucketId = item.getTermBucketId();
            Long ratingGradeId = item.getInnerRatingGradeId();
            Long poolId = item.getPoolId();
            if (!termBucketMap.containsKey(termBucketId)) {
                throw new BizException("期限分组不存在或已停用，termBucketId=" + termBucketId);
            }
            if (!ratingGradeMap.containsKey(ratingGradeId)) {
                throw new BizException("主体内评分档不存在或已停用，innerRatingGradeId=" + ratingGradeId);
            }
            InvestmentPoolBo pool = poolMap.get(poolId);
            if (pool == null) {
                throw new BizException("投资池不属于信用债大库一级库至五级库，poolId=" + poolId);
            }
            String uniqueKey = termBucketId + "_" + ratingGradeId + "_" + poolId;
            if (!uniqueKeys.add(uniqueKey)) {
                continue;
            }
            CreditBondPoolGradeRuleBo rule = new CreditBondPoolGradeRuleBo();
            rule.setTermBucketId(termBucketId);
            rule.setInnerRatingGradeId(ratingGradeId);
            rule.setPoolId(poolId);
            rule.setPoolCodeSnapshot(pool.getPoolCode());
            rule.setPoolNameSnapshot(pool.getPoolName());
            rule.setEnabled(ENABLED);
            rule.setSortNo(pool.getInnerSort());
            rule.setCrteTime(now);
            rule.setUpdtTime(now);
            rules.add(rule);
        }
        return rules;
    }

    /**
     * 构建期限分组映射
     */
    private Map<Long, CreditBondTermBucketBo> buildTermBucketMap(List<CreditBondTermBucketBo> termBuckets) {
        Map<Long, CreditBondTermBucketBo> map = new HashMap<>();
        for (CreditBondTermBucketBo item : termBuckets) {
            map.put(item.getId(), item);
        }
        return map;
    }

    /**
     * 构建主体内评分档映射
     */
    private Map<Long, CreditBondInnerRatingGradeBo> buildRatingGradeMap(List<CreditBondInnerRatingGradeBo> ratingGrades) {
        Map<Long, CreditBondInnerRatingGradeBo> map = new HashMap<>();
        for (CreditBondInnerRatingGradeBo item : ratingGrades) {
            map.put(item.getId(), item);
        }
        return map;
    }

    /**
     * 构建投资池映射
     */
    private Map<Long, InvestmentPoolBo> buildPoolMap(List<InvestmentPoolBo> pools) {
        Map<Long, InvestmentPoolBo> map = new HashMap<>();
        for (InvestmentPoolBo item : pools) {
            map.put(item.getId(), item);
        }
        return map;
    }
}
