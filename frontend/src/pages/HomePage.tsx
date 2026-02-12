import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useBookingStore } from '../stores/bookingStore';
import { fetchActiveSchedules, fetchSeats } from '../api/client';
import type { TrainSchedule } from '../types';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

export default function HomePage() {
  const navigate = useNavigate();
  const setSchedule = useBookingStore((s) => s.setSchedule);
  const setStoredSeats = useBookingStore((s) => s.setSeats);

  const [schedules, setSchedules] = useState<TrainSchedule[]>([]);
  const [syncing, setSyncing] = useState(false);

  useEffect(() => {
    fetchActiveSchedules()
      .then((list) => {
        const mapped = list.map((s) => ({
          eventId: s.eventId,
          scheduleId: s.scheduleId,
          trainName: s.trainName,
          trainNumber: s.trainNumber,
          departure: s.departure,
          arrival: s.arrival,
          departureTime: s.departureTime,
          arrivalTime: s.arrivalTime,
          date: s.serviceDate,
          price: s.price,
        }));
        setSchedules(mapped);
        if (mapped.length > 0) {
          setSchedule(mapped[0]);
          setStoredSeats([]);
        }
      })
      .catch(() => {
        // ignore load errors on home load
      });
  }, [setSchedule]);

  const handleReady = useCallback(async (target: TrainSchedule) => {
    if (!target) {
      alert('현재 활성화된 스케줄이 없습니다.');
      return;
    }
    setSyncing(true);
    try {
      try {
        const prefetched = await fetchSeats(target.eventId, target.scheduleId);
        setStoredSeats(prefetched);
        setSchedule(target);
        navigate('/seats', { replace: true });
        return;
      } catch {
        // 세션 없음 → 대기열 진입
      }

      setSchedule(target);
      navigate('/gate', { replace: true });
    } catch {
      alert('서버 연결에 실패했습니다. 다시 시도해주세요.');
    } finally {
      setSyncing(false);
    }
  }, [navigate, setSchedule, setStoredSeats]);

  const grouped = schedules.reduce<Record<string, TrainSchedule[]>>((acc, s) => {
    if (!acc[s.date]) acc[s.date] = [];
    acc[s.date].push(s);
    return acc;
  }, {});

  const dateKeys = Object.keys(grouped).sort();

  const formatDate = (dateStr: string) => {
    const d = new Date(`${dateStr}T00:00:00`);
    if (Number.isNaN(d.getTime())) return dateStr;
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    const day = WEEKDAYS[d.getDay()];
    return `${d.getFullYear()}.${mm}.${dd} (${day})`;
  };

  const formatTime = (timeStr: string) => timeStr.slice(0, 5);

  const calcDuration = (dep: string, arr: string) => {
    const [dh, dm] = dep.split(':').map(Number);
    const [ah, am] = arr.split(':').map(Number);
    let diff = (ah * 60 + am) - (dh * 60 + dm);
    if (diff < 0) diff += 24 * 60;
    const h = Math.floor(diff / 60);
    const m = diff % 60;
    return h > 0 ? `${h}시간 ${m}분` : `${m}분`;
  };

  return (
    <div>
      {/* Schedule List */}
      <div className="space-y-6">
        {dateKeys.length === 0 && (
          <div className="bg-white rounded-xl border border-gray-100 p-6 text-center text-gray-500">
            현재 활성화된 스케줄이 없습니다.
          </div>
        )}

        {dateKeys.map((dateKey) => (
          <div key={dateKey} className="space-y-0">
            <div className="flex items-center justify-between mb-2">
              <h2 className="text-base font-semibold text-ktx-navy">
                {formatDate(dateKey)}
              </h2>
              <span className="text-xs text-gray-500">
                {grouped[dateKey].length}편
              </span>
            </div>
            <div className="border-t border-gray-200">
              {grouped[dateKey].map((item) => (
                <div
                  key={item.scheduleId}
                  className="bg-white border-b border-gray-200 flex items-center hover:bg-gray-50 transition-colors"
                >
                  {/* Left accent */}
                  <div className="w-1 self-stretch bg-ktx-blue" />

                  {/* KTX logo + number */}
                  <div className="px-4 py-4 min-w-[70px] text-center">
                    <p className="text-sm font-black italic">
                      <span className="text-ktx-blue">KT</span><span className="text-ktx-red">X</span>
                    </p>
                    <p className="text-xs text-gray-500">{item.trainNumber}</p>
                  </div>

                  {/* Route info */}
                  <div className="flex-1 py-4 pr-4">
                    <p className="text-sm font-bold text-ktx-navy">
                      {item.departure} → {item.arrival}
                      <span className="font-normal text-gray-700">
                        ({formatTime(item.departureTime)} ~ {formatTime(item.arrivalTime)})
                      </span>
                    </p>
                    <p className="text-xs text-gray-400 mt-0.5">
                      소요시간: {calcDuration(item.departureTime, item.arrivalTime)}
                    </p>
                  </div>

                  {/* Price column */}
                  <div className="px-4 py-4 text-center min-w-[100px] border-l border-gray-100">
                    <p className="text-xs text-gray-400">일반실</p>
                    <p className="text-sm font-bold text-ktx-navy">{item.price.toLocaleString()}원</p>
                  </div>

                  {/* Action */}
                  <div className="px-4 py-4">
                    <button
                      onClick={() => handleReady(item)}
                      disabled={syncing}
                      className="bg-ktx-blue hover:bg-ktx-navy disabled:bg-gray-300 text-white text-sm font-semibold px-5 py-2 rounded-lg transition-colors cursor-pointer disabled:cursor-not-allowed whitespace-nowrap"
                    >
                      {syncing ? '준비 중...' : '예매'}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>

      {/* Info */}
      <div className="mt-6 bg-blue-50 border border-blue-200 rounded-lg p-4 text-sm text-blue-800">
        <p className="font-semibold mb-1">안내사항</p>
        <ul className="list-disc list-inside space-y-1 text-xs">
          <li>예매 버튼을 누르면 바로 대기열에 진입합니다.</li>
          <li>대기 순번에 따라 좌석 선택 화면으로 자동 이동합니다.</li>
          <li>좌석 선택 후 제한 시간 내 결제를 완료해 주세요.</li>
        </ul>
      </div>
    </div>
  );
}
