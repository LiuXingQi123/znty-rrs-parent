package com.znty.sirm.common;

import lombok.Data;

/**
 * 分页请求基类，子类继承后追加业务字段
 */
@Data
public class PageRequest {
    /** 页码，从 1 开始 */
    private Integer pageIndex = 1;
    /** 每页条数 */
    private Integer pageSize = 20;

    /** 获取合法页码。 */
    public int getPageIndex() {
        return pageIndex == null || pageIndex < 1 ? 1 : pageIndex;
    }

    /** 获取合法每页条数。 */
    public int getPageSize() {
        if (pageSize == null || pageSize < 1) return 20;
        return Math.min(pageSize, 100);
    }

    /** 获取 SQL 偏移量，用于分页查询。 */
    public int offset() {
        return (getPageIndex() - 1) * getPageSize();
    }
}
