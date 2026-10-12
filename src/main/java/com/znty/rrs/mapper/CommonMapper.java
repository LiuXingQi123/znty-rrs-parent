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
     * 按担保债/ABS 属性批量筛选合格担保人并查询最新主体内评分
     *
     * @param securityCodes Wind 证券代码列表
     * @return 担保债返回担保人类型，ABS 返回四类相关主体及其最新内评结果
     */
    List<GuarantorGradeDto> queryGuarantorGradeList(@Param("securityCodes") List<String> securityCodes);
}
