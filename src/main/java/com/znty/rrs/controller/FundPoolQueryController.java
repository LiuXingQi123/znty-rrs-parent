package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.fundpoolquery.FundPoolQueryDto;
import com.znty.rrs.entity.fundpoolquery.FundPoolQueryReq;
import com.znty.rrs.service.FundPoolQueryService;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基金池查询控制器。
 * <p>负责基金池当前状态查询、基金类型选项查询和结果导出。</p>
 */
@RestController
@RequestMapping("/api/v1/fundPoolQuery")
public class FundPoolQueryController {

    /** 基金池查询服务。 */
    @Resource
    private FundPoolQueryService fundPoolQueryService;

    /**
     * 分页查询当前已生效的基金池状态。
     *
     * @param req 基金池筛选及分页条件
     * @return 基金池分页记录
     */
    @PostMapping("/queryFundPoolPage")
    public ApiResponse<PageResult<FundPoolQueryDto>> queryFundPoolPage(@RequestBody FundPoolQueryReq req) {
        return ApiResponse.success(fundPoolQueryService.queryFundPoolPage(req));
    }

    /**
     * 按当前查询条件导出基金池列表。
     *
     * @param req 基金池筛选条件
     * @return 可下载的 Excel 文件
     */
    @PostMapping("/exportFundPoolExcel")
    public ApiResponse<CommonFileDto> exportFundPoolExcel(@RequestBody FundPoolQueryReq req) {
        return ApiResponse.success(fundPoolQueryService.exportFundPoolExcel(req));
    }

    /**
     * 查询基金类型下拉选项。
     *
     * @param req 基金池查询请求
     * @return 当前已生效基金池中出现的基金类型
     */
    @PostMapping("/queryFundTypeList")
    public ApiResponse<List<SecurityTypeOptionDto>> queryFundTypeList(@RequestBody FundPoolQueryReq req) {
        return ApiResponse.success(fundPoolQueryService.queryFundTypeList());
    }
}
