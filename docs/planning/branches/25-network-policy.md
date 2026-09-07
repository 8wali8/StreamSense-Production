# hardening/25-network-policy

Item 25 of `docs/planning/production-hardening-followups.md` (follow-up from branch 04): the `streamsense` namespace is default-deny for ingress and egress, each workload gets a policy listing exactly the peers it needs, a CI check keeps the policies in step with the dependencies the manifests declare, and the frontend gets a PodDisruptionBudget. Stacked on `hardening/24-read-only-rootfs`.

## What was wrong

Branch 04 hardened every pod's own security context, but any pod could still open a connection to any other pod or to the internet: a compromised ml-engine could reach Postgres, the frontend's nginx could reach Kafka, and nothing recorded which service is allowed to talk to which. Kubernetes also had no `PodDisruptionBudget`, so a node drain could take the console down with nothing objecting.

## What changed

- **`k8s/network/network-policies.yaml`** (added to `k8s/kustomization.yaml`): a `default-deny-all` policy over every pod, then one policy per workload (20: 18 Deployments, the Kafka StatefulSet, and the topics Job) with `policyTypes: [Ingress, Egress]`, ingress `from` and egress `to` rules per port, egress to `kube-dns` in `kube-system` on 53 for everyone, and `namespaceSelector` rules for the `ingress-nginx` namespace on the four Ingress backends (frontend, api-gateway, grafana, zipkin). Three workloads may reach public addresses (`0.0.0.0/0` minus the three private ranges): chat-service on 443, 6697, and 6667 (Twitch IRC and `gql.twitch.tv` replay), video-capture-service on 443 (Twitch HLS through streamlink), ml-engine on 443 (a Hugging Face download when the model cache is empty). Each policy carries a one-line comment saying why its peers are what they are.
- **Dependency matrix** (source → destination:port), derived from the manifests and config and checked into the policies:

  | Source | May reach |
  |---|---|
  | six Spring clients (gateway, chat, recommendation, sentiment, video, analytics) | config-server 8888, eureka-server 8761, zipkin 9411 |
  | api-gateway | + redis 6379, kafka 9092, chat 8081, recommendation 8082, sentiment 8083, video 8084, video-capture 8090, analytics 8085, ml-engine 8000 (the proxied `POST /ml/segment` route) |
  | chat-service | + kafka 9092, internet 443/6697/6667 |
  | recommendation-service | + sentiment 8083, video 8084 |
  | sentiment-service, video-service | + kafka 9092, postgres 5432, redis 6379, ml-engine 8000 |
  | analytics-service | + kafka 9092, postgres 5432 |
  | video-capture-service | kafka 9092, minio 9000, ml-engine 8000, internet 443 |
  | ml-engine | minio 9000, internet 443 |
  | frontend | api-gateway 8080 (every nginx `proxy_pass` line, `/ml/segment` included, targets the gateway) |
  | config-server | eureka-server 8761 (its init wait), zipkin 9411 |
  | eureka-server | zipkin 9411 |
  | kafka | itself on 29093 (KRaft controller) and 9092 |
  | kafka-topics-init, kafka-exporter | kafka 9092 |
  | prometheus | itself and every scrape target in `k8s/config/prometheus-config.yaml` |
  | grafana | prometheus 9090 |
  | postgres, redis, minio, zipkin | nothing but DNS |
  | ingress-nginx namespace | frontend 8080, api-gateway 8080, grafana 3000, zipkin 9411 |

  Ingress rules are the exact inverse of that table, so every allowed edge is written on both ends.
- **`tools/k8s/check_network_policies.py`**, run by CI right after `kubectl kustomize`: it re-derives the edges from the manifests (init-container `nc -z` and `wget http://…` waits, container env values), `config-server/config-repo/*.yml` URLs (`application.yml` applies to all six clients), the Prometheus static targets, `frontend/nginx.conf` `proxy_pass` lines, and the Ingress backends, resolves each destination Service to its `app` label, and fails unless the source policy allows the egress and the destination policy allows the ingress. It also fails when a pod template has no `app` label. Sixty-four edges today, all covered; deleting one rule (postgres from analytics-service's egress) makes it fail with `analytics-service -> postgres:5432: egress not allowed by policy analytics-service`.
- **`kafka-topics-init` Job** gets `app: kafka-topics-init` on its pod template so a policy can select it (without one, default-deny would leave the Job unable to reach Kafka).
- **frontend**: two replicas with a preferred anti-affinity across nodes (preferred, not required, because kind has one node), and a `PodDisruptionBudget` with `minAvailable: 1` and `unhealthyPodEvictionPolicy: AlwaysAllow`.
- **`k8s/kind/cluster-calico.yaml`** and a new section in `docs/kubernetes-kind.md`: kind's default CNI ignores `NetworkPolicy`, so the guide shows how to create the cluster without it and install Calico v3.32.2 (the operator manifests from Calico's kind guide) before applying StreamSense, plus a two-command proof that the policies bite.
- **CLAUDE.md** and the follow-ups plan describe the rule: a new dependency means a new rule on both ends in the same change.

## Deliberately left alone

- **The gateway keeps one replica and gets no PodDisruptionBudget.** Its five Kafka consumers share `group-id: api-gateway-subscriptions`; a second replica would take half the partitions, and a WebSocket subscriber on either pod would miss the events consumed by the other. Giving each replica its own group (a per-instance suffix) is a gateway change with its own test, listed below. A PDB with `minAvailable: 1` on one replica would only block drains, so none is declared.
- `k8s/kind/cluster.yaml` is unchanged: the documented flow still works on plain kind, with the policies accepted but not enforced. Running the Calico flow was not possible here (no kind on this machine), so the enforcement proof is the documented manual check.
- MinIO's console port 9001 and Prometheus's UI have no ingress rule: nothing in the cluster calls them, and `kubectl port-forward` traffic comes from the node, which the usual CNIs do not subject to policy.
- Compose is untouched: the policies are a Kubernetes concept, and the Compose network already only exposes the published ports.

## Verification

| Check | Command | Result |
|---|---|---|
| Policies cover every declared dependency | `python3 tools/k8s/check_network_policies.py` | OK: 64 edges, 20 policies; negative test (one rule removed) fails naming the edge |
| Compose agreement | every `depends_on` edge in `docker-compose.yml` (minus `kafka-topics-init`, ordering only, and `kafka-ui`, Compose-only) is in the source's egress policy | none missing |
| Kubernetes renders | `kubectl kustomize .` | 78 resources (21 NetworkPolicies, 1 PodDisruptionBudget) |
| Schema validation | `kubeconform -strict -summary` on the rendered output | 78 valid, 0 invalid, 0 errors |
| kube-linter | `kube-linter lint` on the rendered output | "No lint errors found!" (its `no-anti-affinity` and `pdb-unhealthy-pod-eviction-policy` findings on the first draft are what added the anti-affinity and the eviction policy) |
| Trivy misconfiguration gate | `trivy fs --scanners misconfig --severity HIGH,CRITICAL --exit-code 1` (`aquasec/trivy:0.69.0`) | exit 0 |
| Workflow syntax | `actionlint .github/workflows/ci.yml` | clean |
| Tool lint | `ruff check` / `ruff format --line-length 120` (ruff 0.16.6) on the new script | clean |

## Manual checks for the reviewer

1. Plain kind (`k8s/kind/cluster.yaml`): `kubectl apply -k .` still brings every pod to Ready and the topics Job completes; `kubectl -n streamsense get networkpolicy` lists 21.
2. Calico kind (`k8s/kind/cluster-calico.yaml` plus the four Calico commands in the guide): the same, and `kubectl -n streamsense exec deploy/frontend -- wget -qO- --timeout=3 http://postgres:5432` times out while `… http://api-gateway:8080/actuator/health` answers. The console at `http://streamsense.local` loads and its live feeds update, which exercises ingress-nginx → frontend → gateway → Kafka/Redis and the REST fan-out.
3. `kubectl -n streamsense get pdb frontend` shows `ALLOWED DISRUPTIONS 1` once both replicas are Ready.
4. Remove any `- to:` block from a policy and run `python3 tools/k8s/check_network_policies.py`: it names the missing edge.

## Follow-ups

- Give the gateway a per-instance Kafka consumer group for its subscription consumers, then run two replicas and add a PodDisruptionBudget for it.
- Once a Calico cluster run confirms every pod admits under the policies, consider `restricted` Pod Security enforcement together with branch 24's follow-up.
