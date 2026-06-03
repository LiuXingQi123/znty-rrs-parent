package com.znty.sirm.mapper;

import com.znty.sirm.model.MyBondPoolBo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 我的债券池数据访问层
 */
public interface MyBondPoolMapper {

    /**
     * 添加债券到我的债券池（已存在则更新状态为 use）
     */
    int addToMyPool(MyBondPoolBo bo);

    /**
     * 从我的债券池移除（软删除，状态改为 del）
     */
    int removeFromMyPool(@Param("userId") String userId, @Param("securityCode") String securityCode);

    /**
     * 查询用户某证券的收藏记录（状态为 use）
     */
    MyBondPoolBo findByUserAndCode(@Param("userId") String userId, @Param("securityCode") String securityCode);

    /**
     * 批量查询用户已收藏的证券代码列表
     */
    List<String> queryFavoritedCodes(@Param("userId") String userId);
}
