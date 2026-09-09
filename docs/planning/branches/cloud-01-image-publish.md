# cloud/01-image-publish

Branch 01 of `docs/planning/cloud-hosting.md`: CI publishes the eleven service images to GitHub Container Registry so a deployment pulls a tested commit instead of building on the target machine. Stacked on `cloud/00-plan`.

## What was missing

CI built every image for the Compose smoke and threw them away. Nothing in the repository produced an artifact a VM could run; `docs/planning/branches/14-supply-chain.md` had deferred registry publishing as "needs infrastructure the repository does not have", and the Kubernetes manifests still reference locally built `sprint9` tags for the same reason.

## What changed

- **`publish-images` job** in `.github/workflows/ci.yml`, after `ci-ok`, one matrix entry per image (`eureka-server`, `config-server`, `api-gateway`, `chat-service`, `recommendation-service`, `sentiment-service`, `video-service`, `analytics-service`, `ml-engine`, `video-capture-service`, `frontend`). It runs only when every check passed (`needs.ci-ok.result == 'success'`) and only on a push to `main` or a manual dispatch. The condition starts with `!cancelled()`, the same guard `docker-smoke` uses: jobs upstream of `ci-ok` are skipped by the path filter (and `sbom` on every ref but `main`), and GitHub's implicit `success()` would otherwise skip this job along with them, which is what the first dispatched run showed. Java entries package their own jar with Maven first, because the Java Dockerfiles copy `target/*.jar`; the Python and frontend images build from source. Buildx builds with the Actions cache (`type=gha`, one scope per service) and pushes to `ghcr.io/<owner>/streamsense/<service>` tagged with the commit SHA only. `provenance: false` keeps each tag a plain image manifest rather than an attestation index.
- **`promote-main` job**, after `publish-images`, on `main` only and only when all eleven matrix entries succeeded: re-tags each SHA image as `main` with `docker buildx imagetools create` (no rebuild). A review of the first version pointed out that moving `main` inside the matrix would let a partial failure leave `main` on a mix of commits, which the overlay in branch 02 would then pull by default; with the promotion separated, `main` is either the previous complete set or this one.
- **Concurrency**: pushes to `main` get a per-SHA group (`github.sha`), because a workflow-level group with a shared key keeps only one pending run and would drop the publish of a merge that landed while an earlier run was still going. Pull requests keep their per-number group with cancellation. `packages: write` is granted to this job alone; the workflow default stays read-only. The three Docker actions are pinned by commit SHA with the version in a comment, like every other action.
- **`workflow_dispatch` trigger**, so the job can be proven from a branch before it merges and a maintainer can republish a ref by hand. A dispatched run goes through the same checks first; `promote-main` runs only when the ref is `main`, so tracking `main` never picks up an unmerged image.
- **`schema-compat` base fallback**: the job compared against `github.event.before`, which a manual dispatch does not have; it now falls back to `origin/main`.
- **CLAUDE.md**: the CI parity paragraph describes the publish job and the image names.

Image names: `ghcr.io/8wali8/streamsense/<service>:<sha>` and `:main`. The repository is public; after the first push each package is switched to public visibility once in the GitHub UI (Packages, the package, Package settings, Change visibility), after which `docker pull` needs no credentials. That is a one-time owner action GitHub does not expose to the workflow token.

## Deliberately left alone

- No `trivy image` scan of the pushed images yet: the filesystem scan already covers the same dependency manifests, and the image scan is listed as a phase-two branch in the plan.
- No per-image SBOM or cosign signature, for the same reason as in branch 14.
- The Kubernetes manifests keep their `sprint9` tags; moving them to the registry belongs to the k3s phase, where they are next used.
- The Compose smoke job still builds its own images rather than pulling; it runs before anything is published.

## Verification

| Check | Command | Result |
|---|---|---|
| Workflow lint | `actionlint .github/workflows/ci.yml` (1.7.12) | clean |
| Publish dry run, first attempt | `gh workflow run ci.yml --ref cloud/01-image-publish` (run 34284934977, head 500a100) | every check green, `ci-ok` success, `publish-images` **skipped**: the implicit `success()` on a job whose ancestors include a skipped job (`sbom`); fixed in c78197d with `!cancelled()` |
| Publish dry run, second attempt | same, run [34285998367](https://github.com/8wali8/StreamSense-Production/actions/runs/34285998367) (head c78197d) | every check green; all eleven `publish-images` entries success, 48 s to 117 s each (ml-engine the slowest), tagged `c78197dc5b706e5ddebceb64f379a407d50c7030`, no `main` tag |
| Anonymous pull | `GET /v2/8wali8/streamsense/eureka-server/manifests/<sha>` with an anonymous registry token | HTTP 403 until the owner makes the packages public (see below); the pushes themselves are proven by the job logs |

## What to check by hand

- The eleven packages appear under the owner's Packages page after the dispatched run, tagged with the branch head SHA and without a `main` tag.
- Set each package to public once; until then a pull from a machine that is not logged in returns `denied`.
- After this merges, the push to `main` publishes the same images and `promote-main` moves the eleven `main` tags to them; that run is the first proof of the promote job, which a branch dispatch cannot exercise.
