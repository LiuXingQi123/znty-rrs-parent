package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.enums.CategoryType;
import com.znty.rrs.common.enums.MarketCode;
import com.znty.rrs.common.enums.TempOprtSource;
import com.znty.rrs.entity.bo.SecurityCodeConversionBo;
import com.znty.rrs.entity.securitycodeconversion.SecurityCodeConversionReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.SecurityCodeConversionMapper;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 证券代码转换服务。
 * <p>在证券主数据把临时代码替换为正式代码前，统一迁移仍在运行的业务引用并保留历史审计。</p>
 * <p>本服务拥有独立 Mapper 与完整迁移流程，不调用现有临时代码管理、调库或附件 Service，
 * 也不接入现有业务调用链。</p>
 */
@Service
public class SecurityCodeConversionService {

    /** 调库运行态字段允许的最大证券代码长度 */
    private static final int RUNTIME_SECURITY_CODE_MAX_LENGTH = 32;

    /** 转正时临时出库原因 */
    private static final String REASON_TEMP_OUT = "债券临时代码调出";

    /** 原在池记录缺少原因时使用的正式代码调入原因 */
    private static final String REASON_FORMAL_IN = "债券正式代码调入";

    /** 系统操作人 ID */
    private static final String SYSTEM_ADJUSTER_ID = "0";

    /** 系统操作人名称 */
    private static final String SYSTEM_ADJUSTER_NAME = "系统";

    /** 系统调出类型 */
    private static final String ADJUST_TYPE_TEMP_OUT = "临时代码调出";

    /** 独立证券代码转换数据访问组件 */
    @Resource
    private SecurityCodeConversionMapper securityCodeConversionMapper;

    /**
     * 在证券主数据更新前，将临时代码的运行态业务引用迁移到正式代码。
     * <p>本方法强制加入调用方已有事务。调用方须在方法成功后、同一事务内更新或合并
     * {@code rrs_securityinfo}；不得把两步拆成两个独立事务。</p>
     *
     * @param req 临时及正式证券信息
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void replaceTempSecurityCodeReferences(SecurityCodeConversionReq req) {
        // 校验转换请求
        validateConversionReq(req);
        String tempSecurityCode = req.getTempSecurityCode().trim();
        String securityCode = req.getSecurityCode().trim();
        // 按代码顺序锁定临时及正式证券主数据，避免并发转换按不同顺序取锁
        List<SecurityCodeConversionBo> securityInfoList = securityCodeConversionMapper
                .querySecurityInfoListForUpdate(Arrays.asList(tempSecurityCode, securityCode));
        if (securityInfoList == null) {
            securityInfoList = Collections.emptyList();
        }
        // 从锁定结果中定位临时证券主数据
        SecurityCodeConversionBo tempSecurity = findSecurityInfo(securityInfoList, tempSecurityCode);
        if (tempSecurity == null) {
            throw new BizException("临时证券主数据不存在，tempSecurityCode=" + req.getTempSecurityCode());
        }
        // 正式证券主数据必须由调用方在本方法成功后写入，当前存在会导致主键覆盖语义不明确
        SecurityCodeConversionBo formalSecurity = findSecurityInfo(securityInfoList, securityCode);
        if (formalSecurity != null) {
            throw new BizException("正式证券主数据已存在，禁止覆盖或合并，securityCode=" + securityCode);
        }
        // 校验正式证券类型仍属于债券大类
        validateFormalSecurityType(req.getSecurityType().trim());
        // 阻止与原临时代码管理流程同时处理同一条记录
        validateNoActiveTempRegistration(tempSecurityCode);
        // 构建全部迁移 SQL 共用的参数
        SecurityCodeConversionBo replaceBo = buildReplaceBo(tempSecurity, req);
        // 转换前校验正式码不会与在途业务单形成同一逻辑对象
        validateNoPendingAdjustConflict(replaceBo);
        // 先记录主档代码映射摘要，确保零业务引用时仍有转换留痕
        addReplaceLog(replaceBo, "rrs_securityinfo", null);
        // 作废浏览器仍可能持有校验结果的未提交导入批次，防止旧代码再次提交
        invalidateAffectedImportDrafts(replaceBo);
        // 替换在途调库日志及其快照中的证券引用
        replacePendingSecurityReferences(replaceBo);
        // 替换在途调库日志及其快照中的 CRMW 凭证引用
        replacePendingCrmwReferences(replaceBo);
        // 转换普通池当前生效状态
        convertActivePoolStatusList(replaceBo, false);
        // 替换普通池当前状态中的 CRMW 凭证引用
        replacePoolCrmwReferences(replaceBo);
        // 转换 CRMW 池当前生效的标的证券状态
        convertActivePoolStatusList(replaceBo, true);
        // 替换 CRMW 池当前状态中的 CRMW 凭证引用
        replaceCrmwPoolCrmwReferences(replaceBo);
        // 迁移收藏、报告与待处理提醒等当前业务引用
        replaceCurrentBusinessReferences(replaceBo);
        // 复核所有运行态表已不再引用临时代码
        validateNoRuntimeReference(replaceBo);
    }

    /** 从已锁定的证券主数据中按代码查找记录。 */
    private SecurityCodeConversionBo findSecurityInfo(List<SecurityCodeConversionBo> securityInfoList,
                                                       String securityCode) {
        for (SecurityCodeConversionBo securityInfo : securityInfoList) {
            if (securityInfo.getWindCode() != null
                    && securityCode.equalsIgnoreCase(securityInfo.getWindCode().trim())) {
                return securityInfo;
            }
        }
        return null;
    }

    /** 校验正式证券类型存在且属于债券大类。 */
    private void validateFormalSecurityType(String securityType) {
        String categoryType = securityCodeConversionMapper.querySecurityCategoryType(securityType);
        if (!CategoryType.BOND.getCode().equals(categoryType)) {
            throw new BizException("正式证券类型不存在、已删除或不属于债券大类，securityType="
                    + securityType);
        }
    }

    /** 校验当前代码未由原临时代码管理流程负责。 */
    private void validateNoActiveTempRegistration(String tempSecurityCode) {
        List<Long> registrationIds = securityCodeConversionMapper
                .queryActiveTempSecurityRegistrationIdListForUpdate(tempSecurityCode);
        if (registrationIds != null && !registrationIds.isEmpty()) {
            throw new BizException("临时代码仍存在有效登记，请使用原临时代码管理流程处理，tempSecurityCode="
                    + tempSecurityCode + "，registrationIds=" + registrationIds);
        }
    }

    /** 校验代码转换不会与现有在途调库业务单冲突。 */
    private void validateNoPendingAdjustConflict(SecurityCodeConversionBo replaceBo) {
        int conflictCount = securityCodeConversionMapper.queryPendingAdjustLogConflictCount(replaceBo);
        if (conflictCount > 0) {
            throw new BizException("正式代码已存在相同池、方向及证券组合的在途调库记录，securityCode="
                    + replaceBo.getSecurityCode() + "，conflictCount=" + conflictCount);
        }
    }

    /** 作废包含临时代码且尚未成功提交的证券池、CRMW 池导入草稿。 */
    private void invalidateAffectedImportDrafts(SecurityCodeConversionBo replaceBo) {
        List<Long> batchIds = securityCodeConversionMapper
                .queryAffectedImportBatchIdListForUpdate(replaceBo);
        if (batchIds == null || batchIds.isEmpty()) {
            return;
        }
        // 锁定受影响批次的全部有效明细，整批作废而不是只删除命中行
        List<Long> detailIds = securityCodeConversionMapper
                .queryAffectedImportDetailIdListForUpdate(batchIds);
        if (detailIds == null) {
            detailIds = Collections.emptyList();
        }
        int detailRows = securityCodeConversionMapper.deleteAffectedImportDetailListSoft(
                batchIds, replaceBo.getUpdateTime());
        // 校验导入明细没有发生并发提交或取消
        validateUpdatedRows("sys_imp_tmp_detl", detailIds.size(), detailRows);
        int batchRows = securityCodeConversionMapper.deleteAffectedImportBatchListSoft(
                batchIds, replaceBo.getUpdateTime());
        // 校验导入批次没有发生并发提交或取消
        validateUpdatedRows("sys_imp_tmp", batchIds.size(), batchRows);
        // 记录被作废的导入明细
        addReplaceLogList(replaceBo, "sys_imp_tmp_detl", detailIds);
        // 记录被作废的导入批次
        addReplaceLogList(replaceBo, "sys_imp_tmp", batchIds);
    }

    /** 替换在途调库日志及其证券快照引用。 */
    private void replacePendingSecurityReferences(SecurityCodeConversionBo replaceBo) {
        List<Long> logIds = securityCodeConversionMapper.queryPendingAdjustLogSecurityReferenceIdList(replaceBo);
        if (logIds.isEmpty()) {
            return;
        }
        int updated = securityCodeConversionMapper.editAdjustLogSecurityReference(replaceBo, logIds);
        // 校验在途日志没有发生并发漂移
        validateUpdatedRows("ip_adjust_log.security_code", logIds.size(), updated);
        // 记录在途调库日志替换明细
        addReplaceLogList(replaceBo, "ip_adjust_log", logIds);

        List<Long> snapshotIds = securityCodeConversionMapper.queryPendingSecuritySnapshotReferenceIdList(
                replaceBo, logIds);
        if (!snapshotIds.isEmpty()) {
            int snapshotUpdated = securityCodeConversionMapper.editPendingSecuritySnapshotReference(
                    replaceBo, snapshotIds);
            // 校验普通证券快照没有发生并发漂移
            validateUpdatedRows("ip_adjust_security_snapshot.wind_code",
                    snapshotIds.size(), snapshotUpdated);
            // 记录普通证券快照替换明细
            addReplaceLogList(replaceBo, "ip_adjust_security_snapshot", snapshotIds);
        }

        List<Long> crmwSnapshotIds = securityCodeConversionMapper.queryPendingCrmwSecuritySnapshotReferenceIdList(
                replaceBo, logIds);
        if (!crmwSnapshotIds.isEmpty()) {
            int crmwSnapshotUpdated = securityCodeConversionMapper.editPendingCrmwSecuritySnapshotReference(
                    replaceBo, crmwSnapshotIds);
            // 校验 CRMW 标的证券快照没有发生并发漂移
            validateUpdatedRows("ip_adjust_security_snapshot_crmw.wind_code",
                    crmwSnapshotIds.size(), crmwSnapshotUpdated);
            // 记录 CRMW 标的证券快照替换明细
            addReplaceLogList(replaceBo, "ip_adjust_security_snapshot_crmw.security",
                    crmwSnapshotIds);
        }
    }

    /** 替换在途调库日志及其 CRMW 凭证快照引用。 */
    private void replacePendingCrmwReferences(SecurityCodeConversionBo replaceBo) {
        List<Long> logIds = securityCodeConversionMapper.queryPendingAdjustLogCrmwReferenceIdList(replaceBo);
        if (logIds.isEmpty()) {
            return;
        }
        int updated = securityCodeConversionMapper.editAdjustLogCrmwReference(replaceBo, logIds);
        // 校验在途 CRMW 日志没有发生并发漂移
        validateUpdatedRows("ip_adjust_log.crmw_scode", logIds.size(), updated);
        // 记录在途 CRMW 日志替换明细
        addReplaceLogList(replaceBo, "ip_adjust_log.crmw", logIds);

        List<Long> snapshotIds = securityCodeConversionMapper.queryPendingCrmwSnapshotReferenceIdList(
                replaceBo, logIds);
        if (!snapshotIds.isEmpty()) {
            int snapshotUpdated = securityCodeConversionMapper.editPendingCrmwSnapshotReference(
                    replaceBo, snapshotIds);
            // 校验 CRMW 凭证快照没有发生并发漂移
            validateUpdatedRows("ip_adjust_security_snapshot_crmw.crmw_scode",
                    snapshotIds.size(), snapshotUpdated);
            // 记录 CRMW 凭证快照替换明细
            addReplaceLogList(replaceBo, "ip_adjust_security_snapshot_crmw.crmw", snapshotIds);
        }
    }

    /** 转换普通池或 CRMW 池当前生效状态。 */
    private void convertActivePoolStatusList(SecurityCodeConversionBo replaceBo, boolean crmwPool) {
        List<SecurityCodeConversionBo> rows = crmwPool
                ? securityCodeConversionMapper.queryActiveCrmwPoolStatusList(replaceBo)
                : securityCodeConversionMapper.queryActivePoolStatusList(replaceBo);
        for (SecurityCodeConversionBo row : rows) {
            // 单池执行临时代码出库及正式代码按需入库
            convertOnePoolStatus(row, replaceBo, crmwPool);
        }
    }

    /** 替换普通池当前状态中的 CRMW 凭证引用。 */
    private void replacePoolCrmwReferences(SecurityCodeConversionBo replaceBo) {
        List<Long> ids = securityCodeConversionMapper.queryPoolStatusCrmwReferenceIdList(replaceBo);
        if (ids.isEmpty()) {
            return;
        }
        int updated = securityCodeConversionMapper.editPoolStatusCrmwReference(replaceBo, ids);
        // 校验普通池 CRMW 引用没有发生并发漂移
        validateUpdatedRows("ip_pool_status.crmw_scode", ids.size(), updated);
        // 记录普通池 CRMW 引用替换明细
        addReplaceLogList(replaceBo, "ip_pool_status.crmw", ids);
    }

    /** 替换 CRMW 池当前状态中的 CRMW 凭证引用。 */
    private void replaceCrmwPoolCrmwReferences(SecurityCodeConversionBo replaceBo) {
        List<Long> ids = securityCodeConversionMapper.queryCrmwPoolStatusCrmwReferenceIdList(replaceBo);
        if (ids.isEmpty()) {
            return;
        }
        int updated = securityCodeConversionMapper.editCrmwPoolStatusCrmwReference(replaceBo, ids);
        // 校验 CRMW 池凭证引用没有发生并发漂移
        validateUpdatedRows("ip_pool_status_crmw.crmw_scode", ids.size(), updated);
        // 记录 CRMW 池凭证引用替换明细
        addReplaceLogList(replaceBo, "ip_pool_status_crmw.crmw", ids);
    }

    /** 迁移收藏、报告及当前待办引用。 */
    private void replaceCurrentBusinessReferences(SecurityCodeConversionBo replaceBo) {
        // 合并当前有效个人收藏
        List<Long> favoriteIds = securityCodeConversionMapper
                .queryActiveMySecurityPoolReferenceIdList(replaceBo);
        if (!favoriteIds.isEmpty()) {
            int updated = securityCodeConversionMapper.editActiveMySecurityPoolReference(replaceBo);
            // 校验收藏引用没有发生并发漂移并记录替换明细
            validateAndLogReferences(replaceBo, "my_security_pool", favoriteIds, updated);
        }
        // 迁移未删除内部报告的证券归属
        List<Long> inReportIds = securityCodeConversionMapper.queryActiveInReportReferenceIdList(replaceBo);
        if (!inReportIds.isEmpty()) {
            int updated = securityCodeConversionMapper.editActiveInReportReference(replaceBo);
            // 校验内部报告引用没有发生并发漂移并记录替换明细
            validateAndLogReferences(replaceBo, "rrs_report_in", inReportIds, updated);
        }
        // 迁移未删除外部报告的证券归属
        List<Long> outReportIds = securityCodeConversionMapper.queryActiveOutReportReferenceIdList(replaceBo);
        if (!outReportIds.isEmpty()) {
            int updated = securityCodeConversionMapper.editActiveOutReportReference(replaceBo);
            // 校验外部报告引用没有发生并发漂移并记录替换明细
            validateAndLogReferences(replaceBo, "rrs_report_out", outReportIds, updated);
        }
        // 正式码已存在同池待办时失效旧待办，否则直接迁移待办代码
        List<Long> alertIds = securityCodeConversionMapper.queryOpenGradeRuleAlertReferenceIdList(replaceBo);
        if (!alertIds.isEmpty()) {
            int updated = securityCodeConversionMapper.editOpenGradeRuleAlertReference(replaceBo);
            // 校验评级提醒引用没有发生并发漂移并记录替换明细
            validateAndLogReferences(replaceBo, "ip_grade_rule_alert", alertIds, updated);
        }
    }

    /** 校验并记录一类直接引用替换。 */
    private void validateAndLogReferences(SecurityCodeConversionBo replaceBo, String tableName,
                                          List<Long> ids, int updatedRows) {
        // 校验直接引用没有发生并发漂移
        validateUpdatedRows(tableName, ids.size(), updatedRows);
        // 记录直接引用替换明细
        addReplaceLogList(replaceBo, tableName, ids);
    }

    /**
     * 单个池状态执行临时代码出库，正式代码未在同一逻辑池状态时再入库。
     */
    private void convertOnePoolStatus(SecurityCodeConversionBo poolStatus,
                                       SecurityCodeConversionBo replaceBo,
                                       boolean crmwPool) {
        Date now = replaceBo.getUpdateTime();
        // 为同一池的一出一入生成同一个可追溯批次号
        String conversionBatchNo = buildConversionBatchNo(crmwPool, now);
        // 构建并写入临时代码调出日志
        SecurityCodeConversionBo outLog = buildTempOutAdjustLog(
                poolStatus, replaceBo, now, conversionBatchNo);
        int outLogRows = securityCodeConversionMapper.addAdjustLog(outLog);
        // 校验临时代码调出日志写入成功
        validateUpdatedRows("ip_adjust_log.tempOut", 1, outLogRows);
        // 校验调出日志已回填主键
        validateGeneratedId("ip_adjust_log.tempOut", outLog.getId());
        // 为调出日志复制原提交快照，原快照不存在时从临时证券主数据补建
        addConversionSnapshot(poolStatus, replaceBo, outLog, crmwPool, false);
        // 记录新建的临时代码调出日志
        addReplaceLog(replaceBo, "ip_adjust_log.tempOut", outLog.getId());

        int deletedRows = crmwPool
                ? securityCodeConversionMapper.deleteCrmwPoolStatusSoftById(poolStatus.getId(), now)
                : securityCodeConversionMapper.deletePoolStatusSoftById(poolStatus.getId(), now);
        // 校验旧池状态仍为本次加锁的数据
        validateUpdatedRows(crmwPool ? "ip_pool_status_crmw" : "ip_pool_status", 1, deletedRows);
        // 记录旧池状态退出明细
        addReplaceLog(replaceBo, crmwPool ? "ip_pool_status_crmw" : "ip_pool_status",
                poolStatus.getId());

        int formalCount;
        if (crmwPool) {
            boolean replaceCrmwReference = replaceBo.getTempSecurityCode().equals(
                    poolStatus.getCrmwScode());
            formalCount = securityCodeConversionMapper.queryActiveCrmwPoolStatusCount(
                    replaceBo.getSecurityCode(),
                    replaceCrmwReference ? replaceBo.getSecurityCode() : poolStatus.getCrmwScode(),
                    replaceCrmwReference ? replaceBo.getSecurityType() : poolStatus.getCrmwStype(),
                    poolStatus.getTargetPoolId());
        } else {
            formalCount = securityCodeConversionMapper.queryActivePoolStatusCount(
                    replaceBo.getSecurityCode(), poolStatus.getTargetPoolId());
        }
        if (formalCount > 0) {
            return;
        }

        // 构建并写入正式代码调入日志
        SecurityCodeConversionBo inLog = buildFormalInAdjustLog(
                poolStatus, replaceBo, now, conversionBatchNo);
        int inLogRows = securityCodeConversionMapper.addAdjustLog(inLog);
        // 校验正式代码调入日志写入成功
        validateUpdatedRows("ip_adjust_log.formalIn", 1, inLogRows);
        // 校验调入日志已回填主键
        validateGeneratedId("ip_adjust_log.formalIn", inLog.getId());
        // 为正式代码调入日志复制原提交快照并替换正式证券身份字段
        addConversionSnapshot(poolStatus, replaceBo, inLog, crmwPool, true);
        // 承接原入池日志的有效附件关联，物理文件保持不变
        copyFormalInAttachments(poolStatus, replaceBo, inLog);
        // 记录新建的正式代码调入日志
        addReplaceLog(replaceBo, "ip_adjust_log.formalIn", inLog.getId());

        // 构建正式代码当前池状态，普通池与 CRMW 池使用同一套独立参数
        SecurityCodeConversionBo newStatus = buildFormalPoolStatus(
                poolStatus, replaceBo, inLog, now);
        if (crmwPool) {
            int statusRows = securityCodeConversionMapper.addCrmwPoolStatus(newStatus);
            // 校验 CRMW 池正式代码状态写入成功
            validateUpdatedRows("ip_pool_status_crmw.formalIn", 1, statusRows);
        } else {
            int statusRows = securityCodeConversionMapper.addPoolStatus(newStatus);
            // 校验普通池正式代码状态写入成功
            validateUpdatedRows("ip_pool_status.formalIn", 1, statusRows);
        }
        // 校验正式代码池状态已回填主键
        validateGeneratedId(crmwPool ? "ip_pool_status_crmw.formalIn"
                : "ip_pool_status.formalIn", newStatus.getId());
        // 记录新建的正式代码池状态
        addReplaceLog(replaceBo, crmwPool ? "ip_pool_status_crmw.formalIn"
                : "ip_pool_status.formalIn", newStatus.getId());
    }

    /** 为代码转换生成的调库日志写入完整证券快照。 */
    private void addConversionSnapshot(SecurityCodeConversionBo poolStatus,
                                       SecurityCodeConversionBo replaceBo,
                                       SecurityCodeConversionBo targetLog,
                                       boolean crmwPool,
                                       boolean formal) {
        int inserted;
        if (crmwPool) {
            inserted = securityCodeConversionMapper.addCrmwSecuritySnapshotFromSource(
                    poolStatus.getAdjustLogId(), targetLog.getId(), targetLog.getAdjusterId(),
                    formal, replaceBo, replaceBo.getUpdateTime());
            if (inserted == 0) {
                inserted = securityCodeConversionMapper.addCrmwSecuritySnapshotFromMaster(
                        targetLog.getId(), targetLog.getAdjusterId(), formal, replaceBo,
                        poolStatus, replaceBo.getUpdateTime());
            }
        } else {
            inserted = securityCodeConversionMapper.addSecuritySnapshotFromSource(
                    poolStatus.getAdjustLogId(), targetLog.getId(), targetLog.getAdjusterId(),
                    formal, replaceBo, replaceBo.getUpdateTime());
            if (inserted == 0) {
                inserted = securityCodeConversionMapper.addSecuritySnapshotFromMaster(
                        targetLog.getId(), targetLog.getAdjusterId(), formal, replaceBo,
                        replaceBo.getUpdateTime());
            }
        }
        // 每个生成的调库日志必须恰好有一条证券快照
        validateUpdatedRows(crmwPool ? "ip_adjust_security_snapshot_crmw"
                : "ip_adjust_security_snapshot", 1, inserted);
        // 以调库日志主键记录快照创建留痕
        addReplaceLog(replaceBo, crmwPool
                ? "ip_adjust_security_snapshot_crmw.adjustLogId"
                : "ip_adjust_security_snapshot.adjustLogId", targetLog.getId());
    }

    /** 将原入池日志的有效附件关联复制到正式代码调入日志。 */
    private void copyFormalInAttachments(SecurityCodeConversionBo poolStatus,
                                         SecurityCodeConversionBo replaceBo,
                                         SecurityCodeConversionBo inLog) {
        if (poolStatus.getAdjustLogId() == null) {
            return;
        }
        List<Long> attachmentIds = securityCodeConversionMapper
                .queryActiveAttachmentIdListForUpdate(poolStatus.getAdjustLogId());
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        int inserted = securityCodeConversionMapper.addFormalInAttachmentList(
                poolStatus.getAdjustLogId(), inLog.getId(), replaceBo.getUpdateTime());
        // 校验附件关联完整复制
        validateUpdatedRows("sys_attachment.formalIn", attachmentIds.size(), inserted);
        // 记录被复制的源附件主键
        addReplaceLogList(replaceBo, "sys_attachment.copyToFormalIn", attachmentIds);
    }

    /** 生成带业务前缀的代码转换调库批次号。 */
    private String buildConversionBatchNo(boolean crmwPool, Date now) {
        String prefix = crmwPool ? "CRMW" : "BOND";
        String timeText = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(now);
        String randomText = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return prefix + timeText + randomText;
    }

    /** 构建临时代码调出日志。 */
    private SecurityCodeConversionBo buildTempOutAdjustLog(SecurityCodeConversionBo poolStatus,
                                                 SecurityCodeConversionBo replaceBo,
                                                 Date now,
                                                 String conversionBatchNo) {
        SecurityCodeConversionBo log = new SecurityCodeConversionBo();
        log.setSecurityCode(poolStatus.getSecurityCode());
        log.setSecurityShortName(poolStatus.getSecurityShortName());
        log.setSecurityType(poolStatus.getSecurityType());
        log.setCrmwName(poolStatus.getCrmwName());
        log.setCrmwScode(poolStatus.getCrmwScode());
        log.setCrmwMktcode(poolStatus.getCrmwMktcode());
        log.setCrmwStype(poolStatus.getCrmwStype());
        log.setAdjustType(ADJUST_TYPE_TEMP_OUT);
        log.setAdjustMode(AdjustMode.OUT.getCode());
        log.setAdjustBatchNo(conversionBatchNo);
        log.setTargetPoolId(poolStatus.getTargetPoolId());
        log.setTargetPoolName(poolStatus.getTargetPoolName());
        log.setPoolType(poolStatus.getPoolType());
        log.setAuditStatus(AuditStatus.APPROVED.getCode());
        log.setAdjusterId(SYSTEM_ADJUSTER_ID);
        log.setAdjusterName(SYSTEM_ADJUSTER_NAME);
        // 组装临时代码调出原因
        String reason = appendReasonDetails(REASON_TEMP_OUT,
                "临时代码：" + poolStatus.getSecurityCode(),
                "正式代码：" + replaceBo.getSecurityCode());
        log.setAdjustReason(reason);
        if (!TempOprtSource.JOB.getCode().equals(replaceBo.getOprtSource())) {
            log.setAdjustAdvice(reason);
        }
        log.setSubmitTime(now);
        log.setAuditTime(now);
        return log;
    }

    /** 构建正式代码调入日志。 */
    private SecurityCodeConversionBo buildFormalInAdjustLog(SecurityCodeConversionBo poolStatus,
                                                  SecurityCodeConversionBo replaceBo,
                                                  Date now,
                                                  String conversionBatchNo) {
        SecurityCodeConversionBo log = new SecurityCodeConversionBo();
        log.setSecurityCode(replaceBo.getSecurityCode());
        log.setSecurityShortName(replaceBo.getShortName());
        log.setSecurityType(replaceBo.getSecurityType());
        boolean replaceCrmwReference = replaceBo.getTempSecurityCode().equals(
                poolStatus.getCrmwScode());
        log.setCrmwName(replaceCrmwReference ? replaceBo.getShortName() : poolStatus.getCrmwName());
        log.setCrmwScode(replaceCrmwReference ? replaceBo.getSecurityCode() : poolStatus.getCrmwScode());
        log.setCrmwMktcode(replaceCrmwReference
                ? replaceBo.getSecurityMarket() : poolStatus.getCrmwMktcode());
        log.setCrmwStype(replaceCrmwReference
                ? replaceBo.getSecurityType() : poolStatus.getCrmwStype());
        log.setAdjustType(poolStatus.getAdjustType());
        log.setAdjustMode(AdjustMode.IN.getCode());
        log.setAdjustBatchNo(conversionBatchNo);
        log.setTargetPoolId(poolStatus.getTargetPoolId());
        log.setTargetPoolName(poolStatus.getTargetPoolName());
        log.setPoolType(poolStatus.getPoolType());
        log.setAuditStatus(AuditStatus.APPROVED.getCode());
        log.setAdjusterId(poolStatus.getAdjusterId() != null
                ? poolStatus.getAdjusterId() : SYSTEM_ADJUSTER_ID);
        log.setAdjusterName(poolStatus.getAdjusterName() != null
                ? poolStatus.getAdjusterName() : SYSTEM_ADJUSTER_NAME);
        // 构建保留原原因的正式代码调入说明
        String reason = buildFormalInReason(poolStatus, replaceBo);
        log.setAdjustReason(reason);
        if (!TempOprtSource.JOB.getCode().equals(replaceBo.getOprtSource())) {
            log.setAdjustAdvice(reason);
        }
        log.setSubmitTime(now);
        log.setAuditTime(now);
        log.setEntryTime(now);
        return log;
    }

    /** 构建普通池或 CRMW 池正式代码状态。 */
    private SecurityCodeConversionBo buildFormalPoolStatus(SecurityCodeConversionBo oldStatus,
                                                 SecurityCodeConversionBo replaceBo,
                                                 SecurityCodeConversionBo inLog,
                                                 Date now) {
        SecurityCodeConversionBo status = new SecurityCodeConversionBo();
        status.setSecurityCode(replaceBo.getSecurityCode());
        status.setSecurityShortName(replaceBo.getShortName());
        status.setSecurityType(replaceBo.getSecurityType());
        status.setCrmwName(inLog.getCrmwName());
        status.setCrmwScode(inLog.getCrmwScode());
        status.setCrmwMktcode(inLog.getCrmwMktcode());
        status.setCrmwStype(inLog.getCrmwStype());
        status.setAdjustType(oldStatus.getAdjustType());
        status.setAdjustMode(AdjustMode.IN.getCode());
        status.setAdjustBatchNo(inLog.getAdjustBatchNo());
        status.setAdjustLogId(inLog.getId());
        status.setTargetPoolId(oldStatus.getTargetPoolId());
        status.setTargetPoolName(oldStatus.getTargetPoolName());
        status.setPoolType(oldStatus.getPoolType());
        status.setAuditStatus(AuditStatus.APPROVED.getCode());
        status.setAdjusterId(oldStatus.getAdjusterId() != null
                ? oldStatus.getAdjusterId() : SYSTEM_ADJUSTER_ID);
        status.setAdjusterName(oldStatus.getAdjusterName() != null
                ? oldStatus.getAdjusterName() : SYSTEM_ADJUSTER_NAME);
        // 构建保留原原因的正式代码调入说明
        String reason = buildFormalInReason(oldStatus, replaceBo);
        status.setAdjustReason(reason);
        if (!TempOprtSource.JOB.getCode().equals(replaceBo.getOprtSource())) {
            status.setAdjustAdvice(reason);
        }
        status.setSubmitTime(now);
        status.setAuditTime(now);
        status.setEntryTime(now);
        status.setIsDeleted(0);
        status.setCrteTime(now);
        status.setUpdtTime(now);
        return status;
    }

    /** 在原在池原因后补充代码转换信息。 */
    private String buildFormalInReason(SecurityCodeConversionBo poolStatus, SecurityCodeConversionBo replaceBo) {
        String baseReason = poolStatus.getAdjustReason() == null
                || poolStatus.getAdjustReason().trim().isEmpty()
                ? REASON_FORMAL_IN : poolStatus.getAdjustReason().trim();
        // 在原原因后补充代码映射
        return appendReasonDetails(baseReason,
                "代码替换：" + poolStatus.getSecurityCode() + "→" + replaceBo.getSecurityCode());
    }

    /** 在业务原因后追加全角括号明细。 */
    private String appendReasonDetails(String baseReason, String... details) {
        StringBuilder builder = new StringBuilder(baseReason).append('（');
        for (int index = 0; index < details.length; index++) {
            if (index > 0) {
                builder.append('；');
            }
            builder.append(details[index]);
        }
        return builder.append('）').toString();
    }

    /** 构建迁移及替换日志公共参数。 */
    private SecurityCodeConversionBo buildReplaceBo(SecurityCodeConversionBo tempSecurity,
                                               SecurityCodeConversionReq req) {
        SecurityCodeConversionBo bo = new SecurityCodeConversionBo();
        bo.setTempSecurityCode(req.getTempSecurityCode().trim());
        // 解析临时证券展示名称
        bo.setTempSecurityName(resolveSecurityName(tempSecurity));
        // 解析临时证券市场
        bo.setTempSecurityMarket(resolveSecurityMarket(tempSecurity));
        bo.setTempSecurityType(tempSecurity.getSecurityType());
        // 解析正式证券全称和简称，兼容调用方只提供 securityName
        bo.setFullName(resolveFormalFullName(req));
        bo.setShortName(resolveFormalShortName(req));
        bo.setSecurityName(bo.getShortName());
        bo.setSecurityCode(req.getSecurityCode().trim());
        bo.setWindCode(bo.getSecurityCode());
        bo.setSecurityMarket(req.getSecurityMarket().trim());
        bo.setSecurityType(req.getSecurityType().trim());
        bo.setWindCodeSh(trimToNull(req.getWindCodeSh()));
        bo.setWindCodeSz(trimToNull(req.getWindCodeSz()));
        bo.setWindCodeNib(trimToNull(req.getWindCodeNib()));
        bo.setWindCodeBj(trimToNull(req.getWindCodeBj()));
        bo.setWindCodeNbc(trimToNull(req.getWindCodeNbc()));
        // 正式市场对应代码未显式提供时，使用正式关联代码补齐
        applyCanonicalMarketCode(bo);
        bo.setOprtSource(req.getOprtSource().trim());
        bo.setReplaceStatus("success");
        bo.setUpdateTime(new Date());
        return bo;
    }

    /** 解析正式证券全称。 */
    private String resolveFormalFullName(SecurityCodeConversionReq req) {
        if (hasText(req.getSecurityFullName())) {
            return req.getSecurityFullName().trim();
        }
        return req.getSecurityName().trim();
    }

    /** 解析正式证券简称。 */
    private String resolveFormalShortName(SecurityCodeConversionReq req) {
        if (hasText(req.getSecurityShortName())) {
            return req.getSecurityShortName().trim();
        }
        return req.getSecurityName().trim();
    }

    /** 补齐正式证券所属市场对应的代码列。 */
    private void applyCanonicalMarketCode(SecurityCodeConversionBo bo) {
        if (MarketCode.SSE.getCode().equals(bo.getSecurityMarket()) && bo.getWindCodeSh() == null) {
            bo.setWindCodeSh(bo.getSecurityCode());
        } else if (MarketCode.SZSE.getCode().equals(bo.getSecurityMarket())
                && bo.getWindCodeSz() == null) {
            bo.setWindCodeSz(bo.getSecurityCode());
        } else if (MarketCode.CIBM.getCode().equals(bo.getSecurityMarket())
                && bo.getWindCodeNib() == null) {
            bo.setWindCodeNib(bo.getSecurityCode());
        } else if (MarketCode.BSE.getCode().equals(bo.getSecurityMarket())
                && bo.getWindCodeBj() == null) {
            bo.setWindCodeBj(bo.getSecurityCode());
        } else if (bo.getWindCodeNbc() == null) {
            bo.setWindCodeNbc(bo.getSecurityCode());
        }
    }

    /** 去除字符串首尾空白并把空字符串转换为 null。 */
    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    /** 解析证券展示名称。 */
    private String resolveSecurityName(SecurityCodeConversionBo security) {
        // 优先使用证券简称
        if (hasText(security.getShortName())) {
            return security.getShortName().trim();
        }
        // 证券简称为空时使用证券全称
        if (hasText(security.getFullName())) {
            return security.getFullName().trim();
        }
        return security.getWindCode();
    }

    /** 根据证券主数据解析市场。 */
    private String resolveSecurityMarket(SecurityCodeConversionBo security) {
        // 判断沪市证券代码
        if (hasText(security.getWindCodeSh())) {
            return MarketCode.SSE.getCode();
        }
        // 判断深市证券代码
        if (hasText(security.getWindCodeSz())) {
            return MarketCode.SZSE.getCode();
        }
        // 判断银行间市场代码
        if (hasText(security.getWindCodeNib())) {
            return MarketCode.CIBM.getCode();
        }
        // 判断北交所代码
        if (hasText(security.getWindCodeBj())) {
            return MarketCode.BSE.getCode();
        }
        // 判断其他市场代码
        if (hasText(security.getWindCodeNbc())) {
            return MarketCode.OTHER.getCode();
        }
        return MarketCode.UNKNOWN.getCode();
    }

    /** 校验转换请求。 */
    private void validateConversionReq(SecurityCodeConversionReq req) {
        if (req == null) {
            throw new BizException("证券代码转换请求不能为空");
        }
        // 校验临时代码
        validateRequired(req.getTempSecurityCode(), "临时证券代码不能为空");
        // 校验正式代码
        validateRequired(req.getSecurityCode(), "正式证券代码不能为空");
        if (!hasText(req.getSecurityName())
                && (!hasText(req.getSecurityFullName()) || !hasText(req.getSecurityShortName()))) {
            throw new BizException("正式证券名称不能为空，须提供 securityName，或同时提供全称和简称");
        }
        // 校验正式市场
        validateRequired(req.getSecurityMarket(), "正式证券市场不能为空");
        // 校验正式类型
        validateRequired(req.getSecurityType(), "正式证券类型不能为空");
        // 校验操作来源
        validateRequired(req.getOprtSource(), "操作来源不能为空");
        if (req.getTempSecurityCode().trim().equalsIgnoreCase(req.getSecurityCode().trim())) {
            throw new BizException("正式证券代码不能与临时代码相同");
        }
        if (req.getTempSecurityCode().trim().length() > RUNTIME_SECURITY_CODE_MAX_LENGTH
                || req.getSecurityCode().trim().length() > RUNTIME_SECURITY_CODE_MAX_LENGTH) {
            throw new BizException("证券代码长度不能超过 " + RUNTIME_SECURITY_CODE_MAX_LENGTH + " 位");
        }
        if (!MarketCode.isValid(req.getSecurityMarket().trim())) {
            throw new BizException("正式证券市场不合法，securityMarket=" + req.getSecurityMarket());
        }
        if (MarketCode.COMPANY.getCode().equals(req.getSecurityMarket().trim())) {
            throw new BizException("债券正式证券市场不能为主体市场");
        }
        // 校验操作来源枚举
        if (!isValidOprtSource(req.getOprtSource().trim())) {
            throw new BizException("操作来源不合法，oprtSource=" + req.getOprtSource());
        }
    }

    /** 校验必填字符串。 */
    private void validateRequired(String value, String message) {
        // 判断必填字符串是否有值
        if (!hasText(value)) {
            throw new BizException(message);
        }
    }

    /** 判断操作来源是否合法。 */
    private boolean isValidOprtSource(String value) {
        for (TempOprtSource item : TempOprtSource.values()) {
            if (item.getCode().equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** 判断字符串是否有值。 */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** 校验批量更新影响行数。 */
    private void validateUpdatedRows(String target, int expected, int actual) {
        if (expected != actual) {
            throw new BizException("证券代码转换期间数据已变化，target=" + target
                    + "，expected=" + expected + "，actual=" + actual);
        }
    }

    /** 校验新增记录已回填主键。 */
    private void validateGeneratedId(String target, Long id) {
        if (id == null) {
            throw new BizException("证券代码转换新增记录未回填主键，target=" + target);
        }
    }

    /** 复核运行态引用已全部迁移。 */
    private void validateNoRuntimeReference(SecurityCodeConversionBo replaceBo) {
        int count = securityCodeConversionMapper.queryRuntimeReferenceCount(replaceBo);
        if (count > 0) {
            throw new BizException("临时代码仍被运行态业务数据引用，tempSecurityCode="
                    + replaceBo.getTempSecurityCode() + "，referenceCount=" + count);
        }
    }

    /** 写入一条替换日志。 */
    private void addReplaceLog(SecurityCodeConversionBo replaceBo, String tableName, Long recordId) {
        replaceBo.setReplaceTableName(tableName);
        replaceBo.setReplaceRecordId(recordId);
        int inserted = securityCodeConversionMapper.addSecurityCodeConversionLog(replaceBo);
        // 校验转换日志写入成功
        validateUpdatedRows("rrs_temp_security_code_update_log", 1, inserted);
    }

    /** 批量写入替换日志。 */
    private void addReplaceLogList(SecurityCodeConversionBo replaceBo,
                                   String tableName,
                                   List<Long> recordIds) {
        for (Long recordId : recordIds) {
            // 写入单条业务引用替换日志
            addReplaceLog(replaceBo, tableName, recordId);
        }
    }
}
