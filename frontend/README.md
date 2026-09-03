# StreamSense frontend

React 19 + TypeScript + Vite, Apollo Client 4 for GraphQL queries and `graphql-ws` subscriptions, Vitest + Testing Library for tests.

## Commands

```bash
npm ci               # Install exactly what package-lock.json says
npm run dev          # Dev server on http://localhost:5173, proxying to the Compose backend (3000 is the Docker frontend)
npm run build        # tsc -b && vite build
npm run test         # Vitest (vitest run)
npm run lint         # ESLint
npm run codegen      # Regenerate src/graphql/generated.ts from the gateway SDL
npm run codegen:check  # Fail if generated.ts is stale (CI runs this)
```

## Running against the backend

`npm run dev` proxies `/graphql` (HTTP and WebSocket) and `/api` to the API gateway and `/ml` to ml-engine, so `make up` (or `make up-fast`) in the repository root is all the backend you need. The targets default to `http://localhost:8080` and `http://localhost:8000`; override them with `VITE_DEV_API_TARGET` / `VITE_DEV_ML_TARGET` in a git-ignored `.env.local` (see `.env.example`). In Docker, nginx serves `dist/` and proxies the same three routes.

Set `VITE_API_BASE_URL` only when the built app is served from a different origin than the gateway; it is inlined at build time.

## Layout

| Path | Role |
|---|---|
| `src/config/env.ts` | Every environment read; nothing else touches `import.meta.env` |
| `src/lib/api-client.ts` | The only `fetch` caller: base URL, bearer token, JSON, timeout, `ApiError` with RFC 9457 problem details |
| `src/api/*.ts` | One module per backend feature (`chat`, `video`, `sentiment`, `ml`) with typed request functions |
| `src/graphql/` | `queries.ts`, `subscriptions.ts`, and the generated `generated.ts` |
| `src/apollo/client.ts` | Apollo Client with the HTTP/WebSocket split link and the same auth token as REST |
| `src/hooks/usePolledResource.ts` | Load now and every N ms, keyed by streamer |
| `src/components/`, `src/pages/` | Panels and pages |

Rules: components never call `fetch` or read `import.meta.env` directly; GraphQL result types come from `generated.ts`; new REST endpoints get a function in `src/api/` and a test.
