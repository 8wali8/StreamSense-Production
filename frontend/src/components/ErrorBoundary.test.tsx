import { fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ErrorBoundary } from "./ErrorBoundary";

function Explodes({ when }: { when: boolean }) {
  if (when) {
    throw new Error("boom");
  }
  return <div>healthy</div>;
}

function Harness() {
  const [broken, setBroken] = useState(true);
  return (
    <div>
      <button onClick={() => setBroken(false)}>fix</button>
      <ErrorBoundary label="metrics">
        <Explodes when={broken} />
      </ErrorBoundary>
    </div>
  );
}

describe("ErrorBoundary", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders children when nothing throws", () => {
    render(
      <ErrorBoundary label="metrics">
        <Explodes when={false} />
      </ErrorBoundary>,
    );

    expect(screen.getByText("healthy")).toBeInTheDocument();
  });

  it("replaces a throwing child with a named error state and recovers on retry", () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);

    render(<Harness />);

    expect(screen.getByRole("alert")).toHaveTextContent("The metrics failed to render: boom");

    fireEvent.click(screen.getByText("fix"));
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(screen.getByText("healthy")).toBeInTheDocument();
  });
});
