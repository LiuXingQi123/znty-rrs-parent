package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.StockInfoBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 股票 Excel 导入临时批次、明细及目标池查询。 */
@Mapper
public interface StockPoolExcelImportMapper {
    /**
     * 新增股票导入批次。
     *
     * @param batch 包含锁定业务参数的股票导入批次
     */
    int addBatch(SysImpTmpBo batch);
    /**
     * 查询有效股票导入批次。
     *
     * @param impId 股票导入批次号
     */
    SysImpTmpBo queryBatchByImpId(@Param("impId") String impId);
    /**
     * 锁定有效股票导入批次。
     *
     * @param impId 需取得行锁的股票导入批次号
     */
    Long queryBatchIdForUpdate(@Param("impId") String impId);
    /**
     * 保存批次校验快照及计数。
     *
     * @param batch 包含行级计数和服务器校验快照的批次
     */
    int editBatchCheckResult(SysImpTmpBo batch);
    /**
     * 保存批次提交结果。
     *
     * @param batch 包含提交状态、说明和结果快照的批次
     */
    int editBatchSaveResult(SysImpTmpBo batch);
    /**
     * 取消未提交批次。
     *
     * @param impId 尚未提交的股票导入批次号
     */
    int deleteBatchSoft(@Param("impId") String impId);
    /**
     * 批量插入股票原始明细。
     *
     * @param items 保留原始四列和 Excel 物理行号的明细
     */
    int addItemList(@Param("list") List<SysImpTmpDetlBo> items);
    /**
     * 分页查询明细，配合 PageHelper。
     *
     * @param impId 股票导入批次号
     * @param chkRslt 可选行级校验结果筛选
     * @param keyword 可选股票代码或名称关键字
     */
    List<SysImpTmpDetlBo> queryItemPage(@Param("impId") String impId,
                                     @Param("chkRslt") String chkRslt, @Param("keyword") String keyword);
    /**
     * 查询当前批次全部有效明细。
     *
     * @param impId 股票导入批次号
     */
    List<SysImpTmpDetlBo> queryBatchItemList(@Param("impId") String impId);
    /**
     * 回写明细校验、目标池及权威股票名称。
     *
     * @param item 包含校验结果、解析池及主档股票名称的来源明细
     */
    int editItemCheckResult(SysImpTmpDetlBo item);
    /**
     * 回写已提交来源行保存结果。
     *
     * @param item 已成功生成股票审批申请的来源明细
     */
    int editItemSaveResult(SysImpTmpDetlBo item);
    /**
     * 逻辑删除当前批次明细。
     *
     * @param impId 待取消的股票导入批次号
     */
    int deleteItemsByImpIdSoft(@Param("impId") String impId);
    /**
     * 按父子池名称查询支持股票的启用叶子池，多个匹配交由业务拒绝。
     *
     * @param parentPoolName Excel 原始父池名称
     * @param childPoolName Excel 原始子池名称
     */
    List<InvestmentPoolBo> queryEnabledLeafPoolList(@Param("parentPoolName") String parentPoolName,
                                                 @Param("childPoolName") String childPoolName);
    /**
     * 查询目标池当前已审批通过的股票成员。
     *
     * @param poolId 需计算清空差集的目标股票池 ID
     */
    List<StockInfoBo> queryPoolMemberList(@Param("poolId") Long poolId);
}
