package com.znty.rrs.service;

import com.znty.rrs.common.enums.BondStatus;
import com.znty.rrs.common.util.QueryListExportHelper;
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
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 查询证券当前所在的投资池
 *
 * 按证券、池和状态等条件分页查询生效记录，提供证券类型及状态选项，并支持按相同条件导出
 * 同时维护当前用户的个人收藏，便于从查询结果中保存或移除关注的证券
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

    /**
     * 分页查询证券池中的证券列表
     *
     * 执行顺序：
     * 1. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 2. 填充投资池全路径名称
     * 3. 将本页记录和查询总条数一起返回
     *
     * @param req 与列表查询相同的筛选条件（可不传分页）
     * @return 查询记录及分页信息
     */
    public PageResult<SecurityPoolQueryDto> querySecurityPoolPage(SecurityPoolQueryReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SecurityPoolQueryDto> list = securityPoolQueryMapper.querySecurityPoolPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<SecurityPoolQueryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 按当前筛选条件生成证券池查询 Excel
     *
     * 执行顺序：
     * 1. 复用列表 SQL 全量查询命中记录
     * 2. 回填投资池完整路径，保证与页面展示一致
     *
     * @param req 与列表查询相同的筛选条件（可不传分页）
     * @return 可下载的 Excel 文件
     */
    public CommonFileDto exportSecurityPoolExcel(SecurityPoolQueryReq req) {
        // 1. 复用列表 SQL 全量查询命中记录
        List<SecurityPoolQueryDto> list = securityPoolQueryMapper.querySecurityPoolPage(req);
        // 2. 回填投资池完整路径，保证与页面展示一致
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
                // 转换为导出行并填充模板
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

    /**
     * 将页面展示字段转换为模板填充数据
     *
     * 执行顺序：
     * 1. 逐项格式化页面相同的入池时间
     *
     * @param list 本次处理的数据列表
     * @return 筛选出的列表
     */
    private List<SecurityPoolQueryExportDto> buildExportRows(List<SecurityPoolQueryDto> list) {
        List<SecurityPoolQueryExportDto> result = new ArrayList<>();
        for (SecurityPoolQueryDto source : list) {
            SecurityPoolQueryExportDto target = new SecurityPoolQueryExportDto();
            target.setSecurityShortName(source.getSecurityShortName());
            target.setSecurityCode(source.getSecurityCode());
            target.setAdjusterName(source.getAdjusterName());
            // 格式化页面相同的入池时间
            target.setEntryTime(formatDateTime(source.getEntryTime()));
            target.setTargetPoolName(source.getTargetPoolName());
            target.setSecurityTypeName(source.getSecurityTypeName());
            target.setCouponRate(source.getCouponRate());
            target.setIssuer(source.getIssuer());
            target.setFullName(source.getFullName());
            target.setIssueDate(source.getIssueDate());
            target.setCarryDate(source.getCarryDate());
            target.setMaturityDate(source.getMaturityDate());
            // 与页面「证券期限」列一致，直接导出 dateExistsStr 展示串
            target.setDateExistsStr(source.getDateExistsStr() == null ? "" : source.getDateExistsStr());
            target.setSecurityStatusLabel(QueryListExportHelper.bondStatusLabel(source.getSecurityStatus()));
            target.setDelistDate(source.getDelistDate());
            target.setRepurchaseDate(source.getRepurchaseDate());
            // 转换是否展示文字
            target.setAbsLabel(yesNo(source.getAbsFlag() != null && source.getAbsFlag() == 1 || "abs".equals(source.getSecurityType())));
            // 转换是否展示文字，再判断数值标志是否为是
            target.setGuarantLabel(yesNo(isOne(source.getGuarantFlag())));
            // 转换是否展示文字，再判断数值标志是否为是
            target.setYxLabel(yesNo(isOne(source.getYxFlag())));
            // 转换是否展示文字，再判断数值标志是否为是
            target.setCjLabel(yesNo(isOne(source.getCjFlag())));
            // 转换是否展示文字，再判断文本是否标识私募
            target.setPrivateLabel(yesNo(containsPrivate(source.getIssueType()) || containsPrivate(source.getInnerClass())));
            // 转换是否展示文字，再判断数值标志是否为是
            target.setInrightLabel(yesNo(isOne(source.getInrightFlag())));
            // 转换是否展示文字，再判断数值标志是否为是
            target.setStdCreditLabel(yesNo(isOne(source.getStdCreditFlag())));
            result.add(target);
        }
        return result;
    }

    /**
     * 格式化页面相同的入池时间
     *
     * 执行顺序：
     * 1. 缺少入池时间时返回空字符串，否则按 yyyy-MM-dd HH:mm:ss 格式化
     *
     * @param value 入池时间
     * @return 格式化后的日期时间；为空时返回空字符串
     */
    private String formatDateTime(Date value) {
        return value == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value);
    }

    /**
     * 转换是否展示文字
     *
     * 执行顺序：
     * 1. 输入为 true 时显示「是」，为 false 时显示「否」
     *
     * @param value 待处理的字段值
     * @return 是否展示文字
     */
    private String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    /**
     * 判断数值标志是否为是
     *
     * 执行顺序：
     * 1. 把标志值与 1 比较，空值按未命中处理
     *
     * @param value 待处理的字段值
     * @return true=标志值为 1，false=标志为空或不为 1
     */
    private boolean isOne(Integer value) {
        return value != null && value == 1;
    }

    /**
     * 判断文本是否标识私募
     *
     * 执行顺序：
     * 1. 文本有值且包含「私募」时返回 true，其余情况返回 false
     *
     * @param value 待统一空值的文本
     * @return true=文本含私募标识，false=文本不含私募标识
     */
    private boolean containsPrivate(String value) {
        return value != null && value.contains("私募");
    }

    /**
     * 填充投资池全路径名称
     *
     * 执行顺序：
     * 1. 查询投资池全路径名称映射
     *
     * @param list 本次处理的数据列表
     */
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

    /**
     * 查询证券类型下拉选项（code + name）
     *
     * 执行顺序：
     * 1. 查询禁投池中出现的证券类型下拉选项（code + name）
     *
     * @return 证券类型选项列表
     */
    public List<SecurityTypeOptionDto> querySecurityTypeList() {
        return securityPoolQueryMapper.querySecurityTypeList();
    }

    /**
     * 查询证券状态下拉选项
     *
     * 执行顺序：
     * 1. 依次加入正常和到期对应的证券状态编码
     *
     * @return 证券状态列表
     */
    public List<String> querySecurityStatusList() {
        List<String> options = new ArrayList<>();
        options.add(BondStatus.ACTIVE.getCode());
        options.add(BondStatus.MATURED.getCode());
        return options;
    }

    /**
     * 添加证券到我的证券池（幂等：若已收藏则直接返回已有记录，不重复插入）
     *
     * 执行顺序：
     * 1. 先查询是否已收藏，避免重复写入
     *
     * @param req 收藏用户 ID、证券代码、证券类型和市场
     * @return 用户个人收藏记录
     */
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

    /**
     * 从我的证券池移除（证券不在收藏中时静默返回 null，不抛异常）
     *
     * 执行顺序：
     * 1. 删除前查询确认存在，并保留记录用于返回给前端展示
     *
     * @param req 操作用户 ID 和要取消收藏的证券代码
     * @return 用户个人收藏记录
     */
    public MySecurityPoolBo deleteSecurityFromMyPool(MySecurityPoolReq req) {
        // 删除前查询确认存在，并保留记录用于返回给前端展示
        MySecurityPoolBo existing = mySecurityPoolMapper.queryByUserAndCode(req.getUserId(), req.getSecurityCode());
        if (existing != null) {
            mySecurityPoolMapper.deleteSecurityFromMyPool(req.getUserId(), req.getSecurityCode());
        }
        return existing;
    }

    /**
     * 批量查询用户已收藏的证券代码
     *
     * 执行顺序：
     * 1. 按用户 ID 读取已经收藏的证券代码列表
     *
     * @param req 要查询证券收藏的用户 ID
     * @return 用户已收藏的证券代码列表
     */
    public List<String> queryFavoritedCodeList(MySecurityPoolReq req) {
        return mySecurityPoolMapper.queryFavoritedCodeList(req.getUserId());
    }

}
