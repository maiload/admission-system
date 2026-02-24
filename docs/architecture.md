# Architecture

## 1. Project Summary

동시 접속 최대 100만을 가정한 티켓팅 서비스.
대기열(Queue) - 입장권(Admission) - 좌석 선점(Hold) - 확정(Confirm) 흐름을 구현하며,
멀티 워커 + Redis Cluster + Lua 원자화로 공정성과 안정성을 보장한다.

## 2. Core Goals

| Goal | Description |
|------|-------------|
| 폭주 트래픽 흡수 | 오픈 순간 동시 100만 사용자를 시스템이 무너지지 않고 처리 |
| 입장 제어 | 코어(좌석 API)를 보호하기 위해 입장권을 제한 발급 |
| 좌석 강정합 | 좌석 중복 판매 0 (홀드/확정의 정합성) |
| 현실감 UX | "정각 클릭" 감성(밀리초 카운트다운) + SSE 기반 실시간 대기열 |

## 3. System Components

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

## 4. Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React, Vite, Tailwind CSS, Zustand, SSE (EventSource) |
| Backend | Java 21, Spring Boot 4.0.x, Spring WebFlux |
| Data (Reactive) | Spring Data R2DBC + PostgreSQL, DatabaseClient + R2dbcEntityTemplate |
| Cache/Queue | Spring Data Redis Reactive (Lettuce) + Redis Cluster, Redis Lua scripting |
| Infra | HAProxy, Nginx, Docker Compose |
| Observability | Micrometer + Prometheus/Grafana, Logback + JSON 로그 (선택) |

## 5. Communication Pattern

```
Gate ←→ Redis Cluster ←→ Admission Worker
                ↕
          Ticketing Core ←→ PostgreSQL

Gate와 Core 간 직접 통신 없음.
Redis의 토큰(enter_token)이 유일한 연결 고리.
```

## 6. Module Structure (Gradle Multi-Module)

```
admission-system/
├── common/                  # 공유 코드
│   ├── Redis key constants
│   ├── Token utilities (HMAC)
│   ├── Shared DTOs
│   └── Error code definitions
├── queue-gate/              # 대기열 게이트 (WebFlux)
│   ├── POST /gate/join
│   ├── GET  /gate/stream (SSE)
│   └── GET  /gate/status
├── admission-worker/        # 입장권 발급 워커
│   ├── Polling scheduler
│   └── Lua-based batch issuer
├── ticketing-core/          # 좌석 코어 (WebFlux + R2DBC)
│   ├── POST /core/enter
│   ├── GET  /core/seats
│   ├── POST /core/holds
│   ├── POST /core/holds/{holdGroupId}/confirm
│   ├── GET  /core/schedules/active
│   ├── GET  /core/admin/schedules/activate
│   ├── DELETE /core/admin/schedules
│   ├── Hold expiry scheduler
│   └── Active session cleanup scheduler
├── frontend/                # React SPA
├── infra/                   # Docker Compose, HAProxy config, Redis config
└── docs/                    # 설계 문서
```

## 7. Component Responsibilities

### Queue Gate
- 대기열 등록 (`/gate/join`) — clientId 쿠키 기반, Lua 원자화
- SSE 실시간 대기열 상태 스트리밍 (`/gate/stream`)
- 대기열 상태 polling (`/gate/status`)
- SOLD_OUT 플래그 감지 및 전파

### Admission Worker
- Redis polling 기반 (100ms~500ms 주기)
- Lua 원자화로 상위 N명 선별 + enter_token 발급
- rate cap + concurrency cap 준수
- 멀티 워커 환경에서 중복/초과 발급 방지

### Ticketing Core
- enter_token 검증 + coreSessionToken 쿠키 발급 (Lua 원자화)
- 좌석 조회/홀드(복수 좌석)/확정 (DB 트랜잭션)
- 스케줄 활성화 관리 (Admin API)
- Hold 만료 정리 스케줄러 (@Scheduled)
- Active session 정리 스케줄러 (@Scheduled)
- SOLD_OUT 감지 시 Redis 플래그 세팅

### Frontend
- SSE 대기열 화면 (순위, 진행 상태)
- Zone 선택 → 좌석 그리드 (복수 좌석 선택)
- 결제 타이머 (홀드 만료 카운트다운)
- 세션 쿠키 기반 인증

## 8. Atomicity Points

| Point | Mechanism | Purpose |
|-------|-----------|---------|
| Queue Join | Redis Lua | 중복 등록 방지 + 상태 저장 + ZADD 원자화 |
| Admission Issue | Redis Lua | pop + 토큰 발급 + 상태 변경 + 제한 체크 원자화 |
| Core Handshake | Redis Lua | enter_token DEL + session SET + active SADD 원자화 |
| Seat Hold | DB Transaction + Unique Constraint | 좌석 중복 홀드 방지 |
| Seat Confirm | DB Transaction + Unique Constraint | 중복 확정 방지 |

---

## 9. Architecture Patterns

### 9.1 Overall Principle

전체 모듈 공통으로 **Hexagonal(Ports & Adapters) + 얇은 Layered**를 적용한다.
DDD/CQRS는 ticketing-core에만 적용하고, 나머지 모듈은 필요한 만큼만 사용한다.

| Module | Hexagonal | DDD | CQRS | 특수 패턴 |
|--------|-----------|-----|------|----------|
| common | - | - | - | 계약/상수 공유만 |
| queue-gate | **Strong** | X | X | - |
| admission-worker | **Strong** | X | X | Engine Pattern |
| ticketing-core | **Strong** | **Full** | **Lightweight** | Aggregate + State Machine |

### 공통 패키지 구조

```
{module}/
├── domain/           # 엔티티, VO, 도메인 서비스, 상태머신, 불변식
│                     # 프레임워크 의존 ZERO
├── application/      # Inbound Port/Service, Port(인터페이스), DTO
│                     # 프레임워크 의존 ZERO
├── adapter/
│   ├── in/           # Web Controller, SSE Handler, Scheduler
│   │                 # 프레임워크 의존 (Spring WebFlux 등)
│   └── out/          # Redis Adapter, DB Adapter, Clock, IdGen
│                     # 프레임워크 의존 (Lettuce, R2DBC 등)
└── config/           # Spring Configuration, Bean 정의
```

**핵심 규칙:**
- `domain/`과 `application/`은 Spring, Redis, DB 의존 금지
- 입출력은 반드시 `application/`의 Port 인터페이스를 통해서만
- `adapter/`가 Port를 구현하고, `config/`에서 Bean으로 연결

### 9.2 queue-gate

**성격**: 상태는 Redis에 존재, DB 없음. 핵심: 트래픽 제어, 대기열 관리, SSE 스트리밍. 복잡한 비즈니스 규칙 없음.

**패턴**: Hexagonal (DDD/CQRS 미적용)

```
queue-gate/src/main/java/com/example/ticket/gate/
├── domain/
│   └── QueueStatus.java              # enum: WAITING, ADMISSION_GRANTED, EXPIRED, SOLD_OUT
│
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── JoinQueueInPort.java      # /gate/join 처리
│   │   │   └── StreamQueueInPort.java    # /gate/stream, /gate/status 처리
│   │   └── out/
│   │       ├── QueueRepositoryPort.java  # Redis ZSET/HASH 추상화
│   │       ├── ScheduleQueryPort.java    # 활성 스케줄 startAt 조회
│   │       └── SoldOutQueryPort.java     # soldout 플래그 조회
│   ├── service/
│   │   ├── JoinQueueService.java         # JoinQueueInPort 구현
│   │   └── StreamQueueService.java       # StreamQueueInPort 구현
│   └── metrics/
│       └── GateMetrics.java              # Micrometer 메트릭
│
├── adapter/
│   ├── in/
│   │   └── web/
│   │       ├── GateController.java       # /gate/* endpoints
│   │       └── GlobalExceptionHandler.java
│   └── out/
│       ├── redis/
│       │   ├── RedisQueueAdapter.java    # QueueRepositoryPort 구현 (Lua 호출 포함)
│       │   ├── RedisScheduleQuery.java   # ScheduleQueryPort 구현
│       │   └── RedisSoldOutQuery.java    # SoldOutQueryPort 구현
│       └── system/
│           ├── SystemClock.java          # ClockPort 구현
│           └── UuidGenerator.java        # IdGeneratorPort 구현
│
└── config/
    ├── GateConfig.java                   # Bean 조립
    └── GateProperties.java              # 설정값 바인딩
```

#### Inbound Port 흐름

**JoinQueueInPort (JoinQueueService)**
```
Input:  eventId, scheduleId, clientId, loadTest
Output: JoinResult(queueToken, sseUrl, alreadyJoined)

1. ScheduleQueryPort로 스케줄 startAt 조회
2. SoldOutQueryPort로 매진 확인
3. QueueRepositoryPort.join() → Lua 원자 실행
4. JoinResult 반환
```

**StreamQueueInPort (StreamQueueService)**
```
Input:  eventId, scheduleId, queueToken
Output: Flux<ProgressResult> (stream) 또는 Mono<ProgressResult> (poll)

1. 1초 interval Flux 생성 (stream) 또는 단일 조회 (poll)
2. QueueRepositoryPort.getState(queueToken) — 상태, 순위 조회
3. QueueRepositoryPort.getQueueSize — 전체 대기열 크기 조회
4. SoldOutQueryPort 확인
5. ProgressResult emit (status, rank, totalInQueue, enterToken)
6. ADMISSION_GRANTED 시 enterToken 포함
7. SOLD_OUT/EXPIRED 시 스트림 종료
```

### 9.3 admission-worker

**성격**: 주기적 배치 엔진. 핵심: rate/concurrency cap 준수 + Lua 원자적 발급. 도메인 모델 불필요, 설정 객체가 핵심.

**패턴**: Hexagonal + Job Pattern

```
admission-worker/src/main/java/com/example/ticket/admission/
├── domain/
│   └── AdmissionConfig.java          # 발급 설정 (rateCap, concurrencyCap, maxBatch)
│
├── application/
│   ├── job/
│   │   └── AdmissionJob.java         # tick loop 실행 (스케줄별 순회)
│   ├── port/
│   │   └── out/
│   │       ├── IssuerPort.java              # Lua 스크립트 실행 추상화
│   │       ├── ActiveSchedulePort.java      # 활성 스케줄 목록 조회
│   │       └── TokenGeneratorPort.java      # enterToken 생성 (jti + HMAC)
│   └── metrics/
│       └── AdmissionMetrics.java      # Micrometer 메트릭
│
├── adapter/
│   ├── in/
│   │   └── scheduler/
│   │       └── AdmissionPoller.java   # @Scheduled: 200ms마다 job.tick() 호출
│   └── out/
│       ├── redis/
│       │   ├── RedisIssuer.java       # IssuerPort 구현 (Lua EVAL)
│       │   └── RedisActiveSchedule.java
│       ├── token/
│       │   └── HmacTokenGenerator.java
│       └── system/
│           ├── SystemClock.java
│           └── UuidGenerator.java
│
└── config/
    ├── WorkerConfig.java
    └── AdmissionProperties.java      # 설정값 바인딩
```

#### Job 흐름

**AdmissionJob.tick()**
```
1. ActiveSchedulePort로 활성 스케줄 목록 획득
2. 각 스케줄에 대해 processSchedule():
   a. TokenGeneratorPort로 maxBatch개만큼 jti+token 사전 생성
   b. IssuerPort.issue(keys, args, tokens) → Lua 실행
   c. IssueResult 로깅 (메트릭 기록)
3. 발급 가능량이 0이면 해당 스케줄 skip
```

### 9.4 ticketing-core

**성격**: 좌석 상태 머신 (AVAILABLE → HELD → CONFIRMED). Hold 불변식: 만료 검증, 1인 1좌석. DB 트랜잭션 경계 중요. Read(좌석 조회)와 Write(홀드/확정) 성격이 다름.

**패턴**: Hexagonal + DDD + Lightweight CQRS

#### DDD 적용 범위

| DDD 요소 | 적용 | 설명 |
|----------|------|------|
| Entity | O | Hold, Reservation |
| Value Object | O | SeatStatus, Zone |
| Aggregate | O | HoldAggregate (핵심) |
| Domain Service | O | SeatAvailabilityService |
| Repository (Port) | O | HoldRepositoryPort, SeatQueryPort 등 |
| Domain Event | X | 범위 초과 (이벤트 소싱 안 함) |
| Bounded Context | X | 단일 모듈 내 단일 컨텍스트 |

#### CQRS 적용 범위

| 구분 | 방식 | 설명 |
|------|------|------|
| Write | HoldService (createHold, confirmHold) | DB 트랜잭션, 불변식 검증 |
| Read | SeatQueryService | 조인/프로젝션, 읽기 최적화 |
| DB | **동일 PostgreSQL** | 멀티 DB/이벤트 소싱 아님 |
| 모델 | Write Model ≠ Read Model | write는 Aggregate, read는 flat DTO |

```
ticketing-core/src/main/java/com/example/ticket/core/
├── domain/
│   ├── hold/
│   │   ├── Hold.java                   # Entity (Aggregate Root)
│   │   │   # 불변식: isExpired(), belongsTo(clientId)
│   │   │   # 상태 전이: confirm() → Reservation 생성 판단
│   │   ├── HoldCreateCommand.java      # 홀드 생성 VO
│   │   └── HoldPolicy.java            # TTL 정책, 만료 판단
│   ├── reservation/
│   │   └── Reservation.java           # Entity
│   ├── seat/
│   │   ├── Seat.java                  # Entity (읽기 모델에 가까움)
│   │   ├── SeatStatus.java            # enum: AVAILABLE, HELD, CONFIRMED
│   │   └── Zone.java                  # Value Object
│   └── service/
│       └── SeatAvailabilityService.java  # 매진 판단 도메인 서비스
│
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── EnterCoreInPort.java          # enter_token → coreSession 핸드셰이크
│   │   │   ├── HoldInPort.java               # 좌석 홀드 생성 + 확정 (통합)
│   │   │   ├── SeatQueryInPort.java          # 좌석 목록 조회 (zone별 그룹핑)
│   │   │   ├── ActiveScheduleInPort.java     # 스케줄 활성화 관리
│   │   │   └── ActiveScheduleQueryInPort.java # 활성 스케줄 조회
│   │   └── out/
│   │       ├── HoldRepositoryPort.java        # Hold CRUD (write)
│   │       ├── ReservationRepositoryPort.java # Reservation INSERT (write)
│   │       ├── SeatQueryPort.java             # 좌석 조회 (read, 조인 포함)
│   │       ├── SessionPort.java               # Redis core session + active SET
│   │       ├── SoldOutPort.java               # Redis soldout 플래그 R/W
│   │       ├── TokenSignerPort.java           # 세션 토큰 서명/검증
│   │       ├── ActiveSchedulePort.java        # Redis active_schedules 관리
│   │       ├── ScheduleReadPort.java          # DB 스케줄 조회
│   │       ├── ClockPort.java
│   │       └── IdGeneratorPort.java
│   ├── service/
│   │   ├── EnterCoreService.java              # EnterCoreInPort 구현
│   │   ├── HoldService.java                   # HoldInPort 구현 (생성+확정)
│   │   ├── SeatQueryService.java              # SeatQueryInPort 구현
│   │   ├── ActivateSchedulesService.java      # ActiveScheduleInPort 구현
│   │   └── ActiveScheduleQueryService.java    # ActiveScheduleQueryInPort 구현
│   └── dto/
│       ├── command/
│       │   ├── EnterResult.java
│       │   ├── HoldResult.java
│       │   └── ConfirmResult.java
│       └── query/
│           ├── SeatView.java                 # 개별 좌석 (read model)
│           └── ZoneSeatsView.java            # zone별 좌석 목록
│
├── adapter/
│   ├── in/
│   │   ├── web/
│   │   │   ├── CoreEnterController.java      # POST /core/enter
│   │   │   ├── CoreHoldController.java       # POST /core/holds, POST /core/holds/{holdGroupId}/confirm
│   │   │   ├── CoreSeatController.java       # GET /core/seats
│   │   │   ├── AdminScheduleController.java  # GET /core/admin/schedules/activate, DELETE /core/admin/schedules
│   │   │   └── ActiveScheduleController.java # GET /core/schedules/active
│   │   └── scheduler/
│   │       ├── HoldCleanupScheduler.java     # @Scheduled: hold 만료 정리
│   │       └── ActiveCleanupScheduler.java   # @Scheduled: active SET 정리
│   └── out/
│       ├── persistence/
│       │   ├── R2dbcHoldAdapter.java      # HoldRepositoryPort 구현
│       │   ├── R2dbcReservationAdapter.java
│       │   └── R2dbcSeatQuery.java           # SeatQueryPort 구현 (조인 쿼리)
│       ├── redis/
│       │   ├── RedisSessionAdapter.java      # SessionPort 구현 (Lua 포함)
│       │   └── RedisSoldOutAdapter.java      # SoldOutPort 구현
│       └── system/
│           ├── SystemClock.java
│           └── UuidGenerator.java
│
└── config/
    └── CoreConfig.java
```

#### Aggregate: Hold

```java
// domain/hold/Hold.java — 프레임워크 의존 ZERO

public class Hold {
    private final UUID id;
    private final UUID scheduleId;
    private final UUID seatId;
    private final String clientId;
    private final Instant expiresAt;
    private final Instant createdAt;

    // 불변식 1: 만료 검증
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    // 불변식 2: 소유자 검증
    public boolean belongsTo(String clientId) {
        return this.clientId.equals(clientId);
    }

    // 상태 전이: confirm 가능 여부 판단
    public void validateConfirmable(String clientId, Instant now) {
        if (!belongsTo(clientId)) {
            throw new HoldNotOwnedException(id, clientId);
        }
        if (isExpired(now)) {
            throw new HoldExpiredException(id);
        }
    }
}
```

#### Inbound Port 흐름

**HoldInPort.createHold (HoldService)**
```
Input:  clientId, eventId, scheduleId, seatIds (복수)
Output: CreateHoldResult(holdGroupId, scheduleId, seats, expiresAtMs, holdTtlSec)

1. 컨트롤러에서 세션 검증/연장 완료 후 호출
2. HoldRepositoryPort로 기존 hold 확인 (1인 1그룹)
   → 있으면 409 ALREADY_HOLDING
3. SeatQueryPort로 좌석 정보 조회
4. Hold 도메인 객체들 생성 (holdGroupId 공유)
5. HoldRepositoryPort.save(holds)
   → unique 위반 시 409 SEAT_ALREADY_HELD
6. 매진 여부 확인 → SoldOutPort.markSoldOut()
7. CreateHoldResult 반환
```

**HoldInPort.confirmHold (HoldService)**
```
Input:  clientId, holdGroupId, eventId, scheduleId, sessionId
Output: ConfirmHoldResult(scheduleId, seats, confirmedAtMs)

1. HoldRepositoryPort.findById(holdGroupId)
2. hold.validateConfirmable(clientId, now)  ← 도메인 불변식
3. Reservation 생성
4. DB 트랜잭션:
   a. ReservationRepositoryPort.save(reservations)
   b. HoldRepositoryPort.delete(holdGroupId)
5. SessionPort: SREM active, DEL session (세션 종료)
6. 매진 여부 확인 → SoldOutPort.markSoldOut()
7. ConfirmHoldResult 반환
```

**SeatQueryInPort (SeatQueryService)**
```
1. SessionPort로 coreSessionToken 검증
2. SessionPort로 TTL 연장
3. SeatQueryPort.findAllBySchedule(eventId, scheduleId)
   → LEFT JOIN holds, reservations로 상태 포함
   → zone별 그룹핑
4. ZoneSeatsView 반환
```

### 9.5 common 모듈

#### 포함할 것

```
common/src/main/java/com/example/ticket/common/
├── key/
│   └── RedisKeyBuilder.java           # Redis 키 네이밍 헬퍼
│       # q({eventId}, {scheduleId}) → "q:{eventId}:{scheduleId}:z"
├── token/
│   └── HmacSigner.java               # HMAC 서명/검증 유틸
├── error/
│   ├── ErrorCode.java                 # enum: 전체 에러 코드
│   └── ErrorResponse.java            # 공통 에러 응답 DTO
├── port/
│   ├── ClockPort.java                 # 시간 추상화 인터페이스 (선택)
│   └── IdGeneratorPort.java           # ID 생성 추상화 인터페이스 (선택)
└── util/
    └── TokenFormat.java               # 토큰 포맷 상수 ("qt_", "cs_" prefix 등)
```

#### 포함하지 않을 것

- 비즈니스 로직 (특히 core 도메인)
- DB 엔티티
- Gate/Worker 전용 로직
- Spring 의존 코드 (가능하면)

### 9.6 Dependency Direction

```
            ┌──────────────────┐
            │      common      │
            │  (키, 토큰, 에러) │
            └────────┬─────────┘
                     │ (의존)
        ┌────────────┼────────────┐
        │            │            │
        ▼            ▼            ▼
┌──────────┐  ┌───────────┐  ┌──────────┐
│queue-gate│  │admission- │  │ticketing-│
│          │  │worker     │  │core      │
└──────────┘  └───────────┘  └──────────┘

모듈 간 직접 의존 없음.
common만 공유하고, 런타임 연결은 Redis 토큰으로.
```

#### 의존 흐름 (각 모듈 내부)

```
adapter/in  →  application (InPort/Service)  →  domain
                    │
                    ▼
              application (Port)
                    │
                    ▼
              adapter/out

config: 전체 Bean 조립
```

- domain은 아무것도 의존하지 않음 (순수 Java)
- application은 domain + port 인터페이스만 의존
- adapter는 application의 port를 구현
- config는 모든 레이어를 알고 Bean으로 연결

---

## 10. Infrastructure & Deployment

### 10.1 Docker Compose Service Map

```yaml
services:
  # --- Load Balancer ---
  haproxy:            # Single HAProxy (gate/core frontends)

  # --- Application ---
  queue-gate-1:       # Gate instance 1
  queue-gate-2:       # Gate instance 2
  admission-worker:   # Worker instance
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

### 10.2 Port Allocation

| Service | Internal Port | External Port | Description |
|---------|--------------|---------------|-------------|
| haproxy | 8010 | 8010 | Gate LB (frontend) |
| haproxy | 8030 | 8030 | Core LB (frontend) |
| haproxy | 9000 | 9000 | HAProxy stats |
| queue-gate-1 | 8010 | - | Gate instance |
| queue-gate-2 | 8010 | - | Gate instance |
| admission-worker | 8020 | - | Worker (내부 관리용) |
| ticketing-core-1 | 8030 | - | Core instance |
| ticketing-core-2 | 8030 | - | Core instance |
| redis-node-1~6 | 6379 | 7001~7006 | Redis Cluster |
| postgres | 5432 | 5432 | PostgreSQL |
| frontend | 80 (nginx) / 3000 (dev) | 3000 | Vite dev / nginx |

### 10.3 Network Topology

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
│                 ┌──────────────┐                    │
│                 │  haproxy     │                    │
│                 │ :8010/:8030  │                    │
│                 └──┬───────┬───┘                    │
│                    │       │                        │
│                    ▼       ▼                        │
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

### 10.4 HAProxy Configuration

#### Single HAProxy (haproxy.cfg)

```
global
    maxconn 50000

defaults
    mode http
    timeout connect 5s
    timeout client  60s
    timeout server  60s
    timeout tunnel  300s    # SSE tunnel

frontend ft_gate
    bind *:8010
    timeout client 300s     # SSE 장기 연결 허용
    default_backend bk_gate

backend bk_gate
    balance leastconn       # SSE 장기 연결에 유리
    timeout server 300s
    option httpchk GET /actuator/health
    http-check expect status 200
    server gate1 queue-gate-1:8010 check inter 5s fall 3 rise 2
    server gate2 queue-gate-2:8010 check inter 5s fall 3 rise 2

frontend ft_core
    bind *:8030
    default_backend bk_core

backend bk_core
    balance leastconn
    option httpchk GET /actuator/health
    http-check expect status 200
    server core1 ticketing-core-1:8030 check inter 5s fall 3 rise 2
    server core2 ticketing-core-2:8030 check inter 5s fall 3 rise 2
```

**Key Decisions**

| Decision | Choice | Reason |
|----------|--------|--------|
| Algorithm | leastconn | SSE 장기 연결이 특정 서버에 몰리지 않도록 |
| Health check | /actuator/health | Spring Boot Actuator 기본 엔드포인트 |
| Gate timeout | 300s | SSE 연결 유지 (5분) |
| Core timeout | 60s | 일반 API 요청 |

### 10.5 Redis Cluster Setup

#### Node Configuration

| Node | Role | Port |
|------|------|------|
| redis-node-1 | Master (slot 0~5460) | 7001 |
| redis-node-2 | Master (slot 5461~10922) | 7002 |
| redis-node-3 | Master (slot 10923~16383) | 7003 |
| redis-node-4 | Replica of node-1 | 7004 |
| redis-node-5 | Replica of node-2 | 7005 |
| redis-node-6 | Replica of node-3 | 7006 |

#### Hash Tag Strategy

모든 키에 `{eventId}:{scheduleId}` 해시태그를 사용하여 동일 이벤트/스케줄의 키가 같은 슬롯에 배치되도록 한다.

```
q:{ev_2026_0101}:{sc_2026_0101_2000}:z        → slot = CRC16("{ev_2026_0101}:{sc_2026_0101_2000}") % 16384
qstate:{ev_2026_0101}:{sc_2026_0101_2000}:qt1  → same slot
enter:{ev_2026_0101}:{sc_2026_0101_2000}:jti1   → same slot
```

Lua 스크립트 내에서 접근하는 모든 키가 동일 슬롯에 있어야 CROSSSLOT 에러가 발생하지 않는다.

#### Cluster Init Command

```bash
redis-cli --cluster create \
  redis-node-1:6379 redis-node-2:6379 redis-node-3:6379 \
  redis-node-4:6379 redis-node-5:6379 redis-node-6:6379 \
  --cluster-replicas 1 --cluster-yes
```

### 10.6 PostgreSQL Configuration

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

### 10.7 Application Configuration (application.yml)

#### Queue Gate

```yaml
server:
  port: 8010

spring:
  data:
    redis:
      cluster:
        nodes: redis-node-1:6379,redis-node-2:6379,redis-node-3:6379

gate:
  queue:
    state-ttl-sec: 60
    refresh-threshold-sec: 10
  sse:
    push-interval-ms: 1000
  client:
    cookie-name: cid
    cookie-max-age-days: 1
```

#### Admission Worker

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

#### Ticketing Core

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

### 10.8 Environment Variables

| Variable | Used By | Description |
|----------|---------|-------------|
| `ENTER_TOKEN_SECRET` | admission-worker, ticketing-core | enterToken HMAC 비밀키 |
| `CORE_SESSION_SECRET` | ticketing-core | coreSessionToken HMAC 비밀키 |
| `POSTGRES_DB` | postgres | DB 이름 |
| `POSTGRES_USER` | postgres | DB 사용자 |
| `POSTGRES_PASSWORD` | postgres, ticketing-core | DB 비밀번호 |

### 10.9 Health Check Endpoints

모든 애플리케이션 모듈은 Spring Boot Actuator를 사용:

- **Liveness**: `GET /actuator/health/liveness`
- **Readiness**: `GET /actuator/health/readiness`
- **General**: `GET /actuator/health`

HAProxy는 `GET /actuator/health`로 health check 수행.
