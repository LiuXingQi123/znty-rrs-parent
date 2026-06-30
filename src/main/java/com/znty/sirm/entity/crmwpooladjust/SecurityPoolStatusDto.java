package com.znty.sirm.entity.crmwpooladjust;


import lombok.Data;

import java.util.List;

/**
 * 证券池状态返回对象（当前所在池）
 */
@Data
public class SecurityPoolStatusDto {
    /** 当前证券所在池列表 */
    private List<PoolStatusDto> securityCurrentPools;
    /** 当前证券主体所在池列表 */
    private List<PoolStatusDto> issuerCurrentPools;
}
