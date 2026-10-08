package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.FundAdjustLogBo;
import com.znty.rrs.entity.bo.FundAdjustStepBo;
import com.znty.rrs.entity.bo.FundInfoBo;
import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.common.SecurityTypeOptionDto;
import com.znty.rrs.entity.fundpooladjust.FundInfoDto;
import com.znty.rrs.entity.fundpooladjust.FundPoolAdjustReq;
import com.znty.rrs.entity.fundpooladjust.FundPoolDto;
import com.znty.rrs.entity.fundpooladjust.FundPoolStatusDto;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 基金池调整数据访问接口。
 */
@Mapper
public interface FundPoolAdjustMapper {
    /**
     * 分页查询有效基金。
     *
     * @param req 基金筛选条件
     * @return 有效基金信息列表
     */
    List<FundInfoDto> queryFundPage(FundPoolAdjustReq req);

    /**
     * 查询有效基金产品类型。
     *
     * @return 基金类型选项列表
     */
    List<SecurityTypeOptionDto> queryFundTypeList();

    /**
     * 查询基金基础信息。
     *
     * @param fundCode 基金代码
     * @return 基金基础信息，不存在时返回 null
     */
    FundInfoBo queryFundByCode(@Param("fundCode") String fundCode);

    /**
     * 按主键顺序锁定基金主档并读取最新状态，供调库及临时代码变更共用。
     * <p>清除当前 SqlSession 的查询缓存，保证锁后日志及步骤复核不复用锁前结果。</p>
     *
     * @param fundCodes 需要锁定的非空基金代码列表
     * @return 按主键升序排列的当前基金主档
     */
    List<FundInfoBo> queryFundListForUpdate(@Param("fundCodes") List<String> fundCodes);

    /**
     * 查询基金当前所在池。
     *
     * @param fundCode 基金代码
     * @return 基金池状态列表
     */
    List<FundPoolStatusDto> queryFundPoolStatusList(@Param("fundCode") String fundCode);

    /**
     * 查询基金当前所在池 ID。
     *
     * @param fundCode 基金代码
     * @return 基金所在池 ID 列表
     */
    List<Long> queryFundCurrentPoolIdList(@Param("fundCode") String fundCode);

    /**
     * 查询基金调库记录；未指定批次时仅返回未结束流程。
     *
     * @param req 调库批次号或调库记录 ID
     * @return 基金调库记录列表
     */
    List<FundAdjustLogBo> queryAdjustLogList(FundPoolAdjustReq req);

    /**
     * 查询指定批次或调库记录的基金审批步骤。
     *
     * @param adjustLogId 调库记录 ID
     * @param adjustBatchNo 调库批次号
     * @return 基金审批步骤列表
     */
    List<FundAdjustStepBo> queryAdjustStepList(@Param("adjustLogId") Long adjustLogId,
                                                @Param("adjustBatchNo") String adjustBatchNo);

    /**
     * 查询指定批次或调库记录的基金审批步骤，批次号优先。
     *
     * @param adjustLogId 调库记录 ID
     * @param adjustBatchNo 调库批次号
     * @return 基金审批步骤列表
     */
    List<FundAdjustStepBo> queryAdjustStepByBatchList(@Param("adjustLogId") Long adjustLogId,
                                                       @Param("adjustBatchNo") String adjustBatchNo);

    /**
     * 按 ID 查询基金流程步骤。
     *
     * @param id 流程步骤 ID
     * @return 基金流程步骤，不存在时返回 null
     */
    FundAdjustStepBo queryAdjustStepById(@Param("id") Long id);

    /**
     * 查询指定处理人在当前审批节点的待办步骤。
     *
     * @param adjustLogId 调库记录 ID
     * @param adjustBatchNo 调库批次号
     * @param flowNodeId 当前审批节点 ID
     * @param handlerId 处理人 ID
     * @return 待处理步骤，不存在时返回 null
     */
    FundAdjustStepBo queryPendingStepByHandler(@Param("adjustLogId") Long adjustLogId,
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
    List<FundAdjustLogBo> queryAdjustLogListForAudit(@Param("adjustBatchNo") String adjustBatchNo);

    /**
     * 新增基金池状态记录。
     *
     * @param log 审批通过的基金调库记录
     * @return 新增记录数
     */
    int addFundPoolStatus(FundAdjustLogBo log);

    /**
     * 逻辑删除基金在目标池的有效状态。
     *
     * @param fundCode 基金代码
     * @param targetPoolId 目标池 ID
     * @return 删除记录数
     */
    int deleteFundPoolStatus(@Param("fundCode") String fundCode, @Param("targetPoolId") Long targetPoolId);

    /**
     * 查询基金在指定池的入池时间。
     *
     * @param fundCode 基金代码
     * @param targetPoolId 目标池 ID
     * @return 基金入池时间，不存在时返回 null
     */
    Date queryFundPoolEntryTime(@Param("fundCode") String fundCode, @Param("targetPoolId") Long targetPoolId);

    /**
     * 查询各池当前基金数。
     *
     * @return 各池当前基金数量列表
     */
    List<FundPoolDto> queryPoolCurrentCountList();

    /**
     * 查询指定池当前基金数。
     *
     * @param poolId 投资池 ID
     * @return 指定池当前基金数量
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
     * 查询基金是否存在进行中的目标池流程。
     *
     * @param fundCode 基金代码
     * @param targetPoolId 目标池 ID
     * @param excludedBatchNo 排除的当前审批批次号
     * @return 是否存在进行中的目标池流程
     */
    boolean queryFundHasPendingProcess(@Param("fundCode") String fundCode,
                                       @Param("targetPoolId") Long targetPoolId,
                                       @Param("excludedBatchNo") String excludedBatchNo);

    /**
     * 查询最近短时间内是否已提交相同申请。
     *
     * @param fundCode 基金代码
     * @param targetPoolId 目标池 ID
     * @param adjustMode 调整方向
     * @param adjusterId 调整人 ID
     * @return 是否存在近期重复申请
     */
    boolean queryRecentDuplicate(@Param("fundCode") String fundCode,
                                 @Param("targetPoolId") Long targetPoolId,
                                 @Param("adjustMode") String adjustMode,
                                 @Param("adjusterId") String adjusterId);

    /**
     * 新增基金调库记录。
     *
     * @param log 基金调库记录
     * @return 新增记录数
     */
    int addAdjustLog(FundAdjustLogBo log);

    /**
     * 新增基金调库步骤。
     *
     * @param step 基金调库流程步骤
     * @return 新增记录数
     */
    int addAdjustStep(FundAdjustStepBo step);
}
