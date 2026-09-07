package com.streamsense.analyticsservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler("analytics-service");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void illegalArgumentBecomesA400ProblemWithServiceAndInstance() {
        MDC.put("correlationId", "corr-123");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/anything");

        ProblemDetail problem =
                handler.handleBadRequest(new IllegalArgumentException("windowMinutes must be positive"), request);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getDetail()).isEqualTo("windowMinutes must be positive");
        assertThat(problem.getType()).hasToString("https://streamsense.dev/problems/invalid-request");
        assertThat(problem.getInstance()).hasToString("/api/anything");
        assertThat(problem.getProperties())
                .containsEntry("service", "analytics-service")
                .containsEntry("correlationId", "corr-123");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void illegalStateBecomesA409Conflict() {
        ProblemDetail problem = handler.handleConflict(
                new IllegalStateException("ingestion is disabled"), new MockHttpServletRequest());

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getType()).hasToString("https://streamsense.dev/problems/conflict");
        assertThat(problem.getDetail()).isEqualTo("ingestion is disabled");
    }

    @Test
    @SuppressWarnings("unchecked")
    void constraintViolationsListEveryFieldError() {
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("recent.limit");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be less than or equal to 100");

        ProblemDetail problem = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of(violation)), new MockHttpServletRequest());

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getType()).hasToString("https://streamsense.dev/problems/validation-failed");
        List<Map<String, String>> errors =
                (List<Map<String, String>>) problem.getProperties().get("errors");
        assertThat(errors)
                .containsExactly(Map.of("field", "recent.limit", "message", "must be less than or equal to 100"));
    }

    @Test
    void frameworkErrorsGetAStableStreamSenseType() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/anything");
        ServletWebRequest webRequest = new ServletWebRequest(request);

        // handleException is the public entry point that dispatches to the framework's protected handlers.
        ResponseEntity<Object> malformed = handler.handleException(
                new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0])), webRequest);
        ResponseEntity<Object> missing =
                handler.handleException(new MissingServletRequestParameterException("streamer", "String"), webRequest);
        ResponseEntity<Object> unsupported =
                handler.handleException(new HttpMediaTypeNotSupportedException("text/plain"), webRequest);

        assertThat(((ProblemDetail) malformed.getBody()).getType())
                .hasToString("https://streamsense.dev/problems/malformed-request");
        assertThat(((ProblemDetail) missing.getBody()).getType())
                .hasToString("https://streamsense.dev/problems/missing-parameter");
        assertThat(((ProblemDetail) unsupported.getBody()).getType())
                .hasToString("https://streamsense.dev/problems/unsupported-media-type");
        assertThat(((ProblemDetail) malformed.getBody()).getProperties()).containsEntry("service", "analytics-service");
        assertThat(((ProblemDetail) malformed.getBody()).getInstance()).hasToString("/api/anything");
    }

    @Test
    void unexpectedExceptionsNeverLeakTheirMessage() {
        ProblemDetail problem = handler.handleUnexpected(
                new RuntimeException("jdbc password=hunter2"), new MockHttpServletRequest("GET", "/api/x"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(problem.toString()).doesNotContain("hunter2");
    }
}
