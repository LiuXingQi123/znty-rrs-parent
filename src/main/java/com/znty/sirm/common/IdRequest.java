package com.znty.sirm.common;

import lombok.Data;

/**
 * 通用单 ID 请求体，用于仅需传入主键的接口（如查详情、删除等场景）。
 */
@Data
public class IdRequest {
    private Long id; // 目标记录的主键 ID
}
