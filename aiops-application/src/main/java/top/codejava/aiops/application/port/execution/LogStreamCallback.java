package top.codejava.aiops.application.port.execution;

/**
 * 流式日志回调
 * 本地主导架构：远程执行的日志实时回传给本地处理
 */
@FunctionalInterface
public interface LogStreamCallback {

    /**
     * 接收到一行标准输出日志
     * @param logLine 日志内容
     */
    void onNext(String logLine);

    /**
     * 接收到一行错误输出日志
     * 默认空实现
     * @param errorLog 错误日志内容
     */
    default void onError(String errorLog) {
    }

    /**
     * 执行完成回调
     * 默认空实现
     * @param exitCode 退出码
     */
    default void onComplete(int exitCode) {
    }
}
