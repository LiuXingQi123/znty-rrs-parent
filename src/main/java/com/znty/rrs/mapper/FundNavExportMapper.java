package com.znty.rrs.mapper;

import com.znty.rrs.entity.fundnavexport.FundNavExportReq;
import com.znty.rrs.entity.fundnavexport.FundNavFundDto;
import com.znty.rrs.entity.fundnavexport.FundNavRecordDto;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 基金净值导出数据访问层。
 */
@Mapper
public interface FundNavExportMapper {
    /**
     * 分页查询未删除的基金基础信息。
     *
     * @param req 基金净值导出查询条件
     * @return 分页基金基础信息列表
     */
    List<FundNavFundDto> queryFundNavFundPage(FundNavExportReq req);

    /**
     * 查询导出所选基金的基础信息。
     *
     * @param req 导出所选基金查询条件
     * @return 所选基金基础信息列表
     */
    List<FundNavFundDto> querySelectedFundList(FundNavExportReq req);

    /**
     * 查询所选基金在指定日期范围内的单位净值。
     *
     * @param req 基金代码和净值日期范围
     * @return 基金单位净值记录列表
     */
    List<FundNavRecordDto> queryFundNavRecords(FundNavExportReq req);
}
