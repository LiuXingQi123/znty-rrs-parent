package com.znty.sirm.controller;

import com.znty.sirm.common.ApiResponse;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.entity.tempsecuritycode.TempSecurityCodeDto;
import com.znty.sirm.entity.tempsecuritycode.TempSecurityCodeReq;
import com.znty.sirm.service.TempSecurityCodeService;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 临时代码管理控制器，提供临时代码查询、新增、更新、取消发行和删除能力
 */
@RestController
@RequestMapping("/api/v1/tempSecurityCode")
public class TempSecurityCodeController {

    /** 临时代码管理服务 */
    @Resource
    private TempSecurityCodeService tempSecurityCodeService;

    /**
     * 分页查询临时代码列表
     */
    @PostMapping("/queryTempSecurityCodePage")
    public ApiResponse<PageResult<TempSecurityCodeDto>> queryTempSecurityCodePage(@RequestBody TempSecurityCodeReq req) {
        return ApiResponse.success(tempSecurityCodeService.queryTempSecurityCodePage(req));
    }

    /**
     * 查询新增页面下拉选项
     */
    @PostMapping("/queryTempSecurityCodeOptions")
    public ApiResponse<TempSecurityCodeDto.OptionBundle> queryTempSecurityCodeOptions(@RequestBody TempSecurityCodeReq req) {
        return ApiResponse.success(tempSecurityCodeService.queryTempSecurityCodeOptions(req));
    }

    /**
     * 新增临时代码
     */
    @PostMapping("/addTempSecurityCode")
    public ApiResponse<TempSecurityCodeDto> addTempSecurityCode(@RequestBody TempSecurityCodeReq req) {
        return ApiResponse.success(tempSecurityCodeService.addTempSecurityCode(req));
    }

    /**
     * 更新临时代码为正式证券
     */
    @PostMapping("/editTempSecurityCodeToUpdated")
    public ApiResponse<TempSecurityCodeDto> editTempSecurityCodeToUpdated(@RequestBody TempSecurityCodeReq req) {
        return ApiResponse.success(tempSecurityCodeService.editTempSecurityCodeToUpdated(req));
    }

    /**
     * 取消发行临时代码
     */
    @PostMapping("/editTempSecurityCodeToCancelled")
    public ApiResponse<TempSecurityCodeDto> editTempSecurityCodeToCancelled(@RequestBody TempSecurityCodeReq req) {
        return ApiResponse.success(tempSecurityCodeService.editTempSecurityCodeToCancelled(req));
    }

    /**
     * 删除临时代码
     */
    @PostMapping("/deleteTempSecurityCode")
    public ApiResponse<TempSecurityCodeDto> deleteTempSecurityCode(@RequestBody TempSecurityCodeReq req) {
        return ApiResponse.success(tempSecurityCodeService.deleteTempSecurityCode(req));
    }
}
