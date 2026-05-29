package com.znty.sirm.model;

import lombok.Data;

import java.util.List;

/**
 * 调库提交返回对象
 */
@Data
public class AdjustSubmitDto {

    /** 债券代码 */
    private String bondCode;

    /** 已提交的调库项数量 */
    private Integer submitCount;

    /** 生成的调库记录 ID 列表 */
    private List<Long> logIds;
}
