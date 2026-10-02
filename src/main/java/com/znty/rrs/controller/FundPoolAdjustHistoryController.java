package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.fundpooladjusthistory.FundPoolAdjustHistoryDto;
import com.znty.rrs.entity.fundpooladjusthistory.FundPoolAdjustHistoryReq;
import com.znty.rrs.service.FundPoolAdjustHistoryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/** 基金池调整历史控制器。 */
@RestController
@RequestMapping("/api/v1/fundPoolAdjustHistory")
public class FundPoolAdjustHistoryController {

    /** 基金池调整历史服务。 */
    @Resource
    private FundPoolAdjustHistoryService fundPoolAdjustHistoryService;

    /**
     * 分页查询基金池调整历史记录。
     * <p>按基金信息、调整时间及审核状态等条件筛选，返回全部审核状态的历史记录。</p>
     *
     * @param req 分页参数和基金池调整历史筛选条件
     */
    @PostMapping("/queryFundPoolAdjustHistoryPage")
    public ApiResponse<PageResult<FundPoolAdjustHistoryDto>> queryFundPoolAdjustHistoryPage(
            @RequestBody FundPoolAdjustHistoryReq req) {
        return ApiResponse.success(fundPoolAdjustHistoryService.queryFundPoolAdjustHistoryPage(req));
    }

    /**
     * 按当前筛选条件导出基金池调整历史。
     * <p>筛选条件与列表一致，不传分页时导出全部命中记录。</p>
     *
     * @param req 基金池调整历史筛选条件
     */
    @PostMapping("/exportFundPoolAdjustHistoryExcel")
    public ApiResponse<CommonFileDto> exportFundPoolAdjustHistoryExcel(
            @RequestBody FundPoolAdjustHistoryReq req) {
        return ApiResponse.success(fundPoolAdjustHistoryService.exportFundPoolAdjustHistoryExcel(req));
    }

    /**
     * 查询调整历史中出现的基金类型选项。
     * <p>返回存在未删除调库记录的基金类型，供历史筛选条件使用。</p>
     *
     * @param req 接口请求体，此查询不使用筛选条件
     */
    @PostMapping("/queryFundTypeList")
    public ApiResponse<List<SecurityTypeOptionDto>> queryFundTypeList(
            @RequestBody FundPoolAdjustHistoryReq req) {
        return ApiResponse.success(fundPoolAdjustHistoryService.queryFundTypeList());
    }

}
