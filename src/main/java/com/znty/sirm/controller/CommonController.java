package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.model.CommonReq;
import com.znty.sirm.model.PoolTreeDto;
import com.znty.sirm.service.CommonService;
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
            @RequestBody(required = false) CommonReq req) {
        return ApiResponse.success(commonService.queryPoolTreeList());
    }
}
