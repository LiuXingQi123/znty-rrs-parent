package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.graderulealert.GradeRuleAlertDto;
import com.znty.rrs.entity.graderulealert.GradeRuleAlertReq;
import com.znty.rrs.entity.graderulealert.AbsAlertCandidateDto;
import com.znty.rrs.service.GradeRuleAlertService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 不符合主体债入库规则提醒
 */
@RestController
@RequestMapping("/api/v1/gradeRuleAlert")
public class GradeRuleAlertController {

    /** 提醒服务 */
    @Resource
    private GradeRuleAlertService gradeRuleAlertService;

    /**
     * 分页查询待办。
     *
     * @param req 查询条件
     * @return 分页结果
     */
    @PostMapping("/queryAlertPage")
    public ApiResponse<PageResult<GradeRuleAlertDto>> queryAlertPage(@RequestBody GradeRuleAlertReq req) {
        return ApiResponse.success(gradeRuleAlertService.queryAlertPage(req));
    }

    /**
     * 人工标记已处理（不改池状态）。
     *
     * @param req 含待办 id 与处理人
     * @return 更新后的待办
     */
    @PostMapping("/editAlertProcessed")
    public ApiResponse<GradeRuleAlertDto> editAlertProcessed(@RequestBody GradeRuleAlertReq req) {
        return ApiResponse.success(gradeRuleAlertService.editAlertProcessed(req));
    }

    /** 查询 ABS 规则提醒的权益人候选 */
    @PostMapping("/queryAbsCandidates")
    public ApiResponse<List<AbsAlertCandidateDto>> queryAbsCandidates(@RequestBody GradeRuleAlertReq req) {
        return ApiResponse.success(gradeRuleAlertService.queryAbsCandidates(req.getSecurityCode()));
    }

    /** 确认 ABS 主体或忽略本次入池 */
    @PostMapping("/processAbsAlert")
    public ApiResponse<GradeRuleAlertDto> processAbsAlert(@RequestBody GradeRuleAlertReq req) {
        return ApiResponse.success(gradeRuleAlertService.processAbsAlert(req));
    }
}
