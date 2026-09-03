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

    /** 验证证券代码会去空、去重后一次性筛选合格主体并查询评分 */
    @Test
    public void queryGuarantorGradeListShouldNormalizeCodesAndQueryOnce() {
        CommonMapper mapper = mock(CommonMapper.class);
        CommonService service = new CommonService();
        ReflectionTestUtils.setField(service, "commonMapper", mapper);
        List<String> normalizedCodes = Arrays.asList("DBB001.IB", "DBB002.IB");
        GuarantorGradeDto grade = new GuarantorGradeDto();
        grade.setSecurityCode("DBB001.IB");
        grade.setWindcode("C10010");
        grade.setGuarantorTypeCode(115203000L);
        grade.setTotalScore("1");
        when(mapper.queryGuarantorGradeList(normalizedCodes)).thenReturn(Collections.singletonList(grade));

        GuarantorGradeReq req = new GuarantorGradeReq();
        req.setSecurityCodes(Arrays.asList(" DBB001.IB ", "", null, "DBB002.IB", "DBB001.IB"));
        List<GuarantorGradeDto> result = service.queryGuarantorGradeList(req);

        assertThat(result).containsExactly(grade);
        assertThat(result.get(0).getGuarantorTypeCode()).isEqualTo(115203000L);
        verify(mapper).queryGuarantorGradeList(normalizedCodes);
    }

    /** 验证空代码列表不访问数据库 */
    @Test
    public void queryGuarantorGradeListShouldSkipEmptyCodes() {
        CommonMapper mapper = mock(CommonMapper.class);
        CommonService service = new CommonService();
        ReflectionTestUtils.setField(service, "commonMapper", mapper);
        GuarantorGradeReq req = new GuarantorGradeReq();
        req.setSecurityCodes(Arrays.asList(" ", null));

        assertThat(service.queryGuarantorGradeList(req)).isEmpty();
        verifyZeroInteractions(mapper);
    }

    /** 验证符合主体类型但暂无评分的担保人仍会返回 */
    @Test
    public void queryGuarantorGradeShouldKeepEligibleGuarantorWithoutGrade() {
        CommonMapper mapper = mock(CommonMapper.class);
        CommonService service = new CommonService();
        ReflectionTestUtils.setField(service, "commonMapper", mapper);
        GuarantorGradeDto guarantor = new GuarantorGradeDto();
        guarantor.setWindcode("C10008");
        when(mapper.queryGuarantorGradeList(Collections.singletonList("DBB002.IB")))
                .thenReturn(Collections.singletonList(guarantor));

        GuarantorGradeDto result = service.queryGuarantorGrade(" DBB002.IB ", " C10008 ");

        assertThat(result).isSameAs(guarantor);
        assertThat(result.getTotalScore()).isNull();
        verify(mapper).queryGuarantorGradeList(Collections.singletonList("DBB002.IB"));
    }
}
