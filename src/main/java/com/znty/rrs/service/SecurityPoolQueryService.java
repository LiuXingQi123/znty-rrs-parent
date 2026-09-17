package com.znty.rrs.service;

import com.znty.rrs.common.enums.BondStatus;
import com.znty.rrs.common.util.CreditBondRemainTermUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.fill.FillWrapper;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.entity.securitypoolquery.SecurityPoolQueryExportDto;
import com.znty.rrs.exception.BizException;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.mapper.SecurityPoolQueryMapper;
import com.znty.rrs.mapper.MySecurityPoolMapper;
import com.znty.rrs.entity.securitypoolquery.SecurityPoolQueryDto;
import com.znty.rrs.entity.securitypoolquery.SecurityPoolQueryReq;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.entity.bo.MySecurityPoolBo;
import com.znty.rrs.entity.securitypoolquery.MySecurityPoolReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 证券池查询服务。
 * <p>负责证券池证券的分页查询、证券类型和状态下拉选项查询，以及"我的证券池"收藏管理。</p>
 */
@Service
public class SecurityPoolQueryService {

    /** 证券池查询数据访问组件 */
    @Resource
    private SecurityPoolQueryMapper securityPoolQueryMapper;

    /** 我的证券池数据访问组件 */
    @Resource
    private MySecurityPoolMapper mySecurityPoolMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /** 分页查询证券池中的证券列表 */
    public PageResult<SecurityPoolQueryDto> querySecurityPoolPage(SecurityPoolQueryReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SecurityPoolQueryDto> list = securityPoolQueryMapper.querySecurityPoolPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<SecurityPoolQueryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 按当前筛选条件生成证券池查询 Excel。 */
    public CommonFileDto exportSecurityPoolExcel(SecurityPoolQueryReq req) {
        List<SecurityPoolQueryDto> list = securityPoolQueryMapper.querySecurityPoolPage(req);
        // 回填投资池完整路径，保证与页面展示一致。
        fillPoolFullName(list);
        try (InputStream template = SecurityPoolQueryService.class.getResourceAsStream(
                "/xlsx/security_pool_query_export_template.xlsx");
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (template == null) {
                throw new BizException("证券池查询导出模板不存在");
            }
            ExcelWriter writer = EasyExcel.write(outputStream).withTemplate(template).build();
            try {
                WriteSheet sheet = EasyExcel.writerSheet(0).build();
                writer.fill(new FillWrapper("data", buildExportRows(list)), sheet);
            } finally {
                writer.finish();
            }
            byte[] bytes = outputStream.toByteArray();
            CommonFileDto file = new CommonFileDto();
            file.setFileName("证券池查询_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx");
            file.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            file.setFileSize((long) bytes.length);
            file.setContentBase64(Base64.getEncoder().encodeToString(bytes));
            return file;
        } catch (Exception e) {
            throw new BizException("生成证券池查询 Excel 失败：" + e.toString());
        }
    }

    /** 将页面展示字段转换为模板填充数据。 */
    private List<SecurityPoolQueryExportDto> buildExportRows(List<SecurityPoolQueryDto> list) {
        List<SecurityPoolQueryExportDto> result = new ArrayList<>();
        for (SecurityPoolQueryDto source : list) {
            SecurityPoolQueryExportDto target = new SecurityPoolQueryExportDto();
            target.setSecurityShortName(source.getSecurityShortName());
            target.setSecurityCode(source.getSecurityCode());
            target.setAdjusterName(source.getAdjusterName());
            target.setEntryTime(formatDateTime(source.getEntryTime()));
            target.setTargetPoolName(source.getTargetPoolName());
            target.setSecurityTypeName(source.getSecurityTypeName());
            target.setCouponRate(source.getCouponRate());
            target.setIssuer(source.getIssuer());
            target.setFullName(source.getFullName());
            target.setIssueDate(source.getIssueDate());
            target.setCarryDate(source.getCarryDate());
            target.setMaturityDate(source.getMaturityDate());
            target.setRemainingTermYears(formatRemainingTermYears(source.getDateExistsStr()));
            target.setSecurityStatusLabel(statusLabel(source.getSecurityStatus()));
            target.setDelistDate(source.getDelistDate());
            target.setRepurchaseDate(source.getRepurchaseDate());
            target.setAbsLabel(yesNo(source.getAbsFlag() != null && source.getAbsFlag() == 1 || "abs".equals(source.getSecurityType())));
            target.setGuarantLabel(yesNo(isOne(source.getGuarantFlag())));
            target.setYxLabel(yesNo(isOne(source.getYxFlag())));
            target.setCjLabel(yesNo(isOne(source.getCjFlag())));
            target.setPrivateLabel(yesNo(containsPrivate(source.getIssueType()) || containsPrivate(source.getInnerClass())));
            target.setInrightLabel(yesNo(isOne(source.getInrightFlag())));
            result.add(target);
        }
        return result;
    }

    /** 格式化页面相同的入池时间。 */
    private String formatDateTime(Date value) {
        return value == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value);
    }

    /** 从证券期限文本解析并格式化年数。 */
    private String formatRemainingTermYears(String termText) {
        // 使用 date_exists_str 解析期限，避免将 date_exists 总天数直接除以 365
        BigDecimal years = CreditBondRemainTermUtil.parseRemainTermYears(termText);
        return years == null ? "" : years.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    /** 转换证券状态中文名称。 */
    private String statusLabel(String status) {
        return "active".equals(status) ? "存续" : "matured".equals(status) ? "到期" : "";
    }

    /** 转换是否展示文字。 */
    private String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    /** 判断数值标志是否为是。 */
    private boolean isOne(Integer value) {
        return value != null && value == 1;
    }

    /** 判断文本是否标识私募。 */
    private boolean containsPrivate(String value) {
        return value != null && value.contains("私募");
    }

    /** 填充投资池全路径名称 */
    private void fillPoolFullName(List<SecurityPoolQueryDto> list) {
        if (list.isEmpty()) {
            return;
        }
        // 查询投资池全路径名称映射
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (SecurityPoolQueryDto dto : list) {
            String fullName = poolFullNameMap.get(dto.getTargetPoolId());
            if (fullName != null) {
                dto.setTargetPoolName(fullName);
            }
        }
    }

    /** 查询证券类型下拉选项（code + name） */
    public List<SecurityTypeOptionDto> querySecurityTypeList() {
        return securityPoolQueryMapper.querySecurityTypeList();
    }

    /** 查询证券状态下拉选项 */
    public List<String> querySecurityStatusList() {
        List<String> options = new ArrayList<>();
        options.add(BondStatus.ACTIVE.getCode());
        options.add(BondStatus.MATURED.getCode());
        return options;
    }

    /** 添加证券到我的证券池（幂等：若已收藏则直接返回已有记录，不重复插入） */
    public MySecurityPoolBo addSecurityToMyPool(MySecurityPoolReq req) {
        // 先查询是否已收藏，避免重复写入
        MySecurityPoolBo existing = mySecurityPoolMapper.queryByUserAndCode(req.getUserId(), req.getSecurityCode());
        if (existing != null) {
            return existing;
        }
        MySecurityPoolBo bo = new MySecurityPoolBo();
        bo.setSecurityCode(req.getSecurityCode());
        bo.setSecurityType(req.getSecurityType());
        bo.setMarket(req.getMarket());
        bo.setUserId(req.getUserId());
        mySecurityPoolMapper.addSecurityToMyPool(bo);
        return bo;
    }

    /** 从我的证券池移除（证券不在收藏中时静默返回 null，不抛异常） */
    public MySecurityPoolBo deleteSecurityFromMyPool(MySecurityPoolReq req) {
        // 删除前查询确认存在，并保留记录用于返回给前端展示
        MySecurityPoolBo existing = mySecurityPoolMapper.queryByUserAndCode(req.getUserId(), req.getSecurityCode());
        if (existing != null) {
            mySecurityPoolMapper.deleteSecurityFromMyPool(req.getUserId(), req.getSecurityCode());
        }
        return existing;
    }

    /** 批量查询用户已收藏的证券代码 */
    public List<String> queryFavoritedCodeList(MySecurityPoolReq req) {
        return mySecurityPoolMapper.queryFavoritedCodeList(req.getUserId());
    }

}
