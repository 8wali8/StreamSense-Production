import { CombinedGraphQLErrors } from "@apollo/client/errors";
import { describe, expect, it } from "vitest";
import { ApiError } from "./api-client";
import { describeError } from "./errors";

describe("describeError", () => {
  it("prefers the REST problem detail", () => {
    const error = new ApiError("/api/chat/twitch/channels", 409, {
      status: 409,
      detail: "Twitch chat ingestion is disabled",
    });

    expect(describeError(error)).toBe("Twitch chat ingestion is disabled");
  });

  it("falls back to the ApiError message when there is no problem body", () => {
    expect(describeError(new ApiError("/ml/segment", 503, null))).toBe("/ml/segment returned 503");
  });

  it("translates the gateway's stable GraphQL codes", () => {
    const unavailable = new CombinedGraphQLErrors({
      errors: [
        {
          message: "Downstream service unavailable",
          extensions: { code: "DOWNSTREAM_UNAVAILABLE", host: "sentiment-service" },
        },
      ],
    });
    const failed = new CombinedGraphQLErrors({
      errors: [
        {
          message: "Downstream service returned an error",
          extensions: { code: "DOWNSTREAM_ERROR", host: "analytics-service", status: 503 },
        },
      ],
    });
    const badRequest = new CombinedGraphQLErrors({ errors: [{ message: "x", extensions: { code: "BAD_REQUEST" } }] });

    expect(describeError(unavailable)).toBe("sentiment-service is unavailable");
    expect(describeError(failed)).toBe("analytics-service answered 503");
    expect(describeError(badRequest)).toBe("the request was rejected as invalid");
  });

  it("keeps the raw GraphQL message for unknown codes and plain errors", () => {
    const unknown = new CombinedGraphQLErrors({ errors: [{ message: "Field 'x' is undefined" }] });

    expect(describeError(unknown)).toBe("Field 'x' is undefined");
    expect(describeError(new Error("network down"))).toBe("network down");
    expect(describeError(undefined)).toBe("unknown error");
  });
});
