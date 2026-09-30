package com.streamsense.sentimentservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/** A catalog entry as it should be from now on: the whole entry, keyed by the name (case does not matter). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SponsorCatalogUpdateRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    private List<String> aliases = new ArrayList<>();
    private List<String> semanticTerms = new ArrayList<>();
    private Double minScore;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases != null ? aliases : new ArrayList<>();
    }

    public List<String> getSemanticTerms() {
        return semanticTerms;
    }

    public void setSemanticTerms(List<String> semanticTerms) {
        this.semanticTerms = semanticTerms != null ? semanticTerms : new ArrayList<>();
    }

    public Double getMinScore() {
        return minScore;
    }

    public void setMinScore(Double minScore) {
        this.minScore = minScore;
    }
}
