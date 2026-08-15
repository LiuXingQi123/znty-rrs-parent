package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpGradeRuleAlertBo;
import com.znty.rrs.entity.graderulealert.GradeRuleAlertReq;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 不符合主体债入库规则提醒
 */
public interface GradeRuleAlertMapper {

    /**
     * 查询已在信用债 / 境外债 1～5 级且生效的债券在池记录
     *
     * @return 在池记录
     */
    List<IpGradeRuleAlertBo> queryGradedBondInPoolList();

    /**
     * 按条件分页查询待办
     *
     * @param req 查询条件
     * @return 待办列表
     */
    List<IpGradeRuleAlertBo> queryAlertPage(GradeRuleAlertReq req);

    /**
     * 查询同一证券+当前池的待处理待办
     *
     * @param securityCode   证券代码
     * @param currentPoolId  当前池
     * @return 待办
     */
    IpGradeRuleAlertBo queryOpenAlert(@Param("securityCode") String securityCode,
                                      @Param("currentPoolId") Long currentPoolId);

    /**
     * 新增待办
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
     * @param scanTime 本轮扫描时间
     * @return 行数
     */
    int editStaleOpenAlertInvalid(@Param("scanTime") Date scanTime);

    /**
     * 标记已处理
     *
     * @param bo 待办
     * @return 行数
     */
    int editAlertProcessed(IpGradeRuleAlertBo bo);

    /**
     * 按主键查询
     *
     * @param id 主键
     * @return 待办
     */
    IpGradeRuleAlertBo queryAlertById(@Param("id") Long id);
}
