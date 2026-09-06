import {
  HttpResponse,
  delay,
  graphql,
  http,
  type GraphQLResponseResolver,
  type HttpResponseResolver,
  type JsonBodyType,
} from "msw";
import { setupServer } from "msw/node";

/**
 * One MSW server for the whole test run (started in setup.ts). Tests add the handlers they need
 * with `server.use(...)`; handlers are reset after every test. Requests with no handler fail.
 */
export const server = setupServer();

/** A GraphQL query handler answering with `data`, optionally after a delay. */
export function graphqlData<TData extends Record<string, unknown>>(operation: string, data: TData, delayMs?: number) {
  return graphql.query(operation, async () => {
    if (delayMs !== undefined) await delay(delayMs);
    return HttpResponse.json({ data });
  });
}

/** A GraphQL query handler that never answers, to hold a component in its loading state. */
export function graphqlPending(operation: string) {
  return graphql.query(operation, async () => {
    await delay("infinite");
    return HttpResponse.json({ data: null });
  });
}

/** A GraphQL query handler answering with a top-level error, the way the gateway reports a downstream failure. */
export function graphqlError(operation: string, message: string, code = "DOWNSTREAM_UNAVAILABLE") {
  return graphql.query(operation, () => HttpResponse.json({ errors: [{ message, extensions: { code } }] }));
}

export function graphqlResolver(operation: string, resolver: GraphQLResponseResolver) {
  return graphql.query(operation, resolver);
}

/** A JSON REST handler; `path` is matched on any origin (`*` prefix). */
export function restJson(method: "get" | "post", path: string, body: JsonBodyType, status = 200) {
  return http[method](`*${path}`, () => HttpResponse.json(body, { status }));
}

/** A REST handler that answers with an RFC 9457 problem body. */
export function restProblem(method: "get" | "post", path: string, status: number, detail: string) {
  return http[method](`*${path}`, () =>
    HttpResponse.json(
      { type: "https://streamsense.dev/problems/test", title: "Test problem", status, detail },
      { status, headers: { "content-type": "application/problem+json" } },
    ),
  );
}

export function restResolver(method: "get" | "post", path: string, resolver: HttpResponseResolver) {
  return http[method](`*${path}`, resolver);
}

export { HttpResponse, delay, graphql, http };
