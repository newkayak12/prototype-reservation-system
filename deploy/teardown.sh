#!/usr/bin/env bash
# 로컬 k3s(패리티) 환경 정리 — 클러스터째 삭제(레지스트리 포함).
set -euo pipefail
CLUSTER_NAME="reservation"
if k3d cluster list 2>/dev/null | grep -q "^${CLUSTER_NAME}\b"; then
  echo "▶ k3d 클러스터 '${CLUSTER_NAME}' 삭제"
  k3d cluster delete "${CLUSTER_NAME}"
else
  echo "삭제할 클러스터 없음: ${CLUSTER_NAME}"
fi
