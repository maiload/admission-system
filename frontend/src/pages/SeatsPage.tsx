import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useBookingStore } from '../stores/bookingStore';
import { enter, fetchSeats, holdSeats } from '../api/client';
import type { Seat } from '../types';

export default function SeatsPage() {
  const navigate = useNavigate();
  const schedule = useBookingStore((s) => s.schedule);
  const enterToken = useBookingStore((s) => s.enterToken);
  const coreSessionToken = useBookingStore((s) => s.coreSessionToken);
  const setCoreSessionToken = useBookingStore((s) => s.setCoreSessionToken);
  const setHoldId = useBookingStore((s) => s.setHoldId);
  const setExpiresAt = useBookingStore((s) => s.setExpiresAt);

  const [seats, setSeats] = useState<Seat[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [holding, setHolding] = useState(false);
  const [error, setError] = useState('');

  // enter + fetch seats
  useEffect(() => {
    if (!schedule || !enterToken) {
      navigate('/', { replace: true });
      return;
    }

    (async () => {
      try {
        let token = coreSessionToken;
        if (!token) {
          const res = await enter(schedule.eventId, schedule.scheduleId, enterToken);
          token = res.coreSessionToken;
          setCoreSessionToken(token);
        }
        const seatList = await fetchSeats(schedule.eventId, schedule.scheduleId, token);
        setSeats(seatList);
      } catch {
        setError('좌석 정보를 불러오는 데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    })();
  }, [schedule, enterToken, coreSessionToken, setCoreSessionToken, navigate]);

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
      const token = useBookingStore.getState().coreSessionToken;
      const res = await holdSeats(schedule.eventId, schedule.scheduleId, [...selected], token);
      setHoldId(res.holdId);
      setExpiresAt(res.expiresAt);
      // store selected seat IDs
      const store = useBookingStore.getState();
      [...selected].forEach((id) => {
        if (!store.selectedSeatIds.includes(id)) {
          store.toggleSeat(id);
        }
      });
      navigate('/confirm', { replace: true });
    } catch {
      setError('좌석 선점에 실패했습니다. 다시 시도해주세요.');
      setHolding(false);
    }
  };

  if (!schedule) return null;

  // group seats into rows for train layout (4 seats per row: A B | C D)
  const seatRows: Seat[][] = [];
  for (let i = 0; i < seats.length; i += 4) {
    seatRows.push(seats.slice(i, i + 4));
  }

  const getSeatColor = (seat: Seat) => {
    if (selected.has(seat.seatId)) return 'bg-ktx-red text-white border-ktx-red';
    if (seat.status === 'HELD' || seat.status === 'CONFIRMED') return 'bg-gray-200 text-gray-400 border-gray-200 cursor-not-allowed';
    return 'bg-white text-ktx-navy border-gray-300 hover:border-ktx-blue cursor-pointer';
  };

  return (
    <div>
      {/* Train info */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-4 mb-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-xs bg-ktx-red text-white px-2 py-0.5 rounded font-semibold">
              {schedule.trainName} {schedule.trainNumber}
            </span>
            <span className="text-sm font-bold text-ktx-navy">
              {schedule.departure} → {schedule.arrival}
            </span>
          </div>
          <span className="text-sm text-gray-500">{schedule.date}</span>
        </div>
      </div>

      {loading ? (
        <div className="flex flex-col items-center py-20">
          <div className="animate-spin w-10 h-10 border-4 border-ktx-blue/20 border-t-ktx-blue rounded-full mb-4" />
          <p className="text-gray-500">좌석 정보를 불러오는 중...</p>
        </div>
      ) : error ? (
        <div className="text-center py-20">
          <p className="text-red-600 font-semibold">{error}</p>
        </div>
      ) : (
        <>
          {/* Legend */}
          <div className="flex gap-4 mb-4 text-xs">
            <div className="flex items-center gap-1">
              <div className="w-4 h-4 rounded border border-gray-300 bg-white" />
              <span>선택 가능</span>
            </div>
            <div className="flex items-center gap-1">
              <div className="w-4 h-4 rounded bg-ktx-red" />
              <span>선택됨</span>
            </div>
            <div className="flex items-center gap-1">
              <div className="w-4 h-4 rounded bg-gray-200" />
              <span>매진</span>
            </div>
          </div>

          {/* Train car */}
          <div className="bg-white rounded-2xl shadow-lg p-6 mb-4">
            {/* Car header */}
            <div className="flex items-center justify-between mb-4 pb-3 border-b border-gray-100">
              <span className="text-sm font-bold text-ktx-navy">1호차 (일반실)</span>
              <span className="text-xs text-gray-400">창측 ← A B &nbsp;&nbsp; 복도 &nbsp;&nbsp; C D → 창측</span>
            </div>

            {/* Seat grid */}
            <div className="space-y-2">
              {seatRows.map((row, ri) => (
                <div key={ri} className="flex items-center gap-2">
                  <span className="text-xs text-gray-400 w-6 text-right">{ri + 1}</span>
                  {/* Left pair */}
                  <div className="flex gap-1">
                    {row.slice(0, 2).map((seat) => (
                      <button
                        key={seat.seatId}
                        disabled={seat.status !== 'AVAILABLE'}
                        onClick={() => toggleSeat(seat.seatId)}
                        className={`w-12 h-10 rounded-lg border-2 text-xs font-semibold transition-colors ${getSeatColor(seat)}`}
                      >
                        {seat.label}
                      </button>
                    ))}
                  </div>
                  {/* Aisle */}
                  <div className="w-8" />
                  {/* Right pair */}
                  <div className="flex gap-1">
                    {row.slice(2, 4).map((seat) => (
                      <button
                        key={seat.seatId}
                        disabled={seat.status !== 'AVAILABLE'}
                        onClick={() => toggleSeat(seat.seatId)}
                        className={`w-12 h-10 rounded-lg border-2 text-xs font-semibold transition-colors ${getSeatColor(seat)}`}
                      >
                        {seat.label}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Bottom bar */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-4 flex items-center justify-between sticky bottom-4">
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
              className="bg-ktx-red hover:bg-red-700 disabled:bg-gray-300 text-white font-semibold px-8 py-3 rounded-xl transition-colors cursor-pointer disabled:cursor-not-allowed"
            >
              {holding ? '처리 중...' : '선택 완료'}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
