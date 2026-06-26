package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.entity.creditbondgraderule.CreditBondGradeRuleDto;
import com.znty.sirm.entity.creditbondgraderule.CreditBondGradeRuleReq;
import com.znty.sirm.service.CreditBondGradeRuleService;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主体内评分档规则控制器，维护信用债期限、主体内评分档与投资池的关系矩阵
 */
@RestController
@RequestMapping("/api/v1/creditBondGradeRule")
public class CreditBondGradeRuleController {

    /** 主体内评分档规则服务 */
    @Resource
    private CreditBondGradeRuleService creditBondGradeRuleService;

    /**
     * 查询主体内评分档规则矩阵
     */
    @PostMapping("/queryGradeRuleMatrix")
    public ApiResponse<CreditBondGradeRuleDto> queryGradeRuleMatrix(@RequestBody CreditBondGradeRuleReq req) {
        return ApiResponse.success(creditBondGradeRuleService.queryGradeRuleMatrix(req));
    }

    /**
     * 保存主体内评分档规则矩阵
     */
    @PostMapping("/editGradeRuleMatrix")
    public ApiResponse<CreditBondGradeRuleDto> editGradeRuleMatrix(@RequestBody CreditBondGradeRuleReq req) {
        return ApiResponse.success(creditBondGradeRuleService.editGradeRuleMatrix(req));
    }
}
