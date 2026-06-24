package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.ReportMapper;
import com.znty.sirm.model.ReportDto;
import com.znty.sirm.model.ReportReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 报告库查询服务
 */
@Service
public class ReportService {

    /** 报告库数据访问组件 */
    @Resource
    private ReportMapper reportMapper;

    /** 分页查询内部报告库列表 */
    public PageResult<ReportDto> queryInReportPage(ReportReq req) {
        if (req == null) {
            req = new ReportReq();
        }
        // 开启分页查询
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<ReportDto> list = reportMapper.queryInReportPage(req);
        PageInfo<ReportDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 分页查询外部报告库列表 */
    public PageResult<ReportDto> queryOutReportPage(ReportReq req) {
        if (req == null) {
            req = new ReportReq();
        }
        // 开启分页查询
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<ReportDto> list = reportMapper.queryOutReportPage(req);
        PageInfo<ReportDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }
}
