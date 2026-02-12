package com.example.ticket.core.adapter.out.persistence;

public final class SqlQueries {

    private SqlQueries() {
    }

    public static final String SEAT_FIND_ALL_BY_SCHEDULE = """
            SELECT s.id AS seat_id, s.zone, s.seat_no,
                   CASE
                       WHEN EXISTS (
                           SELECT 1 FROM reservations r
                           WHERE r.seat_id = s.id AND r.schedule_id = :scheduleId
                       ) THEN 'CONFIRMED'
                       WHEN EXISTS (
                           SELECT 1 FROM holds h
                           WHERE h.seat_id = s.id AND h.schedule_id = :scheduleId
                       ) THEN 'HELD'
                       ELSE 'AVAILABLE'
                   END AS status
            FROM seats s
            WHERE s.event_id = :eventId
            ORDER BY s.zone, s.seat_no
            """;

    public static final String SEAT_FIND_BY_SCHEDULE_AND_SEAT_ID = """
            SELECT s.id AS seat_id, s.zone, s.seat_no,
                   CASE
                       WHEN EXISTS (
                           SELECT 1 FROM reservations r
                           WHERE r.seat_id = s.id AND r.schedule_id = :scheduleId
                       ) THEN 'CONFIRMED'
                       WHEN EXISTS (
                           SELECT 1 FROM holds h
                           WHERE h.seat_id = s.id AND h.schedule_id = :scheduleId
                       ) THEN 'HELD'
                       ELSE 'AVAILABLE'
                   END AS status
            FROM seats s
            WHERE s.event_id = :eventId AND s.id = :seatId
            """;

    public static final String SEAT_COUNT_TOTAL_BY_EVENT = """
            SELECT COUNT(*) AS cnt FROM seats WHERE event_id = :eventId
            """;

    public static final String HOLD_COUNT_BY_SCHEDULE = """
            SELECT COUNT(*) AS cnt FROM holds WHERE schedule_id = :scheduleId
            """;

    public static final String RESERVATION_COUNT_BY_SCHEDULE = """
            SELECT COUNT(*) AS cnt FROM reservations WHERE schedule_id = :scheduleId
            """;

    public static final String HOLD_INSERT = """
            INSERT INTO holds (id, hold_group_id, schedule_id, seat_id, client_id, expires_at, created_at)
            VALUES (:id, :holdGroupId, :scheduleId, :seatId, :clientId, :expiresAt, :createdAt)
            """;

    public static final String HOLD_FIND_BY_ID = """
            SELECT * FROM holds WHERE id = :id
            """;

    public static final String HOLD_FIND_BY_SCHEDULE_AND_CLIENT = """
            SELECT * FROM holds WHERE schedule_id = :scheduleId AND client_id = :clientId
            """;

    public static final String HOLD_FIND_BY_GROUP_ID = """
            SELECT * FROM holds WHERE hold_group_id = :holdGroupId
            """;

    public static final String HOLD_DELETE_BY_ID = """
            DELETE FROM holds WHERE id = :id
            """;

    public static final String HOLD_DELETE_BY_GROUP_ID = """
            DELETE FROM holds WHERE hold_group_id = :holdGroupId
            """;

    public static final String HOLD_DELETE_EXPIRED = """
            DELETE FROM holds WHERE expires_at < :now
            """;

    public static final String RESERVATION_INSERT = """
            INSERT INTO reservations (id, schedule_id, seat_id, client_id, confirmed_at)
            VALUES (:id, :scheduleId, :seatId, :clientId, :confirmedAt)
            """;

    public static final String RESERVATION_FIND_BY_SCHEDULE_AND_SEAT = """
            SELECT * FROM reservations WHERE schedule_id = :scheduleId AND seat_id = :seatId
            """;

    public static final String SCHEDULE_FIND_ALL = """
            SELECT id, event_id, start_at FROM schedules
            """;

    public static final String SCHEDULE_FIND_DETAILS_BY_IDS = """
            SELECT id, event_id, start_at,
                   train_name, train_number, departure, arrival,
                   departure_time, arrival_time, service_date, price
            FROM schedules
            WHERE id = ANY(:scheduleIds)
            ORDER BY start_at
            """;
}
