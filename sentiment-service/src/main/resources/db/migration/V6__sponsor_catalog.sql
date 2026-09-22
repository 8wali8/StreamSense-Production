-- What is known about a sponsor across every channel: its name, the spellings it goes by, the terms
-- that mean it, and the score a line needs. A deal that names the sponsor by name or alias borrows
-- these into the channel's profile. Seeded from config on first start; operators own it afterwards.
-- Terms are stored one per line, as in sponsor_relevance_profiles.
CREATE TABLE sponsor_catalog (
    sponsor_key VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    aliases VARCHAR(4000) NOT NULL DEFAULT '',
    semantic_terms VARCHAR(4000) NOT NULL DEFAULT '',
    min_score DOUBLE PRECISION,
    updated_at BIGINT NOT NULL
);
