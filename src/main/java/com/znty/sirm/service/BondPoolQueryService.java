package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.mapper.BondPoolQueryMapper;
import com.znty.sirm.mapper.MyBondPoolMapper;
import com.znty.sirm.model.BondPoolQueryDto;
import com.znty.sirm.model.BondPoolQueryReq;
import com.znty.sirm.model.MyBondPoolBo;
import com.znty.sirm.model.MyBondPoolReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 债券池查询业务逻辑
 */
@Service
public class BondPoolQueryService {

    @Resource
    private BondPoolQueryMapper bondPoolQueryMapper;

    @Resource
    private MyBondPoolMapper myBondPoolMapper;

    /** 分页查询债券池中的债券列表 */
    public PageResult<BondPoolQueryDto> queryPage(BondPoolQueryReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BondPoolQueryDto> list = bondPoolQueryMapper.queryPage(req);
        PageInfo<BondPoolQueryDto> pageInfo = new PageInfo<>(list);
        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 查询债券类型下拉选项 */
    public List<String> queryBondTypeOptions() {
        return bondPoolQueryMapper.queryBondTypeOptions();
    }

    /** 查询债券状态下拉选项 */
    public List<String> queryBondStatusOptions() {
        List<String> options = new ArrayList<>();
        options.add("active");
        options.add("matured");
        return options;
    }

    /** 添加债券到我的债券池 */
    public MyBondPoolBo addToMyPool(MyBondPoolReq req) {
        MyBondPoolBo existing = myBondPoolMapper.findByUserAndCode(req.getUserId(), req.getSecurityCode());
        if (existing != null) {
            return existing;
        }
        MyBondPoolBo bo = new MyBondPoolBo();
        bo.setSecurityCode(req.getSecurityCode());
        bo.setSecurityType(req.getSecurityType());
        bo.setMarket(req.getMarket());
        bo.setUserId(req.getUserId());
        myBondPoolMapper.addToMyPool(bo);
        return bo;
    }

    /** 从我的债券池移除 */
    public MyBondPoolBo removeFromMyPool(MyBondPoolReq req) {
        MyBondPoolBo existing = myBondPoolMapper.findByUserAndCode(req.getUserId(), req.getSecurityCode());
        if (existing != null) {
            myBondPoolMapper.removeFromMyPool(req.getUserId(), req.getSecurityCode());
        }
        return existing;
    }

    /** 批量查询用户已收藏的证券代码 */
    public List<String> queryFavoritedCodes(String userId) {
        return myBondPoolMapper.queryFavoritedCodes(userId);
    }

}
