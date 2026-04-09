package com.streamsense.videoservice.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.streamsense.videoservice.config.StreamSenseProperties;

class VideoControllerValidationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StreamSenseProperties properties = new StreamSenseProperties();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        VideoController controller = new VideoController(null, null, null, properties);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
    }

    @Test
    void uploadFrame_rejectsBlankStreamer() throws Exception {
        mockMvc.perform(post("/api/video/upload-frame")
                .contentType("application/json")
                .content("""
                        {
                          "streamer": "",
                          "frameRef": "frames/test.png",
                          "frameSequence": 1,
                          "capturedAt": 1710000000000
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadFrame_rejectsMissingFrameRef() throws Exception {
        mockMvc.perform(post("/api/video/upload-frame")
                .contentType("application/json")
                .content("""
                        {
                          "streamer": "test",
                          "frameSequence": 1,
                          "capturedAt": 1710000000000
                        }
                        """))
                .andExpect(status().isBadRequest());
    }
}
