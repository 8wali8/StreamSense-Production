/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Origin of the API gateway when the frontend is not served behind the same host (default: same origin). */
  readonly VITE_API_BASE_URL?: string;
  /** Origin of ml-engine when `/ml` is not proxied by the host serving the app (default: same origin). */
  readonly VITE_ML_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
