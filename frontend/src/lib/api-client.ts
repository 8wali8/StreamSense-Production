import { env } from "../config/env";
import { authHeaders, type TokenStorage } from "./auth-token";

/** RFC 9457 problem details, the error body every StreamSense REST service returns. */
export type ProblemDetail = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  service?: string;
  correlationId?: string;
  errors?: Array<{ field: string; message: string }>;
};

export class ApiError extends Error {
  readonly path: string;
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(path: string, status: number, problem: ProblemDetail | null) {
    super(problem?.detail ? `${path} returned ${status}: ${problem.detail}` : `${path} returned ${status}`);
    this.name = "ApiError";
    this.path = path;
    this.status = status;
    this.problem = problem;
  }
}

export type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  /** Serialised as JSON; sets the Content-Type. */
  body?: unknown;
  signal?: AbortSignal;
  /** Applied when no signal is given. Every call is bounded. */
  timeoutMs?: number;
  storage?: TokenStorage | null;
};

export const DEFAULT_TIMEOUT_MS = 10_000;

function timeoutSignal(timeoutMs: number): AbortSignal | undefined {
  return typeof AbortSignal !== "undefined" && typeof AbortSignal.timeout === "function"
    ? AbortSignal.timeout(timeoutMs)
    : undefined;
}

async function readProblem(response: Response): Promise<ProblemDetail | null> {
  // Test doubles often omit headers; treat that as "no problem body".
  const contentType = response.headers?.get?.("content-type") ?? "";
  if (!contentType.includes("json")) {
    return null;
  }
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return null;
  }
}

/**
 * The one place the frontend talks HTTP to the backend: same-origin (or `VITE_API_BASE_URL`) paths,
 * the bearer token the gateway expects, JSON in and out, a timeout on every call, and errors that
 * carry the service's problem details instead of a bare status code.
 */
export async function apiRequest(path: string, options: RequestOptions = {}): Promise<Response> {
  const headers: Record<string, string> = { Accept: "application/json", ...authHeaders(options.storage) };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(`${env.apiBaseUrl}${path}`, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal ?? timeoutSignal(options.timeoutMs ?? DEFAULT_TIMEOUT_MS),
  });

  if (!response.ok) {
    throw new ApiError(path, response.status, await readProblem(response));
  }
  return response;
}

/** Request and parse a JSON body. */
export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await apiRequest(path, options);
  return (await response.json()) as T;
}

/** Request where only success matters (channel switches, profile updates). */
export async function apiSend(path: string, options: RequestOptions = {}): Promise<void> {
  await apiRequest(path, { method: "POST", ...options });
}

export function apiUrl(path: string, params?: Record<string, string | number>): string {
  if (!params) {
    return `${env.apiBaseUrl}${path}`;
  }
  const query = new URLSearchParams(Object.entries(params).map(([key, value]) => [key, String(value)]));
  return `${env.apiBaseUrl}${path}?${query.toString()}`;
}
