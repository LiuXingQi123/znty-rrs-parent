package com.znty.sirm.service;

import com.znty.sirm.common.enums.BondStatus;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.SecurityPoolQueryMapper;
import com.znty.sirm.mapper.MySecurityPoolMapper;
import com.znty.sirm.entity.securitypoolquery.SecurityPoolQueryDto;
import com.znty.sirm.entity.securitypoolquery.SecurityPoolQueryReq;
import com.znty.sirm.entity.common.SecurityTypeOptionDto;
import com.znty.sirm.entity.bo.MySecurityPoolBo;
import com.znty.sirm.entity.securitypoolquery.MySecurityPoolReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
