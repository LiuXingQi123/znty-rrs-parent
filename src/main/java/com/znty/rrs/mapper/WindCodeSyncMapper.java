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
     * 按数据库时间和扫描天数查询变更，同一 SEC_ID 按 CHANGE_DATE、OPDATE 取最新记录
     *
     * @param scanDays 页面扩展参数指定的扫描天数，正整数
     * @return 扫描范围内的最新变更记录
     */
    List<WindCodeChangeDto> queryRecentWindCodeChangeList(@Param("scanDays") int scanDays);

    /** 按唯一 SEC_ID 查询当前主档 */
    SecurityInfoBo querySecurityBySecId(@Param("secId") String secId);

    /**
     * 按目标代码查询已存在的证券主档
     *
     * @param windCode 目标证券代码
     * @return 目标主档，不存在时为空
     */
    SecurityInfoBo querySecurityByWindCode(@Param("windCode") String windCode);

    /** 查询旧码未删除且未完成的登记 */
    List<TempSecurityCodeBo> queryOldCodeRegistrationList(@Param("oldCode") String oldCode);

    /** 查询已转正到当前旧码的历史登记，核实 temp_converted 来源。 */
    List<TempSecurityCodeBo> queryConvertedRegistrationList(@Param("oldCode") String oldCode);

    /**
     * 按完整事件标记查询本次换码的成功登记
     *
     * @param eventKey 本次事件完成标记
     * @return 符合条件的成功登记数
     */
    int queryCompletedWindCodeChangeCount(@Param("eventKey") String eventKey);

    /** 按实体新增新代码主档，保留新代码的正式 SEC_ID。 */
    int addSecurityWindCodeMaster(SecurityInfoBo securityInfo);

    /**
     * 按唯一代码删除失效的临时目标主档
     *
     * @param windCode 目标证券代码
     * @return 删除行数
     */
    int deleteWindHistoricalMaster(@Param("windCode") String windCode);

    /** 将旧代码主档保留为失效临时代码记录，避免与新代码共用 SEC_ID。 */
    int editSecurityWindCodeToTemporary(@Param("change") WindCodeChangeDto change,
                                        @Param("oldSource") String oldSource,
                                        @Param("tempSecId") String tempSecId,
                                        @Param("updateTime") Date updateTime);
}
