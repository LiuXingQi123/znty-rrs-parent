package com.znty.rrs.service;

import com.znty.rrs.common.enums.TestResult;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.RuleMapper;
import com.znty.rrs.mapper.TestCaseMapper;
import com.znty.rrs.entity.bo.RuleDefinitionBo;
import com.znty.rrs.entity.bo.RuleParamBo;
import com.znty.rrs.entity.testcase.RuleRunResultDto;
import com.znty.rrs.entity.bo.RuleTestCaseBo;
import com.znty.rrs.entity.bo.RuleTestCaseParamBo;
import com.znty.rrs.entity.bo.RuleTestRunBo;
import com.znty.rrs.entity.bo.RuleTestRunLogBo;
import com.znty.rrs.entity.testcase.TestCaseDto;
import com.znty.rrs.entity.testcase.TestCaseReq;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 测试用例服务。
 * <p>负责测试用例的 CRUD、单个/批量执行，以及执行历史查询。</p>
 */
@Service
public class TestCaseService {

    /** 规则管理服务 */
    @Resource
    private RuleService ruleService;

    /** 规则管理数据访问组件 */
    @Resource
    private RuleMapper ruleMapper;

    /** 测试用例数据访问组件 */
    @Resource
    private TestCaseMapper testCaseMapper;

    /** 自身代理引用，用于批量执行时每个用例享有独立的 @Transactional */
    @Resource
    @Lazy
    private TestCaseService self;

    /**
     * 分页查询测试用例列表，包含参数绑定数据和最近执行结果。
     * <p>采用批量加载避免 N+1：用例 → 批量参数值 → 批量规则名称。</p>
     */
    public PageResult<TestCaseDto> queryTestCasePage(TestCaseReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<RuleTestCaseBo> cases = testCaseMapper.queryCasePage(req.getKeyword(), req.getResult());
        PageInfo<RuleTestCaseBo> pageInfo = new PageInfo<>(cases);
        // 批量加载测试用例的参数值
        Map<Long, Map<String, String>> paramMap = loadCaseParamMap(cases);
        // 批量加载测试用例关联的规则定义
        Map<Long, RuleDefinitionBo> ruleMap = loadRuleMap(cases);
        List<TestCaseDto> records = cases.stream()
                // 组装测试用例返回对象
                .map(c -> toDto(c, paramMap.get(c.getId()), ruleMap.get(c.getRuleId()))).collect(Collectors.toList());
        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 新增测试用例，含参数值的全量保存。
     */
    @Transactional(rollbackFor = Exception.class)
    public TestCaseDto addTestCase(TestCaseReq req) {
        // 校验保存请求的必填字段（名称、关联规则）
        validateSaveReq(req);
        // 校验并加载关联规则定义
        RuleDefinitionBo rule = ruleService.requireRule(req.getRuleId());
        RuleTestCaseBo testCase = new RuleTestCaseBo();
        testCase.setCaseName(req.getName().trim());
        testCase.setRuleId(rule.getId());
        testCase.setRuleNameSnapshot(rule.getRuleName());
        testCase.setLastResult(null);
        testCase.setLastOutput(null);
        testCaseMapper.addCase(testCase);
        // 全量替换测试用例的参数值（先删后增）
        replaceCaseParams(testCase.getId(), rule.getId(), req.getParams());
        // 按 ID 查询测试用例并组装为完整的 TestCaseDto
        return detailById(testCase.getId());
    }

    /**
     * 编辑测试用例，含参数值的全量替换（先删后增）。
     */
    @Transactional(rollbackFor = Exception.class)
    public TestCaseDto editTestCase(TestCaseReq req) {
        // 校验保存请求的必填字段（名称、关联规则）
        validateSaveReq(req);
        // 校验并加载关联规则定义
        RuleDefinitionBo rule = ruleService.requireRule(req.getRuleId());
        // 按 ID 查询测试用例实体
        RuleTestCaseBo testCase = requireCase(req.getId());
        testCase.setCaseName(req.getName().trim());
        testCase.setRuleId(rule.getId());
        testCase.setRuleNameSnapshot(rule.getRuleName());
        testCaseMapper.editCase(testCase);
        // 全量替换测试用例的参数值（先删后增）
        replaceCaseParams(testCase.getId(), rule.getId(), req.getParams());
        // 按 ID 查询测试用例并组装为完整的 TestCaseDto
        return detailById(testCase.getId());
    }

    /**
     * 仅修改测试用例名称，不涉及参数变更。
     */
    @Transactional(rollbackFor = Exception.class)
    public TestCaseDto editTestCaseName(TestCaseReq req) {
        if (req.getId() == null) {
            throw new BizException("测试用例ID不能为空");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException("测试用例名称不能为空");
        }
        int updated = testCaseMapper.editCaseName(req.getId(), req.getName().trim());
        if (updated == 0) {
            throw new BizException("测试用例不存在");
        }
        // 按 ID 查询测试用例并组装为完整的 TestCaseDto
        return detailById(req.getId());
    }

    /**
     * 物理删除测试用例及其关联的参数数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public TestCaseDto deleteTestCase(TestCaseReq req) {
        // 按 ID 查询测试用例实体
        RuleTestCaseBo testCase = requireCase(req.getId());
        // 删除前查出实体一并返回，便于前端展示
        TestCaseDto deleted = detailById(testCase.getId());
        testCaseMapper.deleteParamsByCaseId(testCase.getId());
        testCaseMapper.deleteCaseById(testCase.getId());
        return deleted;
    }

    /**
     * 执行单个测试用例。
     * <p>流程：加载用例参数 → 调用规则引擎执行 → 更新用例的最近执行结果。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public TestCaseDto runTestCase(TestCaseReq req) {
        // 按 ID 查询测试用例实体
        RuleTestCaseBo testCase = requireCase(req.getId());
        // 查询测试用例关联的规则定义
        RuleDefinitionBo rule = ruleMapper.queryRuleById(testCase.getRuleId());
        if (rule == null) {
            throw new BizException("测试用例关联规则不存在");
        }
        // 批量加载测试用例的参数值
        Map<String, String> params = loadCaseParamMap(Collections.singletonList(testCase))
                .getOrDefault(testCase.getId(), Collections.emptyMap());
        // 调用规则引擎执行并记录运行结果
        RuleRunResultDto runResult = ruleService.executeAndRecord(rule, params, testCase.getId());
        testCase.setLastResult(runResult.getStatus());
        testCase.setLastOutput(runResult.getOutput());
        testCase.setLastRunTime(new Date());
        testCaseMapper.editCaseLastResult(testCase);
        // 按 ID 查询测试用例并组装为完整的 TestCaseDto
        return detailById(testCase.getId());
    }

    /**
     * 批量执行全部测试用例。
     * <p>每个用例通过代理调用 self.runTestCase() 获得独立事务，单个失败不影响其余用例继续执行。</p>
     */
    public List<TestCaseDto> runAllTestCases() {
        List<RuleTestCaseBo> cases = testCaseMapper.queryAllCaseList();
        for (RuleTestCaseBo testCase : cases) {
            try {
                // 构建仅含 ID 的 TestCaseReq，供批量调用 runTestCase 使用
                self.runTestCase(newTestCaseReq(testCase.getId()));
            } catch (Exception e) {
                testCase.setLastResult(TestResult.FAIL.getCode());
                testCase.setLastOutput(e.getMessage());
                testCase.setLastRunTime(new Date());
                testCaseMapper.editCaseLastResult(testCase);
            }
        }
        List<RuleTestCaseBo> refreshedCases = testCaseMapper.queryAllCaseList();
        // 批量加载测试用例的参数值
        Map<Long, Map<String, String>> paramMap = loadCaseParamMap(refreshedCases);
        // 批量加载测试用例关联的规则定义
        Map<Long, RuleDefinitionBo> ruleMap = loadRuleMap(refreshedCases);
        return refreshedCases.stream()
                // 组装测试用例返回对象
                .map(c -> toDto(c, paramMap.get(c.getId()), ruleMap.get(c.getRuleId()))).collect(Collectors.toList());
    }

    /**
     * 查询指定测试用例的执行历史记录，含每次执行的步骤日志。
     */
    public List<RuleRunResultDto> queryRunHistoryList(TestCaseReq req) {
        Long caseId = req.getId();
        if (caseId == null) {
            throw new BizException("测试用例ID不能为空");
        }
        List<RuleTestRunBo> runs = testCaseMapper.queryRunsByCaseIdList(caseId);
        return runs.stream().map(run -> {
            List<RuleTestRunLogBo> logEntities = testCaseMapper.queryRunLogList(run.getId());
            List<Map<String, Object>> logMaps = logEntities.stream().map(bo -> {
                Map<String, Object> log = new LinkedHashMap<>();
                log.put("time", bo.getLogTime() == null ? null :
                        new SimpleDateFormat("HH:mm:ss").format(bo.getLogTime()));
                log.put("type", bo.getLogType());
                log.put("msg", bo.getMessage());
                return log;
            }).collect(Collectors.toList());
            return RuleRunResultDto.from(run, logMaps);
        }).collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    /** 校验保存请求的必填字段（名称、关联规则） */
    private void validateSaveReq(TestCaseReq req) {
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException("测试用例名称不能为空");
        }
        if (req.getRuleId() == null) {
            throw new BizException("关联规则不能为空");
        }
    }

    /** 按 ID 查询测试用例实体，不存在则抛出业务异常 */
    private RuleTestCaseBo requireCase(Long id) {
        if (id == null) {
            throw new BizException("测试用例ID不能为空");
        }
        RuleTestCaseBo testCase = testCaseMapper.queryCaseById(id);
        if (testCase == null) {
            throw new BizException("测试用例不存在");
        }
        return testCase;
    }

    /** 按 ID 查询测试用例并组装为完整的 TestCaseDto */
    private TestCaseDto detailById(Long id) {
        // 按 ID 查询测试用例实体
        RuleTestCaseBo testCase = requireCase(id);
        // 批量加载测试用例的参数值
        Map<String, String> params = loadCaseParamMap(Collections.singletonList(testCase))
                .getOrDefault(testCase.getId(), Collections.emptyMap());
        RuleDefinitionBo rule = testCase.getRuleId() == null
                // 查询测试用例关联的规则定义
                ? null : ruleMapper.queryRuleById(testCase.getRuleId());
        // 组装测试用例返回对象
        return toDto(testCase, params, rule);
    }

    /**
     * 全量替换测试用例的参数值（先删后增）。
     * <p>同时快照参数的显示名和类型，保证历史用例在规则参数变更后仍可复现。</p>
     */
    private void replaceCaseParams(Long caseId, Long ruleId, Map<String, String> params) {
        testCaseMapper.deleteParamsByCaseId(caseId);
        Map<String, String> safeParams = params == null ? Collections.emptyMap() : params;
        // 加载规则参数定义用于快照
        Map<String, RuleParamBo> paramDefineMap = ruleService.listParams(ruleId).stream()
                .collect(Collectors.toMap(RuleParamBo::getParamName, param -> param, (left, right) -> left));
        safeParams.forEach((name, value) -> {
            RuleParamBo define = paramDefineMap.get(name);
            RuleTestCaseParamBo param = new RuleTestCaseParamBo();
            param.setCaseId(caseId);
            param.setParamName(name);
            param.setParamValue(value);
            param.setParamLabelSnapshot(define == null ? name : define.getParamLabel());
            param.setParamTypeSnapshot(define == null ? null : define.getParamType());
            testCaseMapper.addCaseParam(param);
        });
    }

    /**
     * 批量加载测试用例的参数值，返回 caseId → (paramName → paramValue) 映射。
     * <p>使用 LinkedHashMap 保持参数插入顺序。</p>
     */
    private Map<Long, Map<String, String>> loadCaseParamMap(List<RuleTestCaseBo> cases) {
        if (cases.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> caseIds = cases.stream().map(RuleTestCaseBo::getId).filter(Objects::nonNull).collect(Collectors.toList());
        if (caseIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return testCaseMapper.queryParamsByCaseIdsList(caseIds).stream()
                .collect(Collectors.groupingBy(
                        RuleTestCaseParamBo::getCaseId,
                        Collectors.toMap(
                                RuleTestCaseParamBo::getParamName,
                                RuleTestCaseParamBo::getParamValue,
                                (left, right) -> left,
                                LinkedHashMap::new
                        )
                ));
    }

    /**
     * RuleTestCaseBo 实体 → TestCaseDto，附带参数值映射和关联规则名称。
     *
     * @param testCase 测试用例实体
     * @param params   参数值映射（paramName → paramValue）
     * @param rule     关联的规则定义（由调用方预加载，避免 N+1）
     */
    private TestCaseDto toDto(RuleTestCaseBo testCase, Map<String, String> params, RuleDefinitionBo rule) {
        TestCaseDto dto = new TestCaseDto();
        dto.setId(testCase.getId());
        dto.setName(testCase.getCaseName());
        dto.setRuleId(rule == null ? null : rule.getId());
        dto.setRuleName(rule == null ? testCase.getRuleNameSnapshot() : rule.getRuleName());
        dto.setParams(new LinkedHashMap<>(params == null ? Collections.emptyMap() : params));
        dto.setLastResult(testCase.getLastResult());
        dto.setLastOutput(testCase.getLastOutput());
        dto.setLastRunTime(testCase.getLastRunTime());
        return dto;
    }

    /**
     * 批量加载测试用例关联的规则定义，返回 ruleId → RuleDefinitionBo 映射。
     * <p>避免 N+1 查询。</p>
     */
    private Map<Long, RuleDefinitionBo> loadRuleMap(List<RuleTestCaseBo> cases) {
        if (cases.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ruleIds = cases.stream()
                .map(RuleTestCaseBo::getRuleId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ruleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return ruleMapper.queryRuleByIdsList(ruleIds).stream()
                .collect(Collectors.toMap(RuleDefinitionBo::getId, rule -> rule, (left, right) -> left));
    }

    /** 构建仅含 ID 的 TestCaseReq，供批量调用 runTestCase 使用 */
    private TestCaseReq newTestCaseReq(Long id) {
        TestCaseReq req = new TestCaseReq();
        req.setId(id);
        return req;
    }
}
