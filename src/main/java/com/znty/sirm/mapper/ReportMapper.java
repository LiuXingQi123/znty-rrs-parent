package com.znty.sirm.mapper;

import com.znty.sirm.model.ReportDto;
import com.znty.sirm.model.ReportReq;

import java.util.List;

/**
 * 报告库数据访问接口
 */
public interface ReportMapper {

    /**
     * 分页查询内部报告库列表
     */
    List<ReportDto> queryInReportPage(ReportReq req);

    /**
     * 分页查询外部报告库列表
     */
    List<ReportDto> queryOutReportPage(ReportReq req);
}
