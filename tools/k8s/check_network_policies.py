#!/usr/bin/env python3
"""Cross-check k8s/network/network-policies.yaml against the dependencies the manifests actually declare.

Every network edge a pod needs is written down somewhere already: an init container's `nc -z host port`
or `wget http://host:port/...` wait, a container env value such as `http://minio:9000` or `kafka:9092`,
a URL in config-server/config-repo/<service>.yml, a Prometheus scrape target, a `proxy_pass` in
frontend/nginx.conf, or an Ingress backend. This script collects those edges and checks that the policy
for the source pod allows the egress and the policy for the destination pod allows the ingress. It fails
when a policy stops covering an edge, so a new dependency has to be added to the policies in the same
change. Run from the repository root; no arguments.
"""

from __future__ import annotations

import pathlib
import re
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parents[2]
NAMESPACE = "streamsense"
INGRESS_NAMESPACE = "ingress-nginx"

URL = re.compile(r"(?:https?|jdbc:postgresql)://([a-z][a-z0-9-]*):(\d+)")
NC = re.compile(r"nc -z ([a-z][a-z0-9-]*) (\d+)")
HOST_PORT = re.compile(r"^([a-z][a-z0-9-]*):(\d+)$")


def load_all(path: pathlib.Path):
    return [d for d in yaml.safe_load_all(path.read_text(encoding="utf-8")) if d]


def collect_edges() -> tuple[set[tuple[str, str, int]], dict[str, str]]:
    """Returns (edges, service->app) where an edge is (source app, destination service name, port)."""
    edges: set[tuple[str, str, int]] = set()
    service_app: dict[str, str] = {}
    workloads = []
    for path in sorted((ROOT / "k8s").rglob("*.yaml")):
        for doc in load_all(path):
            kind = doc.get("kind")
            if kind == "Service":
                selector = doc["spec"].get("selector") or {}
                if "app" in selector:
                    service_app[doc["metadata"]["name"]] = selector["app"]
            elif kind in ("Deployment", "StatefulSet", "Job"):
                workloads.append(doc)
            elif kind == "Ingress":
                for rule in doc["spec"]["rules"]:
                    for p in rule["http"]["paths"]:
                        svc = p["backend"]["service"]
                        edges.add((INGRESS_NAMESPACE, svc["name"], int(svc["port"]["number"])))
            elif kind == "ConfigMap" and doc["metadata"]["name"] == "prometheus-config":
                for text in doc["data"].values():
                    cfg = yaml.safe_load(text)
                    for job in cfg.get("scrape_configs", []):
                        for sc in job.get("static_configs", []):
                            for target in sc.get("targets", []):
                                m = HOST_PORT.match(target)
                                if m:
                                    edges.add(("prometheus", m.group(1), int(m.group(2))))
    for doc in workloads:
        template = doc["spec"]["template"]
        app = (template.get("metadata") or {}).get("labels", {}).get("app")
        if not app:
            sys.exit(
                f"{doc['kind']} {doc['metadata']['name']} has no app label on its pod template; the policies select on it"
            )
        spec = template["spec"]
        for container in (spec.get("initContainers") or []) + spec["containers"]:
            texts = list(container.get("command") or []) + list(container.get("args") or [])
            texts += [str(e.get("value")) for e in (container.get("env") or []) if e.get("value")]
            for text in texts:
                for host, port in URL.findall(text) + NC.findall(text):
                    edges.add((app, host, int(port)))
                m = HOST_PORT.match(text)
                if m:
                    edges.add((app, m.group(1), int(m.group(2))))
    # config-server serves config-repo/<service>.yml to the service of that name; application.yml to all Spring clients
    repo = ROOT / "config-server/config-repo"
    spring_clients = [p.stem for p in repo.glob("*.yml") if p.stem not in ("application", "eureka-server")]
    for path in repo.glob("*.yml"):
        text = path.read_text(encoding="utf-8")
        found = {(h, int(p)) for h, p in URL.findall(text)}
        if re.search(r"\$\{REDIS_HOST:redis\}", text):
            found.add(("redis", 6379))
        if "KAFKA_BOOTSTRAP_SERVERS:kafka:9092" in text:
            found.add(("kafka", 9092))
        apps = spring_clients if path.stem == "application" else [path.stem]
        for app in apps:
            for host, port in found:
                edges.add((app, host, port))
    for host, port in URL.findall((ROOT / "frontend/nginx.conf").read_text(encoding="utf-8")):
        edges.add(("frontend", host, int(port)))
    return edges, service_app


def load_policies() -> dict[str, dict]:
    policies = {}
    for doc in load_all(ROOT / "k8s/network/network-policies.yaml"):
        sel = doc["spec"].get("podSelector") or {}
        app = (sel.get("matchLabels") or {}).get("app")
        if app:
            policies[app] = doc["spec"]
    return policies


def peer_matches(peer: dict, app: str | None, namespace: str | None) -> bool:
    """True when a `from`/`to` peer selects the given app in this namespace, or the given foreign namespace."""
    ns_sel = peer.get("namespaceSelector")
    pod_sel = peer.get("podSelector")
    if namespace:
        return bool(ns_sel) and (ns_sel.get("matchLabels") or {}).get("kubernetes.io/metadata.name") == namespace
    if ns_sel:
        return False
    return bool(pod_sel) and (pod_sel.get("matchLabels") or {}).get("app") == app


def rule_allows(rules: list[dict], key: str, app: str | None, namespace: str | None, port: int) -> bool:
    for rule in rules:
        ports = {int(p["port"]) for p in rule.get("ports", []) if str(p.get("protocol", "TCP")) == "TCP"}
        if ports and port not in ports:
            continue
        if any(peer_matches(peer, app, namespace) for peer in rule.get(key, [])):
            return True
    return False


def main() -> int:
    edges, service_app = collect_edges()
    policies = load_policies()
    failures = []
    for src, service, port in sorted(edges):
        dst = service_app.get(service)
        if dst is None:
            failures.append(f"{src} -> {service}:{port}: no Service named {service} under k8s/")
            continue
        if dst not in policies:
            failures.append(f"{src} -> {dst}:{port}: no NetworkPolicy selects app={dst}")
            continue
        if src == INGRESS_NAMESPACE:
            if not rule_allows(policies[dst].get("ingress", []), "from", None, INGRESS_NAMESPACE, port):
                failures.append(f"{INGRESS_NAMESPACE} -> {dst}:{port}: ingress not allowed by policy {dst}")
            continue
        if src not in policies:
            failures.append(f"{src} -> {dst}:{port}: no NetworkPolicy selects app={src}")
            continue
        if not rule_allows(policies[src].get("egress", []), "to", dst, None, port):
            failures.append(f"{src} -> {dst}:{port}: egress not allowed by policy {src}")
        if not rule_allows(policies[dst].get("ingress", []), "from", src, None, port):
            failures.append(f"{src} -> {dst}:{port}: ingress not allowed by policy {dst}")
    for app, spec in policies.items():
        if "Ingress" not in spec.get("policyTypes", []) or "Egress" not in spec.get("policyTypes", []):
            failures.append(f"policy {app} must declare both Ingress and Egress policy types")
    if failures:
        print("network policy check FAILED:")
        for f in failures:
            print("  -", f)
        return 1
    print(
        f"network policy check OK: {len(edges)} edges derived from the manifests, all allowed by {len(policies)} policies"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
