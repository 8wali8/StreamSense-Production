-- What is known about a sponsor across every channel: its name, the spellings it goes by, the terms
-- that mean it, and the score a line needs. A deal that names the sponsor by name or alias borrows
-- these into the channel's profile. Seeded from config on first start; operators own it afterwards.
-- Terms are stored one per line, as in sponsor_relevance_profiles. A removed entry keeps its row
-- with removed set, so the configured sponsors do not bring it back at the next start.
CREATE TABLE sponsor_catalog (
    sponsor_key VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    aliases VARCHAR(4000) NOT NULL DEFAULT '',
    semantic_terms VARCHAR(4000) NOT NULL DEFAULT '',
    min_score DOUBLE PRECISION,
    removed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at BIGINT NOT NULL
);

-- A profile's min_score is the channel's own override from here on; null means the catalog entry's
-- score, or the configured default. Until now every profile stored the configured default (0.50)
-- when nobody had chosen a score, so those rows are cleared: a score nobody chose is not an override.
UPDATE sponsor_relevance_profiles SET min_score = NULL WHERE min_score = 0.5;
