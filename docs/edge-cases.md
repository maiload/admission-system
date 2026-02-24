# 6. Edge Cases & Exception Handling

## 6.1 Happy Path Flow

```
1. 사용자가 홈 페이지에서 활성 스케줄 목록 조회
2. 스케줄 선택 → "예매하기" 클릭
3. GET /core/seats 호출 (세션 프리페치 시도)
   → 200 OK: 기존 세션 유효 → 바로 좌석 페이지 진입 (7번으로)
   → 401: 세션 없음 → Gate 페이지로 이동 (4번으로)
4. POST /gate/join → queueToken, sseUrl 수신 (clientId 쿠키 자동 발급)
5. SSE 연결 → 대기열 실시간 상태 수신
6. 순번 도달 → ADMISSION_GRANTED + enterToken 수신
7. POST /core/enter → coreSessionToken 쿠키 수신
8. GET /core/seats → 좌석 목록 조회
9. 좌석 선택 → POST /core/holds → holdGroupId 수신
10. 60초 내 POST /core/holds/{holdGroupId}/confirm → reservationId 수신
11. 예매 완료 화면 표시
```

---

## 6.2 Edge Cases by Phase

### Phase 0: 예매 페이지 진입

| Case | Trigger | Server Response | Client Action |
|------|---------|----------------|---------------|
| E0-1: 존재하지 않는 eventId/scheduleId | 잘못된 URL 접근 | 404 NOT_FOUND | 에러 페이지 표시 |
| E0-2: 활성 스케줄 없음 (이벤트 미등록) | 스케줄 미존재 | 빈 목록 반환 | "현재 예매 가능한 스케줄이 없습니다" 표시 |

### Phase 1: 정각 클릭 게이트

| Case | Trigger | Server Response | Client Action |
|------|---------|----------------|---------------|
| E1-2: clientId 쿠키 누락 | 쿠키 차단 환경 | /gate/join에서 UUID 자동 발급 | 자동 처리 |
| E1-3: 같은 clientId로 중복 join | 더블 클릭/새로고침 | 200 + 기존 queueToken (멱등, alreadyJoined=true) | 정상 진행 |
| E1-4: join 시 이미 SOLD_OUT | 매진 상태 | 409 SOLD_OUT | "매진되었습니다" 표시 |

### Phase 2: 대기열

| Case | Trigger | Server Response | Client Action |
|------|---------|----------------|---------------|
| E2-1: SSE 연결 끊김 | 네트워크 불안정 | - | EventSource 자동 재연결 → queueToken으로 상태 재조회 |
| E2-2: 브라우저 닫기 | 사용자 이탈 | - | qstate TTL(30분) 만료 후 자연 정리 |
| E2-3: 브라우저 재오픈 (동일 clientId) | 재접속 | join 재호출 시 기존 queueToken 반환 | SSE 재연결로 대기열 복귀 |
| E2-4: 대기 중 SOLD_OUT | 전석 매진 | SSE: status=SOLD_OUT push | "매진되었습니다" 화면 전환 |
| E2-5: qstate TTL 만료 (30분 초과 대기) | 장시간 대기 | SSE: 상태 조회 실패 | "대기 시간이 초과되었습니다" 표시 |
| E2-6: queueToken 위조 | 토큰 조작 | SSE/status: 404 또는 빈 상태 | 에러 표시 |

### Phase 3: 입장 (Admission)

| Case | Trigger | Server Response | Client Action |
|------|---------|----------------|---------------|
| E3-1: enterToken 만료 (120초 초과) | 입장 지연 | 403 ENTER_TOKEN_INVALID | "입장권이 만료되었습니다" → 재대기 안내 |
| E3-2: enterToken 재사용 (이미 DEL됨) | 더블 클릭/탈취 | 403 ENTER_TOKEN_INVALID | 에러 표시 |
| E3-3: enterToken clientId 불일치 | 탈취 시도 | 403 ENTER_TOKEN_INVALID (MISMATCH) | 에러 표시 |
| E3-4: 동일 clientId 재입장 (이전 세션 존재) | 재접속 | Core Handshake Lua가 기존 세션 정리 후 새 세션 발급 | 정상 진행 |

### Phase 4: 좌석 선택 & 홀드

| Case | Trigger | Server Response | Client Action |
|------|---------|----------------|---------------|
| E4-1: 이미 다른 유저가 홀드한 좌석 선택 | 동시 선점 경합 | 409 SEAT_ALREADY_HELD | "다른 사용자가 선택 중입니다" → 좌석 재선택 |
| E4-2: 이미 다른 좌석을 홀드 중 | 1인 1좌석 위반 | 409 ALREADY_HOLDING | "이미 다른 좌석을 선택하셨습니다" |
| E4-3: 전석 매진 (holds + reservations = total) | 매진 | 409 SOLD_OUT | "매진되었습니다" 화면 |
| E4-4: coreSession 만료 | 5분 무활동 | 403 SESSION_TOKEN_INVALID | "세션이 만료되었습니다" → 재입장 안내 |
| E4-5: coreSessionToken 위조 | 토큰 조작 | 403 SESSION_TOKEN_INVALID | 에러 표시 |
| E4-6: 좌석 조회 중 다른 유저가 홀드 완료 | 화면 데이터 stale | hold 시도 시 409 | 좌석 목록 새로고침 유도 |

### Phase 5: 결제 (Confirm)

| Case | Trigger | Server Response | Client Action |
|------|---------|----------------|---------------|
| E5-1: 60초 내 confirm 성공 | 정상 흐름 | 200 + reservationId | 예매 완료 화면 |
| E5-2: 60초 초과 (홀드 만료) | 결제 지연 | 409 HOLD_EXPIRED | "선점 시간이 만료되었습니다" → 좌석 재선택 |
| E5-3: confirm 중복 호출 | 더블 클릭 | 200 + 기존 reservationId (멱등) | 정상 처리 |
| E5-4: 타인의 holdId로 confirm | 조작 시도 | 409 HOLD_EXPIRED | 에러 표시 |
| E5-5: confirm 중 네트워크 오류 | 불안정 | 클라이언트 timeout | 재시도 → 멱등성으로 안전 |

---

## 6.3 System-Level Edge Cases

### Admission Worker

| Case | Handling |
|------|---------|
| 모든 워커 동시 다운 | 대기열만 쌓이고 입장 발급 중단. 워커 재시작 시 자동 복구 (ZSET 유지) |
| 워커 중복 발급 시도 | Lua 원자화로 ZPOPMIN + rate/cap 체크가 원자적이므로 안전 |
| 발급 후 상태 변경 실패 | Lua 내에서 전부 처리하므로 부분 실패 없음 |

### Redis

| Case | Handling |
|------|---------|
| Redis 노드 일시 장애 | Cluster failover로 replica가 승격. 짧은 지연 발생 가능 |
| Redis 전체 장애 | Gate/Worker/Core 모두 503 반환. 복구 후 TTL 기반 자동 정리 |

### PostgreSQL

| Case | Handling |
|------|---------|
| DB 일시 장애 | Core 503 반환. hold 만료 정리 중단되나, 복구 후 스케줄러가 밀린 만료 일괄 정리 |
| 유니크 제약 위반 (race condition) | R2DBC에서 DataIntegrityViolationException → 409 응답 매핑 |

### 정리 스케줄러

| Case | Handling |
|------|---------|
| 멀티 Core 인스턴스에서 동시 실행 | 멱등하게 설계 (DELETE WHERE 조건). 중복 실행 안전 |
| 스케줄러 장애 | hold는 DB에 남지만, confirm 시 expires_at 검증으로 이중 방어 |

---

## 6.4 Security Considerations

| Threat | Mitigation |
|--------|-----------|
| enterToken 탈취 | 1회 사용 (DEL 후 무효). clientId 대조. TTL 120초 |
| coreSessionToken 탈취 | HMAC 서명 + Redis 검증. TTL 5분. clientId 쿠키 대조 |
| 대기열 순번 조작 | 서버가 ZSET 스코어 기반으로 순위 관리. 클라이언트 입력 불신 |
| 자동화 봇 (매크로) | clientId 쿠키 기반 중복 방지. Lua 원자화로 순서 보장 |
| 쿠키 공유/변조 | HttpOnly + clientId는 서버 발급. HMAC 토큰에 clientId 바인딩 |
| DDoS on SSE | HAProxy 연결 수 제한. SSE 주기 1초(과도한 push 방지) |
