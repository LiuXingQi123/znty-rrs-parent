package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpCrmwRuleAlertBo;
import com.znty.rrs.entity.schedule.CrmwRuleAlertScanDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * CRMW提醒定时任务扫描及待办维护数据访问
 */
@Mapper
public interface CrmwRuleAlertTaskMapper {

    /**
     * 仅查询生效的 CRMW 在池组合，不关联证券主档和评级
     *
     * @return 符合条件的提醒扫描所需的 CRMW 组合列表
     */
    List<CrmwRuleAlertScanDto> queryCrmwInPoolList();

    /**
     * 批量查询本轮凭证主档中的创设方信息
     *
     * @param crmwCodes CRMW 凭证代码列表
     * @return 符合条件的提醒扫描所需的 CRMW 组合列表
     */
    List<CrmwRuleAlertScanDto> queryCrmwSecurityList(@Param("crmwCodes") List<String> crmwCodes);

    /**
     * 批量查询创设方最新时间对应的内评，不回退旧评分
     *
     * @param issuerCodes 发行主体代码列表
     * @return 符合条件的提醒扫描所需的 CRMW 组合列表
     */
    List<CrmwRuleAlertScanDto> queryLatestIssuerRatingList(@Param("issuerCodes") List<String> issuerCodes);

    /**
     * 批量查询创设方的主体观察及重点观察名单状态
     *
     * @param issuerCodes 发行主体代码列表
     * @return 符合条件的提醒扫描所需的 CRMW 组合列表
     */
    List<CrmwRuleAlertScanDto> queryIssuerWatchList(@Param("issuerCodes") List<String> issuerCodes);

    /**
     * 批量查询本轮凭证各组合和池的最近提醒
     *
     * @param crmwCodes CRMW 凭证代码列表
     * @return 符合条件的CRMW 规则提醒列表
     */
    List<IpCrmwRuleAlertBo> queryLatestAlertList(@Param("crmwCodes") List<String> crmwCodes);

    /**
     * 新增 CRMW 提醒
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param row 待写入的CRMW 规则提醒
     * @return 受影响的行数
     */
    int addAlert(IpCrmwRuleAlertBo row);

    /**
     * 刷新待处理提醒及本次依据
     *
     * @param row 包含 ID 及待修改字段的CRMW 规则提醒
     * @return 受影响的行数
     */
    int editAlertScan(IpCrmwRuleAlertBo row);

    /**
     * 查询本轮未命中的旧待处理提醒
     *
     * @param scanTime 本次扫描时间
     * @return 符合条件的CRMW 规则提醒列表
     */
    List<IpCrmwRuleAlertBo> queryStaleOpenAlertList(String scanTime);

    /**
     * 将待处理提醒置为失效
     *
     * @param row 包含 ID 及待修改字段的CRMW 规则提醒
     * @return 受影响的行数
     */
    int editAlertInvalid(IpCrmwRuleAlertBo row);

    /**
     * 统计所有待处理 CRMW 提醒
     *
     * @return 未删除且待处理的提醒数量
     */
    int queryOpenAlertCount();

    /**
     * 保存提醒变更后的完整审计快照
     *
     * @param id CRMW 规则提醒操作审计记录 ID
     * @param operatorId 操作人 ID
     * @param operationType 操作类型
     * @return 受影响的行数
     */
    int addAlertEvent(@Param("id") Long id, @Param("operatorId") String operatorId,
                      @Param("operationType") String operationType);
}
