FROM nginx:1.25-alpine
RUN apk add --no-cache curl openssl \
    && mkdir -p /etc/nginx/certs \
    && openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
        -keyout /etc/nginx/certs/server.key \
        -out /etc/nginx/certs/server.crt \
        -subj "/CN=localhost"
COPY nginx.conf /etc/nginx/nginx.conf
