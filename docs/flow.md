# End-to-End Flow

This document describes the end-to-end request flow and the exact module/class path
for each major API in the admission system.

---

## 1. Join Queue (`POST /gate/join`)

**Goal**: validate join eligibility, atomically enqueue.

**Call chain**
1. `queue-gate` → `GateController` (`/gate/join`)
2. `GateController`: clientId 쿠키 확인 (없으면 UUID 생성 + Set-Cookie)
3. `JoinQueueInPort.execute(eventId, scheduleId, clientId, loadTest)` → `JoinQueueService`
4. `ScheduleQueryPort.getStartAtMs` → `RedisScheduleQuery` (Redis `active_schedules` ZSET)
5. `SoldOutQueryPort.isSoldOut` → `RedisSoldOutQuery`
6. `QueueRepositoryPort.join` → `RedisQueueAdapter`
7. Lua: `queue-join.lua`

**Outputs**: `JoinResult(queueToken, sseUrl, alreadyJoined)`

---

## 2. Queue Stream (`GET /gate/stream`)

**Goal**: SSE stream of queue progress until admission granted or sold out.

**Call chain**
1. `queue-gate` → `GateController` (`/gate/stream`)
2. `StreamQueueInPort.stream(eventId, scheduleId, queueToken)` → `StreamQueueService`
3. `QueueRepositoryPort.getState` → `RedisQueueAdapter` (qstate HASH)
4. `QueueRepositoryPort.getQueueSize` → `RedisQueueAdapter` (ZSET size)
5. `SoldOutQueryPort.isSoldOut` → `RedisSoldOutQuery`

**Outputs**: `ProgressResult` SSE events (`WAITING` → `ADMISSION_GRANTED`/`SOLD_OUT`)

---

## 3. Queue Status (`GET /gate/status`)

**Goal**: polling fallback for queue state.

**Call chain**
1. `queue-gate` → `GateController` (`/gate/status`)
2. `StreamQueueInPort.poll(eventId, scheduleId, queueToken)` → `StreamQueueService`
3. Same ports as Queue Stream (single poll instead of continuous stream)

**Outputs**: `ProgressResult(status, rank, totalInQueue, enterToken, eventId, scheduleId)`

---

## 4. Enter Core (`POST /core/enter`)

**Goal**: consume enterToken and issue coreSessionToken via cookie.

**Call chain**
1. `ticketing-core` → `CoreEnterController`
2. 기존 세션 쿠키 확인 → 유효하면 재사용 (멱등)
3. `EnterCoreInPort.execute(enterToken, eventId, scheduleId)` → `EnterCoreService`
4. `TokenSignerPort.verifyAndExtract(enterToken)`
5. `SessionPort.handshake` → `RedisSessionAdapter`
6. Lua: `core-handshake.lua`
   - `DEL enter:{evt}:{sch}:{jti}`
   - `SET cs:{evt}:{sch}:{sessionId}`
   - `SET csidx:{evt}:{sch}:{clientId}`
   - `SADD active:{evt}:{sch}`
7. `TokenSignerPort.createToken` (coreSessionToken)
8. Set-Cookie: coreSessionToken

**Outputs**: `EnterResult(sessionTtlSec, eventId, scheduleId)` + 쿠키

---

## 5. Query Seats (`GET /core/seats`)

**Goal**: validate session, refresh TTL, return seat map.

**Call chain**
1. `ticketing-core` → `CoreSeatController`
2. 쿠키에서 coreSessionToken 추출 + `TokenSignerPort.verifySessionToken`
3. `SeatQueryInPort.execute(sessionId, eventId, scheduleId)` → `SeatQueryService`
4. `SessionPort.validateSession` → `RedisSessionAdapter`
5. `SessionPort.refreshSession` → `RedisSessionAdapter`
6. `SeatQueryPort.findAllBySchedule` → `R2dbcSeatQuery`

**Outputs**: `List<ZoneSeatsView>`

---

## 6. Create Hold (`POST /core/holds`)

**Goal**: reserve multiple seats temporarily as a holdGroup.

**Call chain**
1. `ticketing-core` → `CoreHoldController`
2. 쿠키에서 coreSessionToken 추출 + `TokenSignerPort.verifySessionToken`
3. `SessionPort.validateSession` → `RedisSessionAdapter`
4. `SessionPort.refreshSession` → `RedisSessionAdapter`
5. `HoldInPort.createHold(clientId, eventId, scheduleId, seatIds)` → `HoldService`
6. `HoldRepositoryPort` — 기존 hold 확인 (1인 1그룹)
7. `SeatQueryPort` — 좌석 정보 조회
8. `HoldRepositoryPort.save` → `R2dbcHoldAdapter`
9. `SoldOutPort.markSoldOut` → `RedisSoldOutAdapter` (매진 시)

**Outputs**: `CreateHoldResult(holdGroupId, scheduleId, seats, expiresAtMs, holdTtlSec)`

---

## 7. Confirm Hold (`POST /core/holds/{holdGroupId}/confirm`)

**Goal**: finalize reservation and close session.

**Call chain**
1. `ticketing-core` → `CoreHoldController`
2. 쿠키에서 coreSessionToken 추출 + `TokenSignerPort.verifySessionToken`
3. `SessionPort.validateSession` → `RedisSessionAdapter`
4. `HoldInPort.confirmHold(clientId, holdGroupId, eventId, scheduleId, sessionId)` → `HoldService`
5. `HoldRepositoryPort.findById` → `R2dbcHoldAdapter`
6. Hold 도메인 불변식 검증 (`validateConfirmable`)
7. `ReservationRepositoryPort.save` → `R2dbcReservationAdapter`
8. `HoldRepositoryPort.deleteById` → `R2dbcHoldAdapter`
9. `SessionPort.closeSession` → `RedisSessionAdapter`
10. `SoldOutPort.markSoldOut` → `RedisSoldOutAdapter` (매진 시)

**Outputs**: `ConfirmHoldResult(scheduleId, seats[{reservationId, seatId, zone, seatNo}], confirmedAtMs)`

---

## 8. Admission Worker (Background)

**Goal**: periodically issue enter tokens to top of queue.

**Call chain**
1. `admission-worker` → `AdmissionPoller` (`@Scheduled`)
2. `AdmissionJob.tick()`
3. `ActiveSchedulePort.getActiveSchedules` → `RedisActiveSchedule`
4. 각 스케줄에 대해 `processSchedule()`:
   - `TokenGeneratorPort.generateBatch` → `HmacTokenGenerator`
   - `IssuerPort.issue` → `RedisIssuer`
5. Lua: `admission-issue.lua`
   - `ZPOPMIN` top N
   - `HSET qstate` (`ADMISSION_GRANTED`)
   - `SET enter:{evt}:{sch}:{jti}`
   - `SADD active:{evt}:{sch}`

---

## 9. Schedulers (Background)

- **Hold Cleanup**
  `HoldCleanupScheduler` → `HoldRepositoryPort.deleteExpired`

- **Active Session Cleanup**
  `ActiveCleanupScheduler` → `SessionPort.cleanupExpiredSessions`
