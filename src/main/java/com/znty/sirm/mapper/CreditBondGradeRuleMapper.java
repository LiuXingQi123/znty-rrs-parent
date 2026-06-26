package com.znty.sirm.mapper;

import com.znty.sirm.entity.bo.CreditBondInnerRatingGradeBo;
import com.znty.sirm.entity.bo.CreditBondPoolGradeRuleBo;
import com.znty.sirm.entity.bo.CreditBondTermBucketBo;
import com.znty.sirm.entity.bo.InvestmentPoolBo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

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
}
