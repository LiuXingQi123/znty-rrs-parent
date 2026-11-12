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
 * <p>覆盖：通用导入临时主表/明细、CRMW 叶子池、证券主数据查询。</p>
 */
@Mapper
public interface CrmwPoolExcelImportMapper {

    /**
     * 新增导入批次
     */
    int insertBatch(SysImpTmpBo bo);

    /**
     * 按业务批次号查询有效批次
     */
    SysImpTmpBo queryByImpId(@Param("impId") String impId);

    /** 锁定导入批次，串行处理主体修改、校验、提交和取消 */
    Long queryBatchIdForUpdate(@Param("impId") String impId);

    /** 查询指定批次的有效导入行 */
    SysImpTmpDetlBo queryItemById(@Param("impId") String impId, @Param("itemId") Long itemId);

    /** 更新单条导入行的评级主体 */
    int editRatingCompany(SysImpTmpDetlBo bo);

    /** 清除批次内所有导入行的旧校验结果 */
    int resetItemCheckResults(@Param("impId") String impId, @Param("updtTime") Date updtTime);

    /**
     * 更新批次校验结果与计数
     */
    int updateBatchCheckResult(SysImpTmpBo bo);

    /**
     * 更新批次保存结果
     */
    int updateBatchSaveResult(SysImpTmpBo bo);

    /**
     * 逻辑删除批次
     */
    int deleteBatchSoft(@Param("impId") String impId);

    /**
     * 批量新增明细
     */
    int insertItemList(@Param("list") List<SysImpTmpDetlBo> list);

    /**
     * 按条件查询明细列表（配合 PageHelper）
     */
    List<SysImpTmpDetlBo> queryItemList(@Param("impId") String impId,
                                        @Param("chkRslt") String chkRslt,
                                        @Param("keyword") String keyword);

    /**
     * 查询批次下全部有效明细
     */
    List<SysImpTmpDetlBo> queryAllByImpId(@Param("impId") String impId);

    /**
     * 更新单条明细校验结果及解析字段
     */
    int updateItemCheckResult(SysImpTmpDetlBo bo);

    /**
     * 更新单条明细保存结果
     */
    int updateItemSaveResult(SysImpTmpDetlBo bo);

    /**
     * 逻辑删除批次下全部明细
     */
    int deleteItemsByImpIdSoft(@Param("impId") String impId);

    /**
     * 统计指定校验结果数量
     */
    int countByChkRslt(@Param("impId") String impId, @Param("chkRslt") String chkRslt);

    /**
     * 查询启用叶子 CRMW 投资池列表
     */
    List<CrmwPoolExcelImportPoolDto> queryEnabledLeafCrmwPoolList();

    /**
     * 按 ID 查询启用叶子 CRMW 投资池
     */
    InvestmentPoolBo queryEnabledLeafCrmwPoolById(@Param("poolId") Long poolId);

    /**
     * 按 Wind 代码查询证券主数据（含类型名称）
     */
    SecurityInfoDetailDto querySecurityImportInfoByCode(@Param("securityCode") String securityCode);
}
