package com.znty.rrs.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一分页查询结果
 *
 * 返回当前页记录和分页信息，供前端渲染列表及分页控件
 *
 * @param <T> 当前页单条记录的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 本次查询返回的当前页记录列表 */
    private List<T> records;

    /** 满足条件的总记录数，用于前端计算总页数 */
    private long total;

    /** 当前查询页码，从 1 开始 */
    private int pageIndex;

    /** 本次分页查询使用的每页条数 */
    private int pageSize;
}
