package com.vedioai.server.identity.web;

import java.util.LinkedHashMap;
import java.util.Map;

import com.vedioai.server.identity.challenge.ChallengeInvalidException;
import com.vedioai.server.identity.challenge.ChallengeResendTooSoonException;
import com.vedioai.server.identity.challenge.EmailAlreadyRegisteredException;
import com.vedioai.server.identity.challenge.InvalidEmailException;
import com.vedioai.server.identity.challenge.InvalidNicknameException;
import com.vedioai.server.identity.challenge.RegistrationDisabledException;
import com.vedioai.server.identity.security.PasswordCapacityExceededException;
import com.vedioai.server.identity.security.WeakPasswordException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

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

    @ExceptionHandler(ChallengeResendTooSoonException.class)
    public ResponseEntity<ApiErrorResponse> resendTooSoon(
            HttpServletRequest request,
            ChallengeResendTooSoonException exception) {

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(exception.getRetryAfterSeconds()))
                .body(body(request, "RATE_LIMITED", exception.getMessage(), null));
    }

    @ExceptionHandler(PasswordCapacityExceededException.class)
    public ResponseEntity<ApiErrorResponse> passwordCapacity(
            HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "5")
                .body(body(request, "PASSWORD_CAPACITY_EXCEEDED",
                        "密码计算繁忙，请稍后重试", null));
    }

    @ExceptionHandler(WeakPasswordException.class)
    public ResponseEntity<ApiErrorResponse> weakPassword(
            HttpServletRequest request,
            WeakPasswordException exception) {

        return error(request, HttpStatus.BAD_REQUEST,
                "PASSWORD_POLICY_VIOLATION", exception.getMessage(), null);
    }

    @ExceptionHandler(ChallengeInvalidException.class)
    public ResponseEntity<ApiErrorResponse> challengeInvalid(
            HttpServletRequest request,
            ChallengeInvalidException exception) {

        return error(request, HttpStatus.BAD_REQUEST,
                "CHALLENGE_INVALID", exception.getMessage(), null);
    }

    @ExceptionHandler(InvalidEmailException.class)
    public ResponseEntity<ApiErrorResponse> invalidEmail(
            HttpServletRequest request,
            InvalidEmailException exception) {

        return error(request, HttpStatus.BAD_REQUEST,
                "INVALID_EMAIL", exception.getMessage(), null);
    }

    @ExceptionHandler(InvalidNicknameException.class)
    public ResponseEntity<ApiErrorResponse> invalidNickname(
            HttpServletRequest request,
            InvalidNicknameException exception) {

        return error(request, HttpStatus.BAD_REQUEST,
                "INVALID_NICKNAME", exception.getMessage(), null);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiErrorResponse> emailAlreadyRegistered(
            HttpServletRequest request,
            EmailAlreadyRegisteredException exception) {

        return error(request, HttpStatus.CONFLICT,
                "EMAIL_ALREADY_REGISTERED", exception.getMessage(), null);
    }

    @ExceptionHandler(RegistrationDisabledException.class)
    public ResponseEntity<ApiErrorResponse> registrationDisabled(
            HttpServletRequest request,
            RegistrationDisabledException exception) {

        return error(request, HttpStatus.SERVICE_UNAVAILABLE,
                "REGISTRATION_DISABLED", exception.getMessage(), null);
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String message,
            Object details) {

        return ResponseEntity.status(status).body(body(request, code, message, details));
    }

    private ApiErrorResponse body(
            HttpServletRequest request,
            String code,
            String message,
            Object details) {

        return new ApiErrorResponse(
                new ApiErrorResponse.ErrorBody(code, message, details),
                TraceIdFilter.traceId(request));
    }
}