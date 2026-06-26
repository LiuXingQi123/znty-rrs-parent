package com.znty.sirm.service;

import com.znty.sirm.mapper.TestCaseMapper;
import com.znty.sirm.mapper.RuleMapper;
import com.znty.sirm.entity.bo.RuleDefinitionBo;
import com.znty.sirm.entity.bo.RuleTestCaseBo;
import com.znty.sirm.entity.testcase.TestCaseReq;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TestCaseService 测试类。 */
public class TestCaseServiceTest {

    /** 验证分页查询会向数据访问层传递筛选条件。 */
    @Test
    public void queryTestCasePageShouldPassFiltersToMapper() {
        TestCaseMapper mapper = mock(TestCaseMapper.class);
        TestCaseService service = new TestCaseService();
        ReflectionTestUtils.setField(service, "testCaseMapper", mapper);
        TestCaseReq req = new TestCaseReq();
        req.setKeyword("评级");
        req.setResult("pass");
        when(mapper.queryCasePage("评级", "pass")).thenReturn(Collections.<RuleTestCaseBo>emptyList());

        service.queryTestCasePage(req);

        verify(mapper).queryCasePage("评级", "pass");
    }

    /** 验证新增测试用例调用新增数据访问方法。 */
    @Test
    public void addTestCase_ValidRequest_InsertsCase() {
        TestCaseMapper testCaseMapper = mock(TestCaseMapper.class);
        RuleMapper ruleMapper = mock(RuleMapper.class);
        RuleService ruleService = mock(RuleService.class);
        TestCaseService service = buildService(testCaseMapper, ruleMapper, ruleService);
        RuleDefinitionBo rule = buildRule();
        TestCaseReq req = buildReq(null);
        when(ruleService.requireRule(3L)).thenReturn(rule);
        doAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) {
                RuleTestCaseBo testCase = (RuleTestCaseBo) invocation.getArguments()[0];
                testCase.setId(11L);
                return 1;
            }
        }).when(testCaseMapper).addCase(any(RuleTestCaseBo.class));
        when(testCaseMapper.queryCaseById(11L)).thenReturn(buildCase(11L));
        when(ruleMapper.queryRuleById(3L)).thenReturn(rule);
        when(testCaseMapper.queryParamsByCaseIdsList(Collections.singletonList(11L))).thenReturn(Collections.emptyList());

        service.addTestCase(req);

        verify(testCaseMapper).addCase(any(RuleTestCaseBo.class));
        verify(testCaseMapper).deleteParamsByCaseId(11L);
    }

    /** 验证编辑测试用例调用编辑数据访问方法。 */
    @Test
    public void editTestCase_ValidRequest_UpdatesCase() {
        TestCaseMapper testCaseMapper = mock(TestCaseMapper.class);
        RuleMapper ruleMapper = mock(RuleMapper.class);
        RuleService ruleService = mock(RuleService.class);
        TestCaseService service = buildService(testCaseMapper, ruleMapper, ruleService);
        RuleDefinitionBo rule = buildRule();
        TestCaseReq req = buildReq(12L);
        when(ruleService.requireRule(3L)).thenReturn(rule);
        when(testCaseMapper.queryCaseById(12L)).thenReturn(buildCase(12L));
        when(ruleMapper.queryRuleById(3L)).thenReturn(rule);
        when(testCaseMapper.queryParamsByCaseIdsList(Collections.singletonList(12L))).thenReturn(Collections.emptyList());

        service.editTestCase(req);

        verify(testCaseMapper).editCase(any(RuleTestCaseBo.class));
        verify(testCaseMapper).deleteParamsByCaseId(12L);
    }

    /** 构建测试用例服务 */
    private TestCaseService buildService(TestCaseMapper testCaseMapper, RuleMapper ruleMapper, RuleService ruleService) {
        TestCaseService service = new TestCaseService();
        ReflectionTestUtils.setField(service, "testCaseMapper", testCaseMapper);
        ReflectionTestUtils.setField(service, "ruleMapper", ruleMapper);
        ReflectionTestUtils.setField(service, "ruleService", ruleService);
        return service;
    }

    /** 构建测试用例保存请求 */
    private TestCaseReq buildReq(Long id) {
        TestCaseReq req = new TestCaseReq();
        req.setId(id);
        req.setName("评级用例");
        req.setRuleId(3L);
        return req;
    }

    /** 构建规则实体 */
    private RuleDefinitionBo buildRule() {
        RuleDefinitionBo rule = new RuleDefinitionBo();
        rule.setId(3L);
        rule.setRuleName("评级规则");
        return rule;
    }

    /** 构建测试用例实体 */
    private RuleTestCaseBo buildCase(Long id) {
        RuleTestCaseBo testCase = new RuleTestCaseBo();
        testCase.setId(id);
        testCase.setCaseName("评级用例");
        testCase.setRuleId(3L);
        testCase.setRuleNameSnapshot("评级规则");
        return testCase;
    }
}
