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
     *
     * @return 符合条件的信用债期限分组列表
     */
    List<CreditBondTermBucketBo> queryEnabledTermBucketList();

    /**
     * 查询启用的主体内评分档列表
     *
     * @return 符合条件的信用债内评分档列表
     */
    List<CreditBondInnerRatingGradeBo> queryEnabledRatingGradeList();

    /**
     * 查询信用债大库一至五级库列表
     *
     * @return 符合条件的投资池配置列表
     */
    List<InvestmentPoolBo> queryCreditBondPoolList();

    /**
     * 查询启用的矩阵关系列表
     *
     * @return 符合条件的信用债评级矩阵规则列表
     */
    List<CreditBondPoolGradeRuleBo> queryEnabledRuleList();

    /**
     * 删除全部矩阵关系
     *
     * @return 受影响的行数
     */
    int deleteAllRule();

    /**
     * 批量新增矩阵关系
     *
     * 写入后将生成的主键回填到各条记录的 id 字段
     *
     * @param rules 待写入的信用债评级矩阵规则列表
     * @return 受影响的行数
     */
    int addRuleList(List<CreditBondPoolGradeRuleBo> rules);

    /**
     * 按主体内评分档编码 + 期限分组编码查询允许调入的投资池 ID 列表
     *
     * 用于信用债大库主体债入库规则校验：给定债券的主体内评分档与期限档，
     * 查矩阵 credit_bond_pool_grade_rule 得到允许调入的池列表
     *
     * @param gradeCode 内评分档代码
     * @param bucketCode 期限分组代码
     * @return 允许调入的投资池 ID 列表
     */
    List<Long> queryAllowedPoolIdsByGradeAndBucket(@Param("gradeCode") String gradeCode,
                                                   @Param("bucketCode") String bucketCode);
}
