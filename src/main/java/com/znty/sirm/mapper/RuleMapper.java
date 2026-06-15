package com.znty.sirm.mapper;

import com.znty.sirm.model.RuleCategoryBo;
import com.znty.sirm.model.RuleDefinitionBo;
import com.znty.sirm.model.RuleParamBo;
import com.znty.sirm.model.RuleParamOptionBo;
import com.znty.sirm.model.RulePresetOptionItemBo;
import com.znty.sirm.model.RulePresetOptionSetBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 规则管理数据访问接口，统一管理以下表的 SQL 操作：
 * <ul>
 *   <li>rule_definition — 规则定义</li>
 *   <li>rule_param — 规则参数</li>
 *   <li>rule_param_option — 规则参数选项</li>
 *   <li>rule_category — 规则分类</li>
 *   <li>rule_preset_option_set — 预设选项集</li>
 *   <li>rule_preset_option_item — 预设选项明细</li>
 * </ul>
 * <p>SQL 映射文件：mapper/RuleMapper.xml</p>
 */
@Mapper
public interface RuleMapper {

    // ==================== rule_definition ====================

    /** 分页查询规则列表，支持按关键字（名称/描述/参数）和状态筛选 */
    List<RuleDefinitionBo> queryRulePage(@Param("keyword") String keyword,
                                       @Param("status") String status);

    /** 按主键 ID 查询单条规则（仅未删除记录） */
    RuleDefinitionBo queryRuleById(@Param("id") Long id);

    /** 按主键 ID 列表批量查询规则（用于关联查询，避免 N+1） */
    List<RuleDefinitionBo> queryRuleByIds(@Param("ids") List<Long> ids);

    /** 新增规则定义，主键自增回填 */
    int addRule(RuleDefinitionBo rule);

    /** 按主键更新规则定义字段（仅未删除记录） */
    int editRule(RuleDefinitionBo rule);

    /** 按主键更新规则启用状态 */
    int editRuleStatus(@Param("id") Long id, @Param("status") String status);

    /** 按主键软删除（deleted_flag 置为 1） */
    int deleteRuleSoft(@Param("id") Long id);

    // ==================== rule_param ====================

    /** 按规则 ID 查询参数列表，按排序号升序 */
    List<RuleParamBo> queryParamsByRuleId(@Param("ruleId") Long ruleId);

    /** 按规则 ID 列表批量查询参数 */
    List<RuleParamBo> queryParamsByRuleIds(@Param("ruleIds") List<Long> ruleIds);

    /** 新增一条规则参数，主键自增回填 */
    int addParam(RuleParamBo param);

    /** 按规则 ID 删除该规则下的所有参数 */
    int deleteParamsByRuleId(@Param("ruleId") Long ruleId);

    // ==================== rule_param_option ====================

    /** 按参数 ID 列表批量查询选项 */
    List<RuleParamOptionBo> queryOptionsByParamIds(@Param("paramIds") List<Long> paramIds);

    /** 新增一条参数选项，主键自增回填 */
    int addParamOption(RuleParamOptionBo option);

    /** 按规则 ID 级联删除该规则下所有参数的选项 */
    int deleteParamOptionsByRuleId(@Param("ruleId") Long ruleId);

    // ==================== rule_category ====================

    /** 查询所有启用的规则分类，按排序号和 ID 升序 */
    List<RuleCategoryBo> queryCategoryList();

    // ==================== rule_preset_option_set ====================

    /** 查询所有启用的预设选项集，按排序号和 ID 升序 */
    List<RulePresetOptionSetBo> queryPresetSetList();

    // ==================== rule_preset_option_item ====================

    /** 按选项集 ID 列表批量查询预设选项明细 */
    List<RulePresetOptionItemBo> queryPresetItemList(@Param("setIds") List<Long> setIds);
}
