package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.SecurityPoolQueryMapper;
import com.znty.sirm.mapper.MySecurityPoolMapper;
import com.znty.sirm.model.SecurityPoolQueryDto;
import com.znty.sirm.model.SecurityPoolQueryReq;
import com.znty.sirm.model.SecurityTypeOptionDto;
import com.znty.sirm.model.MySecurityPoolBo;
import com.znty.sirm.model.MySecurityPoolReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 证券池查询业务逻辑
 */
@Service
public class SecurityPoolQueryService {

    @Resource
    private SecurityPoolQueryMapper securityPoolQueryMapper;

    @Resource
    private MySecurityPoolMapper mySecurityPoolMapper;

    /** 分页查询证券池中的证券列表 */
    public PageResult<SecurityPoolQueryDto> querySecurityPoolPage(SecurityPoolQueryReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<SecurityPoolQueryDto> list = securityPoolQueryMapper.querySecurityPoolPage(req);
        PageInfo<SecurityPoolQueryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 查询证券类型下拉选项（code + name） */
    public List<SecurityTypeOptionDto> querySecurityTypeList() {
        return securityPoolQueryMapper.querySecurityTypeList();
    }

    /** 查询证券状态下拉选项 */
    public List<String> querySecurityStatusList() {
        List<String> options = new ArrayList<>();
        options.add("active");
        options.add("matured");
        return options;
    }

    /** 添加证券到我的证券池 */
    public MySecurityPoolBo addToMyPool(MySecurityPoolReq req) {
        MySecurityPoolBo existing = mySecurityPoolMapper.queryByUserAndCode(req.getUserId(), req.getSecurityCode());
        if (existing != null) {
            return existing;
        }
        MySecurityPoolBo bo = new MySecurityPoolBo();
        bo.setSecurityCode(req.getSecurityCode());
        bo.setSecurityType(req.getSecurityType());
        bo.setMarket(req.getMarket());
        bo.setUserId(req.getUserId());
        mySecurityPoolMapper.addToMyPool(bo);
        return bo;
    }

    /** 从我的证券池移除 */
    public MySecurityPoolBo deleteFromMyPool(MySecurityPoolReq req) {
        MySecurityPoolBo existing = mySecurityPoolMapper.queryByUserAndCode(req.getUserId(), req.getSecurityCode());
        if (existing != null) {
            mySecurityPoolMapper.deleteFromMyPool(req.getUserId(), req.getSecurityCode());
        }
        return existing;
    }

    /** 批量查询用户已收藏的证券代码 */
    public List<String> queryFavoritedCodeList(String userId) {
        return mySecurityPoolMapper.queryFavoritedCodeList(userId);
    }

}
