package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.util.QueryListExportHelper;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.fundpooladjusthistory.FundPoolAdjustHistoryDto;
import com.znty.rrs.entity.fundpooladjusthistory.FundPoolAdjustHistoryExportDto;
import com.znty.rrs.entity.fundpooladjusthistory.FundPoolAdjustHistoryReq;
import com.znty.rrs.mapper.FundPoolAdjustHistoryMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基金池调整历史服务。
 * <p>负责基金调库历史分页查询、导出，以及筛选条件所需的基金类型查询。</p>
 */
@Service
public class FundPoolAdjustHistoryService {

    /** 基金池调整历史数据访问组件。 */
    @Resource
    private FundPoolAdjustHistoryMapper fundPoolAdjustHistoryMapper;

    /** 投资池服务。 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /**
     * 分页查询基金池调整历史列表。
     *
     * @param req 基金池调整历史筛选条件
     * @return 分页后的基金池调整历史记录
     */
    public PageResult<FundPoolAdjustHistoryDto> queryFundPoolAdjustHistoryPage(FundPoolAdjustHistoryReq req) {
        // 开启分页并查询基金池调整历史列表。
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<FundPoolAdjustHistoryDto> list = fundPoolAdjustHistoryMapper.queryFundPoolAdjustHistoryPage(req);
        // 回填投资池全路径名称，保持页面展示完整。
        fillPoolFullName(list);
        // 读取分页总数并组装分页结果。
        PageInfo<FundPoolAdjustHistoryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 按当前筛选条件导出全部命中的基金池调整历史。
     *
     * @param req 与列表查询相同的筛选条件（可不传分页）
     * @return 可下载的 Excel 文件
     */
    public CommonFileDto exportFundPoolAdjustHistoryExcel(FundPoolAdjustHistoryReq req) {
        // 复用列表 SQL 查询全部命中记录，不受当前分页限制。
        List<FundPoolAdjustHistoryDto> list = fundPoolAdjustHistoryMapper.queryFundPoolAdjustHistoryPage(req);
        // 回填投资池全路径名称，保证导出与页面展示一致。
        fillPoolFullName(list);
        // 将查询记录转换为模板所需的导出行。
        List<FundPoolAdjustHistoryExportDto> exportRows = buildExportRows(list);
        // 按基金历史模板生成 Excel 文件。
        return QueryListExportHelper.fillTemplate(
                "/xlsx/fund_pool_adjust_history_export_template.xlsx",
                "基金池调整历史",
                "基金池调整历史导出模板不存在",
                "生成基金池调整历史 Excel 失败：",
                exportRows);
    }

    /**
     * 查询调整历史中出现的基金类型选项。
     *
     * @return 基金类型选项列表
     */
    public List<SecurityTypeOptionDto> queryFundTypeList() {
        return fundPoolAdjustHistoryMapper.queryFundTypeList();
    }

    /**
     * 将页面展示字段转换为 Excel 行。
     *
     * @param list 基金池调整历史记录
     * @return Excel 导出行
     */
    private List<FundPoolAdjustHistoryExportDto> buildExportRows(List<FundPoolAdjustHistoryDto> list) {
        List<FundPoolAdjustHistoryExportDto> result = new ArrayList<>();
        for (FundPoolAdjustHistoryDto source : list) {
            FundPoolAdjustHistoryExportDto target = new FundPoolAdjustHistoryExportDto();
            target.setAdjusterName(source.getAdjusterName());
            // 提交时间按页面展示格式写入 Excel。
            target.setSubmitTime(QueryListExportHelper.formatDateTime(source.getSubmitTime()));
            target.setFundName(source.getFundName());
            target.setFundCode(source.getFundCode());
            target.setSecurityTypeName(source.getSecurityTypeName());
            target.setAdjustType(source.getAdjustType());
            target.setAdjustMode(source.getAdjustMode());
            target.setTargetPoolPath(source.getTargetPoolPath());
            // 审核状态按统一字典转换为导出文案。
            target.setAuditStatusLabel(QueryListExportHelper.auditStatusLabel(source.getAuditStatus()));
            result.add(target);
        }
        return result;
    }

    /**
     * 填充投资池全路径名称。
     *
     * @param list 查询结果
     */
    private void fillPoolFullName(List<FundPoolAdjustHistoryDto> list) {
        if (list.isEmpty()) {
            return;
        }
        // 查询投资池全路径名称映射。
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (FundPoolAdjustHistoryDto dto : list) {
            String fullName = poolFullNameMap.get(dto.getTargetPoolId());
            if (fullName != null) {
                dto.setTargetPoolPath(fullName);
            }
        }
    }
}
