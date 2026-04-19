package top.codejava.aiops.type.exception;

public class OpsException extends RuntimeException {

    private final ErrorCode errorCode;

    public OpsException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public OpsException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
