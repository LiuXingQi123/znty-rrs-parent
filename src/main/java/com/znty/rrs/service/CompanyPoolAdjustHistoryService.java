package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.common.util.QueryListExportHelper;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.mapper.CompanyPoolAdjustHistoryMapper;
import com.znty.rrs.entity.companypool.CompanyPoolAdjustHistoryDto;
import com.znty.rrs.entity.companypool.CompanyPoolAdjustHistoryExportDto;
import com.znty.rrs.entity.companypool.CompanyPoolAdjustHistoryReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * 主体池调整历史查询服务。
 * <p>负责主体池调整日志的分页查询与按筛选条件导出。</p>
 */
@Service
public class CompanyPoolAdjustHistoryService {

    /** 主体池调整历史数据访问组件 */
    @Resource
    private CompanyPoolAdjustHistoryMapper companyPoolAdjustHistoryMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /** 分页查询主体池调整历史 */
    public PageResult<CompanyPoolAdjustHistoryDto> queryCompanyPoolAdjustHistoryPage(
            CompanyPoolAdjustHistoryReq req) {
        // 解析当前用户可查看的投资池范围并与页面选择求交集
        applyViewablePoolIds(req);
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<CompanyPoolAdjustHistoryDto> list = companyPoolAdjustHistoryMapper.queryCompanyPoolAdjustHistoryPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<CompanyPoolAdjustHistoryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 按当前筛选条件导出主体池调整历史。
     *
     * @param req 与列表查询相同的筛选条件（可不传分页）
     * @return 可下载的 Excel 文件
     */
    public CommonFileDto exportCompanyPoolAdjustHistoryExcel(CompanyPoolAdjustHistoryReq req) {
        // 导出与列表共用 VIEWABLE 权限并与页面选池求交
        applyViewablePoolIds(req);
        // 复用列表 SQL 全量查询命中记录
        List<CompanyPoolAdjustHistoryDto> list = companyPoolAdjustHistoryMapper.queryCompanyPoolAdjustHistoryPage(req);
        // 回填投资池完整路径，保证与页面展示一致
        fillPoolFullName(list);
        // 转换为导出行并按模板生成 Excel
        return QueryListExportHelper.fillTemplate(
                "/xlsx/company_pool_adjust_history_export_template.xlsx",
                "主体池调整历史",
                "主体池调整历史导出模板不存在",
                "生成主体池调整历史 Excel 失败：",
                buildExportRows(list));
    }

    /**
     * 将页面展示字段转换为模板填充数据。
     *
     * @param list 查询结果
     * @return Excel 导出行
     */
    private List<CompanyPoolAdjustHistoryExportDto> buildExportRows(List<CompanyPoolAdjustHistoryDto> list) {
        List<CompanyPoolAdjustHistoryExportDto> result = new ArrayList<>();
        for (CompanyPoolAdjustHistoryDto source : list) {
            CompanyPoolAdjustHistoryExportDto target = new CompanyPoolAdjustHistoryExportDto();
            target.setAdjusterName(source.getAdjusterName());
            // 提交时间格式与页面一致
            target.setSubmitTime(QueryListExportHelper.formatDateTime(source.getSubmitTime()));
            target.setCompanyName(source.getCompanyName());
            target.setCompanyCode(source.getCompanyCode());
            target.setAdjustType(source.getAdjustType());
            target.setAdjustMode(source.getAdjustMode());
            target.setTargetPoolName(source.getTargetPoolName());
            // 审核状态转中文
            target.setAuditStatusLabel(QueryListExportHelper.auditStatusLabel(source.getAuditStatus()));
            result.add(target);
        }
        return result;
    }

    /**
     * 解析当前用户可查看的投资池范围并与页面选择求交集。
     *
     * @param req 查询入参（写入 viewablePoolIds）
     */
    private void applyViewablePoolIds(CompanyPoolAdjustHistoryReq req) {
        Set<Long> permittedIds = investmentPoolService.queryPermittedPoolIdsByUser(
                req.getCurrentUserId(), PermissionType.VIEWABLE.getCode());
        if (permittedIds != null) {
            if (req.getPoolIds() != null && !req.getPoolIds().isEmpty()) {
                permittedIds.retainAll(new HashSet<>(req.getPoolIds()));
            }
            req.setViewablePoolIds(new ArrayList<>(permittedIds));
        }
    }

    /**
     * 填充投资池全路径名称。
     *
     * @param list 查询结果
     */
    private void fillPoolFullName(List<CompanyPoolAdjustHistoryDto> list) {
        if (list.isEmpty()) {
            return;
        }
        // 查询投资池全路径名称映射
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (CompanyPoolAdjustHistoryDto dto : list) {
            String fullName = poolFullNameMap.get(dto.getTargetPoolId());
            if (fullName != null) {
                dto.setTargetPoolName(fullName);
            }
        }
    }
}
