package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.MyMattersMapper;
import com.znty.sirm.entity.flow.FlowOptionDto;
import com.znty.sirm.entity.mymatters.MyMattersDto;
import com.znty.sirm.entity.mymatters.MyMattersReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 我的事宜服务，负责查询当前用户相关的流程事项。
 */
@Service
public class MyMattersService {

    /** 我的事项数据访问组件 */
    @Resource
    private MyMattersMapper myMattersMapper;

    /** 投资池服务，用于获取池全路径映射 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /**
     * 分页查询我的事宜列表。
     */
    public PageResult<MyMattersDto> queryMyMattersPage(MyMattersReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<MyMattersDto> list = myMattersMapper.queryMyMattersPage(req);
        // 将流程描述中的目标池叶子名称替换为全路径
        replacePoolNameWithFullPath(list);
        PageInfo<MyMattersDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 查询当前用户事宜中出现过的流程下拉选项。
     */
    public List<FlowOptionDto> queryFlowOptionList(MyMattersReq req) {
        return myMattersMapper.queryFlowOptionList(req);
    }

    /**
     * 将流程描述中的目标池叶子名称替换为全路径名称。
     * 例如："管理员 将 23某基建PRN001 调入 二级库 的审批申请"
     *    → "管理员 将 23某基建PRN001 调入 信用债大库/二级库 的审批申请"
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
