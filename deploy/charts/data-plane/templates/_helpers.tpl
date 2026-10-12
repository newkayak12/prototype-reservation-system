{{/* 공통 라벨 — 데이터 면 리소스 식별 */}}
{{- define "data-plane.labels" -}}
app.kubernetes.io/part-of: reservation-data-plane
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}
