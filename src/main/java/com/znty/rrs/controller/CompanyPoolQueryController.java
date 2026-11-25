package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.companypool.CompanyPoolQueryDto;
import com.znty.rrs.entity.companypool.CompanyPoolQueryReq;
import com.znty.rrs.service.CompanyPoolQueryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 发行主体池查询控制器
 *
 * 查询企业及机构等发行主体在各投资池中的准入状态，供主体风险管控和投资限额管理使用
 */
@RestController
@RequestMapping("/api/v1/companyPoolQuery")
public class CompanyPoolQueryController {

    /** 主体池查询服务 */
    @Resource
    private CompanyPoolQueryService companyPoolQueryService;

    /**
     * 分页查询发行主体池列表，支持按主体名称、统一社会信用代码、投资池等条件筛选
     *
     * @param req 在池主体的筛选和分页条件
     * @return 在池主体及分页信息
     */
    @PostMapping("/queryCompanyPoolPage")
    public ApiResponse<PageResult<CompanyPoolQueryDto>> queryCompanyPoolPage(@RequestBody CompanyPoolQueryReq req) {
        return ApiResponse.success(companyPoolQueryService.queryCompanyPoolPage(req));
    }

    /**
     * 按当前查询条件导出主体池列表
     *
     * 筛选条件与列表一致，不传分页时导出全部命中记录
     *
     * @param req 在池主体的筛选条件
     * @return Excel 文件名、内容类型、文件大小和 Base64 内容
     */
    @PostMapping("/exportCompanyPoolExcel")
    public ApiResponse<CommonFileDto> exportCompanyPoolExcel(@RequestBody CompanyPoolQueryReq req) {
        return ApiResponse.success(companyPoolQueryService.exportCompanyPoolExcel(req));
    }

}
