package com.znty.rrs.service;

import com.znty.rrs.entity.common.GuarantorGradeDto;
import com.znty.rrs.entity.common.GuarantorGradeReq;
import com.znty.rrs.mapper.CommonMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * 公共查询业务服务测试
 */
public class CommonServiceTest {

    /** 验证担保人代码会去空、去重后一次性查询 */
    @Test
    public void queryGuarantorGradeListShouldNormalizeCodesAndQueryOnce() {
        CommonMapper mapper = mock(CommonMapper.class);
        CommonService service = new CommonService();
        ReflectionTestUtils.setField(service, "commonMapper", mapper);
        List<String> normalizedCodes = Arrays.asList("C10010", "C10008");
        GuarantorGradeDto grade = new GuarantorGradeDto();
        grade.setWindcode("C10010");
        grade.setTotalScore("1");
        when(mapper.queryGuarantorGradeList(normalizedCodes)).thenReturn(Collections.singletonList(grade));

        GuarantorGradeReq req = new GuarantorGradeReq();
        req.setWindCodes(Arrays.asList(" C10010 ", "", null, "C10008", "C10010"));
        List<GuarantorGradeDto> result = service.queryGuarantorGradeList(req);

        assertThat(result).containsExactly(grade);
        verify(mapper).queryGuarantorGradeList(normalizedCodes);
    }

    /** 验证空代码列表不访问数据库 */
    @Test
    public void queryGuarantorGradeListShouldSkipEmptyCodes() {
        CommonMapper mapper = mock(CommonMapper.class);
        CommonService service = new CommonService();
        ReflectionTestUtils.setField(service, "commonMapper", mapper);
        GuarantorGradeReq req = new GuarantorGradeReq();
        req.setWindCodes(Arrays.asList(" ", null));

        assertThat(service.queryGuarantorGradeList(req)).isEmpty();
        verifyZeroInteractions(mapper);
    }
}
