package com.znty.rrs.mapper;

import com.znty.rrs.entity.mymatters.BusinessDomainDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 查询用户与角色对应的页面业务入口 */
@Mapper
public interface BusinessPermissionMapper {
    /** 查询固定用户及其启用的直属角色可显示的业务入口 */
    List<BusinessDomainDto> queryBusinessDomainList(@Param("userId") Long userId);

}
