package com.znty.rrs.service;

import com.znty.rrs.common.enums.RuleStatus;
import com.znty.rrs.common.enums.RunStatus;
import com.znty.rrs.common.enums.LogType;
import com.znty.rrs.common.enums.ParamType;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ql.util.express.DefaultContext;
import com.ql.util.express.ExpressRunner;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.RuleMapper;
import com.znty.rrs.mapper.TestCaseMapper;
import com.znty.rrs.entity.rule.CategoryDto;
import com.znty.rrs.entity.rule.PresetOptionDto;
import com.znty.rrs.entity.rule.PresetSetDto;
import com.znty.rrs.entity.bo.RuleDefinitionBo;
import com.znty.rrs.entity.rule.RuleDto;
import com.znty.rrs.entity.bo.RuleParamBo;
import com.znty.rrs.entity.bo.RuleParamOptionBo;
import com.znty.rrs.entity.bo.RulePresetOptionItemBo;
import com.znty.rrs.entity.bo.RulePresetOptionSetBo;
import com.znty.rrs.entity.rule.RuleReq;
import com.znty.rrs.entity.testcase.RuleRunResultDto;
import com.znty.rrs.entity.bo.RuleTestRunBo;
import com.znty.rrs.entity.bo.RuleTestRunLogBo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 维护风控规则并执行规则脚本
 *
 * 管理规则状态、输入参数、选项、分类和预设选项集
 * 执行时按参数定义绑定输入，再用 QLExpress 运行脚本，保存执行结果和过程日志供 {@link TestCaseService} 查询
 */
@Service
public class RuleService {

    /** 日期时间格式化（yyyy-MM-dd HH:mm:ss） */
    private static final SimpleDateFormat DATE_TIME_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /** 时间格式化（HH:mm:ss），用于执行日志 */
    private static final SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat("HH:mm:ss");

    /** 规则管理数据访问组件 */
    @Resource
    private RuleMapper ruleMapper;

    /** 测试用例数据访问组件 */
    @Resource
    private TestCaseMapper testCaseMapper;

    /** QLExpress 规则执行器 */
    @Resource
    private ExpressRunner expressRunner;

    // ==================== 规则 CRUD ====================

    /**
     * 分页查询规则列表，支持按关键字（名称/描述/参数）和状态筛选
     *
     * 采用两阶段批量加载避免 N+1 查询：先查规则 → 批量查参数 → 批量查选项
     *
     * 执行顺序：
     * 1. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 2. 批量加载规则列表的参数和选项
     * 3. 将本页记录和查询总条数一起返回
     *
     * @param req 风控规则的查询或维护请求
     * @return 风控规则详情及分页信息
     */
    public PageResult<RuleDto> queryRulePage(RuleReq req) {
        // 1. 按页码和筛选条件查询
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<RuleDefinitionBo> rules = ruleMapper.queryRulePage(req.getKeyword(), req.getStatus());
        PageInfo<RuleDefinitionBo> pageInfo = new PageInfo<>(rules);
        // 2. 批量加载规则列表的参数和选项
        Map<Long, List<Map<String, Object>>> paramMap = loadRuleParamMap(rules);
        // 3. 组装规则资料、参数和预设选项
        List<RuleDto> records = rules.stream().map(rule -> toRuleDto(rule, paramMap.get(rule.getId()))).collect(Collectors.toList());
        // 4. 返回本页数据和查询总条数
        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 按规则 ID 查询规则详情，包含关联的参数和选项列表
     *
     * 执行顺序：
     * 1. 批量加载规则列表的参数和选项
     *
     * @param req 风控规则的查询或维护请求
     * @return 风控规则详情
     */
    public RuleDto queryRuleDetail(RuleReq req) {
        RuleDefinitionBo rule = requireRule(req.getId());
        // 批量加载规则列表的参数和选项
        Map<Long, List<Map<String, Object>>> paramMap = loadRuleParamMap(Collections.singletonList(rule));
        // RuleDefinitionBo → RuleDto
        return toRuleDto(rule, paramMap.get(rule.getId()));
    }

    /**
     * 新增规则，含参数和选项的全量保存
     *
     * 执行顺序：
     * 1. 校验保存请求的必填字段（名称、脚本）
     * 2. 填充字符串默认值
     * 3. 全量替换规则的参数和选项（先删后增）
     * 4. 按 ID 查询规则并组装为完整的 RuleDto
     *
     * @param req 风控规则的查询或维护请求
     * @return 风控规则详情
     */
    @Transactional(rollbackFor = Exception.class)
    public RuleDto addRule(RuleReq req) {
        // 1. 校验保存请求的必填字段（名称、脚本）
        validateSaveReq(req);
        RuleDefinitionBo rule = new RuleDefinitionBo();
        rule.setRuleName(req.getName().trim());
        rule.setDescription(req.getDescription());
        // 2. 填充字符串默认值
        rule.setCategoryCode(defaultText(req.getCategory(), "adjust_in"));
        rule.setScript(req.getScript());
        rule.setStatus(RuleStatus.ACTIVE.getCode());
        rule.setDeletedFlag(0);
        ruleMapper.addRule(rule);
        // 3. 全量替换规则的参数和选项（先删后增）
        replaceParams(rule.getId(), req.getParamList());
        // 4. 按 ID 查询规则并组装为完整的 RuleDto
        return detailById(rule.getId());
    }

    /**
     * 编辑规则，含参数和选项的全量替换（先删后增）
     *
     * 执行顺序：
     * 1. 校验保存请求的必填字段（名称、脚本）
     * 2. 填充字符串默认值
     * 3. 全量替换规则的参数和选项（先删后增）
     * 4. 按 ID 查询规则并组装为完整的 RuleDto
     *
     * @param req 风控规则的查询或维护请求
     * @return 风控规则详情
     */
    @Transactional(rollbackFor = Exception.class)
    public RuleDto editRule(RuleReq req) {
        // 1. 校验保存请求的必填字段（名称、脚本）
        validateSaveReq(req);
        RuleDefinitionBo rule = requireRule(req.getId());
        rule.setRuleName(req.getName().trim());
        rule.setDescription(req.getDescription());
        // 2. 填充字符串默认值
        rule.setCategoryCode(defaultText(req.getCategory(), "adjust_in"));
        rule.setScript(req.getScript());
        ruleMapper.editRule(rule);
        // 3. 全量替换规则的参数和选项（先删后增）
        replaceParams(rule.getId(), req.getParamList());
        // 4. 按 ID 查询规则并组装为完整的 RuleDto
        return detailById(rule.getId());
    }

    /**
     * 更新规则启用状态（active / disabled）
     *
     * 执行顺序：
     * 1. 检查规则ID不能为空
     * 2. 规则状态不合法时终止处理
     * 3. 规则不存在时终止处理
     * 4. 按 ID 查询规则并组装为完整的 RuleDto
     *
     * @param req 风控规则的查询或维护请求
     * @return 风控规则详情
     */
    @Transactional(rollbackFor = Exception.class)
    public RuleDto editRuleStatus(RuleReq req) {
        if (req.getId() == null) {
            throw new BizException("规则ID不能为空");
        }
        if (!RuleStatus.ACTIVE.getCode().equals(req.getStatus()) && !RuleStatus.DISABLED.getCode().equals(req.getStatus())) {
            throw new BizException("规则状态不合法");
        }
        int updated = ruleMapper.editRuleStatus(req.getId(), req.getStatus());
        if (updated == 0) {
            throw new BizException("规则不存在");
        }
        // 按 ID 查询规则并组装为完整的 RuleDto
        return detailById(req.getId());
    }

    /**
     * 软删除规则（deleted_flag 置为 1，保留数据）
     *
     * 执行顺序：
     * 1. 检查规则ID不能为空
     * 2. 删除前查出实体一并返回，便于前端展示
     * 3. 规则不存在时终止处理
     *
     * @param req 风控规则的查询或维护请求
     * @return 风控规则详情
     */
    @Transactional(rollbackFor = Exception.class)
    public RuleDto deleteRule(RuleReq req) {
        if (req.getId() == null) {
            throw new BizException("规则ID不能为空");
        }
        // 删除前查出实体一并返回，便于前端展示
        RuleDto deleted = detailById(req.getId());
        int updated = ruleMapper.deleteRuleSoft(req.getId());
        if (updated == 0) {
            throw new BizException("规则不存在");
        }
        return deleted;
    }

    // ==================== 规则查询辅助 ====================

    /**
     * 按 ID 查询规则实体，不存在则抛出业务异常
     *
     * 供本类和 TestCaseService 复用
     *
     * 执行顺序：
     * 1. 检查规则ID不能为空
     * 2. 按ID查询规则
     * 3. 规则不存在时终止处理
     *
     * @param id 登记主键
     * @return 规则
     */
    public RuleDefinitionBo requireRule(Long id) {
        if (id == null) {
            throw new BizException("规则ID不能为空");
        }
        RuleDefinitionBo rule = ruleMapper.queryRuleById(id);
        if (rule == null) {
            throw new BizException("规则不存在");
        }
        return rule;
    }

    /**
     * 按规则 ID 查询关联的参数定义列表
     *
     * 执行顺序：
     * 1. 规则ID为空时返回空列表
     * 2. 按规则ID查询参数
     *
     * @param ruleId 规则ID
     * @return 规则参数列表
     */
    public List<RuleParamBo> listParams(Long ruleId) {
        if (ruleId == null) {
            return Collections.emptyList();
        }
        return ruleMapper.queryParamsByRuleId(ruleId);
    }

    // ==================== 规则执行 ====================

    /**
     * 临时执行规则（不关联测试用例），传入规则 ID 和运行时参数
     *
     * 执行顺序：
     * 1. 检查规则ID不能为空
     * 2. 执行规则脚本并持久化运行记录和步骤日志
     *
     * @param req 风控规则的查询或维护请求
     * @return 规则执行结果
     */
    @Transactional(rollbackFor = Exception.class)
    public RuleRunResultDto executeRule(RuleReq req) {
        if (req.getId() == null) {
            throw new BizException("规则ID不能为空");
        }
        RuleDefinitionBo rule = requireRule(req.getId());
        return executeAndRecord(rule, req.getRunParams(), null);
    }

    /**
     * 执行规则脚本并持久化运行记录和步骤日志
     *
     * 核心流程：参数绑定 → QLExpress 执行 → 记录结果/异常 → 持久化 run + logs
     *
     * 执行顺序：
     * 1. 记录规则执行步骤
     * 2. 持久化规则执行日志
     *
     * @param rule 规则定义实体
     * @param params 运行时参数键值对
     * @param caseId 关联测试用例 ID（临时执行时为 null）
     * @return 执行结果（状态、输出、步骤日志）
     */
    @Transactional(rollbackFor = Exception.class)
    public RuleRunResultDto executeAndRecord(RuleDefinitionBo rule, Map<String, String> params, Long caseId) {
        Date startTime = new Date();
        RuleRunResultDto result = new RuleRunResultDto();
        // 1. 记录规则执行步骤
        addLog(result, startTime, LogType.INFO.getCode(), "开始执行规则: " + rule.getRuleName());

        Map<String, String> safeParams = params == null ? Collections.emptyMap() : params;
        safeParams.forEach((key, value) ->
                // 记录规则执行步骤
                addLog(result, new Date(), LogType.INFO.getCode(), "参数绑定: " + key + " = " + value));

        String output;
        String errorMessage;
        String status;
        try {
            // 调用 QLExpress 引擎执行规则脚本
            Object executeResult = executeScript(rule, safeParams);
            output = String.valueOf(executeResult == null ? "(void)" : executeResult);
            // 记录规则执行步骤
            addLog(result, new Date(), LogType.SUCCESS.getCode(), "执行完成，返回值: " + output);
            status = RunStatus.PASS.getCode();
            errorMessage = null;
        } catch (Exception e) {
            status = RunStatus.FAIL.getCode();
            errorMessage = e.getMessage();
            output = errorMessage;
            // 记录规则执行步骤
            addLog(result, new Date(), LogType.ERROR.getCode(), "执行异常: " + errorMessage);
        }

        Date finishTime = new Date();
        RuleTestRunBo run = new RuleTestRunBo();
        run.setCaseId(caseId);
        run.setRuleId(rule.getId());
        run.setRunStatus(status);
        run.setOutput(output);
        run.setErrorMessage(errorMessage);
        run.setStartTime(startTime);
        run.setFinishTime(finishTime);
        testCaseMapper.addRun(run);
        // 2. 持久化规则执行日志
        saveLogs(run.getId(), result.getLogs());

        result.setStatus(status);
        result.setOutput(output);
        return result;
    }

    // ==================== 选项字典 ====================

    /**
     * 查询所有启用的规则分类
     *
     * 执行顺序：
     * 1. 读取规则分类并将每条分类的编码和名称组装成选项
     *
     * @return 所有启用的规则分类列表
     */
    public List<CategoryDto> queryCategoryList() {
        return ruleMapper.queryCategoryList().stream().map(category -> {
            CategoryDto dto = new CategoryDto();
            dto.setCode(category.getCategoryCode());
            dto.setName(category.getCategoryName());
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 查询规则参数可用的预设选项集
     *
     * 执行顺序：
     * 1. 查询所有启用的预设选项集，没有启用项时返回空列表
     * 2. 按选项集 ID 批量查询选项并分组
     * 3. 为每个选项集组装名称和选项列表
     *
     * @return 启用的预设选项集及对应选项，无启用项时为空列表
     */
    public List<PresetSetDto> queryPresetSetList() {
        List<RulePresetOptionSetBo> sets = ruleMapper.queryPresetSetList();
        if (sets.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> setIds = sets.stream().map(RulePresetOptionSetBo::getId).collect(Collectors.toList());
        Map<Long, List<RulePresetOptionItemBo>> itemMap = ruleMapper.queryPresetItemList(setIds).stream()
                .collect(Collectors.groupingBy(RulePresetOptionItemBo::getSetId));
        return sets.stream().map(set -> {
            PresetSetDto dto = new PresetSetDto();
            dto.setId(set.getId());
            dto.setName(set.getSetName());
            // 选项同时带编码和中文名，供预设芯片与参数下拉展示
            dto.setOptions(itemMap.getOrDefault(set.getId(), Collections.emptyList()).stream()
                    // 预设选项 Bo → 值+中文名
                    .map(this::toPresetOption)
                    .collect(Collectors.toList()));
            return dto;
        }).collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    /**
     * 按 ID 查询规则并组装为完整的 RuleDto
     *
     * 执行顺序：
     * 1. 批量加载规则列表的参数和选项
     *
     * @param id 登记主键
     * @return 风控规则详情
     */
    private RuleDto detailById(Long id) {
        RuleDefinitionBo rule = requireRule(id);
        // 批量加载规则列表的参数和选项
        Map<Long, List<Map<String, Object>>> paramMap = loadRuleParamMap(Collections.singletonList(rule));
        // RuleDefinitionBo → RuleDto
        return toRuleDto(rule, paramMap.get(rule.getId()));
    }

    /**
     * 校验保存请求的必填字段（名称、脚本）
     *
     * 执行顺序：
     * 1. 检查规则名称不能为空
     * 2. 检查规则脚本不能为空
     *
     * @param req 风控规则的查询或维护请求
     */
    private void validateSaveReq(RuleReq req) {
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException("规则名称不能为空");
        }
        if (!StringUtils.hasText(req.getScript())) {
            throw new BizException("规则脚本不能为空");
        }
    }

    /**
     * 全量替换规则的参数和选项（先删后增）
     *
     * 先级联删除旧选项 → 删除旧参数 → 再按请求顺序逐条插入新参数和选项
     *
     * 执行顺序：
     * 1. 先删除规则原有的参数选项和参数记录
     * 2. 将缺省列表视作没有新增参数，逐个重建有效参数及选项
     *
     * @param ruleId 规则ID
     * @param paramList 参数列表
     */
    private void replaceParams(Long ruleId, List<Map<String, Object>> paramList) {
        ruleMapper.deleteParamOptionsByRuleId(ruleId);
        ruleMapper.deleteParamsByRuleId(ruleId);
        List<Map<String, Object>> safeList = paramList == null ? Collections.emptyList() : paramList;
        for (int i = 0; i < safeList.size(); i++) {
            Map<String, Object> dto = safeList.get(i);
            String paramName = (String) dto.get("name");
            if (!StringUtils.hasText(paramName)) {
                continue;
            }
            RuleParamBo param = new RuleParamBo();
            param.setRuleId(ruleId);
            param.setParamName(paramName.trim());
            // 填充字符串默认值
            param.setParamLabel(defaultText((String) dto.get("label"), paramName.trim()));
            // 填充字符串默认值
            param.setParamType(defaultText((String) dto.get("type"), ParamType.STRING.getCode()));
            param.setRequired(0);
            param.setSortNo(i + 1);
            ruleMapper.addParam(param);
            // 保存参数候选项列表
            saveOptions(param.getId(), dto.get("options"));
        }
    }

    /**
     * 保存参数的候选项列表，按顺序逐条写入
     *
     * 执行顺序：
     * 1. 逐项新增参数选项
     *
     * @param paramId 参数ID
     * @param optionsObj 待保存的规则参数选项列表，可包含选项映射或直接填写的选项值
     */
    @SuppressWarnings("unchecked")
    private void saveOptions(Long paramId, Object optionsObj) {
        if (!(optionsObj instanceof List)) {
            return;
        }
        List<Object> rawList = (List<Object>) optionsObj;
        int idx = 0;
        for (Object item : rawList) {
            String value = null;
            String label = null;
            if (item instanceof Map) {
                Map<?, ?> optionMap = (Map<?, ?>) item;
                Object rawValue = optionMap.get("value");
                Object rawLabel = optionMap.get("label");
                value = rawValue == null ? null : String.valueOf(rawValue);
                label = rawLabel == null ? null : String.valueOf(rawLabel);
            } else if (item != null) {
                value = String.valueOf(item);
            }
            if (!StringUtils.hasText(value)) {
                continue;
            }
            idx++;
            RuleParamOptionBo option = new RuleParamOptionBo();
            option.setParamId(paramId);
            option.setOptionValue(value.trim());
            option.setOptionLabel(StringUtils.hasText(label) ? label.trim() : value.trim());
            option.setSortNo(idx);
            ruleMapper.addParamOption(option);
        }
    }

    /**
     * 批量加载规则列表的参数和选项，返回 ruleId → 参数列表 映射
     *
     * 两阶段加载：规则 → 批量参数 → 批量选项，再按规则 ID 分组组装
     *
     * 执行顺序：
     * 1. 规则ID 列表为空时返回空映射
     * 2. 按规则ID 列表列表查询参数
     * 3. 按参数ID 列表列表查询选项
     *
     * @param rules 本次处理使用的列表
     * @return 规则参数索引
     */
    private Map<Long, List<Map<String, Object>>> loadRuleParamMap(List<RuleDefinitionBo> rules) {
        if (rules.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ruleIds = rules.stream().map(RuleDefinitionBo::getId).filter(Objects::nonNull).collect(Collectors.toList());
        if (ruleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<RuleParamBo> params = ruleMapper.queryParamsByRuleIdsList(ruleIds);
        List<Long> paramIds = params.stream().map(RuleParamBo::getId).filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, List<RuleParamOptionBo>> optionMap = paramIds.isEmpty()
                ? Collections.emptyMap()
                : ruleMapper.queryOptionsByParamIdsList(paramIds).stream()
                .collect(Collectors.groupingBy(RuleParamOptionBo::getParamId));
        return params.stream().collect(Collectors.groupingBy(
                RuleParamBo::getRuleId,
                // RuleParamBo → Map
                Collectors.mapping(param -> toParamMap(param, optionMap.get(param.getId())), Collectors.toList())
        ));
    }

    /**
     * 将规则定义和参数转换成规则详情
     *
     * 执行顺序：
     * 1. 复制规则 ID、名称、分类、脚本和状态，格式化创建时间
     * 2. 将参数列表填入规则详情，未提供参数时使用空列表
     *
     * @param rule 规则定义实体
     * @param params 参数列表
     * @return 风控规则详情
     */
    private RuleDto toRuleDto(RuleDefinitionBo rule, List<Map<String, Object>> params) {
        RuleDto dto = new RuleDto();
        dto.setId(rule.getId());
        dto.setName(rule.getRuleName());
        dto.setDescription(rule.getDescription());
        dto.setCategory(rule.getCategoryCode());
        dto.setScript(rule.getScript());
        dto.setStatus(rule.getStatus());
        dto.setCreatedAt(rule.getCrteTime() == null ? null : DATE_TIME_FORMATTER.format(rule.getCrteTime()));
        dto.setParams(new ArrayList<>(params == null ? Collections.emptyList() : params));
        return dto;
    }

    /**
     * RuleParamBo → Map，附带选项 value/label
     *
     * 执行顺序：
     * 1. 选项带 value/label，脚本仍用 value
     *
     * @param param 待转换为页面参数信息的规则参数定义
     * @param options 选项列表
     * @return 参数索引
     */
    private Map<String, Object> toParamMap(RuleParamBo param, List<RuleParamOptionBo> options) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", param.getId());
        map.put("label", param.getParamLabel());
        map.put("name", param.getParamName());
        map.put("type", param.getParamType());
        // 选项带 value/label，脚本仍用 value
        map.put("options", options == null
                ? new ArrayList<>()
                // 参数选项 Bo → {value,label}，供下拉展示中文名
                : options.stream().map(this::toParamOptionMap).collect(Collectors.toList()));
        return map;
    }

    /**
     * 预设选项 Bo → 值+中文名
     *
     * 执行顺序：
     * 1. 填入预设选项值和显示名称，名称为空时使用选项值显示
     *
     * @param item 明细
     * @return 规则预设选项
     */
    private PresetOptionDto toPresetOption(RulePresetOptionItemBo item) {
        PresetOptionDto dto = new PresetOptionDto();
        dto.setValue(item.getOptionValue());
        dto.setLabel(StringUtils.hasText(item.getOptionLabel()) ? item.getOptionLabel() : item.getOptionValue());
        return dto;
    }

    /**
     * 参数选项 Bo → {value,label}，供下拉展示中文名
     *
     * 执行顺序：
     * 1. 将选项值与显示名称写到下拉数据中，显示名称为空时回退为选项值
     *
     * @param option 选项
     * @return 参数选项索引
     */
    private Map<String, Object> toParamOptionMap(RuleParamOptionBo option) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        String value = option.getOptionValue();
        map.put("value", value);
        map.put("label", StringUtils.hasText(option.getOptionLabel()) ? option.getOptionLabel() : value);
        return map;
    }

    /**
     * 调用 QLExpress 引擎执行规则脚本
     *
     * 执行前会将字符串参数按参数定义的类型自动转换（如 number → Long/Double）
     *
     * 执行顺序：
     * 1. 按规则 ID 读取参数定义，按参数名建立索引
     * 2. 将请求中的参数按定义转换类型并放入运行上下文
     * 3. 运行规则脚本并返回引擎计算结果
     *
     * @param rule 规则定义实体
     * @param params 运行时参数键值对
     * @return QLExpress 执行规则脚本后返回的结果
     */
    private Object executeScript(RuleDefinitionBo rule, Map<String, String> params) throws Exception {
        // 1. 初始化上下文并按名称索引参数定义
        DefaultContext<String, Object> context = new DefaultContext<>();
        Map<String, RuleParamBo> paramDefineMap = listParams(rule.getId()).stream()
                .collect(Collectors.toMap(RuleParamBo::getParamName, param -> param, (left, right) -> left));
        // 2. 按参数定义转换输入值，构建规则运行上下文
        params.forEach((key, value) -> context.put(key, convertValue(value, paramDefineMap.get(key))));
        // 3. 把上下文交给规则引擎运行脚本
        return expressRunner.execute(rule.getScript(), context, null, true, false);
    }

    /**
     * 将规则参数文本转换成规则引擎使用的值
     *
     * number 类型通过 BigDecimal 精确解析，含小数点 → Double，否则 → Long
     *
     * 执行顺序：
     * 1. 输入为 null 时直接返回 null
     * 2. 数字参数通过 BigDecimal 解析，含小数点时取 doubleValue，否则先取 longValue 再转成 double
     * 3. 其他类型或数字格式无效时返回原文本
     *
     * @param value 待转换的规则参数文本
     * @param param 用于判断参数类型的规则参数定义
     * @return 数值转换成功时返回 Double，其他类型或无法解析时返回原文本，输入为 null 时返回 null
     */
    private Object convertValue(String value, RuleParamBo param) {
        // 1. 输入为 null 时直接返回 null
        if (value == null) {
            return null;
        }
        // 2. 仅对数字参数执行转换，其他类型或解析失败时保留原文本
        String type = param == null ? null : param.getParamType();
        if (ParamType.NUMBER.getCode().equals(type)) {
            try {
                BigDecimal decimal = new BigDecimal(value);
                return value.contains(".") ? decimal.doubleValue() : decimal.longValue();
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        return value;
    }

    /**
     * 向执行结果中添加一条步骤日志
     *
     * 执行顺序：
     * 1. 格式化操作时间，将时间、类型和内容追加到本次规则执行的日志列表
     *
     * @param result 本次操作的结果对象
     * @param time 本次规则执行日志的记录时间
     * @param type 类型
     * @param message 返回给调用方的处理信息
     */
    private void addLog(RuleRunResultDto result, Date time, String type, String message) {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("time", TIME_FORMATTER.format(time));
        log.put("type", type);
        log.put("msg", message);
        result.getLogs().add(log);
    }

    /**
     * 将执行步骤日志批量持久化到数据库
     *
     * 执行顺序：
     * 1. 逐项新增执行日志
     *
     * @param runId 执行ID
     * @param logs 日志列表
     */
    private void saveLogs(Long runId, List<Map<String, Object>> logs) {
        for (Map<String, Object> log : logs) {
            RuleTestRunLogBo entity = new RuleTestRunLogBo();
            entity.setRunId(runId);
            entity.setLogTime(new Date());
            entity.setLogType((String) log.get("type"));
            entity.setMessage((String) log.get("msg"));
            testCaseMapper.addRunLog(entity);
        }
    }

    /**
     * 返回非空字符串值，为空则返回默认值
     *
     * 执行顺序：
     * 1. 检查文本
     *
     * @param value 待统一空值的文本
     * @param defaultValue 原文本未填写时使用的默认文本
     * @return 非空字符串值，为空则返回默认值
     */
    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
