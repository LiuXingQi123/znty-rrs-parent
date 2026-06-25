package com.znty.sirm.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页查询统一响应结构，封装当前页数据列表及分页元信息，供前端渲染分页控件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    /** 当前页的数据列表 */
    private List<T> records;
    /** 满足条件的总记录数，用于前端计算总页数 */
    private long total;
    /** 当前页码（从 1 开始） */
    private int pageIndex;
    /** 每页条数 */
    private int pageSize;
}
