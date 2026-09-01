#!/usr/bin/env bash
# 로컬 k3s(패리티) 환경 부트스트랩 — 클러스터 생성 → 플랫폼 operator → 데이터 면.
# 멱등: 이미 있으면 건너뛴다. 근거: DESIGN-012 §2 · DESIGN-010 §4.5.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLUSTER_NAME="reservation"
NAMESPACE="reservation"

# operator 버전 핀(재현성). 필요 시 갱신.
STRIMZI_VERSION="0.45.0"
ENVOY_GATEWAY_VERSION="1.2.4"

log() { printf "\033[1;34m▶ %s\033[0m\n" "$1"; }

require() { command -v "$1" >/dev/null 2>&1 || { echo "필수 도구 없음: $1 (brew install $1)"; exit 1; }; }
require k3d
require kubectl
require helm
docker info >/dev/null 2>&1 || { echo "Docker 데몬이 떠 있지 않음. Docker Desktop 을 먼저 실행."; exit 1; }

# 1) k3d 클러스터
if k3d cluster list 2>/dev/null | grep -q "^${CLUSTER_NAME}\b"; then
  log "k3d 클러스터 '${CLUSTER_NAME}' 이미 존재 — 건너뜀"
else
  log "k3d 클러스터 생성"
  k3d cluster create --config "${SCRIPT_DIR}/k3d/cluster.yaml"
fi
kubectl config use-context "k3d-${CLUSTER_NAME}" >/dev/null

# 2) Strimzi Kafka operator (CRD 포함) — Kafka CR 보다 먼저
log "Strimzi operator 설치 (v${STRIMZI_VERSION})"
helm repo add strimzi https://strimzi.io/charts/ >/dev/null 2>&1 || true
helm repo update strimzi >/dev/null
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
helm upgrade --install strimzi strimzi/strimzi-kafka-operator \
  --version "${STRIMZI_VERSION}" \
  --namespace "${NAMESPACE}" \
  --set watchNamespaces="{${NAMESPACE}}" \
  --wait

# 3) Envoy Gateway operator — 엣지(라우트는 Phase 7)
# Gateway API CRD 는 Envoy Gateway 차트가 자체 번들하므로 별도로 설치하지 않는다(충돌 방지).
log "Envoy Gateway 설치 (v${ENVOY_GATEWAY_VERSION})"
helm upgrade --install envoy-gateway oci://docker.io/envoyproxy/gateway-helm \
  --version "v${ENVOY_GATEWAY_VERSION}" \
  --namespace envoy-gateway-system --create-namespace \
  --wait

# 3b) 엣지 GatewayClass + Gateway (라우트는 Phase 7 앱과 함께)
log "엣지(GatewayClass + Gateway) 설치"
helm upgrade --install edge "${SCRIPT_DIR}/charts/edge" \
  --namespace "${NAMESPACE}" \
  --wait --timeout 3m

# 4) 데이터 면
log "데이터 면 설치 (MySQL×2·Redis·localstack·Kafka CR)"
helm upgrade --install data-plane "${SCRIPT_DIR}/charts/data-plane" \
  --namespace "${NAMESPACE}" \
  --wait --timeout 10m

log "완료. 상태: kubectl -n ${NAMESPACE} get pods,kafka,kafkanodepool"
