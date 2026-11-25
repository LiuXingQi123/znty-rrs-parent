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
 * 按任务编码挂载、刷新和取消 Cron 调度
 *
 * 使用 {@link ThreadPoolTaskScheduler} 在 Cron 指定的时间触发任务
 * 页面修改 Cron 或启停任务后，可以立即调整对应任务的调度
 * 不同任务之间没有固定执行顺序，有依赖的任务需要通过 Cron 错开
 */
@Slf4j
@Component
public class DynamicTaskScheduler {

    /** 按 Cron 触发任务的 Spring 线程池调度器 */
    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();

    /** 按任务编码保存当前调度句柄，用于取消或查询挂载状态 */
    private final Map<String, ScheduledFuture<?>> futureMap = new ConcurrentHashMap<>();

    /**
     * 应用启动时初始化调度线程池
     *
     * 执行顺序：
     * 1. 配置线程数为 4，设置线程名前缀，并开启取消后从队列移除任务
     * 2. 初始化线程池，并记录启动日志
     */
    @PostConstruct
    public void init() {
        // 1. 设置调度线程和取消策略
        taskScheduler.setPoolSize(4);
        taskScheduler.setThreadNamePrefix("rrs-scheduled-");
        taskScheduler.setRemoveOnCancelPolicy(true);
        // 2. 启动线程池
        taskScheduler.initialize();
        log.info("动态定时任务调度器已启动");
    }

    /**
     * 应用关闭时释放调度资源
     *
     * 执行顺序：
     * 1. 按任务编码逐个取消已挂载的调度
     * 2. 关闭调度线程池
     */
    @PreDestroy
    public void destroy() {
        // 1. 逐个取消已挂载的调度
        for (String code : futureMap.keySet()) {
            cancel(code);
        }
        // 2. 关闭调度线程池
        taskScheduler.shutdown();
    }

    /**
     * 按任务编码挂载或刷新 Cron 调度
     *
     * 执行顺序：
     * 1. 取消该任务原有的调度
     * 2. 任务编码或 Cron 为空白，或执行动作缺失时，结束处理
     * 3. 去掉 Cron 首尾空白，按系统默认时区创建触发器并挂载调度，取得调度句柄时保存
     * 4. 挂载过程抛出异常时，记录失败原因并将异常抛给调用方
     *
     * @param taskCode 任务编码
     * @param cron Spring 六段式 Cron 表达式
     * @param runnable 到达触发时间时执行的动作
     */
    public synchronized void schedule(String taskCode, String cron, Runnable runnable) {
        // 1. 先取消该任务的旧调度
        cancel(taskCode);
        // 2. 缺少必要信息时结束处理
        if (!StringUtils.hasText(taskCode) || !StringUtils.hasText(cron) || runnable == null) {
            return;
        }
        try {
            // 3. 按系统默认时区挂载调度，并保存调度句柄
            CronTrigger trigger = new CronTrigger(cron.trim(), TimeZone.getDefault());
            ScheduledFuture<?> future = taskScheduler.schedule(runnable, trigger);
            if (future != null) {
                futureMap.put(taskCode, future);
                log.info("已挂载定时任务 {} -> {}", taskCode, cron);
            }
        } catch (Exception e) {
            // 4. 记录挂载失败原因，并将异常抛给调用方
            log.error("挂载定时任务失败: code={}, cron={}", taskCode, cron, e);
            throw e;
        }
    }

    /**
     * 取消指定任务的后续调度，正在执行的任务继续运行
     *
     * 执行顺序：
     * 1. 任务编码为空白时结束处理
     * 2. 按编码移除调度句柄，有句柄时取消调度并记录日志
     *
     * @param taskCode 要取消调度的任务编码
     */
    public synchronized void cancel(String taskCode) {
        // 1. 没有任务编码时结束处理
        if (!StringUtils.hasText(taskCode)) {
            return;
        }
        // 2. 取消后续触发，保留已经开始的执行
        ScheduledFuture<?> future = futureMap.remove(taskCode);
        if (future != null) {
            future.cancel(false);
            log.info("已取消定时任务 {}", taskCode);
        }
    }

    /**
     * 查询指定任务的调度挂载状态
     *
     * 执行顺序：
     * 1. 按编码查找调度句柄，并检查句柄是否已取消
     *
     * @param taskCode 要查询的非 null 任务编码
     * @return true=已有调度句柄且尚未取消，false=没有调度句柄或句柄已取消
     */
    public boolean isScheduled(String taskCode) {
        ScheduledFuture<?> future = futureMap.get(taskCode);
        return future != null && !future.isCancelled();
    }

    /**
     * 校验 Spring 六段式 Cron 表达式
     *
     * 执行顺序：
     * 1. 表达式为空白时返回 false
     * 2. 去掉首尾空白并按系统默认时区创建触发器，创建失败时返回 false
     *
     * @param cron 待校验的 Cron 表达式
     * @return true=表达式可用于创建触发器，false=表达式为空白或创建触发器时发生异常
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
