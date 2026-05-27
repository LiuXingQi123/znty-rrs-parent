package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.exception.BizException;
import com.znty.sirm.mapper.BondPoolAdjustMapper;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.model.BondInfoBo;
import com.znty.sirm.model.BondInfoDetailDto;
import com.znty.sirm.model.BondInfoDto;
import com.znty.sirm.model.BondPoolAdjustReq;
import com.znty.sirm.model.InvestmentPoolBo;
import com.znty.sirm.model.PoolTreeNodeDto;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 债券池调库业务逻辑
 */
@Service
public class BondPoolAdjustService {

    @Resource
    private BondPoolAdjustMapper bondPoolAdjustMapper;

    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /** 分页查询债券列表 */
    public PageResult<BondInfoDto> queryBondPage(BondPoolAdjustReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<BondInfoBo> entities = bondPoolAdjustMapper.queryBondPage(
                req.getBondCode(), req.getBondShortName(), req.getIssuer());
        PageInfo<BondInfoBo> pageInfo = new PageInfo<>(entities);

        List<BondInfoDto> records = entities.stream().map(this::toBondInfoDto).collect(Collectors.toList());
        return new PageResult<>(records, pageInfo.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /** 查询债券详情 */
    public BondInfoDetailDto queryBondDetail(Long bondId) {
        if (bondId == null) {
            throw new BizException("债券ID不能为空");
        }
        BondInfoBo bo = bondPoolAdjustMapper.queryBondDetail(bondId);
        if (bo == null) {
            throw new BizException(404, "债券不存在");
        }
        return toBondInfoDetailDto(bo);
    }

    /** 查询可调库/可调出库的投资池树 */
    public List<PoolTreeNodeDto> queryPoolTreeForAdjust(Long bondId, String adjustDirection) {
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools == null || allPools.isEmpty()) {
            return new ArrayList<>();
        }

        // 构建 ID → 节点映射
        Map<Long, PoolTreeNodeDto> nodeMap = new HashMap<>();
        List<PoolTreeNodeDto> roots = new ArrayList<>();

        for (InvestmentPoolBo pool : allPools) {
            PoolTreeNodeDto node = toPoolTreeNode(pool);
            nodeMap.put(node.getId(), node);
        }

        // 组装树结构
        for (InvestmentPoolBo pool : allPools) {
            PoolTreeNodeDto node = nodeMap.get(pool.getId());
            if (pool.getParentId() != null && nodeMap.containsKey(pool.getParentId())) {
                nodeMap.get(pool.getParentId()).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }

        return roots;
    }

    /** BondInfoBo → BondInfoDto */
    private BondInfoDto toBondInfoDto(BondInfoBo bo) {
        BondInfoDto d = new BondInfoDto();
        d.setId(bo.getId());
        d.setBondCode(bo.getSInfoCode());
        d.setBondShortName(bo.getSInfoName());
        d.setIssuer(bo.getBInfoIssuer());
        d.setFullName(bo.getBInfoFullname());
        d.setIssueAmount(bo.getBIssueAmountplan());
        d.setCarryDate(bo.getBInfoCarrydate());
        d.setMaturityDate(bo.getBInfoMaturitydate());
        d.setBondRating(bo.getBCreditRating());
        d.setIssuerRating(bo.getIssuerCreditRating());
        d.setBondType(mapBondType(bo.getDBondType()));
        d.setCurrentRate(bo.getBInfoCurInterestrate());
        d.setTermStr(bo.getBInfoTermStr());
        return d;
    }

    /** BondInfoBo → BondInfoDetailDto */
    private BondInfoDetailDto toBondInfoDetailDto(BondInfoBo bo) {
        BondInfoDetailDto d = new BondInfoDetailDto();
        d.setFullName(bo.getBInfoFullname());
        d.setBondCode(bo.getSInfoCode());
        d.setIssuerName(bo.getBInfoIssuer());
        d.setNibCode(bo.getSWindcodeNib());
        // 交易所代码：优先沪市，其次深市
        if (bo.getSWindcodeSh() != null && !bo.getSWindcodeSh().isEmpty()) {
            d.setExchangeCode(bo.getSWindcodeSh());
        } else {
            d.setExchangeCode(bo.getSWindcodeSz());
        }
        d.setIssueAmount(bo.getBIssueAmountplan());
        d.setCurrentRate(bo.getBInfoCurInterestrate());
        d.setRemExeTerm(bo.getBInfoRemExeTerm());
        d.setCarryDate(bo.getBInfoCarrydate());
        d.setMaturityDate(bo.getBInfoMaturitydate());
        d.setPledgeRatio(bo.getBInfoPledgeRatio());
        d.setRatingAgency(bo.getCreditRatingAgency());
        d.setBondRating(bo.getBCreditRating());
        d.setIssuerRating(bo.getIssuerCreditRating());
        d.setRatingOutlook(bo.getRatingOutlook());
        d.setGuaranteeStatus(bo.getBInfoGuaranteeStatus());
        d.setLeadUnderwriter(bo.getBInfoLeadUnderwriter());
        d.setInnerIssuerRating(bo.getInnerIssuerRating());
        d.setBondType(mapBondType(bo.getDBondType()));
        d.setPutExeTerm(bo.getBInfoPutExeTerm());
        d.setCallRemTerm(bo.getBInfoCallRemTerm());
        d.setInnerGuarantorRating(bo.getInnerGuarantorRating());
        d.setOptRemTerm(bo.getBInfoOptRemTerm());
        d.setTermStr(bo.getBInfoTermStr());
        d.setFundUsage(bo.getBFundUsage());
        d.setPromptReason(bo.getBPromptReason());
        d.setAnalysis(bo.getBAnalysis());
        return d;
    }

    /** InvestmentPoolBo → PoolTreeNodeDto */
    private PoolTreeNodeDto toPoolTreeNode(InvestmentPoolBo pool) {
        PoolTreeNodeDto node = new PoolTreeNodeDto();
        node.setId(pool.getId());
        node.setParentId(pool.getParentId());
        node.setPoolName(pool.getPoolName());
        node.setPoolCode(pool.getPoolCode());
        node.setPoolType(pool.getPoolType());
        node.setPoolLevel(pool.getPoolLevel());
        node.setMaxCapacity(pool.getMaxCapacity());
        node.setCurrentCount(0);
        node.setAttachmentReport(null);
        node.setOtherMaterials(null);
        node.setChildren(new ArrayList<>());
        return node;
    }

    /** 债券类型 int → 中文 */
    private String mapBondType(Integer dBondType) {
        if (dBondType == null) {
            return null;
        }
        switch (dBondType) {
            case 1: return "中期票据";
            case 2: return "公司债";
            case 3: return "可交换债";
            case 4: return "商业银行债";
            case 5: return "短期融资券";
            case 6: return "资产支持证券";
            case 7: return "超短期融资券";
            default: return "其他";
        }
    }
}
