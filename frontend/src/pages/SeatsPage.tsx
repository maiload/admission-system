import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useBookingStore } from '../stores/bookingStore';
import { enter, fetchSeats, holdSeats } from '../api/client';
import type { Seat } from '../types';

export default function SeatsPage() {
  const navigate = useNavigate();
  const schedule = useBookingStore((s) => s.schedule);
  const enterToken = useBookingStore((s) => s.enterToken);
  const clearHoldAndSelection = useBookingStore((s) => s.clearHoldAndSelection);
  const setHoldGroupId = useBookingStore((s) => s.setHoldGroupId);
  const setExpiresAt = useBookingStore((s) => s.setExpiresAt);
  const setSelectedSeatLabels = useBookingStore((s) => s.setSelectedSeatLabels);

  const [seats, setSeats] = useState<Seat[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [holding, setHolding] = useState(false);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const ROWS_PER_PAGE = 20;

  // clear previous hold/selection on mount
  useEffect(() => {
    clearHoldAndSelection();
  }, [clearHoldAndSelection]);

  // enter (멱등) + fetch seats
  useEffect(() => {
    if (!schedule) {
      navigate('/', { replace: true });
      return;
    }

    (async () => {
      try {
        // enter는 멱등: 세션 있으면 재사용, 없으면 enterToken으로 신규 발급
        await enter(schedule.eventId, schedule.scheduleId, enterToken || undefined);
        const seatList = await fetchSeats(schedule.eventId, schedule.scheduleId);
        setSeats(seatList);
      } catch {
        navigate('/', { replace: true });
      } finally {
        setLoading(false);
      }
    })();
  }, [schedule, enterToken, navigate]);

  const toggleSeat = (seatId: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(seatId)) {
        next.delete(seatId);
      } else if (next.size < 4) {
        next.add(seatId);
      }
      return next;
    });
  };

  const handleHold = async () => {
    if (!schedule || selected.size === 0) return;
    setHolding(true);
    try {
      const res = await holdSeats(schedule.eventId, schedule.scheduleId, [...selected]);
      setHoldGroupId(res.holdGroupId);
      setExpiresAt(res.expiresAt);
      // store selected seat IDs
      const store = useBookingStore.getState();
      [...selected].forEach((id) => {
        if (!store.selectedSeatIds.includes(id)) {
          store.toggleSeat(id);
        }
      });
      const labels = [...selected]
        .map((id) => seats.find((s) => s.seatId === id)?.label)
        .filter((v): v is string => Boolean(v));
      setSelectedSeatLabels(labels);
      navigate('/confirm', { replace: true });
    } catch {
      setError('좌석 선점에 실패했습니다. 다시 시도해주세요.');
      setHolding(false);
    }
  };

  if (!schedule) return null;

  // group seats into rows for train layout (4 seats per row: D C | B A)
  const seatRows: Seat[][] = [];
  for (let i = 0; i < seats.length; i += 4) {
    seatRows.push(seats.slice(i, i + 4));
  }
  const totalPages = Math.max(1, Math.ceil(seatRows.length / ROWS_PER_PAGE));
  const pagedRows = seatRows.slice(page * ROWS_PER_PAGE, (page + 1) * ROWS_PER_PAGE);

  const availableCount = seats.filter((s) => s.status === 'AVAILABLE').length;

  const getSeatStyle = (seat: Seat) => {
    if (selected.has(seat.seatId))
      return 'bg-[#098EB9] text-white border-[#098EB9]';
    if (seat.status === 'HELD' || seat.status === 'CONFIRMED')
      return 'bg-gray-100 text-gray-300 border-gray-200 cursor-not-allowed';
    return 'bg-white text-gray-700 border-gray-300 hover:border-ktx-blue cursor-pointer';
  };

  return (
    <div className="max-w-2xl mx-auto">
      {/* Title bar */}
      <div className="bg-white border-b border-gray-200 px-6 py-3 text-center mb-0 rounded-t-xl">
        <h1 className="text-base font-bold text-ktx-navy">좌석선택</h1>
      </div>

      {loading ? (
        <div className="flex flex-col items-center py-20 bg-white rounded-b-xl">
          <div className="animate-spin w-10 h-10 border-4 border-ktx-blue/20 border-t-ktx-blue rounded-full mb-4" />
          <p className="text-gray-500">좌석 정보를 불러오는 중...</p>
        </div>
      ) : error ? (
        <div className="text-center py-20 bg-white rounded-b-xl">
          <p className="text-red-600 font-semibold">{error}</p>
        </div>
      ) : (
        <div className="bg-white rounded-b-xl shadow-lg pb-4">
          {/* Train info */}
          <div className="px-4 py-3 text-center border-b border-gray-100">
            <p className="text-sm font-bold text-ktx-navy">
              {schedule.trainName} {schedule.trainNumber} (일반실)
            </p>
            <p className="text-xs text-gray-500 mt-1">
              잔여 {availableCount}석 / 전체 {seats.length}석
            </p>
          </div>

          {/* Legend */}
          <div className="flex justify-center gap-5 px-4 py-3 text-xs text-gray-500 border-b border-gray-100">
            <div className="flex items-center gap-1">
              <div className="w-3 h-3 rounded-sm bg-gray-100 border border-gray-200" />
              <span>선택 불가</span>
            </div>
            <div className="flex items-center gap-1">
              <div className="w-3 h-3 rounded-sm bg-[#098EB9]" />
              <span>선택됨</span>
            </div>
            <div className="flex items-center gap-1">
              <div className="w-3 h-3 rounded-sm bg-white border border-gray-300" />
              <span>선택 가능</span>
            </div>
          </div>

          {/* Column headers */}
          <div className="flex items-center justify-center gap-2 px-6 pt-3 pb-2">
            <span className="text-xs text-gray-400 w-14 text-center">창측</span>
            <span className="text-xs text-gray-400 w-14 text-center">내측</span>
            <div className="w-14" />
            <span className="text-xs text-gray-400 w-14 text-center">내측</span>
            <span className="text-xs text-gray-400 w-14 text-center">창측</span>
          </div>

          {/* Seat grid */}
          <div className="space-y-2 px-6">
            {pagedRows.map((row, ri) => (
              <div key={ri} className="flex items-center justify-center gap-2">
                {/* Left pair (D, C) */}
                {row.slice(0, 2).map((seat) => (
                  <button
                    key={seat.seatId}
                    disabled={seat.status !== 'AVAILABLE'}
                    onClick={() => toggleSeat(seat.seatId)}
                    className={`w-14 h-11 rounded-lg border text-xs font-semibold transition-colors ${getSeatStyle(seat)}`}
                  >
                    {seat.label}
                  </button>
                ))}
                {/* Aisle with arrow */}
                <div className="w-14 flex items-center justify-center text-gray-300 text-sm">
                  ▲
                </div>
                {/* Right pair (B, A) */}
                {row.slice(2, 4).map((seat) => (
                  <button
                    key={seat.seatId}
                    disabled={seat.status !== 'AVAILABLE'}
                    onClick={() => toggleSeat(seat.seatId)}
                    className={`w-14 h-11 rounded-lg border text-xs font-semibold transition-colors ${getSeatStyle(seat)}`}
                  >
                    {seat.label}
                  </button>
                ))}
              </div>
            ))}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-4 mt-4 px-4">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-3 py-1.5 text-xs rounded border border-gray-200 text-gray-500 disabled:opacity-40 disabled:cursor-not-allowed hover:bg-gray-50"
              >
                이전
              </button>
              <span className="text-xs text-gray-400">{page + 1} / {totalPages}</span>
              <button
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="px-3 py-1.5 text-xs rounded border border-gray-200 text-gray-500 disabled:opacity-40 disabled:cursor-not-allowed hover:bg-gray-50"
              >
                다음
              </button>
            </div>
          )}
        </div>
      )}

      {/* Bottom bar */}
      {!loading && !error && (
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-4 mt-4 flex items-center justify-between sticky bottom-4">
          <div>
            <p className="text-sm text-gray-500">
              선택 좌석: <span className="font-bold text-ktx-navy">{selected.size}석</span>
              <span className="text-xs text-gray-400 ml-1">(최대 4석)</span>
            </p>
            <p className="text-lg font-bold text-ktx-navy">
              {(schedule.price * selected.size).toLocaleString()}원
            </p>
          </div>
          <button
            onClick={handleHold}
            disabled={selected.size === 0 || holding}
            className="bg-ktx-blue hover:bg-ktx-navy disabled:bg-gray-300 text-white font-semibold px-8 py-3 rounded-xl transition-colors cursor-pointer disabled:cursor-not-allowed"
          >
            {holding ? '처리 중...' : '선택 완료'}
          </button>
        </div>
      )}
    </div>
  );
}
