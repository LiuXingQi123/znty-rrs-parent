package com.znty.rrs.service;

import com.znty.rrs.entity.schedule.ScheduledReminderMessageDto;
import lombok.Getter;
import org.springframework.stereotype.Service;

/**
 * 定时提醒统一投递边界。
 *
 * <p>当前系统尚无可用的邮件或站内信发送服务，本期明确不执行外部发送。
 * 后续接入通知基础设施时只需替换 {@link #send(ScheduledReminderMessageDto)} 的实现。</p>
 */
@Service
public class ScheduledReminderDeliveryService {

    /**
     * 投递定时提醒消息。
     *
     * @param message 已完成业务组装的提醒消息
     * @return 投递结果
     */
    public DeliveryResult send(ScheduledReminderMessageDto message) {
        return DeliveryResult.pending("通知通道未接入，本轮仅生成提醒候选");
    }

    /** 通知投递结果。 */
    @Getter
    public static class DeliveryResult {

        /** 是否已真实投递 */
        private final boolean delivered;

        /** 投递结果说明 */
        private final String message;

        /** 创建投递结果。 */
        private DeliveryResult(boolean delivered, String message) {
            this.delivered = delivered;
            this.message = message;
        }

        /** 创建真实投递成功结果，供后续通知实现使用。 */
        public static DeliveryResult delivered(String message) {
            return new DeliveryResult(true, message);
        }

        /** 创建暂未投递结果。 */
        public static DeliveryResult pending(String message) {
            return new DeliveryResult(false, message);
        }
    }
}
