package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.CreditBondInnerRatingGradeBo;
import com.znty.rrs.entity.bo.CreditBondPoolGradeRuleBo;
import com.znty.rrs.entity.bo.CreditBondTermBucketBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 主体内评分档规则数据访问接口
 */
@Mapper
public interface CreditBondGradeRuleMapper {

    /**
     * 查询启用的期限分组列表
     */
    List<CreditBondTermBucketBo> queryEnabledTermBucketList();

    /**
     * 查询启用的主体内评分档列表
     */
    List<CreditBondInnerRatingGradeBo> queryEnabledRatingGradeList();

    /**
     * 查询信用债大库一至五级库列表
     */
    List<InvestmentPoolBo> queryCreditBondPoolList();

    /**
     * 查询启用的矩阵关系列表
     */
    List<CreditBondPoolGradeRuleBo> queryEnabledRuleList();

    /**
     * 删除全部矩阵关系
     */
    int deleteAllRule();

    /**
     * 批量新增矩阵关系
     */
    int addRuleList(List<CreditBondPoolGradeRuleBo> rules);

    /**
     * 按主体内评分档编码 + 期限分组编码查询允许调入的投资池 ID 列表。
     *
     * <p>用于信用债大库主体债入库规则校验：给定债券的主体内评分档与期限档，
     * 查矩阵 credit_bond_pool_grade_rule 得到允许调入的池列表。
     */
    List<Long> queryAllowedPoolIdsByGradeAndBucket(@Param("gradeCode") String gradeCode,
                                                   @Param("bucketCode") String bucketCode);
}
