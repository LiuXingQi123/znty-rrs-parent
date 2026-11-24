package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.FundInfoBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 基金 Excel 导入临时批次、明细及目标池查询
 */
@Mapper
public interface FundPoolExcelImportMapper {
    /**
     * 新增基金导入批次
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param batch 包含锁定业务参数的基金导入批次
     * @return 受影响的行数
     */
    int addBatch(SysImpTmpBo batch);
    /**
     * 查询有效基金导入批次
     *
     * @param impId 基金导入批次号
     * @return 导入批次，未找到时为 null
     */
    SysImpTmpBo queryBatchByImpId(@Param("impId") String impId);
    /**
     * 锁定有效基金导入批次
     *
     * @param impId 需取得行锁的基金导入批次号
     * @return 被锁定的导入批次主键，无匹配记录时为 null
     */
    Long queryBatchIdForUpdate(@Param("impId") String impId);
    /**
     * 保存批次校验快照及计数
     *
     * @param batch 包含行级计数和服务器校验快照的批次
     * @return 受影响的行数
     */
    int editBatchCheckResult(SysImpTmpBo batch);
    /**
     * 保存批次提交结果
     *
     * @param batch 包含提交状态、说明和结果快照的批次
     * @return 受影响的行数
     */
    int editBatchSaveResult(SysImpTmpBo batch);
    /**
     * 取消未提交批次
     *
     * @param impId 尚未提交的基金导入批次号
     * @return 受影响的行数
     */
    int deleteBatchSoft(@Param("impId") String impId);
    /**
     * 批量插入基金原始明细
     *
     * @param items 保留原始七列和 Excel 物理行号的明细
     * @return 受影响的行数
     */
    int addItemList(@Param("list") List<SysImpTmpDetlBo> items);
    /**
     * 分页查询明细，配合 PageHelper
     *
     * @param impId 基金导入批次号
     * @param chkRslt 可选行级校验结果筛选
     * @param keyword 可选基金代码或名称关键字
     * @return 符合条件的导入明细列表
     */
    List<SysImpTmpDetlBo> queryItemPage(@Param("impId") String impId,
                                     @Param("chkRslt") String chkRslt, @Param("keyword") String keyword);
    /**
     * 查询当前批次全部有效明细
     *
     * @param impId 基金导入批次号
     * @return 符合条件的导入明细列表
     */
    List<SysImpTmpDetlBo> queryBatchItemList(@Param("impId") String impId);
    /**
     * 回写明细校验、目标池及权威基金名称
     *
     * @param item 包含校验结果、解析池及主档基金名称的来源明细
     * @return 受影响的行数
     */
    int editItemCheckResult(SysImpTmpDetlBo item);
    /**
     * 回写已提交来源行保存结果
     *
     * @param item 已成功生成基金审批申请的来源明细
     * @return 受影响的行数
     */
    int editItemSaveResult(SysImpTmpDetlBo item);
    /**
     * 逻辑删除当前批次明细
     *
     * @param impId 待取消的基金导入批次号
     * @return 受影响的行数
     */
    int deleteItemsByImpIdSoft(@Param("impId") String impId);
    /**
     * 按父子池名称查询支持基金的启用叶子池，多个匹配交由业务拒绝
     *
     * @param parentPoolName Excel 原始父池名称
     * @param childPoolName Excel 原始子池名称
     * @return 符合条件的投资池配置列表
     */
    List<InvestmentPoolBo> queryEnabledLeafPoolList(@Param("parentPoolName") String parentPoolName,
                                                 @Param("childPoolName") String childPoolName);
    /**
     * 查询目标池当前已审批通过的基金成员
     *
     * @param poolId 需计算清空差集的目标基金池 ID
     * @return 符合条件的基金主档列表
     */
    List<FundInfoBo> queryPoolMemberList(@Param("poolId") Long poolId);
}
