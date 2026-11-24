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
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param bo 待写入的个人自选记录
     * @return 受影响的行数
     */
    int addSecurityToMyPool(MySecurityPoolBo bo);

    /**
     * 从我的证券池移除（软删除，状态改为 del）
     *
     * @param userId 用户 ID
     * @param securityCode 证券代码
     * @return 受影响的行数
     */
    int deleteSecurityFromMyPool(@Param("userId") String userId, @Param("securityCode") String securityCode);

    /**
     * 查询用户某证券的收藏记录（状态为 use）
     *
     * @param userId 用户 ID
     * @param securityCode 证券代码
     * @return 个人自选记录，未找到时为 null
     */
    MySecurityPoolBo queryByUserAndCode(@Param("userId") String userId, @Param("securityCode") String securityCode);

    /**
     * 批量查询用户已收藏的证券代码列表
     *
     * @param userId 用户 ID
     * @return 用户在候选范围内已收藏的代码列表
     */
    List<String> queryFavoritedCodeList(@Param("userId") String userId);
}
