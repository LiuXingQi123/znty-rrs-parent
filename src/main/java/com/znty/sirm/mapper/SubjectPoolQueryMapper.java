package com.znty.sirm.mapper;

import com.znty.sirm.model.SubjectPoolQueryDto;
import com.znty.sirm.model.SubjectPoolQueryReq;

import java.util.List;

/**
 * 主体池查询数据访问层
 */
public interface SubjectPoolQueryMapper {

    /**
     * 分页查询主体池中的主体列表（security_type 属于公司主体大类）
     *
     * @param req 查询条件
     */
    List<SubjectPoolQueryDto> querySubjectPoolPage(SubjectPoolQueryReq req);
}
