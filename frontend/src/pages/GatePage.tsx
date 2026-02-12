import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useBookingStore } from '../stores/bookingStore';
import { joinQueue, streamQueue } from '../api/client';
import korailLogo from '../assets/korail-logo.png';

type Phase = 'JOINING' | 'WAITING' | 'GRANTED' | 'SOLD_OUT' | 'ERROR';

export default function GatePage() {
  const navigate = useNavigate();
  const schedule = useBookingStore((s) => s.schedule);
  const setQueueToken = useBookingStore((s) => s.setQueueToken);
  const setEnterToken = useBookingStore((s) => s.setEnterToken);
  const setQueueProgress = useBookingStore((s) => s.setQueueProgress);

  const [phase, setPhase] = useState<Phase>('JOINING');
  const [rank, setRank] = useState(0);
  const [initialRank, setInitialRank] = useState(0);
  const [error, setError] = useState('');
  const esRef = useRef<EventSource | null>(null);

  // auto join on mount
  useEffect(() => {
    if (phase !== 'JOINING' || !schedule) return;
    (async () => {
      try {
        const join = await joinQueue(schedule.eventId, schedule.scheduleId);
        setQueueToken(join.queueToken);
        setPhase('WAITING');
      } catch (err) {
        const message = err instanceof Error ? err.message : '';
        const isTooEarly = message.includes('"code":"TOO_EARLY"')
          || message.includes('TOO_EARLY');
        setError(isTooEarly
          ? '아직 대기열에 입장할 수 없습니다.'
          : '대기열 진입에 실패했습니다. 다시 시도해주세요.'
        );
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
        setRank(progress.rank);
        if (initialRank === 0 && progress.rank > 0) {
          setInitialRank(progress.rank);
        }
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

  const progressPercent = initialRank > 0
    ? Math.max(0, Math.min(100, ((initialRank - rank) / initialRank) * 100))
    : 0;

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] -mt-10">
      {/* Main status card */}
      <div className="bg-white rounded-2xl shadow-lg w-full max-w-md p-8 text-center">
        {/* Logo */}
        <img src={korailLogo} alt="KORAIL" className="h-8 mx-auto mb-6" />

        {phase === 'JOINING' && (
          <>
            <div className="animate-spin w-10 h-10 border-4 border-ktx-blue/20 border-t-ktx-blue rounded-full mx-auto mb-4" />
            <p className="text-ktx-navy font-semibold">대기열 진입 중...</p>
          </>
        )}

        {phase === 'WAITING' && (
          <>
            <div className="text-sm text-gray-600 leading-relaxed mb-6">
              <p>대기화면을 켜진 상태로 유지하여 주십시오.</p>
              <p>화면이 비활성화되면 연결이 끊어지고 대기순서가 다시 부여됩니다.</p>
              <p className="mt-3">새로고침, 닫기 버튼을 누르면 대기순서가 다시 부여됩니다.</p>
            </div>

            <p className="text-sm font-bold text-ktx-navy mb-2">나의 대기 순서</p>
            <p className="text-5xl font-bold text-ktx-navy tracking-wide mb-4">
              {rank.toLocaleString()}
            </p>

            {/* Progress bar */}
            <div className="w-full bg-gray-200 rounded-full h-3 mb-6">
              <div
                className="bg-ktx-blue h-3 rounded-full transition-all duration-500"
                style={{ width: `${progressPercent}%` }}
              />
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
            <p className="text-base font-semibold text-ktx-navy mb-4">{error}</p>
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
