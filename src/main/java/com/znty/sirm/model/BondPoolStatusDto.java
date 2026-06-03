package com.znty.sirm.model;

import lombok.Data;

import java.util.List;

/**
 * 债券池状态返回对象（当前所在池）
 */
@Data
public class BondPoolStatusDto {
    /** 当前债券所在池列表 */
    private List<PoolStatusDto> bondCurrentPools;
    /** 当前债券主体所在池列表 */
    private List<PoolStatusDto> issuerCurrentPools;
}
