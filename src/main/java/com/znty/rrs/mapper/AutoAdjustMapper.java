package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.IpAdjustLogBo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 自动调库数据访问组件。
 *
 * <p>支持定时任务扫描到期证券、禁止池联动等自动调库规则，
 * 复用 {@link SecurityPoolAdjustMapper#addAdjustLog} / {@link SecurityPoolAdjustMapper#deletePoolStatusSoft} 落地。
 */
@Mapper
public interface AutoAdjustMapper {

    /**
     * 查询配置了自动调出规则（rule_type=auto_out）的启用投资池 ID 列表。
     *
     * @return 池 ID 列表
     */
    List<Long> queryAutoOutPoolIds();

    /**
     * 查询指定池中已生效（audit_status=20）且已到期（maturity_date 早于今日）的在池证券。
     *
     * <p>仅回填 securityCode/securityShortName/securityType 三个字段，其余由调用方补充。
     *
     * @param poolId 目标池 ID
     * @return 到期在池证券列表（每条对应一条待自动调出记录）
     */
    List<IpAdjustLogBo> queryPoolSecurityByExpired(@Param("poolId") Long poolId);
}
