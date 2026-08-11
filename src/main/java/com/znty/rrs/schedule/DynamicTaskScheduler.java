package com.znty.rrs.schedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 基于 {@link ThreadPoolTaskScheduler} 的动态 cron 调度器
 * <p>
 * 支持运行时按 taskCode 挂载 / 取消任务，供页面修改 cron 或启停后即时生效。
 * </p>
 */
@Slf4j
@Component
public class DynamicTaskScheduler {

    /** Spring 线程池调度器 */
    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
    /** taskCode → 已挂载 Future */
    private final Map<String, ScheduledFuture<?>> futureMap = new ConcurrentHashMap<>();

    /**
     * 初始化调度线程池（池大小 4，取消时移除任务）
     */
    @PostConstruct
    public void init() {
        taskScheduler.setPoolSize(4);
        taskScheduler.setThreadNamePrefix("rrs-scheduled-");
        taskScheduler.setRemoveOnCancelPolicy(true);
        taskScheduler.initialize();
        log.info("动态定时任务调度器已启动");
    }

    /**
     * 应用关闭时取消全部已挂载任务并关闭调度线程池
     */
    @PreDestroy
    public void destroy() {
        for (String code : futureMap.keySet()) {
            // 逐个取消已挂载任务
            cancel(code);
        }
        taskScheduler.shutdown();
    }

    /**
     * 挂载或刷新指定任务的 cron 调度；入参不完整时仅取消不挂载
     */
    public synchronized void schedule(String taskCode, String cron, Runnable runnable) {
        cancel(taskCode);
        if (!StringUtils.hasText(taskCode) || !StringUtils.hasText(cron) || runnable == null) {
            return;
        }
        try {
            CronTrigger trigger = new CronTrigger(cron.trim(), TimeZone.getDefault());
            ScheduledFuture<?> future = taskScheduler.schedule(runnable, trigger);
            if (future != null) {
                futureMap.put(taskCode, future);
                log.info("已挂载定时任务 {} -> {}", taskCode, cron);
            }
        } catch (Exception e) {
            log.error("挂载定时任务失败: code={}, cron={}", taskCode, cron, e);
            throw e;
        }
    }

    /**
     * 取消指定任务编码上已挂载的调度（不中断正在执行的线程）
     */
    public synchronized void cancel(String taskCode) {
        if (!StringUtils.hasText(taskCode)) {
            return;
        }
        ScheduledFuture<?> future = futureMap.remove(taskCode);
        if (future != null) {
            future.cancel(false);
            log.info("已取消定时任务 {}", taskCode);
        }
    }

    /**
     * 判断指定任务是否已挂载且尚未取消
     */
    public boolean isScheduled(String taskCode) {
        ScheduledFuture<?> future = futureMap.get(taskCode);
        return future != null && !future.isCancelled();
    }

    /**
     * 校验 cron 表达式是否合法（Spring 6 位格式）
     */
    public boolean isValidCron(String cron) {
        if (!StringUtils.hasText(cron)) {
            return false;
        }
        try {
            new CronTrigger(cron.trim(), TimeZone.getDefault());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
