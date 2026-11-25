package com.znty.rrs.service;

import com.znty.rrs.entity.schedule.ScheduledReminderMessageDto;
import lombok.Getter;
import org.springframework.stereotype.Service;

/**
 * 接收定时任务生成的提醒并返回投递结果
 *
 * 债券主体不在池和质押黑名单库增量任务共用 {@link #send(ScheduledReminderMessageDto)}
 * 当前邮件和站内信通道均未接入，调用方收到待投递结果后继续记录本轮提醒明细
 */
@Service
public class ScheduledReminderDeliveryService {

    /**
     * 投递定时提醒消息
     *
     * 执行顺序：
     * 1. 通知通道未接入，直接返回待投递结果并保留原因
     *
     * @param message 已完成业务组装的提醒消息
     * @return 投递结果
     */
    public DeliveryResult send(ScheduledReminderMessageDto message) {
        return DeliveryResult.pending("通知通道未接入，本轮仅生成提醒候选");
    }

    /**
     * 保存一次定时提醒的投递结果
     *
     * 记录提醒是否已实际投递以及结果说明，供调用方记录本次发送情况
     */
    @Getter
    public static class DeliveryResult {

        /** 是否已真实投递 */
        private final boolean delivered;

        /** 投递结果说明 */
        private final String message;

        /**
         * 记录定时提醒的投递结果
         *
         * 执行顺序：
         * 1. 保存是否投递成功及对应的结果说明
         *
         * @param delivered 投递结果，true=投递成功 / false=未投递成功
         * @param message 投递结果说明
         */
        private DeliveryResult(boolean delivered, String message) {
            this.delivered = delivered;
            this.message = message;
        }

        /**
         * 创建通知已经投递的结果
         *
         * 执行顺序：
         * 1. 保存调用方给出的说明，将结果标记为已送达
         *
         * @param message 记录投递结果时使用的说明
         * @return 已投递的通知结果
         */
        public static DeliveryResult delivered(String message) {
            return new DeliveryResult(true, message);
        }

        /**
         * 创建通知暂未投递的结果
         *
         * 执行顺序：
         * 1. 保存未投递原因，将结果标记为未送达
         *
         * @param message 记录投递结果时使用的说明
         * @return 带原因的未投递结果
         */
        public static DeliveryResult pending(String message) {
            return new DeliveryResult(false, message);
        }
    }
}
