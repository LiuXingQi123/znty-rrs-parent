package com.znty.sirm.mapper;

import com.znty.sirm.entity.subjectpool.SubjectPoolQueryDto;
import com.znty.sirm.entity.subjectpool.SubjectPoolQueryReq;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 主体池查询数据访问层
 */
@Mapper
public interface SubjectPoolQueryMapper {

    /**
     * 分页查询主体池中的主体列表（security_type 属于公司主体大类）
     *
     * @param req 查询条件
     */
    List<SubjectPoolQueryDto> querySubjectPoolPage(SubjectPoolQueryReq req);
}
