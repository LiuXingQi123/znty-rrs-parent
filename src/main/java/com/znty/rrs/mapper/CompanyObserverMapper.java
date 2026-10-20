package com.znty.rrs.mapper;

import com.znty.rrs.entity.schedule.CompanyObserverDto;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** AIS 主体观察名单同步查询。 */
public interface CompanyObserverMapper {
    /** 查询观察名单及主体信息。 */
    List<CompanyObserverDto> queryCompanyObserverList();

    /** 查询目标池内主体及在池债券的发行主体。 */
    List<ScheduledAdjustCandidateDto> queryCurrentCompanyList(@Param("poolIds") List<Long> poolIds);
}
