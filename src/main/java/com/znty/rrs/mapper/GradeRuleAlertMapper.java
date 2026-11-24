package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpGradeRuleAlertBo;
import com.znty.rrs.entity.graderulealert.GradeRuleAlertReq;
import com.znty.rrs.entity.graderulealert.GradeRuleAlertScanDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 不符合主体债入池规则提醒
 */
@Mapper
public interface GradeRuleAlertMapper {

    /**
     * 查询已在信用债 1～5 级且生效的债券在池记录（不含境外债）
     *
     * @return 在池记录
     */
    List<GradeRuleAlertScanDto> queryGradedBondInPoolList();

    /**
     * 按条件分页查询待办
     *
     * @param req 查询条件
     * @return 待办列表
     */
    List<IpGradeRuleAlertBo> queryAlertPage(GradeRuleAlertReq req);

    /**
     * 查询指定证券在当前池的最新待处理分级提醒
     *
     * 兼容未设置规则类型的历史提醒，按提醒 ID 倒序取一条
     *
     * @param securityCode 证券代码
     * @param currentPoolId 当前投资池 ID
     * @return 最新待处理提醒，未找到时为 null
     */
    IpGradeRuleAlertBo queryOpenAlert(@Param("securityCode") String securityCode,
                                      @Param("currentPoolId") Long currentPoolId);

    /**
     * 查询指定证券在当前池的最新分级提醒
     *
     * 按提醒 ID 倒序取一条，包含已处理和已失效的提醒
     *
     * @param securityCode 证券代码
     * @param currentPoolId 当前投资池 ID
     * @return 最新提醒，未找到时为 null
     */
    IpGradeRuleAlertBo queryLatestGradeAlert(@Param("securityCode") String securityCode,
                                             @Param("currentPoolId") Long currentPoolId);

    /**
     * 新增待办
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param bo 待办
     * @return 行数
     */
    int addAlert(IpGradeRuleAlertBo bo);

    /**
     * 更新待办扫描结果
     *
     * @param bo 待办
     * @return 行数
     */
    int editAlertScan(IpGradeRuleAlertBo bo);

    /**
     * 将未命中本轮扫描的待处理待办置为已失效
     *
     * @param scanTime 本轮扫描时间（yyyy-MM-dd HH:mm:ss）
     * @return 影响行数
     */
    int editStaleOpenAlertInvalid(@Param("scanTime") String scanTime);

    /**
     * 统计待处理待办数量
     *
     * @return 待处理条数
     */
    int queryOpenAlertCount();

    /**
     * 标记已处理
     *
     * @param bo 待办
     * @return 行数
     */
    int editAlertProcessed(IpGradeRuleAlertBo bo);

    /**
     * 按提醒 ID 查询未删除的提醒
     *
     * @param id 提醒 ID
     * @return 匹配的提醒，未找到时为 null
     */
    IpGradeRuleAlertBo queryAlertById(@Param("id") Long id);
}
