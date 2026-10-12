package com.znty.rrs.entity.graderulealert;

import com.znty.rrs.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 不符合分级规则提醒查询 / 处理入参
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GradeRuleAlertReq extends PageRequest {

    /** 待办主键 */
    private Long id;

    /** 证券代码（模糊） */
    private String securityCode;

    /** 待办状态：00=待处理 / 20=已处理 / 99=已失效 */
    private String alertStatus;

    /** 处理人 ID */
    private String currentUserId;

    /** 处理人名称 */
    private String currentUserName;
}
