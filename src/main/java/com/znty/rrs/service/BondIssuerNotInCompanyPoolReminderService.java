package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.entity.bo.SysScheduledTaskBo;
import com.znty.rrs.entity.schedule.BondReminderDto;
import com.znty.rrs.entity.schedule.ScheduledReminderMessageDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.BondReminderMapper;
import com.znty.rrs.mapper.ScheduledTaskMapper;
import com.znty.rrs.schedule.RrsScheduledTask;
import com.znty.rrs.schedule.ScheduledTaskResult;
import com.znty.rrs.schedule.TaskDetailLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 主体库内债券主体不在池债邮件提醒。
 *
 * <p>对齐老系统 RemindLimitPoolToNewBondJob：按债券池、主体池映射扫描
 * 债券池中仍生效但发行主体不在对应主体池的债券，排除 CRMW。</p>
 */
@Slf4j
@Service
public class BondIssuerNotInCompanyPoolReminderService implements RrsScheduledTask {

    /** 任务编码 */
    public static final String TASK_CODE = "bond_issuer_not_in_company_pool_reminder";

    /** 任务名称 */
    public static final String TASK_NAME = "主体库内债券主体不在池债邮件提醒";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PARAM_HELP =
            "参数格式：JSON 对象。同池示例 <code>{\"poolIds\":[15]}</code>；跨池示例 "
                    + "<code>{\"mappings\":[{\"bondPoolId\":15,\"companyPoolId\":15}]}</code>\n"
                    + "poolIds：每个 ID 同时作为债券池和主体池；未配置参数时默认 [15]\n"
                    + "mappings：bondPoolId 为债券当前所在池，companyPoolId 为发行主体应在池\n"
                    + "扫描内容：债券池内 audit_status=20、发行主体不在主体池的债券；排除 CRMW\n"
                    + "消息投递：当前通知通道未接入，仅生成候选明细并写执行过程日志";

    /** 提醒查询 Mapper */
    @Resource
    private BondReminderMapper bondReminderMapper;

    /** 定时任务配置 Mapper */
    @Resource
    private ScheduledTaskMapper scheduledTaskMapper;

    /** 统一通知投递服务 */
    @Resource
    private ScheduledReminderDeliveryService reminderDeliveryService;

    /** 返回主体不在池债券提醒任务编码。 */
    @Override
    public String getTaskCode() {
        return TASK_CODE;
    }

    /** 返回主体不在池债券提醒任务参数说明。 */
    @Override
    public String getParamHelp() {
        return PARAM_HELP;
    }

    /** 执行主体不在池债券提醒。 */
    @Override
    public ScheduledTaskResult execute() {
        Date startTime = new Date();
        long begin = System.currentTimeMillis();
        TaskDetailLog detail = new TaskDetailLog();
        detail.line("任务开始：【" + TASK_NAME + "】");
        try {
            // 解析债券池与主体池映射
            List<long[]> mappings = resolveMappings();
            detail.line("扫描条件：ip_pool_status.is_deleted=0、audit_status=20、category_type=bond、"
                    + "security_type!=crmw；债券在 bondPoolId，发行主体不在对应 companyPoolId 的生效主体记录中");
            detail.line("扫描范围：池映射 " + mappings.size() + " 组");
            List<BondReminderDto> rows = new ArrayList<BondReminderDto>();
            for (long[] mapping : mappings) {
                // 按单个池映射扫描主体不在池债券
                List<BondReminderDto> current = bondReminderMapper.queryIssuerNotInCompanyPoolBondList(
                        mapping[0], mapping[1]);
                if (current != null) {
                    rows.addAll(current);
                }
                detail.line("映射 bondPoolId=" + mapping[0] + "，companyPoolId=" + mapping[1]
                        + "，命中 " + (current == null ? 0 : current.size()) + " 条");
            }
            detail.line("扫描完成：主体不在池债券候选 " + rows.size() + " 条");
            // 生成消息及执行历史中的可追溯明细
            ScheduledReminderMessageDto reminder = buildReminder(rows);
            appendDetail(rows, detail);
            // 调用统一投递边界；当前实现明确返回未接入
            ScheduledReminderDeliveryService.DeliveryResult delivery = reminderDeliveryService.send(reminder);
            detail.line("消息投递状态=" + (delivery.isDelivered() ? "已发送" : "待接入")
                    + "，说明=" + delivery.getMessage());
            String message = "本轮发现 " + rows.size() + " 条主体不在池债券；" + delivery.getMessage();
            long duration = System.currentTimeMillis() - begin;
            detail.line("任务结束（成功）：扫描映射 " + mappings.size()
                    + " 组，候选 " + rows.size() + " 条，通知状态="
                    + (delivery.isDelivered() ? "已发送" : "待接入")
                    + "，耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.success(TASK_CODE, TASK_NAME, message, rows.size(), startTime,
                    duration, detail.build());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - begin;
            log.error("{}执行异常", TASK_NAME, e);
            detail.line("ERROR", "执行异常：" + e.getMessage());
            detail.line("ERROR", "任务结束（失败）：提醒扫描或组装过程异常，耗时 " + duration + " 毫秒");
            return ScheduledTaskResult.failure(TASK_CODE, TASK_NAME, "执行异常: " + e.getMessage(),
                    startTime, duration, detail.build());
        }
    }

    /** 读取并解析债券池、主体池映射。 */
    private List<long[]> resolveMappings() {
        SysScheduledTaskBo conf = scheduledTaskMapper.queryTaskByCode(TASK_CODE);
        if (conf == null || !StringUtils.hasText(conf.getParamJson())) {
            return Collections.singletonList(new long[]{15L, 15L});
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(conf.getParamJson());
        } catch (Exception e) {
            throw new BizException("扩展参数 JSON 解析失败: " + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            throw new BizException("扩展参数须为 JSON 对象");
        }
        List<long[]> result = new ArrayList<long[]>();
        Set<String> keys = new LinkedHashSet<String>();
        // poolIds 表示债券池与主体池相同
        appendSamePoolMappings(root.get("poolIds"), result, keys);
        // mappings 表示债券池与主体池可不同
        appendCrossPoolMappings(root.get("mappings"), result, keys);
        if (result.isEmpty()) {
            throw new BizException("poolIds 与 mappings 不能同时为空");
        }
        return result;
    }

    /** 解析同池映射。 */
    private void appendSamePoolMappings(JsonNode node, List<long[]> result, Set<String> keys) {
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isArray()) {
            throw new BizException("poolIds 须为数字数组");
        }
        for (JsonNode item : node) {
            // 校验同池配置 ID
            long id = requirePositiveId(item, "poolIds");
            // 对同一映射去重
            addMapping(id, id, result, keys);
        }
    }

    /** 解析跨池映射。 */
    private void appendCrossPoolMappings(JsonNode node, List<long[]> result, Set<String> keys) {
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isArray()) {
            throw new BizException("mappings 须为对象数组");
        }
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                throw new BizException("mappings 元素须为对象");
            }
            // 校验债券池和主体池 ID
            long bondPoolId = requirePositiveId(item.get("bondPoolId"), "bondPoolId");
            long companyPoolId = requirePositiveId(item.get("companyPoolId"), "companyPoolId");
            // 对同一映射去重
            addMapping(bondPoolId, companyPoolId, result, keys);
        }
    }

    /** 校验并读取正整数池 ID。 */
    private long requirePositiveId(JsonNode node, String fieldName) {
        if (node == null || !node.isNumber() || node.asLong() <= 0) {
            throw new BizException(fieldName + " 须为正整数，非法值: " + node);
        }
        return node.asLong();
    }

    /** 添加去重后的池映射。 */
    private void addMapping(long bondPoolId, long companyPoolId,
                            List<long[]> result, Set<String> keys) {
        String key = bondPoolId + "-" + companyPoolId;
        if (keys.add(key)) {
            result.add(new long[]{bondPoolId, companyPoolId});
        }
    }

    /** 构建后续通知通道可直接消费的消息。 */
    private ScheduledReminderMessageDto buildReminder(List<BondReminderDto> rows) {
        ScheduledReminderMessageDto message = new ScheduledReminderMessageDto();
        message.setTaskCode(TASK_CODE);
        message.setTitle(TASK_NAME);
        message.setItems(rows);
        message.setContent(rows.isEmpty() ? "本轮未发现主体不在池债券"
                : "本轮发现主体不在池债券 " + rows.size() + " 条");
        return message;
    }

    /** 将候选明细写入过程日志，通知未接入时仍可人工核对。 */
    private void appendDetail(List<BondReminderDto> rows, TaskDetailLog detail) {
        if (rows.isEmpty()) {
            detail.line("本轮无主体不在池债券");
            return;
        }
        for (BondReminderDto row : rows) {
            // 将可空主体字段格式化为可读文本
            detail.line("提醒候选：债券=" + row.getSecurityShortName() + "(" + row.getSecurityCode() + ")"
                    + "，发行主体=" + nullToEmpty(row.getIssuerName())
                    + "(" + nullToEmpty(row.getIssuerCode()) + ")"
                    + "，债券所在池=" + row.getTargetPoolName());
        }
    }

    /** 将空字符串字段安全输出到过程日志。 */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
