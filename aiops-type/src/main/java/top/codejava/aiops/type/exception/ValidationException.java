package top.codejava.aiops.type.exception;

public class ValidationException extends OpsException {

    public ValidationException(String message) {
        super(ErrorCode.INVALID_INPUT, message);
    }
}
