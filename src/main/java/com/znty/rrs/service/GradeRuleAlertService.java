package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.common.util.CreditBondSpecialInboundRule;
import com.znty.rrs.entity.bo.IpGradeRuleAlertBo;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.graderulealert.GradeRuleAlertDto;
import com.znty.rrs.entity.graderulealert.GradeRuleAlertReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.GradeRuleAlertMapper;
import com.znty.rrs.mapper.SecurityPoolAdjustMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 不符合主体债入库规则提醒：定时扫描已在分级库但按当前规则不再允许的债券，生成待办供人工处理。
 *
 * <p>对齐老系统 InconformityMaingrade2Job：只提醒、不自动出池。处理入口在「我的事宜」分级规则提醒页签。</p>
 */
@Slf4j
@Service
public class GradeRuleAlertService implements RrsScheduledTask {

    /** 任务编码 */
    public static final String TASK_CODE = "bond_grade_inconformity_alert";

    /** 任务名称 */
    public static final String TASK_NAME = "不符合主体债入库规则提醒";

    /** 待处理 */
    public static final String STATUS_OPEN = "00";

    /** 已处理 */
    public static final String STATUS_DONE = "20";

    private static final String PARAM_HELP =
            "本任务无需扩展参数，请将 param_json 留空\n"
                    + "扫描已在信用债/境外债 1～5 级且生效的债券\n"
                    + "按当前主体债入库规则（含特殊债天花板、观察封顶、重点观察）复核\n"
                    + "不符合则写入待办，不自动出池；人工在「我的事宜」分级规则提醒页签处理";

    /** 待办 Mapper */
    @Resource
    private GradeRuleAlertMapper gradeRuleAlertMapper;

    /** 证券池调库（复用入库规则评估） */
    @Resource
    private SecurityPoolAdjustService securityPoolAdjustService;

    /** 证券主数据 */
    @Resource
    private SecurityPoolAdjustMapper securityPoolAdjustMapper;

    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    @Override
    public String getParamHelp() {
        return PARAM_HELP;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduledTaskResult execute() {
        Date start = new Date();
        TaskDetailLog detailLog = new TaskDetailLog();
        detailLog.line("开始扫描已在分级库债券是否仍符合入库规则");
        List<IpGradeRuleAlertBo> inPoolList = gradeRuleAlertMapper.queryGradedBondInPoolList();
        if (inPoolList == null) {
            inPoolList = new ArrayList<IpGradeRuleAlertBo>();
        }
        detailLog.line("在池分级库记录 " + inPoolList.size() + " 条");
        int hitCount = 0;
        Date scanTime = new Date();
        for (IpGradeRuleAlertBo row : inPoolList) {
            if (row.getSecurityCode() == null || row.getCurrentPoolId() == null) {
                continue;
            }
            String fail = securityPoolAdjustService.evaluateGradedInboundForPool(
                    row.getSecurityCode(), row.getCurrentPoolId());
            if (fail == null || fail.isEmpty()) {
                continue;
            }
            hitCount++;
            // 命中则新增或刷新待处理待办
            upsertOpenAlert(row, fail, scanTime);
        }
        int invalidCount = gradeRuleAlertMapper.editStaleOpenAlertInvalid(scanTime);
        detailLog.line("本轮新增/刷新待办 " + hitCount + " 条，失效 " + invalidCount + " 条");
        return ScheduledTaskResult.success(TASK_CODE, TASK_NAME,
                "扫描完成，待办 " + hitCount + " 条，失效 " + invalidCount + " 条",
                hitCount, start, System.currentTimeMillis() - start.getTime(), detailLog.build());
    }

    /**
     * 分页查询待办。
     *
     * @param req 查询条件
     * @return 分页结果
     */
    public PageResult<GradeRuleAlertDto> queryAlertPage(GradeRuleAlertReq req) {
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<IpGradeRuleAlertBo> rows = gradeRuleAlertMapper.queryAlertPage(req);
        PageInfo<IpGradeRuleAlertBo> pageInfo = new PageInfo<IpGradeRuleAlertBo>(rows);
        List<GradeRuleAlertDto> dtoList = new ArrayList<GradeRuleAlertDto>();
        for (IpGradeRuleAlertBo row : rows) {
            GradeRuleAlertDto dto = new GradeRuleAlertDto();
            BeanUtils.copyProperties(row, dto);
            dtoList.add(dto);
        }
        return new PageResult<GradeRuleAlertDto>(dtoList, pageInfo.getTotal(),
                req.getPageIndex(), req.getPageSize());
    }

    /**
     * 人工标记已处理。不改池状态，只关闭待办。
     *
     * @param req 含 id 与处理人
     * @return 更新后的待办
     */
    @Transactional(rollbackFor = Exception.class)
    public GradeRuleAlertDto editAlertProcessed(GradeRuleAlertReq req) {
        if (req.getId() == null) {
            throw new BizException("待办 ID 不能为空");
        }
        IpGradeRuleAlertBo exist = gradeRuleAlertMapper.queryAlertById(req.getId());
        if (exist == null) {
            throw new BizException("待办不存在");
        }
        if (!STATUS_OPEN.equals(exist.getAlertStatus())) {
            throw new BizException("仅待处理记录可标记已处理");
        }
        Date now = new Date();
        exist.setDealUserId(req.getCurrentUserId());
        exist.setDealUserName(req.getCurrentUserName());
        exist.setDealTime(now);
        exist.setUpdtTime(now);
        int n = gradeRuleAlertMapper.editAlertProcessed(exist);
        if (n == 0) {
            throw new BizException("待办状态已变化，请刷新后重试");
        }
        GradeRuleAlertDto dto = new GradeRuleAlertDto();
        BeanUtils.copyProperties(gradeRuleAlertMapper.queryAlertById(req.getId()), dto);
        return dto;
    }

    /**
     * 写入或刷新待处理待办。
     *
     * @param row      在池行
     * @param fail     不符合原因
     * @param scanTime 本轮扫描时间
     */
    private void upsertOpenAlert(IpGradeRuleAlertBo row, String fail, Date scanTime) {
        IpGradeRuleAlertBo open = gradeRuleAlertMapper.queryOpenAlert(row.getSecurityCode(), row.getCurrentPoolId());
        SecurityInfoBo sec = securityPoolAdjustMapper.querySecurityBoByCode(row.getSecurityCode());
        String specialDesc = buildSpecialTypeDesc(sec, row.getSecurityCode());
        if (open == null) {
            row.setFailReason(fail);
            row.setSpecialTypeDesc(specialDesc);
            row.setAlertStatus(STATUS_OPEN);
            row.setLastScanTime(scanTime);
            row.setCrteTime(scanTime);
            row.setUpdtTime(scanTime);
            gradeRuleAlertMapper.addAlert(row);
            return;
        }
        open.setSecurityShortName(row.getSecurityShortName());
        open.setIssuerCode(row.getIssuerCode());
        open.setIssuerName(row.getIssuerName());
        open.setCurrentPoolName(row.getCurrentPoolName());
        open.setCurrentSort(row.getCurrentSort());
        open.setFailReason(fail);
        open.setSpecialTypeDesc(specialDesc);
        open.setLastScanTime(scanTime);
        open.setUpdtTime(scanTime);
        gradeRuleAlertMapper.editAlertScan(open);
    }

    /**
     * 拼特殊类型说明，便于列表一眼看到为何被扫到。
     *
     * @param sec          证券
     * @param securityCode 证券代码
     * @return 说明
     */
    private String buildSpecialTypeDesc(SecurityInfoBo sec, String securityCode) {
        List<String> parts = new ArrayList<String>();
        if (CreditBondSpecialInboundRule.isPrivateBond(sec)) {
            parts.add("私募");
        }
        if (CreditBondSpecialInboundRule.isPerpetual(sec)) {
            parts.add("永续");
        }
        if (CreditBondSpecialInboundRule.isSubordinated(sec)) {
            parts.add("次级");
        }
        if (CreditBondSpecialInboundRule.isAbs(sec)) {
            parts.add("ABS");
        }
        if (CreditBondSpecialInboundRule.isGuaranteed(sec)) {
            parts.add("担保");
        }
        if (CreditBondSpecialInboundRule.isInright(sec)) {
            parts.add("含权");
        }
        if (securityPoolAdjustMapper.querySecurityInObservePool(securityCode)
                || securityPoolAdjustMapper.queryIssuerInObservePool(securityCode)) {
            parts.add("观察名单");
        }
        if (securityPoolAdjustMapper.querySecurityInRestrictedPool(securityCode)
                || securityPoolAdjustMapper.queryIssuerInRestrictedPool(securityCode)) {
            parts.add("重点观察");
        }
        if (parts.isEmpty()) {
            return "普通债/内评或期限变化";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append("/");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
