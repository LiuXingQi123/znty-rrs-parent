package com.znty.sirm.mapper;

import com.znty.sirm.model.ReportDto;
import com.znty.sirm.model.ReportInBo;
import com.znty.sirm.model.ReportReq;
import com.znty.sirm.model.SysAttachmentDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 报告库数据访问接口
 */
@Mapper
public interface ReportMapper {

    /**
     * 分页查询内部报告库列表
     */
    List<ReportDto> queryInReportPage(ReportReq req);

    /**
     * 分页查询外部报告库列表
     */
    List<ReportDto> queryOutReportPage(ReportReq req);

    /**
     * 按内部报告 ID 批量查询报告附件
     */
    List<SysAttachmentDto> queryInReportAttachmentList(@Param("reportIds") List<Long> reportIds);

    /**
     * 按外部报告 ID 批量查询报告附件
     */
    List<SysAttachmentDto> queryOutReportAttachmentList(@Param("reportIds") List<Long> reportIds);

    /**
     * 新增内部报告记录，回填主键 ID
     */
    int addInReport(ReportInBo bo);
}
