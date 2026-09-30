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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * The sponsor catalog: what is known about a sponsor across every channel. Entries live in the
 * database and are mirrored in memory, keyed by the lower-cased name; an entry is found by its name
 * or by any of its aliases, so a deal that says "redbull" reaches "Red Bull". A name or an alias
 * belongs to one entry only, so what a spelling reaches never depends on the order entries are held
 * in. The configured sponsors ({@code streamsense.sentiment.relevance.sponsors}) only fill in names
 * the table has never held: an operator's edit survives a restart, a config change never overwrites
 * it, and an entry the operator removed stays removed.
 */
@Service
public class SponsorCatalogService {

    private static final Logger log = LoggerFactory.getLogger(SponsorCatalogService.class);

    private final StreamSenseProperties properties;
    private final SponsorCatalogRepository repository;
    private final ConcurrentMap<String, SponsorCatalogEntry> entries = new ConcurrentHashMap<>();
    /** The keys of removed entries: known to the table, absent from the catalog. */
    private final Set<String> removedKeys = ConcurrentHashMap.newKeySet();

    public SponsorCatalogService(StreamSenseProperties properties, SponsorCatalogRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    @PostConstruct
    public void seedConfiguredSponsors() {
        for (SponsorCatalogEntity stored : repository.findAll()) {
            if (stored.isRemoved()) {
                removedKeys.add(stored.getSponsorKey());
            } else {
                entries.put(stored.getSponsorKey(), toEntry(stored));
            }
        }
        for (StreamSenseProperties.Sponsor configured :
                properties.getSentiment().getRelevance().getSponsors()) {
            String key = normalize(configured.getName());
            if (key.isEmpty() || entries.containsKey(key) || removedKeys.contains(key)) {
                continue;
            }
            SponsorCatalogUpdateRequest request = new SponsorCatalogUpdateRequest();
            request.setName(configured.getName());
            request.setAliases(configured.getAliases());
            request.setSemanticTerms(configured.getSemanticTerms());
            try {
                upsert(request);
            } catch (IllegalArgumentException | IllegalStateException refused) {
                // A configured sponsor the catalog cannot hold must not stop the service from starting.
                log.warn(
                        "configured sponsor {} was not added to the catalog: {}",
                        configured.getName(),
                        refused.getMessage());
            }
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

    /**
     * Replaces the entry of that name (case does not matter), or adds it. Terms are trimmed and
     * de-duplicated. Refused as bad input when a term holds a line break or a list is longer than its
     * column, and as a conflict when the name or an alias already belongs to another entry.
     */
    public SponsorCatalogEntry upsert(SponsorCatalogUpdateRequest request) {
        String name = clean(request.getName());
        if (name == null) {
            throw new IllegalArgumentException("name is required");
        }
        String key = normalize(name);
        List<String> aliases = cleanedTerms(request.getAliases(), "aliases");
        List<String> semanticTerms = cleanedTerms(request.getSemanticTerms(), "semanticTerms");
        requireUnclaimed(key, name, name);
        for (String alias : aliases) {
            requireUnclaimed(key, name, alias);
        }
        SponsorCatalogEntity saved = repository.save(new SponsorCatalogEntity(
                key, name, aliases, semanticTerms, request.getMinScore(), System.currentTimeMillis()));
        SponsorCatalogEntry entry = toEntry(saved);
        entries.put(key, entry);
        removedKeys.remove(key);
        return entry;
    }

    /**
     * Removes the entry of that name; false when there is none. Its row stays, marked removed, so the
     * configured sponsors do not bring it back. Channel profiles keep what they borrowed.
     */
    public boolean delete(String name) {
        String key = normalize(name);
        SponsorCatalogEntry entry = entries.remove(key);
        if (entry == null) {
            return false;
        }
        repository.save(SponsorCatalogEntity.removed(key, entry.name(), System.currentTimeMillis()));
        removedKeys.add(key);
        return true;
    }

    /** A spelling (the name or an alias of the entry being saved) must not be another entry's name or alias. */
    private void requireUnclaimed(String key, String name, String spelling) {
        String wanted = normalize(spelling);
        for (SponsorCatalogEntry other : entries.values()) {
            String otherKey = normalize(other.name());
            if (otherKey.equals(key)) {
                continue;
            }
            boolean claimed = otherKey.equals(wanted)
                    || other.aliases().stream()
                            .anyMatch(alias -> normalize(alias).equals(wanted));
            if (claimed) {
                throw new IllegalStateException(
                        "\"" + spelling + "\" already belongs to " + other.name() + ", so it cannot also mean " + name);
            }
        }
    }

    private static SponsorCatalogEntry toEntry(SponsorCatalogEntity stored) {
        return new SponsorCatalogEntry(
                stored.getName(),
                new ArrayList<>(stored.aliasList()),
                new ArrayList<>(stored.semanticTermList()),
                stored.getMinScore(),
                stored.getUpdatedAt());
    }

    private static List<String> cleanedTerms(List<String> terms, String field) {
        Set<String> cleaned = new LinkedHashSet<>();
        if (terms != null) {
            for (String term : terms) {
                String value = clean(term);
                if (value == null) {
                    continue;
                }
                if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                    throw new IllegalArgumentException(field + " must not contain line breaks");
                }
                cleaned.add(value);
            }
        }
        List<String> result = new ArrayList<>(cleaned);
        if (SponsorCatalogEntity.storedLength(result) > SponsorCatalogEntity.MAX_STORED_TERMS) {
            throw new IllegalArgumentException(field + " are too long: at most " + SponsorCatalogEntity.MAX_STORED_TERMS
                    + " characters in all, one per line");
        }
        return result;
    }

    static String normalize(String value) {
        String cleaned = clean(value);
        return cleaned == null ? "" : cleaned.toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
