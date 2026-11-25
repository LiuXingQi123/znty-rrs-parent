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
 * 维护信用债期限、主体内评分档与投资池的关系
 *
 * 按启用的期限分组、内评分档和信用债一至五级库组装规则矩阵，供页面查看和编辑
 * 保存前检查矩阵引用的期限、评级和投资池是否可用，再更新对应的分档规则
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
     *
     * 执行顺序：
     * 1. 查询矩阵基础配置
     * 2. 组装矩阵返回对象
     *
     * @param req 信用债分档规则
     * @return 信用债分档规则
     */
    public CreditBondGradeRuleDto queryGradeRuleMatrix(CreditBondGradeRuleReq req) {
        // 1. 查询矩阵基础配置
        List<CreditBondTermBucketBo> termBuckets = creditBondGradeRuleMapper.queryEnabledTermBucketList();
        List<CreditBondInnerRatingGradeBo> ratingGrades = creditBondGradeRuleMapper.queryEnabledRatingGradeList();
        List<InvestmentPoolBo> pools = creditBondGradeRuleMapper.queryCreditBondPoolList();
        List<CreditBondPoolGradeRuleBo> rules = creditBondGradeRuleMapper.queryEnabledRuleList();
        // 2. 组装矩阵返回对象
        return buildMatrixDto(termBuckets, ratingGrades, pools, rules);
    }

    /**
     * 保存主体内评分档规则矩阵
     *
     * 执行顺序：
     * 1. 查询并校验矩阵基础配置
     * 2. 构建待保存关系
     *
     * @param req 信用债分档规则
     * @return 信用债分档规则
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
        // 1. 查询并校验矩阵基础配置
        List<CreditBondTermBucketBo> termBuckets = creditBondGradeRuleMapper.queryEnabledTermBucketList();
        List<CreditBondInnerRatingGradeBo> ratingGrades = creditBondGradeRuleMapper.queryEnabledRatingGradeList();
        List<InvestmentPoolBo> pools = creditBondGradeRuleMapper.queryCreditBondPoolList();
        // 2. 构建待保存关系
        List<CreditBondPoolGradeRuleBo> rules = buildRulesForSave(items, termBuckets, ratingGrades, pools);
        creditBondGradeRuleMapper.deleteAllRule();
        if (!rules.isEmpty()) {
            creditBondGradeRuleMapper.addRuleList(rules);
        }
        return queryGradeRuleMatrix(req);
    }

    /**
     * 组装矩阵返回对象
     *
     * 执行顺序：
     * 1. 把期限区间、主体内评分档和信用债投资池分别转成页面选项
     * 2. 附上按期限、评级和池 ID 对应的规则记录
     *
     * @param termBuckets 信用债期限区间列表
     * @param ratingGrades 本次处理使用的列表
     * @param pools 待过滤的投资池列表
     * @param rules 信用债投资池分档规则列表
     * @return 信用债分档规则
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
            item.setPoolFullName("信用债大库(new)/" + pool.getPoolName());
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
     *
     * 执行顺序：
     * 1. 构建期限分组校验映射
     * 2. 构建主体内评分档校验映射
     * 3. 构建信用债投资池校验映射
     *
     * @param items 明细列表
     * @param termBuckets 信用债期限区间列表
     * @param ratingGrades 本次处理使用的列表
     * @param pools 待过滤的投资池列表
     * @return 信用债投资池分档规则列表
     */
    private List<CreditBondPoolGradeRuleBo> buildRulesForSave(List<CreditBondGradeRuleReq.RuleItem> items,
                                                              List<CreditBondTermBucketBo> termBuckets,
                                                              List<CreditBondInnerRatingGradeBo> ratingGrades,
                                                              List<InvestmentPoolBo> pools) {
        // 1. 构建期限分组校验映射
        Map<Long, CreditBondTermBucketBo> termBucketMap = buildTermBucketMap(termBuckets);
        // 2. 构建主体内评分档校验映射
        Map<Long, CreditBondInnerRatingGradeBo> ratingGradeMap = buildRatingGradeMap(ratingGrades);
        // 3. 构建信用债投资池校验映射
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
     *
     * 执行顺序：
     * 1. 以期限分组 ID 为键保存每条期限配置，供后续按 ID 查找
     *
     * @param termBuckets 信用债期限区间列表
     * @return 按期限分组索引的分档配置
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
     *
     * 执行顺序：
     * 1. 以主体内评分档 ID 为键保存每条评级配置，供后续按 ID 查找
     *
     * @param ratingGrades 本次处理使用的列表
     * @return 评级档级索引
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
     *
     * 执行顺序：
     * 1. 以投资池 ID 为键保存各投资池记录
     *
     * @param pools 待过滤的投资池列表
     * @return 池映射
     */
    private Map<Long, InvestmentPoolBo> buildPoolMap(List<InvestmentPoolBo> pools) {
        Map<Long, InvestmentPoolBo> map = new HashMap<>();
        for (InvestmentPoolBo item : pools) {
            map.put(item.getId(), item);
        }
        return map;
    }
}
