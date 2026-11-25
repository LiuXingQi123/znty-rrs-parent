package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.mapper.BondMyMattersMapper;
import com.znty.rrs.entity.flow.FlowOptionDto;
import com.znty.rrs.entity.mymatters.MyMattersDto;
import com.znty.rrs.entity.mymatters.MyMattersReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 查询当前用户相关的债券调库事项
 *
 * 分别查询当前用户的待办、已办和已发起债券事项，并返回页面筛选所需的流程选项
 * {@link MyMattersService} 按债券业务分派查询，列表使用投资池全路径名称区分同名子池
 */
@Service
public class BondMyMattersService {

    /** 我的事项数据访问组件 */
    @Resource
    private BondMyMattersMapper bondMyMattersMapper;

    /** 投资池服务，用于获取池全路径映射 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /**
     * 分页查询我的事宜列表（待处理 / 已完成）
     *
     * 执行顺序：
     * 1. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 2. 将流程描述中的目标池叶子名称替换为全路径
     * 3. 将本页记录和查询总条数一起返回
     *
     * @param req 筛选与分页参数
     * @return 分页结果
     */
    public PageResult<MyMattersDto> queryMyMattersPage(MyMattersReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<MyMattersDto> list = bondMyMattersMapper.queryMyMattersPage(req);
        // 将流程描述中的目标池叶子名称替换为全路径
        replacePoolNameWithFullPath(list);
        PageInfo<MyMattersDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 分页查询我发起的事宜列表
     *
     * 执行顺序：
     * 1. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 2. 将流程描述中的目标池叶子名称替换为全路径
     * 3. 将本页记录和查询总条数一起返回
     *
     * @param req 筛选与分页参数（按 currentUserId 作为发起人过滤）
     * @return 分页结果
     */
    public PageResult<MyMattersDto> queryMyInitiatedMattersPage(MyMattersReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<MyMattersDto> list = bondMyMattersMapper.queryMyInitiatedMattersPage(req);
        // 将流程描述中的目标池叶子名称替换为全路径
        replacePoolNameWithFullPath(list);
        PageInfo<MyMattersDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 查询当前用户事宜中出现过的流程下拉选项
     *
     * 执行顺序：
     * 1. 按当前用户的事宜范围查询出现过的审批流程，作为页面筛选选项
     *
     * @param req 含 currentUserId
     * @return 流程下拉选项
     */
    public List<FlowOptionDto> queryFlowOptionList(MyMattersReq req) {
        return bondMyMattersMapper.queryFlowOptionList(req);
    }

    /**
     * 将流程描述中的目标池叶子名称替换为全路径名称
     * 例如："管理员 将 23某基建PRN001 调入 二级库 的审批申请"
     * → "管理员 将 23某基建PRN001 调入 信用债大库/二级库 的审批申请"
     *
     * 执行顺序：
     * 1. 获取池 ID → 全路径名称映射
     *
     * @param list 当前页股票流程事项
     */
    private void replacePoolNameWithFullPath(List<MyMattersDto> list) {
        if (list.isEmpty()) {
            return;
        }
        // 获取池 ID → 全路径名称映射
        Map<Long, String> fullNameMap = investmentPoolService.queryPoolFullNameMap();
        if (fullNameMap.isEmpty()) {
            return;
        }
        for (MyMattersDto dto : list) {
            if (dto.getTargetPoolId() == null || dto.getTargetPoolName() == null) {
                continue;
            }
            String fullName = fullNameMap.get(dto.getTargetPoolId());
            if (fullName == null || fullName.equals(dto.getTargetPoolName())) {
                continue;
            }
            // 将描述中的叶子名称替换为全路径
            dto.setProcessDescription(
                dto.getProcessDescription().replace(dto.getTargetPoolName(), fullName)
            );
        }
    }
}
