import app.main as main_module
from app.main import app
from app.relevance import (
    SponsorRelevanceInput,
    SponsorRelevanceResult,
    contains_term,
    direct_match,
    normalize_text,
)
from fastapi.testclient import TestClient

client = TestClient(app)


def test_relevance_endpoint_returns_valid_shape(monkeypatch):
    monkeypatch.setattr(
        main_module,
        "analyze_relevance",
        lambda request: SponsorRelevanceResult(
            True,
            "Nike",
            ["shoes"],
            0.74,
            "semantic-similarity",
            "test-relevance-v1",
        ),
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
    body = response.json()
    assert body == {
        "sponsorRelevant": True,
        "matchedSponsor": "Nike",
        "matchedTerms": ["shoes"],
        "relevanceScore": 0.74,
        "relevanceReason": "semantic-similarity",
        "modelVersion": "test-relevance-v1",
    }


def test_direct_match_handles_aliases_and_suffixes():
    result = direct_match(
        SponsorRelevanceInput(
            text="That NikePartner segment was actually good",
            sponsor="Nike",
            aliases=["swoosh"],
            semantic_terms=[],
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
            text="the map rotation is weird today",
            sponsor="Nike",
            aliases=["swoosh"],
            semantic_terms=["shoes"],
        ),
        0.5,
    )

    assert result.sponsor_relevant is False
    assert result.matched_terms == []


def test_text_normalization_supports_hashtags_mentions_and_punctuation():
    assert normalize_text("Check #Nike, @Swoosh!!!") == "check nike swoosh"
    assert contains_term(normalize_text("#PrimePartner"), "prime") is True
