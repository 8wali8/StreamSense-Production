import { CombinedGraphQLErrors } from "@apollo/client/errors";
import { ApiError } from "./api-client";

export type ErrorLike = { message: string } | Error | null | undefined;

/** Human-readable text for the gateway's `extensions.code` values (see api-gateway GraphQlErrorAdvice). */
function describeGraphQlCode(code: unknown, extensions: Record<string, unknown>): string | null {
  switch (code) {
    case "DOWNSTREAM_UNAVAILABLE":
      return typeof extensions.host === "string"
        ? `${extensions.host} is unavailable`
        : "a downstream service is unavailable";
    case "DOWNSTREAM_ERROR":
      return typeof extensions.status === "number"
        ? `${typeof extensions.host === "string" ? extensions.host : "a downstream service"} answered ${extensions.status}`
        : "a downstream service returned an error";
    case "BAD_REQUEST":
      return "the request was rejected as invalid";
    default:
      return null;
  }
}

/**
 * One sentence a panel can show for any failure: the REST problem `detail` when the service sent one,
 * the gateway's stable GraphQL error code translated to words, otherwise the raw message.
 */
export function describeError(error: ErrorLike): string {
  if (!error) return "unknown error";
  if (error instanceof ApiError) {
    return error.problem?.detail ?? error.message;
  }
  if (CombinedGraphQLErrors.is(error)) {
    const described = error.errors
      .map((graphQlError) => {
        const extensions = (graphQlError.extensions ?? {}) as Record<string, unknown>;
        return describeGraphQlCode(extensions.code, extensions) ?? graphQlError.message;
      })
      .filter(Boolean);
    if (described.length > 0) return described.join("; ");
  }
  return error.message || "unknown error";
}
