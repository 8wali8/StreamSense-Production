# Build stage: dependencies are installed from the lockfile before the sources are copied, so a
# source-only change reuses the cached node_modules layer.
FROM node:20.20.2-alpine@sha256:fb4cd12c85ee03686f6af5362a0b0d56d50c58a04632e6c0fb8363f609372293 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# Set VITE_API_BASE_URL only for a build that talks to an API on another origin (see .env.example);
# the same value is allowed in the Content-Security-Policy below.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

# Serve stage: the unprivileged nginx image runs as uid 101, listens on 8080, and keeps its pid and
# cache under /tmp, so the container needs no root and no capabilities.
FROM nginxinc/nginx-unprivileged:1.31.5-alpine@sha256:aa8c9087d36d93e9d650c5365f883b421e8214aedbad24ade52b844c583358f1
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY streamsense-headers.conf /etc/nginx/streamsense-headers.conf
ARG VITE_API_BASE_URL=""
RUN sed -i "s|__API_ORIGIN__|${VITE_API_BASE_URL}|" /etc/nginx/streamsense-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
# The base image already runs as this user; stating it lets scanners see it.
USER 101
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD ["wget", "-qO-", "http://127.0.0.1:8080/healthz"]
