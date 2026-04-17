package top.codejava.aiops.type.preflight;

/**
 * 预检责任链节点
 */
public interface PreflightNode {

    /**
     * 执行检查
     * @param context 预检上下文
     */
    void check(PreflightContext context);

    /**
     * 节点优先级（数字越小优先级越高）
     */
    default int order() {
        return 100;
    }

    /**
     * 节点名称
     */
    default String name() {
        return this.getClass().getSimpleName();
    }
}
