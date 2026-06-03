package com.znty.sirm.model;

import com.znty.sirm.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 债券池查询请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BondPoolQueryReq extends PageRequest {
    /** 债券池树选中节点ID列表 */
    private List<Long> poolIds;
    /** 债券代码（模糊搜索） */
    private String bondCode;
    /** 债券类型（精确匹配） */
    private String bondType;
    /** 债券状态：active=存续 / matured=到期 */
    private String bondStatus;
    /** 入池时间起 */
    private String entryTimeStart;
    /** 入池时间止 */
    private String entryTimeEnd;
    /** 调整人（模糊搜索） */
    private String adjusterName;
    /** 发行主体名称（模糊搜索） */
    private String issuer;
    /** 我的债券 */
    private Boolean myBonds;
    /** 当前用户ID（我的债券勾选时使用） */
    private String currentUserId;
}
