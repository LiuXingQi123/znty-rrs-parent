package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.enums.PermissionType;
import com.znty.rrs.mapper.ForbiddenPoolQueryMapper;
import com.znty.rrs.entity.forbiddenpoolquery.ForbiddenPoolQueryDto;
import com.znty.rrs.entity.forbiddenpoolquery.ForbiddenPoolQueryReq;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;

/**
 * 禁投池查询服务。
 * <p>负责禁投池证券的分页查询，以及筛选条件所需的证券类型下拉选项查询。</p>
 */
@Service
public class ForbiddenPoolQueryService {

    /** 禁投池查询数据访问组件 */
    @Resource
    private ForbiddenPoolQueryMapper forbiddenPoolQueryMapper;

    /** 投资池服务 */
    @Resource
    private InvestmentPoolService investmentPoolService;

    /** 分页查询禁投池证券列表 */
    public PageResult<ForbiddenPoolQueryDto> queryForbiddenPoolPage(ForbiddenPoolQueryReq req) {
        // 解析当前用户可查看的投资池范围
        Set<Long> permittedIds = investmentPoolService.queryPermittedPoolIdsByUser(
                req.getCurrentUserId(), PermissionType.VIEWABLE.getCode());
        req.setViewablePoolIds(permittedIds == null ? null : new ArrayList<>(permittedIds));
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<ForbiddenPoolQueryDto> list = forbiddenPoolQueryMapper.queryForbiddenPoolPage(req);
        // 填充投资池全路径名称
        fillPoolFullName(list);
        PageInfo<ForbiddenPoolQueryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 填充投资池全路径名称 */
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

    /** 查询禁投池中出现的证券类型下拉选项（code + name） */
    public List<SecurityTypeOptionDto> querySecurityTypeList(ForbiddenPoolQueryReq req) {
        // 解析当前用户可查看的投资池范围
        Set<Long> permittedIds = investmentPoolService.queryPermittedPoolIdsByUser(
                req.getCurrentUserId(), PermissionType.VIEWABLE.getCode());
        req.setViewablePoolIds(permittedIds == null ? null : new ArrayList<>(permittedIds));
        return forbiddenPoolQueryMapper.querySecurityTypeList(req);
    }
}
