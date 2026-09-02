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
 * 自动调库扫描池解析：扩展参数 poolIds 与投资池关系配置绑定的定时任务取并集。
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 自动调库查询 */
    @Resource
    private AutoAdjustMapper autoAdjustMapper;
    /** 投资池查询（关系配置日志带池名称） */
    @Resource
    private InvestmentPoolMapper investmentPoolMapper;

    /**
     * 解析 param_json.poolIds 后与池上绑定本任务的配置取并集（去重，参数在前）。
     * <p>参数为空视为空集合；JSON 非法仍抛异常。并集为空时抛 {@link BizException}。
     */
    public List<Long> resolveUnionPoolIds(String paramJson, String taskCode, String ruleType,
                                          TaskDetailLog detail) {
        List<Long> fromParam = parseOptionalPoolIds(paramJson);
        List<Long> fromConfig = queryBoundPoolIds(taskCode, ruleType);
        List<Long> union = unionPoolIds(fromParam, fromConfig);
        // 单独列出关系配置绑定池，含后续扫描无候选数据的池
        infoDetail(detail, "关系配置 poolIds=" + formatBoundPoolIds(fromConfig));
        infoDetail(detail, "扫描池并集 poolIds=" + union
                + "（参数=" + fromParam + "，关系配置=" + fromConfig + "）");
        if (union.isEmpty()) {
            throw new BizException(EMPTY_POOL_SCOPE_MESSAGE);
        }
        return union;
    }

    /**
     * 将关系配置中绑定本任务的池按同池映射追加到参数映射，去重后若为空则失败。
     */
    public List<long[]> unionSamePoolMappings(List<long[]> paramMappings, String taskCode,
                                              String ruleType, TaskDetailLog detail) {
        List<long[]> result = new ArrayList<long[]>();
        Set<String> seen = new LinkedHashSet<String>();
        int paramCount = addPairs(result, seen, paramMappings);
        List<Long> fromConfig = queryBoundPoolIds(taskCode, ruleType);
        List<long[]> configPairs = new ArrayList<long[]>();
        if (fromConfig != null) {
            for (Long poolId : fromConfig) {
                if (poolId != null) {
                    configPairs.add(new long[]{poolId.longValue(), poolId.longValue()});
                }
            }
        }
        int configCount = addPairs(result, seen, configPairs);
        // 单独列出关系配置绑定池，含后续扫描无候选数据的池
        infoDetail(detail, "关系配置 poolIds=" + formatBoundPoolIds(fromConfig));
        infoDetail(detail, "扫描映射并集 " + result.size() + " 组（参数=" + paramCount
                + " 组，关系配置同池=" + configCount + " 组）");
        if (result.isEmpty()) {
            throw new BizException(EMPTY_MAPPING_SCOPE_MESSAGE);
        }
        return result;
    }

    /**
     * 解析 JSON 中的 poolIds；缺字段、空数组、参数为空均返回空列表。非法 JSON 抛业务异常。
     */
    public static List<Long> parseOptionalPoolIds(String paramJson) {
        List<Long> result = new ArrayList<Long>();
        if (!StringUtils.hasText(paramJson)) {
            return result;
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
     * 查询投资池关系配置中绑定了指定定时任务与调入/调出类型的池 ID。
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
     * 合并两份池 ID，参数在前、配置在后，去重保序。
     */
    static List<Long> unionPoolIds(List<Long> fromParam, List<Long> fromConfig) {
        List<Long> union = new ArrayList<Long>();
        Set<Long> seen = new LinkedHashSet<Long>();
        appendPoolIds(union, seen, fromParam);
        appendPoolIds(union, seen, fromConfig);
        return union;
    }

    /**
     * 格式化关系配置绑定池：保留全部绑定 ID，能查到名称则带上，无绑定打印空数组。
     */
    String formatBoundPoolIds(List<Long> poolIds) {
        if (poolIds == null || poolIds.isEmpty()) {
            return "[]";
        }
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
     * 读取未删除投资池 ID → 名称，供关系配置日志使用。
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
     * 将池 ID 追加到结果（跳过空值与重复）。
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
     * 将映射对追加到结果，返回实际新增组数。
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
     * 写 INFO 过程日志并同步控制台。
     */
    private void infoDetail(TaskDetailLog detail, String line) {
        log.info(line);
        if (detail != null) {
            detail.line("INFO", line);
        }
    }
}
