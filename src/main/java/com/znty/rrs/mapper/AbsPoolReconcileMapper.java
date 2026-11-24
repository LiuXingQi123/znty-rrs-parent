package com.znty.rrs.mapper;

import com.znty.rrs.entity.schedule.AbsBondCandidateDto;
import com.znty.rrs.entity.schedule.AbsCompanyRelationDto;
import com.znty.rrs.entity.schedule.PoolSecurityRowDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * ABS 池状态对账数据访问组件
 *
 * 批量读取 ABS 债、关联主体及当前的主体池和 ABS 池记录
 * 供 ABS 主体自动入池任务一次加载，在内存中比对需要调整的池状态
 */
@Mapper
public interface AbsPoolReconcileMapper {

    /**
     * 查询未退市的 ABS 主档全集，含主档已回写的发行主体与市场
     *
     * @return 符合条件的ABS 债候选记录列表
     */
    List<AbsBondCandidateDto> queryAbsBondList();

    /**
     * 查询 ABS 四类关系主体全集，供未确认身份时按关联主体匹配池
     *
     * @return 符合条件的ABS 证券与主体的关系列表
     */
    List<AbsCompanyRelationDto> queryAbsCompanyRelationList();

    /**
     * 查询各池已生效的主体代码，口径与调库落池一致
     *
     * @return 符合条件的证券或主体与投资池的生效关系列表
     */
    List<PoolSecurityRowDto> queryEffectiveCompanyPoolRows();

    /**
     * 查询各池已生效的 ABS 代码，用于判断债券是否已在目标池
     *
     * @return 符合条件的证券或主体与投资池的生效关系列表
     */
    List<PoolSecurityRowDto> queryEffectiveAbsPoolRows();
}
