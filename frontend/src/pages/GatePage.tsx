import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useBookingStore } from '../stores/bookingStore';
import { joinQueue, streamQueue } from '../api/client';

type Phase = 'JOINING' | 'WAITING' | 'GRANTED' | 'SOLD_OUT' | 'ERROR';

export default function GatePage() {
  const navigate = useNavigate();
  const schedule = useBookingStore((s) => s.schedule);
  const setQueueToken = useBookingStore((s) => s.setQueueToken);
  const setEnterToken = useBookingStore((s) => s.setEnterToken);
  const setQueueProgress = useBookingStore((s) => s.setQueueProgress);

  const [phase, setPhase] = useState<Phase>('JOINING');
  const [rank, setRank] = useState(0);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState('');
  const esRef = useRef<EventSource | null>(null);

  // auto join on mount
  useEffect(() => {
    if (phase !== 'JOINING' || !schedule) return;
    const { syncToken } = useBookingStore.getState();

    (async () => {
      try {
        const join = await joinQueue(schedule.eventId, schedule.scheduleId, syncToken);
        setQueueToken(join.queueToken);
        setRank(join.estimatedRank);
        setPhase('WAITING');
      } catch {
        setError('대기열 진입에 실패했습니다. 다시 시도해주세요.');
        setPhase('ERROR');
      }
    })();
  }, [phase, schedule, setQueueToken]);

  // start SSE stream when WAITING
  useEffect(() => {
    if (phase !== 'WAITING' || !schedule) return;
    const { queueToken } = useBookingStore.getState();
    if (!queueToken) return;

    const es = streamQueue(
      schedule.eventId,
      schedule.scheduleId,
      queueToken,
      (progress) => {
        setQueueProgress(progress);
        setRank(progress.estimatedRank);
        setTotal(progress.totalInQueue);
        if (progress.status === 'ADMISSION_GRANTED' && progress.enterToken) {
          setEnterToken(progress.enterToken);
          setPhase('GRANTED');
          es.close();
        } else if (progress.status === 'SOLD_OUT') {
          setPhase('SOLD_OUT');
          es.close();
        }
      },
      () => {
        // SSE error - ignore reconnection
      },
    );
    esRef.current = es;

    return () => {
      esRef.current?.close();
    };
  }, [phase, schedule, setQueueProgress, setEnterToken]);

  // auto navigate when granted
  useEffect(() => {
    if (phase !== 'GRANTED') return;
    const timer = setTimeout(() => navigate('/seats', { replace: true }), 1500);
    return () => clearTimeout(timer);
  }, [phase, navigate]);

  if (!schedule) {
    navigate('/', { replace: true });
    return null;
  }

  const progressPercent = total > 0 ? Math.max(0, Math.min(100, ((total - rank) / total) * 100)) : 0;

  return (
    <div className="flex flex-col items-center">
      {/* Train info summary */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-4 w-full mb-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-xs bg-ktx-red text-white px-2 py-0.5 rounded font-semibold">
              {schedule.trainName}
            </span>
            <span className="text-sm font-bold text-ktx-navy">
              {schedule.departure} → {schedule.arrival}
            </span>
          </div>
          <span className="text-sm text-gray-500">
            {schedule.date} {schedule.departureTime}
          </span>
        </div>
      </div>

      {/* Main status card */}
      <div className="bg-white rounded-2xl shadow-lg w-full max-w-md p-8 text-center">
        {phase === 'JOINING' && (
          <>
            <div className="animate-spin w-10 h-10 border-4 border-ktx-red/20 border-t-ktx-red rounded-full mx-auto mb-4" />
            <p className="text-ktx-navy font-semibold">대기열 진입 중...</p>
          </>
        )}

        {phase === 'WAITING' && (
          <>
            <div className="mb-6">
              <p className="text-sm text-gray-500 mb-1">현재 나의 순번</p>
              <p className="text-5xl font-bold text-ktx-blue">{rank.toLocaleString()}</p>
              {total > 0 && (
                <p className="text-xs text-gray-400 mt-1">전체 대기 {total.toLocaleString()}명</p>
              )}
            </div>

            {/* Progress bar */}
            <div className="w-full bg-gray-100 rounded-full h-3 mb-4">
              <div
                className="bg-gradient-to-r from-ktx-blue to-ktx-red h-3 rounded-full transition-all duration-500"
                style={{ width: `${progressPercent}%` }}
              />
            </div>

            <div className="flex items-center justify-center gap-2 text-sm text-gray-500">
              <div className="w-2 h-2 rounded-full bg-ktx-blue animate-pulse" />
              대기 중입니다. 잠시만 기다려 주세요.
            </div>
          </>
        )}

        {phase === 'GRANTED' && (
          <>
            <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#16a34a" strokeWidth="3">
                <polyline points="20,6 9,17 4,12" />
              </svg>
            </div>
            <p className="text-xl font-bold text-green-600 mb-2">입장 승인!</p>
            <p className="text-sm text-gray-500">좌석 선택 화면으로 이동합니다...</p>
          </>
        )}

        {phase === 'SOLD_OUT' && (
          <>
            <div className="w-16 h-16 bg-red-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#dc2626" strokeWidth="2">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </div>
            <p className="text-xl font-bold text-red-600 mb-2">매진되었습니다</p>
            <p className="text-sm text-gray-500 mb-4">죄송합니다. 모든 좌석이 판매되었습니다.</p>
            <button
              onClick={() => navigate('/', { replace: true })}
              className="bg-ktx-blue text-white px-6 py-2 rounded-lg text-sm font-semibold cursor-pointer"
            >
              돌아가기
            </button>
          </>
        )}

        {phase === 'ERROR' && (
          <>
            <div className="w-16 h-16 bg-yellow-100 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#d97706" strokeWidth="2">
                <path d="M12 9v4m0 4h.01M10.29 3.86l-8.6 14.86A2 2 0 003.42 21h17.16a2 2 0 001.73-2.98l-8.6-14.86a2 2 0 00-3.46 0z" />
              </svg>
            </div>
            <p className="text-lg font-bold text-yellow-700 mb-2">오류 발생</p>
            <p className="text-sm text-gray-500 mb-4">{error}</p>
            <button
              onClick={() => navigate('/', { replace: true })}
              className="bg-ktx-blue text-white px-6 py-2 rounded-lg text-sm font-semibold cursor-pointer"
            >
              돌아가기
            </button>
          </>
        )}
      </div>
    </div>
  );
}
