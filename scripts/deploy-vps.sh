#!/usr/bin/env bash
set -euo pipefail

# Deploy zalord K8s manifests to vietnam-vps
# Usage:
#   ./scripts/deploy-vps.sh                  # deploy dev overlay
#   ./scripts/deploy-vps.sh --dry-run        # preview without applying
#   ./scripts/deploy-vps.sh --destroy        # delete all resources

VPS_HOST="vietnam-vps"
ENV="${1:-dev}"
OVERLAY_DIR="deploy/k8s/overlays/$ENV"
AGE_KEY="${SOPS_AGE_KEY_FILE:-$HOME/.config/sops/age/keys.txt}"

if [ ! -f "$AGE_KEY" ]; then
  echo "age key not found at $AGE_KEY" >&2
  exit 1
fi

export SOPS_AGE_KEY_FILE="$AGE_KEY"

# Decrypt secrets to temp dir, apply, then clean up
TMPDIR=$(mktemp -d)
trap "rm -rf $TMPDIR" EXIT

echo "==> Decrypting secrets for $ENV..."
for secret in "$OVERLAY_DIR"/*-secret.example.yaml; do
  [ -f "$secret" ] || continue
  target="$TMPDIR/$(basename "$secret" .example.yaml)"
  sops -d "$secret" > "$target"
  echo "    decrypted: $(basename "$secret")"
done

# Copy non-secret files
cp "$OVERLAY_DIR"/*-config.yaml "$TMPDIR/" 2>/dev/null || true

# Copy namespace if exists
cp "$OVERLAY_DIR"/namespace.yaml "$TMPDIR/" 2>/dev/null || true

echo "==> Syncing to $VPS_HOST..."
ssh "$VPS_HOST" "mkdir -p ~/zalord-deploy" 2>/dev/null
scp -r "$TMPDIR"/* "$VPS_HOST:~/zalord-deploy/"

echo "==> Applying manifests..."
if [ "${DRY_RUN:-}" = "true" ] || [ "${1:-}" = "--dry-run" ]; then
  ssh "$VPS_HOST" "kubectl apply --dry-run=client -f ~/zalord-deploy/"
else
  ssh "$VPS_HOST" "kubectl apply -f ~/zalord-deploy/"
fi

echo "==> Cleaning up remote temp files..."
ssh "$VPS_HOST" "rm -rf ~/zalord-deploy"

echo "==> Done. Verifying..."
ssh "$VPS_HOST" "kubectl get configmaps,secrets -n zalord-$ENV"
