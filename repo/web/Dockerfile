FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
# Resilient installs on flaky networks (ECONNRESET / aborted downloads).
RUN npm config set fetch-retries 10 \
    && npm config set fetch-retry-mintimeout 15000 \
    && npm config set fetch-retry-maxtimeout 120000 \
    && npm config set fetch-timeout 300000 \
    && (npm ci --legacy-peer-deps \
        || (echo "npm ci failed; retrying once after 15s..." && sleep 15 && npm ci --legacy-peer-deps))
COPY . .
RUN npm run build

FROM nginx:1.25-alpine
COPY --from=build /app/dist/meridian-web /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
