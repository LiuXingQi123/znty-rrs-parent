package com.znty.rrs.controller;

import com.znty.rrs.common.ApiResponse;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.tempfundcode.TempFundCodeDto;
import com.znty.rrs.entity.tempfundcode.TempFundCodeReq;
import com.znty.rrs.service.TempFundCodeService;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 基金临时代码接口，提供人工登记、转正、取消及删除能力。 */
@RestController
@RequestMapping("/api/v1/tempFundCode")
public class TempFundCodeController {
    /** 基金临时代码业务服务 */
    @Resource
    private TempFundCodeService tempFundCodeService;

    /**
     * 分页查询基金临时代码。
     *
     * @param req 临时基金代码、简称、状态、来源及分页条件
     * @return 分页查询基金临时代码的结果
     */
    @PostMapping("/queryTempFundCodePage")
    public ApiResponse<PageResult<TempFundCodeDto>> queryTempFundCodePage(@RequestBody TempFundCodeReq req) {
        return ApiResponse.success(tempFundCodeService.queryTempFundCodePage(req));
    }

    /**
     * 查询基金产品类型选项。
     *
     * @param req 选项查询参数，当前无需额外条件
     * @return 查询基金产品类型选项的结果
     */
    @PostMapping("/queryTempFundCodeOptions")
    public ApiResponse<TempFundCodeDto.OptionBundle> queryTempFundCodeOptions(@RequestBody TempFundCodeReq req) {
        return ApiResponse.success(tempFundCodeService.queryTempFundCodeOptions(req));
    }

    /**
     * 搜索已有正式基金。
     *
     * @param req 正式基金代码或名称搜索关键字
     * @return 搜索已有正式基金的结果
     */
    @PostMapping("/queryFormalFundOptionList")
    public ApiResponse<List<TempFundCodeDto.FormalFundOption>> queryFormalFundOptionList(@RequestBody TempFundCodeReq req) {
        return ApiResponse.success(tempFundCodeService.queryFormalFundOptionList(req));
    }

    /**
     * 新增临时代码和占位主档。
     *
     * @param req 临时基金四项录入信息及操作人
     * @return 新增临时代码和占位主档的结果
     */
    @PostMapping("/addTempFundCode")
    public ApiResponse<TempFundCodeDto> addTempFundCode(@RequestBody TempFundCodeReq req) {
        return ApiResponse.success(tempFundCodeService.addTempFundCode(req));
    }

    /**
     * 将临时代码转为正式基金。
     *
     * @param req 登记 ID、正式基金代码及操作人
     * @return 将临时代码转为正式基金的结果
     */
    @PostMapping("/editTempFundCodeToUpdated")
    public ApiResponse<TempFundCodeDto> editTempFundCodeToUpdated(@RequestBody TempFundCodeReq req) {
        return ApiResponse.success(tempFundCodeService.editTempFundCodeToUpdated(req));
    }

    /**
     * 取消发行并禁用临时主档。
     *
     * @param req 待取消登记 ID 及操作人
     * @return 取消发行并禁用临时主档的结果
     */
    @PostMapping("/editTempFundCodeToCancelled")
    public ApiResponse<TempFundCodeDto> editTempFundCodeToCancelled(@RequestBody TempFundCodeReq req) {
        return ApiResponse.success(tempFundCodeService.editTempFundCodeToCancelled(req));
    }

    /**
     * 校验业务引用并软删除登记。
     *
     * @param req 待删除登记 ID 及操作人
     * @return 成功响应，数据为空
     */
    @PostMapping("/deleteTempFundCode")
    public ApiResponse<TempFundCodeDto> deleteTempFundCode(@RequestBody TempFundCodeReq req) {
        return ApiResponse.success(tempFundCodeService.deleteTempFundCode(req));
    }
}
