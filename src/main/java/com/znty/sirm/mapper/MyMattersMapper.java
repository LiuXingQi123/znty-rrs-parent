package com.znty.sirm.mapper;

import com.znty.sirm.model.FlowOptionDto;
import com.znty.sirm.model.MyMattersDto;
import com.znty.sirm.model.MyMattersReq;
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
