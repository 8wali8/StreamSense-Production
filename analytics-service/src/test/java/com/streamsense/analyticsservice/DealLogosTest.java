package com.streamsense.analyticsservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.analyticsservice.api.Deal;
import com.streamsense.analyticsservice.api.DealCreateRequest;
import com.streamsense.analyticsservice.service.DealService;
import com.streamsense.analyticsservice.storage.InMemoryLogoObjectStore;
import com.streamsense.analyticsservice.storage.LogoObjectStore;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** A deal keeps up to two logos: real images within bounds, stored as objects, listed on the deal, served back, and removed with it. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.kafka.listener.auto-startup=false",
            "spring.datasource.url=jdbc:h2:mem:analytics-logos-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=true",
            "streamsense.analytics.capture-session-close-check-ms=3600000",
            // 60 KB, so an oversize upload is a small noisy image rather than five megabytes of test fixture.
            "streamsense.logos.max-bytes=61440",
            "streamsense.logos.bucket=test-logos"
        })
class DealLogosTest {

    private static final String LOGIN = "X-StreamSense-Auth-Login";
    private static final String ROLE = "X-StreamSense-Auth-Role";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DealService deals;

    @Autowired
    private LogoObjectStore store;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void aDealKeepsUpToTwoLogosServesTheirBytesAndDropsThemWithTheDeal() throws Exception {
        long dealId = createDeal("logoer").id();
        byte[] png = png(64, 48, false);

        // The first logo: recognised from its bytes, measured, stored under the deal's own key.
        JsonNode first = objectMapper.readTree(mockMvc.perform(
                        multipart("/api/analytics/deals/{id}/logos", dealId).file(part("logo.png", "image/png", png)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/analytics/deals/" + dealId + "/logos/")))
                .andExpect(jsonPath("$.dealId").value(dealId))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.width").value(64))
                .andExpect(jsonPath("$.height").value(48))
                .andExpect(jsonPath("$.sizeBytes").value(png.length))
                .andExpect(jsonPath("$.ref").value(startsWith("s3://test-logos/deals/" + dealId + "/")))
                .andReturn()
                .getResponse()
                .getContentAsString());
        long firstId = first.get("id").asLong();
        String firstKey = first.get("ref").asText().substring("s3://test-logos/".length());
        assertThat(((InMemoryLogoObjectStore) store).keys()).contains(firstKey);

        // The deal lists it, by id and in the streamer's list.
        mockMvc.perform(get("/api/analytics/deals/{id}", dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logos.length()").value(1))
                .andExpect(jsonPath("$.logos[0].id").value(firstId));
        mockMvc.perform(get("/api/analytics/deals").param("streamer", "logoer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].logos[0].id").value(firstId));

        // The bytes come back with the stored content type, privately cacheable.
        mockMvc.perform(get("/api/analytics/deals/{id}/logos/{logoId}", dealId, firstId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(content().bytes(png));

        // A second variant, a JPEG the client mislabelled: the bytes decide the type.
        mockMvc.perform(multipart("/api/analytics/deals/{id}/logos", dealId)
                        .file(part("dark.bin", "application/octet-stream", jpeg(80, 40))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.width").value(80));

        // A third is one too many; the deal still has two.
        mockMvc.perform(multipart("/api/analytics/deals/{id}/logos", dealId)
                        .file(part("third.png", "image/png", png(40, 40, false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("a deal holds at most 2 logos; remove one first"));
        assertThat(deals.get(dealId).orElseThrow().logos()).hasSize(2);

        // Removing one frees its slot and its object.
        mockMvc.perform(delete("/api/analytics/deals/{id}/logos/{logoId}", dealId, firstId))
                .andExpect(status().isNoContent());
        assertThat(((InMemoryLogoObjectStore) store).keys()).doesNotContain(firstKey);
        mockMvc.perform(get("/api/analytics/deals/{id}/logos/{logoId}", dealId, firstId))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/analytics/deals/{id}/logos/{logoId}", dealId, firstId))
                .andExpect(status().isNotFound());
        assertThat(deals.get(dealId).orElseThrow().logos()).hasSize(1);

        // Deleting the deal takes the remaining object with it.
        mockMvc.perform(delete("/api/analytics/deals/{id}", dealId)).andExpect(status().isNoContent());
        assertThat(((InMemoryLogoObjectStore) store).keys()).noneMatch(key -> key.startsWith("deals/" + dealId + "/"));
    }

    @Test
    void anUploadIsRefusedWhenItIsNotAnImageWithinBounds() throws Exception {
        long dealId = createDeal("bounds").id();

        mockMvc.perform(multipart("/api/analytics/deals/{id}/logos", dealId)
                        .file(part("logo.png", "image/png", "not an image".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("the logo must be a PNG or JPEG image"));
        mockMvc.perform(multipart("/api/analytics/deals/{id}/logos", dealId)
                        .file(part("tiny.png", "image/png", png(16, 16, false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("the logo must be at least 32 pixels on each side"));
        mockMvc.perform(multipart("/api/analytics/deals/{id}/logos", dealId)
                        .file(part("big.png", "image/png", png(400, 400, true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("the logo must be at most 60 KB"));
        mockMvc.perform(multipart("/api/analytics/deals/{id}/logos", dealId)
                        .file(part("empty.png", "image/png", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("the logo file is empty"));
        mockMvc.perform(multipart("/api/analytics/deals/{id}/logos", 987654L)
                        .file(part("logo.png", "image/png", png(64, 64, false))))
                .andExpect(status().isNotFound());
        assertThat(deals.get(dealId).orElseThrow().logos()).isEmpty();
    }

    @Test
    void aStreamerUploadsAndReadsLogosOnTheirOwnDealOnly() throws Exception {
        long own = createDeal("owner").id();
        long other = createDeal("someone-else").id();

        mockMvc.perform(multipart("/api/analytics/deals/{id}/logos", other)
                        .file(part("logo.png", "image/png", png(64, 64, false)))
                        .header(LOGIN, "owner")
                        .header(ROLE, "streamer"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("channel_forbidden"));
        long logoId = objectMapper
                .readTree(mockMvc.perform(multipart("/api/analytics/deals/{id}/logos", own)
                                .file(part("logo.png", "image/png", png(64, 64, false)))
                                .header(LOGIN, "owner")
                                .header(ROLE, "streamer"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asLong();
        mockMvc.perform(get("/api/analytics/deals/{id}/logos/{logoId}", own, logoId)
                        .header(LOGIN, "owner")
                        .header(ROLE, "streamer"))
                .andExpect(status().isOk());
        // A logo id reaches its image only through its own deal.
        mockMvc.perform(get("/api/analytics/deals/{id}/logos/{logoId}", other, logoId)
                        .header(LOGIN, "someone-else")
                        .header(ROLE, "streamer"))
                .andExpect(status().isNotFound());
    }

    private Deal createDeal(String streamer) {
        long now = System.currentTimeMillis();
        return deals.create(new DealCreateRequest(
                streamer, "Red Bull", now - 1000, null, null, null, null, null, null, null, null, null));
    }

    private static MockMultipartFile part(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    /** A PNG of the given size; noisy pixels make it incompressible, for the size bound. */
    private static byte[] png(int width, int height, boolean noisy) throws IOException {
        return encode(image(width, height, noisy), "png");
    }

    private static byte[] jpeg(int width, int height) throws IOException {
        return encode(image(width, height, false), "jpeg");
    }

    private static BufferedImage image(int width, int height, boolean noisy) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = noisy ? random.nextInt(0xFFFFFF) : ((x * 255 / Math.max(1, width - 1)) << 16) | 0x2040;
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, out)).isTrue();
        return out.toByteArray();
    }
}
