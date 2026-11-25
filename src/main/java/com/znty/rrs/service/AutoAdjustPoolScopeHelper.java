package com.znty.rrs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.znty.rrs.entity.bo.InvestmentPoolBo;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.AutoAdjustMapper;
import com.znty.rrs.mapper.InvestmentPoolMapper;
import com.znty.rrs.schedule.TaskDetailLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 确定自动调库任务的扫描池范围
 *
 * 本类是自动调库辅助类，合并扩展参数中的 poolIds 与通过池关系配置绑定本任务的池
 */
@Slf4j
@Component
public class AutoAdjustPoolScopeHelper {

    /** 参数与关系配置都未给出扫描池 */
    static final String EMPTY_POOL_SCOPE_MESSAGE =
            "未配置扫描池：请在扩展参数填写 poolIds，或在投资池关系配置中绑定本任务";

    /** 映射类任务未给出扫描池 */
    static final String EMPTY_MAPPING_SCOPE_MESSAGE =
            "未配置扫描池：请在扩展参数填写 poolIds 或 mappings，或在投资池关系配置中绑定本任务";

    /** 解析定时任务扩展参数的 JSON 组件 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 自动调库查询 */
    @Resource
    private AutoAdjustMapper autoAdjustMapper;

    /** 投资池查询（关系配置日志带池名称） */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /**
     * 确定任务实际扫描的投资池
     *
     * 参数为空视为空集合；JSON 非法仍抛异常。并集为空时抛 {@link BizException}
     *
     * 执行顺序：
     * 1. 读取扩展参数和关系配置中的池 ID，按参数在前、配置在后的顺序合并去重
     * 2. 记录配置来源和最终扫描范围，无可扫描池时终止任务
     *
     * @param paramJson 定时任务扩展参数原文
     * @param taskCode 定时任务编码
     * @param ruleType 任务绑定的池关系类型
     * @param detail 任务过程日志记录器
     * @return 扩展参数与关系配置合并后的扫描池 ID
     */
    public List<Long> resolveUnionPoolIds(String paramJson, String taskCode, String ruleType,
                                          TaskDetailLog detail) {
        // 1. 读取参数和关系配置中的池并按顺序合并去重
        List<Long> fromParam = parseOptionalPoolIds(paramJson);
        List<Long> fromConfig = queryBoundPoolIds(taskCode, ruleType);
        List<Long> union = unionPoolIds(fromParam, fromConfig);
        // 2. 记录各来源和最终范围，扫描池为空时终止任务
        infoDetail(detail, "关系配置 poolIds=" + formatBoundPoolIds(fromConfig));
        // 记录扩展参数与关系配置合并后的扫描池范围
        infoDetail(detail, "扫描池并集 poolIds=" + union
                + "（参数=" + fromParam + "，关系配置=" + fromConfig + "）");
        if (union.isEmpty()) {
            throw new BizException(EMPTY_POOL_SCOPE_MESSAGE);
        }
        return union;
    }

    /**
     * 合并任务参数和关系配置中的扫描池映射
     *
     * 执行顺序：
     * 1. 先加入参数中的池映射，再把每个绑定池加入为从该池到同一池的映射
     * 2. 记录各来源的新增映射数，没有可扫描的映射时终止任务
     *
     * @param paramMappings 参数中的起止池映射
     * @param taskCode 定时任务编码
     * @param ruleType 任务绑定的池关系类型
     * @param detail 任务过程日志记录器
     * @return 参数与关系配置合并后的去重池映射
     */
    public List<long[]> unionSamePoolMappings(List<long[]> paramMappings, String taskCode,
                                              String ruleType, TaskDetailLog detail) {
        List<long[]> result = new ArrayList<long[]>();
        Set<String> seen = new LinkedHashSet<String>();
        // 1. 先加入扩展参数指定的池映射
        int paramCount = addPairs(result, seen, paramMappings);
        // 2. 查询配置池，将其补成起止池相同的映射
        List<Long> fromConfig = queryBoundPoolIds(taskCode, ruleType);
        List<long[]> configPairs = new ArrayList<long[]>();
        if (fromConfig != null) {
            for (Long poolId : fromConfig) {
                if (poolId != null) {
                    configPairs.add(new long[]{poolId.longValue(), poolId.longValue()});
                }
            }
        }
        // 把任务绑定池补成起止池相同的映射
        int configCount = addPairs(result, seen, configPairs);
        // 3. 记录池来源和有效映射数量，没有可扫描映射时终止任务
        infoDetail(detail, "关系配置 poolIds=" + formatBoundPoolIds(fromConfig));
        // 记录参数映射和配置映射各自新增的数量
        infoDetail(detail, "扫描映射并集 " + result.size() + " 组（参数=" + paramCount
                + " 组，关系配置同池=" + configCount + " 组）");
        if (result.isEmpty()) {
            throw new BizException(EMPTY_MAPPING_SCOPE_MESSAGE);
        }
        return result;
    }

    /**
     * 读取扩展参数中可选的池 ID 列表
     *
     * 执行顺序：
     * 1. 解析扩展参数，未配置 poolIds 时返回空列表
     * 2. 检查 poolIds 为数组且各项均为数字，再按原顺序转成池 ID
     *
     * @param paramJson 定时任务扩展参数原文
     * @return 配置中的池 ID，未配置时为空列表
     */
    public static List<Long> parseOptionalPoolIds(String paramJson) {
        List<Long> result = new ArrayList<Long>();
        // 读取 JSON 对象，再按扫描池字段解析
        JsonNode root = parseParamJson(paramJson);
        if (root == null) {
            return result;
        }
        JsonNode poolIds = root.get("poolIds");
        if (poolIds == null || poolIds.isNull()) {
            return result;
        }
        if (!poolIds.isArray()) {
            throw new BizException("扩展参数须包含 poolIds 数组，示例 {\"poolIds\":[15]}");
        }
        for (JsonNode item : poolIds) {
            if (item == null || item.isNull() || !item.isNumber()) {
                throw new BizException("poolIds 元素须为数字，非法值: " + item
                        + "；正确示例 {\"poolIds\":[15]}（勿写 '15' 单引号）");
            }
            result.add(item.asLong());
        }
        return result;
    }

    /**
     * 读取到期自动出池的日期偏移天数，省略时沿用原有的 1 天
     *
     * 执行顺序：
     * 1. 解析扩展参数，未配置 maturityOffsetDays 时使用 1 天
     * 2. 校验配置是 int 范围内的整数并返回
     *
     * @param paramJson 扩展参数原文
     * @return DATE_SUB 使用的偏移天数
     */
    public static int parseMaturityOffsetDays(String paramJson) {
        // 读取 JSON 对象，再校验日期偏移字段
        JsonNode root = parseParamJson(paramJson);
        if (root == null || !root.has("maturityOffsetDays")) {
            return 1;
        }
        JsonNode days = root.get("maturityOffsetDays");
        if (!days.isIntegralNumber() || !days.canConvertToInt()) {
            throw new BizException("maturityOffsetDays 须为 int 范围内的整数，当前: " + days);
        }
        return days.intValue();
    }

    /**
     * 解析任务扩展参数的 JSON 对象
     *
     * 执行顺序：
     * 1. 空参数返回空值，其他参数去除首尾空白后检查对象格式
     * 2. 解析 JSON，解析失败时提示正确格式
     *
     * @param paramJson 扩展参数原文
     * @return JSON 对象，未配置时为 null
     */
    private static JsonNode parseParamJson(String paramJson) {
        if (!StringUtils.hasText(paramJson)) {
            return null;
        }
        String text = paramJson.trim();
        if (!text.startsWith("{")) {
            throw new BizException("扩展参数仅支持 JSON 对象，示例 {\"poolIds\":[15]}，当前: " + text);
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(text);
        } catch (Exception e) {
            throw new BizException("扩展参数 JSON 解析失败: " + e.getMessage()
                    + "；请使用标准 JSON，数字勿加单引号，示例 {\"poolIds\":[15]}");
        }
        return root;
    }

    /**
     * 查询投资池关系配置中绑定了指定定时任务与调入/调出类型的池 ID
     *
     * 执行顺序：
     * 1. 按任务编码和关系类型查询绑定池，缺少查询条件或查询组件时返回空列表
     * 2. 去掉查询结果中的空 ID
     *
     * @param taskCode 定时任务编码
     * @param ruleType 任务绑定的池关系类型
     * @return 绑定该任务和关系类型的池 ID，无结果时为空列表
     */
    public List<Long> queryBoundPoolIds(String taskCode, String ruleType) {
        List<Long> result = new ArrayList<Long>();
        if (!StringUtils.hasText(taskCode) || !StringUtils.hasText(ruleType) || autoAdjustMapper == null) {
            return result;
        }
        List<Long> bound = autoAdjustMapper.queryBoundPoolIds(taskCode, ruleType);
        if (bound == null) {
            return result;
        }
        for (Long poolId : bound) {
            if (poolId != null) {
                result.add(poolId);
            }
        }
        return result;
    }

    /**
     * 合并两份池 ID，参数在前、配置在后，去重保序
     *
     * 执行顺序：
     * 1. 先加入参数中的池 ID，再加入关系配置中的池 ID，每个 ID 只保留首次出现的位置
     *
     * @param fromParam 参数中的池 ID
     * @param fromConfig 关系配置绑定的池 ID
     * @return 合并去重且保留首次出现顺序的池 ID
     */
    static List<Long> unionPoolIds(List<Long> fromParam, List<Long> fromConfig) {
        List<Long> union = new ArrayList<Long>();
        Set<Long> seen = new LinkedHashSet<Long>();
        // 先加入扩展参数里的池 ID，保留配置顺序
        appendPoolIds(union, seen, fromParam);
        // 再追加关系配置里的池 ID，跳过重复项
        appendPoolIds(union, seen, fromConfig);
        return union;
    }

    /**
     * 格式化关系配置绑定池：保留全部绑定 ID，能查到名称则带上，无绑定打印空数组
     *
     * 执行顺序：
     * 1. 没有绑定池时返回空数组的文本，有绑定池时查询池名称
     * 2. 逐个拼接池 ID，名称存在时附在 ID 后
     *
     * @param poolIds 关系配置绑定的池 ID
     * @return 供日志展示的池 ID 和名称文本
     */
    String formatBoundPoolIds(List<Long> poolIds) {
        if (poolIds == null || poolIds.isEmpty()) {
            return "[]";
        }
        // 查询池名称，日志中没有名称的池仍保留 ID
        Map<Long, String> names = queryPoolNameMap();
        List<String> parts = new ArrayList<String>();
        for (Long poolId : poolIds) {
            if (poolId == null) {
                continue;
            }
            String name = names.get(poolId);
            if (StringUtils.hasText(name)) {
                parts.add(poolId + "(" + name + ")");
            } else {
                parts.add(String.valueOf(poolId));
            }
        }
        return parts.toString();
    }

    /**
     * 读取未删除投资池 ID → 名称，供关系配置日志使用
     *
     * 执行顺序：
     * 1. 查询投资池列表，查询组件不可用或没有结果时返回空映射
     * 2. 把有 ID 的投资池按 ID 记录名称
     *
     * @return 池 ID 到名称的映射，无结果时为空映射
     */
    private Map<Long, String> queryPoolNameMap() {
        Map<Long, String> names = new HashMap<Long, String>();
        if (investmentPoolMapper == null) {
            return names;
        }
        List<InvestmentPoolBo> pools = investmentPoolMapper.queryPoolList();
        if (pools == null) {
            return names;
        }
        for (InvestmentPoolBo pool : pools) {
            if (pool != null && pool.getId() != null) {
                names.put(pool.getId(), pool.getPoolName());
            }
        }
        return names;
    }

    /**
     * 将池 ID 追加到结果（跳过空值与重复）
     *
     * 执行顺序：
     * 1. 遍历来源池 ID，只追加有值且首次出现的 ID
     *
     * @param union 接收合并后池 ID 的列表
     * @param seen 已经加入合并列表的池 ID 集合
     * @param source 待加入合并列表的池 ID
     */
    private static void appendPoolIds(List<Long> union, Set<Long> seen, List<Long> source) {
        if (source == null) {
            return;
        }
        for (Long poolId : source) {
            if (poolId != null && seen.add(poolId)) {
                union.add(poolId);
            }
        }
    }

    /**
     * 将映射对追加到结果，返回实际新增组数
     *
     * 执行顺序：
     * 1. 跳过缺少起点或终点的映射，按起止池 ID 去重并复制新增映射
     * 2. 返回本次实际追加的映射数
     *
     * @param result 接收新映射的列表
     * @param seen 已经加入结果的起止池组合键
     * @param pairs 待加入的起止池映射
     * @return 实际追加的映射组数
     */
    private int addPairs(List<long[]> result, Set<String> seen, List<long[]> pairs) {
        int added = 0;
        if (pairs == null) {
            return added;
        }
        for (long[] pair : pairs) {
            if (pair == null || pair.length < 2) {
                continue;
            }
            String key = pair[0] + ":" + pair[1];
            if (seen.add(key)) {
                result.add(new long[]{pair[0], pair[1]});
                added++;
            }
        }
        return added;
    }

    /**
     * 写 INFO 过程日志并同步控制台
     *
     * 执行顺序：
     * 1. 写入应用日志，有任务日志记录器时同步记录这行 INFO 日志
     *
     * @param detail 任务过程日志记录器
     * @param line 要记录的日志文本
     */
    private void infoDetail(TaskDetailLog detail, String line) {
        log.info(line);
        if (detail != null) {
            detail.line("INFO", line);
        }
    }
}
