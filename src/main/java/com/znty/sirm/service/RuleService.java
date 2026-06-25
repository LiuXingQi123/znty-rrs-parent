package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ql.util.express.DefaultContext;
import com.ql.util.express.ExpressRunner;
import com.znty.sirm.common.IdRequest;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.exception.BizException;
import com.znty.sirm.mapper.RuleMapper;
import com.znty.sirm.mapper.TestCaseMapper;
import com.znty.sirm.model.CategoryDto;
import com.znty.sirm.model.PresetSetDto;
import com.znty.sirm.model.RuleDefinitionBo;
import com.znty.sirm.model.RuleDto;
import com.znty.sirm.model.RuleParamBo;
import com.znty.sirm.model.RuleParamOptionBo;
import com.znty.sirm.model.RulePresetOptionItemBo;
import com.znty.sirm.model.RulePresetOptionSetBo;
import com.znty.sirm.model.RuleReq;
import com.znty.sirm.model.RuleRunResultDto;
import com.znty.sirm.model.RuleTestRunBo;
import com.znty.sirm.model.RuleTestRunLogBo;
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
 * 规则管理服务。
 * <p>负责规则的 CRUD、QLExpress 脚本执行、参数与选项的级联维护，以及分类和预设选项集的查询。</p>
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
     * 分页查询规则列表，支持按关键字（名称/描述/参数）和状态筛选。
     * <p>采用两阶段批量加载避免 N+1 查询：先查规则 → 批量查参数 → 批量查选项。</p>
     */
    public PageResult<RuleDto> queryRulePage(RuleReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<RuleDefinitionBo> rules = ruleMapper.queryRulePage(req.getKeyword(), req.getStatus());
        PageInfo<RuleDefinitionBo> pageInfo = new PageInfo<>(rules);
        // 批量加载规则列表的参数和选项
        Map<Long, List<Map<String, Object>>> paramMap = loadRuleParamMap(rules);
        // RuleDefinitionBo → RuleDto
        List<RuleDto> records = rules.stream().map(rule -> toRuleDto(rule, paramMap.get(rule.getId()))).collect(Collectors.toList());
        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 按规则 ID 查询规则详情，包含关联的参数和选项列表。
     */
    public RuleDto queryRuleDetail(IdRequest req) {
        RuleDefinitionBo rule = requireRule(req.getId());
        // 批量加载规则列表的参数和选项
        Map<Long, List<Map<String, Object>>> paramMap = loadRuleParamMap(Collections.singletonList(rule));
        // RuleDefinitionBo → RuleDto
        return toRuleDto(rule, paramMap.get(rule.getId()));
    }

    /**
     * 新增或编辑规则，含参数和选项的全量替换（先删后增）。
     * <p>新增时默认状态为 active，编辑时保持原状态不变。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public RuleDto addOrEditRule(RuleReq req) {
        // 校验保存请求的必填字段（名称、脚本）
        validateSaveReq(req);
        RuleDefinitionBo rule;
        if (req.getId() != null) {
            rule = requireRule(req.getId());
            rule.setRuleName(req.getName().trim());
            rule.setDescription(req.getDescription());
            // 填充字符串默认值
            rule.setCategoryCode(defaultText(req.getCategory(), "business"));
            rule.setScript(req.getScript());
            ruleMapper.editRule(rule);
            // 全量替换规则的参数和选项（先删后增）
            replaceParams(rule.getId(), req.getParamList());
        } else {
            rule = new RuleDefinitionBo();
            rule.setRuleName(req.getName().trim());
            rule.setDescription(req.getDescription());
            // 填充字符串默认值
            rule.setCategoryCode(defaultText(req.getCategory(), "business"));
            rule.setScript(req.getScript());
            rule.setStatus("active");
            rule.setDeletedFlag(0);
            ruleMapper.addRule(rule);
            // 全量替换规则的参数和选项（先删后增）
            replaceParams(rule.getId(), req.getParamList());
        }
        // 按 ID 查询规则并组装为完整的 RuleDto
        return detailById(rule.getId());
    }

    /**
     * 更新规则启用状态（active / disabled）。
     */
    @Transactional(rollbackFor = Exception.class)
    public RuleDto editRuleStatus(RuleReq req) {
        if (req.getId() == null) {
            throw new BizException("规则ID不能为空");
        }
        if (!"active".equals(req.getStatus()) && !"disabled".equals(req.getStatus())) {
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
     * 软删除规则（deleted_flag 置为 1，保留数据）。
     */
    @Transactional(rollbackFor = Exception.class)
    public RuleDto deleteRule(IdRequest req) {
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
     * 按 ID 查询规则实体，不存在则抛出业务异常。
     * <p>供本类和 TestCaseService 复用。</p>
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
     * 按规则 ID 查询关联的参数定义列表。
     */
    public List<RuleParamBo> listParams(Long ruleId) {
        if (ruleId == null) {
            return Collections.emptyList();
        }
        return ruleMapper.queryParamsByRuleId(ruleId);
    }

    // ==================== 规则执行 ====================

    /**
     * 临时执行规则（不关联测试用例），传入规则 ID 和运行时参数。
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
     * 执行规则脚本并持久化运行记录和步骤日志。
     * <p>核心流程：参数绑定 → QLExpress 执行 → 记录结果/异常 → 持久化 run + logs。</p>
     *
     * @param rule   规则定义实体
     * @param params 运行时参数键值对
     * @param caseId 关联测试用例 ID（临时执行时为 null）
     * @return 执行结果（状态、输出、步骤日志）
     */
    @Transactional(rollbackFor = Exception.class)
    public RuleRunResultDto executeAndRecord(RuleDefinitionBo rule, Map<String, String> params, Long caseId) {
        Date startTime = new Date();
        RuleRunResultDto result = new RuleRunResultDto();
        // 记录规则执行步骤
        addLog(result, startTime, "info", "开始执行规则: " + rule.getRuleName());

        Map<String, String> safeParams = params == null ? Collections.emptyMap() : params;
        safeParams.forEach((key, value) ->
                // 记录规则执行步骤
                addLog(result, new Date(), "info", "参数绑定: " + key + " = " + value));

        String output;
        String errorMessage;
        String status;
        try {
            // 调用 QLExpress 引擎执行规则脚本
            Object executeResult = executeScript(rule, safeParams);
            output = String.valueOf(executeResult == null ? "(void)" : executeResult);
            // 记录规则执行步骤
            addLog(result, new Date(), "success", "执行完成，返回值: " + output);
            status = "pass";
            errorMessage = null;
        } catch (Exception e) {
            status = "fail";
            errorMessage = e.getMessage();
            output = errorMessage;
            // 记录规则执行步骤
            addLog(result, new Date(), "error", "执行异常: " + errorMessage);
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
        // 持久化规则执行日志
        saveLogs(run.getId(), result.getLogs());

        result.setStatus(status);
        result.setOutput(output);
        return result;
    }

    // ==================== 选项字典 ====================

    /**
     * 查询所有启用的规则分类
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
     * 查询所有启用的预设选项集及其选项子项
     * <p>采用批量加载：先查选项集 → 按 setId 批量查子项 → 分组组装。</p>
     */
    public List<PresetSetDto> queryPresetSetList() {
        List<RulePresetOptionSetBo> sets = ruleMapper.queryPresetSetList();
        if (sets.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> setIds = sets.stream().map(RulePresetOptionSetBo::getId).collect(Collectors.toList());
        Map<Long, List<String>> itemMap = ruleMapper.queryPresetItemList(setIds).stream()
                .collect(Collectors.groupingBy(
                        RulePresetOptionItemBo::getSetId,
                        Collectors.mapping(RulePresetOptionItemBo::getOptionValue, Collectors.toList())
                ));
        return sets.stream().map(set -> {
            PresetSetDto dto = new PresetSetDto();
            dto.setId(set.getId());
            dto.setName(set.getSetName());
            dto.setOptions(itemMap.getOrDefault(set.getId(), Collections.emptyList()));
            return dto;
        }).collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    /** 按 ID 查询规则并组装为完整的 RuleDto */
    private RuleDto detailById(Long id) {
        RuleDefinitionBo rule = requireRule(id);
        // 批量加载规则列表的参数和选项
        Map<Long, List<Map<String, Object>>> paramMap = loadRuleParamMap(Collections.singletonList(rule));
        // RuleDefinitionBo → RuleDto
        return toRuleDto(rule, paramMap.get(rule.getId()));
    }

    /** 校验保存请求的必填字段（名称、脚本） */
    private void validateSaveReq(RuleReq req) {
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException("规则名称不能为空");
        }
        if (!StringUtils.hasText(req.getScript())) {
            throw new BizException("规则脚本不能为空");
        }
    }

    /**
     * 全量替换规则的参数和选项（先删后增）。
     * <p>先级联删除旧选项 → 删除旧参数 → 再按请求顺序逐条插入新参数和选项。</p>
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
            param.setParamType(defaultText((String) dto.get("type"), "string"));
            param.setRequired(0);
            param.setSortNo(i + 1);
            ruleMapper.addParam(param);
            // 保存参数候选项列表
            saveOptions(param.getId(), dto.get("options"));
        }
    }

    /** 保存参数的候选项列表，按顺序逐条写入 */
    @SuppressWarnings("unchecked")
    private void saveOptions(Long paramId, Object optionsObj) {
        if (!(optionsObj instanceof List)) {
            return;
        }
        List<Object> rawList = (List<Object>) optionsObj;
        int idx = 0;
        for (Object item : rawList) {
            String value = item == null ? null : String.valueOf(item);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            idx++;
            RuleParamOptionBo option = new RuleParamOptionBo();
            option.setParamId(paramId);
            option.setOptionValue(value);
            option.setOptionLabel(value);
            option.setSortNo(idx);
            ruleMapper.addParamOption(option);
        }
    }

    /**
     * 批量加载规则列表的参数和选项，返回 ruleId → 参数列表 映射。
     * <p>两阶段加载：规则 → 批量参数 → 批量选项，再按规则 ID 分组组装。</p>
     */
    private Map<Long, List<Map<String, Object>>> loadRuleParamMap(List<RuleDefinitionBo> rules) {
        if (rules.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ruleIds = rules.stream().map(RuleDefinitionBo::getId).filter(Objects::nonNull).collect(Collectors.toList());
        if (ruleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<RuleParamBo> params = ruleMapper.queryParamsByRuleIds(ruleIds);
        List<Long> paramIds = params.stream().map(RuleParamBo::getId).filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, List<RuleParamOptionBo>> optionMap = paramIds.isEmpty()
                ? Collections.emptyMap()
                : ruleMapper.queryOptionsByParamIds(paramIds).stream()
                .collect(Collectors.groupingBy(RuleParamOptionBo::getParamId));
        return params.stream().collect(Collectors.groupingBy(
                RuleParamBo::getRuleId,
                // RuleParamBo → Map
                Collectors.mapping(param -> toParamMap(param, optionMap.get(param.getId())), Collectors.toList())
        ));
    }

    /** RuleDefinitionBo → RuleDto */
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

    /** RuleParamBo → Map，附带选项值列表 */
    private Map<String, Object> toParamMap(RuleParamBo param, List<RuleParamOptionBo> options) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", param.getId());
        map.put("label", param.getParamLabel());
        map.put("name", param.getParamName());
        map.put("type", param.getParamType());
        map.put("options", options == null
                ? new ArrayList<>()
                : options.stream().map(RuleParamOptionBo::getOptionValue).collect(Collectors.toList()));
        return map;
    }

    /**
     * 调用 QLExpress 引擎执行规则脚本。
     * <p>执行前会将字符串参数按参数定义的类型自动转换（如 number → Long/Double）。</p>
     */
    private Object executeScript(RuleDefinitionBo rule, Map<String, String> params) throws Exception {
        DefaultContext<String, Object> context = new DefaultContext<>();
        Map<String, RuleParamBo> paramDefineMap = listParams(rule.getId()).stream()
                .collect(Collectors.toMap(RuleParamBo::getParamName, param -> param, (left, right) -> left));
        // 转换规则参数值类型
        params.forEach((key, value) -> context.put(key, convertValue(value, paramDefineMap.get(key))));
        return expressRunner.execute(rule.getScript(), context, null, true, false);
    }

    /**
     * 根据参数定义类型将字符串值转换为 Java 对应类型。
     * <p>number 类型通过 BigDecimal 精确解析，含小数点 → Double，否则 → Long。</p>
     */
    private Object convertValue(String value, RuleParamBo param) {
        if (value == null) {
            return null;
        }
        String type = param == null ? null : param.getParamType();
        if ("number".equals(type)) {
            try {
                BigDecimal decimal = new BigDecimal(value);
                return value.contains(".") ? decimal.doubleValue() : decimal.longValue();
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        return value;
    }

    /** 向执行结果中添加一条步骤日志 */
    private void addLog(RuleRunResultDto result, Date time, String type, String message) {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("time", TIME_FORMATTER.format(time));
        log.put("type", type);
        log.put("msg", message);
        result.getLogs().add(log);
    }

    /** 将执行步骤日志批量持久化到数据库 */
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

    /** 返回非空字符串值，为空则返回默认值 */
    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
