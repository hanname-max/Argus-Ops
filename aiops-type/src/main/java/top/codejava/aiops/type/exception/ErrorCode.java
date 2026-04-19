package top.codejava.aiops.type.exception;

public enum ErrorCode {
    INVALID_INPUT("AIOPS_400", "Invalid input"),
    LOCAL_AI_FAILURE("AIOPS_5001", "Local AI call failed"),
    REMOTE_AI_FAILURE("AIOPS_5002", "Remote AI call failed"),
    REMOTE_EXEC_FAILURE("AIOPS_5003", "Remote command execution failed");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
