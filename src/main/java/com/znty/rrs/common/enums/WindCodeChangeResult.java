package com.znty.rrs.common.enums;

/**
 * 单条 Wind 代码变更处理结果
 *
 * 定时任务按结果区分换码成功、数据缺失跳过、代码已是最新或重复事件跳过
 */
public enum WindCodeChangeResult {

    /** 本次换码及关联业务转换已完成，计入换码成功条数 */
    UPDATED,

    /**
     * 按 SEC_ID 未找到证券主档时跳过换码
     *
     * 本分支只读取主档并记录过程提示，任务汇总单列「主档缺失跳过」
     */
    MISSING_MASTER,

    /** 主档当前代码已经是最新代码，无需换码，不新增临时代码记录或调库记录 */
    ALREADY_CURRENT,

    /** 已有本事件的成功处理标记，按重复事件跳过，不再次换码或生成业务记录 */
    ALREADY_COMPLETED
}
