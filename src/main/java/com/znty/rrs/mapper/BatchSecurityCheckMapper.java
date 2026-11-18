package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.PoolRelationBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.WindIssuerRatingBo;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData.Row;
import com.znty.rrs.entity.securitypooladjust.BatchSecurityCheckData.ScheduledSecurity;
import com.znty.rrs.entity.securitypooladjust.PoolDto;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 手工和定时任务共用的调库批次校验数据访问 */
@Mapper
public interface BatchSecurityCheckMapper {
    /**
     * 查询批量校验涉及的投资池关系
     *
     * @return 生效的投资池关系
     */
    List<PoolRelationBo> queryPoolRelations();

    /**
     * 按池统计已审批通过的在池证券数
     *
     * @param poolIds 本批涉及的池 ID
     * @return 各池当前在池数量
     */
    List<PoolDto> queryPoolCounts(@Param("poolIds") List<Long> poolIds);

    /**
     * 按公司代码精确查询 ABS 自选权益人及当前内外评
     *
     * @param companyCodes 自选主体代码
     * @return 有效的主体记录
     */
    List<SelfSelectedRightsHolderDto> querySelfSelectedCompanies(@Param("companyCodes") List<String> companyCodes);

    /**
     * 查询 ABS 的主档确认、在库和在途申请使用的主体
     *
     * @param securityCodes 主券与同券实际代码
     * @return 主体占用记录
     */
    List<Row> queryAbsIdentities(@Param("securityCodes") List<String> securityCodes);

    /**
     * 批量读取任务主券和关联券主档
     *
     * @param securityCodes 任务本批需复核的证券代码
     * @return 证券主档及基本补债资格
     */
    List<ScheduledSecurity> queryScheduledSecurities(@Param("securityCodes") List<String> securityCodes);

    /**
     * 按代码读取本批证券主档
     *
     * @param securityCodes 待校验证券代码
     * @return 对应的数据库记录
     */
    List<SecurityInfoBo> querySecurities(@Param("securityCodes") List<String> securityCodes);

    /**
     * 批量查询每只主券的同 ISIN 关联代码
     *
     * @param securityCodes 待校验证券代码
     * @param marketCodes 参与同券关联的市场范围
     * @return 对应的数据库记录
     */
    List<Row> queryRelatedCodes(@Param("securityCodes") List<String> securityCodes,
            @Param("marketCodes") List<String> marketCodes);

    /**
     * 按现有候选排序批量读取关联评级主体
     *
     * @param securityCodes 待校验证券代码
     * @return 对应的数据库记录
     */
    List<RelatedRatingCompanyDto> queryRatingCompanies(@Param("securityCodes") List<String> securityCodes);

    /**
     * 读取证券和主体生效所在池及互斥调出使用的入池时间
     *
     * @param securityCodes 待校验证券代码
     * @return 对应的数据库记录
     */
    List<Row> queryActiveStates(@Param("securityCodes") List<String> securityCodes);

    /**
     * 按手工单券口径批量查询在途批次占用的目标池
     *
     * @param securityCodes 待校验证券代码
     * @return 对应的数据库记录
     */
    List<Row> queryPendingPools(@Param("securityCodes") List<String> securityCodes);

    /**
     * 保留每只证券按原顺序取到的首个待处理节点
     *
     * @param securityCodes 待校验证券代码
     * @return 对应的数据库记录
     */
    List<Row> queryPendingLabels(@Param("securityCodes") List<String> securityCodes);

    /**
     * 读取有效证券品种的大类映射
     *
     * @return 对应的数据库记录
     */
    List<Row> queryCategories();

    /**
     * 查询当日开放的指定池
     *
     * @param poolIds 涉及的投资池 ID
     * @param today 本次校验日期
     * @return 对应的数据库记录
     */
    List<Long> queryOpenPools(@Param("poolIds") List<Long> poolIds,
            @Param("today") String today);

    /**
     * 批量查询在 CRMW 组合池有有效来源的标的代码
     *
     * @param securityCodes 待校验证券代码
     * @param poolId CRMW 来源池 ID
     * @return 对应的数据库记录
     */
    List<String> queryCrmwSources(@Param("securityCodes") List<String> securityCodes,
            @Param("poolId") Long poolId);

    /**
     * 按手工口径批量查询命中近一年低外评的主体
     *
     * @param companyCodes 发行主体或担保人代码
     * @param agencyCodes 有效外评机构代码
     * @return 对应的数据库记录
     */
    List<String> queryLowRatedCompanies(@Param("companyCodes") List<String> companyCodes,
            @Param("agencyCodes") List<String> agencyCodes);

    /**
     * 为各主体读取用于评级下调判断的最新记录
     *
     * @param companyCodes 发行主体或担保人代码
     * @param agencyCodes 有效外评机构代码
     * @return 对应的数据库记录
     */
    List<WindIssuerRatingBo> queryLatestRatings(@Param("companyCodes") List<String> companyCodes,
            @Param("agencyCodes") List<String> agencyCodes);

    /**
     * 按发行人批量查询简易资格所需的非简易信用债入库历史
     *
     * @param companyCodes 发行主体或担保人代码
     * @param days 按实际入池日期回溯的天数
     * @return 对应的数据库记录
     */
    List<String> queryNonSimpleInboundIssuers(@Param("companyCodes") List<String> companyCodes,
            @Param("days") int days);

    /**
     * 按发行人批量查询简易资格所需的历史信评附件
     *
     * @param companyCodes 发行主体或担保人代码
     * @param days 按实际入池日期回溯的天数
     * @return 对应的数据库记录
     */
    List<String> queryRecentReportIssuers(@Param("companyCodes") List<String> companyCodes,
            @Param("days") int days);

    /**
     * 读取满足近六个自然月同发行人报告豁免的主体
     *
     * @param companyCodes 发行主体或担保人代码
     * @return 对应的数据库记录
     */
    List<String> queryReportExemptIssuers(@Param("companyCodes") List<String> companyCodes);
}
