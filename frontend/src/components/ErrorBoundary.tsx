import { Component, type ErrorInfo, type ReactNode } from "react";

type ErrorBoundaryProps = {
  /** Named in the fallback so the operator knows which part of the console failed. */
  label: string;
  children: ReactNode;
};

type ErrorBoundaryState = { error: Error | null };

/**
 * Keeps one broken section from blanking the whole console. Rendering errors are logged and
 * replaced by an inline error state; the rest of the page keeps streaming.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error(`[${this.props.label}] render failed`, error, info.componentStack);
  }

  reset = (): void => {
    this.setState({ error: null });
  };

  render(): ReactNode {
    if (this.state.error) {
      return (
        <section className="dashboard-panel" role="alert">
          <div className="error-state">
            The {this.props.label} failed to render: {this.state.error.message}
          </div>
          <button className="button-secondary" onClick={this.reset}>
            Try again
          </button>
        </section>
      );
    }
    return this.props.children;
  }
}
