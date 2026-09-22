package com.streamsense.sentimentservice.dto;

import java.util.List;

/**
 * A sponsor as the catalog knows it: the canonical name, the spellings it goes by, the terms that
 * mean it, and the score a line needs (null means the configured default). What a channel's profile
 * borrows when a deal names the sponsor by name or by alias.
 */
public record SponsorCatalogEntry(
        String name, List<String> aliases, List<String> semanticTerms, Double minScore, long updatedAt) {}
