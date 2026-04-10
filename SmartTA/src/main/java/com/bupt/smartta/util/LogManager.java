
package com.bupt.smartta.util;

import com.bupt.smartta.model.SystemLog;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 增强型日志管理器，提供分级日志记录、过滤查询和异步写入功能。
 */
public class LogManager {
    private static LogManager instance;
    private static final Object initLock = new Object();

    // 日志级别常量
    public static final String LEVEL_DEBUG = "DEBUG";
    public static final String LEVEL_INFO = "INFO";
    public static final String LEVEL_WARN = "WARN";
    public static final String LEVEL_ERROR = "ERROR";

    // 日志级别优先级
    private static final Map<String, Integer> LEVEL_PRIORITY = Map.of(
        LEVEL_DEBUG, 0,
        LEVEL_INFO, 1,
        LEVEL_WARN, 2,
        LEVEL_ERROR, 3
    );

    // 内存中的日志缓存
    private final ConcurrentLinkedQueue<SystemLog> logCache = new ConcurrentLinkedQueue<>();

    // 最大缓存日志数
    private static final int MAX_CACHE_SIZE = 1000;

    // 当前日志级别
    private String currentLogLevel = LEVEL_INFO;

    // 异步写入执行器
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // 数据存储引用
    private final DataStore dataStore;

    // 时间格式化器
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LogManager(DataStore dataStore) {
        this.dataStore = dataStore;
        // 每5秒执行一次批量写入
        executor.scheduleAtFixedRate(this::flushLogs, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * 获取日志管理器单例
     */
    public static LogManager getInstance() {
        if (instance == null) {
            synchronized (initLock) {
                if (instance == null) {
                    instance = new LogManager(DataStore.getInstance());
                }
            }
        }
        return instance;
    }

    /**
     * 设置日志级别
     */
    public void setLogLevel(String level) {
        if (LEVEL_PRIORITY.containsKey(level)) {
            this.currentLogLevel = level;
        }
    }

    /**
     * 获取当前日志级别
     */
    public String getLogLevel() {
        return currentLogLevel;
    }

    /**
     * 记录DEBUG级别日志
     */
    public void debug(String operation, String fileName, String detail) {
        log(LEVEL_DEBUG, operation, fileName, detail);
    }

    /**
     * 记录INFO级别日志
     */
    public void info(String operation, String fileName, String detail) {
        log(LEVEL_INFO, operation, fileName, detail);
    }

    /**
     * 记录WARN级别日志
     */
    public void warn(String operation, String fileName, String detail) {
        log(LEVEL_WARN, operation, fileName, detail);
    }

    /**
     * 记录ERROR级别日志
     */
    public void error(String operation, String fileName, String detail) {
        log(LEVEL_ERROR, operation, fileName, detail);
    }

    /**
     * 记录日志（内部方法）
     */
    private void log(String level, String operation, String fileName, String detail) {
        // 检查日志级别
        if (LEVEL_PRIORITY.get(level) < LEVEL_PRIORITY.get(currentLogLevel)) {
            return;
        }

        // 创建日志对象
        SystemLog log = new SystemLog();
        log.setTimestamp(LocalDateTime.now().format(TIME_FORMATTER));
        log.setOperation(operation);
        log.setFileName(fileName);
        log.setStatus(level); // 使用日志级别作为状态
        log.setDetail(detail);

        // 添加到缓存
        logCache.add(log);

        // 如果缓存超过最大值，立即刷新
        if (logCache.size() >= MAX_CACHE_SIZE) {
            flushLogs();
        }
    }

    /**
     * 刷新日志到持久化存储
     */
    private synchronized void flushLogs() {
        if (logCache.isEmpty()) {
            return;
        }

        List<SystemLog> logsToFlush = new ArrayList<>();
        while (!logCache.isEmpty()) {
            SystemLog log = logCache.poll();
            if (log != null) {
                logsToFlush.add(log);
            }
        }

        if (!logsToFlush.isEmpty()) {
            // 批量添加到DataStore
            for (SystemLog log : logsToFlush) {
                dataStore.addLog(log);
            }
        }
    }

    /**
     * 关闭日志管理器，释放资源
     */
    public void shutdown() {
        flushLogs();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 查询日志
     * @param filter 日志过滤条件
     * @return 符合条件的日志列表
     */
    public List<SystemLog> queryLogs(Predicate<SystemLog> filter) {
        return dataStore.getLogs().stream()
            .filter(filter)
            .collect(Collectors.toList());
    }

    /**
     * 按时间范围查询日志
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 符合条件的日志列表
     */
    public List<SystemLog> queryLogsByTimeRange(String startTime, String endTime) {
        return queryLogs(log -> {
            String timestamp = log.getTimestamp();
            return timestamp.compareTo(startTime) >= 0 && timestamp.compareTo(endTime) <= 0;
        });
    }

    /**
     * 按日志级别查询日志
     * @param level 日志级别
     * @return 符合条件的日志列表
     */
    public List<SystemLog> queryLogsByLevel(String level) {
        return queryLogs(log -> level.equals(log.getStatus()));
    }

    /**
     * 按操作类型查询日志
     * @param operation 操作类型
     * @return 符合条件的日志列表
     */
    public List<SystemLog> queryLogsByOperation(String operation) {
        return queryLogs(log -> operation.equals(log.getOperation()));
    }

    /**
     * 按文件名查询日志
     * @param fileName 文件名
     * @return 符合条件的日志列表
     */
    public List<SystemLog> queryLogsByFileName(String fileName) {
        return queryLogs(log -> fileName.equals(log.getFileName()));
    }

    /**
     * 获取最近的日志
     * @param limit 日志数量限制
     * @return 最近的日志列表
     */
    public List<SystemLog> getRecentLogs(int limit) {
        List<SystemLog> allLogs = dataStore.getLogs();
        int actualLimit = Math.min(limit, allLogs.size());
        return new ArrayList<>(allLogs.subList(0, actualLimit));
    }

    /**
     * 获取日志统计信息
     * @return 日志统计信息
     */
    public Map<String, Object> getLogStatistics() {
        List<SystemLog> allLogs = dataStore.getLogs();
        Map<String, Integer> levelCount = new HashMap<>();
        Map<String, Integer> operationCount = new HashMap<>();

        for (SystemLog log : allLogs) {
            // 统计日志级别
            String level = log.getStatus();
            levelCount.put(level, levelCount.getOrDefault(level, 0) + 1);

            // 统计操作类型
            String operation = log.getOperation();
            operationCount.put(operation, operationCount.getOrDefault(operation, 0) + 1);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalLogs", allLogs.size());
        stats.put("levelCount", levelCount);
        stats.put("operationCount", operationCount);

        return stats;
    }
}
