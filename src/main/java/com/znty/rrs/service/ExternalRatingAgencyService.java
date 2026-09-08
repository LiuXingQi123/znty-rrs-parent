package com.znty.rrs.service;

import com.znty.rrs.exception.BizException;
import com.znty.rrs.mapper.ExternalRatingAgencyMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/** 外部评级机构配置服务。 */
@Service
public class ExternalRatingAgencyService {

    /** 外部评级机构配置数据访问组件。 */
    @Resource
    private ExternalRatingAgencyMapper externalRatingAgencyMapper;

    /**
     * 查询有效外部评级机构编码。
     *
     * @return 有效机构编码；无配置时返回空列表
     */
    public List<String> queryActiveAgencyCodeList() {
        List<String> agencyCodes = externalRatingAgencyMapper.queryActiveAgencyCodeList();
        return agencyCodes == null ? Collections.<String>emptyList() : agencyCodes;
    }

    /**
     * 查询业务必需的有效外部评级机构编码。
     *
     * @return 有效机构编码
     * @throws BizException 未配置有效机构时抛出
     */
    public List<String> queryRequiredAgencyCodeList() {
        // 外评调库与黑名单校验必须有明确口径，空配置时阻断处理
        List<String> agencyCodes = queryActiveAgencyCodeList();
        if (agencyCodes.isEmpty()) {
            throw new BizException("未配置有效外部评级机构");
        }
        return agencyCodes;
    }
}
