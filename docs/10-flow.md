# 10. End-to-End Flow

This document describes the end-to-end request flow and the exact module/class path
for each major API in the admission system.

---

## 10.1 Gate Sync (`GET /gate/sync`)

**Goal**: provide server time + start time + syncToken for precise countdown.

**Call chain**
1. `queue-gate` → `GateController` (`/gate/sync`)
2. `SyncInPort.execute(eventId, scheduleId)` → `SyncService`
3. `ScheduleQueryPort.getStartAtMs` → `RedisScheduleQuery` (Redis `active_schedules` ZSET)
4. `ClockPort.nowMillis` → `SystemClock`
5. `TokenSignerPort.signSyncToken` → `HmacTokenSigner`

**Outputs**: `SyncResult(serverTimeMs, startAtMs, syncToken)`

---

## 10.2 Join Queue (`POST /gate/join`)

**Goal**: validate syncToken + join window, estimate rank, atomically enqueue.

**Call chain**
1. `queue-gate` → `GateController` (`/gate/join`)
2. `JoinQueueInPort.execute(eventId, scheduleId, syncToken, clientId)` → `JoinQueueService`
3. `TokenSignerPort.verifySyncToken` → `HmacTokenSigner`
4. window validation (`joinWindowBeforeMs`, `joinWindowAfterMs`)
5. `SoldOutQueryPort.isSoldOut` → `RedisSoldOutQuery`
6. `RankEstimator.estimate` (score tie-breaker computed in Lua with seq)
7. `QueueRepositoryPort.join` → `RedisQueueRepository`
8. Lua: `queue-join.lua`

**Outputs**: `JoinResult(queueToken, estimatedRank, sseUrl, alreadyJoined)`

---

## 10.3 Queue Stream (`GET /gate/stream`)

**Goal**: SSE stream of queue progress until admission granted or sold out.

**Call chain**
1. `queue-gate` → `GateController` (`/gate/stream`)
2. `StreamQueueInPort.stream(eventId, scheduleId, queueToken)` → `StreamQueueService`
3. `QueueRepositoryPort.getState` → `RedisQueueRepository` (qstate HASH)
4. `QueueRepositoryPort.getQueueSize` → `RedisQueueRepository` (ZSET size)
5. `SoldOutQueryPort.isSoldOut` → `RedisSoldOutQuery`

**Outputs**: `QueueProgressDto` SSE events (`WAITING` → `ADMISSION_GRANTED`/`SOLD_OUT`)

---

## 10.4 Enter Core (`POST /core/enter`)

**Goal**: consume enterToken and issue coreSessionToken.

**Call chain**
1. `ticketing-core` → `CoreEnterController`
2. `EnterCoreInPort.execute(enterToken, eventId, scheduleId)` → `EnterCoreService`
3. `HmacSigner.verifyAndExtract(enterToken)`
4. `SessionPort.handshake` → `RedisSessionAdapter`
5. Lua: `core-handshake.lua`  
   - `DEL enter:{evt}:{sch}:{jti}`
   - `SET cs:{evt}:{sch}:{sessionId}`
   - `SET csidx:{evt}:{sch}:{clientId}`
   - `SADD active:{evt}:{sch}`
6. `HmacSigner.createToken` (coreSessionToken)

**Outputs**: `EnterResult(coreSessionToken, ttlSec, eventId, scheduleId)`

---

## 10.5 Query Seats (`GET /core/seats`)

**Goal**: validate session, refresh TTL, return seat map.

**Call chain**
1. `ticketing-core` → `CoreSeatController`
2. `SeatQueryInPort.execute(sessionId, eventId, scheduleId)` → `SeatQueryService`
3. `SessionPort.validateSession` → `RedisSessionAdapter`
4. `SessionPort.refreshSession` → `RedisSessionAdapter`
5. `SeatQueryPort.findAllBySchedule` → `R2dbcSeatQuery`

**Outputs**: `ZoneSeatsView[]`

---

## 10.6 Create Hold (`POST /core/holds`)

**Goal**: reserve a seat temporarily.

**Call chain**
1. `ticketing-core` → `CoreHoldController`
2. `SessionPort.validateSession` → `RedisSessionAdapter`
3. `SessionPort.refreshSession` → `RedisSessionAdapter`
4. `CreateHoldInPort.execute(clientId, scheduleId, seatId)` → `CreateHoldService`
5. `HoldRepositoryPort.findByScheduleAndClient` → `R2dbcHoldRepository`
6. `HoldPolicy.calculateExpiresAt`
7. `HoldRepositoryPort.save` → `R2dbcHoldRepository`
8. `SeatAvailabilityService.isSoldOut`
9. `SoldOutPort.markSoldOut` → `RedisSoldOutAdapter`

**Outputs**: `HoldResult(holdId, seatId, zone, seatNo, expiresAtMs, ttlSec)`

---

## 10.7 Confirm Hold (`POST /core/holds/{id}/confirm`)

**Goal**: finalize reservation and close session.

**Call chain**
1. `ticketing-core` → `CoreHoldController`
2. `ConfirmHoldInPort.execute(clientId, holdId, scheduleId, sessionId)` → `ConfirmHoldService`
3. `HoldRepositoryPort.findById` → `R2dbcHoldRepository`
4. `Hold.validateConfirmable`
5. `ReservationRepositoryPort.findByScheduleAndSeat` → `R2dbcReservationRepository`
6. `ReservationRepositoryPort.save` → `R2dbcReservationRepository`
7. `HoldRepositoryPort.deleteById` → `R2dbcHoldRepository`
8. `SessionPort.closeSession` → `RedisSessionAdapter`
9. `SeatAvailabilityService.isSoldOut`
10. `SoldOutPort.markSoldOut` → `RedisSoldOutAdapter`

**Outputs**: `ConfirmResult(reservationId, seatId, zone, seatNo, confirmedAtMs)`

---

## 10.8 Admission Worker (Background)

**Goal**: periodically issue enter tokens to top of queue.

**Call chain**
1. `admission-worker` → `AdmissionPoller` (`@Scheduled`)
2. `AdmissionEngine.tick()`
3. `ActiveSchedulePort.getActiveSchedules` → `RedisActiveSchedule`
4. `TokenGeneratorPort.generateBatch` → `HmacTokenGenerator`
5. `IssuerPort.issue` → `RedisIssuer`
6. Lua: `admission-issue.lua`  
   - `ZPOPMIN` top N  
   - `HSET qstate` (`ADMISSION_GRANTED`)  
   - `SET enter:{evt}:{sch}:{jti}`  
   - `SADD active:{evt}:{sch}`

---

## 10.9 Schedulers (Background)

- **Hold Cleanup**  
  `HoldCleanupScheduler` → `HoldRepositoryPort.deleteExpired`

- **Active Session Cleanup**  
  `ActiveCleanupScheduler` → `SessionPort.cleanupExpiredSessions`
