package com.znty.sirm.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.sirm.common.PageResult;
import com.znty.sirm.exception.BizException;
import com.znty.sirm.mapper.BondPoolAdjustMapper;
import com.znty.sirm.mapper.InvestmentPoolMapper;
import com.znty.sirm.model.AdjustLogDto;
import com.znty.sirm.model.AdjustSubmitDto;
import com.znty.sirm.model.BondInfoBo;
import com.znty.sirm.model.BondInfoDetailDto;
import com.znty.sirm.model.BondInfoDto;
import com.znty.sirm.model.BondPoolAdjustReq;
import com.znty.sirm.model.BondPoolAdjustSubmitReq;
import com.znty.sirm.model.BondPoolStatusDto;
import com.znty.sirm.model.PoolStatusDto;
import com.znty.sirm.model.InvestmentPoolBo;
import com.znty.sirm.model.IpAdjustLogBo;
import com.znty.sirm.model.PoolDto;
import com.znty.sirm.model.PoolRelationBo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
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
    public BondInfoDetailDto queryBondDetail(BondPoolAdjustReq req) {
        if (req.getBondId() == null) {
            throw new BizException("债券ID不能为空");
        }
        BondInfoBo bo = bondPoolAdjustMapper.queryBondDetail(req.getBondId());
        if (bo == null) {
            throw new BizException(404, "债券不存在");
        }
        return toBondInfoDetailDto(bo);
    }

    /** 查询可调库/可调出库的投资池列表（树结构由前端组装） */
    public List<PoolDto> queryAdjustPoolList(BondPoolAdjustReq req) {
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools == null || allPools.isEmpty()) {
            return new ArrayList<>();
        }
        List<PoolRelationBo> mutexList = investmentPoolMapper.queryMutexRelationList();
        // 按池 ID 分组互斥关系：调入互斥 / 调出互斥
        Map<Long, List<Long>> inMutexMap = new HashMap<>();
        Map<Long, List<Long>> outMutexMap = new HashMap<>();
        if (mutexList != null) {
            for (PoolRelationBo r : mutexList) {
                if ("in_mutex".equals(r.getRelationType())) {
                    inMutexMap.computeIfAbsent(r.getPoolId(), k -> new ArrayList<>()).add(r.getRelationPoolId());
                } else if ("out_mutex".equals(r.getRelationType())) {
                    outMutexMap.computeIfAbsent(r.getPoolId(), k -> new ArrayList<>()).add(r.getRelationPoolId());
                }
            }
        }
        return allPools.stream().map(p -> toPoolDto(p, inMutexMap, outMutexMap)).collect(Collectors.toList());
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
        d.setBondRating(bo.getRatingBond());
        d.setIssuerRating(bo.getRatingBondissuer());
        d.setBondType(bo.getDBondType() != null ? String.valueOf(bo.getDBondType()) : null);
        d.setCurrentRate(null);
        d.setTermStr(bo.getDateExists());
        return d;
    }

    /** BondInfoBo → BondInfoDetailDto */
    private BondInfoDetailDto toBondInfoDetailDto(BondInfoBo bo) {
        BondInfoDetailDto d = new BondInfoDetailDto();
        d.setFullName(bo.getBInfoFullname());
        d.setBondShortName(bo.getSInfoName());
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
        d.setCurrentRate(null);
        d.setRemExeTerm(bo.getDateInrightExists());
        d.setCarryDate(bo.getBInfoCarrydate());
        d.setMaturityDate(bo.getBInfoMaturitydate());
        d.setPledgeRatio(bo.getBInfoPledgeRatio());
        d.setRatingAgency(bo.getRatingBondAgency());
        d.setBondRating(bo.getRatingBond());
        d.setIssuerRating(bo.getRatingBondissuer());
        d.setRatingOutlook(bo.getRatingOutlook());
        d.setGuaranteeStatus(bo.getBAgencyGrnttype());
        d.setLeadUnderwriter(bo.getBAgencyName());
        d.setInnerIssuerRating(bo.getInnerIssuerRating());
        d.setBondType(bo.getDBondType() != null ? String.valueOf(bo.getDBondType()) : null);
        d.setPutExeTerm(bo.getDateRedemtionExists());
        d.setCallRemTerm(bo.getDateCallExists());
        d.setInnerGuarantorRating(bo.getInnerGuarantorRating());
        d.setOptRemTerm(bo.getDateInrightExists());
        d.setTermStr(bo.getDateExists());
        d.setFundUsage(bo.getBFundUsage());
        d.setPromptReason(bo.getBPromptReason());
        d.setAnalysis(bo.getBAnalysis());
        return d;
    }

    /** InvestmentPoolBo → PoolDto */
    private PoolDto toPoolDto(InvestmentPoolBo pool,
                              Map<Long, List<Long>> inMutexMap,
                              Map<Long, List<Long>> outMutexMap) {
        PoolDto dto = new PoolDto();
        dto.setId(pool.getId());
        dto.setParentId(pool.getParentId());
        dto.setPoolName(pool.getPoolName());
        dto.setPoolCode(pool.getPoolCode());
        dto.setPoolType(pool.getPoolType());
        dto.setPoolLevel(pool.getPoolLevel());
        dto.setMaxCapacity(pool.getMaxCapacity());
        dto.setCurrentCount(0);
        dto.setInMutexPoolIds(inMutexMap.getOrDefault(pool.getId(), Collections.emptyList()));
        dto.setOutMutexPoolIds(outMutexMap.getOrDefault(pool.getId(), Collections.emptyList()));
        return dto;
    }

    /** 查询债券当前所在池及主体所在池 */
    public BondPoolStatusDto queryBondPoolStatus(BondPoolAdjustReq req) {
        if (req.getBondCode() == null || req.getBondCode().isEmpty()) {
            throw new BizException("债券代码不能为空");
        }
        BondPoolStatusDto dto = new BondPoolStatusDto();
        dto.setBondCurrentPools(bondPoolAdjustMapper.queryBondPoolStatus(req.getBondCode()));
        dto.setIssuerCurrentPools(bondPoolAdjustMapper.queryIssuerPoolStatus(req.getBondCode()));
        return dto;
    }

    /** 提交调库申请 */
    @Transactional(rollbackFor = Exception.class)
    public AdjustSubmitDto submitAdjust(BondPoolAdjustSubmitReq req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("调库项不能为空");
        }
        List<Long> logIds = new ArrayList<>();
        for (BondPoolAdjustSubmitReq.AdjustItem item : req.getItems()) {
            IpAdjustLogBo bo = buildAdjustLog(req, item);
            bondPoolAdjustMapper.addAdjustLog(bo);
            logIds.add(bo.getId());
        }
        AdjustSubmitDto dto = new AdjustSubmitDto();
        dto.setBondCode(req.getBondCode());
        dto.setSubmitCount(logIds.size());
        dto.setLogIds(logIds);
        return dto;
    }

    /** 构建调库记录实体 */
    private IpAdjustLogBo buildAdjustLog(BondPoolAdjustSubmitReq req, BondPoolAdjustSubmitReq.AdjustItem item) {
        IpAdjustLogBo bo = new IpAdjustLogBo();
        bo.setBondCode(req.getBondCode());
        bo.setBondShortName(req.getBondShortName());
        bo.setBondType(req.getBondType());
        bo.setAdjustType(req.getAdjustType());
        bo.setAdjustMode(item.getAdjustMode());
        bo.setTargetPoolId(item.getTargetPoolId());
        bo.setTargetPoolName(item.getTargetPoolName());
        bo.setPoolType(item.getPoolType());
        bo.setAuditStatus("00");
        bo.setAdjusterId(req.getAdjusterId());
        bo.setAdjusterName(req.getAdjusterName());
        bo.setAdjustReason(req.getAdjustReason());
        bo.setAdjustAdvice(req.getAdjustAdvice());
        bo.setAttachmentFiles(item.getAttachmentFiles());
        bo.setMaterialFiles(item.getMaterialFiles());
        return bo;
    }

    /** 查询债券的调库记录列表 */
    public List<AdjustLogDto> queryAdjustLogList(BondPoolAdjustReq req) {
        if (req.getBondCode() == null || req.getBondCode().isEmpty()) {
            throw new BizException("债券代码不能为空");
        }
        List<IpAdjustLogBo> logs = bondPoolAdjustMapper.queryAdjustLogList(req.getBondCode());
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取所有目标池及其父级信息以构建路径
        Map<Long, InvestmentPoolBo> poolMap = new HashMap<>();
        List<InvestmentPoolBo> allPools = investmentPoolMapper.queryPoolList();
        if (allPools != null) {
            for (InvestmentPoolBo p : allPools) {
                poolMap.put(p.getId(), p);
            }
        }

        List<AdjustLogDto> result = new ArrayList<>();
        for (IpAdjustLogBo log : logs) {
            AdjustLogDto dto = new AdjustLogDto();
            dto.setId(log.getId());
            dto.setPoolPath(buildPoolPath(log.getTargetPoolId(), poolMap));
            dto.setAdjustType(log.getAdjustType());
            dto.setAdjustMode(log.getAdjustMode());
            dto.setAttachmentFiles(log.getAttachmentFiles());
            dto.setMaterialFiles(log.getMaterialFiles());
            dto.setAuditStatus(log.getAuditStatus());
            dto.setAdjustReason(log.getAdjustReason());
            dto.setAdjustAdvice(log.getAdjustAdvice());
            dto.setSubmitTime(log.getSubmitTime());
            result.add(dto);
        }
        return result;
    }

    /** 构建投资池路径（父级名称/名称） */
    private String buildPoolPath(Long poolId, Map<Long, InvestmentPoolBo> poolMap) {
        InvestmentPoolBo pool = poolMap.get(poolId);
        if (pool == null) {
            return "";
        }
        if (pool.getParentId() != null) {
            InvestmentPoolBo parent = poolMap.get(pool.getParentId());
            if (parent != null && parent.getPoolName() != null) {
                return parent.getPoolName() + "/" + (pool.getPoolName() != null ? pool.getPoolName() : "");
            }
        }
        return pool.getPoolName() != null ? pool.getPoolName() : "";
    }

}
