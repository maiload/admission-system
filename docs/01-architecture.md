# 1. Architecture Overview

## 1.1 Project Summary

동시 접속 최대 100만을 가정한 티켓팅 서비스.
대기열(Queue) - 입장권(Admission) - 좌석 선점(Hold) - 확정(Confirm) 흐름을 구현하며,
멀티 워커 + Redis Cluster + Lua 원자화로 공정성과 안정성을 보장한다.

## 1.2 Core Goals

| Goal | Description |
|------|-------------|
| 폭주 트래픽 흡수 | 오픈 순간 동시 100만 사용자를 시스템이 무너지지 않고 처리 |
| 입장 제어 | 코어(좌석 API)를 보호하기 위해 입장권을 제한 발급 |
| 좌석 강정합 | 좌석 중복 판매 0 (홀드/확정의 정합성) |
| 현실감 UX | "정각 클릭" 감성(밀리초 카운트다운) + SSE 기반 실시간 대기열 |

## 1.3 System Components

```
┌─────────────┐
│   Frontend   │  React + Vite + Tailwind + Zustand
│  (SPA)       │  SSE client (EventSource)
└──────┬───────┘
       │
       ▼
┌──────────────┐     ┌──────────────────┐
│  HAProxy     │     │    HAProxy       │
│  (Gate LB)   │     │   (Core LB)      │
└──────┬───────┘     └───────┬──────────┘
       │                     │
       ▼                     ▼
┌──────────────┐     ┌──────────────────┐
│  Queue Gate  │     │ Ticketing Core   │
│  (WebFlux)   │     │ (WebFlux+R2DBC)  │
│  x N 인스턴스 │     │  x K 인스턴스     │
└──────┬───────┘     └───────┬──────────┘
       │                     │
       ▼                     ▼
┌──────────────┐     ┌──────────────────┐
│    Redis     │     │   PostgreSQL     │
│   Cluster    │◄────│                  │
│  (6 nodes)   │     │                  │
└──────────────┘     └──────────────────┘
       ▲
       │
┌──────────────┐
│  Admission   │
│   Worker     │
│  x M 인스턴스 │
│ (polling)    │
└──────────────┘
```

## 1.4 Module Structure (Gradle Multi-Module)

```
admission-system/
├── common/                  # 공유 코드
│   ├── Redis key constants
│   ├── Token utilities (HMAC)
│   ├── Shared DTOs
│   └── Error code definitions
├── queue-gate/              # 대기열 게이트 (WebFlux)
│   ├── GET  /gate/sync
│   ├── POST /gate/join
│   ├── GET  /gate/stream (SSE)
│   └── GET  /gate/status
├── admission-worker/        # 입장권 발급 워커
│   ├── Polling scheduler
│   └── Lua-based batch issuer
├── ticketing-core/          # 좌석 코어 (WebFlux + R2DBC)
│   ├── GET  /core/seats
│   ├── POST /core/holds
│   ├── POST /core/holds/{id}/confirm
│   ├── Hold expiry scheduler
│   └── Active session cleanup scheduler
├── frontend/                # React SPA
├── infra/                   # Docker Compose, HAProxy config, Redis config
└── docs/                    # 설계 문서
```

## 1.5 Component Responsibilities

### Queue Gate
- 클라이언트 시간 동기화 (`/gate/sync`)
- 대기열 등록 (`/gate/join`) - Lua 원자화
- SSE 실시간 대기열 상태 스트리밍
- SOLD_OUT 플래그 감지 및 전파

### Admission Worker
- Redis polling 기반 (100ms~500ms 주기)
- Lua 원자화로 상위 N명 선별 + enter_token 발급
- rate cap + concurrency cap 준수
- 멀티 워커 환경에서 중복/초과 발급 방지

### Ticketing Core
- enter_token 검증 + coreSessionToken 발급 (Lua 원자화)
- 좌석 조회/홀드/확정 (DB 트랜잭션)
- Hold 만료 정리 스케줄러 (@Scheduled)
- Active session 정리 스케줄러 (@Scheduled)
- SOLD_OUT 감지 시 Redis 플래그 세팅

### Frontend
- 정각 클릭 카운트다운 (밀리초)
- SSE 대기열 화면
- Zone 선택 -> 좌석 그리드
- 60초 결제 타이머

## 1.6 Communication Pattern

```
Gate ←→ Redis Cluster ←→ Admission Worker
                ↕
          Ticketing Core ←→ PostgreSQL

Gate와 Core 간 직접 통신 없음.
Redis의 토큰(enter_token)이 유일한 연결 고리.
```

## 1.7 Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React, Vite, Tailwind CSS, Zustand, SSE (EventSource) |
| Backend | Java 21, Spring Boot 4.0.x, Spring WebFlux |
| Data (Reactive) | Spring Data R2DBC + PostgreSQL, DatabaseClient + R2dbcEntityTemplate |
| Cache/Queue | Spring Data Redis Reactive (Lettuce) + Redis Cluster, Redis Lua scripting |
| Infra | HAProxy (x2), Docker Compose |
| Observability | Micrometer + Prometheus/Grafana (권장), Logback + JSON 로그 (선택) |

## 1.8 Architecture Patterns (Summary)

모듈별로 다른 패턴을 적용한다. 상세는 [08-architecture-patterns.md](./08-architecture-patterns.md) 참조.

| Module | Hexagonal | DDD | CQRS | 특수 패턴 |
|--------|-----------|-----|------|----------|
| common | - | - | - | 계약/상수 공유만 |
| queue-gate | Strong | X | X | - |
| admission-worker | Strong | X | X | Engine Pattern |
| ticketing-core | Strong | Full | Lightweight | Aggregate + State Machine |

**공통 원칙:**
- `domain/`, `application/`은 프레임워크 의존 ZERO
- 입출력은 Port(인터페이스)로 정의, Adapter가 구현
- 모듈 간 직접 의존 없음. common만 공유, 런타임 연결은 Redis 토큰

## 1.9 Atomicity Points

| Point | Mechanism | Purpose |
|-------|-----------|---------|
| Queue Join | Redis Lua | 중복 등록 방지 + 상태 저장 + ZADD 원자화 |
| Admission Issue | Redis Lua | pop + 토큰 발급 + 상태 변경 + 제한 체크 원자화 |
| Core Handshake | Redis Lua | enter_token DEL + session SET + active SADD 원자화 |
| Seat Hold | DB Transaction + Unique Constraint | 좌석 중복 홀드 방지 |
| Seat Confirm | DB Transaction + Unique Constraint | 중복 확정 방지 |
