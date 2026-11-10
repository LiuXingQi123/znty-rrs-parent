package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpCrmwRuleAlertBo;
import com.znty.rrs.entity.crmwrulealert.CrmwRuleAlertDto;
import com.znty.rrs.entity.crmwrulealert.CrmwRuleAlertReq;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** CRMW提醒页面查询及人工处理数据访问 */
public interface CrmwRuleAlertMapper {

    /** 分页查询 CRMW 提醒 */
    List<CrmwRuleAlertDto> queryAlertPage(CrmwRuleAlertReq req);

    /** 按主键查询提醒 */
    IpCrmwRuleAlertBo queryAlertById(Long id);

    /** 将待处理提醒标记为已处理 */
    int editAlertProcessed(IpCrmwRuleAlertBo row);

    /** 保存提醒变更后的完整审计快照 */
    int addAlertEvent(@Param("id") Long id, @Param("operatorId") String operatorId,
                      @Param("operationType") String operationType);
}
