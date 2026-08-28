package com.streamsense.videoservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler("video-service");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void illegalArgumentBecomesA400ProblemWithServiceAndInstance() {
        MDC.put("correlationId", "corr-123");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/anything");

        ProblemDetail problem = handler.handleBadRequest(new IllegalArgumentException("windowMinutes must be positive"), request);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getDetail()).isEqualTo("windowMinutes must be positive");
        assertThat(problem.getType()).hasToString("https://streamsense.dev/problems/invalid-request");
        assertThat(problem.getInstance()).hasToString("/api/anything");
        assertThat(problem.getProperties()).containsEntry("service", "video-service").containsEntry("correlationId", "corr-123");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    void illegalStateBecomesA409Conflict() {
        ProblemDetail problem = handler.handleConflict(new IllegalStateException("ingestion is disabled"), new MockHttpServletRequest());

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

        ProblemDetail problem = handler.handleConstraintViolation(new ConstraintViolationException(Set.of(violation)), new MockHttpServletRequest());

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getType()).hasToString("https://streamsense.dev/problems/validation-failed");
        List<Map<String, String>> errors = (List<Map<String, String>>) problem.getProperties().get("errors");
        assertThat(errors).containsExactly(Map.of("field", "recent.limit", "message", "must be less than or equal to 100"));
    }

    @Test
    void unexpectedExceptionsNeverLeakTheirMessage() {
        ProblemDetail problem = handler.handleUnexpected(new RuntimeException("jdbc password=hunter2"), new MockHttpServletRequest("GET", "/api/x"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(problem.toString()).doesNotContain("hunter2");
    }
}
