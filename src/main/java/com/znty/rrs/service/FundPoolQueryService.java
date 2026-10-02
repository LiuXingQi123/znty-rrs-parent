package com.znty.rrs.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.fill.FillWrapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.fundpoolquery.FundPoolQueryDto;
import com.znty.rrs.entity.fundpoolquery.FundPoolQueryExportDto;
import com.znty.rrs.entity.fundpoolquery.FundPoolQueryReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.FundPoolQueryMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 基金池查询服务。
 * <p>负责基金池分页查询、基金类型选项查询和查询结果导出。</p>
 */
@Service
public class FundPoolQueryService {

    /** 基金池查询数据访问组件。 */
    @Resource
    private FundPoolQueryMapper fundPoolQueryMapper;

    /** 投资池服务。 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /**
     * 分页查询当前已生效的基金池状态。
     *
     * @param req 基金池筛选及分页条件
     * @return 基金池分页记录
     */
    public PageResult<FundPoolQueryDto> queryFundPoolPage(FundPoolQueryReq req) {
        // 开启分页
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        // 查询当前有效的基金池记录
        List<FundPoolQueryDto> list = fundPoolQueryMapper.queryFundPoolPage(req);
        // 回填投资池全路径名称
        fillPoolFullName(list);
        // 获取分页总数
        PageInfo<FundPoolQueryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 按当前筛选条件生成基金池查询 Excel。
     *
     * @param req 与列表查询相同的筛选条件（可不传分页）
     * @return 可下载的 Excel 文件
     */
    public CommonFileDto exportFundPoolExcel(FundPoolQueryReq req) {
        // 复用列表 SQL 全量查询命中记录
        List<FundPoolQueryDto> list = fundPoolQueryMapper.queryFundPoolPage(req);
        // 回填投资池完整路径，保证与页面展示一致
        fillPoolFullName(list);
        // 读取基金池查询导出模板
        try (InputStream template = FundPoolQueryService.class.getResourceAsStream(
                "/xlsx/fund_pool_query_export_template.xlsx");
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (template == null) {
                throw new BizException("基金池查询导出模板不存在");
            }
            ExcelWriter writer = EasyExcel.write(outputStream).withTemplate(template).build();
            try {
                WriteSheet sheet = EasyExcel.writerSheet(0).build();
                // 转换为导出行并填充基金池查询模板
                writer.fill(new FillWrapper("data", buildExportRows(list)), sheet);
            } finally {
                // 完成模板写入并释放写入器
                writer.finish();
            }
            byte[] bytes = outputStream.toByteArray();
            // 封装可下载的 Excel 文件
            CommonFileDto file = new CommonFileDto();
            file.setFileName("基金池查询_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx");
            file.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            file.setFileSize((long) bytes.length);
            file.setContentBase64(Base64.getEncoder().encodeToString(bytes));
            return file;
        } catch (Exception e) {
            throw new BizException("生成基金池查询 Excel 失败：" + e.toString());
        }
    }

    /**
     * 查询基金池中出现的基金类型选项。
     *
     * @return 当前已生效基金池中出现的基金类型
     */
    public List<SecurityTypeOptionDto> queryFundTypeList() {
        return fundPoolQueryMapper.queryFundTypeList();
    }

    /**
     * 转换页面展示字段为 Excel 行。
     *
     * @param list 页面展示数据
     * @return Excel 行数据
     */
    private List<FundPoolQueryExportDto> buildExportRows(List<FundPoolQueryDto> list) {
        List<FundPoolQueryExportDto> result = new ArrayList<>();
        // 按列表顺序组装基金导出行
        for (FundPoolQueryDto source : list) {
            FundPoolQueryExportDto target = new FundPoolQueryExportDto();
            target.setFundName(source.getFundName());
            target.setFundCode(source.getFundCode());
            target.setTargetPoolName(source.getTargetPoolName());
            target.setAdjusterName(source.getAdjusterName());
            // 格式化入池时间，保持与页面展示一致
            target.setEntryTime(formatDateTime(source.getEntryTime()));
            target.setSecurityTypeName(source.getSecurityTypeName());
            target.setFundManagerNames(source.getFundManagerNames());
            target.setFundAdministrator(source.getFundAdministrator());
            target.setFundCustodian(source.getFundCustodian());
            // 格式化成立日期，保持与页面展示一致
            target.setEstablishmentDate(formatDate(source.getEstablishmentDate()));
            target.setIssueTerm(source.getIssueTerm());
            target.setLatestNav(source.getLatestNav());
            target.setAccumulatedNav(source.getAccumulatedNav());
            target.setDailyTenThousandIncome(source.getDailyTenThousandIncome());
            target.setSevenDayAnnualizedYield(source.getSevenDayAnnualizedYield());
            target.setLatestScale(source.getLatestScale());
            target.setPreviousClosePrice(source.getPreviousClosePrice());
            target.setPremiumDiscountRate(source.getPremiumDiscountRate());
            result.add(target);
        }
        return result;
    }

    /**
     * 回填投资池全路径名称。
     *
     * @param list 基金池记录列表
     */
    private void fillPoolFullName(List<FundPoolQueryDto> list) {
        if (list.isEmpty()) {
            return;
        }
        // 查询投资池全路径名称映射
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        // 用全路径名称替换列表中的投资池名称
        for (FundPoolQueryDto dto : list) {
            String fullName = poolFullNameMap.get(dto.getTargetPoolId());
            if (fullName != null) {
                dto.setTargetPoolName(fullName);
            }
        }
    }

    /**
     * 格式化入池时间。
     *
     * @param value 入池时间
     * @return 格式化后的日期时间；为空时返回空字符串
     */
    private String formatDateTime(Date value) {
        return value == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value);
    }

    /**
     * 格式化成立日期。
     *
     * @param value 成立日期
     * @return 格式化后的日期；为空时返回空字符串
     */
    private String formatDate(Date value) {
        return value == null ? "" : new SimpleDateFormat("yyyy-MM-dd").format(value);
    }
}
