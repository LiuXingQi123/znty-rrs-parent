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

/** 证券市场权威查询，统一校验、详情、导入与导出的口径。 */
@Service
public class SecurityMarketService {
    /** 限制单次派生表大小，批量导出按段查询。 */
    private static final int QUERY_BATCH_SIZE = 500;
    /** 证券市场映射访问组件。 */
    @Resource
    private SecurityMarketMapper securityMarketMapper;

    /** 查询当前代码的唯一市场；缺失、重复或不支持均报出代码。 */
    public String queryMarketCode(String windCode) {
        return queryMarketCodeMap(Collections.singletonList(windCode)).get(windCode);
    }

    /** 批量查询当前代码市场，避免列表与导出逐行查询。 */
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
                String raw = mapping.getRawMarketCode();
                if (raw == null || raw.trim().isEmpty()) {
                    throw new BizException("证券缺少市场映射：" + code);
                }
                String market = raw.trim();
                if (!"temporary".equals(mapping.getSecuritySource()) && "IB".equals(market)) {
                    market = MarketCode.CIBM.getCode();
                }
                if (!MarketCode.isValid(market) || MarketCode.UNKNOWN.getCode().equals(market)
                        || MarketCode.COMPANY.getCode().equals(market)) {
                    throw new BizException("证券市场不受支持：" + code + "，市场=" + raw);
                }
                result.put(code, market);
            }
        }
        for (String code : unique) {
            if (!result.containsKey(code)) {
                throw new BizException("证券缺少市场映射：" + code);
            }
        }
        return result;
    }

    /** 按当前代码读取其他市场代码；临时代码不进入 Wind 同券关联。 */
    public List<SecurityMarketDto> queryRelatedMarketCodes(String windCode) {
        queryMarketCode(windCode);
        List<String> codes = securityMarketMapper.queryRelatedWindCodeList(windCode);
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

    /** 调库仅校验当前记录所属市场，关联记录由扩批流程独立校验。 */
    public boolean matchesPoolMarkets(String marketCodesJson, SecurityInfoBo security) {
        if (security == null) {
            return false;
        }
        String market = "company".equals(security.getSecurityType()) ? MarketCode.COMPANY.getCode()
                : queryMarketCode(security.getWindCode());
        return MarketCodeMatchUtil.matchesPoolMarkets(marketCodesJson, market);
    }
}
