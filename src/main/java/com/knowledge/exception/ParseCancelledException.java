package com.knowledge.exception;

/**
 * 用户取消解析或任务已标记为 CANCELLED 时，在分块提取循环中抛出，由 AiService 捕获后结束后台任务。
 */
public class ParseCancelledException extends RuntimeException {
    public ParseCancelledException() {
        super("解析已取消");
    }
}
