import react from "@vitejs/plugin-react";
import { loadEnv } from "vite";
import { defineConfig } from "vitest/config";

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const apiTarget = env.VITE_DEV_API_TARGET || "http://localhost:8080";
  const mlTarget = env.VITE_DEV_ML_TARGET || "http://localhost:8000";

  return {
    plugins: [react()],
    server: {
      port: 3000,
      // Same routes nginx proxies in the Docker image, so `npm run dev` works against `make up`.
      proxy: {
        "/graphql": { target: apiTarget, changeOrigin: true, ws: true },
        "/api": { target: apiTarget, changeOrigin: true },
        "/ml": { target: mlTarget, changeOrigin: true },
      },
    },
    test: {
      environment: "jsdom",
      setupFiles: "./src/test/setup.ts",
      coverage: {
        provider: "v8",
        include: ["src/**/*.{ts,tsx}"],
        exclude: [
          "src/**/*.test.{ts,tsx}",
          "src/test/**",
          "src/graphql/generated.ts",
          "src/main.tsx",
          "src/vite-env.d.ts",
        ],
        reporter: ["text-summary", "html"],
        // Floors, not targets: CI fails if a change drops coverage below them.
        thresholds: { statements: 90, branches: 80, functions: 80, lines: 90 },
      },
    },
  };
});
