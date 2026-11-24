package com.znty.rrs.mapper;

import com.znty.rrs.entity.flow.FlowOptionDto;
import com.znty.rrs.entity.mymatters.MyMattersDto;
import com.znty.rrs.entity.mymatters.MyMattersReq;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 我的事宜数据访问接口
 */
@Mapper
public interface StockMyMattersMapper {

    /**
     * 分页查询我的事宜列表（待处理 / 已完成）
     *
     * @param req 当前用户及待办或已办事项的筛选条件
     * @return 符合条件的流程事项列表
     */
    List<MyMattersDto> queryMyMattersPage(MyMattersReq req);

    /**
     * 分页查询我发起的事宜列表
     *
     * @param req 当前发起用户及其已发起事项的筛选条件
     * @return 符合条件的流程事项列表
     */
    List<MyMattersDto> queryMyInitiatedMattersPage(MyMattersReq req);

    /**
     * 查询我的事宜流程名称下拉选项
     *
     * @param req 当前用户和事项范围等筛选条件
     * @return 符合条件的可筛选的流程选项列表
     */
    List<FlowOptionDto> queryFlowOptionList(MyMattersReq req);
}
