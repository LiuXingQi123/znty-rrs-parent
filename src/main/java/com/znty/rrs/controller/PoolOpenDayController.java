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
     *
     * @param req 开放日配置的筛选条件和分页参数，允许缺省
     * @return 投资池开放日区间信息及分页信息
     */
    @PostMapping("/queryPoolOpenDayPage")
    public ApiResponse<PageResult<PoolOpenDayDto>> queryPoolOpenDayPage(@RequestBody PoolOpenDayReq req) {
        return ApiResponse.success(poolOpenDayService.queryPoolOpenDayPage(req));
    }

    /**
     * 新增开放日配置
     *
     * @param req 新增开放日的目标池、起止日期和描述
     * @return 投资池开放日区间信息
     */
    @PostMapping("/addPoolOpenDay")
    public ApiResponse<PoolOpenDayDto> addPoolOpenDay(@RequestBody PoolOpenDayReq req) {
        return ApiResponse.success(poolOpenDayService.addPoolOpenDay(req));
    }

    /**
     * 修改开放日配置
     *
     * @param req 待修改的开放日记录 ID、投资池、起止日期和描述
     * @return 投资池开放日区间信息
     */
    @PostMapping("/editPoolOpenDay")
    public ApiResponse<PoolOpenDayDto> editPoolOpenDay(@RequestBody PoolOpenDayReq req) {
        return ApiResponse.success(poolOpenDayService.editPoolOpenDay(req));
    }

    /**
     * 删除开放日配置（逻辑删除）
     *
     * @param req 待删除的开放日配置记录 ID
     * @return 投资池开放日区间信息
     */
    @PostMapping("/deletePoolOpenDay")
    public ApiResponse<PoolOpenDayDto> deletePoolOpenDay(@RequestBody PoolOpenDayReq req) {
        return ApiResponse.success(poolOpenDayService.deletePoolOpenDay(req));
    }
}
