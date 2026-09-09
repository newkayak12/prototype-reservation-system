# 로컬 k3s(패리티) 인프라 조작. 자세한 배경은 README.md.
NS := reservation
CTX := k3d-reservation

.PHONY: up down status data-plane edge logs-kafka \
        pf-mysql-command pf-mysql-query pf-redis pf-kafka

up: ## 클러스터 생성 + 플랫폼 operator + 데이터 면
	./bootstrap.sh

down: ## 클러스터째 삭제
	./teardown.sh

status: ## 파드/Kafka CR/Gateway 상태
	kubectl --context $(CTX) -n $(NS) get pods,svc,kafka,kafkanodepool,gateway
	@echo "--- GatewayClass (cluster-scoped) ---"
	kubectl --context $(CTX) get gatewayclass

data-plane: ## 데이터 면만 재설치(클러스터 유지)
	helm --kube-context $(CTX) upgrade --install data-plane ./charts/data-plane \
		--namespace $(NS) --wait --timeout 10m

edge: ## 엣지(GatewayClass+Gateway)만 재설치
	helm --kube-context $(CTX) upgrade --install edge ./charts/edge \
		--namespace $(NS) --wait --timeout 3m

logs-kafka: ## Kafka 브로커 로그
	kubectl --context $(CTX) -n $(NS) logs -l strimzi.io/cluster=reservation --tail=100 -f

# --- 디버깅용 host port-forward (인-클러스터 앱은 ClusterIP DNS 로 직접 접근) ---
pf-mysql-command: ## localhost:3306 -> command MySQL
	kubectl --context $(CTX) -n $(NS) port-forward svc/mysql-command 3306:3306

pf-mysql-query: ## localhost:3307 -> query MySQL
	kubectl --context $(CTX) -n $(NS) port-forward svc/mysql-query 3307:3306

pf-redis: ## localhost:6379 -> Redis
	kubectl --context $(CTX) -n $(NS) port-forward svc/redis 6379:6379

pf-kafka: ## localhost:9092 -> Kafka (bootstrap)
	kubectl --context $(CTX) -n $(NS) port-forward svc/reservation-kafka-bootstrap 9092:9092
