#!/usr/bin/env bash
# ============================================================
# Kind K8s Integration Smoke Test — 3-layer gRPC
# Spins up a Kind cluster, loads the CI image, applies K8s
# manifests, waits for all pods to be Ready, then runs the
# standard smoke test through the controller NodePort.
#
# Requires: kind, kubectl (in PATH or /var/jenkins_home/bin)
# Usage: bash scripts/kind-smoke-test.sh <image-tag> [timeout-seconds]
# ============================================================
set -euo pipefail

IMAGE_TAG="${1:-localhost:5000/arcana/springboot-app:latest}"
MAX_WAIT="${2:-480}"   # seconds to wait for all pods ready

export PATH="/var/jenkins_home/bin:$PATH"

CLUSTER="arcana-ci-$$"
MANIFEST="deployment/kubernetes/ci/kind-ci-grpc.yaml"
NS="arcana-ci-kind"

cleanup() {
    echo "▶ Cleanup: deleting Kind cluster '${CLUSTER}'"
    kind delete cluster --name "${CLUSTER}" 2>/dev/null || true
}
trap cleanup EXIT

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  K8s Integration Smoke Test — 3-layer gRPC (Kind)   ║"
echo "║  Image: ${IMAGE_TAG}"
echo "║  Cluster: ${CLUSTER}"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

# ── 1. Create Kind cluster ───────────────────────────────────
echo "▶ [1/6] Creating Kind cluster '${CLUSTER}'..."
kind create cluster --name "${CLUSTER}" --wait 60s
export KUBECONFIG="$(kind get kubeconfig --name "${CLUSTER}" 2>/dev/null | head -1)"
kind get kubeconfig --name "${CLUSTER}" > /tmp/kind-kubeconfig-${CLUSTER}
export KUBECONFIG="/tmp/kind-kubeconfig-${CLUSTER}"
echo "  ✓ Cluster created"

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
echo "  ✓ Manifests applied"

# ── 4. Wait for MySQL and Redis to be Ready ──────────────────
echo ""
echo "▶ [4/6] Waiting for MySQL and Redis to be Ready..."
kubectl wait --for=condition=Ready pod -l app=mysql -n "${NS}" --timeout=120s
kubectl wait --for=condition=Ready pod -l app=redis -n "${NS}" --timeout=60s
echo "  ✓ MySQL and Redis ready"

# ── 5. Wait for all app pods to be Ready ────────────────────
echo ""
echo "▶ [5/6] Waiting for app pods (repository → service → controller)..."
ELAPSED=0
INTERVAL=10
while true; do
    READY=$(kubectl get pods -n "${NS}" \
        -l 'app in (arcana-ci-repository,arcana-ci-service,arcana-ci-controller)' \
        --no-headers 2>/dev/null | \
        awk '{split($2,a,"/"); if(a[1]==a[2]) print "ready"}' | \
        wc -l | tr -d ' ')
    TOTAL=$(kubectl get pods -n "${NS}" \
        -l 'app in (arcana-ci-repository,arcana-ci-service,arcana-ci-controller)' \
        --no-headers 2>/dev/null | wc -l | tr -d ' ')
    echo "  ... ${READY}/${TOTAL} app pods ready (${ELAPSED}s)"
    kubectl get pods -n "${NS}" --no-headers 2>/dev/null | grep arcana-ci || true
    if [ "${READY}" -ge "3" ] 2>/dev/null; then
        break
    fi
    if [ "${ELAPSED}" -ge "${MAX_WAIT}" ]; then
        echo "✗ Timed out after ${MAX_WAIT}s waiting for pods"
        kubectl describe pods -n "${NS}" 2>/dev/null | tail -30 || true
        exit 1
    fi
    sleep "${INTERVAL}"
    ELAPSED=$((ELAPSED + INTERVAL))
done
echo "  ✓ All 3 app pods ready"

# ── 6. Run smoke test via NodePort ──────────────────────────
echo ""
echo "▶ [6/6] Running smoke test via Kind NodePort (30090)..."
# Get Kind node IP
NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}' 2>/dev/null)
echo "  Node IP: ${NODE_IP}"
BASE_URL="http://${NODE_IP}:30090"

bash scripts/integration-smoke-test.sh "${BASE_URL}" "k8s-grpc" 120

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  ✅ K8s 3-layer gRPC — ALL TESTS PASSED             ║"
echo "║  Real Kubernetes pod networking + gRPC service mesh  ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""
