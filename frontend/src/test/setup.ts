import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";
import { server } from "./msw";

let fetchBeforeTests: typeof globalThis.fetch;
const unhandledRequests: string[] = [];

beforeAll(() => {
  // Every HTTP request a test makes must be answered by an MSW handler. "error" rejects the request,
  // but a component that swallows the resulting network error (Apollo, the polling hook) would let
  // the test pass anyway, so unhandled requests are also recorded and failed in afterEach.
  server.listen({ onUnhandledRequest: "error" });
  server.events.on("request:unhandled", ({ request }) => {
    unhandledRequests.push(`${request.method} ${request.url}`);
  });

  // vitest's jsdom environment replaces AbortController/AbortSignal with jsdom's, but `fetch` (and
  // MSW's interceptor around it) is Node's, which rejects a signal from another realm. Requests in
  // tests are answered by MSW and never cancelled, so the signal is dropped above the interceptor;
  // timeout and signal behaviour is covered by api-client.test.ts with a stubbed fetch.
  // The same split bites a file upload: jsdom's FormData is not the one Node's fetch serialises (it
  // would go out as the text "[object FormData]"), so it is encoded here into the multipart body a
  // browser would send, and MSW handlers read it back with `request.formData()` as they would in one.
  fetchBeforeTests = globalThis.fetch;
  const interceptedFetch = fetchBeforeTests.bind(globalThis);
  globalThis.fetch = async (input, init) => {
    let request = init ? { ...init, signal: undefined } : init;
    if (request?.body instanceof FormData) {
      const encoded = await encodeMultipart(request.body);
      request = {
        ...request,
        body: encoded.body,
        headers: { ...(request.headers as Record<string, string> | undefined), "Content-Type": encoded.contentType },
      };
    }
    return interceptedFetch(input, request);
  };
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  if (unhandledRequests.length > 0) {
    const requests = unhandledRequests.splice(0).join(", ");
    throw new Error(`Unhandled request(s) during the test: ${requests}. Add an MSW handler with server.use(...).`);
  }
});

afterAll(() => {
  globalThis.fetch = fetchBeforeTests;
  server.close();
});

/** The multipart/form-data body a browser would build for the form, with its boundary in the content type. */
async function encodeMultipart(form: FormData): Promise<{ body: Uint8Array<ArrayBuffer>; contentType: string }> {
  const boundary = `----vitest${Math.random().toString(16).slice(2)}`;
  const encoder = new TextEncoder();
  const chunks: Uint8Array[] = [];
  for (const [name, value] of form.entries()) {
    chunks.push(encoder.encode(`--${boundary}\r\n`));
    if (typeof value === "string") {
      chunks.push(encoder.encode(`Content-Disposition: form-data; name="${name}"\r\n\r\n${value}\r\n`));
      continue;
    }
    chunks.push(
      encoder.encode(
        `Content-Disposition: form-data; name="${name}"; filename="${value.name}"\r\n` +
          `Content-Type: ${value.type || "application/octet-stream"}\r\n\r\n`,
      ),
    );
    chunks.push(new Uint8Array(await bytesOf(value)));
    chunks.push(encoder.encode("\r\n"));
  }
  chunks.push(encoder.encode(`--${boundary}--\r\n`));
  // Backed by a plain ArrayBuffer, which is what fetch's BodyInit accepts (a SharedArrayBuffer view is not).
  const body = new Uint8Array(new ArrayBuffer(chunks.reduce((total, chunk) => total + chunk.length, 0)));
  let offset = 0;
  for (const chunk of chunks) {
    body.set(chunk, offset);
    offset += chunk.length;
  }
  return { body, contentType: `multipart/form-data; boundary=${boundary}` };
}

/** A jsdom Blob's bytes, through FileReader when the Blob has no `arrayBuffer` of its own. */
function bytesOf(blob: Blob): Promise<ArrayBuffer> {
  if (typeof blob.arrayBuffer === "function") {
    return blob.arrayBuffer();
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as ArrayBuffer);
    reader.onerror = () => reject(reader.error ?? new Error("could not read the file"));
    reader.readAsArrayBuffer(blob);
  });
}
