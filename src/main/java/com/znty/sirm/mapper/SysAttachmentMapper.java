package com.znty.sirm.mapper;

import com.znty.sirm.entity.bo.SysAttachmentBo;
import com.znty.sirm.entity.sysattachment.SysAttachmentDto;
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

    /** 按附件 ID 列表查询附件 */
    List<SysAttachmentBo> queryAttachmentListByIds(@Param("ids") List<Long> ids);

    /** 逻辑删除指定调库日志下的附件 */
    int deleteAttachmentByIdsList(@Param("adjustLogId") Long adjustLogId, @Param("ids") List<Long> ids);

    /** 查询指定调库记录下手工上传的信评报告附件 */
    List<SysAttachmentBo> queryHandCreditReportAttachments(@Param("adjustLogId") Long adjustLogId);
}
