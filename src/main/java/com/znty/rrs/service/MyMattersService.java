package com.znty.rrs.service;

import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.BusinessDomain;
import com.znty.rrs.entity.flow.FlowOptionDto;
import com.znty.rrs.entity.mymatters.BusinessDomainDto;
import com.znty.rrs.entity.mymatters.MyMattersDto;
import com.znty.rrs.entity.mymatters.MyMattersReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.BusinessPermissionMapper;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;

/**
 * 为我的事宜页面查询可见业务并分派请求
 *
 * 按当前用户读取业务权限，校验请求查询的业务范围
 * 待办、已办、已发起事项和流程筛选选项，由 {@link BondMyMattersService}、{@link FundMyMattersService} 或 {@link StockMyMattersService} 按业务分别查询
 */
@Service
public class MyMattersService {
    /** 页面业务入口查询组件 */
    @Resource
    private BusinessPermissionMapper businessPermissionMapper;

    /** 债券事宜查询服务 */
    @Resource
    private BondMyMattersService bondMyMattersService;

    /** 基金事宜查询服务 */
    @Resource
    private FundMyMattersService fundMyMattersService;

    /** 股票事宜查询服务 */
    @Resource
    private StockMyMattersService stockMyMattersService;

    /**
     * 查询当前用户可显示的业务入口，仅供页面初始化使用
     *
     * 执行顺序：
     * 1. 先验证当前用户 ID，再按该用户权限查询页面允许显示的业务入口
     *
     * @param req 筛选与分页参数
     * @return 当前用户可显示的业务入口，仅供页面初始化使用列表
     */
    public List<BusinessDomainDto> queryBusinessDomainList(MyMattersReq req) {
        // 校验当前用户 ID 并查询固定业务入口名单
        return businessPermissionMapper.queryBusinessDomainList(requireUserId(req.getCurrentUserId()));
    }

    /**
     * 查询当前业务待处理或已完成事项
     *
     * 执行顺序：
     * 1. 校验业务编码及当前用户参数
     * 2. 步骤状态只能为 pending 或 completed时终止处理
     * 3. 分页查询我的事宜列表（待处理 / 已完成）
     *
     * @param req 筛选与分页参数
     * @return 分页结果
     */
    public PageResult<MyMattersDto> queryMyMattersPage(MyMattersReq req) {
        // 校验业务编码及当前用户参数
        validateBusinessRequest(req);
        if (!"pending".equals(req.getStepStatus()) && !"completed".equals(req.getStepStatus())) {
            throw new BizException("步骤状态只能为 pending 或 completed");
        }
        if (BusinessDomain.BOND.getCode().equals(req.getBusinessDomain())) {
            return bondMyMattersService.queryMyMattersPage(req);
        }
        if (BusinessDomain.FUND.getCode().equals(req.getBusinessDomain())) {
            return fundMyMattersService.queryMyMattersPage(req);
        }
        return stockMyMattersService.queryMyMattersPage(req);
    }

    /**
     * 查询本人发起的当前业务事项，管理员同样只查询本人
     *
     * 执行顺序：
     * 1. 校验业务编码及当前用户参数
     * 2. 分页查询我发起的事宜列表
     *
     * @param req 筛选与分页参数
     * @return 分页结果
     */
    public PageResult<MyMattersDto> queryMyInitiatedMattersPage(MyMattersReq req) {
        // 校验业务编码及当前用户参数
        validateBusinessRequest(req);
        if (BusinessDomain.BOND.getCode().equals(req.getBusinessDomain())) {
            return bondMyMattersService.queryMyInitiatedMattersPage(req);
        }
        if (BusinessDomain.FUND.getCode().equals(req.getBusinessDomain())) {
            return fundMyMattersService.queryMyInitiatedMattersPage(req);
        }
        return stockMyMattersService.queryMyInitiatedMattersPage(req);
    }

    /**
     * 查询当前业务可见事项涉及的流程选项
     *
     * 执行顺序：
     * 1. 校验业务编码及当前用户参数
     * 2. 查询当前用户事宜中出现过的流程下拉选项
     *
     * @param req 筛选与分页参数
     * @return 流程下拉选项
     */
    public List<FlowOptionDto> queryFlowOptionList(MyMattersReq req) {
        // 校验业务编码及当前用户参数
        validateBusinessRequest(req);
        if (BusinessDomain.BOND.getCode().equals(req.getBusinessDomain())) {
            return bondMyMattersService.queryFlowOptionList(req);
        }
        if (BusinessDomain.FUND.getCode().equals(req.getBusinessDomain())) {
            return fundMyMattersService.queryFlowOptionList(req);
        }
        return stockMyMattersService.queryFlowOptionList(req);
    }

    /**
     * 验证必传且已接入的业务编码以及当前用户 ID
     *
     * 执行顺序：
     * 1. 检查业务编码 businessDomain 不能为空
     * 2. 校验查询本人事项所需的当前用户 ID
     *
     * @param req 筛选与分页参数
     */
    private void validateBusinessRequest(MyMattersReq req) {
        if (req.getBusinessDomain() == null || req.getBusinessDomain().trim().isEmpty()) {
            throw new BizException("业务编码 businessDomain 不能为空");
        }
        if (!BusinessDomain.BOND.getCode().equals(req.getBusinessDomain())
                && !BusinessDomain.FUND.getCode().equals(req.getBusinessDomain())
                && !BusinessDomain.STOCK.getCode().equals(req.getBusinessDomain())) {
            throw new BizException("业务未接入或编码无效：" + req.getBusinessDomain());
        }
        // 校验查询本人事项所需的当前用户 ID
        requireUserId(req.getCurrentUserId());
    }

    /**
     * 校验当前演示用户 ID
     *
     * 执行顺序：
     * 1. 当前用户 ID 无效时终止处理
     *
     * @param userId 本次权限复核的操作人 ID
     * @return 用户 ID
     */
    private Long requireUserId(String userId) {
        if (userId == null || !userId.matches("[1-9][0-9]*")) {
            throw new BizException("当前用户 ID 无效");
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw new BizException("当前用户 ID 无效");
        }
    }
}
