package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.MySecurityPoolBo;
import com.znty.rrs.entity.stockpoolquery.StockPoolQueryDto;
import com.znty.rrs.entity.stockpoolquery.StockPoolQueryReq;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 当前股票池和个人自选数据访问。 */
@Mapper
public interface StockPoolQueryMapper {
    /** 查询符合全部条件的生效状态，分页由 PageHelper 控制。 */
    List<StockPoolQueryDto> queryStockPoolPage(StockPoolQueryReq req);
    /** 锁定股票基础主行，使同一股票的并发收藏串行核验。 */
    MySecurityPoolBo queryStockForFavorite(@Param("stockCode") String stockCode);
    /** 查询当前用户真正的股票自选代码，排除其他品种。 */
    List<String> queryFavoritedCodeList(@Param("userId") String userId);
    /** 删除当前用户的股票收藏，完整限定股票品种与市场。 */
    int deleteStockFromMyPool(@Param("userId") String userId, @Param("stockCode") String stockCode,
                             @Param("securityType") String securityType, @Param("market") String market);
}
