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
 * 全局异常处理器
 *
 * {@link BizException} 优先使用业务文案，业务错误码只写入服务端日志
 * 请求参数错误按场景返回失败原因，数据库和其他系统异常返回整理后的提示，完整堆栈写入服务端日志
 */
@RestControllerAdvice
public class ExceptionConfig {

    /** 记录接口失败原因和需要排查的异常堆栈 */
    private static final Logger log = LoggerFactory.getLogger(ExceptionConfig.class);

    /** 前端提示截断时保留的正文字符数上限，省略号另计 */
    private static final int MAX_CLIENT_MESSAGE_LEN = 600;

    /** 查找异常原因时使用的遍历深度上限 */
    private static final int MAX_CAUSE_DEPTH = 10;

    // ─── 业务异常 ─────────────────────────────────────────────

    /**
     * 将业务异常转换为失败响应
     *
     * 执行顺序：
     * 1. 把业务错误码和原始文案记入日志
     * 2. 整理业务文案，文案为空白时使用默认提示
     *
     * @param e 业务异常
     * @return 带业务提示的失败响应，文案为空白时提示「业务处理失败」
     */
    @ExceptionHandler(BizException.class)
    public ApiResponse<?> handleBiz(BizException e) {
        // 1. 在日志中保留业务错误码，便于定位失败原因
        log.warn("业务异常 code={}: {}", e.getCode(), e.getMessage());
        // 2. 整理业务文案，缺少文案时使用默认提示
        return ApiResponse.fail(safeMessage(e.getMessage(), "业务处理失败"));
    }

    // ─── 请求/参数类 ──────────────────────────────────────────

    /**
     * 返回请求体无法解析时的失败提示
     *
     * 执行顺序：
     * 1. 记录请求体解析失败的原始文案
     * 2. 用「请求体解析失败」和整理后的异常信息生成响应
     *
     * @param e 请求体解析异常
     * @return 带解析失败提示的失败响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<?> handleNotReadable(HttpMessageNotReadableException e) {
        // 1. 记录请求体解析失败的原始文案
        log.warn("请求体解析失败: {}", e.getMessage());
        // 2. 整理适合返回给前端的解析提示
        return ApiResponse.fail(buildClientMessage("请求体解析失败", e));
    }

    /**
     * 返回请求体参数校验失败时的提示
     *
     * 执行顺序：
     * 1. 提取首个字段错误的字段名和提示
     * 2. 优先记录字段错误，取不到字段错误时记录原始异常文案
     * 3. 返回「参数校验失败」，取得字段错误时附上该错误
     *
     * @param e 请求体参数校验异常
     * @return 带参数校验失败提示的失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<?> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        // 提取首个字段错误，便于调用方找到需要修改的参数
        String detail = firstFieldError(e.getBindingResult());
        log.warn("参数校验失败: {}", detail != null ? detail : e.getMessage());
        return ApiResponse.fail(detail != null ? ("参数校验失败: " + detail) : "参数校验失败");
    }

    /**
     * 返回表单或查询参数绑定失败时的提示
     *
     * 执行顺序：
     * 1. 提取首个字段错误的字段名和提示
     * 2. 优先记录字段错误，取不到字段错误时记录原始异常文案
     * 3. 返回「参数绑定失败」，取得字段错误时附上该错误
     *
     * @param e 请求参数绑定异常
     * @return 带参数绑定失败提示的失败响应
     */
    @ExceptionHandler(BindException.class)
    public ApiResponse<?> handleBind(BindException e) {
        // 提取首个字段错误，便于调用方定位绑定失败的参数
        String detail = firstFieldError(e.getBindingResult());
        log.warn("参数绑定失败: {}", detail != null ? detail : e.getMessage());
        return ApiResponse.fail(detail != null ? ("参数绑定失败: " + detail) : "参数绑定失败");
    }

    /**
     * 提示调用方补齐必填请求参数
     *
     * 执行顺序：
     * 1. 记录缺少参数的原始异常文案
     * 2. 在响应中带上缺少的参数名，取得类型时一并返回
     *
     * @param e 缺少必填请求参数的异常
     * @return 带缺参提示的失败响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<?> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getMessage());
        return ApiResponse.fail("缺少请求参数: " + e.getParameterName()
                + (e.getParameterType() != null ? (" (" + e.getParameterType() + ")") : ""));
    }

    /**
     * 提示调用方按接口要求修正参数类型
     *
     * 执行顺序：
     * 1. 记录类型错误的原始异常文案
     * 2. 取接口要求的类型名，取不到时显示「未知类型」
     * 3. 在响应中带上参数名、所需类型和实际传入的值
     *
     * @param e 请求参数类型不匹配的异常
     * @return 带参数类型错误提示的失败响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型错误: {}", e.getMessage());
        String required = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知类型";
        return ApiResponse.fail("参数类型错误: " + e.getName() + " 需要 " + required
                + "，实际值=" + String.valueOf(e.getValue()));
    }

    /**
     * 提示调用方使用接口支持的 HTTP 方法
     *
     * 执行顺序：
     * 1. 记录请求方法不受支持的原始异常文案
     * 2. 在失败提示中附上本次请求使用的方法
     *
     * @param e HTTP 方法不受支持的异常
     * @return 带实际请求方法的失败响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return ApiResponse.fail("请求方法不支持: " + e.getMethod());
    }

    // ─── 数据访问 ─────────────────────────────────────────────

    /**
     * 处理 Spring 封装的数据访问异常
     *
     * 执行顺序：
     * 1. 记录数据访问异常的完整堆栈
     * 2. 用「数据库异常」和整理后的异常信息生成响应
     *
     * @param e Spring 数据访问异常，例如 SQL 语法错误或数据完整性错误
     * @return 带数据库异常提示的失败响应
     */
    @ExceptionHandler(DataAccessException.class)
    public ApiResponse<?> handleDataAccess(DataAccessException e) {
        // 1. 保留完整堆栈供服务端排查
        log.error("数据库访问异常", e);
        // 2. 整理适合返回给前端的数据库错误提示
        return ApiResponse.fail(buildClientMessage("数据库异常", e));
    }

    /**
     * 处理直接交给本类的 JDBC 异常
     *
     * 执行顺序：
     * 1. 记录 JDBC 异常的完整堆栈
     * 2. 用「数据库异常」和整理后的异常信息生成响应
     *
     * @param e JDBC 抛出的 SQL 异常
     * @return 带数据库异常提示的失败响应
     */
    @ExceptionHandler(SQLException.class)
    public ApiResponse<?> handleSql(SQLException e) {
        // 1. 保留完整堆栈供服务端排查
        log.error("数据库异常", e);
        // 2. 整理适合返回给前端的数据库错误提示
        return ApiResponse.fail(buildClientMessage("数据库异常", e));
    }

    // ─── 兜底 ─────────────────────────────────────────────────

    /**
     * 处理其余系统异常并生成失败提示
     *
     * 执行顺序：
     * 1. 记录异常的完整堆栈
     * 2. 当前异常的类名符合持久化异常特征时，生成「数据库异常」提示
     * 3. 其余情况生成「系统异常」提示
     *
     * @param e 交给兜底处理器的异常
     * @return 带对应异常提示的失败响应
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleOther(Exception e) {
        // 1. 保留完整堆栈供服务端排查
        log.error("系统异常", e);
        // 2. 根据当前异常的类名识别走到兜底处理的持久化异常
        if (isPersistenceLike(e)) {
            // 生成带数据库异常前缀的提示
            return ApiResponse.fail(buildClientMessage("数据库异常", e));
        }
        // 3. 生成带系统异常前缀的提示
        return ApiResponse.fail(buildClientMessage("系统异常", e));
    }

    // ─── 文案组装 ─────────────────────────────────────────────

    /**
     * 组装返回给前端的错误提示
     *
     * 执行顺序：
     * 1. 异常为空时直接返回前缀，前缀也为空时返回「未知错误」
     * 2. 选出要展示的异常类型和简短文案，将非空内容与场景前缀拼接
     * 3. 找到带 SQLState 的 SQL 异常且提示中尚未包含 SQLState 时，补上状态和非零错误码
     * 4. 提示超过正文上限时截断并加省略号
     *
     * @param prefix 出错场景的提示前缀
     * @param e 用于提取异常类型和文案的异常
     * @return 拼接后的错误提示，异常为空时返回前缀或「未知错误」
     */
    private String buildClientMessage(String prefix, Throwable e) {
        // 1. 没有异常详情时使用场景前缀或默认提示
        if (e == null) {
            return prefix != null ? prefix : "未知错误";
        }
        // 2. 选择能说明失败原因的异常类型，再提取简短文案
        Throwable showType = pickDisplayThrowable(e);
        String typeName = showType.getClass().getSimpleName();
        // 提取并压缩最适合展示的异常文案
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
        // 3. 查找 SQL 异常，按需补上能用于排查的状态和错误码
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
        // 4. 控制返回文案长度
        return truncate(sb.toString(), MAX_CLIENT_MESSAGE_LEN);
    }

    /**
     * 选择用于展示类型名的异常
     *
     * 执行顺序：
     * 1. 在异常原因链中查找 SQL 异常，找到就使用该异常
     * 2. 否则从外向内查找首个 Spring 数据访问异常或符合持久化特征的异常
     * 3. 仍未找到时，使用深度限制内最内层的异常
     *
     * @param e 用于查找展示类型的非空异常
     * @return 选中用于展示类型名的异常
     */
    private Throwable pickDisplayThrowable(Throwable e) {
        // 1. 优先查找 SQL 异常，便于展示具体的数据库错误类型
        SQLException sql = findCause(e, SQLException.class);
        if (sql != null) {
            return sql;
        }
        // 2. 找出首个数据访问或持久化异常
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth < MAX_CAUSE_DEPTH) {
            // 通过类名识别持久化异常，兼顾由 Spring 封装的数据访问异常
            if (cur instanceof DataAccessException || isPersistenceLike(cur)) {
                return cur;
            }
            if (cur.getCause() == null || cur.getCause() == cur) {
                break;
            }
            cur = cur.getCause();
            depth++;
        }
        // 3. 使用深度限制内最内层的异常
        return unwrapRootCause(e);
    }

    /**
     * 从异常原因链中选择适合展示的提示文案
     *
     * 执行顺序：
     * 1. 优先使用首个 SQL 异常精简后仍有内容的文案
     * 2. 否则在深度限制内逐层精简文案，按长度、类型和所在层级打分
     * 3. 返回得分最高的文案，同分时保留先找到的文案
     *
     * @param e 用于查找提示文案的异常
     * @return 选中的精简文案，未找到有效文案时返回 null
     */
    private String pickBestMessage(Throwable e) {
        // 1. 优先提取 SQL 异常文案
        SQLException sql = findCause(e, SQLException.class);
        if (sql != null) {
            // 去掉 SQL 异常文案中的长模板或多余行
            String sqlMsg = compactMessage(sql.getMessage());
            if (sqlMsg != null && !sqlMsg.isEmpty()) {
                return sqlMsg;
            }
        }
        // 2. 遍历异常原因链，保留更适合展示的文案
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth < MAX_CAUSE_DEPTH) {
            String raw = cur.getMessage();
            // 先精简文案，再判断是否有内容可供展示
            String msg = compactMessage(raw);
            if (msg != null && !msg.isEmpty()) {
                // 给当前文案打分，用于与此前选中的文案比较
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
        // 3. 返回得分最高的文案，未找到时返回 null
        return best;
    }

    /**
     * 给候选异常文案打分，优先保留简短且能说明失败原因的文案
     *
     * 执行顺序：
     * 1. 按文案长度扣分，最多扣 100 分，再按异常层级加分
     * 2. 为 SQL 异常和数据访问或持久化异常加分
     * 3. 文案仍带 MyBatis 模板标记时扣分
     *
     * @param msg 精简后的候选文案
     * @param owner 提供该文案的异常
     * @param depth 异常所在层级，最外层为 0
     * @return 候选文案的分数
     */
    private int scoreMessage(String msg, Throwable owner, int depth) {
        // 1. 短文案和较内层的异常优先
        int score = 100 - Math.min(msg.length(), 200) / 2;
        score += depth * 5;
        // 2. 优先保留 SQL 异常和数据访问相关异常的文案
        if (owner instanceof SQLException) {
            score += 80;
        }
        // 通过类名识别需要加分的持久化异常
        if (owner instanceof DataAccessException || isPersistenceLike(owner)) {
            score += 30;
        }
        // 3. 降低仍带 MyBatis 模板标记的文案优先级
        if (msg.contains("### Error") || msg.contains("### The error")) {
            score -= 50;
        }
        return score;
    }

    /**
     * 从原始异常文案中提取一行可展示的内容
     *
     * 执行顺序：
     * 1. 去掉首尾空白，文案为空时返回 null，否则统一换行符
     * 2. 不区分大小写查找 MyBatis 文案中的 {@code Cause:} 标记，优先使用标记后的原因
     * 3. 取不到原因时保留原文首行，并把连续空白合并为一个空格
     *
     * @param msg 原始异常文案
     * @return 提取出的原因或首行，文案为空白时返回 null
     */
    private String compactMessage(String msg) {
        // 1. 跳过空白文案，并统一换行符以便取出单行
        if (msg == null) {
            return null;
        }
        String t = msg.trim();
        if (t.isEmpty()) {
            return null;
        }
        t = t.replace("\r\n", "\n").replace('\r', '\n');

        // 2. 查找 MyBatis 原因段，只保留后续区段或换行前的原因
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

        // 3. 取不到原因段时保留首行，并合并连续空白
        int nl = t.indexOf('\n');
        if (nl > 0) {
            t = t.substring(0, nl).trim();
        }
        t = t.replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    /**
     * 提取参数校验或绑定结果中的首个字段错误
     *
     * 执行顺序：
     * 1. 绑定结果为空或没有字段错误时返回 null
     * 2. 读取字段名和默认提示，两项都为空时返回 null
     * 3. 仅有一项时直接返回，两项都有时用空格拼接
     *
     * @param bindingResult Spring 提供的参数校验或绑定结果
     * @return 首个字段错误的提示，取不到可展示的内容时返回 null
     */
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

    /**
     * 根据当前异常的类名判断是否符合持久化异常特征
     *
     * 执行顺序：
     * 1. 异常为空时返回 false
     * 2. 检查类名是否位于 MyBatis 包下，或包含预设的持久化异常名称
     *
     * @param e 待判断的异常
     * @return true=类名符合持久化异常特征，false=异常为空或类名不符合
     */
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

    /**
     * 从外向内查找首个符合指定类型的异常
     *
     * 执行顺序：
     * 1. 从传入的异常开始逐层检查，找到类型匹配的异常时返回
     * 2. 遇到空原因、原因指向当前异常或达到查找深度上限时结束
     *
     * @param <T> 要查找的异常类型
     * @param e 查找起点，包含该异常本身
     * @param type 用于匹配异常类型的类对象
     * @return 首个匹配指定类型的异常，未找到时返回 null
     */
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

    /**
     * 逐层拆开异常包装并取得最内层可达的原因
     *
     * 执行顺序：
     * 1. 从当前异常向内查找，直到没有下一层原因、原因指向自身或达到深度上限
     *
     * @param e 用于追溯原因的非空异常
     * @return 停止查找时所在层的异常
     */
    private Throwable unwrapRootCause(Throwable e) {
        Throwable cur = e;
        int depth = 0;
        while (cur.getCause() != null && cur.getCause() != cur && depth < MAX_CAUSE_DEPTH) {
            cur = cur.getCause();
            depth++;
        }
        return cur;
    }

    /**
     * 查找标记在文案中首次出现的位置
     *
     * 执行顺序：
     * 1. 文案或标记为 null 时返回 -1
     * 2. 将两者都转为小写后查找标记
     *
     * @param text 待查找的文案
     * @param token 要查找的标记
     * @return 标记首次出现的位置，从 0 开始，输入为 null 或未找到时返回 -1
     */
    private int indexOfIgnoreCase(String text, String token) {
        if (text == null || token == null) {
            return -1;
        }
        return text.toLowerCase().indexOf(token.toLowerCase());
    }

    /**
     * 整理要返回给前端的业务文案
     *
     * 执行顺序：
     * 1. 原文为 null 或去掉首尾空白后为空时，使用备用文案
     * 2. 保留整理后的原文，超出正文上限时截断
     *
     * @param message 原始业务文案
     * @param fallback 原文为空白时使用的备用文案
     * @return 整理后的业务文案，原文为空白时原样返回备用文案
     */
    private String safeMessage(String message, String fallback) {
        if (message == null) {
            return fallback;
        }
        String t = message.trim();
        if (t.isEmpty()) {
            return fallback;
        }
        // 超出前端提示的正文上限时截断
        return truncate(t, MAX_CLIENT_MESSAGE_LEN);
    }

    /**
     * 截短超过正文上限的提示文案
     *
     * 执行顺序：
     * 1. 文案为 null 或未超出上限时原样返回
     * 2. 超出上限时保留前 maxLen 个字符，并追加三个英文点
     *
     * @param text 要限制长度的文案
     * @param maxLen 正文最多保留的字符数
     * @return 原文或截断后加省略号的文案，原文为 null 时返回 null
     */
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
