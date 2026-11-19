package com.znty.rrs.common;

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

    /** 获取合法每页条数，最多 4000 条，防止单次查询数据量过大 */
    public int getPageSize() {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 4000);
    }
}
