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
    /** 查询所有可导出的叶子恒生池。 */
    List<HsPoolExportPoolDto> queryExportPoolList();

    /**
     * 查询指定叶子恒生池当前在库债券。
     *
     * @param poolId 投资池 ID
     * @return 当前在库债券
     */
    List<HsPoolExportRowDto> queryFullExportRowList(@Param("poolId") Long poolId);

    /**
     * 查询指定叶子恒生池在时间窗口内调入且当前仍在池的债券。
     *
     * @param poolId 投资池 ID
     * @param startTime 时间窗口下界
     * @param endTime 时间窗口上界
     * @return 增量导出债券
     */
    List<HsPoolExportRowDto> queryIncrementExportRowList(@Param("poolId") Long poolId,
                                                         @Param("startTime") Date startTime,
                                                         @Param("endTime") Date endTime);
}
