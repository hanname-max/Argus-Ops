package top.codejava.aiops.type.exception;

public enum ErrorCode {
    INVALID_INPUT("AIOPS_400"),
    REMOTE_EXEC_FAILURE("AIOPS_5003"),
    SSH_CONNECTION_FAILURE("AIOPS_5004");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
