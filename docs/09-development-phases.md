# 9. Development Phases

## Overview

의존 방향(하위 → 상위)에 따라 빌드 가능한 순서로 진행한다.
각 Phase는 이전 Phase가 완료된 상태에서 동작 가능해야 한다.

```
Phase 1: 프로젝트 기반 구성
Phase 2: common 모듈
Phase 3: ticketing-core (DB + 도메인)
Phase 4: queue-gate (Redis + SSE)
Phase 5: admission-worker (Lua + 엔진)
Phase 6: 통합 연결 + 인프라
Phase 7: frontend
Phase 8: 통합 테스트 + 마무리
```

---

## Phase 1: 프로젝트 기반 구성

Gradle 멀티모듈 셋업 + Docker Compose + DB/Redis 인프라 기동.
이후 모든 모듈이 이 기반 위에 올라간다.

### Tasks

- [ ] 1-1. Gradle 멀티모듈 전환
  - `settings.gradle`에 `common`, `queue-gate`, `admission-worker`, `ticketing-core` 서브프로젝트 등록
  - 루트 `build.gradle`에 공통 의존성/플러그인 설정
  - 각 서브모듈 `build.gradle` 생성 (의존성 분리)
  - 기존 단일 모듈 `src/` 정리

- [ ] 1-2. Docker Compose 작성
  - `infra/docker-compose.yml`
  - PostgreSQL (포트 5432, init.sql 마운트)
  - Redis Cluster 6노드 (7001~7006) + cluster-init 컨테이너
  - 네트워크: `app-net`, `data-net`

- [ ] 1-3. DB 초기화 스크립트
  - `infra/sql/init.sql`: DDL (events, schedules, seats, holds, reservations) + seed data

- [ ] 1-4. 기본 application.yml
  - 각 모듈별 application.yml 스켈레톤 (포트, Redis, DB 접속 정보)

### 완료 기준
- `docker compose up` 으로 PostgreSQL + Redis Cluster 기동
- 각 서브모듈 `./gradlew :모듈명:build` 성공

---

## Phase 2: common 모듈

모든 모듈이 공유하는 상수, 유틸, 에러 코드.

### Tasks

- [ ] 2-1. Redis 키 빌더
  - `RedisKeyBuilder`: 해시태그 포함 키 생성 메서드
  - `q()`, `qstate()`, `qjoin()`, `enter()`, `rate()`, `active()`, `cs()`, `csidx()`, `soldout()`, `activeSchedules()`

- [ ] 2-2. HMAC 토큰 유틸
  - `HmacSigner`: sign(payload, secret) → base64url, verify(token, secret) → boolean
  - 토큰 포맷 상수: `TokenFormat` ("qt_", "cs_" prefix 등)

- [ ] 2-3. 에러 코드/응답
  - `ErrorCode` enum: 전체 에러 코드 정의
  - `ErrorResponse` DTO: code, message, timestamp
  - `BusinessException` 베이스 클래스 + 서브 예외

- [ ] 2-4. 공통 Port 인터페이스 (선택)
  - `ClockPort`, `IdGeneratorPort`

### 완료 기준
- `./gradlew :common:build` 성공
- 단위 테스트: HmacSigner, RedisKeyBuilder

---

## Phase 3: ticketing-core

DB 도메인이 먼저 있어야 Gate/Worker가 "좌석이 존재하는 상태"에서 테스트 가능하다.
DDD + Lightweight CQRS 적용.

### Tasks

- [ ] 3-1. 도메인 레이어
  - `Hold` 엔티티 (불변식: isExpired, belongsTo, validateConfirmable)
  - `Reservation` 엔티티
  - `Seat` 엔티티, `SeatStatus` enum, `Zone` VO
  - `HoldPolicy` (TTL 정책)
  - `SeatAvailabilityService` (매진 판단)

- [ ] 3-2. Port 인터페이스
  - `HoldRepositoryPort`, `ReservationRepositoryPort`, `SeatQueryPort`
  - `SessionPort` (Redis core session + active SET)
  - `SoldOutPort` (Redis soldout 플래그)

- [ ] 3-3. Inbound Port + Service (application 레이어)
  - Command InPort: `EnterCoreInPort`, `CreateHoldInPort`, `ConfirmHoldInPort`
  - Query InPort: `SeatQueryInPort`

- [ ] 3-4. Adapter - DB (R2DBC)
  - `R2dbcHoldRepository`, `R2dbcReservationRepository`, `R2dbcSeatQuery`
  - DatabaseClient 기반 쿼리 구현

- [ ] 3-5. Adapter - Redis
  - `RedisSessionAdapter` (Core Handshake Lua 포함)
  - `RedisSoldOutAdapter`

- [ ] 3-6. Adapter - Web (Controller)
  - `CoreEnterController` (POST /core/enter)
  - `CoreHoldController` (POST /core/holds, POST /core/holds/{id}/confirm)
  - `CoreSeatController` (GET /core/seats)
  - coreSessionToken 인증 필터/핸들러

- [ ] 3-7. Scheduler
  - `HoldCleanupScheduler` (2초 주기, 만료 hold DELETE)
  - `ActiveCleanupScheduler` (10초 주기, active SET 정리)

- [ ] 3-8. Lua 스크립트
  - `core-handshake.lua` 파일 작성 + RedisSessionAdapter에서 EVAL 호출

- [ ] 3-9. 에러 핸들링
  - `GlobalExceptionHandler` (WebFlux ExceptionHandler)
  - DB unique 위반 → 409 매핑

### 완료 기준
- Core 단독 기동 가능 (Docker: PostgreSQL + Redis)
- curl/httpie로 enter → seats → hold → confirm 해피 패스 동작
- hold 만료 → 자동 정리 확인
- unique 위반 시 409 응답 확인

---

## Phase 4: queue-gate

대기열 + SSE. Core 없이도 Redis만으로 독립 동작 가능.

### Tasks

- [ ] 4-1. 도메인 레이어
  - `RankEstimator` (delta → rank 비선형 버킷)
  - `RankBucket` VO
  - `QueueStatus` enum

- [ ] 4-2. Port 인터페이스
  - `QueueRepositoryPort` (ZSET/HASH 추상화)
  - `SoldOutQueryPort`
  - `TokenSignerPort`

- [ ] 4-3. Inbound Port + Service
  - `SyncInPort` / `SyncService`
  - `JoinQueueInPort` / `JoinQueueService`
  - `StreamQueueInPort` / `StreamQueueService`

- [ ] 4-4. Adapter - Redis
  - `RedisQueueRepository` (Queue Join Lua 포함)
  - `RedisSoldOutQuery`

- [ ] 4-5. Adapter - Web
  - `GateController` (GET /gate/sync, POST /gate/join, GET /gate/stream, GET /gate/status)

- [ ] 4-6. Lua 스크립트
  - `queue-join.lua` 파일 작성 + RedisQueueRepository에서 EVAL 호출

- [ ] 4-7. syncToken 서명/검증
  - `HmacTokenSigner` 어댑터

### 완료 기준
- Gate 단독 기동 가능 (Docker: Redis)
- curl로 sync → join → SSE 연결 → WAITING 상태 수신
- 중복 join 멱등성 확인

---

## Phase 5: admission-worker

Gate가 대기열에 넣은 사용자에게 입장권을 발급하는 엔진.
Gate + Worker + Redis가 연동되어야 E2E 확인 가능.

### Tasks

- [ ] 5-1. 도메인 레이어
  - `AdmissionPolicy` (cap 계산 순수 함수)

- [ ] 5-2. Port 인터페이스
  - `IssuerPort` (Lua 실행 추상화)
  - `ActiveSchedulePort` (활성 스케줄 조회)
  - `TokenGeneratorPort` (enterToken 생성)

- [ ] 5-3. Engine
  - `AdmissionEngine` (tick 실행: 스케줄 순회 → 발급)

- [ ] 5-4. Adapter - Redis
  - `RedisIssuer` (Admission Issue Lua 호출)
  - `RedisActiveSchedule`

- [ ] 5-5. Adapter - Scheduler
  - `AdmissionPoller` (@Scheduled, 200ms 주기로 engine.tick())

- [ ] 5-6. Lua 스크립트
  - `admission-issue.lua` 파일 작성

- [ ] 5-7. enterToken 생성
  - `HmacTokenGenerator` 어댑터

### 완료 기준
- Gate + Worker 동시 기동
- join → SSE에서 ADMISSION_GRANTED + enterToken 수신
- 멀티 워커(2개) 실행 시 중복 발급 없음
- rate cap 초과 시 대기열에 남는 것 확인

---

## Phase 6: 통합 연결 + 인프라

모든 백엔드 모듈을 Docker Compose로 묶고 HAProxy를 붙인다.

### Tasks

- [ ] 6-1. 각 모듈 Dockerfile 작성
  - 멀티스테이지 빌드 (Gradle build → JRE runtime)

- [ ] 6-2. Docker Compose 확장
  - haproxy-gate, haproxy-core 서비스 추가
  - queue-gate x2, admission-worker x2, ticketing-core x2
  - 환경변수 (.env 파일)

- [ ] 6-3. HAProxy 설정 파일
  - `infra/haproxy/haproxy-gate.cfg`
  - `infra/haproxy/haproxy-core.cfg`

- [ ] 6-4. 활성 스케줄 Redis seed
  - 데모용 active_schedules ZSET에 스케줄 등록 스크립트

- [ ] 6-5. E2E 통합 흐름 검증
  - sync → join → SSE(WAITING) → Worker 발급 → SSE(ADMISSION_GRANTED)
  - → enter → seats → hold → confirm
  - 전 구간 HAProxy 경유

### 완료 기준
- `docker compose up` 한 번으로 전체 인프라 + 백엔드 기동
- HAProxy 경유 E2E 해피 패스 통과
- 멀티 인스턴스 환경에서 정합성 유지

---

## Phase 7: Frontend

백엔드 E2E가 동작하는 상태에서 UI 구현.

### Tasks

- [ ] 7-1. 프로젝트 초기화
  - Vite + React + TypeScript + Tailwind CSS + Zustand
  - `frontend/` 디렉토리

- [ ] 7-2. 페이지 라우팅
  - `/` : 이벤트/스케줄 선택
  - `/gate` : 정각 클릭 + 대기열
  - `/seats` : 좌석 선택
  - `/confirm` : 결제(모킹) + 60초 타이머
  - `/complete` : 예매 완료

- [ ] 7-3. 정각 클릭 게이트 화면
  - /gate/sync 호출 → 서버 시간 보정
  - 밀리초 카운트다운 표시
  - 00:00.000에 버튼 활성화
  - 클릭 시 /gate/join 호출

- [ ] 7-4. 대기열 화면
  - SSE 연결 (EventSource)
  - 순번/예상 대기시간 실시간 표시
  - ADMISSION_GRANTED → 자동 Core 진입
  - SOLD_OUT → 매진 화면

- [ ] 7-5. 좌석 선택 화면
  - /core/enter 핸드셰이크
  - Zone 탭 (A/B)
  - 좌석 그리드 (색상: 초록=available, 빨강=held, 회색=confirmed)
  - 선택 → /core/holds 호출

- [ ] 7-6. 결제(Confirm) 화면
  - 선택 좌석 정보 표시
  - 60초 카운트다운 타이머
  - "결제하기" 버튼 → /core/holds/{id}/confirm
  - 타이머 만료 → hold expired 안내

- [ ] 7-7. 예매 완료 화면
  - reservationId, 좌석 정보, 확정 시간 표시

- [ ] 7-8. Zustand 상태 관리
  - queueToken, enterToken, coreSessionToken
  - selectedSeat, holdId, expiresAt
  - 전역 에러 상태

- [ ] 7-9. 에러 처리
  - API 에러 응답 → 사용자 친화적 메시지
  - SSE 끊김 → 자동 재연결

### 완료 기준
- 브라우저에서 전 구간 해피 패스 동작
- 대기열 → 좌석 → 결제 → 완료 UX 자연스럽게 연결
- 에러 케이스 (매진, 만료, 중복) UI 표시

---

## Phase 8: 통합 테스트 + 마무리

### Tasks

- [ ] 8-1. 엣지 케이스 검증
  - 06-edge-cases.md의 26건 시나리오 수동/자동 확인
  - 중복 join, hold 만료, 1인 1좌석, confirm 멱등성 등

- [ ] 8-2. 멀티 워커 정합성 테스트
  - Worker 2~4개 동시 실행
  - 중복 발급 0건 확인
  - rate cap / concurrency cap 준수 확인

- [ ] 8-3. 부하 시나리오 (선택)
  - k6 또는 Gatling으로 동시 접속 시뮬레이션
  - Gate join 처리량 측정
  - Core hold/confirm 정합성 확인

- [ ] 8-4. 최종 정리
  - 불필요 코드/설정 정리
  - Docker Compose 최종 검증 (cold start)
  - 데모 시나리오 문서화

### 완료 기준
- 전체 Docker Compose cold start → 프론트엔드 해피 패스 통과
- 엣지 케이스 주요 시나리오 통과
- 멀티 워커 환경 정합성 확인

---

## Phase Dependency Graph

```
Phase 1 (기반)
   │
   ▼
Phase 2 (common)
   │
   ├──────────┬──────────┐
   ▼          ▼          ▼
Phase 3    Phase 4    Phase 5
(core)     (gate)     (worker)
   │          │          │
   │          │ Phase 5는 │
   │          │ Phase 4  │
   │          │ 이후 E2E │
   │          │ 검증 가능 │
   └──────────┴──────────┘
              │
              ▼
         Phase 6 (통합)
              │
              ▼
         Phase 7 (frontend)
              │
              ▼
         Phase 8 (테스트)
```

**참고:** Phase 3, 4는 병렬 진행 가능하나, Phase 5의 E2E 검증은 Phase 4 이후에 가능.
Phase 6은 Phase 3~5 모두 완료 후 진행.
- delta < 0 → 400 TOO_EARLY 확인
