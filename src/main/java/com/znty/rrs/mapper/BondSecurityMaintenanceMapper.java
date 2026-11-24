package com.znty.rrs.mapper;

import com.znty.rrs.entity.schedule.BondSecurityTypeChangeDto;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 债券主数据维护定时任务数据访问接口
 */
@Mapper
public interface BondSecurityMaintenanceMapper {

    /**
     * 查询普通池中债券类型与主数据不一致的生效记录
     *
     * @return 符合条件的需要同步证券类型的记录列表
     */
    List<BondSecurityTypeChangeDto> queryPoolSecurityTypeChangeList();

    /**
     * 查询 CRMW 池中标的债券类型与主数据不一致的生效记录
     *
     * @return 符合条件的需要同步证券类型的记录列表
     */
    List<BondSecurityTypeChangeDto> queryCrmwPoolSecurityTypeChangeList();

    /**
     * 条件更新普通池证券类型
     *
     * @param row 普通池状态 ID 及变更前后的证券类型
     * @param updateTime 记录更新时间
     * @return 受影响的行数
     */
    int editPoolSecurityType(@Param("row") BondSecurityTypeChangeDto row,
                             @Param("updateTime") Date updateTime);

    /**
     * 条件更新 CRMW 池标的证券类型
     *
     * @param row CRMW 池状态 ID 及变更前后的标的证券类型
     * @param updateTime 记录更新时间
     * @return 受影响的行数
     */
    int editCrmwPoolSecurityType(@Param("row") BondSecurityTypeChangeDto row,
                                 @Param("updateTime") Date updateTime);

    /**
     * 更新池状态当前关联调库日志的证券类型
     *
     * @param adjustLogId 调库日志 ID
     * @param securityType 证券类型代码
     * @param updateTime 记录更新时间
     * @return 受影响的行数
     */
    int editAdjustLogSecurityType(@Param("adjustLogId") Long adjustLogId,
                                  @Param("securityType") String securityType,
                                  @Param("updateTime") Date updateTime);

    /**
     * 同步已审批通过且未删除的债券调库流水类型，包括已经出池的证券
     *
     * @param updateTime 本轮更新时间
     * @return 实际更新的流水条数
     */
    int editApprovedAdjustLogSecurityTypes(@Param("updateTime") Date updateTime);
}
