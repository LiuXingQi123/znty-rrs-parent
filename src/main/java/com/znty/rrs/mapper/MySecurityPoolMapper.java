package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.MySecurityPoolBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 我的证券池数据访问层
 */
@Mapper
public interface MySecurityPoolMapper {

    /**
     * 添加证券到我的证券池（已存在则更新状态为 use）
     */
    int addSecurityToMyPool(MySecurityPoolBo bo);

    /**
     * 从我的证券池移除（软删除，状态改为 del）
     */
    int deleteSecurityFromMyPool(@Param("userId") String userId, @Param("securityCode") String securityCode);

    /**
     * 查询用户某证券的收藏记录（状态为 use）
     */
    MySecurityPoolBo queryByUserAndCode(@Param("userId") String userId, @Param("securityCode") String securityCode);

    /**
     * 批量查询用户已收藏的证券代码列表
     */
    List<String> queryFavoritedCodeList(@Param("userId") String userId);
    /** 按ISIN查询债券收藏，不影响独立基金和股票的代码收藏。 */
    MySecurityPoolBo queryByUserAndIsin(@Param("userId") String userId, @Param("isinCode") String isinCode);

    /** 按ISIN移除债券收藏。 */
    int deleteSecurityFromMyPoolByIsin(@Param("userId") String userId, @Param("isinCode") String isinCode);

    /** 查询用户收藏的全部债券身份。 */
    List<String> queryFavoritedIsinList(@Param("userId") String userId);
}
