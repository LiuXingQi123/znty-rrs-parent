package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.schedule.CrmwForbiddenOutCandidateDto;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** CRMW关联禁投自动出库的在库扫描和状态更新 */
public interface CrmwForbiddenAutoOutMapper {

    /** 一次查询全部有效CRMW在库组合，不排序 */
    List<CrmwForbiddenOutCandidateDto> queryCrmwInPoolList();

    /** 批量查询本轮标的及凭证的主档身份和发行主体 */
    List<SecurityInfoBo> querySecurityIssuerList(@Param("securityCodes") List<String> securityCodes);

    /** 批量查询本轮证券及主体在有效禁止库中的生效代码 */
    List<String> queryForbiddenCodeList(@Param("codes") List<String> codes);

    /** 仅软删除本次扫描且仍然有效的原组合池状态 */
    int editCrmwPoolStatusOut(CrmwForbiddenOutCandidateDto candidate);
}
