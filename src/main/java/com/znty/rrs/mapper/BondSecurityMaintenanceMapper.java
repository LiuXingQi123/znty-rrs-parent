package com.znty.rrs.mapper;

import com.znty.rrs.entity.schedule.BondSecurityTypeChangeDto;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 债券主数据维护定时任务数据访问接口。
 */
@Mapper
public interface BondSecurityMaintenanceMapper {

    /** 查询普通池中债券类型与主数据不一致的生效记录 */
    List<BondSecurityTypeChangeDto> queryPoolSecurityTypeChangeList();

    /** 查询 CRMW 池中标的债券类型与主数据不一致的生效记录 */
    List<BondSecurityTypeChangeDto> queryCrmwPoolSecurityTypeChangeList();

    /** 条件更新普通池证券类型 */
    int editPoolSecurityType(@Param("row") BondSecurityTypeChangeDto row,
                             @Param("updateTime") Date updateTime);

    /** 条件更新 CRMW 池标的证券类型 */
    int editCrmwPoolSecurityType(@Param("row") BondSecurityTypeChangeDto row,
                                 @Param("updateTime") Date updateTime);

    /** 更新池状态当前关联调库日志的证券类型 */
    int editAdjustLogSecurityType(@Param("adjustLogId") Long adjustLogId,
                                  @Param("securityType") String securityType,
                                  @Param("updateTime") Date updateTime);
}
