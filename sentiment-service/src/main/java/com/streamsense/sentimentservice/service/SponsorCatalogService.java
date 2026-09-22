package com.streamsense.sentimentservice.service;

import com.streamsense.sentimentservice.config.StreamSenseProperties;
import com.streamsense.sentimentservice.dto.SponsorCatalogEntry;
import com.streamsense.sentimentservice.dto.SponsorCatalogUpdateRequest;
import com.streamsense.sentimentservice.persistence.SponsorCatalogEntity;
import com.streamsense.sentimentservice.persistence.SponsorCatalogRepository;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * The sponsor catalog: what is known about a sponsor across every channel. Entries live in the
 * database and are mirrored in memory, keyed by the lower-cased name; an entry is found by its name
 * or by any of its aliases, so a deal that says "redbull" reaches "Red Bull". The configured sponsors
 * ({@code streamsense.sentiment.relevance.sponsors}) only fill in names the table does not hold yet,
 * so an operator's edit survives a restart and a config change never overwrites it.
 */
@Service
public class SponsorCatalogService {

    private final StreamSenseProperties properties;
    private final SponsorCatalogRepository repository;
    private final ConcurrentMap<String, SponsorCatalogEntry> entries = new ConcurrentHashMap<>();

    public SponsorCatalogService(StreamSenseProperties properties, SponsorCatalogRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    @PostConstruct
    public void seedConfiguredSponsors() {
        for (SponsorCatalogEntity stored : repository.findAll()) {
            entries.put(stored.getSponsorKey(), toEntry(stored));
        }
        for (StreamSenseProperties.Sponsor configured :
                properties.getSentiment().getRelevance().getSponsors()) {
            String key = normalize(configured.getName());
            if (key.isEmpty() || entries.containsKey(key)) {
                continue;
            }
            SponsorCatalogUpdateRequest request = new SponsorCatalogUpdateRequest();
            request.setName(configured.getName());
            request.setAliases(configured.getAliases());
            request.setSemanticTerms(configured.getSemanticTerms());
            upsert(request);
        }
    }

    /** Every entry, by name. */
    public List<SponsorCatalogEntry> list() {
        return entries.values().stream()
                .sorted(Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    /** The entry a sponsor's name reaches: by its name first, then by any entry's alias. Case does not matter. */
    public Optional<SponsorCatalogEntry> find(String sponsor) {
        String key = normalize(sponsor);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        SponsorCatalogEntry byName = entries.get(key);
        if (byName != null) {
            return Optional.of(byName);
        }
        return entries.values().stream()
                .filter(entry -> entry.aliases().stream()
                        .anyMatch(alias -> normalize(alias).equals(key)))
                .findFirst();
    }

    /** Replaces the entry of that name (case does not matter), or adds it. Terms are trimmed and de-duplicated. */
    public SponsorCatalogEntry upsert(SponsorCatalogUpdateRequest request) {
        String name = clean(request.getName());
        if (name == null) {
            throw new IllegalArgumentException("name is required");
        }
        String key = normalize(name);
        SponsorCatalogEntity saved = repository.save(new SponsorCatalogEntity(
                key,
                name,
                cleanedTerms(request.getAliases()),
                cleanedTerms(request.getSemanticTerms()),
                request.getMinScore(),
                System.currentTimeMillis()));
        SponsorCatalogEntry entry = toEntry(saved);
        entries.put(key, entry);
        return entry;
    }

    /** Removes the entry of that name; false when there is none. Channel profiles keep what they borrowed. */
    public boolean delete(String name) {
        String key = normalize(name);
        if (entries.remove(key) == null) {
            return false;
        }
        repository.deleteById(key);
        return true;
    }

    private static SponsorCatalogEntry toEntry(SponsorCatalogEntity stored) {
        return new SponsorCatalogEntry(
                stored.getName(),
                new ArrayList<>(stored.aliasList()),
                new ArrayList<>(stored.semanticTermList()),
                stored.getMinScore(),
                stored.getUpdatedAt());
    }

    private static List<String> cleanedTerms(List<String> terms) {
        Set<String> cleaned = new LinkedHashSet<>();
        if (terms != null) {
            for (String term : terms) {
                String value = clean(term);
                if (value != null) {
                    cleaned.add(value);
                }
            }
        }
        return new ArrayList<>(cleaned);
    }

    static String normalize(String value) {
        String cleaned = clean(value);
        return cleaned == null ? "" : cleaned.toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
