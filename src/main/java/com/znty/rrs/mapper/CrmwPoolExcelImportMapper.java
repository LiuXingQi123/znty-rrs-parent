package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.crmwpooladjust.SecurityInfoDetailDto;
import com.znty.rrs.entity.crmwpoolexcelimport.CrmwPoolExcelImportPoolDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * CRMW 池 Excel 导入数据访问接口
 *
 * 覆盖：通用导入临时主表/明细、CRMW 叶子池、证券主数据查询
 */
@Mapper
public interface CrmwPoolExcelImportMapper {

    /**
     * 新增导入批次
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param bo 待写入的导入批次
     * @return 受影响的行数
     */
    int addBatch(SysImpTmpBo bo);

    /**
     * 按业务批次号查询有效批次
     *
     * @param impId 导入批次编号
     * @return 导入批次，未找到时为 null
     */
    SysImpTmpBo queryByImpId(@Param("impId") String impId);

    /**
     * 锁定导入批次，串行处理主体修改、校验、提交和取消
     *
     * @param impId 导入批次编号
     * @return 被锁定的导入批次主键，无匹配记录时为 null
     */
    Long queryBatchIdForUpdate(@Param("impId") String impId);

    /**
     * 查询指定批次的有效导入行
     *
     * @param impId 导入批次编号
     * @param itemId 导入明细 ID
     * @return 导入明细，未找到时为 null
     */
    SysImpTmpDetlBo queryItemById(@Param("impId") String impId, @Param("itemId") Long itemId);

    /**
     * 更新单条导入行的评级主体
     *
     * @param bo 包含 ID 及待修改字段的导入明细
     * @return 受影响的行数
     */
    int editRatingCompany(SysImpTmpDetlBo bo);

    /**
     * 清除批次内所有导入行的旧校验结果
     *
     * @param impId 导入批次编号
     * @param updtTime 记录更新时间
     * @return 受影响的行数
     */
    int resetItemCheckResults(@Param("impId") String impId, @Param("updtTime") Date updtTime);

    /**
     * 更新批次校验结果与计数
     *
     * @param bo 导入批次编号、通过和失败数量、校验结果及说明
     * @return 受影响的行数
     */
    int editBatchCheckResult(SysImpTmpBo bo);

    /**
     * 更新批次保存结果
     *
     * @param bo 导入批次编号、提交结果及说明
     * @return 受影响的行数
     */
    int editBatchSaveResult(SysImpTmpBo bo);

    /**
     * 逻辑删除批次
     *
     * @param impId 导入批次编号
     * @return 受影响的行数
     */
    int deleteBatchSoft(@Param("impId") String impId);

    /**
     * 批量新增明细
     *
     * @param list 待写入的导入明细列表
     * @return 受影响的行数
     */
    int addItemList(@Param("list") List<SysImpTmpDetlBo> list);

    /**
     * 分页查询指定批次的有效导入明细
     *
     * @param impId 导入批次编号
     * @param chkRslt 校验结果代码，为空时不筛选
     * @param keyword 用于匹配导入明细的关键字，为空时不筛选
     * @return 符合条件的导入明细列表
     */
    List<SysImpTmpDetlBo> queryItemPage(@Param("impId") String impId,
                                        @Param("chkRslt") String chkRslt,
                                        @Param("keyword") String keyword);

    /**
     * 查询批次下全部有效明细
     *
     * @param impId 导入批次编号
     * @return 符合条件的导入明细列表
     */
    List<SysImpTmpDetlBo> queryAllByImpId(@Param("impId") String impId);

    /**
     * 更新单条明细校验结果及解析字段
     *
     * @param bo 包含 ID 及待修改字段的导入明细
     * @return 受影响的行数
     */
    int editItemCheckResult(SysImpTmpDetlBo bo);

    /**
     * 更新单条明细保存结果
     *
     * @param bo 包含 ID 及待修改字段的导入明细
     * @return 受影响的行数
     */
    int editItemSaveResult(SysImpTmpDetlBo bo);

    /**
     * 逻辑删除批次下全部明细
     *
     * @param impId 导入批次编号
     * @return 受影响的行数
     */
    int deleteItemsByImpIdSoft(@Param("impId") String impId);

    /**
     * 统计指定校验结果数量
     *
     * @param impId 导入批次编号
     * @param chkRslt 校验结果代码
     * @return 指定校验结果的有效导入明细数
     */
    int countByChkRslt(@Param("impId") String impId, @Param("chkRslt") String chkRslt);

    /**
     * 查询启用叶子 CRMW 投资池列表
     *
     * @return 符合条件的CRMW 导入目标池列表
     */
    List<CrmwPoolExcelImportPoolDto> queryEnabledLeafCrmwPoolList();

    /**
     * 按 ID 查询启用叶子 CRMW 投资池
     *
     * @param poolId 投资池 ID
     * @return 投资池配置，未找到时为 null
     */
    InvestmentPoolBo queryEnabledLeafCrmwPoolById(@Param("poolId") Long poolId);

    /**
     * 按 Wind 代码查询证券主数据（含类型名称）
     *
     * @param securityCode 要精确匹配的 Wind 证券代码
     * @return 证券详细信息，未找到时为 null
     */
    SecurityInfoDetailDto querySecurityImportInfoByCode(@Param("securityCode") String securityCode);
}
