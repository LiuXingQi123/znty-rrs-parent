package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpCrmwRuleAlertBo;
import com.znty.rrs.entity.schedule.CrmwRuleAlertScanDto;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** CRMW提醒定时任务扫描及待办维护数据访问 */
public interface CrmwRuleAlertTaskMapper {

    /** 仅查询生效的 CRMW 在池组合，不关联证券主档和评级 */
    List<CrmwRuleAlertScanDto> queryCrmwInPoolList();

    /** 批量查询本轮凭证主档中的创设方信息 */
    List<CrmwRuleAlertScanDto> queryCrmwSecurityList(@Param("crmwCodes") List<String> crmwCodes);

    /** 批量查询创设方最新时间对应的内评，不回退旧评分 */
    List<CrmwRuleAlertScanDto> queryLatestIssuerRatingList(@Param("issuerCodes") List<String> issuerCodes);

    /** 批量查询创设方的主体观察及重点观察名单状态 */
    List<CrmwRuleAlertScanDto> queryIssuerWatchList(@Param("issuerCodes") List<String> issuerCodes);

    /** 批量查询本轮凭证各组合和池的最近提醒 */
    List<IpCrmwRuleAlertBo> queryLatestAlertList(@Param("crmwCodes") List<String> crmwCodes);

    /** 新增 CRMW 提醒 */
    int addAlert(IpCrmwRuleAlertBo row);

    /** 刷新待处理提醒及本次依据 */
    int editAlertScan(IpCrmwRuleAlertBo row);

    /** 查询本轮未命中的旧待处理提醒 */
    List<IpCrmwRuleAlertBo> queryStaleOpenAlertList(String scanTime);

    /** 将待处理提醒置为失效 */
    int editAlertInvalid(IpCrmwRuleAlertBo row);

    /** 统计所有待处理 CRMW 提醒 */
    int queryOpenAlertCount();

    /** 保存提醒变更后的完整审计快照 */
    int addAlertEvent(@Param("id") Long id, @Param("operatorId") String operatorId,
                      @Param("operationType") String operationType);
}
