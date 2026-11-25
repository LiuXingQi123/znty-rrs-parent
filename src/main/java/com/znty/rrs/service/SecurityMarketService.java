package com.znty.rrs.service;

import com.znty.rrs.common.enums.MarketCode;
import com.znty.rrs.common.util.MarketCodeMatchUtil;
import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.common.SecurityMarketDto;
import com.znty.rrs.entity.common.SecurityMarketMappingDto;
import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.SecurityMarketMapper;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 为校验、详情、导入和导出提供统一的证券市场查询
 *
 * 支持按证券代码批量查询所属市场及同券跨市场代码，供页面展示和批量调库使用
 * 判断是否符合目标池市场配置时使用本条记录的市场，跨市场关联项各自检查
 */
@Service
public class SecurityMarketService {
    /** 限制单次派生表大小，批量导出按段查询 */
    private static final int QUERY_BATCH_SIZE = 500;

    /** 证券市场映射访问组件 */
    @Resource
    private SecurityMarketMapper securityMarketMapper;

    /**
     * 查询当前代码的唯一市场；缺失、重复或不支持均报出代码
     *
     * 执行顺序：
     * 1. 查询当前代码的唯一市场
     *
     * @param windCode Wind 代码
     * @return 市场代码
     */
    public String queryMarketCode(String windCode) {
        return queryMarketCodeMap(Collections.singletonList(windCode)).get(windCode);
    }

    /**
     * 批量查询当前代码市场，避免列表与导出逐行查询
     *
     * 执行顺序：
     * 1. 逐项查询市场映射列表
     *
     * @param windCodes Wind代码列表
     * @return 市场代码索引
     */
    public Map<String, String> queryMarketCodeMap(List<String> windCodes) {
        Map<String, String> result = new LinkedHashMap<>();
        if (windCodes == null || windCodes.isEmpty()) {
            return result;
        }
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(windCodes));
        for (String code : unique) {
            if (code == null || code.trim().isEmpty()) {
                throw new BizException("证券市场查询代码不能为空");
            }
        }
        for (int offset = 0; offset < unique.size(); offset += QUERY_BATCH_SIZE) {
            List<String> batch = unique.subList(offset, Math.min(offset + QUERY_BATCH_SIZE, unique.size()));
            for (SecurityMarketMappingDto mapping : securityMarketMapper.queryMarketMappingList(batch)) {
                String code = mapping.getWindCode();
                if (result.containsKey(code)) {
                    throw new BizException("证券市场映射重复：" + code);
                }
                // 复用批次校验使用的市场有效性判断
                result.put(code, requireMarketCode(code, Collections.singletonList(mapping)));
            }
        }
        for (String code : unique) {
            if (!result.containsKey(code)) {
                throw new BizException("证券缺少市场映射：" + code);
            }
        }
        return result;
    }

    /**
     * 验证指定证券只有一条受支持的市场映射
     *
     * 执行顺序：
     * 1. 确认证券恰好存在一条市场映射，缺失或重复时报错
     * 2. 去掉市场编码首尾空白，拒绝空值、未知市场和主体市场
     *
     * @param code 证券代码
     * @param records 记录列表
     * @return 市场代码
     */
    static String requireMarketCode(String code, List<SecurityMarketMappingDto> records) {
        if (records.isEmpty()) {
            throw new BizException("证券缺少市场映射：" + code);
        }
        if (records.size() > 1) {
            throw new BizException("证券市场映射重复：" + code);
        }
        String raw = records.get(0).getRawMarketCode();
        if (raw == null || raw.trim().isEmpty()) {
            throw new BizException("证券缺少市场映射：" + code);
        }
        String market = raw.trim();
        if (!MarketCode.isValid(market) || MarketCode.UNKNOWN.getCode().equals(market)
                || MarketCode.COMPANY.getCode().equals(market)) {
            throw new BizException("证券市场不受支持：" + code + "，市场=" + raw);
        }
        return market;
    }

    /**
     * 按共用市场集合读取同券代码；范围外代码及临时代码不进入 Wind 同券关联
     *
     * 执行顺序：
     * 1. 查询当前代码的唯一市场
     * 2. 批量查询当前代码市场，避免列表与导出逐行查询
     *
     * @param windCode Wind 代码
     * @return 证券市场列表
     */
    public List<SecurityMarketDto> queryRelatedMarketCodes(String windCode) {
        queryMarketCode(windCode);
        List<String> codes = securityMarketMapper.queryRelatedWindCodeList(windCode, MarketCode.queryCodeList());
        Map<String, String> markets = queryMarketCodeMap(codes);
        List<SecurityMarketDto> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : markets.entrySet()) {
            SecurityMarketDto dto = new SecurityMarketDto();
            dto.setWindCode(entry.getKey());
            dto.setMarketCode(entry.getValue());
            result.add(dto);
        }
        return result;
    }

    /**
     * 调库仅校验当前记录所属市场，关联记录由扩批流程独立校验
     *
     * 执行顺序：
     * 1. 没有证券记录时按不匹配处理
     * 2. 主体使用主体市场代码，其他证券查询实际市场，再与目标池配置比对
     *
     * @param marketCodesJson 市场代码列表JSON
     * @param security 本次证券
     * @return true=当前记录的市场符合目标池要求，false=当前记录的市场不符合目标池要求
     */
    public boolean matchesPoolMarkets(String marketCodesJson, SecurityInfoBo security) {
        if (security == null) {
            return false;
        }
        String market = "company".equals(security.getSecurityType()) ? MarketCode.COMPANY.getCode()
                : queryMarketCode(security.getWindCode());
        return MarketCodeMatchUtil.matchesPoolMarkets(marketCodesJson, market);
    }
}
