package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.StockAdjustLogBo;
import com.znty.rrs.entity.bo.StockAdjustStepBo;
import com.znty.rrs.entity.bo.StockInfoBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.entity.stockpooladjusthistory.StockIndustryOptionDto;
import com.znty.rrs.entity.stockpooladjust.StockInfoDto;
import com.znty.rrs.entity.stockpooladjust.StockPoolAdjustReq;
import com.znty.rrs.entity.stockpooladjust.StockPoolDto;
import com.znty.rrs.entity.stockpooladjust.StockPoolStatusDto;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 股票池调整数据访问接口。
 */
@Mapper
public interface StockPoolAdjustMapper {
    /**
     * 分页查询有效股票。
     *
     * @param req 股票筛选条件
     * @return 有效股票信息列表
     */
    List<StockInfoDto> queryStockPage(StockPoolAdjustReq req);

    /**
     * 查询有效股票产品类型。
     *
     * @return 股票类型选项列表
     */
    List<SecurityTypeOptionDto> queryStockTypeList();

    /** 查询实际股票基础信息中的行业选项。 */
    List<StockIndustryOptionDto> queryIndustryList();

    /**
     * 查询股票基础信息。
     *
     * @param stockCode 股票代码
     * @return 股票基础信息，不存在时返回 null
     */
    StockInfoBo queryStockByCode(@Param("stockCode") String stockCode);

    /**
     * 按主键顺序锁定股票主档并读取最新状态，供调库及临时代码变更共用。
     * <p>清除当前 SqlSession 的查询缓存，保证锁后日志及步骤复核不复用锁前结果。</p>
     *
     * @param stockCodes 需要锁定的非空股票代码列表
     * @return 按主键升序排列的当前股票主档
     */
    List<StockInfoBo> queryStockListForUpdate(@Param("stockCodes") List<String> stockCodes);

    /**
     * 查询股票当前所在池。
     *
     * @param stockCode 股票代码
     * @return 股票池状态列表
     */
    List<StockPoolStatusDto> queryStockPoolStatusList(@Param("stockCode") String stockCode);

    /**
     * 查询股票当前所在池 ID。
     *
     * @param stockCode 股票代码
     * @return 股票所在池 ID 列表
     */
    List<Long> queryStockCurrentPoolIdList(@Param("stockCode") String stockCode);

    /**
     * 查询股票调库记录；未指定批次时仅返回未结束流程。
     *
     * @param req 调库批次号或调库记录 ID
     * @return 股票调库记录列表
     */
    List<StockAdjustLogBo> queryAdjustLogList(StockPoolAdjustReq req);

    /**
     * 查询指定批次或调库记录的股票审批步骤。
     *
     * @param adjustLogId 调库记录 ID
     * @param adjustBatchNo 调库批次号
     * @return 股票审批步骤列表
     */
    List<StockAdjustStepBo> queryAdjustStepList(@Param("adjustLogId") Long adjustLogId,
                                                @Param("adjustBatchNo") String adjustBatchNo);

    /**
     * 查询指定批次或调库记录的股票审批步骤，批次号优先。
     *
     * @param adjustLogId 调库记录 ID
     * @param adjustBatchNo 调库批次号
     * @return 股票审批步骤列表
     */
    List<StockAdjustStepBo> queryAdjustStepByBatchList(@Param("adjustLogId") Long adjustLogId,
                                                       @Param("adjustBatchNo") String adjustBatchNo);

    /**
     * 按 ID 查询股票流程步骤。
     *
     * @param id 流程步骤 ID
     * @return 股票流程步骤，不存在时返回 null
     */
    StockAdjustStepBo queryAdjustStepById(@Param("id") Long id);

    /**
     * 查询指定处理人在当前审批节点的待办步骤。
     *
     * @param adjustLogId 调库记录 ID
     * @param adjustBatchNo 调库批次号
     * @param flowNodeId 当前审批节点 ID
     * @param handlerId 处理人 ID
     * @return 待处理步骤，不存在时返回 null
     */
    StockAdjustStepBo queryPendingStepByHandler(@Param("adjustLogId") Long adjustLogId,
                                                @Param("adjustBatchNo") String adjustBatchNo,
                                                @Param("flowNodeId") Long flowNodeId,
                                                @Param("handlerId") String handlerId);

    /**
     * 乐观更新待处理步骤。
     *
     * @param id 流程步骤 ID
     * @param stepStatus 更新后的步骤状态
     * @param processAction 处理动作
     * @param processComment 处理意见
     * @return 更新记录数
     */
    int editAdjustStepProcess(@Param("id") Long id, @Param("stepStatus") String stepStatus,
                              @Param("processAction") String processAction,
                              @Param("processComment") String processComment);

    /**
     * 查询当前节点剩余待处理步骤数。
     *
     * @param adjustLogId 调库记录 ID
     * @param adjustBatchNo 调库批次号
     * @param flowNodeId 当前审批节点 ID
     * @return 剩余待处理步骤数
     */
    int queryPendingStepCountByNode(@Param("adjustLogId") Long adjustLogId,
                                    @Param("adjustBatchNo") String adjustBatchNo,
                                    @Param("flowNodeId") Long flowNodeId);

    /**
     * 将同节点其他待处理步骤标记为跳过。
     *
     * @param id 当前处理的步骤 ID
     * @param adjustLogId 调库记录 ID
     * @param adjustBatchNo 调库批次号
     * @param flowNodeId 当前审批节点 ID
     * @param stepStatus 跳过状态码
     * @return 更新记录数
     */
    int editOtherPendingStepSkipped(@Param("id") Long id, @Param("adjustLogId") Long adjustLogId,
                                    @Param("adjustBatchNo") String adjustBatchNo,
                                    @Param("flowNodeId") Long flowNodeId,
                                    @Param("stepStatus") String stepStatus);

    /**
     * 更新批次审核状态。
     *
     * @param adjustBatchNo 调库批次号
     * @param auditStatus 更新后的审核状态
     * @return 更新记录数
     */
    int editAdjustLogAuditStatus(@Param("adjustBatchNo") String adjustBatchNo,
                                 @Param("auditStatus") String auditStatus);

    /**
     * 更新驳回待修改批次的调整原因和意见。
     *
     * @param adjustBatchNo 当前待办所属批次号
     * @param adjustReason 调整原因，null 表示不修改
     * @param adjustAdvice 调整意见，null 表示不修改
     * @return 更新记录数
     */
    int editAdjustLogReasonAdvice(@Param("adjustBatchNo") String adjustBatchNo,
                                  @Param("adjustReason") String adjustReason,
                                  @Param("adjustAdvice") String adjustAdvice);

    /**
     * 查询批次调整日志。
     *
     * @param adjustBatchNo 调库批次号
     * @return 批次调库记录列表
     */
    List<StockAdjustLogBo> queryAdjustLogListForAudit(@Param("adjustBatchNo") String adjustBatchNo);

    /**
     * 新增股票池状态记录。
     *
     * @param log 审批通过的股票调库记录
     * @return 新增记录数
     */
    int addStockPoolStatus(StockAdjustLogBo log);

    /**
     * 逻辑删除股票在目标池的有效状态。
     *
     * @param stockCode 股票代码
     * @param targetPoolId 目标池 ID
     * @return 删除记录数
     */
    int deleteStockPoolStatus(@Param("stockCode") String stockCode, @Param("targetPoolId") Long targetPoolId);

    /**
     * 查询股票在指定池的入池时间。
     *
     * @param stockCode 股票代码
     * @param targetPoolId 目标池 ID
     * @return 股票入池时间，不存在时返回 null
     */
    Date queryStockPoolEntryTime(@Param("stockCode") String stockCode, @Param("targetPoolId") Long targetPoolId);

    /**
     * 查询各池当前股票数。
     *
     * @return 各池当前股票数量列表
     */
    List<StockPoolDto> queryPoolCurrentCountList();

    /**
     * 查询指定池当前股票数。
     *
     * @param poolId 投资池 ID
     * @return 指定池当前股票数量
     */
    int queryPoolCurrentCount(@Param("poolId") Long poolId);

    /**
     * 查询全量池关系。
     *
     * @return 全量池关系列表
     */
    List<PoolRelationBo> queryAllPoolRelationList();

    /**
     * 查询指定日期是否在池开放区间。
     *
     * @param poolId 投资池 ID
     * @param today 待判断日期
     * @return 指定日期是否在池开放区间内
     */
    boolean queryPoolInOpenDay(@Param("poolId") Long poolId, @Param("today") String today);

    /**
     * 查询股票是否存在进行中的目标池流程。
     *
     * @param stockCode 股票代码
     * @param targetPoolId 目标池 ID
     * @param excludedBatchNo 排除的当前审批批次号
     * @return 是否存在进行中的目标池流程
     */
    boolean queryStockHasPendingProcess(@Param("stockCode") String stockCode,
                                       @Param("targetPoolId") Long targetPoolId,
                                       @Param("excludedBatchNo") String excludedBatchNo);

    /**
     * 查询最近短时间内是否已提交相同申请。
     *
     * @param stockCode 股票代码
     * @param targetPoolId 目标池 ID
     * @param adjustMode 调整方向
     * @param adjusterId 调整人 ID
     * @return 是否存在近期重复申请
     */
    boolean queryRecentDuplicate(@Param("stockCode") String stockCode,
                                 @Param("targetPoolId") Long targetPoolId,
                                 @Param("adjustMode") String adjustMode,
                                 @Param("adjusterId") String adjusterId, @Param("recentSince") Date recentSince);

    /**
     * 新增股票调库记录。
     *
     * @param log 股票调库记录
     * @return 新增记录数
     */
    int addAdjustLog(StockAdjustLogBo log);

    /**
     * 新增股票调库步骤。
     *
     * @param step 股票调库流程步骤
     * @return 新增记录数
     */
    int addAdjustStep(StockAdjustStepBo step);
}
