package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.RuleCategoryBo;
import com.znty.rrs.entity.bo.RuleDefinitionBo;
import com.znty.rrs.entity.bo.RuleParamBo;
import com.znty.rrs.entity.bo.RuleParamOptionBo;
import com.znty.rrs.entity.bo.RulePresetOptionItemBo;
import com.znty.rrs.entity.bo.RulePresetOptionSetBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 规则管理数据访问接口，统一管理以下表的 SQL 操作：
 * - rule_definition — 规则定义
 * - rule_param — 规则参数
 * - rule_param_option — 规则参数选项
 * - rule_category — 规则分类
 * - rule_preset_option_set — 预设选项集
 * - rule_preset_option_item — 预设选项明细
 *
 * SQL 映射文件：mapper/RuleMapper.xml
 */
@Mapper
public interface RuleMapper {

    // ==================== rule_definition ====================

    /**
     * 分页查询规则列表，支持按关键字（名称/描述/参数）和状态筛选
     *
     * @param keyword 规则查询关键字，为空时不筛选
     * @param status 规则定义状态代码，为空时不筛选
     * @return 符合条件的规则定义列表
     */
    List<RuleDefinitionBo> queryRulePage(@Param("keyword") String keyword,
                                       @Param("status") String status);

    /**
     * 按主键 ID 查询单条规则（仅未删除记录）
     *
     * @param id 规则定义 ID
     * @return 规则定义，未找到时为 null
     */
    RuleDefinitionBo queryRuleById(@Param("id") Long id);

    /**
     * 按主键 ID 列表批量查询规则（用于关联查询，避免 N+1）
     *
     * @param ids 规则定义 ID 列表
     * @return 符合条件的规则定义列表
     */
    List<RuleDefinitionBo> queryRuleByIdsList(@Param("ids") List<Long> ids);

    /**
     * 新增规则定义，主键自增回填
     *
     * @param rule 待写入的规则定义
     * @return 受影响的行数
     */
    int addRule(RuleDefinitionBo rule);

    /**
     * 按主键更新规则定义字段（仅未删除记录）
     *
     * @param rule 包含 ID 及待修改字段的规则定义
     * @return 受影响的行数
     */
    int editRule(RuleDefinitionBo rule);

    /**
     * 按主键更新规则启用状态
     *
     * @param id 规则定义 ID
     * @param status 要写入的规则状态
     * @return 受影响的行数
     */
    int editRuleStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 按主键软删除（deleted_flag 置为 1）
     *
     * @param id 规则定义 ID
     * @return 受影响的行数
     */
    int deleteRuleSoft(@Param("id") Long id);

    // ==================== rule_param ====================

    /**
     * 按规则 ID 查询参数列表，按排序号升序
     *
     * @param ruleId 规则 ID
     * @return 符合条件的规则参数列表
     */
    List<RuleParamBo> queryParamsByRuleId(@Param("ruleId") Long ruleId);

    /**
     * 按规则 ID 列表批量查询参数
     *
     * @param ruleIds 规则 ID 列表
     * @return 符合条件的规则参数列表
     */
    List<RuleParamBo> queryParamsByRuleIdsList(@Param("ruleIds") List<Long> ruleIds);

    /**
     * 新增一条规则参数，主键自增回填
     *
     * @param param 待写入的规则参数
     * @return 受影响的行数
     */
    int addParam(RuleParamBo param);

    /**
     * 按规则 ID 删除该规则下的所有参数
     *
     * @param ruleId 规则 ID
     * @return 受影响的行数
     */
    int deleteParamsByRuleId(@Param("ruleId") Long ruleId);

    // ==================== rule_param_option ====================

    /**
     * 按参数 ID 列表批量查询选项
     *
     * @param paramIds 规则参数 ID 列表
     * @return 符合条件的规则参数选项列表
     */
    List<RuleParamOptionBo> queryOptionsByParamIdsList(@Param("paramIds") List<Long> paramIds);

    /**
     * 新增一条参数选项，主键自增回填
     *
     * @param option 待写入的规则参数选项
     * @return 受影响的行数
     */
    int addParamOption(RuleParamOptionBo option);

    /**
     * 按规则 ID 级联删除该规则下所有参数的选项
     *
     * @param ruleId 规则 ID
     * @return 受影响的行数
     */
    int deleteParamOptionsByRuleId(@Param("ruleId") Long ruleId);

    // ==================== rule_category ====================

    /**
     * 查询所有启用的规则分类，按排序号和 ID 升序
     *
     * @return 符合条件的规则分类列表
     */
    List<RuleCategoryBo> queryCategoryList();

    // ==================== rule_preset_option_set ====================

    /**
     * 查询所有启用的预设选项集，按排序号和 ID 升序
     *
     * @return 符合条件的预置选项集合列表
     */
    List<RulePresetOptionSetBo> queryPresetSetList();

    // ==================== rule_preset_option_item ====================

    /**
     * 按选项集 ID 列表批量查询预设选项明细
     *
     * @param setIds 预置选项集合 ID 列表
     * @return 符合条件的预置选项条目列表
     */
    List<RulePresetOptionItemBo> queryPresetItemList(@Param("setIds") List<Long> setIds);
}
