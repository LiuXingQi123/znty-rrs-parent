package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.FundAdjustLogBo;
import com.znty.rrs.entity.bo.TempFundCodeBo;
import com.znty.rrs.entity.bo.TempFundCodeUpdateLogBo;
import com.znty.rrs.entity.tempfundcode.TempFundCodeDto;
import com.znty.rrs.entity.tempfundcode.TempFundCodeReq;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 基金临时代码持久化，独立操作基金登记、占位主档及基金业务引用。 */
@Mapper
public interface TempFundCodeMapper {
    /**
     * 分页查询，包含已删除登记，供状态筛选。
     *
     * @param req 筛选和分页条件
     */
    List<TempFundCodeDto> queryTempFundCodePage(TempFundCodeReq req);

    /**
     * 查询基金大类产品类型字典。
     */
    List<TempFundCodeDto.TypeOption> queryFundTypeList();

    /** 查询并锁定基金产品类型，作为新增登记的跨实例事务互斥点。 */
    List<TempFundCodeDto.TypeOption> queryFundTypeListForUpdate();

    /**
     * 搜索有效正式基金，最多五十条。
     *
     * @param req 正式基金搜索关键字
     */
    List<TempFundCodeDto.FormalFundOption> queryFormalFundOptionList(TempFundCodeReq req);

    /**
     * 读取有效正式基金，不允许临时占位主档。
     *
     * @param fundCode 正式基金代码
     */
    TempFundCodeDto.FormalFundOption queryFormalFundByCode(@Param("fundCode") String fundCode);

    /**
     * 读取未删除登记及字典展示信息。
     *
     * @param id 登记主键
     */
    TempFundCodeDto queryTempFundCodeDetail(@Param("id") Long id);

    /**
     * 读取未删除登记以确定主档加锁对象。
     *
     * @param id 登记主键
     */
    TempFundCodeBo queryTempFundCodeById(@Param("id") Long id);

    /**
     * 在主档锁之后锁定登记并重新复核状态。
     *
     * @param id 登记主键
     */
    TempFundCodeBo queryTempFundCodeByIdForUpdate(@Param("id") Long id);

    /**
     * 锁定同码基金主档，已删除主档也不可重复占用代码。
     *
     * @param fundCode 待新增临时代码
     */
    List<Long> queryFundCodeReferenceIdListForUpdate(@Param("fundCode") String fundCode);

    /**
     * 查询尚未软删除的同码登记数量。
     *
     * @param fundCode 临时基金代码
     */
    int queryTempFundCodeCount(@Param("fundCode") String fundCode);

    /**
     * 识别未删除且仍为临时状态的登记，用于 O32 转人工。
     *
     * @param fundCode 当前调库基金代码
     */
    int queryTemporaryCodeCountByFundCode(@Param("fundCode") String fundCode);

    /**
     * 新增临时代码登记并回填主键。
     *
     * @param bo 录入信息及创建状态
     */
    int addTempFundCode(TempFundCodeBo bo);

    /**
     * 新增可用于基金调库的占位主档。
     *
     * @param bo 临时代码及简称、市场、产品类型
     */
    int addTempFundInfo(TempFundCodeBo bo);

    /**
     * 仅从临时状态修改登记并保存正式快照。
     *
     * @param bo 目标状态、更新时间及正式信息
     */
    int editTempFundCodeState(TempFundCodeBo bo);

    /**
     * 转正或取消时将占位主档置为终止。
     *
     * @param fundCode 原临时基金代码
     * @param now 操作时间
     */
    int editTempFundInfoToDisabled(@Param("fundCode") String fundCode, @Param("now") Date now);

    /**
     * 保存变更后的登记全字段审计快照。
     *
     * @param id 登记主键
     * @param operatorId 操作人
     * @param oprtType 审计操作类型：INSERT=新增 / UPDATE=修改 / DELETE=删除
     * @param now 操作时间
     */
    int addTempFundCodeEvent(@Param("id") Long id, @Param("operatorId") String operatorId,
                             @Param("oprtType") String oprtType, @Param("now") Date now);

    /**
     * 检查所有未删除基金日志和池状态引用，不限审核状态。
     *
     * @param fundCode 待删除临时代码
     */
    int queryCoreReferenceCount(@Param("fundCode") String fundCode);

    /**
     * 锁定临时代码仍处于在途状态的调库日志。
     *
     * @param fundCode 原临时代码
     */
    List<FundAdjustLogBo> queryPendingAdjustLogListForUpdate(@Param("fundCode") String fundCode);

    /**
     * 替换已锁定的在途基金四字段，保留审批和调库参数。
     *
     * @param bo 原临时代码及正式信息
     * @param ids 待替换在途日志 ID
     */
    int editPendingFundReference(@Param("bo") TempFundCodeBo bo, @Param("ids") List<Long> ids);

    /**
     * 读取并锁定有效在池记录及原业务信息。
     *
     * @param fundCode 原临时代码
     */
    List<TempFundCodeDto.PoolStatusReference> queryActivePoolStatusListForUpdate(@Param("fundCode") String fundCode);

    /**
     * 判断正式基金是否已经在同一池内。
     *
     * @param fundCode 正式基金代码
     * @param poolId 目标池 ID
     */
    int queryFormalPoolStatusCount(@Param("fundCode") String fundCode, @Param("poolId") Long poolId);

    /**
     * 按主键软删除原临时基金在池状态。
     *
     * @param id 原池状态 ID
     * @param now 转正时间
     */
    int deleteTempPoolStatusById(@Param("id") Long id, @Param("now") Date now);

    /**
     * 追加一条事务内成功的业务替换明细。
     *
     * @param bo 替换前后信息、业务表和记录 ID
     */
    int addTempFundCodeUpdateLog(TempFundCodeUpdateLogBo bo);
}
