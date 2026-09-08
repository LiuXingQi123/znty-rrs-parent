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
     * 按担保债/ABS 属性批量筛选合格担保人并查询主体内评分
     *
     * @param req Wind 证券代码列表
     * @return 担保债返回担保人类型，ABS 返回四类相关主体及其最新内评结果
     */
    @PostMapping("/queryGuarantorGradeList")
    public ApiResponse<List<GuarantorGradeDto>> queryGuarantorGradeList(
            @RequestBody GuarantorGradeReq req) {
        return ApiResponse.success(commonService.queryGuarantorGradeList(req));
    }
}
