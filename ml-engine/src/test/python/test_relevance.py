from app.relevance import (
    SponsorRelevanceInput,
    SponsorRelevanceResult,
    contains_term,
    direct_match,
    normalize_text,
)
from conftest import FakeRelevance


def test_relevance_endpoint_returns_valid_shape(make_client):
    client, registry = make_client()
    registry.relevance = FakeRelevance(
        SponsorRelevanceResult(True, "Nike", ["shoes"], 0.74, "semantic-similarity", "test-relevance-v1")
    )

    response = client.post(
        "/ml/relevance",
        json={
            "eventId": "evt-1",
            "streamer": "test",
            "text": "those shoes are clean",
            "sponsor": "Nike",
            "aliases": ["swoosh"],
            "semanticTerms": ["shoes"],
            "minScore": 0.5,
        },
    )

    assert response.status_code == 200
    assert response.json() == {
        "sponsorRelevant": True,
        "matchedSponsor": "Nike",
        "matchedTerms": ["shoes"],
        "relevanceScore": 0.74,
        "relevanceReason": "semantic-similarity",
        "modelVersion": "test-relevance-v1",
    }
    sent = registry.relevance.calls[0]
    assert sent.aliases == ["swoosh"]
    assert sent.semantic_terms == ["shoes"]
    assert sent.min_score == 0.5


def test_direct_backend_end_to_end(real_lightweight_client):
    body = real_lightweight_client.post(
        "/ml/relevance",
        json={"streamer": "t", "text": "That NikePartner segment was good", "sponsor": "Nike"},
    ).json()

    assert body["sponsorRelevant"] is True
    assert body["relevanceReason"] == "direct-match"
    assert body["modelVersion"] == "direct-relevance-v1"


def test_direct_match_handles_aliases_and_suffixes():
    result = direct_match(
        SponsorRelevanceInput(
            text="That NikePartner segment was actually good", sponsor="Nike", aliases=["swoosh"], semantic_terms=[]
        ),
        0.5,
    )

    assert result.sponsor_relevant is True
    assert result.matched_terms == ["nike"]
    assert result.relevance_reason == "direct-match"


def test_semantic_term_direct_match_is_relevant():
    result = direct_match(
        SponsorRelevanceInput(
            text="Those running shoes look comfortable",
            sponsor="Nike",
            aliases=[],
            semantic_terms=["running shoes", "apparel"],
        ),
        0.5,
    )

    assert result.sponsor_relevant is True
    assert result.matched_terms == ["running shoes"]
    assert result.relevance_score == 0.82
    assert result.relevance_reason == "semantic-term-match"


def test_unrelated_text_is_not_directly_relevant():
    result = direct_match(
        SponsorRelevanceInput(
            text="the map rotation is weird today", sponsor="Nike", aliases=["swoosh"], semantic_terms=["shoes"]
        ),
        0.5,
    )

    assert result.sponsor_relevant is False
    assert result.matched_terms == []


def test_text_normalization_supports_hashtags_mentions_and_punctuation():
    assert normalize_text("Check #Nike, @Swoosh!!!") == "check nike swoosh"
    assert contains_term(normalize_text("#PrimePartner"), "prime") is True
