package com.znty.sirm.mapper;

import com.znty.sirm.model.SysAttachmentBo;
import com.znty.sirm.model.SysAttachmentDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统附件数据访问接口
 */
@Mapper
public interface SysAttachmentMapper {

    /** 新增附件关联记录 */
    int addAttachment(SysAttachmentBo bo);

    /** 按调库日志 ID 查询附件列表 */
    List<SysAttachmentDto> queryAttachmentList(@Param("adjustLogId") Long adjustLogId);

    /** 按附件 ID 查询附件 */
    SysAttachmentBo queryAttachmentById(@Param("id") Long id);

}
