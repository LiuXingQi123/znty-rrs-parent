package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.IpAdjustStepBo;
import com.znty.rrs.entity.forbiddenpooladjust.ForbiddenPoolAdjustDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Excel 主体批量提交取数与落库
 */
@Mapper
public interface BatchCompanyAdjustSubmitMapper {

    /**
     * 按主体代码查询用于调库提交的主档
     *
     * @param companyCodes 主体代码
     * @return 主体代码及名称
     */
    List<ForbiddenPoolAdjustDto> queryCompanyList(@Param("companyCodes") List<String> companyCodes);

    /**
     * 批量新增调库日志并回填主键
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
     * 按主体和池组合结束已生效的旧在池状态
     *
     * @param rows 直通调库日志
     * @return 更新条数
     */
    int deletePoolStatusesSoft(@Param("rows") List<IpAdjustLogBo> rows);

    /**
     * 批量新增直通调入的在池状态
     *
     * @param rows 直通调入日志
     * @return 新增条数
     */
    int addPoolStatuses(@Param("rows") List<IpAdjustLogBo> rows);
}
