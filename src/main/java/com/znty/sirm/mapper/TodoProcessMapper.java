package com.znty.sirm.mapper;

import com.znty.sirm.model.FlowOptionDto;
import com.znty.sirm.model.TodoProcessDto;
import com.znty.sirm.model.TodoProcessReq;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 待办处理数据访问接口。
 */
@Mapper
public interface TodoProcessMapper {

    /** 分页查询待办处理列表 */
    List<TodoProcessDto> queryTodoProcessPage(TodoProcessReq req);

    /** 查询待办流程名称下拉选项 */
    List<FlowOptionDto> queryFlowOptionList(TodoProcessReq req);
}
