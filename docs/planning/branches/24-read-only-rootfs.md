# hardening/24-read-only-rootfs

Item 24 of `docs/planning/production-hardening-followups.md` (follow-ups from branches 04 and 14): every container in the cluster runs on a read-only root filesystem with its writable paths declared, every pod carries the non-root and seccomp settings at pod level, and the Trivy misconfiguration gate now fails on HIGH. Stacked on `hardening/23-frontend-error-surfacing`.

## What was wrong

Branch 04 gave every container a non-root `securityContext` that drops all capabilities, but only the frontend (branch 12a) had `readOnlyRootFilesystem: true`. Trivy reported the missing setting and the missing pod-level security context as HIGH on every workload, which is why branch 14 could only gate on CRITICAL and had to log HIGH findings instead of failing on them. A writable root filesystem lets a compromised process drop files next to the application, and nothing documented which paths each image actually needs to write.

## What changed

- **`readOnlyRootFilesystem: true` on all 54 containers** under `k8s/` (main containers, the busybox wait-for init containers, and the `kafka-topics-init` Job), 18 Deployments, the Kafka StatefulSet, and the Job. Compose is untouched: the images are proven read-only-safe there too, but the local stack stays as it was so a developer's `make up` behaves the same.
- **Pod-level `securityContext`** on every workload: `runAsNonRoot: true` and `seccompProfile: RuntimeDefault` (the existing `fsGroup` entries stay). This is what Trivy's KSV-0118 asks for; the per-container settings already said the same, so no pod runs differently.
- **Scratch mounts, one `emptyDir` per path, named `scratch-<path>`**, only where the image proved it needs one:

  | Image | Writable paths | How it was proven |
  |---|---|---|
  | Java services (8) | `/tmp` | eureka-server and config-server images run with `--read-only --tmpfs /tmp`: healthy in 8 s and 2 s, no read-only errors |
  | ml-engine, video-capture-service | `/tmp` (`/models` is already a PVC) | images run with `--read-only --tmpfs /tmp`: live and ready, uid 10001 |
  | Postgres | `/tmp`, `/var/run/postgresql` | `pg_isready` after 4 s |
  | Redis | none | `PING` after 2 s |
  | Kafka | `/tmp`, `/etc/kafka`, `/var/log/kafka` | with `/tmp` alone the entrypoint stops at `dub path /etc/kafka/ writable` (it templates `kafka.properties` there); with `/etc/kafka` the JVM then fails on `-Xlog:gc*:file=/var/log/kafka/kafkaServer-gc.log` (read-only file system); with all three the broker starts and `kafka-topics --list` answers after 2 s |
  | kafka-topics-init Job | `/tmp` | the `kafka-topics` CLI run read-only with only `/tmp` reports connection retries, never a write error |
  | MinIO | `/tmp` | `/minio/health/live` after 4 s |
  | Grafana | `/tmp` (`/var/lib/grafana` is already a PVC) | `/api/health` after 12 s |
  | Prometheus | none (`/prometheus` is already a PVC) | `/-/ready` after 2 s |
  | Zipkin | `/tmp` | `/health` after 8 s |
  | kafka-exporter | none | starts read-only; exits only because there is no broker to reach |
  | busybox init containers | none | `sh -c` runs read-only |

  Every third-party check ran the exact digest pinned in the manifests. Mounting an `emptyDir` over `/etc/kafka` hides the sample `*.properties` the image ships; the Confluent entrypoint generates the one file the broker reads, so nothing is lost. The Kafka pod already sets `fsGroup: 1000`, and Kubernetes creates `emptyDir` directories world-writable, so uid 1000 can write to them (a Docker `--tmpfs` inherits the image directory's mode instead, which is why the local check needed `uid=1000`).
- **CI**: the two Trivy misconfiguration steps (a HIGH,CRITICAL report at exit-code 0 and a CRITICAL gate) collapse into one HIGH,CRITICAL gate that fails the job.
- **CLAUDE.md** and `docs/kubernetes-kind.md` state the rule: a new writable path is a `scratch-<path>` `emptyDir`, added only after `docker run --read-only --tmpfs <path>` proves the image needs it.

The edits were applied by a script that hooks `readOnlyRootFilesystem` onto each container's existing `allowPrivilegeEscalation: false` line, so the count of the two settings matches exactly (54 and 54) and every comment in the manifests is preserved.

## Deliberately left alone

- The busybox wait-for init containers stay (the plan considered dropping them). They now run read-only too, and removing them would trade an ordered start for crash-loop restarts while config-server and Kafka come up; a later branch can revisit that once the NetworkPolicy work (25) settles the dependency graph.
- Compose keeps writable containers: the read-only proof is recorded above, but switching `docker-compose.yml` to `read_only: true` is a separate, testable change and the local stack is not the security boundary.
- No `sizeLimit` on the scratch `emptyDir`s: the JVM and Python services write only small temp files, and a limit would need per-service measurement first.
- The namespace's Pod Security admission level stays `baseline` (warn on `restricted`); with this branch every workload should pass `restricted`, so raising the enforce level is a one-line follow-up once a cluster run confirms it.

## Verification

| Check | Command | Result |
|---|---|---|
| Kubernetes renders | `kubectl kustomize .` (with a placeholder `k8s/secrets/streamsense.env`) | 56 resources |
| Schema validation | `kubeconform -strict -summary` on the rendered output | 56 valid, 0 invalid, 0 errors |
| kube-linter | `kube-linter lint` on the rendered output | "No lint errors found!" (the `no-read-only-root-fs` findings from branches 12a and 12b are gone) |
| Trivy misconfiguration gate at HIGH | `trivy fs --scanners misconfig --severity HIGH,CRITICAL --exit-code 1 --ignorefile .trivyignore .` (`aquasec/trivy:0.69.0`) | exit 0; the first run found one HIGH (the topics Job's pod spec, whose `restartPolicy` line preceded `containers` and defeated the script's anchor), fixed and re-run clean |
| Workflow syntax | `actionlint .github/workflows/ci.yml` | clean |
| Read-only image checks | `docker run --read-only --tmpfs <paths> <pinned image>` and the probes in the table above | every image healthy with only the listed paths |

## Manual checks for the reviewer

1. `kubectl apply -k .` on a kind cluster, then `kubectl -n streamsense get pods -w`: every pod reaches Ready, including `kafka-0` (watch for `dub path /etc/kafka/ writable` or `Read-only file system` in `kubectl logs kafka-0` if it does not) and the `kafka-topics-init` Job completes.
2. `kubectl -n streamsense exec deploy/api-gateway -- sh -c 'touch /app/x'` fails with "Read-only file system" while `touch /tmp/x` succeeds.
3. `kubectl -n streamsense get pods -o yaml | grep -c 'readOnlyRootFilesystem: true'` counts every container.
4. `kubectl label ns streamsense pod-security.kubernetes.io/enforce=restricted --dry-run=server` reports no violations; if so, the follow-up below is a one-line change.

## Follow-ups

- Raise the namespace's Pod Security admission `enforce` level from `baseline` to `restricted` after a cluster run confirms every pod admits.
- Mirror the read-only setting into `docker-compose.yml` (`read_only: true` with matching `tmpfs:` entries) once the Compose smoke job can prove it.
