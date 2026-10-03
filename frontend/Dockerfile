# Build the Angular app and serve it with nginx.
#
# `npm test` is not run here. The gate is `npm test` before the image is built,
# the same as for the backend.

FROM node:24-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime

RUN apk add --no-cache curl
# the snippet lives outside conf.d, which nginx auto-includes as whole server
# configuration files
COPY deploy/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/frontend/browser/ /usr/share/nginx/html/

# listens on 8080 so the container does not need to start as root
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -fsS http://localhost:8080/healthz >/dev/null
