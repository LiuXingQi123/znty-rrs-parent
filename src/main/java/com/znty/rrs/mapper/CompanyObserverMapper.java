package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.CompanyBondTypeScopeBo;
import com.znty.rrs.entity.bo.IpPoolStatusBo;
import com.znty.rrs.entity.schedule.CompanyObserverDto;
import com.znty.rrs.entity.schedule.ScheduledAdjustCandidateDto;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** AIS 主体观察名单及本轮调库基础数据查询。 */
public interface CompanyObserverMapper {
    /** 查询观察名单及主体信息。 */
    List<CompanyObserverDto> queryCompanyObserverList();

    /** 查询目标池内主体及在池债券的发行主体。 */
    List<ScheduledAdjustCandidateDto> queryCurrentCompanyList(@Param("poolIds") List<Long> poolIds);

    /** 批量查询指定主体旗下未退市、未到期的债券，不按目标池重复扫描。 */
    List<ScheduledAdjustCandidateDto> queryObserverBondList(@Param("companyCodes") List<String> companyCodes,
                                                          @Param("bondTypeScope") CompanyBondTypeScopeBo bondTypeScope);

    /** 批量读取本轮主体、债券在所有普通池中的有效状态。 */
    List<IpPoolStatusBo> queryObserverPoolStatusList(@Param("securityCodes") List<String> securityCodes);
}
