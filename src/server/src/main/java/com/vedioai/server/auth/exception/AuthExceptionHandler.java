package com.vedioai.server.auth.exception;

import com.vedioai.server.common.api.ApiErrorResponse;
import com.vedioai.server.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class AuthExceptionHandler {

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

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> invalidCredentials(
            HttpServletRequest request,
            InvalidCredentialsException exception) {

        return error(request, HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS", exception.getMessage(), null);
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ApiErrorResponse> accountDisabled(
            HttpServletRequest request,
            AccountDisabledException exception) {

        return error(request, HttpStatus.FORBIDDEN,
                "ACCOUNT_DISABLED", exception.getMessage(), null);
    }

    @ExceptionHandler(SessionLimitException.class)
    public ResponseEntity<ApiErrorResponse> sessionLimit(
            HttpServletRequest request,
            SessionLimitException exception) {

        return error(request, HttpStatus.CONFLICT,
                "SESSION_LIMIT", exception.getMessage(), null);
    }

    @ExceptionHandler(AlreadyAuthenticatedException.class)
    public ResponseEntity<ApiErrorResponse> alreadyAuthenticated(
            HttpServletRequest request,
            AlreadyAuthenticatedException exception) {

        return error(request, HttpStatus.CONFLICT,
                "ALREADY_AUTHENTICATED", exception.getMessage(), null);
    }

    @ExceptionHandler(CurrentPasswordInvalidException.class)
    public ResponseEntity<ApiErrorResponse> currentPasswordInvalid(
            HttpServletRequest request,
            CurrentPasswordInvalidException exception) {

        return error(request, HttpStatus.BAD_REQUEST,
                "CURRENT_PASSWORD_INVALID", exception.getMessage(), null);
    }

    @ExceptionHandler(ReauthenticationRequiredException.class)
    public ResponseEntity<ApiErrorResponse> reauthenticationRequired(
            HttpServletRequest request,
            ReauthenticationRequiredException exception) {

        return error(request, HttpStatus.FORBIDDEN,
                "REAUTH_REQUIRED", exception.getMessage(), null);
    }

    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<ApiErrorResponse> versionConflict(
            HttpServletRequest request,
            VersionConflictException exception) {

        return error(request, HttpStatus.CONFLICT,
                "VERSION_CONFLICT", exception.getMessage(), null);
    }

    @ExceptionHandler(LastAdminException.class)
    public ResponseEntity<ApiErrorResponse> lastAdmin(
            HttpServletRequest request,
            LastAdminException exception) {

        return error(request, HttpStatus.CONFLICT,
                "LAST_ADMIN", exception.getMessage(), null);
    }

    @ExceptionHandler(SelfModificationException.class)
    public ResponseEntity<ApiErrorResponse> selfModification(
            HttpServletRequest request,
            SelfModificationException exception) {

        return error(request, HttpStatus.CONFLICT,
                "SELF_MODIFICATION", exception.getMessage(), null);
    }

    @ExceptionHandler(NicknameInvalidException.class)
    public ResponseEntity<ApiErrorResponse> nicknameInvalid(
            HttpServletRequest request,
            NicknameInvalidException exception) {

        return error(request, HttpStatus.BAD_REQUEST,
                "INVALID_NICKNAME", exception.getMessage(), null);
    }

    @ExceptionHandler({SessionExpiredException.class, SessionGuardException.class})
    public ResponseEntity<ApiErrorResponse> sessionExpired(
            HttpServletRequest request,
            RuntimeException exception) {

        return error(request, HttpStatus.UNAUTHORIZED,
                "SESSION_EXPIRED", exception.getMessage(), null);
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String message,
            Object details) {

        return ResponseEntity.status(status)
                .body(body(request, code, message, details));
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
