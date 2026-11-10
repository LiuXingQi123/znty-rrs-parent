package com.znty.rrs.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.znty.rrs.common.PageResult;
import com.znty.rrs.entity.bo.IpCrmwRuleAlertBo;
import com.znty.rrs.entity.crmwrulealert.CrmwRuleAlertDto;
import com.znty.rrs.entity.crmwrulealert.CrmwRuleAlertReq;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.CrmwRuleAlertMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/** CRMW规则提醒页面服务，只负责列表查询和人工处理 */
@Service
public class CrmwRuleAlertService {

    /** 提醒页面查询与人工处理数据访问 */
    @Resource
    private CrmwRuleAlertMapper crmwRuleAlertMapper;

    /**
     * 分页查询独立 CRMW 提醒，沿用债券业务共享提醒口径
     *
     * 执行顺序：
     * 1. 开启分页并紧接查询提醒
     * 2. 返回记录和分页总数
     *
     * @param req 凭证、标的及状态筛选
     * @return CRMW 提醒分页
     */
    public PageResult<CrmwRuleAlertDto> queryAlertPage(CrmwRuleAlertReq req) {
        // 1. 分页必须紧接数据库查询
        PageHelper.startPage(req.getPageIndex(), req.getPageSize());
        List<CrmwRuleAlertDto> rows = crmwRuleAlertMapper.queryAlertPage(req);
        // 2. 保留分页插件给出的总数
        PageInfo<CrmwRuleAlertDto> page = new PageInfo<>(rows);
        return new PageResult<>(rows, page.getTotal(), req.getPageIndex(), req.getPageSize());
    }

    /**
     * 记录研究员已处理结论，不修改 CRMW 组合或主体池状态
     *
     * 执行顺序：
     * 1. 校验提醒主键、处理人及当前待处理状态
     * 2. 更新处理人和处理时间，保留本次依据摘要并记录审计
     * 3. 返回处理后的提醒
     *
     * @param req 提醒主键与处理人
     * @return 处理后的提醒
     */
    @Transactional(rollbackFor = Exception.class)
    public CrmwRuleAlertDto editAlertProcessed(CrmwRuleAlertReq req) {
        // 1. 处理请求在入口校验一次
        if (req.getId() == null || !StringUtils.hasText(req.getCurrentUserId())
                || !StringUtils.hasText(req.getCurrentUserName())) {
            throw new BizException("CRMW提醒ID及处理人不能为空");
        }
        IpCrmwRuleAlertBo row = crmwRuleAlertMapper.queryAlertById(req.getId());
        if (row == null || !"00".equals(row.getAlertStatus())) {
            throw new BizException("CRMW提醒不存在或已不是待处理状态，请刷新后重试");
        }
        // 2. 只标记提醒，不执行出库或主体禁投调整
        Date now = new Date();
        row.setDealUserId(req.getCurrentUserId());
        row.setDealUserName(req.getCurrentUserName());
        row.setDealTime(now);
        row.setUpdtTime(now);
        if (crmwRuleAlertMapper.editAlertProcessed(row) != 1) {
            throw new BizException("CRMW提醒状态已变化，请刷新后重试");
        }
        crmwRuleAlertMapper.addAlertEvent(row.getId(), req.getCurrentUserId(), "UPDATE");
        // 3. 返回更新后的记录，摘要保持不变供下一轮复用
        row.setAlertStatus("20");
        CrmwRuleAlertDto dto = new CrmwRuleAlertDto();
        BeanUtils.copyProperties(row, dto);
        return dto;
    }
}
