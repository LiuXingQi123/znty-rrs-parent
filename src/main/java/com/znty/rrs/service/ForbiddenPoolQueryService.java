package com.znty.rrs.service;

import com.znty.rrs.common.enums.CategoryType;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.common.util.QueryListExportHelper;
import com.znty.rrs.entity.commonfile.CommonFileDto;
import com.znty.rrs.mapper.ForbiddenPoolQueryMapper;
import com.znty.rrs.entity.forbiddenpoolquery.ForbiddenPoolQueryDto;
import com.znty.rrs.entity.forbiddenpoolquery.ForbiddenPoolQueryExportDto;
import com.znty.rrs.entity.forbiddenpoolquery.ForbiddenPoolQueryReq;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;

/**
 * 查询禁投池的当前在池记录
 *
 * 按池和证券条件筛选生效记录，并提供页面所需的证券类型选项
 * Excel 导出沿用列表筛选条件，统一展示投资池全路径名称
 */
@Service
public class ForbiddenPoolQueryService {

    /** 禁投池查询数据访问组件 */
    @Resource
    private ForbiddenPoolQueryMapper forbiddenPoolQueryMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /**
     * 分页查询禁投池证券列表
     *
     * 执行顺序：
     * 1. 解析当前用户可查看的投资池范围
     * 2. 按请求的页码和每页条数开启分页，并用筛选条件查询记录
     * 3. 填充投资池全路径名称
     * 4. 将本页记录和查询总条数一起返回
     *
     * @param req 与列表查询相同的筛选条件（可不传分页）
     * @return 查询记录及分页信息
     */
    public PageResult<ForbiddenPoolQueryDto> queryForbiddenPoolPage(ForbiddenPoolQueryReq req) {
        // 1. 解析当前用户可查看的投资池范围
        applyViewablePoolIds(req);
        // 2. 按页码和筛选条件查询
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<ForbiddenPoolQueryDto> list = forbiddenPoolQueryMapper.queryForbiddenPoolPage(req);
        // 3. 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<ForbiddenPoolQueryDto> pageInfo = new PageInfo<>(list);
        // 4. 返回本页记录和查询总条数
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 按当前筛选条件导出禁投池查询结果
     *
     * 执行顺序：
     * 1. 导出与列表共用 VIEWABLE 权限范围
     * 2. 复用列表 SQL 全量查询命中记录
     * 3. 回填投资池完整路径，保证与页面展示一致
     * 4. 转换为导出行并按模板生成 Excel
     *
     * @param req 与列表查询相同的筛选条件（可不传分页）
     * @return 可下载的 Excel 文件
     */
    public CommonFileDto exportForbiddenPoolExcel(ForbiddenPoolQueryReq req) {
        // 1. 导出与列表共用 VIEWABLE 权限范围
        applyViewablePoolIds(req);
        // 2. 复用列表 SQL 全量查询命中记录
        List<ForbiddenPoolQueryDto> list = forbiddenPoolQueryMapper.queryForbiddenPoolPage(req);
        // 3. 回填投资池完整路径，保证与页面展示一致
        fillPoolFullName(list);
        // 4. 转换为导出行并按模板生成 Excel
        return QueryListExportHelper.fillTemplate(
                "/xlsx/forbidden_pool_query_export_template.xlsx",
                "禁投池查询",
                "禁投池查询导出模板不存在",
                "生成禁投池查询 Excel 失败：",
                // 将页面展示字段转换为模板填充数据
                buildExportRows(list));
    }

    /**
     * 将页面展示字段转换为模板填充数据
     *
     * 执行顺序：
     * 1. 逐项格式化入池时间
     *
     * @param list 查询结果
     * @return Excel 导出行
     */
    private List<ForbiddenPoolQueryExportDto> buildExportRows(List<ForbiddenPoolQueryDto> list) {
        List<ForbiddenPoolQueryExportDto> result = new ArrayList<>();
        for (ForbiddenPoolQueryDto source : list) {
            ForbiddenPoolQueryExportDto target = new ForbiddenPoolQueryExportDto();
            target.setSecurityShortName(source.getSecurityShortName());
            target.setSecurityCode(source.getSecurityCode());
            target.setIssuer(source.getIssuer());
            target.setAdjusterName(source.getAdjusterName());
            target.setSecurityTypeName(source.getSecurityTypeName());
            target.setTargetPoolName(source.getTargetPoolName());
            // 入池时间格式与页面一致
            target.setEntryTime(QueryListExportHelper.formatDateTime(source.getEntryTime()));
            // 债券按主档状态展示，主体行显示空白
            target.setSecurityStatusLabel(CategoryType.BOND.getCode().equals(source.getCategoryType())
                    ? QueryListExportHelper.bondStatusLabel(source.getSecurityStatus()) : "");
            target.setMaturityDate(source.getMaturityDate());
            target.setRepurchaseDate(source.getRepurchaseDate());
            result.add(target);
        }
        return result;
    }

    /**
     * 解析当前用户可查看的投资池范围
     *
     * 执行顺序：
     * 1. 查询当前用户通过本人或角色拥有指定类型权限的投资池
     *
     * @param req 查询入参（写入 viewablePoolIds）
     */
    private void applyViewablePoolIds(ForbiddenPoolQueryReq req) {
        Set<Long> permittedIds = investmentPoolService.queryPermittedPoolIdsByUser(
                req.getCurrentUserId(), PermissionType.VIEWABLE.getCode());
        req.setViewablePoolIds(permittedIds == null ? null : new ArrayList<>(permittedIds));
    }

    /**
     * 填充投资池全路径名称
     *
     * 执行顺序：
     * 1. 查询投资池全路径名称映射
     *
     * @param list 查询结果
     */
    private void fillPoolFullName(List<ForbiddenPoolQueryDto> list) {
        if (list.isEmpty()) {
            return;
        }
        // 查询投资池全路径名称映射
        Map<Long, String> poolFullNameMap = investmentPoolService.queryPoolFullNameMap();
        for (ForbiddenPoolQueryDto dto : list) {
            String fullName = poolFullNameMap.get(dto.getTargetPoolId());
            if (fullName != null) {
                dto.setTargetPoolName(fullName);
            }
        }
    }

    /**
     * 查询禁投池中出现的证券类型下拉选项（code + name）
     *
     * 执行顺序：
     * 1. 解析当前用户可查看的投资池范围
     *
     * @param req 与列表查询相同的筛选条件（可不传分页）
     * @return 证券类型选项列表
     */
    public List<SecurityTypeOptionDto> querySecurityTypeList(ForbiddenPoolQueryReq req) {
        // 解析当前用户可查看的投资池范围
        applyViewablePoolIds(req);
        return forbiddenPoolQueryMapper.querySecurityTypeList(req);
    }
}
