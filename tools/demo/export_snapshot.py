#!/usr/bin/env python3
"""Export the sealed snapshot the console's /demo route runs on.

Runs the console's own GraphQL operations (read from frontend/src/graphql/queries.ts, with __typename
added the way Apollo's cache does) and REST reads against a gateway, for one channel and one sponsor,
and writes frontend/src/demo/snapshot.json. Chat usernames are pseudonymised; sessions shorter than
--min-session-minutes are dropped everywhere they appear, so a demo does not show capture restarts.

    python tools/demo/export_snapshot.py --gateway http://localhost:8080 --streamer redbull-testing --sponsor "Red Bull"
    python tools/demo/export_snapshot.py --gateway https://streamsense.dev --token "$TOKEN" ...

Standard library only. Deterministic for the same data, so a refresh is a reviewable diff.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
QUERIES = ROOT / "frontend/src/graphql/queries.ts"
OUTPUT = ROOT / "frontend/src/demo/snapshot.json"

# Visible handles for chat users: stable per real name, plausible, and not anyone's.
ADJECTIVES = ["quiet", "brisk", "amber", "lucky", "north", "velvet", "pixel", "rapid", "solar", "tidal",
              "mellow", "cobalt", "ember", "frosty", "gilded", "hollow", "ivory", "jade", "keen", "lunar"]
NOUNS = ["otter", "falcon", "comet", "harbor", "maple", "pilot", "quartz", "river", "summit", "tundra",
         "walrus", "yonder", "zephyr", "beacon", "canyon", "drift", "ember", "glacier", "meadow", "orbit"]


def gql_documents() -> dict[str, tuple[str, list[str]]]:
    """Operation name -> (document with __typename on every selection, declared variable names)."""
    text = QUERIES.read_text(encoding="utf-8")
    docs = {}
    for body in re.findall(r"gql`([\s\S]*?)`", text):
        header = re.search(r"\b(query|subscription)\s+(\w+)\s*(\(([^)]*)\))?", body)
        if not header:
            continue
        name = header.group(2)
        variables = re.findall(r"\$(\w+)\s*:", header.group(4) or "")
        # Apollo's cache adds __typename to every selection set; the snapshot must carry it too.
        with_typename = re.sub(r"\{(?!\s*__typename)", "{ __typename ", body)
        docs[name] = (with_typename, variables)
    return docs


class Gateway:
    def __init__(self, base: str, token: str | None):
        self.base = base.rstrip("/")
        self.token = token

    def _headers(self) -> dict[str, str]:
        headers = {"Accept": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        return headers

    def graphql(self, document: str, variables: dict) -> dict:
        payload = json.dumps({"query": document, "variables": variables}).encode("utf-8")
        request = urllib.request.Request(
            self.base + "/graphql", data=payload, headers={**self._headers(), "Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=60) as response:
            body = json.load(response)
        if body.get("errors"):
            raise RuntimeError(f"{variables}: {body['errors'][0].get('message')}")
        return body["data"]

    def rest(self, path: str) -> tuple[int, object]:
        request = urllib.request.Request(self.base + path, headers=self._headers())
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            try:
                return error.code, json.load(error)
            except Exception:
                return error.code, None


def pseudonym(name: str) -> str:
    digest = hashlib.sha256(name.strip().lower().encode("utf-8")).digest()
    return f"{ADJECTIVES[digest[0] % len(ADJECTIVES)]}_{NOUNS[digest[1] % len(NOUNS)]}{digest[2] % 100:02d}"


def pseudonymise(value, names: dict[str, str]):
    """Replace every `user`/`username` string in a JSON tree with a stable pseudonym."""
    if isinstance(value, dict):
        out = {}
        for key, item in value.items():
            if key in ("user", "username") and isinstance(item, str) and item:
                out[key] = names.setdefault(item, pseudonym(item))
            else:
                out[key] = pseudonymise(item, names)
        return out
    if isinstance(value, list):
        return [pseudonymise(item, names) for item in value]
    return value


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Export the console's sealed demo snapshot.")
    parser.add_argument("--gateway", default="http://localhost:8080")
    parser.add_argument("--token", default=None, help="bearer token when the gateway has auth on")
    parser.add_argument("--streamer", required=True)
    parser.add_argument("--sponsor", required=True)
    parser.add_argument("--min-session-minutes", type=int, default=10)
    parser.add_argument("--max-sessions", type=int, default=4, help="newest sessions to keep; 0 keeps all")
    parser.add_argument("--feed-limit", type=int, default=24)
    parser.add_argument("--output", default=str(OUTPUT))
    args = parser.parse_args(argv)

    gateway = Gateway(args.gateway, args.token)
    docs = gql_documents()
    streamer, sponsor = args.streamer, args.sponsor
    names: dict[str, str] = {}
    graphql: dict[str, list[dict]] = {}

    def run(name: str, **variables) -> dict:
        document, declared = docs[name]
        passed = {key: value for key, value in variables.items() if key in declared}
        data = gateway.graphql(document, passed)
        graphql.setdefault(name, []).append({"variables": passed, "data": data})
        return data

    run("Health")
    sessions = run("Sessions", streamer=streamer, limit=50)["sessions"]
    keep = [s for s in sessions if s["durationMs"] >= args.min_session_minutes * 60_000]
    if args.max_sessions and len(keep) > args.max_sessions:
        # The newest ones, so the demo's history reads like a recent month rather than an archive.
        keep = keep[: args.max_sessions]
    kept_ids = {s["id"] for s in keep}
    graphql["Sessions"][-1]["data"]["sessions"] = keep
    print(f"sessions: {len(sessions)} found, {len(keep)} kept", file=sys.stderr)

    deals = run("Deals", streamer=streamer, limit=20)["deals"]
    for deal in deals:
        run("Deal", id=deal["id"])
        summary = run("DealSummary", id=deal["id"])
        summary["dealSummary"]["sessions"] = [
            entry for entry in summary["dealSummary"]["sessions"] if entry["session"]["id"] in kept_ids]

    for session in keep:
        run("Session", id=session["id"])
        for chosen in (None, sponsor):
            run("SessionSummary", sessionId=session["id"], sponsor=chosen)
            run("SponsorMoments", sessionId=session["id"], sponsor=chosen)

    limit = args.feed_limit
    run("RecentSentiment", streamer=streamer, limit=limit)
    run("RecentTranscriptSegments", streamer=streamer, limit=limit)
    run("RecentTranscriptSentiment", streamer=streamer, limit=limit)
    run("SponsorDetections", streamer=streamer, limit=limit)
    for chosen in (None, sponsor):
        run("RecentSponsorSentiment", streamer=streamer, sponsor=chosen, limit=limit)
        run("RecentSponsorTranscriptSentiment", streamer=streamer, sponsor=chosen, limit=limit)
    run("StreamAnalytics", streamer=streamer, windowMinutes=15, bucketSeconds=60)

    rest: dict[str, dict] = {}
    for path in (
        "/api/chat/twitch/status",
        "/api/video/capture/status",
        f"/api/sentiment/transcript/recent?streamer={urllib.parse.quote(streamer)}&limit=10",
        f"/api/sentiment/relevance/sponsors/{urllib.parse.quote(streamer)}",
    ):
        status, body = gateway.rest(path)
        rest[f"GET {path}"] = {"status": status, "body": body}

    # The live chat feed has no history query; the sentiment feed carries the same lines.
    chat = [
        {
            "__typename": "ChatMessageEvent",
            "eventId": event["sentimentEventId"],
            "streamer": event["streamer"],
            "user": event["user"],
            "message": event["message"],
            "timestamp": event["chatTimestamp"],
        }
        for event in graphql["RecentSentiment"][0]["data"]["recentSentiment"]
        if event.get("message")
    ]

    snapshot = {
        "meta": {
            "channel": streamer,
            "sponsor": sponsor,
            "dealId": deals[0]["id"] if deals else None,
            "latestSessionId": keep[0]["id"] if keep else None,
            "gateway": args.gateway,
        },
        "graphql": graphql,
        "rest": rest,
        "chat": chat,
    }
    snapshot = pseudonymise(snapshot, names)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(snapshot, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    print(f"wrote {output} ({output.stat().st_size // 1024} KB, {len(names)} usernames pseudonymised)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
