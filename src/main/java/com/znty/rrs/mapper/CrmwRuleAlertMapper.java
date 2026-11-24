package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpCrmwRuleAlertBo;
import com.znty.rrs.entity.crmwrulealert.CrmwRuleAlertDto;
import com.znty.rrs.entity.crmwrulealert.CrmwRuleAlertReq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * CRMW提醒页面查询及人工处理数据访问
 */
@Mapper
public interface CrmwRuleAlertMapper {

    /**
     * 分页查询 CRMW 提醒
     *
     * @param req CRMW 规则提醒查询条件
     * @return 符合条件的CRMW 规则提醒列表
     */
    List<CrmwRuleAlertDto> queryAlertPage(CrmwRuleAlertReq req);

    /**
     * 按主键查询提醒
     *
     * @param id CRMW 规则提醒 ID
     * @return CRMW 规则提醒，未找到时为 null
     */
    IpCrmwRuleAlertBo queryAlertById(Long id);

    /**
     * 将待处理提醒标记为已处理
     *
     * @param row 包含 ID 及待修改字段的CRMW 规则提醒
     * @return 受影响的行数
     */
    int editAlertProcessed(IpCrmwRuleAlertBo row);

    /**
     * 保存提醒变更后的完整审计快照
     *
     * @param id 需要记录操作的 CRMW 提醒 ID
     * @param operatorId 操作人 ID
     * @param operationType 操作类型
     * @return 受影响的行数
     */
    int addAlertEvent(@Param("id") Long id, @Param("operatorId") String operatorId,
                      @Param("operationType") String operationType);
}
