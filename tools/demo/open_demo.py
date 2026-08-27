#!/usr/bin/env python3
"""Print and optionally open the main StreamSense demo surfaces."""

from __future__ import annotations

import argparse
import webbrowser


URLS = [
    ("Frontend", "http://localhost:3000"),
    ("GraphQL", "http://localhost:8080/graphql"),
    ("Gateway health", "http://localhost:8080/actuator/health"),
    ("Eureka", "http://localhost:8761"),
    ("Prometheus", "http://localhost:9090"),
    ("Grafana", "http://localhost:3001", "admin/admin"),
    ("Zipkin", "http://localhost:9411"),
    ("Kafka UI", "http://localhost:8088"),
]


def main() -> int:
    parser = argparse.ArgumentParser(description="Open StreamSense demo URLs.")
    parser.add_argument("--print-only", action="store_true", help="only print URLs")
    args = parser.parse_args()

    print("StreamSense demo URLs:")
    for entry in URLS:
        label, url = entry[0], entry[1]
        suffix = f" ({entry[2]})" if len(entry) > 2 else ""
        print(f"- {label}: {url}{suffix}")

    print("\nUseful demo commands:")
    print("- Seed data: python tools/demo/seed_demo.py")
    print("- Smoke test: python tools/smoke/compose_smoke.py --start-compose --teardown")
    print("- Relax rate limits for benchmark: STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=false docker compose up -d api-gateway")

    if not args.print_only:
        for label, url, *_ in URLS:
            if label in {"Frontend", "Grafana", "Zipkin"}:
                webbrowser.open(url)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
