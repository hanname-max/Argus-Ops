package top.codejava.aiops.type.exception;

public class AiIntegrationException extends OpsException {

    public AiIntegrationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
