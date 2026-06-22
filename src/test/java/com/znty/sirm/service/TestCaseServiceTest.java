package com.znty.sirm.service;

import com.znty.sirm.mapper.TestCaseMapper;
import com.znty.sirm.model.RuleTestCaseBo;
import com.znty.sirm.model.TestCaseReq;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

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
}
