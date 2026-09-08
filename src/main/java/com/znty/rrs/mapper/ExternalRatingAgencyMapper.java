package com.znty.rrs.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** 外部评级机构配置数据访问组件。 */
@Mapper
public interface ExternalRatingAgencyMapper {

    /**
     * 查询有效外部评级机构编码列表。
     *
     * @return 去重并按机构编码数值升序排列的编码列表
     */
    List<String> queryActiveAgencyCodeList();
}
