package com.znty.rrs.service;

import com.znty.rrs.common.enums.AdjustMode;
import com.znty.rrs.common.enums.AuditStatus;
import com.znty.rrs.common.util.CreditBondSpecialInboundRule;
import com.znty.rrs.entity.bo.IpAdjustLogBo;
import com.znty.rrs.entity.bo.AdjustSecuritySnapshotBo;
import com.znty.rrs.entity.bo.IpGradeRuleAlertBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.securitypooladjust.RelatedRatingCompanyDto;
import com.znty.rrs.entity.securitypooladjust.AdjustCheckDto;
import com.znty.rrs.entity.securitypooladjust.SelfSelectedRightsHolderDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.AbsIdentityMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashMap;

/**
 * ABS 在库及在途主体身份约束。
 *
 * <p>承担身份判定、选择校验、位置占用查询、审批落档与申请快照五类职责：
 * 同一 ABS 的全部有效在库与未结束申请只能有一个主体代码，首次审核通过后永久固定。
 * 主体联动与规则提醒见 {@link AbsCompanySyncService}，评级主体候选缓存见 {@link AbsRatingCompanyCache}。
 */
@Service
public class AbsIdentityService {

    /** ABS 身份与联动查询 */
    @Resource
    private AbsIdentityMapper absIdentityMapper;

    /** 已有调库快照查询 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    /** 本次交互已查出的评级主体候选，短期复用由独立缓存承担；提供默认实例便于脱离容器单独使用 */
    @Resource
    private AbsRatingCompanyCache absRatingCompanyCache = new AbsRatingCompanyCache();

    /**
     * 保存已查询的关联候选供后续选择复用
     *
     * @param securityCode 证券代码
     * @param rows         已查询候选
     */
    public void rememberRelated(String securityCode, List<RelatedRatingCompanyDto> rows) {
        absRatingCompanyCache.rememberRelated(securityCode, rows);
    }

    /**
     * 读取本次交互已查询的关联候选
     *
     * @param securityCode 证券代码
     * @return 已查询候选，未查询或已过期时返回 null
     */
    public List<RelatedRatingCompanyDto> findRelated(String securityCode) {
        return absRatingCompanyCache.findRelated(securityCode);
    }

    /**
     * 查询 ABS 规则提醒需要展示的普通权益人候选
     *
     * @param securityCode 证券代码
     * @return 普通权益人候选
     */
    public List<RelatedRatingCompanyDto> queryRelatedRatingCompanies(String securityCode) {
        return absRatingCompanyCache.queryRelatedRatingCompanies(securityCode);
    }

    /**
     * 保存已查询的自选主体及其完整评级数据
     *
     * @param rows 当前页或精确查询结果
     */
    public void rememberSelf(List<SelfSelectedRightsHolderDto> rows) {
        absRatingCompanyCache.rememberSelf(rows);
    }

    /**
     * 读取已查询的自选主体
     *
     * @param companyCode 主体代码
     * @return 已查询候选，未查询或已过期时返回 null
     */
    public SelfSelectedRightsHolderDto findSelf(String companyCode) {
        return absRatingCompanyCache.findSelf(companyCode);
    }

    /**
     * 汇总禁止类池校验需要的主体，已确认身份后不再受其他权益人影响
     * 执行顺序：
     * 1. 读取主档已确认身份，有值时只使用该主体
     * 2. 未确认时按四类候选和本次选择汇总
     * @param security 本次证券信息
     * @return 风险主体代码集合
     */
    public List<String> queryRiskCompanyCodes(SecurityInfoBo security) {
        if (security == null) {
            return Collections.emptyList();
        }
        return queryRiskCompanyCodes(security,
                absIdentityMapper.queryConfirmedIdentityCode(security.getWindCode()));
    }

    /**
     * 使用调用方已加载的确认身份汇总主体，避免重复读取主档
     * 执行顺序：
     * 1. 已确认身份直接返回唯一主体，B/C 等其他权益人不再参与判断
     * 2. 未确认时复用已查询的四类候选并加入本次最终主体，风险判断不受评分是否存在影响
     * @param security 本次证券信息
     * @param confirmedCode 主档已确认主体，未确认时传空
     * @return 风险主体代码集合
     */
    public List<String> queryRiskCompanyCodes(SecurityInfoBo security, String confirmedCode) {
        if (security == null) {
            return Collections.emptyList();
        }
        // 已确认身份只认主档主体，其他权益人不再影响该 ABS
        if (confirmedCode != null && !confirmedCode.trim().isEmpty()) {
            return Collections.singletonList(confirmedCode.trim());
        }
        List<RelatedRatingCompanyDto> candidates = findRelated(security.getWindCode());
        if (candidates == null) {
            candidates = securityPoolAdjustMapper.queryRelatedRatingCompanyList(security.getWindCode());
            // 后台查询也在本轮交互有效期内复用
            rememberRelated(security.getWindCode(), candidates);
        }
        Set<String> codes = new LinkedHashSet<>();
        for (RelatedRatingCompanyDto candidate : candidates) {
            if (candidate.getCompanyCode() != null && !candidate.getCompanyCode().trim().isEmpty()) {
                codes.add(candidate.getCompanyCode());
            }
        }
        if (security.getIssuerCode() != null && !security.getIssuerCode().trim().isEmpty()) {
            codes.add(security.getIssuerCode());
        }
        return new ArrayList<>(codes);
    }

    /**
     * 按已查询主体集合检查观察或其他类型池
     * 执行顺序：
     * 1. 复用四类候选及本次自选代码
     * 2. 只查询最新池状态
     * @param security 本次证券
     * @param poolType 池类型
     * @return 任一主体是否命中
     */
    public boolean isRelatedInPool(SecurityInfoBo security, String poolType) {
        // 汇总风险主体代码
        List<String> codes = queryRiskCompanyCodes(security);
        return !codes.isEmpty() && absIdentityMapper.queryCompanyInPoolType(codes, poolType);
    }

    /**
     * 将同一主体的五字段写入 ABS 本次业务对象，保留现有矩阵所需评级字段
     * 执行顺序：
     * 1. 填写经后端查询的主体和评级，身份冲突在下一步校验中展示
     * @param security 当前证券
     * @param companyCode 最终选择的主体代码
     * @param companyName 最终选择的主体名称
     * @param innerRating 主体内评
     * @param outerRating 主体外评
     * @param outerAgency 主体外评机构
     */
    public void applySelection(SecurityInfoBo security, String companyCode, String companyName,
                               String innerRating, String outerRating, String outerAgency) {
        if (!CreditBondSpecialInboundRule.isAbs(security)) {
            return;
        }
        if (companyName == null || companyName.trim().isEmpty()) {
            throw new BizException("ABS[" + security.getWindCode() + "]所选主体[" + companyCode + "]缺少名称");
        }
        security.setIssuerCode(companyCode);
        security.setIssuer(companyName);
        security.setInnerIssuerRating(innerRating);
        security.setRatingBondissuer(outerRating);
        security.setRatingBondissuerAgency(outerAgency);
    }

    /**
     * 校验同一 ABS 所有有效在库及未结束申请只使用一个主体
     * 执行顺序：
     * 1. 查询所有池的有效在库和在途身份
     * 2. 检查主体是否与成功入库后固定的主档及未结束申请一致
     * @param securityCode 证券代码
     * @param companyCode 本次主体代码
     */
    public void validateIdentity(String securityCode, String companyCode) {
        // 提交及审批必须阻止主体冲突，页面校验则直接使用相同原因
        String failure = queryIdentityFailure(securityCode, companyCode);
        if (failure != null) {
            throw new BizException(failure);
        }
    }

    /** 校验确认主体时的已确认身份冲突，不把当前被动入池记录当成阻断条件 */
    public void validateConfirmedIdentity(String securityCode, String companyCode) {
        if (companyCode == null || companyCode.trim().isEmpty()) {
            throw new BizException("ABS[" + securityCode + "]缺少确认主体代码");
        }
        List<String> occupied = absIdentityMapper.queryOccupiedIdentityCodes(securityCode);
        if (occupied == null) {
            return;
        }
        for (String code : occupied) {
            if (code != null && !code.trim().isEmpty() && !companyCode.equals(code)) {
                throw new BizException("ABS[" + securityCode + "]已绑定或存在在途主体[" + code
                        + "]，不能确认主体[" + companyCode + "]");
            }
        }
    }

    /**
     * 查询主体占用冲突，供调整说明展示，正常业务冲突不抛异常
     * 执行顺序：
     * 1. 检查本次主体与已固定主档、当前有效在库及在途身份
     * 2. 存在冲突时返回原因，一致时返回空
     * @param securityCode 证券代码
     * @param companyCode 本次主体代码
     * @return 不可调整原因
     */
    public String queryIdentityFailure(String securityCode, String companyCode) {
        if (companyCode == null || companyCode.trim().isEmpty()) {
            return "ABS[" + securityCode + "]缺少本次入库主体代码";
        }
        if (absIdentityMapper.queryUnconfirmedActiveOrPending(securityCode)) {
            return "ABS[" + securityCode + "]已有未确认主体的在库或在途记录，请先处理规则提醒中的主体确认";
        }
        List<String> occupied = absIdentityMapper.queryOccupiedIdentityCodes(securityCode);
        if (occupied == null) {
            occupied = Collections.emptyList();
        }
        for (String code : occupied) {
            // 自动联动入池可以暂时没有确认主体，空值不作为身份冲突
            if (code == null || code.trim().isEmpty()) continue;
            if (!companyCode.equals(code)) {
                return "ABS[" + securityCode + "]已绑定或存在在途主体[" + code
                        + "]，不能以主体[" + companyCode + "]调整；首次人工确认主体成功入库后，即使全部出库且流程结束也不能更换主体";
            }
        }
        return null;
    }

    /**
     * 将当前主体冲突写入调整说明，同组联动、互斥及关联市场项一并阻断
     * 执行顺序：
     * 1. 普通债直接返回，ABS 按实际代码检查本次所选主体
     * 2. 汇总各组冲突原因，保留已有失败原因并标记不可调整
     * @param security 本次已回填主体的证券
     * @param items 完整校验结果
     */
    public void applyIdentityCheckFailures(SecurityInfoBo security, List<AdjustCheckDto.CheckResultItem> items) {
        if (!CreditBondSpecialInboundRule.isAbs(security) || items == null) return;
        Map<String, Set<String>> codesByGroup = new HashMap<>();
        for (AdjustCheckDto.CheckResultItem item : items) {
            String code = item.getSecurityCode() == null ? security.getWindCode() : item.getSecurityCode();
            codesByGroup.computeIfAbsent(item.getAdjustGroupKey(), key -> new LinkedHashSet<>()).add(code);
        }
        Map<String, Set<String>> failuresByGroup = queryIdentityGroupFailures(security, codesByGroup);
        for (AdjustCheckDto.CheckResultItem item : items) {
            Set<String> failures = failuresByGroup.get(item.getAdjustGroupKey());
            if (failures != null) {
                for (String failure : failures) {
                    // 将不可绕过的主体冲突加入原有调整说明
                    applyCheckFailure(item, failure);
                }
            }
        }
    }

    /**
     * 按调库组汇总主体冲突，证券与 CRMW 的不同返回结构共用同一校验
     * 执行顺序：
     * 1. 每个实际证券代码仅查询一次主体占用
     * 2. 将冲突原因归入其所在的调库组
     * @param security 本次已选择主体的证券
     * @param codesByGroup 各组实际证券代码
     * @return 各组的主体冲突原因
     */
    public Map<String, Set<String>> queryIdentityGroupFailures(SecurityInfoBo security, Map<String, Set<String>> codesByGroup) {
        Map<String, Set<String>> failures = new HashMap<>();
        if (!CreditBondSpecialInboundRule.isAbs(security)) return failures;
        Map<String, String> failuresByCode = new HashMap<>();
        for (Map.Entry<String, Set<String>> group : codesByGroup.entrySet()) {
            for (String code : group.getValue()) {
                if (!failuresByCode.containsKey(code)) {
                    failuresByCode.put(code, queryIdentityFailure(code, security.getIssuerCode()));
                }
                String failure = failuresByCode.get(code);
                if (failure != null) failures.computeIfAbsent(group.getKey(), key -> new LinkedHashSet<>()).add(failure);
            }
        }
        return failures;
    }

    /**
     * 为校验项追加一条不可调整原因，兼容原有不可变原因列表
     * 执行顺序：
     * 1. 复制并去重原原因，清除该项可提交流程及材料豁免
     * @param item 校验结果项
     * @param failure 本次失败原因
     */
    public void applyCheckFailure(AdjustCheckDto.CheckResultItem item, String failure) {
        if (failure == null) return;
        List<String> failures = item.getFailReasons() == null ? new ArrayList<>() : new ArrayList<>(item.getFailReasons());
        if (!failures.contains(failure)) failures.add(failure);
        item.setFailReasons(failures);
        item.setCanAdjust(false);
        item.setFlowOptions(Collections.emptyList());
        item.setMaterialExemptionType(null);
    }

    /**
     * 查询同批 ABS 不同主体选择的冲突，返回逐券原因而不中断整批校验
     * 执行顺序：
     * 1. 筛选同批多次选择主体的 ABS，普通债保持原规则
     * 2. 同代码不同角色不冲突，省略选择时按原候选首条确定主体
     * @param selections 各证券的最终主体代码集合
     * @return 存在冲突的证券及原因
     */
    public Map<String, String> queryBatchIdentityFailures(Map<String, Set<String>> selections) {
        List<String> duplicated = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : selections.entrySet()) {
            if (entry.getValue().size() > 1) duplicated.add(entry.getKey());
        }
        Map<String, String> failures = new HashMap<>();
        // 只核验重复选择的实际 ABS 主档
        for (SecurityInfoBo security : queryAbsMasters(duplicated).values()) {
            Set<String> codes = new LinkedHashSet<>(selections.get(security.getWindCode()));
            if (codes.remove("")) {
                queryRiskCompanyCodes(security);
                List<RelatedRatingCompanyDto> related = findRelated(security.getWindCode());
                if (related != null && !related.isEmpty()) codes.add(related.get(0).getCompanyCode());
            }
            if (codes.size() > 1) {
                failures.put(security.getWindCode(), "同一 ABS[" + security.getWindCode()
                        + "]在同批申请中选择了不同主体，请统一后重新校验");
            }
        }
        return failures;
    }

    /**
     * 分组前拒绝同批同一 ABS 的不同主体，不能先合并明细后丢掉冲突选择
     * 执行顺序：
     * 1. 未携带证券类型的内部调用读取主档识别 ABS
     * 2. 比较各来源主项最终选择的主体代码
     * @param securityCode 来源证券代码
     * @param rightsHolderCode 普通权益人
     * @param selfSelectedCode 自选权益人
     * @param selectedBySecurity 本批已选主体
     */
    public void validateBatchSelection(String securityCode, String rightsHolderCode, String selfSelectedCode,
                                       Map<String, String> selectedBySecurity) {
        // 未携带类型的调用沿用本次读取到的证券类型
        SecurityInfoBo security = securityPoolAdjustMapper.querySecurityBoByCode(securityCode);
        validateBatchSelection(securityCode, CreditBondSpecialInboundRule.isAbs(security) ? "abs" : null,
                rightsHolderCode, selfSelectedCode, selectedBySecurity);
    }

    /**
     * 使用页面已知类型比较同批 ABS 主体，避免逐项读取证券主档
     *
     * 执行顺序：
     * 1. 非 ABS 直接返回，ABS 比较本批已选主体
     * @param securityCode 来源证券代码
     * @param securityType 页面已知证券类型
     * @param rightsHolderCode 普通权益人
     * @param selfSelectedCode 自选权益人
     * @param selectedBySecurity 本批已选主体
     */
    public void validateBatchSelection(String securityCode, String securityType, String rightsHolderCode,
                                       String selfSelectedCode, Map<String, String> selectedBySecurity) {
        if (!"abs".equals(securityType)) {
            return;
        }
        String code = selfSelectedCode != null && !selfSelectedCode.trim().isEmpty()
                ? selfSelectedCode.trim() : rightsHolderCode;
        if (code == null || code.trim().isEmpty()) {
            // 没有显式选择时使用同一候选顺序，不能用空值绕过跨行比较
            List<RelatedRatingCompanyDto> related = findRelated(securityCode);
            code = related == null || related.isEmpty() ? null : related.get(0).getCompanyCode();
        }
        if (code == null || code.trim().isEmpty()) {
            throw new BizException("ABS[" + securityCode + "]请选择权益人或自选权益人");
        }
        String previous = selectedBySecurity.put(securityCode, code);
        if (previous != null && !previous.equals(code)) {
            throw new BizException("同一 ABS[" + securityCode + "]在同批申请中选择了不同主体，请统一后重新校验");
        }
    }

    /**
     * 为申请页面提供已固定或在途占用的主体，成功入库后出清仍沿用原主体
     * 执行顺序：
     * 1. 检查所有在库和在途身份是否一致
     * 2. 主档已保存该身份时直接复用，否则读取尚未通过的申请快照
     * @param security 当前主档详情
     * @return 当前占用主体，没有占用时为空
     */
    public SecurityInfoBo queryCurrentIdentity(SecurityInfoBo security) {
        if (!CreditBondSpecialInboundRule.isAbs(security)) {
            return null;
        }
        List<String> codes = absIdentityMapper.queryOccupiedIdentityCodes(security.getWindCode());
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        String code = codes.get(0);
        // 直接检查本次已查出的占用，不重复查询相同状态
        for (String occupied : codes) {
            if (occupied == null || occupied.trim().isEmpty() || !occupied.equals(code)) {
                throw new BizException("ABS[" + security.getWindCode() + "]在库或在途主体身份不一致，请先处理冲突记录");
            }
        }
        SecurityInfoBo identity = new SecurityInfoBo();
        if (code.equals(security.getIssuerCode())) {
            BeanUtils.copyProperties(security, identity);
        } else {
            identity = absIdentityMapper.queryPendingIdentityDetail(security.getWindCode());
            if (identity == null || !code.equals(identity.getIssuerCode())) {
                throw new BizException("ABS[" + security.getWindCode() + "]在途主体快照不完整");
            }
        }
        return identity;
    }

    /**
     * 批量筛选 ABS 并恢复固定或在途占用的唯一主体
     * 执行顺序：
     * 1. 每批最多一千个代码读取 ABS 主档
     * 2. 只对 ABS 查询在库及在途占用，普通债保留原批量路径
     * @param securityCodes 已去重证券代码
     * @return 被占用的主体，未占用证券不返回
     */
    public Map<String, SecurityInfoBo> queryCurrentIdentities(List<String> securityCodes) {
        Map<String, SecurityInfoBo> identities = new HashMap<>();
        for (int offset = 0; offset < securityCodes.size(); offset += 1000) {
            List<String> batch = securityCodes.subList(offset, Math.min(offset + 1000, securityCodes.size()));
            for (SecurityInfoBo security : absIdentityMapper.queryAbsSecurityList(batch)) {
                // 每只 ABS 只恢复一次身份，同码导入行共享这个结果
                SecurityInfoBo identity = queryCurrentIdentity(security);
                if (identity != null) identities.put(security.getWindCode(), identity);
            }
        }
        return identities;
    }

    /**
     * 从精确申请快照保存审核通过的 ABS 主体，和落池处于同一事务
     * 执行顺序：
     * 1. 复核所有 ABS 调入项的在库和在途主体
     * 2. 同一批主体一致后，仅更新主档五字段
     * @param logs 本次已通过最终业务复核的日志
     */
    public void approveIdentities(List<IpAdjustLogBo> logs) {
        // 读取本笔快照并完成主体一致性校验
        processApprovalIdentities(logs, true, false);
    }

    /**
     * 最终业务复核前检查 ABS 在库和在途主体
     * 执行顺序：
     * 1. 从各笔申请快照读取主体并校验，不提前修改主档
     * @param logs 本次待生效日志
     */
    public void validateApprovalIdentities(List<IpAdjustLogBo> logs) {
        // 这里只检查身份，主档在实际落池时更新
        processApprovalIdentities(logs, false, false);
    }

    /**
     * CRMW 标的 ABS 与普通池共用身份
     * 执行顺序：
     * 1. 从组合申请快照读取身份并按指定阶段校验或保存
     * @param logs 组合申请日志
     * @param save 是否审核通过并保存主档
     */
    public void processCrmwApproval(List<IpAdjustLogBo> logs, boolean save) {
        // 两条链路共用日志，主体快照按各自来源表读取
        processApprovalIdentities(logs, save, true);
    }

    /**
     * 从申请快照复核主体并按需保存主档
     * 执行顺序：
     * 1. 汇总本次主体并检查同批不同身份
     * 2. 检查在库及在途身份，实际通过时更新五字段
     * @param logs 本次日志
     * @param save 是否更新主档
     * @param crmw 是否读取 CRMW 快照
     */
    private void processApprovalIdentities(List<IpAdjustLogBo> logs, boolean save, boolean crmw) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        List<String> securityCodes = new ArrayList<>();
        for (IpAdjustLogBo log : logs) {
            if (!securityCodes.contains(log.getSecurityCode())) securityCodes.add(log.getSecurityCode());
        }
        // 一次筛选 ABS，普通债不逐条额外查询主档
        Map<String, SecurityInfoBo> absMasters = queryAbsMasters(securityCodes);
        Map<String, SecurityInfoBo> selected = new TreeMap<>();
        for (IpAdjustLogBo log : logs) {
            if (!save && log.getId() == null) {
                // 批量预检尚未创建申请，真实提交保存快照后仍须完整复核
                continue;
            }
            if (save && !AdjustMode.IN.getCode().equals(log.getAdjustMode())) {
                continue;
            }
            if (!absMasters.containsKey(log.getSecurityCode())) {
                continue;
            }
            SecurityInfoBo snapshot = crmw
                    ? absIdentityMapper.queryCrmwIdentitySnapshot(log.getId(), log.getSecurityCode())
                    : securityPoolAdjustMapper.querySecuritySnapshotDetailByAdjustLogId(log.getId(), log.getSecurityCode());
            if (snapshot == null || snapshot.getIssuerCode() == null) {
                throw new BizException("ABS[" + log.getSecurityCode() + "]申请[" + log.getId() + "]缺少主体快照");
            }
            SecurityInfoBo identity = new SecurityInfoBo();
            BeanUtils.copyProperties(snapshot, identity);
            identity.setWindCode(log.getSecurityCode());
            SecurityInfoBo previous = selected.put(log.getSecurityCode(), identity);
            if (previous != null && !previous.getIssuerCode().equals(identity.getIssuerCode())) {
                throw new BizException("同一 ABS[" + log.getSecurityCode() + "]在同批申请中选择了不同主体");
            }
        }
        for (SecurityInfoBo identity : selected.values()) {
            // 在主档更新前复核全部身份占用
            validateIdentity(identity.getWindCode(), identity.getIssuerCode());
            if (save && absIdentityMapper.editApprovedIdentity(identity) != 1) {
                throw new BizException("ABS[" + identity.getWindCode() + "]审核通过后保存主体身份失败");
            }
        }
    }

    /**
     * 为本次每条实际证券日志保存同一主体身份，关联市场也使用各自证券代码
     * 执行顺序：
     * 1. 从后端选择结果复制五字段和旧矩阵评级字段
     * 2. 逐日志保存不可变申请快照，空前端基本信息也不会漏存
     * @param security 后端已核验的本次证券信息
     * @param logs 本次产生的日志
     * @param submitterId 提交人
     * @param rightsHolderName 普通权益人名称
     * @param selfSelectedName 自选权益人名称
     */
    public void saveSnapshots(SecurityInfoBo security, Map<Long, IpAdjustLogBo> logs, String submitterId,
                              String rightsHolderName, String selfSelectedName) {
        Date now = new Date();
        for (IpAdjustLogBo log : logs.values()) {
            AdjustSecuritySnapshotBo snapshot = new AdjustSecuritySnapshotBo();
            BeanUtils.copyProperties(security, snapshot);
            snapshot.setWindCode(log.getSecurityCode());
            snapshot.setAdjustLogId(log.getId());
            snapshot.setSubmitterId(submitterId);
            snapshot.setSubmitTime(now);
            snapshot.setCrteTime(now);
            snapshot.setUpdtTime(now);
            snapshot.setIsDeleted(0);
            snapshot.setAbsOriginatorName(rightsHolderName);
            snapshot.setCompanySelector(selfSelectedName);
            if (securityPoolAdjustMapper.addAdjustSecuritySnapshot(snapshot) != 1) {
                throw new BizException("ABS[" + log.getSecurityCode() + "]申请主体快照保存失败");
            }
        }
    }

    /**
     * 最终规则复核使用本笔已选主体，同时保留主档最新证券状态和日期
     * 执行顺序：
     * 1. 按申请来源精确读取快照
     * 2. 仅回填五个主体字段及现有矩阵评级字段
     * @param security 当前主档业务对象
     * @param log 本笔日志
     * @param crmw 是否为组合申请
     */
    public void restoreSnapshotSelection(SecurityInfoBo security, IpAdjustLogBo log, boolean crmw) {
        SecurityInfoBo snapshot = crmw
                ? absIdentityMapper.queryCrmwIdentitySnapshot(log.getId(), log.getSecurityCode())
                : securityPoolAdjustMapper.querySecuritySnapshotDetailByAdjustLogId(log.getId(), log.getSecurityCode());
        if (snapshot == null || snapshot.getIssuerCode() == null) {
            throw new BizException("ABS[" + log.getSecurityCode() + "]申请[" + log.getId() + "]缺少主体快照");
        }
        security.setIssuer(snapshot.getIssuer());
        security.setIssuerCode(snapshot.getIssuerCode());
        security.setInnerIssuerRating(snapshot.getInnerIssuerRating());
        security.setRatingBondissuer(snapshot.getRatingBondissuer());
        security.setRatingBondissuerAgency(snapshot.getRatingBondissuerAgency());
        security.setGuarantor(snapshot.getGuarantor());
        security.setGuarantorId(snapshot.getGuarantorId());
        security.setInnerGuarantorRating(snapshot.getInnerGuarantorRating());
    }

    /**
     * 分批读取代码集合中的 ABS 主档
     * 执行顺序：
     * 1. 每批最多一千个代码筛选 ABS，并按代码建立索引
     * @param codes 实际证券代码
     * @return ABS 主档索引
     */
    public Map<String, SecurityInfoBo> queryAbsMasters(List<String> codes) {
        Map<String, SecurityInfoBo> result = new HashMap<>();
        for (int offset = 0; offset < codes.size(); offset += 1000) {
            for (SecurityInfoBo security : absIdentityMapper.queryAbsSecurityList(
                    codes.subList(offset, Math.min(offset + 1000, codes.size())))) {
                result.put(security.getWindCode(), security);
            }
        }
        return result;
    }
}
