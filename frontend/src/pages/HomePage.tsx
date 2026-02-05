import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useBookingStore } from '../stores/bookingStore';
import { syncTime } from '../api/client';
import type { TrainSchedule } from '../types';

const EVENT_ID = 'a0000000-0000-0000-0000-000000000001';

const SCHEDULE: TrainSchedule = {
  eventId: EVENT_ID,
  scheduleId: 'b0000000-0000-0000-0000-000000000002',
  trainName: 'KTX',
  trainNumber: '103',
  departure: '서울',
  arrival: '부산',
  departureTime: '09:00',
  arrivalTime: '11:35',
  date: '2026-01-30',
  price: 59800,
};

const COUNTDOWN_SEC = 5;

export default function HomePage() {
  const navigate = useNavigate();
  const setSchedule = useBookingStore((s) => s.setSchedule);
  const setSyncToken = useBookingStore((s) => s.setSyncToken);

  const [showModal, setShowModal] = useState(false);
  const [countdown, setCountdown] = useState(COUNTDOWN_SEC);
  const [tooEarly, setTooEarly] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [serverOffsetMs, setServerOffsetMs] = useState(0);
  const [startAtMs, setStartAtMs] = useState(0);
  const [isOpen, setIsOpen] = useState(false);
  const timerRef = useRef<ReturnType<typeof setInterval>>(null);

  const handleReady = useCallback(async () => {
    setSyncing(true);
    try {
      const sync = await syncTime(SCHEDULE.eventId, SCHEDULE.scheduleId);
      setServerOffsetMs(sync.serverTimeMs - Date.now());
      setStartAtMs(sync.startAtMs);
      setSyncToken(sync.syncToken);
      setSchedule(SCHEDULE);
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

  const s = SCHEDULE;

  return (
    <div>
      {/* Banner */}
      <div className="bg-gradient-to-r from-ktx-blue to-ktx-navy rounded-xl p-6 mb-6 text-white">
        <h1 className="text-2xl font-bold mb-1">KTX 승차권 예매</h1>
        <p className="text-sm opacity-80">
          설 특별 수송 기간 열차 예매가 시작됩니다. 선착순 예매 진행 중!
        </p>
      </div>

      {/* Date selector */}
      <div className="flex items-center gap-2 mb-4">
        <span className="text-sm font-semibold text-ktx-navy">출발일</span>
        <span className="bg-white border border-gray-200 rounded-lg px-4 py-2 text-sm font-medium">
          2026년 1월 30일 (금)
        </span>
      </div>

      {/* Single schedule */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-4 hover:shadow-md transition-shadow">
        <div className="flex items-center justify-between">
          {/* Train info */}
          <div className="flex items-center gap-4 flex-1">
            <div className="text-center">
              <p className="text-xs text-ktx-red font-semibold">{s.trainName}</p>
              <p className="text-xs text-gray-400">{s.trainNumber}호</p>
            </div>
            <div className="flex items-center gap-3 flex-1">
              <div className="text-center min-w-[60px]">
                <p className="text-lg font-bold text-ktx-navy">{s.departureTime}</p>
                <p className="text-xs text-gray-500">{s.departure}</p>
              </div>
              <div className="flex-1 flex items-center">
                <div className="w-1.5 h-1.5 rounded-full bg-ktx-blue" />
                <div className="flex-1 border-t border-gray-300 mx-1" />
                <div className="w-1.5 h-1.5 rounded-full bg-ktx-red" />
              </div>
              <div className="text-center min-w-[60px]">
                <p className="text-lg font-bold text-ktx-navy">{s.arrivalTime}</p>
                <p className="text-xs text-gray-500">{s.arrival}</p>
              </div>
            </div>
          </div>

          {/* Price & button */}
          <div className="text-right ml-4">
            <p className="text-sm font-bold text-ktx-navy mb-1">
              {s.price.toLocaleString()}원
            </p>
            <button
              onClick={handleReady}
              disabled={syncing}
              className="bg-ktx-blue hover:bg-ktx-navy disabled:bg-gray-300 text-white text-sm font-semibold px-4 py-2 rounded-lg transition-colors cursor-pointer disabled:cursor-not-allowed"
            >
              {syncing ? '준비 중...' : '준비'}
            </button>
          </div>
        </div>
      </div>

      {/* Info */}
      <div className="mt-6 bg-blue-50 border border-blue-200 rounded-lg p-4 text-sm text-blue-800">
        <p className="font-semibold mb-1">안내사항</p>
        <ul className="list-disc list-inside space-y-1 text-xs">
          <li>준비 버튼을 누르면 카운트다운이 시작됩니다.</li>
          <li>카운트다운이 끝나면 예매하기 버튼을 눌러 대기열에 진입하세요.</li>
          <li>대기 순번에 따라 좌석 선택 화면으로 자동 이동합니다.</li>
          <li>좌석 선택 후 제한 시간 내 결제를 완료해 주세요.</li>
        </ul>
      </div>

      {/* Countdown Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-2xl p-8 max-w-sm w-full mx-4 text-center">
            <p className="text-sm text-gray-500 mb-2">예매 시작까지</p>
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
