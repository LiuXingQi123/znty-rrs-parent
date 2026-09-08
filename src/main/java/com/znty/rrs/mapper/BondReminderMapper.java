package com.znty.rrs.mapper;

import com.znty.rrs.entity.schedule.BondReminderDto;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 债券定时提醒查询接口。
 */
@Mapper
public interface BondReminderMapper {

    /** 查询指定池在时间窗口内审批通过的债券、主体增量调库记录 */
    List<BondReminderDto> queryPledgeBlacklistIncrementList(
            @Param("poolIds") List<Long> poolIds,
            @Param("windowStart") Date windowStart,
            @Param("windowEnd") Date windowEnd);

    /** 查询债券池中发行主体不在对应主体池的生效债券 */
    List<BondReminderDto> queryIssuerNotInCompanyPoolBondList(
            @Param("bondPoolId") Long bondPoolId,
            @Param("companyPoolId") Long companyPoolId);
}
