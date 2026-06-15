package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
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
 * 发行主体池查询控制器
 * <p>
 * 负责发行主体池的数据查询，发行主体池记录债券等证券对应的发行主体（企业/机构）
 * 在各投资池中的准入状态，用于主体维度的风险管控和投资限额管理。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/subjectPoolQuery")
public class SubjectPoolQueryController {

    /** 主体池查询服务 */
    @Resource
    private SubjectPoolQueryService subjectPoolQueryService;

    /**
     * 分页查询发行主体池列表，支持按主体名称、统一社会信用代码、投资池等条件筛选
     */
    @PostMapping("/querySubjectPoolPage")
    public ApiResponse<PageResult<SubjectPoolQueryDto>> querySubjectPoolPage(@RequestBody SubjectPoolQueryReq req) {
        return ApiResponse.success(subjectPoolQueryService.querySubjectPoolPage(req));
    }

    /**
     * 查询投资池层级树数据，供前端筛选条件中的投资池树形选择器使用
     */
    @PostMapping("/queryPoolTreeList")
    public ApiResponse<List<InvestmentPoolBo>> queryPoolTreeList(
            @RequestBody(required = false) SubjectPoolQueryReq req) {
        return ApiResponse.success(subjectPoolQueryService.queryPoolTreeList());
    }
}
