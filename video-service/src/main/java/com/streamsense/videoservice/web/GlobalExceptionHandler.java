package com.streamsense.videoservice.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * Every error leaves this service as an RFC 9457 {@code application/problem+json} body.
 *
 * <p>Framework and validation errors are handled by {@link ResponseEntityExceptionHandler};
 * this class adds the domain mappings ({@code IllegalArgumentException} is client input,
 * {@code IllegalStateException} is a conflict with current state), field details for
 * validation failures, and a last-resort 500 that never leaks the exception message.
 * Each problem carries {@code service}, {@code timestamp}, and the request's
 * {@code correlationId} so a client can quote it back.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_TYPE_BASE = "https://streamsense.dev/problems/";

    private final String serviceName;

    public GlobalExceptionHandler(@Value("${spring.application.name:unknown}") String serviceName) {
        this.serviceName = serviceName;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleConflict(IllegalStateException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "conflict", ex.getMessage(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        ProblemDetail problem =
                problem(HttpStatus.BAD_REQUEST, "validation-failed", "Request validation failed", request);
        problem.setProperty(
                "errors",
                ex.getConstraintViolations().stream()
                        .map(violation -> Map.of(
                                "field", String.valueOf(violation.getPropertyPath()),
                                "message", String.valueOf(violation.getMessage())))
                        .toList());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("unhandled exception method={} path={}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "An unexpected error occurred", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ex.getBody();
        body.setType(URI.create(PROBLEM_TYPE_BASE + "validation-failed"));
        body.setDetail("Request validation failed");
        body.setProperty(
                "errors",
                ex.getBindingResult().getFieldErrors().stream()
                        .map(error -> Map.of(
                                "field", error.getField(),
                                "message", String.valueOf(error.getDefaultMessage())))
                        .toList());
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body == null && ex instanceof ErrorResponse errorResponse) {
            // Some framework handlers pass no body and let the superclass build it later; build it here so
            // the type and the request context below apply to every problem, not only the ones handed to us.
            body = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
        }
        if (body instanceof ProblemDetail problem) {
            if (problem.getType() == null || "about:blank".equals(problem.getType().toString())) {
                // The framework's own problems arrive typed "about:blank"; give them a StreamSense identifier so
                // clients can tell a malformed body from a missing parameter or an unsupported media type.
                problem.setType(URI.create(PROBLEM_TYPE_BASE + typeFor(ex, statusCode)));
            }
            if (request instanceof ServletWebRequest servletRequest) {
                decorate(problem, servletRequest.getRequest());
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    /** Stable problem type slug for the framework exceptions {@link ResponseEntityExceptionHandler} maps. */
    static String typeFor(Exception ex, HttpStatusCode status) {
        if (ex instanceof HttpMessageNotReadableException) {
            return "malformed-request";
        }
        if (ex instanceof MissingServletRequestParameterException || ex instanceof MissingServletRequestPartException) {
            return "missing-parameter";
        }
        if (ex instanceof MethodArgumentTypeMismatchException || ex instanceof TypeMismatchException) {
            return "invalid-request";
        }
        if (ex instanceof HttpMediaTypeNotSupportedException || ex instanceof HttpMediaTypeNotAcceptableException) {
            return "unsupported-media-type";
        }
        if (ex instanceof HttpRequestMethodNotSupportedException) {
            return "method-not-allowed";
        }
        if (ex instanceof NoResourceFoundException || ex instanceof NoHandlerFoundException) {
            return "not-found";
        }
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String phrase = resolved != null ? resolved.getReasonPhrase() : "error";
        return phrase.toLowerCase().replace(' ', '-');
    }

    private ProblemDetail problem(HttpStatus status, String type, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create(PROBLEM_TYPE_BASE + type));
        decorate(problem, request);
        return problem;
    }

    private void decorate(ProblemDetail problem, HttpServletRequest request) {
        if (problem.getInstance() == null && request.getRequestURI() != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        problem.setProperty("service", serviceName);
        problem.setProperty("timestamp", Instant.now().toString());
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.isBlank()) {
            problem.setProperty("correlationId", correlationId);
        }
    }
}
