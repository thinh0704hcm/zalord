#!/usr/bin/env sh
# Idempotent bucket creation. Run as a one-shot init container (image: minio/mc)
# after MinIO is healthy. Replaces the BucketInitializer that previously lived
# in media-service Java code — cleaner separation: infra in /infra, app in /backend.

set -e

ENDPOINT="${MINIO_ENDPOINT:-http://minio:9000}"
ROOT_USER="${MINIO_ROOT_USER:-minioadmin}"
ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:-minioadmin}"
BUCKET_AVATARS="${MINIO_BUCKET_AVATARS:-avatars}"
BUCKET_ATTACHMENTS="${MINIO_BUCKET_ATTACHMENTS:-attachments}"

echo "[minio-init] waiting for MinIO at $ENDPOINT ..."
until mc alias set local "$ENDPOINT" "$ROOT_USER" "$ROOT_PASSWORD" >/dev/null 2>&1; do
    sleep 2
done
echo "[minio-init] MinIO ready"

ensure_bucket() {
    local bucket="$1"
    if mc ls "local/$bucket" >/dev/null 2>&1; then
        echo "[minio-init] ✓ $bucket already exists"
    else
        mc mb "local/$bucket"
        echo "[minio-init] ✓ $bucket created"
    fi
}

ensure_bucket "$BUCKET_AVATARS"
ensure_bucket "$BUCKET_ATTACHMENTS"

echo "[minio-init] done"
mc ls local
