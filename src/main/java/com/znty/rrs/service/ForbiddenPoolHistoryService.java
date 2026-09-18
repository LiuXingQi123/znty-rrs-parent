package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.common.util.QueryListExportHelper;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.mapper.ForbiddenPoolHistoryMapper;
import com.znty.rrs.entity.forbiddenpoolhistory.ForbiddenPoolHistoryDto;
import com.znty.rrs.entity.forbiddenpoolhistory.ForbiddenPoolHistoryExportDto;
import com.znty.rrs.entity.forbiddenpoolhistory.ForbiddenPoolHistoryReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;

/**
 * 禁投池历史查询服务。
 * <p>负责禁投池调整日志的分页查询与按筛选条件导出。</p>
 */
@Service
public class ForbiddenPoolHistoryService {

    /** 禁投池历史数据访问组件 */
    @Resource
    private ForbiddenPoolHistoryMapper forbiddenPoolHistoryMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /** 分页查询禁投池调整历史 */
    public PageResult<ForbiddenPoolHistoryDto> queryForbiddenPoolHistoryPage(ForbiddenPoolHistoryReq req) {
        // 解析当前用户可查看的投资池范围
        applyViewablePoolIds(req);
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<ForbiddenPoolHistoryDto> list = forbiddenPoolHistoryMapper.queryForbiddenPoolHistoryPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<ForbiddenPoolHistoryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 按当前筛选条件导出禁投池历史。
     *
     * @param req 与列表查询相同的筛选条件（可不传分页）
     * @return 可下载的 Excel 文件
     */
    public CommonFileDto exportForbiddenPoolHistoryExcel(ForbiddenPoolHistoryReq req) {
        // 导出与列表共用 VIEWABLE 权限范围
        applyViewablePoolIds(req);
        // 复用列表 SQL 全量查询命中记录
        List<ForbiddenPoolHistoryDto> list = forbiddenPoolHistoryMapper.queryForbiddenPoolHistoryPage(req);
        // 回填投资池完整路径，保证与页面展示一致
        fillPoolFullName(list);
        // 转换为导出行并按模板生成 Excel
        return QueryListExportHelper.fillTemplate(
                "/xlsx/forbidden_pool_history_export_template.xlsx",
                "禁投池历史",
                "禁投池历史导出模板不存在",
                "生成禁投池历史 Excel 失败：",
                buildExportRows(list));
    }

    /**
     * 将页面展示字段转换为模板填充数据。
     *
     * @param list 查询结果
     * @return Excel 导出行
     */
    private List<ForbiddenPoolHistoryExportDto> buildExportRows(List<ForbiddenPoolHistoryDto> list) {
        List<ForbiddenPoolHistoryExportDto> result = new ArrayList<>();
        for (ForbiddenPoolHistoryDto source : list) {
            ForbiddenPoolHistoryExportDto target = new ForbiddenPoolHistoryExportDto();
            target.setAdjusterName(source.getAdjusterName());
            // 提交时间格式与页面一致
            target.setSubmitTime(QueryListExportHelper.formatDateTime(source.getSubmitTime()));
            target.setSecurityShortName(source.getSecurityShortName());
            target.setSecurityCode(source.getSecurityCode());
            target.setIssuer(source.getIssuer());
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
     * 解析当前用户可查看的投资池范围。
     *
     * @param req 查询入参（写入 viewablePoolIds）
     */
    private void applyViewablePoolIds(ForbiddenPoolHistoryReq req) {
        Set<Long> permittedIds = investmentPoolService.queryPermittedPoolIdsByUser(
                req.getCurrentUserId(), PermissionType.VIEWABLE.getCode());
        req.setViewablePoolIds(permittedIds == null ? null : new ArrayList<>(permittedIds));
    }

    /**
     * 填充投资池全路径名称。
     *
     * @param list 查询结果
     */
    private void fillPoolFullName(List<ForbiddenPoolHistoryDto> list) {
        if (list.isEmpty()) {
            return;
        }
        // 查询投资池全路径名称映射
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (ForbiddenPoolHistoryDto dto : list) {
            String fullName = poolFullNameMap.get(dto.getTargetPoolId());
            if (fullName != null) {
                dto.setTargetPoolName(fullName);
            }
        }
    }
}
