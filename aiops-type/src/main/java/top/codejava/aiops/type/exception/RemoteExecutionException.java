package top.codejava.aiops.type.exception;

public class RemoteExecutionException extends OpsException {

    public RemoteExecutionException(String message, Throwable cause) {
        super(ErrorCode.REMOTE_EXEC_FAILURE, message, cause);
    }
}
