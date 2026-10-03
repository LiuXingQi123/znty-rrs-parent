package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.securitypoolexcelimport.PoolMemberDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 证券/主体 Excel 导入数据访问接口
 * <p>覆盖：通用导入临时主表/明细、目标池解析、池内成员查询。</p>
 */
@Mapper
public interface SecurityPoolExcelImportMapper {

    // ─────────── 导入临时主表 sys_imp_tmp ───────────

    /**
     * 新增导入批次
     */
    int addBatch(SysImpTmpBo bo);

    /**
     * 按业务批次号查询有效批次
     */
    SysImpTmpBo queryBatchByImpId(@Param("impId") String impId);

    /** 锁定导入批次，串行处理主体修改、校验、提交和取消 */
    Long queryBatchIdForUpdate(@Param("impId") String impId);

    /**
     * 更新批次校验结果与计数
     */
    int editBatchCheckResult(SysImpTmpBo bo);

    /**
     * 更新批次保存结果
     */
    int editBatchSaveResult(SysImpTmpBo bo);

    /**
     * 逻辑删除批次
     */
    int deleteBatchSoft(@Param("impId") String impId);

    // ─────────── 导入临时明细表 sys_imp_tmp_detl ───────────

    /**
     * 批量新增明细
     */
    int addItemList(@Param("list") List<SysImpTmpDetlBo> list);

    /**
     * 分页查询导入明细（配合 PageHelper）
     */
    List<SysImpTmpDetlBo> queryItemPage(@Param("impId") String impId,
                                        @Param("chkRslt") String chkRslt,
                                        @Param("keyword") String keyword);

    /**
     * 查询批次下全部有效明细
     */
    List<SysImpTmpDetlBo> queryBatchItemList(@Param("impId") String impId);

    /** 按明细 ID 查询本批有效导入行 */
    SysImpTmpDetlBo queryItemById(@Param("impId") String impId, @Param("itemId") Long itemId);

    /**
     * 更新单条明细校验结果
     */
    int editItemCheckResult(SysImpTmpDetlBo bo);

    /** 更新当前导入行的关联主体和 ABS 自选权益人 */
    int editRatingCompany(SysImpTmpDetlBo bo);

    /** 主体选择变更后清空本批全部行的旧校验结果 */
    int editItemCheckResultByImpId(@Param("impId") String impId, @Param("updtTime") Date updtTime);

    /**
     * 更新单条明细保存结果
     */
    int editItemSaveResult(SysImpTmpDetlBo bo);

    /**
     * 逻辑删除批次下全部明细
     */
    int deleteItemsByImpIdSoft(@Param("impId") String impId);

    /**
     * 统计指定校验结果数量
     */
    int queryItemCountByCheckResult(@Param("impId") String impId, @Param("chkRslt") String chkRslt);

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
