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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        // 根据到期日推导债券状态
        for (BondPoolQueryDto dto : list) {
            dto.setBondStatus(mapBondStatus(dto.getMaturityDate()));
        }

        return new PageResult<>(list, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 查询债券类型下拉选项 */
    public List<String> queryBondTypeOptions() {
        return bondPoolQueryMapper.queryBondTypeOptions();
    }

    /** 查询债券状态下拉选项 */
    public List<Map<String, String>> queryBondStatusOptions() {
        List<Map<String, String>> options = new ArrayList<>();
        Map<String, String> active = new HashMap<>();
        active.put("value", "active");
        active.put("label", "存续");
        options.add(active);
        Map<String, String> matured = new HashMap<>();
        matured.put("value", "matured");
        matured.put("label", "到期");
        options.add(matured);
        return options;
    }

    /** 添加债券到我的债券池 */
    public void addToMyPool(MyBondPoolReq req) {
        MyBondPoolBo existing = myBondPoolMapper.findByUserAndCode(req.getUserId(), req.getSecurityCode());
        if (existing != null) {
            // 已存在，无需重复添加
            return;
        }
        MyBondPoolBo bo = new MyBondPoolBo();
        bo.setSecurityCode(req.getSecurityCode());
        bo.setSecurityType(req.getSecurityType());
        bo.setMarket(req.getMarket());
        bo.setUserId(req.getUserId());
        myBondPoolMapper.addToMyPool(bo);
    }

    /** 从我的债券池移除 */
    public void removeFromMyPool(MyBondPoolReq req) {
        myBondPoolMapper.removeFromMyPool(req.getUserId(), req.getSecurityCode());
    }

    /** 批量查询用户已收藏的证券代码 */
    public List<String> queryFavoritedCodes(String userId) {
        return myBondPoolMapper.queryFavoritedCodes(userId);
    }

    /** 根据到期日推导债券状态 */
    private String mapBondStatus(String maturityDate) {
        if (maturityDate == null || maturityDate.isEmpty()) {
            return "存续";
        }
        if (maturityDate.compareTo(java.time.LocalDate.now().toString()) < 0) {
            return "到期";
        }
        return "存续";
    }
}
