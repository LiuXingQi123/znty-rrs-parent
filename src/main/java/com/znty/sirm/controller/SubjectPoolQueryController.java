package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.model.InvestmentPoolBo;
import com.znty.sirm.model.SubjectPoolQueryDto;
import com.znty.sirm.model.SubjectPoolQueryReq;
import com.znty.sirm.service.SubjectPoolQueryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 主体池查询控制器
 */
@RestController
@RequestMapping("/api/v1/subjectPoolQuery")
public class SubjectPoolQueryController {

    @Resource
    private SubjectPoolQueryService subjectPoolQueryService;

    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 分页查询主体池列表 */
    @PostMapping("/querySubjectPoolPage")
    public ApiResponse<PageResult<SubjectPoolQueryDto>> querySubjectPoolPage(@RequestBody SubjectPoolQueryReq req) {
        return ApiResponse.success(subjectPoolQueryService.querySubjectPoolPage(req));
    }

    /** 查询投资池树 */
    @PostMapping("/queryPoolTreeList")
    public ApiResponse<List<InvestmentPoolBo>> queryPoolTreeList() {
        return ApiResponse.success(investmentPoolMapper.queryPoolList());
    }
}
