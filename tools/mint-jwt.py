#!/usr/bin/env python3
"""Mint an HS256 bearer token the api-gateway accepts when STREAMSENSE_GATEWAY_AUTH_ENABLED=true.

Development and verification only: there is no identity provider in the stack yet, so this is how a local
operator or smoke test obtains a token that passes signature, issuer, audience, and expiry checks.

    export STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET='a-secret-of-at-least-32-bytes-for-hs256'
    python tools/mint-jwt.py --subject demo-user

Standard library only.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
import os
import sys
import time

DEFAULT_ISSUER = "streamsense-local"
DEFAULT_AUDIENCE = "streamsense-clients"
MINIMUM_SECRET_BYTES = 32


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def _segment(value: dict) -> str:
    return _b64url(json.dumps(value, separators=(",", ":")).encode("utf-8"))


def mint(secret: str, subject: str, issuer: str, audience: str, ttl_seconds: int, now: int | None = None) -> str:
    issued_at = int(time.time()) if now is None else now
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "sub": subject,
        "iss": issuer,
        "aud": audience,
        "iat": issued_at,
        "exp": issued_at + ttl_seconds,
    }
    signing_input = f"{_segment(header)}.{_segment(payload)}"
    signature = hmac.new(secret.encode("utf-8"), signing_input.encode("ascii"), hashlib.sha256).digest()
    return f"{signing_input}.{_b64url(signature)}"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Mint an HS256 JWT for the StreamSense api-gateway.")
    parser.add_argument("--secret", default=os.environ.get("STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET"),
                        help="HMAC secret; defaults to $STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET")
    parser.add_argument("--subject", default="local-dev", help="sub claim (default: local-dev)")
    parser.add_argument("--issuer", default=DEFAULT_ISSUER, help=f"iss claim (default: {DEFAULT_ISSUER})")
    parser.add_argument("--audience", default=DEFAULT_AUDIENCE, help=f"aud claim (default: {DEFAULT_AUDIENCE})")
    parser.add_argument("--ttl-seconds", type=int, default=3600, help="seconds until exp (default: 3600)")
    args = parser.parse_args(argv)

    if not args.secret:
        parser.error("no secret: pass --secret or set STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET")
    if len(args.secret.encode("utf-8")) < MINIMUM_SECRET_BYTES:
        parser.error(f"secret must be at least {MINIMUM_SECRET_BYTES} bytes for HS256")
    if args.ttl_seconds <= 0:
        parser.error("--ttl-seconds must be positive")

    print(mint(args.secret, args.subject, args.issuer, args.audience, args.ttl_seconds))
    return 0


if __name__ == "__main__":
    sys.exit(main())
