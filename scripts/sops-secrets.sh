#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   ./scripts/sops-secrets.sh encrypt     # encrypt all example secrets
#   ./scripts/sops-secrets.sh decrypt     # decrypt to plaintext for local deploy
#   ./scripts/sops-secrets.sh edit FILE   # edit a secret in place (sops editor)
#   ./scripts/sops-secrets.sh view FILE   # view decrypted content

OVERLAY_DIR="deploy/k8s/overlays"
AGE_KEY="${SOPS_AGE_KEY_FILE:-$HOME/.config/sops/age/keys.txt}"

if [ ! -f "$AGE_KEY" ]; then
  echo "age key not found at $AGE_KEY" >&2
  echo "Generate one with: age-keygen -o $AGE_KEY" >&2
  exit 1
fi

export SOPS_AGE_KEY_FILE="$AGE_KEY"

encrypt_all() {
  local count=0
  for f in "$OVERLAY_DIR"/*/; do
    for secret in "$f"*-secret.example.yaml; do
      [ -f "$secret" ] || continue
      if grep -q 'sops:' "$secret" 2>/dev/null; then
        echo "skip (already encrypted): $secret"
      else
        sops -e -i "$secret"
        echo "encrypted: $secret"
        count=$((count + 1))
      fi
    done
  done
  echo "encrypted $count file(s)"
}

decrypt_to_plaintext() {
  local env="${1:-dev}"
  local dir="$OVERLAY_DIR/$env"
  for secret in "$dir"*-secret.example.yaml; do
    [ -f "$secret" ] || continue
    local target="${secret%.example.yaml}"
    sops -d "$secret" > "$target"
    echo "decrypted -> $target"
  done
}

view() {
  sops -d "$1"
}

edit() {
  sops "$1"
}

case "${1:-}" in
  encrypt) encrypt_all ;;
  decrypt) decrypt_to_plaintext "${2:-dev}" ;;
  view)    [ -n "${2:-}" ] && view "$2" || { echo "Usage: $0 view FILE" >&2; exit 1; } ;;
  edit)    [ -n "${2:-}" ] && edit "$2" || { echo "Usage: $0 edit FILE" >&2; exit 1; } ;;
  *)
    echo "Usage: $0 {encrypt|decrypt [env]|view FILE|edit FILE}" >&2
    exit 1
    ;;
esac
