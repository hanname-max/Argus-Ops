// aiops-domain/src/main/java/top/codejava/aiops/domain/exception/OpsExecutionException.java
package top.codejava.aiops.domain.exception;

/**
 * 运维执行异常
 * 所有远程执行相关异常都包装为此异常
 */
public class OpsExecutionException extends RuntimeException {

    public OpsExecutionException(String message) {
        super(message);
    }

    public OpsExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
