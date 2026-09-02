package com.znty.rrs.mapper;

import com.znty.rrs.entity.common.CommonReq;
import com.znty.rrs.entity.common.GuarantorGradeDto;
import com.znty.rrs.entity.common.PoolTreeDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 公共查询数据访问接口
 */
@Mapper
public interface CommonMapper {

    /**
     * 查询投资池树节点列表
     *
     * @param req 公共查询请求
     * @return 投资池树节点列表，包含节点名称、全路径名称和投资池类型
     */
    List<PoolTreeDto> queryPoolTreeList(CommonReq req);

    /**
     * 批量查询担保人的最新主体内评分
     *
     * @param windCodes Wind 主体代码列表
     * @return 每个主体最新的内评结果
     */
    List<GuarantorGradeDto> queryGuarantorGradeList(@Param("windCodes") List<String> windCodes);
}
