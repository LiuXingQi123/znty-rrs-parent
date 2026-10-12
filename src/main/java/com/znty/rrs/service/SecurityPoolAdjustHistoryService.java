package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.util.QueryListExportHelper;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.mapper.SecurityPoolAdjustHistoryMapper;
import com.znty.rrs.entity.securitypooladjusthistory.SecurityPoolAdjustHistoryDto;
import com.znty.rrs.entity.securitypooladjusthistory.SecurityPoolAdjustHistoryExportDto;
import com.znty.rrs.entity.securitypooladjusthistory.SecurityPoolAdjustHistoryReq;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 证券池调整历史服务。
 * <p>负责证券池调整操作审计记录的分页查询，以及筛选条件所需的证券类型选项查询。</p>
 */
@Service
public class SecurityPoolAdjustHistoryService {

    /** 证券池调库历史数据访问组件 */
    @Resource
    private SecurityPoolAdjustHistoryMapper securityPoolAdjustHistoryMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /**
     * 分页查询证券池调整历史列表
     */
    public PageResult<SecurityPoolAdjustHistoryDto> querySecurityPoolAdjustHistoryPage(SecurityPoolAdjustHistoryReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SecurityPoolAdjustHistoryDto> list = securityPoolAdjustHistoryMapper.querySecurityPoolAdjustHistoryPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<SecurityPoolAdjustHistoryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 按当前筛选条件导出证券池调整历史。
     *
     * @param req 与列表查询相同的筛选条件（可不传分页）
     * @return 可下载的 Excel 文件
     */
    public CommonFileDto exportSecurityPoolAdjustHistoryExcel(SecurityPoolAdjustHistoryReq req) {
        // 复用列表 SQL 全量查询命中记录
        List<SecurityPoolAdjustHistoryDto> list = securityPoolAdjustHistoryMapper.querySecurityPoolAdjustHistoryPage(req);
        // 回填投资池完整路径，保证与页面展示一致
        fillPoolFullName(list);
        // 转换为导出行并按模板生成 Excel
        return QueryListExportHelper.fillTemplate(
                "/xlsx/security_pool_adjust_history_export_template.xlsx",
                "证券池调整历史",
                "证券池调整历史导出模板不存在",
                "生成证券池调整历史 Excel 失败：",
                buildExportRows(list));
    }

    /**
     * 将页面展示字段转换为模板填充数据。
     *
     * @param list 查询结果
     * @return Excel 导出行
     */
    private List<SecurityPoolAdjustHistoryExportDto> buildExportRows(List<SecurityPoolAdjustHistoryDto> list) {
        List<SecurityPoolAdjustHistoryExportDto> result = new ArrayList<>();
        for (SecurityPoolAdjustHistoryDto source : list) {
            SecurityPoolAdjustHistoryExportDto target = new SecurityPoolAdjustHistoryExportDto();
            target.setAdjusterName(source.getAdjusterName());
            // 提交时间格式与页面一致
            target.setSubmitTime(QueryListExportHelper.formatDateTime(source.getSubmitTime()));
            target.setSecurityShortName(source.getSecurityShortName());
            target.setSecurityCode(source.getSecurityCode());
            target.setSecurityTypeName(source.getSecurityTypeName());
            target.setIssuer(source.getIssuer());
            target.setAdjustType(source.getAdjustType());
            target.setAdjustMode(source.getAdjustMode());
            target.setAdjustReason(source.getAdjustReason());
            target.setTargetPoolPath(source.getTargetPoolPath());
            // 审核状态转中文
            target.setAuditStatusLabel(QueryListExportHelper.auditStatusLabel(source.getAuditStatus()));
            // 是否类字段对齐页面 Tag 判定
            target.setAbsLabel(QueryListExportHelper.yesNo(
                    QueryListExportHelper.isOne(source.getAbsFlag()) || "abs".equals(source.getSecurityType())));
            target.setGuarantLabel(QueryListExportHelper.yesNo(QueryListExportHelper.isOne(source.getGuarantFlag())));
            target.setYxLabel(QueryListExportHelper.yesNo(QueryListExportHelper.isOne(source.getYxFlag())));
            target.setCjLabel(QueryListExportHelper.yesNo(QueryListExportHelper.isOne(source.getCjFlag())));
            target.setPrivateLabel(QueryListExportHelper.yesNo(
                    QueryListExportHelper.containsPrivate(source.getIssueType())
                            || QueryListExportHelper.containsPrivate(source.getInnerClass())));
            target.setInrightLabel(QueryListExportHelper.yesNo(QueryListExportHelper.isOne(source.getInrightFlag())));
            result.add(target);
        }
        return result;
    }

    /**
     * 填充投资池全路径名称。
     *
     * @param list 查询结果
     */
    private void fillPoolFullName(List<SecurityPoolAdjustHistoryDto> list) {
        if (list.isEmpty()) {
            return;
        }
        // 查询投资池全路径名称映射
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (SecurityPoolAdjustHistoryDto dto : list) {
            String fullName = poolFullNameMap.get(dto.getTargetPoolId());
            if (fullName != null) {
                dto.setTargetPoolPath(fullName);
            }
        }
    }

    /**
     * 查询调整历史中出现的证券类型选项
     */
    public List<SecurityTypeOptionDto> querySecurityTypeList() {
        return securityPoolAdjustHistoryMapper.querySecurityTypeList();
    }

}
