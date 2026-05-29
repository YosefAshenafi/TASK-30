# syntax=docker/dockerfile:1

# ---- Build stage: compile the Angular application ----
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json ./
RUN npm install
COPY . .
RUN npm run build

# ---- Runtime stage: serve the compiled assets with Nginx ----
FROM nginx:1.27-alpine
# Angular 17 "application" builder emits the browser bundle under dist/<project>/browser
COPY --from=build /app/dist/meridian/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
CMD ["nginx", "-g", "daemon off;"]
