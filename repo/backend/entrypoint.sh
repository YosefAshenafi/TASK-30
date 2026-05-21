#!/bin/bash
set -e
export JWT_ACCESS_SECRET="${JWT_ACCESS_SECRET:-$(openssl rand -hex 32)}"
export JWT_REFRESH_SECRET="${JWT_REFRESH_SECRET:-$(openssl rand -hex 32)}"
export ENCRYPTION_KEY="${ENCRYPTION_KEY:-$(openssl rand -hex 32)}"
export DATABASE_URL="${DATABASE_URL:-postgresql://postgres:postgres@db:5432/meridian}"
exec "$@"
