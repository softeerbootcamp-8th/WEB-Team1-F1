package com.softeer.race.common.presentation;

import com.softeer.race.common.exception.BusinessException;
import com.softeer.race.common.exception.CommonErrorCode;
import com.softeer.race.common.exception.ErrorCode;
import com.softeer.race.common.presentation.dto.response.ValidationErrorResponse;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// 모든 실패 응답을 ProblemDetail로 통일(RFC 9457)
// ResponseEntityExceptionHandler를 상속해 Spring MVC 내장 예외까지 같은 포맷으로 내린다
// 응답은 handleExceptionInternal을 거친다, 응답이 이미 커밋된 경우를 처리하고
// jakarta.servlet.error.exception 속성을 세팅해 서블릿 오류 처리 계층이 예외를 볼 수 있게 해준다
// @NullMarked는 재정의하는 스프링 메서드의 파라미터 널 계약을 맞추기 위한 것이다
@NullMarked
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String CODE = "code";
    private static final String ERRORS = "errors";

    @ExceptionHandler(BusinessException.class)
    public @Nullable ResponseEntity<Object> handleBusinessException(
            BusinessException exception, WebRequest request) {

        ErrorCode errorCode = exception.errorCode();
        log.warn("비즈니스 예외: {} - {} [{}]",
                errorCode.code(), exception.getMessage(), request.getDescription(false));

        ProblemDetail body = createProblemDetail(
                exception, errorCode.status(), exception.getMessage(), null, null, request);
        body.setProperty(CODE, errorCode.code());

        return handleExceptionInternal(exception, body, new HttpHeaders(), errorCode.status(), request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ValidationErrorResponse> errors = exception.getBindingResult().getAllErrors()
                .stream()
                .map(GlobalExceptionHandler::toValidationError)
                .toList();

        return handleExceptionInternal(
                exception, invalidRequest(exception, errors, request), headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return handleExceptionInternal(
                exception, invalidRequest(exception, List.of(), request), headers, status, request);
    }

    // 개별 파라미터 검증은 위 MethodArgumentNotValidException과 별개 경로라 둘 다 재정의해야 한다
    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ValidationErrorResponse> errors = exception.getParameterValidationResults().stream()
                .flatMap(GlobalExceptionHandler::toValidationErrors)
                .toList();

        return handleExceptionInternal(
                exception, invalidRequest(exception, errors, request), headers, status, request);
    }

    // 최후 방어선, 매핑되지 않은 예외는 서버 버그로 취급해 500으로 두고 원인은 로그에만 남긴다
    @ExceptionHandler(Exception.class)
    public @Nullable ResponseEntity<Object> handleUnexpectedException(
            Exception exception, WebRequest request) {

        log.error("처리되지 않은 예외 [{}]", request.getDescription(false), exception);

        ErrorCode errorCode = CommonErrorCode.INTERNAL_ERROR;
        ProblemDetail body = createProblemDetail(
                exception, errorCode.status(), errorCode.message(), null, null, request);
        body.setProperty(CODE, errorCode.code());

        return handleExceptionInternal(exception, body, new HttpHeaders(), errorCode.status(), request);
    }

    private ProblemDetail invalidRequest(
            Exception exception, List<ValidationErrorResponse> errors, WebRequest request) {

        log.warn("요청 검증 실패: {} [{}]", errors, request.getDescription(false));

        ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
        ProblemDetail body = createProblemDetail(
                exception, errorCode.status(), errorCode.message(), null, null, request);
        body.setProperty(CODE, errorCode.code());
        body.setProperty(ERRORS, errors);
        return body;
    }

    // @Valid 객체 파라미터만 실제 필드명을 알 수 있고, 단순 파라미터는 파라미터명뿐이다
    private static Stream<ValidationErrorResponse> toValidationErrors(
            ParameterValidationResult result) {

        if (result instanceof ParameterErrors parameterErrors) {
            return parameterErrors.getAllErrors().stream()
                    .map(GlobalExceptionHandler::toValidationError);
        }
        String field = Objects.requireNonNullElse(
                result.getMethodParameter().getParameterName(), "unknown");
        return result.getResolvableErrors().stream()
                .map(error -> new ValidationErrorResponse(field, error.getDefaultMessage()));
    }

    // 필드 단위가 아닌 클래스 레벨 검증(교차 필드 제약 등)은 FieldError가 아니라 ObjectError로 온다
    private static ValidationErrorResponse toValidationError(ObjectError error) {
        String field = error instanceof FieldError fieldError
                ? fieldError.getField()
                : error.getObjectName();
        return new ValidationErrorResponse(field, error.getDefaultMessage());
    }
}
