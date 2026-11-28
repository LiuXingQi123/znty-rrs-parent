package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpPoolStatusBo;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.AdjustSecuritySnapshotBo;
import com.znty.rrs.entity.bo.AdjustSecuritySnapshotCrmwBo;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.entity.bo.TempSecurityCodeBo;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeDto;
import com.znty.rrs.entity.tempsecuritycode.TempSecurityCodeReq;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 临时代码管理数据访问接口
 */
@Mapper
public interface TempSecurityCodeMapper {

    /**
     * 分页查询临时代码列表
     *
     * @param req 证券临时代码登记信息查询条件
     * @return 符合条件的证券临时代码登记信息列表
     */
    List<TempSecurityCodeDto> queryTempSecurityCodePage(TempSecurityCodeReq req);

    /**
     * 根据 ID 查询临时代码
     *
     * @param id 证券临时代码登记 ID
     * @return 证券临时代码登记，未找到时为 null
     */
    TempSecurityCodeBo queryTempSecurityCodeById(@Param("id") Long id);

    /**
     * 查询本次转正涉及的普通池，用于加载池配置和校验转换计划
     *
     * @param bo 用于匹配记录的临时证券代码、临时证券类型
     * @return 本次转正涉及的普通池 ID 列表
     */
    List<Long> queryOrdinaryConversionPoolIdList(TempSecurityCodeBo bo);

    /**
     * 查询临时证券的普通池流程中申请，CRMW 申请由对应查询单独读取
     *
     * @param bo 用于匹配记录的临时证券代码、临时证券类型
     * @return 符合条件的调库日志列表
     */
    List<IpAdjustLogBo> queryOrdinaryPendingLogList(TempSecurityCodeBo bo);

    /**
     * 查询临时代码作为 CRMW 标的的流程中申请
     *
     * @param bo 用于匹配记录的临时证券代码、临时证券类型
     * @return 符合条件的调库日志列表
     */
    List<IpAdjustLogBo> queryCrmwPendingLogList(TempSecurityCodeBo bo);

    /**
     * 查询同一凭证下正式标的代码组的当前在池记录
     *
     * @param codes 正式证券及其同券组代码列表
     * @param crmwScode CRMW 凭证代码
     * @param crmwStype CRMW 凭证的证券类型代码
     * @return 符合条件的证券或主体池状态列表
     */
    List<IpPoolStatusBo> queryFormalGroupCrmwPoolStatusList(@Param("codes") List<String> codes,
            @Param("crmwScode") String crmwScode, @Param("crmwStype") String crmwStype);

    /**
     * 查询同一凭证下正式标的代码组的流程中申请
     *
     * @param codes 正式证券及其同券组代码列表
     * @param crmwScode CRMW 凭证代码
     * @param crmwStype CRMW 凭证的证券类型代码
     * @return 符合条件的调库日志列表
     */
    List<IpAdjustLogBo> queryFormalGroupCrmwPendingLogList(@Param("codes") List<String> codes,
            @Param("crmwScode") String crmwScode, @Param("crmwStype") String crmwStype);

    /**
     * 查询本笔日志最新的 CRMW 标的快照，旧、新代码快照可同时保留
     *
     * @param logId 调库日志 ID
     * @return 指定日志最新的未删除 CRMW 快照，未找到时为 null
     */
    AdjustSecuritySnapshotCrmwBo queryCrmwConversionSnapshot(@Param("logId") Long logId);

    /**
     * 查询正式代码组当前全部普通池状态
     *
     * @param codes 正式证券及其同券组代码列表
     * @return 符合条件的证券或主体池状态列表
     */
    List<IpPoolStatusBo> queryFormalGroupPoolStatusList(@Param("codes") List<String> codes);

    /**
     * 查询正式代码组在信用债大库（含各级库）的独立流程中申请
     *
     * @param codes 正式证券及其同券组代码列表
     * @return 符合条件的调库日志列表
     */
    List<IpAdjustLogBo> queryFormalGroupPendingLogList(@Param("codes") List<String> codes);

    /**
     * 查询本次转换涉及的普通池配置
     *
     * @param ids 投资池 ID 列表
     * @return 符合条件的投资池配置列表
     */
    List<InvestmentPoolBo> queryConversionPoolList(@Param("ids") List<Long> ids);

    /**
     * 替换普通池或 CRMW 流程中主项的证券身份与原因，保留原审批信息
     *
     * @param log 完成代码转换后需回写的在途调库日志
     * @param tempCode 临时代码
     * @return 受影响的行数
     */
    int editConversionPendingLog(@Param("log") IpAdjustLogBo log, @Param("tempCode") String tempCode);

    /**
     * 查询本笔日志最新的证券快照，旧、新代码快照可同时保留
     *
     * @param logId 调库日志 ID
     * @return 指定日志最新的未删除普通证券快照，未找到时为 null
     */
    AdjustSecuritySnapshotBo queryConversionSnapshot(@Param("logId") Long logId);

    /**
     * 转正时条件删除普通池当前状态并返回实际影响行数
     *
     * @param id 普通证券或主体池状态 ID
     * @param updateTime 记录更新时间
     * @return 受影响的行数
     */
    int deleteOrdinaryPoolStatusForConversion(@Param("id") Long id, @Param("updateTime") Date updateTime);

    /**
     * 根据 ID 查询临时代码详情
     *
     * @param id 证券临时代码登记 ID
     * @return 证券临时代码登记信息，未找到时为 null
     */
    TempSecurityCodeDto queryTempSecurityCodeDetail(@Param("id") Long id);

    /**
     * 查询已由外部数据补齐正式证券代码、等待定时替换的临时代码
     *
     * @return 符合条件的证券临时代码登记列表
     */
    List<TempSecurityCodeBo> queryJobReadyTempSecurityCodeList();

    /**
     * 查询临时代码未删除记录数量
     *
     * @param tempSecurityCode 临时证券代码
     * @param excludeId 编辑时排除的记录 ID，为 null 时不排除记录
     * @return 未删除且符合条件的临时代码登记数量
     */
    int queryTempSecurityCodeCount(@Param("tempSecurityCode") String tempSecurityCode, @Param("excludeId") Long excludeId);

    /**
     * 判断证券代码是否为有效临时代码（status=temporary，按 temp_security_code 匹配）
     * 对齐老系统 sdc_sirm_tempsecurity.lscode 识别方式
     *
     * @param securityCode 证券代码
     * @return 有效临时代码登记数量
     */
    int queryTemporaryCodeCountBySecurityCode(@Param("securityCode") String securityCode);

    /**
     * 按关键字查询发行主体选项（wind_compintroduction 按 COMP_ID 查询，最多 50 条）
     *
     * @param req 发行主体选项查询条件
     * @return 符合条件的发行主体选项列表
     */
    List<TempSecurityCodeDto.CompanyOption> queryCompanyOptionList(TempSecurityCodeReq req);

    /**
     * 从公司简介查询最多 100 条符合弹窗筛选条件的发行主体选项
     *
     * @param req 主体代码前缀、全称片段和统一社会信用代码前缀
     * @return 发行主体选项列表
     */
    List<TempSecurityCodeDto.IssuerOption> queryIssuerOptionList(TempSecurityCodeReq.IssuerQueryReq req);

    /**
     * 根据主体代码查询发行主体
     *
     * @param companyCode 主体代码
     * @return 发行主体选项，未找到时为 null
     */
    TempSecurityCodeDto.CompanyOption queryCompanyByCode(@Param("companyCode") String companyCode);

    /**
     * 按主体代码查询 AIS 最新内评分
     *
     * @param companyCode 发行主体代码
     * @return 最新内评分，未评级时为 null
     */
    String queryIssuerInnerRating(@Param("companyCode") String companyCode);

    /**
     * 按关键字远程查询正式证券（rrs_securityinfo，最多 50 条）
     *
     * @param req 正式证券选项查询条件
     * @return 符合条件的正式证券选项列表
     */
    List<TempSecurityCodeDto.FormalSecurityOption> queryFormalSecurityOptionList(TempSecurityCodeReq req);

    /**
     * 根据证券代码查询正式证券选项
     *
     * @param securityCode 证券代码
     * @param allowDelisted 是否允许查询退市证券
     * @return 匹配的正式证券选项，未找到时为 null
     */
    TempSecurityCodeDto.FormalSecurityOption queryFormalSecurityByCode(@Param("securityCode") String securityCode,
            @Param("allowDelisted") boolean allowDelisted);

    /**
     * 排除当前登记，读取同一正式代码已成功关联的临时代码
     *
     * @param securityCode 证券代码
     * @param excludeId 编辑时排除的记录 ID，为 null 时不排除记录
     * @return 证券临时代码登记，未找到时为 null
     */
    TempSecurityCodeBo queryAssociatedTempSecurityCode(@Param("securityCode") String securityCode,
            @Param("excludeId") Long excludeId);

    /**
     * 查询正式证券及同券市场代码的首条调库日志，不区分审批状态、投资池或软删除标志
     *
     * @param codes 正式证券及其同券组代码列表
     * @return 调库日志，未找到时为 null
     */
    IpAdjustLogBo queryFormalSecurityAdjustHistory(@Param("codes") List<String> codes);

    /**
     * 查询证券类型选项
     *
     * @return 符合条件的证券类型选项列表
     */
    List<TempSecurityCodeDto.SecurityTypeOption> querySecurityTypeList();

    /**
     * 查询证券类型数量
     *
     * @param securityType 证券类型代码
     * @return 符合条件的有效证券类型数量
     */
    int querySecurityTypeCount(@Param("securityType") String securityType);

    /**
     * 根据正式证券代码查询证券信息数量
     *
     * @param securityCode 证券代码
     * @return 符合证券代码的主档数量
     */
    int querySecurityInfoCount(@Param("securityCode") String securityCode);

    /**
     * 新增临时证券占位主档
     *
     * @param bo 待写入主档的临时证券信息及发行主体内评分
     */
    void addSecurityInfo(TempSecurityCodeBo bo);

    /**
     * 更新证券信息
     *
     * @param bo 正式证券代码及用于更新主档的名称、类型、主体和日期
     */
    void editSecurityInfo(TempSecurityCodeBo bo);

    /**
     * 取消发行时更新临时证券主数据
     *
     * @param bo 要取消的临时证券代码、取消发行日期和更新时间
     */
    void editSecurityInfoToCancelled(TempSecurityCodeBo bo);

    /**
     * 转正式时禁用临时证券主数据
     *
     * @param bo 要停用的临时证券代码和更新时间
     */
    void editTempSecurityInfoToDisabled(TempSecurityCodeBo bo);

    /**
     * 新增临时代码
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param bo 待写入的证券临时代码登记
     */
    void addTempSecurityCode(TempSecurityCodeBo bo);

    /**
     * 更新临时代码为已更新，返回影响行数
     *
     * @param bo 包含 ID 及待修改字段的证券临时代码登记
     * @return 受影响的行数
     */
    int editTempSecurityCodeToUpdated(@Param("bo") TempSecurityCodeBo bo);

    /**
     * 取消发行临时代码
     *
     * @param bo 包含 ID 及待修改字段的证券临时代码登记
     */
    void editTempSecurityCodeToCancelled(TempSecurityCodeBo bo);

    /**
     * 删除仍处于临时状态的登记
     *
     * @param id 临时代码登记 ID
     * @return 影响的登记行数
     */
    int deleteTempSecurityCode(@Param("id") Long id);

    /**
     * 删除代码对应的临时占位主档
     *
     * @param windCode 临时代码
     */
    void deleteTemporarySecurityInfo(@Param("windCode") String windCode);

    /**
     * 查询核心业务表引用数量
     *
     * @param bo 用于匹配记录的临时证券代码、临时证券类型、临时证券市场
     * @return 普通池和 CRMW 相关调库日志、池状态中的有效引用总数
     */
    int queryCoreReferenceCount(TempSecurityCodeBo bo);

    /**
     * 查询在途调库日志证券引用 ID（audit_status in 00/11）
     *
     * @param bo 用于匹配记录的临时证券代码、临时证券类型
     * @return 以该临时代码为证券代码的在途调库日志 ID 列表
     */
    List<Long> queryPendingAdjustLogSecurityReferenceIdList(TempSecurityCodeBo bo);

    /**
     * 查询有效在池状态列表（临时证券）
     *
     * @param bo 用于匹配记录的临时证券代码、临时证券类型
     * @return 符合条件的证券或主体池状态列表
     */
    List<IpPoolStatusBo> queryActivePoolStatusList(TempSecurityCodeBo bo);

    /**
     * 查询正式证券是否已在指定池
     *
     * @param securityCode 证券代码
     * @param securityType 证券类型代码
     * @param targetPoolId 目标投资池 ID
     * @return 指定普通池中的有效在池记录数
     */
    int queryActivePoolStatusCount(@Param("securityCode") String securityCode,
                                   @Param("securityType") String securityType,
                                   @Param("targetPoolId") Long targetPoolId);

    /**
     * 按主键软删除池状态
     *
     * @param id 普通证券或主体池状态 ID
     * @param updateTime 记录更新时间
     */
    void deletePoolStatusSoftById(@Param("id") Long id, @Param("updateTime") Date updateTime);

    /**
     * 查询在途调库日志 CRMW 引用 ID 列表
     *
     * @param bo 用于匹配记录的临时证券代码、临时证券类型、临时证券市场
     * @return 以该临时代码为 CRMW 凭证代码的在途调库日志 ID 列表
     */
    List<Long> queryPendingAdjustLogCrmwReferenceIdList(TempSecurityCodeBo bo);

    /**
     * 查询当前池状态 CRMW 引用 ID 列表
     *
     * @param bo 用于匹配记录的临时证券代码、临时证券类型、临时证券市场
     * @return 以该临时代码为 CRMW 凭证代码的普通池状态 ID 列表
     */
    List<Long> queryPoolStatusCrmwReferenceIdList(TempSecurityCodeBo bo);

    /**
     * 查询 CRMW 池有效在池状态（security 为临时码）
     *
     * @param bo 用于匹配记录的临时证券代码、临时证券类型
     * @return 符合条件的证券或主体池状态列表
     */
    List<IpPoolStatusBo> queryActiveCrmwPoolStatusList(TempSecurityCodeBo bo);

    /**
     * 查询正式证券是否已在 CRMW 池
     *
     * @param securityCode 证券代码
     * @param securityType 证券类型代码
     * @param targetPoolId 目标投资池 ID
     * @return 符合条件的有效 CRMW 在池记录数
     */
    int queryActiveCrmwPoolStatusCount(@Param("securityCode") String securityCode,
                                       @Param("securityType") String securityType,
                                       @Param("targetPoolId") Long targetPoolId);

    /**
     * 按主键软删除 CRMW 池状态
     *
     * @param id CRMW 组合池状态 ID
     * @param updateTime 记录更新时间
     * @return 受影响的行数
     */
    int deleteCrmwPoolStatusSoftById(@Param("id") Long id, @Param("updateTime") Date updateTime);

    /**
     * 查询 CRMW 池状态 CRMW 引用 ID 列表
     *
     * @param bo 用于匹配记录的临时证券代码、临时证券类型、临时证券市场
     * @return 以该临时代码为 CRMW 凭证代码的 CRMW 池状态 ID 列表
     */
    List<Long> queryCrmwPoolStatusCrmwReferenceIdList(TempSecurityCodeBo bo);

    /**
     * 批量替换在途调库日志证券引用
     *
     * @param bo 要写入日志的正式证券代码、名称、类型和更新时间
     * @param idList 调库日志 ID 列表
     */
    void editAdjustLogSecurityReference(@Param("bo") TempSecurityCodeBo bo, @Param("idList") List<Long> idList);

    /**
     * 批量替换在途调库日志 CRMW 引用
     *
     * @param bo 要写入日志的正式凭证代码、市场、类型和更新时间
     * @param idList 调库日志 ID 列表
     */
    void editAdjustLogCrmwReference(@Param("bo") TempSecurityCodeBo bo, @Param("idList") List<Long> idList);

    /**
     * 批量替换当前池状态 CRMW 引用
     *
     * @param bo 要写入池状态的正式凭证代码、市场、类型和更新时间
     * @param idList 普通证券或主体池状态 ID 列表
     */
    void editPoolStatusCrmwReference(@Param("bo") TempSecurityCodeBo bo, @Param("idList") List<Long> idList);

    /**
     * 批量替换 CRMW 池状态 CRMW 引用
     *
     * @param bo 要写入 CRMW 池状态的正式凭证代码、市场、类型和更新时间
     * @param idList CRMW 组合池状态 ID 列表
     */
    void editCrmwPoolStatusCrmwReference(@Param("bo") TempSecurityCodeBo bo, @Param("idList") List<Long> idList);

    /**
     * 新增 CRMW 池状态
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param bo 待写入的证券或主体池状态
     */
    void addCrmwPoolStatus(IpPoolStatusBo bo);

    /**
     * 新增临时代码替换日志，返回实际写入行数
     *
     * @param bo 待写入的证券临时代码登记
     * @return 受影响的行数
     */
    int addTempSecurityCodeUpdateLog(TempSecurityCodeBo bo);
}
