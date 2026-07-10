package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.WindIssuerRatingBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Wind 发行人评级跨库查询接口（数据源：ais_inv_ods.wind_cbondissuerrating）。
 * 主体与担保人同为发行人，均按 s_info_compcode 关联查询。
 */
@Mapper
public interface WindRatingMapper {

    /**
     * 按公司代码查询最新一条发行人主体评级记录（用于评级下调判断）。
     * 跨库查询 ais_inv_ods.wind_cbondissuerrating，取评级日期最新的一条。
     *
     * @param compCode 公司代码（rrs_securityinfo.issuer_code 或前端选中的担保人代码，与 wind 表 s_info_compcode 同体系）
     * @return 最新评级记录；无记录返回 null
     */
    WindIssuerRatingBo queryLatestRating(@Param("compCode") String compCode);
}
