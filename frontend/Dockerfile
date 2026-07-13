# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM node:24-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production

# ── Stage 2: serve with nginx (unprivileged — no root) ────────────────────────
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime

COPY --from=builder /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

# Let the nginx entrypoint populate ${NGINX_LOCAL_RESOLVERS} from the container's
# resolv.conf so the template's `resolver` directive gets a real nameserver.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost:8080/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
