package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.bo.TempSecurityCodeBo;
import com.znty.rrs.entity.schedule.WindCodeChangeDto;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Wind 代码变更扫描、主档更新及转换登记查询。 */
@Mapper
public interface WindCodeSyncMapper {
    /**
     * 按数据库时间和扫描天数查询变更，同一 SEC_ID 只保留 CHANGE_DATE 最新的记录。
     *
     * @param scanDays 页面扩展参数指定的扫描天数，正整数
     * @return 扫描范围内的最新变更记录
     */
    List<WindCodeChangeDto> queryRecentWindCodeChangeList(@Param("scanDays") int scanDays);

    /** 按唯一 SEC_ID 锁定当前主档，序列化同一证券的变更。 */
    SecurityInfoBo querySecurityBySecIdForUpdate(@Param("secId") String secId);

    /** 查询并锁定以旧码登记的有效记录。 */
    List<TempSecurityCodeBo> queryOldCodeRegistrationList(@Param("oldCode") String oldCode);

    /** 查询已转正到当前旧码的历史登记，核实 temp_converted 来源。 */
    List<TempSecurityCodeBo> queryConvertedRegistrationList(@Param("oldCode") String oldCode);

    /** 查询原子提交的事件完成标记，支持连续换码后的重复扫描。 */
    int queryCompletedWindCodeChangeCount(@Param("eventKey") String eventKey);

    /** 按实体新增新代码主档，保留新代码的正式 SEC_ID。 */
    int addSecurityWindCodeMaster(SecurityInfoBo securityInfo);

    /** 将旧代码主档保留为失效临时代码记录，避免与新代码共用 SEC_ID。 */
    int editSecurityWindCodeToTemporary(@Param("change") WindCodeChangeDto change,
                                        @Param("oldSource") String oldSource,
                                        @Param("tempSecId") String tempSecId,
                                        @Param("updateTime") Date updateTime);
}
