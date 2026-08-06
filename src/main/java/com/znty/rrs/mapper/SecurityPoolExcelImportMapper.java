package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SysImpTmpBatchBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.securitypoolexcelimport.PoolMemberDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 证券/主体 Excel 导入数据访问接口
 * <p>覆盖：导入批次临时表、导入明细临时表、目标池解析、池内成员查询。</p>
 */
@Mapper
public interface SecurityPoolExcelImportMapper {

    // ─────────── 导入批次主表 sys_imp_tmp_batch ───────────

    /**
     * 新增导入批次
     */
    int insertBatch(SysImpTmpBatchBo bo);

    /**
     * 按业务批次号查询有效批次
     */
    SysImpTmpBatchBo queryByImpId(@Param("impId") String impId);

    /**
     * 更新批次校验结果与计数
     */
    int updateBatchCheckResult(SysImpTmpBatchBo bo);

    /**
     * 更新批次保存结果
     */
    int updateBatchSaveResult(SysImpTmpBatchBo bo);

    /**
     * 逻辑删除批次
     */
    int deleteBatchSoft(@Param("impId") String impId);

    // ─────────── 导入明细临时表 sys_imp_tmp ───────────

    /**
     * 批量新增明细
     */
    int insertItemList(@Param("list") List<SysImpTmpBo> list);

    /**
     * 按条件查询明细列表（配合 PageHelper）
     */
    List<SysImpTmpBo> queryItemList(@Param("impId") String impId,
                                    @Param("chkRslt") String chkRslt,
                                    @Param("keyword") String keyword);

    /**
     * 查询批次下全部有效明细
     */
    List<SysImpTmpBo> queryAllByImpId(@Param("impId") String impId);

    /**
     * 更新单条明细校验结果
     */
    int updateItemCheckResult(SysImpTmpBo bo);

    /**
     * 更新单条明细保存结果
     */
    int updateItemSaveResult(SysImpTmpBo bo);

    /**
     * 逻辑删除批次下全部明细
     */
    int deleteItemsByImpIdSoft(@Param("impId") String impId);

    /**
     * 统计指定校验结果数量
     */
    int countByChkRslt(@Param("impId") String impId, @Param("chkRslt") String chkRslt);

    // ─────────── 目标池 ───────────

    /**
     * 按父池名称 + 子池名称解析启用叶子投资池
     * <p>父池名为空时按子池名称匹配启用叶子池（含根叶子）。</p>
     *
     * @param parentPoolName 父池名称（可空）
     * @param childPoolName  子池名称
     * @return 匹配的叶子池，不存在时返回 null
     */
    InvestmentPoolBo queryEnabledLeafPoolByParentAndChildName(@Param("parentPoolName") String parentPoolName,
                                                             @Param("childPoolName") String childPoolName);

    /**
     * 查询目标池当前有效在池成员（audit_status=20）
     *
     * @param poolId     目标池 ID
     * @param memberType security=排除 crmw/company；company=仅 company
     * @return 在池成员列表
     */
    List<PoolMemberDto> queryPoolMemberList(@Param("poolId") Long poolId,
                                            @Param("memberType") String memberType);
}
