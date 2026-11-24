package com.znty.rrs.mapper;

import com.znty.rrs.entity.forbiddenpooladjust.BatchCompanyAdjustCheckData.Row;
import com.znty.rrs.entity.securitypoolexcelimport.CompanyImportMatchDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 主体 Excel 批量校验取数
 */
@Mapper
public interface BatchCompanyAdjustCheckMapper {

    /**
     * 读取清空候选的主体主档并保留查询代码
     *
     * @param codes 来自在池记录的主体代码
     * @return 主体代码和名称
     */
    List<CompanyImportMatchDto> queryCompanyDetails(@Param("codes") List<String> codes);

    /**
     * 查询各主体的有效在池及可用入池时间
     *
     * @param codes 主体代码
     * @param entryPoolIds 需要冻结期数据的池
     * @return 有效在池记录
     */
    List<Row> queryCurrentPools(@Param("codes") List<String> codes,
                               @Param("entryPoolIds") Set<Long> entryPoolIds);

    /**
     * 查询各主体按原优先级排在首位的待处理节点
     *
     * @param codes 主体代码
     * @return 主体代码和节点名称
     */
    List<Row> queryPendingLabels(@Param("codes") List<String> codes);

    /**
     * 查询主体手工调整在途批次占用的目标池
     *
     * @param codes 主体代码
     * @return 主体代码和占用池 ID
     */
    List<Row> queryPendingPools(@Param("codes") List<String> codes);

    /**
     * 查询各目标池有效在池对象数量
     *
     * @param poolIds 本次涉及的池
     * @return 各池去重后的数量
     */
    List<Row> queryPoolCounts(@Param("poolIds") Set<Long> poolIds);

    /**
     * 查询当天处于开放期的池
     *
     * @param poolIds 启用开放日校验的池
     * @param today 校验日期
     * @return 开放的池 ID
     */
    List<Long> queryOpenPools(@Param("poolIds") Set<Long> poolIds, @Param("today") String today);

    /**
     * 查询近一年出现过符合黑名单条件外评的主体
     *
     * @param codes 主体代码
     * @param agencyCodes 认可外评机构代码
     * @return 命中低外评条件的主体代码
     */
    List<String> queryLowRatedCompanies(@Param("codes") List<String> codes,
                                       @Param("agencyCodes") List<String> agencyCodes);
}
