package top.codejava.aiops.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.codejava.aiops.type.exception.ErrorCode;
import top.codejava.aiops.type.exception.OpsException;
import top.codejava.aiops.type.exception.ValidationException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "code", ErrorCode.INVALID_INPUT.code(),
                "message", "Request body is invalid JSON."
        ));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "code", ex.getErrorCode().code(),
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(OpsException.class)
    public ResponseEntity<Map<String, Object>> handleOpsException(OpsException ex) {
        return ResponseEntity.status(resolveStatus(ex.getErrorCode())).body(Map.of(
                "code", ex.getErrorCode().code(),
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "code", "AIOPS_500",
                "message", ex.getMessage()
        ));
    }

    private HttpStatus resolveStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case LOCAL_AI_FAILURE, REMOTE_AI_FAILURE, REMOTE_EXEC_FAILURE, SSH_CONNECTION_FAILURE -> HttpStatus.BAD_GATEWAY;
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
        };
    }
}
