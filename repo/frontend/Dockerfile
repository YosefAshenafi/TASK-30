# ---- Build stage: compile the Angular application ----
FROM node:20-alpine AS build
WORKDIR /app
# Disable Angular CLI usage-analytics so `ng build`/`ng test` never show the interactive
# "share pseudonymous usage data? (y/N)" prompt — it would block an unattended CI/server run.
# Inherited by the `test` stage below (FROM build).
ENV NG_CLI_ANALYTICS=false
COPY package.json ./
# Make the install resilient to flaky/CI networks: npm's default of 2 fetch retries is not
# enough when a connection is reset mid-download (ECONNRESET); raise retries/timeouts and skip
# the audit/fund network round-trips. Behaviour of the resulting app is unchanged.
RUN npm config set fetch-retries 6 \
 && npm config set fetch-retry-mintimeout 20000 \
 && npm config set fetch-retry-maxtimeout 180000 \
 && npm config set fetch-timeout 600000 \
 && npm install --no-audit --no-fund
COPY . .
RUN npm run build

# ---- Test stage: run Karma/Jasmine unit tests headlessly with Chromium ----
# Reuses the build stage (sources + installed node_modules) and adds the Chromium
# browser the headless Karma launcher needs. Selected via `--target test` by the
# `frontend-test` compose service / run_tests.sh; the runtime image below stays slim.
FROM build AS test
RUN apk add --no-cache chromium nss freetype harfbuzz ca-certificates ttf-freefont
ENV CHROME_BIN=/usr/bin/chromium-browser
CMD ["npm", "test"]

# ---- Runtime stage: serve the compiled assets with Nginx ----
FROM nginx:1.27-alpine
# Angular 17 "application" builder emits the browser bundle under dist/<project>/browser
COPY --from=build /app/dist/meridian/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
CMD ["nginx", "-g", "daemon off;"]
