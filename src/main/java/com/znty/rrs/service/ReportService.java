package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.CategoryType;
import com.znty.rrs.common.enums.ReportType;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.SysAttachmentBo;
import com.znty.rrs.mapper.ReportMapper;
import com.znty.rrs.entity.report.ReportDto;
import com.znty.rrs.entity.bo.ReportInBo;
import com.znty.rrs.entity.report.ReportReq;
import com.znty.rrs.entity.sysattachment.SysAttachmentDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报告库查询及调库完成后的内部报告生成服务
 */
@Service
public class ReportService {

    /** 报告库数据访问组件 */
    @Resource
    private ReportMapper reportMapper;

    /** 系统附件服务，用于查询手工信评附件及绑定内部报告库附件 */
    @Resource
    private SysAttachmentService sysAttachmentService;

    /** 投资池服务，用于查询投资池全路径名称 */
    @Resource
    private InvestmentPoolService investmentPoolService;

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
     * 证券、主体或 CRMW 调库直通或正常审批通过后，逐条调库记录将手工上传的信评报告附件沉淀为内部报告库记录。
     * 每条调库记录生成 1 条 rrs_report_in，其下所有手工上传信评报告附件复制为该报告的 report_in 附件。
     * 无手工上传信评报告附件的调库记录跳过。
     *
     * @param logList 已通过最终复核并落池的调库记录列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void addInternalReportsOnFinish(List<IpAdjustLogBo> logList) {
        if (logList == null || logList.isEmpty()) {
            return;
        }
        Map<Long, String> poolFullNameMap = null;
        for (IpAdjustLogBo log : logList) {
            // 查询该调库记录下手工上传的信评报告附件
            List<SysAttachmentBo> handAttachments = sysAttachmentService.queryHandCreditReportAttachments(log.getId());
            if (handAttachments == null || handAttachments.isEmpty()) {
                continue;
            }
            if (poolFullNameMap == null) {
                // 存在手工信评附件时，整批只查一次投资池全路径映射
                poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
            }
            // 查询证券基础信息，取证券全称与主体编码
            SecurityInfoBo securityInfo = reportMapper.querySecurityBoByCode(log.getSecurityCode());
            // 查询证券所属大类，用于映射报告类型
            String categoryType = reportMapper.queryCategoryTypeBySecurityType(log.getSecurityType());
            // 组装报告标题：证券全称 + 调入/调出 + 投资池全路径名称 + 报告
            String securityFullName = securityInfo != null && securityInfo.getFullName() != null
                    ? securityInfo.getFullName() : log.getSecurityShortName();
            String poolFullName = poolFullNameMap.get(log.getTargetPoolId());
            if (poolFullName == null || poolFullName.isEmpty()) {
                poolFullName = log.getTargetPoolName();
            }
            String reportTitle = securityFullName + log.getAdjustMode() + poolFullName + "报告";
            // 构建内部报告记录
            ReportInBo reportInBo = new ReportInBo();
            reportInBo.setAuthorName(log.getAdjusterName());
            reportInBo.setReportTitle(reportTitle);
            // 按证券大类和调整方向确定报告类型
            reportInBo.setReportType(resolveReportType(categoryType, log.getAdjustMode()));
            reportInBo.setSecurityCode(log.getSecurityCode());
            // 主体调库直接使用主体代码，证券及 CRMW 使用证券发行主体代码
            reportInBo.setCompanyCode(CategoryType.COMPANY.getCode().equals(categoryType)
                    ? log.getSecurityCode() : (securityInfo != null ? securityInfo.getIssuerCode() : null));
            // 按证券大类确定内部报告的证券类型
            reportInBo.setSecurityType(resolveReportSecurityType(categoryType));
            reportInBo.setDataSource("uploaded");
            // 写入内部报告并回填主键 ID
            Long reportId = addInReport(reportInBo);
            // 将手工上传信评报告附件复制为该内部报告的附件
            sysAttachmentService.bindReportFileAttachments(reportId, handAttachments);
        }
    }

    /**
     * 根据证券大类与调入/调出方向映射内部报告类型。
     *
     * @param categoryType 证券大类（bond/fund/stock/company 等）
     * @param adjustMode   调整方向（调入/调出）
     */
    private String resolveReportType(String categoryType, String adjustMode) {
        boolean outbound = AdjustMode.OUT.getCode().equals(adjustMode);
        if (CategoryType.BOND.getCode().equals(categoryType)) {
            return outbound ? ReportType.BOND_OUT_REPORT.getCode() : ReportType.BOND_IN_REPORT.getCode();
        }
        if (CategoryType.FUND.getCode().equals(categoryType)) {
            return outbound ? ReportType.FUND_OUT_REPORT.getCode() : ReportType.FUND_IN_REPORT.getCode();
        }
        if (CategoryType.STOCK.getCode().equals(categoryType)) {
            return outbound ? ReportType.STOCK_OUT_REPORT.getCode() : ReportType.STOCK_IN_REPORT.getCode();
        }
        return ReportType.OTHER_REPORT.getCode();
    }

    /**
     * 根据证券大类映射内部报告证券类型，未匹配归为其他。
     *
     * @param categoryType 证券大类（bond/fund/stock/company 等）
     */
    private String resolveReportSecurityType(String categoryType) {
        if (CategoryType.BOND.getCode().equals(categoryType) || CategoryType.FUND.getCode().equals(categoryType)
                || CategoryType.STOCK.getCode().equals(categoryType) || CategoryType.COMPANY.getCode().equals(categoryType)) {
            return categoryType;
        }
        return "other";
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
