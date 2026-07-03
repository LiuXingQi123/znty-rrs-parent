package com.znty.rrs.mapper;

import com.znty.rrs.entity.flow.FlowOptionDto;
import com.znty.rrs.entity.mymatters.MyMattersDto;
import com.znty.rrs.entity.mymatters.MyMattersReq;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 我的事宜数据访问接口。
 */
@Mapper
public interface MyMattersMapper {

    /** 分页查询我的事宜列表 */
    List<MyMattersDto> queryMyMattersPage(MyMattersReq req);

    /** 查询我的事宜流程名称下拉选项 */
    List<FlowOptionDto> queryFlowOptionList(MyMattersReq req);
}
