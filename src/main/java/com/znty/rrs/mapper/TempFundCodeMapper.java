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

/**
 * 基金临时代码数据访问接口
 */
@Mapper
public interface TempFundCodeMapper {
    /**
     * 分页查询临时基金登记，包含已删除登记以便按状态筛选
     *
     * @param req 基金临时代码登记信息查询条件
     * @return 符合条件的基金临时代码登记信息列表
     */
    List<TempFundCodeDto> queryTempFundCodePage(TempFundCodeReq req);

    /**
     * 查询基金大类产品类型字典
     *
     * @return 符合条件的基金类型选项列表
     */
    List<TempFundCodeDto.TypeOption> queryFundTypeList();

    /**
     * 锁定有效基金类型字典行，让新增登记依次检查和写入代码
     *
     * @return 符合条件的基金类型选项列表
     */
    List<TempFundCodeDto.TypeOption> queryFundTypeListForUpdate();

    /**
     * 搜索有效正式基金，最多五十条
     *
     * @param req 正式基金选项查询条件
     * @return 符合条件的正式基金选项列表
     */
    List<TempFundCodeDto.FormalFundOption> queryFormalFundOptionList(TempFundCodeReq req);

    /**
     * 读取有效正式基金，不允许临时占位主档
     *
     * @param fundCode 基金代码
     * @return 正式基金选项，未找到时为 null
     */
    TempFundCodeDto.FormalFundOption queryFormalFundByCode(@Param("fundCode") String fundCode);

    /**
     * 读取未删除登记及字典展示信息
     *
     * @param id 基金临时代码登记 ID
     * @return 基金临时代码登记信息，未找到时为 null
     */
    TempFundCodeDto queryTempFundCodeDetail(@Param("id") Long id);

    /**
     * 读取未删除登记，用于确定主档加锁对象及加锁后复核状态
     *
     * @param id 基金临时代码登记 ID
     * @return 基金临时代码登记，未找到时为 null
     */
    TempFundCodeBo queryTempFundCodeById(@Param("id") Long id);

    /**
     * 查询占用代码的基金主档，包含已删除主档
     *
     * @param fundCode 基金代码
     * @return 占用基金代码的主档 ID 列表，包含已删除主档
     */
    List<Long> queryFundCodeReferenceIdList(@Param("fundCode") String fundCode);

    /**
     * 查询尚未软删除的同码登记数量
     *
     * @param fundCode 基金代码
     * @return 未删除的同码登记数量
     */
    int queryTempFundCodeCount(@Param("fundCode") String fundCode);

    /**
     * 识别未删除且仍为临时状态的登记，用于 O32 转人工
     *
     * @param fundCode 基金代码
     * @return 未删除且仍为临时状态的同码基金登记数量
     */
    int queryTemporaryCodeCountByFundCode(@Param("fundCode") String fundCode);

    /**
     * 新增临时代码登记并回填主键
     *
     * @param bo 待写入的基金临时代码登记
     * @return 受影响的行数
     */
    int addTempFundCode(TempFundCodeBo bo);

    /**
     * 新增可用于基金调库的占位主档
     *
     * @param bo 待写入的基金临时代码登记
     * @return 受影响的行数
     */
    int addTempFundInfo(TempFundCodeBo bo);

    /**
     * 仅从临时状态修改登记并保存正式快照
     *
     * @param bo 包含 ID 及待修改字段的基金临时代码登记
     * @return 受影响的行数
     */
    int editTempFundCodeState(TempFundCodeBo bo);

    /**
     * 转正或取消时将占位主档置为终止
     *
     * @param fundCode 基金代码
     * @param now 本次操作时间
     * @return 受影响的行数
     */
    int editTempFundInfoToDisabled(@Param("fundCode") String fundCode, @Param("now") Date now);

    /**
     * 保存变更后的登记全字段审计快照
     *
     * @param id 需要记录操作的临时代码登记 ID
     * @param operatorId 操作人 ID
     * @param oprtType 操作类型
     * @param now 本次操作时间
     * @return 受影响的行数
     */
    int addTempFundCodeEvent(@Param("id") Long id, @Param("operatorId") String operatorId,
                             @Param("oprtType") String oprtType, @Param("now") Date now);

    /**
     * 检查所有未删除基金日志和池状态引用，不限审核状态
     *
     * @param fundCode 基金代码
     * @return 未删除的基金调库日志和池状态中的引用总数
     */
    int queryCoreReferenceCount(@Param("fundCode") String fundCode);

    /**
     * 查询临时代码仍处于在途状态的调库日志
     *
     * @param fundCode 基金代码
     * @return 符合条件的基金调库日志列表
     */
    List<FundAdjustLogBo> queryPendingAdjustLogList(@Param("fundCode") String fundCode);

    /**
     * 更新在途日志中的基金代码、全称、简称和产品类型
     *
     * @param bo 包含 ID 及待修改字段的基金临时代码登记
     * @param ids 基金调库日志 ID 列表
     * @return 受影响的行数
     */
    int editPendingFundReference(@Param("bo") TempFundCodeBo bo, @Param("ids") List<Long> ids);

    /**
     * 读取有效在池记录及原业务信息
     *
     * @param fundCode 基金代码
     * @return 符合条件的基金池状态引用列表
     */
    List<TempFundCodeDto.PoolStatusReference> queryActivePoolStatusList(@Param("fundCode") String fundCode);

    /**
     * 判断正式基金是否已经在同一池内
     *
     * @param fundCode 基金代码
     * @param poolId 投资池 ID
     * @return 正式基金在目标池的有效在池记录数
     */
    int queryFormalPoolStatusCount(@Param("fundCode") String fundCode, @Param("poolId") Long poolId);

    /**
     * 按主键软删除原临时基金在池状态
     *
     * @param id 基金池状态 ID
     * @param now 本次操作时间
     * @return 受影响的行数
     */
    int deleteTempPoolStatusById(@Param("id") Long id, @Param("now") Date now);

    /**
     * 保存一条成功替换的业务记录
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param bo 待写入的基金临时代码更新日志
     * @return 受影响的行数
     */
    int addTempFundCodeUpdateLog(TempFundCodeUpdateLogBo bo);
}
