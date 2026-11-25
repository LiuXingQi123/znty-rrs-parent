package com.znty.rrs.common;

import lombok.Data;

/**
 * 分页请求基类
 *
 * 子类在通用分页参数上追加业务筛选字段，查询时通过读取方法取得可用的页码和每页条数
 */
@Data
public class PageRequest {

    /** 查询页码，从 1 开始，默认第 1 页 */
    private Integer pageIndex = 1;

    /** 请求的每页条数，默认 20 条 */
    private Integer pageSize = 20;

    /**
     * 取得用于分页查询的页码
     *
     * @return 页码为 null 或小于 1 时返回 1，其余值原样返回
     */
    public int getPageIndex() {
        return pageIndex == null || pageIndex < 1 ? 1 : pageIndex;
    }

    /**
     * 取得用于查询的每页条数，限制单次查询的数据量
     *
     * 执行顺序：
     * 1. 条数为 null 或小于 1 时使用默认值 20
     * 2. 超过 4000 时按 4000 条查询，其余值原样返回
     *
     * @return 处理后的每页条数，最大为 4000
     */
    public int getPageSize() {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 4000);
    }
}
