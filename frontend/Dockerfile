# Build stage
FROM node:20.20.2-alpine@sha256:fb4cd12c85ee03686f6af5362a0b0d56d50c58a04632e6c0fb8363f609372293 AS build
WORKDIR /app
COPY package*.json ./
COPY . .
RUN npm ci
RUN npm run build

# Serve stage
FROM nginx:1.31.5-alpine@sha256:34f40471dea485273c5e2a04dd5e97a682332ceb4a9adecd67de450dcb2fb390
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80