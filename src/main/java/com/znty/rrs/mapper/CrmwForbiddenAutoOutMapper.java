package com.znty.rrs.mapper;

import com.znty.rrs.entity.bo.SecurityInfoBo;
import com.znty.rrs.entity.schedule.CrmwForbiddenOutCandidateDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * CRMW关联禁投自动出库的在库扫描和状态更新
 */
@Mapper
public interface CrmwForbiddenAutoOutMapper {

    /**
     * 一次查询全部有效CRMW在库组合，不排序
     *
     * @return 符合条件的CRMW 在池组合候选列表
     */
    List<CrmwForbiddenOutCandidateDto> queryCrmwInPoolList();

    /**
     * 批量查询本轮标的及凭证的主档身份和发行主体
     *
     * @param securityCodes 证券代码列表
     * @return 符合条件的证券主档信息列表
     */
    List<SecurityInfoBo> querySecurityIssuerList(@Param("securityCodes") List<String> securityCodes);

    /**
     * 批量查询本轮证券及主体在有效禁止库中的生效代码
     *
     * @param codes 本批待检查的证券与主体代码
     * @return 在有效禁投池中有生效记录的证券或主体代码列表
     */
    List<String> queryForbiddenCodeList(@Param("codes") List<String> codes);

    /**
     * 仅软删除本次扫描且仍然有效的原组合池状态
     *
     * @param candidate 待更新的 CRMW 在池组合
     * @return 受影响的行数
     */
    int editCrmwPoolStatusOut(CrmwForbiddenOutCandidateDto candidate);
}
