package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.mapper.ReportMapper;
import com.znty.rrs.entity.report.ReportDto;
import com.znty.rrs.entity.bo.ReportInBo;
import com.znty.rrs.entity.report.ReportReq;
import com.znty.rrs.entity.sysattachment.SysAttachmentDto;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报告库查询服务
 */
@Service
public class ReportService {

    @Resource
    private ReportMapper reportMapper;

    /** 分页查询内部报告库列表 */
    public PageResult<ReportDto> queryInReportPage(ReportReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<ReportDto> list = reportMapper.queryInReportPage(req);
        // 回填内部报告附件
        fillReportAttachments(list, true);
        PageInfo<ReportDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 分页查询外部报告库列表 */
    public PageResult<ReportDto> queryOutReportPage(ReportReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<ReportDto> list = reportMapper.queryOutReportPage(req);
        // 回填外部报告附件
        fillReportAttachments(list, false);
        PageInfo<ReportDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 回填报告附件列表
     * @param list 报告列表
     * @param internal true=内部报告库，false=外部报告库
     */
    private void fillReportAttachments(List<ReportDto> list, boolean internal) {
        if (list.isEmpty()) {
            return;
        }
        List<Long> reportIds = new ArrayList<>();
        for (ReportDto report : list) {
            report.setAttachments(Collections.<SysAttachmentDto>emptyList());
            if (report.getId() != null) {
                reportIds.add(report.getId());
            }
        }
        if (reportIds.isEmpty()) {
            return;
        }
        List<SysAttachmentDto> attachments = internal
                ? reportMapper.queryInReportAttachmentList(reportIds)
                : reportMapper.queryOutReportAttachmentList(reportIds);
        Map<Long, List<SysAttachmentDto>> attachmentMap = new HashMap<>();
        for (SysAttachmentDto attachment : attachments) {
            Long mainId = attachment.getMainId();
            if (mainId == null) {
                continue;
            }
            if (!attachmentMap.containsKey(mainId)) {
                attachmentMap.put(mainId, new ArrayList<>());
            }
            attachmentMap.get(mainId).add(attachment);
        }
        for (ReportDto report : list) {
            List<SysAttachmentDto> reportAttachments = attachmentMap.get(report.getId());
            if (reportAttachments != null) {
                report.setAttachments(reportAttachments);
            }
        }
    }

    /**
     * 新增内部报告记录，返回回填的主键 ID。
     *
     * @param bo 内部报告记录
     */
    public Long addInReport(ReportInBo bo) {
        reportMapper.addInReport(bo);
        return bo.getId();
    }
}
