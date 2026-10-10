# Build the static site
FROM node:22-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# The backend URL is baked into the JavaScript at build time
ARG VITE_API_URL
RUN test -n "$VITE_API_URL" && npm run build

# Caddy serves the site, forwards /api and /ws to the backend, and gets HTTPS certificates
FROM caddy:2
COPY --from=build /app/dist /srv
