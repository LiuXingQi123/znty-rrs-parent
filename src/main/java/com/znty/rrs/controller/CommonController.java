package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.entity.common.CommonReq;
import com.znty.rrs.entity.common.GuarantorGradeDto;
import com.znty.rrs.entity.common.GuarantorGradeReq;
import com.znty.rrs.entity.common.PoolTreeDto;
import com.znty.rrs.service.CommonService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 公共查询控制器
 */
@RestController
@RequestMapping("/api/v1/common")
public class CommonController {

    /** 公共查询服务 */
    @Resource
    private CommonService commonService;

    /**
     * 查询投资池树节点列表
     *
     * @param req 公共查询请求
     * @return 投资池树节点列表，包含节点名称和全路径名称
     */
    @PostMapping("/queryPoolTreeList")
    public ApiResponse<List<PoolTreeDto>> queryPoolTreeList(
            @RequestBody CommonReq req) {
        return ApiResponse.success(commonService.queryPoolTreeList(req));
    }

    /**
     * 批量查询担保人主体内评分
     *
     * @param req 担保人 Wind 主体代码列表
     * @return 每个主体最新的内评结果
     */
    @PostMapping("/queryGuarantorGradeList")
    public ApiResponse<List<GuarantorGradeDto>> queryGuarantorGradeList(
            @RequestBody GuarantorGradeReq req) {
        return ApiResponse.success(commonService.queryGuarantorGradeList(req));
    }
}
