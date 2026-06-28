#!/bin/bash
set -euo pipefail

# Canary Deployment Demo Script
# Demonstrates: versioning, rollback, canary traffic split on OKE

KUBECONFIG="${KUBECONFIG:-/home/thinh0704hcm/.kube/oke.config}"
NAMESPACE="zalord-prod"
LB_IP="140.245.47.255"
KONG_NS="kong"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

check_deps() {
  for cmd in kubectl jq curl; do
    if ! command -v "$cmd" &>/dev/null; then
      log_error "$cmd not found"
      exit 1
    fi
  done
}

show_status() {
  log_info "=== Current Deployment Status ==="
  echo ""
  kubectl get deploy -n "$NAMESPACE" -o custom-columns='NAME:.metadata.name,REPLICAS:.spec.replicas,IMAGE:.spec.template.spec.containers[0].image' 2>/dev/null
  echo ""
  log_info "=== HTTPRoute Traffic Split ==="
  kubectl get httproute auth-route -n "$NAMESPACE" -o jsonpath='{.spec.rules[0].backendRefs}' 2>/dev/null | jq .
  echo ""
}

deploy_canary() {
  local image_tag="${1:-latest}"
  log_info "Deploying canary with image tag: $image_tag"

  # Apply canary manifest with envsubst
  export DOCKER_NAMESPACE="thinh0704hcm"
  export IMAGE_TAG="$image_tag"

  envsubst '${DOCKER_NAMESPACE} ${IMAGE_TAG}' < deploy/k8s/overlays/prod/auth-service-canary-workload.yaml \
    | kubectl apply -n "$NAMESPACE" -f -

  log_info "Waiting for canary rollout..."
  kubectl rollout status -n "$NAMESPACE" "deployment/auth-service-canary" --timeout=120s || true

  log_info "Canary deployed successfully"
  kubectl get deploy auth-service-canary -n "$NAMESPACE"
}

shift_traffic() {
  local stable_weight="${1:-90}"
  local canary_weight="${2:-10}"

  log_info "Shifting traffic: stable=${stable_weight}% canary=${canary_weight}%"

  kubectl patch httproute auth-route -n "$NAMESPACE" \
    --type=merge \
    --patch "{\"spec\":{\"rules\":[{\"backendRefs\":[{\"name\":\"auth-service\",\"port\":8081,\"weight\":$stable_weight},{\"name\":\"auth-service-canary\",\"port\":8081,\"weight\":$canary_weight}]}]}}"

  log_info "Traffic split updated"
  kubectl get httproute auth-route -n "$NAMESPACE" -o jsonpath='{.spec.rules[0].backendRefs}' | jq .
}

smoke_test() {
  log_info "Running smoke test (50 requests)..."

  local total=50
  local canary_count=0
  local stable_count=0
  local fail_count=0

  for i in $(seq 1 $total); do
    # Auth login endpoint - different versions return different error messages
    local response
    response=$(curl -s -w "\n%{http_code}" -H "Host: auth.zalord.vn" \
      -X POST "http://$LB_IP/api/v1/auth/login" \
      -H "Content-Type: application/json" \
      -d '{"phoneNumber":"0000000000","password":"test"}' \
      --max-time 5 2>/dev/null || echo -e "\n000")

    local code
    code=$(echo "$response" | tail -1)
    local body
    body=$(echo "$response" | head -1)

    if [ "$code" = "401" ]; then
      # Check response to identify version
      if echo "$body" | grep -q "Invalid phone number"; then
        ((stable_count++))
      else
        ((canary_count++))
      fi
    else
      ((fail_count++))
    fi
  done

  echo ""
  log_info "=== Smoke Test Results ==="
  echo "  Total requests: $total"
  echo "  Stable (v1.0): $stable_count ($(( stable_count * 100 / total ))%)"
  echo "  Canary (v1.1): $canary_count ($(( canary_count * 100 / total ))%)"
  echo "  Failed: $fail_count"
}

promote_canary() {
  log_info "Promoting canary to stable..."

  # Update stable deployment to canary image
  local canary_image
  canary_image=$(kubectl get deploy auth-service-canary -n "$NAMESPACE" \
    -o jsonpath='{.spec.template.spec.containers[0].image}')

  kubectl set image deploy/auth-service "auth-service=$canary_image" -n "$NAMESPACE"
  kubectl rollout status -n "$NAMESPACE" "deployment/auth-service" --timeout=120s

  # Restore 100% traffic to stable
  kubectl patch httproute auth-route -n "$NAMESPACE" \
    --type=merge \
    --patch '{"spec":{"rules":[{"backendRefs":[{"name":"auth-service","port":8081,"weight":100}]}]}}'

  # Delete canary deployment
  kubectl delete deploy auth-service-canary -n "$NAMESPACE" --ignore-not-found=true

  log_info "Canary promoted and cleaned up"
  show_status
}

rollback_canary() {
  log_info "Rolling back canary..."

  # Delete canary deployment
  kubectl delete deploy auth-service-canary -n "$NAMESPACE" --ignore-not-found=true

  # Restore 100% traffic to stable
  kubectl patch httproute auth-route -n "$NAMESPACE" \
    --type=merge \
    --patch '{"spec":{"rules":[{"backendRefs":[{"name":"auth-service","port":8081,"weight":100}]}]}}'

  log_info "Rollback complete"
  show_status
}

rollout_history() {
  log_info "=== Rollout History ==="
  kubectl rollout history deploy/auth-service -n "$NAMESPACE"
  echo ""
  log_info "=== Current Revision ==="
  kubectl rollout history deploy/auth-service -n "$NAMESPACE" --revision=1 | head -20
}

usage() {
  echo "Usage: $0 <command> [args]"
  echo ""
  echo "Commands:"
  echo "  status              Show current deployment status"
  echo "  deploy <image_tag>  Deploy canary with specific image tag"
  echo "  shift <s> <c>       Shift traffic (stable weight, canary weight)"
  echo "  test                Run smoke test"
  echo "  promote             Promote canary to stable"
  echo "  rollback            Rollback canary"
  echo "  history             Show rollout history"
  echo ""
  echo "Examples:"
  echo "  $0 status"
  echo "  $0 deploy v1.1.0"
  echo "  $0 shift 90 10"
  echo "  $0 test"
  echo "  $0 promote"
  echo "  $0 rollback"
}

main() {
  check_deps

  case "${1:-}" in
    status)     show_status ;;
    deploy)     deploy_canary "${2:-latest}" ;;
    shift)      shift_traffic "${2:-90}" "${3:-10}" ;;
    test)       smoke_test ;;
    promote)    promote_canary ;;
    rollback)   rollback_canary ;;
    history)    rollout_history ;;
    *)          usage ;;
  esac
}

main "$@"
