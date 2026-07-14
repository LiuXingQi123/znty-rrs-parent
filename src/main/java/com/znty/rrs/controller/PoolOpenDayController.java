package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.poolopenday.PoolOpenDayDto;
import com.znty.rrs.entity.poolopenday.PoolOpenDayReq;
import com.znty.rrs.service.PoolOpenDayService;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 投资池开放日维护控制器，提供开放日区间的分页查询与增删改
 */
@RestController
@RequestMapping("/api/v1/poolOpenDay")
public class PoolOpenDayController {

    /** 投资池开放日维护服务 */
    @Resource
    private PoolOpenDayService poolOpenDayService;

    /**
     * 分页查询开放日配置
     */
    @PostMapping("/queryPoolOpenDayPage")
    public ApiResponse<PageResult<PoolOpenDayDto>> queryPoolOpenDayPage(@RequestBody PoolOpenDayReq req) {
        return ApiResponse.success(poolOpenDayService.queryPoolOpenDayPage(req));
    }

    /**
     * 新增开放日配置
     */
    @PostMapping("/addPoolOpenDay")
    public ApiResponse<PoolOpenDayDto> addPoolOpenDay(@RequestBody PoolOpenDayReq req) {
        return ApiResponse.success(poolOpenDayService.addPoolOpenDay(req));
    }

    /**
     * 修改开放日配置
     */
    @PostMapping("/editPoolOpenDay")
    public ApiResponse<PoolOpenDayDto> editPoolOpenDay(@RequestBody PoolOpenDayReq req) {
        return ApiResponse.success(poolOpenDayService.editPoolOpenDay(req));
    }

    /**
     * 删除开放日配置（逻辑删除）
     */
    @PostMapping("/deletePoolOpenDay")
    public ApiResponse<PoolOpenDayDto> deletePoolOpenDay(@RequestBody PoolOpenDayReq req) {
        return ApiResponse.success(poolOpenDayService.deletePoolOpenDay(req));
    }
}
