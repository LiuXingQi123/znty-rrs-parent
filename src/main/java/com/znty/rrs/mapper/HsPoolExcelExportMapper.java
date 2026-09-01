package com.znty.rrs.mapper;

import com.znty.rrs.entity.schedule.HsPoolExportPoolDto;
import com.znty.rrs.entity.schedule.HsPoolExportRowDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/** 恒生池 Excel 导出数据访问。 */
@Mapper
public interface HsPoolExcelExportMapper {
    /**
     * 查询指定范围内的叶子投资池，未指定范围时查询全部叶子池。
     *
     * @param poolIds 指定投资池 ID；为空时查询全部叶子池
     * @return 可导出的叶子投资池
     */
    List<HsPoolExportPoolDto> queryExportPoolList(@Param("poolIds") List<Long> poolIds);

    /**
     * 查询指定叶子投资池当前有效的非主体证券及 CRMW。
     *
     * @param poolId 投资池 ID
     * @param includeExpired 是否包含已到期普通证券；CRMW 始终不校验到期日
     * @return 当前有效在库证券
     */
    List<HsPoolExportRowDto> queryFullExportRowList(@Param("poolId") Long poolId,
                                                    @Param("includeExpired") boolean includeExpired);

    /**
     * 查询指定叶子投资池在时间窗口内审批通过的调入、调出证券及 CRMW。
     *
     * @param poolId 投资池 ID
     * @param startTime 时间窗口下界
     * @param endTime 时间窗口上界
     * @return 增量调库事件
     */
    List<HsPoolExportRowDto> queryIncrementExportRowList(@Param("poolId") Long poolId,
                                                         @Param("startTime") Date startTime,
                                                         @Param("endTime") Date endTime);

}
