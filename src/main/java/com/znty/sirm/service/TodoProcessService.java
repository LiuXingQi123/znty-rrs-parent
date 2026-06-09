package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.TodoProcessMapper;
import com.znty.sirm.model.FlowOptionDto;
import com.znty.sirm.model.TodoProcessDto;
import com.znty.sirm.model.TodoProcessReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 待办处理服务，负责查询当前用户可处理的待审批流程步骤。
 */
@Service
public class TodoProcessService {

    @Resource
    private TodoProcessMapper todoProcessMapper;

    /**
     * 分页查询待办处理列表。
     */
    public PageResult<TodoProcessDto> queryTodoProcessPage(TodoProcessReq req) {
        TodoProcessReq safeReq = req == null ? new TodoProcessReq() : req;
        PageHelper.startPage(safeReq.getPageIndex(), safeReq.getPageSize());
        List<TodoProcessDto> list = todoProcessMapper.queryTodoProcessPage(safeReq);
        PageInfo<TodoProcessDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), safeReq.getPageIndex(), safeReq.getPageSize());
    }

    /**
     * 查询当前用户待办中出现过的流程下拉选项。
     */
    public List<FlowOptionDto> queryFlowOptionList(TodoProcessReq req) {
        TodoProcessReq safeReq = req == null ? new TodoProcessReq() : req;
        return todoProcessMapper.queryFlowOptionList(safeReq);
    }
}
