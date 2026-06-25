package com.znty.sirm.service;

import com.znty.sirm.mapper.RuleMapper;
import com.znty.sirm.mapper.TestCaseMapper;
import com.znty.sirm.model.RuleDefinitionBo;
import com.znty.sirm.model.RuleReq;
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

/**
 * 规则管理服务测试
 */
public class RuleServiceTest {

    /** 验证新增规则调用新增数据访问方法 */
    @Test
    public void addRule_ValidRequest_InsertsRule() {
        RuleMapper mapper = mock(RuleMapper.class);
        RuleService service = buildService(mapper);
        RuleReq req = buildReq(null);
        doAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) {
                RuleDefinitionBo rule = (RuleDefinitionBo) invocation.getArguments()[0];
                rule.setId(1L);
                return 1;
            }
        }).when(mapper).addRule(any(RuleDefinitionBo.class));
        when(mapper.queryRuleById(1L)).thenReturn(buildRule(1L));
        when(mapper.queryParamsByRuleIdsList(Collections.singletonList(1L))).thenReturn(Collections.emptyList());

        service.addRule(req);

        verify(mapper).addRule(any(RuleDefinitionBo.class));
        verify(mapper).deleteParamsByRuleId(1L);
    }

    /** 验证编辑规则调用编辑数据访问方法 */
    @Test
    public void editRule_ValidRequest_UpdatesRule() {
        RuleMapper mapper = mock(RuleMapper.class);
        RuleService service = buildService(mapper);
        RuleReq req = buildReq(2L);
        when(mapper.queryRuleById(2L)).thenReturn(buildRule(2L));
        when(mapper.queryParamsByRuleIdsList(Collections.singletonList(2L))).thenReturn(Collections.emptyList());

        service.editRule(req);

        verify(mapper).editRule(any(RuleDefinitionBo.class));
        verify(mapper).deleteParamsByRuleId(2L);
    }

    /** 构建规则服务 */
    private RuleService buildService(RuleMapper mapper) {
        RuleService service = new RuleService();
        ReflectionTestUtils.setField(service, "ruleMapper", mapper);
        ReflectionTestUtils.setField(service, "testCaseMapper", mock(TestCaseMapper.class));
        return service;
    }

    /** 构建规则保存请求 */
    private RuleReq buildReq(Long id) {
        RuleReq req = new RuleReq();
        req.setId(id);
        req.setName("评级规则");
        req.setCategory("business");
        req.setScript("return true;");
        return req;
    }

    /** 构建规则实体 */
    private RuleDefinitionBo buildRule(Long id) {
        RuleDefinitionBo rule = new RuleDefinitionBo();
        rule.setId(id);
        rule.setRuleName("评级规则");
        rule.setCategoryCode("business");
        rule.setScript("return true;");
        return rule;
    }
}
