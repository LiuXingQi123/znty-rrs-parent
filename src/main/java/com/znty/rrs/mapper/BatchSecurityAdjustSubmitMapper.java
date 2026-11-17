package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.AdjustSecuritySnapshotBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 证券 Excel 直通提交批量写入 */
@Mapper
public interface BatchSecurityAdjustSubmitMapper {

    /**
     * 批量新增日志并回填日志主键
     *
     * @param rows 调库日志
     * @return 新增条数
     */
    int addAdjustLogs(@Param("rows") List<IpAdjustLogBo> rows);

    /**
     * 批量新增初始流程步骤
     *
     * @param rows 流程步骤
     * @return 新增条数
     */
    int addAdjustSteps(@Param("rows") List<IpAdjustStepBo> rows);

    /**
     * 批量新增证券申请快照
     *
     * @param rows 申请快照
     * @return 新增条数
     */
    int addSecuritySnapshots(@Param("rows") List<AdjustSecuritySnapshotBo> rows);

    /**
     * 按证券代码与池组合结束旧生效状态
     *
     * @param rows 直通调库日志
     * @return 更新条数
     */
    int deletePoolStatusesSoft(@Param("rows") List<IpAdjustLogBo> rows);

    /**
     * 批量新增调入生效状态
     *
     * @param rows 直通调入日志
     * @return 新增条数
     */
    int addPoolStatuses(@Param("rows") List<IpAdjustLogBo> rows);
}
