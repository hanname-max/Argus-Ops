package top.codejava.aiops.type.exception;

public class SshConnectionException extends OpsException {

    public SshConnectionException(String message, Throwable cause) {
        super(ErrorCode.SSH_CONNECTION_FAILURE, message, cause);
    }
}
