# hardening/11a-frontend-codegen

Priority 10 (first of four frontend branches) from `docs/planning/production-hardening.md`: the frontend's GraphQL types come from the gateway schema, not from hand-written declarations. Stacked on `hardening/10-event-schemas` so the CI edits land on the pinned, path-filtered workflow; the frontend changes themselves do not depend on branch 10.

## What was wrong

Every component declared its own `type X = { ... }` for the GraphQL results it read, nine copies across `App.tsx`, the six panels, and `LiveChat.tsx`. They already disagreed with each other (`SentimentPanel` had ten fields of `SentimentAnalysisEvent`, `App.tsx` sixteen; `TranscriptPanel` typed `streamSessionId` as `string`, `App.tsx` as `string | null | undefined`) and nothing tied any of them to `api-gateway/src/main/resources/graphql/*.graphqls`. A field renamed in the SDL would compile cleanly and fail at runtime.

## What changed

- **GraphQL Code Generator** (`@graphql-codegen/cli` 6.1.0 with the `typescript` and `typescript-operations` plugins, exact-pinned dev dependencies). The CLI's 7.x line depends on `listr2` 10, which declares `node >=22.13`, while CI and the frontend image run Node 20; 6.1.0 resolves `listr2` 9 (`node >=20`) and generates identical output. `frontend/codegen.ts` reads the gateway SDL files directly (same repository, no export step to forget) and the operations in `src/graphql/*.ts`, and writes `src/graphql/generated.ts`: the schema types plus a `<Name>Query` / `<Name>Subscription` and `...Variables` type per operation. `strictScalars` with `Float`/`Int` → `number`, `ID` → `string`; `skipTypename` so the shapes match what the components consumed before; `enumsAsTypes` for when the schema grows enums. The generated file is committed so `npm run build` and the Docker image need no extra step.
- **Operations stay where they were.** `queries.ts` and `subscriptions.ts` keep their `gql` constants and export names; the only move is `HEALTH_QUERY` out of `Health.tsx` into `queries.ts` so the `documents` glob covers every operation. Components pass the generated result type and the generated `...Variables` type to `useQuery`/`useSubscription` (both generics, so a wrong or missing variable is a compile error rather than a gateway error) and derive entity types from it (`SponsorDetectionsQuery["sponsorDetections"][number]`), which deleted about 200 lines of hand-written types. No runtime behaviour changed.
- **Scripts**: `npm run codegen` regenerates; `npm run codegen:check` exits 1 when `generated.ts` is stale (verified below).
- **CI**: `frontend-checks` runs `codegen:check` right after `npm ci`, and the `frontend` path filter now includes `api-gateway/src/main/resources/graphql/**`, so an SDL change on its own runs the frontend job and fails if the types were not regenerated.
- **CLAUDE.md** documents the rule: never hand-write a GraphQL result type; regenerate and commit after changing an operation or the SDL.

## Deliberately left alone

- Not the `client-preset` / `graphql()` document style. It would rewrite every operation and every call site, and the typed `gql` constants already give end-to-end types through explicit generics. It can be adopted later without touching the schema pipeline.
- `SegmentationPreview`, `TwitchIngestionStatus`, and `VideoCaptureStatus` keep their hand-written types: they read REST endpoints, not GraphQL. Branch 11b gives them an API layer.
- Tests still `vi.mock` Apollo hooks; branch 11d replaces that with MSW and a real provider.

## Merge note

The user's main checkout has uncommitted edits to `frontend/src/App.tsx`, `App.test.tsx`, `queries.ts`, and `subscriptions.ts`. This branch changes the type declarations at the top of `App.tsx` and the hook generics around lines 449 to 610, adds `HEALTH_QUERY` at the top of `queries.ts`, and leaves `subscriptions.ts` and `App.test.tsx` untouched. After resolving, run `npm run codegen` once so `generated.ts` reflects any operation edits.

## Verification

| Check | Command | Result |
|---|---|---|
| Types generate from the SDL | `npm run codegen` | 461-line `generated.ts`, 13 operation types |
| Lint | `npm run lint` | clean |
| Type-check and bundle | `npm run build` (`tsc -b && vite build`) | OK |
| Tests | `npx vitest run` | 13 files, 40 tests passed |
| Check mode catches drift | append a line to `generated.ts`, `npm run codegen:check` | exit 1; restored file passes with exit 0 |
| Workflow syntax | `actionlint .github/workflows/ci.yml` | OK |
| Docker image | `docker build frontend/` | builds (nginx stage included), no codegen step needed |

## Manual checks for the reviewer

1. `git diff origin/main -- frontend/src/components frontend/src/App.tsx frontend/src/pages`: only type declarations and hook generics change; no JSX or logic.
2. Rename a field in `api-gateway/src/main/resources/graphql/sentiment.graphqls`, run `npm run codegen`, then `npm run build`: every consumer of that field fails to compile, which is the point.
3. `make up`, open `http://localhost:3000`: the console renders as before.

## Follow-ups (branches 11b to 11d)

- REST calls (`/api/...`, `/ml/...`) through one typed client and feature `api/` modules, plus a Vite dev proxy.
- Split `App.tsx` (844 lines) into feature folders with a shared live-feed hook.
- MSW-backed tests with a real `ApolloProvider`, type-aware ESLint, Prettier.
