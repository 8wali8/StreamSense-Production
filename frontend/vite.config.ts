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
      // Vite's default; 3000 belongs to the Compose frontend container, which `make up` publishes.
      port: 5173,
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
    },
  };
});
