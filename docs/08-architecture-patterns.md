# 8. Architecture Patterns

## 8.1 Overall Principle

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

---

## 8.2 queue-gate

### 성격
- 상태는 Redis에 존재, DB 없음
- 핵심: 트래픽 제어, 토큰 발급, 순번 추정, SSE 스트리밍
- 복잡한 비즈니스 규칙 없음

### 패턴: Hexagonal (DDD/CQRS 미적용)

### 패키지 상세

```
queue-gate/src/main/java/com/example/ticket/gate/
├── domain/
│   ├── RankEstimator.java            # delta → estimatedRank 변환 (비선형 버킷)
│   ├── RankBucket.java               # 버킷 정의 VO
│   └── QueueStatus.java              # enum: WAITING, ADMISSION_GRANTED, EXPIRED, SOLD_OUT
│
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   ├── SyncInPort.java           # /gate/sync 처리
│   │   │   ├── JoinQueueInPort.java      # /gate/join 처리
│   │   │   └── StreamQueueInPort.java    # /gate/stream SSE 처리
│   │   └── out/
│   │       ├── QueueRepositoryPort.java  # Redis ZSET/HASH 추상화
│   │       ├── SoldOutQueryPort.java     # soldout 플래그 조회
│   │       ├── TokenSignerPort.java      # HMAC 서명/검증
│   │       ├── ClockPort.java            # 현재 시간 추상화
│   │       └── IdGeneratorPort.java      # UUID 생성 추상화
│   ├── service/
│   │   ├── SyncService.java              # SyncInPort 구현
│   │   ├── JoinQueueService.java         # JoinQueueInPort 구현
│   │   └── StreamQueueService.java       # StreamQueueInPort 구현
│   └── dto/
│       ├── SyncResult.java
│       ├── JoinResult.java
│       └── QueueProgressDto.java
│
├── adapter/
│   ├── in/
│   │   └── web/
│   │       └── GateController.java         # /gate/* endpoints
│   └── out/
│       ├── redis/
│       │   ├── RedisQueueRepository.java   # QueueRepositoryPort 구현 (Lua 호출 포함)
│       │   └── RedisSoldOutQuery.java      # SoldOutQueryPort 구현
│       ├── token/
│       │   └── HmacTokenSigner.java        # TokenSignerPort 구현
│       └── system/
│           ├── SystemClock.java            # ClockPort 구현
│           └── UuidGenerator.java          # IdGeneratorPort 구현
│
└── config/
    └── GateConfig.java                     # Bean 조립, 설정값 바인딩
```

### Inbound Port 흐름

**SyncInPort (SyncService)**
```
Input:  eventId, scheduleId
Output: SyncResult(serverTimeMs, startAtMs, syncToken)

1. ClockPort.nowMs()로 서버 시간 획득
2. 스케줄 정보에서 startAtMs 조회 (Redis active_schedules)
3. TokenSignerPort로 syncToken 생성
4. SyncResult 반환
```

**JoinQueueInPort (JoinQueueService)**
```
Input:  eventId, scheduleId, syncToken, clientId
Output: JoinResult(queueToken, estimatedRank, sseUrl)

1. TokenSignerPort로 syncToken 검증
2. delta 계산 + 유효 윈도우 검증
3. SoldOutQueryPort로 매진 확인
4. RankEstimator로 rank 추정
5. QueueRepositoryPort.join() → Lua 원자 실행
6. JoinResult 반환
```

**StreamQueueInPort (StreamQueueService)**
```
Input:  queueToken
Output: Flux<QueueProgressDto>

1. 1초 interval Flux 생성
2. 매 tick마다 QueueRepositoryPort.getState(queueToken)
3. SoldOutQueryPort 확인
4. QueueProgressDto emit
5. ADMISSION_GRANTED 시 enterToken 포함
6. SOLD_OUT/EXPIRED 시 스트림 종료
```

---

## 8.3 admission-worker

### 성격
- 주기적 배치 엔진
- 핵심: rate/concurrency cap 준수 + Lua 원자적 발급
- 도메인 모델 불필요, 정책 객체가 핵심

### 패턴: Hexagonal + Engine Pattern

### 패키지 상세

```
admission-worker/src/main/java/com/example/ticket/admission/
├── domain/
│   └── AdmissionPolicy.java          # cap 계산 로직 (순수 함수)
│       # calculateIssueCount(rateCap, concurrencyCap, currentRate, currentActive, maxBatch) → int
│
├── application/
│   ├── engine/
│   │   └── AdmissionEngine.java      # tick loop 실행 (스케줄별 순회)
│   ├── port/
│   │   └── out/
│   │       ├── IssuerPort.java              # Lua 스크립트 실행 추상화
│   │       ├── ActiveSchedulePort.java      # 활성 스케줄 목록 조회
│   │       ├── TokenGeneratorPort.java      # enterToken 생성 (jti + HMAC)
│   │       └── ClockPort.java
│   └── dto/
│       └── IssueResult.java           # { issuedCount, skipped, remaining }
│
├── adapter/
│   ├── in/
│   │   └── scheduler/
│   │       └── AdmissionPoller.java   # @Scheduled: 200ms마다 engine.tick() 호출
│   └── out/
│       ├── redis/
│       │   ├── RedisIssuer.java       # IssuerPort 구현 (Lua EVAL)
│       │   └── RedisActiveSchedule.java
│       └── token/
│           └── HmacTokenGenerator.java
│
└── config/
    └── WorkerConfig.java
```

### Engine 흐름

**AdmissionEngine.tick()**
```
1. ActiveSchedulePort로 활성 스케줄 목록 획득 (상위 K개)
2. 각 스케줄에 대해:
   a. TokenGeneratorPort로 maxBatch개만큼 jti+token 사전 생성
   b. IssuerPort.issue(keys, args, tokens) → Lua 실행
   c. IssueResult 로깅
3. 발급 가능량이 0이면 해당 스케줄 skip
```

**AdmissionPolicy.calculateIssueCount()**
```
Input:  rateCap=200, concurrencyCap=10000, currentRate=50, currentActive=8000, maxBatch=200
Output: min(200, 200-50, 10000-8000) = min(200, 150, 2000) = 150
```

---

## 8.4 ticketing-core

### 성격
- 좌석 상태 머신 (AVAILABLE → HELD → CONFIRMED)
- Hold 불변식: 만료 검증, 1인 1좌석
- DB 트랜잭션 경계 중요
- Read(좌석 조회)와 Write(홀드/확정) 성격이 다름

### 패턴: Hexagonal + DDD + Lightweight CQRS

### DDD 적용 범위

| DDD 요소 | 적용 | 설명 |
|----------|------|------|
| Entity | O | Hold, Reservation |
| Value Object | O | SeatStatus, Zone |
| Aggregate | O | HoldAggregate (핵심) |
| Domain Service | O | SeatAvailabilityService |
| Repository (Port) | O | HoldRepositoryPort, SeatQueryPort 등 |
| Domain Event | X | 범위 초과 (이벤트 소싱 안 함) |
| Bounded Context | X | 단일 모듈 내 단일 컨텍스트 |

### CQRS 적용 범위

| 구분 | 방식 | 설명 |
|------|------|------|
| Write | HoldCommandService, ConfirmCommandService | DB 트랜잭션, 불변식 검증 |
| Read | SeatQueryService | 조인/프로젝션, 읽기 최적화 |
| DB | **동일 PostgreSQL** | 멀티 DB/이벤트 소싱 아님 |
| 모델 | Write Model ≠ Read Model | write는 Aggregate, read는 flat DTO |

### 패키지 상세

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
│   │   │   ├── EnterCoreInPort.java       # enter_token → coreSession 핸드셰이크
│   │   │   ├── CreateHoldInPort.java      # 좌석 홀드 생성
│   │   │   ├── ConfirmHoldInPort.java     # 홀드 확정 → 예약 생성
│   │   │   └── SeatQueryInPort.java       # 좌석 목록 조회 (zone별 그룹핑)
│   │   └── out/
│   │       ├── HoldRepositoryPort.java        # Hold CRUD (write)
│   │       ├── ReservationRepositoryPort.java # Reservation INSERT (write)
│   │       ├── SeatQueryPort.java             # 좌석 조회 (read, 조인 포함)
│   │       ├── SessionPort.java               # Redis core session + active SET
│   │       ├── SoldOutPort.java               # Redis soldout 플래그 R/W
│   │       ├── ClockPort.java
│   │       └── IdGeneratorPort.java
│   ├── service/
│   │   ├── EnterCoreService.java        # EnterCoreInPort 구현
│   │   ├── CreateHoldService.java       # CreateHoldInPort 구현
│   │   ├── ConfirmHoldService.java      # ConfirmHoldInPort 구현
│   │   └── SeatQueryService.java        # SeatQueryInPort 구현
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
│   │   │   ├── CoreHoldController.java       # POST /core/holds, POST /core/holds/{id}/confirm
│   │   │   └── CoreSeatController.java       # GET /core/seats
│   │   └── scheduler/
│   │       ├── HoldCleanupScheduler.java     # @Scheduled: hold 만료 정리
│   │       └── ActiveCleanupScheduler.java   # @Scheduled: active SET 정리
│   └── out/
│       ├── persistence/
│       │   ├── R2dbcHoldRepository.java      # HoldRepositoryPort 구현
│       │   ├── R2dbcReservationRepository.java
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

### Aggregate: Hold

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

### Inbound Port 흐름

**CreateHoldInPort (CreateHoldService)**
```
1. SessionPort로 coreSessionToken 검증 → clientId 획득
2. SessionPort로 TTL 연장 (슬라이딩)
3. HoldRepositoryPort로 기존 hold 확인 (1인 1좌석)
   → 있으면 409 ALREADY_HOLDING
4. Hold 도메인 객체 생성
5. HoldRepositoryPort.save(hold)
   → unique 위반 시 409 SEAT_ALREADY_HELD
6. SeatAvailabilityService로 매진 여부 확인
   → 매진이면 SoldOutPort.markSoldOut()
7. HoldResult 반환
```

**ConfirmHoldInPort (ConfirmHoldService)**
```
1. SessionPort로 coreSessionToken 검증 → clientId 획득
2. HoldRepositoryPort.findById(holdId)
3. hold.validateConfirmable(clientId, now)  ← 도메인 불변식
4. Reservation 생성
5. DB 트랜잭션:
   a. ReservationRepositoryPort.save(reservation)
   b. HoldRepositoryPort.delete(holdId)
6. SessionPort: SREM active, DEL session (세션 종료)
7. SeatAvailabilityService로 매진 여부 확인
8. ConfirmResult 반환
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

---

## 8.5 common 모듈

### 포함할 것

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

### 포함하지 않을 것

- 비즈니스 로직 (특히 core 도메인)
- DB 엔티티
- Gate/Worker 전용 로직
- Spring 의존 코드 (가능하면)

---

## 8.6 Dependency Direction

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

### 의존 흐름 (각 모듈 내부)

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
