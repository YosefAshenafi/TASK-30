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

# Test stage: dependencies already installed by the `build` layer above, plus
# Chromium headless so the README "docker compose run --rm web-test" command
# can execute unit tests with no host-side `npm ci`/`npm install` step.
FROM node:20-alpine AS test
WORKDIR /app
RUN apk add --no-cache chromium nss freetype harfbuzz ca-certificates ttf-freefont
ENV CHROME_BIN=/usr/bin/chromium-browser
ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true
COPY --from=build /app/node_modules /app/node_modules
COPY --from=build /app/package*.json /app/
COPY . .
CMD ["npx","ng","test","--watch=false","--browsers=ChromeHeadlessCI","--no-progress"]

FROM nginx:1.25-alpine AS runtime
COPY --from=build /app/dist/meridian-web /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
