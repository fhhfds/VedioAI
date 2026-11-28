package com.vedioai.server.common.error;

import com.vedioai.server.common.api.ApiErrorResponse;
import com.vedioai.server.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;


@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(
            HttpServletRequest request,
            MethodArgumentNotValidException exception) {

        Map<String, String> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                details.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return error(request, HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT", "请求参数不正确", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> constraintViolation(
            HttpServletRequest request,
            ConstraintViolationException exception) {

        return error(request, HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT", "请求参数不正确", null);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> methodValidation(
            HttpServletRequest request,
            HandlerMethodValidationException exception) {

        return error(request, HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT", "请求参数不正确", null);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiErrorResponse> invalidRequest(
            HttpServletRequest request,
            InvalidRequestException exception) {

        return error(request, HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT", exception.getMessage(), null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> resourceNotFound(
            HttpServletRequest request,
            ResourceNotFoundException exception) {

        return error(request, HttpStatus.NOT_FOUND,
                "NOT_FOUND", exception.getMessage(), null);
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String message,
            Object details) {

        return ResponseEntity.status(status).body(
                new ApiErrorResponse(
                        new ApiErrorResponse.ErrorBody(code, message, details),
                        TraceIdFilter.traceId(request)));
    }
}
