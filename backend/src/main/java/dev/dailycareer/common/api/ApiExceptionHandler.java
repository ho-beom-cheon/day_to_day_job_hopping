package dev.dailycareer.common.api;

import dev.dailycareer.common.api.ApiEnvelope.ValidationDetail;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException exception, HttpServletRequest request) {
        return ApiResponses.failure(request, exception);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    ResponseEntity<?> stale(Exception exception, HttpServletRequest request) {
        return ApiResponses.failure(request, new ApiException(ApiErrorCode.PRECONDITION_FAILED));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> denied(AccessDeniedException exception, HttpServletRequest request) {
        return ApiResponses.failure(request, new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<?> unauthenticated(AuthenticationException exception, HttpServletRequest request) {
        return ApiResponses.failure(request, new ApiException(ApiErrorCode.AUTH_REQUIRED));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<?> constraint(ConstraintViolationException exception, HttpServletRequest request) {
        var details = exception.getConstraintViolations().stream().map(v ->
                new ValidationDetail(v.getPropertyPath().toString(), "입력값이 허용된 조건에 맞지 않습니다.")).toList();
        return ApiResponses.failure(request, new ApiException(ApiErrorCode.VALIDATION_FAILED, details));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception exception, HttpServletRequest request) {
        // Exception messages can contain SQL, rejected input or provider credentials.
        log.error("Unhandled API exception type={}", exception.getClass().getName());
        return ApiResponses.failure(request, new ApiException(ApiErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                            HttpStatusCode status, WebRequest webRequest) {
        var request = ((ServletWebRequest) webRequest).getRequest();
        if (!ApiResponses.isApi(request)) return super.handleExceptionInternal(exception, body, headers, status, webRequest);
        ApiException error;
        if (exception instanceof MethodArgumentNotValidException validation) {
            var details = validation.getBindingResult().getFieldErrors().stream()
                    .map(field -> new ValidationDetail(field.getField(), field.getDefaultMessage())).toList();
            error = new ApiException(ApiErrorCode.VALIDATION_FAILED, details);
        } else if (exception instanceof HandlerMethodValidationException validation) {
            if (validation.isForReturnValue()) return serverError(request);
            var details = validation.getAllValidationResults().stream().map(result -> {
                String name = result.getMethodParameter().getParameterName();
                return new ValidationDetail(name == null ? "parameter" : name, "입력값이 허용된 조건에 맞지 않습니다.");
            }).toList();
            error = new ApiException(ApiErrorCode.VALIDATION_FAILED, details);
        } else if (status.value() == 404) {
            error = new ApiException(ApiErrorCode.RESOURCE_NOT_FOUND);
        } else if (status.is5xxServerError()) {
            return serverError(request);
        } else {
            error = new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        var result = ApiResponses.failure(request, error);
        return new ResponseEntity<>(result.getBody(), result.getHeaders(), result.getStatusCode());
    }

    private ResponseEntity<Object> serverError(HttpServletRequest request) {
        var result = ApiResponses.failure(request, new ApiException(ApiErrorCode.INTERNAL_SERVER_ERROR));
        return new ResponseEntity<>(result.getBody(), result.getHeaders(), result.getStatusCode());
    }
}
