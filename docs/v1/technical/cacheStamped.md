# Cache Stampede 이론적 해결책

## Cache Stampede란?
캐시 만료 시점에 동시에 대량의 요청이 몰려서 모든 요청이 동시에 백엔드 데이터베이스에 접근하는 현상

---

## 주요 해결책

### 1. PER (Probabilistic Early Refresh)
TTL 만료 전에 확률적으로 캐시를 미리 갱신하는 방법. 베타 분포나 로그함수를 이용해 시간이 지날수록 갱신 확률을 높임

### 2. Distributed Lock
분산 환경에서 뮤텍스를 구현하여 첫 번째 요청만 데이터베이스에 접근하도록 제어. 나머지 요청들은 락이 해제될 때까지 대기

### 3. Single Flight Pattern  
동일한 키에 대한 여러 요청을 하나의 요청으로 병합하여 처리. 메모리 기반으로 요청을 그룹화하고 결과를 공유

### 4. TTL Jitter
캐시 TTL에 랜덤한 시간을 추가하여 캐시들이 동시에 만료되지 않도록 시차를 둠

### 5. Refresh-Ahead  
백그라운드 스케줄러가 TTL 만료 전 미리 캐시를 갱신하는 선제적 접근법

### 6. Cache Warming
시스템 시작 시점이나 배포 시점에 미리 인기 있는 데이터들을 캐시에 적재

### 7. Semaphore 제한
동시에 데이터베이스에 접근할 수 있는 요청 수를 제한하여 stampede 규모를 축소

### 8. Placeholder Strategy
캐시 미스 시 임시 placeholder 값을 캐시에 저장한 후 실제 데이터를 비동기로 로딩하여 stampede 방지

### 9. Lock-Free Atomic Operations
Redis의 원자적 연산(SETNX, INCR 등)을 활용해 락 없이도 단일 스레드만 캐시 갱신하도록 제어

### 10. Negative Caching
데이터가 없거나 에러가 발생한 경우에도 짧은 TTL로 캐시하여 반복적인 실패 요청으로 인한 stampede 방지

---

## 접근법 분류

### 예방적 접근 (Proactive)
- PER, Refresh-Ahead, Cache Warming, TTL Jitter

### 제어적 접근 (Reactive)  
- Distributed Lock, Single Flight, Semaphore 제한, Lock-Free Atomic Operations

### 복합적 접근 (Hybrid)
- Placeholder Strategy, Negative Caching

### 확률적 접근 (Probabilistic)
- PER, TTL Jitter
