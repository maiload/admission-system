# 7. Infrastructure & Deployment

## 7.1 Docker Compose Service Map

```yaml
services:
  # --- Load Balancers ---
  haproxy-gate:       # Queue Gate LB
  haproxy-core:       # Ticketing Core LB

  # --- Application ---
  queue-gate-1:       # Gate instance 1
  queue-gate-2:       # Gate instance 2
  admission-worker-1: # Worker instance 1
  admission-worker-2: # Worker instance 2
  ticketing-core-1:   # Core instance 1
  ticketing-core-2:   # Core instance 2

  # --- Data ---
  redis-node-1:       # Redis Cluster master 1
  redis-node-2:       # Redis Cluster master 2
  redis-node-3:       # Redis Cluster master 3
  redis-node-4:       # Redis Cluster replica 1
  redis-node-5:       # Redis Cluster replica 2
  redis-node-6:       # Redis Cluster replica 3
  redis-cluster-init: # Cluster 초기화 (1회성)
  postgres:           # PostgreSQL

  # --- Frontend ---
  frontend:           # React SPA (Vite dev server or nginx)
```

## 7.2 Port Allocation

| Service | Internal Port | External Port | Description |
|---------|--------------|---------------|-------------|
| haproxy-gate | 8080 | 8080 | Gate LB |
| haproxy-core | 8081 | 8081 | Core LB |
| queue-gate-1 | 8010 | - | Gate instance |
| queue-gate-2 | 8010 | - | Gate instance |
| admission-worker-1 | 8020 | - | Worker (내부 관리용) |
| admission-worker-2 | 8020 | - | Worker (내부 관리용) |
| ticketing-core-1 | 8030 | - | Core instance |
| ticketing-core-2 | 8030 | - | Core instance |
| redis-node-1~6 | 6379 | 7001~7006 | Redis Cluster |
| postgres | 5432 | 5432 | PostgreSQL |
| frontend | 5173 | 5173 | Vite dev / nginx |

## 7.3 Network Topology

```
┌─────────────────────────────────────────────────────┐
│                    frontend-net                      │
│  ┌──────────┐                                       │
│  │ frontend │                                       │
│  └────┬─────┘                                       │
│       │ :5173                                       │
└───────┼─────────────────────────────────────────────┘
        │
        │ (브라우저 → HAProxy)
        ▼
┌─────────────────────────────────────────────────────┐
│                     app-net                          │
│                                                     │
│  ┌──────────────┐          ┌──────────────┐         │
│  │ haproxy-gate │          │ haproxy-core │         │
│  │    :8080     │          │    :8081     │         │
│  └──┬───────┬───┘          └──┬───────┬───┘         │
│     │       │                 │       │             │
│     ▼       ▼                 ▼       ▼             │
│  ┌──────┐ ┌──────┐      ┌──────┐ ┌──────┐          │
│  │gate-1│ │gate-2│      │core-1│ │core-2│          │
│  │:8010 │ │:8010 │      │:8030 │ │:8030 │          │
│  └──────┘ └──────┘      └──────┘ └──────┘          │
│                                                     │
│  ┌──────────┐  ┌──────────┐                         │
│  │worker-1  │  │worker-2  │                         │
│  │:8020     │  │:8020     │                         │
│  └──────────┘  └──────────┘                         │
│                                                     │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                     data-net                         │
│                                                     │
│  ┌────────────────────────────────────────┐         │
│  │          Redis Cluster (6 nodes)       │         │
│  │  M1:7001  M2:7002  M3:7003            │         │
│  │  R1:7004  R2:7005  R3:7006            │         │
│  └────────────────────────────────────────┘         │
│                                                     │
│  ┌────────────┐                                     │
│  │ PostgreSQL │                                     │
│  │   :5432    │                                     │
│  └────────────┘                                     │
│                                                     │
└─────────────────────────────────────────────────────┘
```

## 7.4 HAProxy Configuration

### Gate LB (haproxy-gate.cfg)

```
global
    maxconn 50000

defaults
    mode http
    timeout connect 5s
    timeout client  300s    # SSE 장기 연결 허용
    timeout server  300s
    timeout tunnel  300s    # WebSocket/SSE tunnel

frontend gate_front
    bind *:8080
    default_backend gate_back

backend gate_back
    balance leastconn       # SSE 장기 연결에 유리
    option httpchk GET /actuator/health
    http-check expect status 200
    server gate1 queue-gate-1:8010 check inter 3s fall 3 rise 2
    server gate2 queue-gate-2:8010 check inter 3s fall 3 rise 2
```

### Core LB (haproxy-core.cfg)

```
global
    maxconn 20000

defaults
    mode http
    timeout connect 5s
    timeout client  60s
    timeout server  60s

frontend core_front
    bind *:8081
    default_backend core_back

backend core_back
    balance leastconn
    option httpchk GET /actuator/health
    http-check expect status 200
    server core1 ticketing-core-1:8030 check inter 3s fall 3 rise 2
    server core2 ticketing-core-2:8030 check inter 3s fall 3 rise 2
```

**Key Decisions**

| Decision | Choice | Reason |
|----------|--------|--------|
| Algorithm | leastconn | SSE 장기 연결이 특정 서버에 몰리지 않도록 |
| Health check | /actuator/health | Spring Boot Actuator 기본 엔드포인트 |
| Gate timeout | 300s | SSE 연결 유지 (5분) |
| Core timeout | 60s | 일반 API 요청 (홀드 TTL과 동일) |

## 7.5 Redis Cluster Setup

### Node Configuration

| Node | Role | Port |
|------|------|------|
| redis-node-1 | Master (slot 0~5460) | 7001 |
| redis-node-2 | Master (slot 5461~10922) | 7002 |
| redis-node-3 | Master (slot 10923~16383) | 7003 |
| redis-node-4 | Replica of node-1 | 7004 |
| redis-node-5 | Replica of node-2 | 7005 |
| redis-node-6 | Replica of node-3 | 7006 |

### Hash Tag Strategy

모든 키에 `{eventId}:{scheduleId}` 해시태그를 사용하여 동일 이벤트/스케줄의 키가 같은 슬롯에 배치되도록 한다.

```
q:{ev_2026_0101}:{sc_2026_0101_2000}:z        → slot = CRC16("{ev_2026_0101}:{sc_2026_0101_2000}") % 16384
qstate:{ev_2026_0101}:{sc_2026_0101_2000}:qt1  → same slot
enter:{ev_2026_0101}:{sc_2026_0101_2000}:jti1   → same slot
```

Lua 스크립트 내에서 접근하는 모든 키가 동일 슬롯에 있어야 CROSSSLOT 에러가 발생하지 않는다.

### Cluster Init Command

```bash
redis-cli --cluster create \
  redis-node-1:6379 redis-node-2:6379 redis-node-3:6379 \
  redis-node-4:6379 redis-node-5:6379 redis-node-6:6379 \
  --cluster-replicas 1 --cluster-yes
```

## 7.6 PostgreSQL Configuration

```yaml
postgres:
  image: postgres:16
  environment:
    POSTGRES_DB: ticketing
    POSTGRES_USER: ticketing
    POSTGRES_PASSWORD: ticketing_pw
  ports:
    - "5432:5432"
  volumes:
    - postgres-data:/var/lib/postgresql/data
    - ./infra/sql/init.sql:/docker-entrypoint-initdb.d/init.sql
```

`init.sql`에 DDL + seed data 포함.

## 7.7 Application Configuration (application.yml)

### Queue Gate

```yaml
server:
  port: 8010

spring:
  data:
    redis:
      cluster:
        nodes: redis-node-1:6379,redis-node-2:6379,redis-node-3:6379

gate:
  sync:
    token-secret: ${SYNC_TOKEN_SECRET}
    window-ms: 5000
    join-window-before-ms: 2000     # startAt - 2s
    join-window-after-ms: 10000     # startAt + 10s
    token-ttl-after-start-ms: 15000 # startAt + 15s
  queue:
    state-ttl-sec: 1800             # 30min
  sse:
    push-interval-ms: 1000
  client:
    cookie-name: cid
    cookie-max-age-days: 7
```

### Admission Worker

```yaml
server:
  port: 8020

spring:
  data:
    redis:
      cluster:
        nodes: redis-node-1:6379,redis-node-2:6379,redis-node-3:6379

admission:
  worker:
    poll-interval-ms: 200
    max-batch: 200
    rate-cap: 200
    concurrency-cap: 10000
  token:
    secret: ${ENTER_TOKEN_SECRET}
    ttl-sec: 120
    rate-counter-ttl-sec: 3
```

### Ticketing Core

```yaml
server:
  port: 8030

spring:
  r2dbc:
    url: r2dbc:postgresql://postgres:5432/ticketing
    username: ticketing
    password: ticketing_pw
  data:
    redis:
      cluster:
        nodes: redis-node-1:6379,redis-node-2:6379,redis-node-3:6379

core:
  session:
    secret: ${CORE_SESSION_SECRET}
    ttl-sec: 300
  hold:
    ttl-sec: 60
  scheduler:
    hold-cleanup-interval-ms: 2000
    active-cleanup-interval-ms: 10000
  soldout:
    ttl-sec: 86400
```

## 7.8 Environment Variables

| Variable | Used By | Description |
|----------|---------|-------------|
| `SYNC_TOKEN_SECRET` | queue-gate | syncToken HMAC 비밀키 |
| `ENTER_TOKEN_SECRET` | admission-worker, ticketing-core | enterToken HMAC 비밀키 |
| `CORE_SESSION_SECRET` | ticketing-core | coreSessionToken HMAC 비밀키 |
| `POSTGRES_DB` | postgres | DB 이름 |
| `POSTGRES_USER` | postgres | DB 사용자 |
| `POSTGRES_PASSWORD` | postgres, ticketing-core | DB 비밀번호 |

## 7.9 Health Check Endpoints

모든 애플리케이션 모듈은 Spring Boot Actuator를 사용:

- **Liveness**: `GET /actuator/health/liveness`
- **Readiness**: `GET /actuator/health/readiness`
- **General**: `GET /actuator/health`

HAProxy는 `GET /actuator/health`로 health check 수행.
