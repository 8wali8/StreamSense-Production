import { CombinedGraphQLErrors, ServerError } from "@apollo/client/errors";
import { ApiError } from "./api-client";

export type ErrorLike = { message: string } | Error | null | undefined;

/** What the gateway's 401 means to a viewer: the console has no token it accepts. */
export const NO_ACCESS_MESSAGE = "this console needs an access link; open the one you were sent";
/** The gateway's 403: signed in, but as a streamer where an operator is needed. */
export const NOT_ALLOWED_MESSAGE = "your account is not allowed to change this; an operator can";

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
    case "SHARE_TOKEN_INVALID":
      return "this share link is no longer valid";
    case "SHARE_FORBIDDEN":
      return "this share link does not open that page";
    case "SHARE_UNAVAILABLE":
      return "the share link could not be checked";
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
    if (error.status === 401) return NO_ACCESS_MESSAGE;
    if (error.status === 403) return NOT_ALLOWED_MESSAGE;
    return error.problem?.detail ?? error.message;
  }
  if (ServerError.is(error) && error.statusCode === 401) {
    return NO_ACCESS_MESSAGE;
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
