import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useBookingStore } from '../stores/bookingStore';
import { fetchActiveSchedules, syncTime } from '../api/client';
import type { TrainSchedule } from '../types';

const COUNTDOWN_SEC = 5;
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

export default function HomePage() {
  const navigate = useNavigate();
  const setSchedule = useBookingStore((s) => s.setSchedule);
  const setSyncToken = useBookingStore((s) => s.setSyncToken);

  const [schedules, setSchedules] = useState<TrainSchedule[]>([]);
  const [showModal, setShowModal] = useState(false);
  const [countdown, setCountdown] = useState(COUNTDOWN_SEC);
  const [tooEarly, setTooEarly] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [serverOffsetMs, setServerOffsetMs] = useState(0);
  const [startAtMs, setStartAtMs] = useState(0);
  const [isOpen, setIsOpen] = useState(false);
  const timerRef = useRef<ReturnType<typeof setInterval>>(null);

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
      const sync = await syncTime(target.eventId, target.scheduleId);
      setServerOffsetMs(sync.serverTimeMs - Date.now());
      setStartAtMs(sync.startAtMs);
      setSyncToken(sync.syncToken);
      setSchedule(target);
      const initialRemaining = Math.max(0, sync.startAtMs - sync.serverTimeMs);
      setCountdown(Math.ceil(initialRemaining / 1000));
      setIsOpen(initialRemaining <= 0);
      setTooEarly(false);
      setShowModal(true);
    } catch {
      alert('서버 연결에 실패했습니다. 다시 시도해주세요.');
    } finally {
      setSyncing(false);
    }
  }, [setSchedule, setSyncToken]);

  // countdown timer
  useEffect(() => {
    if (!showModal) return;
    timerRef.current = setInterval(() => {
      const serverNow = Date.now() + serverOffsetMs;
      const remainingMs = startAtMs - serverNow;
      const seconds =
        remainingMs > 0
          ? Math.ceil(remainingMs / 1000)
          : Math.floor(Math.abs(remainingMs) / 1000);
      setIsOpen(remainingMs <= 0);
      setCountdown((prev) => {
        return seconds === prev ? prev : seconds;
      });
    }, 100);
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [showModal, serverOffsetMs, startAtMs]);

  const handleBook = () => {
    if (!isOpen) {
      setTooEarly(true);
      setTimeout(() => setTooEarly(false), 1500);
      return;
    }
    setShowModal(false);
    navigate('/gate', { replace: true });
  };

  const handleCloseModal = () => {
    setShowModal(false);
    if (timerRef.current) clearInterval(timerRef.current);
  };

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
          <li>예매 버튼을 누르면 카운트다운이 시작됩니다.</li>
          <li>카운트다운이 끝나면 예매하기 버튼을 눌러 대기열에 진입하세요.</li>
          <li>대기 순번에 따라 좌석 선택 화면으로 자동 이동합니다.</li>
          <li>좌석 선택 후 제한 시간 내 결제를 완료해 주세요.</li>
        </ul>
      </div>

      {/* Countdown Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-2xl p-8 max-w-sm w-full mx-4 text-center">
            <p className="text-sm text-gray-500 mb-2">{isOpen ? '예매 시작 후' : '예매 시작까지'}</p>
            <p
              className={`text-7xl font-bold font-mono mb-6 transition-colors ${
                isOpen ? 'text-ktx-red' : 'text-ktx-navy'
              }`}
            >
              {countdown.toString().padStart(2, '0')}
            </p>

            {isOpen && (
              <div className="w-16 h-1 bg-ktx-red rounded mx-auto mb-4 animate-pulse" />
            )}

            <div className="h-6 mb-3">
              {tooEarly && (
                <p className="text-red-500 font-semibold text-sm animate-bounce">
                  아직 시작 전입니다!
                </p>
              )}
            </div>

            <button
              onClick={handleBook}
              className={`w-full font-bold py-4 rounded-xl text-lg transition-all cursor-pointer ${
                isOpen
                  ? 'bg-ktx-red hover:bg-red-700 text-white shadow-lg shadow-red-200'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              예매하기
            </button>

            <button
              onClick={handleCloseModal}
              className="mt-3 text-sm text-gray-400 hover:text-gray-600 cursor-pointer"
            >
              취소
            </button>
          </div>
        </div>
      )}

    </div>
  );
}
