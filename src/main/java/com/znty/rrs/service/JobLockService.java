package com.znty.rrs.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 用 MySQL 命名锁协调多个实例执行同一定时任务
 *
 * {@link ScheduledTaskService} 在执行定时任务前按任务编码申请锁，锁已被占用时跳过本次执行
 * 获取与释放锁使用同一条 JDBC 连接，连接断开时 MySQL 自动释放这条连接持有的命名锁
 */
@Slf4j
@Service
public class JobLockService {

    /** MySQL 命名锁名称最大字符数 */
    private static final int MYSQL_LOCK_NAME_MAX_LENGTH = 64;

    /** 数据库连接池 */
    @Resource
    private DataSource dataSource;

    /**
     * 在 MySQL 命名锁保护下执行任务
     *
     * 执行顺序：
     * 1. 校验锁执行入参
     *
     * @param lockName 锁名称
     * @param waitSeconds 获取锁的最长等待秒数，0 表示立即返回
     * @param task 获取锁成功后执行的任务
     * @return true=已获取锁并执行任务，false=锁正由其他实例持有
     */
    public boolean executeWithLock(String lockName, int waitSeconds, Runnable task) {
        // 校验锁执行入参
        validateParams(lockName, waitSeconds, task);
        try (Connection connection = dataSource.getConnection()) {
            // 使用当前连接获取命名锁，后续释放也必须复用该连接
            if (!acquireLock(connection, lockName, waitSeconds)) {
                log.info("定时任务锁[{}]正由其他实例持有，本次跳过", lockName);
                return false;
            }
            Throwable taskFailure = null;
            try {
                task.run();
                return true;
            } catch (RuntimeException | Error e) {
                taskFailure = e;
                throw e;
            } finally {
                // 任务结束或异常后释放当前连接持有的命名锁
                try {
                    // 在指定连接上释放 MySQL 命名锁
                    releaseLock(connection, lockName);
                } catch (SQLException e) {
                    log.error("定时任务锁[{}]释放异常", lockName, e);
                    if (taskFailure != null) {
                        taskFailure.addSuppressed(e);
                    } else {
                        throw new IllegalStateException("定时任务分布式锁释放失败", e);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("定时任务锁[{}]获取或释放异常", lockName, e);
            throw new IllegalStateException("定时任务分布式锁操作失败", e);
        }
    }

    /**
     * 校验锁执行入参
     *
     * 执行顺序：
     * 1. 检查锁名非空且不超过 MySQL 长度限制
     * 2. 确认等待秒数非负且提供了待执行任务
     *
     * @param lockName 锁名称
     * @param waitSeconds 获取锁的最长等待秒数
     * @param task 待执行任务
     */
    private void validateParams(String lockName, int waitSeconds, Runnable task) {
        if (!StringUtils.hasText(lockName)) {
            throw new IllegalArgumentException("锁名称不能为空");
        }
        if (lockName.length() > MYSQL_LOCK_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("锁名称不能超过 " + MYSQL_LOCK_NAME_MAX_LENGTH + " 个字符");
        }
        if (waitSeconds < 0) {
            throw new IllegalArgumentException("锁等待秒数不能小于 0");
        }
        if (task == null) {
            throw new IllegalArgumentException("锁保护任务不能为空");
        }
    }

    /**
     * 在指定连接上获取 MySQL 命名锁
     *
     * 执行顺序：
     * 1. 用锁名称和等待秒数执行 GET_LOCK
     * 2. 读取锁状态，无结果或返回 SQL null 时报告获取失败
     *
     * @param connection 当前 JDBC 连接
     * @param lockName 锁名称
     * @param waitSeconds 获取锁的最长等待秒数
     * @return true=获取成功，false=锁被占用
     * @throws SQLException 数据库访问异常
     */
    private boolean acquireLock(Connection connection, String lockName, int waitSeconds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, lockName);
            statement.setInt(2, waitSeconds);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("GET_LOCK 未返回结果");
                }
                int result = resultSet.getInt(1);
                if (resultSet.wasNull()) {
                    throw new SQLException("GET_LOCK 返回 NULL");
                }
                return result == 1;
            }
        }
    }

    /**
     * 在指定连接上释放 MySQL 命名锁
     *
     * 执行顺序：
     * 1. 用锁名称执行 RELEASE_LOCK
     * 2. 检查释放结果，只有返回 1 才算释放成功
     *
     * @param connection 当前 JDBC 连接
     * @param lockName 锁名称
     * @throws SQLException 数据库访问异常
     */
    private void releaseLock(Connection connection, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, lockName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("RELEASE_LOCK 未返回结果");
                }
                int result = resultSet.getInt(1);
                if (resultSet.wasNull() || result != 1) {
                    throw new SQLException("RELEASE_LOCK 未成功释放锁");
                }
            }
        }
    }
}
