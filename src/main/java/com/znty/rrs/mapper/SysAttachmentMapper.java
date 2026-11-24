package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.SysAttachmentBo;
import com.znty.rrs.entity.bo.FundAdjustLogBo;
import com.znty.rrs.entity.sysattachment.SysAttachmentDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统附件数据访问接口
 */
@Mapper
public interface SysAttachmentMapper {

    /**
     * 新增附件关联记录
     *
     * 写入后将生成的主键回填到入参的 id 字段
     *
     * @param bo 待写入的附件记录
     * @return 受影响的行数
     */
    int addAttachment(SysAttachmentBo bo);

    /**
     * 按调库日志 ID 列表查询附件列表
     *
     * @param tableName 关联业务表名
     * @param adjustLogIds 调库日志 ID 列表
     * @return 符合条件的附件信息列表
     */
    List<SysAttachmentDto> queryAttachmentList(@Param("tableName") String tableName, @Param("adjustLogIds") List<Long> adjustLogIds);

    /**
     * 按附件 ID 查询附件
     *
     * @param id 附件 ID
     * @return 附件记录，未找到时为 null
     */
    SysAttachmentBo queryAttachmentById(@Param("id") Long id);

    /**
     * 按附件 ID 列表查询附件
     *
     * @param ids 附件 ID 列表
     * @return 符合条件的附件记录列表
     */
    List<SysAttachmentBo> queryAttachmentListByIds(@Param("ids") List<Long> ids);

    /**
     * 逻辑删除指定调库日志下的附件
     *
     * @param adjustLogId 调库日志 ID
     * @param ids 附件 ID 列表
     * @return 受影响的行数
     */
    int deleteAttachmentByIdsList(@Param("adjustLogId") Long adjustLogId, @Param("ids") List<Long> ids);

    /**
     * 核实报告附件对应未删除的当前股票研究报告
     *
     * @param tableName 关联业务表名
     * @param reportId 报告 ID
     * @param stockCode 股票代码
     * @return 指定股票的有效原始研究报告数
     */
    int queryMatchingStockReportSourceCount(@Param("tableName") String tableName, @Param("reportId") Long reportId,
                                          @Param("stockCode") String stockCode);

    /**
     * 查询股票日志附件，终审再次核对报告限制
     *
     * @param adjustLogId 调库日志 ID
     * @return 符合条件的附件记录列表
     */
    List<SysAttachmentBo> queryStockAdjustAttachmentList(@Param("adjustLogId") Long adjustLogId);

    /**
     * 核实继承文件仍有当前股票的有效原始报告来源
     *
     * @param fileName 附件文件名
     * @param stockCode 股票代码
     * @param internalOnly 是否只查询内部报告
     * @return 能关联到指定股票有效原始报告的附件记录数
     */
    int queryMatchingStockBoundReportCount(@Param("fileName") String fileName, @Param("stockCode") String stockCode,
                                         @Param("internalOnly") boolean internalOnly);

    /**
     * 按业务表及日志范围逻辑删除附件
     *
     * @param tableName 关联业务表名
     * @param adjustLogId 调库日志 ID
     * @param ids 附件 ID 列表
     * @return 受影响的行数
     */
    int deleteBusinessAttachmentByIdsList(@Param("tableName") String tableName, @Param("adjustLogId") Long adjustLogId,
                                         @Param("ids") List<Long> ids);

    /**
     * 按调库日志 ID 查询未删除的股票手工研究报告附件，按附件 ID 升序返回
     *
     * @param adjustLogId 调库日志 ID
     * @return 匹配的手工研究报告附件列表
     */
    List<SysAttachmentBo> queryHandStockReportAttachments(@Param("adjustLogId") Long adjustLogId);

    /**
     * 查询指定调库记录下手工上传的信评报告附件
     *
     * @param adjustLogId 调库日志 ID
     * @return 符合条件的附件记录列表
     */
    List<SysAttachmentBo> queryHandCreditReportAttachments(@Param("adjustLogId") Long adjustLogId);

    /**
     * 读取未删除的基金调库日志，仅用于受控附件继承校验
     *
     * @param adjustLogId 基金调库日志 ID
     * @return 基金调库日志，不存在时返回 null
     */
    FundAdjustLogBo queryFundAdjustLogById(@Param("adjustLogId") Long adjustLogId);

    /**
     * 查询基金日志的全部有效附件，保留原始分类供服务校验
     *
     * @param adjustLogId 来源基金调库日志 ID
     * @return 来源日志下的有效附件关联
     */
    List<SysAttachmentBo> queryFundAdjustAttachmentList(@Param("adjustLogId") Long adjustLogId);
}
