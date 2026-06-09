package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.MyMattersMapper;
import com.znty.sirm.model.FlowOptionDto;
import com.znty.sirm.model.MyMattersDto;
import com.znty.sirm.model.MyMattersReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 我的事宜服务，负责查询当前用户相关的流程事项。
 */
@Service
public class MyMattersService {

    @Resource
    private MyMattersMapper myMattersMapper;

    /**
     * 分页查询我的事宜列表。
     */
    public PageResult<MyMattersDto> queryMyMattersPage(MyMattersReq req) {
        MyMattersReq safeReq = req == null ? new MyMattersReq() : req;
        PageHelper.startPage(safeReq.getPageIndex(), safeReq.getPageSize());
        List<MyMattersDto> list = myMattersMapper.queryMyMattersPage(safeReq);
        PageInfo<MyMattersDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), safeReq.getPageIndex(), safeReq.getPageSize());
    }

    /**
     * 查询当前用户事宜中出现过的流程下拉选项。
     */
    public List<FlowOptionDto> queryFlowOptionList(MyMattersReq req) {
        MyMattersReq safeReq = req == null ? new MyMattersReq() : req;
        return myMattersMapper.queryFlowOptionList(safeReq);
    }
}
