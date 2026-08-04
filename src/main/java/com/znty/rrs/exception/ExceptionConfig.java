package com.znty.rrs.exception;

import com.znty.rrs.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;

/**
 * 全局异常处理器。
 *
 * <p>业务异常直接回传业务文案；系统异常回传「场景前缀 + 关键异常类型 + 精简 message」，
 * 便于前端排查。完整堆栈仍只写服务端日志。
 */
@RestControllerAdvice
public class ExceptionConfig {

    private static final Logger log = LoggerFactory.getLogger(ExceptionConfig.class);

    /** 返回前端的 message 最大长度（Element 提示不宜过长） */
    private static final int MAX_CLIENT_MESSAGE_LEN = 600;

    /** cause 链遍历最大深度 */
    private static final int MAX_CAUSE_DEPTH = 10;

    // ─── 业务异常 ─────────────────────────────────────────────

    /** 处理业务异常。 */
    @ExceptionHandler(BizException.class)
    public ApiResponse<?> handleBiz(BizException e) {
        log.warn("业务异常 code={}: {}", e.getCode(), e.getMessage());
        return ApiResponse.fail(safeMessage(e.getMessage(), "业务处理失败"));
    }

    // ─── 请求/参数类 ──────────────────────────────────────────

    /** 请求体 JSON 无法解析。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<?> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ApiResponse.fail(buildClientMessage("请求体解析失败", e));
    }

    /** @RequestBody 参数校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<?> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String detail = firstFieldError(e.getBindingResult());
        log.warn("参数校验失败: {}", detail != null ? detail : e.getMessage());
        return ApiResponse.fail(detail != null ? ("参数校验失败: " + detail) : "参数校验失败");
    }

    /** 表单/查询参数绑定失败。 */
    @ExceptionHandler(BindException.class)
    public ApiResponse<?> handleBind(BindException e) {
        String detail = firstFieldError(e.getBindingResult());
        log.warn("参数绑定失败: {}", detail != null ? detail : e.getMessage());
        return ApiResponse.fail(detail != null ? ("参数绑定失败: " + detail) : "参数绑定失败");
    }

    /** 缺少必填请求参数。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<?> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getMessage());
        return ApiResponse.fail("缺少请求参数: " + e.getParameterName()
                + (e.getParameterType() != null ? (" (" + e.getParameterType() + ")") : ""));
    }

    /** 请求参数类型不匹配。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型错误: {}", e.getMessage());
        String required = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知类型";
        return ApiResponse.fail("参数类型错误: " + e.getName() + " 需要 " + required
                + "，实际值=" + String.valueOf(e.getValue()));
    }

    /** HTTP 方法不支持。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return ApiResponse.fail("请求方法不支持: " + e.getMethod());
    }

    // ─── 数据访问 ─────────────────────────────────────────────

    /**
     * Spring 数据访问异常（含 MyBatis 包装后的 BadSqlGrammar / DataIntegrity 等）。
     * 比裸 SQLException 更常命中。
     */
    @ExceptionHandler(DataAccessException.class)
    public ApiResponse<?> handleDataAccess(DataAccessException e) {
        log.error("数据库访问异常", e);
        return ApiResponse.fail(buildClientMessage("数据库异常", e));
    }

    /** 未再包装的 JDBC SQL 异常。 */
    @ExceptionHandler(SQLException.class)
    public ApiResponse<?> handleSql(SQLException e) {
        log.error("数据库异常", e);
        return ApiResponse.fail(buildClientMessage("数据库异常", e));
    }

    // ─── 兜底 ─────────────────────────────────────────────────

    /** 处理其余系统异常。 */
    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleOther(Exception e) {
        log.error("系统异常", e);
        // MyBatis 顶层常为 PersistenceException（非 DataAccessException 子类时走此分支）
        if (isPersistenceLike(e)) {
            return ApiResponse.fail(buildClientMessage("数据库异常", e));
        }
        return ApiResponse.fail(buildClientMessage("系统异常", e));
    }

    // ─── 文案组装 ─────────────────────────────────────────────

    /**
     * 组装返回给前端的系统错误文案：前缀 + [关键异常类] + 精简 message。
     */
    private String buildClientMessage(String prefix, Throwable e) {
        if (e == null) {
            return prefix != null ? prefix : "未知错误";
        }
        // 展示类型优先 SQL / 数据访问根因，避免只看到包装类名
        Throwable showType = pickDisplayThrowable(e);
        String typeName = showType.getClass().getSimpleName();
        // 文案优先取链上最有信息量的一句，并压缩 MyBatis 长模板
        String detail = compactMessage(pickBestMessage(e));

        StringBuilder sb = new StringBuilder();
        if (prefix != null && !prefix.isEmpty()) {
            sb.append(prefix);
        }
        if (typeName != null && !typeName.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" [").append(typeName).append(']');
            } else {
                sb.append(typeName);
            }
        }
        if (detail != null && !detail.isEmpty()) {
            sb.append(": ").append(detail);
        }
        // SQLException 可附带 SQLState，便于对照错误码
        SQLException sqlEx = findCause(e, SQLException.class);
        if (sqlEx != null && sqlEx.getSQLState() != null && !sqlEx.getSQLState().isEmpty()) {
            String stateHint = " (SQLState=" + sqlEx.getSQLState();
            if (sqlEx.getErrorCode() != 0) {
                stateHint += ", errorCode=" + sqlEx.getErrorCode();
            }
            stateHint += ")";
            if (sb.indexOf("SQLState=") < 0) {
                sb.append(stateHint);
            }
        }
        return truncate(sb.toString(), MAX_CLIENT_MESSAGE_LEN);
    }

    /**
     * 选择用于展示的异常类型：优先 SQLException，其次数据访问/持久化类，否则根因。
     */
    private Throwable pickDisplayThrowable(Throwable e) {
        SQLException sql = findCause(e, SQLException.class);
        if (sql != null) {
            return sql;
        }
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth < MAX_CAUSE_DEPTH) {
            if (cur instanceof DataAccessException || isPersistenceLike(cur)) {
                // 若下一层是 SQLException，上面已返回；此处返回本层更具体的 Spring 数据异常
                return cur;
            }
            if (cur.getCause() == null || cur.getCause() == cur) {
                break;
            }
            cur = cur.getCause();
            depth++;
        }
        return unwrapRootCause(e);
    }

    /**
     * 在 cause 链上选取最有信息量的 message：
     * 优先 SQLException；否则取链上「最短且非空、非纯类名」的文案（避免表层 MyBatis 长模板淹没根因）。
     */
    private String pickBestMessage(Throwable e) {
        SQLException sql = findCause(e, SQLException.class);
        if (sql != null) {
            String sqlMsg = compactMessage(sql.getMessage());
            if (sqlMsg != null && !sqlMsg.isEmpty()) {
                return sqlMsg;
            }
        }
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth < MAX_CAUSE_DEPTH) {
            String raw = cur.getMessage();
            String msg = compactMessage(raw);
            if (msg != null && !msg.isEmpty()) {
                int score = scoreMessage(msg, cur, depth);
                if (score > bestScore) {
                    bestScore = score;
                    best = msg;
                }
            }
            if (cur.getCause() == null || cur.getCause() == cur) {
                break;
            }
            cur = cur.getCause();
            depth++;
        }
        return best;
    }

    /**
     * 给 message 打分：有实质内容、较短、深层根因略加分；MyBatis 模板长文降分。
     */
    private int scoreMessage(String msg, Throwable owner, int depth) {
        int score = 100 - Math.min(msg.length(), 200) / 2;
        // 更靠近根因略加分
        score += depth * 5;
        if (owner instanceof SQLException) {
            score += 80;
        }
        if (owner instanceof DataAccessException || isPersistenceLike(owner)) {
            score += 30;
        }
        // MyBatis 默认大段模板降权
        if (msg.contains("### Error") || msg.contains("### The error")) {
            score -= 50;
        }
        return score;
    }

    /**
     * 压缩过长/多行异常文案：优先提取 MyBatis 的 Cause: 段，否则取首行有效内容。
     */
    private String compactMessage(String msg) {
        if (msg == null) {
            return null;
        }
        String t = msg.trim();
        if (t.isEmpty()) {
            return null;
        }
        // 统一换行，便于截取
        t = t.replace("\r\n", "\n").replace('\r', '\n');

        // MyBatis：### Error ... Cause: xxx ### The error may...
        int causeIdx = indexOfIgnoreCase(t, "Cause:");
        if (causeIdx >= 0) {
            String after = t.substring(causeIdx + "Cause:".length()).trim();
            int nextSection = after.indexOf("###");
            if (nextSection > 0) {
                after = after.substring(0, nextSection).trim();
            }
            int nl = after.indexOf('\n');
            if (nl > 0) {
                after = after.substring(0, nl).trim();
            }
            if (!after.isEmpty()) {
                return after;
            }
        }

        // 多行只取第一行有效内容
        int nl = t.indexOf('\n');
        if (nl > 0) {
            t = t.substring(0, nl).trim();
        }
        // 去掉首尾过长路径噪音中的连续空白
        t = t.replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    private String firstFieldError(BindingResult bindingResult) {
        if (bindingResult == null) {
            return null;
        }
        FieldError fe = bindingResult.getFieldError();
        if (fe == null) {
            return null;
        }
        String field = fe.getField() != null ? fe.getField() : "";
        String defaultMsg = fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "";
        if (field.isEmpty() && defaultMsg.isEmpty()) {
            return null;
        }
        if (field.isEmpty()) {
            return defaultMsg;
        }
        if (defaultMsg.isEmpty()) {
            return field;
        }
        return field + " " + defaultMsg;
    }

    private boolean isPersistenceLike(Throwable e) {
        if (e == null) {
            return false;
        }
        String name = e.getClass().getName();
        return name.startsWith("org.apache.ibatis.")
                || name.contains("PersistenceException")
                || name.contains("MyBatisSystemException")
                || name.contains("BatchExecutorException");
    }

    @SuppressWarnings("unchecked")
    private <T extends Throwable> T findCause(Throwable e, Class<T> type) {
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth < MAX_CAUSE_DEPTH) {
            if (type.isInstance(cur)) {
                return (T) cur;
            }
            if (cur.getCause() == null || cur.getCause() == cur) {
                break;
            }
            cur = cur.getCause();
            depth++;
        }
        return null;
    }

    private Throwable unwrapRootCause(Throwable e) {
        Throwable cur = e;
        int depth = 0;
        while (cur.getCause() != null && cur.getCause() != cur && depth < MAX_CAUSE_DEPTH) {
            cur = cur.getCause();
            depth++;
        }
        return cur;
    }

    private int indexOfIgnoreCase(String text, String token) {
        if (text == null || token == null) {
            return -1;
        }
        return text.toLowerCase().indexOf(token.toLowerCase());
    }

    private String safeMessage(String message, String fallback) {
        if (message == null) {
            return fallback;
        }
        String t = message.trim();
        if (t.isEmpty()) {
            return fallback;
        }
        return truncate(t, MAX_CLIENT_MESSAGE_LEN);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }
}
