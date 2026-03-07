#!/usr/bin/env bash
# ============================================================
# Kind K8s Integration Smoke Test
# Supports both HTTP (2-layer) and gRPC (3-layer) modes.
#
# Requires: kind, kubectl (in PATH or /var/jenkins_home/bin)
#
# Usage:
#   bash scripts/kind-smoke-test.sh <image-tag> <mode> [timeout]
#   mode: http | grpc
#   timeout: seconds to wait for pods (default 480)
#
# Examples:
#   bash scripts/kind-smoke-test.sh localhost:5000/arcana/springboot-app:build-42 http 480
#   bash scripts/kind-smoke-test.sh localhost:5000/arcana/springboot-app:build-42 grpc 600
# ============================================================
set -euo pipefail

IMAGE_TAG="${1:-localhost:5000/arcana/springboot-app:latest}"
MODE="${2:-grpc}"
MAX_WAIT="${3:-480}"

export PATH="/var/jenkins_home/bin:${PATH}"

if [ "${MODE}" = "http" ]; then
    MANIFEST="deployment/kubernetes/ci/kind-ci-http.yaml"
    NS="arcana-ci-kind-http"
    NODE_PORT=30091
    APP_LABELS="app in (arcana-ci-service-http,arcana-ci-controller-http)"
    EXPECTED_PODS=2
else
    MANIFEST="deployment/kubernetes/ci/kind-ci-grpc.yaml"
    NS="arcana-ci-kind"
    NODE_PORT=30090
    APP_LABELS="app in (arcana-ci-repository,arcana-ci-service,arcana-ci-controller)"
    EXPECTED_PODS=3
fi

CLUSTER="arcana-ci-k8s-${MODE}-$$"
KUBECONFIG_FILE="/tmp/kind-kubeconfig-${CLUSTER}"

JENKINS_CONTAINER=$(hostname)

cleanup() {
    echo "▶ Cleanup: deleting Kind cluster '${CLUSTER}'"
    kind delete cluster --name "${CLUSTER}" 2>/dev/null || true
    rm -f "${KUBECONFIG_FILE}"
    # Disconnect Jenkins from the kind network (best-effort)
    docker network disconnect kind "${JENKINS_CONTAINER}" 2>/dev/null || true
}
trap cleanup EXIT

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  K8s Integration Smoke Test — ${MODE^^} mode (Kind)       ║"
echo "║  Manifest: ${MANIFEST}"
echo "║  Image: ${IMAGE_TAG}"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

# ── 1. Create Kind cluster ───────────────────────────────────
echo "▶ [1/6] Creating Kind cluster '${CLUSTER}'..."
kind create cluster --name "${CLUSTER}" --wait 60s

# Jenkins runs inside Docker on a different network from the Kind cluster.
# Connect Jenkins container to the 'kind' network so it can reach the
# control-plane container directly (172.25.x.x range).
echo "  Connecting Jenkins container '${JENKINS_CONTAINER}' to kind network..."
docker network connect kind "${JENKINS_CONTAINER}" 2>/dev/null || true
sleep 2

# Get the Kind control-plane container's IP on the kind network
CONTROL_PLANE_IP=$(docker inspect --format \
    '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' \
    "${CLUSTER}-control-plane" 2>/dev/null | tail -1)
echo "  Kind control-plane IP: ${CONTROL_PLANE_IP}"

# Replace 127.0.0.1:PORT with the real container IP:6443 in kubeconfig
kind get kubeconfig --name "${CLUSTER}" | \
    sed "s|https://127.0.0.1:[0-9]*|https://${CONTROL_PLANE_IP}:6443|g" \
    > "${KUBECONFIG_FILE}"
export KUBECONFIG="${KUBECONFIG_FILE}"

# Verify connectivity to the API server
kubectl cluster-info --request-timeout=15s
echo "  ✓ Cluster ready and reachable"

# ── 2. Load app image into Kind ──────────────────────────────
echo ""
echo "▶ [2/6] Loading app image into Kind..."
docker tag "${IMAGE_TAG}" arcana-cloud-java:ci 2>/dev/null || true
kind load docker-image arcana-cloud-java:ci --name "${CLUSTER}"
echo "  ✓ Image loaded"

# ── 3. Apply K8s manifests ───────────────────────────────────
echo ""
echo "▶ [3/6] Applying K8s manifests..."
kubectl apply -f "${MANIFEST}"
echo "  ✓ Manifests applied (namespace: ${NS})"

# ── 4. Wait for MySQL and Redis to be Ready ──────────────────
echo ""
echo "▶ [4/6] Waiting for MySQL and Redis..."
kubectl wait --for=condition=Ready pod -l app=mysql -n "${NS}" --timeout=120s
kubectl wait --for=condition=Ready pod -l app=redis -n "${NS}" --timeout=60s
echo "  ✓ MySQL and Redis ready"

# ── 5. Wait for app pods to be Ready ─────────────────────────
echo ""
echo "▶ [5/6] Waiting for app pods (${EXPECTED_PODS} expected)..."
ELAPSED=0
INTERVAL=10
while true; do
    READY=0
    TOTAL=0
    while IFS= read -r line; do
        TOTAL=$((TOTAL + 1))
        CONTAINERS=$(echo "$line" | awk '{print $2}')
        RUNNING=$(echo "$CONTAINERS" | cut -d/ -f1)
        WANTED=$(echo "$CONTAINERS" | cut -d/ -f2)
        if [ "${RUNNING}" = "${WANTED}" ]; then
            READY=$((READY + 1))
        fi
    done < <(kubectl get pods -n "${NS}" -l "${APP_LABELS}" --no-headers 2>/dev/null || true)

    echo "  ... ${READY}/${EXPECTED_PODS} app pods ready (${ELAPSED}s elapsed)"
    kubectl get pods -n "${NS}" --no-headers 2>/dev/null | grep arcana-ci || true

    if [ "${READY}" -ge "${EXPECTED_PODS}" ]; then
        break
    fi
    if [ "${ELAPSED}" -ge "${MAX_WAIT}" ]; then
        echo "✗ Timed out after ${MAX_WAIT}s"
        echo "=== Pod descriptions ==="
        kubectl describe pods -n "${NS}" 2>/dev/null | tail -40 || true
        exit 1
    fi
    sleep "${INTERVAL}"
    ELAPSED=$((ELAPSED + INTERVAL))
done
echo "  ✓ All ${EXPECTED_PODS} app pods ready"

# ── 6. Run smoke test via NodePort ───────────────────────────
echo ""
echo "▶ [6/6] Running smoke test via NodePort ${NODE_PORT}..."
NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}' 2>/dev/null)
echo "  Node IP: ${NODE_IP}"
BASE_URL="http://${NODE_IP}:${NODE_PORT}"

bash scripts/integration-smoke-test.sh "${BASE_URL}" "k8s-${MODE}" 120

echo ""
echo "╔══════════════════════════════════════════════════════╗"
printf "║  ✅ K8s %-41s ║\n" "${MODE^^} — ALL TESTS PASSED"
echo "║  Real Kubernetes pod networking validated            ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""
