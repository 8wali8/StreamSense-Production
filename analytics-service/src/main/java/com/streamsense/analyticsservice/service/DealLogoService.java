package com.streamsense.analyticsservice.service;

import com.streamsense.analyticsservice.api.DealLogo;
import com.streamsense.analyticsservice.config.StreamSenseProperties;
import com.streamsense.analyticsservice.model.DealLogoRow;
import com.streamsense.analyticsservice.persistence.DealLogoRepository;
import com.streamsense.analyticsservice.persistence.DealRepository;
import com.streamsense.analyticsservice.storage.LogoObjectStore;
import com.streamsense.analyticsservice.storage.LogoStorageException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The sponsor's logo on a deal. An upload is checked before it is kept: a real PNG or JPEG (decided
 * from the bytes, never from the client's content type), within the size and pixel bounds, and one
 * of at most {@code streamsense.logos.max-per-deal}. The bytes go to the object store under a key of
 * the deal's own, the row records what was measured, and the object is removed again when the row
 * is, or when the deal is deleted.
 */
@Service
public class DealLogoService {

    private static final Logger log = LoggerFactory.getLogger(DealLogoService.class);

    private final DealRepository deals;
    private final DealLogoRepository logos;
    private final LogoObjectStore store;
    private final StreamSenseProperties properties;
    private final Clock clock;

    @Autowired
    public DealLogoService(
            DealRepository deals, DealLogoRepository logos, LogoObjectStore store, StreamSenseProperties properties) {
        this(deals, logos, store, properties, Clock.systemUTC());
    }

    DealLogoService(
            DealRepository deals,
            DealLogoRepository logos,
            LogoObjectStore store,
            StreamSenseProperties properties,
            Clock clock) {
        this.deals = deals;
        this.logos = logos;
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    /** Keeps the image as one of the deal's logos. Empty when the deal is unknown; bad input is an IllegalArgumentException. */
    public Optional<DealLogo> upload(long dealId, byte[] bytes) {
        if (deals.findById(dealId).isEmpty()) {
            return Optional.empty();
        }
        StreamSenseProperties.Logos config = properties.getLogos();
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("the logo file is empty");
        }
        if (bytes.length > config.getMaxBytes()) {
            throw new IllegalArgumentException("the logo must be at most " + describeBytes(config.getMaxBytes()));
        }
        ImageShape shape = decode(bytes);
        if (shape.width() < config.getMinPixels() || shape.height() < config.getMinPixels()) {
            throw new IllegalArgumentException(
                    "the logo must be at least " + config.getMinPixels() + " pixels on each side");
        }
        if (shape.width() > config.getMaxPixels() || shape.height() > config.getMaxPixels()) {
            throw new IllegalArgumentException(
                    "the logo must be at most " + config.getMaxPixels() + " pixels on each side");
        }
        if (logos.countByDeal(dealId) >= config.getMaxPerDeal()) {
            throw new IllegalStateException(
                    "a deal holds at most " + config.getMaxPerDeal() + " logos; remove one first");
        }
        String key = "deals/" + dealId + "/" + UUID.randomUUID() + "." + shape.extension();
        store.put(key, bytes, shape.contentType());
        DealLogoRow row = new DealLogoRow(
                0,
                dealId,
                key,
                shape.contentType(),
                sha256(bytes),
                shape.width(),
                shape.height(),
                bytes.length,
                clock.millis());
        long id;
        try {
            id = logos.insert(row);
        } catch (RuntimeException ex) {
            // The row is what makes the object reachable; without it the object is litter.
            deleteObject(key);
            throw ex;
        }
        log.info(
                "deal {} gained logo {} ({}x{} {}, {} bytes)",
                dealId,
                id,
                shape.width(),
                shape.height(),
                shape.contentType(),
                bytes.length);
        return Optional.of(toApi(new DealLogoRow(
                id,
                dealId,
                key,
                row.contentType(),
                row.sha256(),
                row.width(),
                row.height(),
                row.sizeBytes(),
                row.uploadedAt())));
    }

    /** The logo and its bytes, for the console; empty when the deal has no such logo or the object is gone. */
    public Optional<StoredLogo> read(long dealId, long logoId) {
        return logos.findByDealAndId(dealId, logoId)
                .flatMap(row -> store.get(row.objectKey()).map(bytes -> new StoredLogo(toApi(row), bytes)));
    }

    /** Removes the logo and its object. False when the deal has no such logo. */
    public boolean remove(long dealId, long logoId) {
        Optional<DealLogoRow> found = logos.findByDealAndId(dealId, logoId);
        if (found.isEmpty()) {
            return false;
        }
        logos.delete(found.get().id());
        deleteObject(found.get().objectKey());
        log.info("deal {} lost logo {}", dealId, logoId);
        return true;
    }

    public List<DealLogo> list(long dealId) {
        return logos.findByDeal(dealId).stream().map(this::toApi).toList();
    }

    /** The logos of several deals at once; a deal without any maps to an empty list. */
    public Map<Long, List<DealLogo>> listByDeals(Collection<Long> dealIds) {
        Map<Long, List<DealLogoRow>> rows = logos.findByDeals(dealIds);
        return dealIds.stream()
                .distinct()
                .collect(Collectors.toMap(id -> id, id -> rows.getOrDefault(id, List.of()).stream()
                        .map(this::toApi)
                        .toList()));
    }

    /** The stored rows of a deal, for a caller about to delete the deal and wanting its objects gone too. */
    public List<DealLogoRow> rowsOf(long dealId) {
        return logos.findByDeal(dealId);
    }

    /** Removes the objects behind rows that are already gone (a deleted deal cascades its rows). Best effort. */
    public void deleteObjects(List<DealLogoRow> rows) {
        for (DealLogoRow row : rows) {
            deleteObject(row.objectKey());
        }
    }

    private void deleteObject(String key) {
        try {
            store.delete(key);
        } catch (LogoStorageException ex) {
            log.warn("logo object {} could not be removed: {}", store.ref(key), ex.getMessage());
        }
    }

    private DealLogo toApi(DealLogoRow row) {
        return new DealLogo(
                row.id(),
                row.dealId(),
                row.contentType(),
                row.width(),
                row.height(),
                row.sizeBytes(),
                row.uploadedAt(),
                store.ref(row.objectKey()));
    }

    /**
     * What the bytes are, read from their header alone: the format a registered reader recognises and
     * the dimensions it declares. Nothing is decoded into pixels, so a 5 MB file that would unpack to
     * gigabytes is refused by its declared size, not discovered by an out-of-memory error.
     */
    static ImageShape decode(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IllegalArgumentException("the logo must be a PNG or JPEG image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("the logo must be a PNG or JPEG image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                return switch (format) {
                    case "png" -> new ImageShape("image/png", "png", width, height);
                    case "jpeg", "jpg" -> new ImageShape("image/jpeg", "jpg", width, height);
                    default -> throw new IllegalArgumentException("the logo must be a PNG or JPEG image");
                };
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("the logo must be a PNG or JPEG image", ex);
        }
    }

    static String describeBytes(long bytes) {
        if (bytes % (1024 * 1024) == 0) {
            return (bytes / (1024 * 1024)) + " MB";
        }
        if (bytes % 1024 == 0) {
            return (bytes / 1024) + " KB";
        }
        return bytes + " bytes";
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    record ImageShape(String contentType, String extension, int width, int height) {}

    /** A logo with its bytes, for serving. */
    public record StoredLogo(DealLogo logo, byte[] bytes) {}
}
