package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpAdjustLogBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 自动调库数据访问组件。
 *
 * <p>支持到期证券自动出池、在池主体旗下债券自动入池、主体下债券同池自动入库、
 * 外评 AA- 及以下主体自动入池、外评非 AA- 及以下主体自动出池等定时任务查询，
 * 复用 {@link SecurityPoolAdjustMapper#addAdjustLog} /
 * {@link SecurityPoolAdjustMapper#addPoolStatus} /
 * {@link SecurityPoolAdjustMapper#deletePoolStatusSoft} 落地。
 */
@Mapper
public interface AutoAdjustMapper {

    /**
     * 查询投资池关系配置中绑定了指定定时任务与调入/调出类型的池 ID。
     *
     * @param taskCode 定时任务编码（sys_scheduled_task.task_code）
     * @param ruleType 规则类型：auto_in / auto_out
     * @return 池 ID 列表（去重）
     */
    List<Long> queryBoundPoolIds(@Param("taskCode") String taskCode, @Param("ruleType") String ruleType);

    /**
     * 查询指定池中已生效（audit_status=20）且已到期的在池证券。
     *
     * <p>到期口径对齐老系统 {@code AdjustRuleByExpired}：{@code maturity_date} 早于昨天（T-2），
     * 到期当天与到期次日仍不出池。大类对齐老 ptype=4000/2000，仅债、股；排除 crmw。
     * 仅回填 securityCode/securityShortName/securityType，其余由调用方补充。
     *
     * @param poolId 目标池 ID
     * @return 到期在池证券列表（每条对应一条待自动调出记录）
     */
    List<IpAdjustLogBo> queryPoolSecurityByExpired(@Param("poolId") Long poolId);

    /**
     * 查询「主体已在主体池、旗下未到期债券尚未在目标池」的待自动入池债券。
     *
     * <p>对应老系统 {@code AutoAdjustInNewBondToLimitPoolJob}：
     * 主体在 companyPoolId（category_type=company）且 bond 大类未到期、未在 targetPoolId；
     * 排除已更新为正式代码的临时代码；排除 ABS（{@code abs_flag=1}）与 CRMW
     * （{@code security_type=crmw}）。ABS 须走禁投 ABS 独立入口，与主体禁止库同步债口径不同。
     *
     * @param companyPoolId 主体所在池 ID（如债券禁止库 15）
     * @param targetPoolId  债券自动入池目标池 ID（可与主体池相同）
     * @return 待入池债券（仅回填 securityCode/securityShortName/securityType）
     */
    List<IpAdjustLogBo> queryCompanyNewBondForAutoIn(@Param("companyPoolId") Long companyPoolId,
                                                     @Param("targetPoolId") Long targetPoolId);

    /**
     * 查询「主体已在本池、旗下债未在本池」的同池自动入库候选。
     * <p>对应老系统 IP_RULE type=0「主体下债券自动入库」：主体与债<strong>必须同一池</strong>；
     * 债大类未到期（含到期当天，对齐老 {@code enddate >= sysdate}）；尊重池 {@code market_codes}（空则不限制）；
     * <strong>不</strong>排除临时代码已更新记录（与 Job 版口径区分）。</p>
     *
     * @param poolId 主体所在池且债写入池（同一 ID）
     * @return 待入池债券（securityCode/securityShortName/securityType）
     */
    List<IpAdjustLogBo> queryCompanyBondSamePoolForAutoIn(@Param("poolId") Long poolId);

    /**
     * 查询当前已在来源池、尚未在目标池的主体。
     * <p>供条款（一）禁止库、条款（三）重点观察名单复用；security_type 固定 company。</p>
     *
     * @param sourcePoolId 来源池 ID
     * @param targetPoolId 入池目标池 ID
     * @return 待入池主体
     */
    List<IpAdjustLogBo> queryCompanyInPoolNotInTarget(@Param("sourcePoolId") Long sourcePoolId,
                                                      @Param("targetPoolId") Long targetPoolId);

    /**
     * 查询近一年认可外评孰低为 AA-及以下、且尚未在目标池的主体。
     * <p>仅条款（二）；一年以前忽略；仅认机构 2/3/4/5/6/7/13/14/19/20。</p>
     *
     * @param poolId 入池目标池 ID
     * @return 待入池主体（含 outerRating）
     */
    List<IpAdjustLogBo> queryCompanyByLowOuterRatingNotInPool(@Param("poolId") Long poolId);

    /**
     * 查询已在目标池、近一年认可外评孰低不属于 AA-及以下的主体。
     * <p>仅条款（二）的反面；无认可外评不返回。条款（一）（三）由调用方按在池名单排除。
     * {@code limitPoolIds} 为额外拦截池，空则不加这段。</p>
     *
     * @param poolId       出池目标池 ID
     * @param limitPoolIds 额外拦截池；空则不追加
     * @return 待出池主体（含 outerRating）
     */
    List<IpAdjustLogBo> queryCompanyByNotLowOuterRatingInPool(@Param("poolId") Long poolId,
                                                              @Param("limitPoolIds") List<Long> limitPoolIds);

    /**
     * 查询指定池中当前已生效的主体代码。
     *
     * @param poolId 池 ID
     * @return 主体代码列表
     */
    List<String> queryCompanyCodeListInPool(@Param("poolId") Long poolId);

    /**
     * 查询主体旗下当前已在指定池的债券（bond 大类），供外评出池时顺带出同池债。
     *
     * <p>对应老系统 {@code findSecurityByCompanyCode(..., 4000)} 后再按在池过滤。
     *
     * @param companyCode 主体代码
     * @param poolId      与主体相同的目标池
     * @return 在池债券（securityCode/securityShortName/securityType）
     */
    List<IpAdjustLogBo> queryCompanyBondInSamePoolForAutoOut(@Param("companyCode") String companyCode,
                                                             @Param("poolId") Long poolId);

    /**
     * 查询 CRMW 池中已生效且凭证到期日早于昨天（T-2）的在池组合。
     *
     * <p>对应老 IP_RULE {@code AdjustRuleCrmwDueOutPool}。到期看凭证 {@code crmw_scode}
     * 主数据 {@code maturity_date}，落地表 {@code ip_pool_status_crmw}。
     */
    List<IpAdjustLogBo> queryCrmwPoolByExpired(@Param("poolId") Long poolId);

    /**
     * 查询「债已在债券池、其发行主体不在主体池」的待自动出池债券。
     *
     * <p>对应老 {@code AutoAdjustInLimitPoolToNewBondJob}。老 Job 只排 CRMW；
     * 新系统排除 ABS / CRMW，避免无人审批出池绕过禁投 ABS 独立链路。
     *
     * @param bondPoolId    债券当前所在池
     * @param companyPoolId 主体应在的池（不在则出债）
     */
    List<IpAdjustLogBo> queryBondInPoolWhenCompanyNotIn(@Param("bondPoolId") Long bondPoolId,
                                                        @Param("companyPoolId") Long companyPoolId);
}
