# StreamSense-Production Architecture

## High-level diagram

```mermaid
flowchart LR
  %% ---- Client layer ----
  FE[frontend<br/>React + TS + Apollo] -->|GraphQL queries/subscriptions| GW[api-gateway<br/>GraphQL + routing]

  %% ---- Core platform ----
  GW -->|REST| CHAT[chat-service]
  GW -->|REST| SENT[sentiment-service]
  GW -->|REST| VIDEO[video-service]
  GW -->|REST| RECO[recommendation-service]

  %% ---- Discovery + config ----
  EUREKA[eureka-server<br/>service discovery]
  CONFIG[config-server<br/>central config]

  CHAT -.->|register| EUREKA
  SENT -.->|register| EUREKA
  VIDEO -.->|register| EUREKA
  RECO -.->|register| EUREKA
  GW   -.->|register| EUREKA

  CHAT -->|fetch config| CONFIG
  SENT -->|fetch config| CONFIG
  VIDEO -->|fetch config| CONFIG
  RECO -->|fetch config| CONFIG
  GW   -->|fetch config| CONFIG

  CONFIG -->|serves YAML| CREPO[config-repo<br/>config-server/config-repo/*.yml]

  %% ---- Event backbone ----
  subgraph KAFKA[kafka-cluster]
    TCHAT[(stream.chat.messages)]
    TSENT[(stream.sentiment.events)]
    TVID[(stream.video.frames)]
    TSPON[(stream.sponsor.detections)]
  end

  CHAT -->|produce| TCHAT
  SENT -->|consume| TCHAT
  SENT -->|produce| TSENT

  VIDEO -->|produce| TVID
  VIDEO -->|produce| TSPON

  %% ---- ML ----
  VIDEO -->|HTTP (circuit breaker)| ML[ml-engine<br/>Python inference]

  %% ---- Observability ----
  subgraph OBS[monitoring]
    PROM[Prometheus]
    GRAF[Grafana]
    ZIP[Zipkin]
  end

  GW -.->|metrics/traces| OBS
  CHAT -.->|metrics/traces| OBS
  SENT -.->|metrics/traces| OBS
  VIDEO -.->|metrics/traces| OBS
  RECO -.->|metrics/traces| OBS
```
## Services and their responsibilities

eureka-server: Service discovery registry for all Spring services.

config-server: Central configuration service (native file backend during local/dev).

api-gateway: Single entry point; routing + GraphQL (queries/subscriptions).

chat-service: Chat ingestion; produces stream.chat.messages.

sentiment-service: Consumes chat; produces stream.sentiment.events (stubbed early, real later).

video-service: Frame ingestion; sponsor detection via ML; produces stream.sponsor.detections.

recommendation-service: Aggregates signals into recommendation outputs.

kafka-cluster: Event backbone enabling decoupled async pipelines.

ml-engine: Containerized Python inference services (sentiment + sponsor).

monitoring: Prometheus + Grafana + Zipkin for metrics, dashboards, and tracing.

frontend: React dashboard consuming GraphQL queries/subscriptions.