package com.znty.rrs.mapper;

import com.znty.rrs.entity.schedule.AbsBondCandidateDto;
import com.znty.rrs.entity.schedule.AbsCompanyRelationDto;
import com.znty.rrs.entity.schedule.PoolSecurityRowDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * ABS 池状态对账数据访问组件。
 *
 * <p>四条查询全部是无关联的批量全表读，供
 * {@code CompanyAbsBondAutoInService} 一次装载后在内存中做池差集，
 * 不再把主体、ABS 关系和池状态的相关子查询压进同一条 SQL。
 */
@Mapper
public interface AbsPoolReconcileMapper {

    /** 查询未退市的 ABS 主档全集，含主档已回写的发行主体与市场。 */
    List<AbsBondCandidateDto> queryAbsBondList();

    /** 查询 ABS 四类关系主体全集，供未确认身份时按关联主体匹配池。 */
    List<AbsCompanyRelationDto> queryAbsCompanyRelationList();

    /** 查询各池已生效的主体代码，口径与调库落池一致。 */
    List<PoolSecurityRowDto> queryEffectiveCompanyPoolRows();

    /** 查询各池已生效的 ABS 代码，用于判断债券是否已在目标池。 */
    List<PoolSecurityRowDto> queryEffectiveAbsPoolRows();
}
