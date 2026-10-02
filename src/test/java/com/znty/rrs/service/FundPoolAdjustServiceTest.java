package com.znty.rrs.service;

import com.znty.rrs.common.enums.FundInvestmentType;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustSubmitReq;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustAuditReq;
import com.znty.rrs.exception.BizException;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 基金池调整服务提交字段测试 */
public class FundPoolAdjustServiceTest {
    /** 验证固定基金投资类型枚举 */
    @Test
    public void fundInvestmentTypeShouldExposeFiveFixedCodes() {
        assertThat(FundInvestmentType.values()).extracting(FundInvestmentType::getCode)
                .containsExactly("stock", "equity_hybrid", "money_market", "bond_hybrid", "other");
    }

    /** 验证基金评分为必填数值字段，不执行范围表达式 */
    @Test
    public void validateSubmitRequestShouldRequireFundScoreOnly() {
        FundPoolAdjustService service = new FundPoolAdjustService();
        // 构造基础提交请求，验证基金评分的必填规则
        FundPoolAdjustSubmitReq req = validSubmitReq();
        req.setFundScore(null);

        // 调用提交校验，确认缺少基金评分时返回业务错误
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "validateSubmitRequest", req))
                .isInstanceOf(BizException.class)
                .hasMessage("基金评分不能为空");

        req.setFundScore(new BigDecimal("999999.9999"));
        // 调用提交校验，确认非空评分不受范围限制
        ReflectionTestUtils.invokeMethod(service, "validateSubmitRequest", req);
    }

    /** 验证风管领导审批字段允许空值、0、1并拒绝其他值 */
    @Test
    public void validateSubmitRequestShouldValidateRiskLeaderFlag() {
        FundPoolAdjustService service = new FundPoolAdjustService();
        // 构造基础提交请求，验证风管领导审批标记
        FundPoolAdjustSubmitReq req = validSubmitReq();
        req.setNeedRiskLeaderApproval(null);
        // 调用提交校验，确认审批标记允许为空
        ReflectionTestUtils.invokeMethod(service, "validateSubmitRequest", req);

        req.setNeedRiskLeaderApproval(0);
        // 调用提交校验，确认审批标记允许为 0
        ReflectionTestUtils.invokeMethod(service, "validateSubmitRequest", req);
        req.setNeedRiskLeaderApproval(1);
        // 调用提交校验，确认审批标记允许为 1
        ReflectionTestUtils.invokeMethod(service, "validateSubmitRequest", req);
        req.setNeedRiskLeaderApproval(2);
        // 调用提交校验，确认审批标记为其他值时返回业务错误
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "validateSubmitRequest", req))
                .isInstanceOf(BizException.class)
                .hasMessage("是否需要风管领导审批仅允许为空、0 或 1");
    }

    /** 验证基金审核入口拒绝缺少步骤 ID 或非法动作的请求。 */
    @Test
    public void submitAdjustAuditShouldValidateRequiredFields() {
        FundPoolAdjustFlowService service = new FundPoolAdjustFlowService();
        FundPoolAdjustAuditReq req = new FundPoolAdjustAuditReq();
        assertThatThrownBy(() -> service.submitAdjustAudit(req))
                .isInstanceOf(BizException.class)
                .hasMessage("流程步骤 ID 不能为空");

        req.setStepId(1L);
        req.setProcessAction("skip");
        assertThatThrownBy(() -> service.submitAdjustAudit(req))
                .isInstanceOf(BizException.class)
                .hasMessage("审批动作不合法");
    }

    /** 构造合法提交请求 */
    private FundPoolAdjustSubmitReq validSubmitReq() {
        FundPoolAdjustSubmitReq req = new FundPoolAdjustSubmitReq();
        req.setFundScore(new BigDecimal("8.5"));
        req.setFundInvestmentType("stock");
        req.setAdjusterId("1");
        req.setItems(Collections.singletonList(new FundPoolAdjustSubmitReq.AdjustItem()));
        return req;
    }
}
