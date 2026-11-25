package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeDto;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeReq;
import com.znty.rrs.service.TempSecurityCodeService;
import java.util.List;
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
     *
     * @param req 临时代码筛选和分页条件
     * @return 临时证券代码及分页信息
     */
    @PostMapping("/queryTempSecurityCodePage")
    public ApiResponse<PageResult<TempSecurityCodeDto>> queryTempSecurityCodePage(@RequestBody TempSecurityCodeReq req) {
        return ApiResponse.success(tempSecurityCodeService.queryTempSecurityCodePage(req));
    }

    /**
     * 查询新增页面下拉选项
     *
     * @param req 空请求体
     * @return 新增临时代码所需的下拉选项
     */
    @PostMapping("/queryTempSecurityCodeOptions")
    public ApiResponse<TempSecurityCodeDto.OptionBundle> queryTempSecurityCodeOptions(@RequestBody TempSecurityCodeReq req) {
        return ApiResponse.success(tempSecurityCodeService.queryTempSecurityCodeOptions(req));
    }

    /**
     * 查询发行主体弹窗选项，最多返回 100 条
     *
     * @param req 主体代码、全称和统一社会信用代码筛选条件
     * @return 发行主体选项列表
     */
    @PostMapping("/queryIssuerOptionList")
    public ApiResponse<List<TempSecurityCodeDto.IssuerOption>> queryIssuerOptionList(
            @RequestBody TempSecurityCodeReq.IssuerQueryReq req) {
        return ApiResponse.success(tempSecurityCodeService.queryIssuerOptionList(req));
    }

    /**
     * 远程查询正式证券选项（转正选券，代码、名称模糊，最多 50 条）
     *
     * @param req 正式证券代码或名称关键字
     * @return 正式证券选项列表
     */
    @PostMapping("/queryFormalSecurityOptionList")
    public ApiResponse<List<TempSecurityCodeDto.FormalSecurityOption>> queryFormalSecurityOptionList(
            @RequestBody TempSecurityCodeReq req) {
        return ApiResponse.success(tempSecurityCodeService.queryFormalSecurityOptionList(req));
    }

    /**
     * 新增临时代码
     *
     * @param req 发行主体和待登记的临时证券信息
     * @return 新建的临时代码记录
     */
    @PostMapping("/addTempSecurityCode")
    public ApiResponse<TempSecurityCodeDto> addTempSecurityCode(@RequestBody TempSecurityCodeReq req) {
        return ApiResponse.success(tempSecurityCodeService.addTempSecurityCode(req));
    }

    /**
     * 更新临时代码为正式证券
     *
     * @param req 临时代码记录 ID 和正式证券代码
     * @return 转正后的临时代码记录详情
     */
    @PostMapping("/editTempSecurityCodeToUpdated")
    public ApiResponse<TempSecurityCodeDto> editTempSecurityCodeToUpdated(@RequestBody TempSecurityCodeReq req) {
        return ApiResponse.success(tempSecurityCodeService.editTempSecurityCodeToUpdated(req));
    }

    /**
     * 取消发行临时代码
     *
     * @param req 待取消发行的临时代码记录 ID
     * @return 取消发行后的临时代码记录
     */
    @PostMapping("/editTempSecurityCodeToCancelled")
    public ApiResponse<TempSecurityCodeDto> editTempSecurityCodeToCancelled(@RequestBody TempSecurityCodeReq req) {
        return ApiResponse.success(tempSecurityCodeService.editTempSecurityCodeToCancelled(req));
    }

    /**
     * 删除临时代码
     *
     * @param req 待删除的临时代码记录 ID
     * @return 删除成功响应，数据为空
     */
    @PostMapping("/deleteTempSecurityCode")
    public ApiResponse<TempSecurityCodeDto> deleteTempSecurityCode(@RequestBody TempSecurityCodeReq req) {
        return ApiResponse.success(tempSecurityCodeService.deleteTempSecurityCode(req));
    }
}
