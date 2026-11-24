package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.SysImpTmpBo;
import com.znty.rrs.entity.bo.SysImpTmpDetlBo;
import com.znty.rrs.entity.securitypoolexcelimport.PoolMemberDto;
import com.znty.rrs.entity.securitypoolexcelimport.PoolClearSummaryDto;
import com.znty.rrs.entity.securitypoolexcelimport.CompanyImportMatchDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 证券/主体 Excel 导入数据访问接口
 *
 * 覆盖：通用导入临时主表/明细、目标池解析、池内成员查询
 */
@Mapper
public interface SecurityPoolExcelImportMapper {

    // ─────────── 导入临时主表 sys_imp_tmp ───────────

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
    SysImpTmpBo queryBatchByImpId(@Param("impId") String impId);

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

    // ─────────── 导入临时明细表 sys_imp_tmp_detl ───────────

    /**
     * 批量新增明细
     *
     * @param list 待写入的导入明细列表
     * @return 受影响的行数
     */
    int addItemList(@Param("list") List<SysImpTmpDetlBo> list);

    /**
     * 分页查询导入明细（配合 PageHelper）
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
    List<SysImpTmpDetlBo> queryBatchItemList(@Param("impId") String impId);

    /**
     * 按明细 ID 查询本批有效导入行
     *
     * @param impId 导入批次编号
     * @param itemId 导入明细 ID
     * @return 导入明细，未找到时为 null
     */
    SysImpTmpDetlBo queryItemById(@Param("impId") String impId, @Param("itemId") Long itemId);

    /**
     * 批量更新本批明细的校验结果和已复核的字段
     *
     * @param impId 导入批次号
     * @param items 本次需要回写的导入明细
     * @param updtTime 本批统一的修改时间
     * @return 更新的行数
     */
    int editItemCheckResultList(@Param("impId") String impId,
                                @Param("items") List<SysImpTmpDetlBo> items,
                                @Param("updtTime") Date updtTime);

    /**
     * 更新当前导入行的关联主体和 ABS 自选权益人
     *
     * @param bo 包含 ID 及待修改字段的导入明细
     * @return 受影响的行数
     */
    int editRatingCompany(SysImpTmpDetlBo bo);

    /**
     * 主体选择变更后清空本批全部行的旧校验结果
     *
     * @param impId 导入批次编号
     * @param updtTime 记录更新时间
     * @return 受影响的行数
     */
    int editItemCheckResultByImpId(@Param("impId") String impId, @Param("updtTime") Date updtTime);

    /**
     * 将本批指定的有效导入明细标记为提交成功
     *
     * @param impId 导入批次号
     * @param itemIds 已提交的导入明细 ID
     * @param updtTime 本批统一的修改时间
     * @return 更新的行数
     */
    int editItemSaveResultByIds(@Param("impId") String impId, @Param("itemIds") List<Long> itemIds,
                                 @Param("updtTime") Date updtTime);

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
    int queryItemCountByCheckResult(@Param("impId") String impId, @Param("chkRslt") String chkRslt);

    /**
     * 按非空条件集合批量匹配主体；lookupType：0=信用代码 / 1=主体代码 / 2=全称或简称
     *
     * @param lookupType 主体匹配方式
     * @param values 待匹配的主体编码、统一社会信用代码或名称
     * @return 符合条件的导入主体匹配结果列表
     */
    List<CompanyImportMatchDto> queryCompanyMatchList(@Param("lookupType") int lookupType,
                                                    @Param("values") List<String> values);

    // ─────────── 目标池 ───────────

    /**
     * 按父池名称 + 子池名称解析启用叶子投资池
     *
     * 证券导入根叶子池时父池名和子池名均为池名，主体导入按原池范围匹配
     *
     * @param parentPoolName 父池名称（主体导入可空）
     * @param childPoolName 子池名称
     * @param securityImport 是否证券导入
     * @return 匹配的叶子池，不存在时返回 null
     */
    InvestmentPoolBo queryEnabledLeafPoolByParentAndChildName(@Param("parentPoolName") String parentPoolName,
                                                             @Param("childPoolName") String childPoolName,
                                                             @Param("securityImport") boolean securityImport);

    /**
     * 查询目标池当前有效在池成员（audit_status=20）
     *
     * @param poolIds 目标池 ID
     * @return 在池成员列表
     */
    List<PoolMemberDto> queryPoolMemberList(@Param("poolIds") List<Long> poolIds);

    /**
     * 汇总可清空的叶子池的在池主体和债券数量
     *
     * @param poolIds 有权限的叶子池 ID
     * @return 每个池的成员数量
     */
    List<PoolClearSummaryDto> queryClearPoolSummaryList(@Param("poolIds") List<Long> poolIds);

    /**
     * 查询哪些候选主体或债券满足信评报告豁免条件
     *
     * 从当前时刻往前推 6 个月，只看已审批通过且关联有效信评附件的调入记录
     * 主体按自己的调入记录匹配，债券按同发行主体证券的调入记录匹配
     *
     * @param codes 需要研究报告的主体或债券代码
     * @return 满足报告豁免条件的对象代码
     */
    List<String> queryClearReportExemptCodes(@Param("codes") List<String> codes);
}
