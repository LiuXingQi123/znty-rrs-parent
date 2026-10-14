package com.znty.rrs.service;

import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwAdjustDto;
import com.znty.rrs.entity.batchcrmwpooladjust.BatchCrmwAdjustReq;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckDto;
import com.znty.rrs.entity.crmwpooladjust.AdjustCheckReq;
import com.znty.rrs.entity.crmwpooladjust.CrmwPoolAdjustSubmitReq;
import com.znty.rrs.entity.crmwpooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.crmwpoolexcelimport.CrmwPoolExcelImportCheckItemDto;
import com.znty.rrs.mapper.BatchCrmwPoolAdjustMapper;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证批量和 Excel 入口保留关联标的身份及展示字段。 */
public class CrmwRelatedEntryPointTest {

    /** 批量选中同券两个主组合时，关联展开不重复提交同一个组合。 */
    @Test
    public void batchCheckShouldPreferExplicitManualOverOverlappingRelated() {
        BatchCrmwPoolAdjustService service = new BatchCrmwPoolAdjustService();
        BatchCrmwPoolAdjustMapper mapper = mock(BatchCrmwPoolAdjustMapper.class);
        CrmwPoolAdjustService single = mock(CrmwPoolAdjustService.class);
        ReflectionTestUtils.setField(service, "batchCrmwPoolAdjustMapper", mapper);
        ReflectionTestUtils.setField(service, "crmwPoolAdjustService", single);
        when(mapper.queryEnabledLeafCrmwPoolCount(18L)).thenReturn(1);
        when(single.checkCrmwAdjust(any())).thenAnswer(call -> {
            AdjustCheckReq req = call.getArgument(0);
            List<AdjustCheckDto.CheckResultItem> items = new ArrayList<>();
            for (String code : Arrays.asList("A.IB", "A.SH")) {
                AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
                item.setSecurityCode(code);
                item.setCrmwScode(req.getCrmwScode());
                item.setSourceSecurityCode(req.getCrmwScode() + "|" + req.getSecurityCode());
                item.setTargetPoolId(18L);
                item.setAdjustMode("调入");
                item.setItemTag(code.equals(req.getSecurityCode()) ? "manual" : "related");
                item.setCanAdjust(true);
                items.add(item);
            }
            AdjustCheckDto dto = new AdjustCheckDto();
            dto.setItems(items);
            return dto;
        });
        BatchCrmwAdjustReq req = new BatchCrmwAdjustReq();
        req.setCurrentUserId("1");
        req.setPoolId(18L);
        req.setDirection("in");
        List<BatchCrmwAdjustReq.SecurityItem> securities = new ArrayList<>();
        for (String code : Arrays.asList("A.IB", "A.SH")) {
            BatchCrmwAdjustReq.SecurityItem security = new BatchCrmwAdjustReq.SecurityItem();
            security.setSecurityCode(code);
            security.setCrmwScode("C.IB");
            securities.add(security);
        }
        req.setSecurities(securities);
        BatchCrmwAdjustDto result = service.checkAdjust(req);
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems()).extracting(BatchCrmwAdjustDto.CheckResultItem::getItemTag).containsOnly("manual");
    }

    /** 同一批量主组合的关联项转换为单笔提交时必须保留实际证券代码。 */
    @Test
    public void batchSubmitShouldKeepRelatedIdentityAndGroup() {
        List<BatchCrmwAdjustReq.AdjustItem> items = new ArrayList<>();
        for (String code : Arrays.asList("A.IB", "A.SH")) {
            BatchCrmwAdjustReq.AdjustItem item = new BatchCrmwAdjustReq.AdjustItem();
            item.setSecurityCode(code);
            item.setSecurityShortName("名称" + code);
            item.setSecurityType("mtn");
            item.setCrmwScode("C.IB");
            item.setItemTag("A.IB".equals(code) ? "manual" : "related");
            item.setAdjustGroupKey("GROUP");
            items.add(item);
        }
        CrmwPoolAdjustSubmitReq req = ReflectionTestUtils.invokeMethod(new BatchCrmwPoolAdjustService(),
                "buildSingleSubmitReq", new BatchCrmwAdjustReq(), items);
        assertThat(req.getSecurityCode()).isEqualTo("A.IB");
        assertThat(req.getCrmwScode()).isEqualTo("C.IB");
        assertThat(req.getItems()).extracting(CrmwPoolAdjustSubmitReq.AdjustItem::getSecurityCode)
                .containsExactly("A.IB", "A.SH");
        assertThat(req.getItems().get(1).getSecurityShortName()).isEqualTo("名称A.SH");
        assertThat(req.getItems()).extracting(CrmwPoolAdjustSubmitReq.AdjustItem::getAdjustGroupKey).containsOnly("GROUP");
    }

    /** Excel 单组提交保留关联项实际代码，主组合及其流程分组保持一致。 */
    @Test
    public void excelSubmitShouldKeepRelatedIdentity() {
        List<CrmwPoolExcelImportCheckItemDto> items = new ArrayList<>();
        for (String code : Arrays.asList("A.IB", "A.SH")) {
            CrmwPoolExcelImportCheckItemDto item = new CrmwPoolExcelImportCheckItemDto();
            item.setSecurityCode(code);
            item.setSecurityShortName("名称" + code);
            item.setSecurityType("mtn");
            item.setCrmwScode("C.IB");
            item.setItemTag("A.IB".equals(code) ? "manual" : "related");
            item.setTargetPoolId(18L);
            item.setAdjustGroupKey("GROUP");
            items.add(item);
        }
        CrmwPoolAdjustSubmitReq req = ReflectionTestUtils.invokeMethod(new CrmwPoolExcelImportService(),
                "buildSubmitReq", items, new SysImpTmpBo(), "1", "测试用户", new InvestmentPoolBo());
        assertThat(req.getCrmwScode()).isEqualTo("C.IB");
        assertThat(req.getItems()).extracting(CrmwPoolAdjustSubmitReq.AdjustItem::getSecurityCode)
                .containsExactly("A.IB", "A.SH");
        assertThat(req.getItems().get(1).getSecurityType()).isEqualTo("mtn");
    }

    /** Excel 展开不同市场代码后，展示名称和市场必须来自该代码的主档。 */
    @Test
    public void excelRelatedDisplayShouldUseItsOwnMarketAndFullName() {
        SysImpTmpDetlBo source = new SysImpTmpDetlBo();
        source.setFld001("C.IB");
        source.setFld004("A.IB");
        source.setFld005("银行间名称");
        source.setFld007("CIBM");
        source.setFld011("旧类型名");
        AdjustCheckDto.CheckResultItem item = new AdjustCheckDto.CheckResultItem();
        item.setSecurityCode("A.SH");
        item.setSecurityType("mtn");
        item.setItemTag("related");
        item.setTargetPoolId(18L);
        SecurityInfoDetailDto info = new SecurityInfoDetailDto();
        info.setFullName("上交所名称");
        info.setMarketCode("SSE");
        info.setSecurityTypeName("中期票据");
        Map<String, SecurityInfoDetailDto> cache = new HashMap<>();
        cache.put("A.SH", info);
        cache.put("C.IB", null);
        CrmwPoolExcelImportCheckItemDto result = ReflectionTestUtils.invokeMethod(new CrmwPoolExcelImportService(),
                "mapCheckResult", source, item, new InvestmentPoolBo(), cache);
        assertThat(result.getSecurityName()).isEqualTo("上交所名称");
        assertThat(result.getSecurityMarket()).isEqualTo("SSE");
        assertThat(result.getSecurityTypeName()).isEqualTo("中期票据");
    }
}
