package com.learnspark.common.exception;

import com.learnspark.common.result.ApiResult;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 全局异常处理，统一响应结构。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusiness(BusinessException ex) {
        log.warn("业务异常: code={}, msg={}", ex.getErrorCode().getCode(), ex.getMessage());
        return ResponseEntity.ok(ApiResult.fail(ex.getErrorCode(), ex.getMessage()));
    }

    /** 参数校验异常（@RequestBody） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.ok(ApiResult.fail(ErrorCode.BAD_REQUEST, msg));
    }

    /** 参数校验异常（表单） */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResult<Void>> handleBind(BindException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.ok(ApiResult.fail(ErrorCode.BAD_REQUEST, msg));
    }

    /** 参数校验异常（@RequestParam/@PathVariable） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResult<Void>> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.ok(ApiResult.fail(ErrorCode.BAD_REQUEST, ex.getMessage()));
    }

    /** 上传文件过大 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResult<Void>> handleUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.ok(ApiResult.fail(ErrorCode.KB_FILE_TOO_LARGE));
    }

    /** 认证异常 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResult<Void>> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResult.fail(ErrorCode.UNAUTHORIZED, ex.getMessage()));
    }

    /** 权限异常 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResult.fail(ErrorCode.FORBIDDEN));
    }

    /** 数据完整性冲突（唯一索引、外键约束等，如并发注册同邮箱） */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResult<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("数据完整性冲突: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResult.fail(ErrorCode.CONFLICT, "数据已存在或违反约束"));
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleAll(Exception ex) {
        log.error("未处理异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.fail(ErrorCode.INTERNAL_ERROR, ex.getMessage()));
    }
}
