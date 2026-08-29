package com.streamsense.videoservice.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamsense.videoservice.config.StreamSenseProperties;
import com.streamsense.videoservice.controller.VideoController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** The controller's own IllegalArgumentException and bean-validation failures both arrive as problem+json. */
class VideoControllerProblemDetailTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StreamSenseProperties properties = new StreamSenseProperties();
        properties.getPayload().setMaxFrameRefLength(16);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new VideoController(null, null, properties))
                .setControllerAdvice(new GlobalExceptionHandler("video-service"))
                .setValidator(validator)
                .build();
    }

    @Test
    void oversizedFrameRefIsA400ProblemFromTheDomainCheck() throws Exception {
        mockMvc.perform(
                        post("/api/video/upload-frame")
                                .contentType("application/json")
                                .content(
                                        """
                        {
                          "streamer": "test",
                          "frameRef": "frames/this-reference-is-far-too-long.png",
                          "frameSequence": 1,
                          "capturedAt": 1710000000000
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://streamsense.dev/problems/invalid-request"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("frameRef exceeds configured maximum length"))
                .andExpect(jsonPath("$.instance").value("/api/video/upload-frame"))
                .andExpect(jsonPath("$.service").value("video-service"));
    }

    @Test
    void missingFieldIsA400ProblemListingTheField() throws Exception {
        mockMvc.perform(
                        post("/api/video/upload-frame")
                                .contentType("application/json")
                                .content(
                                        """
                        {
                          "streamer": "test",
                          "frameSequence": 1,
                          "capturedAt": 1710000000000
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://streamsense.dev/problems/validation-failed"))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("frameRef"))
                .andExpect(jsonPath("$.service").value("video-service"));
    }
}
